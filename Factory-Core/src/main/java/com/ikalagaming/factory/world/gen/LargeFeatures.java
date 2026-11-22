package com.ikalagaming.factory.world.gen;

import com.ikalagaming.factory.world.Block;
import com.ikalagaming.factory.world.Chunk;
import com.ikalagaming.factory.world.ChunkPos;
import com.ikalagaming.factory.world.World;
import com.ikalagaming.factory.world.gen.density.EvalCache;

import lombok.NonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * The large layer: features bigger than a block, such as trees, placed as instances over cells. See
 * the Features and Structures design doc.
 *
 * <ol>
 *   <li>Each cell of a placed feature holds a few candidates at hashed columns. Each looks down its
 *       column, from the top of the cell to the bottom, for the first open position with solid
 *       ground under it, and keeps it if its biome and conditions allow.
 *   <li>Candidates are thinned so none is within the spacing of a stronger one, by hash. A single
 *       pass that drops every candidate with any stronger one near it also drops candidates whose
 *       only rival was dropped itself, leaving forests far too sparse, so it takes three passes:
 *       drop any with a stronger candidate near; bring back those with no stronger first-pass
 *       survivor near; then drop any with a stronger second-pass survivor near, which keeps the
 *       spacing. Each pass reads only neighboring cells' previous pass.
 *   <li>Each survivor is an instance whose layout (its blocks, sorted by chunk) is worked out once
 *       and stamped into every chunk it reaches.
 * </ol>
 *
 * <p>Candidates look at the ground as the terrain and block rules make it, through chunks generated
 * to {@link Stage#BLOCK_RULES}, so every chunk sees the same instances whichever chunk is generated
 * first. Everything is cached and shared between threads.
 */
final class LargeFeatures {

    /** How many cells' candidates to keep. */
    private static final int CELL_CAPACITY = 16_384;

    /** How many layouts to keep. */
    private static final int LAYOUT_CAPACITY = 4096;

    /** Bits dropped to turn a hash into a fraction with a double's precision. */
    private static final int UNIT_SHIFT = 11;

    /** One over 2^53. */
    private static final double UNIT_SCALE = 0x1.0p-53;

    /** Salts for each of a candidate's rolls. */
    private static final long SALT_X = 1;

    private static final long SALT_Z = 2;

    private static final long SALT_STRENGTH = 3;

    private static final long SALT_SEED = 4;

    /**
     * A candidate that found its floor.
     *
     * @param x The open position it stands in.
     * @param y The open position's y.
     * @param z The open position's z.
     * @param strength Its strength when spacing candidates, from 0 to 1.
     * @param spacing How close a stronger candidate may be before this one gives way.
     * @param seed The seed its instance grows from.
     */
    record Candidate(long x, long y, long z, double strength, double spacing, long seed) {}

    /**
     * A placed feature's cell.
     *
     * @param feature The placed feature's index in {@link #features}.
     * @param x The cell's x.
     * @param y The cell's y.
     * @param z The cell's z.
     */
    private record CellKey(int feature, long x, long y, long z) {}

    /**
     * An instance whose layout is cached.
     *
     * @param feature The placed feature's index.
     * @param x Its foot's x.
     * @param y Its foot's y.
     * @param z Its foot's z.
     * @param seed The seed it grows from.
     */
    private record InstanceKey(int feature, long x, long y, long z, long seed) {}

    /**
     * An instance's blocks in one chunk.
     *
     * @param indices Each block's index in the chunk, as {@link Chunk#index}.
     * @param blocks Each block.
     * @param logs Whether each block may replace leaves already there.
     */
    private record Part(int[] indices, Block[] blocks, boolean[] logs) {}

    /** The large layer's placed features, in order. */
    private final List<PlacedFeature> features;

    /** Chunks generated to the block rules stage. */
    private final Function<ChunkPos, Chunk> base;

    /** The blocks fluids fill with, which aren't ground. */
    private final Set<String> fluidBlocks;

    /** The leaves of every species, which logs may grow through. */
    private final Set<Block> leaves = new HashSet<>();

    /** Each cell's candidates that found a floor. */
    private final ComputeCache<CellKey, List<Candidate>> anchored =
            new ComputeCache<>(CELL_CAPACITY, this::anchor);

    /** How many thinning passes there are; the last one's survivors are the instances. */
    private static final int PASSES = 3;

    /**
     * A cell after a thinning pass.
     *
     * @param cell The cell.
     * @param pass The pass, from 1 to {@value #PASSES}.
     */
    private record PassKey(CellKey cell, int pass) {}

    /** Each cell's candidates after each thinning pass. */
    private final ComputeCache<PassKey, List<Candidate>> passes =
            new ComputeCache<>(CELL_CAPACITY * PASSES, this::thin);

    /** Each instance's blocks, by chunk. */
    private final ComputeCache<InstanceKey, Map<ChunkPos, Part>> layouts =
            new ComputeCache<>(LAYOUT_CAPACITY, this::layout);

    /**
     * Set up the layer.
     *
     * @param features The large layer's placed features, in order.
     * @param base Finds a chunk generated to the block rules stage.
     * @param fluidBlocks The names of blocks fluids fill with.
     */
    LargeFeatures(
            @NonNull List<PlacedFeature> features,
            @NonNull Function<ChunkPos, Chunk> base,
            @NonNull Set<String> fluidBlocks) {
        this.features = List.copyOf(features);
        this.base = base;
        this.fluidBlocks = Set.copyOf(fluidBlocks);
        for (PlacedFeature placed : features) {
            leaves.add(((Feature.Tree) placed.feature()).species().leaves());
        }
    }

    /**
     * Stamp every instance that reaches a chunk into it. A block only goes where there is air, and
     * logs also replace leaves.
     *
     * @param chunk The chunk, generated to the block rules stage.
     * @param pos Where it is.
     */
    void stamp(@NonNull Chunk chunk, @NonNull ChunkPos pos) {
        for (int f = 0; f < features.size(); ++f) {
            final PlacedFeature placed = features.get(f);
            final TreeSpecies species = ((Feature.Tree) placed.feature()).species();
            final int reach = species.maxRadius();
            final int height = species.maxHeight();
            final int[] size = placed.cells().size();
            final long minX = Math.floorDiv(pos.blockX() - reach, size[0]);
            final long maxX = Math.floorDiv(pos.blockX() + World.CHUNK_SIZE - 1 + reach, size[0]);
            final long minY = Math.floorDiv(pos.blockY() - height, size[1]);
            final long maxY = Math.floorDiv(pos.blockY() + World.CHUNK_SIZE - 1, size[1]);
            final long minZ = Math.floorDiv(pos.blockZ() - reach, size[2]);
            final long maxZ = Math.floorDiv(pos.blockZ() + World.CHUNK_SIZE - 1 + reach, size[2]);
            for (long cy = minY; cy <= maxY; ++cy) {
                for (long cz = minZ; cz <= maxZ; ++cz) {
                    for (long cx = minX; cx <= maxX; ++cx) {
                        for (Candidate tree : survivors(new CellKey(f, cx, cy, cz))) {
                            if (reaches(tree, reach, height, pos)) {
                                final Part part =
                                        layouts.get(
                                                        new InstanceKey(
                                                                f,
                                                                tree.x(),
                                                                tree.y(),
                                                                tree.z(),
                                                                tree.seed()))
                                                .get(pos);
                                if (part != null) {
                                    write(chunk, part);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * The instances a placed feature has in a cell, for tests and debug views.
     *
     * @param feature The placed feature's index among the large layer's.
     * @param cellX The cell's x.
     * @param cellY The cell's y.
     * @param cellZ The cell's z.
     * @return The candidates that survived spacing.
     */
    List<Candidate> instances(int feature, long cellX, long cellY, long cellZ) {
        return survivors(new CellKey(feature, cellX, cellY, cellZ));
    }

    /**
     * Whether an instance's bounds reach a chunk.
     *
     * @param tree The instance.
     * @param reach How far it reaches sideways.
     * @param height How high it reaches.
     * @param pos The chunk.
     * @return True if they overlap.
     */
    private static boolean reaches(Candidate tree, int reach, int height, ChunkPos pos) {
        return tree.x() + reach >= pos.blockX()
                && tree.x() - reach < pos.blockX() + World.CHUNK_SIZE
                && tree.z() + reach >= pos.blockZ()
                && tree.z() - reach < pos.blockZ() + World.CHUNK_SIZE
                && tree.y() + height >= pos.blockY()
                && tree.y() < pos.blockY() + World.CHUNK_SIZE;
    }

    /**
     * Write an instance's part into a chunk.
     *
     * @param chunk The chunk.
     * @param part The part.
     */
    private void write(Chunk chunk, Part part) {
        final int size = World.CHUNK_SIZE;
        for (int i = 0; i < part.indices().length; ++i) {
            final int index = part.indices()[i];
            final int x = index % size;
            final int z = index / size % size;
            final int y = index / (size * size);
            final Block there = chunk.getBlock(x, y, z);
            if (World.AIR.equals(there) || (part.logs()[i] && leaves.contains(there))) {
                chunk.setBlock(x, y, z, part.blocks()[i]);
            }
        }
    }

    /**
     * A cell's candidates that find a floor in it and pass their biome and conditions.
     *
     * @param key The cell.
     * @return The candidates, in order.
     */
    private List<Candidate> anchor(CellKey key) {
        final PlacedFeature placed = features.get(key.feature());
        final PlacedFeature.Cells cells = placed.cells();
        final int[] size = cells.size();
        final List<Candidate> out = new ArrayList<>();
        final long cellHash =
                WorldgenHash.combine(
                        placed.salt(),
                        WorldgenHash.combine(key.x(), WorldgenHash.combine(key.y(), key.z())));
        for (int i = 0; i < cells.candidates(); ++i) {
            final long hash = WorldgenHash.combine(cellHash, i);
            final long x = key.x() * size[0] + (long) (unit(hash, SALT_X) * size[0]);
            final long z = key.z() * size[2] + (long) (unit(hash, SALT_Z) * size[2]);
            final long bottom = key.y() * size[1];
            for (long y = bottom + size[1] - 1; y >= bottom; --y) {
                if (!World.AIR.equals(block(x, y, z))) {
                    continue;
                }
                final Block below = block(x, y - 1, z);
                if (World.AIR.equals(below) || fluidBlocks.contains(below.getName())) {
                    continue;
                }
                final Candidate candidate = check(placed, hash, x, y, z, below);
                if (candidate != null) {
                    out.add(candidate);
                }
                break;
            }
        }
        return out;
    }

    /**
     * Check a candidate's biome and conditions at its floor, and work out its spacing.
     *
     * @param placed The placed feature.
     * @param hash The candidate's hash.
     * @param x The open position's x.
     * @param y The open position's y.
     * @param z The open position's z.
     * @param below The block under it.
     * @return The candidate, or null if it doesn't belong there.
     */
    private Candidate check(PlacedFeature placed, long hash, long x, long y, long z, Block below) {
        final ChunkPos pos = ChunkPos.containing(x, y, z);
        final int mask = World.CHUNK_SIZE - 1;
        final String biome =
                base.apply(pos).getBiome((int) x & mask, (int) y & mask, (int) z & mask);
        if (!placed.biomes().contains(biome)) {
            return null;
        }
        final EvalCache cache = new EvalCache();
        final BlockRules.Context context =
                new BlockRules.Context(x, y, z, 0, 0, biome, false, below, cache);
        if (!placed.passes(context)) {
            return null;
        }
        final PlacedFeature.Cells cells = placed.cells();
        double spacing = cells.spacingMax();
        if (cells.by() != null) {
            final double share = Math.clamp(cells.by().value(x, y, z, cache), 0, 1);
            spacing = cells.spacingMax() - (cells.spacingMax() - cells.spacingMin()) * share;
        }
        return new Candidate(
                x, y, z, unit(hash, SALT_STRENGTH), spacing, WorldgenHash.mix64(hash ^ SALT_SEED));
    }

    /**
     * A cell's instances: its candidates after the last thinning pass.
     *
     * @param cell The cell.
     * @return The instances, in order.
     */
    private List<Candidate> survivors(CellKey cell) {
        return passes.get(new PassKey(cell, PASSES));
    }

    /**
     * A cell's candidates after one thinning pass.
     *
     * <ol>
     *   <li>Pass 1 keeps the anchored candidates with no stronger anchored candidate near.
     *   <li>Pass 2 keeps the anchored candidates with no stronger pass 1 survivor near.
     *   <li>Pass 3 keeps the pass 2 survivors with no stronger pass 2 survivor near.
     * </ol>
     *
     * @param key The cell and pass.
     * @return The survivors, in order.
     */
    private List<Candidate> thin(PassKey key) {
        final CellKey cell = key.cell();
        final List<Candidate> mine =
                key.pass() == PASSES
                        ? passes.get(new PassKey(cell, PASSES - 1))
                        : anchored.get(cell);
        if (mine.isEmpty()) {
            return mine;
        }
        final PlacedFeature.Cells cells = features.get(cell.feature()).cells();
        final int[] size = cells.size();
        final long rx = (long) Math.ceil(cells.spacingMax() / size[0]);
        final long rz = (long) Math.ceil(cells.spacingMax() / size[2]);
        final long ry = (long) Math.ceil(cells.spacingMax() / size[1]);
        final List<Candidate> rivals = new ArrayList<>();
        for (long cy = cell.y() - ry; cy <= cell.y() + ry; ++cy) {
            for (long cz = cell.z() - rz; cz <= cell.z() + rz; ++cz) {
                for (long cx = cell.x() - rx; cx <= cell.x() + rx; ++cx) {
                    final CellKey near = new CellKey(cell.feature(), cx, cy, cz);
                    rivals.addAll(
                            key.pass() == 1
                                    ? anchored.get(near)
                                    : passes.get(new PassKey(near, key.pass() - 1)));
                }
            }
        }
        final List<Candidate> out = new ArrayList<>();
        for (Candidate candidate : mine) {
            boolean crowded = false;
            for (Candidate other : rivals) {
                if (!stronger(other, candidate)) {
                    continue;
                }
                final double dx = other.x() - candidate.x();
                final double dy = other.y() - candidate.y();
                final double dz = other.z() - candidate.z();
                if (dx * dx + dy * dy + dz * dz < candidate.spacing() * candidate.spacing()) {
                    crowded = true;
                    break;
                }
            }
            if (!crowded) {
                out.add(candidate);
            }
        }
        return List.copyOf(out);
    }

    /**
     * Whether one candidate outranks another: the higher strength, then the lower position.
     *
     * @param a One candidate.
     * @param b The other.
     * @return True if {@code a} wins.
     */
    private static boolean stronger(Candidate a, Candidate b) {
        if (a.strength() != b.strength()) {
            return a.strength() > b.strength();
        }
        if (a.y() != b.y()) {
            return a.y() < b.y();
        }
        if (a.z() != b.z()) {
            return a.z() < b.z();
        }
        return a.x() < b.x();
    }

    /**
     * Grow an instance and sort its blocks by chunk.
     *
     * @param key The instance.
     * @return Its blocks in each chunk it reaches.
     */
    private Map<ChunkPos, Part> layout(InstanceKey key) {
        final TreeSpecies species =
                ((Feature.Tree) features.get(key.feature()).feature()).species();
        final TreeSpecies.Grown grown = species.grow(key.seed());
        final Map<ChunkPos, List<Integer>> sorted = new HashMap<>();
        for (int i = 0; i < grown.size(); ++i) {
            final ChunkPos pos =
                    ChunkPos.containing(
                            key.x() + grown.xs()[i],
                            key.y() + grown.ys()[i],
                            key.z() + grown.zs()[i]);
            sorted.computeIfAbsent(pos, p -> new ArrayList<>()).add(i);
        }
        final int mask = World.CHUNK_SIZE - 1;
        final Map<ChunkPos, Part> out = new HashMap<>();
        for (Map.Entry<ChunkPos, List<Integer>> entry : sorted.entrySet()) {
            final List<Integer> items = entry.getValue();
            final int[] indices = new int[items.size()];
            final Block[] blocks = new Block[items.size()];
            final boolean[] logs = new boolean[items.size()];
            for (int j = 0; j < items.size(); ++j) {
                final int i = items.get(j);
                indices[j] =
                        Chunk.index(
                                (int) (key.x() + grown.xs()[i]) & mask,
                                (int) (key.y() + grown.ys()[i]) & mask,
                                (int) (key.z() + grown.zs()[i]) & mask);
                blocks[j] = grown.blocks()[i];
                logs[j] = species.trunk().equals(grown.blocks()[i]);
            }
            out.put(entry.getKey(), new Part(indices, blocks, logs));
        }
        return out;
    }

    /**
     * The block at a position, as the terrain and block rules make it.
     *
     * @param x The x.
     * @param y The y.
     * @param z The z.
     * @return The block.
     */
    private Block block(long x, long y, long z) {
        final int mask = World.CHUNK_SIZE - 1;
        return base.apply(ChunkPos.containing(x, y, z))
                .getBlock((int) x & mask, (int) y & mask, (int) z & mask);
    }

    /**
     * One of a candidate's rolls, as a number from 0 up to 1.
     *
     * @param hash The candidate's hash.
     * @param salt Which roll.
     * @return The roll.
     */
    private static double unit(long hash, long salt) {
        return (WorldgenHash.mix64(hash ^ (salt * 0x9E37_79B9_7F4A_7C15L)) >>> UNIT_SHIFT)
                * UNIT_SCALE;
    }
}
