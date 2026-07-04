import unittest

from V3.app_catalog.indexer import _build_catalog_search_index
from V3.app_catalog.models import AppCatalogEntryRecord


class AppCatalogIndexerTests(unittest.TestCase):
    def test_single_word_alias_skips_compact_and_token_indexes(self):
        index = _build_catalog_search_index([
            AppCatalogEntryRecord(
                label="Telegram",
                package_name="org.telegram.messenger",
                aliases=[],
                match_aliases=["telegram", "telegram messenger"],
            )
        ])

        self.assertIn("telegram", index["exact"])
        self.assertIn("telegram messenger", index["exact"])
        self.assertNotIn("telegram", index["compact"])
        self.assertNotIn("telegram", index["token"])
        self.assertNotIn("messenger", index["token"])
        self.assertIn("tel", index["ngram"])

    def test_compound_alias_uses_compact_and_token_indexes(self):
        index = _build_catalog_search_index([
            AppCatalogEntryRecord(
                label="Cap Heroes",
                package_name="com.example.capheroes",
                aliases=[],
                match_aliases=["cap heroes"],
            )
        ])

        self.assertIn("cap heroes", index["exact"])
        self.assertIn("capheroes", index["compact"])
        self.assertIn("cap", index["token"])
        self.assertIn("heroes", index["token"])


if __name__ == "__main__":
    unittest.main()
