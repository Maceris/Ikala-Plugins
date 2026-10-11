package com.ikalagaming.factory.world.gen.density;

import lombok.NonNull;

import java.util.List;

/**
 * Signed distance shapes, positive inside, so a shape can be used as terrain directly. Each changes
 * by at most one per block moved, so its range over a box is its value at the box's center, give or
 * take the distance to the box's corners.
 */
public final class Shapes {

    /**
     * The range of a shape over a box, from its value at the center and how far the corners are.
     *
     * @param shape The shape.
     * @param box The box.
     * @return The range.
     */
    static Interval lipschitzBounds(@NonNull DensityNode shape, @NonNull Box box) {
        final double[] c = box.center();
        final double v = shape.value(c[0], c[1], c[2], null);
        final double r = box.radius();
        return new Interval(v - r, v + r);
    }

    /**
     * The length of a vector.
     *
     * @param x The x part.
     * @param y The y part.
     * @param z The z part.
     * @return Its length.
     */
    public static double length(double x, double y, double z) {
        return StrictMath.sqrt(x * x + y * y + z * z);
    }

    /**
     * A ball.
     *
     * @param center Its center.
     * @param radius Its radius, in blocks.
     */
    public record Sphere(double @NonNull [] center, double radius) implements DensityNode {
        @Override
        public double value(double x, double y, double z, EvalCache cache) {
            return radius - length(x - center[0], y - center[1], z - center[2]);
        }

        @Override
        public Interval bounds(@NonNull Box box) {
            return lipschitzBounds(this, box);
        }

        @Override
        public int axes() {
            return ALL;
        }

        @Override
        public String type() {
            return "sphere";
        }

        @Override
        public List<DensityNode> children() {
            return List.of();
        }
    }

    /**
     * A box, optionally with rounded edges.
     *
     * @param center Its center.
     * @param halfSize Half its size along x, y and z.
     * @param rounding The radius of the rounded edges, no more than the smallest half size.
     */
    public record BoxShape(double @NonNull [] center, double @NonNull [] halfSize, double rounding)
            implements DensityNode {
        @Override
        public double value(double x, double y, double z, EvalCache cache) {
            final double qx = Math.abs(x - center[0]) - (halfSize[0] - rounding);
            final double qy = Math.abs(y - center[1]) - (halfSize[1] - rounding);
            final double qz = Math.abs(z - center[2]) - (halfSize[2] - rounding);
            final double outside = length(Math.max(qx, 0), Math.max(qy, 0), Math.max(qz, 0));
            final double inside = Math.min(Math.max(qx, Math.max(qy, qz)), 0);
            return -(outside + inside - rounding);
        }

        @Override
        public Interval bounds(@NonNull Box box) {
            return lipschitzBounds(this, box);
        }

        @Override
        public int axes() {
            return ALL;
        }

        @Override
        public String type() {
            return "box";
        }

        @Override
        public List<DensityNode> children() {
            return List.of();
        }
    }

    /**
     * A capped cylinder along one axis.
     *
     * @param center Its center.
     * @param axis The axis it runs along, {@link DensityNode#X}, {@link DensityNode#Y} or {@link
     *     DensityNode#Z}.
     * @param radius Its radius.
     * @param halfHeight Half its length along the axis.
     */
    public record Cylinder(double @NonNull [] center, int axis, double radius, double halfHeight)
            implements DensityNode {
        @Override
        public double value(double x, double y, double z, EvalCache cache) {
            final double dx = x - center[0];
            final double dy = y - center[1];
            final double dz = z - center[2];
            final double along;
            final double across;
            switch (axis) {
                case X -> {
                    along = dx;
                    across = StrictMath.sqrt(dy * dy + dz * dz);
                }
                case Y -> {
                    along = dy;
                    across = StrictMath.sqrt(dx * dx + dz * dz);
                }
                default -> {
                    along = dz;
                    across = StrictMath.sqrt(dx * dx + dy * dy);
                }
            }
            final double radial = across - radius;
            final double axial = Math.abs(along) - halfHeight;
            final double outside = length(Math.max(radial, 0), Math.max(axial, 0), 0);
            final double inside = Math.min(Math.max(radial, axial), 0);
            return -(outside + inside);
        }

        @Override
        public Interval bounds(@NonNull Box box) {
            return lipschitzBounds(this, box);
        }

        @Override
        public int axes() {
            return ALL;
        }

        @Override
        public String type() {
            return "cylinder";
        }

        @Override
        public List<DensityNode> children() {
            return List.of();
        }
    }

    /**
     * A half-space: positive on the side the normal points away from, where {@code dot(p, normal) <
     * offset}. A normal of (0, 1, 0) and offset 0 is solid below y = 0.
     *
     * @param normal The normal, normalized when loaded.
     * @param offset How far along the normal the surface is.
     */
    public record Plane(double @NonNull [] normal, double offset) implements DensityNode {
        @Override
        public double value(double x, double y, double z, EvalCache cache) {
            return offset - (x * normal[0] + y * normal[1] + z * normal[2]);
        }

        @Override
        public Interval bounds(@NonNull Box box) {
            return Interval.of(offset)
                    .add(
                            box.range(X)
                                    .scale(normal[0])
                                    .add(box.range(Y).scale(normal[1]))
                                    .add(box.range(Z).scale(normal[2]))
                                    .neg());
        }

        @Override
        public int axes() {
            return (normal[0] != 0 ? X : 0) | (normal[1] != 0 ? Y : 0) | (normal[2] != 0 ? Z : 0);
        }

        @Override
        public String type() {
            return "plane";
        }

        @Override
        public List<DensityNode> children() {
            return List.of();
        }
    }

    private Shapes() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
