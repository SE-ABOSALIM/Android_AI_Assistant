package com.example.anroidaiassistant.accessibility.click;

import android.os.Build;
import android.view.accessibility.AccessibilityNodeInfo;

import com.example.anroidaiassistant.util.TextNormalizer;

import java.util.Locale;

public final class ClickTextUtils {
    private ClickTextUtils() {}

    public static String normalize(String value) {
        return TextNormalizer.normalizeAsciiText(replaceKnownIconSymbols(value)).toLowerCase(Locale.US);
    }

    private static String replaceKnownIconSymbols(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\uD83E\uDD0D", " heart ")
                .replace("\uD83D\uDC99", " heart ")
                .replace("\uD83D\uDC9A", " heart ")
                .replace("\uD83D\uDC9B", " heart ")
                .replace("\uD83D\uDC9C", " heart ")
                .replace("\uD83D\uDDA4", " heart ")
                .replace("\u2764\uFE0F", " heart ")
                .replace("\u2665", " heart ")
                .replace("\u2661", " heart ")
                .replace("\u2764", " heart ");
    }

    public static String joinNodeText(AccessibilityNodeInfo node) {
        StringBuilder builder = new StringBuilder();
        append(builder, node.getText());
        append(builder, node.getContentDescription());
        append(builder, node.getViewIdResourceName());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            append(builder, node.getHintText());
        }
        return builder.toString();
    }

    public static String joinSubtreeText(AccessibilityNodeInfo node) {
        StringBuilder builder = new StringBuilder();
        appendSubtreeText(builder, node);
        return builder.toString();
    }

    private static void appendSubtreeText(StringBuilder builder, AccessibilityNodeInfo node) {
        if (node == null) {
            return;
        }

        append(builder, node.getText());
        append(builder, node.getContentDescription());
        append(builder, node.getViewIdResourceName());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            append(builder, node.getHintText());
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            appendSubtreeText(builder, node.getChild(i));
        }
    }

    private static void append(StringBuilder builder, CharSequence value) {
        if (value == null) {
            return;
        }
        if (builder.length() > 0) {
            builder.append(' ');
        }
        builder.append(value);
    }
}
