import json
from pathlib import Path

import torch
from transformers import XLMRobertaTokenizer, XLMRobertaForSequenceClassification

BASE_DIR = Path(__file__).resolve().parent
MODEL_PATH = BASE_DIR / "xlmr-intent-model-best"
CALIBRATION_PATH = MODEL_PATH / "calibration.json"

tokenizer = XLMRobertaTokenizer.from_pretrained(MODEL_PATH)
model = XLMRobertaForSequenceClassification.from_pretrained(MODEL_PATH)
model.eval()

id2label = model.config.id2label

temperature = 1.0
if CALIBRATION_PATH.exists():
    with open(CALIBRATION_PATH, "r", encoding="utf-8") as f:
        temperature = float(json.load(f).get("temperature", 1.0))

THRESHOLD = 0.50

def predict_intent(text: str, language: str):
    model_input = f"[{language}] {text}"

    inputs = tokenizer(
        model_input,
        return_tensors="pt",
        truncation=True,
        max_length=64
    )

    with torch.no_grad():
        outputs = model(**inputs)
        probs = torch.softmax(outputs.logits / temperature, dim=1)
        pred_id = torch.argmax(probs, dim=1).item()
        confidence = probs[0][pred_id].item()

    predicted_label = id2label[pred_id]
    accepted = confidence >= THRESHOLD

    return {
        "input": model_input,
        "predicted_label": predicted_label,
        "confidence": round(confidence, 4),
        "accepted": accepted,
        "temperature": temperature,
    }