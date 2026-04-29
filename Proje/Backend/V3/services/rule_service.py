import re
from typing import Any, Dict, Iterable, Optional

from V3.services.extractors import extract_app_name, extract_timer
from V3.services.text_utils import normalize_text, normalized_lower


SCROLL_DOWN_PATTERNS = [
    "scroll down",
    "asagi kaydir",
    "asagiya kaydir",
    "alta kaydir",
    "sayfayi asagi kaydir",
    "ekrani asagi kaydir",
]

SCROLL_UP_PATTERNS = [
    "scroll up",
    "yukari kaydir",
    "yukariya kaydir",
    "uste kaydir",
    "sayfayi yukari kaydir",
    "ekrani yukari kaydir",
]

SWIPE_LEFT_PATTERNS = [
    "swipe left",
    "sola kaydir",
    "sol tarafa kaydir",
    "ekrani sola kaydir",
]

SWIPE_RIGHT_PATTERNS = [
    "swipe right",
    "saga kaydir",
    "sag tarafa kaydir",
    "ekrani saga kaydir",
]

VOLUME_PATTERNS = {
    "decrease": [
        "decrease volume",
        "decrease the volume",
        "decrease sound",
        "decrease the sound",
        "lower volume",
        "lower the volume",
        "lower sound",
        "lower the sound",
        "volume down",
        "sound down",
        "turn volume down",
        "turn the volume down",
        "turn sound down",
        "turn the sound down",
        "sesi azalt",
        "ses azalt",
        "sesi kis",
        "ses kis",
        "ses seviyesini azalt",
        "ses seviyesini dusur",
        "sesi dusur",
    ],
    "increase": [
        "increase volume",
        "increase the volume",
        "increase sound",
        "increase the sound",
        "raise volume",
        "raise the volume",
        "raise sound",
        "raise the sound",
        "volume up",
        "sound up",
        "turn volume up",
        "turn the volume up",
        "turn sound up",
        "turn the sound up",
        "sesi artir",
        "ses artir",
        "sesi arttir",
        "ses arttir",
        "sesi yukselt",
        "ses yukselt",
        "ses seviyesini artir",
        "ses seviyesini yukselt",
        "sesi ac",
    ],
    "mute": [
        "mute sound",
        "mute the sound",
        "mute audio",
        "turn off sound",
        "turn off the sound",
        "silence phone",
        "silence the phone",
        "sesi kapat",
        "sesi sustur",
        "sessize al",
        "telefonu sessize al",
    ],
    "unmute": [
        "unmute sound",
        "unmute the sound",
        "unmute audio",
        "turn sound back on",
        "turn the sound back on",
        "enable sound",
        "sessizi kaldir",
        "sesi geri ac",
        "telefonun sesini geri ac",
    ],
}

GO_HOME_PATTERNS = [
    "go home",
    "home screen",
    "go to home",
    "go to home screen",
    "ana ekrana git",
    "ana ekran",
    "eve don",
    "anasayfaya git",
]

TAKE_PHOTO_PATTERNS = [
    "take photo",
    "take a photo",
    "take picture",
    "take a picture",
    "open camera",
    "camera open",
    "kamera ac",
    "kamerayi ac",
    "foto cek",
    "fotograf cek",
]

TIMER_KEYWORDS = [
    "timer",
    "countdown",
    "count down",
    "timing",
    "zamanlayici",
    "sayac",
    "geri say",
    "sure tut",
    "zaman tut",
    "kronometre",
]

TIMER_ACTION_PATTERNS = [
    "set timer",
    "set a timer",
    "start timer",
    "start a timer",
    "start timing",
    "run a timer",
    "make a timer",
    "create a timer",
    "begin countdown",
    "start countdown",
    "count down",
    "zamanlayici kur",
    "zamanlayici ayarla",
    "zamanlayici olustur",
    "sayac kur",
    "sayac baslat",
    "geri sayim baslat",
    "geri say",
    "sure tut",
    "zaman tut",
    "kronometre ac",
]

ARABIC_PATTERNS = {
    "scroll_down": ["مرر للأسفل", "مرر للاسفل", "انزل للأسفل", "انزل للاسفل"],
    "scroll_up": ["مرر للأعلى", "مرر للاعلى", "اصعد للأعلى", "اصعد للاعلى"],
    "swipe_left": ["اسحب لليسار", "اسحب الى اليسار", "اسحب إلى اليسار"],
    "swipe_right": ["اسحب لليمين", "اسحب الى اليمين", "اسحب إلى اليمين"],
    "volume_decrease": ["اخفض الصوت", "خفض الصوت", "قلل الصوت"],
    "volume_increase": ["ارفع الصوت", "رفع الصوت", "زيد الصوت"],
    "volume_mute": ["اكتم الصوت", "اغلق الصوت", "اجعل الهاتف صامت", "حول الهاتف إلى صامت"],
    "volume_unmute": ["افتح الصوت", "الغ كتم الصوت", "ارجع الصوت", "شغل الصوت مرة أخرى"],
    "go_home": ["اذهب للشاشة الرئيسية", "اذهب الى الشاشة الرئيسية", "الشاشة الرئيسية"],
    "take_photo": ["التقط صورة", "خذ صورة", "افتح الكاميرا"],
    "timer_keywords": ["مؤقت", "موقت", "موقتا", "مؤقتا", "عداد", "عدادا", "عد تنازلي", "توقيت"],
    "timer_actions": ["اضبط", "ابدأ", "ابدا", "شغل", "انشئ", "أنشئ"],
    "negation": ["لا ", "لا ت", "لات"],
}

NEGATION_PATTERNS = [
    r"\bdo not\b",
    r"\bdon't\b",
    r"\bdont\b",
    r"\bnot\b",
    r"\bnever\b",
    r"\bno need to\b",
    r"\byapma\b",
    r"\betme\b",
    r"\bkaydirma\b",
    r"\bacma\b",
    r"\bgitme\b",
    r"\bbaslatma\b",
    r"\bkurma\b",
]

DEFERRED_PATTERNS = [
    r"\blater\b",
    r"\bnot now\b",
    r"\bsonra\b",
    r"\bdaha sonra\b",
]

AMBIGUOUS_PATTERNS = [
    r"\bup\s+or\s+down\b",
    r"\bdown\s+or\s+up\b",
    r"\bleft\s+or\s+right\b",
    r"\bright\s+or\s+left\b",
    r"\bincrease\s+or\s+decrease\b",
    r"\bdecrease\s+or\s+increase\b",
    r"\byukari\s+veya\s+asagi\b",
    r"\basagi\s+veya\s+yukari\b",
    r"\bsaga\s+veya\s+sola\b",
    r"\bsola\s+veya\s+saga\b",
]


def rule_based_command(text: str, language: str) -> Optional[Dict[str, Any]]:
    """
    Net ve deterministic komutları modelden önce yakalar.
    Belirsiz veya olumsuz ifadeleri rule ile çalıştırmaz.
    """

    original = normalize_text(text)
    t = normalized_lower(original)

    if _has_negation(t, original):
        return _unknown("negated_command")

    if _matches_regex(t, DEFERRED_PATTERNS):
        return _unknown("deferred_command")

    if _matches_regex(t, AMBIGUOUS_PATTERNS):
        return _unknown("ambiguous_command")

    if _matches_any(t, SCROLL_DOWN_PATTERNS) or _matches_any(original, ARABIC_PATTERNS["scroll_down"]):
        return _command("SCROLL_SCREEN", "scroll_down", {"direction": "down"})

    if _matches_any(t, SCROLL_UP_PATTERNS) or _matches_any(original, ARABIC_PATTERNS["scroll_up"]):
        return _command("SCROLL_SCREEN", "scroll_up", {"direction": "up"})

    if _matches_any(t, SWIPE_LEFT_PATTERNS) or _matches_any(original, ARABIC_PATTERNS["swipe_left"]):
        return _command("SWIPE_GESTURE", "swipe_left", {"direction": "left"})

    if _matches_any(t, SWIPE_RIGHT_PATTERNS) or _matches_any(original, ARABIC_PATTERNS["swipe_right"]):
        return _command("SWIPE_GESTURE", "swipe_right", {"direction": "right"})

    volume_action = _extract_volume_action(t, original)
    if volume_action == "ambiguous":
        return _unknown("ambiguous_volume")
    if volume_action:
        return _command("ADJUST_VOLUME", f"volume_{volume_action}", {"volume_action": volume_action})

    if _matches_any(t, GO_HOME_PATTERNS) or _matches_any(original, ARABIC_PATTERNS["go_home"]):
        return _command("GO_HOME", "go_home")

    if _matches_any(t, TAKE_PHOTO_PATTERNS) or _matches_any(original, ARABIC_PATTERNS["take_photo"]):
        return _command("TAKE_PHOTO", "take_photo")

    timer_params = extract_timer(original)
    has_timer_keyword = _matches_any(t, TIMER_KEYWORDS) or _matches_any(original, ARABIC_PATTERNS["timer_keywords"])
    has_timer_action = _matches_any(t, TIMER_ACTION_PATTERNS) or (
        _matches_any(original, ARABIC_PATTERNS["timer_actions"])
        and _matches_any(original, ARABIC_PATTERNS["timer_keywords"])
    )

    if has_timer_keyword and (timer_params or has_timer_action):
        return _command("SET_TIMER", "set_timer", timer_params)

    app_name = extract_app_name(original, language)
    if app_name:
        return _command("OPEN_APP", "open_app")

    return None


def _extract_volume_action(text: str, original: str) -> Optional[str]:
    matched_actions = {
        action
        for action, patterns in VOLUME_PATTERNS.items()
        if _matches_any(text, patterns)
    }

    if _matches_any(original, ARABIC_PATTERNS["volume_decrease"]):
        matched_actions.add("decrease")
    if _matches_any(original, ARABIC_PATTERNS["volume_increase"]):
        matched_actions.add("increase")
    if _matches_any(original, ARABIC_PATTERNS["volume_mute"]):
        matched_actions.add("mute")
    if _matches_any(original, ARABIC_PATTERNS["volume_unmute"]):
        matched_actions.add("unmute")

    if len(matched_actions) > 1:
        return "ambiguous"

    return next(iter(matched_actions), None)


def _command(intent: str, rule_matched: str, parameters: Optional[Dict[str, Any]] = None) -> Dict[str, Any]:
    return {
        "intent": intent,
        "parameters": parameters or {},
        "rule_matched": rule_matched,
    }


def _unknown(rule_matched: str) -> Dict[str, Any]:
    return _command("UNKNOWN_COMMAND", rule_matched)


def _has_negation(text: str, original: str) -> bool:
    return _matches_regex(text, NEGATION_PATTERNS) or any(pattern in original for pattern in ARABIC_PATTERNS["negation"])


def _matches_any(text: str, patterns: Iterable[str]) -> bool:
    return any(_contains_phrase(text, pattern) for pattern in patterns)


def _matches_regex(text: str, patterns: Iterable[str]) -> bool:
    return any(re.search(pattern, text) for pattern in patterns)


def _contains_phrase(text: str, phrase: str) -> bool:
    pattern = rf"(?<!\w){re.escape(phrase)}(?!\w)"
    return re.search(pattern, text, flags=re.IGNORECASE) is not None
