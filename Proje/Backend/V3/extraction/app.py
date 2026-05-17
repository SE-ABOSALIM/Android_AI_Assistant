import re
from typing import Optional

from V3.extraction.common import (
    clean_app_name,
    extract_first_match
)
from V3.utils.language import (
    language_key,
    patterns_for_language
)
from V3.utils.text import (
    normalize_text,
    normalized_lower
)
from V3.patterns.commands.app import (
    OPEN_APP_PATTERNS,
    OPEN_APP_REJECT_PATTERNS,
    REJECT_APP_NAMES
)
from V3.patterns.extraction.app import (
    OPEN_APP_INFO_NAME_PATTERNS,
    UNINSTALL_APP_NAME_PATTERNS
)


def extract_app_name_for_intent(text: str, language: str, intent: str) -> Optional[str]:
    if intent == "OPEN_APP":
        return _extract_open_app_name(text, language)

    original = normalize_text(text)
    normalized = normalized_lower(original)
    match_text = normalized

    if intent == "OPEN_APP_INFO":
        app_name = extract_first_match(
            match_text,
            patterns_for_language(OPEN_APP_INFO_NAME_PATTERNS, language),
        )
        return _valid_app_name(app_name, language)

    if intent == "UNINSTALL_APP":
        app_name = extract_first_match(
            match_text,
            patterns_for_language(UNINSTALL_APP_NAME_PATTERNS, language),
        )
        return _valid_app_name(app_name, language)

    return None


def _extract_open_app_name(text: str, language: str) -> Optional[str]:
    original = normalize_text(text)
    normalized = normalized_lower(original)
    match_text = original if language_key(language) == "AR" else normalized

    if _is_rejected_open_app_text(normalized, language):
        return None

    for pattern in patterns_for_language(OPEN_APP_PATTERNS, language):
        match = re.match(pattern, match_text, flags=re.IGNORECASE)
        if match:
            app_name = clean_app_name(match.group(1))
            if app_name and not _is_rejected_app_name(app_name, language):
                return app_name

    return None


def _valid_app_name(app_name: Optional[str], language: str) -> Optional[str]:
    if not app_name:
        return None

    app_name = clean_app_name(app_name)
    if app_name and not _is_rejected_app_name(app_name, language):
        return app_name
    return None


def _is_rejected_open_app_text(normalized_text: str, language: str) -> bool:
    return any(
        re.search(pattern, normalized_text, flags=re.IGNORECASE)
        for pattern in patterns_for_language(OPEN_APP_REJECT_PATTERNS, language)
    )


def _is_rejected_app_name(app_name: str, language: str) -> bool:
    normalized = normalized_lower(app_name)
    if normalized in REJECT_APP_NAMES:
        return True

    return any(
        re.search(pattern, normalized, flags=re.IGNORECASE)
        for pattern in patterns_for_language(OPEN_APP_REJECT_PATTERNS, language)
    )
