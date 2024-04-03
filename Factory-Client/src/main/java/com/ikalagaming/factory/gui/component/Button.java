package com.ikalagaming.factory.gui.component;

import imgui.ImGui;
import lombok.RequiredArgsConstructor;

/** A button, which may have text. */
@RequiredArgsConstructor
public class Button implements Component, Interactive {

    /** The text to show on the button. */
    private final String text;

    /** Whether the user interacted with the button. */
    private boolean pressed;

    @Override
    public boolean checkResult() {
        var result = pressed;
        pressed = false;
        return result;
    }

    @Override
    public void draw(int width, int height) {
        if (ImGui.button(text, width, height)) {
            pressed = true;
        }
    }
}
