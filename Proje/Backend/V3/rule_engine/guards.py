from typing import Any, Dict, Optional

from V3.extraction.common import extract_first_match
from V3.patterns.commands.guards import AMBIGUOUS_PATTERNS, DEFERRED_PATTERNS, NEGATION_PATTERNS
from V3.patterns.commands.navigation import STOP_LISTENING_PATTERNS
from V3.patterns.extraction.text import WRITE_TEXT_PATTERNS
from V3.rule_engine.context import RuleContext
from V3.rule_engine.matching import matches_language_any, matches_language_regex
from V3.rule_engine.result import unknown
from V3.utils.language import patterns_for_language


def guard_command(context: RuleContext) -> Optional[Dict[str, Any]]:
    if matches_language_any(context.original, context.language, STOP_LISTENING_PATTERNS):
        return None

    if _has_negation(context) and not _is_explicit_write_text_command(context):
        return unknown("negated_command")

    if matches_language_regex(context.original, context.language, DEFERRED_PATTERNS):
        return unknown("deferred_command")

    if matches_language_regex(context.original, context.language, AMBIGUOUS_PATTERNS):
        return unknown("ambiguous_command")

    return None


def _has_negation(context: RuleContext) -> bool:
    return matches_language_regex(context.original, context.language, NEGATION_PATTERNS)


def _is_explicit_write_text_command(context: RuleContext) -> bool:
    return extract_first_match(
        context.original,
        patterns_for_language(WRITE_TEXT_PATTERNS, context.language),
        ignore_case=True,
    ) is not None
