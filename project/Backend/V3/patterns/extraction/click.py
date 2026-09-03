CLICK_TARGET_PATTERNS = {
    "ALL": [
        r"^(?:\u0627\u0636\u063a\u0637|\u0627\u0646\u0642\u0631)\s+(?:\u0639\u0644\u0649\s+)?(.+)$",
    ],
    "EN": [
        r"^(?:tap|top|click|press)\s+(?:on\s+)?(?:the\s+)?(.+)$",
    ],
    "TR": [
        r"^(.+?)\s+(?:tikla|tiklayin|bas|basin)$",
        r"^(?:tikla|tiklayin|bas|basin)\s+(.+)$",
    ],
    "AR": [
        r"^(?:\u0627\u0636\u063a\u0637|\u0627\u0646\u0642\u0631)\s+\u0639\u0644\u0649\s+(.+)$",
        r"^(?:\u0627\u0636\u063a\u0637|\u0627\u0646\u0642\u0631)\s+(.+)$",
    ],
}

TARGET_GESTURE_PATTERNS = {
    "HOLD_SCREEN": {
        "EN": [
            r"^(?:hold|long\s+press|press\s+and\s+hold)(?:\s+(?:on|down))?\s+(?:the\s+)?(.+)$",
        ],
        "TR": [
            r"^(?:uzun\s+bas|basili\s+tut)\s+(.+)$",
            r"^(.+?)\s+(?:uzun\s+bas|basili\s+tut)$",
        ],
        "AR": [
            r"^(?:\u0627\u0636\u063a\u0637\s+\u0645\u0637\u0648\u0644\u0627|\u0627\u0636\u063a\u0637\s+\u0628\u0627\u0633\u062a\u0645\u0631\u0627\u0631|\u0627\u0636\u063a\u0637\s+\u0645\u0639\s+\u0627\u0644\u0627\u0633\u062a\u0645\u0631\u0627\u0631|\u062b\u0628\u062a\s+\u0627\u0644\u0636\u063a\u0637)\s+(?:\u0639\u0644\u0649\s+)?(.+)$",
        ],
    },
    "DOUBLE_TAP": {
        "EN": [
            r"^(?:double\s+(?:tap|click|press)|tap\s+twice|click\s+twice|press\s+twice)(?:\s+on)?\s+(?:the\s+)?(.+)$",
        ],
        "TR": [
            r"^(?:cift\s+(?:tikla|dokun|bas)|iki\s+(?:kere|kez|defa)\s+(?:tikla|dokun|bas))\s+(.+)$",
            r"^(.+?)\s+(?:cift\s+(?:tikla|dokun|bas)|iki\s+(?:kere|kez|defa)\s+(?:tikla|dokun|bas))$",
        ],
        "AR": [
            r"^(?:\u0627\u0646\u0642\u0631\s+\u0645\u0631\u062a\u064a\u0646|\u0627\u0636\u063a\u0637\s+\u0645\u0631\u062a\u064a\u0646|\u062f\u0628\u0644\s+\u0643\u0644\u064a\u0643)\s+(?:\u0639\u0644\u0649\s+)?(.+)$",
            r"^\u0627\u0636\u063a\u0637\s+\u0639\u0644\u0649\s+(.+?)\s+\u0645\u0631\u062a\u064a\u0646$",
        ],
    },
}

GENERIC_GESTURE_TARGETS = {
    "EN": {"screen", "the screen"},
    "TR": {"ekran", "ekrani", "ekrana", "ekra"},
    "AR": {"\u0627\u0644\u0634\u0627\u0634\u0629", "\u0634\u0627\u0634\u0629"},
}

CLICK_TARGET_TRAILING_NOISE_PATTERNS = {
    "EN": (
        r"\s+on\s+the\s+(?:top|bottom|left|right)(?:\s+of\s+the\s+(?:page|screen))?$",
        r"\s+on\s+(?:top|bottom|left|right)$",
        r"\s+at\s+the\s+(?:top|bottom|left|right)(?:\s+of\s+the\s+(?:page|screen))?$",
    ),
}

CLICK_ORDINAL_ONLY_TARGET_PATTERNS = {
    "EN": (
        r"^(?:the\s+)?(?:first|second|third|fourth|fifth|sixth|seventh|eighth|ninth|tenth|one|two|three|four|five|six|seven|eight|nine|ten|[1-9]|10)(?:st|nd|rd|th)?(?:\s+(?:one|item|option|result|video|device|row))?$",
    ),
    "TR": (
        r"^(?:ilk|birinci|ikinci|ucuncu|dorduncu|besinci|altinci|yedinci|sekizinci|dokuzuncu|onuncu|[1-9]|10)(?:sine|sina|sune|suna|sini|sunu|ye|ya|e|a|ne|na)?(?:\s+(?:oge|secenek|sonuc|video|cihaz|satir))?$",
    ),
    "AR": (
        r"^(?:\u0627\u0644)?(?:\u0627\u0648\u0644|\u062b\u0627\u0646\u064a|\u062b\u0627\u0644\u062b|\u0631\u0627\u0628\u0639|\u062e\u0627\u0645\u0633|\u0633\u0627\u062f\u0633|\u0633\u0627\u0628\u0639|\u062b\u0627\u0645\u0646|\u062a\u0627\u0633\u0639|\u0639\u0627\u0634\u0631|\u0648\u0627\u062d\u062f|\u0627\u062b\u0646\u064a\u0646|\u062b\u0644\u0627\u062b\u0629|\u0627\u0631\u0628\u0639\u0629|\u062e\u0645\u0633\u0629|\u0633\u062a\u0629|\u0633\u0628\u0639\u0629|\u062b\u0645\u0627\u0646\u064a\u0629|\u062a\u0633\u0639\u0629|\u0639\u0634\u0631\u0629)(?:\s+(?:\u062e\u064a\u0627\u0631|\u0639\u0646\u0635\u0631|\u0646\u062a\u064a\u062c\u0647|\u0641\u064a\u062f\u064a\u0648|\u062c\u0647\u0627\u0632))?$",
    ),
}

CLICK_POSITION_ALIASES = {
    "top": (
        "top",
        "above",
        "upper",
        "ust",
        "ustteki",
        "yukari",
        "yukaridaki",
        "\u0641\u0648\u0642",
        "\u0627\u0644\u0627\u0639\u0644\u0649",
    ),
    "bottom": (
        "bottom",
        "below",
        "lower",
        "alt",
        "alttaki",
        "asagi",
        "asagidaki",
        "\u062a\u062d\u062a",
        "\u0627\u0644\u0627\u0633\u0641\u0644",
    ),
    "left": (
        "left",
        "soldaki",
        "sol",
        "\u064a\u0633\u0627\u0631",
        "\u0627\u0644\u064a\u0633\u0627\u0631",
    ),
    "right": (
        "right",
        "sagdaki",
        "sag",
        "\u064a\u0645\u064a\u0646",
        "\u0627\u0644\u064a\u0645\u064a\u0646",
    ),
    "center": (
        "center",
        "middle",
        "ortadaki",
        "orta",
        "\u0627\u0644\u0648\u0633\u0637",
    ),
}
