package com.example.anroidaiassistant.util;

import java.util.Locale;

public final class TextNormalizer {
    private TextNormalizer() {}

    public static String normalizeText(String text) {
        if (text == null) {
            return "";
        }

        String normalized = normalizeArabicLetters(text.toLowerCase(Locale.US).trim());
        normalized = normalized.replaceAll("[^\\p{L}\\p{Nd}\\s]", " ");
        normalized = normalized.replaceAll("\\s+", " ").trim();
        return normalized;
    }

    public static String toAsciiTurkish(String text) {
        if (text == null) {
            return "";
        }

        return text
                .replace('\u00e7', 'c')
                .replace('\u011f', 'g')
                .replace('\u0131', 'i')
                .replace('\u00f6', 'o')
                .replace('\u015f', 's')
                .replace('\u00fc', 'u')
                .replace('\u00e2', 'a')
                .replace('\u00ee', 'i')
                .replace('\u00fb', 'u');
    }

    public static String normalizeAsciiText(String text) {
        return toAsciiTurkish(normalizeText(text));
    }

    private static String normalizeArabicLetters(String text) {
        if (text == null) {
            return "";
        }

        return text
                .replace("\u0640", "")
                .replaceAll("[\\u064B-\\u065F\\u0670]", "")
                .replace('\u0622', '\u0627')
                .replace('\u0623', '\u0627')
                .replace('\u0625', '\u0627')
                .replace('\u0671', '\u0627')
                .replace('\u0649', '\u064A')
                .replace('\u0629', '\u0647');
    }

    public static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
