package com.ikalagaming.factory.gui.window;

import com.ikalagaming.factory.gui.GuiManager;
import com.ikalagaming.factory.gui.component.GuiWindow;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import lombok.NonNull;

import static com.ikalagaming.factory.gui.DefaultWindows.SINGLE_PLAYER;

public class SinglePlayer extends GuiWindow {
    private final GuiManager guiManager;

    public SinglePlayer(@NonNull GuiManager guiManager) {
        super(
                SINGLE_PLAYER.getName(),
                ImGuiWindowFlags.NoScrollbar
                        | ImGuiWindowFlags.NoScrollWithMouse
                        | ImGuiWindowFlags.NoResize
                        | ImGuiWindowFlags.NoTitleBar
                        | ImGuiWindowFlags.NoDecoration,
                new ImBoolean(true));
        this.guiManager = guiManager;
        setScale(1.0f, 0.98f);
        setDisplacement(0.0f, 0.02f);
    }
}
