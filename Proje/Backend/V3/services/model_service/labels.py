import json
from threading import RLock
from typing import Any, Dict, Optional

from V3.config import MODEL_DIR
from V3.services.model_service.normalization import normalize_model_json


_label_to_target_json: Optional[Dict[str, Dict[str, Any]]] = None
_label_mapping_lock = RLock()


def label_to_json(label: str) -> Dict[str, Any]:
    """
    Converts model labels into the backend intent/parameter format.
    Preferred labels are loaded from label_to_target_json.json; otherwise the
    supported inline label format is INTENT__parameter=value.
    """

    label_mapping = _load_label_mapping()

    if label in label_mapping:
        return normalize_model_json(label_mapping[label])

    if "__" not in label:
        return normalize_model_json({
            "intent": label,
            "parameters": {},
        })

    intent, param = label.split("__", 1)

    if param == "none":
        return normalize_model_json({
            "intent": intent,
            "parameters": {},
        })

    if "=" in param:
        key, value = param.split("=", 1)
        return normalize_model_json({
            "intent": intent,
            "parameters": {
                key: value,
            },
        })

    return normalize_model_json({
        "intent": intent,
        "parameters": {},
    })


def _load_label_mapping() -> Dict[str, Dict[str, Any]]:
    global _label_to_target_json

    if _label_to_target_json is not None:
        return _label_to_target_json

    with _label_mapping_lock:
        if _label_to_target_json is not None:
            return _label_to_target_json

        label_json_path = MODEL_DIR / "label_to_target_json.json"
        if label_json_path.exists():
            with open(label_json_path, "r", encoding="utf-8") as f:
                _label_to_target_json = json.load(f)
        else:
            _label_to_target_json = {}

        return _label_to_target_json
