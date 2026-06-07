import re
from typing import Iterable

from V3.patterns.validation.model_guard import (
    COMMAND_SIGNAL_WORDS,
    MODEL_FALLBACK_GUARDED_INTENTS,
)
from V3.utils.language import language_key
from V3.utils.text import normalized_lower


def should_reject_weak_model_command(
    text: str,
    language: str,
    intent: str,
    raw_label: str,
) -> bool:
    if _is_rule_prediction(raw_label):
        return False

    intent = str(intent or "").upper()
    if intent not in MODEL_FALLBACK_GUARDED_INTENTS:
        return False

    tokens = _tokens(text)
    if not tokens:
        return True

    return not _has_command_signal(tokens, language)


def _is_rule_prediction(raw_label: str) -> bool:
    return str(raw_label or "").startswith("RULE::")


def _has_command_signal(tokens: Iterable[str], language: str) -> bool:
    signal_words = COMMAND_SIGNAL_WORDS.get(language_key(language), COMMAND_SIGNAL_WORDS["EN"])
    return any(token in signal_words for token in tokens)


def _tokens(text: str) -> list[str]:
    return re.findall(r"\w+", normalized_lower(text))
