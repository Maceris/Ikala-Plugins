package com.ikalagaming.graphics.ui.spec;

import com.ikalagaming.graphics.ui.Node;

import lombok.NonNull;

/**
 * Makes a node of one type from its spec properties. Graphics registers the built-in types; plugins
 * register their own with {@code UI.registerNodeType}.
 *
 * <p>Read the type's own properties through {@code properties}; the properties every node has
 * (sizes, padding, classes and so on) are applied afterwards. Properties the factory doesn't read
 * are reported as unknown.
 */
@FunctionalInterface
public interface NodeFactory {
    /**
     * Make a node.
     *
     * @param id The node ID.
     * @param properties The node's properties.
     * @return The node.
     * @throws SpecException If a property is wrong.
     */
    Node<?> create(@NonNull String id, @NonNull SpecProperties properties);
}
