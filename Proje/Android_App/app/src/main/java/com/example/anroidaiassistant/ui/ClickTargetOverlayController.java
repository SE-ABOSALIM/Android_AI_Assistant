package com.example.anroidaiassistant.ui.overlay;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;

import com.example.anroidaiassistant.MyAccessibilityService;

import java.util.Collections;
import java.util.List;

public final class ClickTargetOverlayController {
    private static final int MARKER_WIDTH_DP = 28;
    private static final int MARKER_HEIGHT_DP = 34;
    private static final int MARKER_UPWARD_OFFSET_DP = 45;
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
        overlayView.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);

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
        params.gravity = Gravity.TOP | Gravity.LEFT;
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
        int markerWidth = dp(MARKER_WIDTH_DP);
        int markerHeight = dp(MARKER_HEIGHT_DP);
        int edgePadding = dp(SCREEN_EDGE_PADDING_DP);

        View marker = new PinMarkerView(context, number);
        marker.setElevation(dp(8));
        marker.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);

        Rect safeBounds = targetBounds == null ? new Rect() : targetBounds;
        int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
        int screenHeight = context.getResources().getDisplayMetrics().heightPixels;
        int targetX = safeBounds.isEmpty() ? safeBounds.left : safeBounds.centerX();
        int targetY = safeBounds.isEmpty() ? safeBounds.top : safeBounds.centerY();
        int tipOffsetY = markerHeight - dp(2);
        int upwardOffset = dp(MARKER_UPWARD_OFFSET_DP);
        int left = clamp(
                targetX - markerWidth / 2,
                edgePadding,
                Math.max(edgePadding, screenWidth - markerWidth - edgePadding)
        );
        int top = clamp(
                targetY - tipOffsetY - upwardOffset,
                edgePadding,
                Math.max(edgePadding, screenHeight - markerHeight - edgePadding)
        );

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(markerWidth, markerHeight);
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

    private int dp(float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private final class PinMarkerView extends View {
        private final String numberText;
        private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path pinPath = new Path();

        private PinMarkerView(Context context, int number) {
            super(context);
            this.numberText = String.valueOf(number);

            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(dp(2.2f));
            strokePaint.setColor(Color.WHITE);
            strokePaint.setShadowLayer(dp(2), 0, dp(1), Color.argb(190, 0, 0, 0));

            textPaint.setColor(Color.WHITE);
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            textPaint.setTextSize(dp(number >= 100 ? 9 : 11));
            textPaint.setShadowLayer(dp(2), 0, dp(1), Color.argb(220, 0, 0, 0));

            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            float width = getWidth();
            float height = getHeight();
            float centerX = width / 2f;
            float inset = dp(2.5f);
            float top = inset;
            float left = inset;
            float right = width - inset;
            float tipY = height - inset;
            float headCenterY = height * 0.30f;

            pinPath.reset();
            pinPath.moveTo(centerX, tipY);
            pinPath.cubicTo(width * 0.40f, height * 0.72f, left, height * 0.50f, left, height * 0.34f);
            pinPath.cubicTo(left, height * 0.15f, width * 0.28f, top, centerX, top);
            pinPath.cubicTo(width * 0.72f, top, right, height * 0.15f, right, height * 0.34f);
            pinPath.cubicTo(right, height * 0.50f, width * 0.60f, height * 0.72f, centerX, tipY);

            canvas.drawPath(pinPath, strokePaint);

            Paint.FontMetrics fontMetrics = textPaint.getFontMetrics();
            float textBaseline = headCenterY + dp(2) - (fontMetrics.ascent + fontMetrics.descent) / 2f;
            canvas.drawText(numberText, centerX, textBaseline, textPaint);
        }
    }
}
