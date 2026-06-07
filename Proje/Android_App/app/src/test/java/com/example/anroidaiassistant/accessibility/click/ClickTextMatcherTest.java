package com.example.anroidaiassistant.accessibility.click;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

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

    @Test
    public void expandsSymbolIconAliases() {
        ClickIconAliasMatcher aliasMatcher = new ClickIconAliasMatcher();

        assertTrue(aliasMatcher.targetVariants("x isareti", "").contains("close"));
        assertTrue(aliasMatcher.targetVariants("carpi isareti", "").contains("close"));
        assertTrue(aliasMatcher.targetVariants("kamera isareti", "").contains("camera"));
        assertTrue(aliasMatcher.targetVariants("video icon", "").contains("video"));
        assertTrue(aliasMatcher.targetVariants("\u0639\u0644\u0627\u0645\u0647 \u0627\u0643\u0633", "").contains("close"));
    }

    @Test
    public void expandsCommonActionIconAliases() {
        ClickIconAliasMatcher aliasMatcher = new ClickIconAliasMatcher();

        assertTrue(aliasMatcher.targetVariants("uc noktaya", "").contains("more options"));
        assertTrue(aliasMatcher.targetVariants("three lines", "").contains("navigation drawer"));
        assertTrue(aliasMatcher.targetVariants("kalp isareti", "").contains("heart"));
        assertTrue(aliasMatcher.targetVariants("yorum isareti", "").contains("comment"));
        assertTrue(aliasMatcher.targetVariants("paylas isareti", "").contains("share"));
        assertTrue(aliasMatcher.targetVariants("arama isareti", "").contains("search"));
        assertTrue(aliasMatcher.targetVariants("mikrofon isareti", "").contains("microphone"));
        assertTrue(aliasMatcher.targetVariants("kagit tutucu", "").contains("attachment"));
        assertTrue(aliasMatcher.targetVariants("atac isareti", "").contains("paperclip"));
        assertTrue(aliasMatcher.targetVariants("emoji isareti", "").contains("emoji"));
        assertTrue(aliasMatcher.targetVariants("sepet isareti", "").contains("shopping cart"));
        assertTrue(aliasMatcher.targetVariants("favori isareti", "").contains("favorite"));
        assertTrue(aliasMatcher.targetVariants("\u062B\u0644\u0627\u062B \u0646\u0642\u0627\u0637", "").contains("more options"));
    }

    @Test
    public void expandsVideoCallWithoutSearchAliases() {
        ClickIconAliasMatcher aliasMatcher = new ClickIconAliasMatcher();

        List<String> variants = aliasMatcher.targetVariants("video arama isareti", "");

        assertTrue(variants.contains("video call"));
        assertFalse(variants.contains("search"));
    }

    @Test
    public void doesNotMatchSingleLetterTargetByContains() {
        ClickTextMatch notMatch = matcher.score("explore", Arrays.asList("x"));
        ClickTextMatch exactMatch = matcher.score("x", Arrays.asList("x"));

        assertEquals(0, notMatch.score);
        assertTrue(exactMatch.score >= 100);
    }
}
