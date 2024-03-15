package com.ikalagaming.factory.kvt;

import com.ikalagaming.factory.FactoryPlugin;
import com.ikalagaming.util.SafeResourceLoader;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * A node that is a list of other nodes. The keys must be unique and are kept ordered by name.
 *
 * @author Ches Burks
 */
@Getter
@Slf4j
@EqualsAndHashCode
public class Node implements KVT {

    /**
     * Escape the key if required.
     *
     * @param key The key.
     * @return The key, escaped if necessary.
     */
    private static String formatKey(final @NonNull String key) {
        if (TreeStringSerialization.NORMAL_KEY_PATTERN.matcher(key).matches()) {
            return key;
        }
        return "\"" + key + "\"";
    }

    /** The key value pairs in the node. */
    public final Map<String, KVT> values;

    /** Set up an empty node. */
    public Node() {
        values = new TreeMap<>();
    }

    private void add(final @NonNull List<String> names, int index, @NonNull KVT node) {

        if (index < 0 || index >= names.size()) {
            log.warn(
                    SafeResourceLoader.getStringFormatted(
                            "NODE_INVALID_INDEX",
                            FactoryPlugin.getResourceBundle(),
                            "" + index,
                            String.join(".", names)));
            return;
        }

        final String next = names.get(index);

        if (index == names.size() - 1) {
            values.put(next, node);
            return;
        }

        if (values.computeIfAbsent(next, ignored -> new Node()) instanceof Node cast) {
            cast.add(names, index + 1, node);
        } else {
            log.warn(
                    SafeResourceLoader.getStringFormatted(
                            "NODE_INVALID_TYPE",
                            FactoryPlugin.getResourceBundle(),
                            next,
                            String.join(".", names)));
        }
    }

    @Override
    public void add(final @NonNull String name) {
        this.add(name, new Node());
    }

    private void add(final @NonNull String name, @NonNull KVT node) {
        this.add(List.of(name.split("\\.")), 0, node);
    }

    @Override
    public void add(String name, @NonNull NodeType type, @NonNull Boolean value) {
        this.add(name, new ValueNode<>(type, value));
    }

    @Override
    public <T> void add(String name, @NonNull NodeType type, @NonNull List<T> value) {
        this.add(name, new ArrayNode<>(type, value));
    }

    @Override
    public void add(String name, @NonNull NodeType type, @NonNull Node value) {
        this.add(name, value);
    }

    @Override
    public void add(String name, @NonNull NodeType type, @NonNull String value) {
        this.add(name, new ValueNode<>(type, value));
    }

    @Override
    public <T extends Number> void add(String name, @NonNull NodeType type, @NonNull T value) {
        this.add(name, new ValueNode<>(type, value));
    }

    @Override
    public <U> void add(final @NonNull String name, @NonNull U childValue) {

        if (childValue instanceof Boolean cast) {
            var node = new ValueNode<>(NodeType.BOOLEAN, cast);
            values.put(name, node);
            return;
        }

        if (childValue instanceof Byte cast) {
            var node = new ValueNode<>(NodeType.BYTE, cast);
            values.put(name, node);
            return;
        }

        if (childValue instanceof Double cast) {
            var node = new ValueNode<>(NodeType.DOUBLE, cast);
            values.put(name, node);
            return;
        }

        if (childValue instanceof Float cast) {
            var node = new ValueNode<>(NodeType.FLOAT, cast);
            values.put(name, node);
            return;
        }

        if (childValue instanceof Integer cast) {
            var node = new ValueNode<>(NodeType.INTEGER, cast);
            values.put(name, node);
            return;
        }

        if (childValue instanceof Long cast) {
            var node = new ValueNode<>(NodeType.LONG, cast);
            values.put(name, node);
            return;
        }

        if (childValue instanceof Node cast) {
            var node = new ValueNode<>(NodeType.NODE, cast);
            values.put(name, node);
            return;
        }

        if (childValue instanceof Short cast) {
            var node = new ValueNode<>(NodeType.SHORT, cast);
            values.put(name, node);
            return;
        }

        if (childValue instanceof String cast) {
            var node = new ValueNode<>(NodeType.BOOLEAN, cast);
            values.put(name, node);
        }
        throw new UnsupportedOperationException();
    }

    @SuppressWarnings("unchecked")
    private <U> U get(final @NonNull List<String> names, final int index) {
        if (index < 0 || index >= names.size()) {
            log.warn(
                    SafeResourceLoader.getStringFormatted(
                            "MISSING_NODE",
                            FactoryPlugin.getResourceBundle(),
                            String.join(".", names)));
            return null;
        }

        KVT node = values.get(names.get(index));

        if (node instanceof Node cast) {
            if (index == names.size() - 1) {
                return (U) node;
            }
            return cast.get(names, index + 1);
        }

        if (node instanceof ValueNode<?> cast) {
            return (U) cast.getValue();
        }

        if (node instanceof ArrayNode<?> cast) {
            return (U) cast.getValues();
        }

        log.warn(
                SafeResourceLoader.getStringFormatted(
                        "MISSING_NODE",
                        FactoryPlugin.getResourceBundle(),
                        String.join(".", names)));

        return null;
    }

    @Override
    public <U> U get(final @NonNull String name) {
        return this.get(List.of(name.split("\\.")), 0);
    }

    @Override
    public NodeType getType() {
        return NodeType.NODE;
    }

    @Override
    public boolean hasChild(@NonNull String name) {
        return values.containsKey(name);
    }

    @Override
    public String toString() {
        return String.format(
                "{%s}",
                values.entrySet().stream()
                        .map(
                                pair ->
                                        String.format(
                                                "%s:%s",
                                                Node.formatKey(pair.getKey()), pair.getValue()))
                        .collect(Collectors.joining(",")));
    }
}
