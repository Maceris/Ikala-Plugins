package com.ikalagaming.graphics.graph;

import com.ikalagaming.graphics.bake.BakedVertex;
import com.ikalagaming.graphics.bake.SectionBaker;

import lombok.NonNull;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

/**
 * Builds a section's volume brick from its baked cut out and translucent meshes, on the CPU, see
 * {@link VolumeMath} and {@code volume.glsl}. Pure Java over the baked vertex bytes, so it runs on
 * the bake workers.
 *
 * <p>Each triangle's area is spread over the voxels it passes through by sample points spread
 * evenly over it (Roberts' R2 sequence, pulled a little toward the triangle's middle, since R2's
 * first point falls on an edge and a point on a block face's outer edge would land in the voxel
 * next to it). A face of a block, lying on a voxel boundary and facing along an axis, marks the
 * voxel it belongs to as solid on that side instead, with its optical depth and color. Blocks drop
 * the faces their neighbors hide, so a cut out block's voxels between its top and bottom faces are
 * filled, rather than a canopy being a hollow shell; a translucent block's faces only mark their
 * own voxel, since water drops its faces against the ground too, and filling below its top would
 * fill the ground.
 *
 * <p>A solid voxel stops light as a lone block would, through two of its faces, and keeps its
 * faces' coverage and material, so the walks draw it as a block's face. Other voxels keep their
 * extinction: surfaces that aren't block faces, like plants, give the area times optical depth over
 * twice the voxel's volume; translucent faces their optical depth over the volume.
 */
public final class VolumeVoxelizer {

    /** Voxels in a brick. */
    private static final int VOXELS = VolumeMath.BRICK * VolumeMath.BRICK * VolumeMath.BRICK;

    /** Coordinates in a position. */
    private static final int AXES = 3;

    /** Corners of a triangle. */
    private static final int CORNERS = 3;

    /** Values per color. */
    private static final int COLOR_VALUES = 4;

    /** How near a face's normal is to an axis to count as a block's face. */
    private static final double AXIS_ALIGNED = 0.99;

    /** How near a face's corners are to a voxel boundary to count as a block's face. */
    private static final double ON_BOUNDARY = 1.0 / 256;

    /** How many sample points a triangle gets per square meter. */
    private static final double SAMPLES_PER_SQUARE_METER = 16;

    /** The most sample points a triangle gets. */
    private static final int MAX_SAMPLES = 64;

    /** How much of the way to the triangle's middle each sample point moves. */
    private static final double INSET = 1.0 / 64;

    /** Roberts' R2 sequence's steps, from the plastic number. */
    private static final double R2_X = 0.754_877_666_246_692_7;

    private static final double R2_Y = 0.569_840_290_998_053_2;

    /** Faces a ray crosses through a lone block. */
    private static final double FACES_THROUGH = 2;

    /** The face bits of a block's top and bottom, see {@link #sideBit}. */
    private static final int TOP = 1 << 2;

    private static final int BOTTOM = 1 << 3;

    /** The face bit of a translucent block's face, on any side. */
    private static final int TRANSLUCENT_FACE = 1 << 6;

    /** Voxels along each side of a cell, the bricks' coarse grid for skipping empty space. */
    public static final int CELL = 4;

    /** Cells along each side of a brick. */
    public static final int CELLS = VolumeMath.BRICK / CELL;

    /** The surface bit of a voxel holding cut out surfaces, see {@link Brick#surfaces}. */
    public static final int CUTOUT_SURFACE = 1;

    /** The surface bit of a voxel holding translucent surfaces, see {@link Brick#surfaces}. */
    public static final int TRANSLUCENT_SURFACE = 2;

    /** Bits of surfaces per voxel. */
    public static final int SURFACE_BITS = 2;

    /** Voxels whose surface bits share an int. */
    public static final int SURFACES_PER_INT = Integer.SIZE / SURFACE_BITS;

    /** Ints of surface bits in a brick. */
    public static final int SURFACE_INTS = VOXELS / SURFACES_PER_INT;

    /** Bits in an int, for the cell masks. */
    private static final int BITS = Integer.SIZE;

    /** The largest value of a byte channel. */
    private static final double CHANNEL = 255;

    /**
     * A section's brick, ready to copy into the atlas.
     *
     * @param texels Each voxel's color and stored extinction or coverage, 4 bytes, x fastest, then
     *     y, then z, see {@link VolumeMath}.
     * @param cellMasks Which cells hold something solid (the first two ints, 64 bits), then which
     *     hold anything (the last two).
     * @param materials Each solid voxel's material index plus 1, or 0, in the same order.
     * @param surfaces Which voxels any surface touches, whatever its opacity, {@link #SURFACE_BITS}
     *     bits each in the same order, {@link #SURFACES_PER_INT} to an int, low bits first: {@link
     *     #CUTOUT_SURFACE} and {@link #TRANSLUCENT_SURFACE}. Unlike the rest, nothing is filled in,
     *     so they match what voxelizing the meshes themselves would mark.
     * @param missingMaterial Whether a material wasn't in the table, so the brick should be built
     *     again once it is.
     */
    public record Brick(
            byte @NonNull [] texels,
            int @NonNull [] cellMasks,
            short @NonNull [] materials,
            int @NonNull [] surfaces,
            boolean missingMaterial) {}

    /**
     * One mesh to voxelize.
     *
     * @param bucket The baked mesh, its positions from the section's corner.
     * @param translucent Whether its blocks are translucent, whose faces fill nothing.
     * @param blocker Whether it is the section's opaque blocks, which the brick doesn't hold but
     *     which end a column's fill: a cut out block's bottom face is hidden by an opaque block
     *     under it, and without them the voxels below it, through the opaque block, would be filled
     *     down to the brick's bottom.
     */
    public record Input(SectionBaker.@NonNull Bucket bucket, boolean translucent, boolean blocker) {

        /**
         * A mesh of cut out or translucent blocks to voxelize.
         *
         * @param bucket The baked mesh, its positions from the section's corner.
         * @param translucent Whether its blocks are translucent.
         */
        public Input(SectionBaker.@NonNull Bucket bucket, boolean translucent) {
            this(bucket, translucent, false);
        }
    }

    /**
     * What each voxel gathers: area times optical depth of surfaces that aren't block faces; the
     * weighted color and its weight; the block faces' bits, area times optical depth, area, and the
     * largest cut out face material plus 1.
     */
    private final double[] surfaceDepth = new double[VOXELS];

    private final double[] weighted = new double[VOXELS * AXES];
    private final double[] weight = new double[VOXELS];
    private final int[] faceBits = new int[VOXELS];
    private final double[] faceDepth = new double[VOXELS];
    private final double[] faceArea = new double[VOXELS];
    private final int[] faceMaterial = new int[VOXELS];

    /** Which voxels hold an opaque block, from its faces; they are never filled. */
    private final boolean[] blocked = new boolean[VOXELS];

    /** Which surfaces touch each voxel, packed as {@link Brick#surfaces}. */
    private final int[] surfaces = new int[SURFACE_INTS];

    /** The material table colors are looked up in. */
    private final VolumeMaterials table;

    /** Whether a material wasn't in the table. */
    private boolean missingMaterial;

    /**
     * Set up a voxelizer for one brick.
     *
     * @param table Each material's color and opacity.
     */
    private VolumeVoxelizer(@NonNull VolumeMaterials table) {
        this.table = table;
    }

    /**
     * Build a section's brick.
     *
     * @param inputs Its cut out and translucent meshes.
     * @param table Each material's color and opacity.
     * @return The brick, or null if the meshes have no surfaces and no material was missing.
     */
    public static Brick voxelize(@NonNull List<Input> inputs, @NonNull VolumeMaterials table) {
        final VolumeVoxelizer voxelizer = new VolumeVoxelizer(table);
        boolean any = false;
        for (Input input : inputs) {
            // Opaque blocks only end fills; they give a brick nothing to draw
            any |= voxelizer.add(input) && !input.blocker();
        }
        return any || voxelizer.missingMaterial ? voxelizer.resolve() : null;
    }

    /**
     * Spread one mesh's triangles over the voxels.
     *
     * @param input The mesh.
     * @return Whether anything was added.
     */
    private boolean add(@NonNull Input input) {
        final ByteBuffer vertices =
                input.bucket().vertices().duplicate().order(ByteOrder.LITTLE_ENDIAN);
        final int[] indices = input.bucket().indices();
        final double[][] p = new double[CORNERS][AXES];
        final float[] normal = new float[AXES];
        final double[] point = new double[AXES];
        final double[] middle = new double[AXES];
        boolean any = false;
        for (int t = 0; t + 2 < indices.length; t += CORNERS) {
            for (int k = 0; k < CORNERS; ++k) {
                final int at = indices[t + k] * BakedVertex.SIZE + BakedVertex.POSITION_OFFSET;
                for (int axis = 0; axis < AXES; ++axis) {
                    p[k][axis] =
                            (double) vertices.getShort(at + axis * Short.BYTES)
                                    / BakedVertex.STEPS_PER_UNIT;
                }
            }
            final int first = indices[t] * BakedVertex.SIZE;
            BakedVertex.fromOctahedral(
                    snorm(vertices.getShort(first + BakedVertex.NORMAL_OFFSET)),
                    snorm(vertices.getShort(first + BakedVertex.NORMAL_OFFSET + Short.BYTES)),
                    normal);
            final int material =
                    Short.toUnsignedInt(vertices.getShort(first + BakedVertex.MATERIAL_OFFSET));
            any |=
                    addTriangle(
                            p,
                            normal,
                            material,
                            input.translucent(),
                            input.blocker(),
                            point,
                            middle);
        }
        return any;
    }

    /**
     * An snorm16 value as a float, as the GPU reads it.
     *
     * @param value The stored value.
     * @return The value, from -1 to 1.
     */
    private static float snorm(short value) {
        return Math.max(-1f, value / (float) Short.MAX_VALUE);
    }

    /**
     * Spread one triangle over the voxels.
     *
     * @param p Its corners, in meters from the section's corner.
     * @param normal Its first corner's normal.
     * @param material Its material index.
     * @param translucent Whether its block is translucent.
     * @param blocker Whether it is an opaque block's, which only marks its voxels blocked.
     * @param point Scratch space for a sample point.
     * @param middle Scratch space for its middle.
     * @return Whether it touched any voxel.
     */
    private boolean addTriangle(
            double[][] p,
            float[] normal,
            int material,
            boolean translucent,
            boolean blocker,
            double[] point,
            double[] middle) {
        final double ux = p[1][0] - p[0][0];
        final double uy = p[1][1] - p[0][1];
        final double uz = p[1][2] - p[0][2];
        final double vx = p[2][0] - p[0][0];
        final double vy = p[2][1] - p[0][1];
        final double vz = p[2][2] - p[0][2];
        final double cx = uy * vz - uz * vy;
        final double cy = uz * vx - ux * vz;
        final double cz = ux * vy - uy * vx;
        final double area = 0.5 * Math.sqrt(cx * cx + cy * cy + cz * cz);
        if (area <= 0) {
            return false;
        }
        // The surface's average color and opacity, as the scene takes the texture over the color;
        // where it is marked counts even if it stops no light
        final float[] color = table.color(material);
        if (color == null && !blocker) {
            missingMaterial = true;
        }
        final double opacity =
                color == null ? 0 : Math.clamp(color[COLOR_VALUES - 1], 0, VolumeMath.MAX_OPACITY);
        final double opticalDepth = opacity <= 0 ? 0 : VolumeMath.opticalDepth(opacity);
        final int surface = translucent ? TRANSLUCENT_SURFACE : CUTOUT_SURFACE;

        int axis = -1;
        for (int a = 0; a < AXES; ++a) {
            if (Math.abs(normal[a]) > AXIS_ALIGNED) {
                axis = a;
            }
        }
        final boolean blockFace =
                axis >= 0
                        && Math.abs(p[0][axis] - Math.rint(p[0][axis])) < ON_BOUNDARY
                        && Math.abs(p[1][axis] - p[0][axis]) < ON_BOUNDARY
                        && Math.abs(p[2][axis] - p[0][axis]) < ON_BOUNDARY;
        // From a point on the face into the block it belongs to, and which side of it the face is
        double inward = 0;
        int sideBit = 0;
        if (blockFace) {
            final double outward = Math.signum(normal[axis]);
            inward = -0.5 * outward;
            sideBit = translucent ? TRANSLUCENT_FACE : sideBit(axis, outward > 0);
        }

        final int samples =
                Math.clamp((int) Math.ceil(area * SAMPLES_PER_SQUARE_METER), 1, MAX_SAMPLES);
        final double share = area / samples;
        final double depth = share * opticalDepth;
        final double colorWeight = share * opacity;
        for (int a = 0; a < AXES; ++a) {
            middle[a] = (p[0][a] + p[1][a] + p[2][a]) / CORNERS;
        }
        for (int i = 0; i < samples; ++i) {
            double s = fraction(0.5 + i * R2_X);
            double r = fraction(0.5 + i * R2_Y);
            if (s + r > 1) {
                s = 1 - s;
                r = 1 - r;
            }
            for (int a = 0; a < AXES; ++a) {
                final double onFace = p[0][a] + s * (p[1][a] - p[0][a]) + r * (p[2][a] - p[0][a]);
                point[a] = onFace + (middle[a] - onFace) * INSET + (a == axis ? inward : 0);
            }
            final int voxel = voxelAt(point);
            if (blocker) {
                blocked[voxel] = true;
                continue;
            }
            surfaces[voxel / SURFACES_PER_INT] |=
                    surface << (voxel % SURFACES_PER_INT * SURFACE_BITS);
            if (opacity <= 0) {
                continue;
            }
            if (blockFace) {
                faceBits[voxel] |= sideBit;
                faceDepth[voxel] += depth;
                faceArea[voxel] += share;
                if (!translucent) {
                    faceMaterial[voxel] = Math.max(faceMaterial[voxel], material + 1);
                }
            } else {
                surfaceDepth[voxel] += depth;
            }
            for (int a = 0; a < AXES; ++a) {
                weighted[voxel * AXES + a] += colorWeight * color[a];
            }
            weight[voxel] += colorWeight;
        }
        return true;
    }

    /**
     * A block face's bit: two per axis, facing up the axis first.
     *
     * @param axis The axis it faces along.
     * @param up Whether it faces up the axis.
     * @return The bit.
     */
    private static int sideBit(int axis, boolean up) {
        return 1 << (axis * 2 + (up ? 0 : 1));
    }

    /**
     * The fractional part of a value.
     *
     * @param value The value.
     * @return Its fraction, from 0 to 1.
     */
    private static double fraction(double value) {
        return value - Math.floor(value);
    }

    /**
     * The voxel a point is in, kept inside the brick.
     *
     * @param point The point, in meters from the section's corner.
     * @return The voxel's index.
     */
    private static int voxelAt(double[] point) {
        final int last = VolumeMath.BRICK - 1;
        final int x = Math.clamp((int) Math.floor(point[0]), 0, last);
        final int y = Math.clamp((int) Math.floor(point[1]), 0, last);
        final int z = Math.clamp((int) Math.floor(point[2]), 0, last);
        return index(x, y, z);
    }

    /**
     * A voxel's index, x fastest, then y, then z.
     *
     * @param x Its x.
     * @param y Its y.
     * @param z Its z.
     * @return The index.
     */
    private static int index(int x, int y, int z) {
        return x + VolumeMath.BRICK * (y + VolumeMath.BRICK * z);
    }

    /**
     * Turn what the voxels gathered into the brick.
     *
     * @return The brick.
     */
    private Brick resolve() {
        final byte[] texels = new byte[VOXELS * COLOR_VALUES];
        final int[] masks = new int[COLOR_VALUES];
        final short[] materials = new short[VOXELS];
        final int brick = VolumeMath.BRICK;
        for (int z = 0; z < brick; ++z) {
            for (int y = 0; y < brick; ++y) {
                for (int x = 0; x < brick; ++x) {
                    resolveVoxel(x, y, z, texels, masks, materials);
                }
            }
        }
        return new Brick(texels, masks, materials, surfaces.clone(), missingMaterial);
    }

    /**
     * Resolve one voxel: whether it is solid, its extinction or coverage, its color and material,
     * and its cell's bits.
     *
     * @param x Its x.
     * @param y Its y.
     * @param z Its z.
     * @param texels The brick's texels, written.
     * @param masks The brick's cell masks, written.
     * @param materials The brick's materials, written.
     */
    private void resolveVoxel(int x, int y, int z, byte[] texels, int[] masks, short[] materials) {
        final int voxel = index(x, y, z);
        final int ownBits = faceBits[voxel];
        boolean solid = (ownBits & ~TRANSLUCENT_FACE) != 0;
        // The voxel whose faces make this one solid, if any
        int source = voxel;
        // A column's fill ends at an opaque block, and never fills one
        if (!solid && ownBits == 0 && !blocked[voxel]) {
            boolean decided = false;
            for (int up = y + 1; up < VolumeMath.BRICK && !decided; ++up) {
                final int at = index(x, up, z);
                final int bits = faceBits[at] & (TOP | BOTTOM);
                if (blocked[at]) {
                    decided = true;
                } else if (bits != 0) {
                    decided = true;
                    solid = (bits & BOTTOM) == 0;
                    source = at;
                }
            }
            for (int down = y - 1; down >= 0 && !decided; --down) {
                final int at = index(x, down, z);
                final int bits = faceBits[at] & (TOP | BOTTOM);
                if (blocked[at]) {
                    decided = true;
                } else if (bits != 0) {
                    decided = true;
                    solid = (bits & TOP) == 0;
                    source = at;
                }
            }
        }

        double extinction = surfaceDepth[voxel] / (2 * VolumeMath.VOXEL_SIZE);
        if (!solid && ownBits != 0) {
            extinction += faceDepth[voxel] / VolumeMath.VOXEL_SIZE;
        }
        double coverage = 0;
        if (solid && faceArea[source] > 0) {
            final double averageDepth = faceDepth[source] / faceArea[source];
            extinction += FACES_THROUGH * averageDepth / VolumeMath.VOXEL_SIZE;
            coverage = 1 - Math.exp(-averageDepth);
        }
        final int colorFrom = weight[voxel] > 0 ? voxel : source;
        final int texel = voxel * COLOR_VALUES;
        for (int a = 0; a < AXES; ++a) {
            final double channel =
                    weight[colorFrom] > 0 ? weighted[colorFrom * AXES + a] / weight[colorFrom] : 0;
            texels[texel + a] = (byte) Math.round(Math.clamp(channel, 0, 1) * CHANNEL);
        }
        texels[texel + AXES] =
                (byte) (solid ? VolumeMath.encodeSolid(coverage) : VolumeMath.encode(extinction));
        // A solid voxel shows the material of its own faces, or of the face that showed it was
        // inside
        if (solid) {
            materials[voxel] = (short) faceMaterial[source];
        }
        if (solid || extinction > 0) {
            final int cell = x / CELL + CELLS * (y / CELL + CELLS * (z / CELL));
            final int bit = 1 << (cell % BITS);
            if (solid) {
                masks[cell / BITS] |= bit;
            }
            masks[2 + cell / BITS] |= bit;
        }
    }
}
