from typing import Any, Dict, List

from V3.extraction.timer.result import build_component
from V3.extraction.timer.units import unit_from_token
from V3.patterns.duration_patterns import DIGIT_DURATION_PATTERN


def extract_digit_duration_components(text: str) -> List[Dict[str, Any]]:
    components = []

    for match in DIGIT_DURATION_PATTERN.finditer(text):
        raw_value = match.group(1).replace(",", ".")
        raw_unit = match.group(2).strip().lower()
        _, unit = unit_from_token(raw_unit)

        if not unit:
            continue

        value = float(raw_value)
        component = build_component(value, unit)
        if component:
            components.append(component)

    return components
