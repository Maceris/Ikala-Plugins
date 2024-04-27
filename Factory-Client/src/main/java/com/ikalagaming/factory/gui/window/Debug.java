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
import lombok.extern.slf4j.Slf4j;

/** Used to show general debugging information. */
@Slf4j
public class Debug extends GuiWindow {

    public Debug() {
        super(DEBUG.getName(), ImGuiWindowFlags.None);
        setScale(0.20f, 0.20f);
        setDisplacement(0.01f, 0.01f);
        setAlignment(Alignment.CENTER);
    }

    @Override
    public void draw(final int width, final int height) {
        ImGui.setNextWindowPos(
                getActualDisplaceX() * width, getActualDisplaceY() * height, ImGuiCond.Always);
        ImGui.setNextWindowSize(
                getActualWidth() * width, getActualHeight() * height, ImGuiCond.Always);
        ImGui.begin(title, windowOpen, windowFlags);

        ImGui.text(String.format("Canvas size: %d, %d", width, height));
        ImGui.text(
                String.format(
                        "This window's relative size: %.2f, %.2f",
                        getActualWidth(), getActualHeight()));
        ImGui.text(
                String.format(
                        "This window's actual size: %.2f, %.2f",
                        getActualWidth() * width, getActualHeight() * height));
        ImGui.text(
                String.format(
                        "This window's relative position: %.2f, %.2f",
                        getActualDisplaceX(), getActualDisplaceY()));
        ImGui.text(
                String.format(
                        "This window's actual position: %.2f, %.2f",
                        getActualDisplaceX() * width, getActualDisplaceY() * height));

        ImGui.end();
    }

    @Override
    public boolean handleGuiInput(@NonNull Scene scene, @NonNull Window window) {
        return false;
    }
}
