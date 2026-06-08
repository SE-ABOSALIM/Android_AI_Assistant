import re
from typing import Optional

from V3.extraction.common import clean_free_text, extract_first_match
from V3.utils.language import patterns_for_language
from V3.utils.text import normalize_text, normalized_lower
from V3.patterns.extraction.click import (
    ARABIC_INDIC_DIGITS,
    ASCII_DIGITS_FOR_ARABIC_INDIC,
    CLICK_INDEX_ALIASES,
    CLICK_NUMBERED_ICON_PATTERNS,
    CLICK_POSITION_ALIASES,
    CLICK_TARGET_PATTERNS,
    CLICK_TARGET_TRAILING_NOISE_PATTERNS,
)

def extract_click_target(text: str, language: str) -> Optional[str]:
    normalized = normalized_lower(normalize_text(text))

    target = extract_first_match(
        normalized,
        patterns_for_language(CLICK_TARGET_PATTERNS, language)
    )
    return clean_free_text(_clean_click_target(target, language))


def extract_click_index(text: str) -> Optional[int]:
    normalized = normalized_lower(normalize_text(_translate_arabic_indic_digits(text)))
    if _contains_numbered_icon_phrase(normalized):
        return None

    digit_match = re.search(r"(?<!\d)(10|[1-9])(?:\.|inci|uncu|nci|rd|nd|st|th)?(?!\d)", normalized)
    if digit_match:
        return int(digit_match.group(1))

    padded = f" {normalized} "
    for index, aliases in CLICK_INDEX_ALIASES.items():
        for alias in aliases:
            normalized_alias = normalized_lower(alias)
            if re.search(rf"(?<!\w){re.escape(normalized_alias)}\w*(?!\w)", padded):
                return index
    return None


def extract_click_position(text: str, language: str = "") -> Optional[str]:
    normalized = normalized_lower(normalize_text(text))
    if str(language or "").upper() == "EN" and normalized.startswith("top "):
        return None

    padded = f" {normalized} "
    for position, aliases in CLICK_POSITION_ALIASES.items():
        for alias in aliases:
            normalized_alias = normalized_lower(alias)
            if f" {normalized_alias} " in padded:
                return position
    return None


def _clean_click_target(target: Optional[str], language: str) -> Optional[str]:
    if not target:
        return None

    cleaned = f" {normalized_lower(target)} "
    cleaned = _remove_click_target_noise(cleaned, language)
    for aliases in CLICK_POSITION_ALIASES.values():
        for alias in aliases:
            normalized_alias = normalized_lower(alias)
            cleaned = cleaned.replace(f" {normalized_alias} ", " ")
    for aliases in CLICK_INDEX_ALIASES.values():
        for alias in aliases:
            normalized_alias = normalized_lower(alias)
            cleaned = re.sub(rf"(?<!\w){re.escape(normalized_alias)}\w*(?!\w)", " ", cleaned)

    words = cleaned.split()
    if not words:
        return None

    if str(language or "").upper().startswith("TR"):
        words[-1] = _strip_turkish_click_suffix(words[-1])
    return normalize_text(" ".join(words))


def _strip_turkish_click_suffix(word: str) -> str:
    for suffix in ("yla", "yle", "ya", "ye", "yi", "yu", "nu", "ni", "a", "e"):
        if len(word) > len(suffix) + 2 and word.endswith(suffix):
            return word[: -len(suffix)]
    return word


def _contains_numbered_icon_phrase(normalized_text: str) -> bool:
    return any(re.search(pattern, normalized_text) for pattern in CLICK_NUMBERED_ICON_PATTERNS)


def _remove_click_target_noise(cleaned: str, language: str) -> str:
    patterns = CLICK_TARGET_TRAILING_NOISE_PATTERNS.get(str(language or "").upper(), ())
    cleaned = cleaned.strip()
    for pattern in patterns:
        cleaned = re.sub(pattern, "", cleaned).strip()
    return f" {cleaned} "


def _translate_arabic_indic_digits(text: str) -> str:
    return str(text).translate(str.maketrans(ARABIC_INDIC_DIGITS, ASCII_DIGITS_FOR_ARABIC_INDIC))
