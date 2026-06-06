package com.example.anroidaiassistant.accessibility.click;

import java.util.Set;

public final class ClickAppProfile {
    public void addProfileVariants(String packageName, String normalizedTarget, Set<String> variants) {
        String normalizedPackage = ClickTextUtils.normalize(packageName);
        if (normalizedPackage.contains("youtube")) {
            addYoutubeVariants(normalizedTarget, variants);
        }
        if (normalizedPackage.contains("instagram")) {
            addInstagramVariants(normalizedTarget, variants);
        }
        if (normalizedPackage.contains("whatsapp")) {
            addWhatsappVariants(normalizedTarget, variants);
        }
    }

    private void addYoutubeVariants(String target, Set<String> variants) {
        if (containsAny(target, "home", "anasayfa", "\u0627\u0644\u0631\u0626\u064A\u0633\u064A\u0647")) {
            add(variants, "home", "anasayfa", "\u0627\u0644\u0631\u0626\u064A\u0633\u064A\u0647");
        }
        if (containsAny(target, "short", "shorts")) {
            add(variants, "shorts");
        }
        if (containsAny(target, "subscription", "abonelik")) {
            add(variants, "subscriptions", "abonelikler");
        }
    }

    private void addInstagramVariants(String target, Set<String> variants) {
        if (containsAny(target, "home", "anasayfa", "\u0627\u0644\u0631\u0626\u064A\u0633\u064A\u0647")) {
            add(variants, "home", "anasayfa", "\u0627\u0644\u0631\u0626\u064A\u0633\u064A\u0647");
        }
        if (containsAny(target, "reels", "reel")) {
            add(variants, "reels", "reel");
        }
        if (containsAny(target, "profile", "profil")) {
            add(variants, "profile", "profil");
        }
    }

    private void addWhatsappVariants(String target, Set<String> variants) {
        if (containsAny(target, "chat", "sohbet")) {
            add(variants, "chats", "sohbetler");
        }
        if (containsAny(target, "call", "arama", "\u0627\u062A\u0635\u0627\u0644")) {
            add(variants, "calls", "aramalar", "\u0627\u062A\u0635\u0627\u0644\u0627\u062A");
        }
    }

    private boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            String normalized = ClickTextUtils.normalize(candidate);
            if (value.equals(normalized) || value.contains(normalized)) {
                return true;
            }
        }
        return false;
    }

    private void add(Set<String> variants, String... values) {
        for (String value : values) {
            String normalized = ClickTextUtils.normalize(value);
            if (!normalized.isEmpty()) {
                variants.add(normalized);
            }
        }
    }
}
