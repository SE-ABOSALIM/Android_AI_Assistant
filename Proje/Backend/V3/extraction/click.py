import re
from typing import Optional

from V3.extraction.common import clean_free_text, extract_first_match
from V3.utils.language import patterns_for_language
from V3.utils.text import normalize_text, normalized_lower
from V3.patterns.extraction.click import (
    CLICK_ORDINAL_ONLY_TARGET_PATTERNS,
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
    cleaned_target = clean_free_text(_clean_click_target(target, language))
    if cleaned_target:
        return cleaned_target

    if str(language or "").upper().startswith("TR"):
        return _extract_turkish_merged_click_target(normalized)

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
    words = cleaned.split()
    if not words:
        return None

    if _is_ordinal_only_target(" ".join(words), language):
        return None

    if str(language or "").upper().startswith("TR"):
        words[-1] = _strip_turkish_click_suffix(words[-1])

    cleaned_target = " ".join(words)
    if _is_ordinal_only_target(cleaned_target, language):
        return None
    return normalize_text(cleaned_target)


def _strip_turkish_click_suffix(word: str) -> str:
    for suffix in ("yla", "yle", "ya", "ye", "na", "ne", "yi", "yu", "nu", "ni", "a", "e"):
        if len(word) > len(suffix) + 2 and word.endswith(suffix):
            return word[: -len(suffix)]
    return word


def _extract_turkish_merged_click_target(normalized_text: str) -> Optional[str]:
    words = normalized_text.split()
    if not words:
        return None

    if len(words) >= 2 and _is_turkish_stt_sebas_click_word(words[-1]):
        target_words = words[:-1]
        if not target_words:
            return None
        target_words[-1] = _strip_turkish_click_suffix(target_words[-1])
        return normalize_text(" ".join(target_words))

    repaired_word = _repair_turkish_merged_click_word(words[-1])
    if not repaired_word:
        return None

    if len(words) == 1:
        return normalize_text(repaired_word)

    words[-1] = repaired_word
    return normalize_text(" ".join(words))


def _repair_turkish_merged_click_word(word: str) -> Optional[str]:
    if word == "kapatamaz":
        return "kapat"

    for suffix, repaired_ending in (
        ("yamaz", ""),
        ("yemez", ""),
        ("amaz", ""),
        ("emez", ""),
    ):
        if len(word) > len(suffix) + 2 and word.endswith(suffix):
            return word[: -len(suffix)] + repaired_ending
    return None


def _is_turkish_stt_sebas_click_word(word: str) -> bool:
    return word in {"sebas", "sebasin"}


def _remove_click_target_noise(cleaned: str, language: str) -> str:
    patterns = CLICK_TARGET_TRAILING_NOISE_PATTERNS.get(str(language or "").upper(), ())
    cleaned = cleaned.strip()
    for pattern in patterns:
        cleaned = re.sub(pattern, "", cleaned).strip()
    return f" {cleaned} "


def _is_ordinal_only_target(target: str, language: str) -> bool:
    patterns = patterns_for_language(CLICK_ORDINAL_ONLY_TARGET_PATTERNS, language)
    return any(re.fullmatch(pattern, target) for pattern in patterns)
