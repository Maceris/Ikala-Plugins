package com.ikalagaming.graphics.gizmo;

import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.gui.data.DrawList;
import com.ikalagaming.graphics.gui.data.IkIO;
import com.ikalagaming.graphics.gui.enums.Key;
import com.ikalagaming.graphics.gui.enums.MouseButton;
import com.ikalagaming.graphics.gui.util.Color;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.joml.Matrix3f;
import org.joml.Matrix3fc;
import org.joml.Vector2f;
import org.joml.Vector3dc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.List;
import javax.annotation.Nullable;

/**
 * Handles drawn over the scene for moving, turning and scaling something with the mouse, like the
 * ones in Blender or Unreal: arrows and squares to move along an axis or across a plane, rings to
 * turn about an axis, and boxes to scale. They stay the same size on the screen however far away
 * the camera is.
 *
 * <p>Each frame, call {@link #update} with where the thing is, then {@link #draw}. A drag reports
 * the whole change since it started, not a step per frame, so the caller applies it to what it had
 * when the drag started and nothing drifts. {@link #handleKeys()} switches modes with the keys
 * {@link #KEY_HINTS} lists; show those beside the scene. While {@link #wantsMouse()}, the caller
 * should leave the mouse alone, such as not turning its camera.
 *
 * <p>Not thread safe; use it from the render thread, between IkGui's new frame and render.
 */
public final class TransformGizmo {
    /** What the handles do. */
    public enum Mode {
        /** Move along an axis or across a plane. */
        MOVE,
        /** Turn about an axis. */
        ROTATE,
        /** Grow or shrink. */
        SCALE
    }

    /** Which axes the handles follow. */
    public enum Space {
        /** The world's axes. */
        WORLD,
        /** The thing's own axes, as given to {@link #update}. Scale handles always use these. */
        LOCAL
    }

    /**
     * What a drag has done so far, all of it since the drag started.
     *
     * @param mode What the drag does.
     * @param move How far it moved, in world units, for {@link Mode#MOVE}.
     * @param axis The axis it turns about, in the world, unit length, for {@link Mode#ROTATE}.
     * @param angle How far it turned about the axis, in radians, right-handed.
     * @param scale How much it grew along the handles' x, y and z axes, for {@link Mode#SCALE}:
     *     all three the same for uniform scaling.
     * @param finished Whether the drag ended this frame; the change is final.
     * @param canceled Whether it ended by being canceled; put back what was there at the start.
     */
    public record Drag(
            @NonNull Mode mode,
            @NonNull Vector3fc move,
            @NonNull Vector3fc axis,
            float angle,
            @NonNull Vector3fc scale,
            boolean finished,
            boolean canceled) {}

    /**
     * The mouse and keys for one frame.
     *
     * @param mouseX The mouse's x on the screen, in pixels.
     * @param mouseY Its y.
     * @param pressed Whether the left button went down this frame.
     * @param down Whether it is down.
     * @param overUi Whether the mouse is over UI rather than the scene.
     * @param snap Whether to snap to steps, while Ctrl is held.
     * @param cancel Whether a drag should be canceled, by Escape or the right button.
     */
    public record Input(
            float mouseX,
            float mouseY,
            boolean pressed,
            boolean down,
            boolean overUi,
            boolean snap,
            boolean cancel) {
        /**
         * Read this frame's input from IkGui.
         *
         * @return The input.
         */
        public static Input fromIkGui() {
            IkIO io = IkGui.getIO();
            return new Input(
                    io.mousePosition.x,
                    io.mousePosition.y,
                    IkGui.isMouseClicked(MouseButton.LEFT),
                    IkGui.isMouseDown(MouseButton.LEFT),
                    io.wantCaptureMouse,
                    io.keyCtrl,
                    IkGui.isKeyPressed(Key.ESCAPE, false)
                            || IkGui.isMouseClicked(MouseButton.RIGHT));
        }
    }

    /** What the keys do, to show beside the scene while the handles are up. */
    public static final List<String> KEY_HINTS =
            List.of(
                    "W move, E turn, R scale",
                    "Q world or local axes",
                    "Hold Ctrl to snap",
                    "Esc or right click cancels a drag");

    /** How long the handles look, in pixels. */
    private static final float SIZE = 110;

    /** How close the mouse must be to a handle to grab it, in pixels. */
    private static final float PICK_DISTANCE = 8;

    /** Axes shorter than this on the screen point at the camera and are hidden, in pixels. */
    private static final float HIDDEN_AXIS = 12;

    /** Where a plane square starts and ends along its axes, as fractions of the handle length. */
    private static final float PLANE_START = 0.22f;

    /** Where a plane square ends. */
    private static final float PLANE_END = 0.42f;

    /** How many points a ring is drawn with. */
    private static final int RING_POINTS = 64;

    /** Half the size of a scale box, in pixels. */
    private static final float BOX = 5;

    /** Arrowhead length, in pixels. */
    private static final float ARROW_LENGTH = 12;

    /** Arrowhead half width, in pixels. */
    private static final float ARROW_WIDTH = 5;

    /** Line thickness, in pixels. */
    private static final float LINE = 2.5f;

    /** How far a ring must face the camera to drag along it, as the cosine of its angle. */
    private static final float RING_FACING = 0.15f;

    /** Radians turned per pixel of mouse travel, for a ring seen edge on. */
    private static final float EDGE_TURN = 0.01f;

    /** The axes' colors: x, y, z. */
    private static final int[] AXIS_COLORS = {
        Color.rgba(230, 60, 70, 255), Color.rgba(110, 200, 60, 255), Color.rgba(60, 120, 240, 255)
    };

    /** The color of a hovered or dragged handle. */
    private static final int ACTIVE_COLOR = Color.rgba(255, 220, 40, 255);

    /** The color of the uniform scale box and the middle. */
    private static final int MIDDLE_COLOR = Color.rgba(235, 235, 235, 255);

    /** How see-through plane squares are, as an alpha. */
    private static final int PLANE_ALPHA = 90;

    /** What kind of handle the mouse is on. */
    private enum Kind {
        /** None. */
        NONE,
        /** An axis arrow or scale box. */
        AXIS,
        /** A plane square, named by the axis it is across from. */
        PLANE,
        /** A ring, named by its axis. */
        RING,
        /** The scale box in the middle. */
        UNIFORM
    }

    /**
     * What the handles do now.
     *
     * @param mode The mode.
     * @return The mode.
     */
    @Getter @Setter private @NonNull Mode mode = Mode.MOVE;

    /**
     * Which axes the handles follow.
     *
     * @param space The space.
     * @return The space.
     */
    @Getter @Setter private @NonNull Space space = Space.WORLD;

    /**
     * Whether scaling is the same along every axis, for things that can only grow evenly.
     *
     * @param uniformScale True to scale evenly.
     * @return True if scaling is even.
     */
    @Getter @Setter private boolean uniformScale;

    /** The step moves snap to, in world units, or 0 for none. */
    private float moveStep;

    /** The step turns snap to, in radians, or 0 for none. */
    private float turnStep;

    /** The step scales snap to, or 0 for none. */
    private float scaleStep;

    /** The handles' middle, in render space, as of the last update. */
    private final Vector3f center = new Vector3f();

    /** The handles' axes, as of the last update. */
    private final Vector3f[] axes = {new Vector3f(), new Vector3f(), new Vector3f()};

    /** How long the handles are in the world, as of the last update. */
    private float length;

    /** The view, as of the last update, or null before one. */
    private GizmoView view;

    /** The kind of handle hovered or dragged. */
    private Kind kind = Kind.NONE;

    /** Which axis that handle is for. */
    private int axis;

    /** Whether a handle is being dragged. */
    @Getter private boolean dragging;

    /** Where the mouse was when the drag started. */
    private final Vector2f startMouse = new Vector2f();

    /** Where the middle was on the screen when the drag started. */
    private final Vector2f startScreen = new Vector2f();

    /** The handles' middle when the drag started, in render space. */
    private final Vector3f startCenter = new Vector3f();

    /** Where along the axis, or on the plane, the drag started. */
    private final Vector3f startPoint = new Vector3f();

    /** How far along the axis the drag started. */
    private float startAlong;

    /** The ring's last point under the mouse, from the middle, for adding up turns. */
    private final Vector3f lastRingPoint = new Vector3f();

    /** How far the drag has turned, before snapping. */
    private float turned;

    /** Whether the ring is seen edge on, so turns follow the mouse's travel instead. */
    private boolean edgeOn;

    /**
     * Set the steps Ctrl snaps to.
     *
     * @param move The move step, in world units, or 0 for none.
     * @param turnDegrees The turn step, in degrees, or 0 for none.
     * @param scale The scale step, or 0 for none.
     */
    public void setSnap(float move, float turnDegrees, float scale) {
        moveStep = move;
        turnStep = (float) Math.toRadians(turnDegrees);
        scaleStep = scale;
    }

    /**
     * Whether the mouse is on a handle or dragging one, so the caller should leave it alone.
     *
     * @return True if the gizmo wants the mouse.
     */
    public boolean wantsMouse() {
        return dragging || kind != Kind.NONE;
    }

    /**
     * Switch modes and axes with the keys in {@link #KEY_HINTS}, unless the keyboard is busy, such
     * as typing in a field or flying the camera with the right button held.
     */
    public void handleKeys() {
        IkIO io = IkGui.getIO();
        if (dragging || io.wantCaptureKeyboard || IkGui.isMouseDown(MouseButton.RIGHT)) {
            return;
        }
        if (IkGui.isKeyPressed(Key.W, false)) {
            mode = Mode.MOVE;
        } else if (IkGui.isKeyPressed(Key.E, false)) {
            mode = Mode.ROTATE;
        } else if (IkGui.isKeyPressed(Key.R, false)) {
            mode = Mode.SCALE;
        } else if (IkGui.isKeyPressed(Key.Q, false)) {
            space = space == Space.WORLD ? Space.LOCAL : Space.WORLD;
        }
    }

    /**
     * Find the handle under the mouse, or follow a drag.
     *
     * @param newView How the scene maps to the screen this frame.
     * @param position Where the thing is, in the world.
     * @param localAxes The thing's own axes, as columns, for {@link Space#LOCAL}.
     * @param input This frame's mouse and keys.
     * @return The drag so far, or null if there is none.
     */
    public @Nullable Drag update(
            @NonNull GizmoView newView,
            @NonNull Vector3dc position,
            @NonNull Matrix3fc localAxes,
            @NonNull Input input) {
        view = newView;
        if (!dragging) {
            center.set(
                    (float) (position.x() - view.camera().x()),
                    (float) (position.y() - view.camera().y()),
                    (float) (position.z() - view.camera().z()));
            for (int i = 0; i < 3; ++i) {
                // Scaling is along the thing's own axes, as it grows along them, like Unreal's
                if (space == Space.LOCAL || mode == Mode.SCALE) {
                    localAxes.getColumn(i, axes[i]);
                    if (axes[i].lengthSquared() < 1e-12f) {
                        axes[i].set(i == 0 ? 1 : 0, i == 1 ? 1 : 0, i == 2 ? 1 : 0);
                    }
                    axes[i].normalize();
                } else {
                    axes[i].set(i == 0 ? 1 : 0, i == 1 ? 1 : 0, i == 2 ? 1 : 0);
                }
            }
        }
        length = view.worldSize(center, SIZE);
        if (length <= 0) {
            kind = dragging ? kind : Kind.NONE;
            return null;
        }
        if (!dragging) {
            pick(input);
            if (kind != Kind.NONE && input.pressed()) {
                start(input);
                return result(input, false, false);
            }
            return null;
        }
        if (input.cancel()) {
            dragging = false;
            kind = Kind.NONE;
            return result(input, true, true);
        }
        if (!input.down()) {
            Drag last = result(input, true, false);
            dragging = false;
            return last;
        }
        return result(input, false, false);
    }

    /**
     * Find the handle under the mouse.
     *
     * @param input This frame's input.
     */
    private void pick(Input input) {
        kind = Kind.NONE;
        if (input.overUi()) {
            return;
        }
        Vector2f middle = new Vector2f();
        if (!view.toScreen(center.x, center.y, center.z, middle)) {
            return;
        }
        float x = input.mouseX();
        float y = input.mouseY();
        float best = PICK_DISTANCE;
        Vector2f end = new Vector2f();
        switch (mode) {
            case MOVE -> {
                for (int i = 0; i < 3; ++i) {
                    float[] quad = planeQuad(i);
                    if (quad != null && GizmoMath.insideQuad(x, y, quad)) {
                        kind = Kind.PLANE;
                        axis = i;
                        return;
                    }
                }
                for (int i = 0; i < 3; ++i) {
                    if (!axisEnd(i, 1, end)) {
                        continue;
                    }
                    float distance =
                            GizmoMath.distanceToSegment(x, y, middle.x, middle.y, end.x, end.y);
                    if (distance < best) {
                        best = distance;
                        kind = Kind.AXIS;
                        axis = i;
                    }
                }
            }
            case ROTATE -> {
                float[] ring = new float[RING_POINTS * 2];
                Vector3f toCenter = new Vector3f(center).normalize();
                for (int i = 0; i < 3; ++i) {
                    if (!ringPoints(i, ring)) {
                        continue;
                    }
                    // A ring seen edge on is a line through the others; they win where they cross
                    float penalty =
                            Math.abs(toCenter.dot(axes[i])) < RING_FACING ? PICK_DISTANCE / 2 : 0;
                    for (int p = 0; p < RING_POINTS; ++p) {
                        int q = (p + 1) % RING_POINTS;
                        float distance =
                                penalty
                                        + GizmoMath.distanceToSegment(
                                                x,
                                                y,
                                                ring[p * 2],
                                                ring[p * 2 + 1],
                                                ring[q * 2],
                                                ring[q * 2 + 1]);
                        if (distance < best) {
                            best = distance;
                            kind = Kind.RING;
                            axis = i;
                        }
                    }
                }
            }
            case SCALE -> {
                if (Math.abs(x - middle.x) <= BOX + PICK_DISTANCE / 2
                        && Math.abs(y - middle.y) <= BOX + PICK_DISTANCE / 2) {
                    kind = Kind.UNIFORM;
                    return;
                }
                for (int i = 0; i < 3; ++i) {
                    if (!axisEnd(i, 1, end)) {
                        continue;
                    }
                    float distance =
                            GizmoMath.distanceToSegment(x, y, middle.x, middle.y, end.x, end.y);
                    if (distance < best) {
                        best = distance;
                        kind = uniformScale ? Kind.UNIFORM : Kind.AXIS;
                        axis = i;
                    }
                }
            }
        }
    }

    /**
     * Start dragging the handle under the mouse.
     *
     * @param input This frame's input.
     */
    private void start(Input input) {
        dragging = true;
        startMouse.set(input.mouseX(), input.mouseY());
        view.toScreen(center.x, center.y, center.z, startScreen);
        startCenter.set(center);
        Vector3f ray = view.ray(input.mouseX(), input.mouseY(), new Vector3f());
        Vector3f origin = new Vector3f();
        turned = 0;
        edgeOn = false;
        switch (kind) {
            case AXIS -> startAlong = GizmoMath.closestOnLine(origin, ray, center, axes[axis]);
            case PLANE -> {
                if (!GizmoMath.hitPlane(origin, ray, center, axes[axis], startPoint)) {
                    startPoint.set(center);
                }
            }
            case RING -> {
                Vector3f toCenter = new Vector3f(center).normalize();
                edgeOn = Math.abs(toCenter.dot(axes[axis])) < RING_FACING;
                if (!edgeOn
                        && GizmoMath.hitPlane(origin, ray, center, axes[axis], lastRingPoint)) {
                    lastRingPoint.sub(center);
                } else {
                    edgeOn = true;
                }
            }
            case UNIFORM, NONE -> {}
        }
    }

    /**
     * The drag so far.
     *
     * @param input This frame's input.
     * @param finished Whether it ends now.
     * @param canceled Whether it was canceled.
     * @return The drag.
     */
    private Drag result(Input input, boolean finished, boolean canceled) {
        Vector3f move = new Vector3f();
        Vector3f turnAxis = new Vector3f(axes[Math.max(0, axis)]);
        float angle = 0;
        Vector3f grow = new Vector3f(1, 1, 1);
        Vector3f origin = new Vector3f();
        Vector3f ray = view.ray(input.mouseX(), input.mouseY(), new Vector3f());
        boolean snap = input.snap();
        if (!canceled) {
            switch (kind) {
                case AXIS -> {
                    float along = GizmoMath.closestOnLine(origin, ray, startCenter, axes[axis]);
                    float moved =
                            Float.isNaN(along) || Float.isNaN(startAlong)
                                    ? screenAlong(input, axis)
                                    : along - startAlong;
                    if (mode == Mode.MOVE) {
                        move.set(axes[axis]).mul(snap ? GizmoMath.snap(moved, moveStep) : moved);
                    } else {
                        float factor = Math.max(0.01f, 1 + moved / length);
                        factor = snap ? GizmoMath.snap(factor, scaleStep) : factor;
                        grow.setComponent(axis, Math.max(0.01f, factor));
                    }
                }
                case PLANE -> {
                    Vector3f hit = new Vector3f();
                    if (GizmoMath.hitPlane(origin, ray, startCenter, axes[axis], hit)) {
                        hit.sub(startPoint);
                        for (int i = 0; i < 3; ++i) {
                            if (i == axis) {
                                continue;
                            }
                            float along = hit.dot(axes[i]);
                            along = snap ? GizmoMath.snap(along, moveStep) : along;
                            move.fma(along, axes[i]);
                        }
                    }
                }
                case RING -> {
                    Vector3f hit = new Vector3f();
                    if (!edgeOn
                            && GizmoMath.hitPlane(origin, ray, startCenter, axes[axis], hit)) {
                        hit.sub(startCenter);
                        turned += GizmoMath.signedAngle(lastRingPoint, hit, axes[axis]);
                        lastRingPoint.set(hit);
                    } else {
                        turned =
                                ((input.mouseX() - startMouse.x) - (input.mouseY() - startMouse.y))
                                        * EDGE_TURN;
                    }
                    angle = snap ? GizmoMath.snap(turned, turnStep) : turned;
                }
                case UNIFORM -> {
                    // Away from the middle grows, toward it shrinks, by how much nearer or further
                    // the mouse is than where the drag started: a handle's length doubles it.
                    // The change, not the ratio, so a drag started near the middle doesn't jump
                    float from = startMouse.distance(startScreen);
                    float now =
                            new Vector2f(input.mouseX(), input.mouseY()).distance(startScreen);
                    float factor = 1 + (now - from) / SIZE;
                    factor = snap ? GizmoMath.snap(factor, scaleStep) : factor;
                    factor = Math.max(0.01f, factor);
                    grow.set(factor);
                }
                case NONE -> {}
            }
        }
        if (finished && !canceled) {
            kind = Kind.NONE;
        }
        return new Drag(mode, move, turnAxis, angle, grow, finished, canceled);
    }

    /**
     * How far the mouse has moved along an axis as drawn on the screen, in world units, for an
     * axis that points nearly at the camera.
     *
     * @param input This frame's input.
     * @param i The axis.
     * @return The distance.
     */
    private float screenAlong(Input input, int i) {
        Vector2f end = new Vector2f();
        Vector3f tip = new Vector3f(axes[i]).mul(length).add(startCenter);
        if (!view.toScreen(tip.x, tip.y, tip.z, end)) {
            return 0;
        }
        end.sub(startScreen);
        float lengthSquared = end.lengthSquared();
        if (lengthSquared < 1) {
            return 0;
        }
        float dx = input.mouseX() - startMouse.x;
        float dy = input.mouseY() - startMouse.y;
        return (dx * end.x + dy * end.y) / lengthSquared * length;
    }

    /**
     * Where the end of an axis shows on the screen.
     *
     * @param i The axis.
     * @param fraction How far along it, as a fraction of the handle length.
     * @param out Receives the point.
     * @return False if it doesn't show, or the axis points nearly at the camera.
     */
    private boolean axisEnd(int i, float fraction, Vector2f out) {
        Vector2f middle = new Vector2f();
        Vector3f tip = new Vector3f(axes[i]).mul(length * fraction).add(center);
        return view.toScreen(center.x, center.y, center.z, middle)
                && view.toScreen(tip.x, tip.y, tip.z, out)
                && middle.distance(out) >= HIDDEN_AXIS * fraction;
    }

    /**
     * The corners of a plane square on the screen.
     *
     * @param i The axis the plane is across from.
     * @return The corners as x, y pairs, or null if any is behind the camera.
     */
    private float[] planeQuad(int i) {
        Vector3f a = axes[(i + 1) % 3];
        Vector3f b = axes[(i + 2) % 3];
        float[] corners = new float[8];
        float[][] steps = {
            {PLANE_START, PLANE_START},
            {PLANE_END, PLANE_START},
            {PLANE_END, PLANE_END},
            {PLANE_START, PLANE_END}
        };
        Vector2f out = new Vector2f();
        for (int c = 0; c < 4; ++c) {
            Vector3f point =
                    new Vector3f(center)
                            .fma(steps[c][0] * length, a)
                            .fma(steps[c][1] * length, b);
            if (!view.toScreen(point.x, point.y, point.z, out)) {
                return null;
            }
            corners[c * 2] = out.x;
            corners[c * 2 + 1] = out.y;
        }
        // A plane seen edge on is too thin to grab or see
        float area = 0;
        for (int c = 0; c < 4; ++c) {
            int d = (c + 1) % 4;
            area += corners[c * 2] * corners[d * 2 + 1] - corners[d * 2] * corners[c * 2 + 1];
        }
        float side = (PLANE_END - PLANE_START) * SIZE;
        return Math.abs(area) / 2 < side * side * 0.08f ? null : corners;
    }

    /**
     * The points of a ring on the screen.
     *
     * @param i The ring's axis.
     * @param out Receives the points as x, y pairs.
     * @return False if any is behind the camera.
     */
    private boolean ringPoints(int i, float[] out) {
        Vector3f a = axes[(i + 1) % 3];
        Vector3f b = axes[(i + 2) % 3];
        Vector2f point = new Vector2f();
        Vector3f world = new Vector3f();
        for (int p = 0; p < RING_POINTS; ++p) {
            double angle = 2 * Math.PI * p / RING_POINTS;
            world.set(center)
                    .fma((float) Math.cos(angle) * length, a)
                    .fma((float) Math.sin(angle) * length, b);
            if (!view.toScreen(world.x, world.y, world.z, point)) {
                return false;
            }
            out[p * 2] = point.x;
            out[p * 2 + 1] = point.y;
        }
        return true;
    }

    /**
     * Draw the handles where the last update put them.
     *
     * @param draw Where to draw, such as IkGui's background draw list, behind the UI.
     */
    public void draw(@NonNull DrawList draw) {
        if (view == null || length <= 0) {
            return;
        }
        Vector2f middle = new Vector2f();
        if (!view.toScreen(center.x, center.y, center.z, middle)) {
            return;
        }
        Vector2f end = new Vector2f();
        switch (mode) {
            case MOVE -> {
                for (int i = 0; i < 3; ++i) {
                    float[] quad = planeQuad(i);
                    if (quad == null) {
                        continue;
                    }
                    boolean active = kind == Kind.PLANE && axis == i;
                    int color = active ? ACTIVE_COLOR : AXIS_COLORS[i];
                    draw.addQuadFilled(
                            quad[0],
                            quad[1],
                            quad[2],
                            quad[3],
                            quad[4],
                            quad[5],
                            quad[6],
                            quad[7],
                            Color.multiplyAlpha(color, (active ? 160 : PLANE_ALPHA) / 255f));
                }
                for (int i = 0; i < 3; ++i) {
                    if (!axisEnd(i, 1, end)) {
                        continue;
                    }
                    int color = kind == Kind.AXIS && axis == i ? ACTIVE_COLOR : AXIS_COLORS[i];
                    draw.addLine(middle.x, middle.y, end.x, end.y, color, LINE);
                    arrow(draw, middle, end, color);
                }
            }
            case ROTATE -> {
                float[] ring = new float[RING_POINTS * 2];
                for (int i = 0; i < 3; ++i) {
                    if (!ringPoints(i, ring)) {
                        continue;
                    }
                    boolean active = kind == Kind.RING && axis == i;
                    draw.addPolyline(
                            ring,
                            RING_POINTS,
                            active ? ACTIVE_COLOR : AXIS_COLORS[i],
                            true,
                            active ? LINE * 1.6f : LINE);
                }
            }
            case SCALE -> {
                for (int i = 0; i < 3; ++i) {
                    if (!axisEnd(i, 1, end)) {
                        continue;
                    }
                    boolean active =
                            (kind == Kind.AXIS && axis == i) || kind == Kind.UNIFORM;
                    int color = active ? ACTIVE_COLOR : AXIS_COLORS[i];
                    draw.addLine(middle.x, middle.y, end.x, end.y, color, LINE);
                    draw.addRectFilled(end.x - BOX, end.y - BOX, end.x + BOX, end.y + BOX, color);
                }
                int color = kind == Kind.UNIFORM ? ACTIVE_COLOR : MIDDLE_COLOR;
                draw.addRectFilled(
                        middle.x - BOX, middle.y - BOX, middle.x + BOX, middle.y + BOX, color);
                return;
            }
        }
        draw.addCircleFilled(middle.x, middle.y, 3, MIDDLE_COLOR);
    }

    /**
     * Draw an arrowhead at the end of an axis.
     *
     * @param draw Where to draw.
     * @param from The axis's start on the screen.
     * @param to Its end.
     * @param color The color.
     */
    private static void arrow(DrawList draw, Vector2f from, Vector2f to, int color) {
        Vector2f along = new Vector2f(to).sub(from);
        if (along.lengthSquared() < 1) {
            return;
        }
        along.normalize();
        float tipX = to.x + along.x * ARROW_LENGTH;
        float tipY = to.y + along.y * ARROW_LENGTH;
        draw.addTriangleFilled(
                tipX,
                tipY,
                to.x - along.y * ARROW_WIDTH,
                to.y + along.x * ARROW_WIDTH,
                to.x + along.y * ARROW_WIDTH,
                to.y - along.x * ARROW_WIDTH,
                color);
    }

    /**
     * The axes the handles use now, as columns, for turning a drag's change into the thing's own.
     *
     * @param out Receives the axes.
     * @return out.
     */
    public Matrix3f getAxes(@NonNull Matrix3f out) {
        return out.setColumn(0, axes[0]).setColumn(1, axes[1]).setColumn(2, axes[2]);
    }
}
