from typing import Any, Dict, Optional

from V3.services.extractors import extract_app_name
from V3.services.rules.context import RuleContext
from V3.services.rules.result import command


def app_command(context: RuleContext) -> Optional[Dict[str, Any]]:
    app_name = extract_app_name(context.original, context.language)
    if not app_name:
        return None

    return command("OPEN_APP", "open_app", {"app_name": app_name})
