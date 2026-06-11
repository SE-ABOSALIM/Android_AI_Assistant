from typing import Any, Dict, Optional

from V3.extraction.common import clean_free_text, extract_first_match
from V3.extraction.text import extract_search_query
from V3.patterns.commands.text_controls import CLEAR_TEXT_PATTERNS, DOUBLE_TAP_PATTERNS, HOLD_SCREEN_PATTERNS
from V3.patterns.extraction.text import WRITE_TEXT_PATTERNS
from V3.rule_engine.context import RuleContext
from V3.rule_engine.pattern_rules import PatternRule, match_first_pattern_rule
from V3.rule_engine.result import command
from V3.utils.language import patterns_for_language
from V3.utils.text import normalize_text


TEXT_CONTROL_RULES = [
    PatternRule("CLEAR_TEXT", "clear_text", CLEAR_TEXT_PATTERNS),
    PatternRule("DOUBLE_TAP", "double_tap", DOUBLE_TAP_PATTERNS),
    PatternRule("HOLD_SCREEN", "hold_screen", HOLD_SCREEN_PATTERNS),
]


def text_control_command(context: RuleContext) -> Optional[Dict[str, Any]]:
    search_command = _search_query_command(context)
    if search_command:
        return search_command

    write_command = _write_text_command(context)
    if write_command:
        return write_command

    return match_first_pattern_rule(context, TEXT_CONTROL_RULES)


def _search_query_command(context: RuleContext) -> Optional[Dict[str, Any]]:
    query = extract_search_query(context.original, context.language)
    if not query:
        return None

    return command("SEARCH_QUERY", "search_query", {"query": query})


def _write_text_command(context: RuleContext) -> Optional[Dict[str, Any]]:
    text = extract_first_match(
        normalize_text(context.original),
        patterns_for_language(WRITE_TEXT_PATTERNS, context.language),
        ignore_case=True,
    )
    text = clean_free_text(text)
    if not text:
        return None

    return command("WRITE_TEXT", "write_text", {"text": text})
