package com.ikalagaming.factory.gui.component;

import com.ikalagaming.factory.gui.SizeConstants;

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
    public void draw(final SizeConstants size) {
        if (ImGui.button(text, size.buttonWidth(), size.buttonHeight())) {
            pressed = true;
        }
    }
}
