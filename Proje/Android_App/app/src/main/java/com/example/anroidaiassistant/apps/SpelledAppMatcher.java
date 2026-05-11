package com.example.anroidaiassistant.apps;

import android.content.Context;

import com.example.anroidaiassistant.util.TextNormalizer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public final class SpelledAppMatcher {
    private final AppMatcher appMatcher;

    public SpelledAppMatcher(AppMatcher appMatcher) {
        this.appMatcher = appMatcher;
    }

    public List<AppMatch> findSpelledAppMatches(Context context, String spokenText) {
        Map<String, AppMatch> matchesByPackage = new LinkedHashMap<>();

        for (String candidate : joinSpelledCandidates(spokenText)) {
            for (AppMatch match : appMatcher.findAppMatches(context, candidate, true)) {
                AppMatch existing = matchesByPackage.get(match.getPackageName());
                if (existing == null
                        || match.getScore() > existing.getScore()
                        || (match.isExact() && !existing.isExact())) {
                    matchesByPackage.put(match.getPackageName(), match);
                }
            }
        }

        List<AppMatch> matches = new ArrayList<>(matchesByPackage.values());
        matches.sort(Comparator.comparing(AppMatch::getScore).reversed());
        if (matches.size() > AppMatcher.MAX_APP_CHOICES) {
            return new ArrayList<>(matches.subList(0, AppMatcher.MAX_APP_CHOICES));
        }
        return matches;
    }

    private List<String> joinSpelledCandidates(String text) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        if (!hasText(text)) {
            return new ArrayList<>(candidates);
        }

        candidates.addAll(joinSpelledLetterNameCandidates(text));
        candidates.add(normalizeAsciiText(text).replace(" ", ""));

        candidates.remove(null);
        candidates.remove("");
        return new ArrayList<>(candidates);
    }

    private List<String> joinSpelledLetterNameCandidates(String text) {
        String normalized = normalizeAsciiText(text);
        if (!hasText(normalized)) {
            return Collections.emptyList();
        }

        List<String> candidates = new ArrayList<>();
        candidates.add("");
        int matchedTokens = 0;
        int totalTokens = 0;
        String[] tokens = normalized.split(" ");
        for (int i = 0; i < tokens.length; i++) {
            String token = tokens[i];
            if (!hasText(token)) {
                continue;
            }

            totalTokens++;
            SpelledLetterMatch spelledLetterMatch = spelledTokenToLetters(tokens, i);
            List<Character> letters = spelledLetterMatch.letters;
            if (spelledLetterMatch.consumedTokens > 1) {
                i += spelledLetterMatch.consumedTokens - 1;
                totalTokens += spelledLetterMatch.consumedTokens - 1;
            }
            if (letters.isEmpty()) {
                if (token.length() == 1 && Character.isLetterOrDigit(token.charAt(0))) {
                    letters = Collections.singletonList(token.charAt(0));
                } else {
                    continue;
                }
            }

            List<String> nextCandidates = new ArrayList<>();
            for (String candidate : candidates) {
                for (Character letter : letters) {
                    nextCandidates.add(candidate + letter);
                }
            }
            candidates = nextCandidates;
            matchedTokens++;
        }

        if (matchedTokens == 0 || matchedTokens < Math.max(1, totalTokens - 1)) {
            return Collections.emptyList();
        }

        return candidates;
    }

    private SpelledLetterMatch spelledTokenToLetters(String[] tokens, int index) {
        String token = tokens[index];

        if (("duble".equals(token) || "double".equals(token) || "cift".equals(token))
                && index + 1 < tokens.length
                && ("ve".equals(tokens[index + 1]) || "v".equals(tokens[index + 1])
                || "u".equals(tokens[index + 1]) || "yu".equals(tokens[index + 1])
                || "you".equals(tokens[index + 1]))) {
            return new SpelledLetterMatch(Collections.singletonList('w'), 2);
        }

        switch (token) {
            case "a":
            case "ah":
                return new SpelledLetterMatch(Collections.singletonList('a'), 1);
            case "b":
            case "be":
            case "bee":
                return new SpelledLetterMatch(Collections.singletonList('b'), 1);
            case "c":
            case "ce":
            case "cee":
                return new SpelledLetterMatch(Collections.singletonList('c'), 1);
            case "d":
            case "de":
            case "dee":
                return new SpelledLetterMatch(Collections.singletonList('d'), 1);
            case "e":
                return new SpelledLetterMatch(Collections.singletonList('e'), 1);
            case "f":
            case "fe":
            case "ef":
                return new SpelledLetterMatch(Collections.singletonList('f'), 1);
            case "g":
            case "ge":
                return new SpelledLetterMatch(Collections.singletonList('g'), 1);
            case "h":
            case "he":
            case "aitch":
            case "eyc":
                return new SpelledLetterMatch(Collections.singletonList('h'), 1);
            case "i":
            case "ii":
                return new SpelledLetterMatch(Collections.singletonList('i'), 1);
            case "j":
            case "je":
            case "jay":
                return new SpelledLetterMatch(Collections.singletonList('j'), 1);
            case "k":
            case "ka":
            case "key":
                return new SpelledLetterMatch(Collections.singletonList('k'), 1);
            case "l":
            case "le":
            case "el":
                return new SpelledLetterMatch(Collections.singletonList('l'), 1);
            case "m":
            case "me":
            case "em":
                return new SpelledLetterMatch(Collections.singletonList('m'), 1);
            case "n":
            case "ne":
            case "en":
                return new SpelledLetterMatch(Collections.singletonList('n'), 1);
            case "o":
            case "oh":
                return new SpelledLetterMatch(Collections.singletonList('o'), 1);
            case "p":
            case "pe":
            case "pee":
                return new SpelledLetterMatch(Collections.singletonList('p'), 1);
            case "q":
            case "ku":
            case "queue":
                return new SpelledLetterMatch(Collections.singletonList('q'), 1);
            case "r":
            case "re":
            case "ar":
                return new SpelledLetterMatch(Collections.singletonList('r'), 1);
            case "s":
            case "se":
            case "es":
                return new SpelledLetterMatch(Collections.singletonList('s'), 1);
            case "t":
            case "te":
            case "tee":
                return new SpelledLetterMatch(Collections.singletonList('t'), 1);
            case "u":
            case "yu":
            case "you":
                return new SpelledLetterMatch(Collections.singletonList('u'), 1);
            case "v":
            case "vee":
                return new SpelledLetterMatch(Collections.singletonList('v'), 1);
            case "ve":
                List<Character> veCandidates = new ArrayList<>();
                veCandidates.add('v');
                veCandidates.add('w');
                return new SpelledLetterMatch(veCandidates, 1);
            case "w":
            case "we":
            case "dabilyu":
            case "dabiliyu":
            case "dabulyu":
            case "doubleyou":
            case "doubleu":
                return new SpelledLetterMatch(Collections.singletonList('w'), 1);
            case "x":
            case "iks":
            case "ex":
                return new SpelledLetterMatch(Collections.singletonList('x'), 1);
            case "y":
            case "ye":
            case "why":
                return new SpelledLetterMatch(Collections.singletonList('y'), 1);
            case "z":
            case "ze":
            case "zet":
            case "zed":
            case "zee":
                return new SpelledLetterMatch(Collections.singletonList('z'), 1);
            default:
                return new SpelledLetterMatch(Collections.emptyList(), 1);
        }
    }

    private String normalizeAsciiText(String text) {
        return TextNormalizer.normalizeAsciiText(text);
    }

    private boolean hasText(String value) {
        return TextNormalizer.hasText(value);
    }

    private static final class SpelledLetterMatch {
        private final List<Character> letters;
        private final int consumedTokens;

        private SpelledLetterMatch(List<Character> letters, int consumedTokens) {
            this.letters = letters;
            this.consumedTokens = consumedTokens;
        }
    }
}