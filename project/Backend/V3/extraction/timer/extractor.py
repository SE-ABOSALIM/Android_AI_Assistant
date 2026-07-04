from typing import Any, Dict

from V3.extraction.timer.digits import extract_digit_duration_components
from V3.extraction.timer.result import build_result_from_seconds
from V3.extraction.timer.words import extract_word_duration_components
from V3.utils.text import normalize_text, normalized_lower


def extract_timer(text: str) -> Dict[str, Any]:
    original = normalize_text(text)
    normalized = normalized_lower(original)

    components = extract_digit_duration_components(normalized)
    components.extend(extract_word_duration_components(normalized, include_implicit=not components))

    if not components:
        return {}

    if len(components) == 1:
        return components[0]["result"]

    total_seconds = sum(component["seconds"] for component in components)
    return build_result_from_seconds(total_seconds)
