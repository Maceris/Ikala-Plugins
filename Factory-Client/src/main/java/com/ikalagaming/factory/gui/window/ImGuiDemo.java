package com.ikalagaming.factory.gui.window;

import static com.ikalagaming.factory.gui.DefaultWindows.IMGUI_DEMO;

import com.ikalagaming.graphics.frontend.gui.component.GuiWindow;
import com.ikalagaming.graphics.frontend.gui.util.Alignment;

import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;

/** Used to show the ImGui demo window. */
public class ImGuiDemo extends GuiWindow {

    public ImGuiDemo() {
        super(IMGUI_DEMO.getName(), ImGuiWindowFlags.None);
        setScale(0.20f, 0.20f);
        setDisplacement(0.01f, 0.01f);
        setAlignment(Alignment.CENTER);
    }

    @Override
    public void draw(int width, int height) {
        ImGui.showDemoWindow();
    }
}
