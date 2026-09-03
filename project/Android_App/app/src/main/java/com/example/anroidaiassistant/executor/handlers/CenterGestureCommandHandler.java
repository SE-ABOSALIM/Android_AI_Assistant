package com.example.anroidaiassistant.executor.handlers;

import com.example.anroidaiassistant.MyAccessibilityService;
import com.example.anroidaiassistant.executor.CommandExecutionContext;
import com.example.anroidaiassistant.executor.CommandHandler;
import com.example.anroidaiassistant.util.ParameterReader;
import com.example.anroidaiassistant.util.TextNormalizer;

import java.util.Map;
import java.util.function.Supplier;

public final class CenterGestureCommandHandler implements CommandHandler {
    public enum Action {
        DOUBLE_TAP,
        LONG_PRESS
    }

    public interface GestureAccess {
        boolean longPressCenter();
        boolean doubleTapCenter();
        boolean longPressTarget(String targetText, String position);
        boolean doubleTapTarget(String targetText, String position);
    }

    private final String intent;
    private final Action action;
    private final Supplier<GestureAccess> gestureAccessProvider;

    public CenterGestureCommandHandler(String intent, Action action) {
        this(intent, action, MyAccessibilityService::getInstance);
    }

    CenterGestureCommandHandler(
            String intent,
            Action action,
            Supplier<GestureAccess> gestureAccessProvider
    ) {
        this.intent = intent;
        this.action = action;
        this.gestureAccessProvider = gestureAccessProvider;
    }

    @Override
    public String getIntent() {
        return intent;
    }

    @Override
    public void handle(Map<String, Object> parameters, CommandExecutionContext context) {
        GestureAccess service = gestureAccessProvider.get();
        if (service == null) {
            context.showMessage("Accessibility service is not connected");
            return;
        }

        String targetText = ParameterReader.getStringParam(parameters, "target_text");
        boolean hasExplicitTarget = TextNormalizer.hasText(targetText);
        boolean handled;
        if (hasExplicitTarget) {
            String position = ParameterReader.getStringParam(parameters, "position");
            handled = action == Action.DOUBLE_TAP
                    ? service.doubleTapTarget(targetText, position)
                    : service.longPressTarget(targetText, position);
        } else {
            handled = action == Action.DOUBLE_TAP
                    ? service.doubleTapCenter()
                    : service.longPressCenter();
        }

        if (!handled) {
            context.showMessage(hasExplicitTarget ? "Item not found" : "Gesture could not be performed");
        }
    }
}
