package com.example.anroidaiassistant.accessibility.click;

import android.graphics.Rect;
import android.os.Build;
import android.view.accessibility.AccessibilityNodeInfo;

import com.example.anroidaiassistant.util.TextNormalizer;

import java.util.ArrayList;
import java.util.List;

public final class ClickCandidateCollector {
    private static final int TEXT_SOURCE_BONUS = 18;
    private static final int CONTENT_DESCRIPTION_SOURCE_BONUS = 20;
    private static final int RESOURCE_ID_SOURCE_BONUS = 18;
    private static final int HINT_SOURCE_BONUS = 14;
    private static final int CLICKABLE_PARENT_SOURCE_BONUS = 8;

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
        if (candidate != null) {
            addOrReplaceEquivalentCandidate(candidates, candidate);
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

        List<NodeField> fields = collectNodeFields(node, clickNode);
        if (fields.isEmpty()) {
            return null;
        }

        FieldMatch bestFieldMatch = bestFieldMatch(fields, targetVariants);
        if (bestFieldMatch == null || bestFieldMatch.score <= 0) {
            return null;
        }

        int score = bestFieldMatch.score + positionFilter.score(bounds, position, screenWidth, screenHeight);
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
                clickBounds,
                displayLabel(fields, bounds),
                score,
                bestFieldMatch.reason,
                bestFieldMatch.source,
                bestFieldMatch.matchedTarget,
                bestFieldMatch.matchedText,
                preferBoundsTap
        );
    }

    private List<NodeField> collectNodeFields(AccessibilityNodeInfo node, AccessibilityNodeInfo clickNode) {
        List<NodeField> fields = new ArrayList<>();
        addDirectNodeFields(fields, node, 0);
        if (clickNode != null && clickNode != node) {
            addDirectNodeFields(fields, clickNode, CLICKABLE_PARENT_SOURCE_BONUS);
        }
        return fields;
    }

    private void addDirectNodeFields(List<NodeField> fields, AccessibilityNodeInfo node, int extraBonus) {
        if (node == null) {
            return;
        }

        addField(fields, "text", node.getText(), TEXT_SOURCE_BONUS + extraBonus);
        addField(fields, "content_description", node.getContentDescription(), CONTENT_DESCRIPTION_SOURCE_BONUS + extraBonus);
        addField(fields, "resource_id", resourceIdSearchText(node.getViewIdResourceName()), RESOURCE_ID_SOURCE_BONUS + extraBonus);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            addField(fields, "hint", node.getHintText(), HINT_SOURCE_BONUS + extraBonus);
        }
    }

    private void addField(List<NodeField> fields, String source, CharSequence rawValue, int sourceBonus) {
        if (rawValue == null) {
            return;
        }

        String raw = rawValue.toString();
        String normalized = ClickTextUtils.normalize(raw);
        if (TextNormalizer.hasText(normalized)) {
            fields.add(new NodeField(source, raw, normalized, sourceBonus));
        }
    }

    private String resourceIdSearchText(String resourceId) {
        if (!TextNormalizer.hasText(resourceId)) {
            return "";
        }

        String full = splitIdentifier(resourceId);
        int slashIndex = resourceId.lastIndexOf('/');
        String shortId = slashIndex >= 0 ? resourceId.substring(slashIndex + 1) : resourceId;
        String shortSearchText = splitIdentifier(shortId);
        if (full.equals(shortSearchText)) {
            return shortSearchText;
        }
        return shortSearchText + " " + full;
    }

    private String splitIdentifier(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replaceAll("([a-z])([A-Z])", "$1 $2")
                .replace('_', ' ')
                .replace('-', ' ')
                .replace('.', ' ')
                .replace(':', ' ')
                .replace('/', ' ');
    }

    private FieldMatch bestFieldMatch(List<NodeField> fields, List<String> targetVariants) {
        FieldMatch best = null;
        for (NodeField field : fields) {
            ClickTextMatch textMatch = textMatcher.score(field.normalizedValue, targetVariants);
            if (textMatch.score <= 0) {
                continue;
            }

            int score = textMatch.score + field.sourceBonus;
            FieldMatch candidate = new FieldMatch(
                    score,
                    field.source + ":" + textMatch.reason,
                    field.source,
                    textMatch.matchedTarget,
                    field.normalizedValue
            );
            if (best == null || candidate.score > best.score) {
                best = candidate;
            }
        }
        return best;
    }

    private void addOrReplaceEquivalentCandidate(List<ClickCandidate> candidates, ClickCandidate candidate) {
        for (int i = 0; i < candidates.size(); i++) {
            ClickCandidate existing = candidates.get(i);
            if (sameBounds(existing.actionBounds, candidate.actionBounds)) {
                if (isBetterEquivalentCandidate(candidate, existing)) {
                    candidates.set(i, candidate);
                }
                return;
            }
        }
        candidates.add(candidate);
    }

    private boolean isBetterEquivalentCandidate(ClickCandidate candidate, ClickCandidate existing) {
        if (candidate.score != existing.score) {
            return candidate.score > existing.score;
        }
        if (candidate.bounds.width() * candidate.bounds.height()
                != existing.bounds.width() * existing.bounds.height()) {
            return candidate.bounds.width() * candidate.bounds.height()
                    < existing.bounds.width() * existing.bounds.height();
        }
        return TextNormalizer.hasText(candidate.label) && !TextNormalizer.hasText(existing.label);
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

    private String displayLabel(List<NodeField> fields, Rect bounds) {
        for (NodeField field : fields) {
            if ("text".equals(field.source) || "content_description".equals(field.source)) {
                String label = TextNormalizer.normalizeText(field.rawValue);
                if (TextNormalizer.hasText(label)) {
                    return label;
                }
            }
        }
        for (NodeField field : fields) {
            if ("resource_id".equals(field.source)) {
                String label = TextNormalizer.normalizeText(field.rawValue);
                if (TextNormalizer.hasText(label)) {
                    return label;
                }
            }
        }
        return "Item at " + bounds.centerX() + ", " + bounds.centerY();
    }

    private static final class NodeField {
        final String source;
        final String rawValue;
        final String normalizedValue;
        final int sourceBonus;

        NodeField(String source, String rawValue, String normalizedValue, int sourceBonus) {
            this.source = source;
            this.rawValue = rawValue;
            this.normalizedValue = normalizedValue;
            this.sourceBonus = sourceBonus;
        }
    }

    private static final class FieldMatch {
        final int score;
        final String reason;
        final String source;
        final String matchedTarget;
        final String matchedText;

        FieldMatch(int score, String reason, String source, String matchedTarget, String matchedText) {
            this.score = score;
            this.reason = reason;
            this.source = source;
            this.matchedTarget = matchedTarget;
            this.matchedText = matchedText;
        }
    }

}
