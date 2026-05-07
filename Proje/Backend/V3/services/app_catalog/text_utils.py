import re
import unicodedata
from typing import Iterable, List, Set

from V3.services.app_catalog.constants import MIN_NGRAM_MATCH_RATIO, NGRAM_SIZE


def _split_compound_words(value: str) -> str:
    text = str(value)
    text = re.sub(r"(?<=[a-z])(?=[A-Z])", " ", text)
    text = re.sub(r"(?<=[A-Za-z])(?=\d)", " ", text)
    text = re.sub(r"(?<=\d)(?=[A-Za-z])", " ", text)
    return text

def _normalize_words(value: str) -> str:
    text = str(value).casefold().replace("\u0131", "i").replace("\u0130", "i")
    text = unicodedata.normalize("NFKD", text)
    text = "".join(ch for ch in text if not unicodedata.combining(ch))
    text = re.sub(r"[^\w\s]", " ", text, flags=re.UNICODE)
    text = re.sub(r"_+", " ", text)
    return re.sub(r"\s+", " ", text).strip()

def _tokens(value: str) -> set:
    return {token for token in _normalize_words(value).split(" ") if token}

def _fast_tokens(value: str) -> Set[str]:
    return {token for token in str(value).split(" ") if token}

def _ngrams(value: str, size: int = NGRAM_SIZE) -> Set[str]:
    compact = str(value).replace(" ", "")
    if len(compact) < size:
        return {compact} if compact else set()

    return {compact[index:index + size] for index in range(len(compact) - size + 1)}

def _ngram_overlap_ratio(left: str, right: str) -> float:
    left_ngrams = _ngrams(left)
    right_ngrams = _ngrams(right)
    if not left_ngrams or not right_ngrams:
        return 0.0

    return len(left_ngrams.intersection(right_ngrams)) / min(len(left_ngrams), len(right_ngrams))

def _token_overlap(left: str, right: str) -> float:
    left_tokens = _tokens(left)
    right_tokens = _tokens(right)
    if not left_tokens or not right_tokens:
        return 0.0

    return _token_overlap_from_sets(left_tokens, right_tokens)

def _token_overlap_from_sets(left_tokens: Set[str], right_tokens: Set[str]) -> float:
    if not left_tokens or not right_tokens:
        return 0.0

    overlap = len(left_tokens.intersection(right_tokens))
    return (2.0 * overlap) / (len(left_tokens) + len(right_tokens))

def _should_compare_edit_distance(left: str, right: str, ngram_overlap: float) -> bool:
    max_length = max(len(left), len(right))
    if max_length <= 4:
        return True

    if abs(len(left) - len(right)) > max(2, int(max_length * 0.35)):
        return False

    return left[0] == right[0] or ngram_overlap >= MIN_NGRAM_MATCH_RATIO

def _minimum_score(candidate_compact: str) -> float:
    length = len(candidate_compact)
    if length <= 4:
        return 0.96
    if length <= 7:
        return 0.86
    return 0.74

def _length_penalty(left: str, right: str) -> float:
    return min(0.12, abs(len(left) - len(right)) * 0.01)

def _levenshtein_similarity(left: str, right: str) -> float:
    if not left or not right:
        return 0.0
    if left == right:
        return 1.0

    distance = _levenshtein_distance(left, right)
    return 1.0 - (distance / max(len(left), len(right)))

def _levenshtein_distance(left: str, right: str) -> int:
    previous = list(range(len(right) + 1))
    current = [0] * (len(right) + 1)

    for i, left_char in enumerate(left, start=1):
        current[0] = i
        for j, right_char in enumerate(right, start=1):
            cost = 0 if left_char == right_char else 1
            current[j] = min(
                current[j - 1] + 1,
                previous[j] + 1,
                previous[j - 1] + cost,
            )
        previous, current = current, previous

    return previous[len(right)]

def _dedupe_preserve_order(values: Iterable[str]) -> List[str]:
    result: List[str] = []
    seen: Set[str] = set()
    for value in values:
        if value and value not in seen:
            result.append(value)
            seen.add(value)
    return result

def _has_text(value: object) -> bool:
    return value is not None and str(value).strip() != ""
