import re
from typing import Any, Dict, Tuple

from V3.extraction.common import clean_app_name
from V3.utils.language import language_key
from V3.utils.text import normalized_lower


AMBIGUOUS_TURKISH_SEARCH_CALL_INTENTS = {"CALL_CONTACT", "SEARCH_QUERY"}
LONG_QUERY_WORD_LIMIT = 5

SEARCH_QUERY_HINTS = {
    "altin",
    "dolar",
    "euro",
    "fiyat",
    "film",
    "fragman",
    "haber",
    "hava",
    "kac",
    "kaca",
    "muzik",
    "nasil",
    "ne",
    "nedir",
    "nerede",
    "oyun",
    "sarki",
    "skor",
    "sonuc",
    "tarif",
    "video",
}

SEARCH_QUERY_PHRASES = {
    "hava durumu",
    "ne demek",
    "namaz vakti",
}


def resolve_turkish_search_call_conflict(
    original_text: str,
    language: str,
    intent: str,
    parameters: Dict[str, Any],
    has_search_input: bool,
) -> Tuple[str, Dict[str, Any]]:
    if language_key(language) != "TR" or intent not in AMBIGUOUS_TURKISH_SEARCH_CALL_INTENTS:
        return intent, parameters

    subject = _extract_turkish_ara_subject(original_text)
    if not subject:
        return intent, parameters

    resolved_intent = _resolve_subject(subject, has_search_input)
    if resolved_intent == intent:
        return intent, parameters

    return resolved_intent, {}


def _resolve_subject(subject: str, has_search_input: bool) -> str:
    normalized_subject = normalized_lower(subject)
    word_count = len(_tokens(normalized_subject))

    if _has_search_query_hint(normalized_subject):
        return "SEARCH_QUERY"

    if word_count >= LONG_QUERY_WORD_LIMIT:
        return "SEARCH_QUERY"

    if word_count == 1:
        return "CALL_CONTACT"

    if has_search_input:
        return "SEARCH_QUERY"

    return "CALL_CONTACT"


def _extract_turkish_ara_subject(text: str) -> str:
    normalized = normalized_lower(text)
    match = re.match(r"^(.+?)\s+(?:icin\s+)?ara$", normalized)
    if not match:
        return ""

    return clean_app_name(match.group(1)).strip()


def _has_search_query_hint(normalized_subject: str) -> bool:
    padded = f" {normalized_subject} "
    for phrase in SEARCH_QUERY_PHRASES:
        if f" {phrase} " in padded:
            return True

    return any(token in SEARCH_QUERY_HINTS for token in _tokens(normalized_subject))


def _tokens(value: str) -> list[str]:
    return re.findall(r"[a-z0-9]+", value)
