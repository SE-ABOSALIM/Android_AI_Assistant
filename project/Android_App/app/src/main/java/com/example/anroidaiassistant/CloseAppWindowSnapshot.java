package com.example.anroidaiassistant;

/** Immutable, text-free metadata for one active application window. */
final class CloseAppWindowSnapshot {
    final String packageName;
    final int windowId;
    final boolean applicationWindow;
    final boolean active;
    final boolean focused;
    final int left;
    final int top;
    final int right;
    final int bottom;

    CloseAppWindowSnapshot(String packageName, int windowId, boolean applicationWindow,
            boolean active, boolean focused, int left, int top, int right, int bottom) {
        this.packageName = packageName;
        this.windowId = windowId;
        this.applicationWindow = applicationWindow;
        this.active = active;
        this.focused = focused;
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
    }
}
