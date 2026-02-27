package com.ikalagaming.graphics.frontend.gui.windows;

import com.ikalagaming.graphics.frontend.gui.component.GuiWindow;
import com.ikalagaming.graphics.frontend.gui.util.Alignment;

import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;

/** Used to show the ImGui demo window. */
public class ImGuiDemo extends GuiWindow {

    public static final String WINDOW_NAME = "ImGUI Demo";

    public ImGuiDemo() {
        super(WINDOW_NAME, ImGuiWindowFlags.None);
        setScale(0.20f, 0.20f);
        setDisplacement(0.01f, 0.01f);
        setAlignment(Alignment.CENTER);
    }

    @Override
    public void draw(int width, int height) {
        ImGui.showDemoWindow();
    }
}
