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
from V3.services.rules.context import RuleContext
from V3.services.rules.matching import matches_language_any
from V3.services.rules.result import command


def navigation_command(context: RuleContext) -> Optional[Dict[str, Any]]:
    if matches_language_any(context.original, context.language, GO_BACK_PATTERNS):
        return command("GO_BACK", "go_back")

    if matches_language_any(context.original, context.language, GO_HOME_PATTERNS):
        return command("GO_HOME", "go_home")

    if matches_language_any(context.original, context.language, OPEN_NOTIFICATIONS_PATTERNS):
        return command("OPEN_NOTIFICATIONS", "open_notifications")

    if matches_language_any(context.original, context.language, SHOW_RECENTS_PATTERNS):
        return command("SHOW_RECENTS", "show_recents")

    if matches_language_any(context.original, context.language, CLOSE_APP_PATTERNS):
        return command("CLOSE_APP", "close_app")

    if matches_language_any(context.original, context.language, TAKE_PHOTO_PATTERNS):
        return command("TAKE_PHOTO", "take_photo")

    if matches_language_any(context.original, context.language, TAKE_SCREENSHOT_PATTERNS):
        return command("TAKE_SCREENSHOT", "take_screenshot")

    if matches_language_any(context.original, context.language, STOP_LISTENING_PATTERNS):
        return command("STOP_LISTENING", "stop_listening")

    return None
