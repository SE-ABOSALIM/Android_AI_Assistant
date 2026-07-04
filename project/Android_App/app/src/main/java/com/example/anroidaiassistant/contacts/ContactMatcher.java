package com.example.anroidaiassistant.contacts;

import com.example.anroidaiassistant.util.TextNormalizer;

import java.util.ArrayList;
import java.util.List;

public final class ContactMatcher {
    static final int MIN_CONTACT_FUZZY_SCORE = 82;
    private static final int CONTACT_CONTAINS_QUERY_SCORE = 90;
    private static final int CONTACT_CONTAINS_QUERY_PHRASE_SCORE = 93;

    ContactNameScore scoreContactName(String candidateName, String displayName) {
        String candidate = normalizeContactQuery(candidateName);
        String contact = normalizeAsciiText(displayName);
        if (!hasText(candidate) || !hasText(contact)) {
            return new ContactNameScore(0, false);
        }

        if (candidate.equals(contact)) {
            return new ContactNameScore(100, true);
        }

        int containedNameScore = scoreContainedContactName(candidate, contact);
        if (containedNameScore > 0) {
            return new ContactNameScore(containedNameScore, false);
        }

        if (isShortInitialSuffixMatch(candidate, contact)
                || isShortInitialSuffixMatch(contact, candidate)) {
            return new ContactNameScore(94, false);
        }

        String candidateCompact = candidate.replace(" ", "");
        String contactCompact = contact.replace(" ", "");
        int fullSimilarity = Math.round(levenshteinSimilarity(candidateCompact, contactCompact) * 100.0f);

        String candidateLoose = looseContactNameKey(candidateCompact);
        String contactLoose = looseContactNameKey(contactCompact);
        int looseSimilarity = Math.round(levenshteinSimilarity(candidateLoose, contactLoose) * 100.0f);

        return new ContactNameScore(Math.max(fullSimilarity, looseSimilarity), false);
    }

    private String normalizeContactQuery(String value) {
        String normalized = normalizeAsciiText(value);
        return normalized
                .replaceAll("\\s+(i|yi|u|yu|e|ye|a|ya)$", "")
                .trim();
    }

    private int scoreContainedContactName(String candidate, String contact) {
        List<String> candidateTokens = orderedTokens(candidate);
        List<String> contactTokens = orderedTokens(contact);
        if (candidateTokens.isEmpty() || contactTokens.isEmpty()) {
            return 0;
        }

        if (containsTokenPhrase(contactTokens, candidateTokens)) {
            return candidateTokens.size() > 1
                    ? CONTACT_CONTAINS_QUERY_PHRASE_SCORE
                    : CONTACT_CONTAINS_QUERY_SCORE;
        }

        String candidateCompact = candidate.replace(" ", "");
        String contactCompact = contact.replace(" ", "");
        if (candidateCompact.length() >= 4 && contactCompact.contains(candidateCompact)) {
            return CONTACT_CONTAINS_QUERY_SCORE;
        }

        return 0;
    }

    private boolean containsTokenPhrase(List<String> haystackTokens, List<String> needleTokens) {
        if (needleTokens.size() > haystackTokens.size()) {
            return false;
        }

        for (int start = 0; start <= haystackTokens.size() - needleTokens.size(); start++) {
            boolean matched = true;
            for (int offset = 0; offset < needleTokens.size(); offset++) {
                if (!haystackTokens.get(start + offset).equals(needleTokens.get(offset))) {
                    matched = false;
                    break;
                }
            }
            if (matched) {
                return true;
            }
        }

        return false;
    }

    private List<String> orderedTokens(String text) {
        List<String> tokens = new ArrayList<>();
        if (!hasText(text)) {
            return tokens;
        }

        for (String token : normalizeText(text).split(" ")) {
            if (hasText(token)) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private boolean isShortInitialSuffixMatch(String shorterName, String longerName) {
        if (!hasText(shorterName) || !hasText(longerName) || shorterName.length() >= longerName.length()) {
            return false;
        }

        if (!longerName.startsWith(shorterName + " ")) {
            return false;
        }

        String suffix = longerName.substring(shorterName.length()).trim();
        if (!hasText(suffix)) {
            return false;
        }

        for (String token : suffix.split(" ")) {
            if (token.length() > 2) {
                return false;
            }
        }
        return true;
    }

    private String looseContactNameKey(String value) {
        if (!hasText(value)) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        char previous = 0;
        for (int i = 0; i < value.length(); i++) {
            char current = normalizeLooseContactChar(value.charAt(i));
            if (current == previous) {
                continue;
            }
            builder.append(current);
            previous = current;
        }
        return builder.toString();
    }

    private char normalizeLooseContactChar(char value) {
        switch (value) {
            case 'o':
            case 'u':
                return 'u';
            default:
                return value;
        }
    }

    private String normalizeText(String text) {
        return TextNormalizer.normalizeText(text);
    }

    private String normalizeAsciiText(String text) {
        return TextNormalizer.normalizeAsciiText(text);
    }

    private boolean hasText(String value) {
        return TextNormalizer.hasText(value);
    }

    private float levenshteinSimilarity(String left, String right) {
        if (left == null || right == null || left.isEmpty() || right.isEmpty()) {
            return 0.0f;
        }
        if (left.equals(right)) {
            return 1.0f;
        }

        int distance = levenshteinDistance(left, right);
        return 1.0f - ((float) distance / Math.max(left.length(), right.length()));
    }

    private int levenshteinDistance(String left, String right) {
        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];

        for (int j = 0; j <= right.length(); j++) {
            previous[j] = j;
        }

        for (int i = 1; i <= left.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= right.length(); j++) {
                int cost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(
                        Math.min(current[j - 1] + 1, previous[j] + 1),
                        previous[j - 1] + cost
                );
            }

            int[] temp = previous;
            previous = current;
            current = temp;
        }

        return previous[right.length()];
    }
}