package com.example.anroidaiassistant;

import com.example.anroidaiassistant.api.dto.PredictResponse;
import com.example.anroidaiassistant.apps.AppOpenController;
import com.example.anroidaiassistant.executor.CommandDispatcher;
import com.example.anroidaiassistant.executor.CommandExecutionContext;
import com.example.anroidaiassistant.executor.CommandHandlerRegistry;
import com.example.anroidaiassistant.util.ParameterReader;
import com.example.anroidaiassistant.util.TextNormalizer;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CommandExecutor {

    private final Context context;
    private final CommandExecutionContext executionContext;
    private final AppOpenController appOpenController;
    private final CommandDispatcher commandDispatcher;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

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

        if (isGridBlockingTouchIntent(intent)) {
            showMessage("Grid acikken dokunmak icin grid numarasi soyle.");
            return;
        }

        Map<String, Object> parameters = response.getParameters();
        if ("RUN_CUSTOM_COMMAND".equalsIgnoreCase(intent)) {
            executeCustomCommand(parameters);
            return;
        }

        if (commandDispatcher.dispatch(intent, parameters, executionContext)) {
            return;
        }
        showMessage("Unsupported intent: " + intent);
    }

    private void executeCustomCommand(Map<String, Object> parameters) {
        List<Map<String, Object>> steps = customCommandSteps(parameters);
        if (steps.isEmpty()) {
            showMessage("Custom command has no steps");
            return;
        }

        executeCustomStep(steps, 0);
    }

    private void executeCustomStep(List<Map<String, Object>> steps, int index) {
        if (index >= steps.size()) {
            return;
        }

        Map<String, Object> step = steps.get(index);
        String intent = stringValue(step.get("intent"));
        Map<String, Object> parameters = mapValue(step.get("parameters"));
        int waitAfterMs = intValue(step.get("wait_after_ms"), 0);
        boolean stopOnFailure = booleanValue(step.get("stop_on_failure"), true);

        if (!hasText(intent)) {
            scheduleNextCustomStep(steps, index, waitAfterMs);
            return;
        }

        if ("WAIT".equalsIgnoreCase(intent)) {
            int waitMs = ParameterReader.getIntParam(parameters, "duration_ms");
            mainHandler.postDelayed(
                    () -> executeCustomStep(steps, index + 1),
                    Math.max(0, waitMs)
            );
            return;
        }

        if ("SHOW_LABELS".equalsIgnoreCase(intent)) {
            executeLabelsStep(steps, index, parameters, waitAfterMs, stopOnFailure);
            return;
        }

        boolean dispatched = commandDispatcher.dispatch(intent, parameters, executionContext);
        if (!dispatched && stopOnFailure) {
            showMessage("Custom command step failed: " + intent);
            return;
        }

        scheduleNextCustomStep(steps, index, waitAfterMs);
    }

    private void executeLabelsStep(
            List<Map<String, Object>> steps,
            int index,
            Map<String, Object> parameters,
            int waitAfterMs,
            boolean stopOnFailure
    ) {
        MyAccessibilityService service = MyAccessibilityService.getInstance();
        if (service == null) {
            showMessage("Accessibility service is not connected");
            return;
        }

        if (!service.showScreenLabels()) {
            if (stopOnFailure) {
                showMessage("No clickable labels found");
                return;
            }
            scheduleNextCustomStep(steps, index, waitAfterMs);
            return;
        }

        int labelNumber = ParameterReader.getIntParam(parameters, "label_number");
        if (labelNumber <= 0) {
            return;
        }

        mainHandler.postDelayed(() -> {
            if (!service.selectNumberedChoice(labelNumber) && stopOnFailure) {
                showMessage("Label number could not be selected");
                return;
            }
            scheduleNextCustomStep(steps, index, waitAfterMs);
        }, 350);
    }

    private void scheduleNextCustomStep(List<Map<String, Object>> steps, int index, int waitAfterMs) {
        mainHandler.postDelayed(
                () -> executeCustomStep(steps, index + 1),
                Math.max(0, waitAfterMs)
        );
    }

    private List<Map<String, Object>> customCommandSteps(Map<String, Object> parameters) {
        List<Map<String, Object>> result = new ArrayList<>();
        Object rawSteps = parameters == null ? null : parameters.get("custom_command_steps");
        if (!(rawSteps instanceof List<?>)) {
            return result;
        }

        for (Object rawStep : (List<?>) rawSteps) {
            Map<String, Object> step = mapValue(rawStep);
            if (!step.isEmpty()) {
                result.add(step);
            }
        }
        return result;
    }

    private Map<String, Object> mapValue(Object value) {
        Map<String, Object> result = new HashMap<>();
        if (!(value instanceof Map<?, ?>)) {
            return result;
        }

        for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
            if (entry.getKey() != null) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return result;
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private int intValue(Object value, int defaultValue) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(stringValue(value));
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private boolean booleanValue(Object value, boolean defaultValue) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value == null) {
            return defaultValue;
        }
        return Boolean.parseBoolean(String.valueOf(value));
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

    private boolean isGridBlockingTouchIntent(String intent) {
        if (!isTouchIntent(intent)) {
            return false;
        }

        MyAccessibilityService service = MyAccessibilityService.getInstance();
        return service != null && service.isGridActive();
    }

    private boolean isTouchIntent(String intent) {
        return "CLICK_ITEM".equalsIgnoreCase(intent)
                || "DOUBLE_TAP".equalsIgnoreCase(intent)
                || "HOLD_SCREEN".equalsIgnoreCase(intent);
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
