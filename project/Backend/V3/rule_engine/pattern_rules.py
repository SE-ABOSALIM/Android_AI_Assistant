from dataclasses import dataclass, field
from typing import Any, Dict, Mapping, Optional, Sequence

from V3.rule_engine.context import RuleContext
from V3.rule_engine.matching import matches_language_any
from V3.rule_engine.result import command
from V3.utils.language import LanguagePatterns


@dataclass(frozen=True)
class PatternRule:
    intent: str
    rule_matched: str
    patterns: LanguagePatterns
    parameters: Mapping[str, Any] = field(default_factory=dict)


def match_first_pattern_rule(
    context: RuleContext,
    rules: Sequence[PatternRule],
) -> Optional[Dict[str, Any]]:
    for rule in rules:
        if matches_language_any(context.original, context.language, rule.patterns):
            return command(rule.intent, rule.rule_matched, dict(rule.parameters))

    return None
