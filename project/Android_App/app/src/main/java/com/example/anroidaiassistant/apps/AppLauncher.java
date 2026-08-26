package com.example.anroidaiassistant.apps;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.Settings;
import android.util.Log;

import com.example.anroidaiassistant.MyAccessibilityService;
import com.example.anroidaiassistant.executor.CommandExecutionContext;
import com.example.anroidaiassistant.resources.AppSourceAliases;
import com.example.anroidaiassistant.util.TextNormalizer;

import java.util.Locale;

public final class AppLauncher {
    private static final String TAG = "AppLauncher";
    private final PackageActionGateway packageActionGateway;

    public AppLauncher() {
        this(new AndroidPackageActionGateway());
    }

    AppLauncher(PackageActionGateway packageActionGateway) {
        this.packageActionGateway = packageActionGateway;
    }

    public boolean launchPackage(
            Context context,
            String packageName,
            String label,
            CommandExecutionContext executionContext
    ) {
        try {
            if (packageActionGateway.launch(context, packageName)) {
                return true;
            }
            executionContext.showMessage("App not found. Please spell the app name.");
            return false;
        } catch (Exception exception) {
            Log.e(TAG, "Failed to launch app. errorType="
                    + exception.getClass().getSimpleName());
            executionContext.showMessage("Could not open " + firstNonEmpty(label, "app"));
            return false;
        }
    }

    public boolean openAppInfo(
            Context context,
            String packageName,
            String label,
            CommandExecutionContext executionContext
    ) {
        if (!hasText(packageName)) {
            executionContext.showMessage("App not found. Please spell the app name.");
            return false;
        }

        try {
            if (packageActionGateway.openAppInfo(context, packageName)) {
                return true;
            }
            executionContext.showMessage("Could not open app info for " + firstNonEmpty(label, "app"));
            return false;
        } catch (Exception exception) {
            Log.e(TAG, "Failed to open app info. errorType="
                    + exception.getClass().getSimpleName());
            executionContext.showMessage("Could not open app info for " + firstNonEmpty(label, "app"));
            return false;
        }
    }

    public boolean requestUninstallPackage(
            Context context,
            String packageName,
            String label,
            CommandExecutionContext executionContext
    ) {
        if (!hasText(packageName)) {
            executionContext.showMessage("App not found. Please spell the app name.");
            return false;
        }

        try {
            if (!packageActionGateway.requestUninstall(context, packageName)) {
                executionContext.showMessage("Could not uninstall " + firstNonEmpty(label, "app"));
                return false;
            }
            MyAccessibilityService service = MyAccessibilityService.getInstance();
            if (service != null) {
                service.confirmSystemUninstallDialog(packageName, label);
            }
            return true;
        } catch (Exception exception) {
            Log.e(TAG, "Failed to request uninstall. errorType="
                    + exception.getClass().getSimpleName());
            executionContext.showMessage("Could not uninstall " + firstNonEmpty(label, "app"));
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
            Log.w(TAG, "Could not load app icon. errorType="
                    + exception.getClass().getSimpleName());
            return null;
        }
    }

    public String getAppLabel(Context context, String packageName, String fallback) {
        if (!hasText(packageName)) {
            return fallback;
        }

        try {
            PackageManager packageManager = context.getPackageManager();
            return packageManager.getApplicationLabel(
                    packageManager.getApplicationInfo(packageName, 0)
            ).toString();
        } catch (Exception exception) {
            return fallback;
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
        for (AppSourceAliases.SourceRule rule : AppSourceAliases.SOURCE_RULES) {
            if (matchesRule(normalized, rule)) {
                return rule.source;
            }
        }
        return "";
    }

    private boolean matchesRule(String packageName, AppSourceAliases.SourceRule rule) {
        for (String prefix : rule.startsWith) {
            if (packageName.startsWith(prefix)) {
                return true;
            }
        }
        for (String part : rule.contains) {
            if (packageName.contains(part)) {
                return true;
            }
        }
        return false;
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

    interface PackageActionGateway {
        boolean launch(Context context, String packageName);

        boolean openAppInfo(Context context, String packageName);

        boolean requestUninstall(Context context, String packageName);
    }

    private static final class AndroidPackageActionGateway implements PackageActionGateway {
        @Override
        public boolean launch(Context context, String packageName) {
            PackageManager packageManager = context.getPackageManager();
            Intent intent = packageManager.getLaunchIntentForPackage(packageName);
            if (intent == null) {
                return false;
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return true;
        }

        @Override
        public boolean openAppInfo(Context context, String packageName) {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + packageName));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return true;
        }

        @Override
        public boolean requestUninstall(Context context, String packageName) {
            Intent intent = new Intent(Intent.ACTION_UNINSTALL_PACKAGE);
            intent.setData(Uri.parse("package:" + packageName));
            intent.putExtra(Intent.EXTRA_RETURN_RESULT, false);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return true;
        }
    }
}
