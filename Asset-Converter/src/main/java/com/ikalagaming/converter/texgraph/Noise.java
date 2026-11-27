package com.ikalagaming.converter.texgraph;

/**
 * Noise that tiles: value, gradient and cell noise on a lattice whose cells fit the tile a whole
 * number of times, so the noise wraps at its edges. Octaves double the cells each time, for fBm.
 * Every value comes from hashing a lattice point with the node's seed, so it is the same every run.
 */
final class Noise {
    /** The kinds of noise. */
    enum Kind {
        /** Random values at the lattice points, smoothly blended. */
        VALUE("Value Noise"),
        /** Random slopes at the lattice points, Perlin's noise. */
        GRADIENT("Gradient Noise"),
        /** Distance to the nearest of one random point per cell, Worley's noise. */
        CELLS("Cell Noise");

        /** The node's title. */
        private final String title;

        /**
         * Make a kind.
         *
         * @param title The node's title.
         */
        Kind(String title) {
            this.title = title;
        }

        /**
         * The node's title.
         *
         * @return The title.
         */
        String title() {
            return title;
        }
    }

    /** Gradient noise reaches about this far from zero, to scale it to 0..1. */
    private static final float GRADIENT_REACH = (float) Math.sqrt(0.5);

    /** Not made. */
    private Noise() {}

    /**
     * Make noise.
     *
     * @param kind Which noise.
     * @param width Pixels across.
     * @param height Pixels down.
     * @param size How big the features are, in pixels, at the first octave.
     * @param octaves How many octaves, each with twice the cells.
     * @param persistence How much each octave counts, compared to the one before.
     * @param seed The node's seed.
     * @return The noise, one channel, 0 to 1.
     */
    static Plane make(
            Kind kind,
            int width,
            int height,
            float size,
            int octaves,
            float persistence,
            long seed) {
        final float feature = Math.max(1, size);
        final int cellsX = Math.max(1, Math.round(width / feature));
        final int cellsY = Math.max(1, Math.round(height / feature));
        final Plane out = new Plane(width, height, 1);
        float total = 0;
        float amplitude = 1;
        for (int octave = 0; octave < octaves; ++octave) {
            total += amplitude;
            amplitude *= persistence;
        }
        for (int y = 0; y < height; ++y) {
            for (int x = 0; x < width; ++x) {
                float sum = 0;
                amplitude = 1;
                for (int octave = 0; octave < octaves; ++octave) {
                    final int cx = cellsX << octave;
                    final int cy = cellsY << octave;
                    final float u = (x + 0.5f) / width * cx;
                    final float v = (y + 0.5f) / height * cy;
                    final long octaveSeed = Evaluator.mix(seed + octave);
                    sum += amplitude * sample(kind, u, v, cx, cy, octaveSeed);
                    amplitude *= persistence;
                }
                out.set(x, y, 0, total == 0 ? 0 : sum / total);
            }
        }
        return out;
    }

    /**
     * One octave's noise at a point.
     *
     * @param kind Which noise.
     * @param u Across, in cells.
     * @param v Down, in cells.
     * @param cellsX Cells across the tile, where it wraps.
     * @param cellsY Cells down the tile.
     * @param seed The octave's seed.
     * @return The noise, 0 to 1.
     */
    private static float sample(Kind kind, float u, float v, int cellsX, int cellsY, long seed) {
        final int ix = (int) Math.floor(u);
        final int iy = (int) Math.floor(v);
        final float fx = u - ix;
        final float fy = v - iy;
        return switch (kind) {
            case VALUE -> {
                final float a = CoreNodes.unit(lattice(ix, iy, cellsX, cellsY, seed));
                final float b = CoreNodes.unit(lattice(ix + 1, iy, cellsX, cellsY, seed));
                final float c = CoreNodes.unit(lattice(ix, iy + 1, cellsX, cellsY, seed));
                final float d = CoreNodes.unit(lattice(ix + 1, iy + 1, cellsX, cellsY, seed));
                final float sx = fade(fx);
                final float sy = fade(fy);
                yield lerp(lerp(a, b, sx), lerp(c, d, sx), sy);
            }
            case GRADIENT -> {
                final float a = slope(ix, iy, cellsX, cellsY, seed, fx, fy);
                final float b = slope(ix + 1, iy, cellsX, cellsY, seed, fx - 1, fy);
                final float c = slope(ix, iy + 1, cellsX, cellsY, seed, fx, fy - 1);
                final float d = slope(ix + 1, iy + 1, cellsX, cellsY, seed, fx - 1, fy - 1);
                final float value = lerp(lerp(a, b, fade(fx)), lerp(c, d, fade(fx)), fade(fy));
                yield Math.clamp(value / GRADIENT_REACH * 0.5f + 0.5f, 0, 1);
            }
            case CELLS -> {
                float nearest = Float.MAX_VALUE;
                for (int dy = -1; dy <= 1; ++dy) {
                    for (int dx = -1; dx <= 1; ++dx) {
                        final long bits = lattice(ix + dx, iy + dy, cellsX, cellsY, seed);
                        final float px = dx + CoreNodes.unit(bits) - fx;
                        final float py = dy + CoreNodes.unit(Evaluator.mix(bits)) - fy;
                        nearest = Math.min(nearest, px * px + py * py);
                    }
                }
                yield Math.min(1, (float) Math.sqrt(nearest));
            }
        };
    }

    /**
     * Random bits for a lattice point, wrapping at the tile.
     *
     * @param x The point across.
     * @param y The point down.
     * @param cellsX Where it wraps across.
     * @param cellsY Where it wraps down.
     * @param seed The seed.
     * @return The bits.
     */
    private static long lattice(int x, int y, int cellsX, int cellsY, long seed) {
        final long wrapped = Math.floorMod(x, cellsX) * 0x1F1F1F1FL + Math.floorMod(y, cellsY);
        return Evaluator.mix(seed ^ Evaluator.mix(wrapped));
    }

    /**
     * A lattice point's slope toward a point.
     *
     * @param x The lattice point across.
     * @param y The lattice point down.
     * @param cellsX Where it wraps across.
     * @param cellsY Where it wraps down.
     * @param seed The seed.
     * @param dx How far across the point is from it.
     * @param dy How far down the point is from it.
     * @return The slope's value there.
     */
    private static float slope(
            int x, int y, int cellsX, int cellsY, long seed, float dx, float dy) {
        final double angle = CoreNodes.unit(lattice(x, y, cellsX, cellsY, seed)) * 2 * Math.PI;
        return (float) (Math.cos(angle) * dx + Math.sin(angle) * dy);
    }

    /**
     * Perlin's fade curve.
     *
     * @param t 0 to 1.
     * @return The eased value.
     */
    private static float fade(float t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    /**
     * Linear interpolation.
     *
     * @param a At 0.
     * @param b At 1.
     * @param t Where.
     * @return The value.
     */
    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
