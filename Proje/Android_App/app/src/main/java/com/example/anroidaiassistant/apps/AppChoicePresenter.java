package com.example.anroidaiassistant.apps;

import android.content.Context;

import com.example.anroidaiassistant.MyAccessibilityService;
import com.example.anroidaiassistant.executor.CommandExecutionContext;

import java.util.ArrayList;
import java.util.List;

public final class AppChoicePresenter {
    public interface SelectionCallback {
        void onSelected(AppMatch match);
    }

    private final AppLauncher appLauncher;

    public AppChoicePresenter(AppLauncher appLauncher) {
        this.appLauncher = appLauncher;
    }

    public void showAppChoice(
            Context context,
            List<AppMatch> matches,
            String appName,
            CommandExecutionContext executionContext,
            SelectionCallback callback
    ) {
        MyAccessibilityService service = MyAccessibilityService.getInstance();
        if (service == null || !service.isContinuousListeningActive()) {
            executionContext.showMessage("Multiple apps match. Start listening and say the number.");
            return;
        }

        int choiceCount = Math.min(matches.size(), AppMatcher.MAX_APP_CHOICES);
        List<MyAccessibilityService.NumberedChoice> choices = new ArrayList<>();
        for (int i = 0; i < choiceCount; i++) {
            AppMatch match = matches.get(i);
            choices.add(new MyAccessibilityService.NumberedChoice(
                    match.getLabel(),
                    appLauncher.buildAppChoiceSubtitle(match.getPackageName()),
                    appLauncher.getAppIcon(context, match.getPackageName())
            ));
        }

        service.startNumberSelection(
                buildAppChoiceTitle(matches, appName),
                choices,
                new MyAccessibilityService.NumberSelectionCallback() {
                    @Override
                    public void onSelected(int selectedIndex) {
                        callback.onSelected(matches.get(selectedIndex));
                    }

                    @Override
                    public void onCancelled() {
                        executionContext.showMessage("App selection cancelled");
                    }
                }
        );
    }

    private String buildAppChoiceTitle(List<AppMatch> matches, String appName) {
        if (matches.size() == 1) {
            return "Benzer uygulama bulundu: " + appName;
        }
        return "Birden fazla uygulama bulundu: " + appName;
    }
}