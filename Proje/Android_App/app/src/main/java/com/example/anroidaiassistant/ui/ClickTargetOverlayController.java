package com.example.anroidaiassistant.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.example.anroidaiassistant.MyAccessibilityService;
import com.example.anroidaiassistant.R;

import java.util.Collections;
import java.util.List;

public final class ClickTargetOverlayController {
    private static final int MARKER_SIZE_DP = 34;
    private static final int SCREEN_EDGE_PADDING_DP = 4;

    private final Context context;
    private final WindowManager windowManager;

    private FrameLayout overlayView;

    public ClickTargetOverlayController(Context context, WindowManager windowManager) {
        this.context = context;
        this.windowManager = windowManager;
    }

    public void show(List<MyAccessibilityService.ClickTargetChoice> choices) {
        hide();

        overlayView = new FrameLayout(context);
        overlayView.setClipChildren(false);
        overlayView.setClipToPadding(false);

        List<MyAccessibilityService.ClickTargetChoice> safeChoices =
                choices == null ? Collections.emptyList() : choices;
        for (int i = 0; i < safeChoices.size(); i++) {
            overlayView.addView(createMarker(i + 1, safeChoices.get(i).bounds));
        }

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        windowManager.addView(overlayView, params);
    }

    public void hide() {
        if (overlayView == null) {
            return;
        }

        windowManager.removeView(overlayView);
        overlayView = null;
    }

    private View createMarker(int number, Rect targetBounds) {
        int markerSize = dp(MARKER_SIZE_DP);
        int edgePadding = dp(SCREEN_EDGE_PADDING_DP);

        TextView marker = new TextView(context);
        marker.setText(String.valueOf(number));
        marker.setTextColor(Color.WHITE);
        marker.setTextSize(15);
        marker.setGravity(Gravity.CENTER);
        marker.setTypeface(marker.getTypeface(), Typeface.BOLD);
        marker.setBackgroundResource(R.drawable.selection_number_background);
        marker.setElevation(dp(8));

        Rect safeBounds = targetBounds == null ? new Rect() : targetBounds;
        int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
        int screenHeight = context.getResources().getDisplayMetrics().heightPixels;
        int left = clamp(safeBounds.left, edgePadding, Math.max(edgePadding, screenWidth - markerSize - edgePadding));
        int top = clamp(
                safeBounds.top - markerSize / 3,
                edgePadding,
                Math.max(edgePadding, screenHeight - markerSize - edgePadding)
        );

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(markerSize, markerSize);
        params.leftMargin = left;
        params.topMargin = top;
        marker.setLayoutParams(params);
        return marker;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
