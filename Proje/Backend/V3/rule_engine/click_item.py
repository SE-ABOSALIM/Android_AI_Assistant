from typing import Any, Dict, Optional

from V3.extraction.click import extract_click_position, extract_click_target
from V3.rule_engine.context import RuleContext
from V3.rule_engine.result import command
from V3.utils.text import normalized_lower


def click_item_command(context: RuleContext) -> Optional[Dict[str, Any]]:
    if not _has_click_action(context):
        return None

    target_text = extract_click_target(context.original, context.language)
    if not target_text:
        return None

    parameters: Dict[str, Any] = {}
    parameters["target_text"] = target_text

    position = extract_click_position(context.original, context.language)
    if position:
        parameters["position"] = position

    return command("CLICK_ITEM", "click_item", parameters)


def _has_click_action(context: RuleContext) -> bool:
    normalized = f" {normalized_lower(context.original)} "
    if _looks_like_english_top_click(context) or _looks_like_turkish_merged_click(context):
        return True

    return any(
        action in normalized
        for action in (
            " tap ",
            " click ",
            " press ",
            " tikla ",
            " tiklayin ",
            " bas ",
            " basin ",
            " \u0627\u0636\u063a\u0637 ",
            " \u0627\u0646\u0642\u0631 ",
        )
    )


def _looks_like_english_top_click(context: RuleContext) -> bool:
    if not str(context.language or "").upper().startswith("EN"):
        return False

    words = normalized_lower(context.original).split()
    return len(words) >= 2 and words[0] == "top"


def _looks_like_turkish_merged_click(context: RuleContext) -> bool:
    if not str(context.language or "").upper().startswith("TR"):
        return False

    words = normalized_lower(context.original).split()
    if not words:
        return False

    last_word = words[-1]
    if last_word in {"sebas", "sebasin"}:
        return len(words) >= 2

    return last_word == "kapatamaz" or last_word.endswith(
        ("amaz", "emez", "yamaz", "yemez")
    )
