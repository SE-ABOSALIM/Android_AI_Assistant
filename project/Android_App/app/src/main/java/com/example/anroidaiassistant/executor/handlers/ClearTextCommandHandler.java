package com.example.anroidaiassistant.executor.handlers;

import com.example.anroidaiassistant.MyAccessibilityService;
import com.example.anroidaiassistant.executor.CommandExecutionContext;
import com.example.anroidaiassistant.executor.CommandHandler;

import java.util.Map;

public final class ClearTextCommandHandler implements CommandHandler {
    @Override
    public String getIntent() {
        return "CLEAR_TEXT";
    }

    @Override
    public void handle(Map<String, Object> parameters, CommandExecutionContext context) {
        MyAccessibilityService service = MyAccessibilityService.getInstance();
        if (service == null) {
            context.showMessage("Accessibility service is not connected");
            return;
        }

        if (!service.clearFocusedInputText()) {
            context.showMessage("Text field not found");
        }
    }
}
