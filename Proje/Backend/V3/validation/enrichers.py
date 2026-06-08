from typing import Callable, Dict

from V3.extraction.alarm import extract_alarm
from V3.extraction.click import (
    extract_click_position,
    extract_click_target,
)
from V3.extraction.contact import extract_contact_name
from V3.extraction.photo import extract_photo_camera
from V3.extraction.text import (
    extract_alarm_text,
    extract_search_query,
    extract_write_text,
)
from V3.extraction.timer import extract_timer
from V3.utils.text import normalized_lower
from V3.validation.app_matching import enrich_app_command
from V3.validation.context import ValidationContext
from V3.validation.stop_listening import has_enough_stop_listening_words, should_accept_stop_listening


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
    if not context.parameters.get("target_text"):
        target_text = extract_click_target(context.original_text, context.language)
        if target_text:
            context.parameters["target_text"] = target_text

    if not context.parameters.get("position"):
        position = extract_click_position(context.original_text, context.language)
        if position:
            context.parameters["position"] = position


def enrich_alarm(context: ValidationContext) -> None:
    alarm_params = extract_alarm(context.original_text, context.language)
    for key, value in alarm_params.items():
        if value is None:
            continue

        if key in {"alarm_hour", "alarm_minute", "period"} and alarm_params.get("period"):
            context.parameters[key] = value
            continue

        if not context.parameters.get(key):
            context.parameters[key] = value

    alarm_text = extract_alarm_text(context.original_text, context.language)
    if alarm_text and not context.parameters.get("alarm_text"):
        context.parameters["alarm_text"] = alarm_text


def enrich_take_photo(context: ValidationContext) -> None:
    if context.parameters.get("camera"):
        return

    camera = extract_photo_camera(context.original_text)
    if camera:
        context.parameters["camera"] = camera


def enrich_stop_listening(context: ValidationContext) -> None:
    if not has_enough_stop_listening_words(context.original_text):
        context.reject(
            "STOP_LISTENING_TOO_SHORT",
            "Stop listening cannot be triggered by a single word.",
        )
        return

    if should_accept_stop_listening(
        context.confidence,
        context.raw_label,
        context.top_predictions,
    ):
        return

    context.reject(
        "WEAK_STOP_LISTENING_COMMAND",
        "Stop listening requires a strong and unambiguous model prediction.",
    )


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
    "STOP_LISTENING": enrich_stop_listening,
    "TAKE_PHOTO": enrich_take_photo,
    "UNINSTALL_APP": enrich_app_command,
    "WRITE_TEXT": enrich_write_text,
}
