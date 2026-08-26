package com.example.anroidaiassistant.util;

import android.util.Log;

import com.example.anroidaiassistant.BuildConfig;

/** Keeps content-bearing diagnostics out of release Logcat output. */
public final class SensitiveDebugLog {
    private SensitiveDebugLog() {}

    public static void info(String tag, String message) {
        if (BuildConfig.DEBUG) {
            Log.i(tag, message);
        }
    }
}
