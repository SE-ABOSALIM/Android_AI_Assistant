import re
from typing import Optional

from V3.patterns.commands.app import OPEN_APP_PATTERNS, REJECT_APP_NAMES
from V3.services.extraction.common import clean_app_name, extract_first_match
from V3.services.language_patterns import language_key, patterns_for_language
from V3.services.text_utils import normalize_text, normalized_lower


def extract_app_name(text: str, language: str) -> Optional[str]:
    original = normalize_text(text)
    normalized = normalized_lower(original)
    match_text = original if language_key(language) == "AR" else normalized

    for pattern in patterns_for_language(OPEN_APP_PATTERNS, language):
        match = re.match(pattern, match_text, flags=re.IGNORECASE)
        if match:
            app_name = clean_app_name(match.group(1))
            if app_name and normalized_lower(app_name) not in REJECT_APP_NAMES:
                return app_name

    return None


def extract_app_name_for_intent(text: str, language: str, intent: str) -> Optional[str]:
    if intent == "OPEN_APP":
        return extract_app_name(text, language)

    normalized = normalized_lower(normalize_text(text))

    if intent == "OPEN_APP_INFO":
        app_name = extract_first_match(normalized, [
            r"^open app info for\s+(.+)$",
            r"^open\s+(.+?)\s+app settings$",
            r"^show\s+(.+?)\s+app info$",
            r"^(.+?)\s+hakkinda sayfasini ac$",
            r"^(.+?)\s+uygulama bilgilerini ac$",
            r"^(.+?)\s+uygulama detaylarini goster$",
        ])
        return _valid_app_name(app_name)

    if intent == "UNINSTALL_APP":
        app_name = extract_first_match(normalized, [
            r"^(?:delete|remove|uninstall)\s+(.+)$",
            r"^(.+?)\s+(?:sil|uygulamasini kaldir|uygulamasini sil)$",
        ])
        return _valid_app_name(app_name)

    return None


def _valid_app_name(app_name: Optional[str]) -> Optional[str]:
    if not app_name:
        return None

    app_name = clean_app_name(app_name)
    if app_name and normalized_lower(app_name) not in REJECT_APP_NAMES:
        return app_name
    return None
