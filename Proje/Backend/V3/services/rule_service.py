import re
from typing import Any, Dict, Optional

from V3.services.text_utils import normalize_text, normalized_lower


def rule_based_command(text: str, language: str) -> Optional[Dict[str, Any]]:
    """
    Net ve deterministic komutları modelden önce yakalar.

    Amaç:
    - scroll up/down
    - swipe left/right
    - volume increase/decrease
    - go home
    - open app

    Bunlar açıkça yazıyorsa modelin düşük confidence saçmalamasını beklemiyoruz.
    """

    original = normalize_text(text)
    t = normalized_lower(original)

    # -------------------------
    # SCROLL DOWN
    # -------------------------
    scroll_down_patterns = [
        "scroll down",
        "asagi kaydir",
        "asagiya kaydir",
        "alta kaydir",
        "sayfayi asagi kaydir",
        "ekrani asagi kaydir",
    ]

    arabic_scroll_down = [
        "مرر للأسفل",
        "مرر للاسفل",
        "انزل للأسفل",
        "انزل للاسفل",
    ]

    if any(p in t for p in scroll_down_patterns) or any(p in original for p in arabic_scroll_down):
        return {
            "intent": "SCROLL_SCREEN",
            "parameters": {"direction": "down"},
            "rule_matched": "scroll_down",
        }

    # -------------------------
    # SCROLL UP
    # -------------------------
    scroll_up_patterns = [
        "scroll up",
        "yukari kaydir",
        "yukariya kaydir",
        "uste kaydir",
        "sayfayi yukari kaydir",
        "ekrani yukari kaydir",
    ]

    arabic_scroll_up = [
        "مرر للأعلى",
        "مرر للاعلى",
        "اصعد للأعلى",
        "اصعد للاعلى",
    ]

    if any(p in t for p in scroll_up_patterns) or any(p in original for p in arabic_scroll_up):
        return {
            "intent": "SCROLL_SCREEN",
            "parameters": {"direction": "up"},
            "rule_matched": "scroll_up",
        }

    # -------------------------
    # SWIPE LEFT
    # -------------------------
    swipe_left_patterns = [
        "swipe left",
        "sola kaydir",
        "sol tarafa kaydir",
        "ekrani sola kaydir",
    ]

    arabic_swipe_left = [
        "اسحب لليسار",
        "اسحب الى اليسار",
        "اسحب إلى اليسار",
    ]

    if any(p in t for p in swipe_left_patterns) or any(p in original for p in arabic_swipe_left):
        return {
            "intent": "SWIPE_GESTURE",
            "parameters": {"direction": "left"},
            "rule_matched": "swipe_left",
        }

    # -------------------------
    # SWIPE RIGHT
    # -------------------------
    swipe_right_patterns = [
        "swipe right",
        "saga kaydir",
        "sag tarafa kaydir",
        "ekrani saga kaydir",
    ]

    arabic_swipe_right = [
        "اسحب لليمين",
        "اسحب الى اليمين",
        "اسحب إلى اليمين",
    ]

    if any(p in t for p in swipe_right_patterns) or any(p in original for p in arabic_swipe_right):
        return {
            "intent": "SWIPE_GESTURE",
            "parameters": {"direction": "right"},
            "rule_matched": "swipe_right",
        }

    # -------------------------
    # VOLUME DECREASE
    # -------------------------
    volume_decrease_patterns = [
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
    ]

    arabic_volume_decrease = [
        "اخفض الصوت",
        "خفض الصوت",
        "قلل الصوت",
    ]

    if any(p in t for p in volume_decrease_patterns) or any(p in original for p in arabic_volume_decrease):
        return {
            "intent": "ADJUST_VOLUME",
            "parameters": {"volume_action": "decrease"},
            "rule_matched": "volume_decrease",
        }

    # -------------------------
    # VOLUME INCREASE
    # -------------------------
    volume_increase_patterns = [
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
    ]

    arabic_volume_increase = [
        "ارفع الصوت",
        "رفع الصوت",
        "زيد الصوت",
    ]

    if any(p in t for p in volume_increase_patterns) or any(p in original for p in arabic_volume_increase):
        return {
            "intent": "ADJUST_VOLUME",
            "parameters": {"volume_action": "increase"},
            "rule_matched": "volume_increase",
        }

    # -------------------------
    # GO HOME
    # -------------------------
    go_home_patterns = [
        "go home",
        "home screen",
        "go to home",
        "go to home screen",
        "ana ekrana git",
        "ana ekran",
        "eve don",
        "anasayfaya git",
    ]

    arabic_go_home = [
        "اذهب للشاشة الرئيسية",
        "اذهب الى الشاشة الرئيسية",
        "الشاشة الرئيسية",
    ]

    if any(p == t or p in t for p in go_home_patterns) or any(p in original for p in arabic_go_home):
        return {
            "intent": "GO_HOME",
            "parameters": {},
            "rule_matched": "go_home",
        }

    # -------------------------
    # OPEN APP
    # -------------------------
    open_patterns = [
        r"^open\s+(.+)$",
        r"^launch\s+(.+)$",
        r"^start\s+(.+)$",
        r"^(.+)\s+open$",
        r"^(.+)\s+ac$",
        r"^ac\s+(.+)$",
    ]

    for pattern in open_patterns:
        match = re.match(pattern, t, flags=re.IGNORECASE)
        if match:
            app_name = match.group(1).strip()
            if app_name and app_name not in ["app", "application", "uygulama"]:
                return {
                    "intent": "OPEN_APP",
                    "parameters": {},
                    "rule_matched": "open_app",
                }

    arabic_open_match = re.match(r"^افتح\s+(.+)$", original, flags=re.IGNORECASE)
    if arabic_open_match:
        return {
            "intent": "OPEN_APP",
            "parameters": {},
            "rule_matched": "open_app_ar",
        }

    return None
