import re
from typing import Iterable

from V3.utils.language import LanguagePatterns, patterns_for_language
from V3.utils.text import normalized_lower


def matches_any(text: str, patterns: Iterable[str]) -> bool:
    return any(contains_phrase(text, pattern) for pattern in patterns)


def matches_regex(text: str, patterns: Iterable[str]) -> bool:
    return any(re.search(pattern, text) for pattern in patterns)


def matches_language_any(text: str, language: str, patterns: LanguagePatterns) -> bool:
    normalized_text = normalized_lower(text)
    normalized_patterns = [
        normalized_lower(pattern)
        for pattern in patterns_for_language(patterns, language)
    ]
    return matches_any(normalized_text, normalized_patterns)


def matches_language_regex(text: str, language: str, patterns: LanguagePatterns) -> bool:
    return matches_regex(normalized_lower(text), patterns_for_language(patterns, language))


def contains_phrase(text: str, phrase: str) -> bool:
    pattern = rf"(?<!\w){re.escape(phrase)}(?!\w)"
    return re.search(pattern, text, flags=re.IGNORECASE) is not None
