package com.example.anroidaiassistant.selection;

import com.example.anroidaiassistant.resources.SelectionAliases;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SelectionNumberParser {
    public Integer parseSelectionNumber(String spokenText, int maxChoice) {
        if (spokenText == null) {
            return null;
        }

        String normalized = normalizeSelectionText(spokenText);
        Matcher matcher = Pattern.compile("\\b\\d+\\b").matcher(normalized);
        if (matcher.find()) {
            return toSelectionIndex(matcher.group(), maxChoice);
        }

        for (String token : normalized.split(" ")) {
            Integer value = selectionWordToNumber(token);
            if (value != null) {
                return toSelectionIndex(String.valueOf(value), maxChoice);
            }
        }

        return null;
    }

    public boolean isCancelSelection(String text) {
        String normalized = normalizeSelectionText(text);
        return containsAny(normalized, SelectionAliases.CANCEL_SELECTION);
    }

    public Integer parseConfirmationSelection(String spokenText) {
        String normalized = normalizeSelectionText(spokenText);
        if (!hasText(normalized)) {
            return null;
        }

        if (containsAny(normalized, SelectionAliases.CONFIRM_YES)) {
            return 0;
        }

        if (containsAny(normalized, SelectionAliases.CONFIRM_NO)) {
            return 1;
        }

        return null;
    }

    private Integer toSelectionIndex(String rawNumber, int maxChoice) {
        try {
            int number = Integer.parseInt(rawNumber);
            if (number < 1 || number > maxChoice) {
                return null;
            }
            return number - 1;
        } catch (Exception ignored) {
            return null;
        }
    }

    private Integer selectionWordToNumber(String token) {
        return SelectionAliases.NUMBER_WORDS.get(token);
    }

    private String normalizeSelectionText(String text) {
        if (text == null) {
            return "";
        }

        return normalizeSelectionDigits(text).trim()
                .toLowerCase(Locale.US)
                .replace('\u0640', ' ')
                .replace('\u0623', '\u0627')
                .replace('\u0625', '\u0627')
                .replace('\u0622', '\u0627')
                .replace('\u00e7', 'c')
                .replace('\u011f', 'g')
                .replace('\u0131', 'i')
                .replace('\u00f6', 'o')
                .replace('\u015f', 's')
                .replace('\u00fc', 'u')
                .replaceAll("[\\u064B-\\u065F\\u0670]", "")
                .replaceAll("[^\\p{L}0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String normalizeSelectionDigits(String text) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch >= '\u0660' && ch <= '\u0669') {
                builder.append((char) ('0' + (ch - '\u0660')));
            } else if (ch >= '\u06F0' && ch <= '\u06F9') {
                builder.append((char) ('0' + (ch - '\u06F0')));
            } else {
                builder.append(ch);
            }
        }
        return builder.toString();
    }

    private boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
