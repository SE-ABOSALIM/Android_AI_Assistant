from typing import Callable, List, Optional

from V3.rule_engine.context import RuleContext
from V3.rule_engine.apps import app_command
from V3.rule_engine.display import display_command
from V3.rule_engine.gestures import gesture_command
from V3.rule_engine.navigation import navigation_command
from V3.rule_engine.system_settings import system_settings_command
from V3.rule_engine.text_controls import text_control_command
from V3.rule_engine.timer import timer_command
from V3.rule_engine.volume import volume_command


RuleHandler = Callable[[RuleContext], Optional[dict]]


RULE_HANDLERS: List[RuleHandler] = [
    display_command,
    system_settings_command,
    volume_command,
    navigation_command,
    gesture_command,
    text_control_command,
    timer_command,
    app_command,
]
