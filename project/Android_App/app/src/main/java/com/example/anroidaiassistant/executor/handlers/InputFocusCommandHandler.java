package com.example.anroidaiassistant.executor.handlers;

import com.example.anroidaiassistant.MyAccessibilityService;
import com.example.anroidaiassistant.executor.CommandExecutionContext;
import com.example.anroidaiassistant.executor.CommandHandler;
import com.example.anroidaiassistant.util.ParameterReader;

import java.util.Locale;
import java.util.Map;

public final class InputFocusCommandHandler implements CommandHandler {
    @Override
    public String getIntent() {
        return "SET_INPUT_FOCUS";
    }

    @Override
    public void handle(Map<String, Object> parameters, CommandExecutionContext context) {
        MyAccessibilityService service = MyAccessibilityService.getInstance();
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

        if (!service.focusInputField()) {
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
