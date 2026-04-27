from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent

MODEL_DIR = BASE_DIR / "result_model"

MAX_LENGTH = 96

DEFAULT_CONFIDENCE_THRESHOLD = 0.55

INTENT_THRESHOLDS = {
    "OPEN_APP": 0.45,
    "SET_TIMER": 0.50,
    "SCROLL_SCREEN": 0.55,
    "SWIPE_GESTURE": 0.55,
    "ADJUST_VOLUME": 0.55,
    "GO_HOME": 0.60,
    "TAKE_PHOTO": 0.60,
    "STOP_LISTENING": 0.60,
    "UNKNOWN_COMMAND": 0.50,
}
