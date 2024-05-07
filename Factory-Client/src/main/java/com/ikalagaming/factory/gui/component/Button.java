package com.ikalagaming.factory.gui.component;

import com.ikalagaming.factory.gui.Component;
import com.ikalagaming.graphics.backend.base.TextureHandler;

import imgui.ImGui;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/** A button, which may have text. */
@RequiredArgsConstructor
public class Button extends Component implements Interactive {

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
    public void draw(final int width, final int height, @NonNull TextureHandler textureHandler) {
        ImGui.setCursorPosX(getActualDisplaceX() * width - ImGui.getWindowPosX());
        ImGui.setCursorPosY(getActualDisplaceY() * height - ImGui.getWindowPosY());

        if (ImGui.button(text, getActualWidth() * width, getActualHeight() * height)) {
            pressed = true;
        }
    }
}
