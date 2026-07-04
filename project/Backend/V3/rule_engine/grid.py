from typing import Any, Dict, Optional

from V3.patterns.commands.grid import (
    HIDE_GRID_PATTERNS,
    LARGER_GRID_PATTERNS,
    SHOW_GRID_PATTERNS,
    SMALLER_GRID_PATTERNS,
)
from V3.rule_engine.context import RuleContext
from V3.rule_engine.pattern_rules import PatternRule, match_first_pattern_rule


GRID_RULES = [
    PatternRule("SHOW_GRID", "show_grid", SHOW_GRID_PATTERNS, {"grid_action": "show"}),
    PatternRule("SHOW_GRID", "smaller_grid", SMALLER_GRID_PATTERNS, {"grid_action": "smaller"}),
    PatternRule("SHOW_GRID", "larger_grid", LARGER_GRID_PATTERNS, {"grid_action": "larger"}),
    PatternRule("SHOW_GRID", "hide_grid", HIDE_GRID_PATTERNS, {"grid_action": "hide"}),
]


def grid_command(context: RuleContext) -> Optional[Dict[str, Any]]:
    return match_first_pattern_rule(context, GRID_RULES)
