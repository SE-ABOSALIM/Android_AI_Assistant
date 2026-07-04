package com.example.anroidaiassistant.accessibility;

import android.graphics.Rect;
import android.util.DisplayMetrics;
import android.view.accessibility.AccessibilityNodeInfo;

import com.example.anroidaiassistant.MyAccessibilityService;
import com.example.anroidaiassistant.util.TextNormalizer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ScreenLabelsController {
    private static final int MAX_LABELS = 100;
    private static final int MIN_TARGET_SIZE_PX = 12;
    private static final int NEAR_DUPLICATE_CENTER_DISTANCE_DP = 34;
    private static final float STRONG_OVERLAP_RATIO = 0.70f;
    private static final float WEAK_OVERLAP_RATIO = 0.10f;

    private final MyAccessibilityService service;
    private final GestureController gestureController;
    private final List<LabelTarget> activeTargets = new ArrayList<>();

    public ScreenLabelsController(MyAccessibilityService service, GestureController gestureController) {
        this.service = service;
        this.gestureController = gestureController;
    }

    public boolean showLabels() {
        AccessibilityNodeInfo rootNode = service.getRootInActiveWindow();
        if (rootNode == null) {
            return false;
        }

        activeTargets.clear();
        List<LabelTarget> collectedTargets = new ArrayList<>();
        DisplayMetrics displayMetrics = service.getResources().getDisplayMetrics();
        collectTargets(
                rootNode,
                collectedTargets,
                new HashSet<>(),
                displayMetrics.widthPixels,
                displayMetrics.heightPixels
        );
        activeTargets.addAll(dedupeTargets(collectedTargets));
        activeTargets.sort(targetOrderComparator());

        if (activeTargets.isEmpty()) {
            return false;
        }

        if (activeTargets.size() > MAX_LABELS) {
            activeTargets.subList(MAX_LABELS, activeTargets.size()).clear();
        }

        service.startClickTargetSelection(
                toChoices(activeTargets),
                new MyAccessibilityService.NumberSelectionCallback() {
                    @Override
                    public void onSelected(int selectedIndex) {
                        if (selectedIndex < 0 || selectedIndex >= activeTargets.size()) {
                            service.showFeedback("Item not found");
                            activeTargets.clear();
                            return;
                        }

                        LabelTarget target = activeTargets.get(selectedIndex);
                        activeTargets.clear();
                        if (!clickTarget(target)) {
                            service.showFeedback("Item could not be clicked");
                        }
                    }

                    @Override
                    public void onCancelled() {
                        activeTargets.clear();
                        service.showFeedback("Labels cancelled.");
                    }
                },
                selectionHint()
        );
        return true;
    }

    private List<MyAccessibilityService.ClickTargetChoice> toChoices(List<LabelTarget> targets) {
        List<MyAccessibilityService.ClickTargetChoice> choices = new ArrayList<>();
        for (LabelTarget target : targets) {
            choices.add(new MyAccessibilityService.ClickTargetChoice(
                    target.title,
                    "",
                    target.bounds
            ));
        }
        return choices;
    }

    private Comparator<LabelTarget> targetOrderComparator() {
        Comparator<LabelTarget> comparator = Comparator
                .comparingInt((LabelTarget target) -> target.bounds.top)
                .thenComparingInt(target -> isArabicUi() ? -target.bounds.right : target.bounds.left)
                .thenComparingInt(target -> target.bounds.height())
                .thenComparingInt(target -> target.bounds.width());
        return comparator;
    }

    private boolean isArabicUi() {
        return "AR".equalsIgnoreCase(service.getSelectedLanguage());
    }

    private void collectTargets(
            AccessibilityNodeInfo node,
            List<LabelTarget> targets,
            Set<String> seenBounds,
            int screenWidth,
            int screenHeight
    ) {
        if (node == null || targets.size() >= MAX_LABELS * 3 || !node.isVisibleToUser()) {
            return;
        }

        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        if (isUsableTarget(node, bounds)) {
            LabelTarget target = buildLabelTarget(node, bounds, screenWidth, screenHeight);
            String boundsKey = target.bounds.flattenToString();
            if (!seenBounds.contains(boundsKey)) {
                seenBounds.add(boundsKey);
                targets.add(target);
            }
        }

        for (int i = 0; i < node.getChildCount() && targets.size() < MAX_LABELS * 3; i++) {
            collectTargets(node.getChild(i), targets, seenBounds, screenWidth, screenHeight);
        }
    }

    private LabelTarget buildLabelTarget(
            AccessibilityNodeInfo node,
            Rect bounds,
            int screenWidth,
            int screenHeight
    ) {
        if (isIndependentControl(node, bounds, screenWidth, screenHeight)) {
            return new LabelTarget(node, bounds, nodeTitle(node));
        }

        ActionGroup actionGroup = findActionGroup(node, bounds, screenWidth, screenHeight);
        if (actionGroup == null) {
            return new LabelTarget(node, bounds, nodeTitle(node));
        }

        String title = nodeTitle(actionGroup.node);
        if (!TextNormalizer.hasText(title) || "Item".equals(title)) {
            title = nodeTitle(node);
        }
        return new LabelTarget(actionGroup.node, actionGroup.bounds, title);
    }

    private List<LabelTarget> dedupeTargets(List<LabelTarget> targets) {
        List<LabelTarget> deduped = new ArrayList<>();
        for (LabelTarget target : targets) {
            addOrReplaceEquivalentTarget(deduped, target);
        }
        return deduped;
    }

    private void addOrReplaceEquivalentTarget(List<LabelTarget> targets, LabelTarget candidate) {
        for (int i = 0; i < targets.size(); i++) {
            LabelTarget existing = targets.get(i);
            if (!isEquivalentTarget(existing, candidate)) {
                continue;
            }
            if (isBetterTarget(candidate, existing)) {
                targets.set(i, candidate);
            }
            return;
        }
        targets.add(candidate);
    }

    private boolean isEquivalentTarget(LabelTarget first, LabelTarget second) {
        if (sameBounds(first.bounds, second.bounds)) {
            return true;
        }

        float overlapRatio = overlapRatio(first.bounds, second.bounds);
        float sizeRatio = sizeRatio(first.bounds, second.bounds);
        int centerDistance = centerDistance(first.bounds, second.bounds);
        int nearDuplicateDistance = dp(NEAR_DUPLICATE_CENTER_DISTANCE_DP);
        if (overlapRatio >= STRONG_OVERLAP_RATIO
                && (sizeRatio >= 0.45f || centerDistance <= nearDuplicateDistance)) {
            return true;
        }

        return overlapRatio >= WEAK_OVERLAP_RATIO
                && centerDistance <= nearDuplicateDistance;
    }

    private boolean isBetterTarget(LabelTarget candidate, LabelTarget existing) {
        int candidateScore = targetQualityScore(candidate);
        int existingScore = targetQualityScore(existing);
        if (candidateScore != existingScore) {
            return candidateScore > existingScore;
        }

        int candidateArea = area(candidate.bounds);
        int existingArea = area(existing.bounds);
        if (candidateArea != existingArea) {
            return candidateArea < existingArea;
        }

        return candidate.bounds.top < existing.bounds.top
                || (candidate.bounds.top == existing.bounds.top && candidate.bounds.left < existing.bounds.left);
    }

    private int targetQualityScore(LabelTarget target) {
        int score = 0;
        if (TextNormalizer.hasText(target.title) && !"Item".equals(target.title)) {
            score += 20;
        }
        if (target.bounds.width() <= dp(96) && target.bounds.height() <= dp(96)) {
            score += 4;
        }
        if (target.bounds.width() >= dp(32) && target.bounds.height() >= dp(32)) {
            score += 2;
        }
        return score;
    }

    private boolean isUsableTarget(AccessibilityNodeInfo node, Rect bounds) {
        if (bounds == null
                || bounds.isEmpty()
                || bounds.width() < MIN_TARGET_SIZE_PX
                || bounds.height() < MIN_TARGET_SIZE_PX
                || !node.isEnabled()) {
            return false;
        }

        return node.isClickable() || supportsAction(node, AccessibilityNodeInfo.ACTION_CLICK);
    }

    private ActionGroup findActionGroup(
            AccessibilityNodeInfo node,
            Rect nodeBounds,
            int screenWidth,
            int screenHeight
    ) {
        AccessibilityNodeInfo current = node.getParent();
        ActionGroup best = null;
        while (current != null) {
            Rect parentBounds = new Rect();
            current.getBoundsInScreen(parentBounds);
            if (isUsableTarget(current, parentBounds)
                    && parentBounds.contains(nodeBounds)
                    && isReasonableActionGroup(parentBounds, screenWidth, screenHeight)) {
                best = new ActionGroup(current, parentBounds);
            }
            current = current.getParent();
        }
        return best;
    }

    private boolean isReasonableActionGroup(Rect bounds, int screenWidth, int screenHeight) {
        if (bounds == null || bounds.isEmpty() || screenWidth <= 0 || screenHeight <= 0) {
            return false;
        }

        int screenArea = screenWidth * screenHeight;
        int boundsArea = area(bounds);
        if (boundsArea <= 0 || boundsArea > screenArea * 0.65f) {
            return false;
        }

        return bounds.width() <= screenWidth
                && bounds.height() <= screenHeight * 0.55f
                && bounds.width() >= dp(72)
                && bounds.height() >= dp(48);
    }

    private boolean isIndependentControl(
            AccessibilityNodeInfo node,
            Rect bounds,
            int screenWidth,
            int screenHeight
    ) {
        if (bounds == null || bounds.isEmpty() || screenWidth <= 0 || screenHeight <= 0) {
            return false;
        }

        boolean compactControl = bounds.width() <= dp(96) && bounds.height() <= dp(96);
        boolean rightEdgeControl = bounds.centerX() >= screenWidth * 0.80f;
        boolean hasControlSemanticName = hasControlSemanticName(node);
        boolean classLooksLikeButton = className(node).contains("button")
                || className(node).contains("imagebutton");

        return compactControl && (rightEdgeControl || hasControlSemanticName || classLooksLikeButton);
    }

    private boolean clickTarget(LabelTarget target) {
        if (target.node != null && target.node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            return true;
        }
        return gestureController != null && gestureController.tapBoundsCenter(target.bounds);
    }

    private boolean supportsAction(AccessibilityNodeInfo node, int actionId) {
        List<AccessibilityNodeInfo.AccessibilityAction> actions = node.getActionList();
        if (actions == null) {
            return false;
        }

        for (AccessibilityNodeInfo.AccessibilityAction action : actions) {
            if (action != null && action.getId() == actionId) {
                return true;
            }
        }
        return false;
    }

    private boolean hasControlSemanticName(AccessibilityNodeInfo node) {
        String combined = TextNormalizer.normalizeAsciiText(
                nodeTitle(node) + " " + charSequenceToString(node.getContentDescription()) + " " + node.getViewIdResourceName()
        );
        return containsAny(
                combined,
                "back",
                "geri",
                "close",
                "kapat",
                "menu",
                "more",
                "options",
                "search",
                "ara",
                "home",
                "shorts",
                "subscriptions",
                "share",
                "paylas",
                "like",
                "comment",
                "play",
                "pause",
                "mute",
                "unmute",
                "caption",
                "captions",
                "cc",
                "install",
                "watch",
                "signup",
                "sign up"
        );
    }

    private String className(AccessibilityNodeInfo node) {
        CharSequence className = node.getClassName();
        return className == null ? "" : className.toString().toLowerCase();
    }

    private boolean containsAny(String value, String... candidates) {
        if (!TextNormalizer.hasText(value)) {
            return false;
        }
        for (String candidate : candidates) {
            if (value.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private String nodeTitle(AccessibilityNodeInfo node) {
        String contentDescription = charSequenceToString(node.getContentDescription());
        if (TextNormalizer.hasText(contentDescription)) {
            return contentDescription;
        }

        String text = charSequenceToString(node.getText());
        if (TextNormalizer.hasText(text)) {
            return text;
        }

        String viewId = node.getViewIdResourceName();
        if (TextNormalizer.hasText(viewId)) {
            int slashIndex = viewId.lastIndexOf('/');
            return slashIndex >= 0 && slashIndex + 1 < viewId.length()
                    ? viewId.substring(slashIndex + 1)
                    : viewId;
        }

        return "Item";
    }

    private String charSequenceToString(CharSequence value) {
        return value == null ? "" : value.toString().trim();
    }

    private String selectionHint() {
        if ("EN".equalsIgnoreCase(service.getSelectedLanguage())) {
            return "Say the number of the label you want.";
        }
        if ("AR".equalsIgnoreCase(service.getSelectedLanguage())) {
            return "\u0642\u0644 \u0631\u0642\u0645 \u0627\u0644\u0639\u0646\u0635\u0631 \u0627\u0644\u0630\u064a \u062a\u0631\u064a\u062f\u0647.";
        }
        return "Istediginiz etiketin numarasini soyleyin.";
    }

    private boolean sameBounds(Rect first, Rect second) {
        return first.left == second.left
                && first.top == second.top
                && first.right == second.right
                && first.bottom == second.bottom;
    }

    private float overlapRatio(Rect first, Rect second) {
        Rect intersection = new Rect(first);
        if (!intersection.intersect(second)) {
            return 0f;
        }

        int smallerArea = Math.min(area(first), area(second));
        if (smallerArea <= 0) {
            return 0f;
        }
        return area(intersection) / (float) smallerArea;
    }

    private int centerDistance(Rect first, Rect second) {
        int deltaX = first.centerX() - second.centerX();
        int deltaY = first.centerY() - second.centerY();
        return Math.round((float) Math.sqrt(deltaX * deltaX + deltaY * deltaY));
    }

    private int area(Rect bounds) {
        return Math.max(0, bounds.width()) * Math.max(0, bounds.height());
    }

    private float sizeRatio(Rect first, Rect second) {
        int smallerArea = Math.min(area(first), area(second));
        int largerArea = Math.max(area(first), area(second));
        if (largerArea <= 0) {
            return 0f;
        }
        return smallerArea / (float) largerArea;
    }

    private int dp(int value) {
        return Math.round(value * service.getResources().getDisplayMetrics().density);
    }

    private static final class LabelTarget {
        private final AccessibilityNodeInfo node;
        private final Rect bounds;
        private final String title;

        private LabelTarget(AccessibilityNodeInfo node, Rect bounds, String title) {
            this.node = node;
            this.bounds = new Rect(bounds);
            this.title = title;
        }
    }

    private static final class ActionGroup {
        private final AccessibilityNodeInfo node;
        private final Rect bounds;

        private ActionGroup(AccessibilityNodeInfo node, Rect bounds) {
            this.node = node;
            this.bounds = new Rect(bounds);
        }
    }
}
