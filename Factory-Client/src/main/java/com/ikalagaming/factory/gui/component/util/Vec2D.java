package com.ikalagaming.factory.gui.component.util;

import lombok.*;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class Vec2D {
    private float x;
    private float y;

    /**
     * Sets the x and y values.
     *
     * @param x The new x value.
     * @param y The new y value.
     */
    public void set(final float x, final float y) {
        this.x = x;
        this.y = y;
    }

    /**
     * Sets the values to the provided ones.
     *
     * @param point The new values.
     */
    public void set(final @NonNull Vec2D point) {
        this.x = point.x;
        this.y = point.y;
    }
}
