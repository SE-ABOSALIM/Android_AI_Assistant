package com.example.anroidaiassistant.executor.handlers;

import com.example.anroidaiassistant.apps.AppOpenController;
import com.example.anroidaiassistant.executor.CommandExecutionContext;
import com.example.anroidaiassistant.executor.CommandHandler;

import java.util.Map;

public final class OpenAppCommandHandler implements CommandHandler {
    private final AppOpenController appOpenController;

    public OpenAppCommandHandler(AppOpenController appOpenController) {
        this.appOpenController = appOpenController;
    }

    @Override
    public String getIntent() {
        return "OPEN_APP";
    }

    @Override
    public void handle(Map<String, Object> parameters, CommandExecutionContext context) {
        appOpenController.handleOpenApp(parameters, context);
    }
}