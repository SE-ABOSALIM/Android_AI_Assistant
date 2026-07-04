package com.example.anroidaiassistant.resources;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class SelectionAliases {
    private SelectionAliases() {}

    public static final String[] CANCEL_SELECTION = {
            "iptal",
            "cancel",
            "vazgec",
            "vaz gectim",
            "geri",
            "kapat",
            "cik",
            "cikis",
            "\u0627\u0644\u063a\u0627\u0621",
            "\u0627\u0644\u063a\u064a",
            "\u062e\u0631\u0648\u062c",
            "\u0631\u062c\u0648\u0639",
            "\u0627\u0642\u0641\u0644"
    };

    public static final String[] CONFIRM_YES = {
            "evet",
            "onayla",
            "tamam",
            "yes",
            "yeah",
            "naam",
            "neam",
            "na3am",
            "confirm",
            "approve",
            "\u0646\u0639\u0645",
            "\u0627\u064a\u0648\u0647",
            "\u0627\u064a\u0648\u0627",
            "\u0645\u0648\u0627\u0641\u0642",
            "\u0627\u0643\u062f"
    };

    public static final String[] CONFIRM_NO = {
            "hayir",
            "hayr",
            "hayir istemiyorum",
            "iptal",
            "vazgec",
            "la",
            "le",
            "laa",
            "no",
            "cancel",
            "deny",
            "\u0644\u0627",
            "\u0627\u0644\u063a\u0627\u0621",
            "\u0627\u0644\u063a\u064a",
            "\u0627\u0631\u0641\u0636"
    };

    public static final Map<String, Integer> NUMBER_WORDS = buildNumberWords();

    private static Map<String, Integer> buildNumberWords() {
        Map<String, Integer> words = new HashMap<>();

        add(words, 1,
                "bir",
                "birinci",
                "one",
                "first",
                "\u0648\u0627\u062d\u062f",
                "\u0648\u0627\u062d\u062f\u0647",
                "\u0627\u062d\u062f",
                "\u0627\u062d\u062f\u0649",
                "\u0627\u0648\u0644",
                "\u0627\u0648\u0644\u0649",
                "\u0627\u0644\u0627\u0648\u0644",
                "\u0627\u0644\u0627\u0648\u0644\u0649",
                "wahid",
                "vahid"
        );

        add(words, 2,
                "iki",
                "ikinci",
                "two",
                "second",
                "\u0627\u062b\u0646\u064a\u0646",
                "\u0627\u062b\u0646\u0627\u0646",
                "\u0627\u062a\u0646\u064a\u0646",
                "\u062b\u0646\u064a\u0646",
                "\u0627\u062b\u0646\u062a\u064a\u0646",
                "\u0627\u062b\u0646\u062a\u0627\u0646",
                "\u062b\u0627\u0646\u064a",
                "\u0627\u0644\u062b\u0627\u0646\u064a",
                "\u062a\u0627\u0646\u064a",
                "isnan",
                "ithnan",
                "itnen"
        );

        add(words, 3,
                "uc",
                "ucuncu",
                "three",
                "third",
                "\u062b\u0644\u0627\u062b\u0647",
                "\u062b\u0644\u0627\u062b\u0629",
                "\u062b\u0644\u0627\u062b",
                "\u062a\u0644\u0627\u062a\u0647",
                "\u062a\u0644\u0627\u062a\u0629",
                "\u062b\u0627\u0644\u062b",
                "\u0627\u0644\u062b\u0627\u0644\u062b",
                "talata",
                "thalatha"
        );

        add(words, 4,
                "dort",
                "dorduncu",
                "four",
                "fourth",
                "\u0627\u0631\u0628\u0639\u0647",
                "\u0627\u0631\u0628\u0639\u0629",
                "\u0627\u0631\u0628\u0639",
                "\u0631\u0627\u0628\u0639",
                "\u0627\u0644\u0631\u0627\u0628\u0639",
                "arbaa"
        );

        add(words, 5,
                "bes",
                "besinci",
                "five",
                "fifth",
                "\u062e\u0645\u0633\u0647",
                "\u062e\u0645\u0633\u0629",
                "\u062e\u0645\u0633",
                "\u062e\u0627\u0645\u0633",
                "\u0627\u0644\u062e\u0627\u0645\u0633",
                "khamsa"
        );

        add(words, 6,
                "alti",
                "altinci",
                "six",
                "sixth",
                "\u0633\u062a\u0647",
                "\u0633\u062a\u0629",
                "\u0633\u062a",
                "\u0633\u0627\u062f\u0633",
                "\u0627\u0644\u0633\u0627\u062f\u0633"
        );

        add(words, 7,
                "yedi",
                "yedinci",
                "seven",
                "seventh",
                "\u0633\u0628\u0639\u0647",
                "\u0633\u0628\u0639\u0629",
                "\u0633\u0628\u0639",
                "\u0633\u0627\u0628\u0639",
                "\u0627\u0644\u0633\u0627\u0628\u0639"
        );

        add(words, 8,
                "sekiz",
                "sekizinci",
                "eight",
                "eighth",
                "\u062b\u0645\u0627\u0646\u064a\u0647",
                "\u062b\u0645\u0627\u0646\u064a\u0629",
                "\u062b\u0645\u0627\u0646",
                "\u062b\u0627\u0645\u0646",
                "\u0627\u0644\u062b\u0627\u0645\u0646"
        );

        add(words, 9,
                "dokuz",
                "dokuzuncu",
                "nine",
                "ninth",
                "\u062a\u0633\u0639\u0647",
                "\u062a\u0633\u0639\u0629",
                "\u062a\u0633\u0639",
                "\u062a\u0627\u0633\u0639",
                "\u0627\u0644\u062a\u0627\u0633\u0639"
        );

        add(words, 10,
                "on",
                "onuncu",
                "ten",
                "tenth",
                "\u0639\u0634\u0631\u0647",
                "\u0639\u0634\u0631\u0629",
                "\u0639\u0634\u0631",
                "\u0639\u0627\u0634\u0631",
                "\u0627\u0644\u0639\u0627\u0634\u0631"
        );

        add(words, 11, "eleven", "eleventh");
        add(words, 12, "twelve", "twelfth");
        add(words, 13, "thirteen", "thirteenth");
        add(words, 14, "fourteen", "fourteenth");
        add(words, 15, "fifteen", "fifteenth");
        add(words, 16, "sixteen", "sixteenth");
        add(words, 17, "seventeen", "seventeenth");
        add(words, 18, "eighteen", "eighteenth");
        add(words, 19, "nineteen", "nineteenth");

        add(words, 20, "yirmi", "twenty", "twentieth", "\u0639\u0634\u0631\u064a\u0646");
        add(words, 30, "otuz", "thirty", "thirtieth", "\u062b\u0644\u0627\u062b\u064a\u0646", "\u062a\u0644\u0627\u062a\u064a\u0646");
        add(words, 40, "kirk", "forty", "fortieth", "\u0627\u0631\u0628\u0639\u064a\u0646");
        add(words, 50, "elli", "fifty", "fiftieth", "\u062e\u0645\u0633\u064a\u0646");
        add(words, 60, "altmis", "sixty", "sixtieth", "\u0633\u062a\u064a\u0646");
        add(words, 70, "yetmis", "seventy", "seventieth", "\u0633\u0628\u0639\u064a\u0646");
        add(words, 80, "seksen", "eighty", "eightieth", "\u062b\u0645\u0627\u0646\u064a\u0646");
        add(words, 90, "doksan", "ninety", "ninetieth", "\u062a\u0633\u0639\u064a\u0646");
        add(words, 100, "yuz", "hundred", "one hundred", "\u0645\u0626\u0647", "\u0645\u0627\u0626\u0647", "\u0645\u064a\u0647");

        return Collections.unmodifiableMap(words);
    }

    private static void add(Map<String, Integer> words, int value, String... aliases) {
        for (String alias : aliases) {
            words.put(alias, value);
        }
    }
}
