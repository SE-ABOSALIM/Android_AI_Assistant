package com.example.anroidaiassistant;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.media.AudioManager;
import android.net.Uri;
import android.provider.AlarmClock;
import android.provider.MediaStore;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CommandExecutor {

    private final Context context;

    public CommandExecutor(Context context) {
        this.context = context;
    }

    public void executeCommand(PredictResponse response) {
        if (!response.isAccepted()) {
            Toast.makeText(context, "Command not accepted", Toast.LENGTH_SHORT).show();
            return;
        }

        String intentLabel = response.getIntent();
        MyAccessibilityService service = MyAccessibilityService.getInstance();

        switch (intentLabel) {
            case "OPEN_APP":
                handleOpenApp(response.getParameterAsString("app_name"));
                break;
            case "CLOSE_APP":
                if (service != null) service.performBack();
                break;
            case "GO_HOME":
            case "HOME_PAGE":
                if (service != null) service.performHome();
                break;
            case "CLICK_ITEM":
                if (service != null) service.clickNodeByText(response.getParameterAsString("button_text"));
                break;
            case "SCROLL_SCREEN":
                if (service != null) {
                    String direction = normalizeDirection(response.getParameterAsString("direction"));
                    if (!service.scroll(direction)) {
                        Toast.makeText(context, "Scroll direction not supported", Toast.LENGTH_SHORT).show();
                    }
                }
                break;
            case "SWIPE_GESTURE":
                if (service != null) {
                    String direction = normalizeDirection(response.getParameterAsString("direction"));
                    if (!service.swipe(direction)) {
                        Toast.makeText(context, "Swipe direction not supported", Toast.LENGTH_SHORT).show();
                    }
                }
                break;
            case "SHOW_RECENTS":
                if (service != null) service.performRecents();
                break;
            case "OPEN_NOTIFICATIONS":
                if (service != null) service.performNotifications();
                break;
            case "ADJUST_VOLUME":
                handleVolume(response.getParameterAsString("volume_action"));
                break;
            case "SET_ALARM":
                handleSetAlarm(response.getParameterAsString("time_text"));
                break;
            case "SET_TIMER":
                handleSetTimer(response.getParameterAsInt("duration_seconds", 0));
                break;
            case "TAKE_PHOTO":
                handleTakePhoto();
                break;
            case "ASSISTANT_CONTROL":
                handleAssistantControl(response.getParameterAsString("assistant_action"));
                break;
            case "CALL_CONTACT":
                handleCall(response.getParameterAsString("contact_name"));
                break;
            default:
                Toast.makeText(context, "Unknown command: " + intentLabel, Toast.LENGTH_SHORT).show();
        }
    }

    private void handleOpenApp(String targetAppName) {
        if (targetAppName == null || targetAppName.isEmpty()) return;

        PackageManager pm = context.getPackageManager();
        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> pkgAppsList = pm.queryIntentActivities(mainIntent, 0);

        String bestMatchPackage = null;
        for (ResolveInfo app : pkgAppsList) {
            String appLabel = app.loadLabel(pm).toString().toLowerCase();
            if (appLabel.contains(targetAppName.toLowerCase()) || targetAppName.toLowerCase().contains(appLabel)) {
                bestMatchPackage = app.activityInfo.packageName;
                break;
            }
        }

        if (bestMatchPackage != null) {
            Intent intent = pm.getLaunchIntentForPackage(bestMatchPackage);
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            }
        } else {
            Toast.makeText(context, "App not found: " + targetAppName, Toast.LENGTH_SHORT).show();
        }
    }

    private String normalizeDirection(String direction) {
        if (direction == null) {
            return "";
        }
        return direction.trim().toLowerCase(Locale.US);
    }

    private void handleVolume(String action) {
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if ("increase".equalsIgnoreCase(action)) {
            audioManager.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI);
        } else if ("decrease".equalsIgnoreCase(action)) {
            audioManager.adjustVolume(AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI);
        }
    }

    private void handleSetAlarm(String time) {
        if (time == null || !time.contains(":")) return;
        try {
            String[] parts = time.split(":");
            Intent intent = new Intent(AlarmClock.ACTION_SET_ALARM)
                    .putExtra(AlarmClock.EXTRA_HOUR, Integer.parseInt(parts[0]))
                    .putExtra(AlarmClock.EXTRA_MINUTES, Integer.parseInt(parts[1]))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(context, "Error setting alarm", Toast.LENGTH_SHORT).show();
        }
    }

    private void handleSetTimer(int seconds) {
        if (seconds <= 0) return;
        Intent intent = new Intent(AlarmClock.ACTION_SET_TIMER)
                .putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                .putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    private void handleTakePhoto() {
        MyAccessibilityService service = MyAccessibilityService.getInstance();
        if (service != null && service.capturePhoto()) {
            return;
        }

        Intent intent = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    private void handleAssistantControl(String assistantAction) {
        MyAccessibilityService service = MyAccessibilityService.getInstance();
        if (service == null) {
            return;
        }

        String normalizedAction = normalizeDirection(assistantAction);
        if ("stop_listening".equals(normalizedAction)) {
            service.stopContinuousListening();

            MainActivity mainActivity = MainActivity.getInstance();
            if (mainActivity != null) {
                mainActivity.syncListeningUiState();
            }
        }
    }

    private void handleCall(String contactName) {
        Toast.makeText(context, "Calling " + contactName, Toast.LENGTH_SHORT).show();
    }
}
