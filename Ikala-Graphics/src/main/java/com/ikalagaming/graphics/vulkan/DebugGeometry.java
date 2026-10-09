package com.ikalagaming.graphics.vulkan;

import com.ikalagaming.graphics.scene.debug.DebugArrow;
import com.ikalagaming.graphics.scene.debug.DebugBox;
import com.ikalagaming.graphics.scene.debug.DebugCone;
import com.ikalagaming.graphics.scene.debug.DebugFrustum;
import com.ikalagaming.graphics.scene.debug.DebugLine;
import com.ikalagaming.graphics.scene.debug.DebugShape;
import com.ikalagaming.graphics.scene.debug.DebugSphere;

import lombok.NonNull;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * Turns debug shapes into line segments in render space, which is world space relative to an origin
 * (the camera). Positions are made relative in double precision before converting to float.
 */
public final class DebugGeometry {

    /** Receives line segment vertices, two per segment. */
    @FunctionalInterface
    public interface LineSink {
        /**
         * Add one vertex.
         *
         * @param x The x position in render space.
         * @param y The y position in render space.
         * @param z The z position in render space.
         * @param color The packed RGBA color.
         */
        void vertex(float x, float y, float z, int color);
    }

    /** Segments per circle in spheres and cones. */
    public static final int CIRCLE_SEGMENTS = 32;

    /** Lines from a cone's apex to its base. */
    public static final int CONE_SIDE_LINES = 8;

    /** The fraction of an arrow's length that its head takes up. */
    private static final double ARROW_HEAD_LENGTH = 0.2;

    /** The radius of an arrow's head, relative to the head's length. */
    private static final double ARROW_HEAD_RADIUS = 0.4;

    /** The 12 edges of a box or frustum, as pairs of corner indices. */
    private static final int[][] BOX_EDGES = {
        {0, 1}, {1, 2}, {2, 3}, {3, 0}, {4, 5}, {5, 6}, {6, 7}, {7, 4}, {0, 4}, {1, 5}, {2, 6},
        {3, 7},
    };

    /**
     * How many vertices a shape turns into.
     *
     * @param shape The shape.
     * @return The number of vertices, always even.
     */
    public static int vertexCount(@NonNull DebugShape shape) {
        return switch (shape) {
            case DebugLine ignored -> 2;
            case DebugArrow ignored -> 2 + 4 * 2;
            case DebugBox ignored -> BOX_EDGES.length * 2;
            case DebugFrustum ignored -> BOX_EDGES.length * 2;
            case DebugSphere ignored -> 3 * CIRCLE_SEGMENTS * 2;
            case DebugCone ignored -> (CIRCLE_SEGMENTS + CONE_SIDE_LINES) * 2;
        };
    }

    /**
     * Turn a shape into line segments.
     *
     * @param shape The shape.
     * @param origin The world position of the render space origin.
     * @param sink Receives the vertices, two per segment.
     */
    public static void append(
            @NonNull DebugShape shape, @NonNull Vector3dc origin, @NonNull LineSink sink) {
        final Emitter out = new Emitter(origin, sink, shape.color());
        switch (shape) {
            case DebugLine line -> out.line(line.from(), line.to());
            case DebugArrow arrow -> appendArrow(arrow, out);
            case DebugBox box -> appendBox(box, out);
            case DebugFrustum frustum -> appendCorners(frustum.corners(), out);
            case DebugSphere sphere -> appendSphere(sphere, out);
            case DebugCone cone -> appendCone(cone, out);
        }
    }

    private static void appendArrow(@NonNull DebugArrow arrow, @NonNull Emitter out) {
        out.line(arrow.from(), arrow.to());
        Vector3d axis = new Vector3d(arrow.to()).sub(arrow.from());
        final double length = axis.length();
        if (length == 0) {
            // No direction to point the head in, so draw a degenerate head
            for (int i = 0; i < 4; ++i) {
                out.line(arrow.to(), arrow.to());
            }
            return;
        }
        axis.div(length);
        final double headLength = length * ARROW_HEAD_LENGTH;
        final double headRadius = headLength * ARROW_HEAD_RADIUS;
        Vector3d u = new Vector3d();
        Vector3d v = new Vector3d();
        perpendiculars(axis, u, v);
        Vector3d base = new Vector3d(axis).mul(-headLength).add(arrow.to());
        Vector3d point = new Vector3d();
        for (int i = 0; i < 4; ++i) {
            final Vector3d side = (i % 2 == 0) ? u : v;
            final double sign = i < 2 ? 1 : -1;
            point.set(side).mul(sign * headRadius).add(base);
            out.line(arrow.to(), point);
        }
    }

    private static void appendBox(@NonNull DebugBox box, @NonNull Emitter out) {
        Vector3dc[] corners = new Vector3dc[DebugFrustum.CORNER_COUNT];
        final double[][] signs = {
            {-1, -1, -1}, {1, -1, -1}, {1, 1, -1}, {-1, 1, -1},
            {-1, -1, 1}, {1, -1, 1}, {1, 1, 1}, {-1, 1, 1},
        };
        for (int i = 0; i < corners.length; ++i) {
            Vector3d corner =
                    new Vector3d(
                            signs[i][0] * box.halfExtents().x(),
                            signs[i][1] * box.halfExtents().y(),
                            signs[i][2] * box.halfExtents().z());
            box.rotation().transform(corner);
            corners[i] = corner.add(box.center());
        }
        appendCorners(corners, out);
    }

    private static void appendCorners(@NonNull Vector3dc[] corners, @NonNull Emitter out) {
        for (int[] edge : BOX_EDGES) {
            out.line(corners[edge[0]], corners[edge[1]]);
        }
    }

    private static void appendSphere(@NonNull DebugSphere sphere, @NonNull Emitter out) {
        final Vector3dc[][] planes = {
            {new Vector3d(1, 0, 0), new Vector3d(0, 1, 0)},
            {new Vector3d(0, 1, 0), new Vector3d(0, 0, 1)},
            {new Vector3d(1, 0, 0), new Vector3d(0, 0, 1)},
        };
        for (Vector3dc[] plane : planes) {
            appendCircle(sphere.center(), plane[0], plane[1], sphere.radius(), out);
        }
    }

    private static void appendCone(@NonNull DebugCone cone, @NonNull Emitter out) {
        Vector3d axis = new Vector3d(cone.direction());
        if (axis.lengthSquared() == 0) {
            axis.set(0, 0, -1);
        }
        axis.normalize();
        Vector3d u = new Vector3d();
        Vector3d v = new Vector3d();
        perpendiculars(axis, u, v);
        Vector3d base = new Vector3d(axis).mul(cone.length()).add(cone.apex());
        final double radius = cone.length() * Math.tan(cone.angle());
        appendCircle(base, u, v, radius, out);
        Vector3d point = new Vector3d();
        for (int i = 0; i < CONE_SIDE_LINES; ++i) {
            final double angle = 2 * Math.PI * i / CONE_SIDE_LINES;
            circlePoint(base, u, v, radius, angle, point);
            out.line(cone.apex(), point);
        }
    }

    /**
     * Add a circle as {@link #CIRCLE_SEGMENTS} segments.
     *
     * @param center The center, in world space.
     * @param u One unit axis of the circle's plane.
     * @param v The other unit axis of the circle's plane.
     * @param radius The radius.
     * @param out Where the segments go.
     */
    private static void appendCircle(
            @NonNull Vector3dc center,
            @NonNull Vector3dc u,
            @NonNull Vector3dc v,
            double radius,
            @NonNull Emitter out) {
        Vector3d previous = new Vector3d();
        Vector3d next = new Vector3d();
        circlePoint(center, u, v, radius, 0, previous);
        for (int i = 1; i <= CIRCLE_SEGMENTS; ++i) {
            circlePoint(center, u, v, radius, 2 * Math.PI * i / CIRCLE_SEGMENTS, next);
            out.line(previous, next);
            previous.set(next);
        }
    }

    private static void circlePoint(
            @NonNull Vector3dc center,
            @NonNull Vector3dc u,
            @NonNull Vector3dc v,
            double radius,
            double angle,
            @NonNull Vector3d dest) {
        final double cos = Math.cos(angle) * radius;
        final double sin = Math.sin(angle) * radius;
        dest.set(
                center.x() + u.x() * cos + v.x() * sin,
                center.y() + u.y() * cos + v.y() * sin,
                center.z() + u.z() * cos + v.z() * sin);
    }

    /**
     * Find two unit vectors perpendicular to an axis and to each other.
     *
     * @param axis A unit vector.
     * @param u Where to store the first perpendicular.
     * @param v Where to store the second perpendicular.
     */
    private static void perpendiculars(
            @NonNull Vector3dc axis, @NonNull Vector3d u, @NonNull Vector3d v) {
        // Cross with whichever world axis is least parallel, so the result isn't tiny
        if (Math.abs(axis.x()) < 0.9) {
            u.set(1, 0, 0);
        } else {
            u.set(0, 1, 0);
        }
        axis.cross(u, u).normalize();
        axis.cross(u, v).normalize();
    }

    /** Converts world positions to render space and passes them on. */
    private static final class Emitter {
        private final Vector3dc origin;
        private final LineSink sink;
        private final int color;

        private Emitter(@NonNull Vector3dc origin, @NonNull LineSink sink, int color) {
            this.origin = origin;
            this.sink = sink;
            this.color = color;
        }

        private void line(@NonNull Vector3dc from, @NonNull Vector3dc to) {
            point(from);
            point(to);
        }

        private void point(@NonNull Vector3dc position) {
            sink.vertex(
                    (float) (position.x() - origin.x()),
                    (float) (position.y() - origin.y()),
                    (float) (position.z() - origin.z()),
                    color);
        }
    }

    private DebugGeometry() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
