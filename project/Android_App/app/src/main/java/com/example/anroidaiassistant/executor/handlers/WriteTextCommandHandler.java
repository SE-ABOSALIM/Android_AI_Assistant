package com.example.anroidaiassistant.executor.handlers;

import com.example.anroidaiassistant.MyAccessibilityService;
import com.example.anroidaiassistant.executor.CommandExecutionContext;
import com.example.anroidaiassistant.executor.CommandHandler;
import com.example.anroidaiassistant.util.ParameterReader;
import com.example.anroidaiassistant.util.TextNormalizer;

import java.util.Map;

public final class WriteTextCommandHandler implements CommandHandler {
    @Override
    public String getIntent() {
        return "WRITE_TEXT";
    }

    @Override
    public void handle(Map<String, Object> parameters, CommandExecutionContext context) {
        String text = normalizeSpelledSequence(ParameterReader.getStringParam(parameters, "text"));
        if (!TextNormalizer.hasText(text)) {
            context.showMessage("What should I write?");
            return;
        }

        MyAccessibilityService service = MyAccessibilityService.getInstance();
        if (service == null) {
            context.showMessage("Accessibility service is not connected");
            return;
        }

        if (!service.writeTextToFocusedInput(text)) {
            context.showMessage("Text field not found");
        }
    }

    static String normalizeSpelledSequence(String text) {
        if (text == null) {
            return null;
        }

        String candidate = text.trim();
        String[] tokens = candidate.split("\\s+");
        if (tokens.length < 2) {
            return text;
        }

        StringBuilder joined = new StringBuilder(candidate.length());
        for (String token : tokens) {
            if (token.codePointCount(0, token.length()) != 1
                    || !Character.isLetter(token.codePointAt(0))) {
                return text;
            }
            joined.append(token);
        }
        return joined.toString();
    }
}
