package com.example.anroidaiassistant.executor.handlers;

import android.app.NotificationManager;
import android.content.Context;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.os.Build;
import android.util.Log;

import com.example.anroidaiassistant.MyAccessibilityService;
import com.example.anroidaiassistant.executor.CommandExecutionContext;
import com.example.anroidaiassistant.executor.CommandHandler;
import com.example.anroidaiassistant.util.ParameterReader;
import com.example.anroidaiassistant.util.TextNormalizer;

import java.util.Locale;
import java.util.Map;

public final class SystemSettingCommandHandler implements CommandHandler {
    private static final String TAG = "SystemSettingCommandHandler";

    private final String intent;

    public SystemSettingCommandHandler(String intent) {
        this.intent = intent;
    }

    @Override
    public String getIntent() {
        return intent;
    }

    @Override
    public void handle(Map<String, Object> parameters, CommandExecutionContext context) {
        switch (intent) {
            case "SET_FLASHLIGHT":
                handleFlashlight(parameters, context);
                break;
            case "SET_SOUND_MODE":
                handleSoundMode(parameters, context);
                break;
            case "SET_KEYBOARD":
                handleKeyboard(parameters, context);
                break;
            case "SET_WIFI":
            case "SET_BLUETOOTH":
            case "SET_LOCATION":
            case "SET_MOBILE_DATA":
            case "SET_MOBILE_HOTSPOT":
                handleQuickSettingsToggle(parameters, context);
                break;
            default:
                context.showMessage("System setting not supported");
                break;
        }
    }

    private void handleFlashlight(Map<String, Object> parameters, CommandExecutionContext context) {
        String state = readState(parameters);
        if (!TextNormalizer.hasText(state)) {
            context.showMessage("Should I turn it on or off?");
            return;
        }

        Context androidContext = context.getAndroidContext();
        if (androidContext == null) {
            context.showMessage("Flashlight control is unavailable");
            return;
        }

        CameraManager cameraManager = (CameraManager) androidContext.getSystemService(Context.CAMERA_SERVICE);
        if (cameraManager == null) {
            context.showMessage("Flashlight control is unavailable");
            return;
        }

        try {
            String cameraId = findFlashCameraId(cameraManager);
            if (cameraId == null) {
                context.showMessage("Flashlight is unavailable on this device");
                return;
            }
            cameraManager.setTorchMode(cameraId, "on".equals(state));
        } catch (SecurityException exception) {
            Log.e(TAG, "Missing camera permission for flashlight", exception);
            context.showMessage("Camera permission is required for flashlight");
        } catch (CameraAccessException exception) {
            Log.e(TAG, "Failed to set flashlight", exception);
            context.showMessage("Flashlight control failed");
        }
    }

    private void handleSoundMode(Map<String, Object> parameters, CommandExecutionContext context) {
        String soundMode = normalize(ParameterReader.getStringParam(parameters, "sound_mode"));
        if (!TextNormalizer.hasText(soundMode)) {
            context.showMessage("Which sound mode should I set?");
            return;
        }

        Context androidContext = context.getAndroidContext();
        if (androidContext == null) {
            context.showMessage("Sound mode control is unavailable");
            return;
        }

        AudioManager audioManager = (AudioManager) androidContext.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager == null) {
            context.showMessage("Sound mode control is unavailable");
            return;
        }

        switch (soundMode) {
            case "normal":
                setInterruptionFilterIfAllowed(androidContext, NotificationManager.INTERRUPTION_FILTER_ALL);
                setRingerMode(audioManager, AudioManager.RINGER_MODE_NORMAL, context);
                unmuteAudibleStreams(audioManager);
                break;
            case "mute":
            case "silent":
                setSilentRingerMode(androidContext, audioManager, context);
                break;
            case "vibrate":
                setInterruptionFilterIfAllowed(androidContext, NotificationManager.INTERRUPTION_FILTER_ALL);
                setRingerMode(audioManager, AudioManager.RINGER_MODE_VIBRATE, context);
                break;
            default:
                context.showMessage("Sound mode not supported");
                break;
        }
    }

    private void setSilentRingerMode(
            Context androidContext,
            AudioManager audioManager,
            CommandExecutionContext context
    ) {
        if (trySetRingerMode(audioManager, AudioManager.RINGER_MODE_SILENT)) {
            return;
        }

        if (setInterruptionFilterIfAllowed(androidContext, NotificationManager.INTERRUPTION_FILTER_NONE)
                && trySetRingerMode(audioManager, AudioManager.RINGER_MODE_SILENT)) {
            return;
        }

        if (tryMuteRingStream(audioManager) || audioManager.getRingerMode() == AudioManager.RINGER_MODE_SILENT) {
            return;
        }

        context.showMessage("Do Not Disturb access is required for silent mode");
    }

    private void setRingerMode(
            AudioManager audioManager,
            int ringerMode,
            CommandExecutionContext context
    ) {
        if (trySetRingerMode(audioManager, ringerMode)) {
            return;
        }

        context.showMessage("Sound mode permission is unavailable on this device");
    }

    private boolean trySetRingerMode(AudioManager audioManager, int ringerMode) {
        try {
            audioManager.setRingerMode(ringerMode);
            return audioManager.getRingerMode() == ringerMode;
        } catch (SecurityException exception) {
            Log.e(TAG, "Failed to set sound mode", exception);
            return false;
        }
    }

    private boolean setInterruptionFilterIfAllowed(Context androidContext, int interruptionFilter) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return false;
        }

        NotificationManager notificationManager =
                (NotificationManager) androidContext.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager == null || !notificationManager.isNotificationPolicyAccessGranted()) {
            return false;
        }

        try {
            notificationManager.setInterruptionFilter(interruptionFilter);
            return notificationManager.getCurrentInterruptionFilter() == interruptionFilter;
        } catch (SecurityException exception) {
            Log.e(TAG, "Failed to set interruption filter", exception);
            return false;
        }
    }

    private boolean tryMuteRingStream(AudioManager audioManager) {
        try {
            audioManager.adjustStreamVolume(
                    AudioManager.STREAM_RING,
                    AudioManager.ADJUST_MUTE,
                    AudioManager.FLAG_SHOW_UI
            );
            return audioManager.getRingerMode() == AudioManager.RINGER_MODE_SILENT;
        } catch (SecurityException exception) {
            Log.e(TAG, "Failed to mute ring stream", exception);
            return false;
        }
    }

    private void muteAudibleStreams(AudioManager audioManager) {
        for (int stream : getAudibleStreams()) {
            try {
                audioManager.adjustStreamVolume(stream, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI);
            } catch (SecurityException exception) {
                Log.e(TAG, "Failed to mute stream: " + stream, exception);
            }
        }
    }

    private void unmuteAudibleStreams(AudioManager audioManager) {
        for (int stream : getAudibleStreams()) {
            try {
                audioManager.adjustStreamVolume(stream, AudioManager.ADJUST_UNMUTE, AudioManager.FLAG_SHOW_UI);
            } catch (SecurityException exception) {
                Log.e(TAG, "Failed to unmute stream: " + stream, exception);
            }
        }
    }

    private int[] getAudibleStreams() {
        return new int[]{
                AudioManager.STREAM_RING,
                AudioManager.STREAM_NOTIFICATION,
                AudioManager.STREAM_SYSTEM,
                AudioManager.STREAM_MUSIC
        };
    }

    private void handleKeyboard(Map<String, Object> parameters, CommandExecutionContext context) {
        String state = readState(parameters);
        if (!TextNormalizer.hasText(state)) {
            context.showMessage("Should I open or close the keyboard?");
            return;
        }

        MyAccessibilityService service = MyAccessibilityService.getInstance();
        if (service == null) {
            context.showMessage("Accessibility service is not connected");
            return;
        }

        if ("open".equals(state) || "on".equals(state)) {
            service.setSoftKeyboardVisible(true);
        } else if ("close".equals(state) || "off".equals(state)) {
            service.setSoftKeyboardVisible(false);
        } else {
            context.showMessage("Keyboard state not supported");
        }
    }

    private void handleQuickSettingsToggle(Map<String, Object> parameters, CommandExecutionContext context) {
        String state = readState(parameters);
        if (!TextNormalizer.hasText(state)) {
            context.showMessage("Should I turn it on or off?");
            return;
        }

        MyAccessibilityService service = MyAccessibilityService.getInstance();
        if (service == null) {
            context.showMessage("Accessibility service is not connected");
            return;
        }

        if (!service.setQuickSettingState(intent, state)) {
            context.showMessage("Quick settings control is unavailable");
        }
    }

    private String findFlashCameraId(CameraManager cameraManager) throws CameraAccessException {
        for (String cameraId : cameraManager.getCameraIdList()) {
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
            Boolean hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
            if (Boolean.TRUE.equals(hasFlash)) {
                return cameraId;
            }
        }
        return null;
    }

    private String readState(Map<String, Object> parameters) {
        return normalize(ParameterReader.getStringParam(parameters, "state"));
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.US);
    }
}
