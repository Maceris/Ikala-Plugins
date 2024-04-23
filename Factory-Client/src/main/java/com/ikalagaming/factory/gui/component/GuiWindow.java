package com.ikalagaming.factory.gui.component;

import com.ikalagaming.factory.gui.Component;

import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.type.ImBoolean;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class GuiWindow extends Component {
    protected final String title;
    protected final int windowFlags;
    protected final ImBoolean windowOpen;

    @Override
    public void draw(final int width, final int height) {
        ImGui.setNextWindowPos(
                getActualDisplaceX() * width, getActualDisplaceY() * height, ImGuiCond.Always);
        ImGui.setNextWindowSize(
                getActualWidth() * width, getActualHeight() * height, ImGuiCond.Always);
        ImGui.begin(title, windowOpen, windowFlags);

        super.draw(width, height);

        ImGui.end();
    }
}
