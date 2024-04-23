package com.ikalagaming.graphics;

import com.ikalagaming.graphics.scene.Scene;

import lombok.NonNull;

/** Defines a Graphical User Interface that we can interact with. */
public interface GuiInstance {
    /**
     * Used to draw the GUI.
     *
     * @param width The width of the window, in pixels.
     * @param height The width of the height, in pixels.
     */
    void drawGui(final int width, final int height);

    /**
     * Process GUI inputs, which might happen at a different frequency than rendering.
     *
     * @param scene The scene we are rendering.
     * @param window The window we are using.
     */
    void handleGuiInput(@NonNull Scene scene, @NonNull Window window);
}
