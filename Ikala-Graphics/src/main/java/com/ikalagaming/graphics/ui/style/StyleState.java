package com.ikalagaming.graphics.ui.style;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * An interaction state a style can give different values for. When several apply, the earlier one
 * in this list wins, so a held button uses its held colors even though it is also hovered.
 */
@RequiredArgsConstructor
public enum StyleState {
    /** The item can't be used. */
    DISABLED("disabled"),
    /** The item is being pressed. */
    HELD("held"),
    /** The mouse is over the item. */
    HOVERED("hovered"),
    /** The item has keyboard or gamepad focus. */
    FOCUSED("focused"),
    /** The item is selected, like a chosen row in a list. */
    SELECTED("selected"),
    /** The item is related to what the mouse is over, like a link of the hovered graph node. */
    RELATED("related");

    /**
     * The name used in theme files.
     *
     * @return The name.
     */
    @Getter private final String yamlName;

    /**
     * Find a state by its theme file name.
     *
     * @param name The name.
     * @return The state, or null if there is none with that name.
     */
    public static StyleState byName(@NonNull String name) {
        for (StyleState state : values()) {
            if (state.yamlName.equals(name)) {
                return state;
            }
        }
        return null;
    }
}
