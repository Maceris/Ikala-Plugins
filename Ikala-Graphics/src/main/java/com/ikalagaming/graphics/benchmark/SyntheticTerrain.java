package com.ikalagaming.graphics.benchmark;

import com.ikalagaming.graphics.Sections;
import com.ikalagaming.graphics.bake.BakeSource;
import com.ikalagaming.graphics.bake.Face;
import com.ikalagaming.graphics.bake.Faces;
import com.ikalagaming.graphics.bake.SectionBaker;

import lombok.Getter;
import lombok.NonNull;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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

    /** A dirt cube, under grass. */
    public static final byte DIRT = 8;

    /** A cube of wooden planks. */
    public static final byte PLANKS = 9;

    /** Wooden stairs, climbing toward +z. */
    public static final byte STAIRS = 10;

    /** A water cube, see-through. */
    public static final byte WATER = 11;

    /** How many block types there are, air included. */
    public static final int BLOCK_TYPES = 12;

    /** How many blocks of dirt lie under the grass. */
    private static final int DIRT_DEPTH = 3;

    /** The terrain recipe's water level: open cells at or below it hold water. */
    private static final int WATER_LEVEL = -8;

    /** How far a hut's walls reach from its middle, in blocks. */
    private static final int HUT_RADIUS = 2;

    /** How tall a hut's walls are, in blocks. */
    private static final int HUT_HEIGHT = 3;

    /** How tall a hut's door is, in blocks. */
    private static final int DOOR_HEIGHT = 2;

    /** How far apart the huts recipe's huts are, in blocks: each stands in a cell of this size. */
    private static final int HUT_GRID = 12;

    /** Tree cells at or above this hash get a hut instead, below {@link #GLASS_CELLS}. */
    private static final double HUT_CELLS = 0.9;

    /** Tree cells above this hash get a pane of glass. */
    private static final double GLASS_CELLS = 0.95;

    /** Where a tree's middle is in its cell, along x and z. */
    private static final int TREE_MIDDLE = 4;

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
        /**
         * Rolling hills of grass over dirt and stone, with overhangs, caves near the surface,
         * lakes, plants, trees, plank huts and glass.
         */
        TERRAIN,
        /** A layer with a block in every other cell, so no face hides another. */
        CHECKERBOARD,
        /** A thick slab of rock riddled with caves, under a solid roof. */
        CAVES,
        /**
         * Walls of glass every few blocks on flat ground, with tinted panels, so many see-through
         * layers overlap at different depths.
         */
        GLASS,
        /**
         * Plank huts in a grid on flat grass, with nothing else around them, to see what gets
         * through their walls from a light inside.
         */
        HUTS,
        /**
         * The terrain recipe's hills, overhangs, caves, lakes, slabs and plants, with no trees,
         * huts or glass, so the land itself can be seen from afar.
         */
        HILLS
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

    /** Blocks changed from what the recipe makes, by {@link #editKey}. */
    private final Map<Long, Byte> edits = new ConcurrentHashMap<>();

    /** Bits each coordinate gets in an edit's key. */
    private static final int KEY_BITS = 21;

    /** The mask of one coordinate in an edit's key. */
    private static final long KEY_MASK = (1L << KEY_BITS) - 1;

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
            case TERRAIN, HILLS -> 48;
            case CHECKERBOARD -> SECTION - 1;
            case CAVES -> 34;
            case GLASS -> GLASS_WALL_HEIGHT;
            case HUTS -> HUT_HEIGHT;
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
            case TERRAIN, HILLS -> -64;
            case CHECKERBOARD -> 0;
            case CAVES -> -96;
            case GLASS, HUTS -> 0;
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
            case TERRAIN, HILLS -> height(0, 0) + 6;
            case CHECKERBOARD -> SECTION + 8;
            case CAVES -> 42;
            case GLASS -> GLASS_WALL_HEIGHT / 2.0;
            case HUTS -> HUT_HEIGHT * 2.0;
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
        if (!edits.isEmpty()) {
            final Byte edited = edits.get(editKey(x, y, z));
            if (edited != null) {
                return edited;
            }
        }
        if (y > maxY()) {
            return AIR;
        }
        if (y < minY()) {
            return solidBelow() ? STONE : AIR;
        }
        return switch (recipe) {
            case TERRAIN, HILLS -> terrain(x, y, z);
            case CHECKERBOARD -> ((x + y + z) & 1) == 0 ? STONE : AIR;
            case CAVES -> caves(x, y, z);
            case GLASS -> glass(x, y, z);
            case HUTS -> huts(x, y, z);
        };
    }

    /**
     * Change a block, as an edit would. Placements worked out afterward include it. Safe from any
     * thread. Edits only show between {@link #minY()} and {@link #maxY()}, where sections are
     * looked at.
     *
     * @param x The block's x.
     * @param y The block's y.
     * @param z The block's z.
     * @param block The block type, {@link #AIR} to dig it out.
     */
    public void setBlock(int x, int y, int z, byte block) {
        edits.put(editKey(x, y, z), block);
    }

    /**
     * Whether a block is ground to dig: one that is drawn opaque and fills or floors its cell, not
     * a plant, leaves, glass or water.
     *
     * @param block The block.
     * @return True if it is ground.
     */
    public static boolean isGround(byte block) {
        return block == STONE
                || block == GRASS
                || block == DIRT
                || block == PLANKS
                || block == SLAB
                || block == STAIRS;
    }

    /**
     * The highest ground block of a column, see {@link #isGround}, whatever stands above it.
     *
     * @param x The column's x.
     * @param z The column's z.
     * @return Its y, or {@link #minY()} - 1 if the column has no ground that far down.
     */
    public int ground(int x, int z) {
        int y = maxY();
        while (y >= minY() && !isGround(block(x, y, z))) {
            --y;
        }
        return y;
    }

    /**
     * Whether a column's ground is open to the sky: it has ground, and nothing but air and plants
     * stand above it.
     *
     * @param x The column's x.
     * @param z The column's z.
     * @return True if it is open.
     */
    public boolean isOpen(int x, int z) {
        final int ground = ground(x, z);
        if (ground < minY()) {
            return false;
        }
        for (int y = ground + 1; y <= maxY(); ++y) {
            final byte block = block(x, y, z);
            if (block != AIR && block != PLANT) {
                return false;
            }
        }
        return true;
    }

    /**
     * The highest block of a column that isn't air.
     *
     * @param x The column's x.
     * @param z The column's z.
     * @return Its y, or {@link #minY()} - 1 if the column is empty that far down.
     */
    public int top(int x, int z) {
        int y = maxY();
        while (y >= minY() && block(x, y, z) == AIR) {
            --y;
        }
        return y;
    }

    /**
     * The key of an edited block.
     *
     * @param x The block's x.
     * @param y The block's y.
     * @param z The block's z.
     * @return The key.
     */
    private static long editKey(int x, int y, int z) {
        return ((x & KEY_MASK) << (2 * KEY_BITS)) | ((y & KEY_MASK) << KEY_BITS) | (z & KEY_MASK);
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
            if (!solid(x, y + 1, z, height)) {
                return y < WATER_LEVEL ? DIRT : GRASS;
            }
            return y >= height - DIRT_DEPTH ? DIRT : STONE;
        }
        if (y <= WATER_LEVEL) {
            return WATER;
        }
        // On top of the ground: slabs, plants, then leaves, huts and glass in the air
        if (solid(x, y - 1, z, height)) {
            final double fraction = height - Math.floor(height);
            if (y == (int) Math.floor(height) + 1 && fraction >= 0.5) {
                return SLAB;
            }
            if (hash(x, y, z, 1) < 0.7) {
                return PLANT;
            }
        }
        if (recipe == Recipe.HILLS) {
            return AIR;
        }
        // A ragged blob of leaves on a few columns, a pane of glass on fewer
        final int cellX = Math.floorDiv(x, TREE_SPACING);
        final int cellZ = Math.floorDiv(z, TREE_SPACING);
        final double tree = hash(cellX, 0, cellZ, 2);
        if (tree < HUT_CELLS) {
            final int treeX = cellX * TREE_SPACING + TREE_MIDDLE;
            final int treeZ = cellZ * TREE_SPACING + TREE_MIDDLE;
            final int top = (int) Math.floor(height(treeX, treeZ)) + 4;
            if (Math.abs(x - treeX) <= 3
                    && Math.abs(z - treeZ) <= 3
                    && y >= top
                    && y < top + LEAF_LAYERS
                    && hash(x, y, z, 3) >= LEAF_GAPS) {
                return LEAVES;
            }
        } else if (tree < GLASS_CELLS) {
            return hut(x, y, z, cellX, cellZ);
        } else {
            final int wallZ = cellZ * TREE_SPACING + TREE_MIDDLE;
            final int base =
                    (int) Math.floor(height(cellX * TREE_SPACING + TREE_MIDDLE, wallZ)) + 1;
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
     * The hut nearest a column: in the huts recipe the one whose grid cell holds it, in the terrain
     * recipe the nearest, searching tree cells out from it.
     *
     * @param x The column's x.
     * @param z The column's z.
     * @param cells How many tree cells out to search.
     * @return The hut's middle x, the y of its floor, and its middle z, or null for none.
     */
    public int[] hutNear(int x, int z, int cells) {
        if (recipe == Recipe.HILLS) {
            return null;
        }
        if (recipe == Recipe.HUTS) {
            return new int[] {
                Math.floorDiv(x, HUT_GRID) * HUT_GRID + HUT_GRID / 2,
                1,
                Math.floorDiv(z, HUT_GRID) * HUT_GRID + HUT_GRID / 2
            };
        }
        final int homeX = Math.floorDiv(x, TREE_SPACING);
        final int homeZ = Math.floorDiv(z, TREE_SPACING);
        int[] best = null;
        long bestDistance = Long.MAX_VALUE;
        for (int cellZ = homeZ - cells; cellZ <= homeZ + cells; ++cellZ) {
            for (int cellX = homeX - cells; cellX <= homeX + cells; ++cellX) {
                final double tree = hash(cellX, 0, cellZ, 2);
                if (tree < HUT_CELLS || tree >= GLASS_CELLS) {
                    continue;
                }
                final int middleX = cellX * TREE_SPACING + TREE_MIDDLE;
                final int middleZ = cellZ * TREE_SPACING + TREE_MIDDLE;
                final int base = (int) Math.floor(height(middleX, middleZ)) + 1;
                if (base <= WATER_LEVEL + 1) {
                    continue;
                }
                final long distance =
                        (long) (middleX - x) * (middleX - x) + (long) (middleZ - z) * (middleZ - z);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = new int[] {middleX, base, middleZ};
                }
            }
        }
        return best;
    }

    /**
     * A small plank hut in a tree cell: walls around a square, a door on its -z side, and stairs in
     * front of the door, left out under water.
     *
     * @param x The block's x.
     * @param y The block's y.
     * @param z The block's z.
     * @param cellX The tree cell's x.
     * @param cellZ The tree cell's z.
     * @return The block type, air outside the hut.
     */
    private byte hut(int x, int y, int z, int cellX, int cellZ) {
        final int middleX = cellX * TREE_SPACING + TREE_MIDDLE;
        final int middleZ = cellZ * TREE_SPACING + TREE_MIDDLE;
        final int base = (int) Math.floor(height(middleX, middleZ)) + 1;
        if (base <= WATER_LEVEL + 1) {
            return AIR;
        }
        return hutAt(x, y, z, middleX, base, middleZ);
    }

    /**
     * A plank hut's block at a position: walls around a square, open on top, a door on its -z side,
     * and stairs in front of the door.
     *
     * @param x The block's x.
     * @param y The block's y.
     * @param z The block's z.
     * @param middleX The hut's middle x.
     * @param base The y of its floor.
     * @param middleZ The hut's middle z.
     * @return The block type, air outside the hut.
     */
    private static byte hutAt(int x, int y, int z, int middleX, int base, int middleZ) {
        if (y < base || y >= base + HUT_HEIGHT) {
            return AIR;
        }
        final int dx = x - middleX;
        final int dz = z - middleZ;
        if (dx == 0 && dz == -HUT_RADIUS - 1 && y == base) {
            return STAIRS;
        }
        if (Math.abs(dx) > HUT_RADIUS || Math.abs(dz) > HUT_RADIUS) {
            return AIR;
        }
        final boolean wall = Math.abs(dx) == HUT_RADIUS || Math.abs(dz) == HUT_RADIUS;
        final boolean door = dx == 0 && dz == -HUT_RADIUS && y < base + DOOR_HEIGHT;
        return wall && !door ? PLANKS : AIR;
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
            if (y == 0) {
                return GRASS;
            }
            return y >= -DIRT_DEPTH ? DIRT : STONE;
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
     * The huts recipe: flat grass at y 0, a plank hut in the middle of every cell of a grid, the
     * origin between cells.
     *
     * @param x The block's x.
     * @param y The block's y.
     * @param z The block's z.
     * @return The block type.
     */
    private byte huts(int x, int y, int z) {
        if (y <= 0) {
            if (y == 0) {
                return GRASS;
            }
            return y >= -DIRT_DEPTH ? DIRT : STONE;
        }
        final int middleX = Math.floorDiv(x, HUT_GRID) * HUT_GRID + HUT_GRID / 2;
        final int middleZ = Math.floorDiv(z, HUT_GRID) * HUT_GRID + HUT_GRID / 2;
        return hutAt(x, y, z, middleX, 1, middleZ);
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
        if (y >= 32 - DIRT_DEPTH) {
            return DIRT;
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
