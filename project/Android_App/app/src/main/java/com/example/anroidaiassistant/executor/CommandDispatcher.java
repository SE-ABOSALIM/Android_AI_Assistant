package com.example.anroidaiassistant.executor;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CommandDispatcher {
    private final Map<String, CommandHandler> handlersByIntent = new HashMap<>();

    public CommandDispatcher(List<CommandHandler> handlers) {
        for (CommandHandler handler : handlers) {
            if (handler == null || handler.getIntent() == null) {
                continue;
            }
            handlersByIntent.put(normalizeIntent(handler.getIntent()), handler);
        }
    }

    public boolean dispatch(String intent, Map<String, Object> parameters, CommandExecutionContext context) {
        CommandHandler handler = handlersByIntent.get(normalizeIntent(intent));
        if (handler == null) {
            return false;
        }

        handler.handle(parameters, context);
        return true;
    }

    private String normalizeIntent(String intent) {
        if (intent == null) {
            return "";
        }
        return intent.trim().toUpperCase(Locale.US);
    }
}