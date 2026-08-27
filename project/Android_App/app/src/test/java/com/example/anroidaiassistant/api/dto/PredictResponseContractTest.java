package com.example.anroidaiassistant.api.dto;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;

import org.junit.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class PredictResponseContractTest {
    private final Gson gson = new Gson();

    @Test
    public void dtoContainsOnlyVerifiedPublicResponseFields() {
        Set<String> fieldNames = Arrays.stream(PredictResponse.class.getDeclaredFields())
                .map(Field::getName)
                .collect(Collectors.toSet());

        assertEquals(
                new HashSet<>(Arrays.asList(
                        "intent",
                        "parameters",
                        "accepted",
                        "missingSlots",
                        "errorCode",
                        "errorMessage"
                )),
                fieldNames
        );
    }

    @Test
    public void acceptedResponseParsesExecutionContract() {
        PredictResponse response = gson.fromJson(
                "{\"intent\":\"OPEN_APP\",\"parameters\":{\"app_package_name\":\"com.example\"},"
                        + "\"accepted\":true,\"missing_slots\":[],\"error_code\":null,"
                        + "\"error_message\":null}",
                PredictResponse.class
        );

        assertTrue(response.isAccepted());
        assertEquals("OPEN_APP", response.getIntent());
        assertEquals("com.example", response.getParameterAsString("app_package_name"));
    }

    @Test
    public void rejectedResponseParsesErrorAndMissingSlotContract() {
        PredictResponse response = gson.fromJson(
                "{\"intent\":\"SCROLL_SCREEN\",\"parameters\":{},\"accepted\":false,"
                        + "\"missing_slots\":[\"direction\"],"
                        + "\"error_code\":\"MISSING_REQUIRED_SLOT\","
                        + "\"error_message\":\"Required parameter is missing.\"}",
                PredictResponse.class
        );

        assertFalse(response.isAccepted());
        assertEquals(List.of("direction"), response.getMissingSlots());
        assertEquals("MISSING_REQUIRED_SLOT", response.getErrorCode());
        assertEquals("Required parameter is missing.", response.getErrorMessage());
    }

    @Test
    public void appCandidateResponsePreservesIntentErrorAndCandidateParameters() {
        PredictResponse response = gson.fromJson(
                "{\"intent\":\"OPEN_APP\",\"parameters\":{\"app_name\":\"maps\","
                        + "\"app_match_candidates\":[{\"label\":\"Maps\","
                        + "\"package_name\":\"com.example.maps\",\"score\":0.9}]},"
                        + "\"accepted\":false,\"missing_slots\":[],"
                        + "\"error_code\":\"APP_MATCH_AMBIGUOUS\","
                        + "\"error_message\":\"Choose an app.\"}",
                PredictResponse.class
        );

        assertFalse(response.isAccepted());
        assertEquals("OPEN_APP", response.getIntent());
        assertEquals("APP_MATCH_AMBIGUOUS", response.getErrorCode());
        assertEquals("maps", response.getParameterAsString("app_name"));
        assertTrue(response.getParameters().get("app_match_candidates") instanceof List<?>);
    }
}
