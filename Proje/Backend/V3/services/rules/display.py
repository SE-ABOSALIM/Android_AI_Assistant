from typing import Any, Dict, Optional

from V3.patterns.commands.display import BRIGHTNESS_PATTERNS
from V3.services.rules.context import RuleContext
from V3.services.rules.matching import matches_any
from V3.services.rules.result import command, unknown


def display_command(context: RuleContext) -> Optional[Dict[str, Any]]:
    matched_actions = {
        action
        for action, patterns in BRIGHTNESS_PATTERNS.items()
        if matches_any(context.normalized, patterns)
    }

    if len(matched_actions) > 1:
        return unknown("ambiguous_brightness")

    brightness = next(iter(matched_actions), None)
    if brightness:
        return command("ADJUST_BRIGHTNESS", f"brightness_{brightness}", {"brightness": brightness})

    return None
