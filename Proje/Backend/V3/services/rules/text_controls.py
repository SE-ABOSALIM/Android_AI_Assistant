from typing import Any, Dict, Optional

from V3.patterns.commands.text_controls import CLEAR_TEXT_PATTERNS, DOUBLE_TAP_PATTERNS, HOLD_SCREEN_PATTERNS
from V3.services.rules.context import RuleContext
from V3.services.rules.matching import matches_any
from V3.services.rules.result import command


def text_control_command(context: RuleContext) -> Optional[Dict[str, Any]]:
    if matches_any(context.normalized, CLEAR_TEXT_PATTERNS):
        return command("CLEAR_TEXT", "clear_text")

    if matches_any(context.normalized, DOUBLE_TAP_PATTERNS):
        return command("DOUBLE_TAP", "double_tap")

    if matches_any(context.normalized, HOLD_SCREEN_PATTERNS):
        return command("HOLD_SCREEN", "hold_screen")

    return None
