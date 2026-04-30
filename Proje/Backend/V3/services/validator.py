from typing import Any, Dict, List, Optional

from V3.services.app_catalog_service import find_app_match, has_app_catalog, is_catalog_version_current
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
    session_id: Optional[str] = None,
    catalog_version: Optional[str] = None,
) -> Dict[str, Any]:
    threshold = get_threshold(model_intent)
    top_predictions = top_predictions or []

    if confidence < threshold:
        return _response(
            original_text=original_text,
            language=language,
            intent="UNKNOWN_COMMAND",
            parameters={},
            accepted=False,
            missing_slots=[],
            error_code="LOW_CONFIDENCE",
            error_message=f"Model confidence is too low. Required threshold for {model_intent}: {threshold}",
            needs_confirmation=True,
            confidence=confidence,
            threshold=threshold,
            raw_label=raw_label,
            top_predictions=top_predictions,
        )

    if model_intent == "UNKNOWN_COMMAND":
        return _response(
            original_text=original_text,
            language=language,
            intent="UNKNOWN_COMMAND",
            parameters={},
            accepted=False,
            missing_slots=[],
            error_code="UNKNOWN_COMMAND",
            error_message="This is not a supported command.",
            needs_confirmation=False,
            confidence=confidence,
            threshold=threshold,
            raw_label=raw_label,
            top_predictions=top_predictions,
        )

    parameters = dict(model_parameters or {})
    accepted = True
    missing_slots: List[str] = []
    error_code = None
    error_message = None

    if model_intent in ["SCROLL_SCREEN", "SWIPE_GESTURE"]:
        _require(parameters, "direction", missing_slots)

    elif model_intent == "ADJUST_VOLUME":
        _require(parameters, "volume_action", missing_slots)

    elif model_intent == "OPEN_APP":
        app_name = extract_app_name(original_text, language)
        if not app_name:
            missing_slots.append("app_name")
        elif not has_app_catalog(session_id):
            accepted = False
            error_code = "APP_CATALOG_MISSING"
            error_message = "Installed app catalog is missing for this session."
        elif not is_catalog_version_current(session_id, catalog_version):
            accepted = False
            error_code = "APP_CATALOG_STALE"
            error_message = "Installed app catalog version is stale for this session."
        else:
            app_match = find_app_match(session_id, app_name)
            if app_match:
                parameters["app_name"] = app_match.label
                parameters["app_package_name"] = app_match.package_name
                parameters["app_match_score"] = round(app_match.score, 4)
            else:
                accepted = False
                error_code = "APP_NOT_IN_CATALOG"
                error_message = "The requested app does not match an installed app."

    elif model_intent == "SET_TIMER":
        parameters.update(extract_timer(original_text))
        _require(parameters, "duration_value", missing_slots)
        _require(parameters, "duration_unit", missing_slots)
        _require(parameters, "duration_seconds", missing_slots)

    elif model_intent in ["GO_HOME", "TAKE_PHOTO", "STOP_LISTENING"]:
        pass

    else:
        accepted = False
        error_code = "UNSUPPORTED_INTENT"
        error_message = f"Backend does not support this intent yet: {model_intent}"

    if missing_slots:
        accepted = False

    if not accepted and error_code is None:
        error_code = "MISSING_REQUIRED_SLOT"
        error_message = "Required parameter is missing."

    return _response(
        original_text=original_text,
        language=language,
        intent=model_intent,
        parameters=parameters,
        accepted=accepted,
        missing_slots=missing_slots,
        error_code=error_code,
        error_message=error_message,
        needs_confirmation=not accepted,
        confidence=confidence,
        threshold=threshold,
        raw_label=raw_label,
        top_predictions=top_predictions,
    )


def _require(parameters: Dict[str, Any], key: str, missing_slots: List[str]) -> None:
    if not parameters.get(key):
        missing_slots.append(key)


def _response(
    original_text: str,
    language: str,
    intent: str,
    parameters: Dict[str, Any],
    accepted: bool,
    missing_slots: List[str],
    error_code: Optional[str],
    error_message: Optional[str],
    needs_confirmation: bool,
    confidence: float,
    threshold: float,
    raw_label: str,
    top_predictions: List[Dict[str, Any]],
) -> Dict[str, Any]:
    return {
        "input": original_text,
        "normalized_input": normalize_text(original_text),
        "language": language.upper(),
        "intent": intent,
        "parameters": parameters,
        "accepted": accepted,
        "missing_slots": missing_slots,
        "error_code": error_code,
        "error_message": error_message,
        "needs_confirmation": needs_confirmation,
        "confidence": confidence,
        "threshold": threshold,
        "raw_label": raw_label,
        "top_predictions": top_predictions,
    }
