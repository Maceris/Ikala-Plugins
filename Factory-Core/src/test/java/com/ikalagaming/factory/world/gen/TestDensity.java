package com.ikalagaming.factory.world.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.factory.world.gen.data.Kind;
import com.ikalagaming.factory.world.gen.data.WorldgenCompiler;
import com.ikalagaming.factory.world.gen.density.*;
import com.ikalagaming.factory.world.gen.noise.SimplexNoise;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;

/**
 * Tests for density functions and noise.
 *
 * @author Ches Burks
 */
class TestDensity {

    private static final double EPSILON = 1e-9;

    /**
     * Compile one density from text.
     *
     * @param text The density.
     * @return The node.
     */
    private static DensityNode compile(String text) {
        WorldgenCompiler compiler =
                new WorldgenFixtures.Text().add(Kind.DENSITY, "t:d", text).compileAll();
        assertTrue(!compiler.hasErrors(), compiler.getDiagnostics().toString());
        return compiler.density("t:d", null).orElseThrow();
    }

    /** Hand-worked values for the arithmetic and shapes. */
    @Test
    void testValues() {
        assertEquals(
                7,
                compile(
                                "{type:\"add\", args:[N;{type:\"constant\", value:3.0}, {type:\"constant\", value:4.0}]}")
                        .value(0, 0, 0, null),
                EPSILON);
        assertEquals(
                -5,
                compile("{type:\"neg\", arg:{type:\"axis\", axis:\"y\"}}").value(1, 5, 2, null),
                EPSILON);
        assertEquals(
                27,
                compile("{type:\"cube\", arg:{type:\"axis\", axis:\"x\"}}").value(3, 0, 0, null),
                EPSILON);
        assertEquals(
                1,
                compile("{type:\"clamp\", min:-1.0, max:1.0, arg:{type:\"axis\", axis:\"x\"}}")
                        .value(9, 0, 0, null),
                EPSILON);
        assertEquals(
                10,
                compile("{type:\"remap\", from:[D;0.0,1.0], to:[D;0.0,20.0], arg:0.5}")
                        .value(0, 0, 0, null),
                EPSILON);
        DensityNode spline =
                compile(
                        "{type:\"spline\", arg:{type:\"axis\", axis:\"x\"}, points:[N;{x:0.0, y:0.0}, {x:2.0, y:10.0}]}");
        assertEquals(5, spline.value(1, 0, 0, null), EPSILON);
        assertEquals(10, spline.value(50, 0, 0, null), EPSILON);
        assertEquals(0, spline.value(-50, 0, 0, null), EPSILON);
        assertEquals(
                2.5,
                compile("{type:\"lerp\", t:0.25, a:0.0, b:10.0}").value(0, 0, 0, null),
                EPSILON);
        DensityNode step =
                compile(
                        "{type:\"step\", threshold:0.0, below:-1.0, above:1.0, arg:{type:\"axis\", axis:\"z\"}}");
        assertEquals(-1, step.value(0, 0, -0.5, null), EPSILON);
        assertEquals(1, step.value(0, 0, 0, null), EPSILON);
        DensityNode mask =
                compile(
                        "{type:\"range_mask\", min:0.0, max:1.0, falloff:2.0, arg:{type:\"axis\", axis:\"x\"}}");
        assertEquals(1, mask.value(0.5, 0, 0, null), EPSILON);
        assertEquals(0.5, mask.value(2, 0, 0, null), EPSILON);
        assertEquals(0, mask.value(4, 0, 0, null), EPSILON);
        DensityNode sphere = compile("{type:\"sphere\", center:[D;0.0,0.0,0.0], radius:5.0}");
        assertEquals(5, sphere.value(0, 0, 0, null), EPSILON);
        assertEquals(-5, sphere.value(10, 0, 0, null), EPSILON);
        DensityNode plane = compile("{type:\"plane\", normal:[D;0.0,2.0,0.0], offset:3.0}");
        assertEquals(1, plane.value(0, 2, 0, null), EPSILON);
        DensityNode box =
                compile("{type:\"box\", center:[D;0.0,0.0,0.0], half_size:[D;2.0,2.0,2.0]}");
        assertEquals(2, box.value(0, 0, 0, null), EPSILON);
        assertEquals(-1, box.value(3, 0, 0, null), EPSILON);
        DensityNode translated =
                compile(
                        "{type:\"translate\", offset:[D;10.0,0.0,0.0], arg:{type:\"axis\", axis:\"x\"}}");
        assertEquals(0, translated.value(10, 0, 0, null), EPSILON);
        DensityNode scaled =
                compile("{type:\"scale\", factor:2.0, arg:{type:\"axis\", axis:\"x\"}}");
        assertEquals(5, scaled.value(10, 0, 0, null), EPSILON);
        DensityNode repeated =
                compile(
                        "{type:\"repeat\", period:[D;4.0,0.0,0.0], arg:{type:\"axis\", axis:\"x\"}}");
        assertEquals(1, repeated.value(-7, 0, 0, null), EPSILON);
        DensityNode mirrored =
                compile("{type:\"mirror\", axes:[T;\"x\"], arg:{type:\"axis\", axis:\"x\"}}");
        assertEquals(3, mirrored.value(-3, 0, 0, null), EPSILON);
        DensityNode quantized =
                compile(
                        "{type:\"quantize\", step:[D;4.0,0.0,0.0], arg:{type:\"axis\", axis:\"x\"}}");
        assertEquals(-8, quantized.value(-5, 0, 0, null), EPSILON);
        DensityNode rotated =
                compile(
                        "{type:\"rotate\", axis:\"y\", degrees:90.0, arg:{type:\"axis\", axis:\"x\"}}");
        assertEquals(1, Math.abs(rotated.value(0, 0, 1, null)), EPSILON);
        DensityNode interpolated =
                compile("{type:\"interpolated\", cell:4.0, arg:{type:\"axis\", axis:\"x\"}}");
        assertEquals(3, interpolated.value(3, 0, 0, null), EPSILON);
    }

    /**
     * Every node in a tree.
     *
     * @param root The root.
     * @return The nodes, each once.
     */
    private static Set<DensityNode> nodes(DensityNode root) {
        Set<DensityNode> seen = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        Deque<DensityNode> todo = new ArrayDeque<>();
        todo.push(root);
        while (!todo.isEmpty()) {
            final DensityNode node = todo.pop();
            if (seen.add(node)) {
                node.children().forEach(todo::push);
            }
        }
        return seen;
    }

    /** Every node's bounds hold every value it gives in the box, for every node type. */
    @Test
    void testBoundsHoldValues() {
        WorldgenCompiler compiler = WorldgenFixtures.compiler(WorldgenFixtures.SEED);
        Set<DensityNode> all = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        for (String id : new String[] {"test:terrain", "test:strata"}) {
            all.addAll(nodes(compiler.density(id, null).orElseThrow()));
        }
        all.addAll(nodes(compiler.parameter("test:enclosure", null).orElseThrow().density()));
        Set<String> types = new HashSet<>();
        all.forEach(node -> types.add(node.type()));
        final Set<String> catalogue =
                Set.of(
                        "constant",
                        "noise",
                        "axis",
                        "ref",
                        "add",
                        "mul",
                        "min",
                        "max",
                        "smooth_min",
                        "smooth_max",
                        "neg",
                        "abs",
                        "square",
                        "cube",
                        "clamp",
                        "remap",
                        "spline",
                        "lerp",
                        "step",
                        "range_mask",
                        "translate",
                        "scale",
                        "rotate",
                        "warp",
                        "repeat",
                        "mirror",
                        "quantize",
                        "sphere",
                        "box",
                        "cylinder",
                        "plane",
                        "interpolated",
                        "blur");
        assertTrue(types.containsAll(catalogue), "Fixture misses " + diff(catalogue, types));

        SplittableRandom random = new SplittableRandom(1);
        final int boxes = 40;
        final int points = 40;
        final double spread = 200;
        final double maxSize = 40;
        for (int b = 0; b < boxes; ++b) {
            final double x = random.nextDouble(-spread, spread);
            final double y = random.nextDouble(-spread, spread);
            final double z = random.nextDouble(-spread, spread);
            final Box box =
                    new Box(
                            x,
                            y,
                            z,
                            x + random.nextDouble(maxSize),
                            y + random.nextDouble(maxSize),
                            z + random.nextDouble(maxSize));
            for (DensityNode node : all) {
                final Interval bounds = node.bounds(box);
                for (int p = 0; p < points; ++p) {
                    final double v =
                            node.value(
                                    random.nextDouble(box.minX(), Math.nextUp(box.maxX())),
                                    random.nextDouble(box.minY(), Math.nextUp(box.maxY())),
                                    random.nextDouble(box.minZ(), Math.nextUp(box.maxZ())),
                                    null);
                    assertTrue(
                            v >= bounds.min() - EPSILON && v <= bounds.max() + EPSILON,
                            node.type() + " gave " + v + " outside " + bounds);
                }
            }
        }
    }

    /**
     * What one set has that another doesn't.
     *
     * @param all The bigger set.
     * @param some The smaller one.
     * @return The difference.
     */
    private static Set<String> diff(Set<String> all, Set<String> some) {
        Set<String> missing = new HashSet<>(all);
        missing.removeAll(some);
        return missing;
    }

    /** Nodes know which axes they depend on. */
    @Test
    void testAxes() {
        assertEquals(
                DensityNode.XZ,
                compile("{type:\"noise\", noise:{type:\"simplex\", dimensions:2}}").axes());
        assertEquals(DensityNode.Y, compile("{type:\"axis\", axis:\"y\"}").axes());
        assertEquals(
                DensityNode.ALL,
                compile(
                                "{type:\"add\", args:[N;{type:\"noise\", noise:{type:\"simplex\","
                                        + " dimensions:2}}, {type:\"axis\", axis:\"y\"}]}")
                        .axes());
        assertEquals(0, compile("{type:\"constant\", value:1.0}").axes());
        assertEquals(
                DensityNode.X | DensityNode.Z,
                compile(
                                "{type:\"rotate\", axis:\"y\", degrees:45.0, arg:{type:\"axis\","
                                        + " axis:\"x\"}}")
                        .axes());
    }

    /** The per-chunk cache never changes a value. */
    @Test
    void testCacheChangesNothing() {
        WorldgenCompiler compiler = WorldgenFixtures.compiler(WorldgenFixtures.SEED);
        final DensityNode terrain = compiler.density("test:terrain", null).orElseThrow();
        final DensityNode enclosure =
                compiler.parameter("test:enclosure", null).orElseThrow().density();
        EvalCache cache = new EvalCache();
        SplittableRandom random = new SplittableRandom(2);
        final double spread = 64;
        for (int i = 0; i < 500; ++i) {
            // Whole coordinates, so cached columns get reused
            final double x = Math.floor(random.nextDouble(-spread, spread));
            final double y = Math.floor(random.nextDouble(-spread, spread));
            final double z = Math.floor(random.nextDouble(-spread, spread));
            assertEquals(terrain.value(x, y, z, null), terrain.value(x, y, z, cache));
            assertEquals(enclosure.value(x, y, z, null), enclosure.value(x, y, z, cache));
        }
        assertTrue(cache.getMisses() > 0);
    }

    /** Noise stays in range, at every mode, and far from the origin. */
    @Test
    void testNoiseRange() {
        final double far = 5e9;
        for (SimplexNoise.Mode mode : SimplexNoise.Mode.values()) {
            for (boolean flat : new boolean[] {true, false}) {
                SimplexNoise noise =
                        new SimplexNoise(7, flat, new double[] {1, 1, 1}, mode, 4, 2, 0.5);
                SplittableRandom random = new SplittableRandom(3);
                for (int i = 0; i < 2000; ++i) {
                    final double offset = i % 2 == 0 ? 0 : far;
                    final double v =
                            noise.sample(
                                    0,
                                    offset + random.nextDouble(100),
                                    random.nextDouble(100),
                                    offset + random.nextDouble(100));
                    assertTrue(v >= -1 && v <= 1, mode + " " + v);
                }
            }
        }
    }

    /** 2D noise ignores y, octaves and salts give different fields. */
    @Test
    void testNoiseFields() {
        SimplexNoise flat =
                new SimplexNoise(
                        7, true, new double[] {16, 16, 16}, SimplexNoise.Mode.FBM, 1, 2, 0.5);
        assertEquals(flat.sample(0, 3.5, 0, 9.25), flat.sample(0, 3.5, 900, 9.25));
        SimplexNoise more =
                new SimplexNoise(
                        7, true, new double[] {16, 16, 16}, SimplexNoise.Mode.FBM, 3, 2, 0.5);
        assertNotEquals(flat.sample(0, 3.5, 0, 9.25), more.sample(0, 3.5, 0, 9.25));
        SimplexNoise other =
                new SimplexNoise(
                        8, true, new double[] {16, 16, 16}, SimplexNoise.Mode.FBM, 1, 2, 0.5);
        assertNotEquals(flat.sample(0, 3.5, 0, 9.25), other.sample(0, 3.5, 0, 9.25));

        // Two references to one noise share it; a salt override makes a copy
        WorldgenCompiler compiler =
                new WorldgenFixtures.Text()
                        .add(Kind.NOISE, "t:n", "{type:\"simplex\", wavelength:16.0}")
                        .add(Kind.DENSITY, "t:a", "{type:\"noise\", noise:\"t:n\"}")
                        .add(Kind.DENSITY, "t:b", "{type:\"noise\", noise:\"t:n\"}")
                        .add(
                                Kind.DENSITY,
                                "t:c",
                                "{type:\"noise\", noise:\"t:n\", salt:\"t:other\"}")
                        .compileAll();
        Map<String, Double> values = new java.util.TreeMap<>();
        for (String id : new String[] {"t:a", "t:b", "t:c"}) {
            values.put(id, compiler.density(id, null).orElseThrow().value(1.5, 2.5, 3.5, null));
        }
        assertEquals(values.get("t:a"), values.get("t:b"));
        assertNotEquals(values.get("t:a"), values.get("t:c"));
    }
}
