from typing import Any, Dict, Optional

from V3.patterns.commands.arabic import ARABIC_PATTERNS
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
from V3.services.rules.context import RuleContext
from V3.services.rules.matching import matches_any
from V3.services.rules.result import command


def navigation_command(context: RuleContext) -> Optional[Dict[str, Any]]:
    if matches_any(context.normalized, GO_BACK_PATTERNS):
        return command("GO_BACK", "go_back")

    if matches_any(context.normalized, GO_HOME_PATTERNS) or matches_any(context.original, ARABIC_PATTERNS["go_home"]):
        return command("GO_HOME", "go_home")

    if matches_any(context.normalized, OPEN_NOTIFICATIONS_PATTERNS):
        return command("OPEN_NOTIFICATIONS", "open_notifications")

    if matches_any(context.normalized, SHOW_RECENTS_PATTERNS):
        return command("SHOW_RECENTS", "show_recents")

    if matches_any(context.normalized, CLOSE_APP_PATTERNS):
        return command("CLOSE_APP", "close_app")

    if matches_any(context.normalized, TAKE_PHOTO_PATTERNS) or matches_any(context.original, ARABIC_PATTERNS["take_photo"]):
        return command("TAKE_PHOTO", "take_photo")

    if matches_any(context.normalized, TAKE_SCREENSHOT_PATTERNS):
        return command("TAKE_SCREENSHOT", "take_screenshot")

    if matches_any(context.normalized, STOP_LISTENING_PATTERNS):
        return command("STOP_LISTENING", "stop_listening")

    return None
