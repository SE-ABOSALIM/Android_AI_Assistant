package com.example.anroidaiassistant.util;

public final class CustomCommandControlParser {
    private CustomCommandControlParser() {}

    public static boolean isExplicitCancelCommand(String spokenText) {
        String ascii = TextNormalizer.normalizeAsciiText(spokenText);
        if (ascii.isEmpty()) {
            return false;
        }

        boolean hasCancelAction = containsAny(
                ascii,
                "cancel",
                "stop",
                "abort",
                "iptal",
                "durdur",
                "vazgec"
        );
        boolean hasCustomTarget = containsAny(
                ascii,
                "custom command",
                "command flow",
                "workflow",
                "ozel komut",
                "komut akisi",
                "komut akis"
        );
        if (hasCancelAction && hasCustomTarget) {
            return true;
        }

        String arabic = TextNormalizer.normalizeText(spokenText);
        return containsAny(
                arabic,
                "\u0627\u0644\u063a",
                "\u0627\u0644\u063a\u0627\u0621",
                "\u0627\u0648\u0642\u0641",
                "\u0648\u0642\u0641",
                "\u0627\u0628\u0637\u0644"
        ) && containsAny(
                arabic,
                "\u0627\u0645\u0631 \u0645\u062e\u0635\u0635",
                "\u0627\u0644\u0627\u0645\u0631 \u0627\u0644\u0645\u062e\u0635\u0635",
                "\u0627\u0645\u0631 \u062e\u0627\u0635",
                "\u0627\u0644\u0627\u0645\u0631 \u0627\u0644\u062e\u0627\u0635",
                "\u062a\u0633\u0644\u0633\u0644"
        );
    }

    public static boolean isCancelAction(String spokenText) {
        String ascii = TextNormalizer.normalizeAsciiText(spokenText);
        if (equalsAny(ascii, "cancel", "stop", "abort", "iptal", "durdur", "vazgec")) {
            return true;
        }

        String arabic = TextNormalizer.normalizeText(spokenText);
        return equalsAny(
                arabic,
                "\u0627\u0644\u063a",
                "\u0627\u0644\u063a\u0627\u0621",
                "\u0627\u0648\u0642\u0641",
                "\u0648\u0642\u0641",
                "\u0627\u0628\u0637\u0644"
        );
    }

    private static boolean containsAny(String text, String... parts) {
        if (text == null) {
            return false;
        }
        for (String part : parts) {
            if (part != null && !part.isEmpty() && text.contains(part)) {
                return true;
            }
        }
        return false;
    }

    private static boolean equalsAny(String text, String... values) {
        if (text == null) {
            return false;
        }
        for (String value : values) {
            if (text.equals(value)) {
                return true;
            }
        }
        return false;
    }
}
