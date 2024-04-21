/*
 * NOTICE: This file is a modified version of contents from
 * https://github.com/lwjglgamedev/lwjglbook, which was licensed under Apache
 * v2.0. Changes have been made related to formatting, functionality, and
 * naming.
 */
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
