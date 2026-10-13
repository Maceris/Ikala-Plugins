package com.ikalagaming.graphics.lod;

import com.ikalagaming.util.IntArrayList;

import lombok.NonNull;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Finds where open edges meet along a line without sharing corners: T-junctions, as two meshes
 * leave when each merged its side of a shared border its own way. Open edges are filed by the line
 * they lie on, so an edge can be tested against the others on its line. Where a surface meets
 * itself, its two sides run along the line in opposite directions, so an open edge is closed where
 * open edges running the other way cover it, and open where nothing does.
 */
public final class TJunctions {

    /** Coordinates in a position. */
    private static final int AXES = 3;

    /** Vertices in a triangle. */
    private static final int CORNERS = 3;

    /** The step directions are rounded to when filing lines. */
    private static final double DIRECTION_STEP = 1e-5;

    /** The step a line's nearest point to the origin is rounded to when filing lines, in units. */
    private static final double POINT_STEP = 1e-3;

    /** How far along a line two points may be apart and still be the same, in units. */
    static final double ALONG_SLACK = 1e-4;

    /** How near a triangle must be to an edge to touch it, in units. */
    static final double TOUCH = 1e-3;

    /** How far apart an edge is sampled when finding what touches it, in units. */
    private static final double SAMPLE_SPACING = 0.25;

    /** Samples along an edge at most. */
    private static final int MAX_SAMPLES = 16;

    /**
     * How many points to sample an edge at when finding what touches it.
     *
     * @param from Its start, x, y, z.
     * @param to Its end.
     * @return The count, at least 1.
     */
    static int samples(double @NonNull [] from, double @NonNull [] to) {
        final double length = Math.sqrt(Geometry.distanceSquared(from, to));
        return Math.clamp((long) Math.ceil(length / SAMPLE_SPACING), 1, MAX_SAMPLES);
    }

    /**
     * One of the points an edge is sampled at, spread evenly with none at the ends.
     *
     * @param from Its start, x, y, z.
     * @param to Its end.
     * @param k Which sample.
     * @param samples How many there are.
     * @param out Receives the point.
     */
    static void sample(
            double @NonNull [] from,
            double @NonNull [] to,
            int k,
            int samples,
            double @NonNull [] out) {
        final double t = (k + 0.5) / samples;
        for (int axis = 0; axis < AXES; ++axis) {
            out[axis] = from[axis] + t * (to[axis] - from[axis]);
        }
    }

    /**
     * A line, by its rounded direction, pointing to the positive side of its largest axis, and its
     * rounded point nearest the origin.
     *
     * @param dx The direction's x, in steps.
     * @param dy Its y.
     * @param dz Its z.
     * @param px The nearest point's x, in steps.
     * @param py Its y.
     * @param pz Its z.
     */
    private record Line(long dx, long dy, long dz, long px, long py, long pz) {}

    /**
     * An edge on a line.
     *
     * @param from Where it starts along the line.
     * @param to Where it ends, past the start.
     * @param forward Whether it runs along the line's direction.
     * @param tag Who it belongs to.
     * @param first The vertex at its start.
     * @param last The vertex at its end.
     */
    private record Segment(double from, double to, boolean forward, int tag, int first, int last) {}

    /** Where an edge lies. */
    private record Placed(Line line, double from, double to, boolean forward) {}

    /** The edges filed by line. */
    private final Map<Line, List<Segment>> lines = new HashMap<>();

    /**
     * Where an edge between two points lies.
     *
     * @param a One end, x, y, z.
     * @param b The other.
     * @return Where it lies, or null if it has no length.
     */
    private static Placed place(double @NonNull [] a, double @NonNull [] b) {
        final double[] d = new double[AXES];
        double length = 0;
        for (int axis = 0; axis < AXES; ++axis) {
            d[axis] = b[axis] - a[axis];
            length += d[axis] * d[axis];
        }
        length = Math.sqrt(length);
        if (length == 0) {
            return null;
        }
        int largest = 0;
        for (int axis = 1; axis < AXES; ++axis) {
            if (Math.abs(d[axis]) > Math.abs(d[largest])) {
                largest = axis;
            }
        }
        final boolean forward = d[largest] > 0;
        final double sign = forward ? 1 : -1;
        for (int axis = 0; axis < AXES; ++axis) {
            d[axis] *= sign / length;
        }
        final double ta = Geometry.dot(a, d);
        final double tb = Geometry.dot(b, d);
        final long[] point = new long[AXES];
        final long[] direction = new long[AXES];
        for (int axis = 0; axis < AXES; ++axis) {
            point[axis] = Math.round((a[axis] - ta * d[axis]) / POINT_STEP);
            direction[axis] = Math.round(d[axis] / DIRECTION_STEP);
        }
        return new Placed(
                new Line(direction[0], direction[1], direction[2], point[0], point[1], point[2]),
                Math.min(ta, tb),
                Math.max(ta, tb),
                forward);
    }

    /**
     * File an open edge.
     *
     * @param a Its start, x, y, z.
     * @param b Its end.
     * @param tag Who it belongs to.
     * @param first The vertex at a, or -1.
     * @param last The vertex at b, or -1.
     */
    public void add(double @NonNull [] a, double @NonNull [] b, int tag, int first, int last) {
        final Placed placed = place(a, b);
        if (placed == null) {
            return;
        }
        // The segment's first vertex is the one at its low end
        final int low = placed.forward() ? first : last;
        final int high = placed.forward() ? last : first;
        lines.computeIfAbsent(placed.line(), k -> new ArrayList<>())
                .add(new Segment(placed.from(), placed.to(), placed.forward(), tag, low, high));
    }

    /**
     * Whether open edges running the other way cover an edge from end to end, so the surface meets
     * itself all along it.
     *
     * @param a Its start, x, y, z.
     * @param b Its end.
     * @return True if it is covered.
     */
    public boolean covered(double @NonNull [] a, double @NonNull [] b) {
        final Placed placed = place(a, b);
        if (placed == null) {
            return true;
        }
        final List<Segment> on = lines.get(placed.line());
        if (on == null) {
            return false;
        }
        final List<Segment> others = new ArrayList<>();
        for (Segment segment : on) {
            if (segment.forward() != placed.forward()) {
                others.add(segment);
            }
        }
        others.sort((p, q) -> Double.compare(p.from(), q.from()));
        double reached = placed.from();
        for (Segment segment : others) {
            if (segment.from() > reached + ALONG_SLACK) {
                break;
            }
            reached = Math.max(reached, segment.to());
            if (reached >= placed.to() - ALONG_SLACK) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether an edge shares a stretch of its line with an edge with another tag: they overlap by
     * more than a point.
     *
     * @param a The edge's start, x, y, z.
     * @param b Its end.
     * @param tag The tag to look past.
     * @return True if another's edge overlaps it.
     */
    public boolean overlapsOther(double @NonNull [] a, double @NonNull [] b, int tag) {
        final Placed placed = place(a, b);
        if (placed == null) {
            return false;
        }
        final List<Segment> on = lines.get(placed.line());
        if (on == null) {
            return false;
        }
        for (Segment segment : on) {
            if (segment.tag() != tag
                    && segment.from() < placed.to() - ALONG_SLACK
                    && segment.to() > placed.from() + ALONG_SLACK) {
                return true;
            }
        }
        return false;
    }

    /**
     * The vertices at the ends of filed edges that lie inside an edge, not at its ends, in order
     * from its start.
     *
     * @param a Its start, x, y, z.
     * @param b Its end.
     * @return The vertices, each once.
     */
    public int[] inside(double @NonNull [] a, double @NonNull [] b) {
        final Placed placed = place(a, b);
        if (placed == null) {
            return new int[0];
        }
        final List<Segment> on = lines.get(placed.line());
        if (on == null) {
            return new int[0];
        }
        final Map<Integer, Double> found = new HashMap<>();
        for (Segment segment : on) {
            if (segment.first() >= 0
                    && segment.from() > placed.from() + ALONG_SLACK
                    && segment.from() < placed.to() - ALONG_SLACK) {
                found.put(segment.first(), segment.from());
            }
            if (segment.last() >= 0
                    && segment.to() > placed.from() + ALONG_SLACK
                    && segment.to() < placed.to() - ALONG_SLACK) {
                found.put(segment.last(), segment.to());
            }
        }
        final List<Map.Entry<Integer, Double>> sorted = new ArrayList<>(found.entrySet());
        sorted.sort(Map.Entry.comparingByValue());
        if (!placed.forward()) {
            java.util.Collections.reverse(sorted);
        }
        final int[] out = new int[sorted.size()];
        for (int i = 0; i < out.length; ++i) {
            out[i] = sorted.get(i).getKey();
        }
        return out;
    }

    /**
     * Close a mesh's T-junctions: split every open edge at the open edges' vertices inside it, so
     * that where two parts met along a line without sharing corners they now share every corner on
     * it. Each split triangle becomes a fan from the corner across from the edge. No vertex is
     * added or moved. A vertex let into an edge between two locked vertices is locked too: that
     * edge is a border another part meets, and the vertex must stay on it.
     *
     * @param mesh The mesh.
     * @param from Receives, for each triangle of the result, which triangle of the mesh it came
     *     from.
     * @return The mesh with its T-junctions closed, or the mesh itself if it had none.
     */
    public static LodMesh weld(@NonNull LodMesh mesh, @NonNull IntArrayList from) {
        final int triangles = mesh.getTriangleCount();
        final Map<Long, Integer> uses = new HashMap<>();
        for (int t = 0; t < triangles; ++t) {
            for (int c = 0; c < CORNERS; ++c) {
                uses.merge(
                        edgeKey(mesh.corner(t, c), mesh.corner(t, (c + 1) % CORNERS)),
                        1,
                        Integer::sum);
            }
        }
        final TJunctions index = new TJunctions();
        for (int t = 0; t < triangles; ++t) {
            for (int c = 0; c < CORNERS; ++c) {
                final int v = mesh.corner(t, c);
                final int w = mesh.corner(t, (c + 1) % CORNERS);
                if (uses.getOrDefault(edgeKey(v, w), 0) == 1) {
                    index.add(position(mesh, v), position(mesh, w), 0, v, w);
                }
            }
        }
        // Split each triangle along one edge at a time; the pieces' new edges run inside, and the
        // pieces of the split edge have nothing left inside them
        final List<int[]> out = new ArrayList<>();
        final IntArrayList outFrom = new IntArrayList();
        final ArrayDeque<int[]> work = new ArrayDeque<>();
        final boolean[] locked = Arrays.copyOf(mesh.locked, mesh.getVertexCount());
        boolean any = false;
        for (int t = 0; t < triangles; ++t) {
            work.add(new int[] {mesh.corner(t, 0), mesh.corner(t, 1), mesh.corner(t, 2), t});
            while (!work.isEmpty()) {
                final int[] piece = work.poll();
                boolean split = false;
                for (int c = 0; c < CORNERS && !split; ++c) {
                    final int v = piece[c];
                    final int w = piece[(c + 1) % CORNERS];
                    final int across = piece[(c + 2) % CORNERS];
                    if (uses.getOrDefault(edgeKey(v, w), 0) != 1) {
                        continue;
                    }
                    final int[] between = index.inside(position(mesh, v), position(mesh, w));
                    if (between.length == 0) {
                        continue;
                    }
                    split = true;
                    any = true;
                    if (mesh.locked[v] && mesh.locked[w]) {
                        for (int m : between) {
                            locked[m] = true;
                        }
                    }
                    int previous = v;
                    for (int m : between) {
                        work.add(new int[] {previous, m, across, piece[CORNERS]});
                        previous = m;
                    }
                    work.add(new int[] {previous, w, across, piece[CORNERS]});
                }
                if (!split) {
                    out.add(piece);
                    outFrom.addInt(piece[CORNERS]);
                }
            }
        }
        from.clear();
        if (!any) {
            for (int t = 0; t < triangles; ++t) {
                from.addInt(t);
            }
            return mesh;
        }
        final int[] corners = new int[out.size() * CORNERS];
        final int[] materials = new int[out.size()];
        final float[] visibility = new float[out.size()];
        for (int i = 0; i < out.size(); ++i) {
            final int[] piece = out.get(i);
            System.arraycopy(piece, 0, corners, i * CORNERS, CORNERS);
            materials[i] = mesh.material(piece[CORNERS]);
            visibility[i] = mesh.visibility(piece[CORNERS]);
            from.addInt(outFrom.getInt(i));
        }
        return new LodMesh(
                mesh.getOrigin(),
                mesh.positions,
                mesh.getVertexCount(),
                corners,
                out.size(),
                materials,
                visibility,
                locked);
    }

    /**
     * An undirected edge's key.
     *
     * @param v One end.
     * @param w The other.
     * @return The key.
     */
    private static long edgeKey(int v, int w) {
        return LongIntMap.pair(Math.min(v, w), Math.max(v, w));
    }

    /**
     * A mesh vertex's position.
     *
     * @param mesh The mesh.
     * @param v The vertex.
     * @return x, y, z.
     */
    private static double[] position(@NonNull LodMesh mesh, int v) {
        return new double[] {mesh.position(v, 0), mesh.position(v, 1), mesh.position(v, 2)};
    }
}
