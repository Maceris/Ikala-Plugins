package com.ikalagaming.factory.world.gen;

import com.ikalagaming.factory.world.Block;
import com.ikalagaming.factory.world.gen.density.Box;
import com.ikalagaming.factory.world.gen.density.DensityNode;
import com.ikalagaming.factory.world.gen.density.EvalCache;
import com.ikalagaming.factory.world.gen.density.Interval;

import lombok.NonNull;

/**
 * How open space fills with fluid. There's no sea level in the engine: an ocean is a level fluid
 * with a constant level of 0, given in data.
 */
public interface Fluids {

    /**
     * The fluid at an open position.
     *
     * @param x The x coordinate.
     * @param y The y coordinate.
     * @param z The z coordinate.
     * @param cache The chunk's evaluation cache.
     * @return The fluid block, or null for air.
     */
    Block fluidAt(long x, long y, long z, EvalCache cache);

    /**
     * The highest a fluid can reach over a region, for skipping regions certainly above it.
     *
     * @param box The region.
     * @return The range of the fluid level, or null if there's never fluid.
     */
    Interval levelBounds(@NonNull Box box);

    /** No fluid anywhere. */
    record None() implements Fluids {
        @Override
        public Block fluidAt(long x, long y, long z, EvalCache cache) {
            return null;
        }

        @Override
        public Interval levelBounds(@NonNull Box box) {
            return null;
        }
    }

    /**
     * Open space below a level fills with a fluid.
     *
     * @param level The level, a density function: open space with y below it fills.
     * @param fluid The fluid's block.
     */
    record Level(@NonNull DensityNode level, @NonNull Block fluid) implements Fluids {
        @Override
        public Block fluidAt(long x, long y, long z, EvalCache cache) {
            return y < level.value(x, y, z, cache) ? fluid : null;
        }

        @Override
        public Interval levelBounds(@NonNull Box box) {
            return level.bounds(box);
        }
    }
}
