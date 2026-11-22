package com.ikalagaming.graphics;

import lombok.NonNull;

/**
 * A shape pressed into the soft surfaces under it for one frame, see {@link Surfaces#stamp}: a
 * foot, a body lying down, a wheel's swept track. It presses only a surface whose span its height
 * lies within, give or take a quarter meter, so a print on a roof leaves the ground under it alone,
 * and no deeper than the surface is. Its press eases out toward its edge, and it can push up a rim
 * around itself. A shape pressed every frame doesn't dig deeper: the deepest press stays.
 *
 * @param shape Its shape.
 * @param x Where it is along x, in the world: an ellipse's or box's middle, a capsule's start.
 * @param y Its height, in the world: where its bottom meets the surface.
 * @param z Where it is along z.
 * @param toX A capsule's end along x; unused otherwise.
 * @param toZ A capsule's end along z; unused otherwise.
 * @param sizeA An ellipse's radius along its angle, a box's half length along it, or a capsule's
 *     radius, in meters.
 * @param sizeB An ellipse's radius across its angle, or a box's half width across it; unused for a
 *     capsule.
 * @param angle Which way an ellipse or box points, in radians from +x toward +z.
 * @param depth How deep it presses, in meters.
 * @param rimWidth How far out its rim reaches past its edge, in meters, or 0 for none.
 * @param rimHeight How high its rim rises, in meters.
 */
public record SurfaceStamp(
        @NonNull Shape shape,
        double x,
        double y,
        double z,
        double toX,
        double toZ,
        float sizeA,
        float sizeB,
        float angle,
        float depth,
        float rimWidth,
        float rimHeight) {

    /** A stamp's shape. */
    public enum Shape {
        /** An ellipse, like a foot or a hoof. */
        ELLIPSE,
        /** A line with a radius, like a body, a tail, or a wheel's track over a frame. */
        CAPSULE,
        /** A rectangle, like a crate or a sled's runner. */
        BOX
    }

    /**
     * An ellipse.
     *
     * @param x Its middle along x.
     * @param y Its height.
     * @param z Its middle along z.
     * @param along Its radius along its angle, in meters.
     * @param across Its radius across.
     * @param angle Which way it points, radians from +x toward +z.
     * @param depth How deep it presses, in meters.
     * @return The stamp, without a rim.
     */
    public static SurfaceStamp ellipse(
            double x, double y, double z, float along, float across, float angle, float depth) {
        return new SurfaceStamp(Shape.ELLIPSE, x, y, z, x, z, along, across, angle, depth, 0, 0);
    }

    /**
     * A capsule: a line from one point to another with a radius.
     *
     * @param x Its start along x.
     * @param y Its height.
     * @param z Its start along z.
     * @param toX Its end along x.
     * @param toZ Its end along z.
     * @param radius Its radius, in meters.
     * @param depth How deep it presses, in meters.
     * @return The stamp, without a rim.
     */
    public static SurfaceStamp capsule(
            double x, double y, double z, double toX, double toZ, float radius, float depth) {
        return new SurfaceStamp(Shape.CAPSULE, x, y, z, toX, toZ, radius, radius, 0, depth, 0, 0);
    }

    /**
     * A box.
     *
     * @param x Its middle along x.
     * @param y Its height.
     * @param z Its middle along z.
     * @param halfLength Half its length along its angle, in meters.
     * @param halfWidth Half its width across.
     * @param angle Which way it points, radians from +x toward +z.
     * @param depth How deep it presses, in meters.
     * @return The stamp, without a rim.
     */
    public static SurfaceStamp box(
            double x,
            double y,
            double z,
            float halfLength,
            float halfWidth,
            float angle,
            float depth) {
        return new SurfaceStamp(
                Shape.BOX, x, y, z, x, z, halfLength, halfWidth, angle, depth, 0, 0);
    }

    /**
     * The same stamp pushing up a rim around itself.
     *
     * @param width How far out the rim reaches past its edge, in meters.
     * @param height How high it rises, in meters.
     * @return The stamp.
     */
    public SurfaceStamp withRim(float width, float height) {
        return new SurfaceStamp(
                shape, x, y, z, toX, toZ, sizeA, sizeB, angle, depth, width, height);
    }
}
