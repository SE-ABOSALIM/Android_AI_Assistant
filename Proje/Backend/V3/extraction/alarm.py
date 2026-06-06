import re
from typing import Dict, Optional, Tuple

from V3.extraction.common import clean_free_text
from V3.extraction.timer.numbers import words_to_number
from V3.utils.text import normalize_text, normalized_lower


DAY_ALIASES = {
    "monday": ("monday", "pazartesi", "\u0627\u0644\u0627\u062b\u0646\u064a\u0646", "\u0627\u062b\u0646\u064a\u0646"),
    "tuesday": ("tuesday", "sali", "\u0627\u0644\u062b\u0644\u0627\u062b\u0627\u0621", "\u062b\u0644\u0627\u062b\u0627\u0621"),
    "wednesday": ("wednesday", "carsamba", "\u0627\u0644\u0627\u0631\u0628\u0639\u0627\u0621", "\u0627\u0631\u0628\u0639\u0627\u0621"),
    "thursday": ("thursday", "persembe", "\u0627\u0644\u062e\u0645\u064a\u0633", "\u062e\u0645\u064a\u0633"),
    "friday": ("friday", "cuma", "\u0627\u0644\u062c\u0645\u0639\u0629", "\u062c\u0645\u0639\u0629"),
    "saturday": ("saturday", "cumartesi", "\u0627\u0644\u0633\u0628\u062a", "\u0633\u0628\u062a"),
    "sunday": ("sunday", "pazar", "\u0627\u0644\u0627\u062d\u062f", "\u0627\u062d\u062f"),
}

AM_ALIASES = (
    "am",
    "a m",
    "morning",
    "sabah",
    "\u0635\u0628\u0627\u062d",
    "\u0627\u0644\u0635\u0628\u0627\u062d",
    "\u0641\u062c\u0631",
    "\u0627\u0644\u0641\u062c\u0631",
)

PM_ALIASES = (
    "pm",
    "p m",
    "evening",
    "afternoon",
    "night",
    "aksam",
    "ogleden sonra",
    "gece",
    "\u0645\u0633\u0627\u0621",
    "\u0627\u0644\u0645\u0633\u0627\u0621",
    "\u0644\u064a\u0644",
    "\u0627\u0644\u0644\u064a\u0644",
)


def extract_alarm(text: str, language: str) -> Dict[str, object]:
    original = normalize_text(text)
    normalized = normalized_lower(original)

    time_value = _extract_alarm_time(normalized)
    if time_value is None:
        return {}

    raw_hour, minute = time_value
    period = _extract_period(normalized)
    alarm_hour = _resolve_alarm_hour(raw_hour, period)
    if alarm_hour is None:
        return {}

    params: Dict[str, object] = {
        "alarm_hour": alarm_hour,
        "alarm_minute": minute,
    }

    if period:
        params["period"] = period

    day = _extract_day(normalized)
    if day:
        params["day"] = day

    alarm_text = clean_free_text(original)
    if alarm_text:
        params["alarm_text"] = alarm_text

    return params


def _extract_alarm_time(normalized_text: str) -> Optional[Tuple[int, int]]:
    digit_match = re.search(r"\b(\d{1,2})(?:\s*[:.]\s*(\d{1,2}))?\b", normalized_text)
    if digit_match:
        hour = int(digit_match.group(1))
        minute = int(digit_match.group(2) or 0)
        if _is_valid_raw_time(hour, minute):
            return hour, minute

    tokens = re.findall(r"[a-zA-Z\u0600-\u06FF]+", normalized_text)
    for window_size in (2, 1):
        for index in range(0, len(tokens) - window_size + 1):
            value = words_to_number(tokens[index:index + window_size])
            if value is None:
                continue

            hour = int(value)
            minute = int(round((value - hour) * 60))
            if _is_valid_raw_time(hour, minute):
                return hour, minute

    return None


def _is_valid_raw_time(hour: int, minute: int) -> bool:
    return 0 <= hour <= 23 and 0 <= minute <= 59


def _extract_period(normalized_text: str) -> Optional[str]:
    if _contains_alias(normalized_text, AM_ALIASES):
        return "am"
    if _contains_alias(normalized_text, PM_ALIASES):
        return "pm"
    return None


def _resolve_alarm_hour(hour: int, period: Optional[str]) -> Optional[int]:
    if hour < 0 or hour > 23:
        return None

    if period == "am":
        return hour % 12

    if period == "pm":
        return 12 if hour % 12 == 0 else (hour % 12) + 12

    return hour


def _extract_day(normalized_text: str) -> Optional[str]:
    for day, aliases in DAY_ALIASES.items():
        if _contains_alias(normalized_text, aliases):
            return day
    return None


def _contains_alias(normalized_text: str, aliases: Tuple[str, ...]) -> bool:
    padded = f" {normalized_text} "
    for alias in aliases:
        normalized_alias = normalized_lower(alias)
        if f" {normalized_alias} " in padded:
            return True
    return False
