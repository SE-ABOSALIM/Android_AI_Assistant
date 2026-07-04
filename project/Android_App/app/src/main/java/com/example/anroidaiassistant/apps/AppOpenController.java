package com.example.anroidaiassistant.apps;

import android.content.Context;
import android.graphics.drawable.Drawable;

import com.example.anroidaiassistant.MainActivity;
import com.example.anroidaiassistant.MyAccessibilityService;
import com.example.anroidaiassistant.api.dto.PredictResponse;
import com.example.anroidaiassistant.executor.CommandExecutionContext;
import com.example.anroidaiassistant.util.ParameterReader;
import com.example.anroidaiassistant.util.TextNormalizer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class AppOpenController {
    public enum AppAction {
        OPEN,
        OPEN_INFO,
        UNINSTALL
    }

    private final AppMatcher appMatcher;
    private final SpelledAppMatcher spelledAppMatcher;
    private final AppLauncher appLauncher;
    private final AppChoicePresenter appChoicePresenter;
    private int openAppFailureCount = 0;

    public AppOpenController() {
        this.appMatcher = new AppMatcher();
        this.spelledAppMatcher = new SpelledAppMatcher(appMatcher);
        this.appLauncher = new AppLauncher();
        this.appChoicePresenter = new AppChoicePresenter(appLauncher);
    }

    public void handleOpenApp(Map<String, Object> parameters, CommandExecutionContext executionContext) {
        handleAppCommand(parameters, executionContext, AppAction.OPEN);
    }

    public void handleOpenAppInfo(Map<String, Object> parameters, CommandExecutionContext executionContext) {
        handleAppCommand(parameters, executionContext, AppAction.OPEN_INFO);
    }

    public void handleUninstallApp(Map<String, Object> parameters, CommandExecutionContext executionContext) {
        handleAppCommand(parameters, executionContext, AppAction.UNINSTALL);
    }

    private void handleAppCommand(
            Map<String, Object> parameters,
            CommandExecutionContext executionContext,
            AppAction action
    ) {
        String appName = ParameterReader.getStringParam(parameters, "app_name");
        if (!hasText(appName)) {
            executionContext.showMessage(missingAppNameMessage(action));
            return;
        }

        String packageName = ParameterReader.getStringParam(parameters, "app_package_name");
        if (hasText(packageName)) {
            executePackageAction(packageName, appName, executionContext, action);
            return;
        }

        List<AppMatch> matches = appMatcher.findAppMatches(executionContext.getAndroidContext(), appName);
        if (matches.isEmpty()) {
            onOpenAppFailure(appName, executionContext);
            return;
        }

        if (matches.size() == 1) {
            executeAppMatch(matches.get(0), executionContext, action);
            return;
        }

        showAppChoice(matches, appName, executionContext, action);
    }

    public void handleSpelledAppCandidate(String spokenText, CommandExecutionContext executionContext) {
        if (!hasText(spokenText)) {
            executionContext.showMessage("Which app should I open?");
            return;
        }

        Context context = executionContext.getAndroidContext();
        List<AppMatch> matches = spelledAppMatcher.findSpelledAppMatches(context, spokenText);
        if (matches.isEmpty()) {
            onOpenAppFailure(spokenText, executionContext);
            return;
        }

        List<AppMatch> exactMatches = new ArrayList<>();
        for (AppMatch match : matches) {
            if (match.isExact()) {
                exactMatches.add(match);
            }
        }

        if (exactMatches.size() == 1) {
            launchAppMatch(exactMatches.get(0), executionContext);
            return;
        }

        if (!exactMatches.isEmpty()) {
            showAppChoice(exactMatches, spokenText, executionContext, AppAction.OPEN);
            return;
        }

        showAppChoice(matches, spokenText, executionContext, AppAction.OPEN);
    }

    public boolean handleBackendAppCandidates(PredictResponse response, CommandExecutionContext executionContext) {
        Map<String, Object> parameters = response.getParameters();
        List<AppMatch> matches = getBackendAppMatchCandidates(parameters);
        if (matches.isEmpty()) {
            return false;
        }

        AppAction action = actionFromIntent(response.getIntent());
        showAppChoice(
                matches,
                firstNonEmpty(ParameterReader.getStringParam(parameters, "app_name"), "app"),
                executionContext,
                action
        );
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

    private void showAppChoice(
            List<AppMatch> matches,
            String appName,
            CommandExecutionContext executionContext,
            AppAction action
    ) {
        appChoicePresenter.showAppChoice(
                executionContext.getAndroidContext(),
                matches,
                appName,
                executionContext,
                match -> executeAppMatch(match, executionContext, action)
        );
    }

    private void launchAppMatch(AppMatch match, CommandExecutionContext executionContext) {
        executeAppMatch(match, executionContext, AppAction.OPEN);
    }

    private void executeAppMatch(
            AppMatch match,
            CommandExecutionContext executionContext,
            AppAction action
    ) {
        executePackageAction(match.getPackageName(), match.getLabel(), executionContext, action);
    }

    private void executePackageAction(
            String packageName,
            String label,
            CommandExecutionContext executionContext,
            AppAction action
    ) {
        switch (action) {
            case OPEN_INFO:
                openAppInfo(packageName, label, executionContext);
                break;
            case UNINSTALL:
                confirmAndUninstall(packageName, label, executionContext);
                break;
            case OPEN:
            default:
                launchPackage(packageName, label, executionContext);
                break;
        }
    }

    private void launchPackage(
            String packageName,
            String label,
            CommandExecutionContext executionContext
    ) {
        if (appLauncher.launchPackage(
                executionContext.getAndroidContext(),
                packageName,
                label,
                executionContext
        )) {
            openAppFailureCount = 0;
        }
    }

    private void openAppInfo(
            String packageName,
            String label,
            CommandExecutionContext executionContext
    ) {
        appLauncher.openAppInfo(
                executionContext.getAndroidContext(),
                packageName,
                label,
                executionContext
        );
    }

    private void confirmAndUninstall(
            String packageName,
            String label,
            CommandExecutionContext executionContext
    ) {
        MyAccessibilityService service = MyAccessibilityService.getInstance();
        if (service == null || !service.isContinuousListeningActive()) {
            executionContext.showMessage("Start listening to confirm uninstall.");
            return;
        }

        Context context = executionContext.getAndroidContext();
        String displayLabel = firstNonEmpty(
                appLauncher.getAppLabel(context, packageName, label),
                label,
                packageName
        );
        Drawable icon = appLauncher.getAppIcon(context, packageName);
        service.startUninstallConfirmation(
                displayLabel,
                icon,
                uninstallConfirmationTitle(service.getSelectedLanguage(), firstNonEmpty(displayLabel, "app")),
                confirmationYesText(service.getSelectedLanguage()),
                confirmationNoText(service.getSelectedLanguage()),
                confirmationHintText(service.getSelectedLanguage()),
                new MyAccessibilityService.NumberSelectionCallback() {
                    @Override
                    public void onSelected(int selectedIndex) {
                        if (selectedIndex == 0) {
                            appLauncher.requestUninstallPackage(context, packageName, label, executionContext);
                        } else {
                            executionContext.showMessage("Uninstall cancelled");
                        }
                    }

                    @Override
                    public void onCancelled() {
                        executionContext.showMessage("Uninstall cancelled");
                    }
                }
        );
    }

    private void onOpenAppFailure(String appCandidate, CommandExecutionContext executionContext) {
        openAppFailureCount++;
        executionContext.showMessage("App not found. Please spell the app name.");

        if (openAppFailureCount == 2 || openAppFailureCount == 3) {
            MainActivity mainActivity = MainActivity.getInstance();
            if (mainActivity != null) {
                mainActivity.showSpellingSuggestionDialog();
            }
        }
    }

    private AppAction actionFromIntent(String intent) {
        if ("OPEN_APP_INFO".equalsIgnoreCase(intent)) {
            return AppAction.OPEN_INFO;
        }
        if ("UNINSTALL_APP".equalsIgnoreCase(intent)) {
            return AppAction.UNINSTALL;
        }
        return AppAction.OPEN;
    }

    private String missingAppNameMessage(AppAction action) {
        switch (action) {
            case OPEN_INFO:
                return "Which app info should I open?";
            case UNINSTALL:
                return "Which app should I uninstall?";
            case OPEN:
            default:
                return "Which app should I open?";
        }
    }

    private String uninstallConfirmationTitle(String language, String label) {
        if ("EN".equalsIgnoreCase(language)) {
            return "Do you want to uninstall " + label + "?";
        }
        if ("AR".equalsIgnoreCase(language)) {
            return "\u0647\u0644 \u062a\u0631\u064a\u062f \u0627\u0644\u063a\u0627\u0621 \u062a\u062b\u0628\u064a\u062a " + label + "\u061f";
        }
        return label + " uygulamasini silmek istiyor musunuz?";
    }

    private String confirmationYesText(String language) {
        if ("EN".equalsIgnoreCase(language)) {
            return "Yes";
        }
        if ("AR".equalsIgnoreCase(language)) {
            return "\u0646\u0639\u0645";
        }
        return "Evet";
    }

    private String confirmationNoText(String language) {
        if ("EN".equalsIgnoreCase(language)) {
            return "No";
        }
        if ("AR".equalsIgnoreCase(language)) {
            return "\u0644\u0627";
        }
        return "Hayir";
    }

    private String confirmationHintText(String language) {
        if ("EN".equalsIgnoreCase(language)) {
            return "Say yes or no.";
        }
        if ("AR".equalsIgnoreCase(language)) {
            return "\u0642\u0644 \u0646\u0639\u0645 \u0627\u0648 \u0644\u0627.";
        }
        return "Evet veya hayir deyin.";
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

    private String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return null;
    }

    private boolean hasText(String value) {
        return TextNormalizer.hasText(value);
    }
}
