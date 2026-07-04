from typing import Any, Dict, Optional

from V3.extraction.alarm import extract_alarm, has_alarm_command_signal
from V3.rule_engine.context import RuleContext
from V3.rule_engine.result import command


def alarm_command(context: RuleContext) -> Optional[Dict[str, Any]]:
    if not has_alarm_command_signal(context.original, context.language):
        return None

    alarm_params = extract_alarm(context.original, context.language)
    if "alarm_hour" not in alarm_params:
        return None

    return command("SET_ALARM", "set_alarm", alarm_params)
