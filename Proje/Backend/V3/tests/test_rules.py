import unittest

from V3.services.rule_service import rule_based_command


class RuleServiceTests(unittest.TestCase):
    def test_guard_negated_command_wins_before_matching(self):
        result = rule_based_command("do not scroll down", "EN")

        self.assertEqual(result["intent"], "UNKNOWN_COMMAND")
        self.assertEqual(result["rule_matched"], "negated_command")

    def test_scroll_rule(self):
        result = rule_based_command("scroll down", "EN")

        self.assertEqual(result["intent"], "SCROLL_SCREEN")
        self.assertEqual(result["parameters"], {"direction": "down"})
        self.assertEqual(result["rule_matched"], "scroll_down")

    def test_volume_rule(self):
        result = rule_based_command("increase the volume", "EN")

        self.assertEqual(result["intent"], "ADJUST_VOLUME")
        self.assertEqual(result["parameters"], {"volume_action": "increase"})
        self.assertEqual(result["rule_matched"], "volume_increase")

    def test_volume_level_rule(self):
        result = rule_based_command("set volume max", "EN")

        self.assertEqual(result["intent"], "ADJUST_VOLUME")
        self.assertEqual(result["parameters"], {"volume_level": "max"})
        self.assertEqual(result["rule_matched"], "volume_level_max")

    def test_turkish_volume_level_rule(self):
        result = rule_based_command("sesi orta yap", "TR")

        self.assertEqual(result["intent"], "ADJUST_VOLUME")
        self.assertEqual(result["parameters"], {"volume_level": "medium"})
        self.assertEqual(result["rule_matched"], "volume_level_medium")

    def test_navigation_rule(self):
        result = rule_based_command("go to home screen", "EN")

        self.assertEqual(result["intent"], "GO_HOME")
        self.assertEqual(result["rule_matched"], "go_home")

    def test_call_control_rules(self):
        answer = rule_based_command("answer the call", "EN")
        reject = rule_based_command("aramayi reddet", "TR")

        self.assertEqual(answer["intent"], "ANSWER_CALL")
        self.assertEqual(answer["rule_matched"], "answer_call")
        self.assertEqual(reject["intent"], "REJECT_CALL")
        self.assertEqual(reject["rule_matched"], "reject_call")

    def test_device_power_rules(self):
        power_off = rule_based_command("telefonu kapat", "TR")
        restart = rule_based_command("restart the phone", "EN")

        self.assertEqual(power_off["intent"], "POWER_OFF")
        self.assertEqual(power_off["rule_matched"], "power_off")
        self.assertEqual(restart["intent"], "RESTART_DEVICE")
        self.assertEqual(restart["rule_matched"], "restart_device")

    def test_click_item_rule_wins_for_tap_notifications(self):
        result = rule_based_command("tap notifications", "EN")

        self.assertEqual(result["intent"], "CLICK_ITEM")
        self.assertEqual(result["parameters"], {"target_text": "notifications"})
        self.assertEqual(result["rule_matched"], "click_item")

    def test_click_item_rule_wins_for_tap_home(self):
        result = rule_based_command("tap home", "EN")

        self.assertEqual(result["intent"], "CLICK_ITEM")
        self.assertEqual(result["parameters"], {"target_text": "home"})
        self.assertEqual(result["rule_matched"], "click_item")

    def test_click_item_rule_repairs_english_top_stt(self):
        examples = {
            "top Done": "done",
            "top Stop": "stop",
        }

        for text, target in examples.items():
            with self.subTest(text=text):
                result = rule_based_command(text, "EN")

                self.assertEqual(result["intent"], "CLICK_ITEM")
                self.assertEqual(result["parameters"], {"target_text": target})
                self.assertEqual(result["rule_matched"], "click_item")

    def test_click_item_rule_keeps_position_hint(self):
        result = rule_based_command("asagidaki artiya bas", "TR")

        self.assertEqual(result["intent"], "CLICK_ITEM")
        self.assertEqual(result["parameters"], {"target_text": "arti", "position": "bottom"})
        self.assertEqual(result["rule_matched"], "click_item")

    def test_click_item_rule_strips_turkish_dative_suffix(self):
        result = rule_based_command("Daire numarasına bas", "TR")

        self.assertEqual(result["intent"], "CLICK_ITEM")
        self.assertEqual(result["parameters"], {"target_text": "daire numarasi"})
        self.assertEqual(result["rule_matched"], "click_item")

    def test_click_item_rule_repairs_turkish_merged_bas_stt(self):
        result = rule_based_command("sepeti onaylayamaz", "TR")

        self.assertEqual(result["intent"], "CLICK_ITEM")
        self.assertEqual(result["parameters"], {"target_text": "sepeti onayla"})
        self.assertEqual(result["rule_matched"], "click_item")

    def test_click_item_rule_repairs_turkish_kapatamaz_stt(self):
        result = rule_based_command("T\u00fcm\u00fcn\u00fc kapatamaz", "TR")

        self.assertEqual(result["intent"], "CLICK_ITEM")
        self.assertEqual(result["parameters"], {"target_text": "tumunu kapat"})
        self.assertEqual(result["rule_matched"], "click_item")

    def test_click_item_rule_repairs_turkish_sebas_stt(self):
        result = rule_based_command("Real Sebas", "TR")

        self.assertEqual(result["intent"], "CLICK_ITEM")
        self.assertEqual(result["parameters"], {"target_text": "real"})
        self.assertEqual(result["rule_matched"], "click_item")

    def test_click_item_rule_rejects_list_index(self):
        result = rule_based_command("tap the third option", "EN")

        self.assertIsNone(result)

    def test_click_item_rule_treats_three_dots_as_icon_not_index(self):
        examples = [
            "click three dots",
            "click three dots top",
            "click on the three dots on the top of the page",
        ]

        for text in examples:
            with self.subTest(text=text):
                result = rule_based_command(text, "EN")

                self.assertEqual(result["intent"], "CLICK_ITEM")
                self.assertEqual(result["parameters"], {"target_text": "three dots", "position": "top"} if "top" in text else {"target_text": "three dots"})
                self.assertEqual(result["rule_matched"], "click_item")

    def test_click_item_rule_rejects_index_without_target_text(self):
        result = rule_based_command("ucuncusune bas", "TR")

        self.assertIsNone(result)

    def test_timer_rule(self):
        result = rule_based_command("set a timer for 5 minutes", "EN")

        self.assertEqual(result["intent"], "SET_TIMER")
        self.assertEqual(result["parameters"]["duration_seconds"], 300)
        self.assertEqual(result["rule_matched"], "set_timer")

    def test_go_back_rule(self):
        result = rule_based_command("Geri git", "TR")

        self.assertEqual(result["intent"], "GO_BACK")
        self.assertEqual(result["rule_matched"], "go_back")

    def test_brightness_rule(self):
        result = rule_based_command("Parlaklığı yükselt", "TR")

        self.assertEqual(result["intent"], "ADJUST_BRIGHTNESS")
        self.assertEqual(result["parameters"], {"brightness": "increase"})
        self.assertEqual(result["rule_matched"], "brightness_increase")

    def test_system_state_rule(self):
        result = rule_based_command("Wi-Fi aç", "TR")

        self.assertEqual(result["intent"], "SET_WIFI")
        self.assertEqual(result["parameters"], {"state": "on"})
        self.assertEqual(result["rule_matched"], "set_wifi_on")

    def test_open_notifications_rule(self):
        result = rule_based_command("bildirimleri aç", "TR")

        self.assertEqual(result["intent"], "OPEN_NOTIFICATIONS")
        self.assertEqual(result["rule_matched"], "open_notifications")

    def test_screenshot_rule(self):
        result = rule_based_command("ekran görüntüsü al", "TR")

        self.assertEqual(result["intent"], "TAKE_SCREENSHOT")
        self.assertEqual(result["rule_matched"], "take_screenshot")

    def test_text_control_rule(self):
        result = rule_based_command("metni temizle", "TR")

        self.assertEqual(result["intent"], "CLEAR_TEXT")
        self.assertEqual(result["rule_matched"], "clear_text")

    def test_turkish_search_query_rules(self):
        examples = {
            "hava durumu icin ara": "hava durumu",
            "Ahmet Kaya icin arama yap": "Ahmet Kaya",
            "Istanbul hava durumu icin arama yap": "Istanbul hava durumu",
            "hava durumu aramasi yap": "hava durumu",
            "Ahmet Kaya icin aramasi yap": "Ahmet Kaya",
            "hava durumu arama yap": "hava durumu",
            "hava durumu ile ilgili arama yap": "hava durumu",
            "sunu ara Istanbul hava durumu": "Istanbul hava durumu",
            "\u015funu ara Ahmet Kaya": "Ahmet Kaya",
        }

        for text, query in examples.items():
            with self.subTest(text=text):
                result = rule_based_command(text, "TR")

                self.assertEqual(result["intent"], "SEARCH_QUERY")
                self.assertEqual(result["parameters"], {"query": query})
                self.assertEqual(result["rule_matched"], "search_query")

    def test_arabic_search_query_rules_strip_prepositions(self):
        examples = {
            "\u0627\u0639\u062b\u0631 \u0639\u0644\u0649 \u0627\u0644\u0637\u0642\u0633": "\u0627\u0644\u0637\u0642\u0633",
            "\u0627\u0628\u062d\u062b \u062d\u0648\u0644 \u0627\u0644\u0637\u0642\u0633": "\u0627\u0644\u0637\u0642\u0633",
            "\u0627\u0628\u062d\u062b \u0639\u0646 \u0627\u0644\u0637\u0642\u0633": "\u0627\u0644\u0637\u0642\u0633",
        }

        for text, query in examples.items():
            with self.subTest(text=text):
                result = rule_based_command(text, "AR")

                self.assertEqual(result["intent"], "SEARCH_QUERY")
                self.assertEqual(result["parameters"], {"query": query})
                self.assertEqual(result["rule_matched"], "search_query")

    def test_write_text_rule_for_long_turkish_suffix(self):
        result = rule_based_command("Merhaba Ahmet bugun toplantiyi unutma yaz", "TR")

        self.assertEqual(result["intent"], "WRITE_TEXT")
        self.assertEqual(result["parameters"], {"text": "Merhaba Ahmet bugun toplantiyi unutma"})
        self.assertEqual(result["rule_matched"], "write_text")

    def test_write_text_rule_for_turkish_prefix(self):
        result = rule_based_command("şunu yaz merhaba Ahmet nasılsın", "TR")

        self.assertEqual(result["intent"], "WRITE_TEXT")
        self.assertEqual(result["parameters"], {"text": "merhaba Ahmet nasılsın"})
        self.assertEqual(result["rule_matched"], "write_text")

    def test_write_text_rule_for_english_prefix(self):
        result = rule_based_command("write remember to call Mehmet tomorrow", "EN")

        self.assertEqual(result["intent"], "WRITE_TEXT")
        self.assertEqual(result["parameters"], {"text": "remember to call Mehmet tomorrow"})
        self.assertEqual(result["rule_matched"], "write_text")

    def test_write_text_rule_for_arabic_prefix(self):
        result = rule_based_command("اكتب مرحبا احمد", "AR")

        self.assertEqual(result["intent"], "WRITE_TEXT")
        self.assertEqual(result["parameters"], {"text": "مرحبا احمد"})
        self.assertEqual(result["rule_matched"], "write_text")

    def test_sound_mode_rule(self):
        result = rule_based_command("telefonu sessiz moda al", "TR")

        self.assertEqual(result["intent"], "SET_SOUND_MODE")
        self.assertEqual(result["parameters"], {"sound_mode": "silent"})
        self.assertEqual(result["rule_matched"], "sound_mode_silent")

    def test_open_app_rule_for_spelled_turkish_app_name(self):
        result = rule_based_command("c e p t e aç", "TR")

        self.assertEqual(result["intent"], "OPEN_APP")
        self.assertEqual(result["parameters"], {"app_name": "c e p t e"})
        self.assertEqual(result["rule_matched"], "open_app")

    def test_open_app_rule_for_turkish_enter_suffix(self):
        result = rule_based_command("Instagram'a gir", "TR")

        self.assertEqual(result["intent"], "OPEN_APP")
        self.assertEqual(result["parameters"], {"app_name": "instagram"})
        self.assertEqual(result["rule_matched"], "open_app")

    def test_open_app_rule_for_english_open_prefix(self):
        result = rule_based_command("open offline games", "EN")

        self.assertEqual(result["intent"], "OPEN_APP")
        self.assertEqual(result["parameters"], {"app_name": "offline games"})
        self.assertEqual(result["rule_matched"], "open_app")

    def test_open_app_rule_for_english_open_suffix(self):
        result = rule_based_command("slug it out open", "EN")

        self.assertEqual(result["intent"], "OPEN_APP")
        self.assertEqual(result["parameters"], {"app_name": "slug it out"})
        self.assertEqual(result["rule_matched"], "open_app")

    def test_specific_open_commands_still_win_before_open_app_rule(self):
        result = rule_based_command("open notifications", "EN")

        self.assertEqual(result["intent"], "OPEN_NOTIFICATIONS")
        self.assertEqual(result["rule_matched"], "open_notifications")

    def test_screenshot_rule_wins_before_open_app_rule(self):
        result = rule_based_command("take screenshot", "EN")

        self.assertEqual(result["intent"], "TAKE_SCREENSHOT")
        self.assertEqual(result["rule_matched"], "take_screenshot")

    def test_show_recents_rule_wins_before_open_app_rule(self):
        result = rule_based_command("open recent apps", "EN")

        self.assertEqual(result["intent"], "SHOW_RECENTS")
        self.assertEqual(result["rule_matched"], "show_recents")

    def test_show_grid_rule(self):
        examples = [
            ("show grid", "EN", "show", "show_grid"),
            ("gridi kucult", "TR", "smaller", "smaller_grid"),
            ("larger grid", "EN", "larger", "larger_grid"),
            ("\u0627\u0638\u0647\u0631 \u0627\u0644\u0634\u0628\u0643\u0647", "AR", "show", "show_grid"),
        ]

        for text, language, action, rule_matched in examples:
            with self.subTest(text=text):
                result = rule_based_command(text, language)

                self.assertEqual(result["intent"], "SHOW_GRID")
                self.assertEqual(result["parameters"], {"grid_action": action})
                self.assertEqual(result["rule_matched"], rule_matched)

    def test_show_labels_rule(self):
        examples = [
            ("show labels", "EN"),
            ("show numbers", "EN"),
            ("etiketleri goster", "TR"),
            ("numaralari goster", "TR"),
            ("\u0627\u0638\u0647\u0631 \u0627\u0644\u0627\u0631\u0642\u0627\u0645", "AR"),
            ("\u0627\u0639\u0631\u0636 \u0627\u0644\u062a\u0633\u0645\u064a\u0627\u062a", "AR"),
        ]

        for text, language in examples:
            with self.subTest(text=text):
                result = rule_based_command(text, language)

                self.assertEqual(result["intent"], "SHOW_LABELS")
                self.assertEqual(result["parameters"], {"labels_action": "show"})
                self.assertEqual(result["rule_matched"], "show_labels")

    def test_app_switcher_rule_wins_before_open_app_rule(self):
        result = rule_based_command("open app switcher", "EN")

        self.assertEqual(result["intent"], "SHOW_RECENTS")
        self.assertEqual(result["rule_matched"], "show_recents")

    def test_stop_listening_rule_is_not_blocked_by_guard(self):
        result = rule_based_command("stop listening", "EN")

        self.assertEqual(result["intent"], "STOP_LISTENING")
        self.assertEqual(result["rule_matched"], "stop_listening")

    def test_stop_listening_expanded_rules(self):
        examples = [
            ("cancel command", "EN"),
            ("turn off voice assistant", "EN"),
            ("komutu iptal et", "TR"),
            ("sesli komutlari kapat", "TR"),
            ("\u0627\u0644\u063a \u0627\u0644\u0627\u0645\u0631", "AR"),
            ("\u0627\u0648\u0642\u0641 \u0627\u0644\u0645\u0633\u0627\u0639\u062f", "AR"),
        ]

        for text, language in examples:
            with self.subTest(text=text):
                result = rule_based_command(text, language)

                self.assertEqual(result["intent"], "STOP_LISTENING")
                self.assertEqual(result["rule_matched"], "stop_listening")

    def test_arabic_scroll_rule(self):
        result = rule_based_command("مرر للأسفل", "AR")

        self.assertEqual(result["intent"], "SCROLL_SCREEN")
        self.assertEqual(result["parameters"], {"direction": "down"})
        self.assertEqual(result["rule_matched"], "scroll_down")

    def test_arabic_navigation_rule(self):
        result = rule_based_command("ارجع", "AR")

        self.assertEqual(result["intent"], "GO_BACK")
        self.assertEqual(result["rule_matched"], "go_back")

    def test_arabic_back_variants_do_not_fall_through_to_scroll_model(self):
        examples = [
            "رجعني للخلف",
            "رجعني للصفحة السابقة",
            "عود للصفحة السابقة",
            "ارجع للشاشة السابقة",
            "الصفحة اللي قبل",
            "الى الخلف",
            "الى الوراء",
        ]

        for text in examples:
            with self.subTest(text=text):
                result = rule_based_command(text, "AR")

                self.assertEqual(result["intent"], "GO_BACK")
                self.assertEqual(result["rule_matched"], "go_back")

    def test_arabic_brightness_rule(self):
        result = rule_based_command("ارفع السطوع", "AR")

        self.assertEqual(result["intent"], "ADJUST_BRIGHTNESS")
        self.assertEqual(result["parameters"], {"brightness": "increase"})
        self.assertEqual(result["rule_matched"], "brightness_increase")

    def test_arabic_system_state_rule(self):
        result = rule_based_command("شغل الواي فاي", "AR")

        self.assertEqual(result["intent"], "SET_WIFI")
        self.assertEqual(result["parameters"], {"state": "on"})
        self.assertEqual(result["rule_matched"], "set_wifi_on")

    def test_arabic_open_notifications_rule(self):
        result = rule_based_command("افتح الإشعارات", "AR")

        self.assertEqual(result["intent"], "OPEN_NOTIFICATIONS")
        self.assertEqual(result["rule_matched"], "open_notifications")

    def test_arabic_screenshot_rule(self):
        result = rule_based_command("خذ لقطة شاشة", "AR")

        self.assertEqual(result["intent"], "TAKE_SCREENSHOT")
        self.assertEqual(result["rule_matched"], "take_screenshot")

    def test_arabic_text_control_rule(self):
        result = rule_based_command("امسح النص", "AR")

        self.assertEqual(result["intent"], "CLEAR_TEXT")
        self.assertEqual(result["rule_matched"], "clear_text")

    def test_arabic_sound_mode_rule(self):
        result = rule_based_command("فعل الوضع الصامت", "AR")

        self.assertEqual(result["intent"], "SET_SOUND_MODE")
        self.assertEqual(result["parameters"], {"sound_mode": "silent"})
        self.assertEqual(result["rule_matched"], "sound_mode_silent")

    def test_arabic_volume_level_rule(self):
        result = rule_based_command("اجعل الصوت متوسط", "AR")

        self.assertEqual(result["intent"], "ADJUST_VOLUME")
        self.assertEqual(result["parameters"], {"volume_level": "medium"})
        self.assertEqual(result["rule_matched"], "volume_level_medium")

    def test_arabic_timer_rule(self):
        result = rule_based_command("اضبط مؤقت خمس دقائق", "AR")

        self.assertEqual(result["intent"], "SET_TIMER")
        self.assertEqual(result["parameters"]["duration_seconds"], 300)
        self.assertEqual(result["rule_matched"], "set_timer")

    def test_arabic_open_app_rule(self):
        result = rule_based_command("افتح كاب هيروس", "AR")

        self.assertEqual(result["intent"], "OPEN_APP")
        self.assertEqual(result["parameters"], {"app_name": "كاب هيروس"})
        self.assertEqual(result["rule_matched"], "open_app")

    def test_arabic_enter_open_app_rule(self):
        result = rule_based_command("\u0627\u062f\u062e\u0644 \u064a\u0648\u062a\u064a\u0648\u0628", "AR")

        self.assertEqual(result["intent"], "OPEN_APP")
        self.assertEqual(result["parameters"], {"app_name": "\u064a\u0648\u062a\u064a\u0648\u0628"})
        self.assertEqual(result["rule_matched"], "open_app")

    def test_app_info_rule_wins_before_open_app(self):
        result = rule_based_command("open app info for chrome", "EN")

        self.assertEqual(result["intent"], "OPEN_APP_INFO")
        self.assertEqual(result["parameters"], {"app_name": "chrome"})
        self.assertEqual(result["rule_matched"], "open_app_info")

    def test_arabic_uninstall_app_rule_wins_before_open_app(self):
        result = rule_based_command("\u0627\u0644\u063a\u0627\u0621 \u062a\u062b\u0628\u064a\u062a \u064a\u0648\u062a\u064a\u0648\u0628", "AR")

        self.assertEqual(result["intent"], "UNINSTALL_APP")
        self.assertEqual(result["parameters"], {"app_name": "\u064a\u0648\u062a\u064a\u0648\u0628"})
        self.assertEqual(result["rule_matched"], "uninstall_app")

    def test_timer_rule_wins_before_start_app_rule(self):
        result = rule_based_command("start timing 10 minutes", "EN")

        self.assertEqual(result["intent"], "SET_TIMER")
        self.assertEqual(result["parameters"]["duration_seconds"], 600)
        self.assertEqual(result["rule_matched"], "set_timer")

    def test_timer_rule_wins_before_open_app_for_timer_text(self):
        result = rule_based_command("set timer for 10 minutes", "EN")

        self.assertEqual(result["intent"], "SET_TIMER")
        self.assertEqual(result["parameters"]["duration_seconds"], 600)
        self.assertEqual(result["rule_matched"], "set_timer")

    def test_volume_level_wins_before_volume_action(self):
        result = rule_based_command("set volume high", "EN")

        self.assertEqual(result["intent"], "ADJUST_VOLUME")
        self.assertEqual(result["parameters"], {"volume_level": "max"})
        self.assertEqual(result["rule_matched"], "volume_level_max")

    def test_timer_digit_duration_does_not_count_article_twice(self):
        result = rule_based_command("start a 5 minute countdown", "EN")

        self.assertEqual(result["intent"], "SET_TIMER")
        self.assertEqual(result["parameters"]["duration_seconds"], 300)
        self.assertEqual(result["rule_matched"], "set_timer")

    def test_system_command_wins_before_open_app_rule(self):
        result = rule_based_command("mobil veriyi aç", "TR")

        self.assertEqual(result["intent"], "SET_MOBILE_DATA")
        self.assertEqual(result["parameters"], {"state": "on"})
        self.assertEqual(result["rule_matched"], "set_mobile_data_on")

    def test_open_app_rejects_text_delete_commands(self):
        result = rule_based_command("delete what i wrote", "EN")

        self.assertIsNone(result)


if __name__ == "__main__":
    unittest.main()
