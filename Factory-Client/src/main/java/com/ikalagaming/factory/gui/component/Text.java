package com.ikalagaming.factory.gui.component;

import com.ikalagaming.factory.gui.Component;

import imgui.ImGui;
import lombok.RequiredArgsConstructor;

/** Any regular text that we need to position on the GUI. */
@RequiredArgsConstructor
public class Text extends Component {

    /** The text to show on the button. */
    private final String contents;

    @Override
    public void draw(final int width, final int height) {
        ImGui.setCursorPosX(getActualDisplaceX() * width - ImGui.getWindowPosX());
        ImGui.setCursorPosY(getActualDisplaceY() * height - ImGui.getWindowPosY());

        ImGui.text(contents);
    }
}
