import ast
import io
import unittest
from contextlib import redirect_stdout
from pathlib import Path
from unittest.mock import patch

from fastapi import BackgroundTasks

from V3 import main
from V3.database.installation_auth_repository import find_installation_by_credential
from V3.schemas import PredictRequest
from V3.security.authentication import AuthenticatedInstallation


class _FailingCredentialConnection:
    async def fetchrow(self, query, *args):
        raise RuntimeError("Authorization: Bearer raw-secret-must-not-leak")

    async def close(self):
        return None


class PrivacyLoggingTests(unittest.IsolatedAsyncioTestCase):
    def test_predictProductionLogs_doNotContainRawUserTextOrParameters(self):
        raw_text = "call private-contact at private-number"
        private_parameter = "private-contact"
        response = {
            "accepted": True,
            "intent": "CALL_CONTACT",
            "parameters": {"contact_name": private_parameter},
            "_internal_confidence": 0.99,
        }

        output = io.StringIO()
        with (
            patch.object(main, "try_build_custom_command_response", return_value=response),
            redirect_stdout(output),
        ):
            main.predict(
                PredictRequest(
                    text=raw_text,
                    language="EN",
                    device_id="device-a",
                ),
                BackgroundTasks(),
                AuthenticatedInstallation(
                    device_ref_id="aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                    device_id="device-a",
                ),
            )

        logged = output.getvalue()
        self.assertIn("[predict] processed", logged)
        self.assertIn("duration_ms=", logged)
        self.assertNotIn(raw_text, logged)
        self.assertNotIn(private_parameter, logged)
        self.assertNotIn("parameters=", logged)

    async def test_authTokenAndAuthorizationHeader_areNeverLogged(self):
        output = io.StringIO()
        with (
            patch(
                "V3.database.installation_auth_repository.is_database_configured",
                return_value=True,
            ),
            patch(
                "V3.database.installation_auth_repository.open_database_connection",
                return_value=_FailingCredentialConnection(),
            ),
            redirect_stdout(output),
        ):
            result = await find_installation_by_credential("raw-secret-must-not-leak")

        self.assertIsNone(result)
        logged = output.getvalue()
        self.assertIn("error=RuntimeError", logged)
        self.assertNotIn("raw-secret-must-not-leak", logged)
        self.assertNotIn("Authorization", logged)
        self.assertNotIn("Bearer", logged)

    def test_productionPrintCalls_doNotReferenceSensitiveRequestOrCredentialValues(self):
        backend_root = Path(__file__).resolve().parents[1]
        forbidden = (
            "request.text",
            "response.get('parameters')",
            'response.get("parameters")',
            "raw_credential",
            "credentials.credentials",
            "authorization",
            "device_id",
            "package_name",
            "catalog_version",
        )

        for source_path in backend_root.rglob("*.py"):
            if "tests" in source_path.parts:
                continue
            source = source_path.read_text(encoding="utf-8")
            tree = ast.parse(source)
            for node in ast.walk(tree):
                if not (
                    isinstance(node, ast.Call)
                    and isinstance(node.func, ast.Name)
                    and node.func.id == "print"
                ):
                    continue
                print_source = ast.get_source_segment(source, node) or ""
                lowered = print_source.lower()
                for sensitive_reference in forbidden:
                    with self.subTest(
                        source=source_path.name,
                        reference=sensitive_reference,
                    ):
                        self.assertNotIn(sensitive_reference.lower(), lowered)


if __name__ == "__main__":
    unittest.main()
