import re

HALF_WORDS = {"half", "yarim", "bucuk", "نصف", "نص"}
CONNECTOR_WORDS = {"and", "ve", "و", "a", "an"}
WORD_TOKEN_PATTERN = re.compile(r"[a-zA-ZğüşöçıİĞÜŞÖÇأ-ي]+")