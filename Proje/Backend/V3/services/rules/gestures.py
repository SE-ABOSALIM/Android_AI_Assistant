from typing import Any, Dict, Optional

from V3.patterns.commands.gestures import (
    SCROLL_DOWN_PATTERNS,
    SCROLL_UP_PATTERNS,
    SWIPE_LEFT_PATTERNS,
    SWIPE_RIGHT_PATTERNS,
)
from V3.services.rules.context import RuleContext
from V3.services.rules.matching import matches_language_any
from V3.services.rules.result import command


def gesture_command(context: RuleContext) -> Optional[Dict[str, Any]]:
    if matches_language_any(context.original, context.language, SCROLL_DOWN_PATTERNS):
        return command("SCROLL_SCREEN", "scroll_down", {"direction": "down"})

    if matches_language_any(context.original, context.language, SCROLL_UP_PATTERNS):
        return command("SCROLL_SCREEN", "scroll_up", {"direction": "up"})

    if matches_language_any(context.original, context.language, SWIPE_LEFT_PATTERNS):
        return command("SWIPE_GESTURE", "swipe_left", {"direction": "left"})

    if matches_language_any(context.original, context.language, SWIPE_RIGHT_PATTERNS):
        return command("SWIPE_GESTURE", "swipe_right", {"direction": "right"})

    return None
