package com.ikalagaming.graphics.scene;

import lombok.NonNull;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.Arrays;
import javax.annotation.Nullable;

/**
 * A patch of water's surface, as a height field over a grid of square cells: where the water is,
 * how high its surface stands at each cell corner, how deep the bottom lies, and what the water
 * holds. Levels and slopes of game water are just heights; graphics knows nothing of blocks. Placed
 * through {@link com.ikalagaming.graphics.Water}.
 *
 * <p>Corner values run along x first, then z: corner (x, z) is at index {@code z * (cellsX + 1) +
 * x}. Cell (x, z) spans corners x to x + 1 and z to z + 1 and is at index {@code z * cellsX + x}.
 *
 * <p>Immutable: the arrays are copied in, and handed out as copies.
 *
 * @param origin The grid's corner with the smallest x and z, in the world; heights are above its y.
 * @param cellSize How wide each cell is, in meters.
 * @param cellsX How many cells across x, 1 to {@link #MAX_CELLS}.
 * @param cellsZ How many cells across z, 1 to {@link #MAX_CELLS}.
 * @param heights The surface's height above the origin at each corner, (cellsX + 1) * (cellsZ + 1)
 *     of them.
 * @param bottoms The bottom's height above the origin at each corner, the same layout, or null when
 *     unknown; graphics sees the bottom anyway where it is drawn.
 * @param mask Which cells hold water, cellsX * cellsZ of them, or null for every cell.
 * @param body What the water holds.
 */
public record WaterTile(
        @NonNull Vector3dc origin,
        float cellSize,
        int cellsX,
        int cellsZ,
        float @NonNull [] heights,
        @Nullable float[] bottoms,
        @Nullable boolean[] mask,
        @NonNull WaterBody body) {

    /** The most cells a tile has along each side. */
    public static final int MAX_CELLS = 64;

    /**
     * Check the sizes and copy the arrays in.
     *
     * @throws IllegalArgumentException If the cell size or counts are out of range, or an array is
     *     the wrong length.
     */
    public WaterTile {
        if (cellSize <= 0 || !Float.isFinite(cellSize)) {
            throw new IllegalArgumentException(
                    "A water tile's cells must have a size, not " + cellSize);
        }
        if (cellsX < 1 || cellsX > MAX_CELLS || cellsZ < 1 || cellsZ > MAX_CELLS) {
            throw new IllegalArgumentException(
                    "A water tile has 1 to "
                            + MAX_CELLS
                            + " cells a side, not "
                            + cellsX
                            + " by "
                            + cellsZ);
        }
        final int corners = (cellsX + 1) * (cellsZ + 1);
        if (heights.length != corners) {
            throw new IllegalArgumentException(
                    "A water tile needs " + corners + " heights, not " + heights.length);
        }
        if (bottoms != null && bottoms.length != corners) {
            throw new IllegalArgumentException(
                    "A water tile needs " + corners + " bottom heights, not " + bottoms.length);
        }
        if (mask != null && mask.length != cellsX * cellsZ) {
            throw new IllegalArgumentException(
                    "A water tile's mask needs " + cellsX * cellsZ + " cells, not " + mask.length);
        }
        origin = new Vector3d(origin);
        heights = heights.clone();
        bottoms = bottoms == null ? null : bottoms.clone();
        mask = mask == null ? null : mask.clone();
    }

    /**
     * A flat patch of water, every cell full, its surface at one height.
     *
     * @param origin The grid's corner with the smallest x and z, in the world.
     * @param cellSize How wide each cell is, in meters.
     * @param cellsX How many cells across x.
     * @param cellsZ How many cells across z.
     * @param height The surface's height above the origin.
     * @param body What the water holds.
     * @return The tile.
     */
    public static WaterTile flat(
            @NonNull Vector3dc origin,
            float cellSize,
            int cellsX,
            int cellsZ,
            float height,
            @NonNull WaterBody body) {
        final float[] heights = new float[(Math.max(cellsX, 0) + 1) * (Math.max(cellsZ, 0) + 1)];
        Arrays.fill(heights, height);
        return new WaterTile(origin, cellSize, cellsX, cellsZ, heights, null, null, body);
    }

    /**
     * The same water, holding something else.
     *
     * @param body What it holds now.
     * @return A changed copy.
     */
    public WaterTile withBody(@NonNull WaterBody body) {
        return new WaterTile(origin, cellSize, cellsX, cellsZ, heights, bottoms, mask, body);
    }

    /**
     * Whether a cell holds water.
     *
     * @param x The cell along x.
     * @param z The cell along z.
     * @return Whether it does.
     */
    public boolean isWater(int x, int z) {
        return mask == null || mask[z * cellsX + x];
    }

    /**
     * The surface's height above the origin at a corner.
     *
     * @param x The corner along x, 0 to cellsX.
     * @param z The corner along z, 0 to cellsZ.
     * @return The height, in meters.
     */
    public float heightAt(int x, int z) {
        return heights[z * (cellsX + 1) + x];
    }

    /**
     * How many cells hold water.
     *
     * @return The count.
     */
    public int waterCells() {
        if (mask == null) {
            return cellsX * cellsZ;
        }
        int count = 0;
        for (boolean water : mask) {
            if (water) {
                ++count;
            }
        }
        return count;
    }

    @Override
    public float @NonNull [] heights() {
        return heights.clone();
    }

    @Override
    public @Nullable float[] bottoms() {
        return bottoms == null ? null : bottoms.clone();
    }

    @Override
    public @Nullable boolean[] mask() {
        return mask == null ? null : mask.clone();
    }
}
