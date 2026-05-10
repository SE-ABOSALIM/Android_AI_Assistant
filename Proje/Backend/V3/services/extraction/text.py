from typing import Optional

from V3.services.extraction.common import clean_free_text, extract_first_match, extract_quoted_text
from V3.services.text_utils import normalize_text, normalized_lower


def extract_search_query(text: str, language: str) -> Optional[str]:
    normalized = normalized_lower(normalize_text(text))

    query = extract_first_match(normalized, [
        r"^(?:search for|look up)\s+(.+)$",
        r"^(.+?)\s+(?:icin\s+)?ara$",
    ])
    return clean_free_text(query)


def extract_write_text(text: str, language: str) -> Optional[str]:
    original = normalize_text(text)
    quoted = extract_quoted_text(original)
    if quoted:
        return quoted

    command_text = extract_first_match(original, [
        r"^(?:write|type)\s+(.+)$",
        r"^(.+?)\s+(?:metnini\s+)?yaz$",
    ], ignore_case=True)
    return clean_free_text(command_text or original)


def extract_click_target(text: str, language: str) -> Optional[str]:
    normalized = normalized_lower(normalize_text(text))

    target = extract_first_match(normalized, [
        r"^(?:tap|click|press)\s+(.+)$",
        r"^(.+?)\s+tikla$",
        r"^(.+?)\s+tiklayin$",
    ])
    return clean_free_text(target)


def extract_alarm_text(text: str, language: str) -> Optional[str]:
    return clean_free_text(text)
