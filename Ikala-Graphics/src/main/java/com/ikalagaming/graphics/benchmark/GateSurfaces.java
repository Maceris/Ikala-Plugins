package com.ikalagaming.graphics.benchmark;

import com.ikalagaming.graphics.SurfaceLayer;
import com.ikalagaming.graphics.graph.Material;
import com.ikalagaming.graphics.lod.LodStore;

import lombok.NonNull;

import javax.annotation.Nullable;

/**
 * The gate's soft surface layer, from the {@code .surface=depth} option: snow that deep, in meters,
 * on every full block whose cell above is empty, the highest such block in each column of a
 * section, which owns the snow even where it sits in the section above. Graphics treats it as any
 * surface; this only makes some to look at.
 */
final class GateSurfaces {

    /** Height steps a meter, as the game uses: 1/16 m. */
    static final int STEPS_PER_METER = 16;

    /** Fresh snow's albedo, and its roughness. */
    private static final float SNOW_ALBEDO = 0.9f;

    private static final float SNOW_ROUGHNESS = 0.6f;

    /** The snow's look. */
    private final Material snow;

    /** How deep the snow is, in steps. */
    private final short depth;

    /**
     * Make snow of a depth.
     *
     * @param depthMeters How deep, in meters, up to a block.
     */
    GateSurfaces(float depthMeters) {
        depth = (short) Math.clamp(Math.round(depthMeters * STEPS_PER_METER), 1, STEPS_PER_METER);
        snow = new Material();
        snow.getBaseColor().set(SNOW_ALBEDO, SNOW_ALBEDO, SNOW_ALBEDO, 1);
        snow.setRoughness(SNOW_ROUGHNESS);
        snow.setTransparency(Material.Transparency.OPAQUE);
        snow.setTriplanar(true);
    }

    /**
     * The snow's material.
     *
     * @return The material.
     */
    Material material() {
        return snow;
    }

    /**
     * The option's surfaces, if it is set.
     *
     * @param options The run's options.
     * @return The surfaces, or null without the option.
     */
    static @Nullable GateSurfaces fromOptions(@NonNull GateOptions options) {
        final String value = options.get("surface");
        return value == null ? null : new GateSurfaces(Float.parseFloat(value.trim()));
    }

    /**
     * A section's snow, with its neighbors' columns around it.
     *
     * @param world The world.
     * @param position The section.
     * @return The layer, or null if no column in the section has snow.
     */
    @Nullable SurfaceLayer layer(@NonNull SyntheticTerrain world, LodStore.@NonNull SectionPos position) {
        final int size = SyntheticTerrain.SECTION;
        final SurfaceLayer layer = SurfaceLayer.empty(STEPS_PER_METER, snow);
        boolean any = false;
        for (int z = -1; z <= size; ++z) {
            for (int x = -1; x <= size; ++x) {
                final int worldX = position.x() * size + x;
                final int worldZ = position.z() * size + z;
                // The cell over each block of the section, up to the one over its top layer
                for (int y = size; y >= 1; --y) {
                    final int worldY = position.y() * size + y;
                    if (world.block(worldX, worldY, worldZ) == SyntheticTerrain.AIR
                            && supports(world.block(worldX, worldY - 1, worldZ))) {
                        final int column = SurfaceLayer.index(x, z);
                        layer.tops()[column] = (short) (y * STEPS_PER_METER + depth);
                        layer.depths()[column] = depth;
                        any |= x >= 0 && x < size && z >= 0 && z < size;
                        break;
                    }
                }
            }
        }
        return any ? layer : null;
    }

    /**
     * Whether a block holds snow on its top: a full block.
     *
     * @param block The block.
     * @return True if it does.
     */
    private static boolean supports(byte block) {
        return block == SyntheticTerrain.STONE
                || block == SyntheticTerrain.GRASS
                || block == SyntheticTerrain.DIRT
                || block == SyntheticTerrain.PLANKS
                || block == SyntheticTerrain.LEAVES;
    }
}
