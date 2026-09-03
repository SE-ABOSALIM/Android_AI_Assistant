package com.example.anroidaiassistant.accessibility.click;

import android.graphics.Rect;
import android.util.DisplayMetrics;
import android.view.accessibility.AccessibilityNodeInfo;

import com.example.anroidaiassistant.MyAccessibilityService;
import com.example.anroidaiassistant.accessibility.GestureController;
import com.example.anroidaiassistant.util.SensitiveDebugLog;
import com.example.anroidaiassistant.util.TextNormalizer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ClickItemController {
    private static final String TAG = "ClickItem";
    private static final int DIRECT_MIN_SCORE = 80;
    private static final int DIRECT_MARGIN = 12;
    private static final int FALLBACK_MIN_SCORE = 58;
    private static final int FALLBACK_MAX_SCORE_GAP = 12;

    private final MyAccessibilityService service;
    private final GestureController gestureController;
    private final ClickPositionFilter positionFilter = new ClickPositionFilter();
    private final ClickIconAliasMatcher aliasMatcher = new ClickIconAliasMatcher();
    private final ClickCandidateCollector candidateCollector = new ClickCandidateCollector(
            new ClickTextMatcher(),
            positionFilter
    );

    private enum TargetAction {
        CLICK,
        LONG_PRESS,
        DOUBLE_TAP
    }

    public ClickItemController(MyAccessibilityService service, GestureController gestureController) {
        this.service = service;
        this.gestureController = gestureController;
    }

    public boolean clickItem(String targetText, String position) {
        return performOnTarget(targetText, position, TargetAction.CLICK);
    }

    public boolean longPressItem(String targetText, String position) {
        return performOnTarget(targetText, position, TargetAction.LONG_PRESS);
    }

    public boolean doubleTapItem(String targetText, String position) {
        return performOnTarget(targetText, position, TargetAction.DOUBLE_TAP);
    }

    private boolean performOnTarget(String targetText, String position, TargetAction action) {
        ClickCommand command = new ClickCommand(
                targetText,
                positionFilter.normalizePosition(position)
        );
        if (!command.isValid()) {
            return false;
        }

        if (action == TargetAction.CLICK
                && command.hasTargetText()
                && service.pressKeyboardAction(command.targetText)) {
            return true;
        }

        AccessibilityNodeInfo rootNode = service.getRootInActiveWindow();
        if (rootNode == null) {
            return false;
        }

        DisplayMetrics displayMetrics = service.getResources().getDisplayMetrics();
        String packageName = rootNode.getPackageName() == null ? "" : rootNode.getPackageName().toString();

        return performByTargetText(rootNode, command, packageName, displayMetrics, action);
    }

    private boolean performByTargetText(
            AccessibilityNodeInfo rootNode,
            ClickCommand command,
            String packageName,
            DisplayMetrics displayMetrics,
            TargetAction action
    ) {
        List<ClickCandidate> candidates = candidateCollector.collectTextCandidates(
                rootNode,
                aliasMatcher.targetVariants(command.targetText, packageName),
                command.position,
                displayMetrics.widthPixels,
                displayMetrics.heightPixels
        );

        candidates.sort(bestMatchComparator());
        logCandidates("collected", command, candidates);
        ClickCandidate directCandidate = chooseDirectCandidate(candidates);
        if (directCandidate != null) {
            logCandidate("direct", command, directCandidate);
            return performCandidate(directCandidate, action);
        }

        if (showFallbackIfUseful(candidates, action)) {
            logCandidates("selection", command, topFallbackCandidates(candidates));
            return true;
        }

        boolean fallbackHandled = performTopBarIconFallback(rootNode, command, displayMetrics, action);
        if (!fallbackHandled) {
            SensitiveDebugLog.info(
                    TAG,
                    "no_match | target=\"" + command.targetText
                            + "\" | position=\"" + command.position + "\""
            );
        }
        return fallbackHandled;
    }

    private ClickCandidate chooseDirectCandidate(List<ClickCandidate> candidates) {
        if (candidates.isEmpty()) {
            return null;
        }

        ClickCandidate top = candidates.get(0);
        if (top.score < DIRECT_MIN_SCORE) {
            return null;
        }

        if (candidates.size() == 1) {
            return top;
        }

        ClickCandidate second = candidates.get(1);
        if (ClickCandidateRankingPolicy.shouldSelectDirect(
                top.matchClass,
                top.score,
                second.matchClass,
                second.score,
                DIRECT_MIN_SCORE,
                DIRECT_MARGIN
        )) {
            return top;
        }

        return null;
    }

    private boolean showFallbackIfUseful(List<ClickCandidate> candidates, TargetAction action) {
        List<ClickCandidate> fallbackCandidates = topFallbackCandidates(candidates);
        if (fallbackCandidates.isEmpty()) {
            return false;
        }

        if (fallbackCandidates.size() == 1) {
            ClickCandidate singleCandidate = fallbackCandidates.get(0);
            SensitiveDebugLog.info(
                    TAG,
                    "single_fallback_direct | " + formatCandidate(singleCandidate)
            );
            if (!performCandidate(singleCandidate, action)) {
                service.showFeedback(actionFailureMessage(action));
            }
            return true;
        }

        List<MyAccessibilityService.ClickTargetChoice> choices = new ArrayList<>();
        for (ClickCandidate candidate : fallbackCandidates) {
            choices.add(new MyAccessibilityService.ClickTargetChoice(
                    displayTitle(candidate),
                    displaySubtitle(candidate),
                    candidate.bounds
            ));
        }

        service.startClickTargetSelection(
                choices,
                new MyAccessibilityService.NumberSelectionCallback() {
                    @Override
                    public void onSelected(int selectedIndex) {
                        if (selectedIndex < 0 || selectedIndex >= fallbackCandidates.size()) {
                            service.showFeedback("Item not found");
                            return;
                        }
                        if (!performCandidate(fallbackCandidates.get(selectedIndex), action)) {
                            service.showFeedback(actionFailureMessage(action));
                        }
                    }

                    @Override
                    public void onCancelled() {
                        service.showFeedback("Selection cancelled.");
                    }
                },
                selectionHint()
        );
        return true;
    }

    private List<ClickCandidate> topFallbackCandidates(List<ClickCandidate> candidates) {
        List<ClickCandidate> filtered = new ArrayList<>();
        int topScore = candidates.isEmpty() ? 0 : candidates.get(0).score;
        ClickMatchClass topMatchClass = candidates.isEmpty()
                ? ClickMatchClass.NONE
                : candidates.get(0).matchClass;
        for (ClickCandidate candidate : candidates) {
            if (ClickCandidateRankingPolicy.isFallbackPeer(
                    topMatchClass,
                    topScore,
                    candidate.matchClass,
                    candidate.score,
                    FALLBACK_MIN_SCORE,
                    FALLBACK_MAX_SCORE_GAP
            )) {
                filtered.add(candidate);
            }
        }

        filtered.sort(bestMatchComparator());
        return filtered;
    }

    private boolean clickCandidate(ClickCandidate candidate) {
        if (candidate.clickNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            return true;
        }
        Rect fallbackBounds = candidate.preferBoundsTap ? candidate.bounds : candidate.actionBounds;
        return gestureController.tapBoundsCenter(fallbackBounds);
    }

    private boolean performCandidate(ClickCandidate candidate, TargetAction action) {
        switch (action) {
            case LONG_PRESS:
                return gestureController.longPressBoundsCenter(candidate.bounds);
            case DOUBLE_TAP:
                return gestureController.doubleTapBoundsCenter(candidate.bounds);
            case CLICK:
            default:
                return clickCandidate(candidate);
        }
    }

    private boolean performTopBarIconFallback(
            AccessibilityNodeInfo rootNode,
            ClickCommand command,
            DisplayMetrics displayMetrics,
            TargetAction action
    ) {
        ClickCandidate candidate = null;
        if (aliasMatcher.isDrawerTarget(command.targetText)) {
            candidate = findTopBarIconCandidate(rootNode, command.position, displayMetrics, true);
        } else if (aliasMatcher.isDropdownTarget(command.targetText)) {
            candidate = findTopBarIconCandidate(rootNode, command.position, displayMetrics, false);
        }

        if (candidate != null) {
            logCandidate("top_bar_fallback", command, candidate);
            return performCandidate(candidate, action);
        }
        return false;
    }

    private ClickCandidate findTopBarIconCandidate(
            AccessibilityNodeInfo node,
            String position,
            DisplayMetrics displayMetrics,
            boolean preferRightEdge
    ) {
        if (node == null || displayMetrics.widthPixels <= 0 || displayMetrics.heightPixels <= 0) {
            return null;
        }

        ClickCandidate best = scoreTopBarIconCandidate(node, position, displayMetrics, preferRightEdge);
        for (int i = 0; i < node.getChildCount(); i++) {
            ClickCandidate childBest = findTopBarIconCandidate(
                    node.getChild(i),
                    position,
                    displayMetrics,
                    preferRightEdge
            );
            if (childBest != null && (best == null || childBest.score > best.score)) {
                best = childBest;
            }
        }
        return best;
    }

    private ClickCandidate scoreTopBarIconCandidate(
            AccessibilityNodeInfo node,
            String position,
            DisplayMetrics displayMetrics,
            boolean preferRightEdge
    ) {
        if (!node.isVisibleToUser()
                || !(node.isClickable() || (node.getActions() & AccessibilityNodeInfo.ACTION_CLICK) != 0)) {
            return null;
        }

        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        if (bounds.isEmpty()
                || !positionFilter.matches(bounds, position, displayMetrics.widthPixels, displayMetrics.heightPixels)
                || !isTopBarIconBounds(bounds, displayMetrics, preferRightEdge)) {
            return null;
        }

        int score = topBarIconScore(bounds, displayMetrics, preferRightEdge)
                + positionFilter.score(bounds, position, displayMetrics.widthPixels, displayMetrics.heightPixels);
        return new ClickCandidate(
                node,
                bounds,
                "Icon at " + bounds.centerX() + ", " + bounds.centerY(),
                score,
                preferRightEdge ? "top_right_icon_fallback" : "top_dropdown_icon_fallback"
        );
    }

    private boolean isTopBarIconBounds(Rect bounds, DisplayMetrics displayMetrics, boolean preferRightEdge) {
        float centerX = bounds.centerX() / (float) displayMetrics.widthPixels;
        float centerY = bounds.centerY() / (float) displayMetrics.heightPixels;
        float widthRatio = bounds.width() / (float) displayMetrics.widthPixels;
        float heightRatio = bounds.height() / (float) displayMetrics.heightPixels;

        if (centerY > 0.18f || heightRatio > 0.16f || widthRatio <= 0.01f) {
            return false;
        }
        if (preferRightEdge) {
            return centerX >= 0.70f && widthRatio <= 0.22f;
        }
        return centerX >= 0.30f && centerX <= 0.88f && widthRatio <= 0.50f;
    }

    private int topBarIconScore(Rect bounds, DisplayMetrics displayMetrics, boolean preferRightEdge) {
        float centerX = bounds.centerX() / (float) displayMetrics.widthPixels;
        float centerY = bounds.centerY() / (float) displayMetrics.heightPixels;
        float widthRatio = bounds.width() / (float) displayMetrics.widthPixels;
        float targetX = preferRightEdge ? 1.0f : 0.70f;

        int horizontalScore = Math.round((1.0f - Math.abs(targetX - centerX)) * 80);
        int verticalScore = Math.round((1.0f - centerY) * 20);
        int compactScore = Math.round((1.0f - Math.min(1.0f, widthRatio * 3.0f)) * 10);
        return horizontalScore + verticalScore + compactScore;
    }

    private Comparator<ClickCandidate> bestMatchComparator() {
        return (first, second) -> {
            int ranking = ClickCandidateRankingPolicy.compareBestFirst(
                    first.matchClass,
                    first.score,
                    second.matchClass,
                    second.score
            );
            if (ranking != 0) {
                return ranking;
            }
            int topComparison = Integer.compare(first.bounds.top, second.bounds.top);
            return topComparison != 0
                    ? topComparison
                    : Integer.compare(first.bounds.left, second.bounds.left);
        };
    }

    private String displayTitle(ClickCandidate candidate) {
        if (TextNormalizer.hasText(candidate.label)) {
            return candidate.label;
        }
        return "Item";
    }

    private String displaySubtitle(ClickCandidate candidate) {
        return "";
    }

    private String actionFailureMessage(TargetAction action) {
        return action == TargetAction.CLICK
                ? "Item could not be clicked"
                : "Gesture could not be performed";
    }

    private void logCandidates(String stage, ClickCommand command, List<ClickCandidate> candidates) {
        if (candidates.isEmpty()) {
            SensitiveDebugLog.info(
                    TAG,
                    stage + " | target=\"" + command.targetText + "\" | candidates=0"
            );
            return;
        }

        StringBuilder builder = new StringBuilder();
        int count = Math.min(5, candidates.size());
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                builder.append(" || ");
            }
            builder.append(formatCandidate(candidates.get(i)));
        }
        SensitiveDebugLog.info(TAG, stage
                + " | target=\"" + command.targetText + "\""
                + " | position=\"" + command.position + "\""
                + " | candidates=" + candidates.size()
                + " | top=" + builder);
    }

    private void logCandidate(String stage, ClickCommand command, ClickCandidate candidate) {
        SensitiveDebugLog.info(TAG, stage
                + " | target=\"" + command.targetText + "\""
                + " | position=\"" + command.position + "\""
                + " | " + formatCandidate(candidate));
    }

    private String formatCandidate(ClickCandidate candidate) {
        return "score=" + candidate.score
                + ", matchClass=" + candidate.matchClass
                + ", source=" + candidate.matchSource
                + ", reason=" + candidate.reason
                + ", matchedTarget=\"" + candidate.matchedTarget + "\""
                + ", matchFamily=\"" + candidate.matchFamily + "\""
                + ", matchedText=\"" + candidate.matchedText + "\""
                + ", label=\"" + candidate.label + "\""
                + ", bounds=" + candidate.bounds.toShortString()
                + ", actionBounds=" + candidate.actionBounds.toShortString();
    }

    private String selectionHint() {
        if ("EN".equalsIgnoreCase(service.getSelectedLanguage())) {
            return "Say the number of the item you want.";
        }
        if ("AR".equalsIgnoreCase(service.getSelectedLanguage())) {
            return "\u0642\u0644 \u0631\u0642\u0645 \u0627\u0644\u0639\u0646\u0635\u0631 \u0627\u0644\u0630\u064A \u062A\u0631\u064A\u062F\u0647.";
        }
        return "Istediginiz ogenin numarasini soyleyin.";
    }
}
