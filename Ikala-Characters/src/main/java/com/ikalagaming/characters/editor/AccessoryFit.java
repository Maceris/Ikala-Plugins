package com.ikalagaming.characters.editor;

import com.ikalagaming.characters.gltf.ImportedCharacter;

import lombok.NonNull;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

import java.util.Arrays;
import java.util.List;

/**
 * Measures how an accessory sits on a head, for lining it up: where the head shows through, how
 * far the accessory could drop before it rests on the head, and how far off center it is. Works
 * on positions at rest in model space, Y up, as parts are loaded.
 *
 * @author Ches Burks
 */
final class AccessoryFit {
    /** Floats per vertex in a loaded part: position, normal, tangent, bitangent and UV. */
    private static final int VERTEX_FLOATS = 14;

    /**
     * Triangles to test straight up against.
     *
     * @param positions Their vertices' positions, three floats each.
     * @param indices Three vertex indices per triangle.
     * @param min The positions' smallest corner.
     * @param max The positions' largest corner.
     */
    record Mesh(float[] positions, int[] indices, Vector3f min, Vector3f max) {
        /**
         * The lowest height the mesh reaches.
         *
         * @return The height.
         */
        float bottom() {
            return min.y;
        }
    }

    /**
     * Gather some loaded parts' triangles, moved.
     *
     * @param parts The loaded parts.
     * @param move Moves their positions, or null to leave them.
     * @return The triangles.
     */
    static Mesh mesh(@NonNull List<ImportedCharacter.Part> parts, Matrix4fc move) {
        int vertexCount = 0;
        int indexCount = 0;
        for (ImportedCharacter.Part part : parts) {
            vertexCount += part.vertices().length / VERTEX_FLOATS;
            indexCount += part.indices().length;
        }
        float[] positions = new float[vertexCount * 3];
        int[] indices = new int[indexCount];
        Vector3f min = new Vector3f(Float.POSITIVE_INFINITY);
        Vector3f max = new Vector3f(Float.NEGATIVE_INFINITY);
        Vector3f v = new Vector3f();
        int vertex = 0;
        int index = 0;
        for (ImportedCharacter.Part part : parts) {
            int first = vertex;
            float[] vertices = part.vertices();
            for (int base = 0; base < vertices.length; base += VERTEX_FLOATS) {
                v.set(vertices[base], vertices[base + 1], vertices[base + 2]);
                if (move != null) {
                    move.transformPosition(v);
                }
                positions[vertex * 3] = v.x;
                positions[vertex * 3 + 1] = v.y;
                positions[vertex * 3 + 2] = v.z;
                min.min(v);
                max.max(v);
                ++vertex;
            }
            for (int i : part.indices()) {
                indices[index++] = first + i;
            }
        }
        return new Mesh(positions, indices, min, max);
    }

    /**
     * How far straight up from a point the mesh is, if it is above it.
     *
     * @param mesh The mesh.
     * @param x The point's x.
     * @param y The point's height.
     * @param z The point's z.
     * @return The nearest distance up to the mesh, or NaN if nothing of it is straight above.
     */
    static float distanceUp(Mesh mesh, float x, float y, float z) {
        if (x < mesh.min.x || x > mesh.max.x || z < mesh.min.z || z > mesh.max.z) {
            return Float.NaN;
        }
        float[] p = mesh.positions;
        float best = Float.POSITIVE_INFINITY;
        for (int t = 0; t < mesh.indices.length; t += 3) {
            int a = mesh.indices[t] * 3;
            int b = mesh.indices[t + 1] * 3;
            int c = mesh.indices[t + 2] * 3;
            // Where the vertical line crosses the triangle, by barycentric weights in x and z
            float abx = p[b] - p[a];
            float abz = p[b + 2] - p[a + 2];
            float acx = p[c] - p[a];
            float acz = p[c + 2] - p[a + 2];
            float area = abx * acz - abz * acx;
            if (Math.abs(area) < 1e-12f) {
                continue;
            }
            float px = x - p[a];
            float pz = z - p[a + 2];
            float u = (px * acz - pz * acx) / area;
            float w = (abx * pz - abz * px) / area;
            if (u < 0 || w < 0 || u + w > 1) {
                continue;
            }
            float height = p[a + 1] + u * (p[b + 1] - p[a + 1]) + w * (p[c + 1] - p[a + 1]);
            if (height >= y && height - y < best) {
                best = height - y;
            }
        }
        return best == Float.POSITIVE_INFINITY ? Float.NaN : best;
    }

    /**
     * How a head sits under an accessory.
     *
     * @param showing The head's points above the accessory's bottom and under its outline that
     *     have nothing of it straight above: where the head pokes through or shows through a hole.
     *     Three floats each.
     * @param gap The smallest distance straight up from the head to the accessory, which it could
     *     drop before resting on the head, or NaN if nothing of it is over the head.
     */
    record Seat(float[] showing, float gap) {
        /**
         * How many points of the head show.
         *
         * @return The count.
         */
        int showingCount() {
            return showing.length / 3;
        }
    }

    /**
     * Measure how a head sits under an accessory.
     *
     * @param head The head.
     * @param accessory The accessory.
     * @return The measures.
     */
    static Seat seat(@NonNull Mesh head, @NonNull Mesh accessory) {
        float[] p = head.positions;
        float[] showing = new float[p.length];
        int count = 0;
        float gap = Float.POSITIVE_INFINITY;
        for (int i = 0; i < p.length; i += 3) {
            float x = p[i];
            float y = p[i + 1];
            float z = p[i + 2];
            if (x < accessory.min.x || x > accessory.max.x || z < accessory.min.z
                    || z > accessory.max.z) {
                continue;
            }
            float up = distanceUp(accessory, x, y, z);
            if (!Float.isNaN(up)) {
                gap = Math.min(gap, up);
            } else if (y > accessory.bottom()) {
                showing[count++] = x;
                showing[count++] = y;
                showing[count++] = z;
            }
        }
        return new Seat(
                Arrays.copyOf(showing, count),
                gap == Float.POSITIVE_INFINITY ? Float.NaN : gap);
    }

    /**
     * The middle of a mesh side to side, across what lies above a height.
     *
     * @param mesh The mesh.
     * @param above The height.
     * @return The middle in x, or NaN if nothing is above the height.
     */
    static float middleAbove(@NonNull Mesh mesh, float above) {
        float left = Float.POSITIVE_INFINITY;
        float right = Float.NEGATIVE_INFINITY;
        float[] p = mesh.positions;
        for (int i = 0; i < p.length; i += 3) {
            if (p[i + 1] >= above) {
                left = Math.min(left, p[i]);
                right = Math.max(right, p[i]);
            }
        }
        return left > right ? Float.NaN : (left + right) / 2;
    }

    /** Static methods only. */
    private AccessoryFit() {}
}
