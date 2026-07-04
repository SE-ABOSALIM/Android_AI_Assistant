package com.example.anroidaiassistant.executor.handlers;

import android.content.Intent;
import android.provider.AlarmClock;
import android.util.Log;

import com.example.anroidaiassistant.executor.CommandExecutionContext;
import com.example.anroidaiassistant.executor.CommandHandler;
import com.example.anroidaiassistant.util.ParameterReader;
import com.example.anroidaiassistant.util.TextNormalizer;

import java.util.Map;

public final class TimerCommandHandler implements CommandHandler {
    private static final String TAG = "TimerCommandHandler";

    @Override
    public String getIntent() {
        return "SET_TIMER";
    }

    @Override
    public void handle(Map<String, Object> parameters, CommandExecutionContext context) {
        int durationValue = ParameterReader.getIntParam(parameters, "duration_value");
        String durationUnit = ParameterReader.getStringParam(parameters, "duration_unit");
        int durationSeconds = ParameterReader.getIntParam(parameters, "duration_seconds");

        if (durationValue <= 0 || !TextNormalizer.hasText(durationUnit)) {
            context.showMessage("How long should I set the timer for?");
            return;
        }

        if (durationSeconds <= 0) {
            context.showMessage("Timer duration is missing");
            return;
        }

        Intent intent = new Intent(AlarmClock.ACTION_SET_TIMER)
                .putExtra(AlarmClock.EXTRA_LENGTH, durationSeconds)
                .putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.getAndroidContext().startActivity(intent);
        } catch (Exception exception) {
            Log.e(TAG, "Failed to set timer", exception);
            context.showMessage("Timer app is unavailable");
        }
    }
}