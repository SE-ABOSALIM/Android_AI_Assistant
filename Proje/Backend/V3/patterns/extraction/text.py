SEARCH_QUERY_PATTERNS = {
    "EN": [
        r"^(?:search for|look up)\s+(.+)$",
    ],
    "TR": [
        r"^(.+?)\s+(?:icin\s+)?ara$",
    ],
    "AR": [
        r"^ابحث عن\s+(.+)$",
        r"^ابحث\s+عن\s+(.+)$",
    ],
}

WRITE_TEXT_PATTERNS = {
    "EN": [
        r"^(?:write|type)\s+(.+)$",
    ],
    "TR": [
        r"^(.+?)\s+(?:metnini\s+)?yaz$",
    ],
    "AR": [
        r"^اكتب\s+(.+)$",
        r"^اكتب النص\s+(.+)$",
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
