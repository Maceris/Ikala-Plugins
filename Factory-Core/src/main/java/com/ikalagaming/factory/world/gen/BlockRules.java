package com.ikalagaming.factory.world.gen;

import com.ikalagaming.factory.world.Block;
import com.ikalagaming.factory.world.gen.density.DensityNode;
import com.ikalagaming.factory.world.gen.density.EvalCache;

import lombok.NonNull;

import java.util.List;
import java.util.Set;

/**
 * An ordered list of rules choosing which block a solid position gets; the first rule that matches
 * wins. A rule can hand over to another list instead, which acts as a group: if nothing in the
 * group matches, the outer list carries on.
 *
 * @param id The data ID, or an inline group's location.
 * @param rules The rules, in order.
 */
public record BlockRules(@NonNull String id, @NonNull List<Rule> rules) {

    /**
     * What is known about one solid position while choosing its block.
     *
     * @param x The block's x.
     * @param y The block's y.
     * @param z The block's z.
     * @param airAbove How many blocks up to the first non-solid one, capped at {@value
     *     #EXPOSURE_CAP}.
     * @param airBelow The same, going down.
     * @param biome The biome's ID.
     * @param fluidAdjacent Whether a face touches fluid.
     * @param cache The chunk's evaluation cache.
     */
    public record Context(
            long x,
            long y,
            long z,
            int airAbove,
            int airBelow,
            @NonNull String biome,
            boolean fluidAdjacent,
            EvalCache cache) {}

    /** How far {@code air_above} and {@code air_below} look; this means "this far or more". */
    public static final int EXPOSURE_CAP = 16;

    /** A test on a position. */
    public interface Condition {
        /**
         * Whether the position passes.
         *
         * @param context What is known about the position.
         * @return True if it passes.
         */
        boolean test(@NonNull Context context);
    }

    /**
     * One rule: if the condition holds, the block, or the first match of a nested list.
     *
     * @param condition The test, or null to always match.
     * @param block The block to place, or null when handing over to {@code nested}.
     * @param nested The list to hand over to, or null when placing {@code block}.
     */
    public record Rule(Condition condition, Block block, BlockRules nested) {}

    /**
     * The first matching rule's block.
     *
     * @param context What is known about the position.
     * @return The block, or null if nothing matched.
     */
    public Block choose(@NonNull Context context) {
        for (Rule rule : rules) {
            if (rule.condition() != null && !rule.condition().test(context)) {
                continue;
            }
            if (rule.block() != null) {
                return rule.block();
            }
            final Block nested = rule.nested().choose(context);
            if (nested != null) {
                return nested;
            }
        }
        return null;
    }

    /**
     * Distance to air above, within a range.
     *
     * @param min The smallest passing distance.
     * @param max The largest passing distance.
     */
    public record AirAbove(int min, int max) implements Condition {
        @Override
        public boolean test(@NonNull Context context) {
            return context.airAbove() >= min && context.airAbove() <= max;
        }
    }

    /**
     * Distance to air below, within a range.
     *
     * @param min The smallest passing distance.
     * @param max The largest passing distance.
     */
    public record AirBelow(int min, int max) implements Condition {
        @Override
        public boolean test(@NonNull Context context) {
            return context.airBelow() >= min && context.airBelow() <= max;
        }
    }

    /**
     * In one of some biomes.
     *
     * @param ids The biome IDs.
     */
    public record InBiome(@NonNull Set<String> ids) implements Condition {
        @Override
        public boolean test(@NonNull Context context) {
            return ids.contains(context.biome());
        }
    }

    /**
     * A density function's value within a range: any field can drive block choice.
     *
     * @param density The function.
     * @param min The smallest passing value.
     * @param max The largest passing value.
     */
    public record DensityRange(@NonNull DensityNode density, double min, double max)
            implements Condition {
        @Override
        public boolean test(@NonNull Context context) {
            final double v = density.value(context.x(), context.y(), context.z(), context.cache());
            return v >= min && v <= max;
        }
    }

    /**
     * Whether a face touches fluid.
     *
     * @param adjacent True to pass when touching fluid, false to pass when not.
     */
    public record FluidAdjacent(boolean adjacent) implements Condition {
        @Override
        public boolean test(@NonNull Context context) {
            return context.fluidAdjacent() == adjacent;
        }
    }

    /** How several conditions combine. */
    public enum Logic {
        /** Every one passes. */
        ALL,
        /** At least one passes. */
        ANY,
        /** None passes. */
        NOT
    }

    /**
     * Several conditions combined.
     *
     * @param logic How they combine.
     * @param of The conditions.
     */
    public record Combined(@NonNull Logic logic, @NonNull List<Condition> of) implements Condition {
        @Override
        public boolean test(@NonNull Context context) {
            return switch (logic) {
                case ALL -> of.stream().allMatch(c -> c.test(context));
                case ANY -> of.stream().anyMatch(c -> c.test(context));
                case NOT -> of.stream().noneMatch(c -> c.test(context));
            };
        }
    }
}
