import re
from typing import Optional

from V3.extraction.common import clean_free_text, extract_first_match, extract_quoted_text
from V3.patterns.extraction.text import (
    SEARCH_QUERY_PATTERNS,
    TRAILING_SEARCH_QUERY_NOISE_PATTERN,
    WRITE_TEXT_PATTERNS,
)
from V3.utils.language import patterns_for_language
from V3.utils.text import normalize_text, normalized_lower


def extract_search_query(text: str, language: str) -> Optional[str]:
    normalized = normalized_lower(normalize_text(text))

    query = extract_first_match(
        normalized,
        patterns_for_language(SEARCH_QUERY_PATTERNS, language)
    )
    return clean_search_query(query, language)


def extract_write_text(text: str, language: str) -> Optional[str]:
    original = normalize_text(text)
    quoted = extract_quoted_text(original)
    if quoted:
        return quoted

    command_text = extract_first_match(
        original,
        patterns_for_language(WRITE_TEXT_PATTERNS, language),
        ignore_case=True,
    )
    return clean_free_text(command_text or original)


def extract_alarm_text(text: str, language: str) -> Optional[str]:
    return clean_free_text(text)


def clean_search_query(query: Optional[str], language: str) -> Optional[str]:
    cleaned = clean_free_text(query)
    if not cleaned:
        return None

    if str(language or "").upper() == "TR":
        cleaned = re.sub(TRAILING_SEARCH_QUERY_NOISE_PATTERN, "", cleaned, flags=re.IGNORECASE).strip()

    return clean_free_text(cleaned)
