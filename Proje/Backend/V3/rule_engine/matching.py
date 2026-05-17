import re
from dataclasses import dataclass
from typing import Iterable, Mapping, Optional

from V3.utils.language import LanguagePatterns, patterns_for_language
from V3.utils.text import normalized_lower


@dataclass(frozen=True)
class GroupMatch:
    matched_key: Optional[str] = None
    ambiguous: bool = False


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


def matched_language_keys(
    text: str,
    language: str,
    grouped_patterns: Mapping[str, LanguagePatterns],
) -> list[str]:
    return [
        key
        for key, patterns in grouped_patterns.items()
        if matches_language_any(text, language, patterns)
    ]


def resolve_group_match(
    text: str,
    language: str,
    grouped_patterns: Mapping[str, LanguagePatterns],
) -> GroupMatch:
    matched_keys = matched_language_keys(text, language, grouped_patterns)
    if len(matched_keys) > 1:
        return GroupMatch(ambiguous=True)
    if matched_keys:
        return GroupMatch(matched_key=matched_keys[0])
    return GroupMatch()


def contains_phrase(text: str, phrase: str) -> bool:
    pattern = rf"(?<!\w){re.escape(phrase)}(?!\w)"
    return re.search(pattern, text, flags=re.IGNORECASE) is not None
