package com.ikalagaming.factory.world.gen.density;

/**
 * An axis-aligned region of the world, in block coordinates, which bounds are worked out over.
 *
 * @param minX The smallest x.
 * @param minY The smallest y.
 * @param minZ The smallest z.
 * @param maxX The largest x.
 * @param maxY The largest y.
 * @param maxZ The largest z.
 */
public record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {

    /**
     * The range along one axis.
     *
     * @param axis One of {@link DensityNode#X}, {@link DensityNode#Y} or {@link DensityNode#Z}.
     * @return The axis's interval.
     */
    public Interval range(int axis) {
        return switch (axis) {
            case DensityNode.X -> new Interval(minX, maxX);
            case DensityNode.Y -> new Interval(minY, maxY);
            default -> new Interval(minZ, maxZ);
        };
    }

    /**
     * A box from the range along each axis.
     *
     * @param x The x range.
     * @param y The y range.
     * @param z The z range.
     * @return The box.
     */
    public static Box of(Interval x, Interval y, Interval z) {
        return new Box(x.min(), y.min(), z.min(), x.max(), y.max(), z.max());
    }

    /**
     * Grow the box by a margin on every side, per axis.
     *
     * @param dx How far to grow along x.
     * @param dy How far to grow along y.
     * @param dz How far to grow along z.
     * @return The bigger box.
     */
    public Box expand(double dx, double dy, double dz) {
        return new Box(minX - dx, minY - dy, minZ - dz, maxX + dx, maxY + dy, maxZ + dz);
    }

    /**
     * The point in the middle.
     *
     * @return The center, as x, y, z.
     */
    public double[] center() {
        return new double[] {(minX + maxX) / 2, (minY + maxY) / 2, (minZ + maxZ) / 2};
    }

    /**
     * Half the length of the box's diagonal: no point in it is further than this from the center.
     *
     * @return The radius.
     */
    public double radius() {
        final double dx = (maxX - minX) / 2;
        final double dy = (maxY - minY) / 2;
        final double dz = (maxZ - minZ) / 2;
        return StrictMath.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
