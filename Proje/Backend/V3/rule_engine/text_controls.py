from typing import Any, Dict, Optional

from V3.patterns.commands.text_controls import CLEAR_TEXT_PATTERNS, DOUBLE_TAP_PATTERNS, HOLD_SCREEN_PATTERNS
from V3.rule_engine.context import RuleContext
from V3.rule_engine.matching import matches_language_any
from V3.rule_engine.result import command


def text_control_command(context: RuleContext) -> Optional[Dict[str, Any]]:
    if matches_language_any(context.original, context.language, CLEAR_TEXT_PATTERNS):
        return command("CLEAR_TEXT", "clear_text")

    if matches_language_any(context.original, context.language, DOUBLE_TAP_PATTERNS):
        return command("DOUBLE_TAP", "double_tap")

    if matches_language_any(context.original, context.language, HOLD_SCREEN_PATTERNS):
        return command("HOLD_SCREEN", "hold_screen")

    return None
