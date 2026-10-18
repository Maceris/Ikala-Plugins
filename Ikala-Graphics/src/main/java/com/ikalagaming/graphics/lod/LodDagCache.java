package com.ikalagaming.graphics.lod;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * Whole DAGs built from meshes, as the asset workbench and models build them, kept in the same
 * cache as {@link LodStore}'s cells, so building the same meshes the same way again reads the DAG
 * back instead. The key is a hash of the builder version, every option that changes what is built,
 * the meshes whole, and what their materials look like averaged and how much of them is cut out.
 *
 * <p>A DAG that keeps its lineage isn't cached, since its file wouldn't hold it.
 */
@Slf4j
public final class LodDagCache {

    /** What sets a whole DAG's keys apart from cells'. */
    private static final int WHOLE_DAG = 0x44414721;

    /** Where a whole DAG is stored as a cell. */
    private static final LodStore.CellKey WHOLE = new LodStore.CellKey(0, 0, 0, 0);

    /** Nanoseconds in a millisecond. */
    private static final double NANOS_PER_MILLI = 1e6;

    /**
     * Build meshes into a DAG, or read it from the cache the graphics settings ask for if the same
     * meshes were built the same way before.
     *
     * @param meshes The meshes.
     * @param options How to build.
     * @return The DAG.
     */
    public static @NonNull LodDag build(
            @NonNull List<LodMesh> meshes, DagBuilder.@NonNull Options options) {
        return build(LodCache.fromSettings(), meshes, options);
    }

    /**
     * Build meshes into a DAG, or read it from a cache if the same meshes were built the same way
     * before.
     *
     * @param cache The cache, or null for none.
     * @param meshes The meshes.
     * @param options How to build.
     * @return The DAG.
     */
    public static @NonNull LodDag build(
            LodCache cache, @NonNull List<LodMesh> meshes, DagBuilder.@NonNull Options options) {
        if (cache == null || options.keepLineage()) {
            return DagBuilder.build(meshes, options);
        }
        final long started = System.nanoTime();
        final String key = keyOf(meshes, options);
        final LodCellFile.Stored stored = cache.load(key, LodStore.BUILDER_VERSION);
        if (stored != null) {
            log.info(
                    "Read the level of detail from the cache in {} ms",
                    Math.round((System.nanoTime() - started) / NANOS_PER_MILLI));
            return stored.cell().dag();
        }
        final LodDag dag = DagBuilder.build(meshes, options);
        cache.save(
                key,
                LodStore.BUILDER_VERSION,
                WHOLE,
                new LodStore.Cell(dag, List.of(), dag.getLeafEdge(), List.of()));
        return dag;
    }

    /**
     * The cache key of building meshes some way.
     *
     * @param meshes The meshes.
     * @param options How to build.
     * @return The key.
     */
    static @NonNull String keyOf(
            @NonNull List<LodMesh> meshes, DagBuilder.@NonNull Options options) {
        final LodStore.KeyHash hash = new LodStore.KeyHash();
        hash.putInt(WHOLE_DAG).putInt(LodStore.BUILDER_VERSION);
        hash.putDouble(options.virtualEdgeFactor());
        hash.putInt(options.lockOpenBorder() ? 1 : 0);
        hash.putDouble(options.visibilityReach());
        hash.putInt(options.mergePlanar() ? 1 : 0);
        hash.putInt(options.bakeTile());
        hash.putDouble(options.bakeDensity());
        hash.putInt(options.volumes() ? 1 : 0);
        hash.putInt(meshes.size());
        for (LodMesh mesh : meshes) {
            LodStore.putMesh(hash, mesh);
        }
        LodStore.putAverages(hash, options.averages(), meshes);
        // Which materials are cut out, and, for coverage volumes, how much of each is there
        for (int material : LodStore.materialsOf(meshes)) {
            hash.putInt(material);
            hash.putInt(options.cutOut() != null && options.cutOut().test(material) ? 1 : 0);
            hash.putFloat(options.averages() == null ? 1 : options.averages().coverage(material));
        }
        return hash.hex();
    }

    /** Not made. */
    private LodDagCache() {}
}
