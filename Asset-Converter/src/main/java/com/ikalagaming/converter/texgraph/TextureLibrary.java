package com.ikalagaming.converter.texgraph;

import com.ikalagaming.graphics.ui.graph.model.Graph;
import com.ikalagaming.graphics.ui.graph.model.NodeTypes;
import com.ikalagaming.graphics.ui.graph.model.PinTypes;

import lombok.Getter;
import lombok.NonNull;

import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * The node types texture graphs can use, each with what it does. The graph editor sees the types;
 * the evaluator runs the nodes.
 */
public final class TextureLibrary {
    /**
     * The node types, for graphs and the editor. -- GETTER -- The node types.
     *
     * @return The types.
     */
    @Getter private final NodeTypes nodeTypes = new NodeTypes();

    /**
     * The pin types. -- GETTER -- The pin types.
     *
     * @return The types.
     */
    @Getter private final PinTypes pinTypes = TexturePins.types();

    /** What each node type does, by type ID. */
    private final Map<String, TextureNode> nodes = new HashMap<>();

    /**
     * Add a node type.
     *
     * @param type The type, for graphs and the editor.
     * @param node What it does.
     * @return This library.
     */
    public TextureLibrary register(@NonNull NodeTypes.NodeType type, @NonNull TextureNode node) {
        nodeTypes.register(type);
        nodes.put(type.id(), node);
        return this;
    }

    /**
     * Take a node type out, as when the subgraph it placed is gone from the search.
     *
     * @param type The type's ID.
     */
    public void unregister(@NonNull String type) {
        nodeTypes.remove(type);
        nodes.remove(type);
    }

    /**
     * What a node type does.
     *
     * @param type The type's ID.
     * @return What it does, or null for a type with no evaluation, like the editor's reroutes.
     */
    public @Nullable TextureNode get(@NonNull String type) {
        return nodes.get(type);
    }

    /**
     * An empty graph of this library's types.
     *
     * @return The graph.
     */
    public Graph newGraph() {
        return new Graph(nodeTypes, pinTypes);
    }
}
