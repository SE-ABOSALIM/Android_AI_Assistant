package com.example.anroidaiassistant.accessibility.click;

import com.example.anroidaiassistant.util.TextNormalizer;

import java.util.List;

public final class ClickTextMatcher {
    private static final int EXACT_SCORE = 100;
    private static final int CONTAINS_SCORE = 82;
    private static final int ALL_TOKENS_SCORE = 74;
    private static final int TOKEN_COVERAGE_SCORE = 58;
    private static final int TOKEN_OVERLAP_SCORE = 48;
    private static final int SINGLE_TOKEN_SCORE = 48;
    private static final int FUZZY_SCORE = 66;

    public ClickTextMatch score(String nodeText, List<String> targetVariants) {
        int bestScore = 0;
        String bestReason = "";
        String bestTarget = "";

        for (String target : targetVariants) {
            if (!TextNormalizer.hasText(target)) {
                continue;
            }

            ClickTextMatch match = scoreOneTarget(nodeText, target);
            if (match.score > bestScore) {
                bestScore = match.score;
                bestReason = match.reason;
                bestTarget = target;
            }
        }

        return new ClickTextMatch(bestScore, bestReason, bestTarget);
    }

    private ClickTextMatch scoreOneTarget(String nodeText, String target) {
        if (!TextNormalizer.hasText(nodeText) || !TextNormalizer.hasText(target)) {
            return new ClickTextMatch(0, "", target);
        }

        if (nodeText.equals(target)) {
            return new ClickTextMatch(EXACT_SCORE, "exact", target);
        }
        if (target.replace(" ", "").length() < 2) {
            return new ClickTextMatch(0, "", target);
        }
        if (containsSafePhrase(nodeText, target)) {
            return new ClickTextMatch(CONTAINS_SCORE, "contains", target);
        }
        if (containsAllTokens(nodeText, target)) {
            return new ClickTextMatch(ALL_TOKENS_SCORE, "all_tokens", target);
        }
        if (isStrongTokenCoverage(nodeText, target)) {
            return new ClickTextMatch(TOKEN_COVERAGE_SCORE, "token_coverage", target);
        }
        if (isStrongFuzzyMatch(nodeText, target)) {
            return new ClickTextMatch(FUZZY_SCORE, "fuzzy", target);
        }
        if (hasUsefulTokenOverlap(nodeText, target)) {
            return new ClickTextMatch(TOKEN_OVERLAP_SCORE, "token_overlap", target);
        }
        if (isSingleUsefulTokenMatch(nodeText, target)) {
            return new ClickTextMatch(SINGLE_TOKEN_SCORE, "single_token", target);
        }

        return new ClickTextMatch(0, "", target);
    }

    private boolean containsSafePhrase(String nodeText, String target) {
        if (nodeText.contains(target)) {
            return true;
        }

        String[] nodeTokens = meaningfulTokens(nodeText);
        String[] targetTokens = meaningfulTokens(target);
        return nodeTokens.length >= 2
                && targetTokens.length >= 2
                && target.contains(nodeText);
    }

    private boolean containsAllTokens(String nodeText, String target) {
        String[] targetTokens = meaningfulTokens(target);
        if (targetTokens.length < 2) {
            return false;
        }

        for (String token : targetTokens) {
            if (!containsSimilarToken(nodeText, token)) {
                return false;
            }
        }
        return true;
    }

    private boolean isStrongTokenCoverage(String nodeText, String target) {
        String[] targetTokens = meaningfulTokens(target);
        if (targetTokens.length < 3) {
            return false;
        }

        int matchedTokens = 0;
        for (String token : targetTokens) {
            if (containsSimilarToken(nodeText, token)) {
                matchedTokens++;
            }
        }

        int requiredMatches = Math.max(2, (int) Math.ceil(targetTokens.length * 0.75));
        return matchedTokens >= requiredMatches;
    }

    private boolean isSingleUsefulTokenMatch(String nodeText, String target) {
        String[] targetTokens = meaningfulTokens(target);
        if (targetTokens.length != 1) {
            return false;
        }
        return containsSimilarToken(nodeText, targetTokens[0]);
    }

    private boolean hasUsefulTokenOverlap(String nodeText, String target) {
        String[] targetTokens = meaningfulTokens(target);
        if (targetTokens.length < 2) {
            return false;
        }

        for (String token : targetTokens) {
            if (containsSimilarToken(nodeText, token)) {
                return true;
            }
        }
        return false;
    }

    private String[] meaningfulTokens(String text) {
        if (!TextNormalizer.hasText(text)) {
            return new String[0];
        }

        String[] rawTokens = text.split(" ");
        int count = 0;
        for (String token : rawTokens) {
            if (isMeaningfulToken(token)) {
                count++;
            }
        }

        String[] tokens = new String[count];
        int index = 0;
        for (String token : rawTokens) {
            if (isMeaningfulToken(token)) {
                tokens[index++] = token;
            }
        }
        return tokens;
    }

    private boolean isMeaningfulToken(String token) {
        return TextNormalizer.hasText(token) && token.length() >= 2;
    }

    private boolean containsSimilarToken(String nodeText, String targetToken) {
        for (String nodeToken : meaningfulTokens(nodeText)) {
            if (tokensMatch(nodeToken, targetToken)) {
                return true;
            }
        }
        return false;
    }

    private boolean tokensMatch(String nodeToken, String targetToken) {
        if (nodeToken.equals(targetToken)) {
            return true;
        }
        if (nodeToken.length() < 4 || targetToken.length() < 4) {
            return false;
        }
        return nodeToken.contains(targetToken) || targetToken.contains(nodeToken);
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
