import re
from typing import List, Set, Tuple

from V3.resources.latin_arabic_transliteration import (
    LATIN_LETTER_ARABIC_NAMES,
    LATIN_TO_ARABIC_CHAR_MAP,
    LATIN_TO_ARABIC_CHUNK_MAP,
    LATIN_TO_ARABIC_CHUNK_VARIANTS,
)
from V3.app_catalog.constants import (
    MAX_ARABIC_PHONETIC_ALIASES,
    MAX_ARABIC_PHONETIC_TOKEN_VARIANTS,
    MAX_ARABIC_PHONETIC_TOKENS,
)
from V3.app_catalog.text_normalization import (
    _dedupe_preserve_order,
    _normalize_words,
    _split_compound_words,
)


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
