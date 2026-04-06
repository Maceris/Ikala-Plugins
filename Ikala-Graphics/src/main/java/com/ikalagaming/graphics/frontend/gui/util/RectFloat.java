package com.ikalagaming.graphics.frontend.gui.util;

import lombok.*;
import org.joml.Vector2f;
import org.joml.Vector4f;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@EqualsAndHashCode
public class RectFloat {
    private float left;
    private float top;
    private float right;
    private float bottom;

    /**
     * Construct from a Vector4f.
     *
     * @param values Left, top, right, and bottom in that order.
     */
    public RectFloat(@NonNull Vector4f values) {
        this(values.x, values.y, values.z, values.w);
    }

    /**
     * Construct a rect from a point and size.
     *
     * @param topLeft The left and top position.
     * @param size The width and height.
     */
    public RectFloat(@NonNull Vector2f topLeft, @NonNull Vector2f size) {
        this(topLeft.x, topLeft.y, topLeft.x + size.x, topLeft.y + size.y);
    }

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
     * Returns true if the point is inside the rectangle, inclusive of extra padding. For example, x
     * padding of 1 would mean 1 unit on either side of the x-axis would count as still inside the
     * rect.
     *
     * @param x The x coordinate to check.
     * @param y The y coordinate to check.
     * @param paddingX Padding to add on both sides of the x-axis.
     * @param paddingY Padding to add on both sides of the y-axis.
     * @return true if the point is inside the rectangle plus padding, false otherwise.
     */
    public boolean containsWithPadding(
            final float x, final float y, final float paddingX, final float paddingY) {
        return (x >= left - paddingX)
                && (x <= right + paddingX)
                && (y >= top - paddingY)
                && (y <= bottom + paddingY);
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
     * Returns true if the point is inside the rectangle, inclusive of extra padding.
     *
     * @param point The point to check.
     * @param padding Padding to add in the x and y axes. For example, x padding of 1 would mean 1
     *     unit on either side of the x-axis would count as still inside the rect.
     * @return true if the point is inside the rectangle plus padding, false otherwise.
     */
    public boolean containsWithPadding(@NonNull Vector2f point, @NonNull Vector2f padding) {
        return containsWithPadding(point.x, point.y, padding.x, padding.y);
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

    /**
     * Set the coordinates.
     *
     * @param values Left, top, right, and bottom in that order.
     */
    public void set(@NonNull Vector4f values) {
        set(values.x, values.y, values.z, values.w);
    }

    /**
     * Set based on a top left coordinate and size.
     *
     * @param topLeft The left and top position.
     * @param size The width and height.
     */
    public void set(@NonNull Vector2f topLeft, @NonNull Vector2f size) {
        set(topLeft.x, topLeft.y, topLeft.x + size.x, topLeft.y + size.y);
    }

    /**
     * Set the values of this rectangle to the same as the other one.
     *
     * @param other The values to use.
     */
    public void set(@NonNull RectFloat other) {
        set(other.left, other.top, other.right, other.bottom);
    }
}
