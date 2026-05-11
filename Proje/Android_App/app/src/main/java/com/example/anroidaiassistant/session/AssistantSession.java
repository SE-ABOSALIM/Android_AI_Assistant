package com.example.anroidaiassistant.session;

import java.util.UUID;

public final class AssistantSession {
    private static String currentSessionId;
    private static String catalogVersion;

    private AssistantSession() {}

    public static synchronized String startNewSession() {
        currentSessionId = UUID.randomUUID().toString();
        catalogVersion = null;
        return currentSessionId;
    }

    public static synchronized String getSessionId() {
        return currentSessionId;
    }

    public static synchronized void setCatalogVersion(String version) {
        catalogVersion = version;
    }

    public static synchronized String getCatalogVersion() {
        return catalogVersion;
    }

    public static synchronized String endSession() {
        String endedSessionId = currentSessionId;
        currentSessionId = null;
        catalogVersion = null;
        return endedSessionId;
    }
}
