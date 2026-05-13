import re
from typing import Iterable, Optional

from V3.patterns.commands.app import APP_SUFFIX_SEPARATORS
from V3.utils.text import normalize_text, normalized_lower


def clean_app_name(app_name: str) -> str:
    app_name = app_name.strip()

    for separator in APP_SUFFIX_SEPARATORS:
        if separator in app_name:
            stem, suffix = app_name.split(separator, 1)
            if is_turkish_case_suffix(suffix):
                return stem.strip()

    return app_name


def extract_first_match(text: str, patterns: Iterable[str], ignore_case: bool = False) -> Optional[str]:
    flags = re.IGNORECASE if ignore_case else 0
    for pattern in patterns:
        match = re.match(pattern, text, flags=flags)
        if match:
            return match.group(1).strip()
    return None


def extract_quoted_text(text: str) -> Optional[str]:
    match = re.search(r'"([^"]+)"', text)
    if match:
        return clean_free_text(match.group(1))
    return None


def clean_free_text(value: Optional[str]) -> Optional[str]:
    if value is None:
        return None

    cleaned = normalize_text(value).strip("\"' ")
    return cleaned or None


def is_turkish_case_suffix(suffix: str) -> bool:
    suffix = normalized_lower(suffix).strip()
    return suffix in {
        "i",
        "u",
        "yi",
        "yu",
        "ni",
        "nu",
        "de",
        "da",
        "den",
        "dan",
        "e",
        "a",
        "ye",
        "ya",
    }
