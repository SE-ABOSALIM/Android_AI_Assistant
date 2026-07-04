from threading import RLock
from typing import Any, Tuple

from V3.config import MODEL_DIR


_device = None
_model = None
_tokenizer = None
_model_load_lock = RLock()


def get_device_name() -> str:
    global _device

    if _device is not None:
        return str(_device)

    try:
        import torch

        return str(torch.device("cuda" if torch.cuda.is_available() else "cpu"))
    except Exception:
        return "unknown"


def preload_model() -> None:
    get_model_bundle()


def get_model_bundle() -> Tuple[Any, Any, Any, Any]:
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
