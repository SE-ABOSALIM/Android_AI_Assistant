package com.example.anroidaiassistant.permissions;

import android.Manifest;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;

public final class FeaturePermissionAccess {
    private FeaturePermissionAccess() {}

    public static boolean isGranted(Context context, AssistantCapability capability) {
        if (context == null || capability == null) {
            return false;
        }

        String runtimePermission = runtimePermission(capability);
        if (runtimePermission != null) {
            if (capability == AssistantCapability.ANSWER_CALL
                    && Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                return true;
            }
            return context.checkSelfPermission(runtimePermission)
                    == PackageManager.PERMISSION_GRANTED;
        }

        switch (capability) {
            case POPUP:
                return Settings.canDrawOverlays(context);
            case SOUND_MODE:
                NotificationManager notificationManager = (NotificationManager)
                        context.getSystemService(Context.NOTIFICATION_SERVICE);
                return notificationManager != null
                        && notificationManager.isNotificationPolicyAccessGranted();
            case SYSTEM_SETTINGS:
                return Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                        || Settings.System.canWrite(context);
            default:
                return false;
        }
    }

    public static String runtimePermission(AssistantCapability capability) {
        if (capability == null) {
            return null;
        }
        switch (capability) {
            case MICROPHONE:
                return Manifest.permission.RECORD_AUDIO;
            case CONTACTS:
                return Manifest.permission.READ_CONTACTS;
            case DIRECT_CALL:
                return Manifest.permission.CALL_PHONE;
            case PHONE_STATE:
                return Manifest.permission.READ_PHONE_STATE;
            case ANSWER_CALL:
                return Manifest.permission.ANSWER_PHONE_CALLS;
            case CAMERA:
                return Manifest.permission.CAMERA;
            default:
                return null;
        }
    }
}
