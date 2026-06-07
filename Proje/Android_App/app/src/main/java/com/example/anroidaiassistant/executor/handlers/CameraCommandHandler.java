package com.example.anroidaiassistant.executor.handlers;

import android.content.Intent;
import android.provider.MediaStore;
import android.util.Log;

import com.example.anroidaiassistant.MyAccessibilityService;
import com.example.anroidaiassistant.executor.CommandExecutionContext;
import com.example.anroidaiassistant.executor.CommandHandler;
import com.example.anroidaiassistant.resources.CameraAliases;
import com.example.anroidaiassistant.util.ParameterReader;
import com.example.anroidaiassistant.util.TextNormalizer;

import java.util.Locale;
import java.util.Map;

public final class CameraCommandHandler implements CommandHandler {
    private static final String TAG = "CameraCommandHandler";

    @Override
    public String getIntent() {
        return "TAKE_PHOTO";
    }

    @Override
    public void handle(Map<String, Object> parameters, CommandExecutionContext context) {
        String camera = normalizeCamera(ParameterReader.getStringParam(parameters, "camera"));

        MyAccessibilityService service = MyAccessibilityService.getInstance();
        if (service != null && service.capturePhoto(camera)) {
            return;
        }

        if (context.getAndroidContext() == null) {
            context.showMessage("Camera unavailable");
            return;
        }

        Intent intent = buildCameraIntent();
        try {
            context.getAndroidContext().startActivity(intent);
        } catch (Exception exception) {
            Log.e(TAG, "Failed to open camera", exception);
            context.showMessage("Camera unavailable");
        }
    }

    private Intent buildCameraIntent() {
        return new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    private String normalizeCamera(String camera) {
        String normalized = TextNormalizer.normalizeAsciiText(camera).toLowerCase(Locale.US);
        if (containsAny(normalized, CameraAliases.FRONT_CAMERA_KEYWORDS)
                || normalized.equals("on")) {
            return "front";
        }
        if (containsAny(normalized, CameraAliases.BACK_CAMERA_KEYWORDS)) {
            return "back";
        }
        return "";
    }

    private boolean containsAny(String value, String[] candidates) {
        for (String candidate : candidates) {
            if (value.contains(candidate)) {
                return true;
            }
        }
        return false;
    }
}
