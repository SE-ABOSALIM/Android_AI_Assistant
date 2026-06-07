package com.example.anroidaiassistant.resources;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class SpelledLetterAliases {
    private static final Map<String, char[]> LETTERS_BY_TOKEN = buildLettersByToken();

    private SpelledLetterAliases() {}

    public static final String[] DOUBLE_W_PREFIXES = {
            "duble",
            "double",
            "cift"
    };

    public static final String[] DOUBLE_W_TARGETS = {
            "ve",
            "v",
            "u",
            "yu",
            "you"
    };

    public static char[] lettersFor(String token) {
        return LETTERS_BY_TOKEN.get(token);
    }

    private static Map<String, char[]> buildLettersByToken() {
        Map<String, char[]> aliases = new HashMap<>();

        add(aliases, 'a', "a", "ah");
        add(aliases, 'b', "b", "be", "bee");
        add(aliases, 'c', "c", "ce", "cee");
        add(aliases, 'd', "d", "de", "dee");
        add(aliases, 'e', "e");
        add(aliases, 'f', "f", "fe", "ef");
        add(aliases, 'g', "g", "ge");
        add(aliases, 'h', "h", "he", "aitch", "eyc");
        add(aliases, 'i', "i", "ii");
        add(aliases, 'j', "j", "je", "jay");
        add(aliases, 'k', "k", "ka", "key");
        add(aliases, 'l', "l", "le", "el");
        add(aliases, 'm', "m", "me", "em");
        add(aliases, 'n', "n", "ne", "en");
        add(aliases, 'o', "o", "oh");
        add(aliases, 'p', "p", "pe", "pee");
        add(aliases, 'q', "q", "ku", "queue");
        add(aliases, 'r', "r", "re", "ar");
        add(aliases, 's', "s", "se", "es");
        add(aliases, 't', "t", "te", "tee");
        add(aliases, 'u', "u", "yu", "you");
        add(aliases, 'v', "v", "vee");
        add(aliases, new char[]{'v', 'w'}, "ve");
        add(aliases, 'w', "w", "we", "dabilyu", "dabiliyu", "dabulyu", "doubleyou", "doubleu");
        add(aliases, 'x', "x", "iks", "ex");
        add(aliases, 'y', "y", "ye", "why");
        add(aliases, 'z', "z", "ze", "zet", "zed", "zee");

        return Collections.unmodifiableMap(aliases);
    }

    private static void add(Map<String, char[]> aliases, char letter, String... tokens) {
        add(aliases, new char[]{letter}, tokens);
    }

    private static void add(Map<String, char[]> aliases, char[] letters, String... tokens) {
        for (String token : tokens) {
            aliases.put(token, letters);
        }
    }
}
