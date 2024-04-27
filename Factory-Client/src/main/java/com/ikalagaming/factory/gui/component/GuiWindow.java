package com.ikalagaming.factory.gui.component;

import com.ikalagaming.factory.gui.Component;

import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.type.ImBoolean;

public class GuiWindow extends Component {
    protected final String title;
    protected final int windowFlags;
    protected final ImBoolean windowOpen = new ImBoolean(true);

    /**
     * Create a window with the specified title and window flags. Windows are not visible by
     * default.
     *
     * @param title The title of the window, for use with ImGui.
     * @param windowFlags The ImGui flags for the window.
     * @see imgui.flag.ImGuiWindowFlags
     */
    public GuiWindow(String title, int windowFlags) {
        this.title = title;
        this.windowFlags = windowFlags;
        this.visible = false;
    }

    @Override
    public void draw(final int width, final int height) {
        ImGui.setNextWindowViewport(ImGui.getMainViewport().getID());
        ImGui.setNextWindowPos(
                getActualDisplaceX() * width, getActualDisplaceY() * height, ImGuiCond.Always);
        ImGui.setNextWindowSize(
                getActualWidth() * width, getActualHeight() * height, ImGuiCond.Always);
        ImGui.begin(title, windowOpen, windowFlags);

        super.draw(width, height);

        ImGui.end();
    }
}
