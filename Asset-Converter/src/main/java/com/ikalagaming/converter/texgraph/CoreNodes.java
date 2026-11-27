package com.ikalagaming.converter.texgraph;

import static com.ikalagaming.converter.texgraph.TexturePins.COLOR;
import static com.ikalagaming.converter.texgraph.TexturePins.COORDINATES;
import static com.ikalagaming.converter.texgraph.TexturePins.HEIGHT;
import static com.ikalagaming.converter.texgraph.TexturePins.MASK;
import static com.ikalagaming.converter.texgraph.TexturePins.SCALAR;
import static com.ikalagaming.converter.texgraph.TexturePins.TEXTURE;
import static com.ikalagaming.converter.texgraph.TexturePins.VECTOR;

import com.ikalagaming.graphics.ui.graph.model.NodeTypes;

import lombok.NonNull;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.imageio.ImageIO;

/**
 * The first texture graph nodes: inputs, coordinates, math, channels, levels and blending, noise,
 * and Height to Normal, which makes the same normal maps the old blockNormals tool did, byte for
 * byte. The rest of the library comes later.
 */
public final class CoreNodes {
    /** Category of input nodes. */
    static final String INPUTS = "Inputs";

    /** Category of math nodes. */
    static final String MATH = "Math";

    /** Category of channel nodes. */
    static final String CHANNELS = "Channels";

    /** Category of adjustment nodes. */
    static final String OPERATIONS = "Operations";

    /** Category of noise nodes. */
    static final String NOISE = "Noise";

    /** Category of coordinate nodes. */
    static final String COORDS = "Coordinates";

    /** Category of nodes that make maps for surfaces. */
    static final String SURFACE = "Surface";

    /** The Image node. */
    public static final String IMAGE = "input/image";

    /** The Constant node. */
    public static final String CONSTANT = "input/constant";

    /** The Color node. */
    public static final String COLOR_INPUT = "input/color";

    /** The Height to Normal node. */
    public static final String HEIGHT_TO_NORMAL = "surface/height_to_normal";

    /** The Luminance node. */
    public static final String LUMINANCE = "channels/luminance";

    /** The file parameter of the Image node. */
    public static final String FILE = "file";

    /** A common output's name. */
    static final String VALUE = "value";

    /** A common output's name. */
    static final String RESULT = "result";

    /** An alpha below this, out of 255, is a hole, as the old blockNormals tool counted it. */
    static final int HOLE_ALPHA = 128;

    /** The sRGB weights of red, green and blue in brightness. */
    private static final float RED_WEIGHT = 0.2126f;

    private static final float GREEN_WEIGHT = 0.7152f;

    private static final float BLUE_WEIGHT = 0.0722f;

    /** Not made. */
    private CoreNodes() {}

    /**
     * Add the core nodes to a library.
     *
     * @param library The library.
     * @return The library.
     */
    public static TextureLibrary register(@NonNull TextureLibrary library) {
        inputs(library);
        math(library);
        channels(library);
        operations(library);
        noise(library);
        surface(library);
        return library;
    }

    /**
     * A node type at version 1.
     *
     * @param id The ID.
     * @param title The title.
     * @param category The category.
     * @param inputs The inputs.
     * @param outputs The outputs.
     * @param params The parameters.
     * @return The type.
     */
    static NodeTypes.NodeType type(
            String id,
            String title,
            String category,
            List<NodeTypes.Pin> inputs,
            List<NodeTypes.Pin> outputs,
            List<NodeTypes.Param> params) {
        return new NodeTypes.NodeType(id, title, category, inputs, outputs, params, 1);
    }

    /**
     * A pin.
     *
     * @param name The name.
     * @param type The type.
     * @return The pin.
     */
    static NodeTypes.Pin pin(String name, String type) {
        return new NodeTypes.Pin(name, type);
    }

    /**
     * A parameter.
     *
     * @param name The name.
     * @param value Its default.
     * @return The parameter.
     */
    static NodeTypes.Param param(String name, String value) {
        return new NodeTypes.Param(name, value);
    }

    // ---- inputs

    /**
     * Image, constant, color, seed and tile coordinates, and sampling a texture.
     *
     * @param library The library.
     */
    private static void inputs(TextureLibrary library) {
        library.register(
                type(
                        CONSTANT,
                        "Constant",
                        INPUTS,
                        List.of(),
                        List.of(pin(VALUE, SCALAR)),
                        List.of(param(VALUE, "0"))),
                context ->
                        context.output(
                                VALUE,
                                Plane.filled(
                                        context.getWidth(),
                                        context.getHeight(),
                                        context.number(VALUE))));
        library.register(
                type(
                        COLOR_INPUT,
                        "Color",
                        INPUTS,
                        List.of(),
                        List.of(pin("color", COLOR)),
                        List.of(param("color", "#808080"), param("alpha", "1"))),
                context -> {
                    final int rgb = hexColor(context.text("color"));
                    context.output(
                            "color",
                            Plane.filled(
                                    context.getWidth(),
                                    context.getHeight(),
                                    Srgb.decode((rgb >> 16) & 0xFF),
                                    Srgb.decode((rgb >> 8) & 0xFF),
                                    Srgb.decode(rgb & 0xFF),
                                    context.number("alpha")));
                });
        library.register(
                type(
                        "input/seed",
                        "Seed",
                        INPUTS,
                        List.of(),
                        List.of(pin(VALUE, SCALAR)),
                        List.of()),
                context ->
                        context.output(
                                VALUE,
                                Plane.filled(
                                        context.getWidth(),
                                        context.getHeight(),
                                        unit(Evaluator.mix(context.getSeed())))));
        library.register(new ImageNode().type(), new ImageNode());
        library.register(
                type(
                        "coords/tile",
                        "Tile Coordinates",
                        COORDS,
                        List.of(),
                        List.of(pin("coordinates", COORDINATES)),
                        List.of()),
                context -> context.output("coordinates", tileCoordinates(context)));
        library.register(
                type(
                        TexturePins.SAMPLE,
                        "Sample",
                        COORDS,
                        List.of(pin("texture", TEXTURE), pin("coordinates", COORDINATES)),
                        List.of(pin("color", COLOR)),
                        List.of()),
                context -> {
                    final Plane texture = context.input("texture");
                    final Plane at =
                            context.linked("coordinates")
                                    ? context.input("coordinates")
                                    : tileCoordinates(context);
                    final Plane out = context.plane(4);
                    for (int y = 0; y < context.getHeight(); ++y) {
                        for (int x = 0; x < context.getWidth(); ++x) {
                            final int sx = (int) Math.floor(at.get(x, y, 0) * texture.getWidth());
                            final int sy = (int) Math.floor(at.get(x, y, 1) * texture.getHeight());
                            for (int c = 0; c < 4; ++c) {
                                out.set(x, y, c, texture.get(sx, sy, c));
                            }
                        }
                    }
                    context.output("color", out);
                });
    }

    /**
     * Each pixel's middle as a fraction of the tile.
     *
     * @param context The node's context.
     * @return The coordinates.
     */
    static Plane tileCoordinates(NodeContext context) {
        final Plane out = context.plane(2);
        for (int y = 0; y < context.getHeight(); ++y) {
            for (int x = 0; x < context.getWidth(); ++x) {
                out.set(x, y, 0, (x + 0.5f) / context.getWidth());
                out.set(x, y, 1, (y + 0.5f) / context.getHeight());
            }
        }
        return out;
    }

    /**
     * Read {@code #RRGGBB}.
     *
     * @param text The color.
     * @return The packed RGB.
     * @throws TextureNode.NodeException If it isn't a color.
     */
    static int hexColor(String text) {
        final String trimmed = text.trim();
        if (trimmed.length() == 7 && trimmed.charAt(0) == '#') {
            try {
                return Integer.parseInt(trimmed.substring(1), 16);
            } catch (NumberFormatException e) {
                // Falls through to the error below
            }
        }
        throw new TextureNode.NodeException("A color is #RRGGBB in hex, not " + text);
    }

    /**
     * A number from 0 up to 1 from random bits.
     *
     * @param bits The bits.
     * @return The number.
     */
    static float unit(long bits) {
        return (bits >>> 40) / (float) (1L << 24);
    }

    /**
     * Reads a PNG: its color at the graph's size from an offset, wrapping, as a texture at its own
     * size, and its brightness and alpha as the file stores them, for heights and holes.
     */
    static final class ImageNode implements TextureNode {
        /**
         * The node's type.
         *
         * @return The type.
         */
        NodeTypes.NodeType type() {
            return CoreNodes.type(
                    IMAGE,
                    "Image",
                    INPUTS,
                    List.of(),
                    List.of(
                            pin("color", COLOR),
                            pin("brightness", SCALAR),
                            pin("alpha", MASK),
                            pin("texture", TEXTURE)),
                    List.of(param(FILE, ""), param("x", "0"), param("y", "0")));
        }

        @Override
        public void evaluate(@NonNull NodeContext context) {
            final BufferedImage image = read(context.getFolder(), context.text(FILE));
            final int left = context.integer("x");
            final int top = context.integer("y");
            final int width = image.getWidth();
            final int height = image.getHeight();
            final Plane color = context.plane(4);
            final Plane brightness = context.plane(1);
            final Plane alpha = context.plane(1);
            for (int y = 0; y < context.getHeight(); ++y) {
                for (int x = 0; x < context.getWidth(); ++x) {
                    final int argb =
                            image.getRGB(
                                    Math.floorMod(left + x, width), Math.floorMod(top + y, height));
                    final int a = argb >>> 24;
                    color.set(x, y, 0, Srgb.decode((argb >> 16) & 0xFF));
                    color.set(x, y, 1, Srgb.decode((argb >> 8) & 0xFF));
                    color.set(x, y, 2, Srgb.decode(argb & 0xFF));
                    color.set(x, y, 3, a / (float) Srgb.MAX_BYTE);
                    brightness.set(x, y, 0, storedBrightness(argb));
                    alpha.set(x, y, 0, a / (float) Srgb.MAX_BYTE);
                }
            }
            final Plane texture = new Plane(width, height, 4);
            for (int y = 0; y < height; ++y) {
                for (int x = 0; x < width; ++x) {
                    final int argb = image.getRGB(x, y);
                    texture.set(x, y, 0, Srgb.decode((argb >> 16) & 0xFF));
                    texture.set(x, y, 1, Srgb.decode((argb >> 8) & 0xFF));
                    texture.set(x, y, 2, Srgb.decode(argb & 0xFF));
                    texture.set(x, y, 3, (argb >>> 24) / (float) Srgb.MAX_BYTE);
                }
            }
            context.output("color", color);
            context.output("brightness", brightness);
            context.output("alpha", alpha);
            context.output("texture", texture);
        }

        @Override
        public String fingerprint(@NonNull Map<String, String> params, Path folder) {
            try {
                final Path file = resolve(folder, params.getOrDefault(FILE, ""));
                if (file == null || !Files.isRegularFile(file)) {
                    return "missing";
                }
                return Long.toHexString(Evaluator.hashBytes(Files.readAllBytes(file)));
            } catch (IOException | InvalidPathException e) {
                return "unreadable";
            }
        }

        /**
         * Read an image file.
         *
         * @param folder Where relative names start, or null.
         * @param name The file's name.
         * @return The image.
         * @throws TextureNode.NodeException If it can't be read.
         */
        static BufferedImage read(Path folder, String name) {
            if (name.isBlank()) {
                throw new TextureNode.NodeException("Choose an image file");
            }
            final Path file;
            try {
                file = resolve(folder, name);
            } catch (InvalidPathException e) {
                throw new TextureNode.NodeException("Not a file name: " + name);
            }
            if (file == null) {
                throw new TextureNode.NodeException(
                        "Save the graph first, so " + name + " can be found beside it");
            }
            try {
                final BufferedImage image = ImageIO.read(file.toFile());
                if (image == null) {
                    throw new TextureNode.NodeException(name + " isn't an image");
                }
                return image;
            } catch (IOException e) {
                throw new TextureNode.NodeException("Can't read " + name + ": " + e.getMessage());
            }
        }

        /**
         * Where a file name points.
         *
         * @param folder Where relative names start, or null.
         * @param name The name, relative or absolute.
         * @return The path, or null for a relative name with no folder.
         */
        static Path resolve(Path folder, String name) {
            final Path path = Path.of(name);
            if (path.isAbsolute()) {
                return path;
            }
            return folder == null ? null : folder.resolve(path).normalize();
        }
    }

    /**
     * A pixel's brightness as its stored sRGB values weigh to the eye, as the old blockNormals tool
     * worked heights out from a texture.
     *
     * @param argb The packed pixel.
     * @return The brightness, 0 to 1.
     */
    static float storedBrightness(int argb) {
        final float red = ((argb >> 16) & 0xFF) / (float) Srgb.MAX_BYTE;
        final float green = ((argb >> 8) & 0xFF) / (float) Srgb.MAX_BYTE;
        final float blue = (argb & 0xFF) / (float) Srgb.MAX_BYTE;
        return RED_WEIGHT * red + GREEN_WEIGHT * green + BLUE_WEIGHT * blue;
    }

    // ---- math

    /** A function of one value. */
    @FunctionalInterface
    private interface Unary {
        /**
         * The result.
         *
         * @param value The value.
         * @return The result.
         */
        float apply(float value);
    }

    /** A function of two values. */
    @FunctionalInterface
    private interface Binary {
        /**
         * The result.
         *
         * @param a The first value.
         * @param b The second value.
         * @return The result.
         */
        float apply(float a, float b);
    }

    /**
     * Math on scalars, pixel by pixel.
     *
     * @param library The library.
     */
    private static void math(TextureLibrary library) {
        binary(library, "add", "Add", Float::sum);
        binary(library, "subtract", "Subtract", (a, b) -> a - b);
        binary(library, "multiply", "Multiply", (a, b) -> a * b);
        binary(library, "divide", "Divide", (a, b) -> b == 0 ? 0 : a / b);
        binary(library, "min", "Min", Math::min);
        binary(library, "max", "Max", Math::max);
        binary(library, "power", "Power", (a, b) -> (float) Math.pow(Math.max(a, 0), b));
        unary(library, "abs", "Abs", Math::abs);
        unary(library, "fraction", "Fraction", value -> value - (float) Math.floor(value));
        unary(library, "one_minus", "One Minus", value -> 1 - value);
        unary(
                library,
                "sine",
                "Sine",
                value -> (float) Math.sin(value * 2 * Math.PI) * 0.5f + 0.5f);
        library.register(
                type(
                        "math/lerp",
                        "Lerp",
                        MATH,
                        List.of(pin("a", SCALAR), pin("b", SCALAR), pin("t", MASK)),
                        List.of(pin(RESULT, SCALAR)),
                        List.of()),
                context -> {
                    final Plane a = context.input("a");
                    final Plane b = context.input("b");
                    final Plane t = context.input("t");
                    final Plane out = context.plane(1);
                    for (int y = 0; y < context.getHeight(); ++y) {
                        for (int x = 0; x < context.getWidth(); ++x) {
                            final float along = t.get(x, y, 0);
                            out.set(
                                    x,
                                    y,
                                    0,
                                    a.get(x, y, 0) + (b.get(x, y, 0) - a.get(x, y, 0)) * along);
                        }
                    }
                    context.output(RESULT, out);
                });
        library.register(
                type(
                        "math/clamp",
                        "Clamp",
                        MATH,
                        List.of(pin(VALUE, SCALAR)),
                        List.of(pin(RESULT, SCALAR)),
                        List.of(param("min", "0"), param("max", "1"))),
                context -> {
                    final float low = context.number("min");
                    final float high = Math.max(low, context.number("max"));
                    context.output(
                            RESULT,
                            context.input(VALUE).map((value, c) -> Math.clamp(value, low, high)));
                });
        library.register(
                type(
                        "math/step",
                        "Step",
                        MATH,
                        List.of(pin(VALUE, SCALAR)),
                        List.of(pin(RESULT, MASK)),
                        List.of(param("edge", "0.5"))),
                context -> {
                    final float edge = context.number("edge");
                    context.output(
                            RESULT, context.input(VALUE).map((value, c) -> value < edge ? 0 : 1));
                });
        library.register(
                type(
                        "math/smoothstep",
                        "Smoothstep",
                        MATH,
                        List.of(pin(VALUE, SCALAR)),
                        List.of(pin(RESULT, MASK)),
                        List.of(param("low", "0"), param("high", "1"))),
                context -> {
                    final float low = context.number("low");
                    final float high = context.number("high");
                    context.output(
                            RESULT,
                            context.input(VALUE)
                                    .map(
                                            (value, c) -> {
                                                if (high <= low) {
                                                    return value < low ? 0 : 1;
                                                }
                                                final float t =
                                                        Math.clamp(
                                                                (value - low) / (high - low), 0, 1);
                                                return t * t * (3 - 2 * t);
                                            }));
                });
    }

    /**
     * Register a math node of two scalars.
     *
     * @param library The library.
     * @param name The ID after {@code math/}.
     * @param title The title.
     * @param function What it does.
     */
    private static void binary(TextureLibrary library, String name, String title, Binary function) {
        library.register(
                type(
                        "math/" + name,
                        title,
                        MATH,
                        List.of(pin("a", SCALAR), pin("b", SCALAR)),
                        List.of(pin(RESULT, SCALAR)),
                        List.of()),
                context -> {
                    final Plane a = context.input("a");
                    final Plane b = context.input("b");
                    final Plane out = context.plane(1);
                    for (int y = 0; y < context.getHeight(); ++y) {
                        for (int x = 0; x < context.getWidth(); ++x) {
                            out.set(x, y, 0, function.apply(a.get(x, y, 0), b.get(x, y, 0)));
                        }
                    }
                    context.output(RESULT, out);
                });
    }

    /**
     * Register a math node of one scalar.
     *
     * @param library The library.
     * @param name The ID after {@code math/}.
     * @param title The title.
     * @param function What it does.
     */
    private static void unary(TextureLibrary library, String name, String title, Unary function) {
        library.register(
                type(
                        "math/" + name,
                        title,
                        MATH,
                        List.of(pin(VALUE, SCALAR)),
                        List.of(pin(RESULT, SCALAR)),
                        List.of()),
                context ->
                        context.output(
                                RESULT,
                                context.input(VALUE).map((value, c) -> function.apply(value))));
    }

    // ---- channels

    /**
     * Split, combine and luminance.
     *
     * @param library The library.
     */
    private static void channels(TextureLibrary library) {
        library.register(
                type(
                        "channels/split",
                        "Split",
                        CHANNELS,
                        List.of(pin("color", COLOR)),
                        List.of(
                                pin("r", SCALAR),
                                pin("g", SCALAR),
                                pin("b", SCALAR),
                                pin("a", SCALAR)),
                        List.of()),
                context -> {
                    final Plane color = context.input("color");
                    final String[] names = {"r", "g", "b", "a"};
                    for (int c = 0; c < 4; ++c) {
                        final Plane out = context.plane(1);
                        for (int y = 0; y < context.getHeight(); ++y) {
                            for (int x = 0; x < context.getWidth(); ++x) {
                                out.set(x, y, 0, color.get(x, y, c));
                            }
                        }
                        context.output(names[c], out);
                    }
                });
        library.register(
                type(
                        "channels/combine",
                        "Combine",
                        CHANNELS,
                        List.of(
                                pin("r", SCALAR),
                                pin("g", SCALAR),
                                pin("b", SCALAR),
                                pin("a", SCALAR)),
                        List.of(pin("color", COLOR)),
                        List.of()),
                context -> {
                    final String[] names = {"r", "g", "b", "a"};
                    final Plane out = context.plane(4);
                    for (int c = 0; c < 4; ++c) {
                        // An unlinked alpha is opaque
                        final Plane in =
                                c == 3 && !context.linked("a")
                                        ? Plane.filled(context.getWidth(), context.getHeight(), 1)
                                        : context.input(names[c]);
                        for (int y = 0; y < context.getHeight(); ++y) {
                            for (int x = 0; x < context.getWidth(); ++x) {
                                out.set(x, y, c, in.get(x, y, 0));
                            }
                        }
                    }
                    context.output("color", out);
                });
        library.register(
                type(
                        LUMINANCE,
                        "Luminance",
                        CHANNELS,
                        List.of(pin("color", COLOR)),
                        List.of(pin(VALUE, SCALAR)),
                        List.of()),
                context -> {
                    // Brightness as the eye sees it, which is of the sRGB values, not linear ones
                    final Plane color = context.input("color");
                    final Plane out = context.plane(1);
                    for (int y = 0; y < context.getHeight(); ++y) {
                        for (int x = 0; x < context.getWidth(); ++x) {
                            out.set(
                                    x,
                                    y,
                                    0,
                                    RED_WEIGHT * encoded(color.get(x, y, 0))
                                            + GREEN_WEIGHT * encoded(color.get(x, y, 1))
                                            + BLUE_WEIGHT * encoded(color.get(x, y, 2)));
                        }
                    }
                    context.output(VALUE, out);
                });
    }

    /**
     * A linear value as sRGB encodes it, 0 to 1.
     *
     * @param linear The linear value.
     * @return The encoded value.
     */
    private static float encoded(float linear) {
        return (float) Srgb.fromLinear(Math.clamp(linear, 0, 1));
    }

    // ---- operations

    /**
     * Levels and blending.
     *
     * @param library The library.
     */
    private static void operations(TextureLibrary library) {
        library.register(
                type(
                        "ops/levels",
                        "Levels",
                        OPERATIONS,
                        List.of(pin(VALUE, SCALAR)),
                        List.of(pin(RESULT, SCALAR)),
                        List.of(
                                param("in low", "0"),
                                param("in high", "1"),
                                param("gamma", "1"),
                                param("out low", "0"),
                                param("out high", "1"))),
                context -> {
                    final float inLow = context.number("in low");
                    final float inHigh = context.number("in high");
                    final float gamma = Math.max(1e-3f, context.number("gamma"));
                    final float outLow = context.number("out low");
                    final float outHigh = context.number("out high");
                    final float span = inHigh - inLow;
                    context.output(
                            RESULT,
                            context.input(VALUE)
                                    .map(
                                            (value, c) -> {
                                                final float t =
                                                        span == 0
                                                                ? (value < inLow ? 0 : 1)
                                                                : Math.clamp(
                                                                        (value - inLow) / span,
                                                                        0,
                                                                        1);
                                                final float curved = (float) Math.pow(t, 1 / gamma);
                                                return outLow + (outHigh - outLow) * curved;
                                            }));
                });
        library.register(
                type(
                        "ops/blend",
                        "Blend",
                        OPERATIONS,
                        List.of(pin("base", COLOR), pin("top", COLOR), pin("mask", MASK)),
                        List.of(pin("color", COLOR)),
                        List.of(param("mode", "mix"), param("opacity", "1"))),
                context -> {
                    final Plane base = context.input("base");
                    final Plane top = context.input("top");
                    final Plane mask =
                            context.linked("mask")
                                    ? context.input("mask")
                                    : Plane.filled(context.getWidth(), context.getHeight(), 1);
                    final float opacity = context.number("opacity");
                    final String mode = context.text("mode").trim().toLowerCase(Locale.ROOT);
                    final Binary blend =
                            switch (mode) {
                                case "mix" -> (a, b) -> b;
                                case "multiply" -> (a, b) -> a * b;
                                case "add" -> Float::sum;
                                case "screen" -> (a, b) -> 1 - (1 - a) * (1 - b);
                                case "overlay" ->
                                        (a, b) -> a < 0.5f ? 2 * a * b : 1 - 2 * (1 - a) * (1 - b);
                                default ->
                                        throw new TextureNode.NodeException(
                                                "No blend mode called "
                                                        + mode
                                                        + "; mix, multiply, add, screen or overlay");
                            };
                    final Plane out = context.plane(4);
                    for (int y = 0; y < context.getHeight(); ++y) {
                        for (int x = 0; x < context.getWidth(); ++x) {
                            final float amount = mask.get(x, y, 0) * opacity;
                            for (int c = 0; c < 4; ++c) {
                                final float a = base.get(x, y, c);
                                final float blended = c == 3 ? a : blend.apply(a, top.get(x, y, c));
                                out.set(x, y, c, a + (blended - a) * amount);
                            }
                        }
                    }
                    context.output("color", out);
                });
    }

    // ---- noise

    /**
     * Value, gradient, cell, simplex and ridged noise, each with octaves for fBm and a domain warp.
     * Every noise is periodic over the tile, so tiles never show a seam.
     *
     * @param library The library.
     */
    private static void noise(TextureLibrary library) {
        for (Noise.Kind kind : Noise.Kind.values()) {
            library.register(
                    type(
                            "noise/" + kind.name().toLowerCase(Locale.ROOT),
                            kind.title(),
                            NOISE,
                            List.of(),
                            List.of(pin(VALUE, SCALAR)),
                            List.of(
                                    param("size", "4"),
                                    param("octaves", "1"),
                                    param("persistence", "0.5"),
                                    param("warp", "0"))),
                    context ->
                            context.output(
                                    VALUE,
                                    Noise.make(
                                            kind,
                                            context.getWidth(),
                                            context.getHeight(),
                                            context.number("size"),
                                            Math.clamp(context.integer("octaves"), 1, 8),
                                            context.number("persistence"),
                                            context.number("warp"),
                                            context.getSeed())));
        }
    }

    // ---- surface

    /**
     * Height to Normal.
     *
     * @param library The library.
     */
    private static void surface(TextureLibrary library) {
        library.register(
                type(
                        HEIGHT_TO_NORMAL,
                        "Height to Normal",
                        SURFACE,
                        List.of(pin("height", HEIGHT), pin("coverage", MASK)),
                        List.of(pin("normal", VECTOR)),
                        List.of(param("strength", "1.5"), param("normalize", "true"))),
                context -> {
                    final Plane coverage =
                            context.linked("coverage")
                                    ? context.input("coverage")
                                    : Plane.filled(context.getWidth(), context.getHeight(), 1);
                    context.output(
                            "normal",
                            Normals.make(
                                    context.input("height"),
                                    coverage,
                                    context.number("strength"),
                                    context.flag("normalize")));
                });
    }
}
