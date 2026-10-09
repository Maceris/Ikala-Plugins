package com.ikalagaming.graphics.ui;

import com.ikalagaming.graphics.ui.style.StyleState;

import java.util.EnumSet;
import java.util.Set;

/**
 * The interaction state of a {@link CustomItem} this frame.
 *
 * @param hovered Whether the mouse is over the item.
 * @param held Whether the item is being held down, by the mouse or a navigation input.
 * @param focused Whether the item has keyboard or gamepad navigation focus.
 * @param pressed Whether the item was activated this frame.
 */
public record ItemState(boolean hovered, boolean held, boolean focused, boolean pressed) {

    /**
     * The style states that apply, for looking up state-specific style values.
     *
     * @return The states.
     */
    public Set<StyleState> styleStates() {
        Set<StyleState> states = EnumSet.noneOf(StyleState.class);
        if (hovered) {
            states.add(StyleState.HOVERED);
        }
        if (held) {
            states.add(StyleState.HELD);
        }
        if (focused) {
            states.add(StyleState.FOCUSED);
        }
        return states;
    }
}
