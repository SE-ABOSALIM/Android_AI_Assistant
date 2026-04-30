from typing import Any, Dict

from V3.services.model_service import predict_model_debug
from V3.services.rule_service import rule_based_command
from V3.services.validator import validate_and_build_response


def predict_command(
    text: str,
    language: str,
    session_id: str = None,
    catalog_version: str = None,
) -> Dict[str, Any]:
    """
    Main prediction pipeline:
    1. Rule-based direct command detection
    2. ML fallback
    3. Final backend validation
    """

    rule_result = rule_based_command(text, language)

    if rule_result is not None:
        return validate_and_build_response(
            original_text=text,
            language=language,
            model_intent=rule_result["intent"],
            model_parameters=rule_result["parameters"],
            confidence=1.0,
            raw_label=f"RULE::{rule_result['rule_matched']}",
            top_predictions=[],
            session_id=session_id,
            catalog_version=catalog_version,
        )

    model_result = predict_model_debug(
        text=text,
        language=language,
    )

    return validate_and_build_response(
        original_text=text,
        language=language,
        model_intent=model_result["intent"],
        model_parameters=model_result["parameters"],
        confidence=model_result["confidence"],
        raw_label=model_result["raw_label"],
        top_predictions=model_result["top_predictions"],
        session_id=session_id,
        catalog_version=catalog_version,
    )
