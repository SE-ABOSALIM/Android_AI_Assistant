import re
from typing import Any, Dict, Iterable, Optional

from V3.services.extractors import extract_timer
from V3.services.text_utils import normalize_text, normalized_lower

from V3.patterns.command_patterns import (
    SCROLL_UP_PATTERNS,
    SCROLL_DOWN_PATTERNS,
    SWIPE_LEFT_PATTERNS,
    SWIPE_RIGHT_PATTERNS,
    VOLUME_PATTERNS,
    GO_HOME_PATTERNS,
    TAKE_PHOTO_PATTERNS,
    TIMER_KEYWORDS,
    TIMER_ACTION_PATTERNS,
    ARABIC_PATTERNS,
    NEGATION_PATTERNS,
    DEFERRED_PATTERNS,
    AMBIGUOUS_PATTERNS
)

def rule_based_command(text: str, language: str) -> Optional[Dict[str, Any]]:
    """
    Net ve deterministic komutları modelden önce yakalar.
    Belirsiz veya olumsuz ifadeleri rule ile çalıştırmaz.
    """

    original = normalize_text(text)
    t = normalized_lower(original)

    if _has_negation(t, original):
        return _unknown("negated_command")

    if _matches_regex(t, DEFERRED_PATTERNS):
        return _unknown("deferred_command")

    if _matches_regex(t, AMBIGUOUS_PATTERNS):
        return _unknown("ambiguous_command")

    if _matches_any(t, SCROLL_DOWN_PATTERNS) or _matches_any(original, ARABIC_PATTERNS["scroll_down"]):
        return _command("SCROLL_SCREEN", "scroll_down", {"direction": "down"})

    if _matches_any(t, SCROLL_UP_PATTERNS) or _matches_any(original, ARABIC_PATTERNS["scroll_up"]):
        return _command("SCROLL_SCREEN", "scroll_up", {"direction": "up"})

    if _matches_any(t, SWIPE_LEFT_PATTERNS) or _matches_any(original, ARABIC_PATTERNS["swipe_left"]):
        return _command("SWIPE_GESTURE", "swipe_left", {"direction": "left"})

    if _matches_any(t, SWIPE_RIGHT_PATTERNS) or _matches_any(original, ARABIC_PATTERNS["swipe_right"]):
        return _command("SWIPE_GESTURE", "swipe_right", {"direction": "right"})

    volume_action = _extract_volume_action(t, original)
    if volume_action == "ambiguous":
        return _unknown("ambiguous_volume")
    if volume_action:
        return _command("ADJUST_VOLUME", f"volume_{volume_action}", {"volume_action": volume_action})

    if _matches_any(t, GO_HOME_PATTERNS) or _matches_any(original, ARABIC_PATTERNS["go_home"]):
        return _command("GO_HOME", "go_home")

    if _matches_any(t, TAKE_PHOTO_PATTERNS) or _matches_any(original, ARABIC_PATTERNS["take_photo"]):
        return _command("TAKE_PHOTO", "take_photo")

    timer_params = extract_timer(original)
    has_timer_keyword = _matches_any(t, TIMER_KEYWORDS) or _matches_any(original, ARABIC_PATTERNS["timer_keywords"])
    has_timer_action = _matches_any(t, TIMER_ACTION_PATTERNS) or (
        _matches_any(original, ARABIC_PATTERNS["timer_actions"])
        and _matches_any(original, ARABIC_PATTERNS["timer_keywords"])
    )

    if has_timer_keyword and (timer_params or has_timer_action):
        return _command("SET_TIMER", "set_timer", timer_params)

    return None


def _extract_volume_action(text: str, original: str) -> Optional[str]:
    matched_actions = {
        action
        for action, patterns in VOLUME_PATTERNS.items()
        if _matches_any(text, patterns)
    }

    if _matches_any(original, ARABIC_PATTERNS["volume_decrease"]):
        matched_actions.add("decrease")
    if _matches_any(original, ARABIC_PATTERNS["volume_increase"]):
        matched_actions.add("increase")
    if _matches_any(original, ARABIC_PATTERNS["volume_mute"]):
        matched_actions.add("mute")
    if _matches_any(original, ARABIC_PATTERNS["volume_unmute"]):
        matched_actions.add("unmute")

    if len(matched_actions) > 1:
        return "ambiguous"

    return next(iter(matched_actions), None)


def _command(intent: str, rule_matched: str, parameters: Optional[Dict[str, Any]] = None) -> Dict[str, Any]:
    return {
        "intent": intent,
        "parameters": parameters or {},
        "rule_matched": rule_matched,
    }


def _unknown(rule_matched: str) -> Dict[str, Any]:
    return _command("UNKNOWN_COMMAND", rule_matched)


def _has_negation(text: str, original: str) -> bool:
    return _matches_regex(text, NEGATION_PATTERNS) or any(pattern in original for pattern in ARABIC_PATTERNS["negation"])


def _matches_any(text: str, patterns: Iterable[str]) -> bool:
    return any(_contains_phrase(text, pattern) for pattern in patterns)


def _matches_regex(text: str, patterns: Iterable[str]) -> bool:
    return any(re.search(pattern, text) for pattern in patterns)


def _contains_phrase(text: str, phrase: str) -> bool:
    pattern = rf"(?<!\w){re.escape(phrase)}(?!\w)"
    return re.search(pattern, text, flags=re.IGNORECASE) is not None
