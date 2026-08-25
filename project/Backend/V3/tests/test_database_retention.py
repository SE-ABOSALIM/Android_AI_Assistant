import unittest
from datetime import datetime, timezone
from unittest.mock import AsyncMock, patch
from uuid import uuid4

from V3 import main
from V3.schemas import AppCatalogRequest


class _FakeTransaction:
    async def __aenter__(self):
        return self

    async def __aexit__(self, exc_type, exc, traceback):
        return False


class _PersistentDeviceRecords:
    def __init__(self):
        self.device_ref_id = uuid4()
        self.last_seen_at = datetime(2026, 1, 1, tzinfo=timezone.utc)
        self.rows = {
            "devices": [self.device_ref_id],
            "device_apps": [uuid4()],
            "custom_commands": [uuid4()],
            "custom_command_steps": [uuid4()],
            "command_history": [uuid4()],
            "failed_app_open_attempts": [uuid4()],
            "error_messages": [uuid4()],
        }

    async def fetch(self, query, *args):
        if "FROM devices" in query and "last_seen_at" in query:
            return [
                {
                    "id": self.device_ref_id,
                    "device_id": "stale-device",
                    "last_seen_at": self.last_seen_at,
                }
            ]
        raise AssertionError(f"Unexpected fetch query: {query}")

    async def fetchval(self, query, *args):
        if "INSERT INTO devices" in query:
            return self.device_ref_id
        raise AssertionError(f"Unexpected fetchval query: {query}")

    async def execute(self, query, *args):
        for table_name in self.rows:
            if f"DELETE FROM {table_name}" in query:
                self.rows[table_name].clear()
                if table_name == "custom_commands":
                    self.rows["custom_command_steps"].clear()
                return "DELETE 1"
        raise AssertionError(f"Unexpected execute query: {query}")

    def transaction(self):
        return _FakeTransaction()

    async def close(self):
        return None


def _catalog_request():
    return AppCatalogRequest(
        session_id="stale-device",
        device_id="stale-device",
        language="TR",
        catalog_version="catalog-v1",
        apps=[],
    )


def _catalog_result():
    return {
        "session_id": "stale-device",
        "catalog_version": "catalog-v1",
        "language": "TR",
        "apps": [],
        "app_count": 0,
    }


class DatabaseRetentionTests(unittest.IsolatedAsyncioTestCase):
    async def test_staleDevice_doesNotDeleteDevice(self):
        records = _PersistentDeviceRecords()

        with (
            patch(
                "V3.database.app_catalog_repository.is_database_configured",
                return_value=True,
            ),
            patch(
                "V3.database.app_catalog_repository.open_database_connection",
                new=AsyncMock(return_value=records),
            ),
            patch.object(main, "delete_cached_app_catalog_snapshot", new=AsyncMock()),
            patch.object(main, "preload_model"),
            patch.object(main, "get_device_name", return_value="test-device"),
        ):
            await main.preload_intent_model()

        self.assertEqual([records.device_ref_id], records.rows["devices"])

    async def test_staleDevice_doesNotDeleteAssociatedPersistentData(self):
        records = _PersistentDeviceRecords()
        expected_rows = {
            table_name: list(row_ids)
            for table_name, row_ids in records.rows.items()
            if table_name != "devices"
        }

        with (
            patch(
                "V3.database.app_catalog_repository.is_database_configured",
                return_value=True,
            ),
            patch(
                "V3.database.app_catalog_repository.open_database_connection",
                new=AsyncMock(return_value=records),
            ),
            patch.object(main, "delete_cached_app_catalog_snapshot", new=AsyncMock()),
            patch.object(main, "save_app_catalog", return_value=_catalog_result()),
            patch.object(main, "save_app_catalog_snapshot", new=AsyncMock(return_value=False)),
        ):
            await main.app_catalog(_catalog_request())

        for table_name, row_ids in expected_rows.items():
            with self.subTest(table_name=table_name):
                self.assertEqual(row_ids, records.rows[table_name])

    async def test_backendStartup_doesNotPerformInactivityDatabaseDeletion(self):
        open_connection = AsyncMock(return_value=_PersistentDeviceRecords())

        with (
            patch(
                "V3.database.app_catalog_repository.is_database_configured",
                return_value=True,
            ),
            patch(
                "V3.database.app_catalog_repository.open_database_connection",
                new=open_connection,
            ),
            patch.object(main, "delete_cached_app_catalog_snapshot", new=AsyncMock()),
            patch.object(main, "preload_model"),
            patch.object(main, "get_device_name", return_value="test-device"),
        ):
            await main.preload_intent_model()

        open_connection.assert_not_awaited()

    async def test_appCatalogRequest_doesNotPerformInactivityDatabaseDeletion(self):
        open_connection = AsyncMock(return_value=_PersistentDeviceRecords())

        with (
            patch(
                "V3.database.app_catalog_repository.is_database_configured",
                return_value=True,
            ),
            patch(
                "V3.database.app_catalog_repository.open_database_connection",
                new=open_connection,
            ),
            patch.object(main, "delete_cached_app_catalog_snapshot", new=AsyncMock()),
            patch.object(main, "save_app_catalog", return_value=_catalog_result()),
            patch.object(main, "save_app_catalog_snapshot", new=AsyncMock(return_value=False)),
        ):
            await main.app_catalog(_catalog_request())

        open_connection.assert_not_awaited()

    async def test_explicitCustomCommandDeletion_stillWorks(self):
        records = _PersistentDeviceRecords()
        command_id = str(records.rows["custom_commands"][0])

        with (
            patch(
                "V3.database.custom_command_repository.is_database_configured",
                return_value=True,
            ),
            patch(
                "V3.database.custom_command_repository.open_database_connection",
                new=AsyncMock(return_value=records),
            ),
        ):
            response = await main.remove_custom_command(
                command_id=command_id,
                device_id="device-id",
            )

        self.assertTrue(response.accepted)
        self.assertEqual(1, response.deleted_count)
        self.assertEqual([], records.rows["custom_commands"])
        self.assertEqual([], records.rows["custom_command_steps"])


if __name__ == "__main__":
    unittest.main()
