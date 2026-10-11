package com.ikalagaming.graphics.benchmark;

import com.ikalagaming.graphics.Sections;
import com.ikalagaming.graphics.bake.BakeSource;
import com.ikalagaming.graphics.bake.Face;
import com.ikalagaming.graphics.bake.Faces;
import com.ikalagaming.graphics.bake.SectionBaker;

import lombok.Getter;
import lombok.NonNull;

import java.util.Arrays;

/**
 * A deterministic made-up world for measuring the renderer, standing in for the factory plugin's
 * section corpus until there is one. It is a load model, not a game world: shaped so the number of
 * sections with something in them and the triangles they bake to match the expected load, with
 * recipes for the hard cases.
 *
 * <p>Everything is a pure function of the seed and the block position, so any section can be built
 * on its own, on any thread, and its neighbors across a border agree with it.
 */
public final class SyntheticTerrain {

    /** The size of a section along each axis, in blocks, see {@link Sections#SECTION_SIZE}. */
    public static final int SECTION = Sections.SECTION_SIZE;

    /** No block. */
    public static final byte AIR = 0;

    /** A stone cube. */
    public static final byte STONE = 1;

    /** A grass cube, on the surface. */
    public static final byte GRASS = 2;

    /** A grass slab, filling the bottom half of its cell. */
    public static final byte SLAB = 3;

    /** A plant of crossed quads, with a cut out material. */
    public static final byte PLANT = 4;

    /** A leaf cube, with a cut out material. */
    public static final byte LEAVES = 5;

    /** A glass cube, see-through. */
    public static final byte GLASS = 6;

    /** A red tinted glass cube, more opaque than clear glass. */
    public static final byte TINTED_GLASS = 7;

    /** How many block types there are, air included. */
    public static final int BLOCK_TYPES = 8;

    /** How far apart the GLASS recipe's walls are, in blocks. */
    private static final int GLASS_WALL_SPACING = 6;

    /** How tall the GLASS recipe's walls are, in blocks. */
    private static final int GLASS_WALL_HEIGHT = 10;

    /** How big the GLASS recipe's tinted panels are, in blocks along each side. */
    private static final int GLASS_PANEL_SIZE = 3;

    /** How much of the GLASS recipe's walls are tinted panels. */
    private static final double GLASS_TINTED = 0.2;

    /**
     * How much of a tree's blob of leaves is left open, so it is ragged like a real canopy rather
     * than a solid box, whose inside faces baking removes.
     */
    private static final double LEAF_GAPS = 0.3;

    /** How many layers of leaves each tree's blob has. */
    private static final int LEAF_LAYERS = 6;

    /** How far apart trees and glass panes are, in blocks: each takes one cell of this size. */
    private static final int TREE_SPACING = 9;

    /** What the world is made of. */
    public enum Recipe {
        /** Rolling hills with overhangs, caves near the surface, plants, trees and glass. */
        TERRAIN,
        /** A layer with a block in every other cell, so no face hides another. */
        CHECKERBOARD,
        /** A thick slab of rock riddled with caves, under a solid roof. */
        CAVES,
        /**
         * Walls of glass every few blocks on flat ground, with tinted panels, so many see-through
         * layers overlap at different depths.
         */
        GLASS
    }

    /**
     * The blocks of one section to compose, cells whose every face is hidden left out.
     *
     * @param count How many placements there are.
     * @param blocks The block type of each.
     * @param packedPositions The cell of each, from {@link SectionBaker#pack(int, int, int)}.
     * @param faceMasks The faces of each its neighbors hide.
     */
    public record Placements(
            int count,
            byte @NonNull [] blocks,
            int @NonNull [] packedPositions,
            byte @NonNull [] faceMasks) {}

    /**
     * Which recipe it is. -- GETTER -- The recipe.
     *
     * @return The recipe.
     */
    @Getter private final Recipe recipe;

    /** The seed every noise value is hashed with. */
    private final long seed;

    /**
     * Whether each block type's face toward a neighbor is hidden by it, by block, neighbor, then
     * face ordinal.
     */
    private final boolean[][][] hides = new boolean[BLOCK_TYPES][BLOCK_TYPES][Face.ALL.length];

    /** Whether every triangle of each block type is on a face, so hiding all six hides it all. */
    private final boolean[] boxShaped = new boolean[BLOCK_TYPES];

    /**
     * Set up a world.
     *
     * @param recipe What it is made of.
     * @param seed The seed.
     * @param sources The bake source of each block type, by block id, null for air.
     */
    public SyntheticTerrain(
            @NonNull Recipe recipe, long seed, @NonNull BakeSource @NonNull [] sources) {
        this.recipe = recipe;
        this.seed = seed;
        for (int block = 1; block < BLOCK_TYPES; ++block) {
            boolean allOnFaces = true;
            for (byte face : sources[block].getTriangleFaces()) {
                allOnFaces &= face != Face.NONE;
            }
            boxShaped[block] = allOnFaces;
            for (int neighbor = 1; neighbor < BLOCK_TYPES; ++neighbor) {
                for (Face face : Face.ALL) {
                    hides[block][neighbor][face.ordinal()] =
                            Faces.hides(sources[block], 0, face, sources[neighbor], 0);
                }
            }
        }
    }

    /**
     * The highest the ground goes, so sections above it can be skipped without looking.
     *
     * @return The highest block y that may hold anything.
     */
    public int maxY() {
        return switch (recipe) {
            case TERRAIN -> 48;
            case CHECKERBOARD -> SECTION - 1;
            case CAVES -> 34;
            case GLASS -> GLASS_WALL_HEIGHT;
        };
    }

    /**
     * The lowest y below which everything is solid and has no caves, so sections down there are
     * buried and can be skipped without looking.
     *
     * @return The lowest block y with anything to see.
     */
    public int minY() {
        return switch (recipe) {
            case TERRAIN -> -64;
            case CHECKERBOARD -> 0;
            case CAVES -> -96;
            case GLASS -> 0;
        };
    }

    /**
     * Whether everything below {@link #minY()} is solid, rather than empty.
     *
     * @return True if it is solid rock below.
     */
    public boolean solidBelow() {
        return recipe != Recipe.CHECKERBOARD;
    }

    /**
     * A good place for the camera to look around from, above the ground at the origin.
     *
     * @return The camera's y.
     */
    public double cameraY() {
        return switch (recipe) {
            case TERRAIN -> height(0, 0) + 6;
            case CHECKERBOARD -> SECTION + 8;
            case CAVES -> 42;
            case GLASS -> GLASS_WALL_HEIGHT / 2.0;
        };
    }

    /**
     * The block at a position.
     *
     * @param x The block's x.
     * @param y The block's y.
     * @param z The block's z.
     * @return The block type.
     */
    public byte block(int x, int y, int z) {
        if (y > maxY()) {
            return AIR;
        }
        if (y < minY()) {
            return solidBelow() ? STONE : AIR;
        }
        return switch (recipe) {
            case TERRAIN -> terrain(x, y, z);
            case CHECKERBOARD -> ((x + y + z) & 1) == 0 ? STONE : AIR;
            case CAVES -> caves(x, y, z);
            case GLASS -> glass(x, y, z);
        };
    }

    /**
     * The ground's height at a column of the terrain recipe.
     *
     * @param x The column's x.
     * @param z The column's z.
     * @return The height, a fractional block y.
     */
    double height(int x, int z) {
        return 22 * (fbm2(x / 96.0, z / 96.0, 3) - 0.5) * 2
                + 9 * (fbm2(x / 12.0, z / 12.0, 2) - 0.5) * 2
                + 3 * (fbm2(x / 4.0, z / 4.0, 1) - 0.5) * 2;
    }

    /**
     * The terrain recipe.
     *
     * @param x The block's x.
     * @param y The block's y.
     * @param z The block's z.
     * @return The block type.
     */
    private byte terrain(int x, int y, int z) {
        final double height = height(x, z);
        if (solid(x, y, z, height)) {
            // Caves through the few sections under the surface
            if (y < height - 4
                    && y > height - 24
                    && Math.abs(fbm3(x / 24.0, y / 16.0, z / 24.0, 2) - 0.5) < 0.07) {
                return AIR;
            }
            return solid(x, y + 1, z, height(x, z)) ? STONE : GRASS;
        }
        // On top of the ground: slabs, plants, then leaves and glass in the air
        if (solid(x, y - 1, z, height)) {
            final double fraction = height - Math.floor(height);
            if (y == (int) Math.floor(height) + 1 && fraction >= 0.5) {
                return SLAB;
            }
            if (hash(x, y, z, 1) < 0.7) {
                return PLANT;
            }
        }
        // A ragged blob of leaves on a few columns, a pane of glass on fewer
        final int cellX = Math.floorDiv(x, TREE_SPACING);
        final int cellZ = Math.floorDiv(z, TREE_SPACING);
        final double tree = hash(cellX, 0, cellZ, 2);
        if (tree < 0.9) {
            final int treeX = cellX * TREE_SPACING + 4;
            final int treeZ = cellZ * TREE_SPACING + 4;
            final int top = (int) Math.floor(height(treeX, treeZ)) + 4;
            if (Math.abs(x - treeX) <= 3
                    && Math.abs(z - treeZ) <= 3
                    && y >= top
                    && y < top + LEAF_LAYERS
                    && hash(x, y, z, 3) >= LEAF_GAPS) {
                return LEAVES;
            }
        } else if (tree > 0.95) {
            final int wallZ = cellZ * TREE_SPACING + 4;
            final int base = (int) Math.floor(height(cellX * TREE_SPACING + 4, wallZ)) + 1;
            if (z == wallZ
                    && x >= cellX * TREE_SPACING + 1
                    && x < cellX * TREE_SPACING + TREE_SPACING - 1
                    && y >= base
                    && y < base + 4) {
                return GLASS;
            }
        }
        return AIR;
    }

    /**
     * Whether the ground of the terrain recipe is solid at a block, overhangs included.
     *
     * @param x The block's x.
     * @param y The block's y.
     * @param z The block's z.
     * @param height The ground's height at the column.
     * @return True if solid.
     */
    private boolean solid(int x, int y, int z, double height) {
        final double overhang = 7 * (fbm3(x / 18.0, y / 12.0, z / 18.0, 2) - 0.5) * 2;
        return y < height + overhang * Math.max(0, 1 - Math.abs(y - height) / 10);
    }

    /**
     * The glass recipe: grass on flat ground, and every few blocks a wall of glass across x with
     * tinted panels in it.
     *
     * @param x The block's x.
     * @param y The block's y.
     * @param z The block's z.
     * @return The block type.
     */
    private byte glass(int x, int y, int z) {
        if (y <= 0) {
            return y == 0 ? GRASS : STONE;
        }
        // Halfway between, so the camera at the origin is between walls
        if (y > GLASS_WALL_HEIGHT
                || Math.floorMod(x, GLASS_WALL_SPACING) != GLASS_WALL_SPACING / 2) {
            return AIR;
        }
        final int panelY = Math.floorDiv(y - 1, GLASS_PANEL_SIZE);
        final int panelZ = Math.floorDiv(z, GLASS_PANEL_SIZE);
        return hash(x, panelY, panelZ, 4) < GLASS_TINTED ? TINTED_GLASS : GLASS;
    }

    /**
     * The caves recipe: rock with a roof of grass, riddled with caves below.
     *
     * @param x The block's x.
     * @param y The block's y.
     * @param z The block's z.
     * @return The block type.
     */
    private byte caves(int x, int y, int z) {
        if (y >= 32) {
            return y == 32 ? GRASS : AIR;
        }
        if (y < 28 && fbm3(x / 14.0, y / 10.0, z / 14.0, 3) > 0.53) {
            return AIR;
        }
        return STONE;
    }

    /**
     * Which of the blocks around a section to read: one past each side, for the neighbors of the
     * cells on its borders.
     */
    private static final int BORDER = SECTION + 2;

    /**
     * Whether a section can be skipped without looking at its blocks: entirely above the ground, or
     * entirely buried in solid rock.
     *
     * @param sectionY The section's y, in sections.
     * @return True if it certainly has nothing to draw.
     */
    public boolean certainlyEmpty(int sectionY) {
        final int bottom = sectionY * SECTION;
        final int top = bottom + SECTION - 1;
        // Its neighbors' cells reach one block further either way
        return bottom - 1 > maxY() || top + 1 < minY();
    }

    /**
     * Work out a section's placements: every cell with a block, except those whose every face is
     * hidden and that have nothing off their faces, with their neighbors' face masks.
     *
     * @param sectionX The section's x, in sections.
     * @param sectionY The section's y, in sections.
     * @param sectionZ The section's z, in sections.
     * @return The placements, with a count of 0 if there is nothing to draw.
     */
    public Placements placements(int sectionX, int sectionY, int sectionZ) {
        if (certainlyEmpty(sectionY)) {
            return new Placements(0, new byte[0], new int[0], new byte[0]);
        }
        final int baseX = sectionX * SECTION - 1;
        final int baseY = sectionY * SECTION - 1;
        final int baseZ = sectionZ * SECTION - 1;
        final byte[] grid = new byte[BORDER * BORDER * BORDER];
        for (int x = 0; x < BORDER; ++x) {
            for (int z = 0; z < BORDER; ++z) {
                for (int y = 0; y < BORDER; ++y) {
                    grid[(x * BORDER + z) * BORDER + y] = block(baseX + x, baseY + y, baseZ + z);
                }
            }
        }
        final int cells = SECTION * SECTION * SECTION;
        final byte[] blocks = new byte[cells];
        final int[] positions = new int[cells];
        final byte[] masks = new byte[cells];
        int count = 0;
        for (int x = 1; x <= SECTION; ++x) {
            for (int y = 1; y <= SECTION; ++y) {
                for (int z = 1; z <= SECTION; ++z) {
                    final byte block = grid[(x * BORDER + z) * BORDER + y];
                    if (block == AIR) {
                        continue;
                    }
                    int mask = 0;
                    for (Face face : Face.ALL) {
                        final int nx = x + (face.getAxis() == 0 ? face.getSign() : 0);
                        final int ny = y + (face.getAxis() == 1 ? face.getSign() : 0);
                        final int nz = z + (face.getAxis() == 2 ? face.getSign() : 0);
                        final byte neighbor = grid[(nx * BORDER + nz) * BORDER + ny];
                        if (neighbor != AIR && hides[block][neighbor][face.ordinal()]) {
                            mask |= face.bit();
                        }
                    }
                    final int allFaces = (1 << Face.ALL.length) - 1;
                    if (mask == allFaces && boxShaped[block]) {
                        continue;
                    }
                    blocks[count] = block;
                    positions[count] = SectionBaker.pack(x - 1, y - 1, z - 1);
                    masks[count] = (byte) mask;
                    count += 1;
                }
            }
        }
        return new Placements(
                count,
                Arrays.copyOf(blocks, count),
                Arrays.copyOf(positions, count),
                Arrays.copyOf(masks, count));
    }

    /**
     * A value from 0 to 1 for a lattice point, the same every time for the same seed.
     *
     * @param x The point's x.
     * @param y The point's y.
     * @param z The point's z.
     * @param stream Which independent stream of values to use.
     * @return The value.
     */
    double hash(long x, long y, long z, long stream) {
        long h = seed * 0x9E3779B97F4A7C15L + stream * 0xC2B2AE3D27D4EB4FL;
        h ^= x * 0xBF58476D1CE4E5B9L;
        h = Long.rotateLeft(h, 27) * 0x94D049BB133111EBL;
        h ^= y * 0xD6E8FEB86659FD93L;
        h = Long.rotateLeft(h, 31) * 0x9E3779B97F4A7C15L;
        h ^= z * 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= h >>> 33;
        return (h >>> 11) * 0x1.0p-53;
    }

    /**
     * Smooth 2D value noise, from 0 to 1.
     *
     * @param x The x.
     * @param z The z.
     * @param stream Which stream of values.
     * @return The noise.
     */
    private double noise2(double x, double z, long stream) {
        final long x0 = (long) Math.floor(x);
        final long z0 = (long) Math.floor(z);
        final double fx = smooth(x - x0);
        final double fz = smooth(z - z0);
        final double a = lerp(hash(x0, 0, z0, stream), hash(x0 + 1, 0, z0, stream), fx);
        final double b = lerp(hash(x0, 0, z0 + 1, stream), hash(x0 + 1, 0, z0 + 1, stream), fx);
        return lerp(a, b, fz);
    }

    /**
     * Smooth 3D value noise, from 0 to 1.
     *
     * @param x The x.
     * @param y The y.
     * @param z The z.
     * @param stream Which stream of values.
     * @return The noise.
     */
    private double noise3(double x, double y, double z, long stream) {
        final long x0 = (long) Math.floor(x);
        final long y0 = (long) Math.floor(y);
        final long z0 = (long) Math.floor(z);
        final double fx = smooth(x - x0);
        final double fy = smooth(y - y0);
        final double fz = smooth(z - z0);
        final double c00 = lerp(hash(x0, y0, z0, stream), hash(x0 + 1, y0, z0, stream), fx);
        final double c10 = lerp(hash(x0, y0 + 1, z0, stream), hash(x0 + 1, y0 + 1, z0, stream), fx);
        final double c01 = lerp(hash(x0, y0, z0 + 1, stream), hash(x0 + 1, y0, z0 + 1, stream), fx);
        final double c11 =
                lerp(hash(x0, y0 + 1, z0 + 1, stream), hash(x0 + 1, y0 + 1, z0 + 1, stream), fx);
        return lerp(lerp(c00, c10, fy), lerp(c01, c11, fy), fz);
    }

    /**
     * Octaves of 2D noise, from 0 to 1.
     *
     * @param x The x.
     * @param z The z.
     * @param octaves How many octaves.
     * @return The noise.
     */
    private double fbm2(double x, double z, int octaves) {
        double sum = 0;
        double amplitude = 1;
        double total = 0;
        for (int octave = 0; octave < octaves; ++octave) {
            sum += amplitude * noise2(x * (1 << octave), z * (1 << octave), 10 + octave);
            total += amplitude;
            amplitude /= 2;
        }
        return sum / total;
    }

    /**
     * Octaves of 3D noise, from 0 to 1.
     *
     * @param x The x.
     * @param y The y.
     * @param z The z.
     * @param octaves How many octaves.
     * @return The noise.
     */
    private double fbm3(double x, double y, double z, int octaves) {
        double sum = 0;
        double amplitude = 1;
        double total = 0;
        for (int octave = 0; octave < octaves; ++octave) {
            final int scale = 1 << octave;
            sum += amplitude * noise3(x * scale, y * scale, z * scale, 20 + octave);
            total += amplitude;
            amplitude /= 2;
        }
        return sum / total;
    }

    /**
     * Ease a fraction so noise has no creases at lattice points.
     *
     * @param t The fraction.
     * @return The eased fraction.
     */
    private static double smooth(double t) {
        return t * t * (3 - 2 * t);
    }

    /**
     * Interpolate.
     *
     * @param a The value at 0.
     * @param b The value at 1.
     * @param t How far between.
     * @return The value.
     */
    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }
}
