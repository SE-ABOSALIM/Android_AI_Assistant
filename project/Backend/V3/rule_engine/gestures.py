from typing import Any, Dict, Optional

from V3.patterns.commands.gestures import (
    SCROLL_DOWN_PATTERNS,
    SCROLL_UP_PATTERNS,
    SWIPE_LEFT_PATTERNS,
    SWIPE_RIGHT_PATTERNS,
)
from V3.rule_engine.context import RuleContext
from V3.rule_engine.pattern_rules import PatternRule, match_first_pattern_rule


GESTURE_RULES = [
    PatternRule("SCROLL_SCREEN", "scroll_down", SCROLL_DOWN_PATTERNS, {"direction": "down"}),
    PatternRule("SCROLL_SCREEN", "scroll_up", SCROLL_UP_PATTERNS, {"direction": "up"}),
    PatternRule("SWIPE_GESTURE", "swipe_left", SWIPE_LEFT_PATTERNS, {"direction": "left"}),
    PatternRule("SWIPE_GESTURE", "swipe_right", SWIPE_RIGHT_PATTERNS, {"direction": "right"}),
]


def gesture_command(context: RuleContext) -> Optional[Dict[str, Any]]:
    return match_first_pattern_rule(context, GESTURE_RULES)
