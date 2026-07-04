from typing import Any, Dict, List

from V3.config import MAX_LENGTH
from V3.services.model_service.labels import label_to_json
from V3.services.model_service.runtime import get_model_bundle
from V3.utils.text import normalize_text


def predict_model(text: str, language: str) -> Dict[str, Any]:
    """
    Runs model inference and returns the best prediction plus top-5 debug data.
    """

    torch, tokenizer, model, device = get_model_bundle()
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
