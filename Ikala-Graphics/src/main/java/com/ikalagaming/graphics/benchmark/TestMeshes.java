package com.ikalagaming.graphics.benchmark;

import com.ikalagaming.graphics.graph.Material;
import com.ikalagaming.graphics.graph.MeshData;

import lombok.NonNull;
import org.joml.Vector3f;

/**
 * Authored-style test meshes for the gate: smooth, texture mapped, with seams, so level of detail
 * can be checked on content that isn't block faces. The texture is a checker whose colors follow
 * the texture coordinates, so a coordinate that drifts, or a triangle that spans a seam, shows as a
 * smear or a jump in color.
 */
public final class TestMeshes {

    /** The checker texture's width and height, in pixels. */
    public static final int TEXTURE_SIZE = 256;

    /** Checker squares along each side of the texture. */
    private static final int SQUARES = 8;

    /** Channels in a pixel. */
    private static final int CHANNELS = 4;

    /** The largest channel value. */
    private static final int CHANNEL_MAX = 255;

    /** How much darker the dark squares are. */
    private static final float DARK = 0.55f;

    /** The torus's major radius: its middle to the middle of its tube, in meters. */
    private static final float MAJOR = 1.2f;

    /** The torus's tube radius, in meters. */
    private static final float MINOR = 0.45f;

    /** Segments around the torus. */
    private static final int MAJOR_SEGMENTS = 48;

    /** Segments around the tube. */
    private static final int MINOR_SEGMENTS = 24;

    /** Where the torus's middle is in its cell: standing on the cell's floor, upright. */
    private static final float[] MIDDLE = {0.5f, MAJOR + MINOR, 0.5f};

    /** The surface's roughness. */
    private static final float ROUGHNESS = 0.6f;

    /** Where a vertex's normal starts in MeshData's layout. */
    private static final int NORMAL = 3;

    /** Where its tangent starts. */
    private static final int TANGENT = 6;

    /** Where its bitangent starts. */
    private static final int BITANGENT = 9;

    /** Where its texture coordinates start. */
    private static final int UV = 12;

    /** Coordinates in a direction. */
    private static final int AXES = 3;

    /** Indices in a quad's two triangles. */
    private static final int QUAD_INDICES = 6;

    /** The red and blue channels' value at a coordinate of 0. */
    private static final float CHANNEL_FLOOR = 0.2f;

    /** How much they rise to a coordinate of 1. */
    private static final float CHANNEL_SPAN = 0.8f;

    /** The green channel, flat. */
    private static final float GREEN = 0.35f;

    /**
     * An upright torus standing on its cell's floor, about 3.3 m tall, with smooth normals and
     * texture coordinates around both of its circles, so it has a seam along each.
     *
     * @param material Its material.
     * @return The mesh.
     */
    public static MeshData torus(@NonNull Material material) {
        final int columns = MAJOR_SEGMENTS + 1;
        final int rows = MINOR_SEGMENTS + 1;
        final int stride = MeshData.VERTEX_SIZE_IN_FLOATS;
        final float[] vertices = new float[columns * rows * stride];
        for (int i = 0; i < columns; ++i) {
            final double theta = 2 * Math.PI * i / MAJOR_SEGMENTS;
            final float ct = (float) Math.cos(theta);
            final float st = (float) Math.sin(theta);
            for (int j = 0; j < rows; ++j) {
                final double phi = 2 * Math.PI * j / MINOR_SEGMENTS;
                final float cp = (float) Math.cos(phi);
                final float sp = (float) Math.sin(phi);
                // In the x-y plane, so it stands upright
                final float[] normal = {ct * cp, st * cp, sp};
                final float[] tangent = {-st, ct, 0};
                final float[] bitangent = {-ct * sp, -st * sp, cp};
                final int at = (i * rows + j) * stride;
                vertices[at] = MIDDLE[0] + (MAJOR + MINOR * cp) * ct;
                vertices[at + 1] = MIDDLE[1] + (MAJOR + MINOR * cp) * st;
                vertices[at + 2] = MIDDLE[2] + MINOR * sp;
                System.arraycopy(normal, 0, vertices, at + NORMAL, AXES);
                System.arraycopy(tangent, 0, vertices, at + TANGENT, AXES);
                System.arraycopy(bitangent, 0, vertices, at + BITANGENT, AXES);
                vertices[at + UV] = (float) i / MAJOR_SEGMENTS;
                vertices[at + UV + 1] = (float) j / MINOR_SEGMENTS;
            }
        }
        final int[] indices = new int[MAJOR_SEGMENTS * MINOR_SEGMENTS * QUAD_INDICES];
        int k = 0;
        for (int i = 0; i < MAJOR_SEGMENTS; ++i) {
            for (int j = 0; j < MINOR_SEGMENTS; ++j) {
                final int a = i * rows + j;
                final int b = (i + 1) * rows + j;
                indices[k++] = a;
                indices[k++] = b;
                indices[k++] = b + 1;
                indices[k++] = a;
                indices[k++] = b + 1;
                indices[k++] = a + 1;
            }
        }
        final MeshData mesh =
                new MeshData(
                        new Vector3f(
                                MIDDLE[0] - MAJOR - MINOR,
                                MIDDLE[1] - MAJOR - MINOR,
                                MIDDLE[2] - MINOR),
                        new Vector3f(
                                MIDDLE[0] + MAJOR + MINOR,
                                MIDDLE[1] + MAJOR + MINOR,
                                MIDDLE[2] + MINOR),
                        columns * rows,
                        vertices,
                        indices,
                        0,
                        new byte[0]);
        mesh.setMaterial(material);
        return mesh;
    }

    /**
     * The torus's material: textured, not projected, so level of detail keeps its texture
     * coordinates and normals.
     *
     * @return The material, without its texture yet.
     */
    public static Material material() {
        final Material material = new Material();
        material.setRoughness(ROUGHNESS);
        material.setTransparency(Material.Transparency.OPAQUE);
        material.setTriplanar(false);
        return material;
    }

    /**
     * The checker texture, RGBA8: red follows u and blue follows v, every other square darker.
     *
     * @return The pixels, row by row.
     */
    public static byte[] checkerPixels() {
        final byte[] pixels = new byte[TEXTURE_SIZE * TEXTURE_SIZE * CHANNELS];
        final int square = TEXTURE_SIZE / SQUARES;
        for (int y = 0; y < TEXTURE_SIZE; ++y) {
            for (int x = 0; x < TEXTURE_SIZE; ++x) {
                final float u = (float) x / TEXTURE_SIZE;
                final float v = (float) y / TEXTURE_SIZE;
                final float shade = ((x / square + y / square) % 2 == 0) ? 1 : DARK;
                final int at = (y * TEXTURE_SIZE + x) * CHANNELS;
                pixels[at] =
                        (byte) Math.round(shade * (CHANNEL_FLOOR + CHANNEL_SPAN * u) * CHANNEL_MAX);
                pixels[at + 1] = (byte) Math.round(shade * GREEN * CHANNEL_MAX);
                pixels[at + 2] =
                        (byte) Math.round(shade * (CHANNEL_FLOOR + CHANNEL_SPAN * v) * CHANNEL_MAX);
                pixels[at + 3] = (byte) CHANNEL_MAX;
            }
        }
        return pixels;
    }

    /** Static helpers only. */
    private TestMeshes() {}
}
