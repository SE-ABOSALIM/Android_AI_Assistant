from typing import Iterable, List, Mapping, Union


LanguagePatterns = Union[Iterable[str], Mapping[str, Iterable[str]]]


def language_key(language: str) -> str:
    value = str(language or "").strip().upper()
    if value.startswith("TR"):
        return "TR"
    if value.startswith("AR"):
        return "AR"
    if value.startswith("EN"):
        return "EN"
    return value or "EN"


def patterns_for_language(patterns: LanguagePatterns, language: str) -> List[str]:
    if not isinstance(patterns, Mapping):
        return list(patterns)

    key = language_key(language)
    selected = list(patterns.get(key, []))
    selected.extend(patterns.get("ALL", []))
    return selected
