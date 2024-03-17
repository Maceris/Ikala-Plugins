package com.ikalagaming.factory.world;

/**
 * Used to read and write from save files.
 *
 * @author Ches Burks
 */
public class WorldSave {
    /**
     * The width in chunks of a region. A region stores {@value}x{@value} chunks, each of which is
     * {@value World#CHUNK_WIDTH}x{@value World#CHUNK_WIDTH} blocks wide.
     */
    public static final int REGION_WIDTH = 32;

    public void saveNewRegion() {}
}
