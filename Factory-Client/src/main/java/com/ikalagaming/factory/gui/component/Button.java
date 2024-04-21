package com.ikalagaming.factory.gui.component;

import com.ikalagaming.factory.gui.GuiWindow;

import imgui.ImGui;
import lombok.RequiredArgsConstructor;

/** A button, which may have text. */
@RequiredArgsConstructor
public class Button extends GuiWindow implements Component, Interactive {

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
    public void draw() {
        if (ImGui.button(text, getActualWidth(), getActualHeight())) {
            pressed = true;
        }
    }
}
