import re
from typing import Iterable

from V3.utils.language import language_key
from V3.utils.text import normalized_lower


MODEL_FALLBACK_GUARDED_INTENTS = {
    "ADJUST_BRIGHTNESS",
    "ADJUST_VOLUME",
    "CLEAR_TEXT",
    "CLOSE_APP",
    "DOUBLE_TAP",
    "GO_BACK",
    "GO_HOME",
    "HOLD_SCREEN",
    "OPEN_NOTIFICATIONS",
    "SCROLL_SCREEN",
    "SET_ALARM",
    "SET_BLUETOOTH",
    "SET_FLASHLIGHT",
    "SET_KEYBOARD",
    "SET_LOCATION",
    "SET_MOBILE_DATA",
    "SET_MOBILE_HOTSPOT",
    "SET_SOUND_MODE",
    "SET_TIMER",
    "SET_WIFI",
    "SHOW_RECENTS",
    "SWIPE_GESTURE",
    "TAKE_PHOTO",
    "TAKE_SCREENSHOT",
}

COMMAND_SIGNAL_WORDS = {
    "EN": {
        "adjust", "alarm", "back", "brightness", "call", "capture", "clear",
        "click", "close", "data", "decrease", "delete", "double", "flashlight",
        "go", "home", "hotspot", "increase", "keyboard", "location", "mute",
        "notification", "notifications", "off", "on", "open", "photo", "picture",
        "press", "recent", "recents", "screenshot", "screen", "scroll", "set",
        "silent", "sound", "stop", "swipe", "take", "tap", "timer", "turn",
        "type", "unmute", "vibrate", "volume", "wifi", "wireless", "write",
    },
    "TR": {
        "alarm", "ana", "ara", "arka", "artir", "arttir", "asagi", "azalt",
        "bas", "bildirim", "birak", "bluetooth", "cek", "cift", "cik",
        "cikar", "dokun", "don", "ekran", "el", "ev", "fener", "fotograf",
        "gec", "geri", "git", "goruntu", "goruntusu", "home", "indir",
        "internet", "islemi", "kamera", "kapat", "kaydir", "klavye", "konum",
        "kur", "mobil", "mod", "onceki", "recents", "saga", "sallama",
        "screenshot", "ses", "sessiz", "sifirla", "sil", "sola", "son",
        "sustur", "sure", "sureli", "tikla", "tus", "uygulama",
        "uygulamalar", "wifi", "yaz", "yukari", "yukselt",
    },
    "AR": {
        "اتصل", "اكتب", "اخرج", "اخفض", "اذهب", "ارجع", "ارفع", "اسحب",
        "اضبط", "اضغط", "اطفئ", "اغلق", "افتح", "الغ", "امسح", "ايقاف",
        "بلوتوث", "بيانات", "تطبيق", "تطبيقات", "خلف", "رجوع", "رن",
        "ساعة", "سطوع", "شاشة", "شغل", "صامت", "صورة", "صور", "صوت",
        "فلاش", "لقطة", "مؤقت", "واي", "وايفاي",
    },
}


def should_reject_weak_model_command(
    text: str,
    language: str,
    intent: str,
    raw_label: str,
) -> bool:
    if _is_rule_prediction(raw_label):
        return False

    intent = str(intent or "").upper()
    if intent not in MODEL_FALLBACK_GUARDED_INTENTS:
        return False

    tokens = _tokens(text)
    if not tokens:
        return True

    return not _has_command_signal(tokens, language)


def _is_rule_prediction(raw_label: str) -> bool:
    return str(raw_label or "").startswith("RULE::")


def _has_command_signal(tokens: Iterable[str], language: str) -> bool:
    signal_words = COMMAND_SIGNAL_WORDS.get(language_key(language), COMMAND_SIGNAL_WORDS["EN"])
    return any(token in signal_words for token in tokens)


def _tokens(text: str) -> list[str]:
    return re.findall(r"\w+", normalized_lower(text))
