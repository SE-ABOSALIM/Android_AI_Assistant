package com.example.anroidaiassistant.accessibility;

import android.accessibilityservice.AccessibilityService;
import android.os.Handler;
import android.view.accessibility.AccessibilityNodeInfo;

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
                feedback.showMessage("Quick setting state not supported");
            }
            return true;
        }

        boolean opened = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS);
        if (!opened) {
            if (feedback != null) {
                feedback.showMessage("Quick settings panel is unavailable");
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
                feedback.showMessage(target.displayName + " tile was not found");
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
                feedback.showMessage(target.displayName + " is already " + stateText(desiredEnabled));
            }
            return;
        }

        AccessibilityNodeInfo clickableNode = findClickableNode(tileNode);
        boolean clicked = clickableNode != null
                ? clickableNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                : gestureController != null && gestureController.tapNodeCenter(tileNode);

        if (feedback != null) {
            feedback.showMessage(clicked
                    ? target.displayName + " " + stateText(desiredEnabled) + " requested"
                    : target.displayName + " tile could not be clicked");
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

        if (containsAny(normalized, " off ", " kapali ", " kapalı ", " disabled ", " غير مفعل ", " ايقاف ")) {
            return false;
        }
        if (containsAny(normalized, " on ", " acik ", " açık ", " enabled ", " مفعل ", " تشغيل ")) {
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
                feedback.showMessage("Bluetooth is already " + stateText(desiredEnabled));
            }
            return;
        }

        if (!clickBluetoothCandidate(candidates, 0)) {
            if (feedback != null) {
                feedback.showMessage("Bluetooth tile could not be clicked");
            }
            return;
        }

        if (feedback != null) {
            feedback.showMessage("Bluetooth " + stateText(desiredEnabled) + " requested");
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
                feedback.showMessage("Bluetooth tile was not found");
            }
            return;
        }

        Boolean currentState = readStateFromText(joinNodeText(bluetoothNode));

        if (currentState != null && currentState == desiredEnabled) {
            return;
        }

        if (nextCandidateIndex >= MAX_BLUETOOTH_CLICK_ATTEMPTS) {
            if (feedback != null) {
                feedback.showMessage("Bluetooth did not change state");
            }
            return;
        }

        if (!clickBluetoothCandidate(bluetoothNode, nextCandidateIndex)) {
            if (feedback != null) {
                feedback.showMessage("Bluetooth tile could not be clicked");
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

        if (containsAny(
                normalized,
                " bluetooth off ",
                " bluetooth disabled ",
                " bluetooth kapali ",
                " bluetooth kapalÄ± "
        )) {
            return false;
        }
        if (containsAny(
                normalized,
                " bluetooth on ",
                " bluetooth enabled ",
                " bluetooth acik ",
                " bluetooth aÃ§Ä±k "
        )) {
            return true;
        }
        return null;
    }

    private Boolean readBluetoothStateWithActionText(String value) {
        String normalized = normalize(value);
        if (!TextNormalizer.hasText(normalized)) {
            return null;
        }

        if (containsAny(
                normalized,
                " turn on bluetooth ",
                " enable bluetooth ",
                " bluetooth u ac ",
                " bluetooth ac "
        )) {
            return false;
        }
        if (containsAny(
                normalized,
                " turn off bluetooth ",
                " disable bluetooth ",
                " bluetooth u kapat ",
                " bluetooth kapat "
        )) {
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
        return enabled ? "on" : "off";
    }

    private void registerTargets() {
        targets.put("SET_WIFI", new TileTarget("Wi-Fi", new String[]{
                "Wi-Fi", "Wifi", "Kablosuz", "واي فاي", "وايفاى"
        }));
        targets.put("SET_BLUETOOTH", new TileTarget("Bluetooth", new String[]{
                "Bluetooth", "بلوتوث"
        }, true));
        targets.put("SET_LOCATION", new TileTarget("Location", new String[]{
                "Location", "Konum", "موقع", "الموقع"
        }));
        targets.put("SET_MOBILE_DATA", new TileTarget("Mobile data", new String[]{
                "Mobile data", "Cellular data", "Mobil veri", "Hucresel veri", "Hücresel veri",
                "بيانات الهاتف", "بيانات الجوال", "بيانات المحمول"
        }));
        targets.put("SET_MOBILE_HOTSPOT", new TileTarget("Hotspot", new String[]{
                "Hotspot", "Mobile hotspot", "Personal hotspot", "Tethering",
                "Mobil hotspot", "Kisisel erisim noktasi", "Kişisel erişim noktası", "Erisim noktasi",
                "نقطة الاتصال", "نقطه الاتصال", "هوتسبوت"
        }));
    }

    private static final class TileTarget {
        private final String displayName;
        private final String[] labels;
        private final boolean useBluetoothToggleFallback;

        private TileTarget(String displayName, String[] labels) {
            this(displayName, labels, false);
        }

        private TileTarget(String displayName, String[] labels, boolean useBluetoothToggleFallback) {
            this.displayName = displayName;
            this.labels = labels;
            this.useBluetoothToggleFallback = useBluetoothToggleFallback;
        }
    }
}
