package com.example.anroidaiassistant.executor.handlers;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

interface SystemSettingsAccess {
    boolean canWrite(Context context);

    int getInt(Context context, String name, int defaultValue);

    void putInt(Context context, String name, int value);

    void openWriteSettingsPermission(Context context);
}

final class AndroidSystemSettingsAccess implements SystemSettingsAccess {
    @Override
    public boolean canWrite(Context context) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.System.canWrite(context);
    }

    @Override
    public int getInt(Context context, String name, int defaultValue) {
        return Settings.System.getInt(context.getContentResolver(), name, defaultValue);
    }

    @Override
    public void putInt(Context context, String name, int value) {
        Settings.System.putInt(context.getContentResolver(), name, value);
    }

    @Override
    public void openWriteSettingsPermission(Context context) {
        Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
                .setData(Uri.parse("package:" + context.getPackageName()))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }
}
