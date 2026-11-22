package com.ikalagaming.graphics.benchmark;

import com.ikalagaming.graphics.GraphicsContext;
import com.ikalagaming.graphics.SurfaceStamp;

import lombok.NonNull;

import java.util.function.Supplier;

/**
 * The gate's prints in its soft surfaces, from the {@code .stampTest=x,y,z} option, every shape
 * stamps take: a walker's feet in turn around a circle of {@link #WALK_RADIUS} meters about the
 * point, a sled's track swept around a wider circle, and a crate pressed in the middle. The point's
 * height is where the prints meet the surface. {@code .refill=meters per second} sets how fast the
 * surface fills back in. Render thread.
 */
final class GateStamps {

    /** The walker's circle's radius, its speed and its stride, in meters and seconds. */
    private static final double WALK_RADIUS = 4;

    private static final double WALK_SPEED = 1.4;

    private static final double STRIDE = 0.7;

    /** How far each foot is from the walker's middle line, in meters. */
    private static final double FOOT_SPACING = 0.12;

    /** A foot's half length and half width, how deep it presses, and its rim. */
    private static final float FOOT_LENGTH = 0.14f;

    private static final float FOOT_WIDTH = 0.06f;

    private static final float FOOT_DEPTH = 0.12f;

    private static final float FOOT_RIM_WIDTH = 0.04f;

    private static final float FOOT_RIM_HEIGHT = 0.015f;

    /** The sled's circle's radius, its speed, and its runner's radius and depth. */
    private static final double SLED_RADIUS = 7;

    private static final double SLED_SPEED = 3;

    private static final float SLED_WIDTH = 0.25f;

    private static final float SLED_DEPTH = 0.08f;

    /** The crate's half size and how deep it presses. */
    private static final float CRATE_HALF = 0.4f;

    private static final float CRATE_DEPTH = 0.2f;

    /** How fast the surface fills back in, by default, in meters a second. */
    private static final String DEFAULT_REFILL = "0.002";

    /** The context the stamps go through. */
    private final GraphicsContext graphics;

    /** The run's options. */
    private final GateOptions options;

    /** The world's surfaces, for their material's refill rate. */
    private final Supplier<GateSurfaces> surfaces;

    /** When stamping started, from {@link System#nanoTime()}, or 0 before. */
    private long start;

    /** Where the sled was last frame, or NaN. */
    private double sledX = Double.NaN;

    private double sledZ;

    /**
     * Make the stamps.
     *
     * @param graphics The context the stamps go through.
     * @param options The run's options.
     * @param surfaces The world's surfaces, or null before there are any.
     */
    GateStamps(
            @NonNull GraphicsContext graphics,
            @NonNull GateOptions options,
            @NonNull Supplier<GateSurfaces> surfaces) {
        this.graphics = graphics;
        this.options = options;
        this.surfaces = surfaces;
    }

    /** Press this frame's prints, if the option asks for them. */
    void step() {
        final var at = options.point("stampTest");
        if (at == null) {
            return;
        }
        final long now = System.nanoTime();
        if (start == 0) {
            start = now;
            final GateSurfaces world = surfaces.get();
            if (world != null) {
                graphics.surfaces()
                        .setRefillRate(
                                world.material(),
                                Float.parseFloat(options.get("refill", DEFAULT_REFILL).trim()));
            }
        }
        final double seconds = (now - start) / 1e9;
        // The foot planted now: steps alternate sides along the circle
        final long step = (long) Math.floor(seconds * WALK_SPEED / STRIDE);
        final double angle = step * STRIDE / WALK_RADIUS;
        final double side = (step & 1) == 0 ? FOOT_SPACING : -FOOT_SPACING;
        final double radius = WALK_RADIUS + side;
        final double footX = at.x + radius * Math.cos(angle);
        final double footZ = at.z + radius * Math.sin(angle);
        // Walking round counter-clockwise seen from above: along the tangent
        final float heading = (float) (angle + Math.PI / 2);
        graphics.surfaces()
                .stamp(
                        SurfaceStamp.ellipse(
                                        footX,
                                        at.y,
                                        footZ,
                                        FOOT_LENGTH,
                                        FOOT_WIDTH,
                                        heading,
                                        FOOT_DEPTH)
                                .withRim(FOOT_RIM_WIDTH, FOOT_RIM_HEIGHT));
        // The sled sweeps from where it was to where it is
        final double sledAngle = seconds * SLED_SPEED / SLED_RADIUS;
        final double x = at.x + SLED_RADIUS * Math.cos(sledAngle);
        final double z = at.z + SLED_RADIUS * Math.sin(sledAngle);
        graphics.surfaces()
                .stamp(
                        SurfaceStamp.capsule(
                                Double.isNaN(sledX) ? x : sledX,
                                at.y,
                                Double.isNaN(sledX) ? z : sledZ,
                                x,
                                z,
                                SLED_WIDTH,
                                SLED_DEPTH));
        sledX = x;
        sledZ = z;
        graphics.surfaces()
                .stamp(
                        SurfaceStamp.box(
                                at.x, at.y, at.z, CRATE_HALF, CRATE_HALF, 0.4f, CRATE_DEPTH));
    }
}
