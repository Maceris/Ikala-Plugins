package com.ikalagaming.converter;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * A node in the imported hierarchy, similar to an AINode from assimp.
 *
 * <p>Each node has name, a parent node (except for the root node), a transformation relative to its
 * parent and possibly several child nodes. Simple file formats don't support hierarchical
 * structures - for these formats the imported scene does consist of only a single root node without
 * children.
 */
@Getter
@RequiredArgsConstructor
class Node {
    /**
     * The list of children of this node.
     *
     * @return The child list, which might be empty but shouldn't be null.
     */
    private final List<Node> children = new ArrayList<>();

    /**
     * The name of the node. The name might be empty (length of zero) but all nodes which need to be
     * referenced by either bones or animations are named. Multiple nodes may have the same name,
     * except for nodes which are referenced by bones. Their names must be unique.
     *
     * <p>Cameras and lights reference a specific node by name - if there are multiple nodes with
     * this name, they are assigned to each of them.
     *
     * <p>There are no limitations with regard to the characters contained in the name string as it
     * is usually taken directly from the source file.
     *
     * <p>Implementations should be able to handle tokens such as whitespace, tabs, line feeds,
     * quotation marks, ampersands etc.
     *
     * <p>Sometimes assimp introduces new nodes not present in the source file into the hierarchy
     * (usually out of necessity because sometimes the source hierarchy format is simply not
     * compatible). Their names are surrounded by <> e.g. {@code <DummyRootNode>}.
     *
     * @return The nodes name.
     */
    private final String name;

    /**
     * The parent node, null if this node is the root node.
     *
     * @return The parent node, or null if this is the root.
     */
    private final Node parent;

    /**
     * The transformation relative to the node's parent.
     *
     * @return The transformation matrix for this node.
     */
    private final Matrix4f nodeTransformation;

    /**
     * Add a child node to this one.
     *
     * @param node The node to add as a child.
     */
    public void addChild(@NonNull Node node) {
        children.add(node);
    }
}
