package com.ikalagaming.graphics.lod;

import com.ikalagaming.util.IntArrayList;

import lombok.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lays a cluster's triangles out in a square texture tile, for baking its appearance: triangles
 * facing much the same way are grown into charts, each chart is laid flat on its own plane, and the
 * charts are packed into the tile at one scale, as large as fits, with a gap of texels around each
 * so filtering never reaches a neighbor.
 *
 * <p>A chart only takes a triangle within {@value #CHART_DEGREES} degrees of its first one's facing
 * and that doesn't overlap the chart once flat, so laying it flat is one to one. Every chart has
 * the same texels per world unit, so the bake is equally sharp across the cluster.
 */
public final class BakeLayout {

    /** Coordinates in a position. */
    private static final int AXES = 3;

    /** Corners in a triangle. */
    private static final int CORNERS = 3;

    /** Values in a triangle's corners. */
    private static final int TRIANGLE_VALUES = CORNERS * AXES;

    /** How far, in degrees, a triangle may face from its chart's first to join it. */
    static final double CHART_DEGREES = 60;

    /** The cosine of {@link #CHART_DEGREES}. */
    private static final double CHART_COSINE = Math.cos(Math.toRadians(CHART_DEGREES));

    /** Texels left empty around each chart. */
    static final int GUTTER = 1;

    /** Turns tried for each chart, in steps of 90 degrees over this, for the smallest box. */
    private static final int TURNS = 6;

    /** Halvings of the scale tried when searching for the largest that fits. */
    private static final int SCALE_STEPS = 24;

    /** Each triangle corner's place in the tile, u then v, from 0 to 1, six floats a triangle. */
    private final float[] uvs;

    /** Which chart each triangle is in. */
    private final int[] charts;

    /** How many charts there are. */
    private final int chartCount;

    /** World units a texel spans. */
    private final double texelSize;

    /** The tile's side, in texels. */
    private final int tile;

    /**
     * Keep a layout.
     *
     * @param uvs Each corner's place in the tile.
     * @param charts Each triangle's chart.
     * @param chartCount How many charts there are.
     * @param texelSize World units a texel spans.
     * @param tile The tile's side, in texels.
     */
    private BakeLayout(float[] uvs, int[] charts, int chartCount, double texelSize, int tile) {
        this.uvs = uvs;
        this.charts = charts;
        this.chartCount = chartCount;
        this.texelSize = texelSize;
        this.tile = tile;
    }

    /**
     * The tile's side.
     *
     * @return The side, in texels.
     */
    public int tile() {
        return tile;
    }

    /**
     * Each triangle corner's place in the tile, from 0 to 1.
     *
     * @return u and v for each corner, six floats a triangle. Don't change them.
     */
    public float[] uvs() {
        return uvs;
    }

    /**
     * Which chart each triangle is in.
     *
     * @return The charts. Don't change them.
     */
    public int[] charts() {
        return charts;
    }

    /**
     * How many charts there are.
     *
     * @return The count.
     */
    public int chartCount() {
        return chartCount;
    }

    /**
     * How much of the world a texel spans.
     *
     * @return The size, in world units.
     */
    public double texelSize() {
        return texelSize;
    }

    /**
     * Lay triangles out in a tile.
     *
     * @param corners Their corners, 9 floats a triangle.
     * @param tile The tile's side, in texels.
     * @return The layout.
     */
    public static BakeLayout of(float @NonNull [] corners, int tile) {
        final int triangles = corners.length / TRIANGLE_VALUES;
        final double[][] normals = new double[triangles][];
        final double[] areas = new double[triangles];
        for (int t = 0; t < triangles; ++t) {
            final double[] a = corner(corners, t, 0);
            final double[] b = corner(corners, t, 1);
            final double[] c = corner(corners, t, 2);
            final double[] n = new double[AXES];
            Geometry.normal(a, b, c, n);
            final double length = Math.sqrt(Geometry.dot(n, n));
            areas[t] = length / 2;
            if (length > 0) {
                n[0] /= length;
                n[1] /= length;
                n[2] /= length;
            } else {
                n[1] = 1;
            }
            normals[t] = n;
        }
        final IntArrayList[] neighbors = neighbors(corners, triangles);
        // Grow charts from the largest triangles still free
        final Integer[] bySize = new Integer[triangles];
        for (int t = 0; t < triangles; ++t) {
            bySize[t] = t;
        }
        Arrays.sort(bySize, (x, y) -> Double.compare(areas[y], areas[x]));
        final int[] chart = new int[triangles];
        Arrays.fill(chart, -1);
        final List<Chart> built = new ArrayList<>();
        for (int seed : bySize) {
            if (chart[seed] >= 0) {
                continue;
            }
            final Chart grown = new Chart(built.size(), normals[seed]);
            grown.add(corners, seed);
            chart[seed] = grown.id;
            final IntArrayList open = new IntArrayList();
            open.addInt(seed);
            while (!open.isEmpty()) {
                final int t = open.pop();
                for (int k = 0; k < neighbors[t].size(); ++k) {
                    final int u = neighbors[t].getInt(k);
                    if (chart[u] < 0
                            && Geometry.dot(normals[u], grown.normal) >= CHART_COSINE
                            && grown.fits(corners, u)) {
                        grown.add(corners, u);
                        chart[u] = grown.id;
                        open.addInt(u);
                    }
                }
            }
            built.add(grown);
        }
        for (Chart c : built) {
            c.turnToSmallestBox();
        }
        // The largest scale, texels per world unit, at which the charts pack into the tile
        double high = 0;
        for (Chart c : built) {
            high = Math.max(high, Math.max(c.width(), c.height()));
        }
        high = high > 0 ? tile / high : 1;
        double low = 0;
        for (int step = 0; step < SCALE_STEPS; ++step) {
            final double scale = step == 0 ? high : (low + high) / 2;
            if (pack(built, scale, tile) != null) {
                low = scale;
                if (step == 0) {
                    break;
                }
            } else {
                high = scale;
            }
        }
        if (low <= 0) {
            // Even the smallest scale tried didn't fit: too many charts for the tile's gaps
            low = high / (1 << SCALE_STEPS);
        }
        final double[][] places = pack(built, low, tile);
        final float[] uvs = new float[triangles * CORNERS * 2];
        for (Chart c : built) {
            final double[] place = places == null ? new double[2] : places[c.id];
            for (int i = 0; i < c.triangles.size(); ++i) {
                final int t = c.triangles.getInt(i);
                for (int k = 0; k < CORNERS; ++k) {
                    final double[] flat = c.flat(corners, t, k);
                    uvs[(t * CORNERS + k) * 2] =
                            (float) ((place[0] + (flat[0] - c.minU) * low) / tile);
                    uvs[(t * CORNERS + k) * 2 + 1] =
                            (float) ((place[1] + (flat[1] - c.minV) * low) / tile);
                }
            }
        }
        return new BakeLayout(uvs, chart, built.size(), low > 0 ? 1 / low : 0, tile);
    }

    /**
     * Shelf-pack charts into the tile at a scale: tallest first, left to right in rows.
     *
     * @param built The charts.
     * @param scale Texels per world unit.
     * @param tile The tile's side, in texels.
     * @return Each chart's corner in the tile, in texels, or null if they don't fit.
     */
    private static double[][] pack(@NonNull List<Chart> built, double scale, int tile) {
        final Chart[] order = built.toArray(new Chart[0]);
        Arrays.sort(order, (x, y) -> Double.compare(y.height(), x.height()));
        final double[][] places = new double[built.size()][];
        double x = 0;
        double y = 0;
        double row = 0;
        for (Chart c : order) {
            final double w = Math.ceil(c.width() * scale) + 2 * GUTTER;
            final double h = Math.ceil(c.height() * scale) + 2 * GUTTER;
            if (w > tile || h > tile) {
                return null;
            }
            if (x + w > tile) {
                x = 0;
                y += row;
                row = 0;
            }
            if (y + h > tile) {
                return null;
            }
            places[c.id] = new double[] {x + GUTTER, y + GUTTER};
            x += w;
            row = Math.max(row, h);
        }
        return places;
    }

    /**
     * Which triangles share an edge with each, by their corners' positions.
     *
     * @param corners The corners.
     * @param triangles How many triangles.
     * @return Each triangle's neighbors.
     */
    private static IntArrayList[] neighbors(float @NonNull [] corners, int triangles) {
        final Map<Grouping.Edge, IntArrayList> byEdge = new HashMap<>();
        for (int t = 0; t < triangles; ++t) {
            for (int k = 0; k < CORNERS; ++k) {
                final Grouping.Edge edge =
                        Grouping.Edge.of(
                                Grouping.Corner.of(corners, t * TRIANGLE_VALUES + k * AXES),
                                Grouping.Corner.of(
                                        corners, t * TRIANGLE_VALUES + ((k + 1) % CORNERS) * AXES));
                byEdge.computeIfAbsent(edge, e -> new IntArrayList()).addInt(t);
            }
        }
        final IntArrayList[] out = new IntArrayList[triangles];
        for (int t = 0; t < triangles; ++t) {
            out[t] = new IntArrayList();
        }
        for (IntArrayList sharing : byEdge.values()) {
            for (int i = 0; i < sharing.size(); ++i) {
                for (int j = 0; j < sharing.size(); ++j) {
                    if (i != j) {
                        out[sharing.getInt(i)].addInt(sharing.getInt(j));
                    }
                }
            }
        }
        return out;
    }

    /**
     * A triangle's corner.
     *
     * @param corners The corners.
     * @param t The triangle.
     * @param k The corner.
     * @return Its position.
     */
    private static double[] corner(float @NonNull [] corners, int t, int k) {
        final int at = t * TRIANGLE_VALUES + k * AXES;
        return new double[] {corners[at], corners[at + 1], corners[at + 2]};
    }

    /** A chart being grown: triangles laid flat on one plane. */
    private static final class Chart {
        /** Its index. */
        final int id;

        /** The facing it was grown around. */
        final double[] normal;

        /** One direction across its plane. */
        final double[] across = new double[AXES];

        /** The other direction across its plane. */
        final double[] up = new double[AXES];

        /** Its triangles. */
        final IntArrayList triangles = new IntArrayList();

        /** Its triangles laid flat: u and v of each corner, six a triangle. */
        double[] flat = new double[0];

        /** How far it is turned within its plane, in radians. */
        double turn;

        /** The smallest u laid flat and turned. */
        double minU;

        /** The smallest v. */
        double minV;

        /** The largest u. */
        double maxU;

        /** The largest v. */
        double maxV;

        /**
         * Start a chart.
         *
         * @param id Its index.
         * @param normal Its facing.
         */
        Chart(int id, double @NonNull [] normal) {
            this.id = id;
            this.normal = normal.clone();
            // Any direction at right angles will do
            final double[] axis =
                    Math.abs(normal[0]) < 0.9 ? new double[] {1, 0, 0} : new double[] {0, 1, 0};
            final double[] a = new double[AXES];
            cross(normal, axis, a);
            normalize(a);
            System.arraycopy(a, 0, across, 0, AXES);
            cross(normal, across, up);
        }

        /**
         * A corner laid flat and turned.
         *
         * @param corners The corners.
         * @param t The triangle.
         * @param k The corner.
         * @return Its u and v, in world units.
         */
        double[] flat(float @NonNull [] corners, int t, int k) {
            final int at = t * TRIANGLE_VALUES + k * AXES;
            final double u =
                    corners[at] * across[0]
                            + corners[at + 1] * across[1]
                            + corners[at + 2] * across[2];
            final double v =
                    corners[at] * up[0] + corners[at + 1] * up[1] + corners[at + 2] * up[2];
            final double cos = Math.cos(turn);
            final double sin = Math.sin(turn);
            return new double[] {u * cos - v * sin, u * sin + v * cos};
        }

        /**
         * Whether a triangle, laid flat, would keep clear of the chart's triangles: it may touch
         * them along shared edges and corners, but not overlap.
         *
         * @param corners The corners.
         * @param t The triangle.
         * @return True if it fits.
         */
        boolean fits(float @NonNull [] corners, int t) {
            final double[] mine = new double[CORNERS * 2];
            for (int k = 0; k < CORNERS; ++k) {
                final double[] p = flat(corners, t, k);
                mine[k * 2] = p[0];
                mine[k * 2 + 1] = p[1];
            }
            for (int i = 0; i < triangles.size(); ++i) {
                if (overlap(mine, 0, flat, i * CORNERS * 2)) {
                    return false;
                }
            }
            return true;
        }

        /**
         * Add a triangle.
         *
         * @param corners The corners.
         * @param t The triangle.
         */
        void add(float @NonNull [] corners, int t) {
            final int i = triangles.size();
            triangles.addInt(t);
            if (flat.length < (i + 1) * CORNERS * 2) {
                flat = Arrays.copyOf(flat, Math.max((i + 1) * CORNERS * 2, flat.length * 2));
            }
            for (int k = 0; k < CORNERS; ++k) {
                final double[] p = flat(corners, t, k);
                flat[i * CORNERS * 2 + k * 2] = p[0];
                flat[i * CORNERS * 2 + k * 2 + 1] = p[1];
            }
        }

        /** Turn the chart to whichever of a few turns gives the smallest box, and find it. */
        void turnToSmallestBox() {
            double bestArea = Double.MAX_VALUE;
            double bestTurn = 0;
            final int count = triangles.size() * CORNERS;
            for (int step = 0; step < TURNS; ++step) {
                final double angle = Math.PI / 2 * step / TURNS;
                final double cos = Math.cos(angle);
                final double sin = Math.sin(angle);
                double lowU = Double.MAX_VALUE;
                double lowV = Double.MAX_VALUE;
                double highU = -Double.MAX_VALUE;
                double highV = -Double.MAX_VALUE;
                for (int c = 0; c < count; ++c) {
                    final double u = flat[c * 2] * cos - flat[c * 2 + 1] * sin;
                    final double v = flat[c * 2] * sin + flat[c * 2 + 1] * cos;
                    lowU = Math.min(lowU, u);
                    lowV = Math.min(lowV, v);
                    highU = Math.max(highU, u);
                    highV = Math.max(highV, v);
                }
                final double area = (highU - lowU) * (highV - lowV);
                if (area < bestArea) {
                    bestArea = area;
                    bestTurn = angle;
                    minU = lowU;
                    minV = lowV;
                    maxU = highU;
                    maxV = highV;
                }
            }
            turn = bestTurn;
        }

        /**
         * Its width, turned.
         *
         * @return The width, in world units.
         */
        double width() {
            return maxU - minU;
        }

        /**
         * Its height, turned.
         *
         * @return The height, in world units.
         */
        double height() {
            return maxV - minV;
        }
    }

    /**
     * Whether two flat triangles overlap by more than touching.
     *
     * @param a One, u and v of three corners.
     * @param at Where it starts.
     * @param b The other.
     * @param bt Where it starts.
     * @return True if their insides meet.
     */
    static boolean overlap(double @NonNull [] a, int at, double @NonNull [] b, int bt) {
        // Separating axis test on the six edge normals, shrunk a little so touching isn't overlap
        final double shrink = 1e-9 * (1 + span(a, at) + span(b, bt));
        for (int tri = 0; tri < 2; ++tri) {
            final double[] p = tri == 0 ? a : b;
            final int pt = tri == 0 ? at : bt;
            for (int e = 0; e < CORNERS; ++e) {
                final double ex = p[pt + ((e + 1) % CORNERS) * 2] - p[pt + e * 2];
                final double ey = p[pt + ((e + 1) % CORNERS) * 2 + 1] - p[pt + e * 2 + 1];
                final double nx = -ey;
                final double ny = ex;
                double minA = Double.MAX_VALUE;
                double maxA = -Double.MAX_VALUE;
                double minB = Double.MAX_VALUE;
                double maxB = -Double.MAX_VALUE;
                for (int k = 0; k < CORNERS; ++k) {
                    final double da = a[at + k * 2] * nx + a[at + k * 2 + 1] * ny;
                    final double db = b[bt + k * 2] * nx + b[bt + k * 2 + 1] * ny;
                    minA = Math.min(minA, da);
                    maxA = Math.max(maxA, da);
                    minB = Math.min(minB, db);
                    maxB = Math.max(maxB, db);
                }
                final double length = Math.sqrt(nx * nx + ny * ny);
                if (length == 0) {
                    continue;
                }
                if (maxA <= minB + shrink * length || maxB <= minA + shrink * length) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * How far apart a flat triangle's corners reach.
     *
     * @param p The triangle, u and v of three corners.
     * @param at Where it starts.
     * @return The larger of its width and height.
     */
    private static double span(double @NonNull [] p, int at) {
        double lowU = Double.MAX_VALUE;
        double lowV = Double.MAX_VALUE;
        double highU = -Double.MAX_VALUE;
        double highV = -Double.MAX_VALUE;
        for (int k = 0; k < CORNERS; ++k) {
            lowU = Math.min(lowU, p[at + k * 2]);
            highU = Math.max(highU, p[at + k * 2]);
            lowV = Math.min(lowV, p[at + k * 2 + 1]);
            highV = Math.max(highV, p[at + k * 2 + 1]);
        }
        return Math.max(highU - lowU, highV - lowV);
    }

    /**
     * The cross product of two vectors.
     *
     * @param a The first.
     * @param b The second.
     * @param out Receives a × b.
     */
    private static void cross(double @NonNull [] a, double @NonNull [] b, double @NonNull [] out) {
        out[0] = a[1] * b[2] - a[2] * b[1];
        out[1] = a[2] * b[0] - a[0] * b[2];
        out[2] = a[0] * b[1] - a[1] * b[0];
    }

    /**
     * Scale a vector to unit length, unless it is zero.
     *
     * @param v The vector.
     */
    private static void normalize(double @NonNull [] v) {
        final double length = Math.sqrt(Geometry.dot(v, v));
        if (length > 0) {
            v[0] /= length;
            v[1] /= length;
            v[2] /= length;
        }
    }
}
