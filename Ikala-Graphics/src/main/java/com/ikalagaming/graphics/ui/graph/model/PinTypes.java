package com.ikalagaming.graphics.ui.graph.model;

import lombok.NonNull;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * The kinds of value a graph's pins carry, and which can be linked to which. The caller registers
 * them; the editor gives them no meaning. A link between different types is refused unless a
 * conversion is registered: either direct, the value used as it is, or through a node of a given
 * type that the editor puts between the two pins.
 *
 * <pre>
 * types.register(new PinType("scalar", "Scalar", Color.rgba(0.6f, 0.6f, 0.6f, 1)))
 *      .register(new PinType("color", "Color", Color.rgba(1, 0.8f, 0.2f, 1)))
 *      .convertWith("scalar", "color", "math/gray");
 * </pre>
 */
public final class PinTypes {
    /**
     * The type of the editor's own reroute pins, which link with anything and carry the type of
     * what is linked into them.
     */
    public static final String ANY = "*";

    /** The types by ID, in the order registered. */
    private final Map<String, PinType> types = new LinkedHashMap<>();

    /** Make the types, holding only {@link #ANY}. */
    public PinTypes() {
        types.put(ANY, new PinType(ANY, "Any", 0xFFAAAAAA));
    }

    /** Conversions, by from-type then to-type. */
    private final Map<String, Map<String, Conversion>> conversions = new LinkedHashMap<>();

    /**
     * A kind of value.
     *
     * @param id The ID, unique among the types.
     * @param label What it is called in the editor.
     * @param color The pin's and its links' color, packed as by {@code Color.rgba}.
     */
    public record PinType(@NonNull String id, @NonNull String label, int color) {}

    /**
     * How a value of one type becomes another, or doesn't.
     *
     * @param kind What happens.
     * @param nodeType The node type put between the pins, for {@link Kind#NODE}, else null.
     */
    public record Conversion(@NonNull Kind kind, @Nullable String nodeType) {
        /** The same type, or a direct conversion. */
        public static final Conversion DIRECT = new Conversion(Kind.DIRECT, null);

        /** No conversion. */
        public static final Conversion REFUSED = new Conversion(Kind.REFUSED, null);
    }

    /** What linking two types does. */
    public enum Kind {
        /** The link is made as it is. */
        DIRECT,
        /** A conversion node goes between the pins. */
        NODE,
        /** The link is refused. */
        REFUSED
    }

    /**
     * Add a type, or replace one with the same ID.
     *
     * @param type The type.
     * @return These types.
     */
    public PinTypes register(@NonNull PinType type) {
        types.put(type.id(), type);
        return this;
    }

    /**
     * Let one type link straight into another, the value used as it is.
     *
     * @param from The output's type.
     * @param to The input's type.
     * @return These types.
     */
    public PinTypes convertDirectly(@NonNull String from, @NonNull String to) {
        conversions.computeIfAbsent(from, f -> new LinkedHashMap<>()).put(to, Conversion.DIRECT);
        return this;
    }

    /**
     * Let one type link into another through a node, which the editor puts between the pins.
     *
     * @param from The output's type.
     * @param to The input's type.
     * @param nodeType The node type, with an input of the first type and an output of the second.
     * @return These types.
     */
    public PinTypes convertWith(
            @NonNull String from, @NonNull String to, @NonNull String nodeType) {
        conversions
                .computeIfAbsent(from, f -> new LinkedHashMap<>())
                .put(to, new Conversion(Kind.NODE, nodeType));
        return this;
    }

    /**
     * A type by ID.
     *
     * @param id The ID.
     * @return The type, or null if there is none.
     */
    public @Nullable PinType get(@NonNull String id) {
        return types.get(id);
    }

    /**
     * Every type, in the order registered.
     *
     * @return The types.
     */
    public Collection<PinType> all() {
        return Collections.unmodifiableCollection(types.values());
    }

    /**
     * What linking an output of one type into an input of another does.
     *
     * @param from The output's type.
     * @param to The input's type.
     * @return The conversion: direct for the same type.
     */
    public Conversion conversion(@NonNull String from, @NonNull String to) {
        if (from.equals(to) || ANY.equals(from) || ANY.equals(to)) {
            return Conversion.DIRECT;
        }
        final Map<String, Conversion> into = conversions.get(from);
        final Conversion found = into == null ? null : into.get(to);
        return found == null ? Conversion.REFUSED : found;
    }
}
