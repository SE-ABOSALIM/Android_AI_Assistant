package com.example.anroidaiassistant.apps;

import android.content.Context;

import com.example.anroidaiassistant.MainActivity;
import com.example.anroidaiassistant.api.dto.PredictResponse;
import com.example.anroidaiassistant.executor.CommandExecutionContext;
import com.example.anroidaiassistant.util.ParameterReader;
import com.example.anroidaiassistant.util.TextNormalizer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class AppOpenController {
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
        String appName = ParameterReader.getStringParam(parameters, "app_name");
        if (!hasText(appName)) {
            executionContext.showMessage("Which app should I open?");
            return;
        }

        String packageName = ParameterReader.getStringParam(parameters, "app_package_name");
        if (hasText(packageName)) {
            launchPackage(packageName, appName, executionContext);
            return;
        }

        List<AppMatch> matches = appMatcher.findAppMatches(executionContext.getAndroidContext(), appName);
        if (matches.isEmpty()) {
            onOpenAppFailure(appName, executionContext);
            return;
        }

        if (matches.size() == 1) {
            launchAppMatch(matches.get(0), executionContext);
            return;
        }

        showAppChoice(matches, appName, executionContext);
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
            showAppChoice(exactMatches, spokenText, executionContext);
            return;
        }

        showAppChoice(matches, spokenText, executionContext);
    }

    public boolean handleBackendAppCandidates(PredictResponse response, CommandExecutionContext executionContext) {
        Map<String, Object> parameters = response.getParameters();
        List<AppMatch> matches = getBackendAppMatchCandidates(parameters);
        if (matches.isEmpty()) {
            return false;
        }

        showAppChoice(
                matches,
                firstNonEmpty(ParameterReader.getStringParam(parameters, "app_name"), "app"),
                executionContext
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
            CommandExecutionContext executionContext
    ) {
        appChoicePresenter.showAppChoice(
                executionContext.getAndroidContext(),
                matches,
                appName,
                executionContext,
                match -> launchAppMatch(match, executionContext)
        );
    }

    private void launchAppMatch(AppMatch match, CommandExecutionContext executionContext) {
        launchPackage(match.getPackageName(), match.getLabel(), executionContext);
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