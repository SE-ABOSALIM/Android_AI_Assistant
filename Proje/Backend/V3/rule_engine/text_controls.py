from typing import Any, Dict, Optional

from V3.patterns.commands.text_controls import CLEAR_TEXT_PATTERNS, DOUBLE_TAP_PATTERNS, HOLD_SCREEN_PATTERNS
from V3.rule_engine.context import RuleContext
from V3.rule_engine.pattern_rules import PatternRule, match_first_pattern_rule


TEXT_CONTROL_RULES = [
    PatternRule("CLEAR_TEXT", "clear_text", CLEAR_TEXT_PATTERNS),
    PatternRule("DOUBLE_TAP", "double_tap", DOUBLE_TAP_PATTERNS),
    PatternRule("HOLD_SCREEN", "hold_screen", HOLD_SCREEN_PATTERNS),
]


def text_control_command(context: RuleContext) -> Optional[Dict[str, Any]]:
    return match_first_pattern_rule(context, TEXT_CONTROL_RULES)
