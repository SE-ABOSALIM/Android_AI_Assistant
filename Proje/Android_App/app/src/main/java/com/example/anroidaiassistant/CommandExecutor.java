package com.example.anroidaiassistant;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.net.Uri;
import android.provider.AlarmClock;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.telecom.TelecomManager;
import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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

            case "CALL_CONTACT":
                handleCallContact(parameters);
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

        if (errorEquals(response, "APP_MATCH_AMBIGUOUS")
                || errorEquals(response, "APP_NOT_IN_CATALOG")
                || errorEquals(response, "APP_MATCH_NOT_FOUND")) {
            if (handleBackendAppCandidates(response)) {
                return;
            }
            showMessage(firstNonEmpty(response.getErrorMessage(), "App not found"));
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
        if (containsSlot(missingSlots, "contact_name")) {
            return "Who should I call?";
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

    private boolean handleBackendAppCandidates(PredictResponse response) {
        Map<String, Object> parameters = response.getParameters();
        List<AppMatch> matches = getBackendAppMatchCandidates(parameters);
        if (matches.isEmpty()) {
            return false;
        }

        showAppChoice(matches, firstNonEmpty(getStringParam(parameters, "app_name"), "app"));
        return true;
    }

    private List<AppMatch> getBackendAppMatchCandidates(Map<String, Object> parameters) {
        if (parameters == null || !(parameters.get("app_match_candidates") instanceof List<?>)) {
            return Collections.emptyList();
        }

        List<AppMatch> matches = new ArrayList<>();
        for (Object rawCandidate : (List<?>) parameters.get("app_match_candidates")) {
            if (!(rawCandidate instanceof Map<?, ?>)) {
                continue;
            }

            Map<?, ?> candidate = (Map<?, ?>) rawCandidate;
            String label = stringValue(candidate.get("label"));
            String packageName = stringValue(candidate.get("package_name"));
            if (!hasText(label) || !hasText(packageName)) {
                continue;
            }

            matches.add(new AppMatch(
                    label,
                    packageName,
                    floatValue(candidate.get("score"), 0.0f),
                    false
            ));
        }

        return matches;
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
        MyAccessibilityService service = MyAccessibilityService.getInstance();
        if (service == null || !service.isContinuousListeningActive()) {
            showMessage("Multiple apps match. Start listening and say the number.");
            return;
        }

        int choiceCount = Math.min(matches.size(), MAX_APP_CHOICES);
        List<MyAccessibilityService.NumberedChoice> choices = new ArrayList<>();
        for (int i = 0; i < choiceCount; i++) {
            AppMatch match = matches.get(i);
            choices.add(new MyAccessibilityService.NumberedChoice(
                    match.label,
                    buildAppChoiceSubtitle(match.packageName),
                    getAppIcon(match.packageName)
            ));
        }

        service.startNumberSelection(
                "Birden fazla uygulama bulundu: " + appName,
                choices,
                new MyAccessibilityService.NumberSelectionCallback() {
                    @Override
                    public void onSelected(int selectedIndex) {
                        AppMatch selectedMatch = matches.get(selectedIndex);
                        launchPackage(selectedMatch.packageName, selectedMatch.label);
                    }

                    @Override
                    public void onCancelled() {
                        showMessage("App selection cancelled");
                    }
                }
        );
    }

    private Drawable getAppIcon(String packageName) {
        if (!hasText(packageName)) {
            return null;
        }

        try {
            return context.getPackageManager().getApplicationIcon(packageName);
        } catch (Exception exception) {
            Log.w(TAG, "Could not load app icon: " + packageName, exception);
            return null;
        }
    }

    private String buildAppChoiceSubtitle(String packageName) {
        String source = inferReadableAppSource(packageName);
        return hasText(source) ? source : "Installed app";
    }

    private String inferReadableAppSource(String packageName) {
        if (!hasText(packageName)) {
            return "";
        }

        String normalized = packageName.toLowerCase(Locale.US);
        if (normalized.startsWith("com.google.") || normalized.contains(".google.")) {
            return "Google";
        }
        if (normalized.startsWith("com.microsoft.")
                || normalized.startsWith("com.azure.")
                || normalized.contains(".microsoft.")
                || normalized.contains(".azure.")) {
            return "Microsoft";
        }
        if (normalized.startsWith("com.facebook.")
                || normalized.startsWith("com.instagram.")
                || normalized.startsWith("com.whatsapp.")) {
            return "Meta";
        }
        if (normalized.startsWith("org.telegram.") || normalized.contains(".telegram.")) {
            return "Telegram";
        }
        if (normalized.startsWith("com.spotify.")) {
            return "Spotify";
        }
        if (normalized.startsWith("com.netflix.")) {
            return "Netflix";
        }
        if (normalized.contains("turktelekom") || normalized.contains("turk.telekom")) {
            return "Turk Telekom";
        }
        if (normalized.startsWith("com.android.")) {
            return "System";
        }
        return "";
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
                audioManager.adjustStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_RAISE,
                        AudioManager.FLAG_SHOW_UI
                );
                break;
            case "decrease":
                audioManager.adjustStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_LOWER,
                        AudioManager.FLAG_SHOW_UI
                );
                break;
            case "mute":
                audioManager.adjustStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_MUTE,
                        AudioManager.FLAG_SHOW_UI
                );
                break;
            case "unmute":
                audioManager.adjustStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_UNMUTE,
                        AudioManager.FLAG_SHOW_UI
                );
                break;
            default:
                showMessage("Volume action not supported");
                break;
        }
    }

    private void handleCallContact(Map<String, Object> parameters) {
        String contactName = getStringParam(parameters, "contact_name");
        if (!hasText(contactName)) {
            showMessage("Who should I call?");
            return;
        }

        if (looksLikePhoneNumber(contactName)) {
            callPhoneNumber(normalizePhoneNumber(contactName));
            return;
        }

        List<ContactPhoneMatch> contactPhoneMatches = findContactPhoneMatches(contactName);
        if (contactPhoneMatches.isEmpty()) {
            showMessage("Contact not found or has no phone number: " + contactName);
            return;
        }

        if (contactPhoneMatches.size() == 1) {
            callPhoneNumber(contactPhoneMatches.get(0).phoneNumber);
            return;
        }

        showContactChoice(contactPhoneMatches, contactName);
    }

    private List<ContactPhoneMatch> findContactPhoneMatches(String contactName) {
        if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED) {
            showMessage("Contact permission is required to call contacts");
            return Collections.emptyList();
        }

        String[] projection = {
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
        };

        Map<String, ContactPhoneMatch> uniqueMatches = new LinkedHashMap<>();
        try (Cursor cursor = context.getContentResolver().query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                null
        )) {
            if (cursor == null) {
                return Collections.emptyList();
            }

            int displayNameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
            int numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
            if (displayNameIndex < 0 || numberIndex < 0) {
                return Collections.emptyList();
            }

            while (cursor.moveToNext()) {
                String displayName = cursor.getString(displayNameIndex);
                String phoneNumber = cursor.getString(numberIndex);
                if (!hasText(displayName) || !hasText(phoneNumber)) {
                    continue;
                }

                int score = scoreContactName(contactName, displayName);
                if (score < 80) {
                    continue;
                }

                String normalizedPhoneNumber = normalizePhoneNumber(phoneNumber);
                String key = normalizeAsciiText(displayName) + ":" + normalizedPhoneNumber;
                ContactPhoneMatch existing = uniqueMatches.get(key);
                if (existing == null || score > existing.score) {
                    uniqueMatches.put(key, new ContactPhoneMatch(displayName, normalizedPhoneNumber, score));
                }
            }
        } catch (Exception exception) {
            Log.e(TAG, "Failed to query contacts", exception);
            return Collections.emptyList();
        }

        List<ContactPhoneMatch> matches = new ArrayList<>(uniqueMatches.values());
        matches.sort((left, right) -> {
            int scoreCompare = Integer.compare(right.score, left.score);
            if (scoreCompare != 0) {
                return scoreCompare;
            }
            return left.displayName.compareToIgnoreCase(right.displayName);
        });

        if (matches.size() > MAX_APP_CHOICES) {
            return new ArrayList<>(matches.subList(0, MAX_APP_CHOICES));
        }
        return matches;
    }

    private void showContactChoice(List<ContactPhoneMatch> matches, String contactName) {
        MyAccessibilityService service = MyAccessibilityService.getInstance();
        if (service == null || !service.isContinuousListeningActive()) {
            showMessage("Multiple contacts match. Start listening and say the number.");
            return;
        }

        List<MyAccessibilityService.NumberedChoice> choices = new ArrayList<>();
        for (ContactPhoneMatch match : matches) {
            choices.add(new MyAccessibilityService.NumberedChoice(
                    match.displayName,
                    formatPhoneNumberForDisplay(match.phoneNumber)
            ));
        }

        service.startNumberSelection(
                "Birden fazla kisi bulundu: " + contactName,
                choices,
                new MyAccessibilityService.NumberSelectionCallback() {
                    @Override
                    public void onSelected(int selectedIndex) {
                        callPhoneNumber(matches.get(selectedIndex).phoneNumber);
                    }

                    @Override
                    public void onCancelled() {
                        showMessage("Contact selection cancelled");
                    }
                }
        );
    }

    private int scoreContactName(String candidateName, String displayName) {
        String candidate = normalizeContactQuery(candidateName);
        String contact = normalizeAsciiText(displayName);
        if (!hasText(candidate) || !hasText(contact)) {
            return 0;
        }

        if (candidate.equals(contact)) {
            return 100;
        }
        return 0;
    }

    private String normalizeContactQuery(String value) {
        String normalized = normalizeAsciiText(value);
        return normalized
                .replaceAll("\\s+(i|yi|u|yu|e|ye|a|ya)$", "")
                .trim();
    }

    private void callPhoneNumber(String phoneNumber) {
        if (!hasText(phoneNumber)) {
            showMessage("Contact has no phone number");
            return;
        }

        String dialerPackageName = getDefaultDialerPackageName();
        boolean canCallDirectly = context.checkSelfPermission(Manifest.permission.CALL_PHONE)
                == PackageManager.PERMISSION_GRANTED;

        if (canCallDirectly) {
            Intent callIntent = buildPhoneIntent(Intent.ACTION_CALL, phoneNumber);
            if (tryStartPhoneIntentWithResolvedPackage(callIntent, dialerPackageName)) {
                return;
            }
        }

        Intent dialIntent = buildPhoneIntent(Intent.ACTION_DIAL, phoneNumber);
        if (tryStartPhoneIntentWithResolvedPackage(dialIntent, dialerPackageName)) {
            if (!canCallDirectly) {
                showMessage("Phone call permission is required");
            }
            return;
        }

        showMessage("Could not start phone call");
    }

    private Intent buildPhoneIntent(String action, String phoneNumber) {
        Intent intent = new Intent(action, Uri.fromParts("tel", phoneNumber, null));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }

    private boolean tryStartPhoneIntentWithResolvedPackage(Intent intent, String preferredPackageName) {
        for (String packageName : getPhoneIntentPackageCandidates(intent, preferredPackageName)) {
            Intent packagedIntent = new Intent(intent);
            packagedIntent.setPackage(packageName);
            if (tryStartPhoneIntent(packagedIntent)) {
                return true;
            }
        }
        return false;
    }

    private List<String> getPhoneIntentPackageCandidates(Intent intent, String preferredPackageName) {
        Set<String> packageNames = new LinkedHashSet<>();
        addPhonePackageCandidate(packageNames, preferredPackageName);
        addPhonePackageCandidate(packageNames, "com.samsung.android.dialer");
        addPhonePackageCandidate(packageNames, "com.google.android.dialer");
        addPhonePackageCandidate(packageNames, "com.android.dialer");
        addPhonePackageCandidate(packageNames, "com.android.contacts");
        addPhonePackageCandidate(packageNames, "com.google.android.contacts");

        PackageManager packageManager = context.getPackageManager();
        List<ResolveInfo> handlers = packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
        for (ResolveInfo handler : handlers) {
            if (isSystemPhoneHandler(handler)) {
                addPhonePackageCandidate(packageNames, handler.activityInfo.packageName);
            }
        }

        return new ArrayList<>(packageNames);
    }

    private void addPhonePackageCandidate(Set<String> packageNames, String packageName) {
        if (hasText(packageName)) {
            packageNames.add(packageName);
        }
    }

    private boolean isSystemPhoneHandler(ResolveInfo handler) {
        if (handler == null || handler.activityInfo == null || handler.activityInfo.applicationInfo == null) {
            return false;
        }

        int flags = handler.activityInfo.applicationInfo.flags;
        return (flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                || (flags & android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
    }

    private boolean tryStartPhoneIntent(Intent intent) {
        try {
            context.startActivity(intent);
            return true;
        } catch (Exception exception) {
            Log.w(TAG, "Failed to start phone intent: " + intent.getAction(), exception);
            return false;
        }
    }

    private String getDefaultDialerPackageName() {
        try {
            TelecomManager telecomManager = (TelecomManager) context.getSystemService(Context.TELECOM_SERVICE);
            if (telecomManager != null) {
                return telecomManager.getDefaultDialerPackage();
            }
        } catch (Exception exception) {
            Log.w(TAG, "Could not resolve default dialer package", exception);
        }
        return null;
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
        } else {
            String endedSessionId = AssistantSession.endSession();
            ApiService apiService = RetrofitClient.getClient().create(ApiService.class);
            AppCatalogSyncer.closeSession(apiService, endedSessionId);
        }

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

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private float floatValue(Object value, float defaultValue) {
        if (value instanceof Number) {
            return ((Number) value).floatValue();
        }

        try {
            return Float.parseFloat(String.valueOf(value));
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private boolean looksLikePhoneNumber(String value) {
        if (!hasText(value) || !value.matches(".*\\d.*")) {
            return false;
        }

        String normalized = normalizePhoneNumber(value);
        return normalized.replace("+", "").length() >= 3;
    }

    private String normalizePhoneNumber(String value) {
        return value == null ? "" : value.replaceAll("[^0-9+]", "");
    }

    private String formatPhoneNumberForDisplay(String value) {
        return hasText(value) ? value : "";
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

    private static class ContactPhoneMatch {
        private final String displayName;
        private final String phoneNumber;
        private final int score;

        private ContactPhoneMatch(String displayName, String phoneNumber, int score) {
            this.displayName = displayName;
            this.phoneNumber = phoneNumber;
            this.score = score;
        }
    }
}
