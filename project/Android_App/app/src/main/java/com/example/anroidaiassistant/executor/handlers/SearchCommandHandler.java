package com.example.anroidaiassistant.executor.handlers;

import com.example.anroidaiassistant.MyAccessibilityService;
import com.example.anroidaiassistant.executor.CommandExecutionContext;
import com.example.anroidaiassistant.executor.CommandHandler;
import com.example.anroidaiassistant.util.ParameterReader;
import com.example.anroidaiassistant.util.TextNormalizer;

import java.util.Map;

public final class SearchCommandHandler implements CommandHandler {
    @Override
    public String getIntent() {
        return "SEARCH_QUERY";
    }

    @Override
    public void handle(Map<String, Object> parameters, CommandExecutionContext context) {
        String query = ParameterReader.getStringParam(parameters, "query");
        if (!TextNormalizer.hasText(query)) {
            context.showMessage("What should I search for?");
            return;
        }

        MyAccessibilityService service = MyAccessibilityService.getInstance();
        if (service == null) {
            context.showMessage("Accessibility service is not connected");
            return;
        }

        if (!service.performSearchQuery(query)) {
            context.showMessage("Search field not found");
        }
    }
}
