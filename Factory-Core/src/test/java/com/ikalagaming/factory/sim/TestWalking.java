package com.ikalagaming.factory.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.factory.world.Block;
import com.ikalagaming.factory.world.Chunk;
import com.ikalagaming.factory.world.ChunkBlockView;
import com.ikalagaming.factory.world.ChunkPos;
import com.ikalagaming.factory.world.World;

import org.junit.jupiter.api.Test;

import java.util.Map;

/** Tests for walking: gravity, jumping and the box against small built worlds. */
class TestWalking {

    /** Ground below y = 0. */
    private static final BlockView FLOOR = (x, y, z) -> y < 0;

    /** Standing on {@link #FLOOR} in the middle of a block. */
    private static final Movement STANDING =
            new Movement(MoveMode.WALKING, Fixed.ONE / 2, 0, Fixed.ONE / 2, 0, 0, 0, 0, 0, true);

    /** Walking forward, which is toward -z at yaw 0. */
    private static final PlayerInput FORWARD = new PlayerInput(PlayerInput.FORWARD, 0, 0);

    /** Holding jump. */
    private static final PlayerInput JUMP = new PlayerInput(PlayerInput.UP, 0, 0);

    /**
     * Step one input for a number of frames.
     *
     * @param start Where to start.
     * @param input The input, every frame.
     * @param frames How many frames.
     * @param blocks The blocks.
     * @return Where it ends.
     */
    private static Movement run(Movement start, PlayerInput input, int frames, BlockView blocks) {
        return run(start, input, frames, blocks, GameMode.SURVIVAL);
    }

    /**
     * Step one input for a number of frames in a game mode.
     *
     * @param start Where to start.
     * @param input The input, every frame.
     * @param frames How many frames.
     * @param blocks The blocks.
     * @param mode The game mode.
     * @return Where it ends.
     */
    private static Movement run(
            Movement start, PlayerInput input, int frames, BlockView blocks, GameMode mode) {
        Movement movement = start;
        for (int i = 0; i < frames; ++i) {
            movement = PlayerSimulation.step(movement, input, blocks, mode);
        }
        return movement;
    }

    /** Standing still on the ground stays exactly put. */
    @Test
    void testStanding() {
        final Movement after = run(STANDING, PlayerInput.NONE, 200, FLOOR);
        assertEquals(STANDING, after);
    }

    /** A fall from 10 m lands exactly on the floor, in about the time gravity says. */
    @Test
    void testFallOntoFloor() {
        Movement movement = Movement.at(MoveMode.WALKING, 0, Fixed.ofMeters(10), 0);
        int frames = 0;
        while (!movement.onGround() && frames < 200) {
            movement = PlayerSimulation.step(movement, PlayerInput.NONE, FLOOR, GameMode.SURVIVAL);
            ++frames;
        }
        assertTrue(movement.onGround());
        assertEquals(0, movement.y());
        assertEquals(0, movement.vy());
        // sqrt(2 * 10 / 25) is about 0.89 s, or 56 frames
        assertTrue(frames >= 54 && frames <= 60, "landed after " + frames + " frames");
    }

    /** Walking and sprinting reach their speeds, and letting go stops dead. */
    @Test
    void testSpeeds() {
        Movement movement = run(STANDING, FORWARD, 60, FLOOR);
        assertEquals(-PlayerSimulation.WALK_SPEED, movement.vz());
        assertEquals(0, movement.vx());
        movement =
                run(
                        movement,
                        new PlayerInput(PlayerInput.FORWARD | PlayerInput.SPRINT, 0, 0),
                        60,
                        FLOOR);
        assertEquals(-PlayerSimulation.SPRINT_SPEED, movement.vz());
        movement = run(movement, PlayerInput.NONE, 60, FLOOR);
        assertEquals(0, movement.vz());
        final Movement stopped = run(movement, PlayerInput.NONE, 10, FLOOR);
        assertEquals(movement.z(), stopped.z());
        assertTrue(movement.onGround());
    }

    /** A jump rises a little over 1.25 m and comes back down to the floor. */
    @Test
    void testJumpHeight() {
        Movement movement = PlayerSimulation.step(STANDING, JUMP, FLOOR, GameMode.SURVIVAL);
        long highest = movement.y();
        int frames = 1;
        while (!movement.onGround() && frames < 200) {
            movement = PlayerSimulation.step(movement, PlayerInput.NONE, FLOOR, GameMode.SURVIVAL);
            highest = Math.max(highest, movement.y());
            ++frames;
        }
        assertTrue(highest >= Fixed.ofMeters(125, 100), "rose " + highest);
        assertTrue(highest < Fixed.ofMeters(140, 100), "rose " + highest);
        assertEquals(0, movement.y());
    }

    /** Walking into a wall stops with the box against its face. */
    @Test
    void testWall() {
        final BlockView walled = (x, y, z) -> y < 0 || z == -3;
        final Movement after = run(STANDING, FORWARD, 120, walled);
        assertEquals(-2 * Fixed.ONE + PlayerSimulation.HALF_WIDTH, after.z());
        assertEquals(0, after.vz());
        assertEquals(0, after.y());
        assertTrue(after.onGround());
    }

    /** Walking and jumping at a step one block high ends up on top of it. */
    @Test
    void testJumpOntoBlock() {
        final BlockView step = (x, y, z) -> y < 0 || (y == 0 && z <= -2);
        final Movement after =
                run(
                        STANDING,
                        new PlayerInput(PlayerInput.FORWARD | PlayerInput.UP, 0, 0),
                        90,
                        step);
        assertTrue(after.z() < -Fixed.ONE, "didn't get past the step's edge: " + after.z());
        assertTrue(after.y() >= Fixed.ONE, "not on top: " + after.y());
        final Movement landed = run(after, PlayerInput.NONE, 60, step);
        assertEquals(Fixed.ONE, landed.y());
        assertTrue(landed.onGround());

        // Without jumping, the step is a wall
        final Movement blocked = run(STANDING, FORWARD, 90, step);
        assertEquals(0, blocked.y());
        assertEquals(-Fixed.ONE + PlayerSimulation.HALF_WIDTH, blocked.z());
    }

    /** A ceiling stops a jump at the head, and the fall starts from there. */
    @Test
    void testCeiling() {
        final BlockView low = (x, y, z) -> y < 0 || y == 2;
        Movement movement = PlayerSimulation.step(STANDING, JUMP, low, GameMode.SURVIVAL);
        long highest = movement.y();
        boolean bumped = false;
        for (int i = 0; i < 60; ++i) {
            final Movement next =
                    PlayerSimulation.step(movement, PlayerInput.NONE, low, GameMode.SURVIVAL);
            bumped |= movement.vy() > 0 && next.vy() == 0 && !next.onGround();
            movement = next;
            highest = Math.max(highest, movement.y());
        }
        assertEquals(2 * Fixed.ONE - PlayerSimulation.HEIGHT, highest);
        assertTrue(bumped, "the head never hit the ceiling");
        assertEquals(0, movement.y());
    }

    /** At the edge of the loaded chunks, missing ones are solid: no walking off, no falling in. */
    @Test
    void testMissingChunkEdge() {
        final Block stone = new Block("t:stone", null);
        final Chunk ground = new Chunk(stone, "t:plains");
        final Chunk air = new Chunk(World.AIR, "t:plains");
        final Map<ChunkPos, Chunk> loaded =
                Map.of(new ChunkPos(0, -1, 0), ground, new ChunkPos(0, 0, 0), air);
        final ChunkBlockView view =
                new ChunkBlockView(loaded::get, Map.of(World.AIR_NAME, false, "t:stone", true));
        final Movement start =
                new Movement(
                        MoveMode.WALKING,
                        Fixed.ofMeters(8),
                        0,
                        Fixed.ofMeters(8),
                        0,
                        0,
                        0,
                        0,
                        0,
                        true);
        final Movement after = run(start, FORWARD, 300, view);
        assertEquals(PlayerSimulation.HALF_WIDTH, after.z());
        assertEquals(0, after.y());
        assertTrue(after.onGround());
        // Blocks the definitions don't name are solid, so a chunk of them is a wall
        final ChunkBlockView unknown =
                new ChunkBlockView(
                        Map.of(new ChunkPos(0, 0, 0), new Chunk(stone, "t:plains"))::get,
                        Map.of(World.AIR_NAME, false));
        assertTrue(unknown.solid(3, 3, 3));
    }

    /** Spectators fly through everything; back in survival they fall from rest. */
    @Test
    void testSpectator() {
        final PlayerInput up = new PlayerInput(PlayerInput.UP, 0, 0);
        Movement movement = run(STANDING, up, 10, BlockView.SOLID, GameMode.SPECTATOR);
        assertEquals(MoveMode.FLYING, movement.mode());
        assertEquals(10 * PlayerSimulation.FLY_SPEED, movement.y());
        movement = run(movement, PlayerInput.NONE, 1, FLOOR);
        assertEquals(MoveMode.WALKING, movement.mode());
        assertEquals(-PlayerSimulation.GRAVITY, movement.vy());
        assertFalse(movement.onGround());
        movement = run(movement, PlayerInput.NONE, 120, FLOOR);
        assertEquals(0, movement.y());
        assertTrue(movement.onGround());
    }

    /** Survival ignores the flight button. */
    @Test
    void testSurvivalCantFly() {
        final PlayerInput flyUp = new PlayerInput(PlayerInput.FLY | PlayerInput.UP, 0, 0);
        final Movement movement = run(STANDING, flyUp, 1, FLOOR);
        assertEquals(MoveMode.WALKING, movement.mode());
        assertEquals(PlayerSimulation.JUMP_SPEED - PlayerSimulation.GRAVITY, movement.vy());
    }

    /** Creative flight hovers, rises, stops at ceilings and walls, and lands on the floor. */
    @Test
    void testCreativeFlight() {
        final BlockView room = (x, y, z) -> y < 0 || y >= 4 || z == -3;
        final int fly = PlayerInput.FLY;
        Movement movement =
                run(
                        STANDING,
                        new PlayerInput(fly | PlayerInput.UP, 0, 0),
                        60,
                        room,
                        GameMode.CREATIVE);
        assertEquals(MoveMode.FLYING, movement.mode());
        // Up to the ceiling at y = 4, the head stopping there
        assertEquals(4 * Fixed.ONE - PlayerSimulation.HEIGHT, movement.y());
        // Hovering: no input, no fall
        final Movement hovering =
                run(movement, new PlayerInput(fly, 0, 0), 60, room, GameMode.CREATIVE);
        assertEquals(movement.y(), hovering.y());
        assertEquals(0, hovering.vy());
        // Forward into the wall
        final Movement walled =
                run(
                        hovering,
                        new PlayerInput(fly | PlayerInput.FORWARD, 0, 0),
                        60,
                        room,
                        GameMode.CREATIVE);
        assertEquals(-2 * Fixed.ONE + PlayerSimulation.HALF_WIDTH, walled.z());
        // Down onto the floor, which marks it on the ground
        final Movement landed =
                run(
                        walled,
                        new PlayerInput(fly | PlayerInput.DOWN, 0, 0),
                        60,
                        room,
                        GameMode.CREATIVE);
        assertEquals(0, landed.y());
        assertTrue(landed.onGround());
        // Without the flight button, creative walks
        final Movement walking = run(landed, PlayerInput.NONE, 1, room, GameMode.CREATIVE);
        assertEquals(MoveMode.WALKING, walking.mode());
    }

    /** Game modes read by name, letter and number. */
    @Test
    void testGameModeNames() {
        assertEquals(GameMode.CREATIVE, GameMode.named("creative").orElseThrow());
        assertEquals(GameMode.SPECTATOR, GameMode.named(" SP").orElseThrow());
        assertEquals(GameMode.SURVIVAL, GameMode.named("0").orElseThrow());
        assertEquals(GameMode.SURVIVAL, GameMode.named("s").orElseThrow());
        assertTrue(GameMode.named("adventure").isEmpty());
    }

    /** Diagonal walking is no faster than straight walking, at any yaw. */
    @Test
    void testDiagonal() {
        for (int yaw = 0; yaw < Angle.TURN; yaw += 1009) {
            final Movement after =
                    run(
                            STANDING,
                            new PlayerInput(PlayerInput.FORWARD | PlayerInput.RIGHT, yaw, 0),
                            60,
                            FLOOR);
            final long speed = Fixed.length(after.vx(), 0, after.vz());
            assertTrue(
                    Math.abs(speed - PlayerSimulation.WALK_SPEED) <= 2,
                    "yaw " + yaw + ": " + speed);
        }
    }
}
