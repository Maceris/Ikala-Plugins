package com.ikalagaming.rpg.windows;

import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.scene.Scene;

import lombok.NonNull;

/**
 * An individual window.
 *
 * @author Ches Burks
 */
public interface GUIWindow {
    /** Draw the contents of the window. */
    void draw();

    /**
     * Process event inputs.
     *
     * @param scene The scene we are rendering.
     * @param window The window we are in.
     */
    default void handleGuiInput(@NonNull Scene scene, @NonNull Window window) {}

    /**
     * Set up any required data.
     *
     * @param scene The scene we are setting up in.
     */
    void setup(@NonNull Scene scene);
}
