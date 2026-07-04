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

        String normalized = removeGestureCountPhrases(normalizeSelectionText(spokenText));
        Matcher matcher = Pattern.compile("\\b\\d+\\b").matcher(normalized);
        if (matcher.find()) {
            return toSelectionIndex(matcher.group(), maxChoice);
        }

        Integer wordNumber = parseNumberWords(normalized);
        if (wordNumber != null) {
            return toSelectionIndex(String.valueOf(wordNumber), maxChoice);
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
        Integer directValue = SelectionAliases.NUMBER_WORDS.get(token);
        if (directValue != null) {
            return directValue;
        }
        Integer normalizedArabicValue = normalizedArabicNumberToValue(token);
        if (normalizedArabicValue != null) {
            return normalizedArabicValue;
        }
        if (token != null && token.length() > 1 && token.charAt(0) == '\u0648') {
            String withoutConjunction = token.substring(1);
            Integer value = SelectionAliases.NUMBER_WORDS.get(withoutConjunction);
            return value != null ? value : normalizedArabicNumberToValue(withoutConjunction);
        }
        Integer suffixedTurkishValue = turkishSuffixedNumberToValue(token);
        if (suffixedTurkishValue != null) {
            return suffixedTurkishValue;
        }
        return null;
    }

    private Integer normalizedArabicNumberToValue(String token) {
        if (!hasText(token)) {
            return null;
        }

        String candidate = token;
        if (candidate.startsWith("\u0627\u0644") && candidate.length() > 2) {
            candidate = candidate.substring(2);
        }

        Integer value = SelectionAliases.NUMBER_WORDS.get(candidate);
        if (value != null) {
            return value;
        }

        if (candidate.endsWith("\u0629")) {
            value = SelectionAliases.NUMBER_WORDS.get(candidate.substring(0, candidate.length() - 1) + "\u0647");
            if (value != null) {
                return value;
            }
        }

        if (candidate.endsWith("\u0647")) {
            value = SelectionAliases.NUMBER_WORDS.get(candidate.substring(0, candidate.length() - 1) + "\u0629");
            if (value != null) {
                return value;
            }
        }

        return null;
    }

    private Integer turkishSuffixedNumberToValue(String token) {
        if (!hasText(token)) {
            return null;
        }

        for (String suffix : new String[]{
                "ye", "ya", "e", "a", "yi", "yu", "i", "u",
                "de", "da", "te", "ta", "den", "dan", "ten", "tan",
                "inci", "inciye", "incisine", "incisi",
                "uncu", "uncuye", "uncusuna", "uncusu",
                "nci", "ncu", "ncisine", "ncisi"
        }) {
            if (token.length() > suffix.length() + 1 && token.endsWith(suffix)) {
                Integer value = turkishNumberStemToValue(token.substring(0, token.length() - suffix.length()));
                if (value != null) {
                    return value;
                }
            }
        }
        return null;
    }

    private Integer turkishNumberStemToValue(String stem) {
        Integer value = SelectionAliases.NUMBER_WORDS.get(stem);
        if (value != null) {
            return value;
        }
        if ("dord".equals(stem)) {
            return SelectionAliases.NUMBER_WORDS.get("dort");
        }
        return null;
    }

    private Integer parseNumberWords(String normalized) {
        int sum = 0;
        int matchedCount = 0;

        for (String token : normalized.split(" ")) {
            Integer value = selectionWordToNumber(token);
            if (value == null) {
                continue;
            }
            if (value == 100) {
                return 100;
            }
            sum += value;
            matchedCount++;
        }

        if (matchedCount == 0 || sum <= 0 || sum > 100) {
            return null;
        }
        return sum;
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

    private String removeGestureCountPhrases(String text) {
        if (!hasText(text)) {
            return "";
        }
        return text
                .replaceAll("\\btwo\\s+times\\b", " ")
                .replaceAll("\\biki\\s+(?:kere|kez|defa)\\b", " ")
                .replaceAll("(?:^|\\s)(?:\u0645\u0631\u062a\u064a\u0646|\u0646\u0642\u0631\u062a\u064a\u0646|\u0636\u063a\u0637\u062a\u064a\u0646)(?=\\s|$)", " ")
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
