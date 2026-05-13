import json
from threading import RLock
from typing import Any, Dict, List, Optional, Tuple

from V3.config import MAX_LENGTH, MODEL_DIR
from V3.utils.text import normalize_text


_device = None
_model = None
_tokenizer = None
_label_to_target_json: Optional[Dict[str, Dict[str, Any]]] = None
_model_load_lock = RLock()
_label_mapping_lock = RLock()


def label_to_json(label: str) -> Dict[str, Any]:
    """
    Converts label like:
      SCROLL_SCREEN__down
      ADJUST_VOLUME__decrease
      OPEN_APP__none

    into:
      {"intent": "SCROLL_SCREEN", "parameters": {"direction": "down"}}
    """

    label_mapping = _load_label_mapping()

    if label in label_mapping:
        return _normalize_model_json(label_mapping[label])

    if "__" not in label:
        return _normalize_model_json({
            "intent": label,
            "parameters": {},
        })

    intent, param = label.split("__", 1)

    if param == "none":
        return _normalize_model_json({
            "intent": intent,
            "parameters": {},
        })

    if "=" in param:
        key, value = param.split("=", 1)
        return _normalize_model_json({
            "intent": intent,
            "parameters": {
                key: value,
            },
        })

    if intent in ["SCROLL_SCREEN", "SWIPE_GESTURE"]:
        return _normalize_model_json({
            "intent": intent,
            "parameters": {
                "direction": param,
            },
        })

    if intent == "ADJUST_VOLUME":
        return _normalize_model_json({
            "intent": intent,
            "parameters": {
                "volume_action": param,
            },
        })

    return _normalize_model_json({
        "intent": intent,
        "parameters": {},
    })


def predict_model_debug(text: str, language: str) -> Dict[str, Any]:
    """
    Model prediction + top-5 debug.
    """

    torch, tokenizer, model, device = _get_model_bundle()
    source_text = f"[{language.upper()}] {normalize_text(text)}"

    inputs = tokenizer(
        source_text,
        return_tensors="pt",
        max_length=MAX_LENGTH,
        truncation=True,
    ).to(device)

    with torch.no_grad():
        outputs = model(**inputs)
        probs = torch.softmax(outputs.logits, dim=-1)[0]

    topk = torch.topk(probs, k=min(5, probs.shape[0]))

    top_predictions: List[Dict[str, Any]] = []

    for score, idx in zip(topk.values, topk.indices):
        label = model.config.id2label[int(idx.item())]
        top_predictions.append({
            "label": label,
            "confidence": float(score.item()),
        })

    pred_id = int(topk.indices[0].item())
    confidence = float(topk.values[0].item())
    raw_label = model.config.id2label[pred_id]

    model_json = label_to_json(raw_label)

    return {
        "intent": model_json.get("intent", "UNKNOWN_COMMAND"),
        "parameters": model_json.get("parameters", {}),
        "confidence": confidence,
        "raw_label": raw_label,
        "top_predictions": top_predictions,
    }


def get_device_name() -> str:
    global _device

    if _device is not None:
        return str(_device)

    try:
        import torch

        return str(torch.device("cuda" if torch.cuda.is_available() else "cpu"))
    except Exception:
        return "unknown"


def _get_model_bundle() -> Tuple[Any, Any, Any, Any]:
    global _device, _model, _tokenizer

    if _model is not None and _tokenizer is not None and _device is not None:
        import torch

        return torch, _tokenizer, _model, _device

    with _model_load_lock:
        if _model is not None and _tokenizer is not None and _device is not None:
            import torch

            return torch, _tokenizer, _model, _device

        import torch
        from transformers import AutoModelForSequenceClassification, AutoTokenizer

        _device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
        _tokenizer = _load_tokenizer(AutoTokenizer)
        _model = AutoModelForSequenceClassification.from_pretrained(MODEL_DIR)
        _model.to(_device)
        _model.eval()

        return torch, _tokenizer, _model, _device


def _load_tokenizer(auto_tokenizer):
    try:
        return auto_tokenizer.from_pretrained(MODEL_DIR, fix_mistral_regex=True)
    except TypeError:
        return auto_tokenizer.from_pretrained(MODEL_DIR)


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


def _normalize_model_json(model_json: Dict[str, Any]) -> Dict[str, Any]:
    intent = model_json.get("intent", "UNKNOWN_COMMAND")
    parameters = model_json.get("parameters") or {}
    normalized_intent = str(intent or "UNKNOWN_COMMAND").upper()
    normalized_parameters = parameters if isinstance(parameters, dict) else {}

    if normalized_intent == "ADJUST_VOLUME":
        normalized_parameters = _normalize_volume_parameters(normalized_parameters)

    return {
        "intent": normalized_intent,
        "parameters": normalized_parameters,
    }


def _normalize_volume_parameters(parameters: Dict[str, Any]) -> Dict[str, Any]:
    normalized = dict(parameters)
    volume_level = normalized.get("volume_level")
    if isinstance(volume_level, str) and volume_level.strip().casefold() in {"high", "maximum"}:
        normalized["volume_level"] = "max"
    return normalized
