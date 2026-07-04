import unittest

from V3.database.custom_command_repository import (
    _edit_distance_at_most_one,
    _normalize_name_for_matching,
)


class CustomCommandMatchingTests(unittest.TestCase):
    def test_arabic_name_matching_ignores_common_letter_variants(self):
        self.assertEqual(
            _normalize_name_for_matching("\u0627\u0644\u0642\u0627\u0626\u0645\u0629", "AR"),
            _normalize_name_for_matching("\u0623\u0644\u0642\u0627\u064a\u0645\u0629", "AR"),
        )
        self.assertEqual(
            _normalize_name_for_matching("\u0646\u0647\u0627\u064a\u0629", "AR"),
            _normalize_name_for_matching("\u0646\u0647\u0627\u064a\u0647", "AR"),
        )

    def test_custom_command_relaxed_match_allows_one_character_difference(self):
        self.assertTrue(_edit_distance_at_most_one("\u0645\u062d\u0645\u062f", "\u0645\u062d\u0645\u062f"))
        self.assertTrue(_edit_distance_at_most_one("\u0645\u062d\u0645\u062f", "\u0645\u062d\u0645\u0629"))
        self.assertFalse(_edit_distance_at_most_one("\u0645\u062d\u0645\u062f", "\u0645\u062d\u0645\u062a\u0627"))


if __name__ == "__main__":
    unittest.main()
