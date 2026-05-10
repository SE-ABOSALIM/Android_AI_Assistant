from typing import Any, Dict, Optional

from V3.patterns.commands.arabic import ARABIC_PATTERNS
from V3.patterns.commands.guards import AMBIGUOUS_PATTERNS, DEFERRED_PATTERNS, NEGATION_PATTERNS
from V3.services.rules.context import RuleContext
from V3.services.rules.matching import matches_regex
from V3.services.rules.result import unknown


def guard_command(context: RuleContext) -> Optional[Dict[str, Any]]:
    if _has_negation(context.normalized, context.original):
        return unknown("negated_command")

    if matches_regex(context.normalized, DEFERRED_PATTERNS):
        return unknown("deferred_command")

    if matches_regex(context.normalized, AMBIGUOUS_PATTERNS):
        return unknown("ambiguous_command")

    return None


def _has_negation(text: str, original: str) -> bool:
    return matches_regex(text, NEGATION_PATTERNS) or any(pattern in original for pattern in ARABIC_PATTERNS["negation"])
