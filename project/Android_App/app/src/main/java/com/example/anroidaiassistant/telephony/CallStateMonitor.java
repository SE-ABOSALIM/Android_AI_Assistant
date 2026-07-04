package com.example.anroidaiassistant.telephony;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;

public final class CallStateMonitor {
    public interface Listener {
        void onCallActiveChanged(boolean active);
    }

    private final Context context;
    private final Listener listener;
    private TelephonyManager telephonyManager;
    private PhoneStateListener phoneStateListener;
    private boolean registered;
    private boolean callActive;

    public CallStateMonitor(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    public boolean start() {
        if (registered) {
            return true;
        }
        if (context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE)
                != PackageManager.PERMISSION_GRANTED) {
            return false;
        }

        telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        if (telephonyManager == null) {
            return false;
        }

        phoneStateListener = new PhoneStateListener() {
            @Override
            public void onCallStateChanged(int state, String phoneNumber) {
                updateCallState(state);
            }
        };

        updateCallState(telephonyManager.getCallState());
        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
        registered = true;
        return true;
    }

    public void stop() {
        if (!registered || telephonyManager == null || phoneStateListener == null) {
            return;
        }

        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
        phoneStateListener = null;
        registered = false;
        callActive = false;
    }

    public boolean isCallActive() {
        return callActive;
    }

    private void updateCallState(int state) {
        boolean active = state == TelephonyManager.CALL_STATE_OFFHOOK;
        if (callActive == active) {
            return;
        }

        callActive = active;
        if (listener != null) {
            listener.onCallActiveChanged(active);
        }
    }
}
