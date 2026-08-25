import os

from V3.database.connection import _load_local_env


DEFAULT_MAX_REQUEST_BODY_BYTES = 2 * 1024 * 1024


def max_request_body_bytes() -> int:
    return _positive_int("MAX_REQUEST_BODY_BYTES", DEFAULT_MAX_REQUEST_BODY_BYTES)


def rate_limiting_enabled() -> bool:
    _load_local_env()
    return os.getenv("RATE_LIMIT_ENABLED", "true").strip().lower() not in {
        "0",
        "false",
        "no",
        "off",
    }


def rate_limit_value(name: str, default: int) -> int:
    return _positive_int(name, default)


def _positive_int(name: str, default: int) -> int:
    _load_local_env()
    try:
        value = int(os.getenv(name, str(default)))
    except (TypeError, ValueError):
        return default
    return value if value > 0 else default
