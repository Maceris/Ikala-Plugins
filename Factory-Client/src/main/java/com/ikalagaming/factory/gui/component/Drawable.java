package com.ikalagaming.factory.gui.component;

import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.backend.base.TextureHandler;
import com.ikalagaming.graphics.scene.Scene;

import lombok.NonNull;

/**
 * Something that can be rendered on the screen as part of a {@link
 * com.ikalagaming.graphics.GuiInstance GuiInstance}.
 */
public interface Drawable {

    /**
     * Draws the item on the screen.
     *
     * @param width The width of the window in pixels.
     * @param height The height of the window in pixels.
     * @param textureHandler The texture handler implementation to use.
     */
    void draw(final int width, final int height, @NonNull TextureHandler textureHandler);

    /**
     * Process GUI inputs, which might happen at a different frequency than rendering.
     *
     * @param scene The scene we are rendering.
     * @param window The window we are using.
     */
    boolean handleGuiInput(@NonNull Scene scene, @NonNull Window window);
}
