from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent

MODEL_DIR = BASE_DIR / "models/result_model"

MAX_LENGTH = 96

DEFAULT_CONFIDENCE_THRESHOLD = 0.80
