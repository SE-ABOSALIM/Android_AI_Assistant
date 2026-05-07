import re
from typing import Dict, List, Optional, Set, Tuple

from V3.resources.app_aliases import BRAND_ALIAS_GROUPS
from V3.resources.transliteration import ARABIC_TO_LATIN_TRANSLITERATION
from V3.services.app_catalog.text_utils import _fast_tokens, _normalize_words, _split_compound_words


BRAND_ALIAS_REPLACEMENTS: List[Tuple[str, str]] = []
for group in BRAND_ALIAS_GROUPS:
    canonical = group[0]
    for alias in group:
        BRAND_ALIAS_REPLACEMENTS.append((alias, canonical))
_normalized_brand_alias_replacements: Optional[List[Tuple[str, str]]] = None
_brand_alias_replacement_index: Optional[Dict[str, List[Tuple[str, str]]]] = None
_brand_alias_compact_replacement_index: Optional[Dict[str, List[Tuple[str, str]]]] = None


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

def _arabic_to_latin(value: str) -> str:
    return _normalize_words("".join(ARABIC_TO_LATIN_TRANSLITERATION.get(ch, ch) for ch in str(value)))
