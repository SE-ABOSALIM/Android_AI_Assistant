from typing import Any, Dict, Optional

from V3.patterns.commands.timer import TIMER_ACTION_PATTERNS, TIMER_KEYWORDS
from V3.extraction.timer import extract_timer
from V3.rule_engine.context import RuleContext
from V3.rule_engine.matching import matches_language_any
from V3.rule_engine.result import command


def timer_command(context: RuleContext) -> Optional[Dict[str, Any]]:
    timer_params = extract_timer(context.original)
    has_timer_keyword = matches_language_any(context.original, context.language, TIMER_KEYWORDS)
    has_timer_action = matches_language_any(context.original, context.language, TIMER_ACTION_PATTERNS)

    if has_timer_keyword and (timer_params or has_timer_action):
        return command("SET_TIMER", "set_timer", timer_params)

    return None
