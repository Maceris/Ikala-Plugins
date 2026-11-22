package com.ikalagaming.factory.world.gen;

import lombok.NonNull;

import java.util.List;
import java.util.Set;

/**
 * A feature, and where and how often it goes. Only the small layer is built: single blocks placed
 * with {@code each_floor}, where every air cell with solid ground under it rolls once against a
 * chance. See the Features and Structures design doc.
 *
 * @param id The data ID.
 * @param feature What it builds.
 * @param biomes The biomes it can go in, tags already resolved.
 * @param priority Lower goes first; within a priority, by ID. A cell holds the first feature that
 *     lands in it.
 * @param chance The chance each floor cell rolls against, from 0 to 1.
 * @param conditions The block rule conditions every cell must pass, tested on the cell itself, with
 *     {@link BlockRules.Context#below()} the block under it.
 * @param salt Mixed into every roll, so each placed feature rolls on its own.
 */
public record PlacedFeature(
        @NonNull String id,
        @NonNull Feature feature,
        @NonNull Set<String> biomes,
        int priority,
        double chance,
        @NonNull List<BlockRules.Condition> conditions,
        long salt) {

    /**
     * Copy the collections.
     *
     * @throws IllegalArgumentException If the chance is outside 0 to 1.
     */
    public PlacedFeature {
        if (!(chance >= 0 && chance <= 1)) {
            throw new IllegalArgumentException("Chance must be from 0 to 1: " + chance);
        }
        biomes = Set.copyOf(biomes);
        conditions = List.copyOf(conditions);
    }

    /**
     * Whether every condition passes.
     *
     * @param context The cell.
     * @return True if they all pass.
     */
    public boolean passes(@NonNull BlockRules.Context context) {
        for (BlockRules.Condition condition : conditions) {
            if (!condition.test(context)) {
                return false;
            }
        }
        return true;
    }
}
