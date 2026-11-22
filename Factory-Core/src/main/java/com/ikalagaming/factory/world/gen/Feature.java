package com.ikalagaming.factory.world.gen;

import com.ikalagaming.factory.world.Block;

import lombok.NonNull;

import java.util.List;

/**
 * What a placed feature builds. Feature types are code; their settings are data. See the Features
 * and Structures design doc.
 */
public sealed interface Feature {

    /**
     * A tree of a species, grown from its own seed.
     *
     * @param species The species.
     */
    record Tree(@NonNull TreeSpecies species) implements Feature {}

    /**
     * One block in its own cell, picked by weight, for grass tufts and flowers.
     *
     * @param blocks The blocks to pick from.
     * @param weights Each block's weight, all above zero.
     */
    record SingleBlock(@NonNull List<Block> blocks, double @NonNull [] weights) implements Feature {

        /**
         * Check the lists match.
         *
         * @throws IllegalArgumentException If they are empty, differ in length, or a weight isn't
         *     above zero.
         */
        public SingleBlock {
            if (blocks.isEmpty() || blocks.size() != weights.length) {
                throw new IllegalArgumentException(
                        "A single block feature needs one weight per block, and at least one");
            }
            for (double weight : weights) {
                if (weight <= 0) {
                    throw new IllegalArgumentException("Weights must be above zero: " + weight);
                }
            }
            blocks = List.copyOf(blocks);
            weights = weights.clone();
        }

        /**
         * Pick a block.
         *
         * @param roll A number from 0 up to 1.
         * @return The block whose share of the total weight the roll falls in.
         */
        public Block pick(double roll) {
            double total = 0;
            for (double weight : weights) {
                total += weight;
            }
            double at = roll * total;
            for (int i = 0; i < weights.length; ++i) {
                at -= weights[i];
                if (at < 0) {
                    return blocks.get(i);
                }
            }
            return blocks.get(blocks.size() - 1);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof SingleBlock that
                    && blocks.equals(that.blocks)
                    && java.util.Arrays.equals(weights, that.weights);
        }

        @Override
        public int hashCode() {
            return 31 * blocks.hashCode() + java.util.Arrays.hashCode(weights);
        }

        @Override
        public String toString() {
            return "SingleBlock[blocks="
                    + blocks
                    + ", weights="
                    + java.util.Arrays.toString(weights)
                    + "]";
        }
    }
}
