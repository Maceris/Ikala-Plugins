package com.ikalagaming.graphics.gizmo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

/** Tests for dragging a {@link TransformGizmo}'s handles, with a camera looking down -z. */
class TransformGizmoTest {
    /** How close moves must be, in world units. */
    private static final float EPSILON = 1e-3f;

    /** The screen width, in pixels. */
    private static final float WIDTH = 1280;

    /** The screen height. */
    private static final float HEIGHT = 720;

    /** A camera five units back from the origin, looking at it. */
    private static final GizmoView VIEW =
            new GizmoView(
                    new Matrix4f()
                            .perspective((float) Math.toRadians(60), WIDTH / HEIGHT, 0.1f, 100),
                    new Vector3d(0, 0, 5),
                    0,
                    0,
                    WIDTH,
                    HEIGHT,
                    1);

    /** Where the handles sit. */
    private static final Vector3d AT = new Vector3d();

    /** Unturned axes. */
    private static final Matrix3f AXES = new Matrix3f();

    /**
     * Where a point in the world shows on the screen.
     *
     * @param x The point's x.
     * @param y Its y.
     * @param z Its z.
     * @return Where it shows.
     */
    private static Vector2f screen(double x, double y, double z) {
        Vector2f out = new Vector2f();
        assertTrue(VIEW.toScreen(new Vector3d(x, y, z), out));
        return out;
    }

    /**
     * Input with the mouse at a point.
     *
     * @param at Where the mouse is.
     * @param pressed Whether the button went down.
     * @param down Whether it is down.
     * @return The input.
     */
    private static TransformGizmo.Input mouse(Vector2f at, boolean pressed, boolean down) {
        return new TransformGizmo.Input(at.x, at.y, pressed, down, false, false, false);
    }

    /**
     * How long the handles are in the world at the origin.
     *
     * @return The length.
     */
    private static float handleLength() {
        return VIEW.worldSize(new Vector3f(0, 0, -5), 110);
    }

    /** Dragging the x arrow moves along x by as far as the mouse went in the world. */
    @Test
    void testMoveAlongAxis() {
        TransformGizmo gizmo = new TransformGizmo();
        Vector2f onArrow = screen(handleLength() * 0.7, 0, 0);
        assertNull(gizmo.update(VIEW, AT, AXES, mouse(onArrow, false, false)));
        assertTrue(gizmo.wantsMouse());

        TransformGizmo.Drag started = gizmo.update(VIEW, AT, AXES, mouse(onArrow, true, true));
        assertNotNull(started);
        assertTrue(gizmo.isDragging());

        Vector2f further = screen(handleLength() * 0.7 + 0.5, 0, 0);
        TransformGizmo.Drag moved = gizmo.update(VIEW, AT, AXES, mouse(further, false, true));
        assertEquals(0.5f, moved.move().x(), EPSILON);
        assertEquals(0, moved.move().y(), EPSILON);
        assertFalse(moved.finished());

        TransformGizmo.Drag done = gizmo.update(VIEW, AT, AXES, mouse(further, false, false));
        assertTrue(done.finished());
        assertFalse(gizmo.isDragging());
    }

    /** Snapping rounds moves to the step. */
    @Test
    void testSnappedMove() {
        TransformGizmo gizmo = new TransformGizmo();
        gizmo.setSnap(0.25f, 15, 0.1f);
        Vector2f onArrow = screen(0, handleLength() * 0.7, 0);
        gizmo.update(VIEW, AT, AXES, mouse(onArrow, false, false));
        gizmo.update(VIEW, AT, AXES, mouse(onArrow, true, true));
        Vector2f further = screen(0, handleLength() * 0.7 + 0.3, 0);
        TransformGizmo.Drag moved =
                gizmo.update(
                        VIEW,
                        AT,
                        AXES,
                        new TransformGizmo.Input(
                                further.x, further.y, false, true, false, true, false));
        assertEquals(0.25f, moved.move().y(), EPSILON);
    }

    /** Dragging around the z ring, which faces the camera, turns by the angle swept. */
    @Test
    void testTurnAboutAxis() {
        TransformGizmo gizmo = new TransformGizmo();
        gizmo.setMode(TransformGizmo.Mode.ROTATE);
        float radius = handleLength();
        Vector2f onRing = screen(radius, 0, 0);
        gizmo.update(VIEW, AT, AXES, mouse(onRing, false, false));
        assertNotNull(gizmo.update(VIEW, AT, AXES, mouse(onRing, true, true)));
        // A quarter turn, in a few steps, so the angle adds up
        TransformGizmo.Drag turned = null;
        for (int step = 1; step <= 3; ++step) {
            double angle = Math.PI / 2 * step / 3;
            turned =
                    gizmo.update(
                            VIEW,
                            AT,
                            AXES,
                            mouse(
                                    screen(radius * Math.cos(angle), radius * Math.sin(angle), 0),
                                    false,
                                    true));
        }
        assertEquals(1, turned.axis().z(), EPSILON);
        assertEquals(Math.PI / 2, turned.angle(), EPSILON);
    }

    /**
     * Scaling evenly grows by how much further from the middle the mouse gets and shrinks by how
     * much nearer, wherever the drag starts, so a drag started near the middle doesn't jump.
     */
    @Test
    void testUniformScale() {
        TransformGizmo gizmo = new TransformGizmo();
        gizmo.setMode(TransformGizmo.Mode.SCALE);
        gizmo.setUniformScale(true);
        Vector2f middle = screen(0, 0, 0);
        Vector2f near = new Vector2f(middle).add(2, 0);
        gizmo.update(VIEW, AT, AXES, mouse(near, false, false));
        gizmo.update(VIEW, AT, AXES, mouse(near, true, true));
        TransformGizmo.Drag grown =
                gizmo.update(VIEW, AT, AXES, mouse(new Vector2f(middle).add(57, 0), false, true));
        assertEquals(1.5f, grown.scale().x(), EPSILON);
        assertEquals(1.5f, grown.scale().z(), EPSILON);
        // Away from the middle on the other side still grows
        TransformGizmo.Drag across =
                gizmo.update(VIEW, AT, AXES, mouse(new Vector2f(middle).add(-57, 0), false, true));
        assertEquals(1.5f, across.scale().y(), EPSILON);
        gizmo.update(VIEW, AT, AXES, mouse(new Vector2f(middle).add(-57, 0), false, false));

        // Starting out on an arm and moving toward the middle shrinks
        Vector2f out = new Vector2f(middle).add(60, 0);
        gizmo.update(VIEW, AT, AXES, mouse(out, false, false));
        gizmo.update(VIEW, AT, AXES, mouse(out, true, true));
        TransformGizmo.Drag shrunk =
                gizmo.update(VIEW, AT, AXES, mouse(new Vector2f(middle).add(5, 0), false, true));
        assertEquals(0.5f, shrunk.scale().x(), EPSILON);
    }

    /** An axis's box scales along that axis only, growing away from the middle. */
    @Test
    void testAxisScale() {
        TransformGizmo gizmo = new TransformGizmo();
        gizmo.setMode(TransformGizmo.Mode.SCALE);
        Vector2f onBox = screen(handleLength(), 0, 0);
        gizmo.update(VIEW, AT, AXES, mouse(onBox, false, false));
        gizmo.update(VIEW, AT, AXES, mouse(onBox, true, true));
        TransformGizmo.Drag shrunk =
                gizmo.update(VIEW, AT, AXES, mouse(screen(handleLength() * 0.5, 0, 0), false, true));
        assertEquals(0.5f, shrunk.scale().x(), EPSILON);
        assertEquals(1, shrunk.scale().y(), EPSILON);
        assertEquals(1, shrunk.scale().z(), EPSILON);
        TransformGizmo.Drag grown =
                gizmo.update(VIEW, AT, AXES, mouse(screen(handleLength() * 1.5, 0, 0), false, true));
        assertEquals(1.5f, grown.scale().x(), EPSILON);
    }

    /** Canceling ends the drag with no change, and the mouse over UI grabs nothing. */
    @Test
    void testCancelAndUi() {
        TransformGizmo gizmo = new TransformGizmo();
        Vector2f onArrow = screen(handleLength() * 0.7, 0, 0);
        gizmo.update(VIEW, AT, AXES, mouse(onArrow, false, false));
        gizmo.update(VIEW, AT, AXES, mouse(onArrow, true, true));
        TransformGizmo.Drag canceled =
                gizmo.update(
                        VIEW,
                        AT,
                        AXES,
                        new TransformGizmo.Input(
                                onArrow.x + 50, onArrow.y, false, true, false, false, true));
        assertTrue(canceled.canceled());
        assertTrue(canceled.finished());
        assertEquals(0, canceled.move().x(), EPSILON);
        assertFalse(gizmo.isDragging());

        assertNull(
                gizmo.update(
                        VIEW,
                        AT,
                        AXES,
                        new TransformGizmo.Input(
                                onArrow.x, onArrow.y, true, true, true, false, false)));
        assertFalse(gizmo.wantsMouse());
    }

    /** A screen point turns back into a ray through the same world point. */
    @Test
    void testRayRoundTrip() {
        Vector2f at = screen(0.4, -0.2, 1);
        Vector3f ray = VIEW.ray(at.x, at.y, new Vector3f());
        Vector3f expected = new Vector3f(0.4f, -0.2f, -4).normalize();
        assertEquals(expected.x, ray.x, 1e-4f);
        assertEquals(expected.y, ray.y, 1e-4f);
        assertEquals(expected.z, ray.z, 1e-4f);
    }
}
