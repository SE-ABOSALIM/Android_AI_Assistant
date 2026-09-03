package com.example.anroidaiassistant.accessibility.semantic;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public final class SemanticNodeResolver<N> {
    public interface NodeAdapter<N> {
        N parent(N node);
        List<N> children(N node);
        N labelFor(N node);
        N labeledBy(N node);
        boolean isVisible(N node);
        boolean isEnabled(N node);
        boolean isClickable(N node);
        boolean supportsClick(N node);
        boolean isEditable(N node);
        boolean supportsSetText(N node);
        boolean isCheckable(N node);
        boolean isFocusable(N node);
        String className(N node);
        List<String> semanticValues(N node);
    }

    public static final class Resolution<N> {
        private final N actionNode;
        private final boolean useActionBounds;

        private Resolution(N actionNode, boolean useActionBounds) {
            this.actionNode = actionNode;
            this.useActionBounds = useActionBounds;
        }

        public N getActionNode() {
            return actionNode;
        }

        public boolean usesActionBounds() {
            return useActionBounds;
        }
    }

    private final NodeAdapter<N> adapter;

    public SemanticNodeResolver(NodeAdapter<N> adapter) {
        this.adapter = adapter;
    }

    public Resolution<N> resolveActionNode(N semanticNode) {
        if (semanticNode == null || !adapter.isVisible(semanticNode)) {
            return null;
        }

        N explicitlyLabeledControl = adapter.labelFor(semanticNode);
        if (isActionable(explicitlyLabeledControl)) {
            return new Resolution<>(explicitlyLabeledControl, true);
        }

        N current = semanticNode;
        while (current != null) {
            if (isActionable(current)) {
                return new Resolution<>(current, false);
            }
            current = adapter.parent(current);
        }

        N uniqueDescendant = uniqueActionableDescendant(semanticNode);
        if (uniqueDescendant != null) {
            return new Resolution<>(uniqueDescendant, true);
        }
        return null;
    }

    public boolean isEligibleInput(N node) {
        if (node == null || !adapter.isVisible(node) || !adapter.isEnabled(node)) {
            return false;
        }
        String role = normalizedRole(node);
        boolean inputRole = adapter.isEditable(node) || role.contains("edittext");
        return inputRole && adapter.supportsSetText(node);
    }

    public List<String> semanticValues(N root, N actionNode) {
        Set<String> values = new LinkedHashSet<>();
        addValues(values, actionNode);
        if (actionNode == null) {
            return new ArrayList<>();
        }

        addValues(values, adapter.labeledBy(actionNode));
        collectExplicitLabels(root, actionNode, values);

        N parent = adapter.parent(actionNode);
        while (parent != null) {
            N ownedAction = uniqueActionableDescendant(parent);
            if (!sameNode(ownedAction, actionNode)) {
                break;
            }
            addValues(values, parent);
            parent = adapter.parent(parent);
        }
        return new ArrayList<>(values);
    }

    private boolean isActionable(N node) {
        if (node == null || !adapter.isVisible(node) || !adapter.isEnabled(node)) {
            return false;
        }
        if (adapter.isClickable(node)
                || adapter.supportsClick(node)
                || adapter.isEditable(node)
                || adapter.supportsSetText(node)
                || adapter.isCheckable(node)) {
            return true;
        }
        return adapter.isFocusable(node) && hasInteractiveRole(normalizedRole(node));
    }

    private boolean hasInteractiveRole(String role) {
        return role.contains("button")
                || role.contains("checkbox")
                || role.contains("radiobutton")
                || role.contains("spinner")
                || role.contains("switch")
                || role.contains("edittext")
                || role.contains("seekbar")
                || role.contains("autocomplete")
                || role.contains("combobox")
                || role.contains("dropdown");
    }

    private String normalizedRole(N node) {
        String role = adapter.className(node);
        return role == null ? "" : role.toLowerCase(Locale.US);
    }

    private N uniqueActionableDescendant(N node) {
        List<N> matches = new ArrayList<>();
        collectActionableDescendants(node, matches, 2);
        return matches.size() == 1 ? matches.get(0) : null;
    }

    private void collectActionableDescendants(N node, List<N> matches, int limit) {
        if (node == null || matches.size() >= limit) {
            return;
        }
        for (N child : safeChildren(node)) {
            if (isActionable(child)) {
                addUnique(matches, child);
            } else {
                collectActionableDescendants(child, matches, limit);
            }
            if (matches.size() >= limit) {
                return;
            }
        }
    }

    private void collectExplicitLabels(N node, N actionNode, Set<String> values) {
        if (node == null) {
            return;
        }
        if (sameNode(adapter.labelFor(node), actionNode)) {
            addValues(values, node);
        }
        for (N child : safeChildren(node)) {
            collectExplicitLabels(child, actionNode, values);
        }
    }

    private List<N> safeChildren(N node) {
        List<N> children = adapter.children(node);
        return children == null ? new ArrayList<>() : children;
    }

    private void addValues(Set<String> values, N node) {
        if (node == null) {
            return;
        }
        List<String> nodeValues = adapter.semanticValues(node);
        if (nodeValues == null) {
            return;
        }
        for (String value : nodeValues) {
            if (value != null && !value.trim().isEmpty()) {
                values.add(value.trim());
            }
        }
    }

    private void addUnique(List<N> nodes, N candidate) {
        for (N existing : nodes) {
            if (sameNode(existing, candidate)) {
                return;
            }
        }
        nodes.add(candidate);
    }

    private boolean sameNode(N first, N second) {
        return first == second || Objects.equals(first, second);
    }
}
