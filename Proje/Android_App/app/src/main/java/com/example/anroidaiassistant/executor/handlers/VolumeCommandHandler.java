package com.example.anroidaiassistant.executor.handlers;

import android.content.Context;
import android.media.AudioManager;

import com.example.anroidaiassistant.executor.CommandExecutionContext;
import com.example.anroidaiassistant.executor.CommandHandler;
import com.example.anroidaiassistant.util.ParameterReader;
import com.example.anroidaiassistant.util.TextNormalizer;

import java.util.Locale;
import java.util.Map;

public final class VolumeCommandHandler implements CommandHandler {
    @Override
    public String getIntent() {
        return "ADJUST_VOLUME";
    }

    @Override
    public void handle(Map<String, Object> parameters, CommandExecutionContext context) {
        String volumeAction = normalizeDirection(ParameterReader.getStringParam(parameters, "volume_action"));
        String volumeLevel = normalizeVolumeLevel(ParameterReader.getStringParam(parameters, "volume_level"));
        if (!hasText(volumeAction) && !hasText(volumeLevel)) {
            context.showMessage("Should I increase, decrease, mute, unmute, or set the volume level?");
            return;
        }

        AudioManager audioManager = (AudioManager) context.getAndroidContext().getSystemService(Context.AUDIO_SERVICE);
        if (audioManager == null) {
            context.showMessage("Volume control is unavailable");
            return;
        }

        if (hasText(volumeLevel)) {
            setVolumeLevel(audioManager, volumeLevel, context);
            return;
        }

        switch (volumeAction) {
            case "increase":
                audioManager.adjustStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_RAISE,
                        AudioManager.FLAG_SHOW_UI
                );
                break;
            case "decrease":
                audioManager.adjustStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_LOWER,
                        AudioManager.FLAG_SHOW_UI
                );
                break;
            case "mute":
                audioManager.adjustStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_MUTE,
                        AudioManager.FLAG_SHOW_UI
                );
                break;
            case "unmute":
                audioManager.adjustStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_UNMUTE,
                        AudioManager.FLAG_SHOW_UI
                );
                break;
            default:
                context.showMessage("Volume action not supported");
                break;
        }
    }

    private void setVolumeLevel(
            AudioManager audioManager,
            String volumeLevel,
            CommandExecutionContext context
    ) {
        int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int targetVolume;

        switch (volumeLevel) {
            case "low":
                targetVolume = Math.max(1, Math.round(maxVolume * 0.25f));
                break;
            case "medium":
                targetVolume = Math.max(1, Math.round(maxVolume * 0.50f));
                break;
            case "max":
                targetVolume = maxVolume;
                break;
            default:
                context.showMessage("Volume level not supported");
                return;
        }

        audioManager.setStreamVolume(
                AudioManager.STREAM_MUSIC,
                Math.min(targetVolume, maxVolume),
                AudioManager.FLAG_SHOW_UI
        );
    }

    private String normalizeDirection(String direction) {
        if (direction == null) {
            return "";
        }
        return direction.trim().toLowerCase(Locale.US);
    }

    private String normalizeVolumeLevel(String volumeLevel) {
        if (volumeLevel == null) {
            return "";
        }

        String normalized = volumeLevel.trim().toLowerCase(Locale.US);
        if ("high".equals(normalized) || "maximum".equals(normalized)) {
            return "max";
        }
        return normalized;
    }

    private boolean hasText(String value) {
        return TextNormalizer.hasText(value);
    }
}