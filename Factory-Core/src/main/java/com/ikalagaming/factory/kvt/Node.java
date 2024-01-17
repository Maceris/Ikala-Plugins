package com.ikalagaming.factory.kvt;

import java.util.Map;
import java.util.TreeMap;

/**
 * A node that is a list of other nodes. The keys must be unique and are kept
 * ordered by name.
 * 
 * @author Ches Burks
 *
 */
public class Node extends NodeTree {

	@Override
	public NodeType getType() {
		return NodeType.NODE;
	}

	/**
	 * Set up an empty node.
	 */
	public Node() {
		values = new TreeMap<>();
	}

	/**
	 * The key value pairs in the node.
	 */
	Map<String, NodeTree> values;
}
