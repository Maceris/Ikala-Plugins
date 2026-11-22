package com.ikalagaming.factory.sim;

/**
 * What movement sees of the blocks: whether each one stops a moving box. The server's view is the
 * chunks it has sent the player, and the client's the chunks it has received, so that blocks
 * outside them are solid on both sides and the two agree at the edge of the loaded area.
 *
 * <p>A view must answer the same way for the same blocks every time it is asked within a frame, and
 * must not change the simulation.
 */
@FunctionalInterface
public interface BlockView {

    /** Nothing is solid. */
    BlockView EMPTY = (x, y, z) -> false;

    /** Everything is solid, as for a player with no chunks yet. */
    BlockView SOLID = (x, y, z) -> true;

    /**
     * Whether a block stops movement.
     *
     * @param x The block's x.
     * @param y The block's y.
     * @param z The block's z.
     * @return True if it is solid, or missing.
     */
    boolean solid(int x, int y, int z);
}
