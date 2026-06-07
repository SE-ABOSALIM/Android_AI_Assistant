package com.example.anroidaiassistant.accessibility.click;

import com.example.anroidaiassistant.resources.ClickIconAliases;
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
        return isAny(value, ClickIconAliases.GENERIC_LIST_TARGETS);
    }

    private void addGlobalIconAliases(String normalizedTarget, Set<String> variants) {
        boolean videoCallTarget = isVideoCallTarget(normalizedTarget);

        addIfMatched(normalizedTarget, variants, ClickIconAliases.PLUS_TARGETS, ClickIconAliases.PLUS_VARIANTS);
        addIfMatched(normalizedTarget, variants, ClickIconAliases.CLOSE_TARGETS, ClickIconAliases.CLOSE_VARIANTS);
        if (videoCallTarget) {
            addVariants(variants, ClickIconAliases.VIDEO_CALL_VARIANTS);
        }
        if (!videoCallTarget) {
            addIfMatched(
                    normalizedTarget,
                    variants,
                    ClickIconAliases.SEARCH_TARGETS,
                    ClickIconAliases.SEARCH_VARIANTS
            );
        }
        addIfMatched(normalizedTarget, variants, ClickIconAliases.CAMERA_TARGETS, ClickIconAliases.CAMERA_VARIANTS);
        if (!videoCallTarget) {
            addIfMatched(
                    normalizedTarget,
                    variants,
                    ClickIconAliases.VIDEO_TARGETS,
                    ClickIconAliases.VIDEO_VARIANTS
            );
        }
        addIfMatched(normalizedTarget, variants, ClickIconAliases.SEND_TARGETS, ClickIconAliases.SEND_VARIANTS);
        addIfMatched(normalizedTarget, variants, ClickIconAliases.MICROPHONE_TARGETS, ClickIconAliases.MICROPHONE_VARIANTS);
        addIfMatched(normalizedTarget, variants, ClickIconAliases.ATTACHMENT_TARGETS, ClickIconAliases.ATTACHMENT_VARIANTS);
        addIfMatched(normalizedTarget, variants, ClickIconAliases.EMOJI_TARGETS, ClickIconAliases.EMOJI_VARIANTS);
        addIfMatched(normalizedTarget, variants, ClickIconAliases.BACK_TARGETS, ClickIconAliases.BACK_VARIANTS);
        addIfMatched(normalizedTarget, variants, ClickIconAliases.CONTINUE_TARGETS, ClickIconAliases.CONTINUE_VARIANTS);
        addIfMatched(normalizedTarget, variants, ClickIconAliases.HOME_TARGETS, ClickIconAliases.HOME_VARIANTS);
        addIfMatched(normalizedTarget, variants, ClickIconAliases.MORE_TARGETS, ClickIconAliases.MORE_VARIANTS);
        addIfMatched(normalizedTarget, variants, ClickIconAliases.DRAWER_TARGETS, ClickIconAliases.DRAWER_VARIANTS);
        addIfMatched(normalizedTarget, variants, ClickIconAliases.LIKE_TARGETS, ClickIconAliases.LIKE_VARIANTS);
        addIfMatched(normalizedTarget, variants, ClickIconAliases.COMMENT_TARGETS, ClickIconAliases.COMMENT_VARIANTS);
        addIfMatched(normalizedTarget, variants, ClickIconAliases.CART_TARGETS, ClickIconAliases.CART_VARIANTS);
    }

    private boolean isVideoCallTarget(String normalizedTarget) {
        return isAny(normalizedTarget, ClickIconAliases.VIDEO_CALL_TARGETS);
    }

    private void addIfMatched(
            String normalizedTarget,
            Set<String> variants,
            String[] targets,
            String[] values
    ) {
        if (isAny(normalizedTarget, targets)) {
            addVariants(variants, values);
        }
    }

    private boolean isAny(String value, String... candidates) {
        for (String candidate : candidates) {
            String normalizedCandidate = ClickTextUtils.normalize(candidate);
            if (matchesCandidate(value, normalizedCandidate)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesCandidate(String value, String candidate) {
        if (!TextNormalizer.hasText(candidate)) {
            return false;
        }
        if (value.equals(candidate)) {
            return true;
        }
        if (candidate.replace(" ", "").length() <= 2) {
            return containsToken(value, candidate);
        }
        return value.contains(candidate);
    }

    private boolean containsToken(String value, String candidate) {
        for (String token : value.split(" ")) {
            if (token.equals(candidate)) {
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
