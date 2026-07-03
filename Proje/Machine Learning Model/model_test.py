import argparse
import json
from pathlib import Path

import torch
from transformers import AutoModelForSequenceClassification, AutoTokenizer


DEFAULT_MODEL_DIR = Path(__file__).resolve().parents[1] / "Backend" / "V3" / "models" / "result_model"


def parse_args():
    parser = argparse.ArgumentParser(description="Run a command through the trained XLM-RoBERTa classifier.")
    parser.add_argument("text", help="Voice command text")
    parser.add_argument("--language", "-l", default="EN", choices=("EN", "TR", "AR"))
    parser.add_argument("--model", type=Path, default=DEFAULT_MODEL_DIR)
    return parser.parse_args()


def load_label_targets(model_dir: Path):
    path = model_dir / "label_to_target_json.json"
    if not path.exists():
        return {}
    return json.loads(path.read_text(encoding="utf-8"))


def predict(text: str, language: str, model_dir: Path):
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    tokenizer = AutoTokenizer.from_pretrained(model_dir)
    model = AutoModelForSequenceClassification.from_pretrained(model_dir).to(device)
    model.eval()

    source = f"[{language.strip().upper()}] {text.strip()}"
    inputs = tokenizer(
        source,
        return_tensors="pt",
        truncation=True,
        max_length=96,
    ).to(device)

    with torch.no_grad():
        probabilities = torch.softmax(model(**inputs).logits, dim=-1)[0]

    label_id = int(torch.argmax(probabilities).item())
    label = model.config.id2label[label_id]
    target = load_label_targets(model_dir).get(label, {})

    return {
        "label": label,
        "intent": target.get("intent"),
        "parameters": target.get("parameters", {}),
        "confidence": round(float(probabilities[label_id].item()), 6),
        "device": str(device),
    }


def main():
    args = parse_args()
    result = predict(args.text, args.language, args.model)
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
