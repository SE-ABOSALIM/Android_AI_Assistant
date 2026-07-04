package com.example.anroidaiassistant.executor;

import android.content.Context;

public final class CommandExecutionContext {
    public interface Feedback {
        void showMessage(String message);
    }

    private final Context androidContext;
    private final Feedback feedback;

    public CommandExecutionContext(Context androidContext, Feedback feedback) {
        this.androidContext = androidContext;
        this.feedback = feedback;
    }

    public Context getAndroidContext() {
        return androidContext;
    }

    public void showMessage(String message) {
        if (feedback != null) {
            feedback.showMessage(message);
        }
    }
}