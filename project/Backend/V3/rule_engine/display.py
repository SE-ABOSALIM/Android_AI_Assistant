from typing import Any, Dict, Optional

from V3.patterns.commands.display import BRIGHTNESS_PATTERNS
from V3.rule_engine.context import RuleContext
from V3.rule_engine.matching import resolve_group_match
from V3.rule_engine.result import command, unknown


def display_command(context: RuleContext) -> Optional[Dict[str, Any]]:
    brightness_match = resolve_group_match(context.original, context.language, BRIGHTNESS_PATTERNS)
    if brightness_match.ambiguous:
        return unknown("ambiguous_brightness")

    brightness = brightness_match.matched_key
    if brightness:
        return command("ADJUST_BRIGHTNESS", f"brightness_{brightness}", {"brightness": brightness})

    return None
