from typing import Any, Dict, List, Optional


_INTERNAL_CONFIDENCE_KEY = "_internal_confidence"


def build_response(
    intent: str,
    parameters: Dict[str, Any],
    accepted: bool,
    missing_slots: List[str],
    error_code: Optional[str],
    error_message: Optional[str],
    confidence: float,
) -> Dict[str, Any]:
    return {
        "intent": intent,
        "parameters": parameters,
        "accepted": accepted,
        "missing_slots": missing_slots,
        "error_code": error_code,
        "error_message": error_message,
        _INTERNAL_CONFIDENCE_KEY: confidence,
    }


def pop_internal_confidence(response: Dict[str, Any]) -> Optional[float]:
    return response.pop(_INTERNAL_CONFIDENCE_KEY, None)
