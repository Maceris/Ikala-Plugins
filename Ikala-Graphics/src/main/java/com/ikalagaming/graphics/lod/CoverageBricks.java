package com.ikalagaming.graphics.lod;

import lombok.NonNull;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A {@link CoverageVolume} laid out for the GPU: dense bricks of {@value #SIDE} cubed voxels
 * covering its sparse voxels, each drawn as a box whose fragments walk it, see {@code
 * scene_volume.frag}. A brick is a header then its voxels, x fastest:
 *
 * <pre>
 * header  float voxel size, three unused floats
 * voxel   uint density (half float, low 16 bits), roughness (byte), metalness (byte)
 *         uint color, sRGB encoded, red in the low byte
 *         uint SGGX sigma x, y, z (bytes, 0 to 1), r xy (byte, -1 to 1)
 *         uint SGGX r xz, r yz (bytes, -1 to 1)
 * </pre>
 *
 * <p>The SGGX matrix is kept as Heitz et al.'s compact form (§4.4): the square roots of its
 * diagonal and its correlations, scaled so the largest of the diagonal is 1, with the density
 * scaled the other way so the extinction is the same. An empty voxel is all zero.
 */
public final class CoverageBricks {

    /** Voxels along each side of a brick. */
    public static final int SIDE = 8;

    /** Voxels in a brick. */
    public static final int VOXELS = SIDE * SIDE * SIDE;

    /** Bytes a voxel takes. */
    public static final int VOXEL_BYTES = 4 * Integer.BYTES;

    /** Bytes the header takes. */
    public static final int HEADER_BYTES = 4 * Integer.BYTES;

    /** Bytes a brick takes. */
    public static final int BRICK_BYTES = HEADER_BYTES + VOXELS * VOXEL_BYTES;

    /** Coordinates in a position. */
    private static final int AXES = 3;

    /** Corners of a box. */
    private static final int BOX_CORNERS = 8;

    /** A box's triangles, by corner, facing out; corner bits are x, y, z from the lowest. */
    private static final int[] BOX_TRIANGLES = {
        0, 2, 3, 0, 3, 1, // -z
        4, 5, 7, 4, 7, 6, // +z
        0, 1, 5, 0, 5, 4, // -y
        2, 6, 7, 2, 7, 3, // +y
        0, 4, 6, 0, 6, 2, // -x
        1, 3, 7, 1, 7, 5 // +x
    };

    /** The largest byte. */
    private static final int BYTE_MAX = 255;

    /**
     * One brick.
     *
     * @param x Its lowest voxel's x on the volume's grid, a multiple of {@link #SIDE}.
     * @param y Its lowest voxel's y.
     * @param z Its lowest voxel's z.
     * @param data Its {@link #BRICK_BYTES} bytes, little endian.
     */
    public record Brick(int x, int y, int z, @NonNull ByteBuffer data) {}

    /**
     * Lay a volume out in bricks.
     *
     * @param volume The volume.
     * @return Its bricks, in the order their voxels first come.
     */
    public static List<Brick> of(@NonNull CoverageVolume volume) {
        final Map<Long, Brick> bricks = new LinkedHashMap<>();
        for (int v = 0; v < volume.size(); ++v) {
            final long key = volume.keyOf(v);
            final int x = CoverageVolume.coordinate(key, 0);
            final int y = CoverageVolume.coordinate(key, 1);
            final int z = CoverageVolume.coordinate(key, 2);
            final int bx = x / SIDE * SIDE;
            final int by = y / SIDE * SIDE;
            final int bz = z / SIDE * SIDE;
            final Brick brick =
                    bricks.computeIfAbsent(
                            CoverageVolume.key(bx, by, bz),
                            k -> {
                                final ByteBuffer data =
                                        ByteBuffer.allocate(BRICK_BYTES)
                                                .order(ByteOrder.LITTLE_ENDIAN);
                                data.putFloat(0, (float) volume.voxelSize());
                                return new Brick(bx, by, bz, data);
                            });
            final int local = (x - bx) + SIDE * ((y - by) + SIDE * (z - bz));
            putVoxel(volume, v, brick.data(), HEADER_BYTES + local * VOXEL_BYTES);
        }
        return new ArrayList<>(bricks.values());
    }

    /**
     * Pack a voxel.
     *
     * @param volume The volume.
     * @param v The voxel.
     * @param out Where it goes.
     * @param at The byte it starts at.
     */
    private static void putVoxel(
            @NonNull CoverageVolume volume, int v, @NonNull ByteBuffer out, int at) {
        double largest = 0;
        for (int axis = 0; axis < AXES; ++axis) {
            largest = Math.max(largest, volume.sggx(v, axis));
        }
        if (largest <= 0 || volume.density(v) <= 0) {
            return;
        }
        final double[] sigma = new double[AXES];
        for (int axis = 0; axis < AXES; ++axis) {
            sigma[axis] = Math.sqrt(Math.max(0, volume.sggx(v, axis) / largest));
        }
        // xy, xz, yz, each over its two sigmas
        final double[] r = new double[AXES];
        final int[][] pairs = {{0, 1}, {0, 2}, {1, 2}};
        for (int k = 0; k < AXES; ++k) {
            final double scale = sigma[pairs[k][0]] * sigma[pairs[k][1]];
            r[k] = scale <= 0 ? 0 : volume.sggx(v, AXES + k) / largest / scale;
        }
        // sqrt(w^T S w) scales with the square root of S's scale
        final float density = (float) (volume.density(v) * Math.sqrt(largest));
        out.putInt(
                at,
                (Float.floatToFloat16(density) & 0xFFFF)
                        | unorm(volume.roughness(v)) << Short.SIZE
                        | unorm(volume.metallic(v)) << (Short.SIZE + Byte.SIZE));
        out.putInt(
                at + Integer.BYTES,
                unorm(encodeSrgb(volume.color(v, 0)))
                        | unorm(encodeSrgb(volume.color(v, 1))) << Byte.SIZE
                        | unorm(encodeSrgb(volume.color(v, 2))) << Short.SIZE);
        out.putInt(
                at + 2 * Integer.BYTES,
                unorm(sigma[0])
                        | unorm(sigma[1]) << Byte.SIZE
                        | unorm(sigma[2]) << Short.SIZE
                        | snorm(r[0]) << (Short.SIZE + Byte.SIZE));
        out.putInt(at + 3 * Integer.BYTES, snorm(r[1]) | snorm(r[2]) << Byte.SIZE);
    }

    /**
     * A value from 0 to 1 as a byte.
     *
     * @param value The value.
     * @return The byte.
     */
    private static int unorm(double value) {
        return (int) Math.round(Math.clamp(value, 0, 1) * BYTE_MAX);
    }

    /**
     * A value from -1 to 1 as a byte, -1 at 0 and 1 at 255.
     *
     * @param value The value.
     * @return The byte.
     */
    private static int snorm(double value) {
        return (int) Math.round((Math.clamp(value, -1, 1) + 1) / 2 * BYTE_MAX);
    }

    /**
     * Encode a linear value as sRGB.
     *
     * @param linear The value.
     * @return The encoded value.
     */
    private static double encodeSrgb(double linear) {
        return linear <= 0.003_130_8 ? 12.92 * linear : 1.055 * Math.pow(linear, 1 / 2.4) - 0.055;
    }

    /**
     * A volume's bricks as boxes to draw, each brick's corners carrying its slot in the brick table
     * and which corner they are, see {@link LodVertex#putVolume}.
     *
     * @param volume The volume.
     * @param bricks Its bricks.
     * @param slots Each brick's slot in the brick table.
     * @return The boxes.
     */
    public static ClusterMeshes.Mesh mesh(
            @NonNull CoverageVolume volume, @NonNull List<Brick> bricks, int @NonNull [] slots) {
        final ByteBuffer vertices =
                ByteBuffer.allocate(bricks.size() * BOX_CORNERS * LodVertex.SIZE)
                        .order(ByteOrder.LITTLE_ENDIAN);
        final int[] indices = new int[bricks.size() * BOX_TRIANGLES.length];
        final double[] origin = volume.origin();
        final double span = volume.voxelSize() * SIDE;
        final float[] min = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
        final float[] max = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        final int[] grid = new int[AXES];
        for (int b = 0; b < bricks.size(); ++b) {
            final Brick brick = bricks.get(b);
            final int[] low = {brick.x(), brick.y(), brick.z()};
            for (int corner = 0; corner < BOX_CORNERS; ++corner) {
                for (int axis = 0; axis < AXES; ++axis) {
                    final double at =
                            origin[axis]
                                    + low[axis] * volume.voxelSize()
                                    + ((corner >> axis) & 1) * span;
                    grid[axis] = LodVertex.grid((float) at);
                    min[axis] = Math.min(min[axis], (float) at);
                    max[axis] = Math.max(max[axis], (float) at);
                }
                LodVertex.putVolume(
                        vertices,
                        b * BOX_CORNERS + corner,
                        grid,
                        (float) volume.voxelSize(),
                        slots[b],
                        corner);
            }
            for (int i = 0; i < BOX_TRIANGLES.length; ++i) {
                indices[b * BOX_TRIANGLES.length + i] = b * BOX_CORNERS + BOX_TRIANGLES[i];
            }
        }
        if (bricks.isEmpty()) {
            Arrays.fill(min, 0);
            Arrays.fill(max, 0);
        }
        return new ClusterMeshes.Mesh(vertices, bricks.size() * BOX_CORNERS, indices, min, max);
    }

    /** Static helpers only. */
    private CoverageBricks() {}
}
