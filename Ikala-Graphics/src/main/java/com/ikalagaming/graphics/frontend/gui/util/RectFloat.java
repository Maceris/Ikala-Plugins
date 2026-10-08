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
     * Construct a copy of another rectangle.
     *
     * @param other The rectangle to copy.
     */
    public RectFloat(@NonNull RectFloat other) {
        this(other.left, other.top, other.right, other.bottom);
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

    /**
     * Check if the rectangle is inverted, meaning the left is past the right or the top is below
     * the bottom.
     *
     * @return True if inverted.
     */
    public boolean isInverted() {
        return left > right || top > bottom;
    }

    /**
     * The horizontal center.
     *
     * @return The x coordinate of the center.
     */
    public float getCenterX() {
        return (left + right) * 0.5f;
    }

    /**
     * The vertical center.
     *
     * @return The y coordinate of the center.
     */
    public float getCenterY() {
        return (top + bottom) * 0.5f;
    }

    /**
     * Returns the area of the rectangle.
     *
     * @return The width times the height.
     */
    public float getArea() {
        return (right - left) * (bottom - top);
    }

    /**
     * Check if this rectangle overlaps another one at all. Touching edges do not count as
     * overlapping.
     *
     * @param other The other rectangle.
     * @return True if the rectangles overlap.
     */
    public boolean overlaps(@NonNull RectFloat other) {
        return other.top < bottom && other.bottom > top && other.left < right && other.right > left;
    }

    /**
     * Check if this rectangle fully contains another one.
     *
     * @param other The other rectangle.
     * @return True if the other rectangle is entirely within this one.
     */
    public boolean contains(@NonNull RectFloat other) {
        return other.left >= left
                && other.top >= top
                && other.right <= right
                && other.bottom <= bottom;
    }

    /**
     * Shrink this rectangle so that it fits inside the other one. Only the outer edges are clamped,
     * so the result may be inverted (have a negative width or height) if the rectangles don't
     * overlap.
     *
     * @param other The rectangle to clip with.
     */
    public void clipWith(@NonNull RectFloat other) {
        left = Math.max(left, other.left);
        top = Math.max(top, other.top);
        right = Math.min(right, other.right);
        bottom = Math.min(bottom, other.bottom);
    }

    /**
     * Fully clip this rectangle with another one, ensuring that the result is never inverted (the
     * max values are never less than the min values).
     *
     * @param other The rectangle to clip with.
     */
    public void clipWithFull(@NonNull RectFloat other) {
        left = MathUtil.clamp(left, other.left, Math.max(other.left, other.right));
        top = MathUtil.clamp(top, other.top, Math.max(other.top, other.bottom));
        right = MathUtil.clamp(right, other.left, Math.max(other.left, other.right));
        bottom = MathUtil.clamp(bottom, other.top, Math.max(other.top, other.bottom));
    }

    /**
     * Grow the rectangle in all directions by the given amounts. Negative values shrink it.
     *
     * @param amountX The amount to move the left and right edges outward.
     * @param amountY The amount to move the top and bottom edges outward.
     */
    public void expand(float amountX, float amountY) {
        left -= amountX;
        top -= amountY;
        right += amountX;
        bottom += amountY;
    }

    /**
     * Move the rectangle by the given amount.
     *
     * @param x The amount to move along the x-axis.
     * @param y The amount to move along the y-axis.
     */
    public void translate(float x, float y) {
        left += x;
        right += x;
        top += y;
        bottom += y;
    }

    /**
     * Grow the rectangle so that it contains the given point.
     *
     * @param x The x coordinate of the point.
     * @param y The y coordinate of the point.
     */
    public void add(float x, float y) {
        left = Math.min(left, x);
        top = Math.min(top, y);
        right = Math.max(right, x);
        bottom = Math.max(bottom, y);
    }

    /**
     * Grow the rectangle so that it contains another rectangle.
     *
     * @param other The rectangle to include.
     */
    public void add(@NonNull RectFloat other) {
        left = Math.min(left, other.left);
        top = Math.min(top, other.top);
        right = Math.max(right, other.right);
        bottom = Math.max(bottom, other.bottom);
    }
}
