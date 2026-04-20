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

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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
                handleOpenApp(response);
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

    private void handleOpenApp(PredictResponse response) {
        String rawAppCandidate = firstNonEmpty(
                response.getParameterAsString("app_name"),
                response.getParameterAsString("app_name_original_normalized"),
                response.getParameterAsString("app_name_ascii"),
                response.getParameterAsString("app_name_normalized")
        );
        if (rawAppCandidate == null || rawAppCandidate.isEmpty()) {
            return;
        }

        String candidateOriginalNormalized = normalizeText(
                firstNonEmpty(response.getParameterAsString("app_name_original_normalized"), rawAppCandidate)
        );
        String candidateAscii = normalizeAsciiText(
                firstNonEmpty(response.getParameterAsString("app_name_ascii"), candidateOriginalNormalized)
        );
        String candidateCompact = normalizeAppCandidate(
                firstNonEmpty(response.getParameterAsString("app_name_normalized"), rawAppCandidate)
        );

        if (candidateCompact.isEmpty()) {
            Toast.makeText(context, "App not found: " + rawAppCandidate, Toast.LENGTH_SHORT).show();
            return;
        }

        PackageManager pm = context.getPackageManager();
        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> pkgAppsList = pm.queryIntentActivities(mainIntent, 0);

        String bestMatchPackage = null;
        float bestScore = -1f;
        for (ResolveInfo app : pkgAppsList) {
            float score = scoreAppMatch(
                    candidateCompact,
                    candidateAscii,
                    candidateOriginalNormalized,
                    app.loadLabel(pm).toString(),
                    app.activityInfo.packageName
            );
            if (score > bestScore) {
                bestScore = score;
                bestMatchPackage = app.activityInfo.packageName;
            }
        }

        if (bestMatchPackage != null && bestScore >= minimumAcceptableScore(candidateCompact)) {
            Intent intent = pm.getLaunchIntentForPackage(bestMatchPackage);
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                return;
            }
        }

        Toast.makeText(context, "App not found: " + rawAppCandidate, Toast.LENGTH_SHORT).show();
    }

    private String normalizeDirection(String direction) {
        if (direction == null) {
            return "";
        }
        return direction.trim().toLowerCase(Locale.US);
    }

    private String normalizeText(String text) {
        if (text == null) {
            return "";
        }

        String normalized = text.toLowerCase(Locale.US).trim();
        normalized = normalized.replaceAll("[^\\p{L}\\p{Nd}\\s]", " ");
        normalized = normalized.replaceAll("\\s+", " ").trim();
        return normalized;
    }

    private String toAsciiTurkish(String text) {
        return text
                .replace('\u00e7', 'c')
                .replace('\u011f', 'g')
                .replace('\u0131', 'i')
                .replace('\u00f6', 'o')
                .replace('\u015f', 's')
                .replace('\u00fc', 'u')
                .replace('\u00e2', 'a')
                .replace('\u00ee', 'i')
                .replace('\u00fb', 'u');
    }

    private String normalizeAsciiText(String text) {
        return toAsciiTurkish(normalizeText(text));
    }

    private String normalizeAppCandidate(String text) {
        return normalizeAsciiText(text).replace(" ", "");
    }

    private float scoreAppMatch(
            String candidateCompact,
            String candidateAscii,
            String candidateOriginalNormalized,
            String appLabel,
            String packageName
    ) {
        String labelOriginalNormalized = normalizeText(appLabel);
        String labelAscii = normalizeAsciiText(appLabel);
        String labelCompact = labelAscii.replace(" ", "");
        String packageNormalized = normalizeText(packageName.replace('.', ' '));
        String packageCompact = packageNormalized.replace(" ", "");

        if (candidateCompact.equals(labelCompact)) {
            return 1.0f;
        }
        if (candidateCompact.equals(packageCompact)) {
            return 0.98f;
        }

        float score = 0.0f;

        if (labelCompact.contains(candidateCompact) || candidateCompact.contains(labelCompact)) {
            score = Math.max(score, 0.92f - lengthPenalty(candidateCompact, labelCompact));
        }
        if (packageCompact.contains(candidateCompact) || candidateCompact.contains(packageCompact)) {
            score = Math.max(score, 0.86f - lengthPenalty(candidateCompact, packageCompact));
        }

        score = Math.max(score, levenshteinSimilarity(candidateCompact, labelCompact));
        score = Math.max(score, levenshteinSimilarity(candidateCompact, packageCompact) * 0.88f);
        score = Math.max(score, tokenOverlapScore(candidateAscii, labelAscii) * 0.90f);
        score = Math.max(score, tokenOverlapScore(candidateOriginalNormalized, labelOriginalNormalized) * 0.86f);

        return score;
    }

    private float minimumAcceptableScore(String candidateCompact) {
        int length = candidateCompact.length();
        if (length <= 4) {
            return 0.95f;
        }
        if (length <= 7) {
            return 0.84f;
        }
        return 0.72f;
    }

    private float lengthPenalty(String left, String right) {
        return Math.min(0.12f, Math.abs(left.length() - right.length()) * 0.01f);
    }

    private float tokenOverlapScore(String leftText, String rightText) {
        Set<String> leftTokens = tokenize(leftText);
        Set<String> rightTokens = tokenize(rightText);
        if (leftTokens.isEmpty() || rightTokens.isEmpty()) {
            return 0.0f;
        }

        int overlap = 0;
        for (String token : leftTokens) {
            if (rightTokens.contains(token)) {
                overlap++;
            }
        }

        return (2.0f * overlap) / (leftTokens.size() + rightTokens.size());
    }

    private Set<String> tokenize(String text) {
        Set<String> tokens = new HashSet<>();
        if (text == null || text.trim().isEmpty()) {
            return tokens;
        }

        for (String token : normalizeText(text).split(" ")) {
            if (!token.isEmpty()) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private float levenshteinSimilarity(String left, String right) {
        if (left == null || right == null || left.isEmpty() || right.isEmpty()) {
            return 0.0f;
        }
        if (left.equals(right)) {
            return 1.0f;
        }

        int distance = levenshteinDistance(left, right);
        return 1.0f - ((float) distance / Math.max(left.length(), right.length()));
    }

    private int levenshteinDistance(String left, String right) {
        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];

        for (int j = 0; j <= right.length(); j++) {
            previous[j] = j;
        }

        for (int i = 1; i <= left.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= right.length(); j++) {
                int cost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(
                        Math.min(current[j - 1] + 1, previous[j] + 1),
                        previous[j - 1] + cost
                );
            }

            int[] temp = previous;
            previous = current;
            current = temp;
        }

        return previous[right.length()];
    }

    private String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return null;
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
