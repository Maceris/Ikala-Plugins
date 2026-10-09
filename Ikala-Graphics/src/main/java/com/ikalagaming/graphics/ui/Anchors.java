package com.ikalagaming.graphics.ui;

import lombok.NonNull;

/**
 * Where a node sits inside an {@link Overlay}, or where a {@link Surface} sits on the screen. Each
 * edge is pinned to a fraction of the parent, where 0 is the left or top and 1 the right or bottom,
 * then moved by an offset.
 *
 * <p>On an axis where the two anchors are equal, the node keeps its own size and the point at that
 * fraction of the node lines up with the anchor: 0 pins its left edge, 0.5 its center and 1 its
 * right edge. The offset then moves it, with positive values going right and down. On an axis where
 * the anchors differ, the node stretches between them, and its edges are moved by their own
 * offsets.
 *
 * @param minX The anchor for the left edge.
 * @param minY The anchor for the top edge.
 * @param maxX The anchor for the right edge.
 * @param maxY The anchor for the bottom edge.
 * @param offsetMinX Moves the left edge, or the whole node if it doesn't stretch horizontally.
 * @param offsetMinY Moves the top edge, or the whole node if it doesn't stretch vertically.
 * @param offsetMaxX Moves the right edge when stretching horizontally.
 * @param offsetMaxY Moves the bottom edge when stretching vertically.
 */
public record Anchors(
        float minX,
        float minY,
        float maxX,
        float maxY,
        @NonNull Length offsetMinX,
        @NonNull Length offsetMinY,
        @NonNull Length offsetMaxX,
        @NonNull Length offsetMaxY) {

    /**
     * Pin the node at a point of the parent, keeping its own size.
     *
     * @param x The horizontal fraction, 0 left to 1 right.
     * @param y The vertical fraction, 0 top to 1 bottom.
     * @return The anchors.
     */
    public static Anchors at(float x, float y) {
        return new Anchors(x, y, x, y, Length.ZERO, Length.ZERO, Length.ZERO, Length.ZERO);
    }

    /**
     * Fill the whole parent.
     *
     * @return The anchors.
     */
    public static Anchors fill() {
        return new Anchors(0, 0, 1, 1, Length.ZERO, Length.ZERO, Length.ZERO, Length.ZERO);
    }

    /**
     * Fill the whole parent, less a margin on every side.
     *
     * @param margin The margin.
     * @return The anchors.
     */
    public static Anchors fill(@NonNull Length margin) {
        Length negative = new Length(-margin.units(), -margin.percent(), -margin.em());
        return new Anchors(0, 0, 1, 1, margin, margin, negative, negative);
    }

    /**
     * Centered in the parent.
     *
     * @return The anchors.
     */
    public static Anchors center() {
        return at(0.5f, 0.5f);
    }

    /**
     * In the top left corner.
     *
     * @return The anchors.
     */
    public static Anchors topLeft() {
        return at(0, 0);
    }

    /**
     * In the top right corner.
     *
     * @return The anchors.
     */
    public static Anchors topRight() {
        return at(1, 0);
    }

    /**
     * In the bottom left corner.
     *
     * @return The anchors.
     */
    public static Anchors bottomLeft() {
        return at(0, 1);
    }

    /**
     * In the bottom right corner.
     *
     * @return The anchors.
     */
    public static Anchors bottomRight() {
        return at(1, 1);
    }

    /**
     * A copy moved by an offset. Only meaningful on axes that don't stretch.
     *
     * @param x The horizontal offset, positive is right.
     * @param y The vertical offset, positive is down.
     * @return The moved anchors.
     */
    public Anchors offset(@NonNull Length x, @NonNull Length y) {
        return new Anchors(minX, minY, maxX, maxY, x, y, offsetMaxX, offsetMaxY);
    }

    /**
     * Whether the node stretches along an axis.
     *
     * @param axis The axis.
     * @return True if the two anchors on that axis differ.
     */
    boolean stretches(Axis axis) {
        return axis == Axis.X ? minX != maxX : minY != maxY;
    }
}
