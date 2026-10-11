package com.ikalagaming.factory.world.gen.density;

import lombok.NonNull;

import java.util.List;

/**
 * One node of a density function, a tree computing a value for any point in the world. Terrain is
 * solid where its density is above zero, and the same functions drive parameters, block rule
 * conditions and fluid levels.
 *
 * <p>Every node gives its value at a point and the range of values it can take over a box, and
 * knows which axes it depends on, so chunks that are certainly solid or air are skipped and
 * subtrees that ignore an axis are cached across it. Values must be a pure function of the point,
 * using {@link StrictMath} for anything beyond basic arithmetic, so they are the same on every
 * machine.
 */
public interface DensityNode {
    /** The x axis, as a bit. */
    int X = 1;

    /** The y axis, as a bit. */
    int Y = 2;

    /** The z axis, as a bit. */
    int Z = 4;

    /** The horizontal axes. */
    int XZ = X | Z;

    /** Every axis. */
    int ALL = X | Y | Z;

    /** How many axes coordinates have. */
    int AXIS_COUNT = 3;

    /**
     * The value at a point.
     *
     * @param x The x coordinate, in blocks.
     * @param y The y coordinate, in blocks.
     * @param z The z coordinate, in blocks.
     * @param cache Values shared within one chunk's evaluation, or null for none. Using it never
     *     changes a value.
     * @return The value.
     */
    double value(double x, double y, double z, EvalCache cache);

    /**
     * The range of values over a box. Must hold every value {@link #value} gives inside it.
     *
     * @param box The box.
     * @return The range.
     */
    Interval bounds(@NonNull Box box);

    /**
     * Which axes the value depends on, as a mask of {@link #X}, {@link #Y} and {@link #Z}.
     *
     * @return The axes.
     */
    int axes();

    /**
     * The node's type, as written in data, like {@code add}.
     *
     * @return The type name.
     */
    String type();

    /**
     * The nodes this one reads, for tracing.
     *
     * @return The children, in order.
     */
    List<DensityNode> children();

    /**
     * Where a child is evaluated when this node is evaluated at a point, for tracing. Most nodes
     * evaluate their children at the same point; coordinate transforms move it.
     *
     * @param child The child's index in {@link #children()}.
     * @param x The x coordinate.
     * @param y The y coordinate.
     * @param z The z coordinate.
     * @param cache The chunk's cache, or null.
     * @return The child's point as x, y, z, or null if it is sampled at several points.
     */
    default double[] childPoint(int child, double x, double y, double z, EvalCache cache) {
        return new double[] {x, y, z};
    }

    /**
     * The box a child sees when this node's bounds are worked out over a box, for showing each
     * node's range.
     *
     * @param child The child's index in {@link #children()}.
     * @param box This node's box.
     * @return The child's box.
     */
    default Box childBox(int child, @NonNull Box box) {
        return box;
    }
}
