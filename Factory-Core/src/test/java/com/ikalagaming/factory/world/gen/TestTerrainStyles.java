package com.ikalagaming.factory.world.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.factory.world.Chunk;
import com.ikalagaming.factory.world.ChunkPos;
import com.ikalagaming.factory.world.World;
import com.ikalagaming.factory.world.gen.data.Kind;
import com.ikalagaming.factory.world.gen.data.WorldgenCompiler;
import com.ikalagaming.factory.world.gen.density.Box;
import com.ikalagaming.factory.world.gen.density.DensityNode;
import com.ikalagaming.factory.world.gen.density.Interval;
import com.ikalagaming.factory.world.gen.density.Styles;

import org.junit.jupiter.api.Test;

import java.util.Random;

/**
 * Tests for terrain styles: blending shapes, overlays, style weights as parameters, the checks on
 * style data, and overlays with their own sampling grid.
 *
 * @author Ches Burks
 */
class TestTerrainStyles {

    /** How close two densities must be to count as equal. */
    private static final double EPSILON = 1e-9;

    /**
     * A data set with one parameter, one biome and the given world type.
     *
     * @param world The world type's text, minus the shared fields.
     * @return The data, ready for styles to be added.
     */
    private static WorldgenFixtures.Text world(String world) {
        return new WorldgenFixtures.Text()
                .add(Kind.PARAMETER, "t:p", "{density:0.0}")
                .add(Kind.BIOME, "t:plain", "{climate:{p:[D;-1.0,1.0]}}")
                .add(
                        Kind.WORLD_TYPE,
                        "t:world",
                        "{default_block:\"t:stone\", parameters:[T;\"t:p\"],"
                                + " biomes:[T;\"t:plain\"], fallback_biome:\"t:plain\", "
                                + world
                                + "}");
    }

    /**
     * A shape style.
     *
     * @param weight The weight's text.
     * @param density The density's text.
     * @return The file's text.
     */
    private static String shape(String weight, String density) {
        return "{kind:\"shape\", weight:" + weight + ", density:" + density + "}";
    }

    /**
     * An overlay style.
     *
     * @param op {@code union} or {@code carve}.
     * @param priority When it applies.
     * @param weight The weight's text.
     * @param density The density's text.
     * @return The file's text.
     */
    private static String overlay(String op, int priority, String weight, String density) {
        return "{kind:\"overlay\", op:\""
                + op
                + "\", priority:"
                + priority
                + ", weight:"
                + weight
                + ", density:"
                + density
                + "}";
    }

    /**
     * Compile the world type, failing with the diagnostics if it doesn't.
     *
     * @param text The data.
     * @return The world type.
     */
    private static WorldType compile(WorldgenFixtures.Text text) {
        final WorldgenCompiler compiler = text.compileAll();
        return compiler.worldType("t:world", null)
                .orElseThrow(() -> new AssertionError(compiler.getDiagnostics().toString()));
    }

    /**
     * Whether compiling found a problem with a code.
     *
     * @param text The data.
     * @param code The diagnostic code.
     * @return True if one was found.
     */
    private static boolean reports(WorldgenFixtures.Text text, String code) {
        return text.compileAll().getDiagnostics().stream().anyMatch(d -> code.equals(d.code()));
    }

    /**
     * The terrain's value at a point.
     *
     * @param type The world type.
     * @param x The x coordinate.
     * @return The value at (x, 0, 0).
     */
    private static double at(WorldType type, double x) {
        return type.density().value(x, 0, 0, null);
    }

    /** Shape styles are averaged by weight, and the fallback fills in below a total of 1. */
    @Test
    void testShapeBlend() {
        // a applies where x >= 0, b everywhere at half strength; the fallback is -5
        final WorldType type =
                compile(
                        world("styles:[T;\"t:a\",\"t:b\"], fallback_style:\"t:fallback\"")
                                .add(
                                        Kind.TERRAIN_STYLE,
                                        "t:a",
                                        shape(
                                                "{type:\"step\", arg:{type:\"axis\", axis:\"x\"},"
                                                        + " threshold:0.0, below:0.0, above:1.0}",
                                                "10.0"))
                                .add(Kind.TERRAIN_STYLE, "t:b", shape("0.5", "-10.0"))
                                .add(Kind.TERRAIN_STYLE, "t:fallback", shape("0.0", "-5.0")));
        assertInstanceOf(Styles.class, type.density());
        // x >= 0: weights 1 and 0.5, total 1.5, so (10 - 5) / 1.5
        assertEquals((10 - 5) / 1.5, at(type, 3), EPSILON);
        // x < 0: only b at 0.5, so half b and half the fallback
        assertEquals(0.5 * -10 + 0.5 * -5, at(type, -3), EPSILON);
    }

    /** Overlays fade in with their weight, and have no effect at all where it is 0. */
    @Test
    void testOverlayFade() {
        final WorldType type =
                compile(
                        world(
                                        "styles:[T;\"t:island\"], fallback_style:\"t:ground\","
                                                + " style_fade:64.0")
                                .add(
                                        Kind.TERRAIN_STYLE,
                                        "t:island",
                                        overlay(
                                                "union",
                                                0,
                                                "{type:\"clamp\", arg:{type:\"axis\", axis:\"x\"},"
                                                        + " min:0.0, max:1.0}",
                                                "5.0"))
                                .add(Kind.TERRAIN_STYLE, "t:ground", shape("1.0", "-100.0")));
        // Weight 0: unchanged, even though -100 is further out than the fade
        assertEquals(-100, at(type, -1), EPSILON);
        // Weight 0.5: halfway from -64 to 5
        assertEquals(-64 + 0.5 * (5 + 64), at(type, 0.5), EPSILON);
        // Weight 1: the overlay's shape
        assertEquals(5, at(type, 1), EPSILON);
    }

    /** Overlays apply from low to high priority. */
    @Test
    void testOverlayOrder() {
        final String ground = shape("1.0", "-1.0");
        final String hole = overlay("carve", 1, "1.0", "-3.0");
        // Union first, then carve: the hole wins
        WorldType type =
                compile(
                        world("styles:[T;\"t:hole\",\"t:island\"], fallback_style:\"t:ground\"")
                                .add(Kind.TERRAIN_STYLE, "t:ground", ground)
                                .add(Kind.TERRAIN_STYLE, "t:hole", hole)
                                .add(
                                        Kind.TERRAIN_STYLE,
                                        "t:island",
                                        overlay("union", 0, "1.0", "2.0")));
        assertEquals(-3, at(type, 0), EPSILON);
        // Carve first, then union: the island wins
        type =
                compile(
                        world("styles:[T;\"t:hole\",\"t:island\"], fallback_style:\"t:ground\"")
                                .add(Kind.TERRAIN_STYLE, "t:ground", ground)
                                .add(Kind.TERRAIN_STYLE, "t:hole", hole)
                                .add(
                                        Kind.TERRAIN_STYLE,
                                        "t:island",
                                        overlay("union", 2, "1.0", "2.0")));
        assertEquals(2, at(type, 0), EPSILON);
    }

    /** Bounds hold every value inside the box, so skipping chunks is safe. */
    @Test
    void testBoundsHoldValues() {
        final String noise =
                "{type:\"noise\", noise:{type:\"simplex\", dimensions:3," + " wavelength:24.0}}";
        final String weight =
                "{type:\"range_mask\", arg:" + noise + ", min:0.0, max:0.4, falloff:0.3}";
        final WorldType type =
                compile(
                        world(
                                        "styles:[T;\"t:hills\",\"t:flat\",\"t:island\","
                                                + "\"t:chasm\"], fallback_style:\"t:flat\"")
                                .add(
                                        Kind.TERRAIN_STYLE,
                                        "t:hills",
                                        shape(
                                                weight,
                                                "{type:\"mul\", args:[N;"
                                                        + noise
                                                        + ",{type:\"constant\", value:30.0}]}"))
                                .add(
                                        Kind.TERRAIN_STYLE,
                                        "t:flat",
                                        shape(
                                                "0.3",
                                                "{type:\"neg\","
                                                        + " arg:{type:\"axis\", axis:\"y\"}}"))
                                .add(
                                        Kind.TERRAIN_STYLE,
                                        "t:island",
                                        overlay(
                                                "union",
                                                0,
                                                weight,
                                                "{type:\"mul\", args:[N;"
                                                        + noise
                                                        + ",{type:\"constant\", value:8.0}]}"))
                                .add(
                                        Kind.TERRAIN_STYLE,
                                        "t:chasm",
                                        "{kind:\"overlay\", op:\"carve\", weight:"
                                                + weight
                                                + ", density:"
                                                + noise
                                                + ", interpolation:[I;1,8,1]}"));
        final DensityNode density = type.density();
        final Random random = new Random(1);
        for (int boxes = 0; boxes < 40; ++boxes) {
            final double x = random.nextInt(2000) - 1000;
            final double y = random.nextInt(200) - 100;
            final double z = random.nextInt(2000) - 1000;
            final Box box = new Box(x, y, z, x + 16, y + 16, z + 16);
            final Interval bounds = density.bounds(box);
            for (int i = 0; i < 50; ++i) {
                final double value =
                        density.value(
                                x + random.nextDouble() * 16,
                                y + random.nextDouble() * 16,
                                z + random.nextDouble() * 16,
                                null);
                assertTrue(
                        bounds.min() - EPSILON <= value && value <= bounds.max() + EPSILON,
                        value + " outside " + bounds);
            }
        }
    }

    /** A biome can ask for a style's weight, and lands where the style applies. */
    @Test
    void testStyleWeightSelectsBiome() {
        final WorldType type =
                compile(
                        world("styles:[T;\"t:islands\"], fallback_style:\"t:ground\"")
                                .add(
                                        Kind.BIOME,
                                        "t:plain",
                                        "{climate:{\"style:t:islands\":[D;0.0,0.4],"
                                                + " p:[D;-1.0,1.0]}}")
                                .add(
                                        Kind.BIOME,
                                        "t:sky",
                                        "{climate:{\"style:t:islands\":[D;0.5,1.0],"
                                                + " p:[D;-1.0,1.0]}}")
                                .add(
                                        Kind.WORLD_TYPE,
                                        "t:world",
                                        "{default_block:\"t:stone\", parameters:[T;\"t:p\"],"
                                                + " biomes:[T;\"t:plain\",\"t:sky\"],"
                                                + " fallback_biome:\"t:plain\","
                                                + " styles:[T;\"t:islands\"],"
                                                + " fallback_style:\"t:ground\"}")
                                .add(
                                        Kind.TERRAIN_STYLE,
                                        "t:islands",
                                        overlay(
                                                "union",
                                                0,
                                                "{type:\"step\", arg:{type:\"axis\", axis:\"x\"},"
                                                        + " threshold:0.0, below:0.0, above:1.0}",
                                                "1.0"))
                                .add(Kind.TERRAIN_STYLE, "t:ground", shape("1.0", "-1.0")));
        assertEquals("style:t:islands", type.parameters().get(type.parameters().size() - 1).id());
        final BiomeSelector selector = new BiomeSelector(type, WorldgenFixtures.SEED);
        assertEquals("t:sky", selector.choose(selector.parameters(10, 0, 0, null), 2, 0, 0).id());
        assertEquals(
                "t:plain", selector.choose(selector.parameters(-10, 0, 0, null), -3, 0, 0).id());
    }

    /** Style weights can be read in any density as a parameter. */
    @Test
    void testStyleWeightAsParameter() {
        final WorldgenCompiler compiler =
                new WorldgenFixtures.Text()
                        .add(Kind.TERRAIN_STYLE, "t:a", shape("2.0", "1.0"))
                        .add(Kind.DENSITY, "t:d", "{type:\"parameter\", id:\"style:t:a\"}")
                        .compileAll();
        // Clamped to 0..1, as it is used
        assertEquals(1, compiler.density("t:d", null).orElseThrow().value(0, 0, 0, null));
    }

    /** A world type with no styles keeps using its density directly. */
    @Test
    void testNoStyles() {
        final WorldType type = compile(world("density:3.0"));
        assertFalse(type.density() instanceof Styles);
        assertEquals(3, at(type, 0), EPSILON);
    }

    /** Problems with style data are reported. */
    @Test
    void testStyleErrors() {
        // A weight that reads terrain
        assertTrue(
                reports(
                        world("styles:[T;\"t:a\",\"t:b\"], fallback_style:\"t:a\"")
                                .add(
                                        Kind.DENSITY,
                                        "t:shape",
                                        "{type:\"neg\", arg:{type:\"axis\"," + " axis:\"y\"}}")
                                .add(Kind.TERRAIN_STYLE, "t:a", shape("1.0", "\"t:shape\""))
                                .add(Kind.TERRAIN_STYLE, "t:b", shape("\"t:shape\"", "0.0")),
                        "WORLDGEN_STYLE_WEIGHT_READS_TERRAIN"));
        // Overlay-only fields on a shape
        assertTrue(
                reports(
                        new WorldgenFixtures.Text()
                                .add(
                                        Kind.TERRAIN_STYLE,
                                        "t:a",
                                        "{kind:\"shape\", weight:1.0, density:0.0,"
                                                + " interpolation:[I;1,1,1]}"),
                        "WORLDGEN_OVERLAY_ONLY"));
        // A fallback that isn't a shape
        assertTrue(
                reports(
                        world("styles:[T;\"t:a\"], fallback_style:\"t:a\"")
                                .add(Kind.TERRAIN_STYLE, "t:a", overlay("union", 0, "1.0", "1.0")),
                        "WORLDGEN_FALLBACK_NOT_SHAPE"));
        // Bad kind and op
        assertTrue(
                reports(
                        new WorldgenFixtures.Text()
                                .add(
                                        Kind.TERRAIN_STYLE,
                                        "t:a",
                                        "{kind:\"blob\", weight:1.0," + " density:0.0}"),
                        "WORLDGEN_BAD_STYLE_KIND"));
        assertTrue(
                reports(
                        new WorldgenFixtures.Text()
                                .add(Kind.TERRAIN_STYLE, "t:a", overlay("paint", 0, "1.0", "0.0")),
                        "WORLDGEN_BAD_STYLE_OP"));
        // A weight that reads its own weight
        assertTrue(
                reports(
                        new WorldgenFixtures.Text()
                                .add(
                                        Kind.TERRAIN_STYLE,
                                        "t:a",
                                        shape("{type:\"parameter\", id:\"style:t:a\"}", "0.0")),
                        "WORLDGEN_REFERENCE_CYCLE"));
    }

    /**
     * Ground below y = 0 with a slot carved where x^2 is under 2, so x = -1, 0 and 1 are open.
     *
     * @param ownGrid Whether the chasm samples at every block across.
     * @return The generator.
     */
    private static WorldGenerator chasm(boolean ownGrid) {
        final String grid = ownGrid ? ", interpolation:[I;1,8,1]" : "";
        final WorldType type =
                compile(
                        world("styles:[T;\"t:chasm\"], fallback_style:\"t:ground\"")
                                .add(
                                        Kind.TERRAIN_STYLE,
                                        "t:ground",
                                        shape(
                                                "1.0",
                                                "{type:\"neg\", arg:{type:\"axis\","
                                                        + " axis:\"y\"}}"))
                                .add(
                                        Kind.TERRAIN_STYLE,
                                        "t:chasm",
                                        "{kind:\"overlay\", op:\"carve\", weight:1.0,"
                                                + " density:{type:\"add\", args:[N;{type:\"square\","
                                                + " arg:{type:\"axis\", axis:\"x\"}},{type:\"constant\", value:-2.0}]}"
                                                + grid
                                                + "}"));
        return new WorldGenerator(type, WorldgenFixtures.SEED);
    }

    /**
     * Whether a block is open.
     *
     * @param chunk The chunk.
     * @param x The block's x in the chunk.
     * @return True if the block at (x, 8, 0) is air.
     */
    private static boolean open(Chunk chunk, int x) {
        return World.AIR.equals(chunk.getBlock(x, 8, 0));
    }

    /** An overlay with its own grid stays sharp, and neighboring chunks agree at their border. */
    @Test
    void testFineOverlay() {
        final WorldGenerator fine = chasm(true);
        final Chunk right = fine.generate(new ChunkPos(0, -1, 0), Stage.TERRAIN);
        final Chunk left = fine.generate(new ChunkPos(-1, -1, 0), Stage.TERRAIN);
        assertTrue(open(right, 0));
        assertTrue(open(right, 1));
        assertFalse(open(right, 2));
        assertTrue(open(left, World.CHUNK_SIZE - 1));
        assertFalse(open(left, World.CHUNK_SIZE - 2));

        // On the world's 4-block grid the curved slot is smeared: only x = 0 stays open
        final WorldGenerator coarse = chasm(false);
        final Chunk smeared = coarse.generate(new ChunkPos(0, -1, 0), Stage.TERRAIN);
        int openCount = 0;
        for (int x = 0; x < World.CHUNK_SIZE; ++x) {
            openCount += open(smeared, x) ? 1 : 0;
        }
        int sharpCount = 0;
        for (int x = 0; x < World.CHUNK_SIZE; ++x) {
            sharpCount += open(right, x) ? 1 : 0;
        }
        assertEquals(2, sharpCount);
        assertTrue(openCount != sharpCount, "coarse " + openCount + " vs fine " + sharpCount);
    }
}
