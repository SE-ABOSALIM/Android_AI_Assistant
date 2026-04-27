import json
from typing import Any, Dict, List

import torch
from transformers import AutoModelForSequenceClassification, AutoTokenizer

from V3.config import MAX_LENGTH, MODEL_DIR
from V3.services.text_utils import normalize_text


device = torch.device("cuda" if torch.cuda.is_available() else "cpu")

tokenizer = AutoTokenizer.from_pretrained(MODEL_DIR)
model = AutoModelForSequenceClassification.from_pretrained(MODEL_DIR)
model.to(device)
model.eval()

label_json_path = MODEL_DIR / "label_to_target_json.json"

if label_json_path.exists():
    with open(label_json_path, "r", encoding="utf-8") as f:
        LABEL_TO_TARGET_JSON = json.load(f)
else:
    LABEL_TO_TARGET_JSON = {}


def label_to_json(label: str) -> Dict[str, Any]:
    """
    Converts label like:
      SCROLL_SCREEN__down
      ADJUST_VOLUME__decrease
      OPEN_APP__none

    into:
      {"intent": "SCROLL_SCREEN", "parameters": {"direction": "down"}}
    """

    if label in LABEL_TO_TARGET_JSON:
        return LABEL_TO_TARGET_JSON[label]

    if "__" not in label:
        return {
            "intent": label,
            "parameters": {},
        }

    intent, param = label.split("__", 1)

    if param == "none":
        return {
            "intent": intent,
            "parameters": {},
        }

    if intent in ["SCROLL_SCREEN", "SWIPE_GESTURE"]:
        return {
            "intent": intent,
            "parameters": {
                "direction": param,
            },
        }

    if intent == "ADJUST_VOLUME":
        return {
            "intent": intent,
            "parameters": {
                "volume_action": param,
            },
        }

    return {
        "intent": intent,
        "parameters": {},
    }


def predict_model_debug(text: str, language: str) -> Dict[str, Any]:
    """
    Model prediction + top-5 debug.
    """

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
    return str(device)
