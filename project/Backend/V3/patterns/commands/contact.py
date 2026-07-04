CALL_CONTACT_PATTERNS = {
    "EN": [
        r"^call\s+(.+)$",
        r"^phone\s+(.+)$",
        r"^ring\s+(.+)$",
        r"^(.+)\s+call$",
    ],
    "TR": [
        r"^(.+)\s+ara$",
        r"^(.+)\s+telefon et$",
    ],
    "AR": [
        r"^(?:اتصل|إتصل|اتصلي)\s+ب?(.+)$",
        r"^كلم\s+(.+)$",
    ],
}
