package com.ikalagaming.converter.texgraph;

import static com.ikalagaming.converter.texgraph.CoreNodes.param;
import static com.ikalagaming.converter.texgraph.CoreNodes.pin;
import static com.ikalagaming.converter.texgraph.CoreNodes.type;
import static com.ikalagaming.converter.texgraph.TexturePins.COLOR;
import static com.ikalagaming.converter.texgraph.TexturePins.PALETTE;
import static com.ikalagaming.converter.texgraph.TexturePins.SCALAR;

import lombok.NonNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Color nodes: Gradient Map, HSV Adjust, and the palette nodes that pixel art at 16 pixels a block
 * lives on: Quantize to a palette, with ordered or error-diffusion dither, and Palette, which
 * reduces an image to a few of its own colors. Colors are matched and dithered on their sRGB
 * values, as an artist picks and compares them, and hue and value work there too.
 */
public final class ColorNodes {
    /** The category they are listed under. */
    static final String COLORS = "Color";

    /** Color in and out. */
    static final String COLOR_PIN = "color";

    /** The palette index output. */
    static final String INDEX = "index";

    /** The 4x4 Bayer matrix for ordered dither, 0 to 15. */
    private static final int[] BAYER = {0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5};

    /** Not made. */
    private ColorNodes() {}

    /**
     * Add the color nodes to a library.
     *
     * @param library The library.
     * @return The library.
     */
    public static TextureLibrary register(@NonNull TextureLibrary library) {
        library.register(
                type(
                        "color/gradient_map",
                        "Gradient Map",
                        COLORS,
                        List.of(pin("value", SCALAR)),
                        List.of(pin(COLOR_PIN, COLOR)),
                        List.of(param("stops", "0:#000000 1:#ffffff"))),
                context -> {
                    final List<float[]> stops = stops(context.text("stops"));
                    final Plane in = context.input("value");
                    final Plane out = context.plane(4);
                    for (int y = 0; y < context.getHeight(); ++y) {
                        for (int x = 0; x < context.getWidth(); ++x) {
                            final float[] color = mapped(stops, in.get(x, y, 0));
                            for (int c = 0; c < 4; ++c) {
                                out.set(x, y, c, color[c]);
                            }
                        }
                    }
                    context.output(COLOR_PIN, out);
                });
        library.register(
                type(
                        "color/hsv",
                        "HSV Adjust",
                        COLORS,
                        List.of(pin(COLOR_PIN, COLOR)),
                        List.of(pin(COLOR_PIN, COLOR)),
                        List.of(param("hue", "0"), param("saturation", "1"), param("value", "1"))),
                context -> {
                    final float hue = context.number("hue") / 360f;
                    final float saturation = context.number("saturation");
                    final float value = context.number("value");
                    final Plane in = context.input(COLOR_PIN);
                    final Plane out = context.plane(4);
                    final float[] hsv = new float[3];
                    for (int y = 0; y < context.getHeight(); ++y) {
                        for (int x = 0; x < context.getWidth(); ++x) {
                            toHsv(encode(in, x, y), hsv);
                            hsv[0] = (hsv[0] + hue) % 1 + (hsv[0] + hue < 0 ? 1 : 0);
                            hsv[1] = Math.clamp(hsv[1] * saturation, 0, 1);
                            hsv[2] = Math.max(0, hsv[2] * value);
                            final float[] rgb = fromHsv(hsv);
                            for (int c = 0; c < 3; ++c) {
                                out.set(x, y, c, decode(rgb[c]));
                            }
                            out.set(x, y, 3, in.get(x, y, 3));
                        }
                    }
                    context.output(COLOR_PIN, out);
                });
        library.register(
                type(
                        "color/quantize",
                        "Quantize",
                        COLORS,
                        List.of(pin(COLOR_PIN, COLOR)),
                        List.of(pin(INDEX, PALETTE), pin(COLOR_PIN, COLOR)),
                        List.of(
                                param("palette", "#000000 #ffffff"),
                                param("dither", "none"),
                                param("strength", "1"))),
                context -> {
                    final float[] palette = palette(context.text("palette"));
                    final Plane index =
                            quantize(
                                    context.input(COLOR_PIN),
                                    palette,
                                    context.text("dither").trim().toLowerCase(Locale.ROOT),
                                    context.number("strength"));
                    context.output(INDEX, index);
                    context.output(COLOR_PIN, TexturePins.throughPalette(index));
                });
        library.register(
                type(
                        "color/palette",
                        "Palette",
                        COLORS,
                        List.of(pin(COLOR_PIN, COLOR)),
                        List.of(pin(INDEX, PALETTE), pin(COLOR_PIN, COLOR)),
                        List.of(param("colors", "4"), param("dither", "none"))),
                context -> {
                    final Plane in = context.input(COLOR_PIN);
                    final float[] palette =
                            reduce(in, Math.clamp(context.integer("colors"), 1, 256));
                    final Plane index =
                            quantize(
                                    in,
                                    palette,
                                    context.text("dither").trim().toLowerCase(Locale.ROOT),
                                    1);
                    context.output(INDEX, index);
                    context.output(COLOR_PIN, TexturePins.throughPalette(index));
                });
        return library;
    }

    /**
     * A pixel's color as sRGB values, 0 to 1.
     *
     * @param plane The colors, linear.
     * @param x The column.
     * @param y The row.
     * @return Red, green and blue, encoded.
     */
    private static float[] encode(Plane plane, int x, int y) {
        return new float[] {
            (float) Srgb.fromLinear(Math.clamp(plane.get(x, y, 0), 0, 1)),
            (float) Srgb.fromLinear(Math.clamp(plane.get(x, y, 1), 0, 1)),
            (float) Srgb.fromLinear(Math.clamp(plane.get(x, y, 2), 0, 1))
        };
    }

    /**
     * An sRGB value as linear.
     *
     * @param encoded The value, 0 to 1.
     * @return The linear value.
     */
    private static float decode(float encoded) {
        return (float) Srgb.toLinear(Math.clamp(encoded, 0, 1));
    }

    /**
     * Read colors in hex, separated by spaces: {@code #RRGGBB} or {@code #RRGGBBAA}.
     *
     * @param text The colors.
     * @return Linear RGBA of each in turn.
     * @throws TextureNode.NodeException If one isn't a color, or there are none.
     */
    static float[] palette(String text) {
        final List<float[]> colors = new ArrayList<>();
        for (String part : text.trim().split("[\\s,]+")) {
            if (!part.isEmpty()) {
                colors.add(GraphParameter.color(part));
            }
        }
        if (colors.isEmpty()) {
            throw new TextureNode.NodeException("A palette needs at least one color");
        }
        final float[] out = new float[colors.size() * 4];
        for (int i = 0; i < colors.size(); ++i) {
            System.arraycopy(colors.get(i), 0, out, i * 4, 4);
        }
        return out;
    }

    /**
     * Read gradient stops like {@code 0:#000000 0.5:#ff8800 1:#ffffff}, sorted.
     *
     * @param text The stops.
     * @return Each stop's position, then its linear RGBA.
     * @throws TextureNode.NodeException If they can't be read.
     */
    static List<float[]> stops(String text) {
        final List<float[]> stops = new ArrayList<>();
        for (String part : text.trim().split("\\s+")) {
            final int colon = part.indexOf(':');
            if (colon < 0) {
                throw new TextureNode.NodeException(
                        "Stops are position:#color pairs like 0:#000000 1:#ffffff, not " + part);
            }
            try {
                final float at = Float.parseFloat(part.substring(0, colon));
                final float[] color = GraphParameter.color(part.substring(colon + 1));
                stops.add(new float[] {at, color[0], color[1], color[2], color[3]});
            } catch (NumberFormatException e) {
                throw new TextureNode.NodeException("Not a stop position: " + part);
            }
        }
        if (stops.isEmpty()) {
            throw new TextureNode.NodeException("A gradient needs at least one stop");
        }
        stops.sort(Comparator.comparingDouble(s -> s[0]));
        return stops;
    }

    /**
     * A gradient's color at a value, blending neighboring stops by their sRGB values.
     *
     * @param stops The stops, sorted.
     * @param value Where to read it.
     * @return Linear RGBA.
     */
    private static float[] mapped(List<float[]> stops, float value) {
        if (value <= stops.getFirst()[0]) {
            return rgba(stops.getFirst());
        }
        if (value >= stops.getLast()[0]) {
            return rgba(stops.getLast());
        }
        int i = 0;
        while (value > stops.get(i + 1)[0]) {
            ++i;
        }
        final float[] a = stops.get(i);
        final float[] b = stops.get(i + 1);
        final float t = b[0] == a[0] ? 1 : (value - a[0]) / (b[0] - a[0]);
        final float[] out = new float[4];
        for (int c = 0; c < 3; ++c) {
            final double ea = Srgb.fromLinear(a[c + 1]);
            final double eb = Srgb.fromLinear(b[c + 1]);
            out[c] = (float) Srgb.toLinear(ea + (eb - ea) * t);
        }
        out[3] = a[4] + (b[4] - a[4]) * t;
        return out;
    }

    /**
     * A stop's color.
     *
     * @param stop The stop.
     * @return Its linear RGBA.
     */
    private static float[] rgba(float[] stop) {
        return new float[] {stop[1], stop[2], stop[3], stop[4]};
    }

    /**
     * How far an ordered dither moves each channel at most: the palette's usual distance from a
     * color to its nearest neighbor, per channel. Scaling by the palette's own spacing keeps the
     * pattern between neighboring colors; scaling by the full 0..1 range, as if the palette spread
     * from black to white, throws close palettes into a checkerboard of far apart colors.
     *
     * @param encoded The palette's colors, sRGB values.
     * @return The step, 0 for a palette of one color.
     */
    static float ditherStep(float[][] encoded) {
        if (encoded.length < 2) {
            return 0;
        }
        double sum = 0;
        for (int i = 0; i < encoded.length; ++i) {
            double nearest = Double.MAX_VALUE;
            for (int j = 0; j < encoded.length; ++j) {
                if (i == j) {
                    continue;
                }
                double distance = 0;
                for (int c = 0; c < 3; ++c) {
                    final double d = encoded[i][c] - encoded[j][c];
                    distance += d * d;
                }
                nearest = Math.min(nearest, Math.sqrt(distance));
            }
            sum += nearest;
        }
        // Spread over three channels moving together
        return (float) (sum / encoded.length / Math.sqrt(3));
    }

    /**
     * Each pixel's nearest palette color by sRGB distance, after dither.
     *
     * @param in The colors, linear.
     * @param palette The palette, linear RGBA.
     * @param dither "none", "ordered" or "diffusion".
     * @param strength How strong the dither is.
     * @return The indices, carrying the palette.
     * @throws TextureNode.NodeException If the dither isn't known.
     */
    static Plane quantize(Plane in, float[] palette, String dither, float strength) {
        final int size = palette.length / 4;
        final float[][] encoded = new float[size][3];
        for (int i = 0; i < size; ++i) {
            for (int c = 0; c < 3; ++c) {
                encoded[i][c] = (float) Srgb.fromLinear(Math.clamp(palette[i * 4 + c], 0, 1));
            }
        }
        final float step = ditherStep(encoded);
        final int width = in.getWidth();
        final int height = in.getHeight();
        final float[][] colors = new float[width * height][];
        for (int y = 0; y < height; ++y) {
            for (int x = 0; x < width; ++x) {
                colors[y * width + x] = encode(in, x, y);
            }
        }
        final Plane out = new Plane(width, height, 1);
        for (int y = 0; y < height; ++y) {
            for (int x = 0; x < width; ++x) {
                final float[] color = colors[y * width + x];
                final float[] wanted = color.clone();
                switch (dither) {
                    case "none" -> {
                        // As it is
                    }
                    case "ordered" -> {
                        final float threshold = (BAYER[(y & 3) * 4 + (x & 3)] + 0.5f) / 16 - 0.5f;
                        for (int c = 0; c < 3; ++c) {
                            wanted[c] += threshold * step * strength;
                        }
                    }
                    case "diffusion" -> {
                        // The error was spread into this pixel already
                    }
                    default ->
                            throw new TextureNode.NodeException(
                                    "Dither is none, ordered or diffusion, not " + dither);
                }
                final int nearest = nearest(encoded, wanted);
                out.set(x, y, 0, nearest);
                if ("diffusion".equals(dither)) {
                    // Floyd and Steinberg, wrapping within the row and onto the next one
                    for (int c = 0; c < 3; ++c) {
                        final float error = (color[c] - encoded[nearest][c]) * strength;
                        spread(colors, width, height, x + 1, y, c, error * 7 / 16);
                        if (y + 1 < height) {
                            spread(colors, width, height, x - 1, y + 1, c, error * 3 / 16);
                            spread(colors, width, height, x, y + 1, c, error * 5 / 16);
                            spread(colors, width, height, x + 1, y + 1, c, error / 16);
                        }
                    }
                }
            }
        }
        return out.withPalette(palette);
    }

    /**
     * Add some error to a pixel not yet chosen.
     *
     * @param colors The colors, encoded.
     * @param width The width.
     * @param height The height.
     * @param x The column, wrapping.
     * @param y The row.
     * @param c The channel.
     * @param error How much to add.
     */
    private static void spread(
            float[][] colors, int width, int height, int x, int y, int c, float error) {
        colors[Math.floorMod(y, height) * width + Math.floorMod(x, width)][c] += error;
    }

    /**
     * The palette color nearest a color.
     *
     * @param palette The palette, encoded.
     * @param color The color, encoded.
     * @return Its index; the lowest one on a tie.
     */
    private static int nearest(float[][] palette, float[] color) {
        int best = 0;
        float bestDistance = Float.MAX_VALUE;
        for (int i = 0; i < palette.length; ++i) {
            float distance = 0;
            for (int c = 0; c < 3; ++c) {
                final float d = palette[i][c] - color[c];
                distance += d * d;
            }
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        return best;
    }

    /**
     * A few colors that stand for an image, by median cut on the sRGB values: the box of colors is
     * split at the middle of its longest side until there are enough boxes, and each box's average
     * is a color. Sorted from darkest to lightest, so indices read in order.
     *
     * @param in The image, linear.
     * @param count How many colors.
     * @return The palette, linear RGBA.
     */
    static float[] reduce(Plane in, int count) {
        final List<float[]> all = new ArrayList<>();
        for (int y = 0; y < in.getHeight(); ++y) {
            for (int x = 0; x < in.getWidth(); ++x) {
                all.add(encode(in, x, y));
            }
        }
        final List<List<float[]>> boxes = new ArrayList<>();
        boxes.add(all);
        while (boxes.size() < count) {
            int widest = -1;
            int axis = 0;
            float widestSpan = 0;
            for (int b = 0; b < boxes.size(); ++b) {
                if (boxes.get(b).size() < 2) {
                    continue;
                }
                for (int c = 0; c < 3; ++c) {
                    float low = Float.MAX_VALUE;
                    float high = -Float.MAX_VALUE;
                    for (float[] color : boxes.get(b)) {
                        low = Math.min(low, color[c]);
                        high = Math.max(high, color[c]);
                    }
                    if (high - low > widestSpan) {
                        widestSpan = high - low;
                        widest = b;
                        axis = c;
                    }
                }
            }
            if (widest < 0) {
                break;
            }
            final int sortAxis = axis;
            final List<float[]> box = new ArrayList<>(boxes.remove(widest));
            box.sort(Comparator.comparingDouble(color -> color[sortAxis]));
            boxes.add(new ArrayList<>(box.subList(0, box.size() / 2)));
            boxes.add(new ArrayList<>(box.subList(box.size() / 2, box.size())));
        }
        final List<float[]> averages = new ArrayList<>();
        for (List<float[]> box : boxes) {
            final float[] sum = new float[3];
            for (float[] color : box) {
                for (int c = 0; c < 3; ++c) {
                    sum[c] += color[c];
                }
            }
            for (int c = 0; c < 3; ++c) {
                sum[c] /= box.size();
            }
            averages.add(sum);
        }
        averages.sort(Comparator.comparingDouble(c -> c[0] * 0.299 + c[1] * 0.587 + c[2] * 0.114));
        final float[] out = new float[averages.size() * 4];
        for (int i = 0; i < averages.size(); ++i) {
            for (int c = 0; c < 3; ++c) {
                out[i * 4 + c] = decode(averages.get(i)[c]);
            }
            out[i * 4 + 3] = 1;
        }
        return out;
    }

    /**
     * Red, green and blue as hue, saturation and value, all 0 to 1.
     *
     * @param rgb The color.
     * @param out Gets hue, saturation and value.
     */
    static void toHsv(float[] rgb, float[] out) {
        final float max = Math.max(rgb[0], Math.max(rgb[1], rgb[2]));
        final float min = Math.min(rgb[0], Math.min(rgb[1], rgb[2]));
        final float span = max - min;
        float hue = 0;
        if (span > 0) {
            if (max == rgb[0]) {
                hue = ((rgb[1] - rgb[2]) / span) / 6;
            } else if (max == rgb[1]) {
                hue = ((rgb[2] - rgb[0]) / span + 2) / 6;
            } else {
                hue = ((rgb[0] - rgb[1]) / span + 4) / 6;
            }
            if (hue < 0) {
                hue += 1;
            }
        }
        out[0] = hue;
        out[1] = max == 0 ? 0 : span / max;
        out[2] = max;
    }

    /**
     * Hue, saturation and value as red, green and blue.
     *
     * @param hsv Hue, saturation and value, 0 to 1.
     * @return The color.
     */
    static float[] fromHsv(float[] hsv) {
        final float h = hsv[0] * 6;
        final float s = hsv[1];
        final float v = hsv[2];
        final int sector = Math.floorMod((int) Math.floor(h), 6);
        final float f = h - (float) Math.floor(h);
        final float p = v * (1 - s);
        final float q = v * (1 - s * f);
        final float t = v * (1 - s * (1 - f));
        return switch (sector) {
            case 0 -> new float[] {v, t, p};
            case 1 -> new float[] {q, v, p};
            case 2 -> new float[] {p, v, t};
            case 3 -> new float[] {p, q, v};
            case 4 -> new float[] {t, p, v};
            default -> new float[] {v, p, q};
        };
    }
}
