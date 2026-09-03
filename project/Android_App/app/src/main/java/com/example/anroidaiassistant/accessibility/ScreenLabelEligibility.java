package com.example.anroidaiassistant.accessibility;

import java.util.Locale;

final class ScreenLabelEligibility {
    private static final int MIN_TARGET_SIZE_PX = 12;
    private static final float PAGE_SIZED_AREA_RATIO = 0.65f;

    private ScreenLabelEligibility() {
    }

    static boolean isUsable(NodeFacts node) {
        if (node == null
                || !node.visible
                || !node.enabled
                || node.width < MIN_TARGET_SIZE_PX
                || node.height < MIN_TARGET_SIZE_PX
                || (!node.clickable && !node.supportsClick)) {
            return false;
        }

        return !isPageSizedStructuralNode(node) || hasStrongPageStructuralControlSemantics(node);
    }

    private static boolean isPageSizedStructuralNode(NodeFacts node) {
        return occupiesMostOfScreen(node) && isStructuralCandidate(node);
    }

    private static boolean occupiesMostOfScreen(NodeFacts node) {
        long screenArea = (long) node.screenWidth * node.screenHeight;
        long nodeArea = (long) node.width * node.height;
        return screenArea > 0 && nodeArea > screenArea * PAGE_SIZED_AREA_RATIO;
    }

    private static boolean isStructuralCandidate(NodeFacts node) {
        return !node.hasParent || node.childCount > 0 || isStructuralRole(node.role);
    }

    private static boolean hasStrongPageStructuralControlSemantics(NodeFacts node) {
        return hasText(node.text)
                || hasText(node.contentDescription)
                || hasText(node.hint)
                || node.editable
                || node.checkable
                || isKnownControlRole(node.role);
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static boolean isStructuralRole(String role) {
        String normalized = normalizeRole(role);
        return normalized.equals("android.view.view")
                || normalized.contains("webview")
                || normalized.contains("viewgroup")
                || normalized.contains("layout")
                || normalized.contains("container")
                || normalized.contains("scrollview")
                || normalized.contains("recyclerview");
    }

    private static boolean isKnownControlRole(String role) {
        String normalized = normalizeRole(role);
        return normalized.contains("button")
                || normalized.contains("checkbox")
                || normalized.contains("radiobutton")
                || normalized.contains("checkedtextview")
                || normalized.contains("spinner")
                || normalized.contains("switch")
                || normalized.contains("edittext")
                || normalized.contains("seekbar")
                || normalized.contains("autocomplete")
                || normalized.contains("combobox")
                || normalized.contains("dropdown");
    }

    private static String normalizeRole(String role) {
        return role == null ? "" : role.trim().toLowerCase(Locale.ROOT);
    }

    static final class NodeFacts {
        private boolean visible;
        private boolean enabled;
        private boolean clickable;
        private boolean supportsClick;
        private boolean editable;
        private boolean checkable;
        private boolean hasParent;
        private int childCount;
        private int width;
        private int height;
        private int screenWidth;
        private int screenHeight;
        private String text;
        private String contentDescription;
        private String hint;
        private String viewId;
        private String role;

        NodeFacts visible() {
            visible = true;
            return this;
        }

        NodeFacts enabled() {
            enabled = true;
            return this;
        }

        NodeFacts clickable() {
            clickable = true;
            return this;
        }

        NodeFacts supportsClick() {
            supportsClick = true;
            return this;
        }

        NodeFacts editable() {
            editable = true;
            return this;
        }

        NodeFacts checkable() {
            checkable = true;
            return this;
        }

        NodeFacts hasParent() {
            hasParent = true;
            return this;
        }

        NodeFacts root() {
            hasParent = false;
            return this;
        }

        NodeFacts children(int count) {
            childCount = Math.max(0, count);
            return this;
        }

        NodeFacts text(String value) {
            text = value;
            return this;
        }

        NodeFacts contentDescription(String value) {
            contentDescription = value;
            return this;
        }

        NodeFacts hint(String value) {
            hint = value;
            return this;
        }

        NodeFacts viewId(String value) {
            viewId = value;
            return this;
        }

        NodeFacts role(String value) {
            role = value;
            return this;
        }

        NodeFacts bounds(int left, int top, int right, int bottom) {
            width = Math.max(0, right - left);
            height = Math.max(0, bottom - top);
            return this;
        }

        NodeFacts screen(int width, int height) {
            screenWidth = Math.max(0, width);
            screenHeight = Math.max(0, height);
            return this;
        }
    }
}
