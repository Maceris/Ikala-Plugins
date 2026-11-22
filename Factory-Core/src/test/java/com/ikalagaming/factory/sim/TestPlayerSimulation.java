package com.ikalagaming.factory.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.ecs.Schedule;
import com.ikalagaming.ecs.Snapshot;
import com.ikalagaming.ecs.World;
import com.ikalagaming.factory.networking.request.clientbound.PlayerState;
import com.ikalagaming.factory.networking.request.serverbound.PlayerInputs;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/** Tests for the player simulation and the messages that carry it. */
class TestPlayerSimulation {

    /** Standing at the origin, flying. */
    private static final Movement ORIGIN = Movement.at(MoveMode.FLYING, 0, 0, 0);

    /**
     * Random inputs.
     *
     * @param seed The seed.
     * @param count How many.
     * @return The inputs.
     */
    static List<PlayerInput> randomInputs(long seed, int count) {
        final SplittableRandom random = new SplittableRandom(seed);
        final List<PlayerInput> out = new ArrayList<>();
        PlayerInput current = PlayerInput.NONE;
        for (int i = 0; i < count; ++i) {
            // Inputs change now and then, as a player's would
            if (random.nextInt(8) == 0) {
                current =
                        new PlayerInput(
                                random.nextInt(PlayerInput.ALL_BUTTONS + 1),
                                random.nextInt(Angle.TURN),
                                random.nextInt(-Angle.QUARTER, Angle.QUARTER + 1));
            }
            out.add(current);
        }
        return out;
    }

    /**
     * Run inputs through a fresh world.
     *
     * @param inputs The inputs.
     * @return The snapshot after each frame.
     */
    private static List<Snapshot> run(List<PlayerInput> inputs) {
        final World world = new World();
        final Schedule schedule = PlayerSimulation.schedule();
        final long player = world.create();
        world.set(PlayerSimulation.MOVEMENT, player, ORIGIN);
        final List<Snapshot> out = new ArrayList<>();
        for (PlayerInput input : inputs) {
            world.set(PlayerSimulation.INPUT, player, input);
            schedule.run(world);
            out.add(world.snapshot());
        }
        return out;
    }

    /**
     * Yaw 0 flies toward -z and right toward +x, matching the camera; a quarter turn turns both.
     */
    @Test
    void testDirections() {
        final long speed = PlayerSimulation.FLY_SPEED;
        Movement after = PlayerSimulation.step(ORIGIN, new PlayerInput(PlayerInput.FORWARD, 0, 0));
        assertEquals(0, after.x());
        assertEquals(-speed, after.z());
        after = PlayerSimulation.step(ORIGIN, new PlayerInput(PlayerInput.RIGHT, 0, 0));
        assertEquals(speed, after.x());
        assertEquals(0, after.z());
        after =
                PlayerSimulation.step(
                        ORIGIN, new PlayerInput(PlayerInput.FORWARD, Angle.QUARTER, 0));
        assertEquals(speed, after.x());
        assertEquals(0, after.z());
        after = PlayerSimulation.step(ORIGIN, new PlayerInput(PlayerInput.UP, 0, 0));
        assertEquals(speed, after.y());
        after =
                PlayerSimulation.step(
                        ORIGIN, new PlayerInput(PlayerInput.UP | PlayerInput.DOWN, 0, 0));
        assertEquals(0, after.y());
        after =
                PlayerSimulation.step(
                        ORIGIN, new PlayerInput(PlayerInput.BACK | PlayerInput.SPRINT, 0, 0));
        assertEquals(PlayerSimulation.FLY_SPRINT_SPEED, after.z());
    }

    /** Diagonal moves are no faster than straight ones, at any yaw. */
    @Test
    void testDiagonal() {
        final long speed = PlayerSimulation.FLY_SPEED;
        for (int yaw = 0; yaw < Angle.TURN; yaw += 997) {
            final Movement after =
                    PlayerSimulation.step(
                            ORIGIN,
                            new PlayerInput(PlayerInput.FORWARD | PlayerInput.LEFT, yaw, 0));
            final long length = Fixed.length(after.x(), 0, after.z());
            assertTrue(Math.abs(length - speed) <= 2, "yaw " + yaw + ": " + length);
        }
    }

    /** The same inputs give exactly the same states, run after run. */
    @Test
    void testDeterminism() {
        final List<PlayerInput> inputs = randomInputs(11, 500);
        final List<Snapshot> a = run(inputs);
        final List<Snapshot> b = run(inputs);
        assertEquals(a, b);
        assertTrue(!a.get(a.size() - 1).equals(a.get(0)), "the player never moved in the test run");
    }

    /** Inputs survive the message's run-length coding, and bad messages give nothing. */
    @Test
    void testInputsMessage() {
        final List<PlayerInput> inputs = randomInputs(3, PlayerInputs.MAX_FRAMES);
        final PlayerInputs message = PlayerInputs.of(1000, inputs);
        assertEquals(inputs, message.inputs());
        assertTrue(message.getCounts().length < inputs.size(), "nothing was joined");
        final PlayerInputs held = PlayerInputs.of(5, List.of(PlayerInput.NONE, PlayerInput.NONE));
        assertEquals(1, held.getCounts().length);

        message.setCounts(new int[] {PlayerInputs.MAX_FRAMES + 1});
        message.setButtons(new int[] {0});
        message.setYaws(new int[] {0});
        message.setPitches(new int[] {0});
        assertTrue(message.inputs().isEmpty());
        message.setCounts(new int[] {0});
        assertTrue(message.inputs().isEmpty());
        message.setYaws(null);
        assertTrue(message.inputs().isEmpty());
    }

    /** A state message gives back the movement it was made from. */
    @Test
    void testStateMessage() {
        final Movement movement =
                new Movement(MoveMode.WALKING, 1, -2, 3, 4, -5, 6, 700, -800, true);
        final PlayerState state = PlayerState.of(42, movement, 2, 9);
        assertEquals(movement, state.movement());
        assertEquals(42, state.getFrame());
    }

    /** Out-of-range inputs are brought into range. */
    @Test
    void testSanitized() {
        assertSame(PlayerInput.NONE, PlayerInput.NONE.sanitized());
        final PlayerInput wild = new PlayerInput(-1, Angle.TURN + 5, Angle.TURN);
        assertEquals(new PlayerInput(PlayerInput.ALL_BUTTONS, 5, Angle.QUARTER), wild.sanitized());
    }
}
