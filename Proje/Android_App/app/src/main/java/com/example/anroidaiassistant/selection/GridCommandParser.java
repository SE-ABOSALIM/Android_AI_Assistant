package com.example.anroidaiassistant.selection;

import com.example.anroidaiassistant.resources.SelectionAliases;
import com.example.anroidaiassistant.util.TextNormalizer;

import java.util.Locale;

public final class GridCommandParser {
    public String parseAction(String spokenText) {
        String normalized = normalize(spokenText);
        if (!TextNormalizer.hasText(normalized)) {
            return null;
        }

        if (containsAny(normalized,
                "smaller grid",
                "make grid smaller",
                "more grid cells",
                "gridi kucult",
                "izgarayi kucult",
                "gridi kucuk yap",
                "izgarayi kucuk yap",
                "daha kucuk grid",
                "daha kucuk izgara",
                "\u0635\u063a\u0631 \u0627\u0644\u0634\u0628\u0643\u0647",
                "\u0634\u0628\u0643\u0647 \u0627\u0635\u063a\u0631")) {
            return "smaller";
        }

        if (containsAny(normalized,
                "larger grid",
                "bigger grid",
                "make grid larger",
                "make grid bigger",
                "fewer grid cells",
                "gridi buyut",
                "izgarayi buyut",
                "gridi buyuk yap",
                "izgarayi buyuk yap",
                "daha buyuk grid",
                "daha buyuk izgara",
                "\u0643\u0628\u0631 \u0627\u0644\u0634\u0628\u0643\u0647",
                "\u0634\u0628\u0643\u0647 \u0627\u0643\u0628\u0631")) {
            return "larger";
        }

        if (containsAny(normalized,
                "hide grid",
                "close grid",
                "turn off grid",
                "remove grid",
                "gridi kapat",
                "izgarayi kapat",
                "gridi gizle",
                "izgarayi gizle",
                "\u0627\u062e\u0641\u064a \u0627\u0644\u0634\u0628\u0643\u0647",
                "\u0627\u063a\u0644\u0642 \u0627\u0644\u0634\u0628\u0643\u0647",
                "\u0627\u0644\u063a\u064a \u0627\u0644\u0634\u0628\u0643\u0647")) {
            return "hide";
        }

        return null;
    }

    public boolean isCellSelectionText(String spokenText) {
        String normalized = normalize(spokenText);
        if (!TextNormalizer.hasText(normalized)) {
            return false;
        }

        String commandWordsRemoved = normalized
                .replaceAll("\\b(?:tap|click|press|select|cell|grid|box|number|on|the|double|hold|long|bas|tikla|sec|hucre|kare|numara|cift|uzun|basili|tut)\\b", " ")
                .replaceAll("(?:^|\\s)(?:\u0627\u0636\u063a\u0637|\u0627\u0646\u0642\u0631|\u0627\u062e\u062a\u0631|\u062d\u062f\u062f|\u062e\u0644\u064a\u0647|\u062e\u0627\u0646\u0647|\u0645\u0631\u0628\u0639|\u0631\u0642\u0645|\u0639\u0644\u0649|\u0639\u0644\u064a|\u0645\u0631\u062a\u064a\u0646|\u0636\u063a\u0637\u0647|\u0636\u063a\u0637|\u0645\u0637\u0648\u0644|\u0645\u0637\u0648\u0644\u0627|\u0645\u0637\u0648\u0644\u0647|\u0637\u0648\u064a\u0644|\u0637\u0648\u064a\u0644\u0647|\u0628\u0627\u0633\u062a\u0645\u0631\u0627\u0631|\u0627\u0633\u062a\u0645\u0631\u0627\u0631|\u0627\u0633\u062a\u0645\u0631)(?=\\s|$)", " ")
                .replaceAll("\\s+", " ")
                .trim();

        if (commandWordsRemoved.matches("\\d+")) {
            return true;
        }

        return containsOnlyNumberWords(commandWordsRemoved);
    }

    public String parseCellGestureAction(String spokenText) {
        String normalized = normalize(spokenText);
        if (!TextNormalizer.hasText(normalized)) {
            return "tap";
        }

        if (containsAny(normalized,
                "double tap",
                "double click",
                "double press",
                "cift tikla",
                "cift bas",
                "cift tiklama",
                "\u0645\u0631\u062a\u064a\u0646",
                "\u0636\u063a\u0637\u062a\u064a\u0646")) {
            return "double_tap";
        }

        if (containsAny(normalized,
                "hold",
                "long press",
                "press and hold",
                "uzun bas",
                "basili tut",
                "\u0645\u0637\u0648\u0644",
                "\u0645\u0637\u0648\u0644\u0627",
                "\u0645\u0637\u0648\u0644\u0647",
                "\u0637\u0648\u064a\u0644",
                "\u0637\u0648\u064a\u0644\u0647",
                "\u0628\u0627\u0633\u062a\u0645\u0631\u0627\u0631",
                "\u0627\u0633\u062a\u0645\u0631\u0627\u0631",
                "\u0636\u063a\u0637\u0647 \u0645\u0637\u0648\u0644\u0647",
                "\u0627\u0636\u063a\u0637 \u0645\u0637\u0648\u0644\u0627")) {
            return "hold";
        }

        return "tap";
    }

    private boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.contains(normalize(candidate))) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String text) {
        return TextNormalizer.normalizeAsciiText(text)
                .toLowerCase(Locale.US)
                .replace('\u0640', ' ')
                .replace('\u0623', '\u0627')
                .replace('\u0625', '\u0627')
                .replace('\u0622', '\u0627')
                .replaceAll("[\\u064B-\\u065F\\u0670]", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private boolean containsOnlyNumberWords(String value) {
        if (!TextNormalizer.hasText(value)) {
            return false;
        }

        for (String token : value.split(" ")) {
            if ("and".equals(token)
                    || "ve".equals(token)
                    || "\u0648".equals(token)
                    || "\u0627\u0644".equals(token)) {
                continue;
            }
            if (!SelectionAliases.NUMBER_WORDS.containsKey(token)
                    && !isArabicAndNumberWord(token)
                    && !isTurkishSuffixedNumberWord(token)) {
                return false;
            }
        }
        return true;
    }

    private boolean isArabicAndNumberWord(String token) {
        return token != null
                && token.length() > 1
                && token.charAt(0) == '\u0648'
                && SelectionAliases.NUMBER_WORDS.containsKey(token.substring(1));
    }

    private boolean isTurkishSuffixedNumberWord(String token) {
        if (!TextNormalizer.hasText(token)) {
            return false;
        }

        for (String suffix : new String[]{"ye", "ya", "e", "a", "inci", "inciye", "uncu", "uncuye"}) {
            if (token.length() > suffix.length() + 1
                    && token.endsWith(suffix)
                    && isTurkishNumberStem(token.substring(0, token.length() - suffix.length()))) {
                return true;
            }
        }
        return false;
    }

    private boolean isTurkishNumberStem(String stem) {
        return SelectionAliases.NUMBER_WORDS.containsKey(stem)
                || "dord".equals(stem);
    }
}
