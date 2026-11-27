package com.ikalagaming.graphics.ui.graph.model;

import lombok.NonNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * The kinds of node a graph can hold, which the caller registers: their pins, parameters, category
 * and version. The editor gives them no meaning beyond their pins' types.
 */
public final class NodeTypes {
    /** The types by ID, in the order registered. */
    private final Map<String, NodeType> types = new LinkedHashMap<>();

    /**
     * A pin of a node type.
     *
     * @param name The name, unique among the type's inputs, or among its outputs.
     * @param type The pin type's ID.
     */
    public record Pin(@NonNull String name, @NonNull String type) {}

    /**
     * A parameter of a node type: a value set on each node, as text.
     *
     * @param name The name, unique among the type's parameters.
     * @param defaultValue What it is until set; left out of files.
     */
    public record Param(@NonNull String name, @NonNull String defaultValue) {}

    /**
     * A kind of node.
     *
     * @param id The ID, like {@code math/add}, unique among the types.
     * @param title What its nodes are titled.
     * @param category Where it is listed in the search, like {@code Math}.
     * @param inputs Its input pins, top to bottom.
     * @param outputs Its output pins, top to bottom.
     * @param params Its parameters.
     * @param version Its version, raised when its pins or parameters change, for upgrading files.
     */
    public record NodeType(
            @NonNull String id,
            @NonNull String title,
            @NonNull String category,
            @NonNull List<Pin> inputs,
            @NonNull List<Pin> outputs,
            @NonNull List<Param> params,
            int version) {
        /** Copy the lists so they can't change. */
        public NodeType {
            inputs = List.copyOf(inputs);
            outputs = List.copyOf(outputs);
            params = List.copyOf(params);
        }

        /**
         * An input by name.
         *
         * @param name The name.
         * @return The pin, or null if there is none.
         */
        public @Nullable Pin input(@NonNull String name) {
            return find(inputs, name);
        }

        /**
         * An output by name.
         *
         * @param name The name.
         * @return The pin, or null if there is none.
         */
        public @Nullable Pin output(@NonNull String name) {
            return find(outputs, name);
        }

        /**
         * A parameter's default.
         *
         * @param name The parameter.
         * @return The default, or null if there is no such parameter.
         */
        public @Nullable String defaultOf(@NonNull String name) {
            for (Param param : params) {
                if (param.name().equals(name)) {
                    return param.defaultValue();
                }
            }
            return null;
        }

        /**
         * A pin by name.
         *
         * @param pins The pins.
         * @param name The name.
         * @return The pin, or null.
         */
        private static Pin find(List<Pin> pins, String name) {
            for (Pin pin : pins) {
                if (pin.name().equals(name)) {
                    return pin;
                }
            }
            return null;
        }
    }

    /**
     * Add a type, or replace one with the same ID.
     *
     * @param type The type.
     * @return These types.
     */
    public NodeTypes register(@NonNull NodeType type) {
        types.put(type.id(), type);
        return this;
    }

    /**
     * A type by ID.
     *
     * @param id The ID.
     * @return The type, or null if there is none.
     */
    public @Nullable NodeType get(@NonNull String id) {
        return types.get(id);
    }

    /**
     * Every type, in the order registered.
     *
     * @return The types.
     */
    public Collection<NodeType> all() {
        return Collections.unmodifiableCollection(types.values());
    }

    /**
     * The types whose title, category or ID contain some text, ignoring case, and that have a pin a
     * link from or to a given pin could use, sorted by category then title.
     *
     * @param query The text, or empty for every type.
     * @param pins What converts to what.
     * @param linkType The type of the pin being linked, or null for any node.
     * @param linkFromOutput Whether that pin is an output, so the node needs a fitting input.
     * @return The types.
     */
    public List<NodeType> search(
            @NonNull String query,
            @NonNull PinTypes pins,
            @Nullable String linkType,
            boolean linkFromOutput) {
        final String wanted = query.trim().toLowerCase(Locale.ROOT);
        final List<NodeType> found = new ArrayList<>();
        for (NodeType type : types.values()) {
            final String text =
                    (type.title() + " " + type.category() + " " + type.id())
                            .toLowerCase(Locale.ROOT);
            if (!wanted.isEmpty() && !text.contains(wanted)) {
                continue;
            }
            if (linkType != null && fittingPin(type, pins, linkType, linkFromOutput) == null) {
                continue;
            }
            found.add(type);
        }
        found.sort(
                (a, b) -> {
                    final int byCategory = a.category().compareToIgnoreCase(b.category());
                    return byCategory != 0 ? byCategory : a.title().compareToIgnoreCase(b.title());
                });
        return found;
    }

    /**
     * The first pin of a node type a link from or to a given pin can use: of the same type if there
     * is one, else one a conversion reaches.
     *
     * @param type The node type.
     * @param pins What converts to what.
     * @param linkType The type of the pin being linked.
     * @param linkFromOutput Whether that pin is an output, so an input is wanted.
     * @return The pin, or null if none fits.
     */
    public static @Nullable Pin fittingPin(
            @NonNull NodeType type,
            @NonNull PinTypes pins,
            @NonNull String linkType,
            boolean linkFromOutput) {
        final List<Pin> candidates = linkFromOutput ? type.inputs() : type.outputs();
        Pin converted = null;
        for (Pin pin : candidates) {
            final PinTypes.Conversion conversion =
                    linkFromOutput
                            ? pins.conversion(linkType, pin.type())
                            : pins.conversion(pin.type(), linkType);
            if (pin.type().equals(linkType)) {
                return pin;
            }
            if (conversion.kind() != PinTypes.Kind.REFUSED && converted == null) {
                converted = pin;
            }
        }
        return converted;
    }
}
