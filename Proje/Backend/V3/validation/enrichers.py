from typing import Callable, Dict

from V3.extraction.contact import extract_contact_name
from V3.extraction.text import (
    extract_alarm_text,
    extract_click_target,
    extract_search_query,
    extract_write_text,
)
from V3.extraction.timer import extract_timer
from V3.utils.text import normalized_lower
from V3.validation.app_matching import enrich_app_command
from V3.validation.context import ValidationContext


def enrich_timer(context: ValidationContext) -> None:
    if _looks_like_stopwatch_command(context.original_text):
        context.reject(
            "UNSUPPORTED_STOPWATCH",
            "Stopwatch commands are not supported as timer commands.",
        )
        return

    context.parameters.update(extract_timer(context.original_text))


def enrich_contact(context: ValidationContext) -> None:
    contact_name = extract_contact_name(context.original_text, context.language)
    if contact_name:
        context.parameters["contact_name"] = contact_name


def enrich_search_query(context: ValidationContext) -> None:
    if context.parameters.get("query"):
        return

    query = extract_search_query(context.original_text, context.language)
    if query:
        context.parameters["query"] = query


def enrich_write_text(context: ValidationContext) -> None:
    if context.parameters.get("text"):
        return

    text = extract_write_text(context.original_text, context.language)
    if text:
        context.parameters["text"] = text


def enrich_click_item(context: ValidationContext) -> None:
    if context.parameters.get("target_text"):
        return

    target_text = extract_click_target(context.original_text, context.language)
    if target_text:
        context.parameters["target_text"] = target_text


def enrich_alarm(context: ValidationContext) -> None:
    if context.parameters.get("alarm_text"):
        return

    alarm_text = extract_alarm_text(context.original_text, context.language)
    if alarm_text:
        context.parameters["alarm_text"] = alarm_text


def _looks_like_stopwatch_command(text: str) -> bool:
    normalized = normalized_lower(text)
    return "kronometre" in normalized or "stopwatch" in normalized


INTENT_ENRICHERS: Dict[str, Callable[[ValidationContext], None]] = {
    "CALL_CONTACT": enrich_contact,
    "CLICK_ITEM": enrich_click_item,
    "OPEN_APP": enrich_app_command,
    "OPEN_APP_INFO": enrich_app_command,
    "SEARCH_QUERY": enrich_search_query,
    "SET_ALARM": enrich_alarm,
    "SET_TIMER": enrich_timer,
    "UNINSTALL_APP": enrich_app_command,
    "WRITE_TEXT": enrich_write_text,
}
