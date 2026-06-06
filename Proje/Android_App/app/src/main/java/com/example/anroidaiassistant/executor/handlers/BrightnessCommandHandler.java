package com.example.anroidaiassistant.executor.handlers;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import com.example.anroidaiassistant.executor.CommandExecutionContext;
import com.example.anroidaiassistant.executor.CommandHandler;
import com.example.anroidaiassistant.util.ParameterReader;
import com.example.anroidaiassistant.util.TextNormalizer;

import java.util.Locale;
import java.util.Map;

public final class BrightnessCommandHandler implements CommandHandler {
    private static final String TAG = "BrightnessCommandHandler";
    private static final int BRIGHTNESS_MIN = 10;
    private static final int BRIGHTNESS_MAX = 255;
    private static final int BRIGHTNESS_STEP = 45;

    @Override
    public String getIntent() {
        return "ADJUST_BRIGHTNESS";
    }

    @Override
    public void handle(Map<String, Object> parameters, CommandExecutionContext context) {
        String brightness = normalize(ParameterReader.getStringParam(parameters, "brightness"));
        if (!TextNormalizer.hasText(brightness)) {
            context.showMessage("Should I increase or decrease brightness?");
            return;
        }

        Context androidContext = context.getAndroidContext();
        if (androidContext == null) {
            context.showMessage("Brightness control is unavailable");
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.System.canWrite(androidContext)) {
            openWriteSettingsPermission(androidContext, context);
            return;
        }

        try {
            int current = Settings.System.getInt(
                    androidContext.getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS,
                    125
            );
            int target;
            switch (brightness) {
                case "increase":
                    target = Math.min(BRIGHTNESS_MAX, current + BRIGHTNESS_STEP);
                    break;
                case "decrease":
                    target = Math.max(BRIGHTNESS_MIN, current - BRIGHTNESS_STEP);
                    break;
                default:
                    context.showMessage("Brightness action not supported");
                    return;
            }

            Settings.System.putInt(
                    androidContext.getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            );
            Settings.System.putInt(
                    androidContext.getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS,
                    target
            );
        } catch (Exception exception) {
            Log.e(TAG, "Failed to adjust brightness", exception);
            context.showMessage("Brightness control failed");
        }
    }

    private void openWriteSettingsPermission(Context androidContext, CommandExecutionContext context) {
        Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
                .setData(Uri.parse("package:" + androidContext.getPackageName()))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            androidContext.startActivity(intent);
            context.showMessage("Allow modify system settings to adjust brightness");
        } catch (Exception exception) {
            Log.e(TAG, "Failed to open write settings permission", exception);
            context.showMessage("Allow modify system settings to adjust brightness");
        }
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.US);
    }
}
