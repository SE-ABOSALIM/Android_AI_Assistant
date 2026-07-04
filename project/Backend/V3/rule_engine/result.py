from typing import Any, Dict, Optional


def command(intent: str, rule_matched: str, parameters: Optional[Dict[str, Any]] = None) -> Dict[str, Any]:
    return {
        "intent": intent,
        "parameters": parameters or {},
        "rule_matched": rule_matched,
    }


def unknown(rule_matched: str) -> Dict[str, Any]:
    return command("UNKNOWN_COMMAND", rule_matched)
