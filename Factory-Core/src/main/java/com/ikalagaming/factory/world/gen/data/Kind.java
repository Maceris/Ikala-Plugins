package com.ikalagaming.factory.world.gen.data;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * The kinds of world generation data file, each in its own folder under {@code
 * mods/<mod>/worldgen/}.
 */
@Getter
@AllArgsConstructor
public enum Kind {
    /** Noise sources. */
    NOISE("noise"),
    /** Density functions, scalar fields over the world. */
    DENSITY("density"),
    /** Named density functions biomes are selected by. */
    PARAMETER("parameter"),
    /** Biomes. */
    BIOME("biome"),
    /** Which block goes where. */
    BLOCK_RULES("block_rules"),
    /** How open space fills with fluid. */
    FLUIDS("fluids"),
    /** What ties a world together. */
    WORLD_TYPE("world_type");

    /** The folder the kind's files are in. */
    private final String folder;
}
