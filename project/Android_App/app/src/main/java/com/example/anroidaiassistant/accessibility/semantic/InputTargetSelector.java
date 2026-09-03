package com.example.anroidaiassistant.accessibility.semantic;

import com.example.anroidaiassistant.accessibility.click.ClickTextMatch;
import com.example.anroidaiassistant.accessibility.click.ClickTextMatcher;
import com.example.anroidaiassistant.accessibility.click.ClickTextUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public final class InputTargetSelector<N> {
    private static final int MIN_MATCH_SCORE = 58;
    private static final int AMBIGUITY_SCORE_GAP = 12;

    private final ClickTextMatcher textMatcher;

    public InputTargetSelector(ClickTextMatcher textMatcher) {
        this.textMatcher = textMatcher;
    }

    public List<N> selectMatches(
            List<N> inputs,
            List<String> targetVariants,
            Function<N, List<String>> semanticValues
    ) {
        List<ScoredInput<N>> scored = new ArrayList<>();
        int topScore = 0;
        for (N input : inputs) {
            int score = bestScore(semanticValues.apply(input), targetVariants);
            if (score >= MIN_MATCH_SCORE) {
                scored.add(new ScoredInput<>(input, score));
                topScore = Math.max(topScore, score);
            }
        }

        List<N> matches = new ArrayList<>();
        for (ScoredInput<N> candidate : scored) {
            if (topScore - candidate.score <= AMBIGUITY_SCORE_GAP) {
                matches.add(candidate.input);
            }
        }
        return matches;
    }

    private int bestScore(List<String> values, List<String> targetVariants) {
        int best = 0;
        if (values == null) {
            return best;
        }
        for (String value : values) {
            ClickTextMatch match = textMatcher.score(
                    ClickTextUtils.normalize(value),
                    targetVariants
            );
            best = Math.max(best, match.score);
        }
        return best;
    }

    private static final class ScoredInput<N> {
        final N input;
        final int score;

        ScoredInput(N input, int score) {
            this.input = input;
            this.score = score;
        }
    }
}
