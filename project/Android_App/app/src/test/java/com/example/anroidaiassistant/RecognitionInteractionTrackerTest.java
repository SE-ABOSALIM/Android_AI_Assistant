package com.example.anroidaiassistant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RecognitionInteractionTrackerTest {
    @Test
    public void lateRecognitionCallbackFromOldGeneration_isIgnored() {
        RecognitionInteractionTracker tracker = new RecognitionInteractionTracker();
        int[] acceptedRecognitionCount = {0};
        long generationA = tracker.beginRecognitionSession();
        Runnable lateCallbackFromA = () -> {
            if (tracker.isCurrentRecognitionGeneration(generationA)) {
                acceptedRecognitionCount[0]++;
            }
        };

        long generationB = tracker.beginRecognitionSession();
        lateCallbackFromA.run();

        assertEquals(0, acceptedRecognitionCount[0]);
        assertTrue(tracker.isCurrentRecognitionGeneration(generationB));
    }

    @Test
    public void predictionResponseAfterListeningStopped_doesNotExecute() {
        RecognitionInteractionTracker tracker = new RecognitionInteractionTracker();
        int[] commandExecutionCount = {0};
        long validityToken = tracker.captureInteractionValidity();
        Runnable predictionResponse = () -> {
            if (tracker.isInteractionValid(validityToken)) {
                commandExecutionCount[0]++;
            }
        };

        tracker.invalidateInteractions();
        predictionResponse.run();

        assertEquals(0, commandExecutionCount[0]);
    }

    @Test
    public void pendingPredictionAfterInteractionInvalidated_isIgnored() {
        RecognitionInteractionTracker tracker = new RecognitionInteractionTracker();
        int[] predictionSubmissionCount = {0};
        long validityToken = tracker.captureInteractionValidity();
        Runnable catalogCompletion = () -> {
            if (tracker.isInteractionValid(validityToken)) {
                predictionSubmissionCount[0]++;
            }
        };

        tracker.invalidateInteractions();
        catalogCompletion.run();

        assertEquals(0, predictionSubmissionCount[0]);
    }

    @Test
    public void separateUtterancesWithinActiveInteraction_remainIndependentlyValid() {
        RecognitionInteractionTracker tracker = new RecognitionInteractionTracker();
        int[] commandExecutionCount = {0};

        tracker.beginRecognitionSession();
        long utteranceAValidity = tracker.captureInteractionValidity();
        tracker.beginRecognitionSession();
        long utteranceBValidity = tracker.captureInteractionValidity();

        if (tracker.isInteractionValid(utteranceAValidity)) {
            commandExecutionCount[0]++;
        }
        if (tracker.isInteractionValid(utteranceBValidity)) {
            commandExecutionCount[0]++;
        }

        assertEquals(2, commandExecutionCount[0]);
    }
}
