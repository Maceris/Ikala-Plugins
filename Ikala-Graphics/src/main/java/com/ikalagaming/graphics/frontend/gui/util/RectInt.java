package com.ikalagaming.graphics.frontend.gui.util;

import lombok.*;
import org.joml.Vector2i;
import org.joml.Vector4i;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@EqualsAndHashCode
public class RectInt {
    private int left;
    private int top;
    private int right;
    private int bottom;

    /**
     * Construct from a Vector4i.
     *
     * @param values Left, top, right, and bottom in that order.
     */
    public RectInt(@NonNull Vector4i values) {
        this(values.x, values.y, values.z, values.w);
    }

    /**
     * Construct a rect from a point and size.
     *
     * @param topLeft The left and top position.
     * @param size The width and height.
     */
    public RectInt(@NonNull Vector2i topLeft, @NonNull Vector2i size) {
        this(topLeft.x, topLeft.y, topLeft.x + size.x, topLeft.y + size.y);
    }

    /**
     * Returns true if (x, y) is inside the rectangle.
     *
     * @param x The x coordinate to check.
     * @param y The y coordinate to check.
     * @return true if the point is inside the rectangle, false otherwise.
     */
    public boolean contains(final int x, final int y) {
        return (x >= left) && (x <= right) && (y >= top) && (y <= bottom);
    }

    /**
     * Returns true if the point is inside the rectangle.
     *
     * @param point The point to check.
     * @return true if the point is inside the rectangle, false otherwise.
     */
    public boolean contains(@NonNull Vector2i point) {
        return contains(point.x, point.y);
    }

    /**
     * Return the height of the rectangle. Will always be >= 0.
     *
     * @return The height.
     */
    public int getHeight() {
        return Math.abs(bottom - top);
    }

    /**
     * Return the width of the rectangle. Will always be >= 0.
     *
     * @return The width.
     */
    public int getWidth() {
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
    public void set(final int left, final int top, final int right, final int bottom) {
        this.left = left;
        this.right = right;
        this.top = top;
        this.bottom = bottom;
    }

    /**
     * Set the coordinates.
     *
     * @param values Left, top, right, and bottom in that order.
     */
    public void set(@NonNull Vector4i values) {
        set(values.x, values.y, values.z, values.w);
    }

    /**
     * Set based on a top left coordinate and size.
     *
     * @param topLeft The left and top position.
     * @param size The width and height.
     */
    public void set(@NonNull Vector2i topLeft, @NonNull Vector2i size) {
        set(topLeft.x, topLeft.y, topLeft.x + size.x, topLeft.y + size.y);
    }

    /**
     * Set the values of this rectangle to the same as the other one.
     *
     * @param other The values to use.
     */
    public void set(@NonNull RectInt other) {
        set(other.left, other.top, other.right, other.bottom);
    }
}
