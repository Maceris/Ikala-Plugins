package com.ikalagaming.factory.gui.component;

import com.ikalagaming.factory.gui.Component;

import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;

/** A scrollable area that contains other components. */
public class ScrollBox extends Component {

    protected final String title;
    protected final int windowFlags;

    /**
     * Add a scrollable area.
     *
     * @param title The unique name for the component, which does not actually show up on the
     *     screen.
     */
    public ScrollBox(String title) {
        this.title = title;
        this.windowFlags = ImGuiWindowFlags.AlwaysVerticalScrollbar;
    }

    @Override
    public void draw(int width, int height) {
        ImGui.setCursorPosX(getActualDisplaceX() * width - ImGui.getWindowPosX());
        ImGui.setCursorPosY(getActualDisplaceY() * height - ImGui.getWindowPosY());

        ImGui.beginChild(
                title, getActualWidth() * width, getActualHeight() * height, true, windowFlags);

        super.draw(width, height);
        ImGui.endChild();
    }
}
