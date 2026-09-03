package com.example.anroidaiassistant.accessibility.click;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ClickCandidateRankingPolicyTest {
    @Test
    public void exactMatchSelectsDirectlyOverIncidentalContains() {
        assertTrue(ClickCandidateRankingPolicy.shouldSelectDirect(
                ClickMatchClass.EXACT,
                114,
                ClickMatchClass.CONTAINS,
                110,
                80,
                12
        ));
        assertFalse(ClickCandidateRankingPolicy.isFallbackPeer(
                ClickMatchClass.EXACT,
                114,
                ClickMatchClass.CONTAINS,
                110,
                58,
                12
        ));
    }

    @Test
    public void genuineExactMatchAmbiguityRemains() {
        assertFalse(ClickCandidateRankingPolicy.shouldSelectDirect(
                ClickMatchClass.EXACT,
                120,
                ClickMatchClass.EXACT,
                120,
                80,
                12
        ));
        assertTrue(ClickCandidateRankingPolicy.isFallbackPeer(
                ClickMatchClass.EXACT,
                120,
                ClickMatchClass.EXACT,
                120,
                58,
                12
        ));
    }

    @Test
    public void primaryMatchClassPrecedesSecondaryScore() {
        assertTrue(ClickCandidateRankingPolicy.compareBestFirst(
                ClickMatchClass.EXACT,
                114,
                ClickMatchClass.CONTAINS,
                120
        ) < 0);
        assertTrue(ClickCandidateRankingPolicy.compareBestFirst(
                ClickMatchClass.EXACT,
                122,
                ClickMatchClass.EXACT,
                118
        ) < 0);
    }
}
