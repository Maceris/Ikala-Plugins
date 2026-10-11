package com.ikalagaming.factory.lab;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.factory.world.ChunkPos;
import com.ikalagaming.factory.world.gen.debug.WorldgenDebug;

import org.junit.jupiter.api.Test;

/**
 * Tests for the Lab's view math.
 *
 * @author Ches Burks
 */
class TestLabView {

    private static final double EPSILON = 1e-9;

    private static final int WIDTH = 200;

    private static final int HEIGHT = 100;

    /** The origin and world positions agree with how slices are sampled. */
    @Test
    void testOriginMatchesSampling() {
        for (WorldgenDebug.Plane plane : WorldgenDebug.Plane.values()) {
            LabView view = new LabView();
            view.setPlane(plane);
            view.setCenter(10, 20, 30);
            view.setScale(2);
            final double[] origin = view.origin(WIDTH, HEIGHT);
            for (int[] pixel : new int[][] {{0, 0}, {37, 81}, {WIDTH - 1, HEIGHT - 1}}) {
                assertArrayEquals(
                        WorldgenDebug.pixelPosition(plane, origin, 2, pixel[0], pixel[1]),
                        view.worldAt(pixel[0], pixel[1], WIDTH, HEIGHT),
                        EPSILON,
                        plane.toString());
            }
            assertArrayEquals(
                    new double[] {10, 20, 30},
                    view.worldAt(WIDTH / 2.0, HEIGHT / 2.0, WIDTH, HEIGHT),
                    EPSILON);
        }
    }

    /** Dragging moves the world with the mouse. */
    @Test
    void testPan() {
        LabView view = new LabView();
        view.setPlane(WorldgenDebug.Plane.XY);
        view.setScale(4);
        final double[] under = view.worldAt(50, 50, WIDTH, HEIGHT);
        view.pan(10, -5);
        assertArrayEquals(under, view.worldAt(60, 45, WIDTH, HEIGHT), EPSILON);
    }

    /** Zooming keeps the point under the cursor fixed, and stays in range. */
    @Test
    void testZoomAtCursor() {
        LabView view = new LabView();
        view.setCenter(100, 0, -50);
        final double[] under = view.worldAt(30, 70, WIDTH, HEIGHT);
        view.zoomAt(3, 30, 70, WIDTH, HEIGHT);
        assertArrayEquals(under, view.worldAt(30, 70, WIDTH, HEIGHT), EPSILON);
        assertTrue(view.getScale() < 1);
        view.zoomAt(-1000, 0, 0, WIDTH, HEIGHT);
        assertEquals(LabView.MAX_SCALE, view.getScale());
        view.zoomAt(1000, 0, 0, WIDTH, HEIGHT);
        assertEquals(LabView.MIN_SCALE, view.getScale());
    }

    /** The slice coordinate is the axis the plane leaves out. */
    @Test
    void testSlice() {
        LabView view = new LabView();
        view.setCenter(1, 2, 3);
        assertEquals(2, view.getSlice());
        view.setPlane(WorldgenDebug.Plane.ZY);
        assertEquals(1, view.getSlice());
        view.setSlice(-40);
        assertArrayEquals(new double[] {-40, 2, 3}, view.getCenter(), EPSILON);
    }

    /** The visible chunks cover the view's slice. */
    @Test
    void testVisibleChunks() {
        LabView view = new LabView();
        // 32 blocks across, centered on the origin: two chunks along x and z
        view.setCenter(0, 5, 0);
        final var chunks = view.visibleChunks(32, 32);
        assertEquals(4, chunks.size());
        assertTrue(chunks.contains(new ChunkPos(-1, 0, -1)));
        assertTrue(chunks.contains(new ChunkPos(0, 0, 0)));
    }
}
