package com.example.anroidaiassistant.accessibility.click;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class CanonicalTargetDeduplicatorTest {
    private static final int CLICK = CanonicalTargetDeduplicator.ACTION_ACTIVATE;

    @Test
    public void sameActionNodeFromMultipleSourcesBecomesOneCandidate() {
        Object action = new Object();
        FakeCandidate weak = candidate(action, Collections.emptyList(), "month", CLICK, 0, 0, 100, 40, 100);
        FakeCandidate strong = candidate(action, Collections.emptyList(), "month", CLICK, 2, 1, 102, 41, 120);

        List<FakeCandidate> deduped = deduplicate(weak, strong);

        assertEquals(1, deduped.size());
        assertSame(strong, deduped.get(0));
    }

    @Test
    public void labelAndControlResolvingToSameActionBecomeOneCandidate() {
        Object field = new Object();
        FakeCandidate label = candidate(field, Collections.emptyList(), "gender", CLICK, 0, 0, 120, 50, 118);
        FakeCandidate control = candidate(field, Collections.emptyList(), "gender", CLICK, 0, 0, 120, 50, 122);

        assertEquals(1, deduplicate(label, control).size());
    }

    @Test
    public void nestedSemanticWrappersWithCompatibleActionsBecomeOneCandidate() {
        Object parent = new Object();
        Object child = new Object();
        FakeCandidate parentCandidate = candidate(
                parent,
                Collections.emptyList(),
                "back",
                CLICK,
                0,
                0,
                100,
                100,
                120
        );
        FakeCandidate childCandidate = candidate(
                child,
                Collections.singletonList(parent),
                "back",
                CLICK,
                10,
                10,
                90,
                90,
                122
        );

        List<FakeCandidate> deduped = deduplicate(parentCandidate, childCandidate);

        assertEquals(1, deduped.size());
        assertSame(childCandidate, deduped.get(0));
    }

    @Test
    public void distinctControlsWithEqualBoundsRemainDistinct() {
        FakeCandidate first = candidate(new Object(), Collections.emptyList(), "continue", CLICK, 0, 0, 100, 40, 120);
        FakeCandidate second = candidate(new Object(), Collections.emptyList(), "continue", CLICK, 0, 0, 100, 40, 120);

        assertEquals(2, deduplicate(first, second).size());
    }

    @Test
    public void sameActionIdentityIgnoresSmallBoundsDifferences() {
        String firstWrapper = new String("stable-node");
        String secondWrapper = new String("stable-node");
        FakeCandidate first = candidate(firstWrapper, Collections.emptyList(), "month", CLICK, 0, 0, 100, 40, 118);
        FakeCandidate second = candidate(secondWrapper, Collections.emptyList(), "month", CLICK, 1, 0, 101, 40, 120);

        assertEquals(1, deduplicate(first, second).size());
    }

    @Test
    public void nestedButSemanticallyDifferentActionsRemainDistinct() {
        Object parent = new Object();
        Object child = new Object();
        FakeCandidate container = candidate(parent, Collections.emptyList(), "account", CLICK, 0, 0, 120, 80, 120);
        FakeCandidate button = candidate(child, Collections.singletonList(parent), "continue", CLICK, 20, 20, 100, 60, 120);

        assertEquals(2, deduplicate(container, button).size());
    }

    @Test
    public void iconAliasesOnOneActionRemainOneLogicalCandidate() {
        Object backAction = new Object();
        FakeCandidate contentDescription = candidate(backAction, Collections.emptyList(), "back", CLICK, 0, 0, 48, 48, 122);
        FakeCandidate resourceId = candidate(backAction, Collections.emptyList(), "navigate up", CLICK, 0, 0, 48, 48, 120);

        assertEquals(1, deduplicate(contentDescription, resourceId).size());
    }

    @Test
    public void nestedExactAliasesSharingOneMatchFamilyBecomeOneCandidate() {
        Object parent = new Object();
        Object child = new Object();
        FakeCandidate back = candidateWithSemantics(
                parent,
                Collections.emptyList(),
                Arrays.asList("back", "exact target family back arrow"),
                CLICK,
                0,
                0,
                100,
                100,
                120
        );
        FakeCandidate navigateUp = candidateWithSemantics(
                child,
                Collections.singletonList(parent),
                Arrays.asList("navigate up", "exact target family back arrow"),
                CLICK,
                10,
                10,
                90,
                90,
                122
        );

        assertEquals(1, deduplicate(back, navigateUp).size());
    }

    private List<FakeCandidate> deduplicate(FakeCandidate... candidates) {
        List<FakeCandidate> result = new ArrayList<>();
        for (FakeCandidate candidate : candidates) {
            CanonicalTargetDeduplicator.addOrReplace(
                    result,
                    candidate,
                    value -> value.identity,
                    (replacement, existing) -> replacement.score > existing.score
            );
        }
        return result;
    }

    private FakeCandidate candidate(
            Object node,
            List<Object> ancestors,
            String semanticValue,
            int actions,
            int left,
            int top,
            int right,
            int bottom,
            int score
    ) {
        return candidateWithSemantics(
                node,
                ancestors,
                Arrays.asList(semanticValue),
                actions,
                left,
                top,
                right,
                bottom,
                score
        );
    }

    private FakeCandidate candidateWithSemantics(
            Object node,
            List<Object> ancestors,
            List<String> semanticValues,
            int actions,
            int left,
            int top,
            int right,
            int bottom,
            int score
    ) {
        CanonicalTargetDeduplicator.Identity<Object> identity =
                new CanonicalTargetDeduplicator.Identity<>(
                        node,
                        ancestors,
                        semanticValues,
                        actions,
                        left,
                        top,
                        right,
                        bottom
                );
        return new FakeCandidate(identity, score);
    }

    private static final class FakeCandidate {
        final CanonicalTargetDeduplicator.Identity<Object> identity;
        final int score;

        FakeCandidate(CanonicalTargetDeduplicator.Identity<Object> identity, int score) {
            this.identity = identity;
            this.score = score;
        }
    }
}
