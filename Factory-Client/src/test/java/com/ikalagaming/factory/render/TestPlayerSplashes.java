package com.ikalagaming.factory.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.factory.sim.Fixed;
import com.ikalagaming.factory.sim.MoveMode;
import com.ikalagaming.factory.sim.Movement;

import org.junit.jupiter.api.Test;

import java.util.List;

/** Tests for the player's splashes and ripples. */
class TestPlayerSplashes {

    /** Water below y = 0. */
    private static final PlayerSplashes.WaterAt SEA = (x, y, z) -> y < 0;

    /**
     * A movement.
     *
     * @param y The feet's height, in meters.
     * @param vx Speed along x, in fixed point per frame.
     * @param vy Speed up, in fixed point per frame.
     * @return The movement.
     */
    private static Movement at(double y, long vx, long vy) {
        return new Movement(
                MoveMode.WALKING,
                Fixed.ONE / 2,
                Math.round(y * Fixed.ONE),
                Fixed.ONE / 2,
                vx,
                vy,
                0,
                0,
                0,
                false);
    }

    /** Falling in splashes at the surface, harder the faster; standing still makes nothing. */
    @Test
    void testEntry() {
        final PlayerSplashes splashes = new PlayerSplashes();
        final long slow = -Fixed.perFrame(2, 1);
        final long fast = -Fixed.perFrame(20, 1);
        final List<PlayerSplashes.Splash> gentle =
                splashes.step(at(0.1, 0, slow), at(-0.2, 0, slow), SEA);
        assertEquals(1, gentle.size());
        assertEquals(0, gentle.get(0).position().y());
        final List<PlayerSplashes.Splash> hard =
                new PlayerSplashes().step(at(0.1, 0, fast), at(-0.2, 0, fast), SEA);
        assertTrue(hard.get(0).strength() > gentle.get(0).strength());
        assertTrue(hard.get(0).strength() <= PlayerSplashes.ENTRY_MAX);
        assertTrue(splashes.step(at(-1, 0, 0), at(-1, 0, 0), SEA).isEmpty());
    }

    /** Wading leaves ripples every few frames at the surface; deep under, none. */
    @Test
    void testWake() {
        final PlayerSplashes splashes = new PlayerSplashes();
        final long walking = Fixed.perFrame(4, 1);
        int ripples = 0;
        for (int i = 0; i < 4 * PlayerSplashes.WAKE_FRAMES; ++i) {
            ripples += splashes.step(at(-1, walking, 0), at(-1, walking, 0), SEA).size();
        }
        assertEquals(4, ripples);
        final PlayerSplashes deep = new PlayerSplashes();
        for (int i = 0; i < 4 * PlayerSplashes.WAKE_FRAMES; ++i) {
            assertTrue(deep.step(at(-5, walking, 0), at(-5, walking, 0), SEA).isEmpty());
        }
    }

    /** Climbing out makes a small splash where the player left the water. */
    @Test
    void testExit() {
        final List<PlayerSplashes.Splash> out =
                new PlayerSplashes()
                        .step(at(-0.5, 0, Fixed.ONE / 10), at(0.2, 0, Fixed.ONE / 10), SEA);
        assertEquals(1, out.size());
        assertEquals(PlayerSplashes.EXIT_STRENGTH, out.get(0).strength());
    }
}
