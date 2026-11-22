package com.ikalagaming.factory.world.gen;

import lombok.NonNull;

import java.util.Map;
import java.util.Set;

/**
 * A biome: which parameter values it suits, and what it does to blocks. Terrain is shaped from the
 * parameters, not from the biome, so biome borders don't leave seams; biomes only choose blocks.
 *
 * @param id The data ID.
 * @param climate The range of each parameter the biome suits, by the name the biome file used.
 *     Parameters it leaves out match anything.
 * @param weight How strongly it wins exact ties; rare biomes use a lower weight.
 * @param blockRules The rules that choose its blocks, or null to use the world type's default block
 *     everywhere.
 * @param tags Its tags, such as {@code lotomation:grassy_biomes}, which placed features choose
 *     biomes by.
 */
public record Biome(
        @NonNull String id,
        @NonNull Map<String, ParameterRange> climate,
        double weight,
        BlockRules blockRules,
        @NonNull Set<String> tags) {

    /** Copy the tags. */
    public Biome {
        tags = Set.copyOf(tags);
    }

    /**
     * A biome with no tags.
     *
     * @param id The data ID.
     * @param climate The parameter ranges it is chosen by.
     * @param weight Its weight when choosing.
     * @param blockRules Its block rules, or null for none.
     */
    public Biome(
            @NonNull String id,
            @NonNull Map<String, ParameterRange> climate,
            double weight,
            BlockRules blockRules) {
        this(id, climate, weight, blockRules, Set.of());
    }
}
