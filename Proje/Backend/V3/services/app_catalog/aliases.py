from typing import List, Optional, Set

from V3.services.app_catalog.arabic_phonetic import _arabic_phonetic_aliases
from V3.services.app_catalog.text_utils import _has_text, _normalize_words, _split_compound_words
from V3.services.app_catalog.text_variants import _expand_text_variants


def _build_match_aliases(
    label: str,
    package_name: str,
    aliases: List[str],
    include_arabic_phonetic_aliases: bool = False,
) -> List[str]:
    display_aliases: Set[str] = set()

    for raw_alias in [label, *aliases]:
        if _has_text(raw_alias):
            display_aliases.add(str(raw_alias))
            display_aliases.add(_split_compound_words(str(raw_alias)))

    raw_aliases: Set[str] = set(display_aliases)
    generated_arabic_aliases: Set[str] = set()
    if include_arabic_phonetic_aliases:
        for display_alias in display_aliases:
            generated_arabic_aliases.update(_arabic_phonetic_aliases(display_alias))

    raw_aliases.update(_package_aliases(package_name))

    expanded: Set[str] = set()
    for raw_alias in raw_aliases:
        for normalized, _ in _expand_text_variants(raw_alias):
            if normalized:
                expanded.add(normalized)
                expanded.add(normalized.replace(" ", ""))

    for generated_alias in generated_arabic_aliases:
        normalized = _normalize_words(generated_alias)
        if normalized:
            expanded.add(normalized)
            expanded.add(normalized.replace(" ", ""))

    return sorted(expanded)

def _is_arabic_language(language: Optional[str]) -> bool:
    return _has_text(language) and str(language).strip().casefold().replace("_", "-").startswith("ar")

def _package_aliases(package_name: str) -> Set[str]:
    aliases: Set[str] = set()
    normalized_package = _normalize_words(str(package_name).replace(".", " "))
    tokens = [token for token in normalized_package.split() if token and token not in _package_stopwords()]

    aliases.add(normalized_package)
    if tokens:
        aliases.add(" ".join(tokens))

    for token in tokens:
        aliases.add(token)

    if tokens:
        aliases.add(tokens[-1])

    return aliases

def _package_stopwords() -> Set[str]:
    return {
        "com",
        "org",
        "net",
        "android",
        "app",
        "apps",
        "mobile",
        "client",
        "google",
        "microsoft",
    }
