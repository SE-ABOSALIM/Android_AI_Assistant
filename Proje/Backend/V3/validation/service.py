from typing import Any, Dict, List, Optional

from V3.intents.registry import get_intent_contract, missing_required_parameters
from V3.intents.registry import get_threshold
from V3.validation.context import ValidationContext
from V3.validation.enrichers import INTENT_ENRICHERS
from V3.validation.response import build_response
from V3.validation.search_call_resolver import resolve_turkish_search_call_conflict
from V3.validation.stop_listening import is_rule_prediction


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
    has_search_input: bool = False,
) -> Dict[str, Any]:
    model_intent = str(model_intent or "UNKNOWN_COMMAND").upper()
    model_intent, model_parameters = resolve_turkish_search_call_conflict(
        original_text=original_text,
        language=language,
        intent=model_intent,
        parameters=dict(model_parameters or {}),
        has_search_input=has_search_input,
    )
    top_predictions = top_predictions or []
    if model_intent == "STOP_LISTENING" and not is_rule_prediction(raw_label):
        return build_response(
            original_text=original_text,
            language=language,
            intent="UNKNOWN_COMMAND",
            parameters={},
            accepted=False,
            missing_slots=[],
            error_code="MODEL_STOP_LISTENING_DISABLED",
            error_message="Stop listening can only be triggered by explicit rule commands.",
            needs_confirmation=False,
            confidence=confidence,
            threshold=get_threshold("UNKNOWN_COMMAND"),
            raw_label=raw_label,
            top_predictions=top_predictions,
            contract=get_intent_contract("UNKNOWN_COMMAND"),
        )

    threshold = get_threshold(model_intent)
    contract = get_intent_contract(model_intent)

    if confidence < threshold:
        return build_response(
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
            contract=get_intent_contract("UNKNOWN_COMMAND"),
        )

    if model_intent == "UNKNOWN_COMMAND":
        return build_response(
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
            contract=contract,
        )

    if contract is None:
        return build_response(
            original_text=original_text,
            language=language,
            intent=model_intent,
            parameters=dict(model_parameters or {}),
            accepted=False,
            missing_slots=[],
            error_code="UNSUPPORTED_INTENT",
            error_message=f"Backend does not support this intent yet: {model_intent}",
            needs_confirmation=True,
            confidence=confidence,
            threshold=threshold,
            raw_label=raw_label,
            top_predictions=top_predictions,
            contract=None,
        )

    context = ValidationContext(
        original_text=original_text,
        language=language,
        intent=model_intent,
        parameters=dict(model_parameters or {}),
        confidence=confidence,
        raw_label=raw_label,
        top_predictions=top_predictions,
        text_alternatives=text_alternatives,
        session_id=session_id,
        catalog_version=catalog_version,
        has_search_input=has_search_input,
    )

    enricher = INTENT_ENRICHERS.get(model_intent)
    if enricher:
        enricher(context)

    if context.error_code is None:
        context.missing_slots.extend(missing_required_parameters(contract, context.parameters))
        context.missing_slots = _dedupe_strings(context.missing_slots)

    accepted = context.error_code is None and not context.missing_slots
    error_code = context.error_code
    error_message = context.error_message

    if not accepted and error_code is None:
        error_code = "MISSING_REQUIRED_SLOT"
        error_message = "Required parameter is missing."

    return build_response(
        original_text=original_text,
        language=language,
        intent=model_intent,
        parameters=context.parameters,
        accepted=accepted,
        missing_slots=context.missing_slots,
        error_code=error_code,
        error_message=error_message,
        needs_confirmation=not accepted,
        confidence=confidence,
        threshold=threshold,
        raw_label=raw_label,
        top_predictions=top_predictions,
        contract=contract,
    )

def _dedupe_strings(values: List[str]) -> List[str]:
    result = []
    seen = set()
    for value in values:
        if value not in seen:
            result.append(value)
            seen.add(value)
    return result
