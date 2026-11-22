package com.ikalagaming.factory.client;

import com.ikalagaming.factory.registry.DefinitionLoader;
import com.ikalagaming.factory.registry.Registries;
import com.ikalagaming.factory.world.ChunkBlockView;
import com.ikalagaming.factory.world.World;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Which blocks stop movement, for predicting it. Read from the client's own copy of the block
 * definitions, the same data the server reads, until the server sends its definitions when a player
 * joins.
 */
@Slf4j
final class BlockCollisions {

    /** Guards loading. */
    private static final ReentrantLock LOCK = new ReentrantLock();

    /** The table, once loaded. */
    private static volatile Map<String, Boolean> loaded;

    /** Not made. */
    private BlockCollisions() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }

    /**
     * Whether each block, by name, is solid, loading the definitions the first time.
     *
     * @return The table; if the definitions can't be read, one where only air isn't solid.
     */
    static Map<String, Boolean> solidByName() {
        Map<String, Boolean> table = loaded;
        if (table != null) {
            return table;
        }
        LOCK.lock();
        try {
            if (loaded == null) {
                loaded = load();
            }
            return loaded;
        } finally {
            LOCK.unlock();
        }
    }

    /**
     * Read the block definitions.
     *
     * @return The table.
     */
    private static Map<String, Boolean> load() {
        try {
            final Registries registries = new Registries();
            DefinitionLoader.loadAll(DefinitionLoader.dataFolder(), registries);
            return ChunkBlockView.solidByName(registries.getBlockRegistry());
        } catch (RuntimeException e) {
            log.warn("Could not read the block definitions; every block will be solid", e);
            return Map.of(World.AIR_NAME, false);
        }
    }
}
