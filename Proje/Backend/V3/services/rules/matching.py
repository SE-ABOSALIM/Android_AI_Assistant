import re
from typing import Iterable


def matches_any(text: str, patterns: Iterable[str]) -> bool:
    return any(contains_phrase(text, pattern) for pattern in patterns)


def matches_regex(text: str, patterns: Iterable[str]) -> bool:
    return any(re.search(pattern, text) for pattern in patterns)


def contains_phrase(text: str, phrase: str) -> bool:
    pattern = rf"(?<!\w){re.escape(phrase)}(?!\w)"
    return re.search(pattern, text, flags=re.IGNORECASE) is not None
