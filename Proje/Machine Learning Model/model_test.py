import json
import torch
from transformers import AutoTokenizer, MT5ForConditionalGeneration

MODEL_DIR = "result_model"
DEVICE = "cuda" if torch.cuda.is_available() else "cpu"

tokenizer = AutoTokenizer.from_pretrained(MODEL_DIR, use_fast=False)
model = MT5ForConditionalGeneration.from_pretrained(MODEL_DIR).to(DEVICE)
model.eval()

def predict(text: str, lang: str):
    source = f"[{lang.strip().upper()}] {text.strip()}"
    inputs = tokenizer(
        source,
        return_tensors="pt",
        truncation=True,
        max_length=64
    ).to(DEVICE)

    with torch.no_grad():
        outputs = model.generate(
            **inputs,
            max_length=128,
            num_beams=4,
            early_stopping=True
        )

    decoded = tokenizer.decode(outputs[0], skip_special_tokens=True).strip()

    try:
        parsed = json.loads(decoded)
    except json.JSONDecodeError:
        parsed = {
            "intent": "PARSE_ERROR",
            "parameters": {},
            "accepted": False,
            "missing_slots": ["json_parse_failed"],
            "raw_output": decoded
        }

    return parsed


tests = [
    ("open WhatsApp", "en"),
    ("sesi azalt", "tr"),
    ("camera aç", "tr"),
    ("set timer for 5 seconds", "en"),
    ("اقفل الصوت", "ar"),
]

for text, lang in tests:
    result = predict(text, lang)
    print("=" * 60)
    print("INPUT:", f"[{lang}] {text}")
    print(json.dumps(result, ensure_ascii=False, indent=2))