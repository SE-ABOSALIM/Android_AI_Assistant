package com.example.anroidaiassistant;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.media.AudioManager;
import android.provider.AlarmClock;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class CommandExecutor {

    private static final String TAG = "PredictResponse";
    private static final int MAX_APP_CHOICES = 5;

    private final Context context;
    private int openAppFailureCount = 0;

    public CommandExecutor(Context context) {
        this.context = context;
    }

    public void executeCommand(PredictResponse response) {
        if (response == null) {
            showMessage("No response from backend");
            return;
        }

        if (!response.isAccepted()) {
            handleRejectedCommand(response);
            return;
        }

        if (response.isNeedsConfirmation()) {
            handleRejectedCommand(response);
            return;
        }

        if (errorEquals(response, "LOW_CONFIDENCE")
                || errorEquals(response, "MISSING_REQUIRED_SLOT")
                || errorEquals(response, "UNKNOWN_COMMAND")) {
            handleRejectedCommand(response);
            return;
        }

        String intent = response.getIntent();
        if (!hasText(intent)) {
            showMessage("Unsupported command");
            return;
        }

        if ("UNKNOWN_COMMAND".equalsIgnoreCase(intent)) {
            showMessage(firstNonEmpty(response.getErrorMessage(), "Command not supported"));
            return;
        }

        Map<String, Object> parameters = response.getParameters();
        switch (intent) {
            case "OPEN_APP":
                handleOpenApp(parameters);
                break;

            case "SCROLL_SCREEN":
                handleScroll(parameters);
                break;

            case "SWIPE_GESTURE":
                handleSwipe(parameters);
                break;

            case "ADJUST_VOLUME":
                handleVolume(parameters);
                break;

            case "GO_HOME":
                handleGoHome();
                break;

            case "SET_TIMER":
                handleSetTimer(parameters);
                break;

            case "TAKE_PHOTO":
                handleTakePhoto();
                break;

            case "STOP_LISTENING":
                stopListening();
                break;

            default:
                showMessage("Unsupported intent: " + intent);
                break;
        }
    }

    private void handleRejectedCommand(PredictResponse response) {
        if (response == null) {
            showMessage("No response from backend");
            return;
        }

        if (response.getMissingSlots() != null && !response.getMissingSlots().isEmpty()) {
            showMessage(buildMissingSlotsMessage(response.getMissingSlots()));
            return;
        }

        if (response.isNeedsConfirmation()) {
            showMessage(firstNonEmpty(response.getErrorMessage(), "Please clarify the command."));
            return;
        }

        if (errorEquals(response, "LOW_CONFIDENCE")) {
            showMessage(firstNonEmpty(response.getErrorMessage(), "Please repeat or clarify."));
            return;
        }

        if (errorEquals(response, "UNKNOWN_COMMAND")) {
            showMessage(firstNonEmpty(response.getErrorMessage(), "Command not supported"));
            return;
        }

        if (errorEquals(response, "MISSING_REQUIRED_SLOT")) {
            showMessage(firstNonEmpty(response.getErrorMessage(), "Please provide the missing information."));
            return;
        }

        showMessage(firstNonEmpty(response.getErrorMessage(), "Command not accepted"));
    }

    private String buildMissingSlotsMessage(List<String> missingSlots) {
        if (containsSlot(missingSlots, "direction")) {
            return "Which direction?";
        }
        if (containsSlot(missingSlots, "app_name")) {
            return "Which app should I open?";
        }
        if (containsSlot(missingSlots, "duration_value") || containsSlot(missingSlots, "duration_unit")) {
            return "How long should I set the timer for?";
        }
        if (containsSlot(missingSlots, "volume_action")) {
            return "Should I increase, decrease, mute, or unmute the volume?";
        }
        return "Please provide: " + joinList(missingSlots);
    }

    private boolean containsSlot(List<String> missingSlots, String slot) {
        for (String missingSlot : missingSlots) {
            if (slot.equalsIgnoreCase(missingSlot)) {
                return true;
            }
        }
        return false;
    }

    private void handleOpenApp(Map<String, Object> parameters) {
        String appName = getStringParam(parameters, "app_name");
        if (!hasText(appName)) {
            showMessage("Which app should I open?");
            return;
        }

        String packageName = getStringParam(parameters, "app_package_name");
        if (hasText(packageName)) {
            launchPackage(packageName, appName);
            return;
        }

        List<AppMatch> matches = findAppMatches(appName);
        if (matches.isEmpty()) {
            onOpenAppFailure(appName);
            return;
        }

        if (matches.size() == 1) {
            launchPackage(matches.get(0).packageName, matches.get(0).label);
            return;
        }

        showAppChoice(matches, appName);
    }

    public void handleSpelledAppCandidate(String spokenText) {
        if (!hasText(spokenText)) {
            showMessage("Which app should I open?");
            return;
        }
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("app_name", joinSpelledCandidate(spokenText));
        handleOpenApp(parameters);
    }

    private List<AppMatch> findAppMatches(String appName) {
        String candidateOriginalNormalized = normalizeText(appName);
        String candidateAscii = normalizeAsciiText(appName);
        String candidateCompact = normalizeAppCandidate(appName);
        if (!hasText(candidateCompact)) {
            return Collections.emptyList();
        }

        PackageManager packageManager = context.getPackageManager();
        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> installedApps = packageManager.queryIntentActivities(mainIntent, 0);

        Map<String, AppMatch> exactMatches = new LinkedHashMap<>();
        List<AppMatch> fuzzyMatches = new ArrayList<>();
        float threshold = minimumAcceptableScore(candidateCompact);

        for (ResolveInfo app : installedApps) {
            String packageName = app.activityInfo.packageName;
            String label = app.loadLabel(packageManager).toString();
            AppMatch match = scoreAppMatch(
                    candidateCompact,
                    candidateAscii,
                    candidateOriginalNormalized,
                    label,
                    packageName
            );

            if (match.exact) {
                exactMatches.put(packageName, match);
            } else if (match.score >= threshold) {
                fuzzyMatches.add(match);
            }
        }

        if (!exactMatches.isEmpty()) {
            return new ArrayList<>(exactMatches.values());
        }

        fuzzyMatches.sort(Comparator.comparing((AppMatch match) -> match.score).reversed());
        return fuzzyMatches;
    }

    private AppMatch scoreAppMatch(
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

        boolean exactLabelMatch = candidateCompact.equals(labelCompact);
        boolean exactPackageMatch = candidateCompact.equals(packageCompact);
        float score = exactLabelMatch ? 1.0f : (exactPackageMatch ? 0.98f : 0.0f);

        if (!candidateAscii.contains(" ") && tokenize(labelAscii).contains(candidateAscii)) {
            score = Math.max(score, 0.96f);
        }
        if (labelCompact.contains(candidateCompact) || candidateCompact.contains(labelCompact)) {
            score = Math.max(score, 0.92f - lengthPenalty(candidateCompact, labelCompact));
        }
        if (packageCompact.contains(candidateCompact) || candidateCompact.contains(packageCompact)) {
            score = Math.max(score, 0.82f - lengthPenalty(candidateCompact, packageCompact));
        }

        score = Math.max(score, levenshteinSimilarity(candidateCompact, labelCompact));
        score = Math.max(score, levenshteinSimilarity(candidateCompact, packageCompact) * 0.84f);
        score = Math.max(score, tokenOverlapScore(candidateAscii, labelAscii) * 0.90f);
        score = Math.max(score, tokenOverlapScore(candidateOriginalNormalized, labelOriginalNormalized) * 0.86f);

        return new AppMatch(appLabel, packageName, score, exactLabelMatch || exactPackageMatch);
    }

    private void showAppChoice(List<AppMatch> matches, String appName) {
        MainActivity activity = MainActivity.getInstance();
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            showMessage("Multiple apps match. Please say the full app name.");
            return;
        }

        int choiceCount = Math.min(matches.size(), MAX_APP_CHOICES);
        String[] labels = new String[choiceCount];
        for (int i = 0; i < choiceCount; i++) {
            labels[i] = matches.get(i).label;
        }

        activity.runOnUiThread(() -> new AlertDialog.Builder(activity)
                .setTitle("Choose app")
                .setMessage("Multiple apps match: " + appName)
                .setItems(labels, (dialog, which) -> {
                    AppMatch selectedMatch = matches.get(which);
                    launchPackage(selectedMatch.packageName, selectedMatch.label);
                })
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .show());
    }

    private void launchPackage(String packageName, String label) {
        PackageManager packageManager = context.getPackageManager();
        Intent intent = packageManager.getLaunchIntentForPackage(packageName);
        if (intent == null) {
            showMessage("App not found. Please spell the app name.");
            return;
        }

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
            openAppFailureCount = 0;
        } catch (Exception exception) {
            Log.e(TAG, "Failed to launch app: " + packageName, exception);
            showMessage("Could not open " + firstNonEmpty(label, "app"));
        }
    }

    private void onOpenAppFailure(String appCandidate) {
        openAppFailureCount++;
        showMessage("App not found. Please spell the app name.");

        if (openAppFailureCount == 2 || openAppFailureCount == 3) {
            MainActivity mainActivity = MainActivity.getInstance();
            if (mainActivity != null) {
                mainActivity.showSpellingSuggestionDialog();
            }
        }
    }

    private void handleScroll(Map<String, Object> parameters) {
        String direction = normalizeDirection(getStringParam(parameters, "direction"));
        if (!hasText(direction)) {
            showMessage("Which direction?");
            return;
        }

        MyAccessibilityService service = MyAccessibilityService.getInstance();
        if (service == null) {
            showMessage("Accessibility service is not connected");
            return;
        }

        if (!service.scroll(direction)) {
            showMessage("Cannot scroll " + direction);
        } else {
        }
    }

    private void handleSwipe(Map<String, Object> parameters) {
        String direction = normalizeDirection(getStringParam(parameters, "direction"));
        if (!hasText(direction)) {
            showMessage("Which direction?");
            return;
        }

        MyAccessibilityService service = MyAccessibilityService.getInstance();
        if (service == null) {
            showMessage("Accessibility service is not connected");
            return;
        }

        if (!service.swipe(direction)) {
            showMessage("Swipe direction not supported");
        } else {
        }
    }

    private void handleVolume(Map<String, Object> parameters) {
        String volumeAction = normalizeDirection(getStringParam(parameters, "volume_action"));
        if (!hasText(volumeAction)) {
            showMessage("Should I increase, decrease, mute, or unmute the volume?");
            return;
        }

        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager == null) {
            showMessage("Volume control is unavailable");
            return;
        }

        switch (volumeAction) {
            case "increase":
                audioManager.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI);
                break;
            case "decrease":
                audioManager.adjustVolume(AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI);
                break;
            case "mute":
                audioManager.adjustVolume(AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI);
                break;
            case "unmute":
                audioManager.adjustVolume(AudioManager.ADJUST_UNMUTE, AudioManager.FLAG_SHOW_UI);
                break;
            default:
                showMessage("Volume action not supported");
                break;
        }
    }

    private void handleGoHome() {
        MyAccessibilityService service = MyAccessibilityService.getInstance();
        if (service == null) {
            showMessage("Accessibility service is not connected");
            return;
        }
        service.performHome();
    }

    private void handleSetTimer(Map<String, Object> parameters) {
        int durationValue = getIntParam(parameters, "duration_value");
        String durationUnit = getStringParam(parameters, "duration_unit");
        int durationSeconds = getIntParam(parameters, "duration_seconds");

        if (durationValue <= 0 || !hasText(durationUnit)) {
            showMessage("How long should I set the timer for?");
            return;
        }

        if (durationSeconds <= 0) {
            showMessage("Timer duration is missing");
            return;
        }

        Intent intent = new Intent(AlarmClock.ACTION_SET_TIMER)
                .putExtra(AlarmClock.EXTRA_LENGTH, durationSeconds)
                .putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
        } catch (Exception exception) {
            Log.e(TAG, "Failed to set timer", exception);
            showMessage("Timer app is unavailable");
        }
    }

    private void handleTakePhoto() {
        MyAccessibilityService service = MyAccessibilityService.getInstance();
        if (service != null && service.capturePhoto()) {
            return;
        }

        Intent intent = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
        } catch (Exception exception) {
            Log.e(TAG, "Failed to open camera", exception);
            showMessage("Camera unavailable");
        }
    }

    private void stopListening() {
        MyAccessibilityService service = MyAccessibilityService.getInstance();
        if (service != null) {
            service.stopContinuousListening();
        }
        AssistantSession.endSession();

        MainActivity mainActivity = MainActivity.getInstance();
        if (mainActivity != null) {
            mainActivity.syncListeningUiState();
        }
    }

    private void showMessage(String message) {
        if (!hasText(message)) {
            return;
        }

        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }

    private boolean errorEquals(PredictResponse response, String errorCode) {
        return response.getErrorCode() != null && response.getErrorCode().equalsIgnoreCase(errorCode);
    }

    private String getStringParam(Map<String, Object> params, String key) {
        return PredictResponse.getStringParam(params, key);
    }

    private int getIntParam(Map<String, Object> params, String key) {
        return PredictResponse.getIntParam(params, key);
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

    private String joinSpelledCandidate(String text) {
        if (!hasText(text)) {
            return null;
        }
        return normalizeAsciiText(text).replace(" ", "");
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

    private String joinList(List<String> values) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (!hasText(value)) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(value);
        }
        return builder.toString();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static class AppMatch {
        private final String label;
        private final String packageName;
        private final float score;
        private final boolean exact;

        private AppMatch(String label, String packageName, float score, boolean exact) {
            this.label = label;
            this.packageName = packageName;
            this.score = score;
            this.exact = exact;
        }
    }
}
