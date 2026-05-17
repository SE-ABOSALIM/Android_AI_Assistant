from typing import Any, Dict, List, Optional

from V3.extraction.timer.numbers import normalize_number_token, words_to_number
from V3.extraction.timer.result import build_component
from V3.extraction.timer.units import unit_from_token
from V3.patterns.duration_patterns import IMPLICIT_UNIT_VALUES
from V3.patterns.word_patterns import CONNECTOR_WORDS, HALF_WORDS, WORD_TOKEN_PATTERN


def extract_word_duration_components(text: str, include_implicit: bool) -> List[Dict[str, Any]]:
    components = []
    tokens = WORD_TOKEN_PATTERN.findall(text)
    previous_unit_index = -1
    consumed_post_unit_half_indexes = set()

    for i, raw_unit in enumerate(tokens):
        normalized_unit, unit = unit_from_token(raw_unit)
        if not unit:
            continue

        added_component = False
        value = _number_before_unit(tokens, previous_unit_index + 1, i, allow_article_number=include_implicit)
        if value is not None:
            component = build_component(value, unit)
            if component:
                components.append(component)
                added_component = True
            previous_unit_index = i

        elif include_implicit:
            implicit_value = IMPLICIT_UNIT_VALUES.get(normalized_unit)
            if implicit_value:
                component = build_component(implicit_value, unit)
                if component:
                    components.append(component)
                    added_component = True

        half_index = _post_unit_half_index(tokens, i)
        if half_index is not None and half_index not in consumed_post_unit_half_indexes:
            if added_component or not include_implicit:
                half_component = build_component(0.5, unit)
                if half_component:
                    components.append(half_component)
                    consumed_post_unit_half_indexes.add(half_index)

        previous_unit_index = i

    return components


def _number_before_unit(
    tokens: List[str],
    start_index: int,
    unit_index: int,
    allow_article_number: bool = True,
) -> Optional[float]:
    for start in range(max(start_index, unit_index - 5), unit_index):
        value = words_to_number(tokens[start:unit_index], allow_article_number=allow_article_number)
        if value is not None:
            return value

    return None


def _post_unit_half_index(tokens: List[str], unit_index: int) -> Optional[int]:
    for index in range(unit_index + 1, min(len(tokens), unit_index + 5)):
        token = normalize_number_token(tokens[index])
        if token in CONNECTOR_WORDS:
            continue

        if token in HALF_WORDS and not _next_meaningful_token_is_unit(tokens, index + 1):
            return index

        return None

    return None


def _next_meaningful_token_is_unit(tokens: List[str], start_index: int) -> bool:
    for index in range(start_index, min(len(tokens), start_index + 4)):
        token = normalize_number_token(tokens[index])
        if token in CONNECTOR_WORDS:
            continue

        _, unit = unit_from_token(token)
        return unit is not None

    return False
