import unicodedata


def normalize_text(text: str) -> str:
    return " ".join(str(text).strip().split())


def remove_accents(text: str) -> str:
    """
    Turkish accent-safe simple normalization.
    aşağı -> asagi
    yukarı -> yukari
    sağa -> saga
    sola -> sola
    """
    text = str(text)
    text = text.replace("ı", "i").replace("İ", "I")
    normalized = unicodedata.normalize("NFKD", text)
    return "".join(ch for ch in normalized if not unicodedata.combining(ch))


def normalized_lower(text: str) -> str:
    text = normalize_text(text).lower()
    text = remove_accents(text)
    return text
