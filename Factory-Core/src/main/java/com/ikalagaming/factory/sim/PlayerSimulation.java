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

    /**
     * The blocks an entity moves through, which differ by player on the server since each has been
     * sent different chunks. Local: blocks aren't rolled back with the frames. An entity without
     * one moves as if everything were solid.
     */
    public static final ComponentType<BlockView> BLOCKS =
            ComponentType.local("lotomation:blocks", BlockView.class);

    /** Half the player's width: the box is 0.6 m across. */
    static final long HALF_WIDTH = Fixed.ofMeters(3, 10);

    /** The player's height: 1.8 m. */
    static final long HEIGHT = Fixed.ofMeters(18, 10);

    /** Walking speed: 4.3 m/s. */
    static final long WALK_SPEED = Fixed.perFrame(43, 10);

    /** Sprinting speed: 5.6 m/s. */
    static final long SPRINT_SPEED = Fixed.perFrame(56, 10);

    /** How much of the gap to the wanted speed closes each frame on the ground. */
    static final long GROUND_CONTROL = Fixed.ONE / 4;

    /** How much of the gap to the wanted speed closes each frame in the air. */
    static final long AIR_CONTROL = Fixed.ONE / 32;

    /** Gravity: 25 m/s^2. */
    static final long GRAVITY = Fixed.perFrameSquared(25, 1);

    /** The fastest fall: 60 m/s. */
    static final long TERMINAL_SPEED = Fixed.perFrame(60, 1);

    /** The speed a jump starts with: 8.3 m/s, which rises a little over 1.3 m. */
    static final long JUMP_SPEED = Fixed.perFrame(83, 10);

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
                entity -> {
                    final BlockView blocks =
                            world.has(BLOCKS, entity) ? world.get(BLOCKS, entity) : BlockView.SOLID;
                    world.set(
                            MOVEMENT,
                            entity,
                            step(world.get(MOVEMENT, entity), world.get(INPUT, entity), blocks));
                });
    }

    /**
     * One frame of movement: flying while the input holds {@link PlayerInput#FLY}, otherwise
     * walking.
     *
     * @param before The movement after the previous frame.
     * @param input This frame's input.
     * @param blocks The blocks to move through.
     * @return The movement after this frame.
     */
    public static Movement step(
            @NonNull Movement before, @NonNull PlayerInput input, @NonNull BlockView blocks) {
        return input.held(PlayerInput.FLY) ? fly(before, input) : walk(before, input, blocks);
    }

    /**
     * One frame of walking: speed up or slow down toward where the keys point, quickly on the
     * ground and slowly in the air; fall; jump from the ground while jump is held; then sweep the
     * box through the blocks, y first, then x, then z. Hitting something stops the speed along that
     * axis, and landing sets the on-ground flag.
     *
     * @param before The movement after the previous frame.
     * @param input This frame's input.
     * @param blocks The blocks.
     * @return The movement after this frame.
     */
    static Movement walk(Movement before, PlayerInput input, BlockView blocks) {
        final long speed = input.held(PlayerInput.SPRINT) ? SPRINT_SPEED : WALK_SPEED;
        final long[] wanted = direction(input, speed);
        final long control = before.onGround() ? GROUND_CONTROL : AIR_CONTROL;
        long vx = approach(before.vx(), wanted[0], control);
        long vz = approach(before.vz(), wanted[1], control);
        long vy = before.mode() == MoveMode.WALKING ? before.vy() : 0;
        if (before.onGround() && input.held(PlayerInput.UP)) {
            vy = JUMP_SPEED;
        }
        vy = Math.max(vy - GRAVITY, -TERMINAL_SPEED);

        final long[] min = {before.x() - HALF_WIDTH, before.y(), before.z() - HALF_WIDTH};
        final long[] max = {before.x() + HALF_WIDTH, before.y() + HEIGHT, before.z() + HALF_WIDTH};
        final long dy = slide(blocks, min, max, BoxSweep.Y, vy);
        final boolean onGround = vy < 0 && dy != vy;
        if (dy != vy) {
            vy = 0;
        }
        if (slide(blocks, min, max, BoxSweep.X, vx) != vx) {
            vx = 0;
        }
        if (slide(blocks, min, max, BoxSweep.Z, vz) != vz) {
            vz = 0;
        }
        return new Movement(
                MoveMode.WALKING,
                min[BoxSweep.X] + HALF_WIDTH,
                min[BoxSweep.Y],
                min[BoxSweep.Z] + HALF_WIDTH,
                vx,
                vy,
                vz,
                input.yaw(),
                input.pitch(),
                onGround);
    }

    /**
     * Move a box along an axis as far as the blocks let it.
     *
     * @param blocks The blocks.
     * @param min The box's low corner, moved.
     * @param max The box's high corner, moved.
     * @param axis The axis.
     * @param distance How far to try.
     * @return How far it went.
     */
    private static long slide(BlockView blocks, long[] min, long[] max, int axis, long distance) {
        final long moved = BoxSweep.move(blocks, min, max, axis, distance);
        min[axis] += moved;
        max[axis] += moved;
        return moved;
    }

    /**
     * A speed brought part of the way to a wanted one, or all the way once the step would round to
     * nothing.
     *
     * @param speed The speed now.
     * @param wanted The speed wanted.
     * @param control The share of the gap to close.
     * @return The new speed.
     */
    private static long approach(long speed, long wanted, long control) {
        final long step = Fixed.mul(wanted - speed, control);
        return step == 0 ? wanted : speed + step;
    }

    /**
     * Where the keys point, turned by the yaw, at a speed.
     *
     * @param input The input.
     * @param speed The speed.
     * @return The x and z of the velocity.
     */
    private static long[] direction(PlayerInput input, long speed) {
        final long forward = axis(input, PlayerInput.FORWARD, PlayerInput.BACK);
        final long right = axis(input, PlayerInput.RIGHT, PlayerInput.LEFT);
        final long sin = Angle.sin(input.yaw());
        final long cos = Angle.cos(input.yaw());
        // Forward is (sin, -cos) and right is (cos, sin) in x and z, as fractions of one
        long dirX = forward * sin + right * cos;
        long dirZ = -forward * cos + right * sin;
        if (forward != 0 && right != 0) {
            dirX = Fixed.mul(dirX, DIAGONAL);
            dirZ = Fixed.mul(dirZ, DIAGONAL);
        }
        return new long[] {Fixed.mul(speed, dirX), Fixed.mul(speed, dirZ)};
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
        final long[] flat = direction(input, speed);
        final long vx = flat[0];
        final long vy = axis(input, PlayerInput.UP, PlayerInput.DOWN) * speed;
        final long vz = flat[1];
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
