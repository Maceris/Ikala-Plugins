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

    private static Map<String, BiomeParameters> biomes = new HashMap<>();
    private static Map<String, Byte> biomeEncoding = new HashMap<>();

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
}
