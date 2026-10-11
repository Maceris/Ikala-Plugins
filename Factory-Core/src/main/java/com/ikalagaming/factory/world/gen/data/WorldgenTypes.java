package com.ikalagaming.factory.world.gen.data;

import com.ikalagaming.factory.world.gen.BlockRules;
import com.ikalagaming.factory.world.gen.Fluids;
import com.ikalagaming.factory.world.gen.density.*;
import com.ikalagaming.factory.world.gen.noise.Noise;
import com.ikalagaming.factory.world.gen.noise.SimplexNoise;

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
    public static final TypeRegistry<DensityFactory> DENSITY = new TypeRegistry<>();

    /** Noise types. */
    public static final TypeRegistry<NoiseFactory> NOISE = new TypeRegistry<>();

    /** Block rule condition types. */
    public static final TypeRegistry<ConditionFactory> CONDITION = new TypeRegistry<>();

    /** Fluid filling types. */
    public static final TypeRegistry<FluidsFactory> FLUIDS = new TypeRegistry<>();

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
     * The fields a type reads, for unknown-field warnings.
     *
     * @param fields The fields besides {@code type}.
     * @return Every field it knows.
     */
    private static Set<String> fields(String... fields) {
        Set<String> known = new HashSet<>(Arrays.asList(fields));
        known.add(TYPE);
        return known;
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
                (r, c) -> {
                    r.warnUnknownFields(fields("value"));
                    return new Sources.Constant(r.get("value").number());
                });
        DENSITY.register(
                "noise",
                (r, c) -> {
                    r.warnUnknownFields(fields("noise", "channel", "salt", "wavelength"));
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
                (r, c) -> {
                    r.warnUnknownFields(fields("axis"));
                    return new Sources.Axis(axis(r.get("axis")));
                });
        for (String type : List.of("ref", "parameter")) {
            DENSITY.register(
                    type,
                    (r, c) -> {
                        r.warnUnknownFields(fields("id"));
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
                    (r, c) -> {
                        r.warnUnknownFields(smooth ? fields(ARGS, "k") : fields(ARGS));
                        final List<DensityNode> args = densities(r.get(ARGS), c);
                        final double k = smooth ? positive(r.get("k")) : 0;
                        return new Arithmetic.Fold(op, args, k);
                    });
        }
        for (Arithmetic.UnaryOp op : Arithmetic.UnaryOp.values()) {
            DENSITY.register(
                    op.name().toLowerCase(Locale.ROOT),
                    (r, c) -> {
                        r.warnUnknownFields(fields(ARG));
                        return new Arithmetic.Unary(op, c.density(r.get(ARG)));
                    });
        }
        DENSITY.register(
                "clamp",
                (r, c) -> {
                    r.warnUnknownFields(fields(ARG, "min", "max"));
                    final double min = r.get("min").number();
                    final double max = r.get("max").number();
                    if (max < min) {
                        r.get("max").error("WORLDGEN_RANGE_REVERSED");
                    }
                    return new Arithmetic.Clamp(c.density(r.get(ARG)), min, Math.max(min, max));
                });
        DENSITY.register(
                "remap",
                (r, c) -> {
                    r.warnUnknownFields(fields(ARG, "from", "to"));
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
                (r, c) -> {
                    r.warnUnknownFields(fields(ARG, "points", "interp"));
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
                        point.warnUnknownFields(Set.of("x", "y"));
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
                (r, c) -> {
                    r.warnUnknownFields(fields("t", "a", "b"));
                    return new Arithmetic.Lerp(
                            c.density(r.get("t")), c.density(r.get("a")), c.density(r.get("b")));
                });
        DENSITY.register(
                "step",
                (r, c) -> {
                    r.warnUnknownFields(fields(ARG, "threshold", "below", "above"));
                    return new Arithmetic.Step(
                            c.density(r.get(ARG)),
                            r.get("threshold").number(),
                            r.get("below").number(),
                            r.get("above").number());
                });
        DENSITY.register(
                "range_mask",
                (r, c) -> {
                    r.warnUnknownFields(fields(ARG, "min", "max", "falloff"));
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
                (r, c) -> {
                    r.warnUnknownFields(fields(ARG, "offset"));
                    return new Transforms.Translate(
                            c.density(r.get(ARG)), r.get("offset").numbers(DensityNode.AXIS_COUNT));
                });
        DENSITY.register(
                "scale",
                (r, c) -> {
                    r.warnUnknownFields(fields(ARG, "factor"));
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
                (r, c) -> {
                    r.warnUnknownFields(fields(ARG, "axis", "degrees"));
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
                (r, c) -> {
                    r.warnUnknownFields(fields(ARG, "by", "amplitude"));
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
                (r, c) -> {
                    r.warnUnknownFields(fields(ARG, "period"));
                    return new Transforms.Repeat(
                            c.density(r.get(ARG)), perAxis(r.get("period"), 1, true));
                });
        DENSITY.register(
                "mirror",
                (r, c) -> {
                    r.warnUnknownFields(fields(ARG, "axes"));
                    int mirrored = 0;
                    for (FieldReader axisField : r.get("axes").list()) {
                        mirrored |= axis(axisField);
                    }
                    return new Transforms.Mirror(c.density(r.get(ARG)), mirrored);
                });
        DENSITY.register(
                "quantize",
                (r, c) -> {
                    r.warnUnknownFields(fields(ARG, "step"));
                    return new Transforms.Quantize(
                            c.density(r.get(ARG)), perAxis(r.get("step"), 1, true));
                });
    }

    /** Register the signed distance shapes. */
    private static void registerShapes() {
        DENSITY.register(
                "sphere",
                (r, c) -> {
                    r.warnUnknownFields(fields("center", "radius"));
                    return new Shapes.Sphere(
                            r.get("center").numbers(DensityNode.AXIS_COUNT),
                            positive(r.get("radius")));
                });
        DENSITY.register(
                "box",
                (r, c) -> {
                    r.warnUnknownFields(fields("center", "half_size", "rounding"));
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
                (r, c) -> {
                    r.warnUnknownFields(fields("center", "axis", "radius", "half_height"));
                    return new Shapes.Cylinder(
                            r.get("center").numbers(DensityNode.AXIS_COUNT),
                            axis(r.get("axis")),
                            positive(r.get("radius")),
                            positive(r.get("half_height")));
                });
        DENSITY.register(
                "plane",
                (r, c) -> {
                    r.warnUnknownFields(fields("normal", "offset"));
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
                (r, c) -> {
                    r.warnUnknownFields(fields(ARG, "cell"));
                    return new Interpolated(
                            c.density(r.get(ARG)),
                            perAxis(r.get("cell"), 1, false),
                            false,
                            c.nextSlot());
                });
        DENSITY.register(
                "blur",
                (r, c) -> {
                    r.warnUnknownFields(fields(ARG, "radius"));
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
                (r, seed, wavelengthOverride, c) -> {
                    r.warnUnknownFields(fields("dimensions", "wavelength", "salt", "fractal"));
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
                        fractal.warnUnknownFields(Set.of("mode", "octaves", "lacunarity", "gain"));
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
                    (r, c) -> {
                        r.warnUnknownFields(fields("min", "max"));
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
                (r, c) -> {
                    r.warnUnknownFields(fields("ids"));
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
                (r, c) -> {
                    r.warnUnknownFields(fields("id", "density", "min", "max"));
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
                "fluid",
                (r, c) -> {
                    r.warnUnknownFields(fields("adjacent"));
                    final boolean wanted = r.get("adjacent").bool(true);
                    return new BlockRules.FluidAdjacent(wanted);
                });
        for (BlockRules.Logic logic : BlockRules.Logic.values()) {
            CONDITION.register(
                    logic.name().toLowerCase(Locale.ROOT),
                    (r, c) -> {
                        r.warnUnknownFields(fields("of"));
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
                (r, c) -> {
                    r.warnUnknownFields(fields());
                    return new Fluids.None();
                });
        FLUIDS.register(
                "level",
                (r, c) -> {
                    r.warnUnknownFields(fields("level", "fluid"));
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
