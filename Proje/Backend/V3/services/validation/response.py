from typing import Any, Dict, List, Optional

from V3.services.intent_registry import IntentContract, is_android_supported
from V3.services.text_utils import normalize_text


def build_response(
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
    contract: Optional[IntentContract],
) -> Dict[str, Any]:
    return {
        "input": original_text,
        "normalized_input": normalize_text(original_text),
        "language": language.upper(),
        "intent": intent,
        "parameters": parameters,
        "backend_supported": _is_backend_supported_response(intent, contract),
        "android_supported": is_android_supported(contract, parameters) if contract else False,
        "parameter_contract": serialize_parameter_contract(contract),
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


def serialize_parameter_contract(contract: Optional[IntentContract]) -> Dict[str, Any]:
    if contract is None:
        return {
            "required": [],
            "one_of": [],
            "optional": [],
            "parameters": [],
        }

    return {
        "required": list(contract.required_parameters),
        "one_of": [list(group) for group in contract.required_parameter_groups],
        "optional": list(contract.optional_parameters),
        "parameters": list(contract.parameter_names),
    }


def _is_backend_supported_response(intent: str, contract: Optional[IntentContract]) -> bool:
    return contract is not None and intent != "UNKNOWN_COMMAND"
