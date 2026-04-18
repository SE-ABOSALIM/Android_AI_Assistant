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

import java.util.Calendar;
import java.util.List;

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
        PackageManager pm = context.getPackageManager();
        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> pkgAppsList = pm.queryIntentActivities(mainIntent, 0);

        // Remove trigger words from input to isolate the app name
        String targetApp = input.toLowerCase()
                .replace("aç", "")
                .replace("open", "")
                .replace("launch", "")
                .replace("başlat", "")
                .replace("uygulamasını", "")
                .replace("uygulamayı", "")
                .trim();

        if (targetApp.isEmpty()) {
            Toast.makeText(context, "Hangi uygulamayı açmamı istersiniz?", Toast.LENGTH_SHORT).show();
            return;
        }

        String bestMatchPackage = null;
        int maxSimilarity = 0;

        for (ResolveInfo app : pkgAppsList) {
            String appLabel = app.loadLabel(pm).toString().toLowerCase();
            
            // Fuzzy match: if the label is part of input or input is part of label
            if (appLabel.contains(targetApp) || targetApp.contains(appLabel)) {
                if (appLabel.length() > maxSimilarity) {
                    bestMatchPackage = app.activityInfo.packageName;
                    maxSimilarity = appLabel.length();
                }
            }
        }

        if (bestMatchPackage != null) {
            openAppByPackageName(bestMatchPackage);
        } else {
            Toast.makeText(context, "Uygulama bulunamadı: " + targetApp, Toast.LENGTH_SHORT).show();
        }
    }

    private void handleButtonClick(String input, MyAccessibilityService service) {
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
        Intent intent = new Intent(AlarmClock.ACTION_SET_ALARM)
                .putExtra(AlarmClock.EXTRA_MESSAGE, "AI Assistant Alarm")
                .putExtra(AlarmClock.EXTRA_HOUR, 8)
                .putExtra(AlarmClock.EXTRA_MINUTES, 0);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    private void handleTakePhoto() {
        Intent intent = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    private void handleCall(String input) {
        Toast.makeText(context, "Arama özelliği henüz aktif değil.", Toast.LENGTH_SHORT).show();
    }

    private void openAppByPackageName(String packageName) {
        PackageManager pm = context.getPackageManager();
        Intent intent = pm.getLaunchIntentForPackage(packageName);
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        }
    }
}
