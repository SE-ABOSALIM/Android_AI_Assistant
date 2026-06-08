package com.example.anroidaiassistant.accessibility.click;

import android.graphics.Rect;
import android.view.accessibility.AccessibilityNodeInfo;

import com.example.anroidaiassistant.util.TextNormalizer;

import java.util.ArrayList;
import java.util.List;

public final class ClickCandidateCollector {
    private final ClickTextMatcher textMatcher;
    private final ClickPositionFilter positionFilter;

    public ClickCandidateCollector(ClickTextMatcher textMatcher, ClickPositionFilter positionFilter) {
        this.textMatcher = textMatcher;
        this.positionFilter = positionFilter;
    }

    public List<ClickCandidate> collectTextCandidates(
            AccessibilityNodeInfo rootNode,
            List<String> targetVariants,
            String position,
            int screenWidth,
            int screenHeight
    ) {
        List<ClickCandidate> candidates = new ArrayList<>();
        collectTextCandidates(rootNode, targetVariants, position, screenWidth, screenHeight, candidates);
        return candidates;
    }

    private void collectTextCandidates(
            AccessibilityNodeInfo node,
            List<String> targetVariants,
            String position,
            int screenWidth,
            int screenHeight,
            List<ClickCandidate> candidates
    ) {
        if (node == null || screenWidth <= 0 || screenHeight <= 0) {
            return;
        }

        ClickCandidate candidate = scoreNode(node, targetVariants, position, screenWidth, screenHeight);
        if (candidate != null && !containsEquivalentCandidate(candidates, candidate)) {
            candidates.add(candidate);
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            collectTextCandidates(
                    node.getChild(i),
                    targetVariants,
                    position,
                    screenWidth,
                    screenHeight,
                    candidates
            );
        }
    }

    private ClickCandidate scoreNode(
            AccessibilityNodeInfo node,
            List<String> targetVariants,
            String position,
            int screenWidth,
            int screenHeight
    ) {
        AccessibilityNodeInfo clickNode = findClickableNode(node);
        if (clickNode == null) {
            return null;
        }

        Rect clickBounds = new Rect();
        clickNode.getBoundsInScreen(clickBounds);
        if (clickBounds.isEmpty()) {
            return null;
        }

        Rect nodeBounds = new Rect();
        node.getBoundsInScreen(nodeBounds);
        Rect bounds = chooseTapBounds(nodeBounds, clickBounds);
        if (bounds.isEmpty() || !positionFilter.matches(bounds, position, screenWidth, screenHeight)) {
            return null;
        }

        String rawNodeText = ClickTextUtils.joinNodeText(node);
        String nodeText = ClickTextUtils.normalize(rawNodeText);
        if (!TextNormalizer.hasText(nodeText)) {
            return null;
        }

        ClickTextMatch textMatch = textMatcher.score(nodeText, targetVariants);
        if (textMatch.score <= 0) {
            return null;
        }

        int score = textMatch.score + positionFilter.score(bounds, position, screenWidth, screenHeight);
        if (clickNode == node) {
            score += 2;
        }
        if (node.isFocused()) {
            score += 1;
        }

        boolean preferBoundsTap = clickNode != node && !sameBounds(bounds, clickBounds);
        return new ClickCandidate(
                clickNode,
                bounds,
                displayLabel(rawNodeText, bounds),
                score,
                textMatch.reason,
                preferBoundsTap
        );
    }

    private boolean containsEquivalentCandidate(List<ClickCandidate> candidates, ClickCandidate candidate) {
        for (ClickCandidate existing : candidates) {
            if (sameBounds(existing.bounds, candidate.bounds)) {
                return true;
            }
        }
        return false;
    }

    private boolean sameBounds(Rect first, Rect second) {
        return first.left == second.left
                && first.top == second.top
                && first.right == second.right
                && first.bottom == second.bottom;
    }

    private Rect chooseTapBounds(Rect nodeBounds, Rect clickBounds) {
        if (nodeBounds == null
                || nodeBounds.isEmpty()
                || clickBounds == null
                || clickBounds.isEmpty()
                || !clickBounds.contains(nodeBounds)
                || sameBounds(nodeBounds, clickBounds)) {
            return new Rect(clickBounds);
        }

        return new Rect(nodeBounds);
    }

    private AccessibilityNodeInfo findClickableNode(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo current = node;
        while (current != null) {
            if (current.isClickable()
                    || (current.getActions() & AccessibilityNodeInfo.ACTION_CLICK) != 0) {
                return current;
            }
            current = current.getParent();
        }
        return null;
    }

    private String displayLabel(String rawText, Rect bounds) {
        String label = TextNormalizer.normalizeText(rawText);
        if (TextNormalizer.hasText(label)) {
            return label;
        }
        return "Item at " + bounds.centerX() + ", " + bounds.centerY();
    }

}
