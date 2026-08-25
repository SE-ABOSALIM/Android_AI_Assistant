package com.example.anroidaiassistant.apps;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import com.example.anroidaiassistant.util.TextNormalizer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class InstalledAppReader {
    private static final String LAUNCHER_ACTION = Intent.ACTION_MAIN;
    private static final String LAUNCHER_CATEGORY = Intent.CATEGORY_LAUNCHER;

    private final LauncherQuery launcherQuery;

    public InstalledAppReader() {
        this(null);
    }

    InstalledAppReader(LauncherQuery launcherQuery) {
        this.launcherQuery = launcherQuery;
    }

    public List<LaunchableApp> getLaunchableApps(Context context) {
        if (launcherQuery != null) {
            List<LaunchableApp> apps = launcherQuery.query(LAUNCHER_ACTION, LAUNCHER_CATEGORY);
            return apps == null ? Collections.emptyList() : new ArrayList<>(apps);
        }
        if (context == null) {
            return Collections.emptyList();
        }

        PackageManager packageManager = context.getPackageManager();
        Intent mainIntent = new Intent(LAUNCHER_ACTION, null);
        mainIntent.addCategory(LAUNCHER_CATEGORY);
        List<ResolveInfo> resolvedApps = packageManager.queryIntentActivities(mainIntent, 0);
        List<LaunchableApp> launchableApps = new ArrayList<>();
        for (ResolveInfo resolvedApp : resolvedApps) {
            if (resolvedApp == null || resolvedApp.activityInfo == null) {
                continue;
            }
            String packageName = resolvedApp.activityInfo.packageName;
            CharSequence loadedLabel = resolvedApp.loadLabel(packageManager);
            String label = loadedLabel == null ? null : loadedLabel.toString();
            if (!TextNormalizer.hasText(packageName) || !TextNormalizer.hasText(label)) {
                continue;
            }
            launchableApps.add(new LaunchableApp(label, packageName));
        }
        return launchableApps;
    }

    interface LauncherQuery {
        List<LaunchableApp> query(String action, String category);
    }
}
