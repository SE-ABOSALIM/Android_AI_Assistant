import asyncio
from typing import Any, Dict, Optional

from V3.database.custom_command_repository import find_custom_command_by_spoken_name
from V3.intents.registry import get_intent_contract, get_threshold
from V3.validation.response import build_response


_PREFIXES = {
    "EN": (
        "custom command",
        "custom command:",
        "run custom command",
        "run custom command:",
    ),
    "TR": (
        "özel komut",
        "özel komut:",
        "ozel komut",
        "ozel komut:",
    ),
    "AR": (
        "امر مخصص",
        "امر مخصص:",
        "أمر مخصص",
        "أمر مخصص:",
        "نفذ امر مخصص",
        "نفذ أمر مخصص",
    ),
}


def try_build_custom_command_response(
    *,
    text: str,
    language: str,
    device_id: Optional[str],
) -> Optional[Dict[str, Any]]:
    command_name = _extract_custom_command_name(text, language)
    if command_name is None:
        return None

    command = _run_lookup(device_id=device_id, language=language, command_name=command_name)
    if command is None:
        return build_response(
            original_text=text,
            language=language,
            intent="UNKNOWN_COMMAND",
            parameters={"custom_command_name": command_name},
            accepted=False,
            missing_slots=[],
            error_code="CUSTOM_COMMAND_NOT_FOUND",
            error_message="Custom command was not found.",
            needs_confirmation=False,
            confidence=1.0,
            threshold=get_threshold("UNKNOWN_COMMAND"),
            raw_label="RULE::CUSTOM_COMMAND_PREFIX",
            top_predictions=[],
            contract=get_intent_contract("UNKNOWN_COMMAND"),
        )

    return build_response(
        original_text=text,
        language=language,
        intent="RUN_CUSTOM_COMMAND",
        parameters={
            "custom_command_id": command["id"],
            "custom_command_name": command["name"],
            "custom_command_steps": command.get("steps") or [],
        },
        accepted=True,
        missing_slots=[],
        error_code=None,
        error_message=None,
        needs_confirmation=False,
        confidence=1.0,
        threshold=get_threshold("RUN_CUSTOM_COMMAND"),
        raw_label="RULE::CUSTOM_COMMAND_PREFIX",
        top_predictions=[],
        contract=get_intent_contract("RUN_CUSTOM_COMMAND"),
    )


def _run_lookup(*, device_id: Optional[str], language: str, command_name: str) -> Optional[Dict[str, Any]]:
    try:
        return asyncio.run(
            find_custom_command_by_spoken_name(
                device_id=device_id,
                language=language,
                spoken_name=command_name,
            )
        )
    except RuntimeError:
        return None


def _extract_custom_command_name(text: str, language: str) -> Optional[str]:
    normalized = " ".join(str(text or "").strip().split())
    if not normalized:
        return None

    language = str(language or "TR").strip().upper()
    candidates = _PREFIXES.get(language, ()) + _PREFIXES["EN"]
    lower_text = normalized.lower()
    for prefix in candidates:
        clean_prefix = prefix.strip().lower()
        if lower_text == clean_prefix:
            return ""
        if lower_text.startswith(clean_prefix + " "):
            return normalized[len(prefix):].strip(" :：")
        if lower_text.startswith(clean_prefix):
            suffix = normalized[len(prefix):].strip(" :：")
            if suffix:
                return suffix
    return None
