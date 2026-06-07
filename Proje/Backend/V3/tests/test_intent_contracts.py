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
        top_predictions=kwargs.pop("top_predictions", []),
        text_alternatives=kwargs.pop("text_alternatives", []),
        session_id=kwargs.pop("session_id", None),
        catalog_version=kwargs.pop("catalog_version", None),
        has_search_input=kwargs.pop("has_search_input", False),
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
        missing = _validate("ADJUST_BRIGHTNESS", {}, text="increase brightness")
        accepted = _validate(
            "ADJUST_BRIGHTNESS",
            {"brightness": "increase"},
            text="increase brightness",
        )

        self.assertFalse(missing["accepted"])
        self.assertEqual(missing["missing_slots"], ["brightness"])
        self.assertEqual(missing["error_code"], "MISSING_REQUIRED_SLOT")
        self.assertTrue(accepted["accepted"])
        self.assertTrue(accepted["backend_supported"])
        self.assertTrue(accepted["android_supported"])

    def test_android_supported_requires_android_parameters(self):
        missing = _validate("SCROLL_SCREEN", {}, text="scroll")
        accepted = _validate("SCROLL_SCREEN", {"direction": "down"}, text="scroll down")

        self.assertFalse(missing["accepted"])
        self.assertFalse(missing["android_supported"])
        self.assertTrue(accepted["accepted"])
        self.assertTrue(accepted["android_supported"])

    def test_navigation_intents_are_android_supported(self):
        examples = {
            "GO_BACK": "go back",
            "CLOSE_APP": "close app",
            "SHOW_RECENTS": "show recents",
            "OPEN_NOTIFICATIONS": "open notifications",
            "TAKE_SCREENSHOT": "take screenshot",
        }

        for intent, text in examples.items():
            with self.subTest(intent=intent):
                response = _validate(intent, text=text)

                self.assertTrue(response["accepted"])
                self.assertTrue(response["backend_supported"])
                self.assertTrue(response["android_supported"])

    def test_model_fallback_rejects_bare_words_for_direct_actions(self):
        screenshot = _validate(
            "TAKE_SCREENSHOT",
            {},
            text="Baklava",
            language="TR",
            confidence=0.98,
            top_predictions=[
                {"label": "TAKE_SCREENSHOT__none", "confidence": 0.98},
                {"label": "TAKE_PHOTO__none", "confidence": 0.01},
            ],
        )
        recents = _validate(
            "SHOW_RECENTS",
            {},
            text="\u015eark\u0131lar",
            language="TR",
            confidence=0.95,
            top_predictions=[
                {"label": "SHOW_RECENTS__none", "confidence": 0.95},
                {"label": "OPEN_APP__none", "confidence": 0.02},
            ],
        )

        self.assertFalse(screenshot["accepted"])
        self.assertEqual(screenshot["intent"], "UNKNOWN_COMMAND")
        self.assertEqual(screenshot["error_code"], "WEAK_COMMAND_SHAPE")
        self.assertFalse(recents["accepted"])
        self.assertEqual(recents["intent"], "UNKNOWN_COMMAND")
        self.assertEqual(recents["error_code"], "WEAK_COMMAND_SHAPE")

    def test_stop_listening_requires_strong_model_prediction(self):
        strong = _validate(
            "STOP_LISTENING",
            {},
            text="stop listening",
            language="EN",
            confidence=0.93,
            top_predictions=[
                {"label": "STOP_LISTENING__none", "confidence": 0.93},
                {"label": "UNKNOWN_COMMAND__none", "confidence": 0.30},
            ],
        )

        self.assertTrue(strong["accepted"])
        self.assertTrue(strong["android_supported"])

        weak = _validate(
            "STOP_LISTENING",
            {},
            text="Just once sabas",
            language="EN",
            confidence=0.72,
            top_predictions=[
                {"label": "STOP_LISTENING__none", "confidence": 0.72},
                {"label": "UNKNOWN_COMMAND__none", "confidence": 0.48},
            ],
        )

        self.assertFalse(weak["accepted"])
        self.assertEqual(weak["error_code"], "WEAK_STOP_LISTENING_COMMAND")

    def test_stop_listening_rejects_ambiguous_model_prediction(self):
        ambiguous = _validate(
            "STOP_LISTENING",
            {},
            text="stop something",
            language="EN",
            confidence=0.91,
            top_predictions=[
                {"label": "STOP_LISTENING__none", "confidence": 0.91},
                {"label": "UNKNOWN_COMMAND__none", "confidence": 0.79},
            ],
        )

        self.assertFalse(ambiguous["accepted"])
        self.assertEqual(ambiguous["error_code"], "WEAK_STOP_LISTENING_COMMAND")

    def test_text_and_center_gesture_intents_are_android_supported(self):
        examples = {
            "CLEAR_TEXT": "clear text",
            "DOUBLE_TAP": "double tap",
            "HOLD_SCREEN": "hold screen",
        }

        for intent, text in examples.items():
            with self.subTest(intent=intent):
                response = _validate(intent, text=text)

                self.assertTrue(response["accepted"])
                self.assertTrue(response["backend_supported"])
                self.assertTrue(response["android_supported"])

    def test_click_item_extracts_target_and_position(self):
        bottom_plus = _validate("CLICK_ITEM", {}, text="a\u015fa\u011f\u0131daki art\u0131ya bas", language="TR")
        search = _validate("CLICK_ITEM", {}, text="tap the search button", language="EN")
        third_option = _validate("CLICK_ITEM", {}, text="tap the third option", language="EN")
        second_video = _validate("CLICK_ITEM", {}, text="ikinci videoya bas", language="TR")
        arabic = _validate(
            "CLICK_ITEM",
            {},
            text="\u0627\u0636\u063a\u0637 \u0639\u0644\u0649 \u0627\u0644\u0628\u062d\u062b",
            language="AR",
        )

        self.assertTrue(bottom_plus["accepted"])
        self.assertTrue(bottom_plus["android_supported"])
        self.assertEqual(bottom_plus["parameters"]["target_text"], "arti")
        self.assertEqual(bottom_plus["parameters"]["position"], "bottom")

        self.assertTrue(search["accepted"])
        self.assertEqual(search["parameters"]["target_text"], "search button")

        self.assertTrue(third_option["accepted"])
        self.assertEqual(third_option["parameters"]["target_text"], "option")
        self.assertEqual(third_option["parameters"]["target_index"], 3)

        self.assertTrue(second_video["accepted"])
        self.assertEqual(second_video["parameters"]["target_text"], "video")
        self.assertEqual(second_video["parameters"]["target_index"], 2)

        self.assertTrue(arabic["accepted"])
        self.assertEqual(arabic["parameters"]["target_text"], "\u0627\u0644\u0628\u062d\u062b")

    def test_click_item_allows_index_without_target_text(self):
        response = _validate("CLICK_ITEM", {}, text="\u0627\u0636\u063a\u0637 \u0639\u0644\u0649 \u0627\u0644\u062b\u0627\u0644\u062b", language="AR")

        self.assertTrue(response["accepted"])
        self.assertTrue(response["android_supported"])
        self.assertEqual(response["parameters"]["target_index"], 3)

    def test_system_setting_intents_are_android_supported(self):
        examples = {
            "SET_WIFI": {"state": "on"},
            "SET_BLUETOOTH": {"state": "on"},
            "SET_FLASHLIGHT": {"state": "on"},
            "SET_LOCATION": {"state": "on"},
            "SET_MOBILE_DATA": {"state": "on"},
            "SET_MOBILE_HOTSPOT": {"state": "on"},
            "SET_SOUND_MODE": {"sound_mode": "silent"},
            "SET_KEYBOARD": {"state": "open"},
            "ADJUST_BRIGHTNESS": {"brightness": "increase"},
        }

        for intent, parameters in examples.items():
            with self.subTest(intent=intent):
                response = _validate(intent, parameters, text=f"turn on {intent.lower()}")

                self.assertTrue(response["accepted"])
                self.assertTrue(response["backend_supported"])
                self.assertTrue(response["android_supported"])

    def test_app_management_intents_are_android_supported(self):
        session_id = "unit-test-app-management"
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

            examples = {
                "OPEN_APP_INFO": {
                    "app_name": "WhatsApp",
                    "app_package_name": "com.whatsapp",
                },
                "UNINSTALL_APP": {
                    "app_name": "WhatsApp",
                    "app_package_name": "com.whatsapp",
                },
            }

            for intent, parameters in examples.items():
                with self.subTest(intent=intent):
                    response = _validate(
                        intent,
                        parameters,
                        session_id=session_id,
                        catalog_version=result["catalog_version"],
                    )

                    self.assertTrue(response["accepted"])
                    self.assertTrue(response["backend_supported"])
                    self.assertTrue(response["android_supported"])
        finally:
            delete_app_catalog(session_id)

    def test_parameter_group_allows_volume_level_and_marks_android_supported(self):
        backend_supported = _validate(
            "ADJUST_VOLUME",
            {"volume_level": "max"},
            text="set volume to max",
        )
        android_supported = _validate(
            "ADJUST_VOLUME",
            {"volume_action": "increase"},
            text="increase volume",
        )

        self.assertTrue(backend_supported["accepted"])
        self.assertTrue(backend_supported["backend_supported"])
        self.assertTrue(backend_supported["android_supported"])
        self.assertTrue(android_supported["accepted"])
        self.assertTrue(android_supported["android_supported"])

    def test_enriches_text_parameters(self):
        search = _validate("SEARCH_QUERY", {}, text="search for weather")
        arabic_search = _validate(
            "SEARCH_QUERY",
            {},
            text="\u0628\u062d\u062b \u0639\u0646 \u0627\u0644\u0637\u0642\u0633",
            language="AR",
        )
        arabic_search_without_preposition = _validate(
            "SEARCH_QUERY",
            {},
            text="\u0627\u0628\u062d\u062b \u0627\u0644\u0637\u0642\u0633",
            language="AR",
        )
        write = _validate("WRITE_TEXT", {}, text='write "Meeting starts at 5"')

        self.assertTrue(search["accepted"])
        self.assertTrue(search["android_supported"])
        self.assertEqual(search["parameters"]["query"], "weather")
        self.assertTrue(arabic_search["accepted"])
        self.assertEqual(arabic_search["parameters"]["query"], "\u0627\u0644\u0637\u0642\u0633")
        self.assertTrue(arabic_search_without_preposition["accepted"])
        self.assertEqual(arabic_search_without_preposition["parameters"]["query"], "\u0627\u0644\u0637\u0642\u0633")
        self.assertTrue(write["accepted"])
        self.assertTrue(write["android_supported"])
        self.assertEqual(write["parameters"]["text"], "Meeting starts at 5")

    def test_resolves_turkish_single_word_ara_as_contact(self):
        response = _validate(
            "SEARCH_QUERY",
            {},
            text="Abdullah'i ara",
            language="TR",
            has_search_input=True,
        )

        self.assertTrue(response["accepted"])
        self.assertEqual(response["intent"], "CALL_CONTACT")
        self.assertEqual(response["parameters"]["contact_name"], "abdullah")

    def test_resolves_turkish_query_hints_as_search(self):
        response = _validate(
            "CALL_CONTACT",
            {},
            text="hava durumu ara",
            language="TR",
        )

        self.assertTrue(response["accepted"])
        self.assertEqual(response["intent"], "SEARCH_QUERY")
        self.assertEqual(response["parameters"]["query"], "hava durumu")

    def test_resolves_turkish_long_ara_subject_as_search(self):
        response = _validate(
            "CALL_CONTACT",
            {},
            text="android studio emulator internet sorunu ara",
            language="TR",
        )

        self.assertTrue(response["accepted"])
        self.assertEqual(response["intent"], "SEARCH_QUERY")
        self.assertEqual(response["parameters"]["query"], "android studio emulator internet sorunu")

    def test_resolves_turkish_multi_word_ara_with_search_input_as_search(self):
        response = _validate(
            "CALL_CONTACT",
            {},
            text="Ahmet Kaya ara",
            language="TR",
            has_search_input=True,
        )

        self.assertTrue(response["accepted"])
        self.assertEqual(response["intent"], "SEARCH_QUERY")
        self.assertEqual(response["parameters"]["query"], "ahmet kaya")

    def test_resolves_turkish_multi_word_ara_without_search_input_as_contact(self):
        response = _validate(
            "SEARCH_QUERY",
            {},
            text="Mehmet abi ara",
            language="TR",
            has_search_input=False,
        )

        self.assertTrue(response["accepted"])
        self.assertEqual(response["intent"], "CALL_CONTACT")
        self.assertEqual(response["parameters"]["contact_name"], "mehmet abi")

    def test_enriches_alarm_time_parameters(self):
        morning = _validate("SET_ALARM", {}, text="saat 5 icin alarm kur")
        evening = _validate("SET_ALARM", {}, text="set an alarm for 5 pm")
        turkish_evening = _validate(
            "SET_ALARM",
            {"alarm_hour": 5},
            text="saat ak\u015fam 5 i\u00e7in alarm kur",
            language="TR",
        )
        turkish_morning = _validate(
            "SET_ALARM",
            {"alarm_hour": 17},
            text="sabah 5 i\u00e7in alarm kur",
            language="TR",
        )
        scheduled_day = _validate("SET_ALARM", {}, text="pazartesi saat 17 icin alarm kur")

        self.assertTrue(morning["accepted"])
        self.assertTrue(morning["android_supported"])
        self.assertEqual(morning["parameters"]["alarm_hour"], 5)
        self.assertEqual(morning["parameters"]["alarm_minute"], 0)

        self.assertTrue(evening["accepted"])
        self.assertEqual(evening["parameters"]["alarm_hour"], 17)
        self.assertEqual(evening["parameters"]["period"], "pm")

        self.assertTrue(turkish_evening["accepted"])
        self.assertEqual(turkish_evening["parameters"]["alarm_hour"], 17)
        self.assertEqual(turkish_evening["parameters"]["period"], "pm")

        self.assertTrue(turkish_morning["accepted"])
        self.assertEqual(turkish_morning["parameters"]["alarm_hour"], 5)
        self.assertEqual(turkish_morning["parameters"]["period"], "am")

        self.assertTrue(scheduled_day["accepted"])
        self.assertEqual(scheduled_day["parameters"]["alarm_hour"], 17)
        self.assertEqual(scheduled_day["parameters"]["day"], "monday")

    def test_predict_take_photo_rule_enriches_camera_parameter(self):
        examples = [
            ("arka kamera ile fotograf cek", "TR", "back"),
            ("front camera and capture", "EN", "front"),
        ]

        for text, language, camera in examples:
            with self.subTest(text=text):
                response = predict_command(text=text, language=language)

                self.assertTrue(response["accepted"])
                self.assertEqual(response["intent"], "TAKE_PHOTO")
                self.assertEqual(response["parameters"]["camera"], camera)

    def test_take_photo_validation_enriches_selfie_parameter(self):
        response = _validate("TAKE_PHOTO", {}, text="take a selfie")

        self.assertTrue(response["accepted"])
        self.assertEqual(response["parameters"]["camera"], "front")

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
