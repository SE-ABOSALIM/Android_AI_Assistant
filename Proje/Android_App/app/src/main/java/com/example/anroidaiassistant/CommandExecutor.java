package com.example.anroidaiassistant;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.net.Uri;
import android.provider.AlarmClock;
import android.provider.MediaStore;
import android.widget.Toast;

import java.util.Calendar;

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

        String label = response.getPredicted_label();
        String input = response.getInput().toLowerCase();
        MyAccessibilityService service = MyAccessibilityService.getInstance();

        switch (label) {
            case "OPEN_APP":
                handleOpenApp(input);
                break;
            case "CLOSE_APP":
                if (service != null) service.performBack();
                break;
            case "HOME_PAGE":
            case "GO_HOME":
                if (service != null) service.performHome();
                break;
            case "CLICK_ITEM":
                if (service != null) handleButtonClick(input, service);
                break;
            case "SCROLL_SCREEN":
                if (service != null) service.scroll(input.contains("down") || input.contains("aşağı"));
                break;
            case "SWIPE_GESTURE":
                if (service != null) handleSwipe(input, service);
                break;
            case "SHOW_RECENTS":
                if (service != null) service.performRecents();
                break;
            case "OPEN_NOTIFICATIONS":
                if (service != null) service.performNotifications();
                break;
            case "ADJUST_VOLUME":
                handleVolume(input);
                break;
            case "SET_ALARM":
                handleSetAlarm(input);
                break;
            case "TAKE_PHOTO":
                handleTakePhoto();
                break;
            case "CALL_CONTACT":
                handleCall(input);
                break;
            default:
                Toast.makeText(context, "Unknown command: " + label, Toast.LENGTH_SHORT).show();
        }
    }

    private void handleOpenApp(String input) {
        if (input.contains("whatsapp")) {
            openAppByPackageName("com.whatsapp");
        } else if (input.contains("instagram")) {
            openAppByPackageName("com.instagram.android");
        } else if (input.contains("facebook")) {
            openAppByPackageName("com.facebook.katana");
        } else if (input.contains("youtube")) {
            openAppByPackageName("com.google.android.youtube");
        } else {
            Toast.makeText(context, "App not recognized", Toast.LENGTH_SHORT).show();
        }
    }

    private void handleButtonClick(String input, MyAccessibilityService service) {
        // Logic to extract button text from input
        String buttonName = input.replace("click", "").replace("bas", "").trim();
        service.clickNodeByText(buttonName);
    }

    private void handleSwipe(String input, MyAccessibilityService service) {
        if (input.contains("left") || input.contains("sol")) {
            service.swipe(800, 1000, 200, 1000);
        } else if (input.contains("right") || input.contains("sağ")) {
            service.swipe(200, 1000, 800, 1000);
        }
    }

    private void handleVolume(String input) {
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (input.contains("up") || input.contains("arttır")) {
            audioManager.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI);
        } else {
            audioManager.adjustVolume(AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI);
        }
    }

    private void handleSetAlarm(String input) {
        // Simple alarm for test
        Intent intent = new Intent(AlarmClock.ACTION_SET_ALARM)
                .putExtra(AlarmClock.EXTRA_MESSAGE, "AI Assistant Alarm")
                .putExtra(AlarmClock.EXTRA_HOUR, 8)
                .putExtra(AlarmClock.EXTRA_MINUTES, 0);
        context.startActivity(intent);
    }

    private void handleTakePhoto() {
        Intent intent = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA);
        context.startActivity(intent);
    }

    private void handleCall(String input) {
        // This requires RUNTIME permission and extraction of contact
        Toast.makeText(context, "Calling contact...", Toast.LENGTH_SHORT).show();
    }

    private void openAppByPackageName(String packageName) {
        PackageManager pm = context.getPackageManager();
        Intent intent = pm.getLaunchIntentForPackage(packageName);
        if (intent != null) {
            context.startActivity(intent);
        }
    }
}