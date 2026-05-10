from typing import Any, Dict, Optional

from V3.patterns.commands.arabic import ARABIC_PATTERNS
from V3.patterns.commands.gestures import (
    SCROLL_DOWN_PATTERNS,
    SCROLL_UP_PATTERNS,
    SWIPE_LEFT_PATTERNS,
    SWIPE_RIGHT_PATTERNS,
)
from V3.services.rules.context import RuleContext
from V3.services.rules.matching import matches_any
from V3.services.rules.result import command


def gesture_command(context: RuleContext) -> Optional[Dict[str, Any]]:
    if matches_any(context.normalized, SCROLL_DOWN_PATTERNS) or matches_any(context.original, ARABIC_PATTERNS["scroll_down"]):
        return command("SCROLL_SCREEN", "scroll_down", {"direction": "down"})

    if matches_any(context.normalized, SCROLL_UP_PATTERNS) or matches_any(context.original, ARABIC_PATTERNS["scroll_up"]):
        return command("SCROLL_SCREEN", "scroll_up", {"direction": "up"})

    if matches_any(context.normalized, SWIPE_LEFT_PATTERNS) or matches_any(context.original, ARABIC_PATTERNS["swipe_left"]):
        return command("SWIPE_GESTURE", "swipe_left", {"direction": "left"})

    if matches_any(context.normalized, SWIPE_RIGHT_PATTERNS) or matches_any(context.original, ARABIC_PATTERNS["swipe_right"]):
        return command("SWIPE_GESTURE", "swipe_right", {"direction": "right"})

    return None
