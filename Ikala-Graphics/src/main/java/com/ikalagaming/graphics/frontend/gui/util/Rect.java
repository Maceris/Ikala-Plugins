package com.ikalagaming.graphics.frontend.gui.util;

import lombok.*;
import org.joml.Vector2f;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@EqualsAndHashCode
public class Rect {
    private float left;
    private float top;
    private float right;
    private float bottom;

    /**
     * Returns true if (x, y) is inside the rectangle.
     *
     * @param x The x coordinate to check.
     * @param y The y coordinate to check.
     * @return true if the point is inside the rectangle, false otherwise.
     */
    public boolean contains(final float x, final float y) {
        return (x >= left) && (x <= right) && (y >= top) && (y <= bottom);
    }

    /**
     * Returns true if the point is inside the rectangle.
     *
     * @param point The point to check.
     * @return true if the point is inside the rectangle, false otherwise.
     */
    public boolean contains(@NonNull Vector2f point) {
        return contains(point.x, point.y);
    }

    /**
     * Return the height of the rectangle. Will always be >= 0.
     *
     * @return The height.
     */
    public float getHeight() {
        return Math.abs(bottom - top);
    }

    /**
     * Return the width of the rectangle. Will always be >= 0.
     *
     * @return The width.
     */
    public float getWidth() {
        return Math.abs(right - left);
    }

    /**
     * Set all the coordinates.
     *
     * @param left The left value.
     * @param top The top value.
     * @param right The right value.
     * @param bottom The bottom value.
     */
    public void set(final float left, final float top, final float right, final float bottom) {
        this.left = left;
        this.right = right;
        this.top = top;
        this.bottom = bottom;
    }
}
