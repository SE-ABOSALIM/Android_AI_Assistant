OPEN_APP_PATTERNS = {
    "EN": [
        r"^open\s+(.+)$",
        r"^launch\s+(.+)$",
        r"^start\s+(.+)$",
        r"^(.+)\s+open$",
    ],
    "TR": [
        r"^(.+)\s+ac$",
        r"^ac\s+(.+)$",
        r"^(.+)\s+uygulamasini ac$",
    ],
    "AR": [
        r"^(?:افتح|إفتح|افتحي|شغل|شغلي)\s+(.+)$",
        r"^(.+)\s+(?:افتح|إفتح|شغل)$",
    ],
}

REJECT_APP_NAMES = {
    "app",
    "application",
    "uygulama",
    "التطبيق",
}

APP_SUFFIX_SEPARATORS = (
    "'",
    "’",
    "‘",
    "`",
    "´",
)
