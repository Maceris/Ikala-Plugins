package com.ikalagaming.factory.world.gen;

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

    private static String pickBiome(final @NonNull ParameterPack parameters) {
        var matches =
                biomes.entrySet().stream()
                        .filter(entry -> entry.getValue().contains(parameters))
                        .sorted((e1, e2) -> compareBiomes(e1.getValue(), e2.getValue(), parameters))
                        .map(Map.Entry::getKey)
                        .toList();

        if (matches.isEmpty()) {
            return null;
        }
        if (matches.size() == 1) {
            return matches.get(0);
        }

        return null;
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

    void generateChunk() {}
}
