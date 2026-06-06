package com.example.anroidaiassistant.accessibility.click;

import com.example.anroidaiassistant.util.TextNormalizer;

import java.util.List;

public final class ClickTextMatcher {
    private static final int EXACT_SCORE = 100;
    private static final int CONTAINS_SCORE = 82;
    private static final int ALL_TOKENS_SCORE = 74;
    private static final int TOKEN_OVERLAP_SCORE = 48;
    private static final int FUZZY_SCORE = 66;

    public ClickTextMatch score(String nodeText, List<String> targetVariants) {
        int bestScore = 0;
        String bestReason = "";

        for (String target : targetVariants) {
            if (!TextNormalizer.hasText(target)) {
                continue;
            }

            ClickTextMatch match = scoreOneTarget(nodeText, target);
            if (match.score > bestScore) {
                bestScore = match.score;
                bestReason = match.reason;
            }
        }

        return new ClickTextMatch(bestScore, bestReason);
    }

    private ClickTextMatch scoreOneTarget(String nodeText, String target) {
        if (!TextNormalizer.hasText(nodeText) || !TextNormalizer.hasText(target)) {
            return new ClickTextMatch(0, "");
        }

        if (nodeText.equals(target)) {
            return new ClickTextMatch(EXACT_SCORE, "exact");
        }
        if (nodeText.contains(target)
                || (nodeText.length() >= 4 && target.contains(nodeText))) {
            return new ClickTextMatch(CONTAINS_SCORE, "contains");
        }
        if (containsAllTokens(nodeText, target)) {
            return new ClickTextMatch(ALL_TOKENS_SCORE, "all_tokens");
        }
        if (hasUsefulTokenOverlap(nodeText, target)) {
            return new ClickTextMatch(TOKEN_OVERLAP_SCORE, "token_overlap");
        }
        if (isStrongFuzzyMatch(nodeText, target)) {
            return new ClickTextMatch(FUZZY_SCORE, "fuzzy");
        }

        return new ClickTextMatch(0, "");
    }

    private boolean containsAllTokens(String nodeText, String target) {
        String[] tokens = target.split(" ");
        boolean hasToken = false;
        for (String token : tokens) {
            if (!TextNormalizer.hasText(token)) {
                continue;
            }
            hasToken = true;
            if (!nodeText.contains(token)) {
                return false;
            }
        }
        return hasToken;
    }

    private boolean hasUsefulTokenOverlap(String nodeText, String target) {
        for (String token : target.split(" ")) {
            if (token.length() >= 4 && nodeText.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private boolean isStrongFuzzyMatch(String nodeText, String target) {
        String compactNode = nodeText.replace(" ", "");
        String compactTarget = target.replace(" ", "");
        if (compactNode.length() < 4 || compactTarget.length() < 4) {
            return false;
        }
        if (Math.abs(compactNode.length() - compactTarget.length()) > 2) {
            return false;
        }
        return levenshteinSimilarity(compactNode, compactTarget) >= minimumSimilarity(compactTarget);
    }

    private double minimumSimilarity(String target) {
        if (target.length() <= 4) {
            return 0.86;
        }
        if (target.length() <= 9) {
            return 0.80;
        }
        return 0.74;
    }

    private double levenshteinSimilarity(String left, String right) {
        if (left.equals(right)) {
            return 1.0;
        }

        int distance = levenshteinDistance(left, right);
        return 1.0 - (distance / (double) Math.max(left.length(), right.length()));
    }

    private int levenshteinDistance(String left, String right) {
        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];

        for (int i = 0; i <= right.length(); i++) {
            previous[i] = i;
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

            int[] tmp = previous;
            previous = current;
            current = tmp;
        }

        return previous[right.length()];
    }
}
