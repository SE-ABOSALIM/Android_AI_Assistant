from typing import Iterable, Optional

from V3.patterns.duration_patterns import NUMBER_WORDS, SCALE_WORDS, TENS_WORDS
from V3.patterns.word_patterns import CONNECTOR_WORDS, HALF_WORDS


def normalize_number_token(token: str) -> str:
    token = token.lower().strip()
    known = token in NUMBER_WORDS or token in TENS_WORDS or token in SCALE_WORDS or token in HALF_WORDS

    if not known and token.startswith("Ã™Ë†") and len(token) > 1:
        without_conjunction = token[1:]
        if (
            without_conjunction in NUMBER_WORDS
            or without_conjunction in TENS_WORDS
            or without_conjunction in SCALE_WORDS
            or without_conjunction in HALF_WORDS
        ):
            return without_conjunction

    return token


def words_to_number(tokens: Iterable[str], allow_article_number: bool = True) -> Optional[float]:
    total = 0.0
    current = 0.0
    seen_number = False
    seen_half = False

    normalized_tokens = [normalize_number_token(token) for token in tokens]
    if not normalized_tokens:
        return None
    meaningful_tokens = [token for token in normalized_tokens if token not in {"and", "ve", "Ã™Ë†"}]

    for token in normalized_tokens:
        if token in {"a", "an"}:
            if allow_article_number and len(meaningful_tokens) == 1:
                current += 1
                seen_number = True
            continue

        if token in CONNECTOR_WORDS:
            continue

        if token in HALF_WORDS:
            seen_half = True
            seen_number = True
            continue

        if token in NUMBER_WORDS:
            current += NUMBER_WORDS[token]
            seen_number = True
        elif token in TENS_WORDS:
            current += TENS_WORDS[token]
            seen_number = True
        elif token in SCALE_WORDS:
            if current == 0:
                current = 1
            current *= SCALE_WORDS[token]
            total += current
            current = 0
            seen_number = True
        else:
            return None

    if seen_half:
        current += 0.5

    value = total + current
    if seen_number and value > 0:
        return value

    return None
