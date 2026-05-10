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

    def test_navigation_rule(self):
        result = rule_based_command("go to home screen", "EN")

        self.assertEqual(result["intent"], "GO_HOME")
        self.assertEqual(result["rule_matched"], "go_home")

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

    def test_sound_mode_rule(self):
        result = rule_based_command("telefonu sessiz moda al", "TR")

        self.assertEqual(result["intent"], "SET_SOUND_MODE")
        self.assertEqual(result["parameters"], {"sound_mode": "silent"})
        self.assertEqual(result["rule_matched"], "sound_mode_silent")


if __name__ == "__main__":
    unittest.main()
