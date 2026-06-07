from typing import Optional

from V3.utils.text import normalized_lower
from V3.patterns.extraction.photo import (
    FRONT_CAMERA_KEYWORDS,
    BACK_CAMERA_KEYWORDS
)

def extract_photo_camera(text: str) -> Optional[str]:
    normalized = normalized_lower(text)

    if any(keyword in normalized for keyword in FRONT_CAMERA_KEYWORDS):
        return "front"
    if any(keyword in normalized for keyword in BACK_CAMERA_KEYWORDS):
        return "back"
    return None
