package com.ikalagaming.graphics.ui.spec;

import lombok.NonNull;

/**
 * The event that started a script from a spec's {@code script(...)} action, given to the script as
 * the global {@code event}. It holds values, never nodes, so a script keeping it can't keep a UI
 * alive.
 */
public final class ScriptEvent {
    /** The event's value, like submitted text, or null. */
    private final String value;

    /** The repeat item the event happened in, or null. */
    private final Object item;

    /**
     * Create an event.
     *
     * @param value The event's value, like submitted text, or null.
     * @param item The repeat item the event happened in, or null.
     */
    ScriptEvent(String value, Object item) {
        this.value = value;
        this.item = item;
    }

    /**
     * The event's value, like the text of a submitted text input.
     *
     * @return The value, or null if the event has none.
     */
    public String value() {
        return value;
    }

    /**
     * The repeat item the event happened in, like the row that was clicked.
     *
     * @return The item, or null outside a repeat.
     */
    public Object item() {
        return item;
    }

    /**
     * A field of the repeat item: a map key, a record component, or a getter, the same as {@code
     * {item.field}} in a spec.
     *
     * @param name The field name.
     * @return The value, or null if there is no item or no such field.
     */
    public Object field(@NonNull String name) {
        if (item == null || name.isEmpty()) {
            return null;
        }
        return SpecInstance.readField(item, name);
    }

    @Override
    public String toString() {
        return "ScriptEvent[value=" + value + ", item=" + item + "]";
    }
}
