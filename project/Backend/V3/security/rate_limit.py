from dataclasses import dataclass

from fastapi import Depends, HTTPException, Request, status

from V3.cache.connection import close_redis_client, open_redis_client
from V3.security.authentication import (
    AuthenticatedInstallation,
    require_authenticated_installation,
)
from V3.security.settings import rate_limit_value, rate_limiting_enabled


@dataclass(frozen=True)
class RateLimitPolicy:
    group: str
    limit: int
    window_seconds: int


PREDICT_POLICY = RateLimitPolicy(
    "predict",
    rate_limit_value("RATE_LIMIT_PREDICT_REQUESTS", 60),
    rate_limit_value("RATE_LIMIT_PREDICT_WINDOW_SECONDS", 60),
)
APP_CATALOG_POLICY = RateLimitPolicy(
    "app_catalog",
    rate_limit_value("RATE_LIMIT_APP_CATALOG_REQUESTS", 12),
    rate_limit_value("RATE_LIMIT_APP_CATALOG_WINDOW_SECONDS", 60),
)
CUSTOM_COMMAND_POLICY = RateLimitPolicy(
    "custom_commands",
    rate_limit_value("RATE_LIMIT_CUSTOM_COMMAND_REQUESTS", 60),
    rate_limit_value("RATE_LIMIT_CUSTOM_COMMAND_WINDOW_SECONDS", 60),
)
REGISTRATION_POLICY = RateLimitPolicy(
    "registration",
    rate_limit_value("RATE_LIMIT_REGISTRATION_REQUESTS", 20),
    rate_limit_value("RATE_LIMIT_REGISTRATION_WINDOW_SECONDS", 3600),
)


_ATOMIC_COUNTER_SCRIPT = """
local count = redis.call('INCR', KEYS[1])
if count == 1 then
    redis.call('EXPIRE', KEYS[1], ARGV[1])
end
local ttl = redis.call('TTL', KEYS[1])
return {count, ttl}
"""
_RATE_LIMIT_ERROR_LOGGED = False


async def predict_access(
    identity: AuthenticatedInstallation = Depends(require_authenticated_installation),
) -> AuthenticatedInstallation:
    await enforce_rate_limit(
        PREDICT_POLICY,
        identity_key=identity.device_ref_id,
        fail_closed=False,
    )
    return identity


async def app_catalog_access(
    identity: AuthenticatedInstallation = Depends(require_authenticated_installation),
) -> AuthenticatedInstallation:
    await enforce_rate_limit(
        APP_CATALOG_POLICY,
        identity_key=identity.device_ref_id,
        fail_closed=False,
    )
    return identity


async def custom_command_access(
    identity: AuthenticatedInstallation = Depends(require_authenticated_installation),
) -> AuthenticatedInstallation:
    await enforce_rate_limit(
        CUSTOM_COMMAND_POLICY,
        identity_key=identity.device_ref_id,
        fail_closed=False,
    )
    return identity


async def registration_access(request: Request) -> None:
    client_host = request.client.host if request.client is not None else "unknown"
    await enforce_rate_limit(
        REGISTRATION_POLICY,
        identity_key=client_host,
        fail_closed=True,
    )


async def enforce_rate_limit(
    policy: RateLimitPolicy,
    *,
    identity_key: str,
    fail_closed: bool,
) -> None:
    if not rate_limiting_enabled():
        return

    client = None
    try:
        client = await open_redis_client()
        if client is None:
            if fail_closed:
                raise _rate_limit_unavailable()
            return

        result = await client.eval(
            _ATOMIC_COUNTER_SCRIPT,
            1,
            _rate_limit_key(policy, identity_key),
            policy.window_seconds,
        )
        count = int(result[0])
        retry_after = max(1, int(result[1]))
        if count > policy.limit:
            raise HTTPException(
                status_code=status.HTTP_429_TOO_MANY_REQUESTS,
                detail="Too many requests",
                headers={"Retry-After": str(retry_after)},
            )
    except HTTPException:
        raise
    except Exception as exc:
        _log_rate_limit_error_once(exc)
        if fail_closed:
            raise _rate_limit_unavailable()
    finally:
        await close_redis_client(client)


def _rate_limit_key(policy: RateLimitPolicy, identity_key: str) -> str:
    return f"rate_limit:{policy.group}:{identity_key}"


def _rate_limit_unavailable() -> HTTPException:
    return HTTPException(
        status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
        detail="Request protection is temporarily unavailable",
    )


def _log_rate_limit_error_once(exc: Exception) -> None:
    global _RATE_LIMIT_ERROR_LOGGED
    if _RATE_LIMIT_ERROR_LOGGED:
        return
    _RATE_LIMIT_ERROR_LOGGED = True
    print(f"[rate-limit] Redis operation failed | error={type(exc).__name__}", flush=True)
