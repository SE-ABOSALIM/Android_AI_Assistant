import re
from typing import Optional

from V3.patterns.commands.contact import CALL_CONTACT_PATTERNS
from V3.extraction.common import clean_app_name
from V3.utils.language import language_key, patterns_for_language
from V3.utils.text import normalize_text, normalized_lower


def extract_contact_name(text: str, language: str) -> Optional[str]:
    original = normalize_text(text)
    normalized = normalized_lower(original)
    match_text = original if language_key(language) == "AR" else normalized

    for pattern in patterns_for_language(CALL_CONTACT_PATTERNS, language):
        match = re.match(pattern, match_text, flags=re.IGNORECASE)
        if match:
            contact_name = _clean_contact_name(match.group(1))
            if contact_name:
                return contact_name

    return None


def _clean_contact_name(contact_name: str) -> str:
    contact_name = clean_app_name(contact_name)
    normalized = normalized_lower(contact_name)

    if re.search(r"[\u0600-\u06FF]", contact_name):
        contact_name = _clean_arabic_contact_name(contact_name)
        normalized = normalized_lower(contact_name)

    if " " not in normalized:
        for suffix in ("yi", "yu", "ni", "nu"):
            if len(normalized) > len(suffix) + 2 and normalized.endswith(suffix):
                return contact_name[: -len(suffix)].strip()

        if len(normalized) > 5 and normalized.endswith(("i", "u")):
            return contact_name[:-1].strip()

    return contact_name


def _clean_arabic_contact_name(contact_name: str) -> str:
    contact_name = re.sub(r"^[\sـ]+", "", contact_name).strip()

    while True:
        cleaned = re.sub(r"^(?:الان|الآن|على|بـ)\s+", "", contact_name).strip()
        cleaned = re.sub(r"^(?:الان|الآن|على)\s+ب", "", cleaned).strip()
        cleaned = re.sub(r"^ب(?=[\u0621-\u064A])", "", cleaned).strip()
        cleaned = re.sub(r"^[\sـ]+", "", cleaned).strip()

        if cleaned == contact_name:
            return cleaned
        contact_name = cleaned
