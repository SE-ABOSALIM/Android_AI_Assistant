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
    Minimum timer extractor.
    Şimdilik EN/TR/AR için sayı + birim yakalar.
    """

    original = normalize_text(text)
    t = normalized_lower(original)

    unit_map = {
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
        "saniye": "second",
        "saniyelik": "second",
        "dakika": "minute",
        "dakikalik": "minute",
        "saat": "hour",
        "saatlik": "hour",
    }

    arabic_unit_map = {
        "ثانية": "second",
        "ثواني": "second",
        "دقيقة": "minute",
        "دقائق": "minute",
        "ساعة": "hour",
        "ساعات": "hour",
    }

    # EN/TR pattern
    match = re.search(r"(\d+)\s*([a-zA-ZğüşöçıİĞÜŞÖÇ]+)", t)

    if match:
        value = int(match.group(1))
        raw_unit = match.group(2).strip()
        unit = unit_map.get(raw_unit)

        if unit:
            seconds_multiplier = {
                "second": 1,
                "minute": 60,
                "hour": 3600,
            }

            return {
                "duration_value": value,
                "duration_unit": unit,
                "duration_seconds": value * seconds_multiplier[unit],
            }

    # Arabic pattern
    arabic_match = re.search(r"(\d+)\s*([أ-ي]+)", original)

    if arabic_match:
        value = int(arabic_match.group(1))
        raw_unit = arabic_match.group(2).strip()
        unit = arabic_unit_map.get(raw_unit)

        if unit:
            seconds_multiplier = {
                "second": 1,
                "minute": 60,
                "hour": 3600,
            }

            return {
                "duration_value": value,
                "duration_unit": unit,
                "duration_seconds": value * seconds_multiplier[unit],
            }

    return {}
