package com.ikalagaming.factory.kvt;

import lombok.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * A tree of key-value pairs. All nodes extend this, as all nodes are
 * technically trees.
 *
 * @author Ches Burks
 *
 */
public interface NodeTree {

	/**
	 * Add a child node.
	 *
	 * @param name The name of the child node.
	 */
	void add(final @NonNull String name);

	/**
	 * Add a child to the node. Only works on non-leaf nodes. A node that is
	 * just a literal value will throw an exception.
	 *
	 * @param name The key name.
	 * @param type The type of the node. Always boolean.
	 * @param value The value we are storing. Must be a boolean. Must match the
	 *            specified type.
	 */
	void add(final String name, final @NonNull NodeType type,
		final @NonNull Boolean value);

	/**
	 * Add a child to the node. Only works on non-leaf nodes. A node that is
	 * just a literal value will throw an exception.
	 *
	 * @param <T> The type we are storing. Must be a list of valid types.
	 * @param name The key name.
	 * @param type The type of the node.
	 * @param value The value we are storing. Must match the specified type.
	 */
	<T> void add(final String name, final @NonNull NodeType type,
		final @NonNull List<T> value);

	/**
	 * Add a child to the node. Only works on non-leaf nodes. A node that is
	 * just a literal value will throw an exception.
	 *
	 * @param name The key name.
	 * @param type The type of the node. Always Node.
	 * @param value The value we are storing. Must be a Node. Must match the
	 *            specified type.
	 */
	void add(final String name, final @NonNull NodeType type,
		final @NonNull Node value);

	/**
	 * Add a child to the node. Only works on non-leaf nodes. A node that is
	 * just a literal value will throw an exception.
	 *
	 * @param name The key name.
	 * @param type The type of the node. Always String.
	 * @param value The value we are storing. Must be a String. Must match the
	 *            specified type.
	 */
	void add(final String name, final @NonNull NodeType type,
		final @NonNull String value);

	/**
	 * Add a child to the node. Only works on non-leaf nodes. A node that is
	 * just a literal value will throw an exception.
	 *
	 * @param <T> The type we are storing. Must be a number.
	 * @param name The key name.
	 * @param type The type of the node.
	 * @param value The value we are storing. Must be a number. Must match the
	 *            specified type.
	 */
	<T extends Number> void add(final String name, final @NonNull NodeType type,
		final @NonNull T value);

	/**
	 * Add a child to the node. Only works on non-leaf nodes. A node that is
	 * just a literal value will throw an exception.
	 *
	 * @param <T> The type we are storing. Must be a primitive wrapper or
	 *            string.
	 * @param name The key name.
	 * @param value The value to store. Must be a primitive wrapper or string.
	 * @throws UnsupportedOperationException If trying to call on a leaf node.
	 */
	<T> void add(final @NonNull String name, final @NonNull T value);

	/**
	 * Add a boolean child.
	 *
	 * @param name The name of the child.
	 * @param value The child value.
	 */
	default void addBoolean(final String name, final boolean value) {
		this.add(name, NodeType.BOOLEAN, value);
	}

	/**
	 * Add a list of booleans.
	 *
	 * @param name The name of the child.
	 * @param value The child value.
	 */
	default void addBooleanArray(final @NonNull String name,
		final @NonNull List<Boolean> value) {
		this.add(name, NodeType.BOOLEAN_ARRAY, value);
	}

	/**
	 * Add a byte value.
	 *
	 * @param name The name of the child.
	 * @param value The child value.
	 */
	default void addByte(final @NonNull String name, final byte value) {
		this.add(name, NodeType.BYTE, value);
	}

	/**
	 * Add a list of bytes.
	 *
	 * @param name The name of the child.
	 * @param value The child value.
	 */
	default void addByteArray(final @NonNull String name,
		final @NonNull List<Byte> value) {
		this.add(name, NodeType.BYTE_ARRAY, value);
	}

	/**
	 * Add a double value.
	 *
	 * @param name The name of the child.
	 * @param value The child value.
	 */
	default void addDouble(final @NonNull String name, final double value) {
		this.add(name, NodeType.DOUBLE, value);
	}

	/**
	 * Add a list of doubles.
	 *
	 * @param name The name of the child.
	 * @param value The child value.
	 */
	default void addDoubleArray(final @NonNull String name,
		final @NonNull List<Double> value) {
		this.add(name, NodeType.DOUBLE_ARRAY, value);
	}

	/**
	 * Add a float value.
	 *
	 * @param name The name of the child.
	 * @param value The child value.
	 */
	default void addFloat(final @NonNull String name, final float value) {
		this.add(name, NodeType.FLOAT, value);
	}

	/**
	 * Add a list of floats.
	 *
	 * @param name The name of the child.
	 * @param value The child value.
	 */
	default void addFloatArray(final @NonNull String name,
		final @NonNull List<Float> value) {
		this.add(name, NodeType.FLOAT_ARRAY, value);
	}

	/**
	 * Add an integer value.
	 *
	 * @param name The name of the child.
	 * @param value The child value.
	 */
	default void addInteger(final String name, final int value) {
		this.add(name, NodeType.INTEGER, value);
	}

	/**
	 * Add a list of integers.
	 *
	 * @param name The name of the child.
	 * @param value The child value.
	 */
	default void addIntegerArray(final @NonNull String name,
		final @NonNull List<Integer> value) {
		this.add(name, NodeType.INTEGER_ARRAY, value);
	}

	/**
	 * Add a long .
	 *
	 * @param name The name of the child.
	 * @param value The child value.
	 */
	default void addLong(final @NonNull String name, final long value) {
		this.add(name, NodeType.LONG, value);
	}

	/**
	 * Add a list of longs.
	 *
	 * @param name The name of the child.
	 * @param value The child value.
	 */
	default void addLongArray(final @NonNull String name,
		final @NonNull List<Long> value) {
		this.add(name, NodeType.LONG_ARRAY, value);
	}

	/**
	 * Add a node.
	 *
	 * @param name The name of the child.
	 */
	default void addNode(final @NonNull String name) {
		this.add(name, NodeType.NODE, new Node());
	}

	/**
	 * Add a list of nodes.
	 *
	 * @param name The name of the child.
	 */
	default void addNodeArray(final @NonNull String name) {
		this.add(name, NodeType.NODE_ARRAY, new ArrayList<Node>());
	}

	/**
	 * Add a short value.
	 *
	 * @param name The name of the child.
	 * @param value The child value.
	 */
	default void addShort(final @NonNull String name, final short value) {
		this.add(name, NodeType.SHORT, value);
	}

	/**
	 * Add a list of shorts.
	 *
	 * @param name The name of the child.
	 * @param value The child value.
	 */

	default void addShortArray(final @NonNull String name,
		final @NonNull List<Short> value) {
		this.add(name, NodeType.SHORT_ARRAY, value);
	}

	/**
	 * Add a String.
	 *
	 * @param name The name of the child.
	 * @param value The child value.
	 */
	default void addString(final @NonNull String name,
		final @NonNull String value) {
		this.add(name, NodeType.STRING, value);
	}

	/**
	 * Add a list of Strings.
	 *
	 * @param name The name of the child.
	 * @param value The child value.
	 */
	default void addStringArray(final @NonNull String name,
		final @NonNull List<String> value) {
		this.add(name, NodeType.STRING_ARRAY, value);
	}

	/**
	 * Fetch the value of the given child.
	 *
	 * @param <T> The type we want to fetch.
	 * @param name The name of the child node.
	 * @return The primitive wrapper, String, node, or a List containing those.
	 *         Null if no child of that name exists.
	 */
	<T> T get(final @NonNull String name);

	/**
	 * Returns the type of the node. Each node has a type corresponding to the
	 * values it stores. The root of the tree (or any node with multiple named
	 * values) is always a {@link NodeType#NODE}.
	 *
	 * @return The node type for this node.
	 */
	NodeType getType();

	/**
	 * Checks if there is a child with the given name.
	 *
	 * @param name The name of the child.
	 * @return Whether a child with that name exists on this node.
	 */
	boolean hasChild(final @NonNull String name);

}
