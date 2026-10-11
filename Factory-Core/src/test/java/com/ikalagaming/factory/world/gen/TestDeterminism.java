package com.ikalagaming.factory.world.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.factory.world.ChunkPos;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * World generation must be a pure function of the seed, the chunk position and the data. These
 * tests hash a fixed set of chunks for a few seeds against a golden file, so any change to what
 * generates fails until the file is deliberately updated with {@code ./gradlew test
 * -Dworldgen.updateGolden=true}, and check the hashes don't depend on order or threads.
 *
 * <p>Running on more than one operating system and JVM belongs in CI, once there is CI.
 *
 * @author Ches Burks
 */
class TestDeterminism {

    /** Where the golden hashes live, relative to the project, for updating them. */
    private static final Path GOLDEN_SOURCE =
            Path.of("src", "test", "resources", "worldgen", "golden-hashes.txt");

    /** The golden hashes on the classpath. */
    private static final String GOLDEN_RESOURCE = "/worldgen/golden-hashes.txt";

    /** The seeds hashed. */
    private static final long[] SEEDS = {1L, WorldgenFixtures.SEED, -1_234_567_890_123L};

    /** How many threads the threaded run uses. */
    private static final int THREADS = 8;

    /** One chunk of one seed. */
    private record Key(long seed, ChunkPos pos) {}

    /**
     * The chunks hashed: the surface, caves and the floating island near the origin; around a
     * million and a billion blocks out; and straddling the world border.
     *
     * @return The chunk positions.
     */
    private static List<ChunkPos> chunks() {
        List<ChunkPos> chunks = new ArrayList<>();
        for (int y = -2; y <= 3; ++y) {
            for (int x = -1; x <= 1; ++x) {
                for (int z = -1; z <= 0; ++z) {
                    chunks.add(new ChunkPos(x, y, z));
                }
            }
        }
        final int million = 1_000_000 / 16;
        final int billion = 1_000_000_000 / 16;
        for (int far : new int[] {million, -million, billion, -billion}) {
            for (int y = -1; y <= 0; ++y) {
                chunks.add(new ChunkPos(far, y, far));
                chunks.add(new ChunkPos(far + 1, y, -far));
            }
        }
        // The fixture's border is at x = 2,000,000,007, partway through this chunk
        final int border = 2_000_000_007 >> 4;
        for (int y = -2; y <= 0; ++y) {
            chunks.add(new ChunkPos(border, y, 0));
            chunks.add(new ChunkPos(border + 1, y, 0));
        }
        return chunks;
    }

    /**
     * Every chunk of every seed.
     *
     * @return The keys, in a fixed order.
     */
    private static List<Key> keys() {
        List<Key> keys = new ArrayList<>();
        for (long seed : SEEDS) {
            for (ChunkPos pos : chunks()) {
                keys.add(new Key(seed, pos));
            }
        }
        return keys;
    }

    /**
     * Hash chunks in a given order on one thread, with one generator per seed.
     *
     * @param order The order to generate in.
     * @return The hashes.
     */
    private static Map<Key, Long> hashInOrder(List<Key> order) {
        Map<Long, WorldGenerator> generators = new HashMap<>();
        Map<Key, Long> hashes = new HashMap<>();
        for (Key key : order) {
            final WorldGenerator generator =
                    generators.computeIfAbsent(key.seed(), WorldgenFixtures::generator);
            hashes.put(key, generator.generate(key.pos()).contentHash());
        }
        return hashes;
    }

    /**
     * Format hashes as the golden file's lines, sorted.
     *
     * @param hashes The hashes.
     * @return The lines.
     */
    private static List<String> lines(Map<Key, Long> hashes) {
        List<String> lines = new ArrayList<>();
        for (Key key : keys()) {
            lines.add(
                    String.format(
                            "%d %d %d %d %016x",
                            key.seed(),
                            key.pos().x(),
                            key.pos().y(),
                            key.pos().z(),
                            hashes.get(key)));
        }
        return lines;
    }

    /**
     * The hashes match the golden file.
     *
     * @throws IOException If the file can't be read or written.
     */
    @Test
    void testGoldenHashes() throws IOException {
        final List<String> actual = lines(hashInOrder(keys()));
        if (Boolean.getBoolean("worldgen.updateGolden")) {
            Files.createDirectories(GOLDEN_SOURCE.getParent());
            List<String> file = new ArrayList<>();
            file.add("# seed chunkX chunkY chunkZ contentHash, from TestDeterminism");
            file.add("# Regenerate on purpose with ./gradlew test -Dworldgen.updateGolden=true");
            file.addAll(actual);
            Files.write(GOLDEN_SOURCE, file, StandardCharsets.UTF_8);
            return;
        }
        try (var stream = TestDeterminism.class.getResourceAsStream(GOLDEN_RESOURCE)) {
            assertTrue(stream != null, "No golden hashes; run with -Dworldgen.updateGolden=true");
            final List<String> expected =
                    new String(stream.readAllBytes(), StandardCharsets.UTF_8)
                            .lines()
                            .filter(line -> !line.startsWith("#") && !line.isBlank())
                            .toList();
            assertEquals(expected.size(), actual.size(), "The chunk set changed");
            for (int i = 0; i < expected.size(); ++i) {
                assertEquals(expected.get(i), actual.get(i), "Generation changed");
            }
        }
    }

    /**
     * The order chunks are generated in, and how many threads generate them, change nothing.
     *
     * @throws Exception If a thread fails.
     */
    @Test
    void testOrderAndThreadsDontMatter() throws Exception {
        final List<Key> keys = keys();
        final Map<Key, Long> inOrder = hashInOrder(keys);

        List<Key> shuffled = new ArrayList<>(keys);
        Collections.shuffle(shuffled, new Random(WorldgenFixtures.SEED));
        assertEquals(inOrder, hashInOrder(shuffled));

        // One generator per seed, shared by every thread
        Map<Long, WorldGenerator> generators = new HashMap<>();
        for (long seed : SEEDS) {
            generators.put(seed, WorldgenFixtures.generator(seed));
        }
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            Map<Key, Future<Long>> futures = new HashMap<>();
            for (Key key : shuffled) {
                futures.put(
                        key,
                        pool.submit(
                                () ->
                                        generators
                                                .get(key.seed())
                                                .generate(key.pos())
                                                .contentHash()));
            }
            Map<Key, Long> threaded = new HashMap<>();
            for (Map.Entry<Key, Future<Long>> entry : futures.entrySet()) {
                threaded.put(entry.getKey(), entry.getValue().get());
            }
            assertEquals(inOrder, threaded);
        } finally {
            pool.shutdownNow();
        }
    }
}
