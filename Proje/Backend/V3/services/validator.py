from typing import Any, Dict, List, Optional

from V3.services.extractors import extract_app_name, extract_timer
from V3.services.text_utils import normalize_text
from V3.services.thresholds import get_threshold


def validate_and_build_response(
    original_text: str,
    language: str,
    model_intent: str,
    model_parameters: Dict[str, Any],
    confidence: float,
    raw_label: str,
    top_predictions: Optional[List[Dict[str, Any]]] = None,
) -> Dict[str, Any]:

    threshold = get_threshold(model_intent)

    parameters = dict(model_parameters)
    missing_slots = []
    error_code = None
    error_message = None
    accepted = True

    # Confidence check
    if confidence < threshold:
        return {
            "input": original_text,
            "normalized_input": normalize_text(original_text),
            "language": language.upper(),
            "intent": "UNKNOWN_COMMAND",
            "parameters": {},
            "accepted": False,
            "missing_slots": [],
            "error_code": "LOW_CONFIDENCE",
            "error_message": f"Model confidence is too low. Required threshold for {model_intent}: {threshold}",
            "needs_confirmation": True,
            "confidence": confidence,
            "threshold": threshold,
            "raw_label": raw_label,
            "top_predictions": top_predictions or [],
        }

    # Unknown command
    if model_intent == "UNKNOWN_COMMAND":
        return {
            "input": original_text,
            "normalized_input": normalize_text(original_text),
            "language": language.upper(),
            "intent": "UNKNOWN_COMMAND",
            "parameters": {},
            "accepted": False,
            "missing_slots": [],
            "error_code": "UNKNOWN_COMMAND",
            "error_message": "This is not a supported command.",
            "needs_confirmation": False,
            "confidence": confidence,
            "threshold": threshold,
            "raw_label": raw_label,
            "top_predictions": top_predictions or [],
        }

    # Required slots / backend extraction
    if model_intent in ["SCROLL_SCREEN", "SWIPE_GESTURE"]:
        if not parameters.get("direction"):
            accepted = False
            missing_slots.append("direction")

    elif model_intent == "ADJUST_VOLUME":
        if not parameters.get("volume_action"):
            accepted = False
            missing_slots.append("volume_action")

    elif model_intent == "OPEN_APP":
        app_name = extract_app_name(original_text, language)

        if app_name:
            parameters["app_name"] = app_name
        else:
            accepted = False
            missing_slots.append("app_name")

    elif model_intent == "SET_TIMER":
        timer_params = extract_timer(original_text)

        if timer_params:
            parameters.update(timer_params)
        else:
            accepted = False
            missing_slots.extend(["duration_value", "duration_unit"])

    elif model_intent in ["GO_HOME", "TAKE_PHOTO", "STOP_LISTENING"]:
        pass

    else:
        accepted = False
        error_code = "UNSUPPORTED_INTENT"
        error_message = f"Backend does not support this intent yet: {model_intent}"

    if not accepted and error_code is None:
        error_code = "MISSING_REQUIRED_SLOT"
        error_message = "Required parameter is missing."

    return {
        "input": original_text,
        "normalized_input": normalize_text(original_text),
        "language": language.upper(),
        "intent": model_intent,
        "parameters": parameters,
        "accepted": accepted,
        "missing_slots": missing_slots,
        "error_code": error_code,
        "error_message": error_message,
        "needs_confirmation": not accepted,
        "confidence": confidence,
        "threshold": threshold,
        "raw_label": raw_label,
        "top_predictions": top_predictions or [],
    }
