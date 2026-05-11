package com.example.anroidaiassistant.session;

import java.util.Locale;
import java.util.UUID;

public final class AssistantSession {
    private static String currentSessionId;
    private static String catalogVersion;
    private static String catalogLanguage;

    private AssistantSession() {}

    public static synchronized String startNewSession() {
        currentSessionId = UUID.randomUUID().toString();
        catalogVersion = null;
        catalogLanguage = null;
        return currentSessionId;
    }

    public static synchronized String getSessionId() {
        return currentSessionId;
    }

    public static synchronized void setCatalogVersion(String version) {
        catalogVersion = version;
    }

    public static synchronized void setCatalogVersion(String version, String language) {
        catalogVersion = version;
        catalogLanguage = normalizeLanguage(language);
    }

    public static synchronized String getCatalogVersion() {
        return catalogVersion;
    }

    public static synchronized String getCatalogLanguage() {
        return catalogLanguage;
    }

    public static synchronized boolean isCatalogReadyForLanguage(String language) {
        if (currentSessionId == null || catalogVersion == null) {
            return false;
        }

        String normalizedLanguage = normalizeLanguage(language);
        if (normalizedLanguage == null) {
            return catalogLanguage == null;
        }
        return normalizedLanguage.equals(catalogLanguage);
    }

    public static synchronized String endSession() {
        String endedSessionId = currentSessionId;
        currentSessionId = null;
        catalogVersion = null;
        catalogLanguage = null;
        return endedSessionId;
    }

    private static String normalizeLanguage(String language) {
        if (language == null || language.trim().isEmpty()) {
            return null;
        }

        String normalized = language.trim().replace('_', '-').toUpperCase(Locale.US);
        if (normalized.startsWith("AR")) {
            return "AR";
        }
        if (normalized.startsWith("EN")) {
            return "EN";
        }
        if (normalized.startsWith("TR")) {
            return "TR";
        }
        return normalized;
    }
}