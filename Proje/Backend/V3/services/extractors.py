import re
from typing import Any, Dict, Optional

from V3.services.text_utils import normalize_text, normalized_lower


def extract_app_name(text: str, language: str) -> Optional[str]:
    """
    Minimum OPEN_APP extractor.

    Gerçek app matching Android tarafında installed apps list ile yapılmalı.
    Burada sadece komuttan app name parçasını ayırıyoruz.
    """

    original = normalize_text(text)
    t = normalized_lower(original)

    patterns = [
        r"^open\s+(.+)$",
        r"^launch\s+(.+)$",
        r"^start\s+(.+)$",
        r"^(.+)\s+open$",
        r"^(.+)\s+ac$",
        r"^ac\s+(.+)$",
    ]

    for pattern in patterns:
        match = re.match(pattern, t, flags=re.IGNORECASE)
        if match:
            app_name = match.group(1).strip()
            if app_name and app_name not in ["app", "application", "uygulama"]:
                return app_name

    arabic_match = re.match(r"^افتح\s+(.+)$", original, flags=re.IGNORECASE)
    if arabic_match:
        app_name = arabic_match.group(1).strip()
        if app_name:
            return app_name

    return None


def extract_timer(text: str) -> Dict[str, Any]:
    """
    EN/TR/AR için timer duration extractor.
    Rakamlı ve yazıyla gelen süreleri yakalar:
    - 5 minutes
    - one hour
    - bir saat
    - خمس دقائق
    """

    original = normalize_text(text)
    t = normalized_lower(original)

    NUMBER_WORDS = {
        # EN
        "one": 1, "two": 2, "three": 3, "four": 4, "five": 5,
        "six": 6, "seven": 7, "eight": 8, "nine": 9, "ten": 10,
        "eleven": 11, "twelve": 12, "thirteen": 13, "fourteen": 14,
        "fifteen": 15, "sixteen": 16, "seventeen": 17, "eighteen": 18,
        "nineteen": 19,

        # TR - normalized_lower removes Turkish accents.
        "bir": 1, "iki": 2, "uc": 3, "dort": 4, "bes": 5,
        "alti": 6, "yedi": 7, "sekiz": 8, "dokuz": 9, "on": 10,

        # AR - normalized_lower removes Arabic combining marks.
        "واحد": 1, "واحدة": 1, "احد": 1, "احدى": 1,
        "اثنان": 2, "اثنين": 2, "اثنتان": 2, "اثنتين": 2,
        "ثلاث": 3, "ثلاثة": 3,
        "اربع": 4, "اربعة": 4,
        "خمس": 5, "خمسة": 5,
        "ست": 6, "ستة": 6,
        "سبع": 7, "سبعة": 7,
        "ثمان": 8, "ثمانية": 8, "ثمانيه": 8,
        "تسع": 9, "تسعة": 9,
        "عشر": 10, "عشرة": 10,
    }

    TENS_WORDS = {
        # EN
        "twenty": 20, "thirty": 30, "forty": 40, "fifty": 50,
        "sixty": 60, "seventy": 70, "eighty": 80, "ninety": 90,

        # TR
        "yirmi": 20, "otuz": 30, "kirk": 40, "elli": 50,
        "altmis": 60, "yetmis": 70, "seksen": 80, "doksan": 90,

        # AR
        "عشرون": 20, "ثلاثون": 30, "اربعون": 40, "خمسون": 50,
        "ستون": 60, "سبعون": 70, "ثمانون": 80, "تسعون": 90,
    }

    SCALE_WORDS = {
        "hundred": 100,
        "yuz": 100,
        "مئة": 100,
        "مائه": 100,
        "مية": 100,
        "مايه": 100,
    }

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

    def build_result(value: int, unit: str) -> Dict[str, Any]:
        return {
            "duration_value": value,
            "duration_unit": unit,
            "duration_seconds": value * SECONDS_MULTIPLIER[unit],
        }

    def normalize_number_token(token: str) -> str:
        token = token.lower().strip()
        known = token in NUMBER_WORDS or token in TENS_WORDS or token in SCALE_WORDS

        if not known and token.startswith("و") and len(token) > 1:
            without_conjunction = token[1:]
            if (
                without_conjunction in NUMBER_WORDS
                or without_conjunction in TENS_WORDS
                or without_conjunction in SCALE_WORDS
            ):
                return without_conjunction

        return token

    def words_to_number(tokens):
        if not tokens:
            return None

        total = 0
        current = 0
        seen_number = False

        for raw_token in tokens:
            token = normalize_number_token(raw_token)

            if token in ["and", "ve", "و"]:
                continue

            if token in ["a", "an"] and len(tokens) == 1:
                current += 1
                seen_number = True
            elif token in NUMBER_WORDS:
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

        value = total + current
        if seen_number and value > 0:
            return value

        return None

    def unit_candidates(token: str):
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

    def unit_from_token(token: str):
        for candidate in unit_candidates(token):
            unit = UNIT_MAP.get(candidate)
            if unit:
                return candidate, unit

        return token, None

    # 1) Digit pattern: 5 minutes / 10 dakika / 3 دقائق
    digit_pattern = r"(\d+)[\s-]*([a-zA-ZğüşöçıİĞÜŞÖÇأ-ي]+)"

    for match in re.finditer(digit_pattern, t):
        value = int(match.group(1))
        raw_unit = match.group(2).strip().lower()
        _, unit = unit_from_token(raw_unit)

        if unit:
            return build_result(value, unit)

    # 2) Word number pattern: one hour / twenty five minutes / yirmi bes dakika / خمس دقائق
    tokens = re.findall(r"[a-zA-ZğüşöçıİĞÜŞÖÇأ-ي]+", t)

    for i, raw_unit in enumerate(tokens):
        normalized_unit, unit = unit_from_token(raw_unit)
        if not unit:
            continue

        start_index = max(0, i - 5)
        for start in range(start_index, i):
            value = words_to_number(tokens[start:i])
            if value is not None:
                return build_result(value, unit)

        implicit_value = IMPLICIT_UNIT_VALUES.get(normalized_unit)
        if implicit_value:
            return build_result(implicit_value, unit)

    return {}
