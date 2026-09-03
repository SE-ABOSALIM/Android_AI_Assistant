package com.example.anroidaiassistant.ui.overlay;

final class MarkerPlacement {
    private MarkerPlacement() {
    }

    static Position calculate(
            int targetCenterX,
            int targetCenterY,
            int markerWidth,
            int markerHeight,
            int tipOffsetY,
            int screenWidth,
            int screenHeight,
            int edgePadding
    ) {
        int left = clamp(
                targetCenterX - markerWidth / 2,
                edgePadding,
                Math.max(edgePadding, screenWidth - markerWidth - edgePadding)
        );
        int top = clamp(
                targetCenterY - tipOffsetY,
                edgePadding,
                Math.max(edgePadding, screenHeight - markerHeight - edgePadding)
        );
        return new Position(left, top);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }

    static final class Position {
        final int left;
        final int top;

        Position(int left, int top) {
            this.left = left;
            this.top = top;
        }
    }
}
