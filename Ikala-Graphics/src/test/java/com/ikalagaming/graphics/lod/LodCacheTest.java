package com.ikalagaming.graphics.lod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/** Tests for caching built region cells on disk. */
class LodCacheTest {

    /** A cap no test reaches. */
    private static final long NO_CAP = Long.MAX_VALUE;

    /**
     * Build a store over sections with a cache.
     *
     * @param sections The sections.
     * @param cache The cache.
     * @return The built store.
     */
    private static LodStore built(List<LodMesh> sections, LodCache cache) {
        final LodStore store = LodCellFileTest.store(sections, cache);
        store.rebuild();
        return store;
    }

    /**
     * A copy of a mesh with one triangle's material, corners or a vertex lock changed.
     *
     * @param mesh The mesh.
     * @param change 0 for a material, 1 to drop a triangle, 2 to lock a vertex.
     * @return The copy.
     */
    private static LodMesh changed(LodMesh mesh, int change) {
        final LodMesh.Builder builder = LodMesh.builder(mesh.getOrigin().clone());
        final int[] remap = new int[mesh.getVertexCount()];
        for (int v = 0; v < remap.length; ++v) {
            remap[v] =
                    builder.vertex(mesh.position(v, 0), mesh.position(v, 1), mesh.position(v, 2));
            if (mesh.isLocked(v) || (change == 2 && v == 0)) {
                builder.lock(remap[v]);
            }
        }
        for (int t = 0; t < mesh.getTriangleCount(); ++t) {
            if (change == 1 && t == 0) {
                continue;
            }
            final int material = change == 0 && t == 0 ? mesh.material(t) + 1 : mesh.material(t);
            builder.triangle(
                    remap[mesh.corner(t, 0)],
                    remap[mesh.corner(t, 1)],
                    remap[mesh.corner(t, 2)],
                    material,
                    mesh.visibility(t));
        }
        return builder.build();
    }

    /** A second store over the same sections reads every cell, and has the same DAG. */
    @Test
    void testWarmLoadsEverything(@TempDir Path folder) {
        final List<LodMesh> sections = DagTest.terrain(2);
        final LodStore cold = built(sections, new LodCache(folder, NO_CAP));
        assertEquals(cold.cellCount(), cold.getBuiltCount());
        assertEquals(0, cold.getLoadedCount());
        final LodCache cache = new LodCache(folder, NO_CAP);
        final LodStore warm = built(sections, cache);
        assertEquals(0, warm.getBuiltCount(), "Nothing is built");
        assertEquals(cold.cellCount(), warm.getLoadedCount());
        assertEquals(cold.cellCount(), cache.stats().hits());
        LodCellFileTest.assertSameDag(cold.snapshot(), warm.snapshot());
    }

    /** Changing one section rebuilds its cell and the cells above it, and reads the rest. */
    @Test
    void testOneChangeRebuildsItsChain(@TempDir Path folder) {
        final List<LodMesh> sections = DagTest.terrain(2);
        final LodStore cold = built(sections, new LodCache(folder, NO_CAP));
        final List<LodMesh> edited = new ArrayList<>(sections);
        edited.set(0, changed(sections.get(0), 1));
        final LodStore warm = built(edited, new LodCache(folder, NO_CAP));
        // The section's cell, then one parent a level up to the root
        int levels = 0;
        for (LodStore.CellKey key : warm.cellKeys()) {
            levels = Math.max(levels, key.level() + 1);
        }
        assertEquals(levels, warm.getBuiltCount(), "One cell a level is rebuilt");
        assertEquals(cold.cellCount() - levels, warm.getLoadedCount());
    }

    /** Other options, or a changed material, triangle or lock, give another key. */
    @Test
    void testKeysFollowInputs(@TempDir Path folder) {
        final List<LodMesh> sections = DagTest.terrain(1);
        final LodStore first = built(sections, new LodCache(folder, NO_CAP));
        final LodStore.CellKey leaf = first.cellOf(LodCellFileTest.position(sections.get(0)));
        final String key = first.cacheKey(leaf);
        for (int change = 0; change < 3; ++change) {
            final List<LodMesh> edited = new ArrayList<>(sections);
            edited.set(0, changed(sections.get(0), change));
            final LodStore store = built(edited, new LodCache(folder, NO_CAP));
            assertNotEquals(key, store.cacheKey(leaf), "Change " + change);
        }
        final LodStore other =
                new LodStore(
                        new double[3],
                        LodCellFileTest.SECTION,
                        1,
                        4,
                        new DagBuilder.Options(0.5, false, 2, false, 3),
                        new LodCache(folder, NO_CAP));
        for (LodMesh mesh : sections) {
            other.put(LodCellFileTest.position(mesh), mesh);
        }
        other.rebuild();
        assertEquals(0, other.getLoadedCount(), "Other options read nothing");
    }

    /** A damaged file is rebuilt and written again. */
    @Test
    void testCorruptFileRebuilt(@TempDir Path folder) throws IOException {
        final List<LodMesh> sections = DagTest.terrain(1);
        final LodStore cold = built(sections, new LodCache(folder, NO_CAP));
        final Path damaged = cellFiles(folder).get(0);
        final byte[] bytes = Files.readAllBytes(damaged);
        for (int i = bytes.length / 2; i < bytes.length / 2 + 16; ++i) {
            bytes[i] ^= (byte) 0x5A;
        }
        Files.write(damaged, bytes);
        final LodStore warm = built(sections, new LodCache(folder, NO_CAP));
        assertEquals(1, warm.getBuiltCount());
        assertEquals(cold.cellCount() - 1, warm.getLoadedCount());
        final LodStore again = built(sections, new LodCache(folder, NO_CAP));
        assertEquals(0, again.getBuiltCount(), "The rebuilt cell was written again");
    }

    /** Over the cap, the least recently used files go, down to 90% of it. */
    @Test
    void testCapEvictsLeastRecentlyUsed(@TempDir Path folder) throws IOException {
        final List<LodMesh> sections = DagTest.terrain(2);
        final LodStore store = built(sections, new LodCache(folder, NO_CAP));
        final List<Path> files = cellFiles(folder);
        long total = 0;
        for (Path file : files) {
            total += Files.size(file);
        }
        // Everything long unused, then one file read, which makes it the newest
        long time = 1_000_000_000L;
        for (Path file : files) {
            Files.setLastModifiedTime(file, FileTime.fromMillis(time += 1000));
        }
        final LodStore.CellKey read = store.cellKeys().iterator().next();
        final LodCache small = new LodCache(folder, total / 2);
        final String readKey = store.cacheKey(read);
        assertTrue(small.load(readKey, LodStore.BUILDER_VERSION) != null);
        // Saving one more takes it over the cap
        small.save("extra", LodStore.BUILDER_VERSION, read, store.built(read));
        long after = 0;
        for (Path file : cellFiles(folder)) {
            after += Files.size(file);
        }
        assertTrue(after <= total / 2 * 0.9 + 1, after + " of a cap of " + total / 2);
        assertTrue(small.stats().evicted() > 0);
        assertTrue(Files.exists(small.fileOf(readKey)), "The file just read stays");
        assertTrue(Files.exists(small.fileOf("extra")), "The file just saved stays");
        assertFalse(
                Files.exists(files.get(0)) && !files.get(0).equals(small.fileOf(readKey)),
                "The oldest file goes");
        assertEquals(after, small.stats().folderBytes(), "The scan counted what was there");
    }

    /**
     * The cell files in a folder.
     *
     * @param folder The folder.
     * @return Their paths, sorted.
     * @throws IOException If it can't be listed.
     */
    private static List<Path> cellFiles(Path folder) throws IOException {
        try (Stream<Path> stream = Files.list(folder)) {
            return stream.filter(path -> path.toString().endsWith(LodCache.EXTENSION))
                    .sorted()
                    .toList();
        }
    }
}
