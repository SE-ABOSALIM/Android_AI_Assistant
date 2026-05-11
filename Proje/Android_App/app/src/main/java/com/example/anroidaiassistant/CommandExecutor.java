package com.example.anroidaiassistant;

import com.example.anroidaiassistant.api.dto.PredictResponse;
import com.example.anroidaiassistant.apps.AppOpenController;
import com.example.anroidaiassistant.executor.CommandDispatcher;
import com.example.anroidaiassistant.executor.CommandExecutionContext;
import com.example.anroidaiassistant.executor.CommandHandlerRegistry;
import com.example.anroidaiassistant.util.TextNormalizer;

import android.content.Context;
import android.widget.Toast;

import java.util.List;
import java.util.Map;

public class CommandExecutor {

    private final Context context;
    private final CommandExecutionContext executionContext;
    private final AppOpenController appOpenController;
    private final CommandDispatcher commandDispatcher;

    public CommandExecutor(Context context) {
        this.context = context;
        this.executionContext = new CommandExecutionContext(context, this::showMessage);
        this.appOpenController = new AppOpenController();
        this.commandDispatcher = CommandHandlerRegistry.createDefaultDispatcher(appOpenController);
    }

    public void executeCommand(PredictResponse response) {
        if (response == null) {
            showMessage("No response from backend");
            return;
        }

        if (!response.isAccepted()) {
            handleRejectedCommand(response);
            return;
        }

        if (response.isNeedsConfirmation()) {
            handleRejectedCommand(response);
            return;
        }

        if (errorEquals(response, "LOW_CONFIDENCE")
                || errorEquals(response, "MISSING_REQUIRED_SLOT")
                || errorEquals(response, "UNKNOWN_COMMAND")) {
            handleRejectedCommand(response);
            return;
        }

        String intent = response.getIntent();
        if (!hasText(intent)) {
            showMessage("Unsupported command");
            return;
        }

        if ("UNKNOWN_COMMAND".equalsIgnoreCase(intent)) {
            showMessage(firstNonEmpty(response.getErrorMessage(), "Command not supported"));
            return;
        }

        Map<String, Object> parameters = response.getParameters();
        if (commandDispatcher.dispatch(intent, parameters, executionContext)) {
            return;
        }
        showMessage("Unsupported intent: " + intent);
    }

    private void handleRejectedCommand(PredictResponse response) {
        if (response == null) {
            showMessage("No response from backend");
            return;
        }

        if (errorEquals(response, "APP_MATCH_AMBIGUOUS")
                || errorEquals(response, "APP_NOT_IN_CATALOG")
                || errorEquals(response, "APP_MATCH_NOT_FOUND")) {
            if (appOpenController.handleBackendAppCandidates(response, executionContext)) {
                return;
            }
            showMessage(firstNonEmpty(response.getErrorMessage(), "App not found"));
            return;
        }

        if (response.getMissingSlots() != null && !response.getMissingSlots().isEmpty()) {
            showMessage(buildMissingSlotsMessage(response.getMissingSlots()));
            return;
        }

        if (response.isNeedsConfirmation()) {
            showMessage(firstNonEmpty(response.getErrorMessage(), "Please clarify the command."));
            return;
        }

        if (errorEquals(response, "LOW_CONFIDENCE")) {
            showMessage(firstNonEmpty(response.getErrorMessage(), "Please repeat or clarify."));
            return;
        }

        if (errorEquals(response, "UNKNOWN_COMMAND")) {
            showMessage(firstNonEmpty(response.getErrorMessage(), "Command not supported"));
            return;
        }

        if (errorEquals(response, "MISSING_REQUIRED_SLOT")) {
            showMessage(firstNonEmpty(response.getErrorMessage(), "Please provide the missing information."));
            return;
        }

        showMessage(firstNonEmpty(response.getErrorMessage(), "Command not accepted"));
    }

    private String buildMissingSlotsMessage(List<String> missingSlots) {
        if (containsSlot(missingSlots, "direction")) {
            return "Which direction?";
        }
        if (containsSlot(missingSlots, "app_name")) {
            return "Which app should I open?";
        }
        if (containsSlot(missingSlots, "duration_value") || containsSlot(missingSlots, "duration_unit")) {
            return "How long should I set the timer for?";
        }
        if (containsSlot(missingSlots, "volume_action") || containsSlot(missingSlots, "volume_level")) {
            return "Should I increase, decrease, mute, unmute, or set the volume level?";
        }
        if (containsSlot(missingSlots, "contact_name")) {
            return "Who should I call?";
        }
        return "Please provide: " + joinList(missingSlots);
    }

    private boolean containsSlot(List<String> missingSlots, String slot) {
        for (String missingSlot : missingSlots) {
            if (slot.equalsIgnoreCase(missingSlot)) {
                return true;
            }
            for (String part : missingSlot.split("\\|")) {
                if (slot.equalsIgnoreCase(part.trim())) {
                    return true;
                }
            }
        }
        return false;
    }

    public void handleSpelledAppCandidate(String spokenText) {
        appOpenController.handleSpelledAppCandidate(spokenText, executionContext);
    }

    private void showMessage(String message) {
        if (!hasText(message)) {
            return;
        }

        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }

    private boolean errorEquals(PredictResponse response, String errorCode) {
        return response.getErrorCode() != null && response.getErrorCode().equalsIgnoreCase(errorCode);
    }

    private String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return null;
    }

    private String joinList(List<String> values) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (!hasText(value)) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(value);
        }
        return builder.toString();
    }

    private boolean hasText(String value) {
        return TextNormalizer.hasText(value);
    }
}
