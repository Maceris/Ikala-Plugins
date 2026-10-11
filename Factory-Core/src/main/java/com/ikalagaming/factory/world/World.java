package com.ikalagaming.factory.world;

import lombok.extern.slf4j.Slf4j;

/**
 * Tracks the state of the world.
 *
 * <p>The world is made of cubic chunks, {@value #CHUNK_SIZE} blocks on every side, and y is just
 * another coordinate: there is no height limit, floor or surface in code. What exists at any height
 * comes from the world type's generation data, inside its world border.
 *
 * @author Ches Burks
 */
@Slf4j
public class World {
    /** The number of blocks along each side of a chunk. */
    public static final int CHUNK_SIZE = 16;

    /** How many bits a block coordinate shifts by to give its chunk coordinate. */
    public static final int CHUNK_SHIFT = Integer.numberOfTrailingZeros(CHUNK_SIZE);

    /** The number of blocks in a chunk. */
    public static final int CHUNK_VOLUME = CHUNK_SIZE * CHUNK_SIZE * CHUNK_SIZE;

    /** The name of empty space, which the engine defines rather than any mod. */
    public static final String AIR_NAME = "factory:air";

    /** Empty space. */
    public static final Block AIR = new Block(AIR_NAME, null);
}
