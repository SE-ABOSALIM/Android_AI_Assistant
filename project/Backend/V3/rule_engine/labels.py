from typing import Any, Dict, Optional

from V3.patterns.commands.labels import SHOW_LABELS_PATTERNS
from V3.rule_engine.context import RuleContext
from V3.rule_engine.pattern_rules import PatternRule, match_first_pattern_rule


LABEL_RULES = [
    PatternRule("SHOW_LABELS", "show_labels", SHOW_LABELS_PATTERNS, {"labels_action": "show"}),
]


def labels_command(context: RuleContext) -> Optional[Dict[str, Any]]:
    return match_first_pattern_rule(context, LABEL_RULES)
