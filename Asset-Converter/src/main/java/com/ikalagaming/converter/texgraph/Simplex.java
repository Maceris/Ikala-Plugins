package com.ikalagaming.converter.texgraph;

/**
 * Simplex noise that tiles: the tile is wrapped around a torus in four dimensions, each axis a
 * circle as long as its cells, and 4D simplex noise (after Stefan Gustavson's) is read there. A
 * circle's ends meet, so the noise wraps at the tile's edges, with features about a cell across.
 */
final class Simplex {
    /** Skew factor for four dimensions. */
    private static final double F4 = (Math.sqrt(5) - 1) / 4;

    /** Unskew factor for four dimensions. */
    private static final double G4 = (5 - Math.sqrt(5)) / 20;

    /** Scales the sum to about -1..1. */
    private static final double SCALE = 27;

    /** The 32 gradient directions of 4D simplex noise. */
    private static final int[][] GRADIENTS = gradients();

    /** Not made. */
    private Simplex() {}

    /**
     * The gradients: the midpoints of the edges of a 4D hypercube.
     *
     * @return The 32 directions.
     */
    private static int[][] gradients() {
        final int[][] out = new int[32][];
        int next = 0;
        for (int zero = 0; zero < 4; ++zero) {
            for (int signs = 0; signs < 8; ++signs) {
                final int[] g = new int[4];
                int bit = 0;
                for (int axis = 0; axis < 4; ++axis) {
                    if (axis == zero) {
                        continue;
                    }
                    g[axis] = (signs >> bit & 1) == 0 ? 1 : -1;
                    ++bit;
                }
                out[next++] = g;
            }
        }
        return out;
    }

    /**
     * Noise at a point of the tile, wrapping after a whole number of cells.
     *
     * @param u Across, in cells.
     * @param v Down, in cells.
     * @param cellsX Cells across the tile.
     * @param cellsY Cells down the tile.
     * @param seed The octave's seed.
     * @return The noise, 0 to 1.
     */
    static float torus(float u, float v, int cellsX, int cellsY, long seed) {
        final double angleX = 2 * Math.PI * u / cellsX;
        final double angleY = 2 * Math.PI * v / cellsY;
        final double radiusX = cellsX / (2 * Math.PI);
        final double radiusY = cellsY / (2 * Math.PI);
        final double value =
                noise(
                        radiusX * Math.cos(angleX),
                        radiusX * Math.sin(angleX),
                        radiusY * Math.cos(angleY),
                        radiusY * Math.sin(angleY),
                        seed);
        return (float) Math.clamp(value * 0.5 + 0.5, 0, 1);
    }

    /**
     * 4D simplex noise.
     *
     * @param x The first coordinate.
     * @param y The second.
     * @param z The third.
     * @param w The fourth.
     * @param seed Picks the gradients.
     * @return About -1 to 1.
     */
    static double noise(double x, double y, double z, double w, long seed) {
        final double skew = (x + y + z + w) * F4;
        final int i = (int) Math.floor(x + skew);
        final int j = (int) Math.floor(y + skew);
        final int k = (int) Math.floor(z + skew);
        final int l = (int) Math.floor(w + skew);
        final double unskew = (i + j + k + l) * G4;
        final double[] d = {x - (i - unskew), y - (j - unskew), z - (k - unskew), w - (l - unskew)};
        // Rank the coordinates to find which simplex the point is in
        final int[] rank = new int[4];
        for (int a = 0; a < 4; ++a) {
            for (int b = a + 1; b < 4; ++b) {
                if (d[a] > d[b]) {
                    ++rank[a];
                } else {
                    ++rank[b];
                }
            }
        }
        final int[] base = {i, j, k, l};
        double total = 0;
        final int[] corner = new int[4];
        for (int step = 0; step <= 4; ++step) {
            // Corner 0 is the base; corner n steps in the n largest coordinates
            final double[] offset = new double[4];
            for (int a = 0; a < 4; ++a) {
                corner[a] = step == 0 ? 0 : rank[a] >= 4 - step ? 1 : 0;
                offset[a] = d[a] - corner[a] + step * G4;
            }
            double falloff =
                    0.6
                            - offset[0] * offset[0]
                            - offset[1] * offset[1]
                            - offset[2] * offset[2]
                            - offset[3] * offset[3];
            if (falloff <= 0) {
                continue;
            }
            falloff *= falloff;
            final int[] g =
                    GRADIENTS[
                            (int)
                                    (hash(
                                                    base[0] + corner[0],
                                                    base[1] + corner[1],
                                                    base[2] + corner[2],
                                                    base[3] + corner[3],
                                                    seed)
                                            & 31)];
            total +=
                    falloff
                            * falloff
                            * (g[0] * offset[0]
                                    + g[1] * offset[1]
                                    + g[2] * offset[2]
                                    + g[3] * offset[3]);
        }
        return SCALE * total;
    }

    /**
     * A lattice point's random bits.
     *
     * @param i The first coordinate.
     * @param j The second.
     * @param k The third.
     * @param l The fourth.
     * @param seed The seed.
     * @return The bits.
     */
    private static long hash(int i, int j, int k, int l, long seed) {
        long h = seed;
        h = Evaluator.mix(h ^ i);
        h = Evaluator.mix(h ^ j);
        h = Evaluator.mix(h ^ k);
        return Evaluator.mix(h ^ l);
    }
}
