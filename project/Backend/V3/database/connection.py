import os
from pathlib import Path
from typing import Optional


_ENV_LOADED = False
_MISSING_DRIVER_LOGGED = False


def get_database_url() -> Optional[str]:
    _load_local_env()
    url = os.getenv("DATABASE_URL", "").strip()
    return url or None


def is_database_configured() -> bool:
    return get_database_url() is not None


async def open_database_connection():
    database_url = get_database_url()
    if not database_url:
        return None

    try:
        import asyncpg
    except ModuleNotFoundError:
        _log_missing_driver_once()
        return None

    return await asyncpg.connect(dsn=_to_asyncpg_dsn(database_url), timeout=5)


def _to_asyncpg_dsn(database_url: str) -> str:
    if database_url.startswith("postgresql+asyncpg://"):
        return "postgresql://" + database_url[len("postgresql+asyncpg://"):]
    return database_url


def _load_local_env() -> None:
    global _ENV_LOADED
    if _ENV_LOADED:
        return

    _ENV_LOADED = True
    env_path = Path(__file__).resolve().parents[1] / ".env"
    if not env_path.exists():
        return

    for raw_line in env_path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue

        key, value = line.split("=", 1)
        key = key.strip()
        value = value.strip().strip('"').strip("'")
        if key and key not in os.environ:
            os.environ[key] = value


def _log_missing_driver_once() -> None:
    global _MISSING_DRIVER_LOGGED
    if _MISSING_DRIVER_LOGGED:
        return

    _MISSING_DRIVER_LOGGED = True
    print(
        "[database] asyncpg is not installed; PostgreSQL persistence is disabled.",
        flush=True,
    )
