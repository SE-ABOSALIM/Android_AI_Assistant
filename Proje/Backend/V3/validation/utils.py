from typing import List


def dedupe_strings(values: List[str]) -> List[str]:
    result = []
    seen = set()
    for value in values:
        if value not in seen:
            result.append(value)
            seen.add(value)
    return result
