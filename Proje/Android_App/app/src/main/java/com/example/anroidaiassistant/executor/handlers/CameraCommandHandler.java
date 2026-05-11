package com.example.anroidaiassistant.executor.handlers;

import android.content.Intent;
import android.provider.MediaStore;
import android.util.Log;

import com.example.anroidaiassistant.MyAccessibilityService;
import com.example.anroidaiassistant.executor.CommandExecutionContext;
import com.example.anroidaiassistant.executor.CommandHandler;

import java.util.Map;

public final class CameraCommandHandler implements CommandHandler {
    private static final String TAG = "CameraCommandHandler";

    @Override
    public String getIntent() {
        return "TAKE_PHOTO";
    }

    @Override
    public void handle(Map<String, Object> parameters, CommandExecutionContext context) {
        MyAccessibilityService service = MyAccessibilityService.getInstance();
        if (service != null && service.capturePhoto()) {
            return;
        }

        Intent intent = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.getAndroidContext().startActivity(intent);
        } catch (Exception exception) {
            Log.e(TAG, "Failed to open camera", exception);
            context.showMessage("Camera unavailable");
        }
    }
}