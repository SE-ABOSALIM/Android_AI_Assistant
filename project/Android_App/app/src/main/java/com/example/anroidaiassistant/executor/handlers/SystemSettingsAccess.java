package com.example.anroidaiassistant.executor.handlers;

import android.content.Context;
import android.os.Build;
import android.provider.Settings;

interface SystemSettingsAccess {
    boolean canWrite(Context context);

    int getInt(Context context, String name, int defaultValue);

    void putInt(Context context, String name, int value);

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
}
