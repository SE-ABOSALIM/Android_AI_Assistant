import inspect
import unittest
from pathlib import Path
from unittest.mock import AsyncMock, patch

from fastapi import BackgroundTasks

from V3 import main
from V3.database.command_history_repository import record_command_history
from V3.schemas import PredictRequest
from V3.security.authentication import AuthenticatedInstallation


DEVICE_REF_ID = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
ANDROID_ID = "raw-android-id-must-not-enter-history"
BEARER_CREDENTIAL = "raw-bearer-credential-must-not-enter-history"


class _FakeTransaction:
    async def __aenter__(self):
        return self

    async def __aexit__(self, exc_type, exc, traceback):
        return False


class _HistoryConnection:
    def __init__(self):
        self.insert_query = None
        self.insert_args = None

    def transaction(self):
        return _FakeTransaction()

    async def fetchval(self, query, *args):
        self.insert_query = query
        self.insert_args = args
        return "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"

    async def close(self):
        return None


def _history_task_args(*, raw_text, response):
    tasks = BackgroundTasks()
    with patch.object(main, "try_build_custom_command_response", return_value=response):
        main.predict(
            PredictRequest(
                text=raw_text,
                language="EN",
                session_id="session-with-no-history-purpose",
                device_id=ANDROID_ID,
            ),
            tasks,
            AuthenticatedInstallation(
                device_ref_id=DEVICE_REF_ID,
                device_id=ANDROID_ID,
            ),
        )

    history_tasks = [
        task for task in tasks.tasks
        if task.func is main._record_command_history_background
    ]
    if len(history_tasks) != 1:
        raise AssertionError(f"Expected one history task, got {len(history_tasks)}")
    return history_tasks[0].args


def _assert_not_scheduled(test_case, raw_text, response, *secrets):
    serialized_args = repr(_history_task_args(raw_text=raw_text, response=response))
    test_case.assertNotIn(raw_text, serialized_args)
    test_case.assertNotIn(ANDROID_ID, serialized_args)
    test_case.assertNotIn(BEARER_CREDENTIAL, serialized_args)
    for secret in secrets:
        test_case.assertNotIn(secret, serialized_args)


class CommandHistoryBoundaryTests(unittest.TestCase):
    def test_successfulCommand_doesNotPersistRawText(self):
        _assert_not_scheduled(
            self,
            "turn on the flashlight",
            {
                "intent": "TURN_ON_FLASHLIGHT",
                "parameters": {},
                "accepted": True,
                "_internal_confidence": 0.98,
                "error_code": None,
            },
        )

    def test_writeTextCommand_doesNotPersistTypedText(self):
        typed_text = "my password is ABC123"
        _assert_not_scheduled(
            self,
            f"type {typed_text}",
            {
                "intent": "WRITE_TEXT",
                "parameters": {"text": typed_text},
                "accepted": True,
                "_internal_confidence": 0.99,
                "error_code": None,
            },
            typed_text,
        )

    def test_searchCommand_doesNotPersistSearchQuery(self):
        search_query = "private medical diagnosis"
        _assert_not_scheduled(
            self,
            f"search for {search_query}",
            {
                "intent": "SEARCH_QUERY",
                "parameters": {"query": search_query},
                "accepted": True,
                "_internal_confidence": 0.95,
                "error_code": None,
            },
            search_query,
        )

    def test_unknownCommand_doesNotPersistRawUtterance(self):
        _assert_not_scheduled(
            self,
            "private phrase that became unknown",
            {
                "intent": "UNKNOWN_COMMAND",
                "parameters": {},
                "accepted": False,
                "_internal_confidence": 0.23,
                "error_code": "UNKNOWN_COMMAND",
            },
        )

    def test_misclassifiedSensitiveInput_doesNotPersistRawText(self):
        secret = "ABC123"
        _assert_not_scheduled(
            self,
            f"Hello, type my password {secret}",
            {
                "intent": "UNKNOWN_COMMAND",
                "parameters": {},
                "accepted": False,
                "_internal_confidence": 0.42,
                "error_code": "LOW_CONFIDENCE",
            },
            secret,
        )

    def test_failedPrediction_doesNotPersistRawText(self):
        _assert_not_scheduled(
            self,
            "call private person",
            {
                "intent": "CALL_CONTACT",
                "parameters": {"contact_name": "private person"},
                "accepted": False,
                "_internal_confidence": 0.91,
                "error_code": "MISSING_REQUIRED_SLOT",
            },
            "private person",
        )

    def test_predictionException_doesNotScheduleCommandHistory(self):
        tasks = BackgroundTasks()
        with (
            patch.object(main, "try_build_custom_command_response", return_value=None),
            patch.object(
                main,
                "predict_command",
                side_effect=RuntimeError("private request content"),
            ),
            self.assertRaises(RuntimeError),
        ):
            main.predict(
                PredictRequest(
                    text="private request content",
                    language="EN",
                    device_id=ANDROID_ID,
                ),
                tasks,
                AuthenticatedInstallation(
                    device_ref_id=DEVICE_REF_ID,
                    device_id=ANDROID_ID,
                ),
            )

        self.assertEqual([], tasks.tasks)

    def test_historyRepositoryDoesNotAcceptRawUserContent(self):
        parameter_names = set(inspect.signature(record_command_history).parameters)
        forbidden = {
            "text",
            "command_text",
            "raw_text",
            "utterance",
            "response",
            "parameters",
            "text_alternatives",
            "session_id",
            "device_id",
            "credential",
            "token",
        }

        self.assertTrue(forbidden.isdisjoint(parameter_names))
        self.assertEqual(
            {
                "device_ref_id",
                "intent",
                "language",
                "accepted",
                "confidence",
                "error_code",
                "processing_time_ms",
            },
            parameter_names,
        )


class CommandHistoryRepositoryTests(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self):
        self.connection = _HistoryConnection()
        self.database_patches = (
            patch(
                "V3.database.command_history_repository.is_database_configured",
                return_value=True,
            ),
            patch(
                "V3.database.command_history_repository.open_database_connection",
                new=AsyncMock(return_value=self.connection),
            ),
        )
        for database_patch in self.database_patches:
            database_patch.start()
            self.addCleanup(database_patch.stop)

    async def _record(self):
        return await record_command_history(
            device_ref_id=DEVICE_REF_ID,
            intent="SEARCH_QUERY",
            language="EN",
            accepted=False,
            confidence=0.87,
            error_code="MISSING_REQUIRED_SLOT",
            processing_time_ms=12.34,
        )

    async def test_historyDoesNotPersistRawParameters(self):
        self.assertTrue(await self._record())
        self.assertNotIn("parameters", self.connection.insert_query.lower())
        self.assertNotIn("json", self.connection.insert_query.lower())

    async def test_commandHistoryStoresIntentMetadata(self):
        self.assertTrue(await self._record())
        self.assertIn("intent", self.connection.insert_query.lower())
        self.assertIn("SEARCH_QUERY", self.connection.insert_args)

    async def test_commandHistoryStoresLanguageConfidenceAndStatusWhereApplicable(self):
        self.assertTrue(await self._record())
        query = self.connection.insert_query.lower()
        for column in ("language", "accepted", "confidence", "error_code", "processing_time_ms"):
            with self.subTest(column=column):
                self.assertIn(column, query)
        for value in ("EN", False, 0.87, "MISSING_REQUIRED_SLOT", 12.34):
            with self.subTest(value=value):
                self.assertIn(value, self.connection.insert_args)

    async def test_commandHistoryAssociatesWithInternalDeviceReference(self):
        self.assertTrue(await self._record())
        self.assertIn("device_ref_id", self.connection.insert_query.lower())
        self.assertIn(DEVICE_REF_ID, tuple(map(str, self.connection.insert_args)))

    async def test_commandHistoryDoesNotDuplicateAndroidId(self):
        self.assertTrue(await self._record())
        self.assertNotIn(ANDROID_ID, repr(self.connection.insert_args))
        self.assertNotIn("device_id", self.connection.insert_query.lower().replace("device_ref_id", ""))

    async def test_commandHistoryDoesNotStoreBearerCredential(self):
        self.assertTrue(await self._record())
        self.assertNotIn(BEARER_CREDENTIAL, repr(self.connection.insert_args))
        self.assertNotIn("credential", self.connection.insert_query.lower())
        self.assertNotIn("token", self.connection.insert_query.lower())

    async def test_historyRepositoryRejectsFreeFormMetadata(self):
        unsafe_cases = (
            ("type my password ABC123", "EN", "LOW_CONFIDENCE"),
            ("UNKNOWN_COMMAND", "EN", "raw exception with private content"),
            ("UNKNOWN_COMMAND", "SECRET", "LOW_CONFIDENCE"),
        )
        for intent, language, error_code in unsafe_cases:
            with self.subTest(intent=intent, language=language, error_code=error_code):
                persisted = await record_command_history(
                    device_ref_id=DEVICE_REF_ID,
                    intent=intent,
                    language=language,
                    accepted=False,
                    confidence=0.5,
                    error_code=error_code,
                    processing_time_ms=1.0,
                )
                self.assertFalse(persisted)

        self.assertIsNone(self.connection.insert_query)


class CommandHistoryMigrationTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.backend_root = Path(__file__).resolve().parents[2]
        cls.migration_path = (
            cls.backend_root
            / "V3/database/migrations/009_remove_raw_command_history_content.sql"
        )

    def test_migrationRemovesSensitiveHistoryColumns(self):
        migration = self.migration_path.read_text(encoding="utf-8").lower()
        for column in ("text", "parameters_json", "session_id", "result_status"):
            with self.subTest(column=column):
                self.assertRegex(migration, rf"drop\s+column\s+if\s+exists\s+{column}\b")

    def test_migratedHistoricalRowsCannotContainOldRawText(self):
        migration = self.migration_path.read_text(encoding="utf-8").lower()
        self.assertIn("drop column if exists text", migration)
        for archive_term in ("backup", "archive", "shadow", "rename column text"):
            with self.subTest(archive_term=archive_term):
                self.assertNotIn(archive_term, migration)


class CommandHistoryDocumentationTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.repository_root = Path(__file__).resolve().parents[4]
        cls.policy = (cls.repository_root / "docs/privacy-policy.md").read_text(encoding="utf-8")
        cls.bundled_policy = (
            cls.repository_root
            / "project/Android_App/app/src/main/res/raw/privacy_policy.md"
        ).read_text(encoding="utf-8")

    def test_privacyPolicyNoLongerClaimsRawCommandPersistence(self):
        self.assertIn("Raw command text is not persisted in command history", self.policy)
        self.assertIn("Raw prediction parameters are not persisted in command history", self.policy)
        self.assertNotIn("primary command text is stored in backend command history", self.policy.lower())

    def test_bundledPrivacyPolicyMatchesRepositoryPolicy(self):
        self.assertEqual(self.policy.replace("\r\n", "\n"), self.bundled_policy.replace("\r\n", "\n"))


if __name__ == "__main__":
    unittest.main()
