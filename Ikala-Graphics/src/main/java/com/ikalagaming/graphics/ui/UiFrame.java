package com.ikalagaming.graphics.ui;

import lombok.NonNull;

import java.util.function.Consumer;

/**
 * What nodes need to know while they are submitted to IkGui for a frame.
 *
 * @param context The layout context used this frame.
 * @param events Collects actions that nodes fire, such as button clicks. They run later, outside
 *     rendering, so they can do things like load models.
 */
public record UiFrame(@NonNull LayoutContext context, @NonNull Consumer<Runnable> events) {

    /**
     * Fire an action, which runs on the render thread after the frame is drawn.
     *
     * @param action The action, or null to do nothing.
     */
    public void fire(Runnable action) {
        if (action != null) {
            events.accept(action);
        }
    }
}
