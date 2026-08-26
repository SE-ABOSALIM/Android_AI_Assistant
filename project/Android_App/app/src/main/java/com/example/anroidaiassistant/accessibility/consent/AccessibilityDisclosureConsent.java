package com.example.anroidaiassistant.accessibility.consent;

import android.content.Context;
import android.content.SharedPreferences;

public final class AccessibilityDisclosureConsent implements AccessibilityConsentState {
    public static final int CURRENT_VERSION = 2;

    private static final String PREFERENCES_NAME = "accessibility_disclosure_consent";
    private static final String ACCEPTED_VERSION_KEY = "accepted_version";

    private final VersionStore versionStore;

    public AccessibilityDisclosureConsent(Context context) {
        Context applicationContext = context.getApplicationContext();
        SharedPreferences preferences = applicationContext.getSharedPreferences(
                PREFERENCES_NAME,
                Context.MODE_PRIVATE
        );
        versionStore = new SharedPreferencesVersionStore(preferences);
    }

    AccessibilityDisclosureConsent(VersionStore versionStore) {
        this.versionStore = versionStore;
    }

    @Override
    public boolean hasCurrentConsent() {
        return versionStore.readAcceptedVersion() == CURRENT_VERSION;
    }

    @Override
    public void recordCurrentConsent() {
        versionStore.writeAcceptedVersion(CURRENT_VERSION);
    }

    interface VersionStore {
        int readAcceptedVersion();

        void writeAcceptedVersion(int version);
    }

    private static final class SharedPreferencesVersionStore implements VersionStore {
        private final SharedPreferences preferences;

        private SharedPreferencesVersionStore(SharedPreferences preferences) {
            this.preferences = preferences;
        }

        @Override
        public int readAcceptedVersion() {
            return preferences.getInt(ACCEPTED_VERSION_KEY, 0);
        }

        @Override
        public void writeAcceptedVersion(int version) {
            preferences.edit().putInt(ACCEPTED_VERSION_KEY, version).apply();
        }
    }
}
