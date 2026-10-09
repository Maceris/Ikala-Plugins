package com.ikalagaming.graphics.vulkan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.scene.debug.DebugArrow;
import com.ikalagaming.graphics.scene.debug.DebugBox;
import com.ikalagaming.graphics.scene.debug.DebugCone;
import com.ikalagaming.graphics.scene.debug.DebugFrustum;
import com.ikalagaming.graphics.scene.debug.DebugLine;
import com.ikalagaming.graphics.scene.debug.DebugShape;
import com.ikalagaming.graphics.scene.debug.DebugSphere;

import org.joml.Matrix4d;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

class DebugGeometryTest {

    private static final double FAR = 1_000_000_000.0;
    private static final int COLOR = 0x11223344;
    private static final Vector3d ORIGIN = new Vector3d();

    private record Vertex(Vector3f position, int color) {}

    private static List<Vertex> tessellate(DebugShape shape, Vector3d origin) {
        List<Vertex> vertices = new ArrayList<>();
        DebugGeometry.append(
                shape,
                origin,
                (x, y, z, color) -> vertices.add(new Vertex(new Vector3f(x, y, z), color)));
        return vertices;
    }

    @Test
    void vertexCountsMatch() {
        Stream.of(
                        new DebugLine(new Vector3d(), new Vector3d(1, 0, 0), COLOR),
                        new DebugArrow(new Vector3d(), new Vector3d(0, 2, 0), COLOR),
                        new DebugBox(new Vector3d(), new Vector3d(1, 1, 1), COLOR),
                        new DebugSphere(new Vector3d(), 2, COLOR),
                        new DebugCone(new Vector3d(), new Vector3d(0, 0, -1), 3, 0.5, COLOR),
                        DebugFrustum.fromInverseProjectionView(new Matrix4d(), COLOR, false))
                .forEach(
                        shape -> {
                            List<Vertex> vertices = tessellate(shape, ORIGIN);
                            assertEquals(DebugGeometry.vertexCount(shape), vertices.size());
                            assertEquals(0, vertices.size() % 2, "Pairs of line vertices");
                            assertTrue(vertices.stream().allMatch(v -> v.color() == COLOR));
                        });
    }

    @Test
    void boxCornersAreRotatedAroundTheCenter() {
        // A quarter turn around y swaps the x and z half extents
        Quaterniond rotation = new Quaterniond().rotationY(Math.PI / 2);
        DebugBox box =
                new DebugBox(
                        new Vector3d(10, 0, 0), new Vector3d(2, 1, 0.5), rotation, COLOR, false);

        for (Vertex vertex : tessellate(box, ORIGIN)) {
            Vector3f p = vertex.position();
            assertEquals(0.5, Math.abs(p.x - 10), 1e-5);
            assertEquals(1.0, Math.abs(p.y), 1e-5);
            assertEquals(2.0, Math.abs(p.z), 1e-5);
        }
    }

    @Test
    void spherePointsAreOnTheSurface() {
        DebugSphere sphere = new DebugSphere(new Vector3d(1, 2, 3), 4, COLOR);

        for (Vertex vertex : tessellate(sphere, ORIGIN)) {
            assertEquals(4, vertex.position().distance(1, 2, 3), 1e-4);
        }
    }

    @Test
    void coneBaseIsAtTheRightDistanceAndRadius() {
        final double length = 5;
        final double angle = Math.toRadians(30);
        DebugCone cone =
                new DebugCone(new Vector3d(), new Vector3d(0, 0, -2), length, angle, COLOR);
        final double radius = length * Math.tan(angle);

        List<Vertex> vertices = tessellate(cone, ORIGIN);
        for (Vertex vertex : vertices) {
            Vector3f p = vertex.position();
            boolean apex = p.length() < 1e-5;
            if (!apex) {
                assertEquals(-length, p.z, 1e-4, "Base points sit at the end of the axis");
                assertEquals(radius, Math.hypot(p.x, p.y), 1e-4);
            }
        }
    }

    @Test
    void arrowEndsAtItsTip() {
        DebugArrow arrow = new DebugArrow(new Vector3d(0, 0, 0), new Vector3d(0, 10, 0), COLOR);

        List<Vertex> vertices = tessellate(arrow, ORIGIN);
        assertEquals(new Vector3f(0, 0, 0), vertices.get(0).position());
        assertEquals(new Vector3f(0, 10, 0), vertices.get(1).position());
        // The head lines all start at the tip and go back toward the tail
        for (int i = 2; i < vertices.size(); i += 2) {
            assertEquals(new Vector3f(0, 10, 0), vertices.get(i).position());
            assertTrue(vertices.get(i + 1).position().y < 10);
        }
    }

    @Test
    void frustumCornersFromInverseProjectionView() {
        // Identity maps clip space to itself, so the corners are the clip space box
        DebugFrustum frustum = DebugFrustum.fromInverseProjectionView(new Matrix4d(), COLOR, false);

        assertEquals(new Vector3d(-1, -1, 0), frustum.corners()[0]);
        assertEquals(new Vector3d(1, 1, 0), frustum.corners()[2]);
        assertEquals(new Vector3d(-1, -1, 1), frustum.corners()[4]);
        assertEquals(new Vector3d(-1, 1, 1), frustum.corners()[7]);
    }

    @Test
    void renderSpaceIsExactFarFromTheOrigin() {
        DebugLine line =
                new DebugLine(
                        new Vector3d(FAR + 0.25, 0, FAR), new Vector3d(FAR + 1, 0, FAR), COLOR);

        List<Vertex> vertices = tessellate(line, new Vector3d(FAR, 0, FAR));
        assertEquals(new Vector3f(0.25f, 0, 0), vertices.get(0).position());
        assertEquals(new Vector3f(1, 0, 0), vertices.get(1).position());
    }

    @Test
    void shapesCopyTheirVectors() {
        Vector3d from = new Vector3d(1, 2, 3);
        DebugLine line = new DebugLine(from, new Vector3d(), COLOR);
        from.set(9, 9, 9);

        assertEquals(new Vector3d(1, 2, 3), line.from());
    }
}
