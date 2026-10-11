package com.ikalagaming.graphics.bake;

import com.ikalagaming.graphics.graph.Material;
import com.ikalagaming.graphics.graph.MeshData;

import lombok.Getter;
import lombok.NonNull;
import org.joml.Vector3fc;

import java.util.Arrays;
import javax.annotation.Nullable;

/**
 * The CPU copy of a mesh that sections can be baked from, with its face groups worked out against a
 * box. Graphics knows nothing about blocks: the box is whatever the plugin registering the mesh
 * says one placement fills, like {@code [0, 1]^3}.
 *
 * <p>Each triangle that lies flat on one of the box's faces, inside its rectangle and facing out,
 * belongs to that face's group, and can be dropped when a neighbor hides that face. Every other
 * triangle belongs to no group and is always kept, so meshes that aren't box shaped, like grass,
 * come out with nothing to drop. Each face's triangles are also rasterized into a {@link
 * FaceCoverage}, which is what deciding whether one face hides another is based on.
 *
 * <p>Immutable once made, so bakes on worker threads can share it.
 */
@Getter
public final class BakeSource {

    /** How far off a face's plane a vertex can be and still be on it, as a fraction of the box. */
    public static final float PLANE_TOLERANCE = 1e-4f;

    /** How many floats each vertex takes in {@link MeshData}'s vertex data. */
    private static final int STRIDE = MeshData.VERTEX_SIZE_IN_FLOATS;

    /** Where the normal starts in a vertex. */
    private static final int NORMAL = 3;

    /** Where the tangent starts in a vertex. */
    private static final int TANGENT = 6;

    /** Where the bitangent starts in a vertex. */
    private static final int BITANGENT = 9;

    /** Where the texture coordinates start in a vertex. */
    private static final int UV = 12;

    /**
     * The box's minimum corner, 3 floats. -- GETTER -- The box's minimum corner.
     *
     * @return The corner. Don't change it.
     */
    private final float[] boxMin;

    /**
     * The length of the box's sides. -- GETTER -- The box size.
     *
     * @return The side length.
     */
    private final float boxSize;

    /**
     * How many vertices there are. -- GETTER -- The vertex count.
     *
     * @return The count.
     */
    private final int vertexCount;

    /**
     * Each vertex's position, 3 floats. -- GETTER -- The positions.
     *
     * @return The positions. Don't change them.
     */
    private final float[] positions;

    /**
     * Each vertex's normal, 3 floats. -- GETTER -- The normals.
     *
     * @return The normals. Don't change them.
     */
    private final float[] normals;

    /**
     * Each vertex's tangent, 3 floats. -- GETTER -- The tangents.
     *
     * @return The tangents. Don't change them.
     */
    private final float[] tangents;

    /**
     * Whether each vertex's bitangent points the opposite way from {@code normal × tangent}. --
     * GETTER -- The bitangent signs.
     *
     * @return The flags. Don't change them.
     */
    private final boolean[] flippedBitangents;

    /**
     * Each vertex's texture coordinates, 2 floats. -- GETTER -- The texture coordinates.
     *
     * @return The coordinates. Don't change them.
     */
    private final float[] uvs;

    /**
     * The triangles, three indices each. -- GETTER -- The indices.
     *
     * @return The indices. Don't change them.
     */
    private final int[] indices;

    /**
     * Which face each triangle lies on, as a {@link Face} ordinal, or {@link Face#NONE}. -- GETTER
     * -- The face of each triangle.
     *
     * @return The faces. Don't change them.
     */
    private final byte[] triangleFaces;

    /** The coverage of each face, by face ordinal. */
    private final FaceCoverage[] coverage;

    /**
     * The material the mesh is drawn with, or null for the default. -- GETTER -- The material.
     *
     * @return The material.
     */
    @Nullable private final Material material;

    /**
     * Whether the mesh's material is opaque, cut out or see-through. -- GETTER -- The transparency.
     *
     * @return The transparency.
     */
    private final Material.Transparency transparency;

    /**
     * Make a source from a mesh's vertex data, working out its face groups.
     *
     * @param vertexData The vertices, in {@link MeshData}'s layout.
     * @param indices The triangles.
     * @param boxMin The box's minimum corner.
     * @param boxMax The box's maximum corner. The box must be a cube, since placements are turned
     *     about its center.
     * @param material The material, or null for the default.
     * @return The source.
     * @throws IllegalArgumentException If the box isn't a cube, or the data isn't whole vertices
     *     and triangles.
     */
    public static BakeSource derive(
            float @NonNull [] vertexData,
            int @NonNull [] indices,
            @NonNull Vector3fc boxMin,
            @NonNull Vector3fc boxMax,
            @Nullable Material material) {
        return new BakeSource(vertexData, indices, boxMin, boxMax, material);
    }

    /**
     * Make a source from one of a model's meshes, with its material.
     *
     * @param mesh The mesh.
     * @param boxMin The box's minimum corner.
     * @param boxMax The box's maximum corner, making a cube.
     * @return The source.
     * @throws IllegalArgumentException If the box isn't a cube, or the mesh is animated.
     */
    public static BakeSource derive(
            @NonNull MeshData mesh, @NonNull Vector3fc boxMin, @NonNull Vector3fc boxMax) {
        if (mesh.getBoneCount() > 0) {
            throw new IllegalArgumentException("Animated meshes can't be baked");
        }
        return derive(mesh.getVertexData(), mesh.getIndices(), boxMin, boxMax, mesh.getMaterial());
    }

    /**
     * Copy the mesh and work out its face groups.
     *
     * @param vertexData The vertices.
     * @param indices The triangles.
     * @param boxMin The box's minimum corner.
     * @param boxMax The box's maximum corner.
     * @param material The material, or null.
     */
    private BakeSource(
            float @NonNull [] vertexData,
            int @NonNull [] indices,
            @NonNull Vector3fc boxMin,
            @NonNull Vector3fc boxMax,
            @Nullable Material material) {
        final float size = boxMax.x() - boxMin.x();
        if (size <= 0
                || Math.abs(boxMax.y() - boxMin.y() - size) > PLANE_TOLERANCE * size
                || Math.abs(boxMax.z() - boxMin.z() - size) > PLANE_TOLERANCE * size) {
            throw new IllegalArgumentException(
                    "The box must be a cube, it is " + boxMin + " to " + boxMax);
        }
        if (vertexData.length % STRIDE != 0 || indices.length % 3 != 0) {
            throw new IllegalArgumentException("The data isn't whole vertices and triangles");
        }
        this.boxMin = new float[] {boxMin.x(), boxMin.y(), boxMin.z()};
        this.boxSize = size;
        this.material = material;
        this.transparency =
                material == null ? Material.Transparency.OPAQUE : material.getTransparency();
        vertexCount = vertexData.length / STRIDE;
        positions = new float[vertexCount * 3];
        normals = new float[vertexCount * 3];
        tangents = new float[vertexCount * 3];
        flippedBitangents = new boolean[vertexCount];
        uvs = new float[vertexCount * 2];
        for (int vertex = 0; vertex < vertexCount; ++vertex) {
            final int from = vertex * STRIDE;
            System.arraycopy(vertexData, from, positions, vertex * 3, 3);
            System.arraycopy(vertexData, from + NORMAL, normals, vertex * 3, 3);
            System.arraycopy(vertexData, from + TANGENT, tangents, vertex * 3, 3);
            System.arraycopy(vertexData, from + UV, uvs, vertex * 2, 2);
            flippedBitangents[vertex] = bitangentFlipped(vertexData, from);
        }
        for (int index : indices) {
            if (index < 0 || index >= vertexCount) {
                throw new IllegalArgumentException("An index points past the last vertex");
            }
        }
        this.indices = indices.clone();
        triangleFaces = new byte[indices.length / 3];
        coverage = new FaceCoverage[Face.ALL.length];
        labelTriangles();
    }

    /**
     * Whether a vertex's bitangent points away from {@code normal × tangent}.
     *
     * @param data The vertex data.
     * @param from Where the vertex starts.
     * @return True if it points the other way.
     */
    private static boolean bitangentFlipped(float @NonNull [] data, int from) {
        final float nx = data[from + NORMAL];
        final float ny = data[from + NORMAL + 1];
        final float nz = data[from + NORMAL + 2];
        final float tx = data[from + TANGENT];
        final float ty = data[from + TANGENT + 1];
        final float tz = data[from + TANGENT + 2];
        final float cx = ny * tz - nz * ty;
        final float cy = nz * tx - nx * tz;
        final float cz = nx * ty - ny * tx;
        return cx * data[from + BITANGENT]
                        + cy * data[from + BITANGENT + 1]
                        + cz * data[from + BITANGENT + 2]
                < 0;
    }

    /** Put each triangle in the group of the face it lies on, and rasterize each face. */
    private void labelTriangles() {
        final int triangleCount = triangleFaces.length;
        final float[][] faceTriangles = new float[Face.ALL.length][];
        final int[] faceCounts = new int[Face.ALL.length];
        for (int triangle = 0; triangle < triangleCount; ++triangle) {
            final Face face = faceOf(triangle);
            triangleFaces[triangle] = face == null ? Face.NONE : (byte) face.ordinal();
            if (face == null) {
                continue;
            }
            final int f = face.ordinal();
            if (faceTriangles[f] == null) {
                faceTriangles[f] = new float[6 * 4];
            } else if (faceTriangles[f].length < (faceCounts[f] + 1) * 6) {
                faceTriangles[f] = Arrays.copyOf(faceTriangles[f], faceTriangles[f].length * 2);
            }
            for (int corner = 0; corner < 3; ++corner) {
                final int vertex = indices[triangle * 3 + corner];
                faceTriangles[f][faceCounts[f] * 6 + corner * 2] =
                        faceUnit(vertex, face.getUAxis());
                faceTriangles[f][faceCounts[f] * 6 + corner * 2 + 1] =
                        faceUnit(vertex, face.getVAxis());
            }
            faceCounts[f] += 1;
        }
        for (Face face : Face.ALL) {
            final int f = face.ordinal();
            coverage[f] =
                    faceCounts[f] == 0
                            ? FaceCoverage.EMPTY
                            : FaceCoverage.rasterize(faceTriangles[f], faceCounts[f]);
        }
    }

    /**
     * Where a vertex is along an axis, from 0 at the box's low side to 1 at its high side.
     *
     * @param vertex The vertex.
     * @param axis The axis.
     * @return The position in box units.
     */
    private float faceUnit(int vertex, int axis) {
        return (positions[vertex * 3 + axis] - boxMin[axis]) / boxSize;
    }

    /**
     * Find the face a triangle lies on: all three corners on the face's plane and inside its
     * rectangle, and the triangle facing out of the box.
     *
     * @param triangle The triangle.
     * @return The face, or null if it isn't on one.
     */
    @Nullable private Face faceOf(int triangle) {
        final int a = indices[triangle * 3];
        final int b = indices[triangle * 3 + 1];
        final int c = indices[triangle * 3 + 2];
        for (Face face : Face.ALL) {
            final int axis = face.getAxis();
            final float plane = face.getSign() > 0 ? 1 : 0;
            if (!onFace(a, face, plane) || !onFace(b, face, plane) || !onFace(c, face, plane)) {
                continue;
            }
            // The geometric normal along the face's axis, from the winding
            final int u = (axis + 1) % 3;
            final int v = (axis + 2) % 3;
            final float abU = positions[b * 3 + u] - positions[a * 3 + u];
            final float abV = positions[b * 3 + v] - positions[a * 3 + v];
            final float acU = positions[c * 3 + u] - positions[a * 3 + u];
            final float acV = positions[c * 3 + v] - positions[a * 3 + v];
            final float normal = abU * acV - abV * acU;
            if (normal * face.getSign() > 0) {
                return face;
            }
        }
        return null;
    }

    /**
     * Whether a vertex is on a face's plane and inside its rectangle.
     *
     * @param vertex The vertex.
     * @param face The face.
     * @param plane The face's plane along its axis, in box units, 0 or 1.
     * @return True if it is on the face.
     */
    private boolean onFace(int vertex, @NonNull Face face, float plane) {
        if (Math.abs(faceUnit(vertex, face.getAxis()) - plane) > PLANE_TOLERANCE) {
            return false;
        }
        final float u = faceUnit(vertex, face.getUAxis());
        final float v = faceUnit(vertex, face.getVAxis());
        return u >= -PLANE_TOLERANCE
                && u <= 1 + PLANE_TOLERANCE
                && v >= -PLANE_TOLERANCE
                && v <= 1 + PLANE_TOLERANCE;
    }

    /**
     * How much of a face the mesh covers, before any rotation.
     *
     * @param face The face.
     * @return Its coverage.
     */
    public FaceCoverage getCoverage(@NonNull Face face) {
        return coverage[face.ordinal()];
    }

    /**
     * How many triangles there are.
     *
     * @return The triangle count.
     */
    public int getTriangleCount() {
        return triangleFaces.length;
    }
}
