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

    /** A particle falling onto a floor bounces up, slowed along it, and goes back where it was. */
    @Test
    void testCollideWithAFloor() {
        final Vector3f previous = new Vector3f(2.5f, 1.1f, 0.5f);
        final Vector3f next = new Vector3f(2.7f, 0.9f, 0.5f);
        final Vector3f velocity = new Vector3f(1, -5, 0);
        final Vector3f normal = new Vector3f();
        final Vector3f at = ParticleMath.collide(previous, next, velocity, 1, 0.5f, 0.2f, normal);
        assertEquals(previous, at);
        assertEquals(new Vector3f(0, 1, 0), normal);
        assertEquals(0.8f, velocity.x, 1e-6);
        assertEquals(2.5f, velocity.y, 1e-6);
        assertEquals(0, velocity.z, 1e-6);
    }

    /**
     * Crossing two boundaries in a step, the face is the one crossed last, into the solid voxel.
     */
    @Test
    void testCollideWithTheLastFaceCrossed() {
        final Vector3f normal = new Vector3f();
        // Crosses x = 3 a fifth of the way, then y = 1 four fifths of the way: the floor
        final Vector3f velocity = new Vector3f(5, -5, 0);
        ParticleMath.collide(
                new Vector3f(2.9f, 1.4f, 0.5f),
                new Vector3f(3.4f, 0.9f, 0.5f),
                velocity,
                1,
                0,
                0,
                normal);
        assertEquals(new Vector3f(0, 1, 0), normal);
        assertEquals(5, velocity.x, 1e-6);
        assertEquals(0, velocity.y, 1e-6);
        // Crosses y = 1 a fifth of the way, then x = 3 four fifths of the way: a wall
        velocity.set(5, -5, 0);
        ParticleMath.collide(
                new Vector3f(2.6f, 1.1f, 0.5f),
                new Vector3f(3.1f, 0.6f, 0.5f),
                velocity,
                1,
                1,
                0,
                normal);
        assertEquals(new Vector3f(-1, 0, 0), normal);
        assertEquals(-5, velocity.x, 1e-6);
        assertEquals(-5, velocity.y, 1e-6);
        // Already inside, with nothing crossed: stopped
        velocity.set(1, 1, 1);
        ParticleMath.collide(new Vector3f(0.5f), new Vector3f(0.6f), velocity, 1, 1, 0, normal);
        assertEquals(new Vector3f(), velocity);
    }

    /** The behavior word keeps each part apart, to within a byte. */
    @Test
    void testBehaviorWord() {
        final int word = ParticleMath.packBehavior(-0.4f, true, 2, 0.3f, 0.75f, true);
        assertEquals(1, word >> ParticleMath.BEHAVIOR_VOLUMETRIC_SHIFT & 1);
        assertEquals(-0.4f, ParticleMath.anisotropyOf(word), 1f / ParticleMath.SIGNED_BYTE_MAX);
        assertEquals(1, word >> ParticleMath.BEHAVIOR_LIT_SHIFT & 1);
        assertEquals(
                2,
                word >> ParticleMath.BEHAVIOR_COLLISION_SHIFT
                        & ParticleMath.BEHAVIOR_COLLISION_MASK);
        assertEquals(
                Math.round(0.3f * 255),
                word >> ParticleMath.BEHAVIOR_BOUNCE_SHIFT & ParticleMath.UNSIGNED_BYTE_MAX);
        assertEquals(Math.round(0.75f * 255), word >>> ParticleMath.BEHAVIOR_FRICTION_SHIFT);
        final int unlit = ParticleMath.packBehavior(0.9f, false, 0, 0, 0, false);
        assertEquals(0, unlit >> ParticleMath.BEHAVIOR_VOLUMETRIC_SHIFT & 1);
        assertEquals(0, unlit >> ParticleMath.BEHAVIOR_LIT_SHIFT & 1);
        assertEquals(0.9f, ParticleMath.anisotropyOf(unlit), 1f / ParticleMath.SIGNED_BYTE_MAX);
    }

    /**
     * A volumetric particle's density falls off smoothly to its edge, and a line through its middle
     * loses as much light as its opacity says.
     */
    @Test
    void testSphereDensity() {
        final double opacity = 0.8;
        final double radius = 1.7;
        final double middle = ParticleMath.sphereDensity(opacity, radius);
        final int steps = 100_000;
        double depth = 0;
        for (int i = 0; i < steps; ++i) {
            final double s = -radius + (i + 0.5) * 2 * radius / steps;
            depth += middle * ParticleMath.sphereProfile(Math.abs(s), radius) * 2 * radius / steps;
        }
        assertEquals(opacity, 1 - Math.exp(-depth), 1e-6);
        assertEquals(1, ParticleMath.sphereProfile(0, radius), 1e-12);
        assertEquals(0, ParticleMath.sphereProfile(radius, radius), 1e-12);
        assertTrue(
                ParticleMath.sphereProfile(0.3, radius) > ParticleMath.sphereProfile(0.9, radius));
        assertEquals(0, ParticleMath.sphereDensity(opacity, 0));

        // The depth to the edge is the density integrated out along the radius
        final double from = 0.4;
        double outward = 0;
        for (int i = 0; i < steps; ++i) {
            final double s = from + (i + 0.5) * (radius - from) / steps;
            outward += middle * ParticleMath.sphereProfile(s, radius) * (radius - from) / steps;
        }
        assertEquals(outward, ParticleMath.sphereDepthToEdge(middle, from, radius), 1e-9);
        // From the middle, half of the depth along a whole line through it
        assertEquals(
                -Math.log(1 - opacity) / 2,
                ParticleMath.sphereDepthToEdge(middle, 0, radius),
                1e-9);
        assertEquals(0, ParticleMath.sphereDepthToEdge(middle, radius, radius));

        // Along any direction, the density integrated out to the edge
        final Vector3d offset = new Vector3d(0.3, -0.5, 0.2);
        final Vector3d direction = new Vector3d(0.4, 0.8, -0.3).normalize();
        double along = 0;
        final double step = 1e-5;
        final Vector3d point = new Vector3d();
        for (double t = step / 2; ; t += step) {
            offset.fma(t, direction, point);
            final double distance = point.length();
            if (distance >= radius) {
                break;
            }
            along += middle * ParticleMath.sphereProfile(distance, radius) * step;
        }
        assertEquals(along, ParticleMath.sphereDepthAlong(middle, offset, direction, radius), 1e-6);
        // Straight out it is the depth to the edge
        final Vector3d radial = new Vector3d(offset).normalize();
        assertEquals(
                ParticleMath.sphereDepthToEdge(middle, offset.length(), radius),
                ParticleMath.sphereDepthAlong(middle, offset, radial, radius),
                1e-9);
        // Toward the middle it is deeper than straight out
        assertTrue(
                ParticleMath.sphereDepthAlong(middle, offset, radial.negate(), radius)
                        > ParticleMath.sphereDepthToEdge(middle, offset.length(), radius));
        assertEquals(
                0,
                ParticleMath.sphereDepthAlong(
                        middle, new Vector3d(radius, 0, 0), direction, radius));
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
