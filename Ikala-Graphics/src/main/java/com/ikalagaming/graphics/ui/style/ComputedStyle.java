package com.ikalagaming.graphics.ui.style;

import com.ikalagaming.graphics.ui.Insets;
import com.ikalagaming.graphics.ui.Length;

import lombok.NonNull;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * The finished style for one node: every token replaced by its value, converted to the type each
 * property takes. Values that are not set are null, which means "leave IkGui's own style".
 */
public final class ComputedStyle {
    /** A style with nothing set. */
    public static final ComputedStyle EMPTY =
            new ComputedStyle(new EnumMap<>(StyleKey.class), new EnumMap<>(StyleState.class));

    /** The values outside any state. */
    private final Map<StyleKey, Object> base;

    /** Values that change in each state. */
    private final Map<StyleState, Map<StyleKey, Object>> states;

    /**
     * Create a computed style.
     *
     * @param base The values outside any state.
     * @param states The values that change in each state.
     */
    ComputedStyle(Map<StyleKey, Object> base, Map<StyleState, Map<StyleKey, Object>> states) {
        this.base = Collections.unmodifiableMap(base);
        this.states = Collections.unmodifiableMap(states);
    }

    /**
     * A value outside any state.
     *
     * @param key The property.
     * @return The value, or null if it is not set.
     */
    public Object get(@NonNull StyleKey key) {
        return base.get(key);
    }

    /**
     * A value in a state. If several states are active, the first in {@link StyleState} order that
     * sets the property wins; if none do, the value outside any state is used.
     *
     * @param key The property.
     * @param active The states that apply.
     * @return The value, or null if it is not set.
     */
    public Object get(@NonNull StyleKey key, @NonNull Set<StyleState> active) {
        for (StyleState state : StyleState.values()) {
            if (active.contains(state)) {
                Map<StyleKey, Object> values = states.get(state);
                if (values != null && values.containsKey(key)) {
                    return values.get(key);
                }
            }
        }
        return base.get(key);
    }

    /**
     * A value set for one state only, ignoring the value outside states.
     *
     * @param key The property.
     * @param state The state.
     * @return The value, or null if that state doesn't set it.
     */
    public Object getInState(@NonNull StyleKey key, @NonNull StyleState state) {
        Map<StyleKey, Object> values = states.get(state);
        return values == null ? null : values.get(key);
    }

    /**
     * A color outside any state.
     *
     * @param key A color property.
     * @return The packed color, or null if it is not set.
     */
    public Integer color(@NonNull StyleKey key) {
        return (Integer) get(key);
    }

    /**
     * A length outside any state.
     *
     * @param key A length property.
     * @return The length, or null if it is not set.
     */
    public Length length(@NonNull StyleKey key) {
        return (Length) get(key);
    }

    /**
     * Insets outside any state.
     *
     * @param key An insets property.
     * @return The insets, or null if they are not set.
     */
    public Insets insets(@NonNull StyleKey key) {
        return (Insets) get(key);
    }

    /**
     * A number outside any state.
     *
     * @param key A number property.
     * @return The number, or null if it is not set.
     */
    public Float number(@NonNull StyleKey key) {
        return (Float) get(key);
    }

    @Override
    public String toString() {
        return "ComputedStyle" + base + (states.isEmpty() ? "" : states.toString());
    }
}
