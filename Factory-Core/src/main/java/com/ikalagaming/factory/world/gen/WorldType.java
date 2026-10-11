package com.ikalagaming.factory.world.gen;

import com.ikalagaming.factory.world.Block;
import com.ikalagaming.factory.world.gen.density.Box;
import com.ikalagaming.factory.world.gen.density.DensityNode;

import lombok.NonNull;

import java.util.List;

/**
 * What ties a world together: its terrain, parameters, biomes, fluids and border. Everything inside
 * the border comes from this; outside it there is only air.
 *
 * @param id The data ID.
 * @param density The terrain: solid where above zero.
 * @param interpolation The coarse lattice the terrain is sampled on, along x, y and z, in blocks.
 * @param border The world border, inclusive, in blocks.
 * @param killDistance How far past the border players survive, in blocks.
 * @param defaultBlock The block for solid positions no block rule chooses.
 * @param parameters The parameters biomes are selected by, in order.
 * @param biomes The biomes that can appear.
 * @param fallbackBiome The biome where none suits the parameters.
 * @param fluids How open space fills.
 * @param climates Each biome's range for each parameter, in {@code parameters} order, null where
 *     the biome doesn't care. Indexed like {@code biomes}.
 */
public record WorldType(
        @NonNull String id,
        @NonNull DensityNode density,
        int @NonNull [] interpolation,
        @NonNull Box border,
        int killDistance,
        @NonNull Block defaultBlock,
        @NonNull List<Parameter> parameters,
        @NonNull List<Biome> biomes,
        @NonNull Biome fallbackBiome,
        @NonNull Fluids fluids,
        @NonNull List<ParameterRange[]> climates) {

    /**
     * The largest border the engine allows on any axis, well inside the int range so chunk, cell
     * and neighbor math never overflows: 2^31 - 2^20.
     */
    public static final int MAX_BORDER = Integer.MAX_VALUE - (1 << 20) + 1;

    /** The border used when a world type doesn't give one: as far as the engine allows. */
    public static final Box DEFAULT_BORDER =
            new Box(-MAX_BORDER, -MAX_BORDER, -MAX_BORDER, MAX_BORDER, MAX_BORDER, MAX_BORDER);

    /** The default kill distance past the border, in blocks. */
    public static final int DEFAULT_KILL_DISTANCE = 1024;

    /** The default coarse lattice for terrain: every 4 blocks across, every 8 up. */
    public static final List<Integer> DEFAULT_INTERPOLATION = List.of(4, 8, 4);

    /**
     * Whether a block position is inside the border.
     *
     * @param x The x coordinate.
     * @param y The y coordinate.
     * @param z The z coordinate.
     * @return True if inside.
     */
    public boolean inside(long x, long y, long z) {
        return x >= border.minX()
                && x <= border.maxX()
                && y >= border.minY()
                && y <= border.maxY()
                && z >= border.minZ()
                && z <= border.maxZ();
    }
}
