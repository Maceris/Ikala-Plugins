package com.ikalagaming.factory.world.gen;

import com.ikalagaming.factory.world.gen.density.EvalCache;
import com.ikalagaming.factory.world.gen.density.Interval;

import lombok.NonNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Chooses biomes from parameter values, by nearest midpoint.
 *
 * <p>Biomes whose ranges contain every parameter's value are candidates. The nearest wins: the
 * smallest sum of distances from each value to the middle of the biome's range for it. A parameter
 * a biome leaves out counts as the parameter's whole display range. Close calls go to the narrower
 * biome, then the heavier one, and exact ties to a hash of the world seed, the cell and the biome's
 * ID, so the choice never depends on order or anything outside the cell. With no candidates, the
 * world type's fallback biome.
 */
public final class BiomeSelector {

    /** How close two distances or widths are to count as the same. */
    public static final double EPSILON = 0.001;

    /**
     * One biome's fit at a point, for choosing and for debug views.
     *
     * @param biome The biome.
     * @param contains Whether every parameter's value is in its range.
     * @param distance The sum of distances from each value to the middle of its range.
     * @param width The sum of its ranges' widths, smaller for more specific biomes.
     * @param tieBreak The hash that settles exact ties.
     */
    public record Candidate(
            @NonNull Biome biome, boolean contains, double distance, double width, long tieBreak) {}

    /** Best first: closest, then narrowest, then heaviest, then by hash. */
    private static final Comparator<Candidate> BEST_FIRST =
            (a, b) -> {
                if (Math.abs(a.distance() - b.distance()) > EPSILON) {
                    return Double.compare(a.distance(), b.distance());
                }
                if (Math.abs(a.width() - b.width()) > EPSILON) {
                    return Double.compare(a.width(), b.width());
                }
                if (a.biome().weight() != b.biome().weight()) {
                    return Double.compare(b.biome().weight(), a.biome().weight());
                }
                return Long.compare(b.tieBreak(), a.tieBreak());
            };

    /** The world type. */
    private final WorldType worldType;

    /** The world seed. */
    private final long seed;

    /** Each biome ID's hash, for tie breaks, indexed like the world type's biomes. */
    private final long[] idHashes;

    /**
     * Set up selection for a world.
     *
     * @param worldType The world type.
     * @param seed The world seed.
     */
    public BiomeSelector(@NonNull WorldType worldType, long seed) {
        this.worldType = worldType;
        this.seed = seed;
        idHashes = new long[worldType.biomes().size()];
        for (int i = 0; i < idHashes.length; ++i) {
            idHashes[i] = WorldgenHash.fnv1a64(worldType.biomes().get(i).id());
        }
    }

    /**
     * Every parameter's value at a point.
     *
     * @param x The x coordinate.
     * @param y The y coordinate.
     * @param z The z coordinate.
     * @param cache The chunk's cache, or null.
     * @return The values, in the world type's parameter order.
     */
    public double[] parameters(double x, double y, double z, EvalCache cache) {
        final List<Parameter> parameters = worldType.parameters();
        double[] values = new double[parameters.size()];
        for (int i = 0; i < values.length; ++i) {
            values[i] = parameters.get(i).density().value(x, y, z, cache);
        }
        return values;
    }

    /**
     * Every biome's fit for some parameter values, best first.
     *
     * @param values The parameter values.
     * @param cellX The biome cell's x, for tie breaks.
     * @param cellY The biome cell's y.
     * @param cellZ The biome cell's z.
     * @return The candidates, those containing the values first, each group best first.
     */
    public List<Candidate> rank(double @NonNull [] values, long cellX, long cellY, long cellZ) {
        final long cellHash =
                WorldgenHash.combine(
                        WorldgenHash.combine(WorldgenHash.combine(seed, cellX), cellY), cellZ);
        List<Candidate> inside = new ArrayList<>();
        List<Candidate> outside = new ArrayList<>();
        final List<Biome> biomes = worldType.biomes();
        for (int b = 0; b < biomes.size(); ++b) {
            final ParameterRange[] ranges = worldType.climates().get(b);
            boolean contains = true;
            double distance = 0;
            double width = 0;
            for (int p = 0; p < values.length; ++p) {
                final ParameterRange range = ranges[p];
                double min;
                double max;
                if (range == null) {
                    final Interval display = worldType.parameters().get(p).displayRange();
                    min = display.min();
                    max = display.max();
                } else {
                    min = range.min();
                    max = range.max();
                    contains &= range.contains(values[p]);
                }
                distance += Math.abs(values[p] - (min + max) / 2);
                width += max - min;
            }
            final Candidate candidate =
                    new Candidate(
                            biomes.get(b),
                            contains,
                            distance,
                            width,
                            WorldgenHash.combine(cellHash, idHashes[b]));
            (contains ? inside : outside).add(candidate);
        }
        inside.sort(BEST_FIRST);
        outside.sort(BEST_FIRST);
        inside.addAll(outside);
        return inside;
    }

    /**
     * The biome for some parameter values.
     *
     * @param values The parameter values.
     * @param cellX The biome cell's x.
     * @param cellY The biome cell's y.
     * @param cellZ The biome cell's z.
     * @return The best containing biome, or the fallback if none contains them.
     */
    public Biome choose(double @NonNull [] values, long cellX, long cellY, long cellZ) {
        final List<Candidate> ranked = rank(values, cellX, cellY, cellZ);
        return !ranked.isEmpty() && ranked.get(0).contains()
                ? ranked.get(0).biome()
                : worldType.fallbackBiome();
    }
}
