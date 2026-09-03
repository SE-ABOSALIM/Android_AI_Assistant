FOCUS_TARGET_PATTERNS = {
    "EN": [
        r"^focus(?:\s+on)?\s+(?:the\s+)?(?:input|field)\s+(.+)$",
        r"^focus(?:\s+on)?\s+(?:the\s+)?(.+?)\s+(?:input|field)$",
        r"^focus(?:\s+on)?\s+(?:the\s+)?(.+)$",
    ],
    "TR": [
        r"^(.+?)\s+(?:inputuna|inputine|inputa|inpute|alanina|alana|kutusuna)\s+odaklan$",
        r"^(?:input|alan|kutu)\s+(.+?)\s+(?:alanina|inputuna|inputine)?\s*odaklan$",
    ],
    "AR": [
        r"^\u0631\u0643\u0632\s+\u0639\u0644\u0649\s+(?:\u062d\u0642\u0644|\u062e\u0627\u0646\u0629)\s+(.+)$",
        r"^\u0631\u0643\u0632\s+\u0639\u0644\u0649\s+(.+?)\s+(?:\u062d\u0642\u0644|\u062e\u0627\u0646\u0629)$",
    ],
}


GENERIC_FOCUS_TARGETS = {
    "EN": {
        "a",
        "on",
        "text",
        "input",
        "inputs",
        "field",
        "fields",
        "a field",
        "text field",
        "input field",
        "the input",
        "the field",
        "the text field",
    },
    "TR": {
        "input",
        "metin",
        "yazi",
        "input alani",
        "alan",
        "metin alani",
        "yazi alani",
        "yazi kutusu",
    },
    "AR": {
        "\u0627\u0644\u0646\u0635",
        "\u0627\u0644\u062d\u0642\u0644",
        "\u062d\u0642\u0644",
        "\u062d\u0642\u0644 \u0627\u0644\u0646\u0635",
        "\u062e\u0627\u0646\u0629 \u0627\u0644\u0646\u0635",
    },
}
