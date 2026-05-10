import re
from typing import Optional

from V3.patterns.commands.contact import ARABIC_CALL_PATTERNS, CALL_CONTACT_PATTERNS
from V3.services.extraction.common import clean_app_name
from V3.services.text_utils import normalize_text, normalized_lower


def extract_contact_name(text: str, language: str) -> Optional[str]:
    original = normalize_text(text)
    normalized = normalized_lower(original)

    for pattern in CALL_CONTACT_PATTERNS:
        match = re.match(pattern, normalized, flags=re.IGNORECASE)
        if match:
            contact_name = _clean_contact_name(match.group(1))
            if contact_name:
                return contact_name

    for pattern in ARABIC_CALL_PATTERNS:
        match = re.match(pattern, original, flags=re.IGNORECASE)
        if match:
            contact_name = _clean_contact_name(match.group(1))
            if contact_name:
                return contact_name

    return None


def _clean_contact_name(contact_name: str) -> str:
    contact_name = clean_app_name(contact_name)
    normalized = normalized_lower(contact_name)

    if " " not in normalized:
        for suffix in ("yi", "yu", "ni", "nu"):
            if len(normalized) > len(suffix) + 2 and normalized.endswith(suffix):
                return contact_name[: -len(suffix)].strip()

        if len(normalized) > 5 and normalized.endswith(("i", "u")):
            return contact_name[:-1].strip()

    return contact_name
