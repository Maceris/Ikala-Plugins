package com.ikalagaming.factory.world.gen.density;

import com.ikalagaming.factory.world.gen.noise.Noise;

import lombok.NonNull;

import java.util.List;

/** Density nodes that produce values: constants, noise, coordinates and references. */
public final class Sources {

    /**
     * Read a cached value for a node that ignores some axes, keyed by the axes it uses, or work it
     * out when there's no cache.
     *
     * @param node The node.
     * @param slot Its cache slot, or negative for none.
     * @param x The x coordinate.
     * @param y The y coordinate.
     * @param z The z coordinate.
     * @param cache The chunk's cache, or null.
     * @param compute Works out the value.
     * @return The value.
     */
    static double cached(
            @NonNull DensityNode node,
            int slot,
            double x,
            double y,
            double z,
            EvalCache cache,
            @NonNull java.util.function.DoubleSupplier compute) {
        final int axes = node.axes();
        if (cache == null || slot < 0 || axes == DensityNode.ALL) {
            return compute.getAsDouble();
        }
        return cache.get(
                slot,
                (axes & DensityNode.X) != 0 ? x : 0,
                (axes & DensityNode.Y) != 0 ? y : 0,
                (axes & DensityNode.Z) != 0 ? z : 0,
                compute);
    }

    /**
     * The same value everywhere.
     *
     * @param value The value.
     */
    public record Constant(double value) implements DensityNode {
        @Override
        public double value(double x, double y, double z, EvalCache cache) {
            return value;
        }

        @Override
        public Interval bounds(@NonNull Box box) {
            return Interval.of(value);
        }

        @Override
        public int axes() {
            return 0;
        }

        @Override
        public String type() {
            return "constant";
        }

        @Override
        public List<DensityNode> children() {
            return List.of();
        }
    }

    /**
     * A noise field's value.
     *
     * @param noise The noise.
     * @param channel Which of its channels.
     * @param slot The cache slot for 2D noise, or negative for none.
     */
    public record NoiseNode(@NonNull Noise noise, int channel, int slot) implements DensityNode {
        @Override
        public double value(double x, double y, double z, EvalCache cache) {
            return cached(this, slot, x, y, z, cache, () -> noise.sample(channel, x, y, z));
        }

        @Override
        public Interval bounds(@NonNull Box box) {
            return Interval.UNIT;
        }

        @Override
        public int axes() {
            return noise.is2D() ? XZ : ALL;
        }

        @Override
        public String type() {
            return "noise";
        }

        @Override
        public List<DensityNode> children() {
            return List.of();
        }
    }

    /**
     * One raw coordinate.
     *
     * @param axis {@link DensityNode#X}, {@link DensityNode#Y} or {@link DensityNode#Z}.
     */
    public record Axis(int axis) implements DensityNode {
        @Override
        public double value(double x, double y, double z, EvalCache cache) {
            return switch (axis) {
                case X -> x;
                case Y -> y;
                default -> z;
            };
        }

        @Override
        public Interval bounds(@NonNull Box box) {
            return box.range(axis);
        }

        @Override
        public int axes() {
            return axis;
        }

        @Override
        public String type() {
            return "axis";
        }

        @Override
        public List<DensityNode> children() {
            return List.of();
        }
    }

    /**
     * Another file's density function, or a named parameter's. References are where values that
     * ignore an axis get cached.
     *
     * @param type {@code ref} or {@code parameter}.
     * @param id The referenced data ID.
     * @param target The referenced function.
     * @param slot The cache slot, or negative for none.
     */
    public record Ref(
            @NonNull String type, @NonNull String id, @NonNull DensityNode target, int slot)
            implements DensityNode {
        @Override
        public double value(double x, double y, double z, EvalCache cache) {
            return cached(this, slot, x, y, z, cache, () -> target.value(x, y, z, cache));
        }

        @Override
        public Interval bounds(@NonNull Box box) {
            return target.bounds(box);
        }

        @Override
        public int axes() {
            return target.axes();
        }

        @Override
        public List<DensityNode> children() {
            return List.of(target);
        }
    }

    private Sources() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
