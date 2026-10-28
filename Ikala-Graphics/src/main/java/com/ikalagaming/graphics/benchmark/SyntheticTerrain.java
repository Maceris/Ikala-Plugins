package com.ikalagaming.graphics.benchmark;

import com.ikalagaming.graphics.Sections;
import com.ikalagaming.graphics.bake.BakeSource;
import com.ikalagaming.graphics.bake.Face;
import com.ikalagaming.graphics.bake.Faces;
import com.ikalagaming.graphics.bake.SectionBaker;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

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

    /** A stone slab, filling the bottom half of its cell. */
    public static final byte STONE_SLAB = 12;

    /** A stone slab, filling the top half of its cell. */
    public static final byte STONE_SLAB_TOP = 13;

    /**
     * The first of the jumble recipe's wooden stairs, see {@link #stairs(int, boolean, int)} for
     * the rest.
     */
    public static final byte STAIRS_FIRST = 14;

    /** Straight stairs, a step on the back half. */
    public static final int STAIRS_STRAIGHT = 0;

    /** Inner corner stairs, a step on three quarters. */
    public static final int STAIRS_INNER = 1;

    /** Outer corner stairs, a step on one quarter. */
    public static final int STAIRS_OUTER = 2;

    /** How many stair shapes there are. */
    public static final int STAIR_SHAPES = 3;

    /** How many ways a turnable block can face, quarter turns about y. */
    public static final int FACINGS = 4;

    /** How many stairs block types there are: every shape, both ways up, facing every way. */
    public static final int STAIR_VARIANTS = STAIR_SHAPES * 2 * FACINGS;

    /** A thin glass pane, running along x through the middle of its cell. */
    public static final byte PANE_X = STAIRS_FIRST + STAIR_VARIANTS;

    /** A thin glass pane, running along z through the middle of its cell. */
    public static final byte PANE_Z = PANE_X + 1;

    /** Thin glass panes crossing in the middle of their cell. */
    public static final byte PANE_CROSS = PANE_Z + 1;

    /** The first of four wooden lecterns, one facing each way: a base, a post and a tilted desk. */
    public static final byte LECTERN_FIRST = PANE_CROSS + 1;

    /** The first of four wooden chests, one facing each way: a little gap all round but below. */
    public static final byte CHEST_FIRST = LECTERN_FIRST + FACINGS;

    /** How many block types there are, air included. */
    public static final int BLOCK_TYPES = CHEST_FIRST + FACINGS;

    /** How many blocks of dirt lie under the grass. */
    private static final int DIRT_DEPTH = 3;

    /** The terrain recipe's water level: open cells at or below it hold water. */
    private static final int WATER_LEVEL = -8;

    /** How far a hut's walls reach from its middle, in blocks. */
    private static final int HUT_RADIUS = 2;

    /** How tall a hut's walls are, in blocks. */
    private static final int HUT_HEIGHT = 3;

    /** How high the island recipe's sea stands, in world y: seven eighths up block 0. */
    public static final double SEA_LEVEL = 0.875;

    /** How high the island's hill rises above the sea floor's slope, at its middle, in blocks. */
    private static final int ISLAND_TOP = 10;

    /** How deep the island recipe's sea floor gets, in blocks below y 0. */
    private static final int ISLAND_DEPTH = 24;

    /** How far from the island's middle the sea floor reaches its full depth, in blocks. */
    private static final double ISLAND_REACH = 110;

    /** How far the island's shore wanders in and out, in blocks. */
    private static final double ISLAND_WOBBLE = 18;

    /** How large the shore's wandering is, in blocks. */
    private static final double ISLAND_WOBBLE_SCALE = 48;

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

    /**
     * How deep under the ground's height the jumble recipe's mix of shapes goes, in blocks. Below
     * it is plain stone.
     */
    private static final int JUMBLE_DEPTH = 6;

    /**
     * The kinds of block the jumble recipe mixes, picked between evenly: solid, see-through and cut
     * out cubes, slabs, stairs, panes, lecterns and chests.
     */
    private static final int JUMBLE_KINDS = 8;

    /** How much of the jumble recipe's glass is tinted. */
    private static final double JUMBLE_TINTED = 0.25;

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
        HILLS,
        /**
         * A small hilly island in a wide sea: grass above the water, a stone sea floor sloping down
         * to its full depth well out from the shore. The water itself is drawn by graphics as water
         * tiles over {@link #SEA_LEVEL}, see the gate's water option.
         */
        ISLAND,
        /**
         * The hills recipe's land, its top few blocks an even jumble of cubes (solid, glass and
         * leaves), slabs, stairs of every shape and facing, glass panes, lecterns and chests, so
         * few faces hide each other and level of detail has many small, odd parts to simplify.
         */
        JUMBLE
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

    /**
     * Whether the island has a stepped stone fountain on its top, see {@link #fountainTier}: set
     * before any blocks are read.
     */
    @Getter @Setter private volatile boolean fountain;

    /**
     * Whether the island has its transparency test pieces: a glass dome on the sea floor off its
     * shore and a glass wall on its top, see {@link #glassTest}. Set before any blocks are read.
     */
    @Getter @Setter private volatile boolean glassTest;

    /**
     * Whether the lakes are left to water tiles, see {@link #lakeBottom}: open cells at or below
     * the water level are air rather than water blocks. Set before any blocks are read.
     */
    @Getter @Setter private volatile boolean waterTiles;

    /** The lakes' surface: as far up the water level's block as the sea's is up its own. */
    public static final double LAKE_SURFACE = WATER_LEVEL + SEA_LEVEL;

    /** What {@link #lakeBottom} gives for a column without a lake. */
    public static final int NO_LAKE = Integer.MIN_VALUE;

    /** How far below the water level a lake's bottom is looked for, in blocks. */
    private static final int LAKE_DEPTH = 32;

    /** Where the glass dome stands on the sea floor, along z from the island's middle. */
    public static final int DOME_Z = 60;

    /** The glass dome's outer radius, in blocks. */
    public static final int DOME_RADIUS = 5;

    /**
     * Where the glass wall stands along z, in front of the fountain, along x, off to one side so it
     * covers about half of the fountain and the smoke, and its half width.
     */
    public static final int WALL_Z = 9;

    public static final int WALL_X = 3;

    public static final int WALL_HALF_WIDTH = 2;

    /** How many blocks tall the glass wall stands over the ground. */
    public static final int WALL_HEIGHT = 10;

    /** The fountain's tiers, blocks across each from the outermost in, centered on the origin. */
    public static final int[] FOUNTAIN_SIZES = {12, 8, 4};

    /** How many blocks higher each of the fountain's tiers stands than the one around it. */
    public static final int FOUNTAIN_STEP = 2;

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
            case TERRAIN, HILLS, JUMBLE -> 48;
            case CHECKERBOARD -> SECTION - 1;
            case CAVES -> 34;
            case GLASS -> GLASS_WALL_HEIGHT;
            case HUTS -> HUT_HEIGHT;
            case ISLAND -> glassTest ? ISLAND_TOP + WALL_HEIGHT : ISLAND_TOP;
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
            case TERRAIN, HILLS, JUMBLE -> -64;
            case CHECKERBOARD -> 0;
            case CAVES -> -96;
            case GLASS, HUTS -> 0;
            case ISLAND -> -ISLAND_DEPTH - 1;
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
            case TERRAIN, HILLS, JUMBLE -> height(0, 0) + 6;
            case CHECKERBOARD -> SECTION + 8;
            case CAVES -> 42;
            case GLASS -> GLASS_WALL_HEIGHT / 2.0;
            case HUTS -> HUT_HEIGHT * 2.0;
            case ISLAND -> Math.floor(islandHeight(0, 0)) + 1 + 6;
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
            case JUMBLE -> jumble(x, y, z);
            case ISLAND -> island(x, y, z);
        };
    }

    /**
     * One of the jumble recipe's stairs.
     *
     * @param shape {@link #STAIRS_STRAIGHT}, {@link #STAIRS_INNER} or {@link #STAIRS_OUTER}.
     * @param upsideDown Whether the slab is on top and the step hangs below it.
     * @param facing How many quarter turns about y from climbing toward +z.
     * @return The block type.
     */
    public static byte stairs(int shape, boolean upsideDown, int facing) {
        return (byte) (STAIRS_FIRST + (shape * 2 + (upsideDown ? 1 : 0)) * FACINGS + facing);
    }

    /**
     * A block type's name, for reports.
     *
     * @param block The block type.
     * @return Its name.
     */
    public static String name(int block) {
        final String[] plain = {
            "air",
            "stone",
            "grass",
            "slab",
            "plant",
            "leaves",
            "glass",
            "tinted glass",
            "dirt",
            "planks",
            "stairs",
            "water",
            "stone slab",
            "stone slab top"
        };
        if (block >= 0 && block < plain.length) {
            return plain[block];
        }
        if (block >= STAIRS_FIRST && block < STAIRS_FIRST + STAIR_VARIANTS) {
            final int variant = block - STAIRS_FIRST;
            final String[] shapes = {"straight", "inner", "outer"};
            return shapes[variant / (2 * FACINGS)]
                    + ((variant / FACINGS) % 2 == 1 ? " upside down" : "")
                    + " stairs "
                    + variant % FACINGS;
        }
        if (block == PANE_X || block == PANE_Z || block == PANE_CROSS) {
            return "pane " + (block == PANE_X ? "x" : block == PANE_Z ? "z" : "cross");
        }
        if (block >= LECTERN_FIRST && block < LECTERN_FIRST + FACINGS) {
            return "lectern " + (block - LECTERN_FIRST);
        }
        if (block >= CHEST_FIRST && block < CHEST_FIRST + FACINGS) {
            return "chest " + (block - CHEST_FIRST);
        }
        return "block " + block;
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
                || block == STAIRS
                || block == STONE_SLAB
                || block == STONE_SLAB_TOP
                || (block >= STAIRS_FIRST && block < STAIRS_FIRST + STAIR_VARIANTS)
                || (block >= LECTERN_FIRST && block < CHEST_FIRST + FACINGS);
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
     * Whether the recipe has lakes: open cells at or below its water level, which hold water.
     *
     * @return True for the hills and the jumble.
     */
    public boolean hasLakes() {
        return recipe == Recipe.TERRAIN || recipe == Recipe.HILLS || recipe == Recipe.JUMBLE;
    }

    /**
     * Whether a column of the hills holds a lake: its ground is below the water level there, not
     * counting caves inside the ground. Trees keep off the lakes, so they are open to the sky.
     *
     * @param x The column's x.
     * @param z The column's z.
     * @return True if it does.
     */
    private boolean isLake(int x, int z) {
        return !solid(x, WATER_LEVEL, z, height(x, z));
    }

    /**
     * Where a column's lake's bottom is: the top of the first block under the water level, if the
     * ground is below the water level there, as the water blocks are placed, not where a cave
     * inside the ground reaches it; the water stands from it up to {@link #LAKE_SURFACE}.
     *
     * @param x The column's x.
     * @param z The column's z.
     * @return The bottom's y, or {@link #NO_LAKE} if the column holds no lake.
     */
    public int lakeBottom(int x, int z) {
        if (!hasLakes() || !isLake(x, z) || edits.containsKey(editKey(x, WATER_LEVEL, z))) {
            return NO_LAKE;
        }
        for (int y = WATER_LEVEL - 1; y > WATER_LEVEL - LAKE_DEPTH; --y) {
            final byte block = block(x, y, z);
            if (block != AIR && block != WATER) {
                return y + 1;
            }
        }
        return WATER_LEVEL - LAKE_DEPTH + 1;
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
            return waterTiles ? AIR : WATER;
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
        // A ragged blob of leaves on a few columns, a pane of glass on fewer, none over the lakes
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
                    && hash(x, y, z, 3) >= LEAF_GAPS
                    && !isLake(treeX, treeZ)
                    && !isLake(x, z)) {
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
                    && y < base + 4
                    && base > WATER_LEVEL + 1) {
                return GLASS;
            }
        }
        return AIR;
    }

    /**
     * The jumble recipe: the hills recipe's ground and caves, its top {@link #JUMBLE_DEPTH} blocks
     * a mix of shapes picked by hash, stone below, and lakes.
     *
     * @param x The block's x.
     * @param y The block's y.
     * @param z The block's z.
     * @return The block type.
     */
    private byte jumble(int x, int y, int z) {
        final double height = height(x, z);
        if (!solid(x, y, z, height)) {
            return y <= WATER_LEVEL && !waterTiles ? WATER : AIR;
        }
        if (y < height - 4
                && y > height - 24
                && Math.abs(fbm3(x / 24.0, y / 16.0, z / 24.0, 2) - 0.5) < 0.07) {
            return AIR;
        }
        if (y < height - JUMBLE_DEPTH) {
            return STONE;
        }
        final double variant = hash(x, y, z, 6);
        return switch ((int) (hash(x, y, z, 5) * JUMBLE_KINDS)) {
            case 0 -> STONE;
            case 1 -> variant < JUMBLE_TINTED ? TINTED_GLASS : GLASS;
            case 2 -> LEAVES;
            case 3 -> variant < 0.5 ? STONE_SLAB : STONE_SLAB_TOP;
            case 4 -> (byte) (STAIRS_FIRST + (int) (variant * STAIR_VARIANTS));
            case 5 -> (byte) (PANE_X + (int) (variant * 3));
            case 6 -> (byte) (LECTERN_FIRST + (int) (variant * FACINGS));
            default -> (byte) (CHEST_FIRST + (int) (variant * FACINGS));
        };
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
        if (recipe == Recipe.HILLS || recipe == Recipe.JUMBLE || recipe == Recipe.ISLAND) {
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
     * The island recipe's ground at a column: a hill at the origin sloping smoothly down to the sea
     * floor, its shore wandering in and out.
     *
     * @param x The column's x.
     * @param z The column's z.
     * @return The ground's height, a fractional block y.
     */
    public double islandHeight(int x, int z) {
        final int tier = fountainTier(x, z);
        if (tier >= 0) {
            return fountainBase() + FOUNTAIN_STEP * tier;
        }
        return naturalIslandHeight(x, z);
    }

    /**
     * The transparency test pieces at a block, if any: a shell of glass a block thick, a half
     * sphere on the sea floor, and a wall of glass blocks on the island.
     *
     * @param x The block's x.
     * @param y The block's y.
     * @param z The block's z.
     * @param top The ground's top block in the column.
     * @return GLASS, or AIR for none.
     */
    private byte glassTest(int x, int y, int z, int top) {
        final int floor = (int) Math.floor(naturalIslandHeight(0, DOME_Z));
        final double distance =
                Math.sqrt(x * x + (y - floor) * (y - floor) + (z - DOME_Z) * (z - DOME_Z));
        if (y > floor && distance < DOME_RADIUS && distance >= DOME_RADIUS - 1) {
            return GLASS;
        }
        if (z == WALL_Z
                && Math.abs(x - WALL_X) <= WALL_HALF_WIDTH
                && y > top
                && y <= top + WALL_HEIGHT) {
            return GLASS;
        }
        return AIR;
    }

    /**
     * Which of the fountain's tiers a column is under, the highest whose square covers it.
     *
     * @param x The column's x.
     * @param z The column's z.
     * @return The tier, 0 the outermost, or -1 off the fountain or without one.
     */
    public int fountainTier(int x, int z) {
        if (!fountain || recipe != Recipe.ISLAND) {
            return -1;
        }
        final double reach = Math.max(Math.abs(x + 0.5), Math.abs(z + 0.5));
        int tier = -1;
        for (int i = 0; i < FOUNTAIN_SIZES.length; ++i) {
            if (reach < FOUNTAIN_SIZES[i] / 2.0) {
                tier = i;
            }
        }
        return tier;
    }

    /**
     * The top block of the fountain's outermost tier: the island's summit.
     *
     * @return The block y.
     */
    public int fountainBase() {
        return (int) Math.floor(naturalIslandHeight(0, 0));
    }

    /**
     * The island's ground without the fountain.
     *
     * @param x The column's x.
     * @param z The column's z.
     * @return The ground's height, a fractional block y.
     */
    private double naturalIslandHeight(int x, int z) {
        final double wobble =
                ISLAND_WOBBLE
                        * (fbm2(x / ISLAND_WOBBLE_SCALE, z / ISLAND_WOBBLE_SCALE, 2) - 0.5)
                        * 2;
        final double reach = Math.clamp((Math.hypot(x, z) + wobble) / ISLAND_REACH, 0, 1);
        return ISLAND_TOP - (ISLAND_TOP + ISLAND_DEPTH) * smooth(reach);
    }

    /**
     * The island recipe: grass over dirt on land, bare stone under the sea.
     *
     * @param x The block's x.
     * @param y The block's y.
     * @param z The block's z.
     * @return The block type.
     */
    private byte island(int x, int y, int z) {
        final int top = (int) Math.floor(islandHeight(x, z));
        if (glassTest && recipe == Recipe.ISLAND) {
            final byte piece = glassTest(x, y, z, top);
            if (piece != AIR) {
                return piece;
            }
        }
        if (fountainTier(x, z) >= 0) {
            return y > top ? AIR : STONE;
        }
        if (y > top) {
            return AIR;
        }
        if (top < 0) {
            return STONE;
        }
        if (y == top) {
            return GRASS;
        }
        return y >= top - DIRT_DEPTH ? DIRT : STONE;
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
