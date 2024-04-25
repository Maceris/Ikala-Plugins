package com.ikalagaming.factory.gui.window;

import static com.ikalagaming.factory.gui.DefaultWindows.DEBUG;

import com.ikalagaming.factory.gui.component.GuiWindow;
import com.ikalagaming.factory.gui.component.util.Alignment;
import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.scene.Scene;

import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;
import lombok.NonNull;

/** Used to show general debugging information. */
public class Debug extends GuiWindow {

    public Debug() {
        super(DEBUG.getName(), ImGuiWindowFlags.None);
        setScale(0.10f, 0.10f);
        setDisplacement(0.01f, 0.01f);
        setAlignment(Alignment.CENTER);
    }

    @Override
    public void draw(final int width, final int height) {
        ImGui.setNextWindowPos(
                getActualDisplaceX() * width, getActualDisplaceY() * height, ImGuiCond.Once);
        ImGui.setNextWindowSize(
                getActualWidth() * width, getActualHeight() * height, ImGuiCond.Once);
        ImGui.begin(title, windowOpen, windowFlags);

        ImGui.text(String.format("%d, %d", width, height));

        ImGui.end();
    }

    @Override
    public boolean handleGuiInput(@NonNull Scene scene, @NonNull Window window) {
        return false;
    }
}
