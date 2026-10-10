package com.ikalagaming.graphics.benchmark;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.bake.BakeSource;
import com.ikalagaming.graphics.bake.SectionBaker;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The default world at view distance 12 makes the load the architecture doc expects, about 1,400
 * sections with content and 3 to 8 million triangles, baked without a GPU.
 */
@Slf4j
class SyntheticTerrainLoadTest {

    /** The view distance the gate is about. */
    private static final int RADIUS = 12;

    /** The seed the benchmark uses. */
    private static final long SEED = GateBenchmark.SEED;

    /**
     * Bake every section of a layout, as the benchmark would.
     *
     * @param layout The layout.
     * @param sources The block sources.
     * @return The triangles once baked.
     */
    static long bakeAll(GateScene.Layout layout, BakeSource[] sources) {
        AtomicLong triangles = new AtomicLong();
        layout.sections().parallelStream()
                .forEach(
                        section -> {
                            final SyntheticTerrain.Placements placements = section.placements();
                            final int count = placements.count();
                            final BakeSource[] placed = new BakeSource[count];
                            for (int i = 0; i < count; ++i) {
                                placed[i] = sources[placements.blocks()[i]];
                            }
                            final int[] materials = new int[count];
                            Arrays.fill(materials, 1);
                            triangles.addAndGet(
                                    SectionBaker.bake(
                                                    placed,
                                                    materials,
                                                    placements.packedPositions(),
                                                    new byte[count],
                                                    placements.faceMasks(),
                                                    new short[count])
                                            .triangleCount());
                        });
        return triangles.get();
    }

    @Test
    void theDefaultWorldMatchesTheExpectedLoad() {
        BakeSource[] sources = BlockMeshes.sources(BlockMeshes.create());
        SyntheticTerrain terrain =
                new SyntheticTerrain(SyntheticTerrain.Recipe.TERRAIN, SEED, sources);
        long start = System.nanoTime();
        GateScene.Layout layout = GateScene.build(terrain, RADIUS);
        long laidOut = System.nanoTime();
        GateScene.LoadStats load = layout.load().withTriangles(bakeAll(layout, sources));
        long baked = System.nanoTime();
        System.out.printf(
                "Radius %d: %d sections in the sphere, %d with content in %d columns (%.2f per"
                        + " column), %d placements, %d triangles (%.0f per section); laid out in %d"
                        + " ms, baked in %d ms%n",
                RADIUS,
                load.sectionsInSphere(),
                load.contentSections(),
                load.columns(),
                load.sectionsPerColumn(),
                load.placements(),
                load.triangles(),
                load.trianglesPerSection(),
                (laidOut - start) / 1_000_000,
                (baked - laidOut) / 1_000_000);
        log.info(
                "Radius {}: {} sections in the sphere, {} with content in {} columns ({} per column),"
                        + " {} placements, {} triangles ({} per section); laid out in {} ms, baked in"
                        + " {} ms",
                RADIUS,
                load.sectionsInSphere(),
                load.contentSections(),
                load.columns(),
                String.format("%.2f", load.sectionsPerColumn()),
                load.placements(),
                load.triangles(),
                String.format("%.0f", load.trianglesPerSection()),
                (laidOut - start) / 1_000_000,
                (baked - laidOut) / 1_000_000);
        assertTrue(
                load.contentSections() >= 1000 && load.contentSections() <= 2000,
                "About 1,400 sections with content, got " + load.contentSections());
        assertTrue(
                load.triangles() >= 3_000_000 && load.triangles() <= 8_000_000,
                "3 to 8 million triangles, got " + load.triangles());
    }
}
