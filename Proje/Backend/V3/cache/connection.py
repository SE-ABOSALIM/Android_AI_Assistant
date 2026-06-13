import os
from typing import Optional

from V3.database.connection import _load_local_env


_MISSING_REDIS_DRIVER_LOGGED = False


def get_redis_url() -> Optional[str]:
    _load_local_env()
    url = os.getenv("REDIS_URL", "").strip()
    return url or None


def is_redis_configured() -> bool:
    return get_redis_url() is not None


async def open_redis_client():
    redis_url = get_redis_url()
    if not redis_url:
        return None

    try:
        from redis.asyncio import Redis
    except ModuleNotFoundError:
        _log_missing_driver_once()
        return None

    return Redis.from_url(
        redis_url,
        encoding="utf-8",
        decode_responses=True,
        socket_connect_timeout=1,
        socket_timeout=1,
    )


async def close_redis_client(client) -> None:
    if client is None:
        return

    close = getattr(client, "aclose", None)
    if close is not None:
        await close()
        return

    await client.close()


def _log_missing_driver_once() -> None:
    global _MISSING_REDIS_DRIVER_LOGGED
    if _MISSING_REDIS_DRIVER_LOGGED:
        return

    _MISSING_REDIS_DRIVER_LOGGED = True
    print(
        "[cache] redis package is not installed; Redis app catalog cache is disabled.",
        flush=True,
    )
