package com.ikalagaming.factory.render;

import com.ikalagaming.factory.sim.Fixed;
import com.ikalagaming.factory.sim.Frames;
import com.ikalagaming.factory.sim.Movement;
import com.ikalagaming.factory.sim.PlayerSimulation;

import lombok.NonNull;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Ripples and splashes from the player in water, a look rather than game state: a splash on falling
 * in, scaled by how fast; ripples every few frames while wading or swimming at the surface; and a
 * small splash on climbing or jumping out. The water's own effects throw up droplets where it is
 * disturbed. One per player; call {@link #step} once per command frame.
 */
public final class PlayerSplashes {

    /** Whether there is water at a block. */
    @FunctionalInterface
    public interface WaterAt {
        /**
         * Whether a block is water.
         *
         * @param x Its x.
         * @param y Its y.
         * @param z Its z.
         * @return True if water.
         */
        boolean water(long x, long y, long z);
    }

    /**
     * A disturbance to make.
     *
     * @param position Where, on the surface.
     * @param radius How far around, in meters.
     * @param strength How high the ripples start, in meters.
     */
    public record Splash(@NonNull Vector3d position, float radius, float strength) {}

    /** The radius of the splash on falling in, in meters. */
    static final float ENTRY_RADIUS = 0.9f;

    /** The ripple height of a gentle entry, in meters. */
    static final float ENTRY_BASE = 0.05f;

    /** How much higher the entry ripples start per meter a second of falling. */
    static final float ENTRY_PER_SPEED = 0.03f;

    /** The highest entry splash, in meters. */
    static final float ENTRY_MAX = 0.6f;

    /** The radius of the ripples while moving, in meters. */
    static final float WAKE_RADIUS = 0.5f;

    /** The ripple height while moving, in meters, plus a little per meter a second. */
    static final float WAKE_BASE = 0.02f;

    static final float WAKE_PER_SPEED = 0.01f;

    /** How slowly the player can move and still leave ripples, in meters a second. */
    static final double WAKE_MIN_SPEED = 0.5;

    /** How many command frames apart the ripples while moving are. */
    static final int WAKE_FRAMES = 4;

    /** The splash on leaving the water, in meters. */
    static final float EXIT_STRENGTH = 0.04f;

    /** Meters a second per fixed-point unit a frame. */
    private static final double SPEED_SCALE = 1000.0 / Frames.FRAME_MILLIS / Fixed.ONE;

    /** The player's height, in meters. */
    private static final double HEIGHT = (double) PlayerSimulation.HEIGHT / Fixed.ONE;

    /** Frames since the last ripple while moving. */
    private int sinceWake = WAKE_FRAMES;

    /**
     * The splashes one frame of movement makes.
     *
     * @param before The movement before the frame.
     * @param after The movement after it.
     * @param water Where water is.
     * @return The splashes, often none.
     */
    public List<Splash> step(
            @NonNull Movement before, @NonNull Movement after, @NonNull WaterAt water) {
        final List<Splash> splashes = new ArrayList<>();
        ++sinceWake;
        final double x = (double) after.x() / Fixed.ONE;
        final double z = (double) after.z() / Fixed.ONE;
        final boolean wasIn = feetInWater(before, water);
        final boolean isIn = feetInWater(after, water);
        if (isIn && !wasIn) {
            final double surface = surface(after, water);
            final double falling = Math.max(0, -before.vy() * SPEED_SCALE);
            splashes.add(
                    new Splash(
                            new Vector3d(x, surface, z),
                            ENTRY_RADIUS,
                            (float) Math.min(ENTRY_MAX, ENTRY_BASE + ENTRY_PER_SPEED * falling)));
            sinceWake = 0;
        } else if (wasIn && !isIn) {
            final double surface = surface(before, water);
            splashes.add(
                    new Splash(
                            new Vector3d(
                                    (double) before.x() / Fixed.ONE,
                                    surface,
                                    (double) before.z() / Fixed.ONE),
                            WAKE_RADIUS,
                            EXIT_STRENGTH));
        } else if (isIn && sinceWake >= WAKE_FRAMES) {
            final double surface = surface(after, water);
            final double feet = (double) after.y() / Fixed.ONE;
            final double speed = Math.hypot(after.vx(), after.vz()) * SPEED_SCALE;
            // Only at the surface: wading, or swimming with the head out
            if (surface - feet <= HEIGHT && speed >= WAKE_MIN_SPEED) {
                splashes.add(
                        new Splash(
                                new Vector3d(x, surface, z),
                                WAKE_RADIUS,
                                (float) (WAKE_BASE + WAKE_PER_SPEED * speed)));
                sinceWake = 0;
            }
        }
        return splashes;
    }

    /**
     * Whether the player's feet are in water.
     *
     * @param movement Where they are.
     * @param water Where water is.
     * @return True if the block at their feet is water.
     */
    private static boolean feetInWater(@NonNull Movement movement, @NonNull WaterAt water) {
        return water.water(
                Math.floorDiv(movement.x(), Fixed.ONE),
                Math.floorDiv(movement.y(), Fixed.ONE),
                Math.floorDiv(movement.z(), Fixed.ONE));
    }

    /**
     * The height of the water's surface above the player's feet: the top of the water there.
     *
     * @param movement Where they are.
     * @param water Where water is.
     * @return The surface, in meters.
     */
    private static double surface(@NonNull Movement movement, @NonNull WaterAt water) {
        final long x = Math.floorDiv(movement.x(), Fixed.ONE);
        final long z = Math.floorDiv(movement.z(), Fixed.ONE);
        long y = Math.floorDiv(movement.y(), Fixed.ONE);
        // A few blocks up is enough for a splash; deeper water just gets one at the feet
        for (int i = 0; i < 32 && water.water(x, y + 1, z); ++i) {
            ++y;
        }
        return y + 1.0;
    }
}
