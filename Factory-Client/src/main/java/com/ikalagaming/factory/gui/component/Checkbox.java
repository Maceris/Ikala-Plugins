package com.ikalagaming.factory.gui.component;

import com.ikalagaming.factory.gui.SizeConstants;

import imgui.ImGui;
import imgui.type.ImBoolean;
import lombok.RequiredArgsConstructor;

/** A checkbox with text. */
@RequiredArgsConstructor
public class Checkbox implements Component, Interactive {

    /** The text to show next to the checkbox. */
    private final String text;

    /** The checkbox state. */
    private final ImBoolean state;

    /** Whether the user interacted with the checkbox. */
    private boolean changed;

    /**
     * Set up a new checkbox.
     *
     * @param text The text to show next to the checkbox.
     * @param initialState The initial state of the checkbox.
     */
    public Checkbox(String text, boolean initialState) {
        this.text = text;
        this.state = new ImBoolean(initialState);
    }

    @Override
    public boolean checkResult() {
        var result = changed;
        changed = false;
        return result;
    }

    @Override
    public void draw(final SizeConstants size) {
        if (ImGui.checkbox(text, state)) {
            changed = true;
        }
    }

    /**
     * Fetch the current state of the checkbox.
     *
     * @return Whether the checkbox is checked.
     * @see #checkResult() To see if this value has changed.
     */
    public boolean getState() {
        return state.get();
    }
}
