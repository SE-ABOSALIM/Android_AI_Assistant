import unittest

from V3.services.app_catalog_service import delete_app_catalog, save_app_catalog
from V3.intents.registry import get_intent_contract, missing_required_parameters
from V3.services.model_service import label_to_json
from V3.services.predict_service import predict_command
from V3.services.validation_service import validate_and_build_response


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

    def test_label_to_json_preserves_max_volume_level(self):
        result = label_to_json("ADJUST_VOLUME__volume_level=max")

        self.assertEqual(result["intent"], "ADJUST_VOLUME")
        self.assertEqual(result["parameters"], {"volume_level": "max"})

    def test_label_to_json_decodes_key_value_parameters(self):
        scroll = label_to_json("SCROLL_SCREEN__direction=down")
        swipe = label_to_json("SWIPE_GESTURE__direction=left")
        volume = label_to_json("ADJUST_VOLUME__volume_action=increase")

        self.assertEqual(scroll["parameters"], {"direction": "down"})
        self.assertEqual(swipe["parameters"], {"direction": "left"})
        self.assertEqual(volume["parameters"], {"volume_action": "increase"})

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

    def test_parameter_group_allows_volume_level_and_marks_android_supported(self):
        backend_supported = _validate("ADJUST_VOLUME", {"volume_level": "max"})
        android_supported = _validate("ADJUST_VOLUME", {"volume_action": "increase"})

        self.assertTrue(backend_supported["accepted"])
        self.assertTrue(backend_supported["backend_supported"])
        self.assertTrue(backend_supported["android_supported"])
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

    def test_predict_open_app_rule_bypasses_model_for_spelled_app_name(self):
        session_id = "unit-test-spelled-app"
        try:
            result = save_app_catalog(
                session_id=session_id,
                catalog_version="v1",
                language="TR",
                apps=[
                    {
                        "label": "Cepte Var",
                        "package_name": "com.ceptevar",
                        "aliases": [],
                    }
                ],
            )

            response = predict_command(
                text="c e p t e aç",
                language="TR",
                session_id=session_id,
                catalog_version=result["catalog_version"],
            )

            self.assertTrue(response["accepted"])
            self.assertEqual(response["intent"], "OPEN_APP")
            self.assertEqual(response["raw_label"], "RULE::open_app")
            self.assertEqual(response["parameters"]["app_package_name"], "com.ceptevar")
        finally:
            delete_app_catalog(session_id)

    def test_predict_arabic_open_app_uses_catalog_phonetic_aliases(self):
        session_id = "unit-test-arabic-open-app"
        try:
            result = save_app_catalog(
                session_id=session_id,
                catalog_version="v1",
                language="AR",
                apps=[
                    {
                        "label": "Cap Heroes",
                        "package_name": "com.example.capheroes",
                        "aliases": ["capheroes"],
                    },
                    {
                        "label": "Burrito Bison",
                        "package_name": "com.example.burritobison",
                        "aliases": ["burritobison"],
                    },
                ],
            )

            examples = [
                ("\u0627\u0641\u062a\u062d \u0643\u0627\u0628 \u0647\u064a\u0631\u0648\u0633", "com.example.capheroes"),
                ("\u0627\u0641\u062a\u062d \u0628\u0648\u0631\u064a\u062a\u0648 \u0628\u064a\u0633\u0648\u0646", "com.example.burritobison"),
            ]

            for text, package_name in examples:
                with self.subTest(text=text):
                    response = predict_command(
                        text=text,
                        language="AR",
                        session_id=session_id,
                        catalog_version=result["catalog_version"],
                    )

                    self.assertTrue(response["accepted"])
                    self.assertEqual(response["intent"], "OPEN_APP")
                    self.assertEqual(response["parameters"]["app_package_name"], package_name)
        finally:
            delete_app_catalog(session_id)


if __name__ == "__main__":
    unittest.main()
