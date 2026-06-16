package com.example.anroidaiassistant.accessibility;

import android.accessibilityservice.AccessibilityService;
import android.os.Handler;
import android.view.accessibility.AccessibilityNodeInfo;

import com.example.anroidaiassistant.MyAccessibilityService;
import com.example.anroidaiassistant.R;
import com.example.anroidaiassistant.resources.QuickSettingsAliases;
import com.example.anroidaiassistant.util.TextNormalizer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class QuickSettingsTileController {
    public interface Feedback {
        void showMessage(String message);
    }

    private static final int FIRST_SEARCH_DELAY_MS = 450;
    private static final int NEXT_SEARCH_DELAY_MS = 450;
    private static final int BLUETOOTH_VERIFY_DELAY_MS = 1200;
    private static final int MAX_BLUETOOTH_CLICK_ATTEMPTS = 4;
    private static final int MAX_SEARCH_ATTEMPTS = 4;

    private final AccessibilityService service;
    private final Handler handler;
    private final GestureController gestureController;
    private final Map<String, TileTarget> targets = new HashMap<>();

    public QuickSettingsTileController(
            AccessibilityService service,
            Handler handler,
            GestureController gestureController
    ) {
        this.service = service;
        this.handler = handler;
        this.gestureController = gestureController;
        registerTargets();
    }

    public boolean setTileState(String intent, String rawState, Feedback feedback) {
        TileTarget target = targets.get(intent);
        if (target == null) {
            return false;
        }

        Boolean desiredEnabled = parseDesiredState(rawState);
        if (desiredEnabled == null) {
            if (feedback != null) {
                feedback.showMessage(message(R.string.feedback_quick_setting_state_not_supported));
            }
            return true;
        }

        boolean opened = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS);
        if (!opened) {
            if (feedback != null) {
                feedback.showMessage(message(R.string.feedback_quick_settings_unavailable));
            }
            return true;
        }

        handler.postDelayed(
                () -> findAndToggle(target, desiredEnabled, feedback, 0),
                FIRST_SEARCH_DELAY_MS
        );
        return true;
    }

    private void findAndToggle(
            TileTarget target,
            boolean desiredEnabled,
            Feedback feedback,
            int attempt
    ) {
        AccessibilityNodeInfo rootNode = service.getRootInActiveWindow();
        AccessibilityNodeInfo tileNode = findTileNode(rootNode, target);
        if (tileNode != null) {
            toggleTileIfNeeded(tileNode, target, desiredEnabled, feedback);
            return;
        }

        if (attempt >= MAX_SEARCH_ATTEMPTS - 1) {
            if (feedback != null) {
                feedback.showMessage(message(
                        R.string.feedback_quick_setting_not_found,
                        targetName(target)
                ));
            }
            return;
        }

        moveToNextQuickSettingsPage(attempt);
        handler.postDelayed(
                () -> findAndToggle(target, desiredEnabled, feedback, attempt + 1),
                NEXT_SEARCH_DELAY_MS
        );
    }

    private void toggleTileIfNeeded(
            AccessibilityNodeInfo tileNode,
            TileTarget target,
            boolean desiredEnabled,
            Feedback feedback
    ) {
        if (target.useBluetoothToggleFallback) {
            toggleBluetoothTile(tileNode, desiredEnabled, feedback);
            return;
        }

        Boolean currentState = readCheckedState(tileNode);
        if (currentState != null && currentState == desiredEnabled) {
            if (feedback != null) {
                feedback.showMessage(message(
                        R.string.feedback_quick_setting_already,
                        targetName(target),
                        stateText(desiredEnabled)
                ));
            }
            return;
        }

        AccessibilityNodeInfo clickableNode = findClickableNode(tileNode);
        boolean clicked = clickableNode != null
                ? clickableNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                : gestureController != null && gestureController.tapNodeCenter(tileNode);

        if (feedback != null) {
            feedback.showMessage(clicked
                    ? message(
                    R.string.feedback_quick_setting_requested,
                    targetName(target),
                    stateText(desiredEnabled)
            )
                    : message(
                    R.string.feedback_quick_setting_click_failed,
                    targetName(target)
            ));
        }
    }

    private AccessibilityNodeInfo findTileNode(AccessibilityNodeInfo node, TileTarget target) {
        if (node == null) {
            return null;
        }

        if (matchesTarget(node, target)) {
            return node;
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            AccessibilityNodeInfo result = findTileNode(child, target);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    private boolean matchesTarget(AccessibilityNodeInfo node, TileTarget target) {
        String nodeText = normalize(joinNodeText(node));
        if (!TextNormalizer.hasText(nodeText)) {
            return false;
        }

        for (String label : target.labels) {
            if (nodeText.contains(normalize(label))) {
                return true;
            }
        }
        return false;
    }

    private Boolean readCheckedState(AccessibilityNodeInfo node) {
        Boolean nodeState = readCheckedStateFromNodeAndAncestors(node);
        if (nodeState != null) {
            return nodeState;
        }
        return readCheckedStateFromChildren(node);
    }

    private Boolean readCheckedStateFromNodeAndAncestors(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo current = node;
        while (current != null) {
            if (current.isCheckable()) {
                return current.isChecked();
            }

            Boolean stateFromText = readStateFromText(joinNodeText(current));
            if (stateFromText != null) {
                return stateFromText;
            }
            current = current.getParent();
        }
        return null;
    }

    private Boolean readCheckedStateFromChildren(AccessibilityNodeInfo node) {
        if (node == null) {
            return null;
        }

        if (node.isCheckable()) {
            return node.isChecked();
        }

        Boolean stateFromText = readStateFromText(joinNodeText(node));
        if (stateFromText != null) {
            return stateFromText;
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            Boolean childState = readCheckedStateFromChildren(node.getChild(i));
            if (childState != null) {
                return childState;
            }
        }
        return null;
    }

    private Boolean readStateFromText(String value) {
        String normalized = normalize(value);
        if (!TextNormalizer.hasText(normalized)) {
            return null;
        }

        if (containsAny(normalized, QuickSettingsAliases.STATE_OFF_TEXTS)) {
            return false;
        }
        if (containsAny(normalized, QuickSettingsAliases.STATE_ON_TEXTS)) {
            return true;
        }
        return null;
    }

    private boolean containsAny(String value, String... candidates) {
        String padded = " " + value + " ";
        for (String candidate : candidates) {
            if (padded.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private AccessibilityNodeInfo findClickableNode(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo current = node;
        while (current != null) {
            if (current.isClickable()) {
                return current;
            }
            current = current.getParent();
        }
        return null;
    }

    private void toggleBluetoothTile(
            AccessibilityNodeInfo tileNode,
            boolean desiredEnabled,
            Feedback feedback
    ) {
        AccessibilityNodeInfo bluetoothNode = findBluetoothTextNode(tileNode);
        if (bluetoothNode == null) {
            bluetoothNode = tileNode;
        }

        List<AccessibilityNodeInfo> candidates = collectBluetoothClickCandidates(bluetoothNode);

        Boolean currentState = readBluetoothState(bluetoothNode, candidates);
        if (currentState != null && currentState == desiredEnabled) {
            if (feedback != null) {
                feedback.showMessage(message(
                        R.string.feedback_quick_setting_already,
                        targetName(targets.get("SET_BLUETOOTH")),
                        stateText(desiredEnabled)
                ));
            }
            return;
        }

        if (!clickBluetoothCandidate(candidates, 0)) {
            if (feedback != null) {
                feedback.showMessage(message(
                        R.string.feedback_quick_setting_click_failed,
                        targetName(targets.get("SET_BLUETOOTH"))
                ));
            }
            return;
        }

        if (feedback != null) {
            feedback.showMessage(message(
                    R.string.feedback_quick_setting_requested,
                    targetName(targets.get("SET_BLUETOOTH")),
                    stateText(desiredEnabled)
            ));
        }

        if (desiredEnabled) {
            return;
        }

        handler.postDelayed(
                () -> verifyBluetoothStateOrRetry(desiredEnabled, feedback, 1),
                BLUETOOTH_VERIFY_DELAY_MS
        );
    }

    private void verifyBluetoothStateOrRetry(
            boolean desiredEnabled,
            Feedback feedback,
            int nextCandidateIndex
    ) {
        AccessibilityNodeInfo rootNode = service.getRootInActiveWindow();
        AccessibilityNodeInfo bluetoothNode = findBluetoothTextNode(rootNode);
        if (bluetoothNode == null) {
            if (feedback != null) {
                feedback.showMessage(message(
                        R.string.feedback_quick_setting_not_found,
                        targetName(targets.get("SET_BLUETOOTH"))
                ));
            }
            return;
        }

        Boolean currentState = readStateFromText(joinNodeText(bluetoothNode));

        if (currentState != null && currentState == desiredEnabled) {
            return;
        }

        if (nextCandidateIndex >= MAX_BLUETOOTH_CLICK_ATTEMPTS) {
            if (feedback != null) {
                feedback.showMessage(message(
                        R.string.feedback_quick_setting_did_not_change,
                        targetName(targets.get("SET_BLUETOOTH"))
                ));
            }
            return;
        }

        if (!clickBluetoothCandidate(bluetoothNode, nextCandidateIndex)) {
            if (feedback != null) {
                feedback.showMessage(message(
                        R.string.feedback_quick_setting_click_failed,
                        targetName(targets.get("SET_BLUETOOTH"))
                ));
            }
            return;
        }

        handler.postDelayed(
                () -> verifyBluetoothStateOrRetry(
                        desiredEnabled,
                        feedback,
                        nextCandidateIndex + 1
                ),
                BLUETOOTH_VERIFY_DELAY_MS
        );
    }

    private boolean clickBluetoothCandidate(
            AccessibilityNodeInfo bluetoothNode,
            int candidateIndex
    ) {
        List<AccessibilityNodeInfo> candidates = collectBluetoothClickCandidates(bluetoothNode);

        return clickBluetoothCandidate(candidates, candidateIndex);
    }

    private boolean clickBluetoothCandidate(
            List<AccessibilityNodeInfo> candidates,
            int candidateIndex
    ) {
        if (candidateIndex >= candidates.size()) {
            return false;
        }

        AccessibilityNodeInfo candidate = candidates.get(candidateIndex);
        return candidate.performAction(AccessibilityNodeInfo.ACTION_CLICK);
    }

    private Boolean readBluetoothState(
            AccessibilityNodeInfo bluetoothNode,
            List<AccessibilityNodeInfo> candidates
    ) {
        for (AccessibilityNodeInfo candidate : candidates) {
            Boolean state = readBluetoothExplicitState(joinNodeText(candidate));
            if (state != null) {
                return state;
            }
        }

        Boolean state = readBluetoothExplicitState(joinNodeText(bluetoothNode));
        if (state != null) {
            return state;
        }

        for (AccessibilityNodeInfo candidate : candidates) {
            state = readBluetoothStateWithActionText(joinNodeText(candidate));
            if (state != null) {
                return state;
            }
        }
        return readBluetoothStateWithActionText(joinNodeText(bluetoothNode));
    }

    private Boolean readBluetoothExplicitState(String value) {
        String normalized = normalize(value);
        if (!TextNormalizer.hasText(normalized)) {
            return null;
        }

        if (containsAny(normalized, QuickSettingsAliases.BLUETOOTH_OFF_TEXTS)) {
            return false;
        }
        if (containsAny(normalized, QuickSettingsAliases.BLUETOOTH_ON_TEXTS)) {
            return true;
        }
        return null;
    }

    private Boolean readBluetoothStateWithActionText(String value) {
        String normalized = normalize(value);
        if (!TextNormalizer.hasText(normalized)) {
            return null;
        }

        if (containsAny(normalized, QuickSettingsAliases.BLUETOOTH_ENABLE_ACTION_TEXTS)) {
            return false;
        }
        if (containsAny(normalized, QuickSettingsAliases.BLUETOOTH_DISABLE_ACTION_TEXTS)) {
            return true;
        }
        return readStateFromText(value);
    }

    private List<AccessibilityNodeInfo> collectBluetoothClickCandidates(AccessibilityNodeInfo bluetoothNode) {
        List<AccessibilityNodeInfo> candidates = new ArrayList<>();
        TileTarget bluetoothTarget = targets.get("SET_BLUETOOTH");

        AccessibilityNodeInfo current = bluetoothNode;
        int depth = 0;
        while (current != null && depth < 8) {
            collectTargetClickableDescendants(current, bluetoothTarget, candidates);
            current = current.getParent();
            depth++;
        }
        return candidates;
    }

    private boolean collectTargetClickableDescendants(
            AccessibilityNodeInfo node,
            TileTarget target,
            List<AccessibilityNodeInfo> candidates
    ) {
        if (node == null) {
            return false;
        }

        boolean containsTarget = matchesTarget(node, target);
        for (int i = 0; i < node.getChildCount(); i++) {
            containsTarget = collectTargetClickableDescendants(
                    node.getChild(i),
                    target,
                    candidates
            ) || containsTarget;
        }

        if (containsTarget) {
            addCandidate(candidates, node);
        }
        return containsTarget;
    }

    private void addCandidate(
            List<AccessibilityNodeInfo> candidates,
            AccessibilityNodeInfo node
    ) {
        if (!canClick(node) || containsNode(candidates, node)) {
            return;
        }
        candidates.add(node);
    }

    private boolean containsNode(
            List<AccessibilityNodeInfo> candidates,
            AccessibilityNodeInfo node
    ) {
        for (AccessibilityNodeInfo candidate : candidates) {
            if (candidate == node) {
                return true;
            }
        }
        return false;
    }

    private boolean canClick(AccessibilityNodeInfo node) {
        return node != null
                && (node.isClickable()
                || node.isCheckable()
                || (node.getActions() & AccessibilityNodeInfo.ACTION_CLICK) != 0);
    }

    private AccessibilityNodeInfo findBluetoothTextNode(AccessibilityNodeInfo node) {
        if (node == null) {
            return null;
        }

        String nodeText = normalize(joinNodeText(node));
        if (nodeText.contains("bluetooth")) {
            return node;
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo result = findBluetoothTextNode(node.getChild(i));
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    private void moveToNextQuickSettingsPage(int attempt) {
        if (attempt == 0) {
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS);
            return;
        }

        if (gestureController != null) {
            gestureController.swipe("left");
        }
    }

    private Boolean parseDesiredState(String rawState) {
        String state = normalize(rawState);
        if ("on".equals(state) || "open".equals(state) || "enable".equals(state) || "enabled".equals(state)) {
            return true;
        }
        if ("off".equals(state) || "close".equals(state) || "disable".equals(state) || "disabled".equals(state)) {
            return false;
        }
        return null;
    }

    private String joinNodeText(AccessibilityNodeInfo node) {
        if (node == null) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        append(builder, node.getText());
        append(builder, node.getContentDescription());
        return builder.toString();
    }

    private void append(StringBuilder builder, CharSequence value) {
        if (value == null) {
            return;
        }
        if (builder.length() > 0) {
            builder.append(' ');
        }
        builder.append(value);
    }

    private String normalize(String value) {
        return TextNormalizer.normalizeAsciiText(value).toLowerCase(Locale.US);
    }

    private String stateText(boolean enabled) {
        return message(enabled
                ? R.string.quick_setting_state_on
                : R.string.quick_setting_state_off);
    }

    private String targetName(TileTarget target) {
        if (target == null) {
            return "";
        }
        if (service instanceof MyAccessibilityService) {
            return ((MyAccessibilityService) service).localizedQuickSettingName(
                    target.intent,
                    target.displayName
            );
        }
        return target.displayName;
    }

    private String message(int resId, Object... args) {
        if (service instanceof MyAccessibilityService) {
            return ((MyAccessibilityService) service).localizedOverlayString(resId, args);
        }
        if (args == null || args.length == 0) {
            return service.getString(resId);
        }
        return service.getString(resId, args);
    }

    private void registerTargets() {
        for (QuickSettingsAliases.TileSpec spec : QuickSettingsAliases.TILE_SPECS) {
            targets.put(
                    spec.intent,
                    new TileTarget(
                            spec.intent,
                            spec.displayName,
                            spec.labels,
                            spec.useBluetoothToggleFallback
                    )
            );
        }
    }

    private static final class TileTarget {
        private final String intent;
        private final String displayName;
        private final String[] labels;
        private final boolean useBluetoothToggleFallback;

        private TileTarget(String intent, String displayName, String[] labels) {
            this(intent, displayName, labels, false);
        }

        private TileTarget(
                String intent,
                String displayName,
                String[] labels,
                boolean useBluetoothToggleFallback
        ) {
            this.intent = intent;
            this.displayName = displayName;
            this.labels = labels;
            this.useBluetoothToggleFallback = useBluetoothToggleFallback;
        }
    }
}

