from typing import Any, Dict, Optional

from V3.patterns.commands.volume import VOLUME_LEVEL_PATTERNS, VOLUME_PATTERNS
from V3.rule_engine.context import RuleContext
from V3.rule_engine.matching import GroupMatch, resolve_group_match
from V3.rule_engine.result import command, unknown


def volume_command(context: RuleContext) -> Optional[Dict[str, Any]]:
    volume_level = _extract_volume_level(context)
    if volume_level.ambiguous:
        return unknown("ambiguous_volume_level")
    if volume_level.matched_key:
        return command(
            "ADJUST_VOLUME",
            f"volume_level_{volume_level.matched_key}",
            {"volume_level": volume_level.matched_key},
        )

    volume_action = _extract_volume_action(context)
    if volume_action.ambiguous:
        return unknown("ambiguous_volume")
    if volume_action.matched_key:
        return command(
            "ADJUST_VOLUME",
            f"volume_{volume_action.matched_key}",
            {"volume_action": volume_action.matched_key},
        )

    return None


def _extract_volume_level(context: RuleContext) -> GroupMatch:
    return resolve_group_match(context.original, context.language, VOLUME_LEVEL_PATTERNS)


def _extract_volume_action(context: RuleContext) -> GroupMatch:
    return resolve_group_match(context.original, context.language, VOLUME_PATTERNS)
