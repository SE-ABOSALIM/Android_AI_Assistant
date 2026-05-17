from typing import Any, Dict, Optional

from V3.patterns.commands.navigation import (
    CLOSE_APP_PATTERNS,
    GO_BACK_PATTERNS,
    GO_HOME_PATTERNS,
    OPEN_NOTIFICATIONS_PATTERNS,
    SHOW_RECENTS_PATTERNS,
    STOP_LISTENING_PATTERNS,
    TAKE_PHOTO_PATTERNS,
    TAKE_SCREENSHOT_PATTERNS,
)
from V3.rule_engine.context import RuleContext
from V3.rule_engine.pattern_rules import PatternRule, match_first_pattern_rule


NAVIGATION_RULES = [
    PatternRule("GO_HOME", "go_home", GO_HOME_PATTERNS),
    PatternRule("GO_BACK", "go_back", GO_BACK_PATTERNS),
    PatternRule("OPEN_NOTIFICATIONS", "open_notifications", OPEN_NOTIFICATIONS_PATTERNS),
    PatternRule("SHOW_RECENTS", "show_recents", SHOW_RECENTS_PATTERNS),
    PatternRule("CLOSE_APP", "close_app", CLOSE_APP_PATTERNS),
    PatternRule("TAKE_SCREENSHOT", "take_screenshot", TAKE_SCREENSHOT_PATTERNS),
    PatternRule("TAKE_PHOTO", "take_photo", TAKE_PHOTO_PATTERNS),
    PatternRule("STOP_LISTENING", "stop_listening", STOP_LISTENING_PATTERNS),
]


def navigation_command(context: RuleContext) -> Optional[Dict[str, Any]]:
    return match_first_pattern_rule(context, NAVIGATION_RULES)
