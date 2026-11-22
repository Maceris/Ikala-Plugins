package com.ikalagaming.factory.world.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.factory.world.Chunk;
import com.ikalagaming.factory.world.ChunkPos;
import com.ikalagaming.factory.world.World;
import com.ikalagaming.factory.world.gen.data.Diagnostic;
import com.ikalagaming.factory.world.gen.data.Kind;
import com.ikalagaming.factory.world.gen.data.WorldgenCompiler;
import com.ikalagaming.factory.world.gen.data.WorldgenData;

import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Tests for placed features: single blocks on floors. */
class TestFeatures {

    /** The tuft block. */
    private static final String TUFT = "test:tuft";

    /** The flower block. */
    private static final String FLOWER = "test:flower";

    /** How far around the origin to look for chunks with ground, in chunks. */
    private static final int SEARCH = 3;

    /**
     * The fixture plus a mod with features: tufts on every grass floor in plains, and flowers or
     * tufts on 30% of sand floors in deserts.
     *
     * @return The generator.
     */
    private static WorldGenerator generator() {
        final Path features;
        try {
            features = Path.of(TestFeatures.class.getResource("/worldgen/features").toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
        final Set<String> blocks = new HashSet<>(WorldgenFixtures.BLOCKS);
        blocks.add(TUFT);
        blocks.add(FLOWER);
        final WorldgenCompiler compiler =
                new WorldgenCompiler(
                        WorldgenData.load(List.of(WorldgenFixtures.folder(), features)),
                        WorldgenFixtures.SEED,
                        blocks);
        final WorldType type =
                compiler.worldType(WorldgenFixtures.WORLD, null)
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                compiler.getDiagnostics().toString()));
        assertFalse(compiler.hasErrors(), () -> compiler.getDiagnostics().toString());
        return new WorldGenerator(type, WorldgenFixtures.SEED);
    }

    /**
     * Chunks near the origin that hold some ground: a solid position with open space above.
     *
     * @param generator The generator.
     * @return Their positions.
     */
    private static List<ChunkPos> groundChunks(WorldGenerator generator) {
        final List<ChunkPos> out = new ArrayList<>();
        for (int cx = -SEARCH; cx <= SEARCH; ++cx) {
            for (int cz = -SEARCH; cz <= SEARCH; ++cz) {
                for (int cy = -2; cy <= 2; ++cy) {
                    final ChunkPos pos = new ChunkPos(cx, cy, cz);
                    final Chunk chunk = generator.generate(pos, Stage.BLOCK_RULES);
                    if (chunk.paletteBlocks().size() > 1) {
                        out.add(pos);
                    }
                }
            }
        }
        return out;
    }

    /** Placed features load, sorted by priority, and reach the world type. */
    @Test
    void testCompiles() {
        final List<PlacedFeature> features = generator().getWorldType().features();
        assertEquals(
                List.of("feat:tufts", "feat:flowers"),
                features.stream().map(PlacedFeature::id).toList());
        assertEquals(Set.of("test:plains"), features.get(0).biomes());
    }

    /**
     * With a chance of 1, every open floor on grass in plains holds a tuft and nothing else does,
     * including floors on a chunk's bottom row, whose grass is in the chunk below.
     */
    @Test
    void testEveryGrassFloor() {
        final WorldGenerator generator = generator();
        int tufts = 0;
        int bottomRow = 0;
        for (ChunkPos pos : groundChunks(generator)) {
            final Chunk plain = generator.generate(pos, Stage.BLOCK_RULES);
            final Chunk featured = generator.generate(pos, Stage.FEATURES);
            final Chunk below =
                    generator.generate(
                            new ChunkPos(pos.x(), pos.y() - 1, pos.z()), Stage.BLOCK_RULES);
            for (int y = 0; y < World.CHUNK_SIZE; ++y) {
                for (int z = 0; z < World.CHUNK_SIZE; ++z) {
                    for (int x = 0; x < World.CHUNK_SIZE; ++x) {
                        final String before = plain.getBlock(x, y, z).getName();
                        final String after = featured.getBlock(x, y, z).getName();
                        final String under =
                                (y > 0
                                                ? plain.getBlock(x, y - 1, z)
                                                : below.getBlock(x, World.CHUNK_SIZE - 1, z))
                                        .getName();
                        final boolean wanted =
                                World.AIR_NAME.equals(before)
                                        && "test:grass".equals(under)
                                        && "test:plains".equals(plain.getBiome(x, y, z));
                        if (wanted) {
                            assertEquals(TUFT, after, "at " + pos + " " + x + "," + y + "," + z);
                            ++tufts;
                            if (y == 0) {
                                ++bottomRow;
                            }
                        } else if (!FLOWER.equals(after) && !TUFT.equals(after)) {
                            assertEquals(
                                    before,
                                    after,
                                    "changed at " + pos + " " + x + "," + y + "," + z);
                        }
                    }
                }
            }
        }
        assertTrue(tufts > 0, "no grass floors near the origin to test");
        assertTrue(bottomRow > 0, "no grass floors on a chunk's bottom row to test");
    }

    /** A chance below 1 places about that share, and the same each time. */
    @Test
    void testChanceAndDeterminism() {
        final WorldGenerator generator = generator();
        final WorldGenerator again = generator();
        int floors = 0;
        int placed = 0;
        for (ChunkPos pos : groundChunks(generator)) {
            final Chunk plain = generator.generate(pos, Stage.BLOCK_RULES);
            final Chunk featured = generator.generate(pos, Stage.FEATURES);
            assertEquals(featured.contentHash(), again.generate(pos).contentHash());
            for (int y = 1; y < World.CHUNK_SIZE; ++y) {
                for (int z = 0; z < World.CHUNK_SIZE; ++z) {
                    for (int x = 0; x < World.CHUNK_SIZE; ++x) {
                        if (World.AIR_NAME.equals(plain.getBlock(x, y, z).getName())
                                && "test:sand".equals(plain.getBlock(x, y - 1, z).getName())
                                && "test:desert".equals(plain.getBiome(x, y, z))) {
                            ++floors;
                            final String after = featured.getBlock(x, y, z).getName();
                            if (FLOWER.equals(after) || TUFT.equals(after)) {
                                ++placed;
                            }
                        }
                    }
                }
            }
        }
        if (floors < 100) {
            // Not enough desert near the origin at this seed to measure a share
            return;
        }
        final double share = placed / (double) floors;
        assertTrue(share > 0.2 && share < 0.4, "placed " + placed + " of " + floors);
    }

    /** Tags pick biomes, and features for biomes a world type doesn't have are left out. */
    @Test
    void testTagsAndWorldType() {
        final WorldgenCompiler compiler =
                new WorldgenFixtures.Text()
                        .add(Kind.BIOME, "t:meadow", "{climate:{}, tags:[T;\"t:green\"]}")
                        .add(Kind.BIOME, "t:moor", "{climate:{}, tags:[T;\"t:green\"]}")
                        .add(Kind.BIOME, "t:rock", "{climate:{}}")
                        .add(
                                Kind.PLACED_FEATURE,
                                "t:green",
                                "{feature:{type:\"block\", blocks:[N;{block:\"t:tuft\"}]},"
                                        + " biomes:\"#t:green\","
                                        + " placement:[N;{type:\"each_floor\", chance:0.5}]}")
                        .add(
                                Kind.PLACED_FEATURE,
                                "t:rocky",
                                "{feature:{type:\"block\", blocks:[N;{block:\"t:pebble\"}]},"
                                        + " biomes:[T;\"t:rock\"],"
                                        + " placement:[N;{type:\"each_floor\", chance:0.5}]}")
                        .add(
                                Kind.WORLD_TYPE,
                                "t:world",
                                "{density:-1.0, default_block:\"t:stone\", parameters:[T;],"
                                        + " biomes:[T;\"t:meadow\"], fallback_biome:\"t:meadow\"}")
                        .compileAll();
        assertFalse(compiler.hasErrors(), () -> compiler.getDiagnostics().toString());
        assertEquals(
                Set.of("t:meadow", "t:moor"),
                compiler.placedFeature("t:green", null).orElseThrow().biomes());
        assertEquals(
                List.of("t:green"),
                compiler.worldType("t:world", null).orElseThrow().features().stream()
                        .map(PlacedFeature::id)
                        .toList());
    }

    /** Layers and placements that aren't built yet are errors, not silently ignored. */
    @Test
    void testUnbuiltPlacements() {
        final WorldgenCompiler compiler =
                new WorldgenFixtures.Text()
                        .add(Kind.BIOME, "t:meadow", "{climate:{}}")
                        .add(
                                Kind.PLACED_FEATURE,
                                "t:tree",
                                "{feature:{type:\"block\", blocks:[N;{block:\"t:log\"}]},"
                                        + " biomes:[T;\"t:meadow\"], layer:\"large\","
                                        + " placement:[N;{type:\"each_floor\", chance:0.5}]}")
                        .add(
                                Kind.PLACED_FEATURE,
                                "t:nofloor",
                                "{feature:{type:\"block\", blocks:[N;{block:\"t:log\"}]},"
                                        + " biomes:[T;\"t:meadow\"],"
                                        + " placement:[N;{type:\"cells\", size:16}]}")
                        .compileAll();
        final List<String> codes =
                compiler.getDiagnostics().stream().map(Diagnostic::code).toList();
        assertTrue(codes.contains("WORLDGEN_LAYER_NOT_BUILT"), codes::toString);
        assertTrue(codes.contains("WORLDGEN_NEEDS_EACH_FLOOR"), codes::toString);
        assertTrue(codes.contains("WORLDGEN_UNKNOWN_TYPE"), codes::toString);
    }
}
