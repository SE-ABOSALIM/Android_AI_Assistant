from V3.services.intent_registry import get_threshold as _get_registered_threshold


def get_threshold(intent: str) -> float:
    return _get_registered_threshold(intent)
