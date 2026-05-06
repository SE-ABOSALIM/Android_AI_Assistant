OPEN_APP_PATTERNS = [
    r"^open\s+(.+)$",
    r"^launch\s+(.+)$",
    r"^start\s+(.+)$",
    r"^(.+)\s+open$",
    r"^(.+)\s+ac$",
    r"^ac\s+(.+)$",
]

CALL_CONTACT_PATTERNS = [
    r"^call\s+(.+)$",
    r"^phone\s+(.+)$",
    r"^ring\s+(.+)$",
    r"^(.+)\s+call$",
    r"^(.+)\s+ara$",
]

REJECT_APP_NAMES = {"app", "application", "uygulama"}
ARABIC_CALL_PATTERNS = [
    r"^\u0627\u062a\u0635\u0644\s+\u0628?(.+)$",
]
ARABIC_OPEN_PATTERN = r"^افتح\s+(.+)$"
APP_SUFFIX_SEPARATORS = ("'", "’", "‘", "`", "´")

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
    "timer_keywords": ["مؤقت", "موقت", "موقتا", "مؤقتا", "منبه", "منبها", "عداد", "عدادا", "عد تنازلي", "توقيت"],
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