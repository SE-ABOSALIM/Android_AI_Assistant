from typing import Any, Dict, Optional

from V3.extraction.click import extract_click_index, extract_click_position, extract_click_target
from V3.rule_engine.context import RuleContext
from V3.rule_engine.result import command
from V3.utils.text import normalized_lower


def click_item_command(context: RuleContext) -> Optional[Dict[str, Any]]:
    if not _has_click_action(context.original):
        return None

    target_text = extract_click_target(context.original, context.language)
    target_index = extract_click_index(context.original)
    if not target_text and not target_index:
        return None

    parameters: Dict[str, Any] = {}
    if target_text:
        parameters["target_text"] = target_text
    if target_index:
        parameters["target_index"] = target_index

    position = extract_click_position(context.original, context.language)
    if position:
        parameters["position"] = position

    return command("CLICK_ITEM", "click_item", parameters)


def _has_click_action(text: str) -> bool:
    normalized = f" {normalized_lower(text)} "
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
