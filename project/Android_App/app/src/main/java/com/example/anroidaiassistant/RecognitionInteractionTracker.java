package com.example.anroidaiassistant;

final class RecognitionInteractionTracker {
    private long recognitionGeneration;
    private long interactionValidityGeneration;

    synchronized long beginRecognitionSession() {
        recognitionGeneration++;
        return recognitionGeneration;
    }

    synchronized boolean isCurrentRecognitionGeneration(long generation) {
        return generation == recognitionGeneration;
    }

    synchronized long captureInteractionValidity() {
        return interactionValidityGeneration;
    }

    synchronized boolean isInteractionValid(long validityToken) {
        return validityToken == interactionValidityGeneration;
    }

    synchronized void invalidateRecognitionCallbacks() {
        recognitionGeneration++;
    }

    synchronized void invalidateInteractions() {
        recognitionGeneration++;
        interactionValidityGeneration++;
    }
}
