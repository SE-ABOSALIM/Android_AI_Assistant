from typing import Any, Dict, Optional

from V3.patterns.commands.volume import VOLUME_LEVEL_PATTERNS, VOLUME_PATTERNS
from V3.services.rules.context import RuleContext
from V3.services.rules.matching import matches_language_any
from V3.services.rules.result import command, unknown


def volume_command(context: RuleContext) -> Optional[Dict[str, Any]]:
    volume_level = _extract_volume_level(context)
    if volume_level == "ambiguous":
        return unknown("ambiguous_volume_level")
    if volume_level:
        return command("ADJUST_VOLUME", f"volume_level_{volume_level}", {"volume_level": volume_level})

    volume_action = _extract_volume_action(context)
    if volume_action == "ambiguous":
        return unknown("ambiguous_volume")
    if volume_action:
        return command("ADJUST_VOLUME", f"volume_{volume_action}", {"volume_action": volume_action})

    return None


def _extract_volume_level(context: RuleContext) -> Optional[str]:
    matched_levels = {
        level
        for level, patterns in VOLUME_LEVEL_PATTERNS.items()
        if matches_language_any(context.original, context.language, patterns)
    }

    if len(matched_levels) > 1:
        return "ambiguous"

    return next(iter(matched_levels), None)


def _extract_volume_action(context: RuleContext) -> Optional[str]:
    matched_actions = {
        action
        for action, patterns in VOLUME_PATTERNS.items()
        if matches_language_any(context.original, context.language, patterns)
    }

    if len(matched_actions) > 1:
        return "ambiguous"

    return next(iter(matched_actions), None)
