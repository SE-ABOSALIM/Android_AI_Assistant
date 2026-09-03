package com.example.anroidaiassistant.accessibility.click;

final class ClickCandidateRankingPolicy {
    private ClickCandidateRankingPolicy() {
    }

    static int compareBestFirst(
            ClickMatchClass firstClass,
            int firstScore,
            ClickMatchClass secondClass,
            int secondScore
    ) {
        int classComparison = Integer.compare(priority(secondClass), priority(firstClass));
        if (classComparison != 0) {
            return classComparison;
        }
        return Integer.compare(secondScore, firstScore);
    }

    static boolean shouldSelectDirect(
            ClickMatchClass topClass,
            int topScore,
            ClickMatchClass secondClass,
            int secondScore,
            int minimumScore,
            int scoreMargin
    ) {
        if (topScore < minimumScore) {
            return false;
        }
        int topPriority = priority(topClass);
        int secondPriority = priority(secondClass);
        if (topPriority != secondPriority) {
            return topPriority > secondPriority;
        }
        return topScore - secondScore >= scoreMargin;
    }

    static boolean isFallbackPeer(
            ClickMatchClass topClass,
            int topScore,
            ClickMatchClass candidateClass,
            int candidateScore,
            int minimumScore,
            int maximumScoreGap
    ) {
        return candidateScore >= minimumScore
                && priority(candidateClass) == priority(topClass)
                && (topScore <= 0 || topScore - candidateScore <= maximumScoreGap);
    }

    private static int priority(ClickMatchClass matchClass) {
        return matchClass == null ? ClickMatchClass.NONE.priority() : matchClass.priority();
    }
}
