import re
from typing import Any, Dict, Iterable, List, Optional

from V3.services.text_utils import normalize_text, normalized_lower


OPEN_APP_PATTERNS = [
    r"^open\s+(.+)$",
    r"^launch\s+(.+)$",
    r"^start\s+(.+)$",
    r"^(.+)\s+open$",
    r"^(.+)\s+ac$",
    r"^ac\s+(.+)$",
]

CALL_CONTACT_PATTERNS = [
    r"^call\s+(.+)$",
    r"^phone\s+(.+)$",
    r"^ring\s+(.+)$",
    r"^(.+)\s+call$",
    r"^(.+)\s+ara$",
]

REJECT_APP_NAMES = {"app", "application", "uygulama"}
ARABIC_CALL_PATTERNS = [
    r"^\u0627\u062a\u0635\u0644\s+\u0628?(.+)$",
]
ARABIC_OPEN_PATTERN = r"^افتح\s+(.+)$"
APP_SUFFIX_SEPARATORS = ("'", "’", "‘", "`", "´")

NUMBER_WORDS = {
    # EN
    "one": 1,
    "two": 2,
    "three": 3,
    "four": 4,
    "five": 5,
    "six": 6,
    "seven": 7,
    "eight": 8,
    "nine": 9,
    "ten": 10,
    "eleven": 11,
    "twelve": 12,
    "thirteen": 13,
    "fourteen": 14,
    "fifteen": 15,
    "sixteen": 16,
    "seventeen": 17,
    "eighteen": 18,
    "nineteen": 19,
    # TR - normalized_lower removes Turkish accents.
    "bir": 1,
    "iki": 2,
    "uc": 3,
    "dort": 4,
    "bes": 5,
    "alti": 6,
    "yedi": 7,
    "sekiz": 8,
    "dokuz": 9,
    "on": 10,
    # AR - normalized_lower removes Arabic combining marks.
    "واحد": 1,
    "واحدة": 1,
    "احد": 1,
    "احدى": 1,
    "اثنان": 2,
    "اثنين": 2,
    "اثنتان": 2,
    "اثنتين": 2,
    "ثلاث": 3,
    "ثلاثة": 3,
    "اربع": 4,
    "اربعة": 4,
    "خمس": 5,
    "خمسة": 5,
    "ست": 6,
    "ستة": 6,
    "سبع": 7,
    "سبعة": 7,
    "ثمان": 8,
    "ثمانية": 8,
    "ثمانيه": 8,
    "تسع": 9,
    "تسعة": 9,
    "عشر": 10,
    "عشرة": 10,
}

TENS_WORDS = {
    # EN
    "twenty": 20,
    "thirty": 30,
    "forty": 40,
    "fifty": 50,
    "sixty": 60,
    "seventy": 70,
    "eighty": 80,
    "ninety": 90,
    # TR
    "yirmi": 20,
    "otuz": 30,
    "kirk": 40,
    "elli": 50,
    "altmis": 60,
    "yetmis": 70,
    "seksen": 80,
    "doksan": 90,
    # AR
    "عشرون": 20,
    "ثلاثون": 30,
    "اربعون": 40,
    "خمسون": 50,
    "ستون": 60,
    "سبعون": 70,
    "ثمانون": 80,
    "تسعون": 90,
}

SCALE_WORDS = {
    "hundred": 100,
    "yuz": 100,
    "مئة": 100,
    "مائه": 100,
    "مية": 100,
    "مايه": 100,
}

HALF_WORDS = {"half", "yarim", "bucuk", "نصف", "نص"}
CONNECTOR_WORDS = {"and", "ve", "و", "a", "an"}

UNIT_MAP = {
    # EN
    "second": "second",
    "seconds": "second",
    "sec": "second",
    "secs": "second",
    "minute": "minute",
    "minutes": "minute",
    "min": "minute",
    "mins": "minute",
    "hour": "hour",
    "hours": "hour",
    # TR
    "sn": "second",
    "saniye": "second",
    "saniyelik": "second",
    "dk": "minute",
    "dakika": "minute",
    "dakikalik": "minute",
    "saat": "hour",
    "saatlik": "hour",
    # AR
    "ثانية": "second",
    "ثانيه": "second",
    "ثانيتين": "second",
    "ثواني": "second",
    "ثوان": "second",
    "دقيقة": "minute",
    "دقيقه": "minute",
    "دقيقتين": "minute",
    "دقائق": "minute",
    "دقايق": "minute",
    "ساعة": "hour",
    "ساعه": "hour",
    "ساعتين": "hour",
    "ساعات": "hour",
}

IMPLICIT_UNIT_VALUES = {
    "ثانية": 1,
    "ثانيه": 1,
    "ثانيتين": 2,
    "دقيقة": 1,
    "دقيقه": 1,
    "دقيقتين": 2,
    "ساعة": 1,
    "ساعه": 1,
    "ساعتين": 2,
}

SECONDS_MULTIPLIER = {
    "second": 1,
    "minute": 60,
    "hour": 3600,
}

DIGIT_DURATION_PATTERN = re.compile(
    r"(?<![\d.,])(\d+(?:[.,]\d+)?)[\s-]*([a-zA-ZğüşöçıİĞÜŞÖÇأ-ي]+)"
)
WORD_TOKEN_PATTERN = re.compile(r"[a-zA-ZğüşöçıİĞÜŞÖÇأ-ي]+")


def extract_app_name(text: str, language: str) -> Optional[str]:
    """
    Minimum OPEN_APP extractor.

    Gerçek app matching Android tarafında installed apps list ile yapılmalı.
    Burada sadece komuttan app name parçasını ayırıyoruz.
    """

    original = normalize_text(text)
    t = normalized_lower(original)

    for pattern in OPEN_APP_PATTERNS:
        match = re.match(pattern, t, flags=re.IGNORECASE)
        if match:
            app_name = _clean_app_name(match.group(1))
            if app_name and app_name not in REJECT_APP_NAMES:
                return app_name

    arabic_match = re.match(ARABIC_OPEN_PATTERN, original, flags=re.IGNORECASE)
    if arabic_match:
        app_name = _clean_app_name(arabic_match.group(1))
        if app_name:
            return app_name

    return None


def extract_contact_name(text: str, language: str) -> Optional[str]:
    original = normalize_text(text)
    t = normalized_lower(original)

    for pattern in CALL_CONTACT_PATTERNS:
        match = re.match(pattern, t, flags=re.IGNORECASE)
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


def _clean_app_name(app_name: str) -> str:
    app_name = app_name.strip()

    for separator in APP_SUFFIX_SEPARATORS:
        if separator in app_name:
            stem, suffix = app_name.split(separator, 1)
            if _is_turkish_case_suffix(suffix):
                return stem.strip()

    return app_name


def _clean_contact_name(contact_name: str) -> str:
    contact_name = _clean_app_name(contact_name)
    normalized = normalized_lower(contact_name)

    if " " not in normalized:
        for suffix in ("yi", "yu", "ni", "nu"):
            if len(normalized) > len(suffix) + 2 and normalized.endswith(suffix):
                return contact_name[: -len(suffix)].strip()

        if len(normalized) > 5 and normalized.endswith(("i", "u")):
            return contact_name[:-1].strip()

    return contact_name


def _is_turkish_case_suffix(suffix: str) -> bool:
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


def extract_timer(text: str) -> Dict[str, Any]:
    """
    EN/TR/AR için timer duration extractor.
    Rakamlı, yazıyla gelen, decimal ve çok parçalı süreleri yakalar.
    """

    original = normalize_text(text)
    t = normalized_lower(original)

    components = _extract_digit_duration_components(t)
    components.extend(_extract_word_duration_components(t, include_implicit=not components))

    if not components:
        return {}

    if len(components) == 1:
        return components[0]["result"]

    total_seconds = sum(component["seconds"] for component in components)
    return _build_result_from_seconds(total_seconds)


def _extract_digit_duration_components(text: str) -> List[Dict[str, Any]]:
    components = []

    for match in DIGIT_DURATION_PATTERN.finditer(text):
        raw_value = match.group(1).replace(",", ".")
        raw_unit = match.group(2).strip().lower()
        _, unit = _unit_from_token(raw_unit)

        if not unit:
            continue

        value = float(raw_value)
        component = _build_component(value, unit)
        if component:
            components.append(component)

    return components


def _extract_word_duration_components(text: str, include_implicit: bool) -> List[Dict[str, Any]]:
    components = []
    tokens = WORD_TOKEN_PATTERN.findall(text)
    previous_unit_index = -1
    consumed_post_unit_half_indexes = set()

    for i, raw_unit in enumerate(tokens):
        normalized_unit, unit = _unit_from_token(raw_unit)
        if not unit:
            continue

        added_component = False
        value = _number_before_unit(tokens, previous_unit_index + 1, i)
        if value is not None:
            component = _build_component(value, unit)
            if component:
                components.append(component)
                added_component = True
            previous_unit_index = i

        elif include_implicit:
            implicit_value = IMPLICIT_UNIT_VALUES.get(normalized_unit)
            if implicit_value:
                component = _build_component(implicit_value, unit)
                if component:
                    components.append(component)
                    added_component = True

        half_index = _post_unit_half_index(tokens, i)
        if half_index is not None and half_index not in consumed_post_unit_half_indexes:
            if added_component or not include_implicit:
                half_component = _build_component(0.5, unit)
                if half_component:
                    components.append(half_component)
                    consumed_post_unit_half_indexes.add(half_index)

        previous_unit_index = i

    return components


def _number_before_unit(tokens: List[str], start_index: int, unit_index: int) -> Optional[float]:
    for start in range(max(start_index, unit_index - 5), unit_index):
        value = _words_to_number(tokens[start:unit_index])
        if value is not None:
            return value

    return None


def _post_unit_half_index(tokens: List[str], unit_index: int) -> Optional[int]:
    for index in range(unit_index + 1, min(len(tokens), unit_index + 5)):
        token = _normalize_number_token(tokens[index])
        if token in CONNECTOR_WORDS:
            continue

        if token in HALF_WORDS and not _next_meaningful_token_is_unit(tokens, index + 1):
            return index

        return None

    return None


def _next_meaningful_token_is_unit(tokens: List[str], start_index: int) -> bool:
    for index in range(start_index, min(len(tokens), start_index + 4)):
        token = _normalize_number_token(tokens[index])
        if token in CONNECTOR_WORDS:
            continue

        _, unit = _unit_from_token(token)
        return unit is not None

    return False


def _build_component(value: float, unit: str) -> Optional[Dict[str, Any]]:
    seconds = int(round(value * SECONDS_MULTIPLIER[unit]))
    if seconds <= 0:
        return None

    if float(value).is_integer():
        result = {
            "duration_value": int(value),
            "duration_unit": unit,
            "duration_seconds": seconds,
        }
    else:
        result = _build_result_from_seconds(seconds)

    return {
        "seconds": seconds,
        "result": result,
    }


def _build_result_from_seconds(seconds: int) -> Dict[str, Any]:
    seconds = int(seconds)

    if seconds % SECONDS_MULTIPLIER["hour"] == 0:
        return {
            "duration_value": seconds // SECONDS_MULTIPLIER["hour"],
            "duration_unit": "hour",
            "duration_seconds": seconds,
        }

    if seconds % SECONDS_MULTIPLIER["minute"] == 0:
        return {
            "duration_value": seconds // SECONDS_MULTIPLIER["minute"],
            "duration_unit": "minute",
            "duration_seconds": seconds,
        }

    return {
        "duration_value": seconds,
        "duration_unit": "second",
        "duration_seconds": seconds,
    }


def _normalize_number_token(token: str) -> str:
    token = token.lower().strip()
    known = token in NUMBER_WORDS or token in TENS_WORDS or token in SCALE_WORDS or token in HALF_WORDS

    if not known and token.startswith("و") and len(token) > 1:
        without_conjunction = token[1:]
        if (
            without_conjunction in NUMBER_WORDS
            or without_conjunction in TENS_WORDS
            or without_conjunction in SCALE_WORDS
            or without_conjunction in HALF_WORDS
        ):
            return without_conjunction

    return token


def _words_to_number(tokens: Iterable[str]) -> Optional[float]:
    total = 0.0
    current = 0.0
    seen_number = False
    seen_half = False

    normalized_tokens = [_normalize_number_token(token) for token in tokens]
    if not normalized_tokens:
        return None
    meaningful_tokens = [token for token in normalized_tokens if token not in {"and", "ve", "و"}]

    for token in normalized_tokens:
        if token in {"a", "an"}:
            if len(meaningful_tokens) == 1:
                current += 1
                seen_number = True
            continue

        if token in CONNECTOR_WORDS:
            continue

        if token in HALF_WORDS:
            seen_half = True
            seen_number = True
            continue

        if token in NUMBER_WORDS:
            current += NUMBER_WORDS[token]
            seen_number = True
        elif token in TENS_WORDS:
            current += TENS_WORDS[token]
            seen_number = True
        elif token in SCALE_WORDS:
            if current == 0:
                current = 1
            current *= SCALE_WORDS[token]
            total += current
            current = 0
            seen_number = True
        else:
            return None

    if seen_half:
        current += 0.5

    value = total + current
    if seen_number and value > 0:
        return value

    return None


def _unit_candidates(token: str) -> List[str]:
    token = token.lower().strip()
    candidates = [token]

    if token.startswith("لل") and len(token) > 2:
        candidates.append(token[2:])
    if token.startswith("بال") and len(token) > 3:
        candidates.append(token[3:])
    if token.startswith(("ل", "ب")) and len(token) > 1:
        candidates.append(token[1:])

    for candidate in list(candidates):
        if candidate.startswith("ال") and len(candidate) > 2:
            candidates.append(candidate[2:])

    return candidates


def _unit_from_token(token: str):
    for candidate in _unit_candidates(token):
        unit = UNIT_MAP.get(candidate)
        if unit:
            return candidate, unit

    return token, None
