package com.example.anroidaiassistant;

import com.example.anroidaiassistant.api.dto.PredictResponse;
import com.example.anroidaiassistant.apps.AppOpenController;
import com.example.anroidaiassistant.executor.CommandDispatcher;
import com.example.anroidaiassistant.executor.CommandExecutionContext;
import com.example.anroidaiassistant.executor.CommandHandlerRegistry;
import com.example.anroidaiassistant.util.ParameterReader;
import com.example.anroidaiassistant.util.TextNormalizer;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.telecom.TelecomManager;
import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class CommandExecutor {

    private static final String TAG = "PredictResponse";
    private static final int MAX_APP_CHOICES = 5;
    private static final int MIN_CONTACT_FUZZY_SCORE = 82;
    private static final int CONTACT_CONTAINS_QUERY_SCORE = 90;
    private static final int CONTACT_CONTAINS_QUERY_PHRASE_SCORE = 93;

    private final Context context;
    private final CommandExecutionContext executionContext;
    private final AppOpenController appOpenController;
    private final CommandDispatcher commandDispatcher;

    public CommandExecutor(Context context) {
        this.context = context;
        this.executionContext = new CommandExecutionContext(context, this::showMessage);
        this.appOpenController = new AppOpenController();
        this.commandDispatcher = CommandHandlerRegistry.createDefaultDispatcher(appOpenController);
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
        if (commandDispatcher.dispatch(intent, parameters, executionContext)) {
            return;
        }

        switch (intent) {
            case "SCROLL_SCREEN":
                handleScroll(parameters);
                break;

            case "SWIPE_GESTURE":
                handleSwipe(parameters);
                break;

            case "CALL_CONTACT":
                handleCallContact(parameters);
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
            if (appOpenController.handleBackendAppCandidates(response, executionContext)) {
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
        if (containsSlot(missingSlots, "volume_action") || containsSlot(missingSlots, "volume_level")) {
            return "Should I increase, decrease, mute, unmute, or set the volume level?";
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
            for (String part : missingSlot.split("\\|")) {
                if (slot.equalsIgnoreCase(part.trim())) {
                    return true;
                }
            }
        }
        return false;
    }

    public void handleSpelledAppCandidate(String spokenText) {
        appOpenController.handleSpelledAppCandidate(spokenText, executionContext);
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

        List<ContactPhoneMatch> exactContactMatches = exactContactMatches(contactPhoneMatches);
        if (exactContactMatches.size() == 1) {
            callPhoneNumber(exactContactMatches.get(0).phoneNumber);
            return;
        }

        if (exactContactMatches.size() > 1) {
            showContactChoice(exactContactMatches, contactName);
            return;
        }

        showContactChoice(contactPhoneMatches, contactName);
    }

    private List<ContactPhoneMatch> exactContactMatches(List<ContactPhoneMatch> contactPhoneMatches) {
        List<ContactPhoneMatch> exactMatches = new ArrayList<>();
        for (ContactPhoneMatch match : contactPhoneMatches) {
            if (match.exact) {
                exactMatches.add(match);
            }
        }
        return exactMatches;
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

                ContactNameScore contactNameScore = scoreContactName(contactName, displayName);
                if (contactNameScore.score < MIN_CONTACT_FUZZY_SCORE) {
                    continue;
                }

                String normalizedPhoneNumber = normalizePhoneNumber(phoneNumber);
                String key = normalizeAsciiText(displayName) + ":" + normalizedPhoneNumber;
                ContactPhoneMatch existing = uniqueMatches.get(key);
                if (existing == null || contactNameScore.score > existing.score) {
                    uniqueMatches.put(key, new ContactPhoneMatch(
                            displayName,
                            normalizedPhoneNumber,
                            contactNameScore.score,
                            contactNameScore.exact
                    ));
                }
            }
        } catch (Exception exception) {
            Log.e(TAG, "Failed to query contacts", exception);
            return Collections.emptyList();
        }

        List<ContactPhoneMatch> matches = new ArrayList<>(uniqueMatches.values());
        matches.sort((left, right) -> {
            if (left.exact != right.exact) {
                return left.exact ? -1 : 1;
            }
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
                buildContactChoiceTitle(matches, contactName),
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

    private String buildContactChoiceTitle(List<ContactPhoneMatch> matches, String contactName) {
        if (matches.size() == 1 && !matches.get(0).exact) {
            return "Benzer kisi bulundu: " + contactName;
        }
        return "Birden fazla kisi bulundu: " + contactName;
    }

    private ContactNameScore scoreContactName(String candidateName, String displayName) {
        String candidate = normalizeContactQuery(candidateName);
        String contact = normalizeAsciiText(displayName);
        if (!hasText(candidate) || !hasText(contact)) {
            return new ContactNameScore(0, false);
        }

        if (candidate.equals(contact)) {
            return new ContactNameScore(100, true);
        }

        int containedNameScore = scoreContainedContactName(candidate, contact);
        if (containedNameScore > 0) {
            return new ContactNameScore(containedNameScore, false);
        }

        if (isShortInitialSuffixMatch(candidate, contact)
                || isShortInitialSuffixMatch(contact, candidate)) {
            return new ContactNameScore(94, false);
        }

        String candidateCompact = candidate.replace(" ", "");
        String contactCompact = contact.replace(" ", "");
        int fullSimilarity = Math.round(levenshteinSimilarity(candidateCompact, contactCompact) * 100.0f);

        String candidateLoose = looseContactNameKey(candidateCompact);
        String contactLoose = looseContactNameKey(contactCompact);
        int looseSimilarity = Math.round(levenshteinSimilarity(candidateLoose, contactLoose) * 100.0f);

        return new ContactNameScore(Math.max(fullSimilarity, looseSimilarity), false);
    }

    private String normalizeContactQuery(String value) {
        String normalized = normalizeAsciiText(value);
        return normalized
                .replaceAll("\\s+(i|yi|u|yu|e|ye|a|ya)$", "")
                .trim();
    }

    private int scoreContainedContactName(String candidate, String contact) {
        List<String> candidateTokens = orderedTokens(candidate);
        List<String> contactTokens = orderedTokens(contact);
        if (candidateTokens.isEmpty() || contactTokens.isEmpty()) {
            return 0;
        }

        if (containsTokenPhrase(contactTokens, candidateTokens)) {
            return candidateTokens.size() > 1
                    ? CONTACT_CONTAINS_QUERY_PHRASE_SCORE
                    : CONTACT_CONTAINS_QUERY_SCORE;
        }

        String candidateCompact = candidate.replace(" ", "");
        String contactCompact = contact.replace(" ", "");
        if (candidateCompact.length() >= 4 && contactCompact.contains(candidateCompact)) {
            return CONTACT_CONTAINS_QUERY_SCORE;
        }

        return 0;
    }

    private boolean containsTokenPhrase(List<String> haystackTokens, List<String> needleTokens) {
        if (needleTokens.size() > haystackTokens.size()) {
            return false;
        }

        for (int start = 0; start <= haystackTokens.size() - needleTokens.size(); start++) {
            boolean matched = true;
            for (int offset = 0; offset < needleTokens.size(); offset++) {
                if (!haystackTokens.get(start + offset).equals(needleTokens.get(offset))) {
                    matched = false;
                    break;
                }
            }
            if (matched) {
                return true;
            }
        }

        return false;
    }

    private List<String> orderedTokens(String text) {
        List<String> tokens = new ArrayList<>();
        if (!hasText(text)) {
            return tokens;
        }

        for (String token : normalizeText(text).split(" ")) {
            if (hasText(token)) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private boolean isShortInitialSuffixMatch(String shorterName, String longerName) {
        if (!hasText(shorterName) || !hasText(longerName) || shorterName.length() >= longerName.length()) {
            return false;
        }

        if (!longerName.startsWith(shorterName + " ")) {
            return false;
        }

        String suffix = longerName.substring(shorterName.length()).trim();
        if (!hasText(suffix)) {
            return false;
        }

        for (String token : suffix.split(" ")) {
            if (token.length() > 2) {
                return false;
            }
        }
        return true;
    }

    private String looseContactNameKey(String value) {
        if (!hasText(value)) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        char previous = 0;
        for (int i = 0; i < value.length(); i++) {
            char current = normalizeLooseContactChar(value.charAt(i));
            if (current == previous) {
                continue;
            }
            builder.append(current);
            previous = current;
        }
        return builder.toString();
    }

    private char normalizeLooseContactChar(char value) {
        switch (value) {
            case 'o':
            case 'u':
                return 'u';
            default:
                return value;
        }
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
        return ParameterReader.getStringParam(params, key);
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
        return TextNormalizer.normalizeText(text);
    }


    private String normalizeAsciiText(String text) {
        return TextNormalizer.normalizeAsciiText(text);
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
        return TextNormalizer.hasText(value);
    }

    private static class ContactPhoneMatch {
        private final String displayName;
        private final String phoneNumber;
        private final int score;
        private final boolean exact;

        private ContactPhoneMatch(String displayName, String phoneNumber, int score, boolean exact) {
            this.displayName = displayName;
            this.phoneNumber = phoneNumber;
            this.score = score;
            this.exact = exact;
        }
    }

    private static class ContactNameScore {
        private final int score;
        private final boolean exact;

        private ContactNameScore(int score, boolean exact) {
            this.score = score;
            this.exact = exact;
        }
    }

}
