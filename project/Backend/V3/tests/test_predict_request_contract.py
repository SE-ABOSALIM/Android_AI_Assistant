import unittest
from unittest.mock import AsyncMock, Mock, patch

from fastapi.testclient import TestClient

from V3 import main
from V3.schemas import PredictRequest


class PredictRequestContractTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.client = TestClient(main.app)

    def test_contract_contains_only_active_prediction_inputs(self):
        self.assertEqual(
            {
                "text",
                "language",
                "text_alternatives",
                "device_id",
                "catalog_version",
                "has_search_input",
            },
            set(PredictRequest.model_fields),
        )

    def test_predict_without_session_id_preserves_successful_request_behavior(self):
        prediction = {
            "intent": "OPEN_APP",
            "parameters": {"app_package_name": "com.example.maps"},
            "accepted": True,
            "missing_slots": [],
            "error_code": None,
            "error_message": None,
            "_internal_confidence": 0.99,
        }
        predict_command = Mock(return_value=prediction)
        identity = {
            "device_ref_id": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
            "device_id": "device-a",
        }

        with (
            patch(
                "V3.security.authentication.find_installation_by_credential",
                new=AsyncMock(return_value=identity),
            ),
            patch("V3.security.rate_limit.enforce_rate_limit", new=AsyncMock()),
            patch.object(main, "try_build_custom_command_response", return_value=None),
            patch.object(main, "predict_command", new=predict_command),
            patch.object(main, "_record_command_history_background", new=Mock()),
        ):
            response = self.client.post(
                "/predict",
                json={
                    "text": "open maps",
                    "language": "TR",
                    "text_alternatives": ["open map application"],
                    "device_id": "device-a",
                    "catalog_version": "catalog-v1",
                    "has_search_input": True,
                },
                headers={"Authorization": "Bearer valid-credential"},
            )

        self.assertEqual(200, response.status_code)
        self.assertEqual("OPEN_APP", response.json()["intent"])
        predict_command.assert_called_once_with(
            text="open maps",
            language="TR",
            text_alternatives=["open map application"],
            session_id="device-a",
            device_id="device-a",
            catalog_version="catalog-v1",
            has_search_input=True,
        )


if __name__ == "__main__":
    unittest.main()
