package com.ikalagaming.random;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests for the {@link SeededRandom seeded generator}. The pinned values must never change, since
 * saved and networked rolls depend on them.
 *
 * @author Ches Burks
 */
class TestSeededRandom {

    /** The raw output matches the SplitMix64 reference vector for seed 1234567. */
    @Test
    void testReferenceVector() {
        SeededRandom random = new SeededRandom(1_234_567);
        String[] expected = {
            "6457827717110365317",
            "3203168211198807973",
            "9817491932198370423",
            "4593380528125082431",
            "16408922859458223821"
        };
        for (String value : expected) {
            Assertions.assertEquals(Long.parseUnsignedLong(value), random.nextLong());
        }
    }

    /** The derived and bounded outputs stay what they were when this was written. */
    @Test
    void testPinnedValues() {
        SeededRandom random = new SeededRandom(42);
        Assertions.assertEquals(PINNED_INT, random.nextInt(1000));
        Assertions.assertEquals(PINNED_RANGE, random.nextInt(-50, 50));
        Assertions.assertEquals(PINNED_DOUBLE, random.nextDouble());
        Assertions.assertEquals(PINNED_FLOAT, random.nextFloat());
        Assertions.assertEquals(PINNED_WEIGHTED, random.selectWeighted(new double[] {1, 2, 3, 4}));
        Assertions.assertEquals(PINNED_DERIVED, random.derive("sliders.height").nextLong());
    }

    /** Derived generators depend only on the seed and the key. */
    @Test
    void testDeriveIndependent() {
        SeededRandom first = new SeededRandom(7);
        SeededRandom second = new SeededRandom(7);
        second.nextLong();
        second.nextInt(10);

        Assertions.assertEquals(first.derive("head").nextLong(), second.derive("head").nextLong());
        Assertions.assertNotEquals(
                first.derive("head").nextLong(), first.derive("hands").nextLong());
        Assertions.assertNotEquals(
                first.derive("head").nextLong(), new SeededRandom(8).derive("head").nextLong());
    }

    /** Bounded values stay in their ranges, including ranges wider than an int's positive half. */
    @Test
    void testBounds() {
        SeededRandom random = new SeededRandom(99);
        for (int i = 0; i < 10_000; ++i) {
            int value = random.nextInt(7);
            Assertions.assertTrue(value >= 0 && value < 7);
            int power = random.nextInt(16);
            Assertions.assertTrue(power >= 0 && power < 16);
            int wide = random.nextInt(Integer.MIN_VALUE + 5, Integer.MAX_VALUE - 5);
            Assertions.assertTrue(wide >= Integer.MIN_VALUE + 5 && wide < Integer.MAX_VALUE - 5);
            double real = random.nextDouble(-1, 1);
            Assertions.assertTrue(real >= -1 && real < 1);
            float single = random.nextFloat();
            Assertions.assertTrue(single >= 0 && single < 1);
        }
        Assertions.assertThrows(IllegalArgumentException.class, () -> random.nextInt(0));
        Assertions.assertThrows(IllegalArgumentException.class, () -> random.nextInt(3, 3));
        Assertions.assertEquals(2.5, random.nextDouble(2.5, 2.5));
    }

    /** Every value of a small bound comes up about equally often. */
    @Test
    void testUniform() {
        SeededRandom random = new SeededRandom(5);
        int[] counts = new int[6];
        final int samples = 60_000;
        for (int i = 0; i < samples; ++i) {
            ++counts[random.nextInt(6)];
        }
        for (int count : counts) {
            Assertions.assertEquals(samples / 6.0, count, samples * 0.01);
        }
    }

    /** Weighted picks follow the weights and skip unusable ones. */
    @Test
    void testSelectWeighted() {
        SeededRandom random = new SeededRandom(11);
        double[] weights = {1, 0, Double.NaN, -4, 3, Double.POSITIVE_INFINITY};
        int[] counts = new int[weights.length];
        final int samples = 40_000;
        for (int i = 0; i < samples; ++i) {
            ++counts[random.selectWeighted(weights)];
        }
        Assertions.assertEquals(0, counts[1] + counts[2] + counts[3] + counts[5]);
        Assertions.assertEquals(samples / 4.0, counts[0], samples * 0.01);
        Assertions.assertEquals(samples * 3 / 4.0, counts[4], samples * 0.01);

        Assertions.assertEquals(-1, random.selectWeighted(new double[] {0, -1, Double.NaN}));
        Assertions.assertEquals(-1, random.selectWeighted(new double[0]));
    }

    /** Chances of 0 and 1 are never and always. */
    @Test
    void testNextBoolean() {
        SeededRandom random = new SeededRandom(3);
        for (int i = 0; i < 1000; ++i) {
            Assertions.assertFalse(random.nextBoolean(0));
            Assertions.assertTrue(random.nextBoolean(1));
        }
    }

    private static final int PINNED_INT = 451;
    private static final int PINNED_RANGE = 3;
    private static final double PINNED_DOUBLE = 0.278_601_130_255_138_66;
    private static final float PINNED_FLOAT = 0.344_190_66f;
    private static final int PINNED_WEIGHTED = 0;
    private static final long PINNED_DERIVED = 8446548420544811449L;
}
