import re
from typing import Any, Dict, Iterable, Optional


STOP_LISTENING_MODEL_CONFIDENCE_THRESHOLD = 0.88
STOP_LISTENING_MIN_TOP_MARGIN = 0.25
STOP_LISTENING_NO_TOPK_CONFIDENCE_THRESHOLD = 0.94
STOP_LISTENING_MIN_WORD_COUNT = 2


def has_enough_stop_listening_words(text: str) -> bool:
    return len(re.findall(r"\w+", str(text or ""))) >= STOP_LISTENING_MIN_WORD_COUNT


def should_accept_stop_listening(
    confidence: float,
    raw_label: str,
    top_predictions: Iterable[Dict[str, Any]],
) -> bool:
    if _is_rule_prediction(raw_label):
        return True

    confidence = _safe_float(confidence)
    if confidence < STOP_LISTENING_MODEL_CONFIDENCE_THRESHOLD:
        return False

    predictions = list(top_predictions or [])
    stop_confidence = _confidence_for_intent(predictions, "STOP_LISTENING")
    if stop_confidence is None:
        return confidence >= STOP_LISTENING_NO_TOPK_CONFIDENCE_THRESHOLD

    strongest_other = max(
        (
            _safe_float(prediction.get("confidence"))
            for prediction in predictions
            if _intent_from_label(prediction.get("label")) != "STOP_LISTENING"
        ),
        default=0.0,
    )

    return stop_confidence - strongest_other >= STOP_LISTENING_MIN_TOP_MARGIN


def _is_rule_prediction(raw_label: str) -> bool:
    return str(raw_label or "").startswith("RULE::")


def _confidence_for_intent(
    predictions: Iterable[Dict[str, Any]],
    intent: str,
) -> Optional[float]:
    intent = str(intent or "").upper()
    for prediction in predictions:
        if _intent_from_label(prediction.get("label")) == intent:
            return _safe_float(prediction.get("confidence"))
    return None


def _intent_from_label(label: Any) -> str:
    return str(label or "").split("__", 1)[0].upper()


def _safe_float(value: Any) -> float:
    try:
        return float(value)
    except (TypeError, ValueError):
        return 0.0
