from typing import Any, Dict, Optional

from V3.rule_engine.context import RuleContext
from V3.rule_engine.guards import guard_command
from V3.rule_engine.registry import RULE_HANDLERS
from V3.utils.text import normalize_text, normalized_lower


def rule_based_command(text: str, language: str) -> Optional[Dict[str, Any]]:
    original = normalize_text(text)
    context = RuleContext(
        original=original,
        normalized=normalized_lower(original),
        language=language,
    )

    guarded_result = guard_command(context)
    if guarded_result is not None:
        return guarded_result

    for handler in RULE_HANDLERS:
        result = handler(context)
        if result is not None:
            return result

    return None
