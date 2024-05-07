package com.ikalagaming.factory.gui.component;

import com.ikalagaming.factory.gui.Component;
import com.ikalagaming.graphics.backend.base.TextureHandler;

import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;
import lombok.NonNull;

/** A scrollable area that contains other components. */
public class ScrollBox extends Component {

    private final String title;
    private final int windowFlags;

    /**
     * Add a scrollable area.
     *
     * @param title The unique name for the component, which does not actually show up on the
     *     screen.
     */
    public ScrollBox(String title) {
        this.title = title;
        this.windowFlags = ImGuiWindowFlags.None;
    }

    @Override
    public void draw(int width, int height, @NonNull TextureHandler textureHandler) {
        ImGui.setCursorPosX(getActualDisplaceX() * width - ImGui.getWindowPosX());
        ImGui.setCursorPosY(getActualDisplaceY() * height - ImGui.getWindowPosY());

        ImGui.beginChild(
                title, getActualWidth() * width, getActualHeight() * height, true, windowFlags);

        super.draw(width, height, textureHandler);
        ImGui.endChild();
    }
}
