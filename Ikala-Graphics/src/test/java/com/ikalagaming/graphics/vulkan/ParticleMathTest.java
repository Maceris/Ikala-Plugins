package com.ikalagaming.graphics.vulkan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.joml.Vector3d;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

/** The math of GPU particles, against cases with known answers. */
class ParticleMathTest {

    /** Spawns keep their fractions, so a rate is met over many uneven frames. */
    @Test
    void testSpawnsKeepTheirFractions() {
        final double[] carry = {0};
        int total = 0;
        final double rate = 37.5;
        double seconds = 0;
        for (int frame = 0; frame < 1000; ++frame) {
            final double step = frame % 3 == 0 ? 0.004 : 0.019;
            seconds += step;
            total += ParticleMath.spawns(rate, step, carry);
        }
        assertEquals(rate * seconds, total + carry[0], 1e-9);
        assertTrue(carry[0] >= 0 && carry[0] < 1);
        assertEquals(0, ParticleMath.spawns(0, 1, carry));
        assertEquals(0, ParticleMath.spawns(10, 0, carry));
    }

    /** Anchors are grid corners at or below the camera, the same within a cell. */
    @Test
    void testAnchors() {
        final Vector3d anchor = new Vector3d();
        ParticleMath.anchorFor(new Vector3d(10, -5, 2047.9), anchor);
        assertEquals(new Vector3d(0, -1024, 1024), anchor);
        final Vector3d other = new Vector3d();
        ParticleMath.anchorFor(new Vector3d(1000, -1, 1025), other);
        assertEquals(anchor, other);
        ParticleMath.anchorFor(new Vector3d(1e9 + 3, 0, 0), anchor);
        assertTrue(anchor.x <= 1e9 + 3 && 1e9 + 3 - anchor.x < ParticleMath.ANCHOR_GRID);
    }

    /** Without drag a particle flies a parabola. */
    @Test
    void testBallistic() {
        final Vector3f position = new Vector3f(1, 2, 3);
        final Vector3f velocity = new Vector3f(4, 10, 0);
        ParticleMath.advance(position, velocity, new Vector3f(0, -10, 0), 0, 2);
        assertEquals(9, position.x, 1e-5);
        assertEquals(2 + 20 - 20, position.y, 1e-5);
        assertEquals(-10, velocity.y, 1e-5);
    }

    /**
     * With drag, one long step lands where many short ones do, and the velocity settles at the
     * acceleration over the drag.
     */
    @Test
    void testDragInClosedForm() {
        final Vector3f gravity = new Vector3f(0, -9.81f, 0);
        final float drag = 1.5f;
        final Vector3f once = new Vector3f();
        final Vector3f onceVelocity = new Vector3f(3, 5, -1);
        ParticleMath.advance(once, onceVelocity, gravity, drag, 2);
        final Vector3f many = new Vector3f();
        final Vector3f manyVelocity = new Vector3f(3, 5, -1);
        for (int i = 0; i < 200; ++i) {
            ParticleMath.advance(many, manyVelocity, gravity, drag, 0.01f);
        }
        assertEquals(once.x, many.x, 1e-3);
        assertEquals(once.y, many.y, 1e-3);
        assertEquals(onceVelocity.y, manyVelocity.y, 1e-3);
        final Vector3f settled = new Vector3f();
        final Vector3f settledVelocity = new Vector3f(3, 5, -1);
        ParticleMath.advance(settled, settledVelocity, gravity, drag, 60);
        assertEquals(-9.81f / drag, settledVelocity.y, 1e-4);
        assertEquals(0, settledVelocity.x, 1e-4);
    }

    /** Cone directions are unit length and within the cone, and cover it around the axis. */
    @Test
    void testConeDirections() {
        final Vector3f axis = new Vector3f(1, 2, -0.5f).normalize();
        final float cosSpread = (float) Math.cos(0.4);
        final Vector3f direction = new Vector3f();
        final Vector3f sum = new Vector3f();
        final int steps = 32;
        for (int i = 0; i < steps; ++i) {
            for (int j = 0; j < steps; ++j) {
                ParticleMath.coneDirection(
                        axis, cosSpread, (i + 0.5f) / steps, (j + 0.5f) / steps, direction);
                assertEquals(1, direction.length(), 1e-5);
                assertTrue(direction.dot(axis) >= cosSpread - 1e-5);
                sum.add(direction);
            }
        }
        // Spread evenly around it, the directions average along the axis
        assertEquals(1, sum.normalize().dot(axis), 1e-4);
        ParticleMath.coneDirection(axis, 1, 0.7f, 0.3f, direction);
        assertEquals(1, direction.dot(axis), 1e-5);
        ParticleMath.coneDirection(new Vector3f(0, 1, 0), -1, 1, 0, direction);
        assertEquals(-1, direction.y, 1e-5);
    }

    /** Life runs from 0 to 1, and the round shape and soft fade stay in range. */
    @Test
    void testShapeAndFades() {
        assertEquals(0.5f, ParticleMath.lifeFraction(1, 2));
        assertEquals(1, ParticleMath.lifeFraction(3, 2));
        assertEquals(1, ParticleMath.lifeFraction(0, 0));
        assertEquals(1, ParticleMath.roundShape(0, 0));
        assertEquals(0, ParticleMath.roundShape(1, 0));
        assertEquals(0, ParticleMath.roundShape(0.8f, 0.8f));
        assertTrue(ParticleMath.roundShape(0.3f, 0) > ParticleMath.roundShape(0.6f, 0));
        assertEquals(0, ParticleMath.softFade(10, 10, 0.5f));
        assertEquals(0.5f, ParticleMath.softFade(10, 10.25f, 0.5f), 1e-6);
        assertEquals(1, ParticleMath.softFade(10, 20, 0.5f));
        assertEquals(0, ParticleMath.softFade(10, 9, 0.5f));
        assertEquals(1, ParticleMath.softFade(9, 10, 0));
    }
}
