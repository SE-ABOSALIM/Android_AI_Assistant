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
    public void scoresCloseNameTokenInsideLongNodeText() {
        ClickTextMatch match = matcher.score(
                "muhammed you tez yeni version docx",
                Arrays.asList("muhammad")
        );

        assertTrue(match.score > 0);
        assertTrue(match.score < 66);
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
        assertTrue(aliasMatcher.targetVariants("x icon", "").contains("close icon"));
        assertTrue(aliasMatcher.targetVariants("cross mark", "").contains("close"));
        assertTrue(aliasMatcher.targetVariants("\u0639\u0644\u0627\u0645\u0647 \u0627\u0643\u0633", "").contains("close"));
        assertTrue(aliasMatcher.targetVariants("\u0627\u064a\u0642\u0648\u0646\u0647 \u0627\u0643\u0633", "").contains("close"));
        assertTrue(aliasMatcher.targetVariants("kamera isareti", "").contains("camera"));
        assertTrue(aliasMatcher.targetVariants("video icon", "").contains("video"));
        assertTrue(aliasMatcher.targetVariants("\u0639\u0644\u0627\u0645\u0647 \u0627\u0643\u0633", "").contains("close"));
    }

    @Test
    public void expandsCommonActionIconAliases() {
        ClickIconAliasMatcher aliasMatcher = new ClickIconAliasMatcher();

        assertTrue(aliasMatcher.targetVariants("uc noktaya", "").contains("more options"));
        assertTrue(aliasMatcher.targetVariants("3 noktaya", "").contains("more options"));
        assertTrue(aliasMatcher.targetVariants("three lines", "").contains("navigation drawer"));
        assertTrue(aliasMatcher.targetVariants("3 cizgi", "").contains("navigation drawer"));
        assertTrue(aliasMatcher.targetVariants("3 cizgiye", "").contains("navigation drawer"));
        assertTrue(aliasMatcher.isDrawerTarget("3 cizgi"));
        assertTrue(aliasMatcher.targetVariants("kalp isareti", "").contains("heart"));
        assertTrue(aliasMatcher.targetVariants("kalbe", "").contains("favorite button"));
        assertTrue(aliasMatcher.targetVariants("kalp ikonuna", "").contains("heart icon"));
        assertTrue(aliasMatcher.targetVariants("yorum isareti", "").contains("comment"));
        assertTrue(aliasMatcher.targetVariants("paylas isareti", "").contains("share"));
        assertTrue(aliasMatcher.targetVariants("arama isareti", "").contains("search"));
        assertTrue(aliasMatcher.targetVariants("mikrofon isareti", "").contains("microphone"));
        assertTrue(aliasMatcher.targetVariants("mikrofona", "").contains("voice note"));
        assertTrue(aliasMatcher.targetVariants("mikrofona", "").contains("voice note btn"));
        assertTrue(aliasMatcher.targetVariants("kagit tutucu", "").contains("attachment"));
        assertTrue(aliasMatcher.targetVariants("atac isareti", "").contains("paperclip"));
        assertTrue(aliasMatcher.targetVariants("emoji isareti", "").contains("emoji"));
        assertTrue(aliasMatcher.targetVariants("sepet isareti", "").contains("shopping cart"));
        assertTrue(aliasMatcher.targetVariants("favori isareti", "").contains("favorite"));
        assertTrue(aliasMatcher.targetVariants("favoriye", "").contains("add to favorites"));
        assertTrue(aliasMatcher.targetVariants("\u062B\u0644\u0627\u062B \u0646\u0642\u0627\u0637", "").contains("more options"));
        assertTrue(aliasMatcher.targetVariants("\u0663 \u062E\u0637\u0648\u0637", "").contains("navigation drawer"));
    }

    @Test
    public void normalizesHeartSymbolsForIconMatching() {
        ClickIconAliasMatcher aliasMatcher = new ClickIconAliasMatcher();

        ClickTextMatch outlineHeart = matcher.score(
                ClickTextUtils.normalize("\u2661"),
                aliasMatcher.targetVariants("kalp", "")
        );
        ClickTextMatch filledHeart = matcher.score(
                ClickTextUtils.normalize("\u2665"),
                aliasMatcher.targetVariants("kalp", "")
        );

        assertTrue(outlineHeart.score >= 100);
        assertTrue(filledHeart.score >= 100);
    }

    @Test
    public void expandsDropdownArrowAliases() {
        ClickIconAliasMatcher aliasMatcher = new ClickIconAliasMatcher();

        assertTrue(aliasMatcher.targetVariants("kucuk ok", "").contains("dropdown arrow"));
        assertTrue(aliasMatcher.targetVariants("asagi ok isareti", "").contains("chevron down"));
        assertTrue(aliasMatcher.targetVariants("down arrow", "").contains("dropdown"));
        assertTrue(aliasMatcher.targetVariants("\u0633\u0647\u0645 \u0644\u0644\u0627\u0633\u0641\u0644", "").contains("dropdown arrow"));
        assertTrue(aliasMatcher.isDropdownTarget("kucuk ok"));
    }

    @Test
    public void expandsAdditionalCommonIconAliases() {
        ClickIconAliasMatcher aliasMatcher = new ClickIconAliasMatcher();

        assertTrue(aliasMatcher.targetVariants("ayarlar isareti", "").contains("settings"));
        assertTrue(aliasMatcher.targetVariants("profil ikonuna", "").contains("profile"));
        assertTrue(aliasMatcher.targetVariants("bildirim ziline", "").contains("bell"));
        assertTrue(aliasMatcher.targetVariants("kaydet ikonuna", "").contains("bookmark"));
        assertTrue(aliasMatcher.targetVariants("filtre isareti", "").contains("filter"));
        assertTrue(aliasMatcher.targetVariants("sirala ikonuna", "").contains("sort"));
        assertTrue(aliasMatcher.targetVariants("konum isareti", "").contains("location"));
        assertTrue(aliasMatcher.targetVariants("telefon ikonuna", "").contains("phone"));
        assertTrue(aliasMatcher.targetVariants("kalem isareti", "").contains("pencil"));
        assertTrue(aliasMatcher.targetVariants("cop kutusuna", "").contains("trash"));
        assertTrue(aliasMatcher.targetVariants("yenile ikonuna", "").contains("refresh"));
        assertTrue(aliasMatcher.targetVariants("indir ikonuna", "").contains("download"));
        assertTrue(aliasMatcher.targetVariants("bilgi isareti", "").contains("info"));
        assertTrue(aliasMatcher.targetVariants("onay isareti", "").contains("check"));
        assertTrue(aliasMatcher.targetVariants("dogru isareti", "").contains("check"));
        assertTrue(aliasMatcher.targetVariants("tik isareti", "").contains("tick"));
        assertTrue(aliasMatcher.targetVariants("check mark", "").contains("check"));
        assertTrue(aliasMatcher.targetVariants("tick icon", "").contains("tick"));
        assertTrue(aliasMatcher.targetVariants("correct mark", "").contains("check"));
        assertTrue(aliasMatcher.targetVariants("\u0639\u0644\u0627\u0645\u0647 \u0635\u062D", "").contains("check"));
        assertTrue(aliasMatcher.targetVariants("\u0627\u064a\u0642\u0648\u0646\u0647 \u0635\u062D", "").contains("check"));
        assertTrue(aliasMatcher.targetVariants("takvim ikonuna", "").contains("calendar"));
        assertTrue(aliasMatcher.targetVariants("saat ikonuna", "").contains("clock"));
        assertTrue(aliasMatcher.targetVariants("mesaj ikonuna", "").contains("message"));
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

    @Test
    public void keepsSingleSharedWordAsSuggestionOnlyForMultiWordTarget() {
        ClickTextMatch waffleMatch = matcher.score(
                "waffle tatlisi",
                Arrays.asList("tatli istemiyorum")
        );
        ClickTextMatch puddingMatch = matcher.score(
                "puding tatlisi",
                Arrays.asList("tatli istemiyorum")
        );

        assertTrue(waffleMatch.score > 0);
        assertTrue(puddingMatch.score > 0);
        assertTrue(waffleMatch.score < 66);
        assertTrue(puddingMatch.score < 66);
    }

    @Test
    public void scoresFullPhraseAndClosePhraseMatchesAboveSuggestionOnlyMatches() {
        ClickTextMatch exactMatch = matcher.score(
                "tatli istemiyorum",
                Arrays.asList("tatli istemiyorum")
        );
        ClickTextMatch phraseMatch = matcher.score(
                "sepetinizi goruntuleyin 3 urun",
                Arrays.asList("sepetinizi goruntuleyin")
        );
        ClickTextMatch closeMatch = matcher.score(
                "pudding tatlisi",
                Arrays.asList("buding tatlisi")
        );
        ClickTextMatch sharedWordOnlyMatch = matcher.score(
                "waffle tatlisi",
                Arrays.asList("buding tatlisi")
        );

        assertTrue(exactMatch.score >= 100);
        assertTrue(phraseMatch.score >= 74);
        assertTrue(closeMatch.score >= 66);
        assertTrue(closeMatch.score > sharedWordOnlyMatch.score);
    }

    @Test
    public void scoresSemanticResourceIdPhrasesForFavoriteIcons() {
        ClickIconAliasMatcher aliasMatcher = new ClickIconAliasMatcher();

        ClickTextMatch favoriteMatch = matcher.score(
                "btn add to favorites com trendyol id btn add to favorites",
                aliasMatcher.targetVariants("kalp", "")
        );
        ClickTextMatch wishlistMatch = matcher.score(
                "wishlist button",
                aliasMatcher.targetVariants("kalbe tikla", "")
        );

        assertTrue(favoriteMatch.score >= 82);
        assertTrue(wishlistMatch.score >= 100);
    }

    @Test
    public void scoresWhatsAppVoiceNoteResourceIdAsMicrophone() {
        ClickIconAliasMatcher aliasMatcher = new ClickIconAliasMatcher();

        ClickTextMatch match = matcher.score(
                "com whatsapp id voice note btn",
                aliasMatcher.targetVariants("mikrofona", "com.whatsapp")
        );

        assertTrue(match.score >= 66);
    }

    @Test
    public void treatsMoreActionsAsMenuOnlyWhenMenuIsRequested() {
        ClickIconAliasMatcher aliasMatcher = new ClickIconAliasMatcher();

        assertTrue(ClickCandidateCollector.isMenuActionMatchForNonMenuTarget(
                "more actions for do this to go from b1 to c2 english in 9 minutes",
                aliasMatcher.targetVariants("do this to go", "com.google.android.youtube")
        ));
        assertFalse(ClickCandidateCollector.isMenuActionMatchForNonMenuTarget(
                "more actions",
                aliasMatcher.targetVariants("three dots", "com.google.android.youtube")
        ));
    }
}
