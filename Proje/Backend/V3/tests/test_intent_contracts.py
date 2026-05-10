import unittest

from V3.services.app_catalog_service import delete_app_catalog, save_app_catalog
from V3.services.intent_registry import get_intent_contract, missing_required_parameters
from V3.services.model_service import label_to_json
from V3.services.validator import validate_and_build_response


def _validate(intent, parameters=None, text="test command", confidence=0.99, **kwargs):
    return validate_and_build_response(
        original_text=text,
        language=kwargs.pop("language", "EN"),
        model_intent=intent,
        model_parameters=parameters or {},
        confidence=confidence,
        raw_label=kwargs.pop("raw_label", f"{intent}__test"),
        top_predictions=[],
        text_alternatives=kwargs.pop("text_alternatives", []),
        session_id=kwargs.pop("session_id", None),
        catalog_version=kwargs.pop("catalog_version", None),
    )


class IntentContractTests(unittest.TestCase):
    def test_label_to_json_preserves_backend_unsupported_intent(self):
        result = label_to_json("ADJUST_BRIGHTNESS__brightness=increase")

        self.assertEqual(result["intent"], "ADJUST_BRIGHTNESS")
        self.assertEqual(result["parameters"], {"brightness": "increase"})

    def test_unknown_command_is_distinct_from_unsupported_intent(self):
        unknown = _validate("UNKNOWN_COMMAND")
        unsupported = _validate("FUTURE_INTENT")

        self.assertEqual(unknown["intent"], "UNKNOWN_COMMAND")
        self.assertEqual(unknown["error_code"], "UNKNOWN_COMMAND")
        self.assertEqual(unsupported["intent"], "FUTURE_INTENT")
        self.assertEqual(unsupported["error_code"], "UNSUPPORTED_INTENT")

    def test_registry_reports_missing_required_parameters(self):
        contract = get_intent_contract("ADJUST_BRIGHTNESS")

        self.assertEqual(missing_required_parameters(contract, {}), ["brightness"])
        self.assertEqual(missing_required_parameters(contract, {"brightness": "increase"}), [])

    def test_validator_uses_registry_required_parameters(self):
        missing = _validate("ADJUST_BRIGHTNESS", {})
        accepted = _validate("ADJUST_BRIGHTNESS", {"brightness": "increase"})

        self.assertFalse(missing["accepted"])
        self.assertEqual(missing["missing_slots"], ["brightness"])
        self.assertEqual(missing["error_code"], "MISSING_REQUIRED_SLOT")
        self.assertTrue(accepted["accepted"])
        self.assertTrue(accepted["backend_supported"])
        self.assertFalse(accepted["android_supported"])

    def test_android_supported_requires_android_parameters(self):
        missing = _validate("SCROLL_SCREEN", {})
        accepted = _validate("SCROLL_SCREEN", {"direction": "down"})

        self.assertFalse(missing["accepted"])
        self.assertFalse(missing["android_supported"])
        self.assertTrue(accepted["accepted"])
        self.assertTrue(accepted["android_supported"])

    def test_parameter_group_allows_volume_level_but_marks_android_unsupported(self):
        backend_supported = _validate("ADJUST_VOLUME", {"volume_level": "max"})
        android_supported = _validate("ADJUST_VOLUME", {"volume_action": "increase"})

        self.assertTrue(backend_supported["accepted"])
        self.assertTrue(backend_supported["backend_supported"])
        self.assertFalse(backend_supported["android_supported"])
        self.assertTrue(android_supported["accepted"])
        self.assertTrue(android_supported["android_supported"])

    def test_enriches_text_parameters(self):
        search = _validate("SEARCH_QUERY", {}, text="search for weather")
        write = _validate("WRITE_TEXT", {}, text='write "Meeting starts at 5"')

        self.assertTrue(search["accepted"])
        self.assertEqual(search["parameters"]["query"], "weather")
        self.assertTrue(write["accepted"])
        self.assertEqual(write["parameters"]["text"], "Meeting starts at 5")

    def test_app_catalog_enrichment_sets_package_contract(self):
        session_id = "unit-test-session"
        try:
            result = save_app_catalog(
                session_id=session_id,
                catalog_version="v1",
                language="EN",
                apps=[
                    {
                        "label": "WhatsApp",
                        "package_name": "com.whatsapp",
                        "aliases": ["whatsapp"],
                    }
                ],
            )

            response = _validate(
                "OPEN_APP",
                {},
                text="open WhatsApp",
                session_id=session_id,
                catalog_version=result["catalog_version"],
            )

            self.assertTrue(response["accepted"])
            self.assertTrue(response["android_supported"])
            self.assertEqual(response["parameters"]["app_package_name"], "com.whatsapp")
        finally:
            delete_app_catalog(session_id)


if __name__ == "__main__":
    unittest.main()
