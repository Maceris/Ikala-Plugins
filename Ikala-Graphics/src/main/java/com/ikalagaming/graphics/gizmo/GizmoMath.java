package com.ikalagaming.graphics.gizmo;

import lombok.NonNull;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/** The geometry a gizmo needs to pick handles and follow drags. Static only. */
public final class GizmoMath {
    /** Lines closer to parallel than this, by the sine of their angle, have no nearest point. */
    private static final float PARALLEL = 1e-4f;

    /** Static only. */
    private GizmoMath() {}

    /**
     * How far along a line a ray passes closest to it.
     *
     * @param rayOrigin Where the ray starts.
     * @param rayDirection Its direction, unit length.
     * @param lineOrigin A point on the line.
     * @param lineDirection The line's direction, unit length.
     * @return How far from lineOrigin along lineDirection the closest point is, or NaN if the ray
     *     runs along the line, or the closest point is behind the ray's start.
     */
    public static float closestOnLine(
            @NonNull Vector3fc rayOrigin,
            @NonNull Vector3fc rayDirection,
            @NonNull Vector3fc lineOrigin,
            @NonNull Vector3fc lineDirection) {
        Vector3f between = new Vector3f(rayOrigin).sub(lineOrigin);
        float along = rayDirection.dot(lineDirection);
        float denominator = 1 - along * along;
        if (denominator < PARALLEL) {
            return Float.NaN;
        }
        float onRay = rayDirection.dot(between);
        float onLine = lineDirection.dot(between);
        float rayDistance = (along * onLine - onRay) / denominator;
        if (rayDistance < 0) {
            return Float.NaN;
        }
        return (onLine - along * onRay) / denominator;
    }

    /**
     * Where a ray meets a plane.
     *
     * @param rayOrigin Where the ray starts.
     * @param rayDirection Its direction, unit length.
     * @param planePoint A point on the plane.
     * @param normal The plane's normal, unit length.
     * @param out Receives where they meet.
     * @return False if the ray runs along the plane or meets it behind the start.
     */
    public static boolean hitPlane(
            @NonNull Vector3fc rayOrigin,
            @NonNull Vector3fc rayDirection,
            @NonNull Vector3fc planePoint,
            @NonNull Vector3fc normal,
            @NonNull Vector3f out) {
        float facing = rayDirection.dot(normal);
        if (Math.abs(facing) < PARALLEL) {
            return false;
        }
        float distance = new Vector3f(planePoint).sub(rayOrigin).dot(normal) / facing;
        if (distance < 0) {
            return false;
        }
        rayDirection.mul(distance, out).add(rayOrigin);
        return true;
    }

    /**
     * The angle from one vector to another about an axis, with the right-hand rule.
     *
     * @param from The first vector.
     * @param to The second.
     * @param axis The axis, unit length.
     * @return The angle, in radians from -pi to pi.
     */
    public static float signedAngle(
            @NonNull Vector3fc from, @NonNull Vector3fc to, @NonNull Vector3fc axis) {
        Vector3f cross = new Vector3f(from).cross(to);
        return (float) Math.atan2(cross.dot(axis), from.dot(to));
    }

    /**
     * How far a point is from a line segment, on the screen.
     *
     * @param x The point's x.
     * @param y Its y.
     * @param ax The segment's start x.
     * @param ay Its start y.
     * @param bx Its end x.
     * @param by Its end y.
     * @return The distance.
     */
    public static float distanceToSegment(
            float x, float y, float ax, float ay, float bx, float by) {
        float dx = bx - ax;
        float dy = by - ay;
        float lengthSquared = dx * dx + dy * dy;
        float t =
                lengthSquared <= 0
                        ? 0
                        : Math.clamp(((x - ax) * dx + (y - ay) * dy) / lengthSquared, 0f, 1f);
        float px = ax + t * dx - x;
        float py = ay + t * dy - y;
        return (float) Math.sqrt(px * px + py * py);
    }

    /**
     * Whether a point is inside a convex quad on the screen, its corners in order either way
     * round.
     *
     * @param x The point's x.
     * @param y Its y.
     * @param corners The corners' x and y, four pairs.
     * @return True if inside.
     */
    public static boolean insideQuad(float x, float y, float @NonNull [] corners) {
        int sign = 0;
        for (int i = 0; i < 4; ++i) {
            float ax = corners[i * 2];
            float ay = corners[i * 2 + 1];
            float bx = corners[(i + 1) % 4 * 2];
            float by = corners[(i + 1) % 4 * 2 + 1];
            float side = (bx - ax) * (y - ay) - (by - ay) * (x - ax);
            int now = side > 0 ? 1 : side < 0 ? -1 : 0;
            if (now != 0) {
                if (sign != 0 && now != sign) {
                    return false;
                }
                sign = now;
            }
        }
        return true;
    }

    /**
     * Round a value to the nearest step.
     *
     * @param value The value.
     * @param step The step, or 0 or less for no rounding.
     * @return The rounded value.
     */
    public static float snap(float value, float step) {
        return step > 0 ? Math.round(value / step) * step : value;
    }
}
