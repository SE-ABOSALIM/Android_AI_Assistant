from typing import Any, Dict, Optional

from V3.patterns.commands.guards import AMBIGUOUS_PATTERNS, DEFERRED_PATTERNS, NEGATION_PATTERNS
from V3.patterns.commands.navigation import STOP_LISTENING_PATTERNS
from V3.services.rules.context import RuleContext
from V3.services.rules.matching import matches_language_any, matches_language_regex
from V3.services.rules.result import unknown


def guard_command(context: RuleContext) -> Optional[Dict[str, Any]]:
    if matches_language_any(context.original, context.language, STOP_LISTENING_PATTERNS):
        return None

    if _has_negation(context):
        return unknown("negated_command")

    if matches_language_regex(context.original, context.language, DEFERRED_PATTERNS):
        return unknown("deferred_command")

    if matches_language_regex(context.original, context.language, AMBIGUOUS_PATTERNS):
        return unknown("ambiguous_command")

    return None


def _has_negation(context: RuleContext) -> bool:
    return matches_language_regex(context.original, context.language, NEGATION_PATTERNS)
