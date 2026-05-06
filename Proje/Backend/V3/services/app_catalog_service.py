import re
import time
import unicodedata
from dataclasses import dataclass
from typing import Dict, Iterable, List, Optional, Set, Tuple

from V3.resources.transliteration import ARABIC_TO_LATIN_TRANSLITERATION
from V3.resources.app_aliases import BRAND_ALIAS_GROUPS
from V3.resources.arabic_phonetic import (
    LATIN_LETTER_ARABIC_NAMES,
    LATIN_TO_ARABIC_CHAR_MAP,
    LATIN_TO_ARABIC_CHUNK_MAP,
    LATIN_TO_ARABIC_CHUNK_VARIANTS,
)

APP_CATALOG_TTL_SECONDS = 2 * 60 * 60
MAX_APP_CATALOG_SESSIONS = 64
AMBIGUOUS_SCORE_MARGIN = 0.03
MAX_AMBIGUOUS_MATCHES = 5
MAX_SUGGESTED_MATCHES = 5
SUGGESTION_MIN_SCORE = 0.35
MAX_INDEXED_CANDIDATE_APPS = 80
NGRAM_SIZE = 3
MIN_NGRAM_MATCH_RATIO = 0.25
MAX_ARABIC_PHONETIC_ALIASES = 12
MAX_ARABIC_PHONETIC_TOKENS = 6
MAX_ARABIC_PHONETIC_TOKEN_VARIANTS = 3

BRAND_ALIAS_REPLACEMENTS: List[Tuple[str, str]] = []
for group in BRAND_ALIAS_GROUPS:
    canonical = _canonical = group[0]
    for alias in group:
        BRAND_ALIAS_REPLACEMENTS.append((alias, _canonical))
_normalized_brand_alias_replacements: Optional[List[Tuple[str, str]]] = None
_brand_alias_replacement_index: Optional[Dict[str, List[Tuple[str, str]]]] = None
_brand_alias_compact_replacement_index: Optional[Dict[str, List[Tuple[str, str]]]] = None


@dataclass(frozen=True)
class AppCatalogEntryRecord:
    label: str
    package_name: str
    aliases: List[str]
    match_aliases: List[str]


@dataclass(frozen=True)
class AppMatch:
    label: str
    package_name: str
    score: float


@dataclass(frozen=True)
class AppMatchResolution:
    match: Optional[AppMatch]
    ambiguous_matches: List[AppMatch]
    suggested_matches: List[AppMatch]

    @property
    def is_ambiguous(self) -> bool:
        return bool(self.ambiguous_matches)


_catalogs: Dict[str, Dict[str, object]] = {}


def save_app_catalog(
    session_id: str,
    catalog_version: Optional[str],
    apps: Iterable[object],
    language: Optional[str] = None,
) -> Dict[str, object]:
    _cleanup_expired_catalogs()

    entries: List[AppCatalogEntryRecord] = []
    include_arabic_phonetic_aliases = _is_arabic_language(language)

    for app in apps:
        label = _get_value(app, "label")
        package_name = _get_value(app, "package_name")
        aliases = _get_value(app, "aliases") or []

        if not _has_text(label) or not _has_text(package_name):
            continue

        label_text = str(label).strip()
        package_text = str(package_name).strip()
        alias_texts = [str(alias).strip() for alias in aliases if _has_text(alias)]

        entries.append(AppCatalogEntryRecord(
            label=label_text,
            package_name=package_text,
            aliases=alias_texts,
            match_aliases=_build_match_aliases(
                label_text,
                package_text,
                alias_texts,
                include_arabic_phonetic_aliases=include_arabic_phonetic_aliases,
            ),
        ))

    version = catalog_version or _build_catalog_version(entries)
    now = time.monotonic()
    _catalogs[session_id] = {
        "catalog_version": version,
        "language": str(language).strip().upper() if _has_text(language) else None,
        "apps": entries,
        "search_index": _build_catalog_search_index(entries),
        "created_at": now,
        "last_seen": now,
    }
    _prune_oldest_catalogs()

    return {
        "session_id": session_id,
        "catalog_version": version,
        "app_count": len(entries),
    }


def has_app_catalog(session_id: Optional[str]) -> bool:
    return _get_catalog(session_id) is not None


def is_catalog_version_current(session_id: Optional[str], catalog_version: Optional[str]) -> bool:
    if not _has_text(catalog_version):
        return True

    if not _has_text(session_id):
        return False

    catalog = _get_catalog(session_id)
    if not catalog:
        return False

    return catalog.get("catalog_version") == catalog_version


def delete_app_catalog(session_id: Optional[str]) -> bool:
    if not _has_text(session_id):
        return False

    return _catalogs.pop(str(session_id), None) is not None


def catalog_count() -> int:
    _cleanup_expired_catalogs()
    return len(_catalogs)


def resolve_app_match(session_id: Optional[str], candidate: str) -> AppMatchResolution:
    if not _has_text(session_id) or not _has_text(candidate):
        return AppMatchResolution(None, [], [])

    catalog = _get_catalog(session_id)
    if not catalog:
        return AppMatchResolution(None, [], [])

    candidate_variants = _expand_text_variants(candidate)
    if not candidate_variants:
        return AppMatchResolution(None, [], [])

    threshold = min(_minimum_score(compact) for _, compact in candidate_variants)
    matches: List[AppMatch] = []
    suggested_matches: List[AppMatch] = []

    candidate_entry_indexes = _candidate_entry_indexes(catalog, candidate_variants)
    if not candidate_entry_indexes:
        return AppMatchResolution(None, [], [])

    entries = catalog["apps"]
    for app_index in candidate_entry_indexes:
        app = entries[app_index]
        score = _score_candidate(candidate_variants, app)
        app_match = AppMatch(app.label, app.package_name, score)
        if score >= threshold:
            matches.append(app_match)
        elif score >= SUGGESTION_MIN_SCORE:
            suggested_matches.append(app_match)

    suggested_matches = _top_matches(suggested_matches, MAX_SUGGESTED_MATCHES)

    if not matches:
        return AppMatchResolution(None, [], suggested_matches)

    matches = _top_matches(matches, len(matches))
    generic_label_matches = _generic_label_token_matches(entries, candidate_variants)
    if len(generic_label_matches) > 1:
        return AppMatchResolution(None, generic_label_matches[:MAX_AMBIGUOUS_MATCHES], suggested_matches)

    best_match = matches[0]
    ambiguous_matches = [
        match
        for match in matches
        if best_match.score - match.score <= AMBIGUOUS_SCORE_MARGIN
    ]

    if len(ambiguous_matches) > 1:
        return AppMatchResolution(None, ambiguous_matches[:MAX_AMBIGUOUS_MATCHES], suggested_matches)

    return AppMatchResolution(best_match, [], suggested_matches)


def suggest_app_matches(session_id: Optional[str], candidate: str) -> List[AppMatch]:
    return resolve_app_match(session_id, candidate).suggested_matches


def find_app_match(session_id: Optional[str], candidate: str) -> Optional[AppMatch]:
    return resolve_app_match(session_id, candidate).match


def _top_matches(matches: List[AppMatch], limit: int) -> List[AppMatch]:
    if not matches:
        return []

    matches = _dedupe_matches(matches)
    matches.sort(key=lambda match: (-match.score, match.label.casefold(), match.package_name))
    return matches[:limit]


def _generic_label_token_matches(
    entries: List[AppCatalogEntryRecord],
    candidate_variants: List[Tuple[str, str]],
) -> List[AppMatch]:
    for token in _single_token_candidate_values(candidate_variants):
        exact_label_matches: List[AppMatch] = []
        containing_label_matches: List[AppMatch] = []

        for app in entries:
            label_normalized = _normalize_words(_split_compound_words(app.label))
            label_compact = label_normalized.replace(" ", "")
            label_tokens = _fast_tokens(label_normalized)

            if token == label_compact:
                exact_label_matches.append(AppMatch(app.label, app.package_name, 1.0))
            elif token in label_tokens:
                containing_label_matches.append(AppMatch(app.label, app.package_name, 0.96))

        exact_label_matches = _top_matches(exact_label_matches, MAX_AMBIGUOUS_MATCHES)
        if len(exact_label_matches) > 1:
            return exact_label_matches

        if exact_label_matches:
            continue

        containing_label_matches = _top_matches(containing_label_matches, MAX_AMBIGUOUS_MATCHES)
        if len(containing_label_matches) > 1:
            return containing_label_matches

    return []


def _single_token_candidate_values(candidate_variants: List[Tuple[str, str]]) -> List[str]:
    tokens: List[str] = []
    seen: Set[str] = set()

    for candidate_normalized, candidate_compact in candidate_variants:
        if (
            candidate_normalized
            and candidate_normalized == candidate_compact
            and len(candidate_normalized) >= 3
            and candidate_normalized not in seen
        ):
            tokens.append(candidate_normalized)
            seen.add(candidate_normalized)

    return tokens


def _build_catalog_search_index(entries: List[AppCatalogEntryRecord]) -> Dict[str, Dict[str, Set[int]]]:
    exact_index: Dict[str, Set[int]] = {}
    compact_index: Dict[str, Set[int]] = {}
    token_index: Dict[str, Set[int]] = {}
    ngram_index: Dict[str, Set[int]] = {}

    for app_index, app in enumerate(entries):
        for alias in app.match_aliases:
            normalized = _normalize_words(alias)
            compact = normalized.replace(" ", "")
            if not compact:
                continue

            _add_index_value(exact_index, normalized, app_index)
            _add_index_value(compact_index, compact, app_index)

            for token in _fast_tokens(normalized):
                if len(token) >= 2:
                    _add_index_value(token_index, token, app_index)

            for ngram in _ngrams(compact):
                _add_index_value(ngram_index, ngram, app_index)

    return {
        "exact": exact_index,
        "compact": compact_index,
        "token": token_index,
        "ngram": ngram_index,
    }


def _candidate_entry_indexes(
    catalog: Dict[str, object],
    candidate_variants: List[Tuple[str, str]],
) -> List[int]:
    search_index = catalog.get("search_index")
    if not isinstance(search_index, dict):
        return []

    exact_index = search_index.get("exact", {})
    compact_index = search_index.get("compact", {})
    token_index = search_index.get("token", {})
    ngram_index = search_index.get("ngram", {})
    candidate_scores: Dict[int, float] = {}

    for candidate_normalized, candidate_compact in candidate_variants:
        if not candidate_compact:
            continue

        _add_candidate_scores(candidate_scores, exact_index.get(candidate_normalized, set()), 6.0)
        _add_candidate_scores(candidate_scores, compact_index.get(candidate_compact, set()), 5.5)

        for token in _fast_tokens(candidate_normalized):
            if len(token) >= 2:
                _add_candidate_scores(candidate_scores, token_index.get(token, set()), 2.0)

        ngrams = _ngrams(candidate_compact)
        if ngrams:
            ngram_hits: Dict[int, int] = {}
            for ngram in ngrams:
                for app_index in ngram_index.get(ngram, set()):
                    ngram_hits[app_index] = ngram_hits.get(app_index, 0) + 1

            min_hits = max(1, int(len(ngrams) * MIN_NGRAM_MATCH_RATIO))
            for app_index, hit_count in ngram_hits.items():
                if hit_count >= min_hits:
                    _add_candidate_scores(candidate_scores, [app_index], hit_count / len(ngrams))

    return [
        app_index
        for app_index, _ in sorted(candidate_scores.items(), key=lambda item: (-item[1], item[0]))
    ][:MAX_INDEXED_CANDIDATE_APPS]


def _add_index_value(index: Dict[str, Set[int]], key: str, app_index: int) -> None:
    if key:
        index.setdefault(key, set()).add(app_index)


def _add_candidate_scores(candidate_scores: Dict[int, float], app_indexes: Iterable[int], score: float) -> None:
    for app_index in app_indexes:
        candidate_scores[app_index] = candidate_scores.get(app_index, 0.0) + score


def _build_match_aliases(
    label: str,
    package_name: str,
    aliases: List[str],
    include_arabic_phonetic_aliases: bool = False,
) -> List[str]:
    display_aliases: Set[str] = set()

    for raw_alias in [label, *aliases]:
        if _has_text(raw_alias):
            display_aliases.add(str(raw_alias))
            display_aliases.add(_split_compound_words(str(raw_alias)))

    raw_aliases: Set[str] = set(display_aliases)
    generated_arabic_aliases: Set[str] = set()
    if include_arabic_phonetic_aliases:
        for display_alias in display_aliases:
            generated_arabic_aliases.update(_arabic_phonetic_aliases(display_alias))

    raw_aliases.update(_package_aliases(package_name))

    expanded: Set[str] = set()
    for raw_alias in raw_aliases:
        for normalized, _ in _expand_text_variants(raw_alias):
            if normalized:
                expanded.add(normalized)
                expanded.add(normalized.replace(" ", ""))

    for generated_alias in generated_arabic_aliases:
        normalized = _normalize_words(generated_alias)
        if normalized:
            expanded.add(normalized)
            expanded.add(normalized.replace(" ", ""))

    return sorted(expanded)


def _is_arabic_language(language: Optional[str]) -> bool:
    return _has_text(language) and str(language).strip().casefold().replace("_", "-").startswith("ar")


def _arabic_phonetic_aliases(value: str) -> Set[str]:
    if not _should_build_arabic_phonetic_alias(value):
        return set()

    tokens = _latin_app_name_tokens(value)
    if not tokens:
        return set()

    if len(tokens) > MAX_ARABIC_PHONETIC_TOKENS:
        tokens = tokens[:MAX_ARABIC_PHONETIC_TOKENS]

    token_variants: List[List[str]] = []
    for token in tokens:
        variants = _latin_token_to_arabic_phonetic_variants(token)
        if not variants:
            return set()
        token_variants.append(variants[:MAX_ARABIC_PHONETIC_TOKEN_VARIANTS])

    spaced_aliases = _combine_arabic_phonetic_tokens(token_variants)
    aliases: Set[str] = set()
    for alias in spaced_aliases:
        aliases.add(alias)
        aliases.add(alias.replace(" ", ""))

    return {alias for alias in aliases if alias}


def _latin_app_name_tokens(value: str) -> List[str]:
    split_text = _split_compound_words(str(value))
    return re.findall(r"[A-Za-z0-9]+", split_text)


def _should_build_arabic_phonetic_alias(value: str) -> bool:
    text = str(value)
    if re.search(r"[\u0600-\u06FF]", text):
        return False

    if not re.search(r"[A-Za-z]", text):
        return False

    normalized = _normalize_words(_split_compound_words(text))
    compact = normalized.replace(" ", "")
    if len(compact) < 2:
        return len(compact) == 1 and compact.isalpha()
    if len(compact) > 48:
        return False

    tokens = [token for token in normalized.split(" ") if token]
    if len(tokens) == 1 and len(compact) > 18:
        return False

    return True


def _combine_arabic_phonetic_tokens(token_variants: List[List[str]]) -> List[str]:
    aliases = [""]
    for variants in token_variants:
        next_aliases = []
        for alias in aliases:
            for variant in variants:
                next_aliases.append(f"{alias} {variant}".strip())
                if len(next_aliases) >= MAX_ARABIC_PHONETIC_ALIASES:
                    break
            if len(next_aliases) >= MAX_ARABIC_PHONETIC_ALIASES:
                break
        aliases = next_aliases

    return aliases[:MAX_ARABIC_PHONETIC_ALIASES]


def _latin_token_to_arabic_phonetic_variants(token: str) -> List[str]:
    normalized = re.sub(r"[^a-z0-9]", "", _normalize_words(token))
    if not normalized:
        return []

    variants: List[str] = []
    variants.extend(_latin_acronym_to_arabic_variants(token, normalized))
    variants.extend(_latin_token_to_arabic_phonetic_candidates(normalized))

    general = _latin_token_to_arabic_phonetic(normalized)
    if general:
        variants.append(general)
        compressed = _compress_repeated_arabic_sounds(general)
        if compressed != general:
            variants.append(compressed)

    return _dedupe_preserve_order(variants)


def _latin_token_to_arabic_phonetic(token: str) -> str:
    candidates = _latin_token_to_arabic_phonetic_candidates(token)
    return candidates[0] if candidates else ""


def _latin_acronym_to_arabic_variants(raw_token: str, normalized: str) -> List[str]:
    if not _looks_like_latin_acronym(raw_token, normalized):
        return []

    letter_options = [
        LATIN_LETTER_ARABIC_NAMES.get(char, (LATIN_TO_ARABIC_CHAR_MAP.get(char, char),))[0]
        for char in normalized
    ]

    variants = [" ".join(letter_options)]
    if len(normalized) == 1:
        variants.extend(LATIN_LETTER_ARABIC_NAMES.get(normalized, ()))

    return _dedupe_preserve_order(variants)


def _looks_like_latin_acronym(raw_token: str, normalized: str) -> bool:
    if len(normalized) == 1 and normalized.isalpha():
        return True

    if not normalized.isalpha() or len(normalized) > 5:
        return False

    raw_letters = re.sub(r"[^A-Za-z]", "", str(raw_token))
    if len(raw_letters) >= 2 and raw_letters.isupper():
        return True

    return len(normalized) >= 2 and all(char not in "aeiou" for char in normalized)


def _latin_token_to_arabic_phonetic_candidates(token: str) -> List[str]:
    result: List[str] = []
    states = [("", 0)]

    while states:
        prefix, index = states.pop(0)
        if index >= len(token):
            result.append(prefix)
            if len(result) >= 8:
                break
            continue

        for value, next_index in _latin_phonetic_steps(token, index):
            states.append((prefix + value, next_index))
            if len(states) >= 16:
                states = states[:16]
                break

    return _dedupe_preserve_order(result)


def _latin_phonetic_steps(token: str, index: int) -> List[Tuple[str, int]]:
    char = token[index]

    if char.isdigit():
        return [(char, index + 1)]

    if token[index:index + 2] == "es" and index == len(token) - 2 and len(token) > 3:
        return [("ز", index + 2), ("س", index + 2)]

    for size in (4, 3, 2):
        chunk = token[index:index + size]
        if chunk in LATIN_TO_ARABIC_CHUNK_VARIANTS:
            return [(variant, index + size) for variant in LATIN_TO_ARABIC_CHUNK_VARIANTS[chunk]]
        if chunk in LATIN_TO_ARABIC_CHUNK_MAP:
            return [(LATIN_TO_ARABIC_CHUNK_MAP[chunk], index + size)]

    if _is_silent_final_e(token, index):
        return [("", index + 1), (LATIN_TO_ARABIC_CHAR_MAP[char], index + 1)]

    if _has_long_a_silent_e_pattern(token, index):
        return [("ي", index + 1), (LATIN_TO_ARABIC_CHAR_MAP[char], index + 1)]

    if _can_drop_short_vowel(token, index):
        return [("", index + 1), (LATIN_TO_ARABIC_CHAR_MAP[char], index + 1)]

    if char == "c":
        next_char = token[index + 1:index + 2]
        return [("س" if next_char in {"e", "i", "y"} else "ك", index + 1)]

    if char == "s" and index == len(token) - 1 and index > 0 and token[index - 1] in "aeioy":
        return [("ز", index + 1), (LATIN_TO_ARABIC_CHAR_MAP[char], index + 1)]

    return [(LATIN_TO_ARABIC_CHAR_MAP.get(char, char), index + 1)]


def _is_silent_final_e(token: str, index: int) -> bool:
    return token[index] == "e" and index == len(token) - 1 and len(token) > 2


def _has_long_a_silent_e_pattern(token: str, index: int) -> bool:
    if token[index] != "a":
        return False

    if token.endswith("es"):
        silent_e_index = len(token) - 2
    elif token.endswith("e"):
        silent_e_index = len(token) - 1
    else:
        return False

    if index >= silent_e_index - 1:
        return False

    middle = token[index + 1:silent_e_index]
    return bool(middle) and all(char not in "aeiou" for char in middle)


def _can_drop_short_vowel(token: str, index: int) -> bool:
    if token[index] not in {"u"}:
        return False

    previous_char = token[index - 1:index]
    next_char = token[index + 1:index + 2]
    return bool(previous_char and next_char) and previous_char not in "aeiou" and next_char not in "aeiou"


def _compress_repeated_arabic_sounds(value: str) -> str:
    return re.sub(r"([اوي])\1+", r"\1", value)


def _dedupe_preserve_order(values: Iterable[str]) -> List[str]:
    result: List[str] = []
    seen: Set[str] = set()
    for value in values:
        if value and value not in seen:
            result.append(value)
            seen.add(value)
    return result


def _package_aliases(package_name: str) -> Set[str]:
    aliases: Set[str] = set()
    normalized_package = _normalize_words(str(package_name).replace(".", " "))
    tokens = [token for token in normalized_package.split() if token and token not in _package_stopwords()]

    aliases.add(normalized_package)
    if tokens:
        aliases.add(" ".join(tokens))

    for token in tokens:
        aliases.add(token)

    if tokens:
        aliases.add(tokens[-1])

    return aliases


def _package_stopwords() -> Set[str]:
    return {
        "com",
        "org",
        "net",
        "android",
        "app",
        "apps",
        "mobile",
        "client",
        "google",
        "microsoft",
    }


def _score_candidate(candidate_variants: List[Tuple[str, str]], app: AppCatalogEntryRecord) -> float:
    score = 0.0
    candidate_data = [
        (candidate_normalized, candidate_compact, _fast_tokens(candidate_normalized))
        for candidate_normalized, candidate_compact in candidate_variants
        if candidate_compact
    ]

    for candidate_normalized, candidate_compact, candidate_tokens in candidate_data:
        for alias_normalized in app.match_aliases:
            alias_compact = alias_normalized.replace(" ", "")
            if not alias_compact:
                continue
            alias_tokens = _fast_tokens(alias_normalized)

            if candidate_compact == alias_compact:
                score = max(score, 1.0)

            if candidate_normalized and candidate_normalized in alias_tokens:
                score = max(score, 0.96)

            if alias_compact.startswith(candidate_compact) or candidate_compact.startswith(alias_compact):
                score = max(score, 0.90 - _length_penalty(candidate_compact, alias_compact))

            if candidate_compact in alias_compact or alias_compact in candidate_compact:
                score = max(score, 0.84 - _length_penalty(candidate_compact, alias_compact))

            ngram_overlap = 0.0
            if candidate_compact[0] != alias_compact[0]:
                ngram_overlap = _ngram_overlap_ratio(candidate_compact, alias_compact)
                score = max(score, ngram_overlap * 0.82)

            if _should_compare_edit_distance(candidate_compact, alias_compact, ngram_overlap):
                score = max(score, _levenshtein_similarity(candidate_compact, alias_compact))
            score = max(score, _token_overlap_from_sets(candidate_tokens, alias_tokens) * 0.92)

    return score


def _expand_text_variants(value: str) -> List[Tuple[str, str]]:
    variants: Set[str] = set()

    for raw_value in [str(value), _split_compound_words(str(value))]:
        normalized = _normalize_words(raw_value)
        if not normalized:
            continue

        variants.add(normalized)
        variants.add(normalized.replace(" ", ""))
        variants.update(_expand_spelled_text_variants(normalized))

        transliterated = _arabic_to_latin(normalized)
        if transliterated != normalized:
            variants.add(_normalize_words(transliterated))
            variants.update(_expand_spelled_text_variants(transliterated))

        for expanded in _brand_expanded_texts(normalized):
            variants.add(expanded)
            variants.add(expanded.replace(" ", ""))
            variants.update(_expand_spelled_text_variants(expanded))

        for expanded in _brand_expanded_texts(transliterated):
            variants.add(expanded)
            variants.add(expanded.replace(" ", ""))
            variants.update(_expand_spelled_text_variants(expanded))

    return [
        (variant, variant.replace(" ", ""))
        for variant in sorted(variants)
        if variant.replace(" ", "")
    ]


def _brand_expanded_texts(text: str) -> Set[str]:
    variants = {_normalize_words(text)}

    for _ in range(3):
        for variant in list(variants):
            for alias_normalized, canonical_normalized in _brand_alias_replacements_for(variant):
                replaced = _replace_alias_phrase(variant, alias_normalized, canonical_normalized)
                if replaced != variant:
                    variants.add(replaced)

    return {variant for variant in variants if variant}


def _expand_spelled_text_variants(text: str) -> Set[str]:
    tokens = [token for token in _normalize_words(text).split(" ") if token]
    if len(tokens) < 2:
        return set()

    variants = [""]
    matched_tokens = 0
    index = 0

    while index < len(tokens):
        letters, consumed = _spelled_token_to_letters(tokens, index)
        if not letters:
            index += 1
            continue

        next_variants = []
        for variant in variants:
            for letter in letters:
                next_variants.append(variant + letter)

        variants = next_variants[:8]
        matched_tokens += consumed
        index += consumed

    if not variants or matched_tokens < max(2, len(tokens) - 1):
        return set()

    return {variant for variant in variants if variant}


def _spelled_token_to_letters(tokens: List[str], index: int) -> Tuple[List[str], int]:
    token = tokens[index]

    if len(token) == 1 and token.isalnum():
        return [token], 1

    return [], 1


def _brand_alias_replacements() -> List[Tuple[str, str]]:
    global _normalized_brand_alias_replacements

    if _normalized_brand_alias_replacements is None:
        replacements = set()
        for alias, canonical in BRAND_ALIAS_REPLACEMENTS:
            alias_normalized = _normalize_words(alias)
            canonical_normalized = _normalize_words(canonical)
            if alias_normalized and canonical_normalized:
                replacements.add((alias_normalized, canonical_normalized))

        _normalized_brand_alias_replacements = sorted(
            replacements,
            key=lambda item: len(item[0]),
            reverse=True,
        )

    return _normalized_brand_alias_replacements


def _brand_alias_replacements_for(text: str) -> List[Tuple[str, str]]:
    token_index, compact_index = _brand_alias_indexes()
    candidates: List[Tuple[str, str]] = []
    seen = set()

    for token in _fast_tokens(text):
        for replacement in token_index.get(token, []):
            if replacement not in seen:
                candidates.append(replacement)
                seen.add(replacement)

    for replacement in compact_index.get(text.replace(" ", ""), []):
        if replacement not in seen:
            candidates.append(replacement)
            seen.add(replacement)

    candidates.sort(key=lambda item: len(item[0]), reverse=True)
    return candidates


def _brand_alias_indexes() -> Tuple[Dict[str, List[Tuple[str, str]]], Dict[str, List[Tuple[str, str]]]]:
    global _brand_alias_replacement_index, _brand_alias_compact_replacement_index

    if _brand_alias_replacement_index is None or _brand_alias_compact_replacement_index is None:
        token_index: Dict[str, List[Tuple[str, str]]] = {}
        compact_index: Dict[str, List[Tuple[str, str]]] = {}

        for replacement in _brand_alias_replacements():
            alias_normalized, _ = replacement
            for token in _fast_tokens(alias_normalized):
                token_index.setdefault(token, []).append(replacement)
            compact_index.setdefault(alias_normalized.replace(" ", ""), []).append(replacement)

        _brand_alias_replacement_index = token_index
        _brand_alias_compact_replacement_index = compact_index

    return _brand_alias_replacement_index, _brand_alias_compact_replacement_index


def _replace_alias_phrase(text: str, alias: str, replacement: str) -> str:
    if text == replacement:
        return text

    if text == alias:
        return replacement

    pattern = rf"(?<!\w){re.escape(alias)}(?!\w)"
    replaced = re.sub(pattern, replacement, text, flags=re.IGNORECASE)
    if replaced != text:
        return _normalize_words(replaced)

    text_compact = text.replace(" ", "")
    alias_compact = alias.replace(" ", "")
    if text_compact == alias_compact:
        return replacement

    return text


def _split_compound_words(value: str) -> str:
    text = str(value)
    text = re.sub(r"(?<=[a-z])(?=[A-Z])", " ", text)
    text = re.sub(r"(?<=[A-Za-z])(?=\d)", " ", text)
    text = re.sub(r"(?<=\d)(?=[A-Za-z])", " ", text)
    return text


def _arabic_to_latin(value: str) -> str:
    return _normalize_words("".join(ARABIC_TO_LATIN_TRANSLITERATION.get(ch, ch) for ch in str(value)))


def _get_catalog(session_id: Optional[str]) -> Optional[Dict[str, object]]:
    _cleanup_expired_catalogs()
    if not _has_text(session_id):
        return None

    catalog = _catalogs.get(str(session_id))
    if catalog:
        catalog["last_seen"] = time.monotonic()

    return catalog


def _cleanup_expired_catalogs() -> None:
    if not _catalogs:
        return

    now = time.monotonic()
    expired_session_ids = [
        session_id
        for session_id, catalog in _catalogs.items()
        if now - float(catalog.get("last_seen", catalog.get("created_at", now))) > APP_CATALOG_TTL_SECONDS
    ]

    for session_id in expired_session_ids:
        _catalogs.pop(session_id, None)


def _prune_oldest_catalogs() -> None:
    overflow = len(_catalogs) - MAX_APP_CATALOG_SESSIONS
    if overflow <= 0:
        return

    oldest_session_ids = sorted(
        _catalogs,
        key=lambda session_id: float(_catalogs[session_id].get("last_seen", 0.0)),
    )

    for session_id in oldest_session_ids[:overflow]:
        _catalogs.pop(session_id, None)


def _dedupe_matches(matches: List[AppMatch]) -> List[AppMatch]:
    by_package_name: Dict[str, AppMatch] = {}

    for match in matches:
        existing = by_package_name.get(match.package_name)
        if existing is None or match.score > existing.score:
            by_package_name[match.package_name] = match

    return list(by_package_name.values())


def _get_value(obj: object, key: str):
    if isinstance(obj, dict):
        return obj.get(key)
    return getattr(obj, key, None)


def _build_catalog_version(entries: List[AppCatalogEntryRecord]) -> str:
    parts = sorted(f"{entry.package_name}:{entry.label}" for entry in entries)
    return f"{len(entries)}-{abs(hash(tuple(parts))):x}"


def _normalize_words(value: str) -> str:
    text = str(value).casefold().replace("\u0131", "i").replace("\u0130", "i")
    text = unicodedata.normalize("NFKD", text)
    text = "".join(ch for ch in text if not unicodedata.combining(ch))
    text = re.sub(r"[^\w\s]", " ", text, flags=re.UNICODE)
    text = re.sub(r"_+", " ", text)
    return re.sub(r"\s+", " ", text).strip()


def _tokens(value: str) -> set:
    return {token for token in _normalize_words(value).split(" ") if token}


def _fast_tokens(value: str) -> Set[str]:
    return {token for token in str(value).split(" ") if token}


def _ngrams(value: str, size: int = NGRAM_SIZE) -> Set[str]:
    compact = str(value).replace(" ", "")
    if len(compact) < size:
        return {compact} if compact else set()

    return {compact[index:index + size] for index in range(len(compact) - size + 1)}


def _ngram_overlap_ratio(left: str, right: str) -> float:
    left_ngrams = _ngrams(left)
    right_ngrams = _ngrams(right)
    if not left_ngrams or not right_ngrams:
        return 0.0

    return len(left_ngrams.intersection(right_ngrams)) / min(len(left_ngrams), len(right_ngrams))


def _token_overlap(left: str, right: str) -> float:
    left_tokens = _tokens(left)
    right_tokens = _tokens(right)
    if not left_tokens or not right_tokens:
        return 0.0

    return _token_overlap_from_sets(left_tokens, right_tokens)


def _token_overlap_from_sets(left_tokens: Set[str], right_tokens: Set[str]) -> float:
    if not left_tokens or not right_tokens:
        return 0.0

    overlap = len(left_tokens.intersection(right_tokens))
    return (2.0 * overlap) / (len(left_tokens) + len(right_tokens))


def _should_compare_edit_distance(left: str, right: str, ngram_overlap: float) -> bool:
    max_length = max(len(left), len(right))
    if max_length <= 4:
        return True

    if abs(len(left) - len(right)) > max(2, int(max_length * 0.35)):
        return False

    return left[0] == right[0] or ngram_overlap >= MIN_NGRAM_MATCH_RATIO


def _minimum_score(candidate_compact: str) -> float:
    length = len(candidate_compact)
    if length <= 4:
        return 0.96
    if length <= 7:
        return 0.86
    return 0.74


def _length_penalty(left: str, right: str) -> float:
    return min(0.12, abs(len(left) - len(right)) * 0.01)


def _levenshtein_similarity(left: str, right: str) -> float:
    if not left or not right:
        return 0.0
    if left == right:
        return 1.0

    distance = _levenshtein_distance(left, right)
    return 1.0 - (distance / max(len(left), len(right)))


def _levenshtein_distance(left: str, right: str) -> int:
    previous = list(range(len(right) + 1))
    current = [0] * (len(right) + 1)

    for i, left_char in enumerate(left, start=1):
        current[0] = i
        for j, right_char in enumerate(right, start=1):
            cost = 0 if left_char == right_char else 1
            current[j] = min(
                current[j - 1] + 1,
                previous[j] + 1,
                previous[j - 1] + cost,
            )
        previous, current = current, previous

    return previous[len(right)]


def _has_text(value: object) -> bool:
    return value is not None and str(value).strip() != ""
