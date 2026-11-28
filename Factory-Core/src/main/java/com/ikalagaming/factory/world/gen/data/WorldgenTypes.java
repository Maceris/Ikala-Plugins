package com.ikalagaming.factory.world.gen.data;

import com.ikalagaming.factory.world.gen.BlockRules;
import com.ikalagaming.factory.world.gen.Fluids;
import com.ikalagaming.factory.world.gen.density.*;
import com.ikalagaming.factory.world.gen.noise.Noise;
import com.ikalagaming.factory.world.gen.noise.SimplexNoise;
import com.ikalagaming.util.schema.Field;
import com.ikalagaming.util.schema.Schema;

import lombok.NonNull;

import java.util.*;

/**
 * The code-defined types world generation data can use, by kind, with the built-in ones registered.
 * Data can only configure and combine these. Each factory reads its fields, checks them, and warns
 * about fields it doesn't know.
 */
public final class WorldgenTypes {

    /** Builds a density node. */
    @FunctionalInterface
    public interface DensityFactory {
        /**
         * Build a node.
         *
         * @param reader The node's object.
         * @param compiler Reads child values and references.
         * @return The node.
         */
        DensityNode create(@NonNull FieldReader reader, @NonNull WorldgenCompiler compiler);
    }

    /** Builds a noise field. */
    @FunctionalInterface
    public interface NoiseFactory {
        /**
         * Build a noise field.
         *
         * @param reader The noise's object.
         * @param seed The field's seed.
         * @param wavelength A wavelength to use instead of the object's, or null.
         * @param compiler The compiler, for anything else it needs.
         * @return The noise.
         */
        Noise create(
                @NonNull FieldReader reader,
                long seed,
                double[] wavelength,
                @NonNull WorldgenCompiler compiler);
    }

    /** Builds a block rule condition. */
    @FunctionalInterface
    public interface ConditionFactory {
        /**
         * Build a condition.
         *
         * @param reader The condition's object.
         * @param compiler Reads child values and references.
         * @return The condition.
         */
        BlockRules.Condition create(
                @NonNull FieldReader reader, @NonNull WorldgenCompiler compiler);
    }

    /** Builds a way of filling open space with fluid. */
    @FunctionalInterface
    public interface FluidsFactory {
        /**
         * Build the fluids.
         *
         * @param reader The fluids object.
         * @param compiler Reads child values and references.
         * @return The fluids.
         */
        Fluids create(@NonNull FieldReader reader, @NonNull WorldgenCompiler compiler);
    }

    /** Density node types. */
    public static final TypeRegistry<DensityFactory> DENSITY = new TypeRegistry<>("density");

    /** Noise types. */
    public static final TypeRegistry<NoiseFactory> NOISE = new TypeRegistry<>("noise");

    /** Block rule condition types. */
    public static final TypeRegistry<ConditionFactory> CONDITION = new TypeRegistry<>("condition");

    /** Fluid filling types. */
    public static final TypeRegistry<FluidsFactory> FLUIDS = new TypeRegistry<>("fluids");

    /** The default size of a noise's features, in blocks. */
    public static final double DEFAULT_WAVELENGTH = 64;

    /** The most octaves a noise can have. */
    public static final int MAX_OCTAVES = 16;

    /** The default factor each octave's frequency grows by. */
    public static final double DEFAULT_LACUNARITY = 2;

    /** The default factor each octave's amplitude shrinks by. */
    public static final double DEFAULT_GAIN = 0.5;

    /** How many numbers a range is written with: its two ends. */
    private static final int RANGE_LENGTH = 2;

    /** The dimensions of noise that ignores y. */
    private static final int FLAT_DIMENSIONS = 2;

    /** Degrees in a half turn, for converting to radians. */
    private static final double HALF_TURN_DEGREES = 180;

    /** A spline's point. */
    private static final Schema SPLINE_POINT =
            Schema.of(
                    "spline point",
                    WorldgenFields.number("x", "Where it is"),
                    WorldgenFields.number("y", "What the spline gives there"));

    /** A noise's octaves. */
    private static final Schema FRACTAL =
            Schema.of(
                    "fractal",
                    Field.choice("mode", "fbm", "How octaves add up", "fbm", "ridged", "billow"),
                    Field.integer("octaves", 1, 1, MAX_OCTAVES, "How many octaves"),
                    Field.number(
                            "lacunarity",
                            DEFAULT_LACUNARITY,
                            Double.MIN_VALUE,
                            Double.MAX_VALUE,
                            "How much finer each octave is"),
                    Field.number(
                            "gain",
                            DEFAULT_GAIN,
                            Double.MIN_VALUE,
                            Double.MAX_VALUE,
                            "How much fainter each octave is"));

    /** The field every typed object has. */
    private static final String TYPE = "type";

    /** The usual child field. */
    private static final String ARG = "arg";

    /** The usual list of children. */
    private static final String ARGS = "args";

    static {
        registerSources();
        registerArithmetic();
        registerTransforms();
        registerShapes();
        registerSampling();
        registerNoise();
        registerConditions();
        registerFluids();
    }

    /**
     * Read an axis name.
     *
     * @param reader The field holding {@code x}, {@code y} or {@code z}.
     * @return The axis bit, or {@link DensityNode#Y} after an error.
     */
    private static int axis(@NonNull FieldReader reader) {
        return switch (reader.string()) {
            case "x" -> DensityNode.X;
            case "y" -> DensityNode.Y;
            case "z" -> DensityNode.Z;
            default -> {
                reader.error("WORLDGEN_BAD_AXIS");
                yield DensityNode.Y;
            }
        };
    }

    /**
     * Read a number that must be above zero.
     *
     * @param reader The field.
     * @return The number.
     */
    private static double positive(@NonNull FieldReader reader) {
        final double value = reader.number();
        if (value <= 0) {
            reader.error("WORLDGEN_NOT_POSITIVE");
        }
        return value;
    }

    /**
     * Read three numbers, per axis, or one for all of them.
     *
     * @param reader The field.
     * @param min The smallest each may be.
     * @param allowZero Whether 0 is allowed even if below min, meaning "none on this axis".
     * @return The numbers.
     */
    private static double[] perAxis(@NonNull FieldReader reader, double min, boolean allowZero) {
        final double[] values = reader.numbers(DensityNode.AXIS_COUNT);
        for (double value : values) {
            if (value < min && !(allowZero && value == 0)) {
                reader.error("WORLDGEN_OUT_OF_RANGE", min);
                break;
            }
        }
        return values;
    }

    /**
     * Read a list of density values, at least one.
     *
     * @param reader The list.
     * @param compiler The compiler.
     * @return The nodes.
     */
    private static List<DensityNode> densities(
            @NonNull FieldReader reader, @NonNull WorldgenCompiler compiler) {
        final List<FieldReader> elements = reader.list();
        if (elements.isEmpty() && reader.isList()) {
            reader.error("WORLDGEN_EMPTY_LIST");
        }
        List<DensityNode> nodes = new ArrayList<>(elements.size());
        for (FieldReader element : elements) {
            nodes.add(compiler.density(element));
        }
        return nodes.isEmpty() ? List.of(new Sources.Constant(0)) : List.copyOf(nodes);
    }

    /** Register the nodes that produce values. */
    private static void registerSources() {
        DENSITY.register(
                "constant",
                Schema.of("constant", WorldgenFields.number("value", "The value everywhere")),
                (r, c) -> {
                    return new Sources.Constant(r.get("value").number());
                });
        DENSITY.register(
                "noise",
                Schema.of(
                        "noise",
                        WorldgenFields.noise("noise", "The noise sampled"),
                        Field.text(
                                "channel",
                                Noise.VALUE_CHANNEL,
                                "Which of the noise's values to use"),
                        Field.text(
                                "salt",
                                null,
                                "Changes the noise's pattern without changing its kind"),
                        WorldgenFields.optional(
                                WorldgenFields.perAxis(
                                        "wavelength",
                                        "The size of its features, in blocks, in place of the noise's own"))),
                (r, c) -> {
                    final String salt = r.has("salt") ? r.get("salt").string() : null;
                    final double[] wavelength =
                            r.has("wavelength") ? perAxis(r.get("wavelength"), 0, false) : null;
                    final Optional<Noise> noise = c.noise(r.get("noise"), salt, wavelength);
                    if (noise.isEmpty()) {
                        return new Sources.Constant(0);
                    }
                    final FieldReader channelField = r.get("channel");
                    final String channel = channelField.string(Noise.VALUE_CHANNEL);
                    int index = noise.get().channels().indexOf(channel);
                    if (index < 0) {
                        channelField.error(
                                "WORLDGEN_UNKNOWN_CHANNEL",
                                channel,
                                String.join(", ", noise.get().channels()));
                        index = 0;
                    }
                    return new Sources.NoiseNode(
                            noise.get(), index, noise.get().is2D() ? c.nextSlot() : -1);
                });
        DENSITY.register(
                "axis",
                Schema.of("axis", WorldgenFields.axis("axis", "The coordinate it gives")),
                (r, c) -> {
                    return new Sources.Axis(axis(r.get("axis")));
                });
        for (String type : List.of("ref", "parameter")) {
            DENSITY.register(
                    type,
                    "ref".equals(type)
                            ? Schema.of(
                                    "ref",
                                    WorldgenFields.id("id", Kind.DENSITY, "The density file"))
                            : Schema.of(
                                    "parameter",
                                    Field.reference(
                                                    "id",
                                                    WorldgenFields.folder(Kind.PARAMETER),
                                                    null,
                                                    "The parameter, or style: and a terrain style")
                                            .asRequired()),
                    (r, c) -> {
                        final FieldReader id = r.get("id");
                        return c.reference(type, id.string(), id);
                    });
        }
    }

    /** Register the nodes that combine or reshape values. */
    private static void registerArithmetic() {
        for (Arithmetic.FoldOp op : Arithmetic.FoldOp.values()) {
            final boolean smooth =
                    op == Arithmetic.FoldOp.SMOOTH_MIN || op == Arithmetic.FoldOp.SMOOTH_MAX;
            DENSITY.register(
                    op.name().toLowerCase(Locale.ROOT),
                    smooth
                            ? Schema.of(
                                    "smooth fold",
                                    WorldgenFields.densities(ARGS, "The values it combines"),
                                    WorldgenFields.number("k", "How smoothly, above 0"))
                            : Schema.of(
                                    "fold",
                                    WorldgenFields.densities(ARGS, "The values it combines")),
                    (r, c) -> {
                        final List<DensityNode> args = densities(r.get(ARGS), c);
                        final double k = smooth ? positive(r.get("k")) : 0;
                        return new Arithmetic.Fold(op, args, k);
                    });
        }
        for (Arithmetic.UnaryOp op : Arithmetic.UnaryOp.values()) {
            DENSITY.register(
                    op.name().toLowerCase(Locale.ROOT),
                    Schema.of("unary", WorldgenFields.density(ARG, "The value it works on")),
                    (r, c) -> {
                        return new Arithmetic.Unary(op, c.density(r.get(ARG)));
                    });
        }
        DENSITY.register(
                "clamp",
                Schema.of(
                        "clamp",
                        WorldgenFields.density(ARG, "The value it works on"),
                        WorldgenFields.number("min", "The least it gives"),
                        WorldgenFields.number("max", "The most it gives")),
                (r, c) -> {
                    final double min = r.get("min").number();
                    final double max = r.get("max").number();
                    if (max < min) {
                        r.get("max").error("WORLDGEN_RANGE_REVERSED");
                    }
                    return new Arithmetic.Clamp(c.density(r.get(ARG)), min, Math.max(min, max));
                });
        DENSITY.register(
                "remap",
                Schema.of(
                        "remap",
                        WorldgenFields.density(ARG, "The value it works on"),
                        WorldgenFields.range("from", "The range of values it takes"),
                        WorldgenFields.range("to", "The range that range becomes")),
                (r, c) -> {
                    final double[] from = r.get("from").numbers(RANGE_LENGTH);
                    final double[] to = r.get("to").numbers(RANGE_LENGTH);
                    if (from[0] == from[1]) {
                        r.get("from").error("WORLDGEN_EMPTY_RANGE");
                        from[1] = from[0] + 1;
                    }
                    return new Arithmetic.Remap(
                            c.density(r.get(ARG)), from[0], from[1], to[0], to[1]);
                });
        DENSITY.register(
                "spline",
                Schema.of(
                        "spline",
                        WorldgenFields.density(ARG, "The value it works on"),
                        WorldgenFields.objects(
                                "points", SPLINE_POINT, "The points it passes through, x rising"),
                        Field.choice(
                                "interp",
                                "linear",
                                "How it curves between points",
                                "linear",
                                "cubic")),
                (r, c) -> {
                    final FieldReader interp = r.get("interp");
                    final String mode = interp.string("linear");
                    if (!"linear".equals(mode) && !"cubic".equals(mode)) {
                        interp.error("WORLDGEN_BAD_INTERP");
                    }
                    final List<FieldReader> points = r.get("points").list();
                    if (points.isEmpty()) {
                        r.get("points").error("WORLDGEN_EMPTY_LIST");
                        return new Sources.Constant(0);
                    }
                    double[] xs = new double[points.size()];
                    double[] ys = new double[points.size()];
                    for (int i = 0; i < points.size(); ++i) {
                        final FieldReader point = points.get(i);
                        point.warnUnknownFields(SPLINE_POINT);
                        xs[i] = point.get("x").number();
                        ys[i] = point.get("y").number();
                        if (i > 0 && xs[i] <= xs[i - 1]) {
                            point.get("x").error("WORLDGEN_SPLINE_ORDER");
                        }
                    }
                    return new Arithmetic.Spline(
                            c.density(r.get(ARG)), xs, ys, "cubic".equals(mode));
                });
        DENSITY.register(
                "lerp",
                Schema.of(
                        "lerp",
                        WorldgenFields.density("t", "How far from a to b, 0 to 1"),
                        WorldgenFields.density("a", "The value at 0"),
                        WorldgenFields.density("b", "The value at 1")),
                (r, c) -> {
                    return new Arithmetic.Lerp(
                            c.density(r.get("t")), c.density(r.get("a")), c.density(r.get("b")));
                });
        DENSITY.register(
                "step",
                Schema.of(
                        "step",
                        WorldgenFields.density(ARG, "The value it works on"),
                        WorldgenFields.number("threshold", "Where it steps"),
                        WorldgenFields.number("below", "What it gives below the threshold"),
                        WorldgenFields.number("above", "What it gives at or above it")),
                (r, c) -> {
                    return new Arithmetic.Step(
                            c.density(r.get(ARG)),
                            r.get("threshold").number(),
                            r.get("below").number(),
                            r.get("above").number());
                });
        DENSITY.register(
                "range_mask",
                Schema.of(
                        "range_mask",
                        WorldgenFields.density(ARG, "The value it works on"),
                        WorldgenFields.number("min", "Where the range starts"),
                        WorldgenFields.number("max", "Where it ends"),
                        Field.number(
                                "falloff",
                                0,
                                0,
                                Double.MAX_VALUE,
                                "How far it fades out past the ends")),
                (r, c) -> {
                    final double min = r.get("min").number();
                    final double max = r.get("max").number();
                    if (max < min) {
                        r.get("max").error("WORLDGEN_RANGE_REVERSED");
                    }
                    final FieldReader falloffField = r.get("falloff");
                    final double falloff = falloffField.number(0);
                    if (falloff < 0) {
                        falloffField.error("WORLDGEN_OUT_OF_RANGE", 0);
                    }
                    return new Arithmetic.RangeMask(c.density(r.get(ARG)), min, max, falloff);
                });
    }

    /** Register the nodes that change where their child is evaluated. */
    private static void registerTransforms() {
        DENSITY.register(
                "translate",
                Schema.of(
                        "translate",
                        WorldgenFields.density(ARG, "The value it works on"),
                        WorldgenFields.perAxis("offset", "How far it moves the value")),
                (r, c) -> {
                    return new Transforms.Translate(
                            c.density(r.get(ARG)), r.get("offset").numbers(DensityNode.AXIS_COUNT));
                });
        DENSITY.register(
                "scale",
                Schema.of(
                        "scale",
                        WorldgenFields.density(ARG, "The value it works on"),
                        WorldgenFields.perAxis("factor", "How much it stretches the value; not 0")),
                (r, c) -> {
                    final FieldReader factorField = r.get("factor");
                    final double[] factor = factorField.numbers(DensityNode.AXIS_COUNT);
                    for (int i = 0; i < factor.length; ++i) {
                        if (factor[i] == 0) {
                            factorField.error("WORLDGEN_ZERO_SCALE");
                            factor[i] = 1;
                        }
                    }
                    return new Transforms.Scale(c.density(r.get(ARG)), factor);
                });
        DENSITY.register(
                "rotate",
                Schema.of(
                        "rotate",
                        WorldgenFields.density(ARG, "The value it works on"),
                        WorldgenFields.axis("axis", "What it turns about"),
                        WorldgenFields.number("degrees", "How far it turns")),
                (r, c) -> {
                    final double radians =
                            r.get("degrees").number() * StrictMath.PI / HALF_TURN_DEGREES;
                    return new Transforms.Rotate(
                            c.density(r.get(ARG)),
                            axis(r.get("axis")),
                            StrictMath.cos(radians),
                            StrictMath.sin(radians));
                });
        DENSITY.register(
                "warp",
                Schema.of(
                        "warp",
                        WorldgenFields.density(ARG, "The value it works on"),
                        WorldgenFields.densities("by", "How far to move x, y and z: three values"),
                        WorldgenFields.number("amplitude", "How strongly")),
                (r, c) -> {
                    final FieldReader byField = r.get("by");
                    List<DensityNode> by = densities(byField, c);
                    if (by.size() != DensityNode.AXIS_COUNT) {
                        byField.error("WORLDGEN_WRONG_COUNT", DensityNode.AXIS_COUNT, by.size());
                        by =
                                List.of(
                                        new Sources.Constant(0),
                                        new Sources.Constant(0),
                                        new Sources.Constant(0));
                    }
                    return new Transforms.Warp(
                            c.density(r.get(ARG)), by, r.get("amplitude").number());
                });
        DENSITY.register(
                "repeat",
                Schema.of(
                        "repeat",
                        WorldgenFields.density(ARG, "The value it works on"),
                        WorldgenFields.perAxis(
                                "period",
                                "How often it repeats, in blocks; 0 for never on an axis")),
                (r, c) -> {
                    return new Transforms.Repeat(
                            c.density(r.get(ARG)), perAxis(r.get("period"), 1, true));
                });
        DENSITY.register(
                "mirror",
                Schema.of(
                        "mirror",
                        WorldgenFields.density(ARG, "The value it works on"),
                        Field.list(
                                        "axes",
                                        WorldgenFields.axis("axis", "An axis"),
                                        "The axes it mirrors across")
                                .asRequired()),
                (r, c) -> {
                    int mirrored = 0;
                    for (FieldReader axisField : r.get("axes").list()) {
                        mirrored |= axis(axisField);
                    }
                    return new Transforms.Mirror(c.density(r.get(ARG)), mirrored);
                });
        DENSITY.register(
                "quantize",
                Schema.of(
                        "quantize",
                        WorldgenFields.density(ARG, "The value it works on"),
                        WorldgenFields.perAxis(
                                "step", "The size of its steps, in blocks; 0 for none on an axis")),
                (r, c) -> {
                    return new Transforms.Quantize(
                            c.density(r.get(ARG)), perAxis(r.get("step"), 1, true));
                });
    }

    /** Register the signed distance shapes. */
    private static void registerShapes() {
        DENSITY.register(
                "sphere",
                Schema.of(
                        "sphere",
                        WorldgenFields.perAxis("center", "Where it is"),
                        WorldgenFields.number("radius", "How big it is; above 0")),
                (r, c) -> {
                    return new Shapes.Sphere(
                            r.get("center").numbers(DensityNode.AXIS_COUNT),
                            positive(r.get("radius")));
                });
        DENSITY.register(
                "box",
                Schema.of(
                        "box",
                        WorldgenFields.perAxis("center", "Where it is"),
                        WorldgenFields.perAxis("half_size", "Half its size on each axis; above 0"),
                        Field.number(
                                "rounding",
                                0,
                                0,
                                Double.MAX_VALUE,
                                "How round its edges are, up to its smallest half size")),
                (r, c) -> {
                    final double[] half = perAxis(r.get("half_size"), Double.MIN_VALUE, false);
                    final FieldReader roundingField = r.get("rounding");
                    final double rounding = roundingField.number(0);
                    final double smallest = Math.min(half[0], Math.min(half[1], half[2]));
                    if (rounding < 0 || rounding > smallest) {
                        roundingField.error("WORLDGEN_BAD_ROUNDING");
                    }
                    return new Shapes.BoxShape(
                            r.get("center").numbers(DensityNode.AXIS_COUNT),
                            half,
                            Math.clamp(rounding, 0, Math.max(smallest, 0)));
                });
        DENSITY.register(
                "cylinder",
                Schema.of(
                        "cylinder",
                        WorldgenFields.perAxis("center", "Where it is"),
                        WorldgenFields.axis("axis", "Which way it stands"),
                        WorldgenFields.number("radius", "How wide it is; above 0"),
                        WorldgenFields.number("half_height", "Half how tall it is; above 0")),
                (r, c) -> {
                    return new Shapes.Cylinder(
                            r.get("center").numbers(DensityNode.AXIS_COUNT),
                            axis(r.get("axis")),
                            positive(r.get("radius")),
                            positive(r.get("half_height")));
                });
        DENSITY.register(
                "plane",
                Schema.of(
                        "plane",
                        WorldgenFields.perAxis("normal", "Which way it faces; not 0"),
                        WorldgenFields.number(
                                "offset", 0, "How far it is from the origin along the normal")),
                (r, c) -> {
                    final FieldReader normalField = r.get("normal");
                    double[] normal = normalField.numbers(DensityNode.AXIS_COUNT);
                    final double length = Shapes.length(normal[0], normal[1], normal[2]);
                    if (length == 0) {
                        normalField.error("WORLDGEN_ZERO_NORMAL");
                        normal = new double[] {0, 1, 0};
                    } else {
                        for (int i = 0; i < normal.length; ++i) {
                            normal[i] /= length;
                        }
                    }
                    return new Shapes.Plane(normal, r.get("offset").number(0));
                });
    }

    /** Register the coarse sampling nodes. */
    private static void registerSampling() {
        DENSITY.register(
                "interpolated",
                Schema.of(
                        "interpolated",
                        WorldgenFields.density(ARG, "The value it works on"),
                        WorldgenFields.perAxis(
                                "cell",
                                "The size of the cells it samples and blends between, at least 1")),
                (r, c) -> {
                    return new Interpolated(
                            c.density(r.get(ARG)),
                            perAxis(r.get("cell"), 1, false),
                            false,
                            c.nextSlot());
                });
        DENSITY.register(
                "blur",
                Schema.of(
                        "blur",
                        WorldgenFields.density(ARG, "The value it works on"),
                        WorldgenFields.number("radius", "How far it blurs, at least 1")),
                (r, c) -> {
                    final FieldReader radiusField = r.get("radius");
                    final double radius = radiusField.number();
                    if (radius < 1) {
                        radiusField.error("WORLDGEN_OUT_OF_RANGE", 1);
                    }
                    final double cell = Math.max(radius, 1);
                    return new Interpolated(
                            c.density(r.get(ARG)),
                            new double[] {cell, cell, cell},
                            true,
                            c.nextSlot());
                });
    }

    /** Register the noise primitives. */
    private static void registerNoise() {
        NOISE.register(
                "simplex",
                Schema.of(
                        "simplex",
                        Field.integer(
                                "dimensions",
                                DensityNode.AXIS_COUNT,
                                FLAT_DIMENSIONS,
                                DensityNode.AXIS_COUNT,
                                "2 to ignore y, or 3"),
                        WorldgenFields.optional(
                                WorldgenFields.perAxis(
                                        "wavelength",
                                        "The size of its features, in blocks; 64 by default")),
                        Field.text("salt", null, "Changes the pattern; the file's ID by default"),
                        Field.object("fractal", FRACTAL, "Octaves of finer noise added on")),
                (r, seed, wavelengthOverride, c) -> {
                    final FieldReader dimensionsField = r.get("dimensions");
                    final int dimensions = dimensionsField.integer(DensityNode.AXIS_COUNT);
                    if (dimensions != FLAT_DIMENSIONS && dimensions != DensityNode.AXIS_COUNT) {
                        dimensionsField.error("WORLDGEN_BAD_DIMENSIONS");
                    }
                    double[] wavelength = wavelengthOverride;
                    if (wavelength == null) {
                        wavelength =
                                r.has("wavelength")
                                        ? perAxis(r.get("wavelength"), Double.MIN_VALUE, false)
                                        : new double[] {
                                            DEFAULT_WAVELENGTH,
                                            DEFAULT_WAVELENGTH,
                                            DEFAULT_WAVELENGTH
                                        };
                    }
                    SimplexNoise.Mode mode = SimplexNoise.Mode.FBM;
                    int octaves = 1;
                    double lacunarity = DEFAULT_LACUNARITY;
                    double gain = DEFAULT_GAIN;
                    if (r.has("fractal")) {
                        final FieldReader fractal = r.get("fractal");
                        fractal.warnUnknownFields(FRACTAL);
                        final FieldReader modeField = fractal.get("mode");
                        try {
                            mode =
                                    SimplexNoise.Mode.valueOf(
                                            modeField.string("fbm").toUpperCase(Locale.ROOT));
                        } catch (IllegalArgumentException e) {
                            modeField.error("WORLDGEN_BAD_FRACTAL");
                        }
                        final FieldReader octavesField = fractal.get("octaves");
                        octaves = octavesField.integer(1);
                        if (octaves < 1 || octaves > MAX_OCTAVES) {
                            octavesField.error("WORLDGEN_BAD_OCTAVES", MAX_OCTAVES);
                            octaves = 1;
                        }
                        lacunarity =
                                fractal.has("lacunarity")
                                        ? positive(fractal.get("lacunarity"))
                                        : DEFAULT_LACUNARITY;
                        gain = fractal.has("gain") ? positive(fractal.get("gain")) : DEFAULT_GAIN;
                    }
                    return new SimplexNoise(
                            seed,
                            dimensions == FLAT_DIMENSIONS,
                            wavelength,
                            mode,
                            octaves,
                            lacunarity,
                            gain);
                });
    }

    /** Register the block rule conditions. */
    private static void registerConditions() {
        for (String type : List.of("air_above", "air_below")) {
            CONDITION.register(
                    type,
                    Schema.of(
                            "air",
                            Field.integer(
                                    "min", 0, 0, BlockRules.EXPOSURE_CAP, "The least open blocks"),
                            Field.integer(
                                    "max",
                                    BlockRules.EXPOSURE_CAP,
                                    0,
                                    BlockRules.EXPOSURE_CAP,
                                    "The most open blocks")),
                    (r, c) -> {
                        final int min = r.get("min").integer(0);
                        final int max = r.get("max").integer(BlockRules.EXPOSURE_CAP);
                        if (min < 0 || max > BlockRules.EXPOSURE_CAP || max < min) {
                            r.error("WORLDGEN_BAD_EXPOSURE", BlockRules.EXPOSURE_CAP);
                        }
                        return "air_above".equals(type)
                                ? new BlockRules.AirAbove(min, max)
                                : new BlockRules.AirBelow(min, max);
                    });
        }
        CONDITION.register(
                "biome",
                Schema.of(
                        "biome",
                        Field.textList(
                                        "ids",
                                        WorldgenFields.folder(Kind.BIOME),
                                        "The biomes it is true in")
                                .asRequired()),
                (r, c) -> {
                    Set<String> ids = new TreeSet<>();
                    for (FieldReader idField : r.get("ids").list()) {
                        final String id = idField.string();
                        c.checkBiomeExists(idField, id);
                        ids.add(id);
                    }
                    return new BlockRules.InBiome(Set.copyOf(ids));
                });
        CONDITION.register(
                "density",
                Schema.of(
                        "density",
                        Field.reference(
                                "id",
                                WorldgenFields.folder(Kind.DENSITY),
                                null,
                                "A density file; this or density"),
                        WorldgenFields.optional(
                                WorldgenFields.density("density", "A density value; this or id")),
                        Field.number(
                                "min",
                                Double.NEGATIVE_INFINITY,
                                Double.NEGATIVE_INFINITY,
                                Double.POSITIVE_INFINITY,
                                "The least value it is true for"),
                        Field.number(
                                "max",
                                Double.POSITIVE_INFINITY,
                                Double.NEGATIVE_INFINITY,
                                Double.POSITIVE_INFINITY,
                                "The most value it is true for")),
                (r, c) -> {
                    if (r.has("id") == r.has("density")) {
                        r.error("WORLDGEN_DENSITY_NEEDS_ONE");
                    }
                    final DensityNode density =
                            r.has("id")
                                    ? c.reference("ref", r.get("id").string(), r.get("id"))
                                    : c.density(r.get("density"));
                    final double min = r.get("min").number(Double.NEGATIVE_INFINITY);
                    final double max = r.get("max").number(Double.POSITIVE_INFINITY);
                    return new BlockRules.DensityRange(density, min, max);
                });
        CONDITION.register(
                "below",
                Schema.of("below", WorldgenFields.blocks("blocks", "The blocks it is true above")),
                (r, c) -> {
                    Set<String> names = new TreeSet<>();
                    for (FieldReader blockField : r.get("blocks").list()) {
                        names.add(c.block(blockField).getName());
                    }
                    return new BlockRules.Below(Set.copyOf(names));
                });
        CONDITION.register(
                "fluid",
                Schema.of(
                        "fluid",
                        Field.flag("adjacent", true, "True next to fluid, or false away from it")),
                (r, c) -> {
                    final boolean wanted = r.get("adjacent").bool(true);
                    return new BlockRules.FluidAdjacent(wanted);
                });
        for (BlockRules.Logic logic : BlockRules.Logic.values()) {
            CONDITION.register(
                    logic.name().toLowerCase(Locale.ROOT),
                    Schema.of(
                            "logic",
                            Field.list(
                                            "of",
                                            WorldgenFields.condition("condition", "A condition"),
                                            "The conditions it combines")
                                    .asRequired()),
                    (r, c) -> {
                        List<BlockRules.Condition> of = new ArrayList<>();
                        for (FieldReader element : r.get("of").list()) {
                            of.add(c.condition(element));
                        }
                        return new BlockRules.Combined(logic, List.copyOf(of));
                    });
        }
    }

    /** Register the fluid filling types. */
    private static void registerFluids() {
        FLUIDS.register(
                "none",
                Schema.of("none"),
                (r, c) -> {
                    return new Fluids.None();
                });
        FLUIDS.register(
                "level",
                Schema.of(
                        "level",
                        WorldgenFields.density("level", "The height fluid fills up to"),
                        WorldgenFields.block("fluid", "The fluid's block")),
                (r, c) -> {
                    return new Fluids.Level(c.density(r.get("level")), c.block(r.get("fluid")));
                });
    }

    /**
     * Make sure the built-in types are registered. Touching the class runs its static registration;
     * this gives callers a clear way to do that.
     */
    public static void ensureRegistered() {
        // The static initializer does the work
    }

    private WorldgenTypes() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
