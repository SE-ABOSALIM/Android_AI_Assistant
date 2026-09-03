package com.example.anroidaiassistant.accessibility.click;

import android.graphics.Rect;
import android.os.Build;
import android.view.accessibility.AccessibilityNodeInfo;

import com.example.anroidaiassistant.accessibility.semantic.AccessibilityNodeAdapter;
import com.example.anroidaiassistant.accessibility.semantic.SemanticNodeResolver;
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
    private final AccessibilityNodeAdapter nodeAdapter = new AccessibilityNodeAdapter();
    private final SemanticNodeResolver<AccessibilityNodeInfo> semanticResolver =
            new SemanticNodeResolver<>(nodeAdapter);

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
        SemanticNodeResolver.Resolution<AccessibilityNodeInfo> resolution =
                semanticResolver.resolveActionNode(node);
        if (resolution == null) {
            return null;
        }
        AccessibilityNodeInfo clickNode = resolution.getActionNode();

        Rect clickBounds = new Rect();
        clickNode.getBoundsInScreen(clickBounds);
        if (clickBounds.isEmpty()) {
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
        if (isMenuActionMatchForNonMenuTarget(bestFieldMatch.matchedText, targetVariants)) {
            return null;
        }

        Rect nodeBounds = new Rect();
        node.getBoundsInScreen(nodeBounds);
        Rect bounds = resolution.usesActionBounds() || bestFieldMatch.fromClickableParent
                ? new Rect(clickBounds)
                : chooseTapBounds(nodeBounds, clickBounds);
        if (bounds.isEmpty() || !positionFilter.matches(bounds, position, screenWidth, screenHeight)) {
            return null;
        }

        int score = bestFieldMatch.score + positionFilter.score(bounds, position, screenWidth, screenHeight);
        if (clickNode == node) {
            score += 2;
        }
        if (node.isFocused()) {
            score += 1;
        }

        boolean preferBoundsTap = !resolution.usesActionBounds()
                && !bestFieldMatch.fromClickableParent
                && clickNode != node
                && !sameBounds(bounds, clickBounds);
        return new ClickCandidate(
                clickNode,
                bounds,
                clickBounds,
                displayLabel(fields, bounds),
                score,
                bestFieldMatch.matchClass,
                bestFieldMatch.reason,
                bestFieldMatch.source,
                bestFieldMatch.matchedTarget,
                bestFieldMatch.matchedText,
                primaryTargetVariant(targetVariants),
                preferBoundsTap
        );
    }

    private List<NodeField> collectNodeFields(AccessibilityNodeInfo node, AccessibilityNodeInfo clickNode) {
        List<NodeField> fields = new ArrayList<>();
        addDirectNodeFields(fields, node, 0, false);
        if (clickNode != null && clickNode != node) {
            addDirectNodeFields(fields, clickNode, CLICKABLE_PARENT_SOURCE_BONUS, true);
        }
        AccessibilityNodeInfo labeledBy = nodeAdapter.labeledBy(clickNode);
        if (labeledBy != null && labeledBy != node && labeledBy != clickNode) {
            addDirectNodeFields(fields, labeledBy, CLICKABLE_PARENT_SOURCE_BONUS, true);
        }
        return fields;
    }

    private void addDirectNodeFields(
            List<NodeField> fields,
            AccessibilityNodeInfo node,
            int extraBonus,
            boolean fromClickableParent
    ) {
        if (node == null) {
            return;
        }

        addField(fields, "text", node.getText(), TEXT_SOURCE_BONUS + extraBonus, fromClickableParent);
        addField(fields, "content_description", node.getContentDescription(), CONTENT_DESCRIPTION_SOURCE_BONUS + extraBonus, fromClickableParent);
        addField(fields, "resource_id", resourceIdSearchText(node.getViewIdResourceName()), RESOURCE_ID_SOURCE_BONUS + extraBonus, fromClickableParent);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            addField(fields, "hint", node.getHintText(), HINT_SOURCE_BONUS + extraBonus, fromClickableParent);
        }
    }

    private void addField(
            List<NodeField> fields,
            String source,
            CharSequence rawValue,
            int sourceBonus,
            boolean fromClickableParent
    ) {
        if (rawValue == null) {
            return;
        }

        String raw = rawValue.toString();
        String normalized = ClickTextUtils.normalize(raw);
        if (TextNormalizer.hasText(normalized)) {
            fields.add(new NodeField(source, raw, normalized, sourceBonus, fromClickableParent));
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
                    textMatch.matchClass,
                    field.source + ":" + textMatch.reason,
                    field.source,
                    textMatch.matchedTarget,
                    field.normalizedValue,
                    field.fromClickableParent
            );
            if (best == null || ClickCandidateRankingPolicy.compareBestFirst(
                    candidate.matchClass,
                    candidate.score,
                    best.matchClass,
                    best.score
            ) < 0) {
                best = candidate;
            }
        }
        return best;
    }

    static boolean isMenuActionMatchForNonMenuTarget(String matchedText, List<String> targetVariants) {
        if (!TextNormalizer.hasText(matchedText) || targetVariants == null || targetVariants.isEmpty()) {
            return false;
        }

        String normalizedMatchedText = ClickTextUtils.normalize(matchedText);
        boolean menuActionText = normalizedMatchedText.contains("more actions")
                || normalizedMatchedText.contains("more options")
                || normalizedMatchedText.contains("overflow menu")
                || normalizedMatchedText.contains("kebab menu");
        if (!menuActionText) {
            return false;
        }

        for (String targetVariant : targetVariants) {
            String normalizedTarget = ClickTextUtils.normalize(targetVariant);
            if (normalizedTarget.contains("more options")
                    || normalizedTarget.contains("more actions")
                    || normalizedTarget.contains("overflow")
                    || normalizedTarget.contains("kebab")
                    || normalizedTarget.contains("three dots")) {
                return false;
            }
        }
        return true;
    }

    private void addOrReplaceEquivalentCandidate(List<ClickCandidate> candidates, ClickCandidate candidate) {
        CanonicalTargetDeduplicator.addOrReplace(
                candidates,
                candidate,
                this::canonicalIdentity,
                this::isBetterEquivalentCandidate
        );
    }

    private boolean isBetterEquivalentCandidate(ClickCandidate candidate, ClickCandidate existing) {
        int ranking = ClickCandidateRankingPolicy.compareBestFirst(
                candidate.matchClass,
                candidate.score,
                existing.matchClass,
                existing.score
        );
        if (ranking != 0) {
            return ranking < 0;
        }
        if (candidate.preferBoundsTap != existing.preferBoundsTap) {
            return !candidate.preferBoundsTap;
        }
        return TextNormalizer.hasText(candidate.label) && !TextNormalizer.hasText(existing.label);
    }

    private CanonicalTargetDeduplicator.Identity<AccessibilityNodeInfo> canonicalIdentity(
            ClickCandidate candidate
    ) {
        List<AccessibilityNodeInfo> ancestors = new ArrayList<>();
        AccessibilityNodeInfo parent = candidate.clickNode == null ? null : candidate.clickNode.getParent();
        int depth = 0;
        while (parent != null && depth++ < 64) {
            ancestors.add(parent);
            parent = parent.getParent();
        }

        List<String> semanticValues = new ArrayList<>();
        if (candidate.clickNode != null) {
            for (String value : nodeAdapter.semanticValues(candidate.clickNode)) {
                addSemanticIdentityValue(semanticValues, value);
            }
        }
        addSemanticIdentityValue(semanticValues, candidate.matchedText);
        if (candidate.matchClass == ClickMatchClass.EXACT
                && TextNormalizer.hasText(candidate.matchFamily)) {
            addSemanticIdentityValue(
                    semanticValues,
                    "exact target family " + candidate.matchFamily
            );
        }

        return new CanonicalTargetDeduplicator.Identity<>(
                candidate.clickNode,
                ancestors,
                semanticValues,
                actionKinds(candidate.clickNode),
                candidate.actionBounds.left,
                candidate.actionBounds.top,
                candidate.actionBounds.right,
                candidate.actionBounds.bottom
        );
    }

    private void addSemanticIdentityValue(List<String> values, String rawValue) {
        String normalized = ClickTextUtils.normalize(rawValue);
        if (TextNormalizer.hasText(normalized) && !values.contains(normalized)) {
            values.add(normalized);
        }
    }

    private String primaryTargetVariant(List<String> targetVariants) {
        if (targetVariants == null || targetVariants.isEmpty()) {
            return "";
        }
        return ClickTextUtils.normalize(targetVariants.get(0));
    }

    private int actionKinds(AccessibilityNodeInfo node) {
        if (node == null) {
            return 0;
        }
        int actions = node.getActions();
        int kinds = 0;
        if (node.isClickable()
                || node.isCheckable()
                || node.isFocusable()
                || (actions & AccessibilityNodeInfo.ACTION_CLICK) != 0) {
            kinds |= CanonicalTargetDeduplicator.ACTION_ACTIVATE;
        }
        if (node.isEditable() || (actions & AccessibilityNodeInfo.ACTION_SET_TEXT) != 0) {
            kinds |= CanonicalTargetDeduplicator.ACTION_ACTIVATE;
            kinds |= CanonicalTargetDeduplicator.ACTION_EDIT;
        }
        return kinds;
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
        final boolean fromClickableParent;

        NodeField(
                String source,
                String rawValue,
                String normalizedValue,
                int sourceBonus,
                boolean fromClickableParent
        ) {
            this.source = source;
            this.rawValue = rawValue;
            this.normalizedValue = normalizedValue;
            this.sourceBonus = sourceBonus;
            this.fromClickableParent = fromClickableParent;
        }
    }

    private static final class FieldMatch {
        final int score;
        final ClickMatchClass matchClass;
        final String reason;
        final String source;
        final String matchedTarget;
        final String matchedText;
        final boolean fromClickableParent;

        FieldMatch(
                int score,
                ClickMatchClass matchClass,
                String reason,
                String source,
                String matchedTarget,
                String matchedText,
                boolean fromClickableParent
        ) {
            this.score = score;
            this.matchClass = matchClass;
            this.reason = reason;
            this.source = source;
            this.matchedTarget = matchedTarget;
            this.matchedText = matchedText;
            this.fromClickableParent = fromClickableParent;
        }
    }

}
