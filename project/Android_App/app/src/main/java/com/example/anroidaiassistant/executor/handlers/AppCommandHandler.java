package com.example.anroidaiassistant.executor.handlers;

import com.example.anroidaiassistant.apps.AppOpenController;
import com.example.anroidaiassistant.executor.CommandExecutionContext;
import com.example.anroidaiassistant.executor.CommandHandler;

import java.util.Map;

public final class AppCommandHandler implements CommandHandler {
    private final String intent;
    private final AppOpenController.AppAction action;
    private final AppOpenController appOpenController;

    public AppCommandHandler(
            String intent,
            AppOpenController.AppAction action,
            AppOpenController appOpenController
    ) {
        this.intent = intent;
        this.action = action;
        this.appOpenController = appOpenController;
    }

    @Override
    public String getIntent() {
        return intent;
    }

    @Override
    public void handle(Map<String, Object> parameters, CommandExecutionContext context) {
        switch (action) {
            case OPEN_INFO:
                appOpenController.handleOpenAppInfo(parameters, context);
                break;
            case UNINSTALL:
                appOpenController.handleUninstallApp(parameters, context);
                break;
            case OPEN:
            default:
                appOpenController.handleOpenApp(parameters, context);
                break;
        }
    }
}
