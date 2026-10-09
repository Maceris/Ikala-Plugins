package com.ikalagaming.graphics.ui.spec;

import lombok.NonNull;

import java.util.List;
import java.util.Map;

/**
 * One node of a loaded spec, with templates already expanded.
 *
 * @param type The node type, looked up in the node type registry when the spec is opened.
 * @param id The node ID, unique among its siblings.
 * @param properties The node's other properties, as written.
 * @param children The child nodes, in order.
 * @param repeat Fills the node with one child per list item, or null.
 * @param path Where the node is in the spec, for messages.
 */
public record NodeSpec(
        @NonNull String type,
        @NonNull String id,
        @NonNull Map<String, Object> properties,
        @NonNull List<NodeSpec> children,
        RepeatSpec repeat,
        @NonNull String path) {

    /**
     * Repeats a node once per item of a list.
     *
     * @param list The name of the {@link ObservableList} binding.
     * @param alias The name each item is known by in the item node, like {@code file} in {@code
     *     {file.name}}.
     * @param item The node made for each item.
     * @param key Text that identifies an item, so its node is kept when the list changes, or null
     *     to use the position in the list.
     * @param path Where the repeat is in the spec, for messages.
     */
    public record RepeatSpec(
            @NonNull String list,
            @NonNull String alias,
            @NonNull NodeSpec item,
            String key,
            @NonNull String path) {}
}
