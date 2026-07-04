from typing import Any, Dict, Optional

from V3.patterns.commands.system_settings import SOUND_MODE_PATTERNS, STATE_INTENT_PATTERNS
from V3.rule_engine.context import RuleContext
from V3.rule_engine.matching import matched_language_keys, resolve_group_match
from V3.rule_engine.result import command, unknown


def system_settings_command(context: RuleContext) -> Optional[Dict[str, Any]]:
    state_result = _state_command(context)
    if state_result is not None:
        return state_result

    sound_mode = _matched_sound_mode(context)
    if sound_mode == "ambiguous":
        return unknown("ambiguous_sound_mode")
    if sound_mode:
        return command("SET_SOUND_MODE", f"sound_mode_{sound_mode}", {"sound_mode": sound_mode})

    return None


def _state_command(context: RuleContext) -> Optional[Dict[str, Any]]:
    matches = []
    for intent, state_patterns in STATE_INTENT_PATTERNS.items():
        for state in matched_language_keys(context.original, context.language, state_patterns):
            matches.append((intent, state))

    if len(matches) > 1:
        return unknown("ambiguous_system_setting")

    if matches:
        intent, state = matches[0]
        return command(intent, f"{intent.lower()}_{state}", {"state": state})

    return None


def _matched_sound_mode(context: RuleContext) -> Optional[str]:
    sound_mode_match = resolve_group_match(context.original, context.language, SOUND_MODE_PATTERNS)
    if sound_mode_match.ambiguous:
        return "ambiguous"

    return sound_mode_match.matched_key
