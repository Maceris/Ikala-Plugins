package com.ikalagaming.factory.gui.window;

import com.ikalagaming.factory.gui.DefaultWindows;
import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.frontend.gui.component.Checkbox;
import com.ikalagaming.graphics.frontend.gui.component.GuiWindow;
import com.ikalagaming.graphics.frontend.gui.util.Alignment;
import com.ikalagaming.graphics.scene.Scene;

import imgui.flag.ImGuiWindowFlags;
import lombok.NonNull;

public class GraphicsDebug extends GuiWindow {

    private final Checkbox fogEnabled;

    public GraphicsDebug() {
        super(DefaultWindows.GRAPHICS_DEBUG.getName(), ImGuiWindowFlags.None);
        setScale(0.24f, 0.25f);
        setDisplacement(0.01f, 0.05f);
        setAlignment(Alignment.NORTH_EAST);

        // (TODO) ches update this when a scene is loaded, preferably fix backend to allow real time
        fogEnabled = new Checkbox("Fog enabled", false);

        addChild(fogEnabled);
    }

    @Override
    public boolean handleGuiInput(@NonNull Scene scene, @NonNull Window window) {
        if (fogEnabled.checkResult()) {
            scene.getFog().setActive(fogEnabled.getState());
            return true;
        }
        return false;
    }
}
