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
    public void startingNewSessionPreservesDeviceCatalogReadiness() {
        AssistantSession.startNewSession();
        AssistantSession.setCatalogVersion("v1", "AR");
        AssistantSession.startNewSession();

        assertTrue(AssistantSession.isCatalogReadyForLanguage("AR"));

        AssistantSession.endSession();
    }

    @Test
    public void endingThenCreatingNewSessionPreservesDeviceCatalogReadiness() {
        AssistantSession.startNewSession();
        AssistantSession.setCatalogVersion("v1", "TR");
        AssistantSession.endSession();
        AssistantSession.getOrCreateSessionId();

        assertTrue(AssistantSession.isCatalogReadyForLanguage("TR"));

        AssistantSession.endSession();
    }
}
