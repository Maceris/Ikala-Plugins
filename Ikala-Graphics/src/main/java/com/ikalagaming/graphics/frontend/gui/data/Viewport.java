package com.ikalagaming.graphics.frontend.gui.data;

import org.joml.Vector2f;

public class Viewport {
    private static DrawData DRAW_DATA = null;
    public int flags;
    public final Vector2f workSize;

    public Viewport() {
        workSize = new Vector2f(0.0f, 0.0f);
    }
    // TODO(ches) viewport
}
