package com.ikalagaming.factory.sim;

import lombok.NonNull;

/**
 * Where something is and how it moves, after a command frame. A simulated component.
 *
 * @param mode How it moves.
 * @param x The x of its feet, in {@link Fixed} units.
 * @param y The y of its feet, in units.
 * @param z The z of its feet, in units.
 * @param vx Its speed along x, in units per frame.
 * @param vy Its speed along y, in units per frame.
 * @param vz Its speed along z, in units per frame.
 * @param yaw Its turn about the vertical axis, a 16-bit angle.
 * @param pitch Its tilt up or down, a 16-bit angle.
 * @param onGround Whether it stands on something.
 */
public record Movement(
        @NonNull MoveMode mode,
        long x,
        long y,
        long z,
        long vx,
        long vy,
        long vz,
        int yaw,
        int pitch,
        boolean onGround) {

    /**
     * Standing still at a place.
     *
     * @param mode How it moves.
     * @param x The x of its feet, in units.
     * @param y The y of its feet, in units.
     * @param z The z of its feet, in units.
     * @return The movement.
     */
    public static Movement at(@NonNull MoveMode mode, long x, long y, long z) {
        return new Movement(mode, x, y, z, 0, 0, 0, 0, 0, false);
    }
}
