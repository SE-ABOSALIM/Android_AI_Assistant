CLICK_TARGET_PATTERNS = {
    "ALL": [
        r"^(?:\u0627\u0636\u063a\u0637|\u0627\u0646\u0642\u0631)\s+(?:\u0639\u0644\u0649\s+)?(.+)$",
    ],
    "EN": [
        r"^(?:tap|click|press)\s+(?:the\s+)?(.+)$",
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
