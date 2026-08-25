import unittest
from unittest.mock import AsyncMock, patch

from fastapi import HTTPException
from starlette.requests import Request

from V3.security import rate_limit


class _FakeRedis:
    def __init__(self):
        self.counts = {}
        self.keys = []
        self.ttl = 42

    async def eval(self, script, key_count, key, window_seconds):
        self.keys.append(key)
        self.counts[key] = self.counts.get(key, 0) + 1
        return [self.counts[key], self.ttl]


class RateLimitTests(unittest.IsolatedAsyncioTestCase):
    async def test_authenticatedIdentity_exceedingLimit_returns429_withRetryAfter(self):
        redis = _FakeRedis()
        policy = rate_limit.RateLimitPolicy("test", limit=2, window_seconds=60)

        with self._redis(redis):
            await rate_limit.enforce_rate_limit(
                policy,
                identity_key="device-ref-a",
                fail_closed=False,
            )
            await rate_limit.enforce_rate_limit(
                policy,
                identity_key="device-ref-a",
                fail_closed=False,
            )
            with self.assertRaises(HTTPException) as raised:
                await rate_limit.enforce_rate_limit(
                    policy,
                    identity_key="device-ref-a",
                    fail_closed=False,
                )

        self.assertEqual(429, raised.exception.status_code)
        self.assertEqual("42", raised.exception.headers["Retry-After"])

    async def test_differentAuthenticatedIdentity_hasIndependentQuota(self):
        redis = _FakeRedis()
        policy = rate_limit.RateLimitPolicy("test", limit=1, window_seconds=60)

        with self._redis(redis):
            await rate_limit.enforce_rate_limit(
                policy,
                identity_key="device-ref-a",
                fail_closed=False,
            )
            await rate_limit.enforce_rate_limit(
                policy,
                identity_key="device-ref-b",
                fail_closed=False,
            )

        self.assertEqual(2, len(redis.counts))

    async def test_registrationRateLimit_usesIPNotClientDeviceId(self):
        redis = _FakeRedis()
        request_a = _request("203.0.113.10", b"device_id=device-a")
        request_b = _request("203.0.113.10", b"device_id=device-b")

        with self._redis(redis):
            await rate_limit.registration_access(request_a)
            await rate_limit.registration_access(request_b)

        self.assertEqual(redis.keys[0], redis.keys[1])
        self.assertTrue(redis.keys[0].endswith("203.0.113.10"))

    async def test_redisFailure_authenticatedFailsOpen_registrationFailsClosed(self):
        with (
            patch.object(rate_limit, "rate_limiting_enabled", return_value=True),
            patch.object(
                rate_limit,
                "open_redis_client",
                new=AsyncMock(side_effect=RuntimeError("redis unavailable")),
            ),
            patch.object(rate_limit, "close_redis_client", new=AsyncMock()),
        ):
            await rate_limit.enforce_rate_limit(
                rate_limit.PREDICT_POLICY,
                identity_key="device-ref-a",
                fail_closed=False,
            )
            with self.assertRaises(HTTPException) as raised:
                await rate_limit.enforce_rate_limit(
                    rate_limit.REGISTRATION_POLICY,
                    identity_key="203.0.113.10",
                    fail_closed=True,
                )

        self.assertEqual(503, raised.exception.status_code)

    def _redis(self, redis):
        return _combined_patches(
            patch.object(rate_limit, "rate_limiting_enabled", return_value=True),
            patch.object(rate_limit, "open_redis_client", new=AsyncMock(return_value=redis)),
            patch.object(rate_limit, "close_redis_client", new=AsyncMock()),
        )


def _request(client_ip: str, body: bytes) -> Request:
    return Request(
        {
            "type": "http",
            "method": "POST",
            "path": "/installations/register",
            "headers": [],
            "query_string": body,
            "client": (client_ip, 12345),
            "server": ("testserver", 80),
            "scheme": "http",
        }
    )


class _combined_patches:
    def __init__(self, *patchers):
        self.patchers = patchers

    def __enter__(self):
        for patcher in self.patchers:
            patcher.start()
        return self

    def __exit__(self, exc_type, exc, traceback):
        for patcher in reversed(self.patchers):
            patcher.stop()
        return False


if __name__ == "__main__":
    unittest.main()
