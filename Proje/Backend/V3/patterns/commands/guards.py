NEGATION_PATTERNS = {
    "EN": [
        r"\bdo not\b",
        r"\bdon't\b",
        r"\bdont\b",
        r"\bnot\b",
        r"\bnever\b",
        r"\bno need to\b",
    ],
    "TR": [
        r"\byapma\b",
        r"\betme\b",
        r"\bkaydirma\b",
        r"\bacma\b",
        r"\bgitme\b",
        r"\bbaslatma\b",
        r"\bkurma\b",
    ],
    "AR": [
        r"(^|\s)لا\s+تفعل($|\s)",
        r"(^|\s)لا\s+تفتح($|\s)",
        r"(^|\s)لا\s+تشغل($|\s)",
        r"(^|\s)لا\s+تغلق($|\s)",
        r"(^|\s)لا\s+تمسح($|\s)",
        r"(^|\s)لا\s+ترفع($|\s)",
        r"(^|\s)لا\s+تخفض($|\s)",
    ],
}

DEFERRED_PATTERNS = {
    "EN": [
        r"\blater\b",
        r"\bnot now\b",
    ],
    "TR": [
        r"\bsonra\b",
        r"\bdaha sonra\b",
    ],
    "AR": [
        r"(^|\s)لاحقا($|\s)",
        r"(^|\s)ليس الان($|\s)",
        r"(^|\s)بعد قليل($|\s)",
        r"(^|\s)فيما بعد($|\s)",
    ],
}

AMBIGUOUS_PATTERNS = {
    "EN": [
        r"\bup\s+or\s+down\b",
        r"\bdown\s+or\s+up\b",
        r"\bleft\s+or\s+right\b",
        r"\bright\s+or\s+left\b",
        r"\bincrease\s+or\s+decrease\b",
        r"\bdecrease\s+or\s+increase\b",
    ],
    "TR": [
        r"\byukari\s+veya\s+asagi\b",
        r"\basagi\s+veya\s+yukari\b",
        r"\bsaga\s+veya\s+sola\b",
        r"\bsola\s+veya\s+saga\b",
    ],
    "AR": [
        r"(^|\s)فوق\s+او\s+تحت($|\s)",
        r"(^|\s)تحت\s+او\s+فوق($|\s)",
        r"(^|\s)يمين\s+او\s+يسار($|\s)",
        r"(^|\s)يسار\s+او\s+يمين($|\s)",
        r"(^|\s)ارفع\s+او\s+اخفض($|\s)",
        r"(^|\s)اخفض\s+او\s+ارفع($|\s)",
    ],
}
