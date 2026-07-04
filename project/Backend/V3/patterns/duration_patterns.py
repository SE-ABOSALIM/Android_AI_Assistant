import re

UNIT_MAP = {
    # EN
    "second": "second", "seconds": "second", "sec": "second", "secs": "second",
    "minute": "minute", "minutes": "minute", "min": "minute", "mins": "minute",
    "hour": "hour", "hours": "hour",

    # TR
    "sn": "second", "saniye": "second", "saniyelik": "second", "dk": "minute",
    "dakika": "minute", "dakikalik": "minute", "saat": "hour", "saatlik": "hour",

    # AR
    "ثانية": "second", "ثانيه": "second", "ثانيتين": "second", "ثواني": "second", "ثوان": "second",
    "دقيقة": "minute", "دقيقه": "minute", "دقيقتين": "minute", "دقائق": "minute", "دقايق": "minute",
    "ساعة": "hour", "ساعه": "hour", "ساعتين": "hour", "ساعات": "hour",
}

IMPLICIT_UNIT_VALUES = {
    "ثانية": 1, "ثانيه": 1, "ثانيتين": 2, "دقيقة": 1, "دقيقه": 1,
    "دقيقتين": 2, "ساعة": 1, "ساعه": 1, "ساعتين": 2,
}

SECONDS_MULTIPLIER = {
    "second": 1,
    "minute": 60,
    "hour": 3600,
}

NUMBER_WORDS = {
    # EN
    "one": 1, "two": 2, "three": 3, "four": 4, "five": 5,
    "six": 6, "seven": 7, "eight": 8, "nine": 9, "ten": 10,
    "eleven": 11, "twelve": 12, "thirteen": 13, "fourteen": 14,
    "fifteen": 15, "sixteen": 16, "seventeen": 17, "eighteen": 18,
    "nineteen": 19,

    # TR - normalized_lower removes Turkish accents.
    "bir": 1, "iki": 2, "uc": 3, "dort": 4, "bes": 5,
    "alti": 6, "yedi": 7, "sekiz": 8, "dokuz": 9, "on": 10,

    # AR - normalized_lower removes Arabic combining marks.
    "واحد": 1, "واحدة": 1, "احد": 1, "احدى": 1, "اثنان": 2, "اثنين": 2,
    "اثنتان": 2, "اثنتين": 2, "ثلاث": 3, "ثلاثة": 3, "اربع": 4, "اربعة": 4,
    "خمس": 5, "خمسة": 5, "ست": 6, "ستة": 6, "سبع": 7, "سبعة": 7, "ثمان": 8,
    "ثمانية": 8, "ثمانيه": 8, "تسع": 9, "تسعة": 9, "عشر": 10, "عشرة": 10,
}

TENS_WORDS = {
    # EN
    "twenty": 20, "thirty": 30, "forty": 40, "fifty": 50,
    "sixty": 60, "seventy": 70, "eighty": 80, "ninety": 90,

    # TR
    "yirmi": 20, "otuz": 30, "kirk": 40, "elli": 50,
    "altmis": 60, "yetmis": 70, "seksen": 80, "doksan": 90,
    # AR
    "عشرون": 20, "ثلاثون": 30, "اربعون": 40, "خمسون": 50,
    "ستون": 60, "سبعون": 70, "ثمانون": 80, "تسعون": 90,
}

SCALE_WORDS = {
    "hundred": 100, "yuz": 100, "مئة": 100,
    "مائه": 100, "مية": 100, "مايه": 100,
}

DIGIT_DURATION_PATTERN = re.compile(
    r"(?<![\d.,])(\d+(?:[.,]\d+)?)[\s-]*([a-zA-ZğüşöçıİĞÜŞÖÇأ-ي]+)"
)