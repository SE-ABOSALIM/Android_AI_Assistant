package com.example.anroidaiassistant.settings;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

import java.util.Locale;

public final class AssistantSettings {
    private static final String PREFS_NAME = "assistant_settings";
    private static final String KEY_LANGUAGE = "language";
    private static final String KEY_THEME = "theme";

    public static final String LANGUAGE_TR = "TR";
    public static final String LANGUAGE_EN = "EN";
    public static final String LANGUAGE_AR = "AR";

    public static final String THEME_SYSTEM = "system";
    public static final String THEME_LIGHT = "light";
    public static final String THEME_DARK = "dark";

    private AssistantSettings() {
    }

    public static String getLanguage(Context context) {
        return normalizeLanguage(prefs(context).getString(KEY_LANGUAGE, LANGUAGE_TR));
    }

    public static void setLanguage(Context context, String language) {
        prefs(context)
                .edit()
                .putString(KEY_LANGUAGE, normalizeLanguage(language))
                .apply();
    }

    public static String normalizeLanguage(String language) {
        if (language == null) {
            return LANGUAGE_TR;
        }

        String normalized = language.trim().toUpperCase(Locale.US);
        if (LANGUAGE_EN.equals(normalized) || LANGUAGE_AR.equals(normalized)) {
            return normalized;
        }

        return LANGUAGE_TR;
    }

    public static String getTheme(Context context) {
        return normalizeTheme(prefs(context).getString(KEY_THEME, THEME_SYSTEM));
    }

    public static void setTheme(Context context, String theme) {
        String normalizedTheme = normalizeTheme(theme);
        prefs(context)
                .edit()
                .putString(KEY_THEME, normalizedTheme)
                .apply();
        applyTheme(normalizedTheme);
    }

    public static void applySavedTheme(Context context) {
        applyTheme(getTheme(context));
    }

    public static void applyTheme(String theme) {
        switch (normalizeTheme(theme)) {
            case THEME_LIGHT:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case THEME_DARK:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            case THEME_SYSTEM:
            default:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
        }
    }

    private static String normalizeTheme(String theme) {
        if (THEME_LIGHT.equals(theme) || THEME_DARK.equals(theme)) {
            return theme;
        }
        return THEME_SYSTEM;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
