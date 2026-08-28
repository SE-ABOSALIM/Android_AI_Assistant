package com.example.anroidaiassistant.api.dto;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.anroidaiassistant.session.AssistantSession;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import org.junit.After;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class PredictRequestContractTest {
    private final Gson gson = new Gson();

    @After
    public void tearDown() {
        AssistantSession.setCatalogVersion(null, null);
    }

    @Test
    public void dtoContainsOnlyActivePredictionInputs() {
        Set<String> fieldNames = Arrays.stream(PredictRequest.class.getDeclaredFields())
                .map(Field::getName)
                .collect(Collectors.toSet());

        assertEquals(
                new HashSet<>(Arrays.asList(
                        "text",
                        "language",
                        "textAlternatives",
                        "deviceId",
                        "catalogVersion",
                        "hasSearchInput"
                )),
                fieldNames
        );
    }

    @Test
    public void jsonOmitsSessionIdAndPreservesAllRemainingInputs() {
        AssistantSession.setCatalogVersion("catalog-v1", "TR");
        PredictRequest request = new PredictRequest(
                "open maps",
                "TR",
                "device-a",
                Arrays.asList("open map application", "launch maps"),
                true
        );

        JsonObject json = gson.toJsonTree(request).getAsJsonObject();

        assertFalse(json.has("session_id"));
        assertEquals(
                new HashSet<>(Arrays.asList(
                        "text",
                        "language",
                        "text_alternatives",
                        "device_id",
                        "catalog_version",
                        "has_search_input"
                )),
                json.entrySet().stream()
                        .map(entry -> entry.getKey())
                        .collect(Collectors.toSet())
        );
        assertEquals("open maps", json.get("text").getAsString());
        assertEquals("TR", json.get("language").getAsString());
        assertEquals(2, json.getAsJsonArray("text_alternatives").size());
        assertEquals("device-a", json.get("device_id").getAsString());
        assertEquals("catalog-v1", json.get("catalog_version").getAsString());
        assertTrue(json.get("has_search_input").getAsBoolean());
    }
}
