import json
import re
from pathlib import Path

import torch
from transformers import AutoTokenizer, MT5ForConditionalGeneration

BASE_DIR = Path(__file__).resolve().parent
MODEL_PATH = BASE_DIR / "../result_model"

tokenizer = AutoTokenizer.from_pretrained(MODEL_PATH, use_fast=False)
model = MT5ForConditionalGeneration.from_pretrained(MODEL_PATH)
model.eval()

device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
model.to(device)

VALID_LANGS = {"TR", "EN", "AR"}


def normalize_text(text: str) -> str:
    text = text.lower().strip()
    text = re.sub(r"[^\w\sçğıöşüâîû]", " ", text, flags=re.UNICODE)
    text = re.sub(r"\s+", " ", text).strip()
    return text


def to_ascii_turkish(text: str) -> str:
    replacements = {
        "ç": "c",
        "ğ": "g",
        "ı": "i",
        "ö": "o",
        "ş": "s",
        "ü": "u",
        "â": "a",
        "î": "i",
        "û": "u",
    }
    for src, tgt in replacements.items():
        text = text.replace(src, tgt)
    return text


def normalize_app_name(text: str) -> str:
    text = normalize_text(text)
    text = to_ascii_turkish(text)
    text = text.replace(" ", "")
    return text


def is_probably_spelled_text(text: str) -> bool:
    """
    Harf harf söylenmiş adayları yakalamaya çalışır.
    Örnek:
    - Y O U T U B E
    - T U R K T E L E K O M
    - M Y B U S I N E S S
    """
    if not text or not isinstance(text, str):
        return False

    cleaned = text.strip()
    if not cleaned:
        return False

    tokens = cleaned.split()

    # Çok kısa şeyleri spelled sayma
    if len(tokens) < 3:
        return False

    # Her token tek karakter ise güçlü aday
    if all(len(token) == 1 for token in tokens):
        return True

    return False


def join_spelled_letters(text: str) -> str:
    """
    'Y O U T U B E' -> 'YOUTUBE'
    'T U R K T E L E K O M' -> 'TURKTELEKOM'
    """
    if not text or not isinstance(text, str):
        return text

    tokens = text.strip().split()

    if not tokens:
        return text

    if all(len(token) == 1 for token in tokens):
        return "".join(tokens)

    return text.strip()


def safe_json_parse(text: str):
    try:
        parsed = json.loads(text)

        if not isinstance(parsed, dict):
            raise ValueError("Model output is not a JSON object")

        parameters = parsed.get("parameters", {})
        if not isinstance(parameters, dict):
            parameters = {}

        missing_slots = parsed.get("missing_slots", [])
        if not isinstance(missing_slots, list):
            missing_slots = []

        return {
            "intent": parsed.get("intent", "UNKNOWN"),
            "parameters": parameters,
            "accepted": bool(parsed.get("accepted", False)),
            "missing_slots": missing_slots,
        }

    except Exception:
        return {
            "intent": "PARSE_ERROR",
            "parameters": {},
            "accepted": False,
            "missing_slots": ["json_parse_failed"],
        }


def enrich_app_name_parameters(parameters: dict) -> dict:
    """
    OPEN_APP benzeri intentlerde app_name veya spelled_app_name varsa:
    - harf harf formu tespit et
    - birleştir
    - normalized / ascii alanları ekle
    """
    enriched = dict(parameters) if isinstance(parameters, dict) else {}

    app_name = None
    source_key = None

    if isinstance(enriched.get("app_name"), str) and enriched.get("app_name").strip():
        app_name = enriched["app_name"].strip()
        source_key = "app_name"
    elif isinstance(enriched.get("spelled_app_name"), str) and enriched.get("spelled_app_name").strip():
        app_name = enriched["spelled_app_name"].strip()
        source_key = "spelled_app_name"

    if not app_name:
        return enriched

    is_spelled = is_probably_spelled_text(app_name)
    joined_app_name = join_spelled_letters(app_name)

    normalized_original = normalize_text(app_name)
    normalized_joined = normalize_text(joined_app_name)

    ascii_original = to_ascii_turkish(normalized_original)
    ascii_joined = to_ascii_turkish(normalized_joined)

    compact_original = ascii_original.replace(" ", "")
    compact_joined = ascii_joined.replace(" ", "")

    # Ana alanı joined haliyle overwrite etme. Orijinali koru.
    enriched["app_name_source_key"] = source_key
    enriched["app_name_is_spelled"] = is_spelled
    enriched["app_name_joined"] = joined_app_name
    enriched["app_name_original_normalized"] = normalized_original
    enriched["app_name_joined_normalized"] = compact_joined
    enriched["app_name_ascii"] = ascii_joined

    # Android tarafı tek alanla çalışsın diye ortak normalize alan
    enriched["app_name_normalized"] = compact_joined if is_spelled else compact_original

    # Eğer spelled_app_name geldi ama app_name yoksa ortak alan olarak koy
    if "app_name" not in enriched or not isinstance(enriched.get("app_name"), str) or not enriched.get("app_name").strip():
        enriched["app_name"] = joined_app_name if is_spelled else app_name

    return enriched


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

    parameters = parsed["parameters"]

    # App açma intentlerinde spelling fallback desteği
    if parsed["intent"] in {"OPEN_APP", "SPELL_APP_NAME"}:
        parameters = enrich_app_name_parameters(parameters)

    return {
        "input": source_text,
        "intent": parsed["intent"],
        "parameters": parameters,
        "accepted": parsed["accepted"],
        "missing_slots": parsed["missing_slots"],
        "raw_output": raw_output,
    }