package com.example.anroidaiassistant.accessibility;

import android.accessibilityservice.AccessibilityService;
import android.os.Build;
import android.os.Handler;
import android.view.accessibility.AccessibilityNodeInfo;

import com.example.anroidaiassistant.util.TextNormalizer;

import java.util.Locale;

public final class SystemUninstallController {
    private static final int MAX_CONFIRM_ATTEMPTS = 8;
    private static final int CONFIRM_RETRY_DELAY_MS = 300;

    private static final String[] INSTALLER_PACKAGE_PARTS = {
            "packageinstaller",
            "permissioncontroller",
            "installer"
    };

    private static final String[] UNINSTALL_CONTEXT_WORDS = {
            "uninstall",
            "remove",
            "delete",
            "kaldir",
            "sil",
            "silinsin",
            "uygulama kaldirilsin",
            "\u0627\u0644\u063a\u0627\u0621 \u0627\u0644\u062a\u062b\u0628\u064a\u062a",
            "\u0627\u0632\u0627\u0644\u0647",
            "\u062d\u0630\u0641"
    };

    private static final String[] POSITIVE_BUTTON_WORDS = {
            "uninstall",
            "ok",
            "yes",
            "kaldir",
            "tamam",
            "evet",
            "\u0646\u0639\u0645",
            "\u0645\u0648\u0627\u0641\u0642",
            "\u0627\u0644\u063a\u0627\u0621 \u0627\u0644\u062a\u062b\u0628\u064a\u062a",
            "\u0627\u0632\u0627\u0644\u0647",
            "\u062d\u0630\u0641"
    };

    private static final String[] NEGATIVE_BUTTON_WORDS = {
            "cancel",
            "no",
            "iptal",
            "hayir",
            "\u0644\u0627"
    };

    private final AccessibilityService service;
    private final Handler handler;

    public SystemUninstallController(AccessibilityService service, Handler handler) {
        this.service = service;
        this.handler = handler;
    }

    public void confirmNextSystemUninstallDialog(String expectedPackageName, String expectedLabel) {
        attemptConfirm(expectedPackageName, expectedLabel, 1);
    }

    private void attemptConfirm(String expectedPackageName, String expectedLabel, int attempt) {
        handler.postDelayed(() -> {
            if (clickPositiveUninstallButton(expectedPackageName, expectedLabel)) {
                return;
            }
            if (attempt < MAX_CONFIRM_ATTEMPTS) {
                attemptConfirm(expectedPackageName, expectedLabel, attempt + 1);
            }
        }, CONFIRM_RETRY_DELAY_MS);
    }

    private boolean clickPositiveUninstallButton(String expectedPackageName, String expectedLabel) {
        AccessibilityNodeInfo root = service.getRootInActiveWindow();
        if (root == null || !isLikelyPackageInstaller(root)) {
            return false;
        }

        String screenText = collectScreenText(root);
        if (!looksLikeUninstallDialog(screenText, expectedPackageName, expectedLabel)) {
            return false;
        }

        AccessibilityNodeInfo button = findPositiveButton(root);
        return button != null && clickNode(button);
    }

    private boolean isLikelyPackageInstaller(AccessibilityNodeInfo root) {
        if (root.getPackageName() == null) {
            return false;
        }

        String packageName = root.getPackageName().toString().toLowerCase(Locale.US);
        for (String part : INSTALLER_PACKAGE_PARTS) {
            if (packageName.contains(part)) {
                return true;
            }
        }
        return false;
    }

    private boolean looksLikeUninstallDialog(
            String screenText,
            String expectedPackageName,
            String expectedLabel
    ) {
        if (containsAny(screenText, UNINSTALL_CONTEXT_WORDS)) {
            return true;
        }

        String normalizedPackageName = normalize(expectedPackageName);
        if (TextNormalizer.hasText(normalizedPackageName) && screenText.contains(normalizedPackageName)) {
            return true;
        }

        String normalizedLabel = normalize(expectedLabel);
        return TextNormalizer.hasText(normalizedLabel) && screenText.contains(normalizedLabel);
    }

    private AccessibilityNodeInfo findPositiveButton(AccessibilityNodeInfo node) {
        if (node == null) {
            return null;
        }

        String label = normalize(nodeLabel(node));
        if (TextNormalizer.hasText(label)
                && !containsAny(label, NEGATIVE_BUTTON_WORDS)
                && containsAny(label, POSITIVE_BUTTON_WORDS)
                && isActionable(node)) {
            return node;
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo match = findPositiveButton(node.getChild(i));
            if (match != null) {
                return match;
            }
        }
        return null;
    }

    private boolean isActionable(AccessibilityNodeInfo node) {
        return node.isClickable() || findClickableAncestor(node) != null;
    }

    private boolean clickNode(AccessibilityNodeInfo node) {
        if (node.isClickable() && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            return true;
        }

        AccessibilityNodeInfo ancestor = findClickableAncestor(node);
        return ancestor != null && ancestor.performAction(AccessibilityNodeInfo.ACTION_CLICK);
    }

    private AccessibilityNodeInfo findClickableAncestor(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo current = node;
        while (current != null) {
            if (current.isClickable()) {
                return current;
            }
            current = current.getParent();
        }
        return null;
    }

    private String collectScreenText(AccessibilityNodeInfo node) {
        StringBuilder builder = new StringBuilder();
        appendNodeText(builder, node);
        return normalize(builder.toString());
    }

    private void appendNodeText(StringBuilder builder, AccessibilityNodeInfo node) {
        if (node == null) {
            return;
        }

        appendIfText(builder, node.getText());
        appendIfText(builder, node.getContentDescription());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            appendIfText(builder, node.getHintText());
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            appendNodeText(builder, node.getChild(i));
        }
    }

    private void appendIfText(StringBuilder builder, CharSequence value) {
        if (value == null || value.length() == 0) {
            return;
        }
        if (builder.length() > 0) {
            builder.append(' ');
        }
        builder.append(value);
    }

    private String nodeLabel(AccessibilityNodeInfo node) {
        StringBuilder builder = new StringBuilder();
        appendIfText(builder, node.getText());
        appendIfText(builder, node.getContentDescription());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            appendIfText(builder, node.getHintText());
        }
        return builder.toString();
    }

    private boolean containsAny(String value, String[] candidates) {
        if (!TextNormalizer.hasText(value)) {
            return false;
        }

        for (String candidate : candidates) {
            String normalizedCandidate = normalize(candidate);
            if (TextNormalizer.hasText(normalizedCandidate) && value.contains(normalizedCandidate)) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String value) {
        return TextNormalizer.normalizeAsciiText(value);
    }
}
