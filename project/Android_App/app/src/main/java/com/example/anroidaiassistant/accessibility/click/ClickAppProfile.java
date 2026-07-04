package com.example.anroidaiassistant.accessibility.click;

import com.example.anroidaiassistant.resources.ClickAppProfileAliases;

import java.util.Set;

public final class ClickAppProfile {
    public void addProfileVariants(String packageName, String normalizedTarget, Set<String> variants) {
        String normalizedPackage = ClickTextUtils.normalize(packageName);
        if (normalizedPackage.contains(ClickAppProfileAliases.YOUTUBE_PACKAGE_MARKER)) {
            addYoutubeVariants(normalizedTarget, variants);
        }
        if (normalizedPackage.contains(ClickAppProfileAliases.INSTAGRAM_PACKAGE_MARKER)) {
            addInstagramVariants(normalizedTarget, variants);
        }
        if (normalizedPackage.contains(ClickAppProfileAliases.WHATSAPP_PACKAGE_MARKER)) {
            addWhatsappVariants(normalizedTarget, variants);
        }
    }

    private void addYoutubeVariants(String target, Set<String> variants) {
        addIfMatched(target, variants, ClickAppProfileAliases.HOME_TARGETS, ClickAppProfileAliases.HOME_VARIANTS);
        addIfMatched(target, variants, ClickAppProfileAliases.YOUTUBE_SHORTS_TARGETS, ClickAppProfileAliases.YOUTUBE_SHORTS_VARIANTS);
        addIfMatched(target, variants, ClickAppProfileAliases.YOUTUBE_SUBSCRIPTION_TARGETS, ClickAppProfileAliases.YOUTUBE_SUBSCRIPTION_VARIANTS);
    }

    private void addInstagramVariants(String target, Set<String> variants) {
        addIfMatched(target, variants, ClickAppProfileAliases.HOME_TARGETS, ClickAppProfileAliases.HOME_VARIANTS);
        addIfMatched(target, variants, ClickAppProfileAliases.INSTAGRAM_REELS_TARGETS, ClickAppProfileAliases.INSTAGRAM_REELS_VARIANTS);
        addIfMatched(target, variants, ClickAppProfileAliases.INSTAGRAM_PROFILE_TARGETS, ClickAppProfileAliases.INSTAGRAM_PROFILE_VARIANTS);
    }

    private void addWhatsappVariants(String target, Set<String> variants) {
        addIfMatched(target, variants, ClickAppProfileAliases.WHATSAPP_CHAT_TARGETS, ClickAppProfileAliases.WHATSAPP_CHAT_VARIANTS);
        addIfMatched(target, variants, ClickAppProfileAliases.WHATSAPP_CALL_TARGETS, ClickAppProfileAliases.WHATSAPP_CALL_VARIANTS);
    }

    private void addIfMatched(String target, Set<String> variants, String[] targets, String[] values) {
        if (containsAny(target, targets)) {
            add(variants, values);
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
