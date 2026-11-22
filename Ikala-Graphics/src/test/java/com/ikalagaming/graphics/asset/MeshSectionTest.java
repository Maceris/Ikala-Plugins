package com.ikalagaming.graphics.asset;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ikalagaming.graphics.graph.MeshData;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

/** Tests for {@link MeshSection}. */
class MeshSectionTest {

    /**
     * One triangle with every vertex value different.
     *
     * @return The mesh.
     */
    private static MeshData triangle() {
        final float[] vertices = new float[3 * MeshData.VERTEX_SIZE_IN_FLOATS];
        for (int i = 0; i < vertices.length; ++i) {
            vertices[i] = i * 0.25f - 3;
        }
        return new MeshData(
                new Vector3f(-1, 0, -1),
                new Vector3f(1, 2, 1),
                3,
                vertices,
                new int[] {0, 1, 2},
                0,
                new byte[0]);
    }

    /** A mesh comes back as it went in, through a whole container. */
    @Test
    void testRoundTrip() {
        final MeshData mesh = triangle();
        final ByteBuffer file =
                new AssetWriter()
                        .metadata(AssetMetadata.of("Test", "1"))
                        .section(
                                MeshSection.TAG,
                                MeshSection.VERSION,
                                true,
                                MeshSection.encode(mesh))
                        .toBuffer();
        final MeshData back =
                MeshSection.decode(AssetFile.read(file).section(MeshSection.TAG).orElseThrow());
        assertEquals(3, back.getVertexCount());
        assertArrayEquals(mesh.getVertexData(), back.getVertexData());
        assertArrayEquals(mesh.getIndices(), back.getIndices());
        assertEquals(mesh.getAabbMin(), back.getAabbMin());
        assertEquals(mesh.getAabbMax(), back.getAabbMax());
    }

    /** Indices past the vertices and cut short data are refused. */
    @Test
    void testBadData() {
        final MeshData good = triangle();
        final MeshData badIndex =
                new MeshData(
                        good.getAabbMin(),
                        good.getAabbMax(),
                        3,
                        good.getVertexData(),
                        new int[] {0, 1, 3},
                        0,
                        new byte[0]);
        assertThrows(
                AssetFormatException.class,
                () ->
                        MeshSection.decode(
                                new Section(
                                        MeshSection.TAG,
                                        MeshSection.VERSION,
                                        0,
                                        MeshSection.encode(badIndex))));
        final ByteBuffer cut = MeshSection.encode(good);
        cut.limit(cut.limit() - 4);
        assertThrows(
                AssetFormatException.class,
                () ->
                        MeshSection.decode(
                                new Section(MeshSection.TAG, MeshSection.VERSION, 0, cut)));
    }
}
