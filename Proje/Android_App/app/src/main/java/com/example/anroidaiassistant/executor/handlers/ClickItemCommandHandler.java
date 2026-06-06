package com.example.anroidaiassistant.executor.handlers;

import com.example.anroidaiassistant.MyAccessibilityService;
import com.example.anroidaiassistant.executor.CommandExecutionContext;
import com.example.anroidaiassistant.executor.CommandHandler;
import com.example.anroidaiassistant.util.ParameterReader;
import com.example.anroidaiassistant.util.TextNormalizer;

import java.util.Map;

public final class ClickItemCommandHandler implements CommandHandler {
    @Override
    public String getIntent() {
        return "CLICK_ITEM";
    }

    @Override
    public void handle(Map<String, Object> parameters, CommandExecutionContext context) {
        String targetText = ParameterReader.getStringParam(parameters, "target_text");
        int targetIndex = ParameterReader.getIntParam(parameters, "target_index");
        if (!TextNormalizer.hasText(targetText) && targetIndex < 1) {
            context.showMessage("What should I tap?");
            return;
        }

        MyAccessibilityService service = MyAccessibilityService.getInstance();
        if (service == null) {
            context.showMessage("Accessibility service is not connected");
            return;
        }

        String position = ParameterReader.getStringParam(parameters, "position");
        if (!service.clickItem(targetText, targetIndex, position)) {
            context.showMessage("Item not found");
        }
    }
}
