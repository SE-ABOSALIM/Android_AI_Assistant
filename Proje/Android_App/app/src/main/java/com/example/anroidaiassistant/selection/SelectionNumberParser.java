package com.example.anroidaiassistant.selection;

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
        return normalized.contains("iptal")
                || normalized.contains("cancel")
                || normalized.contains("vazgec")
                || normalized.contains("vaz gectim")
                || normalized.contains("geri")
                || normalized.contains("kapat")
                || normalized.contains("cik")
                || normalized.contains("cikis")
                || normalized.contains("\u0627\u0644\u063a\u0627\u0621")
                || normalized.contains("\u0627\u0644\u063a\u064a")
                || normalized.contains("\u062e\u0631\u0648\u062c")
                || normalized.contains("\u0631\u062c\u0648\u0639")
                || normalized.contains("\u0627\u0642\u0641\u0644");
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
        switch (token) {
            case "bir":
            case "birinci":
            case "one":
            case "first":
            case "\u0648\u0627\u062d\u062f":
            case "\u0648\u0627\u062d\u062f\u0647":
            case "\u0627\u062d\u062f":
            case "\u0627\u062d\u062f\u0649":
            case "\u0627\u0648\u0644":
            case "\u0627\u0648\u0644\u0649":
            case "\u0627\u0644\u0627\u0648\u0644":
            case "\u0627\u0644\u0627\u0648\u0644\u0649":
            case "wahid":
            case "vahid":
                return 1;
            case "iki":
            case "ikinci":
            case "two":
            case "second":
            case "\u0627\u062b\u0646\u064a\u0646":
            case "\u0627\u062b\u0646\u0627\u0646":
            case "\u0627\u062a\u0646\u064a\u0646":
            case "\u062b\u0646\u064a\u0646":
            case "\u0627\u062b\u0646\u062a\u064a\u0646":
            case "\u0627\u062b\u0646\u062a\u0627\u0646":
            case "\u062b\u0627\u0646\u064a":
            case "\u0627\u0644\u062b\u0627\u0646\u064a":
            case "\u062a\u0627\u0646\u064a":
            case "isnan":
            case "ithnan":
            case "itnen":
                return 2;
            case "uc":
            case "ucuncu":
            case "three":
            case "third":
            case "\u062b\u0644\u0627\u062b\u0647":
            case "\u062b\u0644\u0627\u062b\u0629":
            case "\u062b\u0644\u0627\u062b":
            case "\u062a\u0644\u0627\u062a\u0647":
            case "\u062a\u0644\u0627\u062a\u0629":
            case "\u062b\u0627\u0644\u062b":
            case "\u0627\u0644\u062b\u0627\u0644\u062b":
            case "talata":
            case "thalatha":
                return 3;
            case "dort":
            case "dorduncu":
            case "four":
            case "fourth":
            case "\u0627\u0631\u0628\u0639\u0647":
            case "\u0627\u0631\u0628\u0639\u0629":
            case "\u0627\u0631\u0628\u0639":
            case "\u0631\u0627\u0628\u0639":
            case "\u0627\u0644\u0631\u0627\u0628\u0639":
            case "arbaa":
                return 4;
            case "bes":
            case "besinci":
            case "five":
            case "fifth":
            case "\u062e\u0645\u0633\u0647":
            case "\u062e\u0645\u0633\u0629":
            case "\u062e\u0645\u0633":
            case "\u062e\u0627\u0645\u0633":
            case "\u0627\u0644\u062e\u0627\u0645\u0633":
            case "khamsa":
                return 5;
            default:
                return null;
        }
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
}