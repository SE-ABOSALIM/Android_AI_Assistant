package com.example.anroidaiassistant;

final class CloseAppBlockingModalDetector {
    // Require a substantial shrink, not Activity insets or a window-ID change alone.
    private static final double MAX_DIALOG_AREA_RATIO = 0.5;

    private CloseAppBlockingModalDetector() {
    }

    static boolean isBlockingModal(String expectedPackageName,
            CloseAppWindowSnapshot pre, CloseAppWindowSnapshot post) {
        if (!isValidWindow(expectedPackageName, pre)
                || !isValidWindow(expectedPackageName, post)
                || pre.windowId == post.windowId) {
            return false;
        }
        if (post.left < pre.left || post.top < pre.top
                || post.right > pre.right || post.bottom > pre.bottom) {
            return false;
        }
        double preArea = ((double) pre.right - pre.left) * ((double) pre.bottom - pre.top);
        double postArea = ((double) post.right - post.left) * ((double) post.bottom - post.top);
        return postArea <= preArea * MAX_DIALOG_AREA_RATIO;
    }

    private static boolean isValidWindow(String expectedPackageName, CloseAppWindowSnapshot window) {
        return expectedPackageName != null && !expectedPackageName.isEmpty()
                && window != null && expectedPackageName.equals(window.packageName)
                && window.windowId >= 0 && window.applicationWindow && window.active && window.focused
                && window.right > window.left && window.bottom > window.top;
    }
}
