import asyncio
import unittest
from contextlib import asynccontextmanager
from unittest.mock import patch
from uuid import uuid4

from V3.database import installation_auth_repository


class _RecoveryStore:
    def __init__(self):
        self.lock = asyncio.Lock()
        self.devices = {}
        self.credentials = []
        self.custom_commands = []
        self.custom_command_steps = []
        self.device_apps = []
        self.command_history = []

    def active_credentials(self, device_ref_id):
        return [
            item
            for item in self.credentials
            if item["device_ref_id"] == device_ref_id and not item["revoked"]
        ]


class _RecoveryConnection:
    def __init__(self, store):
        self.store = store

    @asynccontextmanager
    async def transaction(self):
        async with self.store.lock:
            yield self

    async def fetchval(self, query, *args):
        normalized = " ".join(query.split())
        if "INSERT INTO devices" in normalized:
            device_id = args[0]
            if device_id in self.store.devices:
                return None
            device_ref_id = uuid4()
            self.store.devices[device_id] = device_ref_id
            return device_ref_id
        if "FROM devices" in normalized and "FOR UPDATE" in normalized:
            return self.store.devices.get(args[0])
        raise AssertionError(f"Unexpected fetchval query: {normalized}")

    async def execute(self, query, *args):
        normalized = " ".join(query.split())
        if "UPDATE devices" in normalized:
            return "UPDATE 1"
        if "UPDATE device_auth_credentials" in normalized:
            device_ref_id = args[0]
            for item in self.store.credentials:
                if item["device_ref_id"] == device_ref_id and not item["revoked"]:
                    item["revoked"] = True
            return "UPDATE"
        if "INSERT INTO device_auth_credentials" in normalized:
            self.store.credentials.append(
                {
                    "device_ref_id": args[0],
                    "token_hash": args[1],
                    "revoked": False,
                }
            )
            return "INSERT 0 1"
        raise AssertionError(f"Unexpected execute query: {normalized}")

    async def fetchrow(self, query, *args):
        token_hash = args[0]
        for item in self.store.credentials:
            if item["token_hash"] == token_hash and not item["revoked"]:
                device_id = next(
                    key
                    for key, value in self.store.devices.items()
                    if value == item["device_ref_id"]
                )
                return {
                    "device_ref_id": str(item["device_ref_id"]),
                    "device_id": device_id,
                }
        return None

    async def close(self):
        return None


class StableDeviceRecoveryTests(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self):
        self.store = _RecoveryStore()
        self.issued_credentials = iter(
            [f"credential-{index}" for index in range(1, 20)]
        )

        async def open_connection():
            return _RecoveryConnection(self.store)

        self.patches = [
            patch.object(installation_auth_repository, "is_database_configured", return_value=True),
            patch.object(
                installation_auth_repository,
                "open_database_connection",
                side_effect=open_connection,
            ),
            patch.object(
                installation_auth_repository.secrets,
                "token_urlsafe",
                side_effect=lambda _size: next(self.issued_credentials),
            ),
        ]
        for patcher in self.patches:
            patcher.start()

    async def asyncTearDown(self):
        for patcher in reversed(self.patches):
            patcher.stop()

    async def _register(self, device_id="android-id-x"):
        return await installation_auth_repository.register_installation(
            device_id=device_id,
            platform="android",
            app_version="1.0",
            language="TR",
        )

    async def test_firstRegistration_createsStableDeviceAndCredential(self):
        result = await self._register()

        self.assertEqual("created", result.status)
        self.assertEqual(1, len(self.store.devices))
        device_ref_id = self.store.devices["android-id-x"]
        self.assertEqual(1, len(self.store.active_credentials(device_ref_id)))
        self.assertNotEqual(
            result.credential,
            self.store.active_credentials(device_ref_id)[0]["token_hash"],
        )

    async def test_repeatedRegistrationSameAndroidId_reusesSameDevice(self):
        await self._register()
        original_device_ref_id = self.store.devices["android-id-x"]

        result = await self._register()

        self.assertEqual("recovered", result.status)
        self.assertEqual(1, len(self.store.devices))
        self.assertEqual(original_device_ref_id, self.store.devices["android-id-x"])

    async def test_recovery_rotatesCredential_andAuthenticationState(self):
        first = await self._register()
        recovered = await self._register()

        old_identity = await installation_auth_repository.find_installation_by_credential(
            first.credential
        )
        new_identity = await installation_auth_repository.find_installation_by_credential(
            recovered.credential
        )

        self.assertIsNone(old_identity)
        self.assertEqual("android-id-x", new_identity["device_id"])
        self.assertEqual(
            str(self.store.devices["android-id-x"]),
            new_identity["device_ref_id"],
        )

    async def test_recovery_preservesCustomCommandsStepsAndDeviceData(self):
        await self._register()
        device_ref_id = self.store.devices["android-id-x"]
        command_id = uuid4()
        self.store.custom_commands.append((command_id, device_ref_id))
        self.store.custom_command_steps.append((uuid4(), command_id))
        self.store.device_apps.append((uuid4(), device_ref_id))
        self.store.command_history.append((uuid4(), device_ref_id))

        await self._register()

        self.assertEqual([(command_id, device_ref_id)], self.store.custom_commands)
        self.assertEqual(command_id, self.store.custom_command_steps[0][1])
        self.assertEqual(device_ref_id, self.store.device_apps[0][1])
        self.assertEqual(device_ref_id, self.store.command_history[0][1])

    async def test_differentAndroidIds_remainDifferentDevices(self):
        await self._register("android-id-x")
        await self._register("android-id-y")

        self.assertEqual(2, len(self.store.devices))
        self.assertNotEqual(
            self.store.devices["android-id-x"],
            self.store.devices["android-id-y"],
        )

    async def test_concurrentRecovery_doesNotCreateDuplicateLogicalDevice(self):
        await self._register()

        results = await asyncio.gather(self._register(), self._register())

        device_ref_id = self.store.devices["android-id-x"]
        self.assertEqual(1, len(self.store.devices))
        self.assertEqual(1, len(self.store.active_credentials(device_ref_id)))
        self.assertTrue(all(result.status == "recovered" for result in results))


if __name__ == "__main__":
    unittest.main()
