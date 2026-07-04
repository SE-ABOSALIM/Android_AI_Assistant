from typing import Any, Dict


def normalize_model_json(model_json: Dict[str, Any]) -> Dict[str, Any]:
    intent = model_json.get("intent", "UNKNOWN_COMMAND")
    parameters = model_json.get("parameters") or {}
    normalized_intent = str(intent or "UNKNOWN_COMMAND").upper()
    normalized_parameters = parameters if isinstance(parameters, dict) else {}

    return {
        "intent": normalized_intent,
        "parameters": normalized_parameters,
    }
