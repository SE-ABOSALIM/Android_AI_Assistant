package com.example.anroidaiassistant.apps;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import com.example.anroidaiassistant.util.TextNormalizer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class AppMatcher {
    public static final int MAX_APP_CHOICES = 5;

    private final InstalledAppReader installedAppReader;

    public AppMatcher() {
        this(new InstalledAppReader());
    }

    public AppMatcher(InstalledAppReader installedAppReader) {
        this.installedAppReader = installedAppReader;
    }

    public List<AppMatch> findAppMatches(Context context, String appName) {
        return findAppMatches(context, appName, false);
    }

    public List<AppMatch> findAppMatches(Context context, String appName, boolean spelledCandidate) {
        String candidateOriginalNormalized = normalizeText(appName);
        String candidateAscii = normalizeAsciiText(appName);
        String candidateCompact = normalizeAppCandidate(appName);
        if (!hasText(candidateCompact)) {
            return Collections.emptyList();
        }

        PackageManager packageManager = context.getPackageManager();
        List<ResolveInfo> installedApps = installedAppReader.getLaunchableApps(context);

        Map<String, AppMatch> exactMatches = new LinkedHashMap<>();
        List<AppMatch> fuzzyMatches = new ArrayList<>();
        float threshold = minimumAcceptableScore(candidateCompact, spelledCandidate);

        for (ResolveInfo app : installedApps) {
            String packageName = app.activityInfo.packageName;
            String label = app.loadLabel(packageManager).toString();
            AppMatch match = scoreAppMatch(
                    candidateCompact,
                    candidateAscii,
                    candidateOriginalNormalized,
                    label,
                    packageName
            );

            if (match.isExact()) {
                exactMatches.put(packageName, match);
            } else if (match.getScore() >= threshold) {
                fuzzyMatches.add(match);
            }
        }

        if (!exactMatches.isEmpty()) {
            return new ArrayList<>(exactMatches.values());
        }

        fuzzyMatches.sort(Comparator.comparing(AppMatch::getScore).reversed());
        return fuzzyMatches;
    }

    private AppMatch scoreAppMatch(
            String candidateCompact,
            String candidateAscii,
            String candidateOriginalNormalized,
            String appLabel,
            String packageName
    ) {
        String labelOriginalNormalized = normalizeText(appLabel);
        String labelAscii = normalizeAsciiText(appLabel);
        String labelCompact = labelAscii.replace(" ", "");
        String packageNormalized = normalizeText(packageName.replace('.', ' '));
        String packageCompact = packageNormalized.replace(" ", "");

        boolean exactLabelMatch = candidateCompact.equals(labelCompact);
        boolean exactPackageMatch = candidateCompact.equals(packageCompact);
        float score = exactLabelMatch ? 1.0f : (exactPackageMatch ? 0.98f : 0.0f);

        if (!candidateAscii.contains(" ") && tokenize(labelAscii).contains(candidateAscii)) {
            score = Math.max(score, 0.96f);
        }
        if (labelCompact.contains(candidateCompact) || candidateCompact.contains(labelCompact)) {
            score = Math.max(score, 0.92f - lengthPenalty(candidateCompact, labelCompact));
        }
        if (packageCompact.contains(candidateCompact) || candidateCompact.contains(packageCompact)) {
            score = Math.max(score, 0.82f - lengthPenalty(candidateCompact, packageCompact));
        }

        score = Math.max(score, levenshteinSimilarity(candidateCompact, labelCompact));
        score = Math.max(score, levenshteinSimilarity(candidateCompact, packageCompact) * 0.84f);
        score = Math.max(score, tokenOverlapScore(candidateAscii, labelAscii) * 0.90f);
        score = Math.max(score, tokenOverlapScore(candidateOriginalNormalized, labelOriginalNormalized) * 0.86f);

        return new AppMatch(appLabel, packageName, score, exactLabelMatch || exactPackageMatch);
    }

    private String normalizeText(String text) {
        return TextNormalizer.normalizeText(text);
    }

    private String normalizeAsciiText(String text) {
        return TextNormalizer.normalizeAsciiText(text);
    }

    private String normalizeAppCandidate(String text) {
        return normalizeAsciiText(text).replace(" ", "");
    }

    private float minimumAcceptableScore(String candidateCompact, boolean spelledCandidate) {
        int length = candidateCompact.length();
        if (spelledCandidate) {
            if (length <= 4) {
                return 0.80f;
            }
            if (length <= 7) {
                return 0.76f;
            }
            return 0.72f;
        }

        if (length <= 4) {
            return 0.95f;
        }
        if (length <= 7) {
            return 0.84f;
        }
        return 0.72f;
    }

    private float lengthPenalty(String left, String right) {
        return Math.min(0.12f, Math.abs(left.length() - right.length()) * 0.01f);
    }

    private float tokenOverlapScore(String leftText, String rightText) {
        Set<String> leftTokens = tokenize(leftText);
        Set<String> rightTokens = tokenize(rightText);
        if (leftTokens.isEmpty() || rightTokens.isEmpty()) {
            return 0.0f;
        }

        int overlap = 0;
        for (String token : leftTokens) {
            if (rightTokens.contains(token)) {
                overlap++;
            }
        }

        return (2.0f * overlap) / (leftTokens.size() + rightTokens.size());
    }

    private Set<String> tokenize(String text) {
        Set<String> tokens = new HashSet<>();
        if (text == null || text.trim().isEmpty()) {
            return tokens;
        }

        for (String token : normalizeText(text).split(" ")) {
            if (!token.isEmpty()) {
                tokens.add(token);
            }
        }
        return tokens;
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

    private boolean hasText(String value) {
        return TextNormalizer.hasText(value);
    }
}