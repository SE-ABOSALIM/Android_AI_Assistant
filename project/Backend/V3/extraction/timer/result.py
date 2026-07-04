from typing import Any, Dict, Optional

from V3.patterns.duration_patterns import SECONDS_MULTIPLIER


def build_component(value: float, unit: str) -> Optional[Dict[str, Any]]:
    seconds = int(round(value * SECONDS_MULTIPLIER[unit]))
    if seconds <= 0:
        return None

    if float(value).is_integer():
        result = {
            "duration_value": int(value),
            "duration_unit": unit,
            "duration_seconds": seconds,
        }
    else:
        result = build_result_from_seconds(seconds)

    return {
        "seconds": seconds,
        "result": result,
    }


def build_result_from_seconds(seconds: int) -> Dict[str, Any]:
    seconds = int(seconds)

    if seconds % SECONDS_MULTIPLIER["hour"] == 0:
        return {
            "duration_value": seconds // SECONDS_MULTIPLIER["hour"],
            "duration_unit": "hour",
            "duration_seconds": seconds,
        }

    if seconds % SECONDS_MULTIPLIER["minute"] == 0:
        return {
            "duration_value": seconds // SECONDS_MULTIPLIER["minute"],
            "duration_unit": "minute",
            "duration_seconds": seconds,
        }

    return {
        "duration_value": seconds,
        "duration_unit": "second",
        "duration_seconds": seconds,
    }
