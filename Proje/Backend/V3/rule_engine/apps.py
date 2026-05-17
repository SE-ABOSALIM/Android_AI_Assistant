from typing import Any, Dict, Optional

from V3.extraction.app import extract_app_name_for_intent
from V3.rule_engine.context import RuleContext
from V3.rule_engine.result import command


def app_command(context: RuleContext) -> Optional[Dict[str, Any]]:
    app_name = extract_app_name_for_intent(context.original, context.language, "OPEN_APP_INFO")
    if app_name:
        return command("OPEN_APP_INFO", "open_app_info", {"app_name": app_name})

    app_name = extract_app_name_for_intent(context.original, context.language, "OPEN_APP")
    if not app_name:
        return None

    return command("OPEN_APP", "open_app", {"app_name": app_name})
