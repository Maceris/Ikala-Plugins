package com.ikalagaming.factory.kvt;

import lombok.Getter;
import lombok.NonNull;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * A node that is a list of other nodes. The keys must be unique and are kept
 * ordered by name.
 *
 * @author Ches Burks
 *
 */
@Getter
public class Node extends NodeTree {

	/**
	 * The key value pairs in the node.
	 */
	public final Map<String, NodeTree> values;

	/**
	 * Set up an empty node.
	 */
	public Node() {
		this.values = new TreeMap<>();
	}

	@Override
	public void add(final @NonNull String name) {
		this.values.put(name, new Node());
	}

	@Override
	public void add(String name, @NonNull NodeType type,
		@NonNull Boolean value) {
		this.values.put(name, new ValueNode<>(type, value));
	}

	@Override
	public <T> void add(String name, @NonNull NodeType type,
		@NonNull List<T> value) {
		this.values.put(name, new ValueNode<>(type, value));
	}

	@Override
	public void add(String name, @NonNull NodeType type,
		@NonNull NodeTree value) {
		this.values.put(name, new ValueNode<>(type, value));
	}

	@Override
	public void add(String name, @NonNull NodeType type,
		@NonNull String value) {
		this.values.put(name, new ValueNode<>(type, value));
	}

	@Override
	public <T extends Number> void add(String name, @NonNull NodeType type,
		@NonNull T value) {
		this.values.put(name, new ValueNode<>(type, value));
	}

	@Override
	public <U> void add(final @NonNull String name, @NonNull U childValue) {

		if (childValue instanceof Boolean cast) {
			var node = new ValueNode<>(NodeType.BOOLEAN, cast);
			this.values.put(name, node);
			return;
		}

		if (childValue instanceof Byte cast) {
			var node = new ValueNode<>(NodeType.BYTE, cast);
			this.values.put(name, node);
			return;
		}

		if (childValue instanceof Double cast) {
			var node = new ValueNode<>(NodeType.DOUBLE, cast);
			this.values.put(name, node);
			return;
		}

		if (childValue instanceof Float cast) {
			var node = new ValueNode<>(NodeType.FLOAT, cast);
			this.values.put(name, node);
			return;
		}

		if (childValue instanceof Integer cast) {
			var node = new ValueNode<>(NodeType.INTEGER, cast);
			this.values.put(name, node);
			return;
		}

		if (childValue instanceof Long cast) {
			var node = new ValueNode<>(NodeType.LONG, cast);
			this.values.put(name, node);
			return;
		}

		if (childValue instanceof Node cast) {
			var node = new ValueNode<>(NodeType.NODE, cast);
			this.values.put(name, node);
			return;
		}

		if (childValue instanceof Short cast) {
			var node = new ValueNode<>(NodeType.SHORT, cast);
			this.values.put(name, node);
			return;
		}

		if (childValue instanceof String cast) {
			var node = new ValueNode<>(NodeType.BOOLEAN, cast);
			this.values.put(name, node);
		}

	}

	@SuppressWarnings("unchecked")
	@Override
	public <U> U get(final @NonNull String name) {

		NodeTree node = this.values.get(name);

		if (node instanceof Node) {
			return (U) node;
		}

		if (node instanceof ValueNode<?> cast) {
			return (U) cast.getValue();
		}

		if (node instanceof ArrayNode<?> cast) {
			return (U) cast.getValues();
		}

		return null;
	}

	@Override
	public NodeType getType() {
		return NodeType.NODE;
	}

	@Override
	public boolean hasChild(@NonNull String name) {
		return this.values.containsKey(name);
	}
}
