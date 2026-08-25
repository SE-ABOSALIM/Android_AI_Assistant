package com.example.anroidaiassistant;

import static org.junit.Assert.assertEquals;

import com.example.anroidaiassistant.api.dto.PredictResponse;
import com.example.anroidaiassistant.executor.CommandDispatcher;
import com.example.anroidaiassistant.executor.CommandExecutionContext;
import com.example.anroidaiassistant.executor.CommandHandler;
import com.google.gson.Gson;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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
