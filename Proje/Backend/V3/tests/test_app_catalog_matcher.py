import unittest
from unittest.mock import patch

from V3.app_catalog.indexer import _build_catalog_search_index
from V3.app_catalog.matcher import resolve_app_match
from V3.services.app_catalog_service import save_app_catalog
from V3.services.predict_service import predict_command



def _catalog(apps, language="EN"):
    catalog = save_app_catalog(
        session_id="unit-test-catalog",
        catalog_version="v1",
        language=language,
        apps=apps,
    )
    catalog["search_index"] = _build_catalog_search_index(catalog["apps"])
    return catalog


def _mock_catalog(catalog):
    return patch("V3.app_catalog.matcher._get_catalog", return_value=catalog)


def _mock_validation_catalog(catalog):
    patches = [
        patch("V3.app_catalog.matcher._get_catalog", return_value=catalog),
        patch("V3.validation.app_matching.has_app_catalog", return_value=True),
        patch("V3.validation.app_matching.is_catalog_version_current", return_value=True),
    ]
    return patches


class AppCatalogMatcherTests(unittest.TestCase):
    def test_spelled_partial_app_name_opens_unique_prefix_or_substring_match(self):
        session_id = "unit-test-partial-spelled-app"
        result = _catalog([
            {
                "label": "Telegram",
                "package_name": "org.telegram.messenger",
                "aliases": [],
            },
            {
                "label": "ChatGPT",
                "package_name": "com.openai.chatgpt",
                "aliases": [],
            },
        ])

        examples = [
            ("open t e l", "org.telegram.messenger"),
            ("open t e l e", "org.telegram.messenger"),
            ("open t e l e g", "org.telegram.messenger"),
            ("open g r a m", "org.telegram.messenger"),
            ("open c h a t", "com.openai.chatgpt"),
            ("open c h a t g", "com.openai.chatgpt"),
        ]

        patches = _mock_validation_catalog(result)
        with patches[0], patches[1], patches[2]:
            for text, package_name in examples:
                with self.subTest(text=text):
                    response = predict_command(
                        text=text,
                        language="EN",
                        session_id=session_id,
                        catalog_version=result["catalog_version"],
                    )

                    self.assertTrue(response["accepted"])
                    self.assertEqual(response["intent"], "OPEN_APP")
                    self.assertEqual(response["parameters"]["app_package_name"], package_name)

    def test_spelled_partial_app_name_is_ambiguous_when_multiple_apps_match(self):
        session_id = "unit-test-ambiguous-partial-spelled-app"
        result = _catalog([
            {
                "label": "Telegram",
                "package_name": "org.telegram.messenger",
                "aliases": [],
            },
            {
                "label": "Instagram",
                "package_name": "com.instagram.android",
                "aliases": [],
            },
        ])

        with _mock_catalog(result):
            resolution = resolve_app_match(session_id, "g r a m")

        self.assertIsNone(resolution.match)
        self.assertEqual(
            {match.package_name for match in resolution.ambiguous_matches},
            {"org.telegram.messenger", "com.instagram.android"},
        )


if __name__ == "__main__":
    unittest.main()
