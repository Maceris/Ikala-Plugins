package com.ikalagaming.graphics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.scene.debug.DebugLabel;
import com.ikalagaming.graphics.scene.debug.DebugLine;
import com.ikalagaming.graphics.scene.debug.DebugShape;

import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3d;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

class DebugDrawTest {

    private GraphicsContext context;

    private static DebugShape line(double x) {
        return new DebugLine(new Vector3d(x, 0, 0), new Vector3d(x, 1, 0), 0xFFFFFFFF);
    }

    private List<DebugShape> collect() {
        List<DebugShape> shapes = new ArrayList<>();
        context.collectDebugShapes(shapes::add);
        return shapes;
    }

    @BeforeEach
    void setUp() {
        context = new GraphicsContext("Test-Plugin", null);
    }

    @Test
    void immediateShapesLastOneFrame() {
        context.debug().draw(line(1));
        context.debug().draw(line(2));

        assertEquals(2, collect().size());
        assertEquals(0, collect().size(), "Gone after the frame they were drawn in");
    }

    @Test
    void persistentShapesStayUntilRemoved() {
        DebugShapeHandle first = context.debug().add(line(1));
        context.debug().add(line(2));

        assertEquals(2, collect().size());
        assertEquals(2, collect().size());

        context.debug().remove(first);
        assertEquals(List.of(line(2)), collect());

        context.debug().clear();
        assertEquals(0, collect().size());
    }

    private List<DebugLabel> collectLabels() {
        List<DebugLabel> labels = new ArrayList<>();
        context.collectDebugLabels(labels::add);
        return labels;
    }

    @Test
    void labelsLastAsLongAsAsked() {
        context.debug().label(new DebugLabel(new Vector3d(), "once"));
        DebugShapeHandle kept = context.debug().addLabel(new DebugLabel(new Vector3d(), "kept"));

        assertEquals(2, collectLabels().size());
        assertEquals(List.of(new DebugLabel(new Vector3d(), "kept")), collectLabels());
        assertEquals(0, collect().size(), "Labels aren't shapes");

        context.debug().remove(kept);
        assertEquals(0, collectLabels().size());
    }

    @Test
    void labelsLandWhereTheSceneDrawsThePoint() {
        // A camera at x 10 looking down -z, 90 degrees of view each way on a square screen
        Matrix4f projectionView =
                new Matrix4f().setPerspective((float) Math.PI / 2, 1, 0.1f, 100, true);
        Vector3d camera = new Vector3d(10, 0, 0);
        Vector2f out = new Vector2f();
        float size = 200;

        assertTrue(
                DebugLabelOverlay.toScreen(
                        projectionView, camera, new Vector3d(10, 0, -5), size, size, out));
        assertEquals(new Vector2f(100, 100), out);
        // Up and to the right, halfway to the edge of the view
        assertTrue(
                DebugLabelOverlay.toScreen(
                        projectionView, camera, new Vector3d(12.5, 2.5, -5), size, size, out));
        assertEquals(150, out.x, 1e-3);
        assertEquals(50, out.y, 1e-3);
        // Behind the camera, and past the side of the view
        assertFalse(
                DebugLabelOverlay.toScreen(
                        projectionView, camera, new Vector3d(10, 0, 5), size, size, out));
        assertFalse(
                DebugLabelOverlay.toScreen(
                        projectionView, camera, new Vector3d(20, 0, -5), size, size, out));
    }

    @Test
    void closingTheContextDropsEverything() {
        context.debug().add(line(1));
        context.debug().draw(line(2));

        context.close();

        assertEquals(0, collect().size());
        assertThrows(IllegalStateException.class, () -> context.debug());
    }
}
