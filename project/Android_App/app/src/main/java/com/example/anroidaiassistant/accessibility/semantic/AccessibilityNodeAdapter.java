package com.example.anroidaiassistant.accessibility.semantic;

import android.os.Build;
import android.view.accessibility.AccessibilityNodeInfo;

import com.example.anroidaiassistant.util.TextNormalizer;

import java.util.ArrayList;
import java.util.List;

public final class AccessibilityNodeAdapter
        implements SemanticNodeResolver.NodeAdapter<AccessibilityNodeInfo> {
    @Override
    public AccessibilityNodeInfo parent(AccessibilityNodeInfo node) {
        return node == null ? null : node.getParent();
    }

    @Override
    public List<AccessibilityNodeInfo> children(AccessibilityNodeInfo node) {
        List<AccessibilityNodeInfo> children = new ArrayList<>();
        if (node == null) {
            return children;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                children.add(child);
            }
        }
        return children;
    }

    @Override
    public AccessibilityNodeInfo labelFor(AccessibilityNodeInfo node) {
        return node == null ? null : node.getLabelFor();
    }

    @Override
    public AccessibilityNodeInfo labeledBy(AccessibilityNodeInfo node) {
        return node == null ? null : node.getLabeledBy();
    }

    @Override public boolean isVisible(AccessibilityNodeInfo node) { return node.isVisibleToUser(); }
    @Override public boolean isEnabled(AccessibilityNodeInfo node) { return node.isEnabled(); }
    @Override public boolean isClickable(AccessibilityNodeInfo node) { return node.isClickable(); }
    @Override public boolean supportsClick(AccessibilityNodeInfo node) {
        return (node.getActions() & AccessibilityNodeInfo.ACTION_CLICK) != 0;
    }
    @Override public boolean isEditable(AccessibilityNodeInfo node) { return node.isEditable(); }
    @Override public boolean supportsSetText(AccessibilityNodeInfo node) {
        return (node.getActions() & AccessibilityNodeInfo.ACTION_SET_TEXT) != 0;
    }
    @Override public boolean isCheckable(AccessibilityNodeInfo node) { return node.isCheckable(); }
    @Override public boolean isFocusable(AccessibilityNodeInfo node) { return node.isFocusable(); }

    @Override
    public String className(AccessibilityNodeInfo node) {
        CharSequence className = node.getClassName();
        return className == null ? "" : className.toString();
    }

    @Override
    public List<String> semanticValues(AccessibilityNodeInfo node) {
        List<String> values = new ArrayList<>();
        if (node == null) {
            return values;
        }
        add(values, node.getText());
        add(values, node.getContentDescription());
        add(values, splitIdentifier(node.getViewIdResourceName()));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            add(values, node.getHintText());
        }
        return values;
    }

    private void add(List<String> values, CharSequence value) {
        if (value != null && TextNormalizer.hasText(value.toString())) {
            values.add(value.toString());
        }
    }

    private String splitIdentifier(String value) {
        if (!TextNormalizer.hasText(value)) {
            return "";
        }
        return value
                .replaceAll("([a-z])([A-Z])", "$1 $2")
                .replace('_', ' ')
                .replace('-', ' ')
                .replace('.', ' ')
                .replace(':', ' ')
                .replace('/', ' ');
    }
}
