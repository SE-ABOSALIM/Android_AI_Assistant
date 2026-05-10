OPEN_APP_PATTERNS = [
    r"^open\s+(.+)$",
    r"^launch\s+(.+)$",
    r"^start\s+(.+)$",
    r"^(.+)\s+open$",
    r"^(.+)\s+ac$",
    r"^ac\s+(.+)$",
]

REJECT_APP_NAMES = {"app", "application", "uygulama"}
ARABIC_OPEN_PATTERN = r"^Ø§ÙØªØ­\s+(.+)$"
APP_SUFFIX_SEPARATORS = ("'", "â€™", "â€˜", "`", "Â´")
