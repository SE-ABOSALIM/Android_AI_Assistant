from typing import Any, Dict, List, Optional

from V3.services.app_catalog_service import has_app_catalog, is_catalog_version_current, resolve_app_match
from V3.services.extractors import extract_app_name, extract_contact_name, extract_timer
from V3.services.text_utils import normalize_text, normalized_lower
from V3.services.thresholds import get_threshold


def validate_and_build_response(
    original_text: str,
    language: str,
    model_intent: str,
    model_parameters: Dict[str, Any],
    confidence: float,
    raw_label: str,
    top_predictions: Optional[List[Dict[str, Any]]] = None,
    text_alternatives: Optional[List[str]] = None,
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
        app_names = _extract_app_name_candidates(original_text, language, text_alternatives)
        if not app_names:
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
            app_resolution = _resolve_best_app_match(session_id, app_names)
            app_name = app_resolution["app_name"]
            if app_resolution["ambiguous_matches"]:
                accepted = False
                parameters["app_name"] = app_name
                parameters["app_match_candidates"] = _serialize_app_matches(app_resolution["ambiguous_matches"])
                error_code = "APP_MATCH_AMBIGUOUS"
                error_message = "Multiple installed apps match the requested app name."
            elif app_resolution["match"]:
                match = app_resolution["match"]
                parameters["app_name"] = match.label
                parameters["app_package_name"] = match.package_name
                parameters["app_match_score"] = round(match.score, 4)
            else:
                accepted = False
                parameters["app_name"] = app_name
                parameters["app_match_candidates"] = _serialize_app_matches(app_resolution["suggested_matches"])
                error_code = "APP_NOT_IN_CATALOG"
                if app_resolution["suggested_matches"]:
                    error_message = "The requested app did not match confidently. Choose one of the closest installed apps."
                else:
                    error_message = "The requested app does not match an installed app."

    elif model_intent == "SET_TIMER":
        if _looks_like_stopwatch_command(original_text):
            accepted = False
            error_code = "UNSUPPORTED_STOPWATCH"
            error_message = "Stopwatch commands are not supported as timer commands."
        else:
            parameters.update(extract_timer(original_text))
            _require(parameters, "duration_value", missing_slots)
            _require(parameters, "duration_unit", missing_slots)
            _require(parameters, "duration_seconds", missing_slots)

    elif model_intent == "CALL_CONTACT":
        contact_name = extract_contact_name(original_text, language)
        if contact_name:
            parameters["contact_name"] = contact_name
        _require(parameters, "contact_name", missing_slots)

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


def _extract_app_name_candidates(
    original_text: str,
    language: str,
    text_alternatives: Optional[List[str]],
) -> List[str]:
    candidates: List[str] = []
    seen = set()

    for text in [original_text, *(text_alternatives or [])]:
        app_name = extract_app_name(text, language)
        if app_name and app_name.casefold() not in seen:
            candidates.append(app_name)
            seen.add(app_name.casefold())

    return candidates


def _resolve_best_app_match(session_id: Optional[str], app_names: List[str]) -> Dict[str, Any]:
    best_matches = []
    ambiguous_matches = []
    suggested_matches = []

    for app_name in app_names:
        resolution = resolve_app_match(session_id, app_name)
        if resolution.match:
            best_matches.append((app_name, resolution.match))
        if resolution.ambiguous_matches:
            ambiguous_matches.extend((app_name, match) for match in resolution.ambiguous_matches)
        if resolution.suggested_matches:
            suggested_matches.extend((app_name, match) for match in resolution.suggested_matches)

    if best_matches:
        best_matches.sort(key=lambda item: (-item[1].score, item[1].label.casefold(), item[1].package_name))
        best_score = best_matches[0][1].score
        tied_matches = [
            match
            for _, match in best_matches
            if best_score - match.score <= 0.03
        ]
        deduped_ties = _dedupe_app_matches(tied_matches)
        if len(deduped_ties) > 1:
            return {
                "app_name": best_matches[0][0],
                "match": None,
                "ambiguous_matches": deduped_ties[:5],
                "suggested_matches": [],
            }
        return {
            "app_name": best_matches[0][0],
            "match": best_matches[0][1],
            "ambiguous_matches": [],
            "suggested_matches": [],
        }

    if ambiguous_matches:
        ambiguous_matches.sort(key=lambda item: (-item[1].score, item[1].label.casefold(), item[1].package_name))
        return {
            "app_name": ambiguous_matches[0][0],
            "match": None,
            "ambiguous_matches": _dedupe_app_matches([match for _, match in ambiguous_matches])[:5],
            "suggested_matches": [],
        }

    suggested_matches.sort(key=lambda item: (-item[1].score, item[1].label.casefold(), item[1].package_name))
    return {
        "app_name": app_names[0],
        "match": None,
        "ambiguous_matches": [],
        "suggested_matches": _dedupe_app_matches([match for _, match in suggested_matches])[:5],
    }


def _dedupe_app_matches(matches: List[Any]) -> List[Any]:
    by_package_name = {}
    for match in matches:
        existing = by_package_name.get(match.package_name)
        if existing is None or match.score > existing.score:
            by_package_name[match.package_name] = match
    return list(by_package_name.values())


def _serialize_app_matches(matches: List[Any]) -> List[Dict[str, Any]]:
    return [
        {
            "label": match.label,
            "package_name": match.package_name,
            "score": round(match.score, 4),
        }
        for match in matches
    ]


def _looks_like_stopwatch_command(text: str) -> bool:
    normalized = normalized_lower(text)
    return "kronometre" in normalized or "stopwatch" in normalized


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
