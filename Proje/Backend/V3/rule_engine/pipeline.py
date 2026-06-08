from typing import Callable, List, Optional

from V3.rule_engine.context import RuleContext
from V3.rule_engine.apps import app_command
from V3.rule_engine.click_item import click_item_command
from V3.rule_engine.display import display_command
from V3.rule_engine.gestures import gesture_command
from V3.rule_engine.grid import grid_command
from V3.rule_engine.navigation import navigation_command
from V3.rule_engine.system_settings import system_settings_command
from V3.rule_engine.text_controls import text_control_command
from V3.rule_engine.timer import timer_command
from V3.rule_engine.volume import volume_command


RuleHandler = Callable[[RuleContext], Optional[dict]]


# Handler order is part of rule behavior: specific device/system commands must
# run before the broad app opener so phrases like "open notifications" are not
# interpreted as app names.
RULE_HANDLERS: List[RuleHandler] = [
    click_item_command,
    display_command,
    system_settings_command,
    volume_command,
    navigation_command,
    gesture_command,
    grid_command,
    text_control_command,
    timer_command,
    app_command,
]
