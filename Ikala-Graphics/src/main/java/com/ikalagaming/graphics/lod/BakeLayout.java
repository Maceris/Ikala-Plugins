package com.ikalagaming.graphics.lod;

import com.ikalagaming.util.IntArrayList;

import lombok.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lays a cluster's triangles out in a texture tile, for baking its appearance: triangles facing
 * much the same way are grown into charts, each chart is laid flat on its own plane, and the charts
 * are packed at the texel size asked for, each either way round, into the smallest rectangle, whole
 * multiples of {@value #ALIGN} texels each way, with a gap of texels around each chart so filtering
 * never reaches a neighbor.
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

    /** Tiles' sides are whole multiples of this many texels. */
    static final int ALIGN = 4;

    /** How much smaller the scale gets each time the charts don't fit the largest tile. */
    private static final double SHRINK = 0.9;

    /** Each triangle corner's place in the tile, u then v, from 0 to 1, six floats a triangle. */
    private final float[] uvs;

    /** Which chart each triangle is in. */
    private final int[] charts;

    /** How many charts there are. */
    private final int chartCount;

    /** World units a texel spans. */
    private final double texelSize;

    /** The tile's width, in texels. */
    private final int width;

    /** The tile's height, in texels. */
    private final int height;

    /**
     * Keep a layout.
     *
     * @param uvs Each corner's place in the tile.
     * @param charts Each triangle's chart.
     * @param chartCount How many charts there are.
     * @param texelSize World units a texel spans.
     * @param width The tile's width, in texels.
     * @param height The tile's height, in texels.
     */
    private BakeLayout(
            float[] uvs, int[] charts, int chartCount, double texelSize, int width, int height) {
        this.uvs = uvs;
        this.charts = charts;
        this.chartCount = chartCount;
        this.texelSize = texelSize;
        this.width = width;
        this.height = height;
    }

    /**
     * The tile's width.
     *
     * @return The width, in texels.
     */
    public int width() {
        return width;
    }

    /**
     * The tile's height.
     *
     * @return The height, in texels.
     */
    public int height() {
        return height;
    }

    /**
     * Each triangle corner's place in the tile, from 0 to 1 across its width and its height.
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
     * Lay triangles out in the smallest tile that holds them at a texel size, or if even a tile of
     * the largest side doesn't, in that at a larger texel size.
     *
     * @param corners Their corners, 9 floats a triangle.
     * @param texelSize World units a texel should span.
     * @param maxSide The largest a tile's side may be, in texels.
     * @return The layout.
     */
    public static BakeLayout at(float @NonNull [] corners, double texelSize, int maxSide) {
        final int triangles = corners.length / TRIANGLE_VALUES;
        final List<Chart> built = charts(corners, triangles);
        double scale = texelSize > 0 ? 1 / texelSize : 1;
        double largest = 0;
        for (Chart c : built) {
            largest = Math.max(largest, Math.max(c.width(), c.height()));
        }
        if (largest > 0) {
            // No chart may be wider than the largest tile, gaps and all
            scale = Math.min(scale, (maxSide - 2 * GUTTER - 1) / largest);
        }
        Packing best = null;
        while (best == null) {
            best = smallest(built, scale, maxSide);
            if (best == null) {
                scale *= SHRINK;
            }
        }
        final float[] uvs = new float[triangles * CORNERS * 2];
        final int[] chart = new int[triangles];
        for (Chart c : built) {
            final int[] place = best.places[c.id];
            final boolean turned = best.turned[c.id];
            final double w = c.width() * scale;
            for (int i = 0; i < c.triangles.size(); ++i) {
                final int t = c.triangles.getInt(i);
                chart[t] = c.id;
                for (int k = 0; k < CORNERS; ++k) {
                    final double[] flat = c.flat(corners, t, k);
                    double u = (flat[0] - c.minU) * scale;
                    double v = (flat[1] - c.minV) * scale;
                    if (turned) {
                        final double was = u;
                        u = v;
                        v = w - was;
                    }
                    uvs[(t * CORNERS + k) * 2] = (float) ((place[0] + u) / best.width);
                    uvs[(t * CORNERS + k) * 2 + 1] = (float) ((place[1] + v) / best.height);
                }
            }
        }
        return new BakeLayout(uvs, chart, built.size(), 1 / scale, best.width, best.height);
    }

    /**
     * Grow a cluster's charts and turn each to its smallest box.
     *
     * @param corners The corners.
     * @param triangles How many triangles.
     * @return The charts.
     */
    private static List<Chart> charts(float @NonNull [] corners, int triangles) {
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
        return built;
    }

    /**
     * Where charts went in a tile.
     *
     * @param width The tile's width.
     * @param height The tile's height.
     * @param places Each chart's corner, in texels, inside its gap.
     * @param turned Whether each chart was turned a quarter.
     */
    private record Packing(int width, int height, int[][] places, boolean[] turned) {}

    /**
     * Find the smallest tile the charts pack into at a scale, trying every width.
     *
     * @param built The charts.
     * @param scale Texels per world unit.
     * @param maxSide The largest a tile's side may be.
     * @return The packing, or null if no tile up to the largest holds them.
     */
    private static Packing smallest(@NonNull List<Chart> built, double scale, int maxSide) {
        final int[] w = new int[built.size()];
        final int[] h = new int[built.size()];
        int widest = ALIGN;
        for (Chart c : built) {
            w[c.id] = (int) Math.ceil(c.width() * scale) + 2 * GUTTER;
            h[c.id] = (int) Math.ceil(c.height() * scale) + 2 * GUTTER;
            widest = Math.max(widest, Math.min(w[c.id], h[c.id]));
        }
        Packing best = null;
        for (int width = align(widest); width <= maxSide; width += ALIGN) {
            final Packing packing = skyline(w, h, width, maxSide);
            if (packing != null
                    && (best == null
                            || (long) packing.width * packing.height
                                    < (long) best.width * best.height)) {
                best = packing;
            }
        }
        return best;
    }

    /**
     * Pack boxes into a tile of a width with a skyline: biggest first, each where its top would be
     * lowest, either way round, then leftmost.
     *
     * @param w Each box's width.
     * @param h Each box's height.
     * @param width The tile's width.
     * @param maxSide The tallest the tile may be.
     * @return The packing, its height rounded up, or null if it would be too tall.
     */
    private static Packing skyline(int @NonNull [] w, int @NonNull [] h, int width, int maxSide) {
        final Integer[] order = new Integer[w.length];
        for (int i = 0; i < order.length; ++i) {
            order[i] = i;
        }
        Arrays.sort(order, (a, b) -> Integer.compare(Math.max(w[b], h[b]), Math.max(w[a], h[a])));
        // The height of each column of texels so far
        final int[] sky = new int[width];
        final int[][] places = new int[w.length][];
        final boolean[] turned = new boolean[w.length];
        int top = 0;
        for (int box : order) {
            int bestX = -1;
            int bestY = Integer.MAX_VALUE;
            boolean bestTurned = false;
            for (int turn = 0; turn < 2; ++turn) {
                final int bw = turn == 0 ? w[box] : h[box];
                final int bh = turn == 0 ? h[box] : w[box];
                if (bw > width) {
                    continue;
                }
                for (int x = 0; x + bw <= width; ++x) {
                    int y = 0;
                    for (int column = x; column < x + bw; ++column) {
                        y = Math.max(y, sky[column]);
                    }
                    if (y + bh < bestY || (y + bh == bestY && x < bestX)) {
                        bestY = y + bh;
                        bestX = x;
                        bestTurned = turn == 1;
                    }
                }
            }
            if (bestX < 0 || bestY > maxSide) {
                return null;
            }
            final int bw = bestTurned ? h[box] : w[box];
            for (int column = bestX; column < bestX + bw; ++column) {
                sky[column] = bestY;
            }
            places[box] =
                    new int[] {bestX + GUTTER, bestY - (bestTurned ? w[box] : h[box]) + GUTTER};
            turned[box] = bestTurned;
            top = Math.max(top, bestY);
        }
        final int height = align(top);
        return height > maxSide ? null : new Packing(width, height, places, turned);
    }

    /**
     * Round texels up to a whole multiple of {@link #ALIGN}.
     *
     * @param texels The texels.
     * @return The rounded count, at least {@link #ALIGN}.
     */
    private static int align(int texels) {
        return Math.max(ALIGN, (texels + ALIGN - 1) / ALIGN * ALIGN);
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
