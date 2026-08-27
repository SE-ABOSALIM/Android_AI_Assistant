package com.example.anroidaiassistant;

import com.example.anroidaiassistant.api.ApiService;
import com.example.anroidaiassistant.api.dto.AppCatalogEntry;
import com.example.anroidaiassistant.api.dto.AppCatalogRequest;
import com.example.anroidaiassistant.api.dto.AppCatalogResponse;
import com.example.anroidaiassistant.apps.InstalledAppReader;
import com.example.anroidaiassistant.apps.LaunchableApp;
import com.example.anroidaiassistant.session.AssistantSession;
import com.example.anroidaiassistant.util.DeviceIdentity;
import com.example.anroidaiassistant.util.TextNormalizer;

import android.content.Context;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public final class AppCatalogSyncer {
    private static final String TAG = "AppCatalogSyncer";

    private AppCatalogSyncer() {}

    public interface SyncCallback {
        void onComplete(boolean success, String message);
    }

    public static Call<AppCatalogResponse> syncInstalledApps(
            Context context,
            ApiService apiService,
            String sessionId,
            String language,
            SyncCallback callback
    ) {
        if (context == null || apiService == null || !hasText(sessionId)) {
            notifyCallback(callback, false, "App catalog sync is unavailable");
            return null;
        }

        List<AppCatalogEntry> apps = collectLaunchableApps(context);
        String catalogVersion = buildCatalogVersion(apps);
        String deviceId = DeviceIdentity.getDeviceId(context);
        AppCatalogRequest request = new AppCatalogRequest(sessionId, deviceId, language, catalogVersion, apps);

        Call<AppCatalogResponse> call = apiService.syncAppCatalog(request);
        call.enqueue(new Callback<AppCatalogResponse>() {
            @Override
            public void onResponse(Call<AppCatalogResponse> call, Response<AppCatalogResponse> response) {
                if (call.isCanceled()) {
                    return;
                }

                if (response.isSuccessful() && response.body() != null && response.body().isAccepted()) {
                    AssistantSession.setCatalogVersion(response.body().getCatalogVersion(), language);
                    notifyCallback(callback, true, "App catalog synced");
                    return;
                }

                Log.e(TAG, "App catalog sync failed. httpCode=" + response.code());
                notifyCallback(callback, false, "Could not sync app list");
            }

            @Override
            public void onFailure(Call<AppCatalogResponse> call, Throwable t) {
                if (call.isCanceled()) {
                    return;
                }

                Log.e(TAG, "App catalog sync request failed", t);
                notifyCallback(callback, false, "Backend unavailable");
            }
        });
        return call;
    }

    public static void closeSession(ApiService apiService, String sessionId) {
        // App catalog data is device-scoped and persistent in the backend database.
        // Stopping the assistant only ends the local listening session; it must not
        // delete the installed app catalog.
    }

    private static List<AppCatalogEntry> collectLaunchableApps(Context context) {
        return buildCatalogEntries(new InstalledAppReader().getLaunchableApps(context));
    }

    static String getInstalledCatalogVersion(Context context) {
        if (context == null) {
            return null;
        }
        return buildCatalogVersion(collectLaunchableApps(context));
    }

    static boolean requiresCatalogSync(
            boolean accepted,
            boolean available,
            String backendCatalogVersion,
            String backendLanguage,
            String installedCatalogVersion,
            String selectedLanguage
    ) {
        if (!accepted || !available) {
            return true;
        }
        if (!hasText(backendCatalogVersion)
                || !backendCatalogVersion.equals(installedCatalogVersion)) {
            return true;
        }
        return !normalizeLanguage(backendLanguage).equals(normalizeLanguage(selectedLanguage));
    }

    static List<AppCatalogEntry> buildCatalogEntries(List<LaunchableApp> installedApps) {
        List<AppCatalogEntry> apps = new ArrayList<>();
        if (installedApps == null) {
            return apps;
        }
        for (LaunchableApp app : installedApps) {
            String packageName = app == null ? null : app.getPackageName();
            String label = app == null ? null : app.getLabel();
            if (!hasText(packageName) || !hasText(label)) {
                continue;
            }
            apps.add(new AppCatalogEntry(label, packageName, Collections.emptyList()));
        }

        apps.sort(Comparator.comparing(AppCatalogEntry::getPackageName));
        return apps;
    }

    private static String buildCatalogVersion(List<AppCatalogEntry> apps) {
        List<String> parts = new ArrayList<>();
        for (AppCatalogEntry app : apps) {
            parts.add(app.getPackageName() + ":" + app.getLabel());
        }
        Collections.sort(parts);
        return apps.size() + "-" + Integer.toHexString(parts.toString().hashCode());
    }

    private static void notifyCallback(SyncCallback callback, boolean success, String message) {
        if (callback != null) {
            callback.onComplete(success, message);
        }
    }

    private static boolean hasText(String value) {
        return TextNormalizer.hasText(value);
    }

    private static String normalizeLanguage(String language) {
        return hasText(language) ? language.trim().toUpperCase(Locale.ROOT) : "";
    }
}
