OPEN_APP_INFO_NAME_PATTERNS = {
    "EN": [
        r"^open app info for\s+(.+)$",
        r"^open\s+(.+?)\s+app settings$",
        r"^show\s+(.+?)\s+app info$",
    ],
    "TR": [
        r"^(.+?)\s+hakkinda sayfasini ac$",
        r"^(.+?)\s+uygulama bilgilerini ac$",
        r"^(.+?)\s+uygulama detaylarini goster$",
    ],
    "AR": [
        r"^اعرض تفاصيل تطبيق\s+(.+)$",
        r"^افتح اعدادات تطبيق\s+(.+)$",
        r"^معلومات تطبيق\s+(.+)$",
    ],
}

UNINSTALL_APP_NAME_PATTERNS = {
    "EN": [
        r"^(?:delete|remove|uninstall)\s+(.+)$",
    ],
    "TR": [
        r"^(.+?)\s+(?:sil|uygulamasini kaldir|uygulamasini sil)$",
    ],
    "AR": [
        r"^ازل\s+(.+)$",
        r"^الغاء تثبيت\s+(.+)$",
    ],
}
