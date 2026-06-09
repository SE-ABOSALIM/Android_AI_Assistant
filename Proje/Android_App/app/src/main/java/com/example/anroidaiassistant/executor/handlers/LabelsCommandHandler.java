package com.example.anroidaiassistant.executor.handlers;

import com.example.anroidaiassistant.MyAccessibilityService;
import com.example.anroidaiassistant.executor.CommandExecutionContext;
import com.example.anroidaiassistant.executor.CommandHandler;

import java.util.Map;

public final class LabelsCommandHandler implements CommandHandler {
    @Override
    public String getIntent() {
        return "SHOW_LABELS";
    }

    @Override
    public void handle(Map<String, Object> parameters, CommandExecutionContext context) {
        MyAccessibilityService service = MyAccessibilityService.getInstance();
        if (service == null) {
            context.showMessage("Accessibility service is not connected");
            return;
        }

        if (!service.showScreenLabels()) {
            context.showMessage("No clickable labels found");
        }
    }
}
