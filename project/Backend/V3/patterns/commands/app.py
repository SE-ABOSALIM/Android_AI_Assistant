OPEN_APP_PATTERNS = {
    "EN": [
        r"^open\s+(.+)$",
        r"^launch\s+(.+)$",
        r"^start\s+(.+)$",
        r"^(.+)\s+open$",
    ],
    "TR": [
        r"^(.+)\s+ac$",
        r"^ac\s+(.+)$",
        r"^(.+)\s+gir$",
        r"^gir\s+(.+)$",
        r"^(.+)\s+uygulamasini ac$",
    ],
    "AR": [
        r"^(?:افتح|إفتح|أفتح|افتحي|شغل|شغلي|ادخل|ادخلي)\s+(.+)$",
        r"^(.+)\s+(?:افتح|إفتح|أفتح|شغل|ادخل)$",
    ],
}

REJECT_APP_NAMES = {
    "app",
    "application",
    "uygulama",
    "التطبيق",
}

OPEN_APP_REJECT_PATTERNS = {
    "EN": [
        r"^app info for\b",
        r"\bapp settings$",
        r"\bapp info$",
        r"^(?:wi-?fi|bluetooth|flashlight|location|mobile data|mobile hotspot|keyboard|typing panel|volume control|notifications?|alerts|recent apps|recent screens|app switcher|previous screen|calling|scrolling|swiping)$",
        r"^(?:front|rear)\s+camera\s+and\s+capture$",
    ],
    "TR": [
        r"\bhakkinda sayfasini$",
        r"\buygulama bilgilerini$",
        r"\buygulama detaylarini$",
        r"\b(?:bildirim|uyari|wi-fi|wifi|bluetooth|fener|feneri|el feneri|mobil veri|mobil veriyi|hucresel veri|mobil erisim noktasi|kisisel erisim noktasi|konum|gps|klavye|yazma paneli|ses|sesi|sesini|sessiz|titresim|ana sayfa|ev ekrani|kronometre|zamanlayici)\b",
    ],
    "AR": [
        r"^(?:اعدادات تطبيق|تفاصيل تطبيق|معلومات تطبيق)\b",
        r"(?:زر|شريط|نتيجة|الواي فاي|البلوتوث|الفلاش|المصباح|الموقع|بيانات الهاتف|نقطة الاتصال|لوحة المفاتيح|الصوت|صوت|الرنين|الاهتزاز|الاشعارات|التطبيقات الاخيرة|المهام المتعددة|الضغط المزدوج|مؤقت|موقت|التقط صورة|خذ صورة|الصفحة الاساسية|الصفحة الأساسية)",
    ],
}

APP_SUFFIX_SEPARATORS = (
    "'",
    "\u2019",
    "\u2018",
    "’",
    "‘",
    "`",
    "´",
)
