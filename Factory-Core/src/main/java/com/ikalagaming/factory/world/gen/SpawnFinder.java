package com.ikalagaming.factory.world.gen;

import com.ikalagaming.factory.world.Block;
import com.ikalagaming.factory.world.Chunk;
import com.ikalagaming.factory.world.ChunkPos;
import com.ikalagaming.factory.world.World;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Finds where new players start, following a world type's {@link SpawnRule}. It is run once, when a
 * world is made, and the result is stored with the world.
 *
 * <p>The search spirals outward from the rule's origin over columns {@value #STEP} blocks apart. In
 * each column it looks down from the top of the rule's height range for the first block that isn't
 * air. That block is a spawn when:
 *
 * <ul>
 *   <li>players can stand on it: it isn't a fluid, and {@link Kinds#canStandOn} accepts it;
 *   <li>it has at least the rule's {@code minAir} blocks of air above it;
 *   <li>nothing but air is above it, up to the top of the range, so it isn't under fluid and has
 *       sky above it within the range;
 *   <li>the 3x3 columns around it reach within one block of the same height, without fluid on top.
 * </ul>
 *
 * <p>If nothing passes within the radius, or the search has looked at {@value #MAX_COLUMNS} chunk
 * columns, the spawn falls back to the origin's column, on top of its highest block (or at the
 * origin's height if it has none), and a {@link SpawnPlatform} is made there.
 *
 * <p>Generation is a pure function of the seed, so the same world always finds the same spawn.
 */
@Slf4j
public final class SpawnFinder {

    /** Blocks between the columns the search tries. */
    static final int STEP = 8;

    /** The most chunk columns the search generates before falling back. */
    static final int MAX_COLUMNS = 64;

    /** How far the floor of the columns around a spot may differ from it. */
    static final int MAX_STEP_HEIGHT = 1;

    /**
     * What kinds of block a spawn cares about.
     *
     * @see SpawnFinder
     */
    public interface Kinds {
        /**
         * Whether a block is a fluid, like water.
         *
         * @param block The block.
         * @return True for fluids.
         */
        boolean isFluid(@NonNull Block block);

        /**
         * Whether a player could start standing on a block: not something like leaves.
         *
         * @param block A block that isn't air or a fluid.
         * @return True if a player can start on it.
         */
        boolean canStandOn(@NonNull Block block);
    }

    /**
     * Where new players start.
     *
     * @param x The x of the block a player's feet are in.
     * @param y The y of the block a player's feet are in, one above the floor.
     * @param z The z of the block a player's feet are in.
     * @param platform Whether nothing suitable was found, so a {@link SpawnPlatform} is made here.
     */
    public record Spawn(int x, int y, int z, boolean platform) {}

    /** Makes the chunks the search reads. */
    private final WorldGenerator generator;

    /** The rule being followed. */
    private final SpawnRule rule;

    /** Which blocks are fluids or can be stood on. */
    private final Kinds kinds;

    /** Chunks generated so far, which nearby columns share. */
    private final Map<ChunkPos, Chunk> chunks = new HashMap<>();

    /** Chunk columns looked at so far, as packed x and z. */
    private final Set<Long> columns = new HashSet<>();

    /**
     * A search.
     *
     * @param generator Makes the chunks the search reads.
     * @param rule The rule to follow.
     * @param kinds Which blocks are fluids or can be stood on.
     */
    private SpawnFinder(
            @NonNull WorldGenerator generator, @NonNull SpawnRule rule, @NonNull Kinds kinds) {
        this.generator = generator;
        this.rule = rule;
        this.kinds = kinds;
    }

    /**
     * Find where new players start in a world.
     *
     * @param generator Makes the world's chunks.
     * @param rule The world type's spawn rule.
     * @param kinds Which blocks are fluids or can be stood on.
     * @return The spawn, which is a platform when nothing suitable was found.
     */
    public static Spawn find(
            @NonNull WorldGenerator generator, @NonNull SpawnRule rule, @NonNull Kinds kinds) {
        return new SpawnFinder(generator, rule, kinds).search();
    }

    /**
     * Spiral out from the origin until a column passes or the search runs out.
     *
     * @return The spawn.
     */
    private Spawn search() {
        final int rings = rule.radius() / STEP;
        for (int ring = 0; ring <= rings; ++ring) {
            for (int[] offset : ring(ring)) {
                final int x = rule.originX() + offset[0] * STEP;
                final int z = rule.originZ() + offset[1] * STEP;
                if (columns.size() > MAX_COLUMNS) {
                    log.debug("Spawn search gave up after {} chunk columns", columns.size());
                    return fallback();
                }
                final Integer floor = floorAt(x, z);
                if (floor != null && isFlatAround(x, z, floor)) {
                    log.debug(
                            "Spawn found at {}, {}, {} after {} chunk columns",
                            x,
                            floor + 1,
                            z,
                            columns.size());
                    return new Spawn(x, floor + 1, z, false);
                }
            }
        }
        return fallback();
    }

    /**
     * The grid offsets on one ring of the spiral, in a fixed order.
     *
     * @param ring How many steps out the ring is; 0 is the origin.
     * @return The offsets, in steps.
     */
    static int[][] ring(int ring) {
        if (ring == 0) {
            return new int[][] {{0, 0}};
        }
        final int side = ring * 2;
        final int[][] offsets = new int[side * 4][];
        int index = 0;
        // Along each side in turn, so every point on the ring comes once
        for (int i = 0; i < side; ++i) {
            offsets[index++] = new int[] {-ring + i, -ring};
        }
        for (int i = 0; i < side; ++i) {
            offsets[index++] = new int[] {ring, -ring + i};
        }
        for (int i = 0; i < side; ++i) {
            offsets[index++] = new int[] {ring - i, ring};
        }
        for (int i = 0; i < side; ++i) {
            offsets[index++] = new int[] {-ring, ring - i};
        }
        return offsets;
    }

    /**
     * The floor a player could start on in a column, if it passes.
     *
     * @param x The column's x.
     * @param z The column's z.
     * @return The floor's y, or null if the column has no floor a player can start on.
     */
    private Integer floorAt(int x, int z) {
        final int top = rule.maxY() + rule.minAir();
        int air = 0;
        for (int y = top; y >= rule.minY(); --y) {
            final Block block = block(x, y, z);
            if (isAir(block)) {
                ++air;
                continue;
            }
            if (kinds.isFluid(block) || !kinds.canStandOn(block) || y > rule.maxY()) {
                return null;
            }
            return air >= rule.minAir() ? y : null;
        }
        return null;
    }

    /**
     * Whether the columns around a spot reach about the same height.
     *
     * @param x The spot's x.
     * @param z The spot's z.
     * @param floor The spot's floor.
     * @return True if each neighboring column's highest block is within {@value #MAX_STEP_HEIGHT}
     *     of the floor and isn't a fluid.
     */
    private boolean isFlatAround(int x, int z, int floor) {
        for (int dx = -1; dx <= 1; ++dx) {
            for (int dz = -1; dz <= 1; ++dz) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                boolean found = false;
                for (int y = floor + MAX_STEP_HEIGHT; y >= floor - MAX_STEP_HEIGHT; --y) {
                    final Block block = block(x + dx, y, z + dz);
                    if (isAir(block)) {
                        continue;
                    }
                    if (kinds.isFluid(block)) {
                        return false;
                    }
                    // Anything solid sitting higher than the step would block the spot
                    found = isAir(block(x + dx, y + 1, z + dz));
                    break;
                }
                if (!found) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * The spawn when nothing passed: on top of the origin column's highest block, or at the
     * origin's height if the column is empty, with a platform made there.
     *
     * @return The spawn.
     */
    private Spawn fallback() {
        final int x = rule.originX();
        final int z = rule.originZ();
        int floor = rule.originY() - 1;
        for (int y = rule.maxY(); y >= rule.minY(); --y) {
            if (!isAir(block(x, y, z))) {
                floor = y;
                break;
            }
        }
        log.debug("No spawn passed, so a platform goes at {}, {}, {}", x, floor + 1, z);
        return new Spawn(x, floor + 1, z, true);
    }

    /**
     * The block at a position, generating its chunk if needed.
     *
     * @param x The x.
     * @param y The y.
     * @param z The z.
     * @return The block.
     */
    private Block block(int x, int y, int z) {
        final ChunkPos pos = ChunkPos.containing(x, y, z);
        Chunk chunk = chunks.get(pos);
        if (chunk == null) {
            columns.add(((long) pos.x() << 32) | (pos.z() & 0xFFFF_FFFFL));
            chunk = generator.generate(pos);
            chunks.put(pos, chunk);
        }
        final int mask = World.CHUNK_SIZE - 1;
        return chunk.getBlock(x & mask, y & mask, z & mask);
    }

    /**
     * Whether a block is air.
     *
     * @param block The block.
     * @return True for air.
     */
    private static boolean isAir(@NonNull Block block) {
        return World.AIR_NAME.equals(block.getName());
    }
}
