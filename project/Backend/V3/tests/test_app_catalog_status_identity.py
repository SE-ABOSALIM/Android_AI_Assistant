import unittest
from unittest.mock import AsyncMock, Mock, patch

from fastapi import BackgroundTasks
from fastapi.testclient import TestClient

from V3 import main
from V3.app_catalog import store
from V3.schemas import PredictRequest
from V3.security.authentication import AuthenticatedInstallation


DEVICE_A_IDENTITY = {
    "device_ref_id": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
    "device_id": "device-a",
}
DEVICE_B_IDENTITY = {
    "device_ref_id": "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
    "device_id": "device-b",
}


def _catalog_status(version: str):
    return {
        "available": True,
        "catalog_version": version,
        "language": "TR",
        "app_count": 2,
    }


def _prediction_response():
    return {
        "input": "open maps",
        "normalized_input": "open maps",
        "language": "TR",
        "intent": "OPEN_APP",
        "parameters": {"app_name": "maps"},
        "accepted": True,
        "missing_slots": [],
        "error_code": None,
        "error_message": None,
        "needs_confirmation": False,
        "confidence": 1.0,
        "threshold": 0.5,
        "raw_label": "RULE::open_app",
    }


class AppCatalogStatusIdentityTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.client = TestClient(main.app)

    def test_sameDevice_newSession_statusStillAvailable(self):
        get_status = Mock(return_value=_catalog_status("catalog-v1"))
        with self._authenticated(DEVICE_A_IDENTITY), patch.object(
            main,
            "get_app_catalog_status",
            new=get_status,
        ):
            response = self.client.get(
                "/app-catalog/assistant-session-2",
                headers={"Authorization": "Bearer credential-a"},
            )

        self.assertEqual(200, response.status_code)
        self.assertTrue(response.json()["available"])
        self.assertEqual("catalog-v1", response.json()["catalog_version"])
        get_status.assert_called_once_with("device-a")

    def test_authenticatedStatusLookup_doesNotUseSessionIdAsOwner(self):
        get_status = Mock(return_value=_catalog_status("catalog-v1"))
        with self._authenticated(DEVICE_A_IDENTITY), patch.object(
            main,
            "get_app_catalog_status",
            new=get_status,
        ):
            response = self.client.get(
                "/app-catalog/status",
                headers={"Authorization": "Bearer credential-a"},
            )

        self.assertEqual(200, response.status_code)
        self.assertTrue(response.json()["available"])
        self.assertNotIn("session_id", response.json())
        get_status.assert_called_once_with("device-a")

    def test_newDevice_withoutCatalog_requiresInitialSync(self):
        get_status = Mock(
            return_value={
                "available": False,
                "catalog_version": None,
                "language": None,
                "app_count": 0,
            }
        )
        with self._authenticated(DEVICE_A_IDENTITY), patch.object(
            main,
            "get_app_catalog_status",
            new=get_status,
        ):
            response = self.client.get(
                "/app-catalog/status",
                headers={"Authorization": "Bearer credential-a"},
            )

        self.assertEqual(200, response.status_code)
        self.assertFalse(response.json()["available"])
        get_status.assert_called_once_with("device-a")

    def test_differentAuthenticatedDevicesRemainIsolated(self):
        get_status = Mock(side_effect=lambda device_id: _catalog_status(f"{device_id}-v1"))

        with self._authenticated(DEVICE_A_IDENTITY), patch.object(
            main,
            "get_app_catalog_status",
            new=get_status,
        ):
            response_a = self.client.get(
                "/app-catalog/status",
                headers={"Authorization": "Bearer credential-a"},
            )

        with self._authenticated(DEVICE_B_IDENTITY), patch.object(
            main,
            "get_app_catalog_status",
            new=get_status,
        ):
            response_b = self.client.get(
                "/app-catalog/status",
                headers={"Authorization": "Bearer credential-b"},
            )

        self.assertEqual("device-a-v1", response_a.json()["catalog_version"])
        self.assertEqual("device-b-v1", response_b.json()["catalog_version"])
        self.assertEqual([unittest.mock.call("device-a"), unittest.mock.call("device-b")], get_status.call_args_list)

    def test_predictTimeCatalogLookup_remainsDeviceScoped(self):
        predict_command = Mock(return_value=_prediction_response())
        request = PredictRequest(
            text="open maps",
            language="TR",
            session_id="assistant-session-2",
            device_id="device-a",
            catalog_version="catalog-v1",
        )
        identity = AuthenticatedInstallation(**DEVICE_A_IDENTITY)

        with (
            patch.object(main, "try_build_custom_command_response", return_value=None),
            patch.object(main, "predict_command", new=predict_command),
        ):
            main.predict(request, BackgroundTasks(), identity)

        self.assertEqual("device-a", predict_command.call_args.kwargs["session_id"])
        self.assertEqual("device-a", predict_command.call_args.kwargs["device_id"])

    @staticmethod
    def _authenticated(identity):
        return _CombinedPatches(
            patch(
                "V3.security.authentication.find_installation_by_credential",
                new=AsyncMock(return_value=identity),
            ),
            patch(
                "V3.security.rate_limit.enforce_rate_limit",
                new=AsyncMock(),
            ),
        )


class AppCatalogFallbackTests(unittest.IsolatedAsyncioTestCase):
    async def test_redisMiss_databaseFallbackFindsExistingDeviceCatalog(self):
        catalog = {
            "catalog_version": "catalog-v1",
            "language": "TR",
            "apps": ["maps", "settings"],
        }
        cache_get = AsyncMock(return_value=None)
        database_load = AsyncMock(return_value=catalog)
        cache_set = AsyncMock(return_value=True)

        with (
            patch("V3.cache.app_catalog_cache.get_cached_app_catalog_snapshot", new=cache_get),
            patch("V3.database.app_catalog_repository.load_app_catalog_snapshot", new=database_load),
            patch("V3.cache.app_catalog_cache.set_cached_app_catalog_snapshot", new=cache_set),
        ):
            result = await store._load_catalog_snapshot("device-a")

        self.assertEqual(catalog, result)
        cache_get.assert_awaited_once_with("device-a")
        database_load.assert_awaited_once_with("device-a")
        cache_set.assert_awaited_once_with("device-a", catalog)

    async def test_redisExpiry_doesNotChangeCatalogCorrectness(self):
        catalog = {
            "catalog_version": "catalog-v1",
            "language": "TR",
            "apps": ["maps", "settings"],
        }
        cache_get = AsyncMock(side_effect=[catalog, None])
        database_load = AsyncMock(return_value=catalog)
        cache_set = AsyncMock(return_value=True)

        with (
            patch("V3.cache.app_catalog_cache.get_cached_app_catalog_snapshot", new=cache_get),
            patch("V3.database.app_catalog_repository.load_app_catalog_snapshot", new=database_load),
            patch("V3.cache.app_catalog_cache.set_cached_app_catalog_snapshot", new=cache_set),
        ):
            before_expiry = await store._load_catalog_snapshot("device-a")
            after_expiry = await store._load_catalog_snapshot("device-a")

        self.assertEqual(before_expiry, after_expiry)
        database_load.assert_awaited_once_with("device-a")
        cache_set.assert_awaited_once_with("device-a", catalog)


class _CombinedPatches:
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
