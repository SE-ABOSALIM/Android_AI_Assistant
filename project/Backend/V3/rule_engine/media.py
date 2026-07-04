from typing import Any, Dict, Optional

from V3.patterns.commands.media import (
    MEDIA_PLAYBACK_EXACT_PATTERNS,
    MEDIA_PLAYBACK_PHRASE_PATTERNS,
)
from V3.rule_engine.context import RuleContext
from V3.rule_engine.matching import resolve_group_match
from V3.rule_engine.result import command, unknown
from V3.utils.language import patterns_for_language
from V3.utils.text import normalized_lower


def media_command(context: RuleContext) -> Optional[Dict[str, Any]]:
    exact_match = _resolve_exact_match(
        context.original,
        context.language,
        MEDIA_PLAYBACK_EXACT_PATTERNS,
    )
    if exact_match:
        return command(
            "SET_MEDIA_PLAYBACK",
            f"media_playback_{exact_match}",
            {"media_action": exact_match},
        )

    phrase_match = resolve_group_match(
        context.original,
        context.language,
        MEDIA_PLAYBACK_PHRASE_PATTERNS,
    )
    if phrase_match.ambiguous:
        return unknown("ambiguous_media_playback")

    media_action = phrase_match.matched_key
    if media_action:
        return command(
            "SET_MEDIA_PLAYBACK",
            f"media_playback_{media_action}",
            {"media_action": media_action},
        )

    return None


def _resolve_exact_match(
    text: str,
    language: str,
    grouped_patterns: Dict[str, Dict[str, list[str]]],
) -> Optional[str]:
    normalized_text = normalized_lower(text)
    matched_actions = []

    for action, language_patterns in grouped_patterns.items():
        for pattern in patterns_for_language(language_patterns, language):
            if normalized_text == normalized_lower(pattern):
                matched_actions.append(action)
                break

    if len(matched_actions) == 1:
        return matched_actions[0]
    return None
