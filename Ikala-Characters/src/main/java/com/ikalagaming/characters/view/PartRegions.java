package com.ikalagaming.characters.view;

import com.ikalagaming.characters.gltf.ImportedCharacter;

import lombok.NonNull;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Which parts of an imported character's body the wearable parts cover, so the body under them can
 * be hidden instead of poking through, until parts come from definitions with authored regions.
 *
 * <p>Parts in slots that are never empty, like the body, feet and hands, are the base. Wearable
 * parts are those in slots that can be empty. A base triangle is covered by a wearable part when
 * the part's surface lies just outside all of it, found by casting short rays out from its middle
 * and near its corners. Triangles covered by the same wearable parts share a mask region, and each
 * wearable part hides the regions it covers, so what a garment leaves open, like the chest in a
 * jacket's open front or the body under a hole, stays shown, while hiding whole regions of the body
 * by bone hid those too. There can be only {@link #MAX_REGIONS} regions: past that, the smallest
 * are merged into ones covered by fewer of the same parts, which hide less, never more, and
 * triangles nothing covers are never hidden. Regions are per triangle, so base vertices on the
 * border between regions are split.
 *
 * @param character The character, with base parts' vertices split along region borders.
 * @param regions Each part's region per vertex, or null for parts that are never hidden.
 * @param hides The regions each part hides when worn, one bit per region.
 * @author Ches Burks
 */
public record PartRegions(
        @NonNull ImportedCharacter character,
        @NonNull List<int[]> regions,
        long @NonNull [] hides) {
    /** The most mask regions there can be. */
    static final int MAX_REGIONS = 64;

    /** The region for triangles that are never hidden. */
    static final int NEVER_HIDDEN = 255;

    /**
     * How far each corner ray starts from its corner toward the triangle's middle, as a share of
     * the way, so rays along a triangle's edge don't graze a garment's edge.
     */
    static final float CORNER_INSET = 0.15f;

    /** How far inside the body a covering surface may be, in meters. */
    static final float INSIDE = 0.04f;

    /** How far outside the body a covering surface may be, in meters. */
    static final float REACH = 0.12f;

    /** The size of the grid cells covering triangles are sorted into, in meters. */
    static final float CELL = 0.1f;

    /** Floats per vertex. */
    private static final int STRIDE = 14;

    /** Weights per vertex. */
    private static final int INFLUENCES = 4;

    /**
     * Work out the regions for a character.
     *
     * @param character The character.
     * @param slots Its parts sorted into slots.
     * @return The regions.
     */
    public static PartRegions of(@NonNull ImportedCharacter character, @NonNull PartSlots slots) {
        List<ImportedCharacter.Part> parts = character.parts();
        boolean[] base = new boolean[parts.size()];
        boolean[] wearable = new boolean[parts.size()];
        for (PartSlots.Slot slot : slots.slots()) {
            for (List<Integer> option : slot.options()) {
                for (int part : option) {
                    base[part] = !slot.optional();
                    wearable[part] = slot.optional();
                }
            }
        }
        return of(character, base, wearable);
    }

    /**
     * Work out the regions for a character whose parts say what they are, like parts from
     * definitions: body parts are the base, layers are worn over it.
     *
     * @param character The character.
     * @param base Whether each part is part of the body, which wearable parts hide.
     * @param wearable Whether each part is worn over the body.
     * @return The regions.
     */
    public static PartRegions of(
            @NonNull ImportedCharacter character,
            boolean @NonNull [] base,
            boolean @NonNull [] wearable) {
        List<ImportedCharacter.Part> parts = character.parts();

        // Which wearable parts cover each base triangle, and how much area each such set covers
        List<BitSet[]> coverings = new ArrayList<>();
        Map<BitSet, Float> areas = new LinkedHashMap<>();
        for (int p = 0; p < parts.size(); ++p) {
            coverings.add(
                    base[p] ? new BitSet[parts.get(p).indices().length / 3] : null);
        }
        for (int w = 0; w < parts.size(); ++w) {
            if (!wearable[w]) {
                continue;
            }
            TriangleGrid grid = new TriangleGrid(parts.get(w));
            for (int p = 0; p < parts.size(); ++p) {
                if (base[p]) {
                    cover(parts.get(p), coverings.get(p), w, grid);
                }
            }
        }
        for (int p = 0; p < parts.size(); ++p) {
            BitSet[] byTriangle = coverings.get(p);
            if (byTriangle == null) {
                continue;
            }
            for (int t = 0; t < byTriangle.length; ++t) {
                if (byTriangle[t] == null) {
                    byTriangle[t] = new BitSet();
                }
                areas.merge(byTriangle[t], area(parts.get(p), t), Float::sum);
            }
        }
        Map<BitSet, BitSet> merged = merge(areas);

        // Regions by the set of parts covering them, in the order they come
        Map<BitSet, Integer> setRegions = new LinkedHashMap<>();
        long[] hides = new long[parts.size()];
        for (BitSet covering : areas.keySet()) {
            if (covering.isEmpty() || merged.containsKey(covering)) {
                continue;
            }
            int region = setRegions.size();
            setRegions.put(covering, region);
            for (int w = covering.nextSetBit(0); w >= 0; w = covering.nextSetBit(w + 1)) {
                hides[w] |= 1L << region;
            }
        }

        List<ImportedCharacter.Part> split = new ArrayList<>(parts);
        List<int[]> regions = new ArrayList<>();
        for (int p = 0; p < parts.size(); ++p) {
            BitSet[] coveredBy = coverings.get(p);
            if (coveredBy == null) {
                regions.add(null);
                continue;
            }
            int[] byTriangle = new int[coveredBy.length];
            for (int t = 0; t < byTriangle.length; ++t) {
                BitSet covering = merged.getOrDefault(coveredBy[t], coveredBy[t]);
                byTriangle[t] = setRegions.getOrDefault(covering, NEVER_HIDDEN);
            }
            Split result = split(parts.get(p), byTriangle);
            split.set(p, result.part());
            regions.add(result.regions());
        }
        ImportedCharacter moved =
                new ImportedCharacter(
                        character.name(),
                        character.rig(),
                        character.rest(),
                        split,
                        character.clips(),
                        character.materials(),
                        character.images());
        return new PartRegions(moved, regions, hides);
    }

    /**
     * The regions to hide while some parts are worn.
     *
     * @param worn The indices of the worn parts.
     * @return One bit per region, set to hide it.
     */
    public long hidden(@NonNull Collection<Integer> worn) {
        long mask = 0;
        for (int part : worn) {
            mask |= hides[part];
        }
        return mask;
    }

    /**
     * Merge the least area's sets of covering parts into others until there are few enough to
     * give each a region. A set merges into the biggest set among the rest that holds only parts
     * it holds too, so its triangles hide while fewer parts are worn, never more, or into the
     * empty set, never hidden.
     *
     * @param areas The area each set of covering parts covers.
     * @return What each merged set merged into.
     */
    static Map<BitSet, BitSet> merge(Map<BitSet, Float> areas) {
        Map<BitSet, Float> left = new LinkedHashMap<>();
        areas.forEach(
                (covering, area) -> {
                    if (!covering.isEmpty()) {
                        left.put(covering, area);
                    }
                });
        Map<BitSet, BitSet> into = new HashMap<>();
        while (left.size() > MAX_REGIONS) {
            BitSet smallest = null;
            for (Map.Entry<BitSet, Float> entry : left.entrySet()) {
                if (smallest == null || entry.getValue() < left.get(smallest)) {
                    smallest = entry.getKey();
                }
            }
            float area = left.remove(smallest);
            BitSet best = new BitSet();
            for (BitSet other : left.keySet()) {
                BitSet outside = (BitSet) other.clone();
                outside.andNot(smallest);
                boolean bigger =
                        other.cardinality() > best.cardinality()
                                || other.cardinality() == best.cardinality()
                                        && !best.isEmpty()
                                        && left.get(other) > left.get(best);
                if (outside.isEmpty() && bigger) {
                    best = other;
                }
            }
            into.put(smallest, best);
            if (!best.isEmpty()) {
                left.merge(best, area, Float::sum);
            }
        }
        Map<BitSet, BitSet> resolved = new HashMap<>();
        for (BitSet covering : into.keySet()) {
            BitSet to = into.get(covering);
            while (into.containsKey(to)) {
                to = into.get(to);
            }
            resolved.put(covering, to);
        }
        return resolved;
    }

    /**
     * A triangle's area.
     *
     * @param part The part.
     * @param triangle The triangle's position.
     * @return Its area.
     */
    private static float area(ImportedCharacter.Part part, int triangle) {
        Vector3f a = new Vector3f();
        Vector3f b = new Vector3f();
        Vector3f c = new Vector3f();
        corner(part, triangle, 0, a);
        corner(part, triangle, 1, b);
        corner(part, triangle, 2, c);
        return b.sub(a).cross(c.sub(a)).length() / 2;
    }

    /**
     * A part with its vertices split so each triangle's corners share its region.
     *
     * @param part The part.
     * @param regions Each vertex's region.
     */
    private record Split(ImportedCharacter.Part part, int[] regions) {}

    /**
     * Split a part's vertices along region borders.
     *
     * @param part The part.
     * @param byTriangle Each triangle's region.
     * @return The split part and its vertices' regions.
     */
    private static Split split(ImportedCharacter.Part part, int[] byTriangle) {
        int[] indices = part.indices();
        int vertexCount = part.vertices().length / STRIDE;
        // A vertex's copy for each region it is used in
        Map<Long, Integer> copies = new HashMap<>();
        List<Integer> sources = new ArrayList<>();
        List<Integer> sourceRegions = new ArrayList<>();
        int[] newIndices = new int[indices.length];
        for (int i = 0; i < indices.length; ++i) {
            int vertex = indices[i];
            int region = byTriangle[i / 3];
            long key = (long) region * vertexCount + vertex;
            Integer copy = copies.get(key);
            if (copy == null) {
                copy = sources.size();
                copies.put(key, copy);
                sources.add(vertex);
                sourceRegions.add(region);
            }
            newIndices[i] = copy;
        }
        int count = sources.size();
        float[] vertices = new float[count * STRIDE];
        int[] joints = new int[count * INFLUENCES];
        float[] weights = new float[count * INFLUENCES];
        int[] regions = new int[count];
        for (int v = 0; v < count; ++v) {
            int from = sources.get(v);
            System.arraycopy(part.vertices(), from * STRIDE, vertices, v * STRIDE, STRIDE);
            System.arraycopy(part.joints(), from * INFLUENCES, joints, v * INFLUENCES, INFLUENCES);
            System.arraycopy(
                    part.weights(), from * INFLUENCES, weights, v * INFLUENCES, INFLUENCES);
            regions[v] = sourceRegions.get(v);
        }
        return new Split(
                new ImportedCharacter.Part(
                        part.name(),
                        vertices,
                        joints,
                        weights,
                        newIndices,
                        part.material(),
                        part.min(),
                        part.max()),
                regions);
    }

    /**
     * Mark the base triangles a wearable part covers: the part's surface lies just outside the
     * triangle's middle and each of its corners, inset a little (see {@link #CORNER_INSET}).
     *
     * @param part A base part.
     * @param coveredBy The wearable parts covering each of its triangles, added to.
     * @param wearable The wearable part's index.
     * @param grid The wearable part's triangles.
     */
    private static void cover(
            ImportedCharacter.Part part, BitSet[] coveredBy, int wearable, TriangleGrid grid) {
        Vector3f[] corners = {new Vector3f(), new Vector3f(), new Vector3f()};
        Vector3f normal = new Vector3f();
        Vector3f middle = new Vector3f();
        Vector3f point = new Vector3f();
        Vector3f start = new Vector3f();
        Vector3f end = new Vector3f();
        for (int t = 0; t < coveredBy.length; ++t) {
            for (int k = 0; k < 3; ++k) {
                corner(part, t, k, corners[k]);
            }
            new Vector3f(corners[1])
                    .sub(corners[0])
                    .cross(new Vector3f(corners[2]).sub(corners[0]), normal);
            if (normal.lengthSquared() <= 0) {
                continue;
            }
            normal.normalize();
            corners[0].add(corners[1], middle).add(corners[2]).div(3);
            boolean covered = true;
            // The middle, then each corner
            for (int k = -1; k < 3 && covered; ++k) {
                if (k < 0) {
                    point.set(middle);
                } else {
                    corners[k].lerp(middle, CORNER_INSET, point);
                }
                // From just inside the body to a little way out
                point.fma(-INSIDE, normal, start);
                point.fma(REACH, normal, end);
                covered = grid.hits(start, end);
            }
            if (covered) {
                if (coveredBy[t] == null) {
                    coveredBy[t] = new BitSet();
                }
                coveredBy[t].set(wearable);
            }
        }
    }

    /**
     * Read a triangle's corner.
     *
     * @param part The part.
     * @param triangle The triangle's position.
     * @param corner Which corner, 0 to 2.
     * @param out Where to put it.
     */
    private static void corner(
            ImportedCharacter.Part part, int triangle, int corner, Vector3f out) {
        int at = part.indices()[triangle * 3 + corner] * STRIDE;
        float[] v = part.vertices();
        out.set(v[at], v[at + 1], v[at + 2]);
    }

    /** A part's triangles sorted into grid cells, to test short segments against quickly. */
    private static final class TriangleGrid {
        /** The part's corners, three per triangle, nine floats each. */
        private final float[] corners;

        /** The triangles touching each cell, by cell key. */
        private final Map<Long, List<Integer>> cells = new HashMap<>();

        /**
         * Sort a part's triangles into cells.
         *
         * @param part The part.
         */
        TriangleGrid(ImportedCharacter.Part part) {
            int triangles = part.indices().length / 3;
            corners = new float[triangles * 9];
            Vector3f corner = new Vector3f();
            for (int t = 0; t < triangles; ++t) {
                Vector3f min = new Vector3f(Float.POSITIVE_INFINITY);
                Vector3f max = new Vector3f(Float.NEGATIVE_INFINITY);
                for (int k = 0; k < 3; ++k) {
                    PartRegions.corner(part, t, k, corner);
                    corners[t * 9 + k * 3] = corner.x;
                    corners[t * 9 + k * 3 + 1] = corner.y;
                    corners[t * 9 + k * 3 + 2] = corner.z;
                    min.min(corner);
                    max.max(corner);
                }
                for (int x = cell(min.x); x <= cell(max.x); ++x) {
                    for (int y = cell(min.y); y <= cell(max.y); ++y) {
                        for (int z = cell(min.z); z <= cell(max.z); ++z) {
                            cells.computeIfAbsent(key(x, y, z), k -> new ArrayList<>()).add(t);
                        }
                    }
                }
            }
        }

        /**
         * Which cell a coordinate is in.
         *
         * @param value The coordinate.
         * @return The cell's index along that axis.
         */
        private static int cell(float value) {
            return (int) Math.floor(value / CELL);
        }

        /**
         * A cell's key.
         *
         * @param x The cell's x index.
         * @param y The cell's y index.
         * @param z The cell's z index.
         * @return The key.
         */
        private static long key(int x, int y, int z) {
            return ((long) (x & 0x1FFFFF) << 42) | ((long) (y & 0x1FFFFF) << 21) | (z & 0x1FFFFF);
        }

        /**
         * Whether a segment crosses any of the triangles.
         *
         * @param start One end.
         * @param end The other end.
         * @return True if it does.
         */
        boolean hits(Vector3f start, Vector3f end) {
            Set<Integer> tested = new HashSet<>();
            Vector3f point = new Vector3f();
            float length = start.distance(end);
            int steps = Math.max(1, (int) Math.ceil(length / (CELL / 2)));
            for (int s = 0; s <= steps; ++s) {
                start.lerp(end, s / (float) steps, point);
                List<Integer> inCell = cells.get(key(cell(point.x), cell(point.y), cell(point.z)));
                if (inCell == null) {
                    continue;
                }
                for (int t : inCell) {
                    if (tested.add(t) && crosses(t, start, end)) {
                        return true;
                    }
                }
            }
            return false;
        }

        /**
         * Whether a segment crosses a triangle, from either side.
         *
         * @param t The triangle's position.
         * @param start One end.
         * @param end The other end.
         * @return True if it does.
         */
        private boolean crosses(int t, Vector3f start, Vector3f end) {
            int at = t * 9;
            float ax = corners[at];
            float ay = corners[at + 1];
            float az = corners[at + 2];
            float e1x = corners[at + 3] - ax;
            float e1y = corners[at + 4] - ay;
            float e1z = corners[at + 5] - az;
            float e2x = corners[at + 6] - ax;
            float e2y = corners[at + 7] - ay;
            float e2z = corners[at + 8] - az;
            float dx = end.x - start.x;
            float dy = end.y - start.y;
            float dz = end.z - start.z;
            // Moller-Trumbore, with the segment as a ray from start, 0 to 1 along it
            float px = dy * e2z - dz * e2y;
            float py = dz * e2x - dx * e2z;
            float pz = dx * e2y - dy * e2x;
            float det = e1x * px + e1y * py + e1z * pz;
            if (Math.abs(det) < 1e-12f) {
                return false;
            }
            float inverse = 1 / det;
            float tx = start.x - ax;
            float ty = start.y - ay;
            float tz = start.z - az;
            float u = (tx * px + ty * py + tz * pz) * inverse;
            if (u < 0 || u > 1) {
                return false;
            }
            float qx = ty * e1z - tz * e1y;
            float qy = tz * e1x - tx * e1z;
            float qz = tx * e1y - ty * e1x;
            float v = (dx * qx + dy * qy + dz * qz) * inverse;
            if (v < 0 || u + v > 1) {
                return false;
            }
            float along = (e2x * qx + e2y * qy + e2z * qz) * inverse;
            return along >= 0 && along <= 1;
        }
    }
}
