package com.example.anroidaiassistant.accessibility.click;

import android.graphics.Rect;
import android.util.DisplayMetrics;
import android.view.accessibility.AccessibilityNodeInfo;

import com.example.anroidaiassistant.MyAccessibilityService;
import com.example.anroidaiassistant.accessibility.GestureController;
import com.example.anroidaiassistant.util.TextNormalizer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ClickItemController {
    private static final int DIRECT_MIN_SCORE = 66;
    private static final int DIRECT_MARGIN = 12;
    private static final int FALLBACK_MIN_SCORE = 48;
    private static final int MAX_FALLBACK_CANDIDATES = 5;

    private final MyAccessibilityService service;
    private final GestureController gestureController;
    private final ClickPositionFilter positionFilter = new ClickPositionFilter();
    private final ClickIconAliasMatcher aliasMatcher = new ClickIconAliasMatcher();
    private final ClickCandidateCollector candidateCollector = new ClickCandidateCollector(
            new ClickTextMatcher(),
            positionFilter
    );

    public ClickItemController(MyAccessibilityService service, GestureController gestureController) {
        this.service = service;
        this.gestureController = gestureController;
    }

    public boolean clickItem(String targetText, String position) {
        ClickCommand command = new ClickCommand(
                targetText,
                positionFilter.normalizePosition(position)
        );
        if (!command.isValid()) {
            return false;
        }

        if (command.hasTargetText() && service.pressKeyboardAction(command.targetText)) {
            return true;
        }

        AccessibilityNodeInfo rootNode = service.getRootInActiveWindow();
        if (rootNode == null) {
            return false;
        }

        DisplayMetrics displayMetrics = service.getResources().getDisplayMetrics();
        String packageName = rootNode.getPackageName() == null ? "" : rootNode.getPackageName().toString();

        return clickByTargetText(rootNode, command, packageName, displayMetrics);
    }

    private boolean clickByTargetText(
            AccessibilityNodeInfo rootNode,
            ClickCommand command,
            String packageName,
            DisplayMetrics displayMetrics
    ) {
        List<ClickCandidate> candidates = candidateCollector.collectTextCandidates(
                rootNode,
                aliasMatcher.targetVariants(command.targetText, packageName),
                command.position,
                displayMetrics.widthPixels,
                displayMetrics.heightPixels
        );

        candidates.sort(bestMatchComparator());
        ClickCandidate directCandidate = chooseDirectCandidate(candidates);
        if (directCandidate != null) {
            return clickCandidate(directCandidate);
        }

        if (showFallbackIfUseful(candidates)) {
            return true;
        }

        return clickTopBarIconFallback(rootNode, command, displayMetrics);
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
        if (top.score - second.score >= DIRECT_MARGIN) {
            return top;
        }

        return null;
    }

    private boolean showFallbackIfUseful(List<ClickCandidate> candidates) {
        List<ClickCandidate> fallbackCandidates = topFallbackCandidates(candidates);
        if (fallbackCandidates.isEmpty()) {
            return false;
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
                        if (!clickCandidate(fallbackCandidates.get(selectedIndex))) {
                            service.showFeedback("Item could not be clicked");
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
        for (ClickCandidate candidate : candidates) {
            if (candidate.score >= FALLBACK_MIN_SCORE) {
                filtered.add(candidate);
            }
        }

        filtered.sort(bestMatchComparator());
        if (filtered.size() > MAX_FALLBACK_CANDIDATES) {
            return new ArrayList<>(filtered.subList(0, MAX_FALLBACK_CANDIDATES));
        }
        return filtered;
    }

    private boolean clickCandidate(ClickCandidate candidate) {
        if (candidate.preferBoundsTap && gestureController.tapBoundsCenter(candidate.bounds)) {
            return true;
        }

        return clickNode(candidate.clickNode);
    }

    private boolean clickTopBarIconFallback(
            AccessibilityNodeInfo rootNode,
            ClickCommand command,
            DisplayMetrics displayMetrics
    ) {
        ClickCandidate candidate = null;
        if (aliasMatcher.isDrawerTarget(command.targetText)) {
            candidate = findTopBarIconCandidate(rootNode, command.position, displayMetrics, true);
        } else if (aliasMatcher.isDropdownTarget(command.targetText)) {
            candidate = findTopBarIconCandidate(rootNode, command.position, displayMetrics, false);
        }

        return candidate != null && clickCandidate(candidate);
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

    private boolean clickNode(AccessibilityNodeInfo node) {
        if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            return true;
        }
        return gestureController.tapNodeCenter(node);
    }

    private Comparator<ClickCandidate> bestMatchComparator() {
        return Comparator
                .comparingInt((ClickCandidate candidate) -> candidate.score)
                .reversed()
                .thenComparingInt(candidate -> candidate.bounds.top)
                .thenComparingInt(candidate -> candidate.bounds.left);
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
