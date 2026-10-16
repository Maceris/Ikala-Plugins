package com.ikalagaming.converter.scene;

import com.ikalagaming.converter.scene.ImportedScene.Node;
import com.ikalagaming.graphics.graph.MeshData;

import lombok.NonNull;
import org.joml.Matrix4d;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.ArrayDeque;
import java.util.Deque;
import javax.annotation.Nullable;

/**
 * Finds what a ray from the camera hits in an imported scene, for picking with the mouse. Each
 * node's meshes are tested in their own space: the ray against the mesh's bounds first, then its
 * triangles, both faces. Brute force past the bounds, which is quick enough for a click even on a
 * few million triangles.
 */
public final class ScenePicker {
    /** Floats per vertex in mesh data. */
    private static final int STRIDE = MeshData.VERTEX_SIZE_IN_FLOATS;

    /** Below this, a ray is taken as parallel to a triangle. */
    private static final double PARALLEL = 1e-12;

    /**
     * What a ray hit.
     *
     * @param node The node whose mesh it hit.
     * @param sceneMesh The scene's index of the mesh.
     * @param distance How far along the ray, in world units.
     */
    public record Hit(@NonNull Node node, int sceneMesh, double distance) {}

    /** Not to be made. */
    private ScenePicker() {}

    /**
     * Find the nearest mesh a ray hits.
     *
     * @param scene The scene.
     * @param origin Where the ray starts, in the world.
     * @param direction Which way it goes, in the world; need not be normalized.
     * @return The nearest hit, or null if it hits nothing.
     */
    public static @Nullable Hit pick(
            @NonNull ImportedScene scene, @NonNull Vector3dc origin, @NonNull Vector3dc direction) {
        final Vector3d worldDirection = new Vector3d(direction).normalize();
        final Matrix4d inverse = new Matrix4d();
        final Vector3d localOrigin = new Vector3d();
        final Vector3d localDirection = new Vector3d();
        final Vector3d point = new Vector3d();
        Hit nearest = null;
        final Deque<Node> open = new ArrayDeque<>();
        open.push(scene.root());
        while (!open.isEmpty()) {
            final Node node = open.pop();
            node.children().forEach(open::push);
            if (node.meshes().length == 0) {
                continue;
            }
            node.world().invert(inverse);
            inverse.transformPosition(origin, localOrigin);
            inverse.transformDirection(worldDirection, localDirection);
            for (int index : node.meshes()) {
                final MeshData mesh = scene.meshes().get(index).data();
                // Local distances differ from world ones by the node's scale, so each mesh is
                // searched whole and compared in the world afterward
                if (!hitsBox(mesh, localOrigin, localDirection, Double.POSITIVE_INFINITY)) {
                    continue;
                }
                final double t =
                        nearestTriangle(
                                mesh, localOrigin, localDirection, Double.POSITIVE_INFINITY);
                if (Double.isInfinite(t)) {
                    continue;
                }
                // Back to the world, where scales between nodes differ
                localDirection.mul(t, point).add(localOrigin);
                node.world().transformPosition(point);
                final double distance = point.distance(origin);
                if (nearest == null || distance < nearest.distance()) {
                    nearest = new Hit(node, index, distance);
                }
            }
        }
        return nearest;
    }

    /**
     * Whether a ray passes through a mesh's bounds within a distance (the slab test).
     *
     * @param mesh The mesh.
     * @param origin The ray's start.
     * @param direction The ray's direction.
     * @param limit How far along to look.
     * @return True if it does.
     */
    static boolean hitsBox(MeshData mesh, Vector3dc origin, Vector3dc direction, double limit) {
        double near = 0;
        double far = limit;
        for (int axis = 0; axis < 3; ++axis) {
            final double o = origin.get(axis);
            final double d = direction.get(axis);
            final double min = mesh.getAabbMin().get(axis);
            final double max = mesh.getAabbMax().get(axis);
            if (Math.abs(d) < PARALLEL) {
                if (o < min || o > max) {
                    return false;
                }
                continue;
            }
            double t0 = (min - o) / d;
            double t1 = (max - o) / d;
            if (t0 > t1) {
                final double swap = t0;
                t0 = t1;
                t1 = swap;
            }
            near = Math.max(near, t0);
            far = Math.min(far, t1);
            if (near > far) {
                return false;
            }
        }
        return true;
    }

    /**
     * The nearest triangle a ray hits, either face (Moller and Trumbore).
     *
     * @param mesh The mesh.
     * @param origin The ray's start.
     * @param direction The ray's direction.
     * @param limit How far along to look.
     * @return How far along the ray, or infinity if it hits none.
     */
    static double nearestTriangle(
            MeshData mesh, Vector3dc origin, Vector3dc direction, double limit) {
        final float[] v = mesh.getVertexData();
        final int[] indices = mesh.getIndices();
        double best = limit;
        final double ox = origin.x();
        final double oy = origin.y();
        final double oz = origin.z();
        final double dx = direction.x();
        final double dy = direction.y();
        final double dz = direction.z();
        for (int i = 0; i + 2 < indices.length; i += 3) {
            final int a = indices[i] * STRIDE;
            final int b = indices[i + 1] * STRIDE;
            final int c = indices[i + 2] * STRIDE;
            final double e1x = v[b] - v[a];
            final double e1y = v[b + 1] - v[a + 1];
            final double e1z = v[b + 2] - v[a + 2];
            final double e2x = v[c] - v[a];
            final double e2y = v[c + 1] - v[a + 1];
            final double e2z = v[c + 2] - v[a + 2];
            final double px = dy * e2z - dz * e2y;
            final double py = dz * e2x - dx * e2z;
            final double pz = dx * e2y - dy * e2x;
            final double det = e1x * px + e1y * py + e1z * pz;
            if (Math.abs(det) < PARALLEL) {
                continue;
            }
            final double inv = 1 / det;
            final double tx = ox - v[a];
            final double ty = oy - v[a + 1];
            final double tz = oz - v[a + 2];
            final double u = (tx * px + ty * py + tz * pz) * inv;
            if (u < 0 || u > 1) {
                continue;
            }
            final double qx = ty * e1z - tz * e1y;
            final double qy = tz * e1x - tx * e1z;
            final double qz = tx * e1y - ty * e1x;
            final double w = (dx * qx + dy * qy + dz * qz) * inv;
            if (w < 0 || u + w > 1) {
                continue;
            }
            final double t = (e2x * qx + e2y * qy + e2z * qz) * inv;
            if (t > 0 && t < best) {
                best = t;
            }
        }
        return best == limit ? Double.POSITIVE_INFINITY : best;
    }
}
