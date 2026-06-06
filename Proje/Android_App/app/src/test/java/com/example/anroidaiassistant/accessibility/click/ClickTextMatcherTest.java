package com.example.anroidaiassistant.accessibility.click;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class ClickTextMatcherTest {
    private final ClickTextMatcher matcher = new ClickTextMatcher();

    @Test
    public void scoresExactMatchHighest() {
        ClickTextMatch match = matcher.score("home", Arrays.asList("home"));

        assertTrue(match.score >= 100);
    }

    @Test
    public void scoresCloseFuzzyMatch() {
        ClickTextMatch match = matcher.score("porty", Arrays.asList("porti"));

        assertTrue(match.score >= 66);
    }

    @Test
    public void expandsGlobalIconAliases() {
        ClickIconAliasMatcher aliasMatcher = new ClickIconAliasMatcher();

        List<String> variants = aliasMatcher.targetVariants("search", "");

        assertTrue(variants.contains("search"));
        assertTrue(variants.contains("arama"));
    }
}
