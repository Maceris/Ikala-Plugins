package com.ikalagaming.factory.gui;

import com.ikalagaming.graphics.GuiInstance;
import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.scene.Scene;

import lombok.NonNull;

public class MainMenu implements GuiInstance {
    @Override
    public void drawGui() {}

    @Override
    public boolean handleGuiInput(@NonNull Scene scene, @NonNull Window window) {
        return false;
    }
}
