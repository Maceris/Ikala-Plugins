package com.ikalagaming.graphics.asset;

import com.ikalagaming.graphics.graph.MeshData;

import lombok.NonNull;
import org.joml.Vector3f;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * A mesh's vertices and triangles, as a {@code MESH} section: what an exporter writes for a model
 * made in the asset editor, and what a game reads back to register it. Only the shape: materials
 * and textures are chosen by whoever registers it, and bones aren't stored.
 *
 * <p>Version 1, little-endian like every payload:
 *
 * <ol>
 *   <li>A fixed record: its own size in bytes (int), the vertex count (int), the index count (int),
 *       the floats per vertex (int, {@link MeshData#VERTEX_SIZE_IN_FLOATS}), then the bounds'
 *       minimum and maximum corners (6 floats).
 *   <li>The vertex data, in {@link MeshData}'s layout.
 *   <li>The indices (ints), three per triangle.
 * </ol>
 *
 * <p>Readers skip fields past the ones they know, using the record's size, so later versions can
 * add to it.
 */
public final class MeshSection {

    /** The section's tag. */
    public static final int TAG = SectionTag.of("MESH");

    /** The version this code writes and reads. */
    public static final int VERSION = 1;

    /** The size of the fixed record this version writes. */
    static final int RECORD_SIZE = 4 * Integer.BYTES + 6 * Float.BYTES;

    /**
     * Encode a mesh.
     *
     * @param mesh The mesh. Its material and bones are left out.
     * @return The payload, little-endian, from position 0 to its limit.
     */
    public static ByteBuffer encode(@NonNull MeshData mesh) {
        final float[] vertices = mesh.getVertexData();
        final int[] indices = mesh.getIndices();
        final ByteBuffer out =
                ByteBuffer.allocate(
                                RECORD_SIZE
                                        + vertices.length * Float.BYTES
                                        + indices.length * Integer.BYTES)
                        .order(ByteOrder.LITTLE_ENDIAN);
        out.putInt(RECORD_SIZE);
        out.putInt(mesh.getVertexCount());
        out.putInt(indices.length);
        out.putInt(MeshData.VERTEX_SIZE_IN_FLOATS);
        put(out, mesh.getAabbMin());
        put(out, mesh.getAabbMax());
        out.asFloatBuffer().put(vertices);
        out.position(out.position() + vertices.length * Float.BYTES);
        out.asIntBuffer().put(indices);
        out.position(out.position() + indices.length * Integer.BYTES);
        return out.flip();
    }

    /**
     * Write a corner.
     *
     * @param out Where to.
     * @param corner The corner.
     */
    private static void put(@NonNull ByteBuffer out, @NonNull Vector3f corner) {
        out.putFloat(corner.x).putFloat(corner.y).putFloat(corner.z);
    }

    /**
     * Decode a mesh.
     *
     * @param section The section.
     * @return The mesh, with no material.
     * @throws AssetFormatException If the section isn't a mesh this code can read, or is cut short.
     */
    public static MeshData decode(@NonNull Section section) {
        if (section.tag() != TAG) {
            throw new AssetFormatException(
                    "Expected a MESH section, not " + SectionTag.toString(section.tag()));
        }
        if (section.version() != VERSION) {
            throw new AssetFormatException("Unsupported MESH version " + section.version());
        }
        final ByteBuffer in = section.data();
        if (in.remaining() < RECORD_SIZE) {
            throw new AssetFormatException("The MESH record is cut short");
        }
        final int start = in.position();
        final int recordSize = in.getInt();
        final int vertexCount = in.getInt();
        final int indexCount = in.getInt();
        final int floatsPerVertex = in.getInt();
        final Vector3f min = new Vector3f(in.getFloat(), in.getFloat(), in.getFloat());
        final Vector3f max = new Vector3f(in.getFloat(), in.getFloat(), in.getFloat());
        if (recordSize < RECORD_SIZE
                || vertexCount < 0
                || indexCount < 0
                || indexCount % 3 != 0
                || floatsPerVertex != MeshData.VERTEX_SIZE_IN_FLOATS) {
            throw new AssetFormatException(
                    "Bad MESH record: size "
                            + recordSize
                            + ", "
                            + vertexCount
                            + " vertices, "
                            + indexCount
                            + " indices, "
                            + floatsPerVertex
                            + " floats per vertex");
        }
        final long needed =
                recordSize
                        + (long) vertexCount * floatsPerVertex * Float.BYTES
                        + (long) indexCount * Integer.BYTES;
        if (in.limit() - start < needed) {
            throw new AssetFormatException("The MESH data is cut short");
        }
        in.position(start + recordSize);
        final float[] vertices = new float[vertexCount * floatsPerVertex];
        in.asFloatBuffer().get(vertices);
        in.position(in.position() + vertices.length * Float.BYTES);
        final int[] indices = new int[indexCount];
        in.asIntBuffer().get(indices);
        for (int index : indices) {
            if (index < 0 || index >= vertexCount) {
                throw new AssetFormatException("A MESH index is out of range: " + index);
            }
        }
        return new MeshData(min, max, vertexCount, vertices, indices, 0, new byte[0]);
    }

    /** Not to be made. */
    private MeshSection() {}
}
