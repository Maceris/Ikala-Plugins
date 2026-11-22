package com.ikalagaming.factory.world.gen;

import com.ikalagaming.factory.world.gen.density.DensityNode;

import lombok.NonNull;

import java.util.List;
import java.util.Set;

/**
 * A feature, and where and how often it goes. See the Features and Structures design doc.
 *
 * <ul>
 *   <li>The {@link Layer#SMALL small} layer places single blocks with {@code each_floor}: every
 *       open position with solid ground under it rolls once against {@link #chance()}.
 *   <li>The {@link Layer#LARGE large} layer places instances, such as trees, over {@link #cells()}:
 *       each cell holds a few candidates, each finds the floor below it in its cell, and one
 *       survives only if no stronger candidate is within its spacing.
 * </ul>
 *
 * @param id The data ID.
 * @param feature What it builds.
 * @param biomes The biomes it can go in, tags already resolved, tested at the open position it
 *     stands in.
 * @param priority Lower goes first; within a priority, by ID.
 * @param layer Which layer it is in.
 * @param chance For the small layer, the chance each floor rolls against, from 0 to 1; 0 for the
 *     large layer.
 * @param cells For the large layer, how candidates are spread; null for the small layer.
 * @param conditions The block rule conditions a position must pass, tested on the open position it
 *     stands in, with {@link BlockRules.Context#below()} the block under it.
 * @param salt Mixed into every roll, so each placed feature rolls on its own.
 */
public record PlacedFeature(
        @NonNull String id,
        @NonNull Feature feature,
        @NonNull Set<String> biomes,
        int priority,
        @NonNull Layer layer,
        double chance,
        Cells cells,
        @NonNull List<BlockRules.Condition> conditions,
        long salt) {

    /** Which layer a placed feature is in. */
    public enum Layer {
        /** Instances bigger than a block, such as trees. They go in first. */
        LARGE,
        /** Single blocks, such as grass tufts, in whatever open space is left. */
        SMALL
    }

    /**
     * How the large layer spreads candidates.
     *
     * @param size The cell's size along x, y and z, in blocks.
     * @param candidates How many candidates each cell holds.
     * @param spacingMin The closest a stronger candidate may be.
     * @param spacingMax The spacing where {@code by} is 0, or always when there is no {@code by}.
     * @param by A density from 0 to 1 that moves the spacing from {@code spacingMax} to {@code
     *     spacingMin}, such as how dense a forest is there, or null.
     */
    public record Cells(
            int @NonNull [] size,
            int candidates,
            double spacingMin,
            double spacingMax,
            DensityNode by) {

        /**
         * Check the values.
         *
         * @throws IllegalArgumentException If a size or the count isn't above zero, or the spacing
         *     is negative or reversed.
         */
        public Cells {
            if (size.length != 3 || size[0] < 1 || size[1] < 1 || size[2] < 1) {
                throw new IllegalArgumentException("A cell's size must be three whole numbers");
            }
            if (candidates < 1) {
                throw new IllegalArgumentException("A cell needs at least one candidate");
            }
            if (spacingMin < 0 || spacingMax < spacingMin) {
                throw new IllegalArgumentException("Spacing must be min then max, from 0");
            }
            size = size.clone();
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Cells that
                    && java.util.Arrays.equals(size, that.size)
                    && candidates == that.candidates
                    && spacingMin == that.spacingMin
                    && spacingMax == that.spacingMax
                    && java.util.Objects.equals(by, that.by);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(
                    java.util.Arrays.hashCode(size), candidates, spacingMin, spacingMax, by);
        }

        @Override
        public String toString() {
            return "Cells[size="
                    + java.util.Arrays.toString(size)
                    + ", candidates="
                    + candidates
                    + ", spacing="
                    + spacingMin
                    + ".."
                    + spacingMax
                    + "]";
        }
    }

    /**
     * Check the layer has what it needs, and copy the collections.
     *
     * @throws IllegalArgumentException If the chance is outside 0 to 1, or the layer's placement is
     *     missing.
     */
    public PlacedFeature {
        if (!(chance >= 0 && chance <= 1)) {
            throw new IllegalArgumentException("Chance must be from 0 to 1: " + chance);
        }
        if (layer == Layer.LARGE && (cells == null || !(feature instanceof Feature.Tree))) {
            throw new IllegalArgumentException("The large layer places trees over cells");
        }
        if (layer == Layer.SMALL && !(feature instanceof Feature.SingleBlock)) {
            throw new IllegalArgumentException("The small layer places single blocks");
        }
        biomes = Set.copyOf(biomes);
        conditions = List.copyOf(conditions);
    }

    /**
     * Whether every condition passes.
     *
     * @param context The position.
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
