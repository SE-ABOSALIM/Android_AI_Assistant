package com.example.anroidaiassistant.executor.handlers;

import com.example.anroidaiassistant.MyAccessibilityService;
import com.example.anroidaiassistant.executor.CommandExecutionContext;
import com.example.anroidaiassistant.executor.CommandHandler;
import com.example.anroidaiassistant.util.ParameterReader;
import com.example.anroidaiassistant.util.TextNormalizer;

import java.util.Locale;
import java.util.Map;

public final class ScrollCommandHandler implements CommandHandler {
    @Override
    public String getIntent() {
        return "SCROLL_SCREEN";
    }

    @Override
    public void handle(Map<String, Object> parameters, CommandExecutionContext context) {
        String direction = normalizeDirection(ParameterReader.getStringParam(parameters, "direction"));
        if (!TextNormalizer.hasText(direction)) {
            context.showMessage("Which direction?");
            return;
        }

        MyAccessibilityService service = MyAccessibilityService.getInstance();
        if (service == null) {
            context.showMessage("Accessibility service is not connected");
            return;
        }

        if (!service.scroll(direction)) {
            context.showMessage("Cannot scroll " + direction);
        }
    }

    private String normalizeDirection(String direction) {
        if (direction == null) {
            return "";
        }
        return direction.trim().toLowerCase(Locale.US);
    }
}