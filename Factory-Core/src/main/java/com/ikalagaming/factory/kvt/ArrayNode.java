package com.ikalagaming.factory.kvt;

import com.ikalagaming.factory.FactoryPlugin;
import com.ikalagaming.util.SafeResourceLoader;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * A node that is an array of the same type of item.
 *
 * @author Ches Burks
 * @param <T> The type that this node stores.
 */
@Getter
@Slf4j
@EqualsAndHashCode
class ArrayNode<T> implements KVT {

    /**
     * Convert a value to the format it should use in the string format.
     *
     * @param <T> The type of the value.
     * @param type The type of the node.
     * @param value The value itself.
     * @return The String format for the given value.
     */
    private static <T> String toString(final @NonNull NodeType type, final @NonNull T value) {
        return switch (type) {
            case BOOLEAN_ARRAY,
                            BYTE_ARRAY,
                            DOUBLE_ARRAY,
                            FLOAT_ARRAY,
                            INTEGER_ARRAY,
                            LONG_ARRAY,
                            NODE_ARRAY,
                            SHORT_ARRAY ->
                    value.toString();
            case STRING_ARRAY -> String.format("\"%s\"", value.toString());
            default -> throw new UnsupportedOperationException();
        };
    }

    /** The type of the node. */
    private final NodeType type;

    /** The array of values stored by the node. */
    private final List<T> values;

    /**
     * Create a new value node.
     *
     * @param type The type of the node.
     * @param values The values to store.
     */
    public ArrayNode(final @NonNull NodeType type, final @NonNull List<T> values) {
        switch (type) {
            case BOOLEAN_ARRAY,
                    BYTE_ARRAY,
                    DOUBLE_ARRAY,
                    FLOAT_ARRAY,
                    INTEGER_ARRAY,
                    LONG_ARRAY,
                    NODE_ARRAY,
                    SHORT_ARRAY,
                    STRING_ARRAY:
                break;
            case BOOLEAN, BYTE, DOUBLE, FLOAT, INTEGER, LONG, NODE, SHORT, STRING:
            default:
                log.warn(
                        SafeResourceLoader.getStringFormatted(
                                "NODE_UNEXPECTED_TYPE",
                                FactoryPlugin.getResourceBundle(),
                                this.getClass().getSimpleName(),
                                type.name()));
                throw new UnsupportedOperationException();
        }
        this.type = type;
        this.values = new ArrayList<>(values);
    }

    @Override
    public void add(final @NonNull String name) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void add(String name, @NonNull NodeType newType, @NonNull Boolean value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <U> void add(String name, @NonNull NodeType newType, @NonNull List<U> value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void add(String name, @NonNull NodeType newType, @NonNull Node value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void add(String name, @NonNull NodeType newType, @NonNull String value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <U extends Number> void add(String name, @NonNull NodeType newType, @NonNull U value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <U> void add(final @NonNull String name, final @NonNull U childValue) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <U> U get(final @NonNull String name) {
        throw new UnsupportedOperationException();
    }

    @Override
    public NodeType getType() {
        return type;
    }

    @Override
    public Optional<NodeType> getType(@NonNull String name) {
        return Optional.empty();
    }

    @Override
    public boolean hasChild(final @NonNull String name) {
        return false;
    }

    @Override
    public String toString() {
        return String.format(
                "[%s;%s]",
                type.getArrayLetter(),
                values.stream()
                        .map(value -> ArrayNode.toString(type, value))
                        .collect(Collectors.joining(",")));
    }
}
