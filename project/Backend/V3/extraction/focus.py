from typing import Optional

from V3.extraction.common import clean_free_text, extract_first_match
from V3.patterns.extraction.focus import FOCUS_TARGET_PATTERNS, GENERIC_FOCUS_TARGETS
from V3.utils.language import language_key, patterns_for_language
from V3.utils.text import normalize_text, normalized_lower


def extract_focus_target(text: str, language: str) -> Optional[str]:
    normalized = normalized_lower(normalize_text(text))
    target = extract_first_match(
        normalized,
        patterns_for_language(FOCUS_TARGET_PATTERNS, language),
    )
    target = clean_free_text(target)
    if not target:
        return None

    cleaned = _strip_focus_grammar(target, language)
    if not cleaned or _is_generic_focus_target(cleaned, language):
        return None
    return normalize_text(cleaned)


def _strip_focus_grammar(target: str, language: str) -> str:
    cleaned = normalized_lower(target)
    key = language_key(language)
    if key == "EN":
        for prefix in ("the ", "a "):
            if cleaned.startswith(prefix):
                cleaned = cleaned[len(prefix):]
        for prefix in ("input ", "field "):
            if cleaned.startswith(prefix):
                cleaned = cleaned[len(prefix):]
        for suffix in (" input", " field"):
            if cleaned.endswith(suffix):
                cleaned = cleaned[:-len(suffix)]
    return cleaned.strip()


def _is_generic_focus_target(target: str, language: str) -> bool:
    return normalized_lower(target) in GENERIC_FOCUS_TARGETS.get(language_key(language), set())
