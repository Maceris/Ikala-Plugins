package com.ikalagaming.factory.world.gen;

import lombok.NonNull;

import java.util.Map;

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
 */
public record Biome(
        @NonNull String id,
        @NonNull Map<String, ParameterRange> climate,
        double weight,
        BlockRules blockRules) {}
