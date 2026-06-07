package com.example.anroidaiassistant.apps;

import android.content.Context;

import com.example.anroidaiassistant.resources.SpelledLetterAliases;
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

        if (containsExact(SpelledLetterAliases.DOUBLE_W_PREFIXES, token)
                && index + 1 < tokens.length
                && containsExact(SpelledLetterAliases.DOUBLE_W_TARGETS, tokens[index + 1])) {
            return new SpelledLetterMatch(Collections.singletonList('w'), 2);
        }

        char[] letters = SpelledLetterAliases.lettersFor(token);
        if (letters == null) {
            return new SpelledLetterMatch(Collections.emptyList(), 1);
        }
        return new SpelledLetterMatch(toCharacterList(letters), 1);
    }

    private List<Character> toCharacterList(char[] letters) {
        List<Character> values = new ArrayList<>();
        for (char letter : letters) {
            values.add(letter);
        }
        return values;
    }

    private boolean containsExact(String[] values, String candidate) {
        for (String value : values) {
            if (value.equals(candidate)) {
                return true;
            }
        }
        return false;
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
