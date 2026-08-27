import unittest
from unittest.mock import AsyncMock, patch
from uuid import uuid4

from fastapi.testclient import TestClient

from V3 import main
from V3.database import installation_auth_repository
from V3.security.authentication import AuthenticatedInstallation


class _FakeTransaction:
    async def __aenter__(self):
        return self

    async def __aexit__(self, exc_type, exc, traceback):
        return False


class _RegistrationConnection:
    def __init__(self, device_ref_id=None):
        self.device_ref_id = device_ref_id
        self.execute_calls = []

    def transaction(self):
        return _FakeTransaction()

    async def fetchval(self, query, *args):
        return self.device_ref_id

    async def execute(self, query, *args):
        self.execute_calls.append((query, args))
        return "INSERT 0 1"

    async def close(self):
        return None


class AuthenticationRegressionTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.client = TestClient(main.app)

    def test_protectedEndpoint_withoutBearer_returns401(self):
        with patch.object(
            main,
            "list_custom_commands",
            new=AsyncMock(return_value={"items": []}),
        ):
            response = self.client.get(
                "/custom-commands",
                params={"device_id": "device-a", "language": "TR"},
            )

        self.assertEqual(401, response.status_code)
        self.assertEqual("Bearer", response.headers.get("www-authenticate"))

    def test_protectedEndpoint_withInvalidBearer_returns401(self):
        with (
            patch.object(main, "list_custom_commands", new=AsyncMock(return_value={"items": []})),
            patch(
                "V3.security.authentication.find_installation_by_credential",
                new=AsyncMock(return_value=None),
            ),
        ):
            response = self.client.get(
                "/custom-commands",
                params={"device_id": "device-a", "language": "TR"},
                headers={"Authorization": "Bearer invalid-credential"},
            )

        self.assertEqual(401, response.status_code)
        self.assertEqual("Bearer", response.headers.get("www-authenticate"))

    def test_malformedOrEmptyBearer_returns401(self):
        for authorization in ("Basic abc", "Bearer", "Bearer    "):
            with self.subTest(authorization=authorization):
                response = self.client.get(
                    "/custom-commands",
                    params={"device_id": "device-a", "language": "TR"},
                    headers={"Authorization": authorization},
                )
                self.assertEqual(401, response.status_code)

    def test_protectedEndpoint_withValidBearer_succeeds(self):
        with self._authenticated_device_a(), patch.object(
            main,
            "list_custom_commands",
            new=AsyncMock(return_value={"items": []}),
        ):
            response = self.client.get(
                "/custom-commands",
                params={"device_id": "device-a", "language": "TR"},
                headers={"Authorization": "Bearer valid-credential"},
            )

        self.assertEqual(200, response.status_code)

    def test_tokenForDeviceA_cannotAccessDeviceB(self):
        with self._authenticated_device_a(), patch.object(
            main,
            "list_custom_commands",
            new=AsyncMock(return_value={"items": []}),
        ):
            response = self.client.get(
                "/custom-commands",
                params={"device_id": "device-b", "language": "TR"},
                headers={"Authorization": "Bearer token-for-device-a"},
            )

        self.assertEqual(403, response.status_code)

    def test_tokenForDeviceA_cannotUseDeviceB_inPredictBody(self):
        with self._authenticated_device_a():
            response = self.client.post(
                "/predict",
                json={"text": "go back", "language": "TR", "device_id": "device-b"},
                headers={"Authorization": "Bearer valid-credential"},
            )

        self.assertEqual(403, response.status_code)

    def test_sessionIdCannotSelectAnotherDeviceCatalog(self):
        get_status = unittest.mock.Mock(
            return_value={
                "available": True,
                "catalog_version": "device-a-v1",
                "language": "TR",
                "app_count": 2,
            }
        )
        with self._authenticated_device_a(), patch.object(
            main,
            "get_app_catalog_status",
            new=get_status,
        ):
            response = self.client.get(
                "/app-catalog/device-b",
                headers={"Authorization": "Bearer valid-credential"},
            )

        self.assertEqual(200, response.status_code)
        self.assertTrue(response.json()["available"])
        self.assertEqual("device-a-v1", response.json()["catalog_version"])
        get_status.assert_called_once_with("device-a")

    def test_deviceIdCannotOverrideAuthenticatedCatalogOwner(self):
        with self._authenticated_device_a():
            response = self.client.post(
                "/app-catalog",
                json={
                    "session_id": "assistant-session-2",
                    "device_id": "device-b",
                    "language": "TR",
                    "catalog_version": "catalog-v1",
                    "apps": [],
                },
                headers={"Authorization": "Bearer valid-credential"},
            )

        self.assertEqual(403, response.status_code)

    def test_explicitCustomCommandCrud_remainsFunctionalForOwner(self):
        item = {
            "id": "11111111-1111-1111-1111-111111111111",
            "name": "My command",
            "language": "TR",
            "enabled": True,
            "steps": [],
            "created_at": "2026-01-01T00:00:00+00:00",
            "updated_at": "2026-01-01T00:00:00+00:00",
        }
        request_body = {
            "device_id": "device-a",
            "language": "TR",
            "name": "My command",
            "steps": [],
        }

        with (
            self._authenticated_device_a(),
            patch.object(main, "list_custom_commands", new=AsyncMock(return_value={"items": []})),
            patch.object(main, "save_custom_command", new=AsyncMock(return_value=item)),
            patch.object(main, "update_custom_command", new=AsyncMock(return_value=item)),
            patch.object(main, "delete_custom_command", new=AsyncMock(return_value=1)),
        ):
            responses = [
                self.client.get(
                    "/custom-commands",
                    params={"device_id": "device-a", "language": "TR"},
                    headers={"Authorization": "Bearer valid-credential"},
                ),
                self.client.post(
                    "/custom-commands",
                    json=request_body,
                    headers={"Authorization": "Bearer valid-credential"},
                ),
                self.client.put(
                    "/custom-commands/11111111-1111-1111-1111-111111111111",
                    json=request_body,
                    headers={"Authorization": "Bearer valid-credential"},
                ),
                self.client.delete(
                    "/custom-commands/11111111-1111-1111-1111-111111111111",
                    params={"device_id": "device-a"},
                    headers={"Authorization": "Bearer valid-credential"},
                ),
            ]

        self.assertEqual([200, 200, 200, 200], [response.status_code for response in responses])

    def _authenticated_device_a(self):
        identity = {
            "device_ref_id": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
            "device_id": "device-a",
        }
        return _combined_patches(
            patch(
                "V3.security.authentication.find_installation_by_credential",
                new=AsyncMock(return_value=identity),
            ),
            patch(
                "V3.security.rate_limit.enforce_rate_limit",
                new=AsyncMock(),
            ),
        )


class InstallationRegistrationTests(unittest.IsolatedAsyncioTestCase):
    async def test_storedCredential_isHashedNotPlaintext(self):
        connection = _RegistrationConnection(device_ref_id=uuid4())
        raw_credential = "high-entropy-registration-credential"

        with (
            patch.object(installation_auth_repository, "is_database_configured", return_value=True),
            patch.object(
                installation_auth_repository,
                "open_database_connection",
                new=AsyncMock(return_value=connection),
            ),
            patch.object(
                installation_auth_repository.secrets,
                "token_urlsafe",
                return_value=raw_credential,
            ),
        ):
            result = await installation_auth_repository.register_installation(
                device_id="device-a",
                platform="android",
                app_version="1.0",
                language="TR",
            )

        self.assertEqual("created", result.status)
        self.assertEqual(raw_credential, result.credential)
        persisted_args = next(
            args
            for query, args in connection.execute_calls
            if "INSERT INTO device_auth_credentials" in query
        )
        self.assertNotIn(raw_credential, persisted_args)
        self.assertIn(
            installation_auth_repository.hash_bearer_credential(raw_credential),
            persisted_args,
        )

    async def test_existingStableDevice_registrationRotatesCredential(self):
        existing_device_ref_id = uuid4()
        connection = _RegistrationConnection(device_ref_id=existing_device_ref_id)
        raw_credential = "rotated-high-entropy-credential"

        with (
            patch.object(installation_auth_repository, "is_database_configured", return_value=True),
            patch.object(
                installation_auth_repository,
                "open_database_connection",
                new=AsyncMock(return_value=connection),
            ),
            patch.object(
                installation_auth_repository.secrets,
                "token_urlsafe",
                return_value=raw_credential,
            ),
        ):
            result = await installation_auth_repository.register_installation(
                device_id="existing-device",
                platform="android",
                app_version="1.0",
                language="TR",
            )

        self.assertIn(result.status, {"created", "recovered"})
        self.assertEqual(raw_credential, result.credential)
        self.assertTrue(
            any("UPDATE device_auth_credentials" in query for query, _ in connection.execute_calls)
        )
        self.assertTrue(
            any("INSERT INTO device_auth_credentials" in query for query, _ in connection.execute_calls)
        )


class InstallationRegistrationRouteTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.client = TestClient(main.app)

    def tearDown(self):
        main.app.dependency_overrides.clear()

    def test_newInstallation_receivesCredentialOnlyOnInitialIssuance(self):
        main.app.dependency_overrides[main.registration_access] = lambda: None
        result = installation_auth_repository.InstallationRegistrationResult(
            status="created",
            credential="issued-once",
        )
        with patch.object(main, "register_installation", new=AsyncMock(return_value=result)):
            response = self.client.post(
                "/installations/register",
                json={"device_id": "new-device", "platform": "android", "language": "TR"},
            )

        self.assertEqual(201, response.status_code)
        self.assertEqual("issued-once", response.json()["credential"])

    def test_existingInstallation_registrationReturnsFreshCredential(self):
        main.app.dependency_overrides[main.registration_access] = lambda: None
        result = installation_auth_repository.InstallationRegistrationResult(
            status="recovered",
            credential="fresh-recovery-credential",
        )
        with patch.object(main, "register_installation", new=AsyncMock(return_value=result)):
            response = self.client.post(
                "/installations/register",
                json={"device_id": "existing-device", "platform": "android", "language": "TR"},
            )

        self.assertEqual(201, response.status_code)
        self.assertEqual("fresh-recovery-credential", response.json()["credential"])


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
