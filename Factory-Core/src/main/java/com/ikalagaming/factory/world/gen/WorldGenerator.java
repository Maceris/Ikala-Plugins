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
        return biomes.entrySet().stream()
                .filter(entry -> entry.getValue().contains(parameters))
                .sorted(
                        (e1, e2) ->
                                BiomeParameters.compareSpecificity(e1.getValue(), e2.getValue()))
                .findFirst()
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    void generateChunk() {}
}
