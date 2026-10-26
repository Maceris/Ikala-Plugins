package com.ikalagaming.characters.view;

import com.ikalagaming.characters.gltf.ImportedCharacter;

import lombok.NonNull;
import org.joml.Vector3f;

import java.util.ArrayList;
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
 * <p>Parts in slots that are never empty, like the body, feet and hands, are the base. Each of
 * their triangles goes in a mask region named for the bone that moves it most. Each wearable part,
 * one in a slot that can be empty, hides the regions it covers: a region is covered when most of
 * its area has the part's surface just outside it, found by casting a short ray out from each
 * triangle. Regions are per triangle, so base vertices on the border between regions are split.
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

    /** How much of a region's area must be covered to hide it. */
    static final float COVERED_SHARE = 0.85f;

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

        // Regions by bone, in the order they come
        Map<Integer, Integer> boneRegions = new LinkedHashMap<>();
        List<ImportedCharacter.Part> split = new ArrayList<>(parts);
        List<int[]> regions = new ArrayList<>();
        List<int[]> triangleRegions = new ArrayList<>();
        for (int p = 0; p < parts.size(); ++p) {
            if (!base[p]) {
                regions.add(null);
                triangleRegions.add(null);
                continue;
            }
            ImportedCharacter.Part part = parts.get(p);
            int[] byTriangle = new int[part.indices().length / 3];
            for (int t = 0; t < byTriangle.length; ++t) {
                int bone = dominantBone(part, t);
                Integer region = boneRegions.get(bone);
                if (region == null && boneRegions.size() < MAX_REGIONS) {
                    region = boneRegions.size();
                    boneRegions.put(bone, region);
                }
                byTriangle[t] = region == null ? NEVER_HIDDEN : region;
            }
            triangleRegions.add(byTriangle);
            Split result = split(part, byTriangle);
            split.set(p, result.part());
            regions.add(result.regions());
        }

        long[] hides = new long[parts.size()];
        for (int w = 0; w < parts.size(); ++w) {
            if (!wearable[w]) {
                continue;
            }
            TriangleGrid grid = new TriangleGrid(parts.get(w));
            float[] covered = new float[MAX_REGIONS];
            float[] total = new float[MAX_REGIONS];
            for (int p = 0; p < parts.size(); ++p) {
                if (base[p]) {
                    measure(parts.get(p), triangleRegions.get(p), grid, covered, total);
                }
            }
            for (int r = 0; r < MAX_REGIONS; ++r) {
                if (total[r] > 0 && covered[r] >= COVERED_SHARE * total[r]) {
                    hides[w] |= 1L << r;
                }
            }
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
     * The bone that moves a triangle most, adding up its vertices' weights.
     *
     * @param part The part.
     * @param triangle The triangle's position.
     * @return The bone's index.
     */
    private static int dominantBone(ImportedCharacter.Part part, int triangle) {
        Map<Integer, Float> weights = new HashMap<>();
        for (int corner = 0; corner < 3; ++corner) {
            int vertex = part.indices()[triangle * 3 + corner];
            for (int i = 0; i < INFLUENCES; ++i) {
                float weight = part.weights()[vertex * INFLUENCES + i];
                if (weight > 0) {
                    weights.merge(part.joints()[vertex * INFLUENCES + i], weight, Float::sum);
                }
            }
        }
        int best = 0;
        float most = -1;
        for (Map.Entry<Integer, Float> entry : weights.entrySet()) {
            if (entry.getValue() > most) {
                most = entry.getValue();
                best = entry.getKey();
            }
        }
        return best;
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
     * Add up how much of each region's area a wearable part covers.
     *
     * @param part A base part.
     * @param byTriangle Its triangles' regions.
     * @param grid The wearable part's triangles.
     * @param covered Each region's covered area, added to.
     * @param total Each region's area, added to.
     */
    private static void measure(
            ImportedCharacter.Part part,
            int[] byTriangle,
            TriangleGrid grid,
            float[] covered,
            float[] total) {
        Vector3f a = new Vector3f();
        Vector3f b = new Vector3f();
        Vector3f c = new Vector3f();
        Vector3f normal = new Vector3f();
        Vector3f start = new Vector3f();
        Vector3f end = new Vector3f();
        for (int t = 0; t < byTriangle.length; ++t) {
            int region = byTriangle[t];
            if (region == NEVER_HIDDEN) {
                continue;
            }
            corner(part, t, 0, a);
            corner(part, t, 1, b);
            corner(part, t, 2, c);
            new Vector3f(b).sub(a).cross(new Vector3f(c).sub(a), normal);
            float area = normal.length() / 2;
            if (area <= 0) {
                continue;
            }
            normal.normalize();
            total[region] += area;
            // From just inside the triangle's middle to a little way out
            Vector3f middle = new Vector3f(a).add(b).add(c).div(3);
            middle.fma(-INSIDE, normal, start);
            middle.fma(REACH, normal, end);
            if (grid.hits(start, end)) {
                covered[region] += area;
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
