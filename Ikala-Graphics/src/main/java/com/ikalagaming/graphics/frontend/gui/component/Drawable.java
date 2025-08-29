package com.ikalagaming.graphics.frontend.gui.component;

import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.scene.Scene;

import lombok.NonNull;

/** Something that can be rendered on the screen as part of a GUI. */
public interface Drawable {

    /**
     * Draws the item on the screen.
     *
     * @param width The width of the window in pixels.
     * @param height The height of the window in pixels.
     */
    void draw(final int width, final int height);

    /**
     * Process GUI inputs, which happens at a different frequency from rendering.
     *
     * @param scene The scene we are rendering.
     * @param window The window we are using.
     */
    boolean handleGuiInput(@NonNull Scene scene, @NonNull Window window);

    /**
     * Update any internal values as required, which happens at a different frequency from
     * rendering.
     *
     * @param scene The scene we are rendering.
     * @param window The window we are using.
     */
    void updateValues(@NonNull Scene scene, @NonNull Window window);
}
