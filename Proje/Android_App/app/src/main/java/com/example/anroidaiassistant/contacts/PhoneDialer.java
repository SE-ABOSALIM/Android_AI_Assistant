package com.example.anroidaiassistant.contacts;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.telecom.TelecomManager;
import android.util.Log;

import com.example.anroidaiassistant.executor.CommandExecutionContext;
import com.example.anroidaiassistant.util.TextNormalizer;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class PhoneDialer {
    private static final String TAG = "PhoneDialer";

    public boolean looksLikePhoneNumber(String value) {
        if (!hasText(value) || !value.matches(".*\\d.*")) {
            return false;
        }

        String normalized = normalizePhoneNumber(value);
        return normalized.replace("+", "").length() >= 3;
    }

    public String normalizePhoneNumber(String value) {
        return value == null ? "" : value.replaceAll("[^0-9+]", "");
    }

    public String formatPhoneNumberForDisplay(String value) {
        return hasText(value) ? value : "";
    }

    public void callPhoneNumber(String phoneNumber, CommandExecutionContext executionContext) {
        if (!hasText(phoneNumber)) {
            executionContext.showMessage("Contact has no phone number");
            return;
        }

        Context context = executionContext.getAndroidContext();
        String dialerPackageName = getDefaultDialerPackageName(context);
        boolean canCallDirectly = context.checkSelfPermission(Manifest.permission.CALL_PHONE)
                == PackageManager.PERMISSION_GRANTED;

        if (canCallDirectly) {
            Intent callIntent = buildPhoneIntent(Intent.ACTION_CALL, phoneNumber);
            if (tryStartPhoneIntentWithResolvedPackage(context, callIntent, dialerPackageName)) {
                return;
            }
        }

        Intent dialIntent = buildPhoneIntent(Intent.ACTION_DIAL, phoneNumber);
        if (tryStartPhoneIntentWithResolvedPackage(context, dialIntent, dialerPackageName)) {
            if (!canCallDirectly) {
                executionContext.showMessage("Phone call permission is required");
            }
            return;
        }

        executionContext.showMessage("Could not start phone call");
    }

    private Intent buildPhoneIntent(String action, String phoneNumber) {
        Intent intent = new Intent(action, Uri.fromParts("tel", phoneNumber, null));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }

    private boolean tryStartPhoneIntentWithResolvedPackage(
            Context context,
            Intent intent,
            String preferredPackageName
    ) {
        for (String packageName : getPhoneIntentPackageCandidates(context, intent, preferredPackageName)) {
            Intent packagedIntent = new Intent(intent);
            packagedIntent.setPackage(packageName);
            if (tryStartPhoneIntent(context, packagedIntent)) {
                return true;
            }
        }
        return false;
    }

    private List<String> getPhoneIntentPackageCandidates(
            Context context,
            Intent intent,
            String preferredPackageName
    ) {
        Set<String> packageNames = new LinkedHashSet<>();
        addPhonePackageCandidate(packageNames, preferredPackageName);
        addPhonePackageCandidate(packageNames, "com.samsung.android.dialer");
        addPhonePackageCandidate(packageNames, "com.google.android.dialer");
        addPhonePackageCandidate(packageNames, "com.android.dialer");
        addPhonePackageCandidate(packageNames, "com.android.contacts");
        addPhonePackageCandidate(packageNames, "com.google.android.contacts");

        PackageManager packageManager = context.getPackageManager();
        List<ResolveInfo> handlers = packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
        for (ResolveInfo handler : handlers) {
            if (isSystemPhoneHandler(handler)) {
                addPhonePackageCandidate(packageNames, handler.activityInfo.packageName);
            }
        }

        return new ArrayList<>(packageNames);
    }

    private void addPhonePackageCandidate(Set<String> packageNames, String packageName) {
        if (hasText(packageName)) {
            packageNames.add(packageName);
        }
    }

    private boolean isSystemPhoneHandler(ResolveInfo handler) {
        if (handler == null || handler.activityInfo == null || handler.activityInfo.applicationInfo == null) {
            return false;
        }

        int flags = handler.activityInfo.applicationInfo.flags;
        return (flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                || (flags & android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
    }

    private boolean tryStartPhoneIntent(Context context, Intent intent) {
        try {
            context.startActivity(intent);
            return true;
        } catch (Exception exception) {
            Log.w(TAG, "Failed to start phone intent: " + intent.getAction(), exception);
            return false;
        }
    }

    private String getDefaultDialerPackageName(Context context) {
        try {
            TelecomManager telecomManager = (TelecomManager) context.getSystemService(Context.TELECOM_SERVICE);
            if (telecomManager != null) {
                return telecomManager.getDefaultDialerPackage();
            }
        } catch (Exception exception) {
            Log.w(TAG, "Could not resolve default dialer package", exception);
        }
        return null;
    }

    private boolean hasText(String value) {
        return TextNormalizer.hasText(value);
    }
}