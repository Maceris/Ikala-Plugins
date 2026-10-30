package com.ikalagaming.graphics.gizmo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

/** Tests for {@link GizmoMath}. */
class GizmoMathTest {
    /** How close floats must be. */
    private static final float EPSILON = 1e-4f;

    /** A ray crossing a line finds where on the line it passes closest. */
    @Test
    void testClosestOnLine() {
        Vector3f origin = new Vector3f(2, 0, 5);
        Vector3f down = new Vector3f(0, 0, -1);
        assertEquals(
                2,
                GizmoMath.closestOnLine(origin, down, new Vector3f(), new Vector3f(1, 0, 0)),
                EPSILON);
        // Along the line, there is no closest point
        assertTrue(
                Float.isNaN(
                        GizmoMath.closestOnLine(
                                origin, down, new Vector3f(), new Vector3f(0, 0, 1))));
        // Behind the ray's start
        assertTrue(
                Float.isNaN(
                        GizmoMath.closestOnLine(
                                origin,
                                new Vector3f(0, 0, 1),
                                new Vector3f(),
                                new Vector3f(1, 0, 0))));
    }

    /** Rays meet planes in front of them only. */
    @Test
    void testHitPlane() {
        Vector3f out = new Vector3f();
        assertTrue(
                GizmoMath.hitPlane(
                        new Vector3f(1, 5, 2),
                        new Vector3f(0, -1, 0),
                        new Vector3f(),
                        new Vector3f(0, 1, 0),
                        out));
        assertEquals(new Vector3f(1, 0, 2), out);
        assertFalse(
                GizmoMath.hitPlane(
                        new Vector3f(1, 5, 2),
                        new Vector3f(0, 1, 0),
                        new Vector3f(),
                        new Vector3f(0, 1, 0),
                        out));
        assertFalse(
                GizmoMath.hitPlane(
                        new Vector3f(1, 5, 2),
                        new Vector3f(1, 0, 0),
                        new Vector3f(),
                        new Vector3f(0, 1, 0),
                        out));
    }

    /** Angles about an axis follow the right-hand rule. */
    @Test
    void testSignedAngle() {
        Vector3f up = new Vector3f(0, 0, 1);
        assertEquals(
                Math.PI / 2,
                GizmoMath.signedAngle(new Vector3f(1, 0, 0), new Vector3f(0, 1, 0), up),
                EPSILON);
        assertEquals(
                -Math.PI / 2,
                GizmoMath.signedAngle(new Vector3f(0, 1, 0), new Vector3f(1, 0, 0), up),
                EPSILON);
    }

    /** Screen distances to segments clamp to the ends, and quads hold their inside. */
    @Test
    void testScreenShapes() {
        assertEquals(3, GizmoMath.distanceToSegment(5, 3, 0, 0, 10, 0), EPSILON);
        assertEquals(5, GizmoMath.distanceToSegment(13, 4, 0, 0, 10, 0), EPSILON);
        float[] square = {0, 0, 10, 0, 10, 10, 0, 10};
        assertTrue(GizmoMath.insideQuad(5, 5, square));
        assertFalse(GizmoMath.insideQuad(15, 5, square));
        float[] backwards = {0, 0, 0, 10, 10, 10, 10, 0};
        assertTrue(GizmoMath.insideQuad(5, 5, backwards));
    }

    /** Snapping rounds to the nearest step, and no step leaves the value. */
    @Test
    void testSnap() {
        assertEquals(0.25f, GizmoMath.snap(0.3f, 0.25f), EPSILON);
        assertEquals(0.3f, GizmoMath.snap(0.3f, 0), EPSILON);
    }
}
