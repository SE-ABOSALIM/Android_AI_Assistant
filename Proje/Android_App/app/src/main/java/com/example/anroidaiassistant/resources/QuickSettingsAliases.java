package com.example.anroidaiassistant.resources;

public final class QuickSettingsAliases {
    private QuickSettingsAliases() {}

    public static final String[] STATE_OFF_TEXTS = {
            " off ",
            " kapali ",
            " kapalı ",
            " disabled ",
            " غير مفعل ",
            " ايقاف "
    };

    public static final String[] STATE_ON_TEXTS = {
            " on ",
            " acik ",
            " açık ",
            " enabled ",
            " مفعل ",
            " تشغيل "
    };

    public static final String[] BLUETOOTH_OFF_TEXTS = {
            " bluetooth off ",
            " bluetooth disabled ",
            " bluetooth kapali ",
            " bluetooth kapalı "
    };

    public static final String[] BLUETOOTH_ON_TEXTS = {
            " bluetooth on ",
            " bluetooth enabled ",
            " bluetooth acik ",
            " bluetooth açık "
    };

    public static final String[] BLUETOOTH_ENABLE_ACTION_TEXTS = {
            " turn on bluetooth ",
            " enable bluetooth ",
            " bluetooth u ac ",
            " bluetooth ac "
    };

    public static final String[] BLUETOOTH_DISABLE_ACTION_TEXTS = {
            " turn off bluetooth ",
            " disable bluetooth ",
            " bluetooth u kapat ",
            " bluetooth kapat "
    };

    public static final TileSpec[] TILE_SPECS = {
            new TileSpec("SET_WIFI", "Wi-Fi", new String[]{
                    "Wi-Fi",
                    "Wifi",
                    "Kablosuz",
                    "واي فاي",
                    "وايفاى"
            }, false),
            new TileSpec("SET_BLUETOOTH", "Bluetooth", new String[]{
                    "Bluetooth",
                    "بلوتوث"
            }, true),
            new TileSpec("SET_LOCATION", "Location", new String[]{
                    "Location",
                    "Konum",
                    "موقع",
                    "الموقع"
            }, false),
            new TileSpec("SET_MOBILE_DATA", "Mobile data", new String[]{
                    "Mobile data",
                    "Cellular data",
                    "Mobil veri",
                    "Hucresel veri",
                    "Hücresel veri",
                    "بيانات الهاتف",
                    "بيانات الجوال",
                    "بيانات المحمول"
            }, false),
            new TileSpec("SET_MOBILE_HOTSPOT", "Hotspot", new String[]{
                    "Hotspot",
                    "Mobile hotspot",
                    "Personal hotspot",
                    "Tethering",
                    "Mobil hotspot",
                    "Kisisel erisim noktasi",
                    "Kişisel erişim noktası",
                    "Erisim noktasi",
                    "نقطة الاتصال",
                    "نقطه الاتصال",
                    "هوتسبوت"
            }, false)
    };

    public static final class TileSpec {
        public final String intent;
        public final String displayName;
        public final String[] labels;
        public final boolean useBluetoothToggleFallback;

        private TileSpec(
                String intent,
                String displayName,
                String[] labels,
                boolean useBluetoothToggleFallback
        ) {
            this.intent = intent;
            this.displayName = displayName;
            this.labels = labels;
            this.useBluetoothToggleFallback = useBluetoothToggleFallback;
        }
    }
}
