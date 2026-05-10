from typing import Any, Dict, Optional

from V3.patterns.commands.arabic import ARABIC_PATTERNS
from V3.patterns.commands.timer import TIMER_ACTION_PATTERNS, TIMER_KEYWORDS
from V3.services.extractors import extract_timer
from V3.services.rules.context import RuleContext
from V3.services.rules.matching import matches_any
from V3.services.rules.result import command


def timer_command(context: RuleContext) -> Optional[Dict[str, Any]]:
    timer_params = extract_timer(context.original)
    has_timer_keyword = (
        matches_any(context.normalized, TIMER_KEYWORDS)
        or matches_any(context.original, ARABIC_PATTERNS["timer_keywords"])
    )
    has_timer_action = matches_any(context.normalized, TIMER_ACTION_PATTERNS) or (
        matches_any(context.original, ARABIC_PATTERNS["timer_actions"])
        and matches_any(context.original, ARABIC_PATTERNS["timer_keywords"])
    )

    if has_timer_keyword and (timer_params or has_timer_action):
        return command("SET_TIMER", "set_timer", timer_params)

    return None
