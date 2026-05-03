import re
import time
import unicodedata
from dataclasses import dataclass
from typing import Dict, Iterable, List, Optional, Set, Tuple

APP_CATALOG_TTL_SECONDS = 2 * 60 * 60
MAX_APP_CATALOG_SESSIONS = 64
AMBIGUOUS_SCORE_MARGIN = 0.03
MAX_AMBIGUOUS_MATCHES = 5
MAX_SUGGESTED_MATCHES = 5
SUGGESTION_MIN_SCORE = 0.35

BRAND_ALIAS_GROUPS = [
    ("youtube", "you tube", "يوتيوب", "يوتوب", "يوتيب"),
    ("instagram", "insta", "انستغرام", "انستقرام", "انستا"),
    ("whatsapp", "whats app", "واتساب", "واتس اب", "واتسآب"),
    ("telegram", "تلجرام", "تليجرام", "تيليجرام"),
    ("tiktok", "tik tok", "تيك توك", "تيكتوك"),
    ("facebook", "fb", "فيسبوك", "فيس بوك"),
    ("messenger", "ماسنجر", "مسنجر"),
    ("chrome", "google chrome", "كروم", "جوجل كروم", "غوغل كروم"),
    ("gmail", "جي ميل", "جيميل"),
    ("maps", "google maps", "خرائط", "خرائط جوجل", "خرائط غوغل"),
    ("snapchat", "snap chat", "سناب شات", "سناب"),
    ("spotify", "سبوتيفاي"),
    ("netflix", "نتفليكس"),
    ("twitter", "x", "تويتر", "اكس"),
    ("microsoft", "مايكروسوفت"),
    ("swiftkey", "swift key", "سويفت كي", "سويفتكي"),
    ("keyboard", "كيبورد", "لوحة المفاتيح"),
    ("turk telekom", "türk telekom", "turk telecom", "turkish telecom", "تورك تيليكوم", "ترك تيليكوم"),
    ("flowq", "flow q", "فلو كيو", "فلوكيو"),
    ("fanytel", "fany tel", "فانيتل", "فاني تل", "فينيتل"),
    ("indeed", "انديد"),
    ("job search", "jobs", "وظائف", "بحث وظائف"),
]

BRAND_ALIAS_REPLACEMENTS: List[Tuple[str, str]] = []
for group in BRAND_ALIAS_GROUPS:
    canonical = _canonical = group[0]
    for alias in group:
        BRAND_ALIAS_REPLACEMENTS.append((alias, _canonical))


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


def save_app_catalog(session_id: str, catalog_version: Optional[str], apps: Iterable[object]) -> Dict[str, object]:
    _cleanup_expired_catalogs()

    entries: List[AppCatalogEntryRecord] = []

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
            match_aliases=_build_match_aliases(label_text, package_text, alias_texts),
        ))

    version = catalog_version or _build_catalog_version(entries)
    now = time.monotonic()
    _catalogs[session_id] = {
        "catalog_version": version,
        "apps": entries,
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

    for app in catalog["apps"]:
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


def _build_match_aliases(label: str, package_name: str, aliases: List[str]) -> List[str]:
    raw_aliases: Set[str] = set()

    for raw_alias in [label, *aliases]:
        if _has_text(raw_alias):
            raw_aliases.add(str(raw_alias))
            raw_aliases.add(_split_compound_words(str(raw_alias)))

    raw_aliases.update(_package_aliases(package_name))

    expanded: Set[str] = set()
    for raw_alias in raw_aliases:
        for normalized, _ in _expand_text_variants(raw_alias):
            if normalized:
                expanded.add(normalized)
                expanded.add(normalized.replace(" ", ""))

    return sorted(expanded)


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

    for candidate_normalized, candidate_compact in candidate_variants:
        if not candidate_compact:
            continue

        for alias_normalized in app.match_aliases:
            alias_compact = alias_normalized.replace(" ", "")
            if not alias_compact:
                continue

            if candidate_compact == alias_compact:
                score = max(score, 1.0)

            if candidate_normalized and candidate_normalized in _tokens(alias_normalized):
                score = max(score, 0.96)

            if alias_compact.startswith(candidate_compact) or candidate_compact.startswith(alias_compact):
                score = max(score, 0.90 - _length_penalty(candidate_compact, alias_compact))

            if candidate_compact in alias_compact or alias_compact in candidate_compact:
                score = max(score, 0.84 - _length_penalty(candidate_compact, alias_compact))

            score = max(score, _levenshtein_similarity(candidate_compact, alias_compact))
            score = max(score, _token_overlap(candidate_normalized, alias_normalized) * 0.92)

    return score


def _expand_text_variants(value: str) -> List[Tuple[str, str]]:
    variants: Set[str] = set()

    for raw_value in [str(value), _split_compound_words(str(value))]:
        normalized = _normalize_words(raw_value)
        if not normalized:
            continue

        variants.add(normalized)
        variants.add(normalized.replace(" ", ""))

        transliterated = _arabic_to_latin(normalized)
        if transliterated != normalized:
            variants.add(_normalize_words(transliterated))

        for expanded in _brand_expanded_texts(normalized):
            variants.add(expanded)
            variants.add(expanded.replace(" ", ""))

        for expanded in _brand_expanded_texts(transliterated):
            variants.add(expanded)
            variants.add(expanded.replace(" ", ""))

    return [
        (variant, variant.replace(" ", ""))
        for variant in sorted(variants)
        if variant.replace(" ", "")
    ]


def _brand_expanded_texts(text: str) -> Set[str]:
    variants = {_normalize_words(text)}
    replacements = sorted(
        BRAND_ALIAS_REPLACEMENTS,
        key=lambda item: len(_normalize_words(item[0])),
        reverse=True,
    )

    for _ in range(3):
        for variant in list(variants):
            for alias, canonical in replacements:
                alias_normalized = _normalize_words(alias)
                canonical_normalized = _normalize_words(canonical)
                if not alias_normalized or not canonical_normalized:
                    continue

                replaced = _replace_alias_phrase(variant, alias_normalized, canonical_normalized)
                if replaced != variant:
                    variants.add(replaced)

    return {variant for variant in variants if variant}


def _replace_alias_phrase(text: str, alias: str, replacement: str) -> str:
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
    transliteration = {
        "ا": "a",
        "أ": "a",
        "إ": "i",
        "آ": "a",
        "ب": "b",
        "ت": "t",
        "ث": "th",
        "ج": "j",
        "ح": "h",
        "خ": "kh",
        "د": "d",
        "ذ": "th",
        "ر": "r",
        "ز": "z",
        "س": "s",
        "ش": "sh",
        "ص": "s",
        "ض": "d",
        "ط": "t",
        "ظ": "z",
        "ع": "a",
        "غ": "gh",
        "ف": "f",
        "ق": "q",
        "ك": "k",
        "ل": "l",
        "م": "m",
        "ن": "n",
        "ه": "h",
        "ة": "h",
        "و": "w",
        "ؤ": "w",
        "ي": "y",
        "ى": "a",
        "ئ": "y",
    }
    return _normalize_words("".join(transliteration.get(ch, ch) for ch in str(value)))


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


def _token_overlap(left: str, right: str) -> float:
    left_tokens = _tokens(left)
    right_tokens = _tokens(right)
    if not left_tokens or not right_tokens:
        return 0.0

    overlap = len(left_tokens.intersection(right_tokens))
    return (2.0 * overlap) / (len(left_tokens) + len(right_tokens))


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
