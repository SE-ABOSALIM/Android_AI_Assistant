package com.example.anroidaiassistant.accessibility;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.List;

public final class AccessibilityActionController {
    private final AccessibilityService service;

    public AccessibilityActionController(AccessibilityService service) {
        this.service = service;
    }

    public void performHome() {
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME);
    }

    public void performBack() {
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
    }

    public void performRecents() {
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS);
    }

    public void performNotifications() {
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS);
    }

    public void clickNodeByText(String text) {
        AccessibilityNodeInfo rootNode = service.getRootInActiveWindow();
        if (rootNode == null) {
            return;
        }

        List<AccessibilityNodeInfo> nodes = rootNode.findAccessibilityNodeInfosByText(text);
        for (AccessibilityNodeInfo node : nodes) {
            if (node.isClickable()) {
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                return;
            }
        }
    }
}
