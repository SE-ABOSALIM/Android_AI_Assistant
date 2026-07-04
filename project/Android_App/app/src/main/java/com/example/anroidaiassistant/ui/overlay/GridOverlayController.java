package com.example.anroidaiassistant.ui.overlay;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;

public final class GridOverlayController {
    private final Context context;
    private final WindowManager windowManager;

    private FrameLayout overlayView;
    private GridView gridView;

    public GridOverlayController(Context context, WindowManager windowManager) {
        this.context = context;
        this.windowManager = windowManager;
    }

    public void show(int columns, int rows) {
        if (columns <= 0 || rows <= 0) {
            return;
        }

        if (overlayView == null) {
            overlayView = new FrameLayout(context);
            overlayView.setClipChildren(false);
            overlayView.setClipToPadding(false);
            gridView = new GridView(context);
            overlayView.addView(
                    gridView,
                    new FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT
                    )
            );

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

        gridView.setGrid(columns, rows);
    }

    public DisplayMetrics displayMetrics() {
        return context.getResources().getDisplayMetrics();
    }

    public Rect cellBoundsInScreen(int cellIndex) {
        if (gridView == null) {
            return null;
        }

        return gridView.cellBoundsInScreen(cellIndex);
    }

    public void hide() {
        if (overlayView == null) {
            return;
        }

        windowManager.removeView(overlayView);
        overlayView = null;
        gridView = null;
    }

    private static final class GridView extends View {
        private static final int BACKGROUND_ALPHA = 28;
        private static final int LINE_ALPHA = 130;
        private static final int TEXT_ALPHA = 235;

        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF cellRect = new RectF();

        private int columns = 4;
        private int rows = 5;

        GridView(Context context) {
            super(context);

            fillPaint.setStyle(Paint.Style.FILL);
            fillPaint.setColor(Color.argb(BACKGROUND_ALPHA, 0, 0, 0));

            linePaint.setStyle(Paint.Style.STROKE);
            linePaint.setStrokeWidth(dp(1));
            linePaint.setColor(Color.argb(LINE_ALPHA, 255, 255, 255));

            textPaint.setColor(Color.argb(TEXT_ALPHA, 255, 255, 255));
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setTypeface(Typeface.DEFAULT_BOLD);
            textPaint.setShadowLayer(dp(3), 0, 0, Color.argb(180, 0, 0, 0));
        }

        void setGrid(int columns, int rows) {
            this.columns = Math.max(1, columns);
            this.rows = Math.max(1, rows);
            invalidate();
        }

        Rect cellBoundsInScreen(int cellIndex) {
            int width = getWidth();
            int height = getHeight();
            int cellCount = columns * rows;
            if (width <= 0 || height <= 0 || cellIndex < 0 || cellIndex >= cellCount) {
                return null;
            }

            int[] location = new int[2];
            getLocationOnScreen(location);

            int row = cellIndex / columns;
            int column = cellIndex % columns;
            float cellWidth = width / (float) columns;
            float cellHeight = height / (float) rows;

            return new Rect(
                    Math.round(location[0] + column * cellWidth),
                    Math.round(location[1] + row * cellHeight),
                    Math.round(location[0] + (column + 1) * cellWidth),
                    Math.round(location[1] + (row + 1) * cellHeight)
            );
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            int width = getWidth();
            int height = getHeight();
            if (width <= 0 || height <= 0) {
                return;
            }

            canvas.drawRect(0, 0, width, height, fillPaint);

            float cellWidth = width / (float) columns;
            float cellHeight = height / (float) rows;

            drawGridLines(canvas, width, height, cellWidth, cellHeight);
            drawNumbers(canvas, cellWidth, cellHeight);
        }

        private void drawGridLines(Canvas canvas, int width, int height, float cellWidth, float cellHeight) {
            for (int column = 0; column <= columns; column++) {
                float x = column * cellWidth;
                canvas.drawLine(x, 0, x, height, linePaint);
            }

            for (int row = 0; row <= rows; row++) {
                float y = row * cellHeight;
                canvas.drawLine(0, y, width, y, linePaint);
            }
        }

        private void drawNumbers(Canvas canvas, float cellWidth, float cellHeight) {
            int cellCount = columns * rows;
            textPaint.setTextSize(textSizeFor(cellCount, cellHeight));
            Paint.FontMetrics metrics = textPaint.getFontMetrics();
            float verticalOffset = -(metrics.ascent + metrics.descent) / 2f;

            for (int index = 0; index < cellCount; index++) {
                int row = index / columns;
                int column = index % columns;
                cellRect.set(
                        column * cellWidth,
                        row * cellHeight,
                        (column + 1) * cellWidth,
                        (row + 1) * cellHeight
                );
                canvas.drawText(
                        String.valueOf(index + 1),
                        cellRect.centerX(),
                        cellRect.centerY() + verticalOffset,
                        textPaint
                );
            }
        }

        private float textSizeFor(int cellCount, float cellHeight) {
            float maxByCell = cellHeight * 0.32f;
            if (cellCount >= 100) {
                return Math.min(dp(17), maxByCell);
            }
            if (cellCount >= 50) {
                return Math.min(dp(19), maxByCell);
            }
            return Math.min(dp(22), maxByCell);
        }

        private int dp(int value) {
            return Math.round(value * getResources().getDisplayMetrics().density);
        }
    }
}
