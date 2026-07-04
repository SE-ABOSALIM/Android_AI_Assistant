package com.example.anroidaiassistant.session;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AssistantSessionTest {
    @Test
    public void catalogReadinessRequiresMatchingLanguage() {
        AssistantSession.startNewSession();
        AssistantSession.setCatalogVersion("v1", "AR");

        assertTrue(AssistantSession.isCatalogReadyForLanguage("ar"));
        assertFalse(AssistantSession.isCatalogReadyForLanguage("TR"));

        AssistantSession.endSession();
    }

    @Test
    public void startingNewSessionClearsCatalogReadiness() {
        AssistantSession.startNewSession();
        AssistantSession.setCatalogVersion("v1", "AR");
        AssistantSession.startNewSession();

        assertFalse(AssistantSession.isCatalogReadyForLanguage("AR"));

        AssistantSession.endSession();
    }
}