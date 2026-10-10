package com.ikalagaming.graphics.ui.graph;

import lombok.NonNull;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Which links light up while the mouse is over a node. */
public enum Highlight {
    /** None. */
    NONE,
    /** The node's own links, in and out. */
    EDGES,
    /** The links leading to the node, back to where they start, like a skill's prerequisites. */
    ANCESTORS,
    /** The links leading on from the node, to everything it unlocks. */
    DESCENDANTS;

    /**
     * A link between two nodes, by ID, for working out highlights.
     *
     * @param from The node the link leaves.
     * @param to The node the link enters.
     */
    public record Link(@NonNull String from, @NonNull String to) {}

    /**
     * Find the links that light up for a node.
     *
     * @param links Every link.
     * @param node The node the mouse is over, or null.
     * @return The indices of the links that light up.
     */
    public Set<Integer> related(@NonNull List<Link> links, String node) {
        Set<Integer> result = new HashSet<>();
        if (node == null || this == NONE) {
            return result;
        }
        if (this == EDGES) {
            for (int i = 0; i < links.size(); ++i) {
                Link link = links.get(i);
                if (link.from().equals(node) || link.to().equals(node)) {
                    result.add(i);
                }
            }
            return result;
        }
        boolean backward = this == ANCESTORS;
        // Follow the links one node at a time, visiting each node once so cycles end
        Set<String> visited = new HashSet<>();
        Deque<String> pending = new ArrayDeque<>();
        pending.add(node);
        visited.add(node);
        while (!pending.isEmpty()) {
            String current = pending.poll();
            for (int i = 0; i < links.size(); ++i) {
                Link link = links.get(i);
                String near = backward ? link.to() : link.from();
                String far = backward ? link.from() : link.to();
                if (near.equals(current)) {
                    result.add(i);
                    if (visited.add(far)) {
                        pending.add(far);
                    }
                }
            }
        }
        return result;
    }

    /**
     * Read a highlight from a spec.
     *
     * @param text The text, like {@code ancestors}.
     * @return The highlight.
     * @throws IllegalArgumentException If it isn't one.
     */
    public static Highlight parse(@NonNull String text) {
        try {
            return valueOf(text.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "not a highlight, use none, edges, ancestors or descendants: " + text, e);
        }
    }
}
