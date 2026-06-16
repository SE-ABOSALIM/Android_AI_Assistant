package com.example.anroidaiassistant.ui.overlay;

import android.content.Context;
import android.graphics.PixelFormat;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import com.example.anroidaiassistant.R;
import com.example.anroidaiassistant.settings.AssistantSettings;
import com.example.anroidaiassistant.util.TextNormalizer;

public final class ListeningOverlayController {
    private static final int EDGE_PADDING_DP = 2;
    private static final int BELOW_STATUS_BAR_PADDING_DP = 2;

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
        applyLanguageDirection();

        int edgePadding = dp(EDGE_PADDING_DP);
        boolean rtl = AssistantSettings.isRtl(context);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                overlayWidth(edgePadding),
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );

        params.gravity = Gravity.TOP | (rtl ? Gravity.RIGHT : Gravity.LEFT);
        params.x = edgePadding;
        params.y = statusBarHeight() + dp(BELOW_STATUS_BAR_PADDING_DP);

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
            applyLanguageDirection();
            overlayTextView.setText(text);
        }
    }

    private void applyLanguageDirection() {
        if (overlayView == null || overlayTextView == null) {
            return;
        }

        boolean rtl = AssistantSettings.isRtl(context);
        overlayView.setLayoutDirection(rtl ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        overlayTextView.setTextDirection(rtl ? View.TEXT_DIRECTION_RTL : View.TEXT_DIRECTION_LTR);
        overlayTextView.setGravity(rtl ? Gravity.RIGHT : Gravity.LEFT);
    }

    private int overlayWidth(int edgePadding) {
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        return Math.max(1, metrics.widthPixels - edgePadding * 2);
    }

    private int statusBarHeight() {
        int resourceId = context.getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            return context.getResources().getDimensionPixelSize(resourceId);
        }
        return dp(24);
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
