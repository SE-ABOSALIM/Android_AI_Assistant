package com.example.anroidaiassistant;

import com.example.anroidaiassistant.api.ApiService;
import com.example.anroidaiassistant.api.dto.AppCatalogEntry;
import com.example.anroidaiassistant.api.dto.AppCatalogRequest;
import com.example.anroidaiassistant.api.dto.AppCatalogResponse;
import com.example.anroidaiassistant.session.AssistantSession;
import com.example.anroidaiassistant.util.TextNormalizer;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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
        AppCatalogRequest request = new AppCatalogRequest(sessionId, language, catalogVersion, apps);

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
        if (apiService == null || !hasText(sessionId)) {
            return;
        }

        apiService.closeAppCatalog(sessionId).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {}

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                if (!call.isCanceled()) {
                    Log.w(TAG, "App catalog close request failed", t);
                }
            }
        });
    }

    private static List<AppCatalogEntry> collectLaunchableApps(Context context) {
        PackageManager packageManager = context.getPackageManager();
        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> installedApps = packageManager.queryIntentActivities(mainIntent, 0);

        List<AppCatalogEntry> apps = new ArrayList<>();
        for (ResolveInfo app : installedApps) {
            String packageName = app.activityInfo == null ? null : app.activityInfo.packageName;
            String label = app.loadLabel(packageManager).toString();
            if (!hasText(packageName) || !hasText(label)) {
                continue;
            }
            apps.add(new AppCatalogEntry(label, packageName, buildAliases(label, packageName)));
        }

        apps.sort(Comparator.comparing(AppCatalogEntry::getPackageName));
        return apps;
    }

    private static List<String> buildAliases(String label, String packageName) {
        Set<String> aliases = new LinkedHashSet<>();
        addAlias(aliases, label);
        addAlias(aliases, normalizeAsciiText(label));
        addAlias(aliases, normalizeAsciiText(label).replace(" ", ""));

        String packageTail = packageName.substring(packageName.lastIndexOf('.') + 1);
        addAlias(aliases, packageTail);
        addAlias(aliases, normalizeAsciiText(packageTail));
        return new ArrayList<>(aliases);
    }

    private static void addAlias(Set<String> aliases, String alias) {
        if (hasText(alias)) {
            aliases.add(alias.trim());
        }
    }

    private static String buildCatalogVersion(List<AppCatalogEntry> apps) {
        List<String> parts = new ArrayList<>();
        for (AppCatalogEntry app : apps) {
            parts.add(app.getPackageName() + ":" + app.getLabel());
        }
        Collections.sort(parts);
        return apps.size() + "-" + Integer.toHexString(parts.toString().hashCode());
    }

    private static String normalizeText(String text) {
        return TextNormalizer.normalizeText(text);
    }

    private static String toAsciiTurkish(String text) {
        return TextNormalizer.toAsciiTurkish(text);
    }

    private static String normalizeAsciiText(String text) {
        return TextNormalizer.normalizeAsciiText(text);
    }

    private static void notifyCallback(SyncCallback callback, boolean success, String message) {
        if (callback != null) {
            callback.onComplete(success, message);
        }
    }

    private static boolean hasText(String value) {
        return TextNormalizer.hasText(value);
    }
}
