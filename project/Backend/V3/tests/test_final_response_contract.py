import unittest
from unittest.mock import Mock, patch

from fastapi.testclient import TestClient

from V3 import main
from V3.security.authentication import AuthenticatedInstallation


FINAL_RESPONSE_FIELDS = {
    "intent",
    "parameters",
    "accepted",
    "missing_slots",
    "error_code",
    "error_message",
}


def _legacy_shaped_response(*, accepted, missing_slots, error_code, error_message):
    return {
        "input": "scroll down",
        "normalized_input": "scroll down",
        "language": "EN",
        "intent": "SCROLL_SCREEN",
        "parameters": {"direction": "down"} if accepted else {},
        "backend_supported": True,
        "android_supported": accepted,
        "parameter_contract": {
            "required": ["direction"],
            "one_of": [],
            "optional": [],
            "parameters": ["direction"],
        },
        "accepted": accepted,
        "missing_slots": missing_slots,
        "error_code": error_code,
        "error_message": error_message,
        "needs_confirmation": not accepted,
        "confidence": 0.99,
        "threshold": 0.92,
        "raw_label": "SCROLL_SCREEN__direction=down",
        "processing_time_ms": 1.0,
        "top_predictions": [
            {"label": "SCROLL_SCREEN__direction=down", "confidence": 0.99}
        ],
        "_internal_confidence": 0.99,
    }


class FinalResponseContractTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.client = TestClient(main.app)

    def setUp(self):
        main.app.dependency_overrides[main.predict_access] = lambda: AuthenticatedInstallation(
            device_ref_id="aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
            device_id="device-a",
        )

    def tearDown(self):
        main.app.dependency_overrides.pop(main.predict_access, None)

    def _predict(self, prediction_response):
        with (
            patch.object(
                main,
                "try_build_custom_command_response",
                return_value=prediction_response,
            ),
            patch.object(main, "_record_command_history_background", new=Mock()),
        ):
            return self.client.post(
                "/predict",
                json={"text": "scroll down", "language": "EN", "device_id": "device-a"},
            )

    def test_acceptedPrediction_exposesOnlyVerifiedPublicContract(self):
        response = self._predict(
            _legacy_shaped_response(
                accepted=True,
                missing_slots=[],
                error_code=None,
                error_message=None,
            )
        )

        self.assertEqual(200, response.status_code)
        payload = response.json()
        self.assertEqual(FINAL_RESPONSE_FIELDS, set(payload))
        self.assertEqual("SCROLL_SCREEN", payload["intent"])
        self.assertEqual({"direction": "down"}, payload["parameters"])
        self.assertTrue(payload["accepted"])

    def test_rejectedPrediction_exposesOnlyVerifiedPublicContract(self):
        response = self._predict(
            _legacy_shaped_response(
                accepted=False,
                missing_slots=["direction"],
                error_code="MISSING_REQUIRED_SLOT",
                error_message="Required parameter is missing.",
            )
        )

        self.assertEqual(200, response.status_code)
        payload = response.json()
        self.assertEqual(FINAL_RESPONSE_FIELDS, set(payload))
        self.assertFalse(payload["accepted"])
        self.assertEqual(["direction"], payload["missing_slots"])
        self.assertEqual("MISSING_REQUIRED_SLOT", payload["error_code"])
        self.assertEqual("Required parameter is missing.", payload["error_message"])


if __name__ == "__main__":
    unittest.main()
