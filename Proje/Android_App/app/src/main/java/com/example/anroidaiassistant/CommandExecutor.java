package com.example.anroidaiassistant;

import com.example.anroidaiassistant.api.dto.PredictResponse;
import com.example.anroidaiassistant.api.ApiService;
import com.example.anroidaiassistant.api.RetrofitClient;
import com.example.anroidaiassistant.api.dto.PredictRequest;
import com.example.anroidaiassistant.apps.AppOpenController;
import com.example.anroidaiassistant.executor.CommandDispatcher;
import com.example.anroidaiassistant.executor.CommandExecutionContext;
import com.example.anroidaiassistant.executor.CommandHandlerRegistry;
import com.example.anroidaiassistant.session.AssistantSession;
import com.example.anroidaiassistant.util.DeviceIdentity;
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

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class CommandExecutor {

    private final Context context;
    private final CommandExecutionContext executionContext;
    private final AppOpenController appOpenController;
    private final CommandDispatcher commandDispatcher;
    private final ApiService apiService;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean isCustomCommandRunning = false;
    private boolean isCustomCommandCancelled = false;
    private Runnable pendingCustomCommandRunnable;
    private Call<PredictResponse> activeCustomStepCall;

    public CommandExecutor(Context context) {
        this.context = context;
        this.executionContext = new CommandExecutionContext(context, this::showMessage);
        this.appOpenController = new AppOpenController();
        this.commandDispatcher = CommandHandlerRegistry.createDefaultDispatcher(appOpenController);
        this.apiService = RetrofitClient.getClient().create(ApiService.class);
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

        clearActiveCustomCommand(false);
        isCustomCommandRunning = true;
        isCustomCommandCancelled = false;
        executeCustomStep(steps, 0);
    }

    private void executeCustomStep(List<Map<String, Object>> steps, int index) {
        if (!isCustomCommandRunning || isCustomCommandCancelled) {
            return;
        }
        if (index >= steps.size()) {
            finishCustomCommand();
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
            scheduleCustomCommandRunnable(() -> executeCustomStep(steps, index + 1), waitMs);
            return;
        }

        if ("SHOW_LABELS".equalsIgnoreCase(intent)) {
            executeLabelsStep(steps, index, parameters, waitAfterMs, stopOnFailure);
            return;
        }

        if (shouldResolveCustomStepThroughBackend(intent)) {
            executeBackendResolvedCustomStep(steps, index, intent, parameters, waitAfterMs, stopOnFailure);
            return;
        }

        boolean dispatched = commandDispatcher.dispatch(intent, parameters, executionContext);
        if (!dispatched && stopOnFailure) {
            failCustomCommand("Custom command step failed: " + intent);
            return;
        }

        scheduleNextCustomStep(steps, index, waitAfterMs);
    }

    private void executeBackendResolvedCustomStep(
            List<Map<String, Object>> steps,
            int index,
            String intent,
            Map<String, Object> parameters,
            int waitAfterMs,
            boolean stopOnFailure
    ) {
        String language = selectedLanguageForCustomCommand();
        String commandText = buildCustomStepCommandText(intent, parameters, language);
        if (!hasText(commandText)) {
            if (stopOnFailure) {
                failCustomCommand("Custom command step failed: " + intent);
                return;
            }
            scheduleNextCustomStep(steps, index, waitAfterMs);
            return;
        }

        PredictRequest request = new PredictRequest(
                commandText,
                language,
                AssistantSession.getSessionId(),
                DeviceIdentity.getDeviceId(context),
                null,
                hasSearchInputForCustomCommand()
        );

        Call<PredictResponse> call = apiService.predict(request);
        activeCustomStepCall = call;
        call.enqueue(new Callback<PredictResponse>() {
            @Override
            public void onResponse(Call<PredictResponse> call, Response<PredictResponse> response) {
                if (activeCustomStepCall == call) {
                    activeCustomStepCall = null;
                }
                if (!isCustomCommandRunning || isCustomCommandCancelled) {
                    return;
                }
                if (!response.isSuccessful() || response.body() == null) {
                    if (stopOnFailure) {
                        failCustomCommand("Custom command step failed: " + intent);
                        return;
                    }
                    scheduleNextCustomStep(steps, index, waitAfterMs);
                    return;
                }

                boolean executed = executeResolvedCustomStep(response.body());
                if (!executed) {
                    if (stopOnFailure) {
                        finishCustomCommand();
                        return;
                    }
                    scheduleNextCustomStep(steps, index, waitAfterMs);
                    return;
                }

                scheduleNextCustomStep(steps, index, waitAfterMs);
            }

            @Override
            public void onFailure(Call<PredictResponse> call, Throwable t) {
                if (activeCustomStepCall == call) {
                    activeCustomStepCall = null;
                }
                if (!isCustomCommandRunning || isCustomCommandCancelled || call.isCanceled()) {
                    return;
                }
                if (stopOnFailure) {
                    failCustomCommand("Custom command step failed: " + intent);
                    return;
                }
                scheduleNextCustomStep(steps, index, waitAfterMs);
            }
        });
    }

    private boolean executeResolvedCustomStep(PredictResponse response) {
        if (response == null) {
            showMessage("No response from backend");
            return false;
        }

        if (!response.isAccepted()) {
            handleRejectedCommand(response);
            return false;
        }

        if (response.isNeedsConfirmation()) {
            handleRejectedCommand(response);
            return false;
        }

        if (errorEquals(response, "LOW_CONFIDENCE")
                || errorEquals(response, "MISSING_REQUIRED_SLOT")
                || errorEquals(response, "UNKNOWN_COMMAND")) {
            handleRejectedCommand(response);
            return false;
        }

        String resolvedIntent = response.getIntent();
        if (!hasText(resolvedIntent)) {
            showMessage("Unsupported command");
            return false;
        }

        if ("UNKNOWN_COMMAND".equalsIgnoreCase(resolvedIntent)) {
            showMessage(firstNonEmpty(response.getErrorMessage(), "Command not supported"));
            return false;
        }

        if ("RUN_CUSTOM_COMMAND".equalsIgnoreCase(resolvedIntent)) {
            showMessage("Nested custom commands are not supported");
            return false;
        }

        if (isGridBlockingTouchIntent(resolvedIntent)) {
            showMessage("Grid acikken dokunmak icin grid numarasi soyle.");
            return false;
        }

        if (commandDispatcher.dispatch(resolvedIntent, response.getParameters(), executionContext)) {
            return true;
        }

        showMessage("Unsupported intent: " + resolvedIntent);
        return false;
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
            failCustomCommand("Accessibility service is not connected");
            return;
        }

        if (!service.showScreenLabels()) {
            if (stopOnFailure) {
                failCustomCommand("No clickable labels found");
                return;
            }
            scheduleNextCustomStep(steps, index, waitAfterMs);
            return;
        }

        int labelNumber = ParameterReader.getIntParam(parameters, "label_number");
        if (labelNumber <= 0) {
            finishCustomCommand();
            return;
        }

        scheduleCustomCommandRunnable(() -> {
            if (!isCustomCommandRunning || isCustomCommandCancelled) {
                return;
            }
            if (!service.selectNumberedChoice(labelNumber) && stopOnFailure) {
                failCustomCommand("Label number could not be selected");
                return;
            }
            scheduleNextCustomStep(steps, index, waitAfterMs);
        }, 350);
    }

    private void scheduleNextCustomStep(List<Map<String, Object>> steps, int index, int waitAfterMs) {
        scheduleCustomCommandRunnable(() -> executeCustomStep(steps, index + 1), waitAfterMs);
    }

    public boolean isCustomCommandRunning() {
        return isCustomCommandRunning;
    }

    public boolean cancelCustomCommand() {
        if (!isCustomCommandRunning) {
            return false;
        }
        clearActiveCustomCommand(true);
        return true;
    }

    private void scheduleCustomCommandRunnable(Runnable action, int delayMillis) {
        if (!isCustomCommandRunning || isCustomCommandCancelled) {
            return;
        }

        Runnable wrapper = new Runnable() {
            @Override
            public void run() {
                if (pendingCustomCommandRunnable == this) {
                    pendingCustomCommandRunnable = null;
                }
                if (!isCustomCommandRunning || isCustomCommandCancelled) {
                    return;
                }
                action.run();
            }
        };
        pendingCustomCommandRunnable = wrapper;
        mainHandler.postDelayed(wrapper, Math.max(0, delayMillis));
    }

    private void failCustomCommand(String message) {
        showMessage(message);
        finishCustomCommand();
    }

    private void finishCustomCommand() {
        clearActiveCustomCommand(false);
    }

    private void clearActiveCustomCommand(boolean notifyCancellation) {
        isCustomCommandCancelled = true;
        isCustomCommandRunning = false;

        if (pendingCustomCommandRunnable != null) {
            mainHandler.removeCallbacks(pendingCustomCommandRunnable);
            pendingCustomCommandRunnable = null;
        }

        if (activeCustomStepCall != null) {
            activeCustomStepCall.cancel();
            activeCustomStepCall = null;
        }

        if (notifyCancellation) {
            showMessage(context.getString(R.string.custom_commands_cancelled));
        }
    }

    private boolean shouldResolveCustomStepThroughBackend(String intent) {
        return "OPEN_APP".equalsIgnoreCase(intent)
                || "CLICK_ITEM".equalsIgnoreCase(intent);
    }

    private String buildCustomStepCommandText(String intent, Map<String, Object> parameters, String language) {
        if ("OPEN_APP".equalsIgnoreCase(intent)) {
            String appName = ParameterReader.getStringParam(parameters, "app_name");
            if (!hasText(appName)) {
                return null;
            }
            if ("AR".equals(language)) {
                return "\u0627\u0641\u062a\u062d " + appName;
            }
            if ("EN".equals(language)) {
                return "open " + appName;
            }
            return appName + " ac";
        }

        if ("SEARCH_QUERY".equalsIgnoreCase(intent)) {
            String query = ParameterReader.getStringParam(parameters, "query");
            if (!hasText(query)) {
                return null;
            }
            if ("AR".equals(language)) {
                return "\u0627\u0628\u062d\u062b \u0639\u0646 " + query;
            }
            if ("EN".equals(language)) {
                return "search " + query;
            }
            return query + " ara";
        }

        if ("CLICK_ITEM".equalsIgnoreCase(intent)) {
            String targetText = ParameterReader.getStringParam(parameters, "target_text");
            if (!hasText(targetText)) {
                return null;
            }
            if ("AR".equals(language)) {
                return "\u0627\u0636\u063a\u0637 \u0639\u0644\u0649 " + targetText;
            }
            if ("EN".equals(language)) {
                return "tap " + targetText;
            }
            return targetText + " bas";
        }

        return null;
    }

    private String selectedLanguageForCustomCommand() {
        MyAccessibilityService service = MyAccessibilityService.getInstance();
        String language = service == null ? null : service.getSelectedLanguage();
        if (!hasText(language)) {
            return "TR";
        }
        language = language.trim().toUpperCase();
        if ("EN".equals(language) || "AR".equals(language) || "TR".equals(language)) {
            return language;
        }
        return "TR";
    }

    private boolean hasSearchInputForCustomCommand() {
        MyAccessibilityService service = MyAccessibilityService.getInstance();
        return service != null && service.hasSearchInputAvailable();
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
