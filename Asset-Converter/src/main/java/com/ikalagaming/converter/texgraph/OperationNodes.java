package com.ikalagaming.converter.texgraph;

import static com.ikalagaming.converter.texgraph.CoreNodes.param;
import static com.ikalagaming.converter.texgraph.CoreNodes.pin;
import static com.ikalagaming.converter.texgraph.CoreNodes.type;
import static com.ikalagaming.converter.texgraph.TexturePins.COLOR;
import static com.ikalagaming.converter.texgraph.TexturePins.COORDINATES;
import static com.ikalagaming.converter.texgraph.TexturePins.MASK;
import static com.ikalagaming.converter.texgraph.TexturePins.SCALAR;
import static com.ikalagaming.converter.texgraph.TexturePins.VECTOR;

import com.ikalagaming.graphics.ui.graph.model.NodeTypes;

import lombok.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Operations on planes, coordinates and channels: curves, remap, threshold, blur, edge detect,
 * distance, slope blur, warp, offset and transform, which wrap at the tile's edges; coordinate
 * offset, rotate, scale, polar and cells; component mask, swizzle and append. Operations that move
 * pixels come for scalars and, with "Color" in the title, for colors.
 */
public final class OperationNodes {
    /** The category operations are listed under. */
    static final String OPERATIONS = "Operations";

    /** The category coordinate nodes are listed under. */
    static final String COORDS = "Coordinates";

    /** The category channel nodes are listed under. */
    static final String CHANNELS = "Channels";

    /** The usual input. */
    static final String VALUE = "value";

    /** The usual output. */
    static final String RESULT = "result";

    /** Coordinates in and out. */
    static final String COORDINATES_PIN = "coordinates";

    /** Not made. */
    private OperationNodes() {}

    /** A whole plane's operation. */
    @FunctionalInterface
    private interface Operation {
        /**
         * Work on a plane.
         *
         * @param context The node's context.
         * @param in The input plane.
         * @return The result, with the same channels.
         */
        Plane apply(NodeContext context, Plane in);
    }

    /**
     * Add the nodes to a library.
     *
     * @param library The library.
     * @return The library.
     */
    public static TextureLibrary register(@NonNull TextureLibrary library) {
        tonal(library);
        moving(library);
        coordinates(library);
        channels(library);
        return library;
    }

    // ---- tonal: one value in, one out

    /**
     * Curves, remap, threshold and edge detect.
     *
     * @param library The library.
     */
    private static void tonal(TextureLibrary library) {
        library.register(
                type(
                        "ops/curves",
                        "Curves",
                        OPERATIONS,
                        List.of(pin(VALUE, SCALAR)),
                        List.of(pin(RESULT, SCALAR)),
                        List.of(param("points", "0:0 1:1"))),
                context -> {
                    final float[][] points = points(context.text("points"));
                    context.output(
                            RESULT, context.input(VALUE).map((value, c) -> curve(points, value)));
                });
        library.register(
                type(
                        "ops/remap",
                        "Remap",
                        OPERATIONS,
                        List.of(pin(VALUE, SCALAR)),
                        List.of(pin(RESULT, SCALAR)),
                        List.of(
                                param("from low", "0"),
                                param("from high", "1"),
                                param("to low", "0"),
                                param("to high", "1"),
                                param("clamp", "false"))),
                context -> {
                    final float fromLow = context.number("from low");
                    final float span = context.number("from high") - fromLow;
                    final float toLow = context.number("to low");
                    final float toHigh = context.number("to high");
                    final boolean clamp = context.flag("clamp");
                    context.output(
                            RESULT,
                            context.input(VALUE)
                                    .map(
                                            (value, c) -> {
                                                float t = span == 0 ? 0 : (value - fromLow) / span;
                                                if (clamp) {
                                                    t = Math.clamp(t, 0, 1);
                                                }
                                                return toLow + (toHigh - toLow) * t;
                                            }));
                });
        library.register(
                type(
                        "ops/threshold",
                        "Threshold",
                        OPERATIONS,
                        List.of(pin(VALUE, SCALAR)),
                        List.of(pin(RESULT, MASK)),
                        List.of(param("level", "0.5"), param("softness", "0"))),
                context -> {
                    final float level = context.number("level");
                    final float soft = Math.max(0, context.number("softness"));
                    context.output(
                            RESULT,
                            context.input(VALUE)
                                    .map(
                                            (value, c) -> {
                                                if (soft == 0) {
                                                    return value >= level ? 1 : 0;
                                                }
                                                final float t =
                                                        Math.clamp(
                                                                (value - level + soft / 2) / soft,
                                                                0,
                                                                1);
                                                return t * t * (3 - 2 * t);
                                            }));
                });
        library.register(
                type(
                        "ops/edge_detect",
                        "Edge Detect",
                        OPERATIONS,
                        List.of(pin(VALUE, SCALAR)),
                        List.of(pin(RESULT, SCALAR)),
                        List.of(param("strength", "1"))),
                context -> {
                    final Plane in = context.input(VALUE);
                    final float strength = context.number("strength");
                    final Plane out = context.plane(1);
                    for (int y = 0; y < in.getHeight(); ++y) {
                        for (int x = 0; x < in.getWidth(); ++x) {
                            final float gx =
                                    at(in, x + 1, y - 1)
                                            + 2 * at(in, x + 1, y)
                                            + at(in, x + 1, y + 1)
                                            - at(in, x - 1, y - 1)
                                            - 2 * at(in, x - 1, y)
                                            - at(in, x - 1, y + 1);
                            final float gy =
                                    at(in, x - 1, y + 1)
                                            + 2 * at(in, x, y + 1)
                                            + at(in, x + 1, y + 1)
                                            - at(in, x - 1, y - 1)
                                            - 2 * at(in, x, y - 1)
                                            - at(in, x + 1, y - 1);
                            out.set(x, y, 0, strength * (float) Math.hypot(gx, gy) / 4);
                        }
                    }
                    context.output(RESULT, out);
                });
        library.register(
                type(
                        "ops/distance",
                        "Distance",
                        OPERATIONS,
                        List.of(pin("mask", MASK)),
                        List.of(pin(RESULT, SCALAR)),
                        List.of(param("reach", "4"))),
                context ->
                        context.output(
                                RESULT, distance(context.input("mask"), context.number("reach"))));
    }

    /**
     * Curve points from text like {@code 0:0 0.5:0.7 1:1}, sorted by their first value.
     *
     * @param text The points.
     * @return Each point's in and out.
     * @throws TextureNode.NodeException If they can't be read.
     */
    static float[][] points(String text) {
        final List<float[]> points = new ArrayList<>();
        for (String part : text.trim().split("\\s+")) {
            final String[] pair = part.split(":");
            try {
                points.add(new float[] {Float.parseFloat(pair[0]), Float.parseFloat(pair[1])});
            } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
                throw new TextureNode.NodeException(
                        "Curve points are in:out pairs like 0:0 0.5:0.7 1:1, not " + part);
            }
        }
        if (points.size() < 2) {
            throw new TextureNode.NodeException("A curve needs at least two points");
        }
        points.sort((a, b) -> Float.compare(a[0], b[0]));
        return points.toArray(new float[0][]);
    }

    /**
     * A smooth curve through points that never overshoots between them (monotone cubic, Fritsch and
     * Carlson), flat past the ends.
     *
     * @param points The points, sorted.
     * @param value Where to read it.
     * @return The curve's value.
     */
    static float curve(float[][] points, float value) {
        final int n = points.length;
        if (value <= points[0][0]) {
            return points[0][1];
        }
        if (value >= points[n - 1][0]) {
            return points[n - 1][1];
        }
        int i = 0;
        while (value > points[i + 1][0]) {
            ++i;
        }
        final float h = points[i + 1][0] - points[i][0];
        if (h <= 0) {
            return points[i + 1][1];
        }
        final float m0 = tangent(points, i);
        final float m1 = tangent(points, i + 1);
        final float t = (value - points[i][0]) / h;
        final float t2 = t * t;
        final float t3 = t2 * t;
        return (2 * t3 - 3 * t2 + 1) * points[i][1]
                + (t3 - 2 * t2 + t) * h * m0
                + (-2 * t3 + 3 * t2) * points[i + 1][1]
                + (t3 - t2) * h * m1;
    }

    /**
     * A point's tangent, zero where the curve turns so it doesn't overshoot.
     *
     * @param p The points.
     * @param i Which point.
     * @return The slope there.
     */
    private static float tangent(float[][] p, int i) {
        final int n = p.length;
        if (i == 0) {
            return slope(p, 0);
        }
        if (i == n - 1) {
            return slope(p, n - 2);
        }
        final float before = slope(p, i - 1);
        final float after = slope(p, i);
        if (before * after <= 0) {
            return 0;
        }
        // The harmonic mean keeps it monotone
        return 2 / (1 / before + 1 / after);
    }

    /**
     * The slope of the segment after a point.
     *
     * @param p The points.
     * @param i The segment's first point.
     * @return Its slope.
     */
    private static float slope(float[][] p, int i) {
        final float run = p[i + 1][0] - p[i][0];
        return run <= 0 ? 0 : (p[i + 1][1] - p[i][1]) / run;
    }

    /**
     * A plane's first channel, wrapping.
     *
     * @param plane The plane.
     * @param x The column, any whole number.
     * @param y The row.
     * @return The value.
     */
    private static float at(Plane plane, int x, int y) {
        return plane.get(
                Math.floorMod(x, plane.getWidth()), Math.floorMod(y, plane.getHeight()), 0);
    }

    /**
     * Each pixel's distance to the nearest gap (where the mask is below one half), wrapping, as a
     * part of a reach: 0 in the gaps, 1 at the reach or further.
     *
     * @param mask The mask.
     * @param reach The distance that counts as 1, in pixels.
     * @return The distances.
     */
    static Plane distance(Plane mask, float reach) {
        final int width = mask.getWidth();
        final int height = mask.getHeight();
        final float far = (float) Math.hypot(width, height);
        final float[] d = new float[width * height];
        for (int y = 0; y < height; ++y) {
            for (int x = 0; x < width; ++x) {
                d[y * width + x] = mask.get(x, y, 0) < 0.5f ? 0 : far;
            }
        }
        // Chamfer passes on a torus; twice round, so distances reach across the wrap
        final float diagonal = (float) Math.sqrt(2);
        for (int round = 0; round < 2; ++round) {
            for (int y = 0; y < height; ++y) {
                for (int x = 0; x < width; ++x) {
                    float best = d[y * width + x];
                    best = Math.min(best, d[index(x - 1, y, width, height)] + 1);
                    best = Math.min(best, d[index(x, y - 1, width, height)] + 1);
                    best = Math.min(best, d[index(x - 1, y - 1, width, height)] + diagonal);
                    best = Math.min(best, d[index(x + 1, y - 1, width, height)] + diagonal);
                    d[y * width + x] = best;
                }
            }
            for (int y = height - 1; y >= 0; --y) {
                for (int x = width - 1; x >= 0; --x) {
                    float best = d[y * width + x];
                    best = Math.min(best, d[index(x + 1, y, width, height)] + 1);
                    best = Math.min(best, d[index(x, y + 1, width, height)] + 1);
                    best = Math.min(best, d[index(x + 1, y + 1, width, height)] + diagonal);
                    best = Math.min(best, d[index(x - 1, y + 1, width, height)] + diagonal);
                    d[y * width + x] = best;
                }
            }
        }
        final float scale = Math.max(1e-6f, reach);
        final Plane out = new Plane(width, height, 1);
        for (int y = 0; y < height; ++y) {
            for (int x = 0; x < width; ++x) {
                out.set(x, y, 0, Math.min(1, d[y * width + x] / scale));
            }
        }
        return out;
    }

    /**
     * Where a wrapped pixel is in a flat array.
     *
     * @param x The column, any whole number.
     * @param y The row.
     * @param width The width.
     * @param height The height.
     * @return The index.
     */
    private static int index(int x, int y, int width, int height) {
        return Math.floorMod(y, height) * width + Math.floorMod(x, width);
    }

    // ---- moving pixels

    /**
     * Blur, slope blur, warp, offset and transform, for scalars and colors.
     *
     * @param library The library.
     */
    private static void moving(TextureLibrary library) {
        both(
                library,
                "ops/blur",
                "Blur",
                List.of(),
                List.of(param("radius", "1")),
                (context, in) -> blur(in, Math.max(0, context.number("radius"))));
        both(
                library,
                "ops/slope_blur",
                "Slope Blur",
                List.of(pin("slope", SCALAR)),
                List.of(param("intensity", "2"), param("samples", "8")),
                (context, in) -> {
                    final Plane slope = context.input("slope");
                    final float intensity = context.number("intensity");
                    final int samples = Math.clamp(context.integer("samples"), 1, 64);
                    final Plane out = new Plane(in.getWidth(), in.getHeight(), in.getChannels());
                    for (int y = 0; y < in.getHeight(); ++y) {
                        for (int x = 0; x < in.getWidth(); ++x) {
                            final float gx = at(slope, x + 1, y) - at(slope, x - 1, y);
                            final float gy = at(slope, x, y + 1) - at(slope, x, y - 1);
                            for (int c = 0; c < in.getChannels(); ++c) {
                                float sum = 0;
                                for (int s = 0; s < samples; ++s) {
                                    final float t = intensity * s / samples;
                                    sum += sample(in, x + 0.5f + gx * t, y + 0.5f + gy * t, c);
                                }
                                out.set(x, y, c, sum / samples);
                            }
                        }
                    }
                    return out;
                });
        both(
                library,
                "ops/warp",
                "Warp",
                List.of(pin("x", SCALAR), pin("y", SCALAR)),
                List.of(param("intensity", "2")),
                (context, in) -> {
                    final Plane dx = context.linked("x") ? context.input("x") : null;
                    final Plane dy = context.linked("y") ? context.input("y") : null;
                    final float intensity = context.number("intensity");
                    final Plane out = new Plane(in.getWidth(), in.getHeight(), in.getChannels());
                    for (int y = 0; y < in.getHeight(); ++y) {
                        for (int x = 0; x < in.getWidth(); ++x) {
                            final float sx =
                                    dx == null ? 0 : (dx.get(x, y, 0) - 0.5f) * 2 * intensity;
                            final float sy =
                                    dy == null ? 0 : (dy.get(x, y, 0) - 0.5f) * 2 * intensity;
                            for (int c = 0; c < in.getChannels(); ++c) {
                                out.set(x, y, c, sample(in, x + 0.5f + sx, y + 0.5f + sy, c));
                            }
                        }
                    }
                    return out;
                });
        both(
                library,
                "ops/offset",
                "Offset",
                List.of(),
                List.of(param("x", "0"), param("y", "0")),
                (context, in) -> {
                    final int ox = Math.round(context.number("x"));
                    final int oy = Math.round(context.number("y"));
                    final Plane out = new Plane(in.getWidth(), in.getHeight(), in.getChannels());
                    for (int y = 0; y < in.getHeight(); ++y) {
                        for (int x = 0; x < in.getWidth(); ++x) {
                            for (int c = 0; c < in.getChannels(); ++c) {
                                out.set(
                                        x,
                                        y,
                                        c,
                                        in.get(
                                                Math.floorMod(x - ox, in.getWidth()),
                                                Math.floorMod(y - oy, in.getHeight()),
                                                c));
                            }
                        }
                    }
                    return out;
                });
        both(
                library,
                "ops/transform",
                "Transform",
                List.of(),
                List.of(
                        param("rotate", "0"),
                        param("scale", "1"),
                        param("x", "0"),
                        param("y", "0"),
                        param("smooth", "false")),
                (context, in) -> {
                    final double angle = Math.toRadians(context.number("rotate"));
                    final float scale = context.number("scale");
                    if (scale == 0) {
                        throw new TextureNode.NodeException("Scale can't be 0");
                    }
                    final float ox = context.number("x");
                    final float oy = context.number("y");
                    final boolean smooth = context.flag("smooth");
                    final float cos = (float) Math.cos(-angle) / scale;
                    final float sin = (float) Math.sin(-angle) / scale;
                    final float cx = in.getWidth() / 2f;
                    final float cy = in.getHeight() / 2f;
                    final Plane out = new Plane(in.getWidth(), in.getHeight(), in.getChannels());
                    for (int y = 0; y < in.getHeight(); ++y) {
                        for (int x = 0; x < in.getWidth(); ++x) {
                            final float px = x + 0.5f - cx - ox;
                            final float py = y + 0.5f - cy - oy;
                            final float sx = px * cos - py * sin + cx;
                            final float sy = px * sin + py * cos + cy;
                            for (int c = 0; c < in.getChannels(); ++c) {
                                out.set(
                                        x,
                                        y,
                                        c,
                                        smooth
                                                ? sample(in, sx, sy, c)
                                                : in.get(
                                                        Math.floorMod(
                                                                (int) Math.floor(sx),
                                                                in.getWidth()),
                                                        Math.floorMod(
                                                                (int) Math.floor(sy),
                                                                in.getHeight()),
                                                        c));
                            }
                        }
                    }
                    return out;
                });
    }

    /**
     * Register an operation for scalars and for colors.
     *
     * @param library The library.
     * @param id The scalar type's ID; the color type adds "_color".
     * @param title The scalar type's title; the color type adds " Color".
     * @param extraInputs Inputs after the one worked on.
     * @param params The parameters.
     * @param operation What it does.
     */
    private static void both(
            TextureLibrary library,
            String id,
            String title,
            List<NodeTypes.Pin> extraInputs,
            List<NodeTypes.Param> params,
            Operation operation) {
        for (boolean color : new boolean[] {false, true}) {
            final String pinType = color ? COLOR : SCALAR;
            final String name = color ? "color" : VALUE;
            final List<NodeTypes.Pin> inputs = new ArrayList<>();
            inputs.add(pin(name, pinType));
            inputs.addAll(extraInputs);
            library.register(
                    type(
                            color ? id + "_color" : id,
                            color ? title + " Color" : title,
                            OPERATIONS,
                            inputs,
                            List.of(pin(RESULT, pinType)),
                            params),
                    context ->
                            context.output(RESULT, operation.apply(context, context.input(name))));
        }
    }

    /**
     * A box blur repeated three times, close to a Gaussian, wrapping.
     *
     * @param in The plane.
     * @param radius The radius, in pixels; fractions blend the next size in.
     * @return The blurred plane.
     */
    static Plane blur(Plane in, float radius) {
        if (radius <= 0) {
            return in;
        }
        final int whole = (int) Math.floor(radius);
        final float part = radius - whole;
        Plane low = in;
        for (int pass = 0; pass < 3; ++pass) {
            low = box(box(low, whole, true), whole, false);
        }
        if (part == 0) {
            return low;
        }
        Plane high = in;
        for (int pass = 0; pass < 3; ++pass) {
            high = box(box(high, whole + 1, true), whole + 1, false);
        }
        final Plane out = new Plane(in.getWidth(), in.getHeight(), in.getChannels());
        for (int y = 0; y < in.getHeight(); ++y) {
            for (int x = 0; x < in.getWidth(); ++x) {
                for (int c = 0; c < in.getChannels(); ++c) {
                    out.set(x, y, c, low.get(x, y, c) * (1 - part) + high.get(x, y, c) * part);
                }
            }
        }
        return out;
    }

    /**
     * One box blur along a direction, wrapping.
     *
     * @param in The plane.
     * @param radius The radius, in whole pixels.
     * @param across Whether along rows, else down columns.
     * @return The blurred plane.
     */
    private static Plane box(Plane in, int radius, boolean across) {
        if (radius == 0) {
            return in;
        }
        final Plane out = new Plane(in.getWidth(), in.getHeight(), in.getChannels());
        final int count = 2 * radius + 1;
        for (int y = 0; y < in.getHeight(); ++y) {
            for (int x = 0; x < in.getWidth(); ++x) {
                for (int c = 0; c < in.getChannels(); ++c) {
                    float sum = 0;
                    for (int k = -radius; k <= radius; ++k) {
                        sum +=
                                across
                                        ? in.get(Math.floorMod(x + k, in.getWidth()), y, c)
                                        : in.get(x, Math.floorMod(y + k, in.getHeight()), c);
                    }
                    out.set(x, y, c, sum / count);
                }
            }
        }
        return out;
    }

    /**
     * A plane read between pixels, blending the four nearest, wrapping.
     *
     * @param in The plane.
     * @param x Across, in pixels, where a pixel's middle is half past it.
     * @param y Down, in pixels.
     * @param c The channel.
     * @return The value.
     */
    static float sample(Plane in, float x, float y, int c) {
        final float fx = x - 0.5f;
        final float fy = y - 0.5f;
        final int x0 = (int) Math.floor(fx);
        final int y0 = (int) Math.floor(fy);
        final float tx = fx - x0;
        final float ty = fy - y0;
        final int w = in.getWidth();
        final int h = in.getHeight();
        final float a = in.get(Math.floorMod(x0, w), Math.floorMod(y0, h), c);
        final float b = in.get(Math.floorMod(x0 + 1, w), Math.floorMod(y0, h), c);
        final float d = in.get(Math.floorMod(x0, w), Math.floorMod(y0 + 1, h), c);
        final float e = in.get(Math.floorMod(x0 + 1, w), Math.floorMod(y0 + 1, h), c);
        return (a + (b - a) * tx) * (1 - ty) + (d + (e - d) * tx) * ty;
    }

    // ---- coordinates

    /**
     * Coordinate offset, rotate, scale, polar and cells.
     *
     * @param library The library.
     */
    private static void coordinates(TextureLibrary library) {
        coordinate(
                library,
                "coords/offset",
                "Offset Coordinates",
                List.of(param("x", "0"), param("y", "0")),
                (context, u, v, out) -> {
                    out[0] = u + context.number("x");
                    out[1] = v + context.number("y");
                });
        coordinate(
                library,
                "coords/rotate",
                "Rotate Coordinates",
                List.of(param("degrees", "0")),
                (context, u, v, out) -> {
                    final double angle = Math.toRadians(context.number("degrees"));
                    final float du = u - 0.5f;
                    final float dv = v - 0.5f;
                    out[0] = (float) (du * Math.cos(angle) - dv * Math.sin(angle)) + 0.5f;
                    out[1] = (float) (du * Math.sin(angle) + dv * Math.cos(angle)) + 0.5f;
                });
        coordinate(
                library,
                "coords/scale",
                "Scale Coordinates",
                List.of(param("x", "1"), param("y", "1")),
                (context, u, v, out) -> {
                    out[0] = (u - 0.5f) * context.number("x") + 0.5f;
                    out[1] = (v - 0.5f) * context.number("y") + 0.5f;
                });
        coordinate(
                library,
                "coords/polar",
                "Polar Coordinates",
                List.of(),
                (context, u, v, out) -> {
                    final float du = u - 0.5f;
                    final float dv = v - 0.5f;
                    out[0] = (float) ((Math.atan2(dv, du) / (2 * Math.PI) + 1) % 1);
                    out[1] = (float) Math.hypot(du, dv) * 2;
                });
        library.register(
                type(
                        "coords/cells",
                        "Cells",
                        COORDS,
                        List.of(pin(COORDINATES_PIN, COORDINATES)),
                        List.of(pin("id", SCALAR), pin("local", COORDINATES)),
                        List.of(param("across", "4"), param("down", "4"))),
                context -> {
                    final int across = Math.max(1, context.integer("across"));
                    final int down = Math.max(1, context.integer("down"));
                    final Plane in =
                            context.linked(COORDINATES_PIN)
                                    ? context.input(COORDINATES_PIN)
                                    : CoreNodes.tileCoordinates(context);
                    final Plane id = context.plane(1);
                    final Plane local = context.plane(2);
                    for (int y = 0; y < context.getHeight(); ++y) {
                        for (int x = 0; x < context.getWidth(); ++x) {
                            final float u = in.get(x, y, 0) * across;
                            final float v = in.get(x, y, 1) * down;
                            final int cu = Math.floorMod((int) Math.floor(u), across);
                            final int cv = Math.floorMod((int) Math.floor(v), down);
                            id.set(
                                    x,
                                    y,
                                    0,
                                    CoreNodes.unit(
                                            Evaluator.mix(
                                                    context.getSeed() ^ (cv * 65_537L + cu))));
                            local.set(x, y, 0, u - (float) Math.floor(u));
                            local.set(x, y, 1, v - (float) Math.floor(v));
                        }
                    }
                    context.output("id", id);
                    context.output("local", local);
                });
    }

    /** Moves one coordinate pair. */
    @FunctionalInterface
    private interface CoordinateMap {
        /**
         * Move a pair.
         *
         * @param context The node's context.
         * @param u Across, as a part of the tile.
         * @param v Down.
         * @param out Gets the new pair.
         */
        void apply(NodeContext context, float u, float v, float[] out);
    }

    /**
     * Register a node that moves coordinates, the tile's own if none are linked.
     *
     * @param library The library.
     * @param id Its ID.
     * @param title Its title.
     * @param params Its parameters.
     * @param map What it does to each pair.
     */
    private static void coordinate(
            TextureLibrary library,
            String id,
            String title,
            List<NodeTypes.Param> params,
            CoordinateMap map) {
        library.register(
                type(
                        id,
                        title,
                        COORDS,
                        List.of(pin(COORDINATES_PIN, COORDINATES)),
                        List.of(pin(COORDINATES_PIN, COORDINATES)),
                        params),
                context -> {
                    final Plane in =
                            context.linked(COORDINATES_PIN)
                                    ? context.input(COORDINATES_PIN)
                                    : CoreNodes.tileCoordinates(context);
                    final Plane out = context.plane(2);
                    final float[] pair = new float[2];
                    for (int y = 0; y < context.getHeight(); ++y) {
                        for (int x = 0; x < context.getWidth(); ++x) {
                            map.apply(context, in.get(x, y, 0), in.get(x, y, 1), pair);
                            out.set(x, y, 0, pair[0]);
                            out.set(x, y, 1, pair[1]);
                        }
                    }
                    context.output(COORDINATES_PIN, out);
                });
    }

    // ---- channels

    /**
     * Component mask, swizzle and append.
     *
     * @param library The library.
     */
    private static void channels(TextureLibrary library) {
        library.register(
                type(
                        "channels/component_mask",
                        "Component Mask",
                        CHANNELS,
                        List.of(pin("color", COLOR)),
                        List.of(pin("color", COLOR)),
                        List.of(
                                param("r", "true"),
                                param("g", "true"),
                                param("b", "true"),
                                param("a", "true"))),
                context -> {
                    final boolean[] keep = {
                        context.flag("r"), context.flag("g"), context.flag("b"), context.flag("a")
                    };
                    // A dropped alpha is opaque, the others zero
                    context.output(
                            "color",
                            context.input("color")
                                    .map((value, c) -> keep[c] ? value : c == 3 ? 1 : 0));
                });
        library.register(
                type(
                        "channels/swizzle",
                        "Swizzle",
                        CHANNELS,
                        List.of(pin("color", COLOR)),
                        List.of(pin("color", COLOR)),
                        List.of(param("order", "rgba"))),
                context -> {
                    final String order = context.text("order").trim().toLowerCase(Locale.ROOT);
                    if (order.length() != 4 || !order.matches("[rgba01]{4}")) {
                        throw new TextureNode.NodeException(
                                "An order is four of r, g, b, a, 0 and 1, like bgra or rrr1");
                    }
                    final Plane in = context.input("color");
                    final Plane out = context.plane(4);
                    for (int y = 0; y < context.getHeight(); ++y) {
                        for (int x = 0; x < context.getWidth(); ++x) {
                            for (int c = 0; c < 4; ++c) {
                                final char from = order.charAt(c);
                                out.set(
                                        x,
                                        y,
                                        c,
                                        switch (from) {
                                            case '0' -> 0;
                                            case '1' -> 1;
                                            default -> in.get(x, y, "rgba".indexOf(from));
                                        });
                            }
                        }
                    }
                    context.output("color", out);
                });
        library.register(
                type(
                        "channels/append",
                        "Append",
                        CHANNELS,
                        List.of(pin("a", SCALAR), pin("b", SCALAR), pin("c", SCALAR)),
                        List.of(pin(COORDINATES_PIN, COORDINATES), pin("vector", VECTOR)),
                        List.of()),
                context -> {
                    final Plane a = context.input("a");
                    final Plane b = context.input("b");
                    final Plane c = context.input("c");
                    final Plane pair = context.plane(2);
                    final Plane triple = context.plane(3);
                    for (int y = 0; y < context.getHeight(); ++y) {
                        for (int x = 0; x < context.getWidth(); ++x) {
                            pair.set(x, y, 0, a.get(x, y, 0));
                            pair.set(x, y, 1, b.get(x, y, 0));
                            triple.set(x, y, 0, a.get(x, y, 0));
                            triple.set(x, y, 1, b.get(x, y, 0));
                            triple.set(x, y, 2, c.get(x, y, 0));
                        }
                    }
                    context.output(COORDINATES_PIN, pair);
                    context.output("vector", triple);
                });
    }
}
