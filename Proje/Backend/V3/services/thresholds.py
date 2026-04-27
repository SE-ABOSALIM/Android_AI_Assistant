from V3.config import DEFAULT_CONFIDENCE_THRESHOLD, INTENT_THRESHOLDS


def get_threshold(intent: str) -> float:
    return INTENT_THRESHOLDS.get(intent, DEFAULT_CONFIDENCE_THRESHOLD)
