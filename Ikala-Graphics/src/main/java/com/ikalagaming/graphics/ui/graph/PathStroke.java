package com.ikalagaming.graphics.ui.graph;

import com.ikalagaming.graphics.gui.data.DrawList;

import lombok.NonNull;
import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * How to draw a path: its width and color, dashes, parallel strokes, how it turns corners and
 * whether it ends in arrowheads. Sizes are in pixels. Paths are polylines; curves are flattened
 * into one first, so dashes and strokes follow them too.
 *
 * @param width How thick each stroke is.
 * @param color The packed color.
 * @param dash On and off lengths that repeat along the path, or null for a solid line.
 * @param strokes How many parallel lines to draw, at least 1.
 * @param strokeGap The space between parallel lines.
 * @param corner How the path turns corners.
 * @param arrow Which ends get an arrowhead.
 */
public record PathStroke(
        float width,
        int color,
        float[] dash,
        int strokes,
        float strokeGap,
        @NonNull Corner corner,
        @NonNull Arrow arrow) {

    /** How a path turns a corner. */
    public enum CornerKind {
        /** A sharp corner. */
        SHARP,
        /** A rounded corner. */
        ROUND,
        /** A corner cut off with a straight line. */
        CHAMFER
    }

    /**
     * How a path turns corners.
     *
     * @param kind The kind of corner.
     * @param size How far from the corner the rounding or cut starts, in pixels.
     */
    public record Corner(@NonNull CornerKind kind, float size) {
        /** Sharp corners. */
        public static final Corner SHARP = new Corner(CornerKind.SHARP, 0);

        /**
         * Read a corner from a style: {@code sharp}, {@code round 6} or {@code chamfer 4}.
         *
         * @param words The style value.
         * @param unit Pixels per UI unit, for the size.
         * @return The corner, sharp if it isn't understood.
         */
        public static Corner parse(String words, float unit) {
            if (words == null) {
                return SHARP;
            }
            String[] parts = words.trim().split("\\s+");
            float size = 0;
            if (parts.length > 1) {
                try {
                    size = Float.parseFloat(parts[1]) * unit;
                } catch (NumberFormatException e) {
                    return SHARP;
                }
            }
            return switch (parts[0].toLowerCase(Locale.ROOT)) {
                case "round" -> new Corner(CornerKind.ROUND, size);
                case "chamfer" -> new Corner(CornerKind.CHAMFER, size);
                default -> SHARP;
            };
        }
    }

    /** Which ends of a path get an arrowhead. */
    public enum Arrow {
        /** No arrowheads. */
        NONE,
        /** An arrowhead at the start. */
        START,
        /** An arrowhead at the end. */
        END,
        /** Arrowheads at both ends. */
        BOTH;

        /**
         * Read arrows from a style: {@code none}, {@code start}, {@code end} or {@code both}.
         *
         * @param words The style value.
         * @return The arrows, none if it isn't understood.
         */
        public static Arrow parse(String words) {
            if (words == null) {
                return NONE;
            }
            return switch (words.trim().toLowerCase(Locale.ROOT)) {
                case "start" -> START;
                case "end" -> END;
                case "both" -> BOTH;
                default -> NONE;
            };
        }
    }

    /**
     * A straight piece of a path, to draw as one line.
     *
     * @param x1 The start, x.
     * @param y1 The start, y.
     * @param x2 The end, x.
     * @param y2 The end, y.
     */
    public record Segment(float x1, float y1, float x2, float y2) {}

    /** How many line pieces a rounded corner or a curve is flattened into. */
    private static final int CURVE_STEPS = 8;

    /**
     * Draw a path.
     *
     * @param drawList Where to draw.
     * @param points The polyline, at least two points.
     */
    public void draw(@NonNull DrawList drawList, @NonNull List<Vector2f> points) {
        if (points.size() < 2) {
            return;
        }
        List<Vector2f> path = cornered(points, corner);
        int count = Math.max(1, strokes);
        for (int k = 0; k < count; ++k) {
            float distance = (k - (count - 1) / 2.0f) * (width + strokeGap);
            List<Vector2f> line = distance == 0 ? path : offset(path, distance);
            for (Segment segment : segments(line, dash)) {
                drawList.addLine(
                        segment.x1(), segment.y1(), segment.x2(), segment.y2(), color, width);
            }
        }
        float arrowSize = Math.max(6, width * 4) + (count - 1) * (width + strokeGap);
        if (arrow == Arrow.END || arrow == Arrow.BOTH) {
            drawArrow(drawList, path.get(path.size() - 2), path.getLast(), arrowSize);
        }
        if (arrow == Arrow.START || arrow == Arrow.BOTH) {
            drawArrow(drawList, path.get(1), path.getFirst(), arrowSize);
        }
    }

    /**
     * Draw a cubic bezier curve, flattened so it can be dashed and stroked like any path.
     *
     * @param drawList Where to draw.
     * @param start The start.
     * @param control1 The first control point.
     * @param control2 The second control point.
     * @param end The end.
     */
    public void drawCurve(
            @NonNull DrawList drawList,
            @NonNull Vector2f start,
            @NonNull Vector2f control1,
            @NonNull Vector2f control2,
            @NonNull Vector2f end) {
        draw(drawList, flattenCubic(start, control1, control2, end, CURVE_STEPS * 3));
    }

    /**
     * Draw an arrowhead pointing from one point toward another, with its tip at the second.
     *
     * @param drawList Where to draw.
     * @param from A point behind the tip, giving the direction.
     * @param tip The tip.
     * @param size The arrowhead's length.
     */
    private void drawArrow(DrawList drawList, Vector2f from, Vector2f tip, float size) {
        float dx = tip.x - from.x;
        float dy = tip.y - from.y;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length < 1e-4f) {
            return;
        }
        dx /= length;
        dy /= length;
        float baseX = tip.x - dx * size;
        float baseY = tip.y - dy * size;
        float half = size * 0.5f;
        drawList.addTriangleFilled(
                tip.x,
                tip.y,
                baseX - dy * half,
                baseY + dx * half,
                baseX + dy * half,
                baseY - dx * half,
                color);
    }

    /**
     * Turn a polyline's corners: leave them sharp, round them, or cut them off.
     *
     * @param points The polyline.
     * @param corner How to turn corners.
     * @return The new polyline, which may have more points.
     */
    public static List<Vector2f> cornered(@NonNull List<Vector2f> points, @NonNull Corner corner) {
        if (corner.kind() == CornerKind.SHARP || corner.size() <= 0 || points.size() < 3) {
            return points;
        }
        List<Vector2f> result = new ArrayList<>();
        result.add(new Vector2f(points.getFirst()));
        for (int i = 1; i < points.size() - 1; ++i) {
            Vector2f before = points.get(i - 1);
            Vector2f at = points.get(i);
            Vector2f after = points.get(i + 1);
            float in = before.distance(at);
            float out = at.distance(after);
            // Each corner can use up to half of each side, so neighboring corners don't overlap
            float size = Math.min(corner.size(), Math.min(in, out) / 2);
            if (size <= 1e-4f) {
                result.add(new Vector2f(at));
                continue;
            }
            Vector2f enter = new Vector2f(before).sub(at).mul(size / in).add(at);
            Vector2f leave = new Vector2f(after).sub(at).mul(size / out).add(at);
            if (corner.kind() == CornerKind.CHAMFER) {
                result.add(enter);
                result.add(leave);
            } else {
                // A quadratic curve through the corner, close to a circular arc for right angles
                for (int step = 0; step <= CURVE_STEPS; ++step) {
                    float t = (float) step / CURVE_STEPS;
                    float u = 1 - t;
                    result.add(
                            new Vector2f(
                                    u * u * enter.x + 2 * u * t * at.x + t * t * leave.x,
                                    u * u * enter.y + 2 * u * t * at.y + t * t * leave.y));
                }
            }
        }
        result.add(new Vector2f(points.getLast()));
        return result;
    }

    /**
     * Move a polyline sideways by a distance, keeping its corners joined, for parallel strokes.
     * Positive distances move it to the left of its direction in screen coordinates, where y points
     * down.
     *
     * @param points The polyline.
     * @param distance How far to move it.
     * @return The moved polyline.
     */
    public static List<Vector2f> offset(@NonNull List<Vector2f> points, float distance) {
        List<Vector2f> result = new ArrayList<>(points.size());
        for (int i = 0; i < points.size(); ++i) {
            Vector2f before = i > 0 ? normal(points.get(i - 1), points.get(i)) : null;
            Vector2f after =
                    i < points.size() - 1 ? normal(points.get(i), points.get(i + 1)) : null;
            Vector2f shift;
            if (before == null || after == null) {
                Vector2f only = before != null ? before : after;
                shift = only == null ? new Vector2f() : new Vector2f(only).mul(distance);
            } else {
                // A miter join: along the average normal, long enough to keep the strokes apart
                Vector2f miter = new Vector2f(before).add(after);
                if (miter.lengthSquared() < 1e-8f) {
                    miter.set(after);
                }
                miter.normalize();
                float cos = Math.max(0.25f, miter.dot(after));
                shift = miter.mul(distance / cos);
            }
            result.add(new Vector2f(points.get(i)).add(shift));
        }
        return result;
    }

    /**
     * The unit normal of a line, pointing to its left in screen coordinates.
     *
     * @param from The start.
     * @param to The end.
     * @return The normal, or null if the line has no length.
     */
    private static Vector2f normal(Vector2f from, Vector2f to) {
        float dx = to.x - from.x;
        float dy = to.y - from.y;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length < 1e-6f) {
            return null;
        }
        return new Vector2f(dy / length, -dx / length);
    }

    /**
     * Split a polyline into the lines to draw: one per piece for a solid line, or the "on" parts of
     * a dash pattern, which continues around corners.
     *
     * @param points The polyline.
     * @param dash On and off lengths, or null or empty for a solid line.
     * @return The lines.
     */
    public static List<Segment> segments(@NonNull List<Vector2f> points, float[] dash) {
        List<Segment> result = new ArrayList<>();
        boolean solid = dash == null || dash.length == 0;
        float patternLength = 0;
        if (!solid) {
            for (float part : dash) {
                patternLength += Math.max(0, part);
            }
            solid = patternLength <= 1e-4f;
        }
        int dashIndex = 0;
        float dashLeft = solid ? 0 : dash[0];
        for (int i = 0; i + 1 < points.size(); ++i) {
            Vector2f from = points.get(i);
            Vector2f to = points.get(i + 1);
            float length = from.distance(to);
            if (solid) {
                if (length > 0) {
                    result.add(new Segment(from.x, from.y, to.x, to.y));
                }
                continue;
            }
            float walked = 0;
            while (walked < length) {
                float step = Math.min(dashLeft, length - walked);
                if (dashIndex % 2 == 0 && step > 0) {
                    float t1 = walked / length;
                    float t2 = (walked + step) / length;
                    result.add(
                            new Segment(
                                    from.x + (to.x - from.x) * t1,
                                    from.y + (to.y - from.y) * t1,
                                    from.x + (to.x - from.x) * t2,
                                    from.y + (to.y - from.y) * t2));
                }
                walked += step;
                dashLeft -= step;
                if (dashLeft <= 1e-4f) {
                    dashIndex = (dashIndex + 1) % dash.length;
                    dashLeft = dash[dashIndex];
                }
            }
        }
        return result;
    }

    /**
     * Flatten a cubic bezier curve into a polyline.
     *
     * @param p0 The start.
     * @param p1 The first control point.
     * @param p2 The second control point.
     * @param p3 The end.
     * @param steps How many lines to use.
     * @return The polyline, steps + 1 points.
     */
    public static List<Vector2f> flattenCubic(
            @NonNull Vector2f p0,
            @NonNull Vector2f p1,
            @NonNull Vector2f p2,
            @NonNull Vector2f p3,
            int steps) {
        List<Vector2f> result = new ArrayList<>(steps + 1);
        for (int i = 0; i <= steps; ++i) {
            float t = (float) i / steps;
            float u = 1 - t;
            float a = u * u * u;
            float b = 3 * u * u * t;
            float c = 3 * u * t * t;
            float d = t * t * t;
            result.add(
                    new Vector2f(
                            a * p0.x + b * p1.x + c * p2.x + d * p3.x,
                            a * p0.y + b * p1.y + c * p2.y + d * p3.y));
        }
        return result;
    }
}
