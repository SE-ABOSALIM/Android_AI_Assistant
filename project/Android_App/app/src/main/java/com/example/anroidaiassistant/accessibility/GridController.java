package com.example.anroidaiassistant.accessibility;

import android.graphics.Rect;
import android.util.DisplayMetrics;

import com.example.anroidaiassistant.ui.overlay.GridOverlayController;
import com.example.anroidaiassistant.util.TextNormalizer;

import java.util.Locale;

public final class GridController {
    private static final GridLevel[] LEVELS = {
            new GridLevel(4, 8),
            new GridLevel(5, 12),
            new GridLevel(8, 12)
    };

    private final GridOverlayController overlayController;
    private final GestureController gestureController;

    private int levelIndex = 0;
    private boolean active = false;
    private GridLayout currentLayout;

    public GridController(
            GridOverlayController overlayController,
            GestureController gestureController
    ) {
        this.overlayController = overlayController;
        this.gestureController = gestureController;
    }

    public void handleAction(String action) {
        String normalizedAction = normalizeAction(action);
        switch (normalizedAction) {
            case "smaller":
                makeSmaller();
                break;
            case "larger":
                makeLarger();
                break;
            case "hide":
                hide();
                break;
            case "show":
            default:
                show();
                break;
        }
    }

    public void show() {
        levelIndex = 0;
        active = true;
        showCurrentLevel();
    }

    public void hide() {
        active = false;
        levelIndex = 0;
        currentLayout = null;
        overlayController.hide();
    }

    public boolean isActive() {
        return active;
    }

    public int getCellCount() {
        GridLayout layout = currentLayout;
        return layout == null ? 0 : layout.cellCount();
    }

    public void makeSmaller() {
        if (levelIndex < LEVELS.length - 1) {
            levelIndex++;
        }
        active = true;
        showCurrentLevel();
    }

    public void makeLarger() {
        if (levelIndex > 0) {
            levelIndex--;
        }
        active = true;
        showCurrentLevel();
    }

    public boolean tapCell(int cellIndex) {
        return performCellGesture(cellIndex, "tap");
    }

    public boolean performCellGesture(int cellIndex, String action) {
        if (!active || cellIndex < 0 || cellIndex >= getCellCount()) {
            return false;
        }

        GridLayout layout = currentLayout;
        if (layout == null) {
            return false;
        }

        Rect bounds = overlayController.cellBoundsInScreen(cellIndex);
        if (bounds == null) {
            return false;
        }

        switch (normalizeAction(action)) {
            case "double_tap":
                return gestureController.doubleTapBoundsCenter(bounds);
            case "hold":
                return gestureController.longPressBoundsCenter(bounds);
            case "tap":
            default:
                return gestureController.tapBoundsCenter(bounds);
        }
    }

    private void showCurrentLevel() {
        currentLayout = calculateLayout(currentLevel());
        overlayController.show(
                currentLayout.columns,
                currentLayout.rows
        );
    }

    private GridLevel currentLevel() {
        return LEVELS[levelIndex];
    }

    private String normalizeAction(String action) {
        if (!TextNormalizer.hasText(action)) {
            return "show";
        }
        return action.trim().toLowerCase(Locale.US);
    }

    private GridLayout calculateLayout(GridLevel level) {
        DisplayMetrics metrics = overlayController.displayMetrics();
        int width = Math.max(1, metrics.widthPixels);
        int height = Math.max(1, metrics.heightPixels);
        return new GridLayout(
                level.columns,
                level.rows,
                width / (float) level.columns,
                height / (float) level.rows
        );
    }

    private static final class GridLevel {
        private final int columns;
        private final int rows;

        private GridLevel(int columns, int rows) {
            this.columns = columns;
            this.rows = rows;
        }
    }

    private static final class GridLayout {
        private final int columns;
        private final int rows;
        private final float cellWidth;
        private final float cellHeight;

        private GridLayout(
                int columns,
                int rows,
                float cellWidth,
                float cellHeight
        ) {
            this.columns = columns;
            this.rows = rows;
            this.cellWidth = cellWidth;
            this.cellHeight = cellHeight;
        }

        private int cellCount() {
            return columns * rows;
        }
    }
}
