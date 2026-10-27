package com.ikalagaming.graphics.benchmark;

import lombok.Getter;
import lombok.NonNull;

/**
 * A current at sea that goes round and round, like the paths in the water surface wavelets paper's
 * demonstrations: out in open water off the island, nothing built, the sea flowing along a closed
 * path, a circle or a figure eight, in a band fastest along its middle and still past its edges, so
 * the gate can show full wind waves and ripples carried round bends and through a crossing.
 *
 * <p>The flow is worked out once per corner over the path's box.
 */
public final class WaterLoop {

    /** The path's shape. */
    public enum Shape {
        /** A ring. */
        CIRCLE,
        /** A figure eight, Bernoulli's lemniscate, crossing itself in the middle. */
        INFINITY
    }

    /** Where the path's middle is along z, in meters: off the island's shore, in open sea. */
    public static final double CENTER_Z = 90;

    /** Half the flowing band's width, in meters. */
    public static final double HALF_WIDTH = 6;

    /** The circle's radius, to the band's middle, in meters. */
    private static final double CIRCLE_RADIUS = 22;

    /** The figure eight's half length, to the band's middle, in meters. */
    private static final double INFINITY_SIZE = 34;

    /** How far apart the spots that paint waves along the path are, in meters. */
    private static final double PAINT_SPACING = 2;

    /** Points along the path, to find the nearest of. */
    private static final int PATH_POINTS = 512;

    /** A full turn. */
    private static final double TURN = 2 * Math.PI;

    /** The path's shape. */
    @Getter private final Shape shape;

    /** Where on the path test ripples drop, x and z: its start. */
    @Getter private final double splashX;

    @Getter private final double splashZ;

    /**
     * Spots along the path, evenly spaced, to paint waves going along it: x, z and the path's way
     * there, radians from +x toward +z, for each.
     */
    private final double[] paintSpots;

    /** The path, x then z for each point, in order of the flow. */
    private final double[] path = new double[2 * PATH_POINTS];

    /** The box's smallest corner, x and z. */
    private final int minX;

    private final int minZ;

    /** How many cells across x and z the box is. */
    private final int sizeX;

    private final int sizeZ;

    /**
     * Each corner's flow direction, x then z, by (z - minZ) * (sizeX + 1) + (x - minX), and how far
     * across the band it is, 0 in the middle and 1 at its edge or past it.
     */
    private final double[] cornerFlow;

    private final double[] cornerAcross;

    /**
     * A path off the island.
     *
     * @param shape The path's shape.
     */
    public WaterLoop(@NonNull Shape shape) {
        this.shape = shape;
        for (int i = 0; i < PATH_POINTS; ++i) {
            final double t = TURN * i / PATH_POINTS;
            if (shape == Shape.CIRCLE) {
                path[2 * i] = CIRCLE_RADIUS * Math.cos(t);
                path[2 * i + 1] = CENTER_Z + CIRCLE_RADIUS * Math.sin(t);
            } else {
                final double sin = Math.sin(t);
                final double scale = INFINITY_SIZE / (1 + sin * sin);
                path[2 * i] = scale * Math.cos(t);
                path[2 * i + 1] = CENTER_Z + scale * Math.cos(t) * sin;
            }
        }
        splashX = path[0];
        splashZ = path[1];
        double length = 0;
        for (int i = 0; i < PATH_POINTS; ++i) {
            final int next = (i + 1) % PATH_POINTS;
            length +=
                    Math.hypot(path[2 * next] - path[2 * i], path[2 * next + 1] - path[2 * i + 1]);
        }
        final int spots = (int) Math.floor(length / PAINT_SPACING);
        paintSpots = new double[3 * spots];
        double walked = 0;
        int spot = 0;
        for (int i = 0; i < PATH_POINTS && spot < spots; ++i) {
            final int next = (i + 1) % PATH_POINTS;
            final double dx = path[2 * next] - path[2 * i];
            final double dz = path[2 * next + 1] - path[2 * i + 1];
            final double step = Math.hypot(dx, dz);
            while (spot < spots && spot * PAINT_SPACING <= walked + step) {
                final double f = (spot * PAINT_SPACING - walked) / step;
                paintSpots[3 * spot] = path[2 * i] + f * dx;
                paintSpots[3 * spot + 1] = path[2 * i + 1] + f * dz;
                paintSpots[3 * spot + 2] = Math.atan2(dz, dx);
                ++spot;
            }
            walked += step;
        }
        double lowX = Double.MAX_VALUE;
        double lowZ = Double.MAX_VALUE;
        double highX = -Double.MAX_VALUE;
        double highZ = -Double.MAX_VALUE;
        for (int i = 0; i < PATH_POINTS; ++i) {
            lowX = Math.min(lowX, path[2 * i]);
            highX = Math.max(highX, path[2 * i]);
            lowZ = Math.min(lowZ, path[2 * i + 1]);
            highZ = Math.max(highZ, path[2 * i + 1]);
        }
        minX = (int) Math.floor(lowX - HALF_WIDTH);
        minZ = (int) Math.floor(lowZ - HALF_WIDTH);
        sizeX = (int) Math.ceil(highX + HALF_WIDTH) - minX;
        sizeZ = (int) Math.ceil(highZ + HALF_WIDTH) - minZ;
        final int corners = (sizeX + 1) * (sizeZ + 1);
        cornerFlow = new double[2 * corners];
        cornerAcross = new double[corners];
        final double[] nearest = new double[3];
        for (int z = 0; z <= sizeZ; ++z) {
            for (int x = 0; x <= sizeX; ++x) {
                nearest(minX + x, minZ + z, nearest);
                final int at = z * (sizeX + 1) + x;
                cornerFlow[2 * at] = nearest[1];
                cornerFlow[2 * at + 1] = nearest[2];
                cornerAcross[at] = Math.min(1, nearest[0] / HALF_WIDTH);
            }
        }
    }

    /**
     * The nearest point on the path to a point.
     *
     * @param x The point's x.
     * @param z Its z.
     * @param out Set to the distance, then the path's direction there, x and z.
     */
    private void nearest(double x, double z, double[] out) {
        double best = Double.MAX_VALUE;
        for (int i = 0; i < PATH_POINTS; ++i) {
            final int next = (i + 1) % PATH_POINTS;
            final double ax = path[2 * i];
            final double az = path[2 * i + 1];
            final double dx = path[2 * next] - ax;
            final double dz = path[2 * next + 1] - az;
            final double length2 = dx * dx + dz * dz;
            final double along = Math.clamp(((x - ax) * dx + (z - az) * dz) / length2, 0.0, 1.0);
            final double px = ax + along * dx - x;
            final double pz = az + along * dz - z;
            final double distance = Math.sqrt(px * px + pz * pz);
            if (distance < best) {
                best = distance;
                final double length = Math.sqrt(length2);
                out[0] = distance;
                out[1] = dx / length;
                out[2] = dz / length;
            }
        }
    }

    /**
     * How many spots paint waves along the path.
     *
     * @return The count, see {@link #paintSpot}.
     */
    public int paintSpotCount() {
        return paintSpots.length / 3;
    }

    /**
     * One of the spots that paint waves along the path.
     *
     * @param spot Which, from 0 along the path.
     * @param value 0 for its x, 1 for its z, 2 for the path's way there in radians from +x toward
     *     +z.
     * @return The value.
     */
    public double paintSpot(int spot, int value) {
        return paintSpots[3 * spot + value];
    }

    /**
     * Whether a corner is in the path's box, where the path sets the flow rather than the sea's
     * current.
     *
     * @param x The corner's x.
     * @param z The corner's z.
     * @return Whether the path covers it.
     */
    public boolean coversCorner(int x, int z) {
        final int lx = x - minX;
        final int lz = z - minZ;
        return lx >= 0 && lz >= 0 && lx <= sizeX && lz <= sizeZ;
    }

    /**
     * How fast the water flows at a corner, along x or z: along the path, fastest in the band's
     * middle and still past its edges.
     *
     * @param x The corner's x.
     * @param z The corner's z.
     * @param speed How fast the middle flows, in meters a second; negative runs the other way.
     * @param alongZ Whether along z, rather than x.
     * @return The speed along that axis, in meters a second; 0 off the path's box.
     */
    public float flowAt(int x, int z, double speed, boolean alongZ) {
        if (!coversCorner(x, z)) {
            return 0;
        }
        final int at = (z - minZ) * (sizeX + 1) + (x - minX);
        final double across = cornerAcross[at];
        return (float) (speed * (1 - across * across) * cornerFlow[2 * at + (alongZ ? 1 : 0)]);
    }
}
