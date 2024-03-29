package com.ikalagaming.factory.kvt;

import com.ikalagaming.factory.FactoryPlugin;
import com.ikalagaming.util.SafeResourceLoader;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;

/**
 * A node that is just a value and the type.
 *
 * @author Ches Burks
 * @param <T> The type that this node stores.
 */
@Getter
@Setter
@Slf4j
@EqualsAndHashCode
public class ValueNode<T> implements KVT {

    /** The type of the node. */
    private final NodeType type;

    /** The value stored by the node. */
    private T value;

    /**
     * Create a new value node.
     *
     * @param type The type of the node.
     * @param value The value to store.
     */
    public ValueNode(final @NonNull NodeType type, final @NonNull T value) {
        switch (type) {
            case BOOLEAN, BYTE, DOUBLE, FLOAT, INTEGER, LONG, SHORT, STRING:
                break;
            case BOOLEAN_ARRAY,
                    BYTE_ARRAY,
                    DOUBLE_ARRAY,
                    FLOAT_ARRAY,
                    INTEGER_ARRAY,
                    LONG_ARRAY,
                    NODE,
                    NODE_ARRAY,
                    SHORT_ARRAY,
                    STRING_ARRAY:
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
        this.value = value;
    }

    @Override
    public void add(final @NonNull String name) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void add(String name, @NonNull NodeType newType, @NonNull Boolean newValue) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <U> void add(String name, @NonNull NodeType newType, @NonNull List<U> newValue) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void add(String name, @NonNull NodeType newType, @NonNull Node newValue) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void add(String name, @NonNull NodeType newType, @NonNull String newValue) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <U extends Number> void add(
            String name, @NonNull NodeType newType, @NonNull U newValue) {
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
        return switch (type) {
            case BOOLEAN_ARRAY,
                            BYTE_ARRAY,
                            DOUBLE_ARRAY,
                            FLOAT_ARRAY,
                            INTEGER_ARRAY,
                            LONG_ARRAY,
                            NODE,
                            NODE_ARRAY,
                            SHORT_ARRAY,
                            STRING_ARRAY ->
                    "";
            case BOOLEAN, DOUBLE, INTEGER -> value.toString();
            case BYTE -> value.toString() + "B";
            case FLOAT -> value.toString() + "F";
            case LONG -> value.toString() + "L";
            case SHORT -> value.toString() + "S";
            case STRING -> String.format("\"%s\"", value.toString());
        };
    }
}
