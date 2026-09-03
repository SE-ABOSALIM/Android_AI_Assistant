package com.example.anroidaiassistant.executor.handlers;

import com.example.anroidaiassistant.MyAccessibilityService;
import com.example.anroidaiassistant.executor.CommandExecutionContext;
import com.example.anroidaiassistant.executor.CommandHandler;
import com.example.anroidaiassistant.util.ParameterReader;
import com.example.anroidaiassistant.util.TextNormalizer;

import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

public final class InputFocusCommandHandler implements CommandHandler {
    public interface InputFocusAccess {
        boolean focusInputField();
        boolean focusInputField(String targetText);
        boolean unfocusInputField();
    }

    private final Supplier<InputFocusAccess> inputFocusAccessProvider;

    public InputFocusCommandHandler() {
        this(MyAccessibilityService::getInstance);
    }

    InputFocusCommandHandler(Supplier<InputFocusAccess> inputFocusAccessProvider) {
        this.inputFocusAccessProvider = inputFocusAccessProvider;
    }

    @Override
    public String getIntent() {
        return "SET_INPUT_FOCUS";
    }

    @Override
    public void handle(Map<String, Object> parameters, CommandExecutionContext context) {
        InputFocusAccess service = inputFocusAccessProvider.get();
        if (service == null) {
            context.showMessage("Accessibility service is not connected");
            return;
        }

        String action = ParameterReader.getStringParam(parameters, "focus_action");
        if (isUnfocusAction(action)) {
            if (!service.unfocusInputField()) {
                context.showMessage("Text field not found");
            }
            return;
        }

        String targetText = ParameterReader.getStringParam(parameters, "target_text");
        boolean handled = TextNormalizer.hasText(targetText)
                ? service.focusInputField(targetText)
                : service.focusInputField();
        if (!handled) {
            context.showMessage("Text field not found");
        }
    }

    private boolean isUnfocusAction(String action) {
        String normalized = action == null ? "" : action.trim().toLowerCase(Locale.US);
        return "unfocus".equals(normalized)
                || "clear".equals(normalized)
                || "close".equals(normalized)
                || "off".equals(normalized);
    }
}
