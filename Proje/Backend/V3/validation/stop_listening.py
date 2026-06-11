import re
from typing import Any, Dict, Iterable


STOP_LISTENING_MIN_WORD_COUNT = 2


def has_enough_stop_listening_words(text: str) -> bool:
    return len(re.findall(r"\w+", str(text or ""))) >= STOP_LISTENING_MIN_WORD_COUNT


def should_accept_stop_listening(
    confidence: float,
    raw_label: str,
    top_predictions: Iterable[Dict[str, Any]],
) -> bool:
    return is_rule_prediction(raw_label)


def is_rule_prediction(raw_label: str) -> bool:
    return str(raw_label or "").startswith("RULE::")
