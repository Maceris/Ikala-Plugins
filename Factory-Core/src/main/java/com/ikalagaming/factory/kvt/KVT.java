package com.ikalagaming.factory.kvt;

import lombok.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * A tree of key-value pairs. All nodes extend this, as all nodes are technically trees.
 *
 * @author Ches Burks
 */
public interface KVT {

    /**
     * Add a child node.
     *
     * <p>Periods can be used to specify nested children, for example "parent.child.value". Any
     * intermediate nodes that don't exist will be created, but if any intermediate node exists but
     * is not a {@link Node} this will fail and stop trying to add nodes.
     *
     * @param name The name of the child node.
     */
    void add(final @NonNull String name);

    /**
     * Add a child to the node. Only works on non-leaf nodes. A node that is just a literal value
     * will throw an exception.
     *
     * <p>Periods can be used to specify nested children, for example "parent.child.value". Any
     * intermediate nodes that don't exist will be created, but if any intermediate node exists but
     * is not a {@link Node} this will fail and stop trying to add nodes.
     *
     * @param name The key name.
     * @param type The type of the node. Always boolean.
     * @param value The value we are storing. Must be a boolean. Must match the specified type.
     */
    void add(final String name, final @NonNull NodeType type, final @NonNull Boolean value);

    /**
     * Add a child to the node. Only works on non-leaf nodes. A node that is just a literal value
     * will throw an exception.
     *
     * <p>Periods can be used to specify nested children, for example "parent.child.value". Any
     * intermediate nodes that don't exist will be created, but if any intermediate node exists but
     * is not a {@link Node} this will fail and stop trying to add nodes.
     *
     * @param <T> The type we are storing. Must be a list of valid types.
     * @param name The key name.
     * @param type The type of the node.
     * @param value The value we are storing. Must match the specified type.
     */
    <T> void add(final String name, final @NonNull NodeType type, final @NonNull List<T> value);

    /**
     * Add a child to the node. Only works on non-leaf nodes. A node that is just a literal value
     * will throw an exception.
     *
     * <p>Periods can be used to specify nested children, for example "parent.child.value". Any
     * intermediate nodes that don't exist will be created, but if any intermediate node exists but
     * is not a {@link Node} this will fail and stop trying to add nodes.
     *
     * @param name The key name.
     * @param type The type of the node. Always Node.
     * @param value The value we are storing. Must be a Node. Must match the specified type.
     */
    void add(final String name, final @NonNull NodeType type, final @NonNull Node value);

    /**
     * Add a child to the node. Only works on non-leaf nodes. A node that is just a literal value
     * will throw an exception.
     *
     * <p>Periods can be used to specify nested children, for example "parent.child.value". Any
     * intermediate nodes that don't exist will be created, but if any intermediate node exists but
     * is not a {@link Node} this will fail and stop trying to add nodes.
     *
     * @param name The key name.
     * @param type The type of the node. Always String.
     * @param value The value we are storing. Must be a String. Must match the specified type.
     */
    void add(final String name, final @NonNull NodeType type, final @NonNull String value);

    /**
     * Add a child to the node. Only works on non-leaf nodes. A node that is just a literal value
     * will throw an exception.
     *
     * <p>Periods can be used to specify nested children, for example "parent.child.value". Any
     * intermediate nodes that don't exist will be created, but if any intermediate node exists but
     * is not a {@link Node} this will fail and stop trying to add nodes.
     *
     * @param <T> The type we are storing. Must be a number.
     * @param name The key name.
     * @param type The type of the node.
     * @param value The value we are storing. Must be a number. Must match the specified type.
     */
    <T extends Number> void add(
            final String name, final @NonNull NodeType type, final @NonNull T value);

    /**
     * Add a child to the node. Only works on non-leaf nodes. A node that is just a literal value
     * will throw an exception.
     *
     * <p>Periods can be used to specify nested children, for example "parent.child.value". Any
     * intermediate nodes that don't exist will be created, but if any intermediate node exists but
     * is not a {@link Node} this will fail and stop trying to add nodes.
     *
     * @param <T> The type we are storing. Must be a primitive wrapper or string.
     * @param name The key name.
     * @param value The value to store. Must be a primitive wrapper or string.
     * @throws UnsupportedOperationException If trying to call on a leaf node.
     */
    <T> void add(final @NonNull String name, final @NonNull T value);

    /**
     * Add a boolean child.
     *
     * <p>Periods can be used to specify nested children, for example "parent.child.value". Any
     * intermediate nodes that don't exist will be created, but if any intermediate node exists but
     * is not a {@link Node} this will fail and stop trying to add nodes.
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
     * <p>Periods can be used to specify nested children, for example "parent.child.value". Any
     * intermediate nodes that don't exist will be created, but if any intermediate node exists but
     * is not a {@link Node} this will fail and stop trying to add nodes.
     *
     * @param name The name of the child.
     * @param value The child value.
     */
    default void addBooleanArray(final @NonNull String name, final @NonNull List<Boolean> value) {
        this.add(name, NodeType.BOOLEAN_ARRAY, value);
    }

    /**
     * Add a byte value.
     *
     * <p>Periods can be used to specify nested children, for example "parent.child.value". Any
     * intermediate nodes that don't exist will be created, but if any intermediate node exists but
     * is not a {@link Node} this will fail and stop trying to add nodes.
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
     * <p>Periods can be used to specify nested children, for example "parent.child.value". Any
     * intermediate nodes that don't exist will be created, but if any intermediate node exists but
     * is not a {@link Node} this will fail and stop trying to add nodes.
     *
     * @param name The name of the child.
     * @param value The child value.
     */
    default void addByteArray(final @NonNull String name, final @NonNull List<Byte> value) {
        this.add(name, NodeType.BYTE_ARRAY, value);
    }

    /**
     * Add a double value.
     *
     * <p>Periods can be used to specify nested children, for example "parent.child.value". Any
     * intermediate nodes that don't exist will be created, but if any intermediate node exists but
     * is not a {@link Node} this will fail and stop trying to add nodes.
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
     * <p>Periods can be used to specify nested children, for example "parent.child.value". Any
     * intermediate nodes that don't exist will be created, but if any intermediate node exists but
     * is not a {@link Node} this will fail and stop trying to add nodes.
     *
     * @param name The name of the child.
     * @param value The child value.
     */
    default void addDoubleArray(final @NonNull String name, final @NonNull List<Double> value) {
        this.add(name, NodeType.DOUBLE_ARRAY, value);
    }

    /**
     * Add a float value.
     *
     * <p>Periods can be used to specify nested children, for example "parent.child.value". Any
     * intermediate nodes that don't exist will be created, but if any intermediate node exists but
     * is not a {@link Node} this will fail and stop trying to add nodes.
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
     * <p>Periods can be used to specify nested children, for example "parent.child.value". Any
     * intermediate nodes that don't exist will be created, but if any intermediate node exists but
     * is not a {@link Node} this will fail and stop trying to add nodes.
     *
     * @param name The name of the child.
     * @param value The child value.
     */
    default void addFloatArray(final @NonNull String name, final @NonNull List<Float> value) {
        this.add(name, NodeType.FLOAT_ARRAY, value);
    }

    /**
     * Add an integer value.
     *
     * <p>Periods can be used to specify nested children, for example "parent.child.value". Any
     * intermediate nodes that don't exist will be created, but if any intermediate node exists but
     * is not a {@link Node} this will fail and stop trying to add nodes.
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
     * <p>Periods can be used to specify nested children, for example "parent.child.value". Any
     * intermediate nodes that don't exist will be created, but if any intermediate node exists but
     * is not a {@link Node} this will fail and stop trying to add nodes.
     *
     * @param name The name of the child.
     * @param value The child value.
     */
    default void addIntegerArray(final @NonNull String name, final @NonNull List<Integer> value) {
        this.add(name, NodeType.INTEGER_ARRAY, value);
    }

    /**
     * Add a long.
     *
     * <p>Periods can be used to specify nested children, for example "parent.child.value". Any
     * intermediate nodes that don't exist will be created, but if any intermediate node exists but
     * is not a {@link Node} this will fail and stop trying to add nodes.
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
     * <p>Periods can be used to specify nested children, for example "parent.child.value". Any
     * intermediate nodes that don't exist will be created, but if any intermediate node exists but
     * is not a {@link Node} this will fail and stop trying to add nodes.
     *
     * @param name The name of the child.
     * @param value The child value.
     */
    default void addLongArray(final @NonNull String name, final @NonNull List<Long> value) {
        this.add(name, NodeType.LONG_ARRAY, value);
    }

    /**
     * Add a node.
     *
     * <p>Periods can be used to specify nested children, for example "parent.child.value". Any
     * intermediate nodes that don't exist will be created, but if any intermediate node exists but
     * is not a {@link Node} this will fail and stop trying to add nodes.
     *
     * @param name The name of the child.
     */
    default void addNode(final @NonNull String name) {
        this.add(name, NodeType.NODE, new Node());
    }

    /**
     * Add a node.
     *
     * <p>Periods can be used to specify nested children, for example "parent.child.value". Any
     * intermediate nodes that don't exist will be created, but if any intermediate node exists but
     * is not a {@link Node} this will fail and stop trying to add nodes.
     *
     * @param name The name of the child.
     * @param node The node to add.
     */
    default void addNode(final @NonNull String name, @NonNull Node node) {
        this.add(name, NodeType.NODE, node);
    }

    /**
     * Add a list of nodes.
     *
     * <p>Periods can be used to specify nested children, for example "parent.child.value". Any
     * intermediate nodes that don't exist will be created, but if any intermediate node exists but
     * is not a {@link Node} this will fail and stop trying to add nodes.
     *
     * @param name The name of the child.
     */
    default void addNodeArray(final @NonNull String name) {
        this.add(name, NodeType.NODE_ARRAY, new ArrayList<Node>());
    }

    /**
     * Add a list of nodes.
     *
     * <p>Periods can be used to specify nested children, for example "parent.child.value". Any
     * intermediate nodes that don't exist will be created, but if any intermediate node exists but
     * is not a {@link Node} this will fail and stop trying to add nodes.
     *
     * @param name The name of the child.
     * @param nodes The child value.
     */
    default void addNodeArray(final @NonNull String name, final @NonNull List<Node> nodes) {
        this.add(name, NodeType.NODE_ARRAY, nodes);
    }

    /**
     * Add a short value.
     *
     * <p>Periods can be used to specify nested children, for example "parent.child.value". Any
     * intermediate nodes that don't exist will be created, but if any intermediate node exists but
     * is not a {@link Node} this will fail and stop trying to add nodes.
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
     * <p>Periods can be used to specify nested children, for example "parent.child.value". Any
     * intermediate nodes that don't exist will be created, but if any intermediate node exists but
     * is not a {@link Node} this will fail and stop trying to add nodes.
     *
     * @param name The name of the child.
     * @param value The child value.
     */
    default void addShortArray(final @NonNull String name, final @NonNull List<Short> value) {
        this.add(name, NodeType.SHORT_ARRAY, value);
    }

    /**
     * Add a String.
     *
     * <p>Periods can be used to specify nested children, for example "parent.child.value". Any
     * intermediate nodes that don't exist will be created, but if any intermediate node exists but
     * is not a {@link Node} this will fail and stop trying to add nodes.
     *
     * @param name The name of the child.
     * @param value The child value.
     */
    default void addString(final @NonNull String name, final @NonNull String value) {
        this.add(name, NodeType.STRING, value);
    }

    /**
     * Add a list of Strings.
     *
     * <p>Periods can be used to specify nested children, for example "parent.child.value". Any
     * intermediate nodes that don't exist will be created, but if any intermediate node exists but
     * is not a {@link Node} this will fail and stop trying to add nodes.
     *
     * @param name The name of the child.
     * @param value The child value.
     */
    default void addStringArray(final @NonNull String name, final @NonNull List<String> value) {
        this.add(name, NodeType.STRING_ARRAY, value);
    }

    /**
     * Fetch the value of the given child. Periods can be used to access nested children, for
     * example "parent.child.value".
     *
     * @param <T> The type we want to fetch.
     * @param name The name of the child node.
     * @return The primitive wrapper, String, node, or a List containing those. Null if no child of
     *     that name exists.
     */
    <T> T get(final @NonNull String name);

    /**
     * Fetch a boolean value.
     *
     * @param name The name of the boolean.
     * @return The boolean value. If it does not exist, will be false.
     */
    default boolean getBoolean(final String name) {
        try {
            return this.get(name);
        } catch (NullPointerException | ClassCastException ignored) {
            return false;
        }
    }

    /**
     * Fetch an array of boolean values.
     *
     * @param name The name of the list.
     * @return The list of values. If it does not exist, may be null or an empty unmodifiable list.
     */
    default List<Boolean> getBooleanArray(final @NonNull String name) {
        try {
            return this.get(name);
        } catch (NullPointerException | ClassCastException ignored) {
            return Collections.emptyList();
        }
    }

    /**
     * Fetch a byte value.
     *
     * @param name The name of the byte.
     * @return The boolean value. If it does not exist, will be 0.
     */
    default byte getByte(final @NonNull String name) {
        try {
            return this.get(name);
        } catch (NullPointerException | ClassCastException ignored) {
            return 0;
        }
    }

    /**
     * Fetch an array of byte values.
     *
     * @param name The name of the list.
     * @return The list of values. If it does not exist, may be null or an empty unmodifiable list.
     */
    default List<Byte> getByteArray(final @NonNull String name) {
        try {
            return this.get(name);
        } catch (NullPointerException | ClassCastException ignored) {
            return Collections.emptyList();
        }
    }

    /**
     * Fetch a double value.
     *
     * @param name The name of the double.
     * @return The boolean value. If it does not exist, will be 0.
     */
    default double getDouble(final @NonNull String name) {
        try {
            return this.get(name);
        } catch (NullPointerException | ClassCastException ignored) {
            return 0;
        }
    }

    /**
     * Fetch an array of double values.
     *
     * @param name The name of the list.
     * @return The list of values. If it does not exist, may be null or an empty unmodifiable list.
     */
    default List<Double> getDoubleArray(final @NonNull String name) {
        try {
            return this.get(name);
        } catch (NullPointerException | ClassCastException ignored) {
            return Collections.emptyList();
        }
    }

    /**
     * Fetch a float value.
     *
     * @param name The name of the float.
     * @return The boolean value. If it does not exist, will be 0.
     */
    default float getFloat(final @NonNull String name) {
        try {
            return this.get(name);
        } catch (NullPointerException | ClassCastException ignored) {
            return 0;
        }
    }

    /**
     * Fetch an array of float values.
     *
     * @param name The name of the list.
     * @return The list of values. If it does not exist, may be null or an empty unmodifiable list.
     */
    default List<Float> getFloatArray(final @NonNull String name) {
        try {
            return this.get(name);
        } catch (NullPointerException | ClassCastException ignored) {
            return Collections.emptyList();
        }
    }

    /**
     * Fetch a integer value.
     *
     * @param name The name of the integer.
     * @return The boolean value. If it does not exist, will be 0.
     */
    default int getInteger(final String name) {
        try {
            return this.get(name);
        } catch (NullPointerException | ClassCastException ignored) {
            return 0;
        }
    }

    /**
     * Fetch an array of integer values.
     *
     * @param name The name of the list.
     * @return The list of values. If it does not exist, may be null or an empty unmodifiable list.
     */
    default List<Integer> getIntegerArray(final @NonNull String name) {
        try {
            return this.get(name);
        } catch (NullPointerException | ClassCastException ignored) {
            return Collections.emptyList();
        }
    }

    /**
     * Fetch a long value.
     *
     * @param name The name of the long.
     * @return The boolean value. If it does not exist, will be 0.
     */
    default long getLong(final @NonNull String name) {
        try {
            return this.get(name);
        } catch (NullPointerException | ClassCastException ignored) {
            return 0;
        }
    }

    /**
     * Fetch an array of long values.
     *
     * @param name The name of the list.
     * @return The list of values. If it does not exist, may be null or an empty unmodifiable list.
     */
    default List<Long> getLongArray(final @NonNull String name) {
        try {
            return this.get(name);
        } catch (NullPointerException | ClassCastException ignored) {
            return Collections.emptyList();
        }
    }

    /**
     * Fetch a node value.
     *
     * @param name The name of the node.
     * @return The boolean value. If it does not exist, will be null.
     */
    default Node getNode(final @NonNull String name) {
        try {
            return this.get(name);
        } catch (NullPointerException | ClassCastException ignored) {
            return null;
        }
    }

    /**
     * Fetch an array of nodes values.
     *
     * @param name The name of the list.
     * @return The list of values. If it does not exist, may be null or an empty unmodifiable list.
     */
    default List<Node> getNodeArray(final @NonNull String name) {
        try {
            return this.get(name);
        } catch (NullPointerException | ClassCastException ignored) {
            return Collections.emptyList();
        }
    }

    /**
     * Fetch a short value.
     *
     * @param name The name of the short.
     * @return The boolean value. If it does not exist, will be 0.
     */
    default short getShort(final @NonNull String name) {
        try {
            return this.get(name);
        } catch (NullPointerException | ClassCastException ignored) {
            return 0;
        }
    }

    /**
     * Fetch an array of short values.
     *
     * @param name The name of the list.
     * @return The list of values. If it does not exist, may be null or an empty unmodifiable list.
     */
    default List<Short> getShortArray(final @NonNull String name) {
        try {
            return this.get(name);
        } catch (NullPointerException | ClassCastException ignored) {
            return Collections.emptyList();
        }
    }

    /**
     * Fetch a String value.
     *
     * @param name The name of the String.
     * @return The boolean value. If it does not exist, will be null.
     */
    default String getString(final @NonNull String name) {
        try {
            return this.get(name);
        } catch (NullPointerException | ClassCastException ignored) {
            return null;
        }
    }

    /**
     * Fetch an array of string values.
     *
     * @param name The name of the list.
     * @return The list of values. If it does not exist, may be null or an empty unmodifiable list.
     */
    default List<String> getStringArray(final @NonNull String name) {
        try {
            return this.get(name);
        } catch (NullPointerException | ClassCastException ignored) {
            return Collections.emptyList();
        }
    }

    /**
     * Returns the type of the node. Each node has a type corresponding to the values it stores. The
     * root of the tree (or any node with multiple named values) is always a {@link NodeType#NODE}.
     *
     * @return The node type for this node.
     */
    NodeType getType();

    /**
     * Returns the type of the child node. Each node has a type corresponding to the values it
     * stores.
     *
     * @return The node type for the child node.
     */
    Optional<NodeType> getType(final @NonNull String name);

    /**
     * Fetch the list of child keys, but only one layer deep. Leaf nodes would return an empty list.
     *
     * @return The list of keys.
     */
    default List<String> getKeys() {
        return List.of();
    }

    /**
     * Checks if there is a child with the given name.
     *
     * @param name The name of the child.
     * @return Whether a child with that name exists on this node.
     */
    boolean hasChild(final @NonNull String name);
}
