package com.ikalagaming.graphics.ui;

import lombok.NonNull;

/**
 * A container that places each child by its own {@link Anchors}, on top of each other in order. Use
 * it for corners, centering, and layers like a HUD.
 */
public class Overlay extends Container<Overlay> {

    /**
     * Create an overlay.
     *
     * @param id The ID, which must be unique among its siblings.
     */
    public Overlay(@NonNull String id) {
        super(id);
    }

    @Override
    public String styleType() {
        return "overlay";
    }
}
