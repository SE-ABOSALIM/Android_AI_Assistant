package com.example.anroidaiassistant.ui.overlay;

import android.content.Context;
import android.graphics.PixelFormat;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import com.example.anroidaiassistant.R;
import com.example.anroidaiassistant.util.TextNormalizer;

public final class ListeningOverlayController {
    private final Context context;
    private final WindowManager windowManager;

    private View overlayView;
    private TextView overlayTextView;

    public ListeningOverlayController(Context context, WindowManager windowManager) {
        this.context = context;
        this.windowManager = windowManager;
    }

    public void show() {
        if (overlayView != null) {
            return;
        }

        overlayView = LayoutInflater.from(context).inflate(R.layout.overlay_layout, null);
        overlayTextView = overlayView.findViewById(R.id.tv_overlay_text);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 20;
        params.y = 100;

        windowManager.addView(overlayView, params);
    }

    public void hide() {
        if (overlayView == null) {
            return;
        }

        windowManager.removeView(overlayView);
        overlayView = null;
        overlayTextView = null;
    }

    public void updateText(String text) {
        if (overlayTextView != null && TextNormalizer.hasText(text)) {
            overlayTextView.setText(text);
        }
    }
}