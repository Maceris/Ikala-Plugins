package com.ikalagaming.factory.kvt;

import com.ikalagaming.factory.FactoryStrings;
import com.ikalagaming.util.SafeResourceLoader;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.Optional;
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
        if (TreeStringSerialization.NORMAL_KEY_PATTERN.matcher(key).matches()
                && !TreeStringSerialization.LITERAL_KEY_PATTERN.matcher(key).matches()) {
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
                            FactoryStrings.bundle(),
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
                            FactoryStrings.bundle(),
                            next,
                            String.join(".", names)));
        }
    }

    @Override
    public void add(final @NonNull String name) {
        this.addPath(name, new Node());
    }

    private void addPath(final @NonNull String name, @NonNull KVT node) {
        this.add(List.of(name.split("\\.")), 0, node);
    }

    @Override
    public void add(String name, @NonNull NodeType type, @NonNull Boolean value) {
        this.addPath(name, new ValueNode<>(type, value));
    }

    @Override
    public <T> void add(String name, @NonNull NodeType type, @NonNull List<T> value) {
        this.addPath(name, new ArrayNode<>(type, value));
    }

    @Override
    public void add(String name, @NonNull NodeType type, @NonNull Node value) {
        this.addPath(name, value);
    }

    @Override
    public void add(String name, @NonNull NodeType type, @NonNull String value) {
        this.addPath(name, new ValueNode<>(type, value));
    }

    @Override
    public <T extends Number> void add(String name, @NonNull NodeType type, @NonNull T value) {
        this.addPath(name, new ValueNode<>(type, value));
    }

    @Override
    public <U> void add(final @NonNull String name, @NonNull U childValue) {

        final KVT node =
                switch (childValue) {
                    case Boolean cast -> new ValueNode<>(NodeType.BOOLEAN, cast);
                    case Byte cast -> new ValueNode<>(NodeType.BYTE, cast);
                    case Double cast -> new ValueNode<>(NodeType.DOUBLE, cast);
                    case Float cast -> new ValueNode<>(NodeType.FLOAT, cast);
                    case Integer cast -> new ValueNode<>(NodeType.INTEGER, cast);
                    case Long cast -> new ValueNode<>(NodeType.LONG, cast);
                    case Node cast -> cast;
                    case Short cast -> new ValueNode<>(NodeType.SHORT, cast);
                    case String cast -> new ValueNode<>(NodeType.STRING, cast);
                    default -> throw new UnsupportedOperationException();
                };
        // Through the path aware add, so periods nest like every other add
        this.addPath(name, node);
    }

    @SuppressWarnings("unchecked")
    private <U> U get(final @NonNull List<String> names, final int index) {
        if (index < 0 || index >= names.size()) {
            log.warn(
                    SafeResourceLoader.getStringFormatted(
                            "MISSING_NODE", FactoryStrings.bundle(), String.join(".", names)));
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
                        "MISSING_NODE", FactoryStrings.bundle(), String.join(".", names)));

        return null;
    }

    @Override
    public <U> U get(final @NonNull String name) {
        return this.get(List.of(name.split("\\.")), 0);
    }

    @Override
    public List<String> getKeys() {
        return List.copyOf(this.values.keySet());
    }

    @Override
    public NodeType getType() {
        return NodeType.NODE;
    }

    @Override
    public Optional<NodeType> getType(@NonNull String name) {
        var parts = List.of(name.split("\\."));
        KVT node = this;

        for (int i = 0; i < parts.size() - 1; ++i) {
            var nextPart = parts.get(i);
            node = Optional.ofNullable(node).map(n -> n.getNode(nextPart)).orElse(null);
        }

        var lastPart = parts.get(parts.size() - 1);
        return Optional.ofNullable(node)
                .map(Node.class::cast)
                .map(n -> n.values.get(lastPart))
                .map(KVT::getType);
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
