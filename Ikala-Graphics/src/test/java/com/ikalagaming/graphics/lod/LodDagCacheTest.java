package com.ikalagaming.graphics.lod;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

/** Tests for whole DAGs in the cache. */
class LodDagCacheTest {

    /** The tile's side, in texels. */
    private static final int TILE = 64;

    /** Options that bake far groups, so the cached DAG has tile mappings to keep. */
    private static final DagBuilder.Options OPTIONS =
            AttributesTest.OPTIONS.withBakeTile(TILE).withBakeDensity(1);

    /** The most bytes the test caches keep. */
    private static final long CAP = 1L << 30;

    /**
     * A second build of the same meshes the same way reads the first back, clusters, groups and
     * tile mappings alike.
     */
    @Test
    void testSecondBuildReadsTheFirst(@TempDir Path folder) {
        final List<LodMesh> meshes = List.of(AttributesTest.sphere());
        final LodCache cache = new LodCache(folder, CAP);
        final LodDag built = LodDagCache.build(cache, meshes, OPTIONS);
        assertEquals(0, cache.stats().hits());
        final LodDag read = LodDagCache.build(cache, meshes, OPTIONS);
        assertEquals(1, cache.stats().hits());

        assertEquals(built.clusters().size(), read.clusters().size());
        assertEquals(built.groups().size(), read.groups().size());
        assertEquals(built.getLevels(), read.getLevels());
        assertEquals(built.getLeafEdge(), read.getLeafEdge());
        for (LodDag.Group a : built.groups()) {
            final LodDag.Group b = read.groups().get(a.id());
            assertArrayEquals(a.children(), b.children());
            assertArrayEquals(a.outputs(), b.outputs());
            assertArrayEquals(a.bakeTiles(), b.bakeTiles(), "Group " + a.id() + "'s bake tiles");
        }
        int bakes = 0;
        for (LodDag.Cluster a : built.clusters()) {
            final LodDag.Cluster b = read.clusters().get(a.id());
            assertArrayEquals(a.corners(), b.corners());
            assertArrayEquals(a.materials(), b.materials());
            assertArrayEquals(a.attributes(), b.attributes());
            assertEquals(a.error(), b.error());
            assertEquals(a.producer(), b.producer());
            final BakeTexels bake = built.bakeOf(a.id());
            if (bake == null) {
                continue;
            }
            ++bakes;
            final BakeTexels back = read.bakeOf(a.id());
            assertNotNull(back, "Cluster " + a.id() + "'s bake");
            assertEquals(bake.width(), back.width());
            assertEquals(bake.height(), back.height());
            for (int texel = 0; texel < bake.width() * bake.height(); ++texel) {
                assertEquals(bake.childCluster(texel), back.childCluster(texel));
                assertEquals(bake.childTriangle(texel), back.childTriangle(texel));
                assertEquals(bake.ownTriangle(texel), back.ownTriangle(texel));
                for (int corner = 1; corner <= 2; ++corner) {
                    assertEquals(bake.childWeight(texel, corner), back.childWeight(texel, corner));
                    assertEquals(bake.ownWeight(texel, corner), back.ownWeight(texel, corner));
                }
            }
            assertEquals(bake.splatCount(), back.splatCount());
            for (int s = 0; s < bake.splatCount(); ++s) {
                assertEquals(bake.splatTexel(s), back.splatTexel(s));
                assertEquals(bake.splatCluster(s), back.splatCluster(s));
                assertEquals(bake.splatCoverage(s), back.splatCoverage(s));
            }
        }
        assertTrue(bakes > 0, "Nothing baked");
    }

    /** The key follows the options and the meshes; a DAG keeping its lineage isn't cached. */
    @Test
    void testKeysFollowWhatIsBuilt(@TempDir Path folder) {
        final List<LodMesh> meshes = List.of(AttributesTest.sphere());
        final String key = LodDagCache.keyOf(meshes, OPTIONS);
        assertEquals(key, LodDagCache.keyOf(List.of(AttributesTest.sphere()), OPTIONS));
        assertNotEquals(key, LodDagCache.keyOf(meshes, OPTIONS.withBakeDensity(2)));
        assertNotEquals(key, LodDagCache.keyOf(meshes, OPTIONS.withVolumes(true)));
        final LodCache cache = new LodCache(folder, CAP);
        final DagBuilder.Options lineage =
                new DagBuilder.Options(
                        OPTIONS.virtualEdgeFactor(),
                        OPTIONS.lockOpenBorder(),
                        OPTIONS.threads(),
                        true,
                        OPTIONS.visibilityReach(),
                        OPTIONS.mergePlanar(),
                        OPTIONS.averages());
        LodDagCache.build(cache, meshes, lineage);
        LodDagCache.build(cache, meshes, lineage);
        assertEquals(0, cache.stats().hits() + cache.stats().misses());
    }
}
