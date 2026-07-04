package com.example.anroidaiassistant.accessibility;

import android.accessibilityservice.AccessibilityService;
import android.os.Build;
import android.os.Handler;
import android.view.accessibility.AccessibilityNodeInfo;

import com.example.anroidaiassistant.util.TextNormalizer;

import java.util.Locale;

public final class DevicePowerController {
    public enum Action {
        POWER_OFF,
        RESTART
    }

    private static final int MAX_CLICK_ATTEMPTS = 6;
    private static final int POWER_MENU_OPEN_DELAY_MS = 450;
    private static final int POWER_MENU_CONFIRM_DELAY_MS = 850;

    private static final String[] POWER_MENU_PACKAGE_PARTS = {
            "systemui",
            "globalactions"
    };

    private static final String[] POWER_OFF_WORDS = {
            "power off",
            "turn off",
            "shut down",
            "kapat",
            "gucu kapat",
            "telefonu kapat",
            "ايقاف التشغيل",
            "اطفاء",
            "اطفي",
            "اغلاق",
            "اقفل"
    };

    private static final String[] RESTART_WORDS = {
            "restart",
            "reboot",
            "yeniden baslat",
            "اعد تشغيل",
            "اعاده تشغيل",
            "اعادة تشغيل"
    };

    private final AccessibilityService service;
    private final Handler handler;

    public DevicePowerController(AccessibilityService service, Handler handler) {
        this.service = service;
        this.handler = handler;
    }

    public boolean perform(Action action) {
        if (!service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG)) {
            return false;
        }

        attemptClick(action, 1, POWER_MENU_OPEN_DELAY_MS);
        return true;
    }

    private void attemptClick(Action action, int attempt, int delayMillis) {
        handler.postDelayed(() -> {
            AccessibilityNodeInfo root = service.getRootInActiveWindow();
            if (root == null || !isLikelyPowerMenu(root)) {
                if (attempt < MAX_CLICK_ATTEMPTS) {
                    attemptClick(action, attempt + 1, POWER_MENU_OPEN_DELAY_MS);
                }
                return;
            }

            boolean clicked = clickActionButton(root, action);
            if (attempt < MAX_CLICK_ATTEMPTS) {
                attemptClick(
                        action,
                        attempt + 1,
                        clicked ? POWER_MENU_CONFIRM_DELAY_MS : POWER_MENU_OPEN_DELAY_MS
                );
            }
        }, delayMillis);
    }

    private boolean isLikelyPowerMenu(AccessibilityNodeInfo root) {
        if (root.getPackageName() == null) {
            return false;
        }

        String packageName = root.getPackageName().toString().toLowerCase(Locale.US);
        for (String part : POWER_MENU_PACKAGE_PARTS) {
            if (packageName.contains(part)) {
                return true;
            }
        }
        return false;
    }

    private boolean clickActionButton(AccessibilityNodeInfo node, Action action) {
        if (node == null) {
            return false;
        }

        String label = normalize(nodeLabel(node));
        if (TextNormalizer.hasText(label)
                && containsAny(label, action == Action.RESTART ? RESTART_WORDS : POWER_OFF_WORDS)
                && clickNode(node)) {
            return true;
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            if (clickActionButton(node.getChild(i), action)) {
                return true;
            }
        }
        return false;
    }

    private boolean clickNode(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo clickableNode = findClickableAncestor(node);
        return clickableNode != null && clickableNode.performAction(AccessibilityNodeInfo.ACTION_CLICK);
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

    private String nodeLabel(AccessibilityNodeInfo node) {
        StringBuilder builder = new StringBuilder();
        appendIfText(builder, node.getText());
        appendIfText(builder, node.getContentDescription());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            appendIfText(builder, node.getHintText());
        }
        return builder.toString();
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

    private boolean containsAny(String value, String[] candidates) {
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
