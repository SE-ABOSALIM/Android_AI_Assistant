package com.example.anroidaiassistant.apps;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.util.Log;

import com.example.anroidaiassistant.executor.CommandExecutionContext;
import com.example.anroidaiassistant.util.TextNormalizer;

import java.util.Locale;

public final class AppLauncher {
    private static final String TAG = "AppLauncher";

    public boolean launchPackage(
            Context context,
            String packageName,
            String label,
            CommandExecutionContext executionContext
    ) {
        PackageManager packageManager = context.getPackageManager();
        Intent intent = packageManager.getLaunchIntentForPackage(packageName);
        if (intent == null) {
            executionContext.showMessage("App not found. Please spell the app name.");
            return false;
        }

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
            return true;
        } catch (Exception exception) {
            Log.e(TAG, "Failed to launch app: " + packageName, exception);
            executionContext.showMessage("Could not open " + firstNonEmpty(label, "app"));
            return false;
        }
    }

    public Drawable getAppIcon(Context context, String packageName) {
        if (!hasText(packageName)) {
            return null;
        }

        try {
            return context.getPackageManager().getApplicationIcon(packageName);
        } catch (Exception exception) {
            Log.w(TAG, "Could not load app icon: " + packageName, exception);
            return null;
        }
    }

    public String buildAppChoiceSubtitle(String packageName) {
        String source = inferReadableAppSource(packageName);
        return hasText(source) ? source : "Installed app";
    }

    private String inferReadableAppSource(String packageName) {
        if (!hasText(packageName)) {
            return "";
        }

        String normalized = packageName.toLowerCase(Locale.US);
        if (normalized.startsWith("com.google.") || normalized.contains(".google.")) {
            return "Google";
        }
        if (normalized.startsWith("com.microsoft.")
                || normalized.startsWith("com.azure.")
                || normalized.contains(".microsoft.")
                || normalized.contains(".azure.")) {
            return "Microsoft";
        }
        if (normalized.startsWith("com.facebook.")
                || normalized.startsWith("com.instagram.")
                || normalized.startsWith("com.whatsapp.")) {
            return "Meta";
        }
        if (normalized.startsWith("org.telegram.") || normalized.contains(".telegram.")) {
            return "Telegram";
        }
        if (normalized.startsWith("com.spotify.")) {
            return "Spotify";
        }
        if (normalized.startsWith("com.netflix.")) {
            return "Netflix";
        }
        if (normalized.contains("turktelekom") || normalized.contains("turk.telekom")) {
            return "Turk Telekom";
        }
        if (normalized.startsWith("com.android.")) {
            return "System";
        }
        return "";
    }

    private String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return null;
    }

    private boolean hasText(String value) {
        return TextNormalizer.hasText(value);
    }
}