package com.ikalagaming.factory.world.gen;

import com.ikalagaming.factory.world.gen.density.DensityNode;
import com.ikalagaming.factory.world.gen.density.Interval;

import lombok.NonNull;

/**
 * A named density function that biomes are selected by, like temperature. Biomes give ranges in the
 * same units, usually -1 to 1.
 *
 * @param id The data ID.
 * @param density The function.
 * @param displayRange The range debug views show, which doesn't affect generation.
 */
public record Parameter(
        @NonNull String id, @NonNull DensityNode density, @NonNull Interval displayRange) {

    /**
     * The part of the ID after the mod name, which biomes can use when it is unambiguous.
     *
     * @return The short name, like {@code temperature}.
     */
    public String shortName() {
        return id.substring(id.indexOf(':') + 1);
    }
}
