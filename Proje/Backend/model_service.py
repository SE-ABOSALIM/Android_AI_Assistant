import json
from pathlib import Path

import torch
from transformers import AutoTokenizer, MT5ForConditionalGeneration

BASE_DIR = Path(__file__).resolve().parent
MODEL_PATH = BASE_DIR / "result_model"

tokenizer = AutoTokenizer.from_pretrained(MODEL_PATH, use_fast=False)
model = MT5ForConditionalGeneration.from_pretrained(MODEL_PATH)
model.eval()

device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
model.to(device)

VALID_LANGS = {"TR", "EN", "AR"}


def safe_json_parse(text: str):
    try:
        parsed = json.loads(text)

        if not isinstance(parsed, dict):
            raise ValueError("Model output is not a JSON object")

        return {
            "intent": parsed.get("intent", "UNKNOWN"),
            "parameters": parsed.get("parameters", {}),
            "accepted": bool(parsed.get("accepted", False)),
            "missing_slots": parsed.get("missing_slots", []),
        }

    except Exception:
        return {
            "intent": "PARSE_ERROR",
            "parameters": {},
            "accepted": False,
            "missing_slots": ["json_parse_failed"],
        }


def predict_intent(text: str, language: str):
    language = language.upper().strip()
    cleaned_text = text.strip()

    if language not in VALID_LANGS:
        raise ValueError("language must be TR, EN, or AR")

    source_text = f"[{language}] {cleaned_text}"

    inputs = tokenizer(
        source_text,
        return_tensors="pt",
        truncation=True,
        max_length=96
    ).to(device)

    with torch.no_grad():
        generated_ids = model.generate(
            **inputs,
            max_length=160,
            num_beams=4,
            early_stopping=True
        )

    raw_output = tokenizer.decode(generated_ids[0], skip_special_tokens=True).strip()
    parsed = safe_json_parse(raw_output)

    return {
        "input": source_text,
        "intent": parsed["intent"],
        "parameters": parsed["parameters"],
        "accepted": parsed["accepted"],
        "missing_slots": parsed["missing_slots"],
        "raw_output": raw_output,
    }