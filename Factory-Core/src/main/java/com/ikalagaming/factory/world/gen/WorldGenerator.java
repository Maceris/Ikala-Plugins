package com.ikalagaming.factory.world.gen;

import com.ikalagaming.factory.world.Block;
import com.ikalagaming.factory.world.Chunk;
import com.ikalagaming.factory.world.ChunkPos;
import com.ikalagaming.factory.world.World;
import com.ikalagaming.factory.world.gen.density.Box;
import com.ikalagaming.factory.world.gen.density.DensityNode;
import com.ikalagaming.factory.world.gen.density.EvalCache;
import com.ikalagaming.factory.world.gen.density.Interval;

import lombok.NonNull;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Generates chunks for one world type and seed. A chunk is a pure function of the seed, its
 * position and the data, with a bounded neighborhood worked out from the same functions, so the
 * result never depends on which chunks were generated before or which thread runs it. Safe to call
 * from several threads at once.
 *
 * <p>Terrain is sampled on the world type's coarse lattice and interpolated between samples. The
 * lattice is aligned to world coordinates, so neighboring chunks agree. Before sampling, the
 * terrain's bounds over the chunk say whether it is certainly solid or certainly air, which skips
 * the sampling entirely for most of a world.
 *
 * <p>The block rules need to know how far each block is from open space above and below, up to
 * {@value BlockRules#EXPOSURE_CAP} blocks, and whether it touches fluid, so the terrain is worked
 * out that far past the chunk's edges rather than read from neighboring chunks.
 */
public final class WorldGenerator {

    /** How far past the chunk the terrain is worked out vertically, for air distances. */
    private static final int MARGIN_Y = BlockRules.EXPOSURE_CAP;

    /** How far past the chunk the terrain is worked out horizontally, for touching fluid. */
    private static final int MARGIN_XZ = 1;

    /** The size of the region worked out along x and z. */
    private static final int REGION_XZ = World.CHUNK_SIZE + 2 * MARGIN_XZ;

    /** The size of the region worked out along y. */
    private static final int REGION_Y = World.CHUNK_SIZE + 2 * MARGIN_Y;

    /** Where in a biome cell its parameters are sampled: the middle. */
    private static final double CELL_CENTER = Chunk.BIOME_CELL_SIZE / 2.0;

    /** Counters for instrumentation. */
    public static final class Stats {
        /** Chunks generated. */
        public final AtomicLong chunks = new AtomicLong();

        /** Chunks whose terrain was certainly air, so nothing was sampled. */
        public final AtomicLong skippedAir = new AtomicLong();

        /** Chunks whose terrain was certainly solid, so nothing was sampled. */
        public final AtomicLong skippedSolid = new AtomicLong();

        /** Chunks entirely outside the world border. */
        public final AtomicLong outsideBorder = new AtomicLong();

        /** Terrain lattice samples taken. */
        public final AtomicLong latticeSamples = new AtomicLong();
    }

    /** The world type. */
    private final WorldType worldType;

    /** Chooses biomes. */
    private final BiomeSelector biomes;

    /** Counters for instrumentation. */
    private final Stats stats = new Stats();

    /** The world type's biomes by ID, including the fallback. */
    private final java.util.Map<String, Biome> biomesById = new java.util.HashMap<>();

    /**
     * Set up generation.
     *
     * @param worldType The world type, compiled with the world seed.
     * @param seed The world seed.
     */
    public WorldGenerator(@NonNull WorldType worldType, long seed) {
        this.worldType = worldType;
        this.biomes = new BiomeSelector(worldType, seed);
        biomesById.put(worldType.fallbackBiome().id(), worldType.fallbackBiome());
        for (Biome biome : worldType.biomes()) {
            biomesById.put(biome.id(), biome);
        }
    }

    /**
     * The world type.
     *
     * @return The world type.
     */
    public WorldType getWorldType() {
        return worldType;
    }

    /**
     * Chooses biomes, for debug views.
     *
     * @return The selector.
     */
    public BiomeSelector getBiomes() {
        return biomes;
    }

    /**
     * Counters for instrumentation.
     *
     * @return The counters.
     */
    public Stats getStats() {
        return stats;
    }

    /**
     * Generate a chunk completely.
     *
     * @param pos The chunk.
     * @return The chunk.
     */
    public Chunk generate(@NonNull ChunkPos pos) {
        return generate(pos, Stage.BLOCK_RULES);
    }

    /**
     * Generate a chunk up to a stage.
     *
     * @param pos The chunk.
     * @param until The last stage to run.
     * @return The chunk.
     * @throws IllegalArgumentException If the stage isn't built yet.
     */
    public Chunk generate(@NonNull ChunkPos pos, @NonNull Stage until) {
        if (!until.isBuilt()) {
            throw new IllegalArgumentException("Stage " + until + " is not built yet");
        }
        stats.chunks.incrementAndGet();
        final EvalCache cache = new EvalCache();
        Chunk chunk = new Chunk(World.AIR, worldType.fallbackBiome().id());
        if (until.includes(Stage.BIOMES)) {
            chooseBiomes(chunk, pos, cache);
        }
        if (!until.includes(Stage.TERRAIN)) {
            return chunk;
        }
        final boolean[] solid = terrain(pos, cache);
        final Block[] fluid =
                until.includes(Stage.FLUIDS) ? fluids(pos, solid, cache) : new Block[0];
        fill(chunk, pos, solid, fluid, until.includes(Stage.BLOCK_RULES), cache);
        return chunk;
    }

    /**
     * Choose the biome of each cell.
     *
     * @param chunk The chunk.
     * @param pos Where it is.
     * @param cache The chunk's cache.
     */
    private void chooseBiomes(@NonNull Chunk chunk, @NonNull ChunkPos pos, EvalCache cache) {
        for (int cy = 0; cy < Chunk.BIOME_CELLS; ++cy) {
            for (int cz = 0; cz < Chunk.BIOME_CELLS; ++cz) {
                for (int cx = 0; cx < Chunk.BIOME_CELLS; ++cx) {
                    final long x = pos.blockX() + (long) cx * Chunk.BIOME_CELL_SIZE;
                    final long y = pos.blockY() + (long) cy * Chunk.BIOME_CELL_SIZE;
                    final long z = pos.blockZ() + (long) cz * Chunk.BIOME_CELL_SIZE;
                    final double[] values =
                            biomes.parameters(
                                    x + CELL_CENTER, y + CELL_CENTER, z + CELL_CENTER, cache);
                    final Biome biome =
                            biomes.choose(
                                    values,
                                    Math.floorDiv(x, Chunk.BIOME_CELL_SIZE),
                                    Math.floorDiv(y, Chunk.BIOME_CELL_SIZE),
                                    Math.floorDiv(z, Chunk.BIOME_CELL_SIZE));
                    chunk.setBiomeCell(cx, cy, cz, biome.id());
                }
            }
        }
    }

    /**
     * The index of a position in the region worked out around a chunk.
     *
     * @param rx The x offset from the region's start.
     * @param ry The y offset from the region's start.
     * @param rz The z offset from the region's start.
     * @return The index.
     */
    private static int regionIndex(int rx, int ry, int rz) {
        return rx + REGION_XZ * (rz + REGION_XZ * ry);
    }

    /**
     * Which positions are solid, over the chunk and its margins.
     *
     * @param pos The chunk.
     * @param cache The chunk's cache.
     * @return Solid flags, indexed by {@link #regionIndex}.
     */
    private boolean[] terrain(@NonNull ChunkPos pos, EvalCache cache) {
        final long startX = pos.blockX() - MARGIN_XZ;
        final long startY = pos.blockY() - MARGIN_Y;
        final long startZ = pos.blockZ() - MARGIN_XZ;
        boolean[] solid = new boolean[REGION_XZ * REGION_Y * REGION_XZ];
        final Box region =
                new Box(
                        startX,
                        startY,
                        startZ,
                        startX + REGION_XZ - 1,
                        startY + REGION_Y - 1,
                        startZ + REGION_XZ - 1);
        final Box border = worldType.border();
        if (region.maxX() < border.minX()
                || region.minX() > border.maxX()
                || region.maxY() < border.minY()
                || region.minY() > border.maxY()
                || region.maxZ() < border.minZ()
                || region.minZ() > border.maxZ()) {
            stats.outsideBorder.incrementAndGet();
            return solid;
        }

        final int[] cell = worldType.interpolation();
        // Interpolated values blend lattice samples up to a cell outside the region
        final Interval range = worldType.density().bounds(region.expand(cell[0], cell[1], cell[2]));
        final boolean wholeInside =
                worldType.inside((long) region.minX(), (long) region.minY(), (long) region.minZ())
                        && worldType.inside(
                                (long) region.maxX(), (long) region.maxY(), (long) region.maxZ());
        if (range.max() <= 0) {
            stats.skippedAir.incrementAndGet();
            return solid;
        }
        if (range.min() > 0 && wholeInside) {
            stats.skippedSolid.incrementAndGet();
            java.util.Arrays.fill(solid, true);
            return solid;
        }

        final Lattice lattice = new Lattice(region, cell);
        final DensityNode density = worldType.density();
        double[] samples = new double[lattice.count()];
        for (int ly = 0; ly < lattice.sizes[1]; ++ly) {
            for (int lz = 0; lz < lattice.sizes[2]; ++lz) {
                for (int lx = 0; lx < lattice.sizes[0]; ++lx) {
                    samples[lattice.index(lx, ly, lz)] =
                            density.value(
                                    lattice.coordinate(0, lx),
                                    lattice.coordinate(1, ly),
                                    lattice.coordinate(2, lz),
                                    cache);
                }
            }
        }
        stats.latticeSamples.addAndGet(samples.length);

        for (int ry = 0; ry < REGION_Y; ++ry) {
            for (int rz = 0; rz < REGION_XZ; ++rz) {
                for (int rx = 0; rx < REGION_XZ; ++rx) {
                    final long x = startX + rx;
                    final long y = startY + ry;
                    final long z = startZ + rz;
                    solid[regionIndex(rx, ry, rz)] =
                            worldType.inside(x, y, z) && lattice.interpolate(samples, x, y, z) > 0;
                }
            }
        }
        return solid;
    }

    /**
     * The coarse lattice terrain is sampled on, covering a region, aligned to world coordinates.
     */
    private static final class Lattice {
        /** The first lattice coordinate along each axis. */
        private final long[] starts = new long[DensityNode.AXIS_COUNT];

        /** The cell size along each axis. */
        private final int[] cells;

        /** How many lattice points along each axis. */
        private final int[] sizes = new int[DensityNode.AXIS_COUNT];

        /**
         * Cover a region.
         *
         * @param region The region.
         * @param cells The cell size along each axis.
         */
        Lattice(@NonNull Box region, int @NonNull [] cells) {
            this.cells = cells;
            final double[] mins = {region.minX(), region.minY(), region.minZ()};
            final double[] maxes = {region.maxX(), region.maxY(), region.maxZ()};
            for (int axis = 0; axis < DensityNode.AXIS_COUNT; ++axis) {
                starts[axis] = Math.floorDiv((long) mins[axis], cells[axis]) * cells[axis];
                final long end = -Math.floorDiv(-(long) maxes[axis], cells[axis]) * cells[axis];
                sizes[axis] = (int) ((end - starts[axis]) / cells[axis]) + 1;
            }
        }

        /**
         * How many points there are.
         *
         * @return The count.
         */
        int count() {
            return sizes[0] * sizes[1] * sizes[2];
        }

        /**
         * A point's index in the sample array.
         *
         * @param lx The point along x.
         * @param ly The point along y.
         * @param lz The point along z.
         * @return The index.
         */
        int index(int lx, int ly, int lz) {
            return lx + sizes[0] * (lz + sizes[2] * ly);
        }

        /**
         * A point's world coordinate.
         *
         * @param axis The axis index.
         * @param point The point along it.
         * @return The coordinate.
         */
        double coordinate(int axis, int point) {
            return starts[axis] + (long) point * cells[axis];
        }

        /**
         * The interpolated terrain at a block.
         *
         * @param samples The lattice samples.
         * @param x The block's x.
         * @param y The block's y.
         * @param z The block's z.
         * @return The density.
         */
        double interpolate(double @NonNull [] samples, long x, long y, long z) {
            final long[] p = {x, y, z};
            int[] base = new int[DensityNode.AXIS_COUNT];
            double[] t = new double[DensityNode.AXIS_COUNT];
            for (int axis = 0; axis < DensityNode.AXIS_COUNT; ++axis) {
                final long offset = p[axis] - starts[axis];
                base[axis] = (int) (offset / cells[axis]);
                t[axis] = (double) (offset % cells[axis]) / cells[axis];
                if (base[axis] >= sizes[axis] - 1) {
                    base[axis] = sizes[axis] - 2;
                    t[axis] = 1;
                }
            }
            double result = 0;
            for (int corner = 0; corner < 1 << DensityNode.AXIS_COUNT; ++corner) {
                double weight = 1;
                int[] at = new int[DensityNode.AXIS_COUNT];
                for (int axis = 0; axis < DensityNode.AXIS_COUNT; ++axis) {
                    final boolean far = (corner >> axis & 1) != 0;
                    at[axis] = base[axis] + (far ? 1 : 0);
                    weight *= far ? t[axis] : 1 - t[axis];
                }
                if (weight != 0) {
                    result += weight * samples[index(at[0], at[1], at[2])];
                }
            }
            return result;
        }
    }

    /**
     * Which open positions hold fluid: the chunk and one block around it.
     *
     * @param pos The chunk.
     * @param solid The solid flags.
     * @param cache The chunk's cache.
     * @return The fluid at each region position, null for none, indexed by {@link #regionIndex};
     *     only filled around the chunk.
     */
    private Block[] fluids(@NonNull ChunkPos pos, boolean @NonNull [] solid, EvalCache cache) {
        Block[] fluid = new Block[solid.length];
        final long startX = pos.blockX() - MARGIN_XZ;
        final long startY = pos.blockY() - MARGIN_Y;
        final long startZ = pos.blockZ() - MARGIN_XZ;
        final Fluids fluids = worldType.fluids();
        final Interval level =
                fluids.levelBounds(
                        new Box(
                                startX,
                                pos.blockY() - 1,
                                startZ,
                                startX + REGION_XZ - 1,
                                pos.blockY() + World.CHUNK_SIZE,
                                startZ + REGION_XZ - 1));
        // Fluid fills below the level, so a chunk certainly above it has none
        if (level == null || level.max() <= pos.blockY() - 1) {
            return fluid;
        }
        for (int ry = MARGIN_Y - 1; ry <= MARGIN_Y + World.CHUNK_SIZE; ++ry) {
            for (int rz = 0; rz < REGION_XZ; ++rz) {
                for (int rx = 0; rx < REGION_XZ; ++rx) {
                    final int index = regionIndex(rx, ry, rz);
                    final long x = startX + rx;
                    final long y = startY + ry;
                    final long z = startZ + rz;
                    if (!solid[index] && worldType.inside(x, y, z)) {
                        fluid[index] = fluids.fluidAt(x, y, z, cache);
                    }
                }
            }
        }
        return fluid;
    }

    /**
     * Write the chunk's blocks.
     *
     * @param chunk The chunk.
     * @param pos Where it is.
     * @param solid The solid flags.
     * @param fluid The fluid at each position, or empty if fluids aren't generated.
     * @param rules Whether to choose solid blocks by block rules.
     * @param cache The chunk's cache.
     */
    private void fill(
            @NonNull Chunk chunk,
            @NonNull ChunkPos pos,
            boolean @NonNull [] solid,
            Block @NonNull [] fluid,
            boolean rules,
            EvalCache cache) {
        for (int y = 0; y < World.CHUNK_SIZE; ++y) {
            for (int z = 0; z < World.CHUNK_SIZE; ++z) {
                for (int x = 0; x < World.CHUNK_SIZE; ++x) {
                    final int rx = x + MARGIN_XZ;
                    final int ry = y + MARGIN_Y;
                    final int rz = z + MARGIN_XZ;
                    final int index = regionIndex(rx, ry, rz);
                    if (!solid[index]) {
                        if (fluid.length > 0 && fluid[index] != null) {
                            chunk.setBlock(x, y, z, fluid[index]);
                        }
                        continue;
                    }
                    Block block = worldType.defaultBlock();
                    if (rules) {
                        final BlockRules.Match match =
                                match(chunk, pos, x, y, z, solid, fluid, cache);
                        if (match != null) {
                            block = match.block();
                        }
                    }
                    chunk.setBlock(x, y, z, block);
                }
            }
        }
    }

    /**
     * The block rule that chooses a solid block, if any.
     *
     * @param chunk The chunk, with its biomes chosen.
     * @param pos Where it is.
     * @param x The block's x within the chunk.
     * @param y The block's y within the chunk.
     * @param z The block's z within the chunk.
     * @param solid The solid flags.
     * @param fluid The fluid at each position, or empty.
     * @param cache The chunk's cache.
     * @return The match, or null if the biome has no rules or none match.
     */
    private BlockRules.Match match(
            @NonNull Chunk chunk,
            @NonNull ChunkPos pos,
            int x,
            int y,
            int z,
            boolean @NonNull [] solid,
            Block @NonNull [] fluid,
            EvalCache cache) {
        final String biomeId = chunk.getBiome(x, y, z);
        final Biome biome = biomesById.get(biomeId);
        if (biome == null || biome.blockRules() == null) {
            return null;
        }
        final int rx = x + MARGIN_XZ;
        final int ry = y + MARGIN_Y;
        final int rz = z + MARGIN_XZ;
        final BlockRules.Context context =
                new BlockRules.Context(
                        pos.blockX() + x,
                        pos.blockY() + y,
                        pos.blockZ() + z,
                        distanceToOpen(solid, rx, ry, rz, 1),
                        distanceToOpen(solid, rx, ry, rz, -1),
                        biomeId,
                        touchesFluid(fluid, rx, ry, rz),
                        cache);
        return biome.blockRules().explain(context);
    }

    /**
     * Why a block is what it is: the block rule that chose it, the same way generation does.
     *
     * @param x The block's x.
     * @param y The block's y.
     * @param z The block's z.
     * @return The rule's match, or null if the position is open, or solid with the default block.
     */
    public BlockRules.Match explain(long x, long y, long z) {
        final ChunkPos pos = ChunkPos.containing(x, y, z);
        final EvalCache cache = new EvalCache();
        Chunk chunk = new Chunk(World.AIR, worldType.fallbackBiome().id());
        chooseBiomes(chunk, pos, cache);
        final boolean[] solid = terrain(pos, cache);
        final int lx = (int) (x - pos.blockX());
        final int ly = (int) (y - pos.blockY());
        final int lz = (int) (z - pos.blockZ());
        if (!solid[regionIndex(lx + MARGIN_XZ, ly + MARGIN_Y, lz + MARGIN_XZ)]) {
            return null;
        }
        return match(chunk, pos, lx, ly, lz, solid, fluids(pos, solid, cache), cache);
    }

    /**
     * How many blocks up or down to the first open position, capped.
     *
     * @param solid The solid flags.
     * @param rx The block's x in the region.
     * @param ry The block's y in the region.
     * @param rz The block's z in the region.
     * @param step 1 to look up, -1 to look down.
     * @return The distance, from 1 for open right next to it to {@value BlockRules#EXPOSURE_CAP}.
     */
    private static int distanceToOpen(boolean[] solid, int rx, int ry, int rz, int step) {
        for (int distance = 1; distance < BlockRules.EXPOSURE_CAP; ++distance) {
            if (!solid[regionIndex(rx, ry + step * distance, rz)]) {
                return distance;
            }
        }
        return BlockRules.EXPOSURE_CAP;
    }

    /**
     * Whether any face of a block touches fluid.
     *
     * @param fluid The fluid at each position, or empty.
     * @param rx The block's x in the region.
     * @param ry The block's y in the region.
     * @param rz The block's z in the region.
     * @return True if a neighbor is fluid.
     */
    private static boolean touchesFluid(Block[] fluid, int rx, int ry, int rz) {
        if (fluid.length == 0) {
            return false;
        }
        return fluid[regionIndex(rx + 1, ry, rz)] != null
                || fluid[regionIndex(rx - 1, ry, rz)] != null
                || fluid[regionIndex(rx, ry + 1, rz)] != null
                || fluid[regionIndex(rx, ry - 1, rz)] != null
                || fluid[regionIndex(rx, ry, rz + 1)] != null
                || fluid[regionIndex(rx, ry, rz - 1)] != null;
    }
}
