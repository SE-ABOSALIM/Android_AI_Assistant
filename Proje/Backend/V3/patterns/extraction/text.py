SEARCH_QUERY_PATTERNS = {
    "EN": [
        r"^(?:search for|look up)\s+(.+)$",
    ],
    "TR": [
        r"^(.+?)\s+(?:icin\s+)?ara$",
    ],
    "AR": [
        r"^(?:\u0627\u0628\u062d\u062b|\u0628\u062d\u062b)\s+(?:\u0639\u0646\s+)?(.+)$",
        r"^\u0627\u0644\u0628\u062d\u062b\s+\u0639\u0646\s+(.+)$",
        r"^(?:\u062f\u0648\u0631|\u0627\u062f\u0648\u0631)\s+\u0639\u0644\u0649\s+(.+)$",
        r"^\u0641\u062a\u0634\s+(?:\u0639\u0646\s+)?(.+)$",
    ],
}

WRITE_TEXT_PATTERNS = {
    "EN": [
        r"^(?:write|type)\s+(.+)$",
    ],
    "TR": [
        r"^(?:sunu|\u015funu|bunu)\s+yaz\s+(.+)$",
        r"^yaz\s+(.+)$",
        r"^(.+?)\s+(?:metnini\s+)?yaz$",
    ],
    "AR": [
        r"^(?:\u0627\u0643\u062a\u0628|\u0627\u0643\u062a\u0628\u064a|\u0627\u0643\u062a\u0628\u0644\u064a)\s+(.+)$",
        r"^\u0627\u0643\u062a\u0628\s+\u0627\u0644\u0646\u0635\s+(.+)$",
    ],
}

CLICK_TARGET_PATTERNS = {
    "EN": [
        r"^(?:tap|click|press)\s+(.+)$",
    ],
    "TR": [
        r"^(.+?)\s+tikla$",
        r"^(.+?)\s+tiklayin$",
    ],
    "AR": [
        r"^اضغط على\s+(.+)$",
        r"^انقر على\s+(.+)$",
    ],
}
