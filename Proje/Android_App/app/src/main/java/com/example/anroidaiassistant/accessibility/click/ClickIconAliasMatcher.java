package com.example.anroidaiassistant.accessibility.click;

import com.example.anroidaiassistant.util.TextNormalizer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ClickIconAliasMatcher {
    private final ClickAppProfile appProfile = new ClickAppProfile();

    public List<String> targetVariants(String targetText, String packageName) {
        Set<String> variants = new LinkedHashSet<>();
        String normalizedTarget = ClickTextUtils.normalize(targetText);
        addVariant(variants, normalizedTarget);

        addGlobalIconAliases(normalizedTarget, variants);
        appProfile.addProfileVariants(packageName, normalizedTarget, variants);

        return new ArrayList<>(variants);
    }

    public boolean isGenericListTarget(String value) {
        return isAny(
                value,
                "option",
                "item",
                "result",
                "video",
                "device",
                "row",
                "secenek",
                "sonuc",
                "cihaz",
                "\u062E\u064A\u0627\u0631",
                "\u0646\u062A\u064A\u062C\u0647",
                "\u0641\u064A\u062F\u064A\u0648",
                "\u062C\u0647\u0627\u0632"
        );
    }

    private void addGlobalIconAliases(String normalizedTarget, Set<String> variants) {
        if (isAny(normalizedTarget, "arti", "plus", "add", "ekle", "olustur", "create", "new")) {
            addVariants(variants, "arti", "plus", "add", "ekle", "olustur", "create", "new", "post", "+");
        }
        if (isAny(normalizedTarget, "ara", "arama", "search", "\u0628\u062D\u062B", "\u0627\u0644\u0628\u062D\u062B")) {
            addVariants(variants, "ara", "arama", "search", "\u0628\u062D\u062B", "\u0627\u0628\u062D\u062B");
        }
        if (isAny(normalizedTarget, "gonder", "send", "\u0627\u0631\u0633\u0644")) {
            addVariants(variants, "gonder", "send", "\u0627\u0631\u0633\u0644", "share", "paylas");
        }
        if (isAny(normalizedTarget, "geri", "back", "\u0631\u062C\u0648\u0639", "\u062E\u0644\u0641")) {
            addVariants(variants, "geri", "back", "navigate up", "\u0631\u062C\u0648\u0639", "\u062E\u0644\u0641");
        }
        if (isAny(normalizedTarget, "devam", "continue", "next", "ileri")) {
            addVariants(variants, "devam", "continue", "next", "ileri");
        }
        if (isAny(normalizedTarget, "home", "anasayfa", "\u0627\u0644\u0631\u0626\u064A\u0633\u064A\u0647")) {
            addVariants(variants, "home", "anasayfa", "\u0627\u0644\u0631\u0626\u064A\u0633\u064A\u0647");
        }
        if (isAny(normalizedTarget, "more", "menu", "options", "secenek", "ayar")) {
            addVariants(variants, "more", "menu", "options", "secenekler", "ayarlar");
        }
        if (isAny(normalizedTarget, "like", "begeni", "kalp")) {
            addVariants(variants, "like", "begeni", "kalp");
        }
    }

    private boolean isAny(String value, String... candidates) {
        for (String candidate : candidates) {
            String normalizedCandidate = ClickTextUtils.normalize(candidate);
            if (value.equals(normalizedCandidate) || value.contains(normalizedCandidate)) {
                return true;
            }
        }
        return false;
    }

    private void addVariants(Set<String> variants, String... values) {
        Arrays.stream(values).forEach(value -> addVariant(variants, value));
    }

    private void addVariant(Set<String> variants, String value) {
        String normalized = ClickTextUtils.normalize(value);
        if (TextNormalizer.hasText(normalized)) {
            variants.add(normalized);
        }
    }
}
