import unittest

from V3.patterns.command_patterns import GO_BACK_PATTERNS
from V3.patterns.commands.navigation import GO_BACK_PATTERNS as NAVIGATION_GO_BACK_PATTERNS


class PatternFacadeTests(unittest.TestCase):
    def test_command_patterns_facade_exports_split_patterns(self):
        self.assertIs(GO_BACK_PATTERNS, NAVIGATION_GO_BACK_PATTERNS)
        self.assertIn("geri git", GO_BACK_PATTERNS)


if __name__ == "__main__":
    unittest.main()
