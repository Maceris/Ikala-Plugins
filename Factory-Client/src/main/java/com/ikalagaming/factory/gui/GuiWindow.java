package com.ikalagaming.factory.gui;

import com.ikalagaming.graphics.GuiInstance;
import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.scene.Scene;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

/**
 * A component that we can draw on the screen, can handle input, and can be enabled/disabled.
 * Intended to be something complex and toggleable like a main menu or floating window, rather than
 * a button or list.
 */
@Setter
@Getter
public abstract class GuiWindow implements GuiInstance {
    /** Whether the window should show. */
    protected boolean visible;

    @Override
    public void handleGuiInput(@NonNull Scene scene, @NonNull Window window) {}
}
