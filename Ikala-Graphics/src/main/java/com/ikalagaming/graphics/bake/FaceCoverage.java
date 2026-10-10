package com.ikalagaming.graphics.bake;

import lombok.NonNull;

import java.util.Arrays;

/**
 * How much of one box face a mesh covers, as a 16×16 grid of cells, one bit each, 256 bits in
 * {@value #WORDS} longs. Cell {@code (column, row)} is bit {@link #cell(int, int)}, with columns
 * along the face's {@code u} and rows along its {@code v} (see {@link Face}).
 *
 * <p>Each face gets two masks, so that comparing them errs on the side of drawing:
 *
 * <ul>
 *   <li>{@code covers}: cells the face covers completely. Used for the face doing the hiding.
 *   <li>{@code touches}: cells the face reaches into at all. Used for the face being hidden.
 * </ul>
 *
 * A face is hidden by its neighbor when everything it touches is covered by the neighbor's opposite
 * face, see {@link #contains(long[], long[])}.
 */
public record FaceCoverage(long @NonNull [] covers, long @NonNull [] touches) {

    /** How many cells there are along each side of a face. */
    public static final int GRID_SIZE = 16;

    /** How many cells a face has. */
    public static final int CELL_COUNT = GRID_SIZE * GRID_SIZE;

    /** How many longs a mask takes. */
    public static final int WORDS = CELL_COUNT / Long.SIZE;

    /**
     * How many points are tested along each side of a cell to decide whether it is covered,
     * including both edges. Exact for faces made of rectangles on the grid, like cubes and slabs.
     */
    public static final int SAMPLES_PER_SIDE = 4;

    /** How far outside a triangle a point can be and still count as inside, in face units. */
    public static final float EDGE_TOLERANCE = 1e-5f;

    /** A face nothing lies on. */
    public static final FaceCoverage EMPTY = new FaceCoverage(new long[WORDS], new long[WORDS]);

    /**
     * The bit of a cell.
     *
     * @param column The column, along {@code u}.
     * @param row The row, along {@code v}.
     * @return The bit index.
     */
    public static int cell(int column, int row) {
        return row * GRID_SIZE + column;
    }

    /**
     * Whether a cell is set in a mask.
     *
     * @param mask The mask.
     * @param cell The bit index.
     * @return True if set.
     */
    public static boolean get(long @NonNull [] mask, int cell) {
        return (mask[cell >>> 6] & (1L << (cell & 63))) != 0;
    }

    /**
     * Set a cell in a mask.
     *
     * @param mask The mask.
     * @param cell The bit index.
     */
    public static void set(long @NonNull [] mask, int cell) {
        mask[cell >>> 6] |= 1L << (cell & 63);
    }

    /**
     * A mask with every cell set.
     *
     * @return A new full mask.
     */
    public static long[] full() {
        long[] mask = new long[WORDS];
        Arrays.fill(mask, -1L);
        return mask;
    }

    /**
     * Whether one mask lies inside another.
     *
     * @param inner The mask that must be inside.
     * @param outer The mask it must be inside.
     * @return True if every cell of {@code inner} is set in {@code outer}.
     */
    public static boolean contains(long @NonNull [] inner, long @NonNull [] outer) {
        for (int i = 0; i < WORDS; ++i) {
            if ((inner[i] & ~outer[i]) != 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether a mask has no cells set.
     *
     * @param mask The mask.
     * @return True if empty.
     */
    public static boolean isEmpty(long @NonNull [] mask) {
        for (long word : mask) {
            if (word != 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether this face has nothing on it.
     *
     * @return True if no triangle touches it.
     */
    public boolean isEmpty() {
        return isEmpty(touches);
    }

    /**
     * Rasterize triangles lying on a face.
     *
     * @param triangles Each triangle's corners in face units, {@code u0, v0, u1, v1, u2, v2}, where
     *     the face runs from 0 to 1 along both.
     * @param count How many triangles there are.
     * @return The coverage, or {@link #EMPTY} if there are no triangles.
     */
    public static FaceCoverage rasterize(float @NonNull [] triangles, int count) {
        if (count == 0) {
            return EMPTY;
        }
        final long[] covers = new long[WORDS];
        final long[] touches = new long[WORDS];
        for (int row = 0; row < GRID_SIZE; ++row) {
            final float cellV0 = (float) row / GRID_SIZE;
            final float cellV1 = (float) (row + 1) / GRID_SIZE;
            for (int column = 0; column < GRID_SIZE; ++column) {
                final float cellU0 = (float) column / GRID_SIZE;
                final float cellU1 = (float) (column + 1) / GRID_SIZE;
                if (anyBoundsOverlap(triangles, count, cellU0, cellV0, cellU1, cellV1)) {
                    set(touches, cell(column, row));
                }
                if (allSamplesInside(triangles, count, cellU0, cellV0)) {
                    set(covers, cell(column, row));
                }
            }
        }
        return new FaceCoverage(covers, touches);
    }

    /**
     * Whether any triangle's bounding rectangle reaches into a cell's interior. An over-estimate of
     * the triangles themselves, which is what {@code touches} wants.
     *
     * @param triangles The triangles.
     * @param count How many there are.
     * @param u0 The cell's low u.
     * @param v0 The cell's low v.
     * @param u1 The cell's high u.
     * @param v1 The cell's high v.
     * @return True if one reaches in.
     */
    private static boolean anyBoundsOverlap(
            float @NonNull [] triangles, int count, float u0, float v0, float u1, float v1) {
        for (int t = 0; t < count; ++t) {
            final int base = t * 6;
            final float minU =
                    Math.min(triangles[base], Math.min(triangles[base + 2], triangles[base + 4]));
            final float maxU =
                    Math.max(triangles[base], Math.max(triangles[base + 2], triangles[base + 4]));
            final float minV =
                    Math.min(
                            triangles[base + 1],
                            Math.min(triangles[base + 3], triangles[base + 5]));
            final float maxV =
                    Math.max(
                            triangles[base + 1],
                            Math.max(triangles[base + 3], triangles[base + 5]));
            // Only touching an edge of the cell doesn't count
            if (maxU > u0 + EDGE_TOLERANCE
                    && minU < u1 - EDGE_TOLERANCE
                    && maxV > v0 + EDGE_TOLERANCE
                    && minV < v1 - EDGE_TOLERANCE) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether every sample point of a cell, edges included, is inside some triangle.
     *
     * @param triangles The triangles.
     * @param count How many there are.
     * @param u0 The cell's low u.
     * @param v0 The cell's low v.
     * @return True if the cell counts as covered.
     */
    private static boolean allSamplesInside(
            float @NonNull [] triangles, int count, float u0, float v0) {
        final float step = 1.0f / GRID_SIZE / (SAMPLES_PER_SIDE - 1);
        for (int i = 0; i < SAMPLES_PER_SIDE; ++i) {
            for (int j = 0; j < SAMPLES_PER_SIDE; ++j) {
                if (!insideAny(triangles, count, u0 + i * step, v0 + j * step)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Whether a point is inside any of the triangles, edges included.
     *
     * @param triangles The triangles.
     * @param count How many there are.
     * @param u The point's u.
     * @param v The point's v.
     * @return True if inside one.
     */
    static boolean insideAny(float @NonNull [] triangles, int count, float u, float v) {
        for (int t = 0; t < count; ++t) {
            final int base = t * 6;
            final float ax = triangles[base];
            final float ay = triangles[base + 1];
            final float bx = triangles[base + 2];
            final float by = triangles[base + 3];
            final float cx = triangles[base + 4];
            final float cy = triangles[base + 5];
            final float area = (bx - ax) * (cy - ay) - (by - ay) * (cx - ax);
            if (Math.abs(area) < EDGE_TOLERANCE * EDGE_TOLERANCE) {
                continue;
            }
            // Edge functions, made positive inside whichever way the triangle winds
            final float sign = Math.signum(area);
            final float e0 = sign * ((bx - ax) * (v - ay) - (by - ay) * (u - ax));
            final float e1 = sign * ((cx - bx) * (v - by) - (cy - by) * (u - bx));
            final float e2 = sign * ((ax - cx) * (v - cy) - (ay - cy) * (u - cx));
            // Scaled by each edge's length, so the tolerance is a distance
            final float tolerance0 = EDGE_TOLERANCE * (float) Math.hypot(bx - ax, by - ay);
            final float tolerance1 = EDGE_TOLERANCE * (float) Math.hypot(cx - bx, cy - by);
            final float tolerance2 = EDGE_TOLERANCE * (float) Math.hypot(ax - cx, ay - cy);
            if (e0 >= -tolerance0 && e1 >= -tolerance1 && e2 >= -tolerance2) {
                return true;
            }
        }
        return false;
    }
}
