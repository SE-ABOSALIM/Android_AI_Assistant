from typing import Any, Dict, Optional

from V3.patterns.commands.display import BRIGHTNESS_PATTERNS
from V3.rule_engine.context import RuleContext
from V3.rule_engine.matching import matches_language_any
from V3.rule_engine.result import command, unknown


def display_command(context: RuleContext) -> Optional[Dict[str, Any]]:
    matched_actions = {
        action
        for action, patterns in BRIGHTNESS_PATTERNS.items()
        if matches_language_any(context.original, context.language, patterns)
    }

    if len(matched_actions) > 1:
        return unknown("ambiguous_brightness")

    brightness = next(iter(matched_actions), None)
    if brightness:
        return command("ADJUST_BRIGHTNESS", f"brightness_{brightness}", {"brightness": brightness})

    return None
