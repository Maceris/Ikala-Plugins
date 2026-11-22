package com.ikalagaming.factory.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.SplittableRandom;

/** Tests for the fixed-point math, checked against exact or floating point references. */
class TestFixedMath {

    /** Sines and cosines are within one unit of the true value, and exact at the quarters. */
    @Test
    void testSine() {
        int worst = 0;
        for (int a = 0; a < Angle.TURN; ++a) {
            final double radians = a * 2 * Math.PI / Angle.TURN;
            final long expectSin = Math.round(StrictMath.sin(radians) * Fixed.ONE);
            final long expectCos = Math.round(StrictMath.cos(radians) * Fixed.ONE);
            worst = (int) Math.max(worst, Math.abs(Angle.sin(a) - expectSin));
            worst = (int) Math.max(worst, Math.abs(Angle.cos(a) - expectCos));
        }
        assertTrue(worst <= 1, "off by " + worst + " units");
        assertEquals(0, Angle.sin(0));
        assertEquals(Fixed.ONE, Angle.sin(Angle.QUARTER));
        assertEquals(0, Angle.sin(Angle.HALF));
        assertEquals(-Fixed.ONE, Angle.sin(Angle.HALF + Angle.QUARTER));
        assertEquals(Fixed.ONE, Angle.cos(0));
        assertEquals(Angle.sin(5), Angle.sin(5 + 3L * Angle.TURN));
        assertEquals(Angle.sin(-5), -Angle.sin(5));
    }

    /** The integer square root matches the exact one. */
    @Test
    void testSqrt() {
        for (long v = 0; v < 100_000; ++v) {
            assertEquals(BigInteger.valueOf(v).sqrt().longValue(), Fixed.sqrt(v), "sqrt " + v);
        }
        final SplittableRandom random = new SplittableRandom(7);
        for (int i = 0; i < 100_000; ++i) {
            final long v = random.nextLong(Long.MAX_VALUE);
            assertEquals(BigInteger.valueOf(v).sqrt().longValue(), Fixed.sqrt(v), "sqrt " + v);
        }
        assertEquals(3037000499L, Fixed.sqrt(Long.MAX_VALUE));
        assertEquals(5 * Fixed.ONE, Fixed.length(3 * Fixed.ONE, 0, 4 * Fixed.ONE));
        assertThrows(IllegalArgumentException.class, () -> Fixed.sqrt(-1));
    }

    /** Conversions from meters per second, and blocks, round as documented. */
    @Test
    void testUnits() {
        // 4.3 m/s for 16 ms is 0.0688 m, 4508.88 units
        assertEquals(4509, Fixed.perFrame(43, 10));
        // 25 m/s^2 over 16 ms squared is 0.0064 m, 419.43 units
        assertEquals(419, Fixed.perFrameSquared(25, 1));
        assertEquals(Fixed.ONE / 2, Fixed.ofMeters(1, 2));
        assertEquals(3 * Fixed.ONE, Fixed.ofMeters(3));
        assertEquals(-1, Fixed.block(-1));
        assertEquals(0, Fixed.block(Fixed.ONE - 1));
        assertEquals(1, Fixed.block(Fixed.ONE));
        assertEquals(-Fixed.ONE / 2, Fixed.mul(-Fixed.ONE, Fixed.ONE / 2));
        assertEquals(-1, Fixed.mul(-1, Fixed.ONE / 2));
        assertEquals(62, Frames.ofMillis(1000));
    }
}
