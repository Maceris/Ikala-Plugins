package com.ikalagaming.graphics.graph;

import lombok.NonNull;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Which voxels a triangle marks in the occupancy volume, in Java, mirroring occupancy_voxelize.comp
 * so the rules can be tested without a GPU.
 *
 * <p>The triangle is first moved a little way against its surface normal (the meshes' own normals,
 * so no winding is assumed), into what it bounds, so a face lying exactly on a plane between
 * voxels, as a block's faces do, marks only the voxel on its solid side and leaves the air beside
 * it empty. Then every voxel its bounding box touches is tested with the separating axis test for a
 * triangle against a box (Akenine-Möller 2001).
 */
public final class VoxelizeMath {

    /**
     * How far a triangle is moved against its normal before it is tested, in voxels. Small enough
     * not to reach the next voxel's far side, large enough to clear a shared plane.
     */
    public static final float INWARD = 1e-3f;

    /** The most voxels one triangle may test, so a huge triangle can't stall the pass. */
    public static final int MAX_VOXELS = 64;

    /** Half a voxel. */
    private static final float HALF = 0.5f;

    /** How far inside a voxel a triangle must reach to overlap it, rather than touch it. */
    private static final float TOUCH = 1e-4f;

    /**
     * The voxels a triangle marks.
     *
     * @param a The first corner, in voxels.
     * @param b The second corner.
     * @param c The third corner.
     * @param surfaceNormal Which way the surface faces, out of what it bounds; any length.
     * @return Each marked voxel, x, y and z.
     */
    public static List<int[]> voxels(
            @NonNull Vector3f a,
            @NonNull Vector3f b,
            @NonNull Vector3f c,
            @NonNull Vector3f surfaceNormal) {
        final Vector3f normal = new Vector3f(surfaceNormal);
        if (normal.lengthSquared() > 0) {
            normal.normalize(INWARD);
        }
        final Vector3f p = new Vector3f(a).sub(normal);
        final Vector3f q = new Vector3f(b).sub(normal);
        final Vector3f r = new Vector3f(c).sub(normal);
        final Vector3f min = new Vector3f(p).min(q).min(r);
        final Vector3f max = new Vector3f(p).max(q).max(r);
        final List<int[]> out = new ArrayList<>();
        int tested = 0;
        for (int z = (int) Math.floor(min.z); z <= (int) Math.floor(max.z); ++z) {
            for (int y = (int) Math.floor(min.y); y <= (int) Math.floor(max.y); ++y) {
                for (int x = (int) Math.floor(min.x); x <= (int) Math.floor(max.x); ++x) {
                    if (tested++ >= MAX_VOXELS) {
                        return out;
                    }
                    if (overlaps(p, q, r, new Vector3f(x + HALF, y + HALF, z + HALF))) {
                        out.add(new int[] {x, y, z});
                    }
                }
            }
        }
        return out;
    }

    /**
     * Whether a triangle overlaps a unit voxel, by the separating axis test: the box's three axes,
     * the triangle's normal, and the nine crosses of the box's axes with the triangle's edges.
     *
     * @param a The first corner.
     * @param b The second corner.
     * @param c The third corner.
     * @param center The voxel's center.
     * @return True if they overlap.
     */
    static boolean overlaps(
            @NonNull Vector3f a,
            @NonNull Vector3f b,
            @NonNull Vector3f c,
            @NonNull Vector3f center) {
        // A hair under half a voxel, so a triangle only touching a face doesn't count
        final float box = HALF - TOUCH;
        final Vector3f v0 = new Vector3f(a).sub(center);
        final Vector3f v1 = new Vector3f(b).sub(center);
        final Vector3f v2 = new Vector3f(c).sub(center);
        // The box's axes
        if (Math.min(v0.x, Math.min(v1.x, v2.x)) > box
                || Math.max(v0.x, Math.max(v1.x, v2.x)) < -box
                || Math.min(v0.y, Math.min(v1.y, v2.y)) > box
                || Math.max(v0.y, Math.max(v1.y, v2.y)) < -box
                || Math.min(v0.z, Math.min(v1.z, v2.z)) > box
                || Math.max(v0.z, Math.max(v1.z, v2.z)) < -box) {
            return false;
        }
        final Vector3f e0 = new Vector3f(v1).sub(v0);
        final Vector3f e1 = new Vector3f(v2).sub(v1);
        final Vector3f e2 = new Vector3f(v0).sub(v2);
        // The triangle's plane
        final Vector3f normal = new Vector3f(e0).cross(e1);
        final float reach = box * (Math.abs(normal.x) + Math.abs(normal.y) + Math.abs(normal.z));
        if (Math.abs(normal.dot(v0)) > reach) {
            return false;
        }
        // Each box axis crossed with each edge
        final Vector3f[] edges = {e0, e1, e2};
        final Vector3f[] boxAxes = {
            new Vector3f(1, 0, 0), new Vector3f(0, 1, 0), new Vector3f(0, 0, 1)
        };
        final Vector3f axis = new Vector3f();
        for (Vector3f edge : edges) {
            for (Vector3f boxAxis : boxAxes) {
                boxAxis.cross(edge, axis);
                final float d0 = axis.dot(v0);
                final float d1 = axis.dot(v1);
                final float d2 = axis.dot(v2);
                final float radius = box * (Math.abs(axis.x) + Math.abs(axis.y) + Math.abs(axis.z));
                if (Math.min(d0, Math.min(d1, d2)) > radius
                        || Math.max(d0, Math.max(d1, d2)) < -radius) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Static helpers only. */
    private VoxelizeMath() {}
}
