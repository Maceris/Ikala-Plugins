package com.ikalagaming.factory.sim;

import com.ikalagaming.ecs.ComponentType;
import com.ikalagaming.ecs.Query;
import com.ikalagaming.ecs.Schedule;
import com.ikalagaming.ecs.World;

import lombok.NonNull;

/**
 * The simulation the server runs and clients predict: the component types, and the systems that
 * step them each command frame. Both sides build their schedule here, so they step exactly alike.
 */
public final class PlayerSimulation {

    /** Where something is and how it moves. */
    public static final ComponentType<Movement> MOVEMENT =
            ComponentType.simulated("lotomation:movement", Movement.class);

    /** The input for the frame being stepped. */
    public static final ComponentType<PlayerInput> INPUT =
            ComponentType.simulated("lotomation:input", PlayerInput.class);

    /** Flying speed: 10 m/s. */
    static final long FLY_SPEED = Fixed.perFrame(10, 1);

    /** Flying speed when sprinting: 40 m/s. */
    static final long FLY_SPRINT_SPEED = Fixed.perFrame(40, 1);

    /** One over the square root of two, so diagonal moves are no faster than straight ones. */
    static final long DIAGONAL = 46_341;

    /** Entities that take input and move. */
    private static final Query DRIVEN = Query.of(MOVEMENT, INPUT);

    /** Not made. */
    private PlayerSimulation() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }

    /**
     * The systems, in the order they run each frame.
     *
     * @return A new schedule.
     */
    public static Schedule schedule() {
        return new Schedule().add("move", PlayerSimulation::move);
    }

    /**
     * Step everything that takes input by its input.
     *
     * @param world The world.
     */
    private static void move(World world) {
        world.each(
                DRIVEN,
                entity ->
                        world.set(
                                MOVEMENT,
                                entity,
                                step(world.get(MOVEMENT, entity), world.get(INPUT, entity))));
    }

    /**
     * One frame of movement.
     *
     * @param before The movement after the previous frame.
     * @param input This frame's input.
     * @return The movement after this frame.
     */
    public static Movement step(@NonNull Movement before, @NonNull PlayerInput input) {
        // Walking comes with collision; until then everything flies
        return fly(before, input);
    }

    /**
     * One frame of flying: move at a steady speed where the keys point, turned by the yaw, and
     * straight up or down. The pitch only sets where the player looks.
     *
     * @param before The movement after the previous frame.
     * @param input This frame's input.
     * @return The movement after this frame.
     */
    static Movement fly(Movement before, PlayerInput input) {
        final long speed = input.held(PlayerInput.SPRINT) ? FLY_SPRINT_SPEED : FLY_SPEED;
        final long forward = axis(input, PlayerInput.FORWARD, PlayerInput.BACK);
        final long right = axis(input, PlayerInput.RIGHT, PlayerInput.LEFT);
        final long up = axis(input, PlayerInput.UP, PlayerInput.DOWN);
        final long sin = Angle.sin(input.yaw());
        final long cos = Angle.cos(input.yaw());
        // Forward is (sin, -cos) and right is (cos, sin) in x and z, as fractions of one
        long dirX = forward * sin + right * cos;
        long dirZ = -forward * cos + right * sin;
        if (forward != 0 && right != 0) {
            dirX = Fixed.mul(dirX, DIAGONAL);
            dirZ = Fixed.mul(dirZ, DIAGONAL);
        }
        final long vx = Fixed.mul(speed, dirX);
        final long vy = up * speed;
        final long vz = Fixed.mul(speed, dirZ);
        return new Movement(
                MoveMode.FLYING,
                before.x() + vx,
                before.y() + vy,
                before.z() + vz,
                vx,
                vy,
                vz,
                input.yaw(),
                input.pitch(),
                false);
    }

    /**
     * One axis of the keys.
     *
     * @param input The input.
     * @param plus The button for the positive way.
     * @param minus The button for the negative way.
     * @return 1, -1, or 0 when both or neither are held.
     */
    private static long axis(PlayerInput input, int plus, int minus) {
        return (input.held(plus) ? 1 : 0) - (input.held(minus) ? 1 : 0);
    }
}
