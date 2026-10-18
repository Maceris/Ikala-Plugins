package com.ikalagaming.graphics.lod;

import lombok.NonNull;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;

/**
 * Shells for coverage volumes small on screen, which cost less to draw as a few triangles than as
 * bricks walked a voxel at a time: a convex hull for each brick's share of each group of touching
 * voxels, so the shell follows the group's outline, its corners carrying the look of the voxels
 * under them and how much of the light they stop, for a hashed alpha test. Each hull's coverage
 * counts only its own voxels, so a ray crossing several hulls is stopped by each in turn, about as
 * the volume stops it. A hull stays as thin as the voxels it wraps, so a rail or a chain keeps a
 * thin shell, and a sparse canopy a mostly see-through one.
 *
 * <p>Hulls are of the voxels' corners, which lie on the voxel grid, so they are worked out in exact
 * integer arithmetic. Each hull corner looks into the volume along a few rays around its inward
 * normal: the share of the light they lose is its coverage, and what they meet, weighted by how
 * much of it they see, its color, roughness and metalness.
 *
 * <p>A model's instances may draw it with other materials, so a model's shell names materials
 * instead: each triangle the material its corners see most of, and each corner its shade against
 * that material's average, as the model's voxels do.
 */
public final class CoverageShells {

    /** Coordinates in a position. */
    private static final int AXES = 3;

    /** Corners of a triangle. */
    private static final int CORNERS = 3;

    /** Values in a triangle's corners. */
    private static final int TRIANGLE_VALUES = 9;

    /** Rays a hull corner looks into the volume along. */
    private static final int RAYS = 5;

    /** How far the rays around the inward normal lean from it, in radians. */
    private static final double LEAN = Math.toRadians(30);

    /** How far apart samples along a ray are, in voxels. */
    private static final double STEP = 0.5;

    /** The largest pieces a group is split into for its hulls, a brick, in voxels a side. */
    private static final int LARGEST_PIECE = CoverageBricks.SIDE;

    /** The smallest pieces, in voxels a side. */
    private static final int SMALLEST_PIECE = 4;

    /** How far past its piece a hull wraps its neighbors' voxels, as a share of its side. */
    private static final int MARGIN_SHARE = 4;

    /**
     * How much of its pieces' hulls a group's voxels must fill for it not to be split finer: below
     * this, its hulls stretch across gaps it has, which they would cover.
     */
    private static final double MIN_FILL = 0.25;

    /** How much a corner must cover to be taken as covering all, see CoverageVoxelizer. */
    private static final double OPAQUE_ENOUGH = 0.9;

    /** The most times a hull's faces are split in four. */
    private static final int MAX_SPLITS = 0;

    /** How many of a hull's longest edges fit across it once its faces are split enough. */
    private static final double SPLIT_EDGES = 6;

    /** Where the points' order starts, so the same volume gives the same shell. */
    private static final long SEED = 0x5EED5EEDL;

    /**
     * A volume's shell.
     *
     * @param corners Its triangles' corners, 9 floats each, in the volume's frame.
     * @param attributes Its triangles' corners' averaged attributes, {@link
     *     Attributes#PER_TRIANGLE} ints a triangle, see {@link Attributes#shell} and {@link
     *     Attributes#namedShell}.
     * @param materials Each triangle's material, for a named shell; 0 for others.
     */
    public record Shell(
            float @NonNull [] corners, int @NonNull [] attributes, int @NonNull [] materials) {
        /**
         * How many triangles it has.
         *
         * @return The count.
         */
        public int triangles() {
            return corners.length / TRIANGLE_VALUES;
        }
    }

    /**
     * What a hull corner sees looking into the volume.
     *
     * @param look Linear red, green and blue, roughness and metalness, and in the specular's place
     *     its coverage, see {@link #lookInto}.
     * @param shade For a named shell, the mean shade of what it sees.
     * @param materials For a named shell, how much of each material it sees; empty for others.
     */
    private record Seen(
            float @NonNull [] look, float shade, @NonNull Map<Integer, Double> materials) {}

    /**
     * Wrap a volume in shells.
     *
     * @param volume The volume.
     * @param named Whether its corners name materials, for a model's volume, rather than carry
     *     colors.
     * @return Its shell, a hull for each group of touching voxels.
     */
    public static @NonNull Shell of(@NonNull CoverageVolume volume, boolean named) {
        final List<float[]> corners = new ArrayList<>();
        final List<int[]> attributes = new ArrayList<>();
        final List<Integer> materials = new ArrayList<>();
        final double[] share = new double[volume.size()];
        for (int[] component : components(volume)) {
            // Hulls of smaller pieces while the group's voxels fill little of its hulls, as a
            // sparse canopy does, so they don't stretch across its gaps
            for (int side = LARGEST_PIECE; ; side /= 2) {
                final int start = corners.size();
                final double hulls =
                        hulls(
                                volume,
                                named,
                                component,
                                side,
                                share,
                                corners,
                                attributes,
                                materials);
                if (side / 2 < SMALLEST_PIECE || component.length >= MIN_FILL * hulls) {
                    break;
                }
                corners.subList(start, corners.size()).clear();
                attributes.subList(start, attributes.size()).clear();
                materials.subList(start, materials.size()).clear();
            }
        }
        final float[] allCorners = new float[corners.size() * TRIANGLE_VALUES];
        final int[] allAttributes = new int[attributes.size() * Attributes.PER_TRIANGLE];
        for (int t = 0; t < corners.size(); ++t) {
            System.arraycopy(corners.get(t), 0, allCorners, t * TRIANGLE_VALUES, TRIANGLE_VALUES);
            System.arraycopy(
                    attributes.get(t),
                    0,
                    allAttributes,
                    t * Attributes.PER_TRIANGLE,
                    Attributes.PER_TRIANGLE);
        }
        final int[] allMaterials = new int[materials.size()];
        for (int t = 0; t < allMaterials.length; ++t) {
            allMaterials[t] = materials.get(t);
        }
        return new Shell(allCorners, allAttributes, allMaterials);
    }

    /**
     * Add a group's hulls, one for each piece's share of it, so the shell follows its outline, each
     * wrapping a little of its neighbors' too, so its faces don't lie along the pieces' planes,
     * where coverage would step from one hull to the next.
     *
     * @param volume The volume.
     * @param named Whether its corners name materials.
     * @param group The group's voxels.
     * @param side The pieces' side, in voxels.
     * @param share Each voxel's share for a hull, all zero, and left so.
     * @param corners Receives the triangles' corners.
     * @param attributes Receives the triangles' attributes.
     * @param materials Receives the triangles' materials.
     * @return How much the hulls hold, in cubed voxels.
     */
    private static double hulls(
            @NonNull CoverageVolume volume,
            boolean named,
            int @NonNull [] group,
            int side,
            double @NonNull [] share,
            @NonNull List<float[]> corners,
            @NonNull List<int[]> attributes,
            @NonNull List<Integer> materials) {
        final List<int[]> pieces = pieces(volume, group, side);
        final Set<Long> cells = new HashSet<>();
        for (int[] piece : pieces) {
            cells.add(pieceOf(volume.keyOf(piece[0]), side));
        }
        double held = 0;
        for (int[] piece : pieces) {
            final long cell = pieceOf(volume.keyOf(piece[0]), side);
            final int[] wrapped = wrapped(volume, group, piece, side);
            for (int v : wrapped) {
                share[v] = shareOf(volume.keyOf(v), cell, cells, side);
            }
            held += hull(volume, named, wrapped, share, corners, attributes, materials);
            for (int v : wrapped) {
                share[v] = 0;
            }
        }
        return held;
    }

    /**
     * A group's voxels split by the cube of some side they are in.
     *
     * @param volume The volume.
     * @param group The group's voxels.
     * @param side The cubes' side, in voxels.
     * @return Each cube's share of them.
     */
    static List<int[]> pieces(@NonNull CoverageVolume volume, int @NonNull [] group, int side) {
        final Map<Long, List<Integer>> byBrick = new java.util.LinkedHashMap<>();
        for (int v : group) {
            byBrick.computeIfAbsent(pieceOf(volume.keyOf(v), side), k -> new ArrayList<>()).add(v);
        }
        final List<int[]> out = new ArrayList<>(byBrick.size());
        for (List<Integer> piece : byBrick.values()) {
            out.add(piece.stream().mapToInt(Integer::intValue).toArray());
        }
        return out;
    }

    /**
     * The piece a voxel is in.
     *
     * @param key The voxel's key.
     * @param side The pieces' side, in voxels.
     * @return The piece's key, in pieces.
     */
    static long pieceOf(long key, int side) {
        return CoverageVolume.key(
                CoverageVolume.coordinate(key, 0) / side,
                CoverageVolume.coordinate(key, 1) / side,
                CoverageVolume.coordinate(key, 2) / side);
    }

    /**
     * How far past its piece a hull wraps its neighbors' voxels.
     *
     * @param side The pieces' side, in voxels.
     * @return The margin, in voxels.
     */
    private static int marginOf(int side) {
        return Math.max(1, side / MARGIN_SHARE);
    }

    /**
     * How much of a voxel a piece's hull takes: all of it well inside the piece, tapering to none
     * its margin past it, shared with the neighboring pieces' hulls so the shares add to one. A ray
     * across the margin then crosses both hulls and loses the voxel's light once between them, so
     * there is neither a seam along the piece's plane nor a double layer over it.
     *
     * @param key The voxel's key.
     * @param brick The piece's key, in pieces.
     * @param bricks The pieces with hulls in the voxel's group.
     * @param side The pieces' side, in voxels.
     * @return The piece's share, from 0 to 1.
     */
    static double shareOf(long key, long brick, @NonNull Set<Long> bricks, int side) {
        final long own = pieceOf(key, side);
        double total = 0;
        for (int dx = -1; dx <= 1; ++dx) {
            for (int dy = -1; dy <= 1; ++dy) {
                for (int dz = -1; dz <= 1; ++dz) {
                    final int x = CoverageVolume.coordinate(own, 0) + dx;
                    final int y = CoverageVolume.coordinate(own, 1) + dy;
                    final int z = CoverageVolume.coordinate(own, 2) + dz;
                    if (x < 0 || y < 0 || z < 0) {
                        continue;
                    }
                    final long near = CoverageVolume.key(x, y, z);
                    if (bricks.contains(near)) {
                        total += taper(key, near, side);
                    }
                }
            }
        }
        return taper(key, brick, side) / total;
    }

    /**
     * How much a piece holds of a voxel before sharing with its neighbors: one inside, falling
     * evenly to none across its margin either side of each of its planes, so two neighbors' holds
     * add to one across the plane between them.
     *
     * @param key The voxel's key.
     * @param brick The piece's key, in pieces.
     * @param side The pieces' side, in voxels.
     * @return The piece's hold, from 0 to 1.
     */
    private static double taper(long key, long brick, int side) {
        final int margin = marginOf(side);
        double out = 1;
        for (int axis = 0; axis < AXES; ++axis) {
            final double at =
                    CoverageVolume.coordinate(key, axis)
                            + 0.5
                            - (double) CoverageVolume.coordinate(brick, axis) * side;
            out *= Math.clamp(Math.min(at + margin, side + margin - at) / (2 * margin), 0, 1);
        }
        return out;
    }

    /**
     * A piece's share of a group and the group's voxels within its margin around the piece, which
     * its hull wraps.
     *
     * @param volume The volume.
     * @param group The group's voxels.
     * @param piece The piece's share of them.
     * @param side The pieces' side, in voxels.
     * @return The voxels to wrap.
     */
    private static int[] wrapped(
            @NonNull CoverageVolume volume,
            int @NonNull [] group,
            int @NonNull [] piece,
            int side) {
        final int margin = marginOf(side);
        final long first = volume.keyOf(piece[0]);
        final int[] low = new int[AXES];
        for (int axis = 0; axis < AXES; ++axis) {
            low[axis] = CoverageVolume.coordinate(first, axis) / side * side - margin;
        }
        final List<Integer> out = new ArrayList<>();
        for (int v : group) {
            final long key = volume.keyOf(v);
            boolean near = true;
            for (int axis = 0; axis < AXES && near; ++axis) {
                final int at = CoverageVolume.coordinate(key, axis);
                near = at >= low[axis] && at < low[axis] + side + 2 * margin;
            }
            if (near) {
                out.add(v);
            }
        }
        return out.stream().mapToInt(Integer::intValue).toArray();
    }

    /**
     * The groups of voxels that touch, by faces, edges or corners.
     *
     * @param volume The volume.
     * @return Each group's voxels.
     */
    static List<int[]> components(@NonNull CoverageVolume volume) {
        final Map<Long, Integer> at = new HashMap<>();
        for (int v = 0; v < volume.size(); ++v) {
            at.put(volume.keyOf(v), v);
        }
        final boolean[] seen = new boolean[volume.size()];
        final List<int[]> out = new ArrayList<>();
        for (int first = 0; first < volume.size(); ++first) {
            if (seen[first]) {
                continue;
            }
            final List<Integer> group = new ArrayList<>();
            final ArrayDeque<Integer> open = new ArrayDeque<>();
            open.add(first);
            seen[first] = true;
            while (!open.isEmpty()) {
                final int v = open.poll();
                group.add(v);
                final long key = volume.keyOf(v);
                final int x = CoverageVolume.coordinate(key, 0);
                final int y = CoverageVolume.coordinate(key, 1);
                final int z = CoverageVolume.coordinate(key, 2);
                for (int dx = -1; dx <= 1; ++dx) {
                    for (int dy = -1; dy <= 1; ++dy) {
                        for (int dz = -1; dz <= 1; ++dz) {
                            if (x + dx < 0 || y + dy < 0 || z + dz < 0) {
                                continue;
                            }
                            final Integer next = at.get(CoverageVolume.key(x + dx, y + dy, z + dz));
                            if (next != null && !seen[next]) {
                                seen[next] = true;
                                open.add(next);
                            }
                        }
                    }
                }
            }
            out.add(group.stream().mapToInt(Integer::intValue).toArray());
        }
        return out;
    }

    /**
     * Add one group's hull.
     *
     * @param volume The volume.
     * @param named Whether its corners name materials.
     * @param group The piece's voxels.
     * @param share How much of each voxel of the volume the piece's hull takes.
     * @param corners Receives its triangles' corners.
     * @param attributes Receives its triangles' attributes.
     * @param materials Receives its triangles' materials.
     * @return How much the hull holds, in cubed voxels.
     */
    private static double hull(
            @NonNull CoverageVolume volume,
            boolean named,
            int @NonNull [] group,
            double @NonNull [] share,
            @NonNull List<float[]> corners,
            @NonNull List<int[]> attributes,
            @NonNull List<Integer> materials) {
        final long[][] points = hullPoints(volume, group);
        final List<int[]> faces = ConvexHull.of(points, SEED);
        if (faces.isEmpty()) {
            return 0;
        }
        // Each hull corner's normal, its faces' normals weighted by their area
        final List<double[]> positions = new ArrayList<>();
        final List<double[]> normals = new ArrayList<>();
        for (long[] p : points) {
            positions.add(new double[] {p[0], p[1], p[2]});
            normals.add(new double[AXES]);
        }
        for (int[] face : faces) {
            final double[] n = ConvexHull.normal(points, face);
            for (int corner : face) {
                for (int axis = 0; axis < AXES; ++axis) {
                    normals.get(corner)[axis] += n[axis];
                }
            }
        }
        // Faces split evenly until their edges are short against the hull, so the coverage and
        // look are sampled across them, not only at the hull's corners, which are its extremes
        final double reachVoxels = reach(points);
        List<int[]> split = faces;
        for (int level = 0;
                level < MAX_SPLITS && longestEdge(positions, split) > reachVoxels / SPLIT_EDGES;
                ++level) {
            split = splitFaces(positions, normals, split);
        }
        final double[] origin = volume.origin();
        final double size = volume.voxelSize();
        final double reach = reachVoxels * size;
        final Map<Integer, int[]> cornerAttributes = new HashMap<>();
        final Map<Integer, Seen> cornerSeen = new HashMap<>();
        for (int[] face : split) {
            for (int corner : face) {
                if (cornerAttributes.containsKey(corner)) {
                    continue;
                }
                final double[] n = unit(normals.get(corner));
                final double[] p = positions.get(corner);
                final double[] position = {
                    origin[0] + p[0] * size, origin[1] + p[1] * size, origin[2] + p[2] * size
                };
                final Seen seen = lookInto(volume, named, share, position, n, reach);
                final float[] normal = {(float) n[0], (float) n[1], (float) n[2]};
                final float coverage = seen.look()[MaterialAverages.SPECULAR];
                final int[] out = new int[Attributes.VALUES];
                if (named) {
                    Attributes.namedShell(normal, seen.shade(), coverage, out, 0);
                } else {
                    Attributes.shell(normal, seen.look(), coverage, out, 0);
                }
                cornerAttributes.put(corner, out);
                cornerSeen.put(corner, seen);
            }
        }
        for (int[] face : split) {
            final float[] triangle = new float[TRIANGLE_VALUES];
            final int[] own = new int[Attributes.PER_TRIANGLE];
            for (int c = 0; c < CORNERS; ++c) {
                final double[] p = positions.get(face[c]);
                for (int axis = 0; axis < AXES; ++axis) {
                    triangle[c * AXES + axis] = (float) (origin[axis] + p[axis] * size);
                }
                System.arraycopy(
                        cornerAttributes.get(face[c]),
                        0,
                        own,
                        c * Attributes.VALUES,
                        Attributes.VALUES);
            }
            corners.add(triangle);
            attributes.add(own);
            materials.add(named ? mainMaterial(cornerSeen, face) : 0);
        }
        return ConvexHull.held(points, faces);
    }

    /**
     * The material a triangle's corners see most of.
     *
     * @param seen What each corner sees.
     * @param face The triangle's corners.
     * @return The material, or 0 if they see none.
     */
    private static int mainMaterial(@NonNull Map<Integer, Seen> seen, int @NonNull [] face) {
        final Map<Integer, Double> total = new HashMap<>();
        for (int corner : face) {
            seen.get(corner).materials().forEach((m, w) -> total.merge(m, w, Double::sum));
        }
        int best = 0;
        double most = -1;
        for (Map.Entry<Integer, Double> e : total.entrySet()) {
            if (e.getValue() > most || e.getValue() == most && e.getKey() < best) {
                most = e.getValue();
                best = e.getKey();
            }
        }
        return best;
    }

    /**
     * The longest edge of some faces.
     *
     * @param positions The corners' positions.
     * @param faces The faces.
     * @return Its length.
     */
    private static double longestEdge(
            @NonNull List<double[]> positions, @NonNull List<int[]> faces) {
        double longest = 0;
        for (int[] face : faces) {
            for (int e = 0; e < CORNERS; ++e) {
                final double[] a = positions.get(face[e]);
                final double[] b = positions.get(face[(e + 1) % CORNERS]);
                final double dx = a[0] - b[0];
                final double dy = a[1] - b[1];
                final double dz = a[2] - b[2];
                longest = Math.max(longest, Math.sqrt(dx * dx + dy * dy + dz * dz));
            }
        }
        return longest;
    }

    /**
     * Split every face in four at its edges' middles, each middle shared by the faces on its edge,
     * its normal the blend of its edge's ends'.
     *
     * @param positions The corners' positions, added to.
     * @param normals The corners' normals, added to.
     * @param faces The faces.
     * @return The split faces, facing the same way.
     */
    private static List<int[]> splitFaces(
            @NonNull List<double[]> positions,
            @NonNull List<double[]> normals,
            @NonNull List<int[]> faces) {
        final Map<Long, Integer> middles = new HashMap<>();
        final List<int[]> out = new ArrayList<>(faces.size() * 4);
        for (int[] face : faces) {
            final int[] m = new int[CORNERS];
            for (int e = 0; e < CORNERS; ++e) {
                final int a = face[e];
                final int b = face[(e + 1) % CORNERS];
                final long key = (long) Math.min(a, b) << Integer.SIZE | Math.max(a, b);
                m[e] =
                        middles.computeIfAbsent(
                                key,
                                k -> {
                                    final double[] pa = positions.get(a);
                                    final double[] pb = positions.get(b);
                                    final double[] na = unit(normals.get(a));
                                    final double[] nb = unit(normals.get(b));
                                    positions.add(
                                            new double[] {
                                                (pa[0] + pb[0]) / 2,
                                                (pa[1] + pb[1]) / 2,
                                                (pa[2] + pb[2]) / 2
                                            });
                                    normals.add(
                                            new double[] {
                                                na[0] + nb[0], na[1] + nb[1], na[2] + nb[2]
                                            });
                                    return positions.size() - 1;
                                });
            }
            out.add(new int[] {face[0], m[0], m[2]});
            out.add(new int[] {m[0], face[1], m[1]});
            out.add(new int[] {m[2], m[1], face[2]});
            out.add(new int[] {m[0], m[1], m[2]});
        }
        return out;
    }

    /**
     * The corners of a group's voxels the hull may pass through: those of voxels with an empty
     * neighbor, since the rest are inside, each once.
     *
     * @param volume The volume.
     * @param group The group's voxels.
     * @return The corners, in voxels on the volume's grid.
     */
    private static long[][] hullPoints(@NonNull CoverageVolume volume, int @NonNull [] group) {
        final Set<Long> inGroup = new HashSet<>();
        for (int v : group) {
            inGroup.add(volume.keyOf(v));
        }
        final Set<Long> corners = new HashSet<>();
        final int[][] faces = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        for (int v : group) {
            final long key = volume.keyOf(v);
            final int x = CoverageVolume.coordinate(key, 0);
            final int y = CoverageVolume.coordinate(key, 1);
            final int z = CoverageVolume.coordinate(key, 2);
            boolean outer = false;
            for (int[] f : faces) {
                if (x + f[0] < 0
                        || y + f[1] < 0
                        || z + f[2] < 0
                        || !inGroup.contains(CoverageVolume.key(x + f[0], y + f[1], z + f[2]))) {
                    outer = true;
                    break;
                }
            }
            if (!outer) {
                continue;
            }
            for (int c = 0; c < 8; ++c) {
                corners.add(
                        CoverageVolume.key(x + (c & 1), y + ((c >> 1) & 1), z + ((c >> 2) & 1)));
            }
        }
        final long[][] out = new long[corners.size()][];
        int i = 0;
        for (long key : corners) {
            out[i++] =
                    new long[] {
                        CoverageVolume.coordinate(key, 0),
                        CoverageVolume.coordinate(key, 1),
                        CoverageVolume.coordinate(key, 2)
                    };
        }
        return out;
    }

    /**
     * How far across a set of points is, along its widest axis.
     *
     * @param points The points.
     * @return The width.
     */
    private static double reach(long @NonNull [] @NonNull [] points) {
        double widest = 0;
        for (int axis = 0; axis < AXES; ++axis) {
            long low = Long.MAX_VALUE;
            long high = Long.MIN_VALUE;
            for (long[] p : points) {
                low = Math.min(low, p[axis]);
                high = Math.max(high, p[axis]);
            }
            widest = Math.max(widest, high - low);
        }
        return widest * Math.sqrt(AXES);
    }

    /**
     * What a hull corner sees looking into its piece of the volume: rays around its inward normal,
     * each losing light to the piece's share of the voxels it crosses. Only its share, since a ray
     * through several pieces crosses each one's shell and is stopped by each in turn.
     *
     * @param volume The volume.
     * @param named Whether to tally the materials and shades it sees too.
     * @param share How much of each voxel the piece's hull takes.
     * @param position The corner.
     * @param normal Its outward unit normal.
     * @param reach How far the rays go.
     * @return Linear red, green and blue, roughness and metalness, each weighted by how much of it
     *     the rays see, and in the specular's place the share of the light they lose; with, if
     *     named, the materials and mean shade they see, weighted alike.
     */
    private static Seen lookInto(
            @NonNull CoverageVolume volume,
            boolean named,
            double @NonNull [] share,
            double @NonNull [] position,
            double @NonNull [] normal,
            double reach) {
        final double[] inward = {-normal[0], -normal[1], -normal[2]};
        final double[] across = unit(cross(inward, Math.abs(inward[0]) < 0.9 ? X : Y));
        final double[] up = cross(inward, across);
        final double step = STEP * volume.voxelSize();
        final double[] look = new double[MaterialAverages.VALUES];
        double seen = 0;
        double lost = 0;
        double shade = 0;
        final Map<Integer, Double> materials = new HashMap<>();
        final double[] point = new double[AXES];
        for (int r = 0; r < RAYS; ++r) {
            final double[] direction = inward.clone();
            if (r > 0) {
                final double angle = (r - 1) * Math.PI / 2;
                for (int axis = 0; axis < AXES; ++axis) {
                    direction[axis] =
                            Math.cos(LEAN) * inward[axis]
                                    + Math.sin(LEAN)
                                            * (Math.cos(angle) * across[axis]
                                                    + Math.sin(angle) * up[axis]);
                }
            }
            double through = 1;
            for (double d = step / 2; d < reach && through > 1e-3; d += step) {
                for (int axis = 0; axis < AXES; ++axis) {
                    point[axis] = position[axis] + direction[axis] * d;
                }
                final int v = volume.indexAt(point);
                if (v < 0 || share[v] == 0) {
                    continue;
                }
                final double kept =
                        Math.exp(
                                -share[v]
                                        * volume.density(v)
                                        * volume.projectedArea(v, direction)
                                        * step);
                final double seenHere = through * (1 - kept);
                for (int c = 0; c < CoverageVolume.COLOR_VALUES; ++c) {
                    look[MaterialAverages.RED + c] += seenHere * volume.color(v, c);
                }
                look[MaterialAverages.ROUGHNESS] += seenHere * volume.roughness(v);
                look[MaterialAverages.METALLIC] += seenHere * volume.metallic(v);
                seen += seenHere;
                if (named && volume.material(v) >= 0) {
                    materials.merge(volume.material(v), seenHere, Double::sum);
                    shade += seenHere * volume.shade(v);
                }
                through *= kept;
            }
            lost += 1 - through;
        }
        final float[] out = new float[MaterialAverages.VALUES];
        for (int k = 0; k < MaterialAverages.SPECULAR; ++k) {
            out[k] = seen > 0 ? (float) (look[k] / seen) : 0;
        }
        // Nearly covering is covering, so the shells behind lose to the depth test
        final double coverage = lost / RAYS;
        out[MaterialAverages.SPECULAR] = (float) (coverage >= OPAQUE_ENOUGH ? 1 : coverage);
        double tallied = 0;
        for (double w : materials.values()) {
            tallied += w;
        }
        return new Seen(out, tallied > 0 ? (float) (shade / tallied) : 1, materials);
    }

    /** The x axis. */
    private static final double[] X = {1, 0, 0};

    /** The y axis. */
    private static final double[] Y = {0, 1, 0};

    /**
     * A cross product.
     *
     * @param a One vector.
     * @param b The other.
     * @return a x b.
     */
    private static double[] cross(double @NonNull [] a, double @NonNull [] b) {
        return new double[] {
            a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]
        };
    }

    /**
     * A vector at unit length.
     *
     * @param v The vector, not zero.
     * @return It at unit length.
     */
    private static double[] unit(double @NonNull [] v) {
        final double length = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        return length <= 0
                ? new double[] {0, 1, 0}
                : new double[] {v[0] / length, v[1] / length, v[2] / length};
    }

    /** Exact convex hulls of integer points. */
    static final class ConvexHull {

        /**
         * The convex hull of points, by adding them one at a time and replacing the faces each one
         * sees with faces to it from their horizon. Points are added in a shuffled order, so the
         * work stays near n log n.
         *
         * @param points The points.
         * @param seed The shuffle's seed.
         * @return The hull's triangles as point indices, counter-clockwise from outside, or none if
         *     the points don't span a volume.
         */
        static List<int[]> of(long @NonNull [] @NonNull [] points, long seed) {
            final int n = points.length;
            final int[] order = new int[n];
            for (int i = 0; i < n; ++i) {
                order[i] = i;
            }
            final SplittableRandom random = new SplittableRandom(seed);
            for (int i = n - 1; i > 0; --i) {
                final int j = random.nextInt(i + 1);
                final int t = order[i];
                order[i] = order[j];
                order[j] = t;
            }
            final int[] start = tetrahedron(points, order);
            if (start == null) {
                return List.of();
            }
            List<int[]> faces = new ArrayList<>();
            final int a = start[0];
            final int b = start[1];
            final int c = start[2];
            final int d = start[3];
            // Each face turned so the tetrahedron's other corner is behind it
            for (int[] f : new int[][] {{a, b, c}, {a, d, b}, {a, c, d}, {b, d, c}}) {
                final int other = remaining(f, a, b, c, d);
                faces.add(
                        orient(points, f[0], f[1], f[2], other) > 0
                                ? new int[] {f[0], f[2], f[1]}
                                : f);
            }
            final Set<Integer> used = new HashSet<>(Arrays.asList(a, b, c, d));
            for (int i : order) {
                if (used.contains(i)) {
                    continue;
                }
                final List<int[]> visible = new ArrayList<>();
                final List<int[]> kept = new ArrayList<>();
                for (int[] f : faces) {
                    (orient(points, f[0], f[1], f[2], i) > 0 ? visible : kept).add(f);
                }
                if (visible.isEmpty()) {
                    continue;
                }
                // The horizon: visible faces' edges whose reverse no visible face has
                final Set<Long> edges = new HashSet<>();
                for (int[] f : visible) {
                    for (int e = 0; e < CORNERS; ++e) {
                        edges.add(edge(f[e], f[(e + 1) % CORNERS]));
                    }
                }
                for (int[] f : visible) {
                    for (int e = 0; e < CORNERS; ++e) {
                        final int u = f[e];
                        final int v = f[(e + 1) % CORNERS];
                        if (!edges.contains(edge(v, u))) {
                            kept.add(new int[] {u, v, i});
                        }
                    }
                }
                faces = kept;
            }
            return faces;
        }

        /**
         * The corner of a tetrahedron a face of it leaves out.
         *
         * @param f The face.
         * @param a A corner.
         * @param b A corner.
         * @param c A corner.
         * @param d A corner.
         * @return The one not in the face.
         */
        private static int remaining(int @NonNull [] f, int a, int b, int c, int d) {
            for (int p : new int[] {a, b, c, d}) {
                if (p != f[0] && p != f[1] && p != f[2]) {
                    return p;
                }
            }
            return d;
        }

        /**
         * Four points that span a volume.
         *
         * @param points The points.
         * @param order The order to look in.
         * @return Their indices, or null if every point lies in one plane.
         */
        private static int[] tetrahedron(
                long @NonNull [] @NonNull [] points, int @NonNull [] order) {
            if (points.length < 4) {
                return null;
            }
            final int a = order[0];
            int b = -1;
            for (int i : order) {
                if (!Arrays.equals(points[i], points[a])) {
                    b = i;
                    break;
                }
            }
            if (b < 0) {
                return null;
            }
            int c = -1;
            for (int i : order) {
                final long[] n = crossOf(points, a, b, i);
                if (n[0] != 0 || n[1] != 0 || n[2] != 0) {
                    c = i;
                    break;
                }
            }
            if (c < 0) {
                return null;
            }
            for (int i : order) {
                if (orient(points, a, b, c, i) != 0) {
                    return new int[] {a, b, c, i};
                }
            }
            return null;
        }

        /**
         * Which side of a face's plane a point is on: positive in front, the way the face's normal
         * points by the right hand rule.
         *
         * @param points The points.
         * @param a The face's first corner.
         * @param b Its second.
         * @param c Its third.
         * @param p The point.
         * @return The signed volume, six times the tetrahedron's.
         */
        static long orient(long @NonNull [] @NonNull [] points, int a, int b, int c, int p) {
            final long[] n = crossOf(points, a, b, c);
            final long[] pa = points[a];
            final long[] pp = points[p];
            return n[0] * (pp[0] - pa[0]) + n[1] * (pp[1] - pa[1]) + n[2] * (pp[2] - pa[2]);
        }

        /**
         * How much a hull holds: the sum of the tetrahedra from the origin to its faces.
         *
         * @param points The points.
         * @param faces The hull's faces, facing out.
         * @return Its volume, in the points' units cubed.
         */
        static double held(long @NonNull [] @NonNull [] points, @NonNull List<int[]> faces) {
            long six = 0;
            for (int[] f : faces) {
                final long[] a = points[f[0]];
                final long[] b = points[f[1]];
                final long[] c = points[f[2]];
                six +=
                        a[0] * (b[1] * c[2] - b[2] * c[1])
                                + a[1] * (b[2] * c[0] - b[0] * c[2])
                                + a[2] * (b[0] * c[1] - b[1] * c[0]);
            }
            return Math.abs(six) / 6.0;
        }

        /**
         * (b - a) x (c - a).
         *
         * @param points The points.
         * @param a One point.
         * @param b Another.
         * @param c Another.
         * @return The cross product.
         */
        private static long[] crossOf(long @NonNull [] @NonNull [] points, int a, int b, int c) {
            final long[] pa = points[a];
            final long[] pb = points[b];
            final long[] pc = points[c];
            final long ux = pb[0] - pa[0];
            final long uy = pb[1] - pa[1];
            final long uz = pb[2] - pa[2];
            final long vx = pc[0] - pa[0];
            final long vy = pc[1] - pa[1];
            final long vz = pc[2] - pa[2];
            return new long[] {uy * vz - uz * vy, uz * vx - ux * vz, ux * vy - uy * vx};
        }

        /**
         * A face's normal, its length twice the face's area.
         *
         * @param points The points.
         * @param face The face.
         * @return The normal.
         */
        static double[] normal(long @NonNull [] @NonNull [] points, int @NonNull [] face) {
            final long[] n = crossOf(points, face[0], face[1], face[2]);
            return new double[] {n[0], n[1], n[2]};
        }

        /**
         * A directed edge's key.
         *
         * @param u Where it starts.
         * @param v Where it ends.
         * @return The key.
         */
        private static long edge(int u, int v) {
            return (long) u << Integer.SIZE | (v & 0xFFFFFFFFL);
        }

        /** Static helpers only. */
        private ConvexHull() {}
    }

    /** Static helpers only. */
    private CoverageShells() {}
}
