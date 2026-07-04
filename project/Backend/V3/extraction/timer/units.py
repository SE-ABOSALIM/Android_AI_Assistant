from typing import List, Optional, Tuple

from V3.patterns.duration_patterns import UNIT_MAP


def unit_candidates(token: str) -> List[str]:
    token = token.lower().strip()
    candidates = [token]

    if token.startswith("Ã™â€žÃ™â€ž") and len(token) > 2:
        candidates.append(token[2:])
    if token.startswith("Ã˜Â¨Ã˜Â§Ã™â€ž") and len(token) > 3:
        candidates.append(token[3:])
    if token.startswith(("Ã™â€ž", "Ã˜Â¨")) and len(token) > 1:
        candidates.append(token[1:])

    for candidate in list(candidates):
        if candidate.startswith("Ã˜Â§Ã™â€ž") and len(candidate) > 2:
            candidates.append(candidate[2:])

    return candidates


def unit_from_token(token: str) -> Tuple[str, Optional[str]]:
    for candidate in unit_candidates(token):
        unit = UNIT_MAP.get(candidate)
        if unit:
            return candidate, unit

    return token, None
