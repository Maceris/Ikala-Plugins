package com.ikalagaming.factory.kvt;

/**
 * A tree of key-value pairs. All nodes extend this, as all nodes are
 * technically trees.
 * 
 * @author Ches Burks
 *
 */
public abstract class NodeTree {
	/**
	 * Returns the type of the node. Each node has a type corresponding to the
	 * values it stores. The root of the tree (or any node with multiple named
	 * values) is always a {@link NodeType#NODE}.
	 * 
	 * @return The node type for this node.
	 */
	public abstract NodeType getType();
}
