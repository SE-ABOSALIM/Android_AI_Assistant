package com.example.anroidaiassistant.executor.handlers;

import com.example.anroidaiassistant.MyAccessibilityService;
import com.example.anroidaiassistant.executor.CommandExecutionContext;
import com.example.anroidaiassistant.executor.CommandHandler;

import java.util.Map;

public final class CenterGestureCommandHandler implements CommandHandler {
    public enum Action {
        DOUBLE_TAP,
        LONG_PRESS
    }

    private final String intent;
    private final Action action;

    public CenterGestureCommandHandler(String intent, Action action) {
        this.intent = intent;
        this.action = action;
    }

    @Override
    public String getIntent() {
        return intent;
    }

    @Override
    public void handle(Map<String, Object> parameters, CommandExecutionContext context) {
        MyAccessibilityService service = MyAccessibilityService.getInstance();
        if (service == null) {
            context.showMessage("Accessibility service is not connected");
            return;
        }

        boolean handled = action == Action.DOUBLE_TAP
                ? service.doubleTapCenter()
                : service.longPressCenter();

        if (!handled) {
            context.showMessage("Gesture could not be performed");
        }
    }
}
