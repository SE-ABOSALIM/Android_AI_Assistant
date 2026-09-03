package com.example.anroidaiassistant.accessibility.click;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Function;

final class CanonicalTargetDeduplicator {
    static final int ACTION_ACTIVATE = 1;
    static final int ACTION_EDIT = 1 << 1;

    private CanonicalTargetDeduplicator() {
    }

    static <C, N> void addOrReplace(
            List<C> candidates,
            C candidate,
            Function<C, Identity<N>> identityProvider,
            BiPredicate<C, C> isBetterReplacement
    ) {
        Identity<N> candidateIdentity = identityProvider.apply(candidate);
        for (int index = 0; index < candidates.size(); index++) {
            C existing = candidates.get(index);
            Identity<N> existingIdentity = identityProvider.apply(existing);
            if (!existingIdentity.representsSameTarget(candidateIdentity)) {
                continue;
            }
            if (isBetterReplacement.test(candidate, existing)) {
                candidates.set(index, candidate);
            }
            return;
        }
        candidates.add(candidate);
    }

    static final class Identity<N> {
        private final N nodeKey;
        private final List<N> ancestorKeys;
        private final List<String> semanticValues;
        private final int actionKinds;
        private final int left;
        private final int top;
        private final int right;
        private final int bottom;

        Identity(
                N nodeKey,
                List<N> ancestorKeys,
                List<String> semanticValues,
                int actionKinds,
                int left,
                int top,
                int right,
                int bottom
        ) {
            this.nodeKey = nodeKey;
            this.ancestorKeys = ancestorKeys == null
                    ? new ArrayList<>()
                    : new ArrayList<>(ancestorKeys);
            this.semanticValues = semanticValues == null
                    ? new ArrayList<>()
                    : new ArrayList<>(semanticValues);
            this.actionKinds = actionKinds;
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }

        boolean representsSameTarget(Identity<N> other) {
            if (other == null) {
                return false;
            }
            if (sameNode(nodeKey, other.nodeKey)) {
                return true;
            }
            if (!areNested(other)
                    || !hasCompatibleAction(other)
                    || !sharesSemanticValue(other)) {
                return false;
            }
            return overlaps(other);
        }

        private boolean areNested(Identity<N> other) {
            return containsNode(ancestorKeys, other.nodeKey)
                    || containsNode(other.ancestorKeys, nodeKey);
        }

        private boolean hasCompatibleAction(Identity<N> other) {
            return (actionKinds & other.actionKinds) != 0;
        }

        private boolean sharesSemanticValue(Identity<N> other) {
            for (String first : semanticValues) {
                if (first == null || first.isEmpty()) {
                    continue;
                }
                for (String second : other.semanticValues) {
                    if (first.equals(second)) {
                        return true;
                    }
                }
            }
            return false;
        }

        private boolean overlaps(Identity<N> other) {
            return Math.max(left, other.left) < Math.min(right, other.right)
                    && Math.max(top, other.top) < Math.min(bottom, other.bottom);
        }

        private boolean containsNode(List<N> nodes, N target) {
            if (target == null) {
                return false;
            }
            for (N node : nodes) {
                if (sameNode(node, target)) {
                    return true;
                }
            }
            return false;
        }

        private boolean sameNode(N first, N second) {
            return first != null && (first == second || Objects.equals(first, second));
        }
    }
}
