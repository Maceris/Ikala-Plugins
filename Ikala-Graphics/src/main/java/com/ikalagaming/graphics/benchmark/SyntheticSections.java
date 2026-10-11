package com.ikalagaming.graphics.benchmark;

import com.ikalagaming.graphics.bake.BakeSource;
import com.ikalagaming.graphics.bake.SectionBaker;
import com.ikalagaming.graphics.graph.Material;
import com.ikalagaming.graphics.lod.LodMesh;

import lombok.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Bakes synthetic terrain on the CPU into one opaque mesh per section, for building level of detail
 * from: the same blocks and face culling the gate benchmark draws, without the GPU.
 */
public final class SyntheticSections {

    /** The section size, in blocks. */
    private static final int SECTION = SyntheticTerrain.SECTION;

    /**
     * Bake columns of sections, from (0, 0) up.
     *
     * @param recipe What the world is made of.
     * @param seed The seed.
     * @param columns Columns of sections along x and z.
     * @param sources The bake source of each block type, by block id, null for air.
     * @param materials The material index each block type's triangles get, by block id.
     * @return One mesh per section with opaque blocks, each with its section's origin.
     */
    public static List<LodMesh> bake(
            SyntheticTerrain.@NonNull Recipe recipe,
            long seed,
            int columns,
            @NonNull BakeSource @NonNull [] sources,
            int @NonNull [] materials) {
        final SyntheticTerrain terrain = new SyntheticTerrain(recipe, seed, sources);
        List<LodMesh> meshes = new ArrayList<>();
        for (int sx = 0; sx < columns; ++sx) {
            for (int sz = 0; sz < columns; ++sz) {
                for (int sy = Math.floorDiv(terrain.minY(), SECTION);
                        sy <= Math.floorDiv(terrain.maxY(), SECTION);
                        ++sy) {
                    final SyntheticTerrain.Placements p = terrain.placements(sx, sy, sz);
                    if (p.count() == 0) {
                        continue;
                    }
                    final BakeSource[] placed = new BakeSource[p.count()];
                    final int[] placedMaterials = new int[p.count()];
                    for (int i = 0; i < p.count(); ++i) {
                        placed[i] = sources[p.blocks()[i]];
                        placedMaterials[i] = materials[p.blocks()[i]];
                    }
                    final SectionBaker.Bucket opaque =
                            SectionBaker.bake(
                                            placed,
                                            placedMaterials,
                                            p.packedPositions(),
                                            new byte[p.count()],
                                            p.faceMasks(),
                                            new short[p.count()])
                                    .get(Material.Transparency.OPAQUE);
                    if (opaque != null) {
                        meshes.add(
                                LodMesh.fromBaked(
                                        opaque,
                                        new double[] {sx * SECTION, sy * SECTION, sz * SECTION}));
                    }
                }
            }
        }
        return meshes;
    }

    /** Static helpers only. */
    private SyntheticSections() {}
}
