import unittest

from V3.extraction.timer import extract_timer
from V3.services.predict_service import predict_command


class ArabicTimerExtractionTests(unittest.TestCase):
    def assert_duration(self, text, value, unit, seconds):
        result = extract_timer(text)

        self.assertEqual(result["duration_value"], value)
        self.assertEqual(result["duration_unit"], unit)
        self.assertEqual(result["duration_seconds"], seconds)

    def test_arabic_attached_conjunction_parses_twenty_five_minutes(self):
        self.assert_duration("خمسة وعشرون دقيقة", 25, "minute", 1500)

    def test_arabic_prefixed_minute_ba_parses_one_minute(self):
        self.assert_duration("بدقيقة", 1, "minute", 60)

    def test_arabic_prefixed_minute_bial_parses_one_minute(self):
        self.assert_duration("بالدقيقة", 1, "minute", 60)

    def test_arabic_prefixed_minute_lam_parses_one_minute(self):
        self.assert_duration("لدقيقة", 1, "minute", 60)

    def test_arabic_prefixed_minute_double_lam_parses_one_minute(self):
        self.assert_duration("للدقيقة", 1, "minute", 60)

    def test_arabic_article_prefixed_minute_parses_one_minute(self):
        self.assert_duration("الدقيقة", 1, "minute", 60)

    def test_existing_arabic_timer_forms_remain_supported(self):
        examples = [
            ("خمس دقائق", 5, "minute", 300),
            ("عشر ثواني", 10, "second", 10),
            ("ساعة", 1, "hour", 3600),
            ("دقيقتين", 2, "minute", 120),
            ("٥ دقائق", 5, "minute", 300),
            ("خمسة و عشرون دقيقة", 25, "minute", 1500),
        ]

        for text, value, unit, seconds in examples:
            with self.subTest(text=text):
                self.assert_duration(text, value, unit, seconds)

    def test_additional_attached_conjunction_parses_twenty_one_minutes(self):
        self.assert_duration("واحد وعشرون دقيقة", 21, "minute", 1260)

    def test_arabic_timer_pipeline_returns_valid_set_timer_response(self):
        result = predict_command("اضبط مؤقت خمسة وعشرون دقيقة", "AR")

        self.assertTrue(result["accepted"])
        self.assertEqual(result["intent"], "SET_TIMER")
        self.assertEqual(result["parameters"]["duration_value"], 25)
        self.assertEqual(result["parameters"]["duration_unit"], "minute")
        self.assertEqual(result["parameters"]["duration_seconds"], 1500)


if __name__ == "__main__":
    unittest.main()
