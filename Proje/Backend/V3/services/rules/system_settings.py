from typing import Any, Dict, Optional

from V3.patterns.commands.system_settings import SOUND_MODE_PATTERNS, STATE_INTENT_PATTERNS
from V3.services.rules.context import RuleContext
from V3.services.rules.matching import matches_any
from V3.services.rules.result import command, unknown


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
        for state, patterns in state_patterns.items():
            if matches_any(context.normalized, patterns):
                matches.append((intent, state))

    if len(matches) > 1:
        return unknown("ambiguous_system_setting")

    if matches:
        intent, state = matches[0]
        return command(intent, f"{intent.lower()}_{state}", {"state": state})

    return None


def _matched_sound_mode(context: RuleContext) -> Optional[str]:
    matched_modes = {
        mode
        for mode, patterns in SOUND_MODE_PATTERNS.items()
        if matches_any(context.normalized, patterns)
    }

    if len(matched_modes) > 1:
        return "ambiguous"

    return next(iter(matched_modes), None)
