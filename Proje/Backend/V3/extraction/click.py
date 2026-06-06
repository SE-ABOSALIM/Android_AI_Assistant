import re
from typing import Optional

from V3.extraction.common import clean_free_text, extract_first_match
from V3.patterns.extraction.click import CLICK_TARGET_PATTERNS
from V3.utils.language import patterns_for_language
from V3.utils.text import normalize_text, normalized_lower


CLICK_POSITION_ALIASES = {
    "top": (
        "top",
        "above",
        "upper",
        "ust",
        "ustteki",
        "yukari",
        "yukaridaki",
        "\u0641\u0648\u0642",
        "\u0627\u0644\u0627\u0639\u0644\u0649",
    ),
    "bottom": (
        "bottom",
        "below",
        "lower",
        "alt",
        "alttaki",
        "asagi",
        "asagidaki",
        "\u062a\u062d\u062a",
        "\u0627\u0644\u0627\u0633\u0641\u0644",
    ),
    "left": (
        "left",
        "soldaki",
        "sol",
        "\u064a\u0633\u0627\u0631",
        "\u0627\u0644\u064a\u0633\u0627\u0631",
    ),
    "right": (
        "right",
        "sagdaki",
        "sag",
        "\u064a\u0645\u064a\u0646",
        "\u0627\u0644\u064a\u0645\u064a\u0646",
    ),
    "center": (
        "center",
        "middle",
        "ortadaki",
        "orta",
        "\u0627\u0644\u0648\u0633\u0637",
    ),
}

CLICK_INDEX_ALIASES = {
    1: (
        "1",
        "first",
        "one",
        "birinci",
        "ilk",
        "\u0627\u0648\u0644",
        "\u0627\u0644\u0627\u0648\u0644",
        "\u0648\u0627\u062d\u062f",
    ),
    2: (
        "2",
        "second",
        "two",
        "ikinci",
        "\u062b\u0627\u0646\u064a",
        "\u0627\u0644\u062b\u0627\u0646\u064a",
        "\u0627\u062b\u0646\u064a\u0646",
    ),
    3: (
        "3",
        "third",
        "three",
        "ucuncu",
        "\u062b\u0627\u0644\u062b",
        "\u0627\u0644\u062b\u0627\u0644\u062b",
        "\u062b\u0644\u0627\u062b\u0629",
    ),
    4: (
        "4",
        "fourth",
        "four",
        "dorduncu",
        "\u0631\u0627\u0628\u0639",
        "\u0627\u0644\u0631\u0627\u0628\u0639",
        "\u0627\u0631\u0628\u0639\u0629",
    ),
    5: (
        "5",
        "fifth",
        "five",
        "besinci",
        "\u062e\u0627\u0645\u0633",
        "\u0627\u0644\u062e\u0627\u0645\u0633",
        "\u062e\u0645\u0633\u0629",
    ),
    6: (
        "6",
        "sixth",
        "six",
        "altinci",
        "\u0633\u0627\u062f\u0633",
        "\u0627\u0644\u0633\u0627\u062f\u0633",
        "\u0633\u062a\u0629",
    ),
    7: (
        "7",
        "seventh",
        "seven",
        "yedinci",
        "\u0633\u0627\u0628\u0639",
        "\u0627\u0644\u0633\u0627\u0628\u0639",
        "\u0633\u0628\u0639\u0629",
    ),
    8: (
        "8",
        "eighth",
        "eight",
        "sekizinci",
        "\u062b\u0627\u0645\u0646",
        "\u0627\u0644\u062b\u0627\u0645\u0646",
        "\u062b\u0645\u0627\u0646\u064a\u0629",
    ),
    9: (
        "9",
        "ninth",
        "nine",
        "dokuzuncu",
        "\u062a\u0627\u0633\u0639",
        "\u0627\u0644\u062a\u0627\u0633\u0639",
        "\u062a\u0633\u0639\u0629",
    ),
    10: (
        "10",
        "tenth",
        "ten",
        "onuncu",
        "\u0639\u0627\u0634\u0631",
        "\u0627\u0644\u0639\u0627\u0634\u0631",
        "\u0639\u0634\u0631\u0629",
    ),
}


def extract_click_target(text: str, language: str) -> Optional[str]:
    normalized = normalized_lower(normalize_text(text))

    target = extract_first_match(
        normalized,
        patterns_for_language(CLICK_TARGET_PATTERNS, language)
    )
    return clean_free_text(_clean_click_target(target, language))


def extract_click_index(text: str) -> Optional[int]:
    normalized = normalized_lower(normalize_text(_translate_arabic_indic_digits(text)))
    digit_match = re.search(r"(?<!\d)(10|[1-9])(?:\.|inci|uncu|nci|rd|nd|st|th)?(?!\d)", normalized)
    if digit_match:
        return int(digit_match.group(1))

    padded = f" {normalized} "
    for index, aliases in CLICK_INDEX_ALIASES.items():
        for alias in aliases:
            normalized_alias = normalized_lower(alias)
            if re.search(rf"(?<!\w){re.escape(normalized_alias)}\w*(?!\w)", padded):
                return index
    return None


def extract_click_position(text: str) -> Optional[str]:
    normalized = normalized_lower(normalize_text(text))
    padded = f" {normalized} "
    for position, aliases in CLICK_POSITION_ALIASES.items():
        for alias in aliases:
            normalized_alias = normalized_lower(alias)
            if f" {normalized_alias} " in padded:
                return position
    return None


def _clean_click_target(target: Optional[str], language: str) -> Optional[str]:
    if not target:
        return None

    cleaned = f" {normalized_lower(target)} "
    for aliases in CLICK_POSITION_ALIASES.values():
        for alias in aliases:
            normalized_alias = normalized_lower(alias)
            cleaned = cleaned.replace(f" {normalized_alias} ", " ")
    for aliases in CLICK_INDEX_ALIASES.values():
        for alias in aliases:
            normalized_alias = normalized_lower(alias)
            cleaned = re.sub(rf"(?<!\w){re.escape(normalized_alias)}\w*(?!\w)", " ", cleaned)

    words = cleaned.split()
    if not words:
        return None

    if str(language or "").upper().startswith("TR"):
        words[-1] = _strip_turkish_click_suffix(words[-1])
    return normalize_text(" ".join(words))


def _strip_turkish_click_suffix(word: str) -> str:
    for suffix in ("yla", "yle", "ya", "ye", "yi", "yu", "nu", "ni", "a", "e"):
        if len(word) > len(suffix) + 2 and word.endswith(suffix):
            return word[: -len(suffix)]
    return word


def _translate_arabic_indic_digits(text: str) -> str:
    return str(text).translate(str.maketrans(
        "\u0660\u0661\u0662\u0663\u0664\u0665\u0666\u0667\u0668\u0669"
        "\u06F0\u06F1\u06F2\u06F3\u06F4\u06F5\u06F6\u06F7\u06F8\u06F9",
        "01234567890123456789",
    ))
