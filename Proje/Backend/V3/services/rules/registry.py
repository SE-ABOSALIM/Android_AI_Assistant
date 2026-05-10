from typing import Callable, List, Optional

from V3.services.rules.context import RuleContext
from V3.services.rules.apps import app_command
from V3.services.rules.display import display_command
from V3.services.rules.gestures import gesture_command
from V3.services.rules.navigation import navigation_command
from V3.services.rules.system_settings import system_settings_command
from V3.services.rules.text_controls import text_control_command
from V3.services.rules.timer import timer_command
from V3.services.rules.volume import volume_command


RuleHandler = Callable[[RuleContext], Optional[dict]]


RULE_HANDLERS: List[RuleHandler] = [
    gesture_command,
    display_command,
    volume_command,
    navigation_command,
    system_settings_command,
    text_control_command,
    app_command,
    timer_command,
]
