package com.ikalagaming.factory.world.gen;

import com.ikalagaming.factory.world.Chunk;
import com.ikalagaming.factory.world.World;
import com.ikalagaming.random.RandomGen;

import lombok.NonNull;

import java.util.HashMap;
import java.util.Map;

/**
 * Used to generate the world.
 *
 * @author Ches Burks
 */
public class WorldGenerator {

    /**
     * A scale parameter used for calculating the block height that the world height noise parameter
     * value corresponds to. This value is chosen so that the total range of height values generated
     * spans half the possible depth values. This way there is always room to dig under and build on
     * top of the surface.
     */
    private static final int WORLD_HEIGHT_SCALE = World.WORLD_HEIGHT_TOTAL / 4;

    private static Map<String, BiomeParameters> biomes = new HashMap<>();
    private static Map<String, Byte> biomeEncoding = new HashMap<>();

    /**
     * Convert from the height parameter to the actual world height in blocks. This serves as the
     * starting point for the world height, but may be modified by various other world generation.
     *
     * <p>The height values generated span half the possible world depth values so there is always
     * room to dig under and build on top of the surface.
     *
     * <p>The function is cubic, so there is a relatively flat region in the middle of the range,
     * but steep extremes.
     *
     * @param height The height parameter, a float in the range [0, 1] inclusive.
     * @return The corresponding height value, in the range [-256, 256].
     */
    private static int calculateHeight(final float height) {
        final float base = (height * 2 - 1);
        final float result = WORLD_HEIGHT_SCALE * base * base * base;
        return Math.round(result);
    }

    private static int compareBiomes(
            BiomeParameters biome1, BiomeParameters biome2, ParameterPack target) {
        float difference = biome2.getDistance(target) - biome1.getDistance(target);
        final float epsilon = 0.001f;
        if (difference < -epsilon) {
            return -1;
        }
        if (difference > epsilon) {
            return 1;
        }
        return 0;
    }

    public static Chunk generateChunk(
            final long seed, final int chunkCoordinateX, final int chunkCoordinateZ) {
        var chunk = new Chunk();

        for (int x = 0; x < World.CHUNK_WIDTH; ++x) {
            for (int z = 0; z < World.CHUNK_WIDTH; ++z) {
                var params =
                        ParameterPack.generateParameters(
                                RandomGen::generateSimplexNoise,
                                seed,
                                chunkCoordinateX + x,
                                chunkCoordinateZ + z);
                var biomeName = pickBiome(biomes, params);
                chunk.biomes[x][z] = biomeEncoding.get(biomeName);
            }
        }
        return chunk;
    }

    public static String pickBiome(
            Map<String, BiomeParameters> biomes, final @NonNull ParameterPack parameters) {
        return biomes.entrySet().stream()
                .filter(entry -> entry.getValue().contains(parameters))
                .sorted((e1, e2) -> compareBiomes(e1.getValue(), e2.getValue(), parameters))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    /** Private constructor so that this class is not instantiated. */
    private WorldGenerator() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
