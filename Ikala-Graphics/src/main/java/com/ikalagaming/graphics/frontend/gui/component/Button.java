package com.ikalagaming.graphics.frontend.gui.component;

import com.ikalagaming.graphics.frontend.gui.IkGui;

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
    public void draw(final int width, final int height) {
        IkGui.setCursorPosX(getActualDisplaceX() * width - IkGui.getWindowPosX());
        IkGui.setCursorPosY(getActualDisplaceY() * height - IkGui.getWindowPosY());

        if (IkGui.button(text, getActualWidth() * width, getActualHeight() * height)) {
            pressed = true;
        }
    }
}
