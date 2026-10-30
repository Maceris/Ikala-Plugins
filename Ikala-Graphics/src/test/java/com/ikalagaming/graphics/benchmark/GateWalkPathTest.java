package com.ikalagaming.graphics.benchmark;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/** The gate's walk along a path of waypoints. */
class GateWalkPathTest {

    /** How close offsets must be. */
    private static final double EPSILON = 1e-9;

    /** Waypoints are read in order, and a bad one drops the whole path. */
    @Test
    void testParse() {
        assertArrayEquals(
                new double[] {300, 0, 0, -50.5},
                GateBenchmark.parseWalkPath("300,0; 0,-50.5"),
                EPSILON);
        assertNull(GateBenchmark.parseWalkPath(null));
        assertNull(GateBenchmark.parseWalkPath("300"));
        assertNull(GateBenchmark.parseWalkPath("a,b"));
    }

    /** The walk follows each leg in turn and stands at the last waypoint. */
    @Test
    void testOffsets() {
        final double[] path = {30, 0, 30, 40, 0, 0};
        assertArrayEquals(new double[] {0, 0}, GateBenchmark.walkOffset(path, 0), EPSILON);
        assertArrayEquals(new double[] {15, 0}, GateBenchmark.walkOffset(path, 15), EPSILON);
        assertArrayEquals(new double[] {30, 10}, GateBenchmark.walkOffset(path, 40), EPSILON);
        // The third leg runs back from 30,40 to the start, 50 long
        assertArrayEquals(new double[] {15, 20}, GateBenchmark.walkOffset(path, 95), EPSILON);
        assertArrayEquals(new double[] {0, 0}, GateBenchmark.walkOffset(path, 1000), EPSILON);
    }
}
