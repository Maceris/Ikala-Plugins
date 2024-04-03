package com.ikalagaming.factory.gui;

import com.ikalagaming.launcher.events.Shutdown;

import imgui.ImColor;
import imgui.ImGui;
import imgui.flag.ImGuiCol;

/** A menu bar at the top of the screen for debugging. */
public class DebugToolbar extends GuiWindow {
    @Override
    public void drawGui() {
        if (ImGui.beginMainMenuBar()) {
            if (ImGui.beginMenu("Windows")) {
                ImGui.endMenu();
            }
            ImGui.pushStyleColor(ImGuiCol.Text, ImColor.rgba(1f, 0.1f, 0.1f, 1.0f));
            if (ImGui.menuItem("Quit Game")) {
                new Shutdown().fire();
            }
            ImGui.popStyleColor();

            ImGui.endMainMenuBar();
        }
    }
}
