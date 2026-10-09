package com.ikalagaming.graphics.ui.style;

import com.ikalagaming.graphics.ui.Insets;
import com.ikalagaming.graphics.ui.Length;

import lombok.NonNull;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * A set of style values, each either a literal or a {@link Token}, plus overrides for interaction
 * states. Immutable; build one with {@link #builder()}.
 */
public final class Style {
    /** A style that sets nothing. */
    public static final Style EMPTY =
            new Style(new EnumMap<>(StyleKey.class), new EnumMap<>(StyleState.class));

    /** The values, by property. */
    private final Map<StyleKey, Object> values;

    /** Overrides for interaction states. */
    private final Map<StyleState, Style> states;

    /**
     * Create a style.
     *
     * @param values The values, which are copied.
     * @param states The state overrides, which are copied.
     */
    private Style(Map<StyleKey, Object> values, Map<StyleState, Style> states) {
        this.values = Collections.unmodifiableMap(new EnumMap<>(values));
        this.states = Collections.unmodifiableMap(new EnumMap<>(states));
    }

    /**
     * Start building a style.
     *
     * @return A new builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * The values set by this style, not counting states.
     *
     * @return The values, each a literal or a {@link Token}.
     */
    public Map<StyleKey, Object> values() {
        return values;
    }

    /**
     * The overrides for interaction states.
     *
     * @return The overrides.
     */
    public Map<StyleState, Style> states() {
        return states;
    }

    /**
     * Whether this style sets nothing at all.
     *
     * @return True if there are no values or states.
     */
    public boolean isEmpty() {
        return values.isEmpty() && states.isEmpty();
    }

    /**
     * Layer another style on top of this one. Its values win, and its state overrides are layered
     * on top of this one's state by state.
     *
     * @param over The style to put on top.
     * @return The combined style.
     */
    public Style with(@NonNull Style over) {
        if (over.isEmpty()) {
            return this;
        }
        if (isEmpty()) {
            return over;
        }
        Map<StyleKey, Object> mergedValues = new EnumMap<>(StyleKey.class);
        mergedValues.putAll(values);
        mergedValues.putAll(over.values);
        Map<StyleState, Style> mergedStates = new EnumMap<>(StyleState.class);
        mergedStates.putAll(states);
        over.states.forEach((state, style) -> mergedStates.merge(state, style, Style::with));
        return new Style(mergedValues, mergedStates);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Style style
                && values.equals(style.values)
                && states.equals(style.states);
    }

    @Override
    public int hashCode() {
        return values.hashCode() * 31 + states.hashCode();
    }

    @Override
    public String toString() {
        return "Style" + values + (states.isEmpty() ? "" : states.toString());
    }

    /** Builds a {@link Style}. */
    public static final class Builder {
        /** The values so far. */
        private final Map<StyleKey, Object> values = new EnumMap<>(StyleKey.class);

        /** The state overrides so far. */
        private final Map<StyleState, Style> states = new EnumMap<>(StyleState.class);

        /** Use {@link Style#builder()}. */
        private Builder() {}

        /**
         * Set a property to a literal value or a token.
         *
         * @param key The property.
         * @param value The value, of the type the property takes, or a {@link Token}.
         * @return This builder.
         * @throws IllegalArgumentException If the value is the wrong type for the property.
         */
        public Builder set(@NonNull StyleKey key, @NonNull Object value) {
            if (!(value instanceof Token) && !fits(key, value)) {
                throw new IllegalArgumentException(
                        key
                                + " takes a "
                                + key.getType()
                                + ", not "
                                + value.getClass().getSimpleName());
            }
            values.put(key, value);
            return this;
        }

        /**
         * Set a property to a token.
         *
         * @param key The property.
         * @param token The token name.
         * @return This builder.
         */
        public Builder token(@NonNull StyleKey key, @NonNull String token) {
            return set(key, new Token(token));
        }

        /**
         * Set a color property.
         *
         * @param key The property.
         * @param rgba The packed color, red in the high byte.
         * @return This builder.
         */
        public Builder color(@NonNull StyleKey key, int rgba) {
            return set(key, rgba);
        }

        /**
         * Set the overrides for an interaction state.
         *
         * @param state The state.
         * @param style The overrides.
         * @return This builder.
         */
        public Builder state(@NonNull StyleState state, @NonNull Style style) {
            states.put(state, style);
            return this;
        }

        /**
         * Finish the style.
         *
         * @return The style.
         */
        public Style build() {
            return values.isEmpty() && states.isEmpty() ? EMPTY : new Style(values, states);
        }
    }

    /**
     * Whether a literal value is the right type for a property.
     *
     * @param key The property.
     * @param value The value.
     * @return True if it fits.
     */
    static boolean fits(StyleKey key, Object value) {
        return switch (key.getType()) {
            case COLOR -> value instanceof Integer;
            case LENGTH -> value instanceof Length;
            case INSETS -> value instanceof Insets;
            case NUMBER -> value instanceof Float;
        };
    }
}
