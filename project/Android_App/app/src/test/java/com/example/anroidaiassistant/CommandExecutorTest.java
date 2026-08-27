package com.example.anroidaiassistant;

import static org.junit.Assert.assertEquals;

import com.example.anroidaiassistant.api.dto.PredictResponse;
import com.example.anroidaiassistant.accessibility.consent.AccessibilityAutomationGate;
import com.example.anroidaiassistant.accessibility.consent.AccessibilityConsentState;
import com.example.anroidaiassistant.executor.CommandDispatcher;
import com.example.anroidaiassistant.executor.CommandExecutionContext;
import com.example.anroidaiassistant.executor.CommandHandler;
import com.google.gson.Gson;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class CommandExecutorTest {
    private final Gson gson = new Gson();

    @Test
    public void validSupportedResponseDispatchesExactlyOnce() {
        List<String> dispatchedValues = new ArrayList<>();
        CommandExecutor executor = executorWithRecordingHandler(dispatchedValues, new ArrayList<>());

        executor.executeCommand(acceptedResponse("first"));

        assertEquals(Collections.singletonList("first"), dispatchedValues);
    }

    @Test
    public void separateExecuteCommandCallsRemainIndependent() {
        List<String> dispatchedValues = new ArrayList<>();
        CommandExecutor executor = executorWithRecordingHandler(dispatchedValues, new ArrayList<>());

        executor.executeCommand(acceptedResponse("first"));
        executor.executeCommand(acceptedResponse("second"));

        assertEquals(List.of("first", "second"), dispatchedValues);
    }

    @Test
    public void sameExecutionId_deliveredTwice_dispatchesOnce() {
        List<String> dispatchedValues = new ArrayList<>();
        CommandExecutor executor = executorWithRecordingHandler(dispatchedValues, new ArrayList<>());
        PredictResponse response = acceptedResponse("Chrome");

        executor.executeCommand("execution-1", response);
        executor.executeCommand("execution-1", response);

        assertEquals(Collections.singletonList("Chrome"), dispatchedValues);
    }

    @Test
    public void differentExecutionIds_withIdenticalCommand_dispatchTwice() {
        List<String> dispatchedValues = new ArrayList<>();
        CommandExecutor executor = executorWithRecordingHandler(dispatchedValues, new ArrayList<>());
        PredictResponse response = acceptedResponse("Chrome");

        executor.executeCommand("execution-1", response);
        executor.executeCommand("execution-2", response);

        assertEquals(List.of("Chrome", "Chrome"), dispatchedValues);
    }

    @Test
    public void nullResponseDoesNotDispatchAndReportsCurrentMessage() {
        List<String> dispatchedValues = new ArrayList<>();
        List<String> messages = new ArrayList<>();
        CommandExecutor executor = executorWithRecordingHandler(dispatchedValues, messages);

        executor.executeCommand(null);

        assertEquals(Collections.emptyList(), dispatchedValues);
        assertEquals(Collections.singletonList("No response from backend"), messages);
    }

    @Test
    public void unsupportedIntentDoesNotDispatchAndReportsCurrentMessage() {
        List<String> dispatchedValues = new ArrayList<>();
        List<String> messages = new ArrayList<>();
        CommandExecutor executor = executorWithRecordingHandler(dispatchedValues, messages);

        executor.executeCommand(responseForIntent("UNREGISTERED_INTENT", "ignored"));

        assertEquals(Collections.emptyList(), dispatchedValues);
        assertEquals(Collections.singletonList("Unsupported intent: UNREGISTERED_INTENT"), messages);
    }

    @Test
    public void rejectedResponseDoesNotDispatchAndShowsBackendMessage() {
        List<String> dispatchedValues = new ArrayList<>();
        List<String> messages = new ArrayList<>();
        CommandExecutor executor = executorWithRecordingHandler(dispatchedValues, messages);
        PredictResponse response = gson.fromJson(
                "{\"accepted\":false,\"intent\":\"OPEN_APP\",\"parameters\":{},"
                        + "\"missing_slots\":[],\"error_code\":\"UNSUPPORTED_INTENT\","
                        + "\"error_message\":\"Backend rejected command\"}",
                PredictResponse.class
        );

        executor.executeCommand(response);

        assertEquals(Collections.emptyList(), dispatchedValues);
        assertEquals(Collections.singletonList("Backend rejected command"), messages);
    }

    @Test
    public void missingSlotResponseDoesNotDispatchAndShowsTargetedPrompt() {
        List<String> dispatchedValues = new ArrayList<>();
        List<String> messages = new ArrayList<>();
        CommandExecutor executor = executorWithRecordingHandler(dispatchedValues, messages);
        PredictResponse response = gson.fromJson(
                "{\"accepted\":false,\"intent\":\"SCROLL_SCREEN\",\"parameters\":{},"
                        + "\"missing_slots\":[\"direction\"],"
                        + "\"error_code\":\"MISSING_REQUIRED_SLOT\","
                        + "\"error_message\":\"Required parameter is missing.\"}",
                PredictResponse.class
        );

        executor.executeCommand(response);

        assertEquals(Collections.emptyList(), dispatchedValues);
        assertEquals(Collections.singletonList("Which direction?"), messages);
    }

    @Test
    public void lowConfidenceResponseDoesNotDispatchAndPreservesBackendMessage() {
        List<String> dispatchedValues = new ArrayList<>();
        List<String> messages = new ArrayList<>();
        CommandExecutor executor = executorWithRecordingHandler(dispatchedValues, messages);
        PredictResponse response = gson.fromJson(
                "{\"accepted\":false,\"intent\":\"UNKNOWN_COMMAND\",\"parameters\":{},"
                        + "\"missing_slots\":[],\"error_code\":\"LOW_CONFIDENCE\","
                        + "\"error_message\":\"Please repeat the command.\"}",
                PredictResponse.class
        );

        executor.executeCommand(response);

        assertEquals(Collections.emptyList(), dispatchedValues);
        assertEquals(Collections.singletonList("Please repeat the command."), messages);
    }

    @Test
    public void accessibilityExecutionBlockedWithoutConsent_routesToDisclosure() {
        List<String> dispatchedValues = new ArrayList<>();
        List<String> messages = new ArrayList<>();
        AtomicInteger disclosureRoutes = new AtomicInteger();
        CommandExecutor executor = executorWithGate(
                "CLICK_ITEM",
                false,
                true,
                dispatchedValues,
                messages,
                disclosureRoutes::incrementAndGet
        );

        executor.executeCommand(responseForIntent("CLICK_ITEM", "button"));

        assertEquals(Collections.emptyList(), dispatchedValues);
        assertEquals(1, messages.size());
        assertEquals(1, disclosureRoutes.get());
    }

    @Test
    public void accessibilityExecutionAllowedWithConsentAndService() {
        List<String> dispatchedValues = new ArrayList<>();
        CommandExecutor executor = executorWithGate(
                "CLICK_ITEM",
                true,
                true,
                dispatchedValues,
                new ArrayList<>(),
                () -> {}
        );

        executor.executeCommand(responseForIntent("CLICK_ITEM", "button"));

        assertEquals(Collections.singletonList("button"), dispatchedValues);
    }

    @Test
    public void nonAccessibilityCommandRemainsUsableWithoutConsent() {
        List<String> dispatchedValues = new ArrayList<>();
        CommandExecutor executor = executorWithGate(
                "OPEN_APP",
                false,
                false,
                dispatchedValues,
                new ArrayList<>(),
                () -> {}
        );

        executor.executeCommand(responseForIntent("OPEN_APP", "Chrome"));

        assertEquals(Collections.singletonList("Chrome"), dispatchedValues);
    }

    private CommandExecutor executorWithRecordingHandler(
            List<String> dispatchedValues,
            List<String> messages
    ) {
        CommandHandler handler = new CommandHandler() {
            @Override
            public String getIntent() {
                return "OPEN_APP";
            }

            @Override
            public void handle(Map<String, Object> parameters, CommandExecutionContext context) {
                dispatchedValues.add(String.valueOf(parameters.get("value")));
            }
        };
        CommandDispatcher dispatcher = new CommandDispatcher(Collections.singletonList(handler));
        CommandExecutionContext executionContext = new CommandExecutionContext(null, messages::add);
        return new CommandExecutor(dispatcher, executionContext);
    }

    private CommandExecutor executorWithGate(
            String handlerIntent,
            boolean hasConsent,
            boolean serviceConnected,
            List<String> dispatchedValues,
            List<String> messages,
            Runnable disclosureRoute
    ) {
        CommandHandler handler = new CommandHandler() {
            @Override
            public String getIntent() {
                return handlerIntent;
            }

            @Override
            public void handle(Map<String, Object> parameters, CommandExecutionContext context) {
                dispatchedValues.add(String.valueOf(parameters.get("value")));
            }
        };
        AccessibilityConsentState consent = new AccessibilityConsentState() {
            @Override
            public boolean hasCurrentConsent() {
                return hasConsent;
            }

            @Override
            public void recordCurrentConsent() {}
        };
        return new CommandExecutor(
                new CommandDispatcher(Collections.singletonList(handler)),
                new CommandExecutionContext(null, messages::add),
                new AccessibilityAutomationGate(consent, () -> serviceConnected),
                disclosureRoute
        );
    }

    private PredictResponse acceptedResponse(String value) {
        return responseForIntent("OPEN_APP", value);
    }

    private PredictResponse responseForIntent(String intent, String value) {
        String json = String.format(
                "{\"accepted\":true,\"intent\":\"%s\",\"parameters\":{\"value\":\"%s\"}}",
                intent,
                value
        );
        return gson.fromJson(json, PredictResponse.class);
    }
}
