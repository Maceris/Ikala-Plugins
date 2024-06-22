package com.ikalagaming.factory.world.gen;

import com.ikalagaming.factory.world.Block;
import com.ikalagaming.factory.world.Chunk;
import com.ikalagaming.factory.world.World;
import com.ikalagaming.random.RandomGen;

import lombok.NonNull;

import java.util.*;
import java.util.stream.Collectors;

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

    private static final Map<String, BiomeParameters> biomes = new HashMap<>();
    private static final Map<String, Byte> biomeEncoding = new HashMap<>();
    private static final Map<String, BiomeDefinition> biomeDefinitions = new HashMap<>();

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

    /**
     * Compare two biomes based on a set of parameters.
     *
     * @param first A map from biome name to parameters, for the first biome.
     * @param second A map from biome name to parameters, for the second biome.
     * @param target The parameters used to pick the biome.
     * @return The value 0 if the values are equivalent; the value less than 0 if first is a better
     *     match than the second; and a value greater than 0 if the first is a worse match than the
     *     second.
     */
    private static int compareBiomes(
            @NonNull Map.Entry<String, BiomeParameters> first,
            @NonNull Map.Entry<String, BiomeParameters> second,
            @NonNull ParameterPack target) {

        BiomeParameters firstBiome = first.getValue();
        BiomeParameters secondBiome = second.getValue();
        float distanceDifference = secondBiome.getDistance(target) - firstBiome.getDistance(target);
        final float epsilon = 0.001f;
        if (distanceDifference < -epsilon) {
            return -1;
        }
        if (distanceDifference > epsilon) {
            return 1;
        }
        // NOTE(ches) They are the same distance away, and may share a center point.
        float rangeDifference = secondBiome.getTotalWidth() - firstBiome.getTotalWidth();
        if (rangeDifference < -epsilon) {
            return -1;
        }
        if (rangeDifference > epsilon) {
            return 1;
        }
        return 0;
    }

    public static Chunk generateChunk(
            final long seed, final int chunkCoordinateX, final int chunkCoordinateZ) {
        var chunk = new Chunk();

        for (int x = 0; x < World.CHUNK_WIDTH; ++x) {
            for (int z = 0; z < World.CHUNK_WIDTH; ++z) {
                final int actualX = chunkCoordinateX + x;
                final int actualZ = chunkCoordinateZ + z;
                var params =
                        ParameterPack.generateParameters(
                                RandomGen::generateSimplexNoise, seed, actualX, actualZ);
                var biomeName = pickBiome(biomes, params, actualX, actualZ);
                chunk.setBiome(x, z, biomeEncoding.get(biomeName));

                final int height = calculateHeight(params.height());

                var definition = biomeDefinitions.get(biomeName);

                int currentLayer = 0;

                for (int y = height; y >= World.WORLD_HEIGHT_MIN; --y) {

                }
            }
        }
        return chunk;
    }

    /**
     * Select a biome from a list given a set of biome parameters and coordinates.
     *
     * @param biomes The list of biomes we are selecting from.
     * @param parameters The biome parameters that have been generated to pick a biome with.
     * @param x The x coordinate that we want a biome for.
     * @param z The z coordinate that we want a biome for.
     * @return The name of the biome we have selected.
     */
    public static String pickBiome(
            @NonNull Map<String, BiomeParameters> biomes,
            final @NonNull ParameterPack parameters,
            int x,
            int z) {

        List<Map.Entry<String, BiomeParameters>> choices =
                biomes.entrySet().stream()
                        .filter(entry -> entry.getValue().contains(parameters))
                        .sorted((e1, e2) -> compareBiomes(e1, e2, parameters))
                        .collect(Collectors.toCollection(ArrayList::new));

        if (choices.isEmpty()) {
            var fallbacks =
                    biomes.entrySet().stream()
                            .sorted((e1, e2) -> compareBiomes(e1, e2, parameters))
                            .collect(Collectors.toCollection(ArrayList::new));
            pruneWorseChoices(fallbacks, parameters);
            if (fallbacks.size() == 1) {
                return fallbacks.get(0).getKey();
            }
            var names = fallbacks.stream().map(Map.Entry::getKey).toList();
            return pickBiomeBasedOnName(names, x, z);
        }
        if (choices.size() == 1) {
            return choices.get(0).getKey();
        }

        pruneWorseChoices(choices, parameters);

        if (choices.size() == 1) {
            return choices.get(0).getKey();
        }

        var names = choices.stream().map(Map.Entry::getKey).toList();
        return pickBiomeBasedOnName(names, x, z);
    }

    /**
     * Pick a biome from a list of valid choices based on the unique names and coordinates.
     *
     * @param choices The list of biome names that are all equivalently good choices.
     * @param x The x coordinate that we want a biome for.
     * @param z The z coordinate that we want a biome for.
     * @return The name of the biome we have selected.
     */
    private static String pickBiomeBasedOnName(@NonNull final List<String> choices, int x, int z) {
        var orderedChoices =
                choices.stream().sorted(Comparator.comparingInt(String::hashCode)).toList();
        var selection = Arrays.hashCode(new int[] {x, z}) % orderedChoices.size();
        return orderedChoices.get(selection);
    }

    /**
     * Remove any entries from the list that are worse matches than the first, presumably best,
     * choice.
     *
     * @param choices The choices, sorted by how close their centers are to the parameters and their
     *     width. The best matches should be first.
     * @param parameters The parameters used to generate the biome.
     */
    private static void pruneWorseChoices(
            @NonNull List<Map.Entry<String, BiomeParameters>> choices,
            final @NonNull ParameterPack parameters) {
        var firstItem = choices.get(0).getValue();
        var distance = firstItem.getDistance(parameters);
        var width = firstItem.getTotalWidth();

        choices.removeIf(
                entry ->
                        entry.getValue().getDistance(parameters) > distance
                                || entry.getValue().getTotalWidth() > width);
    }

    /** Private constructor so that this class is not instantiated. */
    private WorldGenerator() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
