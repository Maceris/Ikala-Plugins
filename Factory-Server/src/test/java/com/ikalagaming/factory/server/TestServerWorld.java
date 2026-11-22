package com.ikalagaming.factory.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.factory.world.Chunk;
import com.ikalagaming.factory.world.ChunkPos;
import com.ikalagaming.factory.world.World;
import com.ikalagaming.factory.world.gen.SpawnFinder;
import com.ikalagaming.factory.world.gen.WorldGenerator;
import com.ikalagaming.factory.world.gen.WorldType;
import com.ikalagaming.factory.world.gen.data.Kind;
import com.ikalagaming.factory.world.gen.data.WorldgenCompiler;
import com.ikalagaming.factory.world.gen.data.WorldgenData;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

/** Tests for a world open on the server. */
class TestServerWorld {

    /** An empty world: air everywhere. */
    private static WorldGenerator emptyWorld() {
        Map<Kind, Map<String, String>> files = new EnumMap<>(Kind.class);
        files.computeIfAbsent(Kind.PARAMETER, k -> new TreeMap<>()).put("t:p", "{density:0.0}");
        files.computeIfAbsent(Kind.BIOME, k -> new TreeMap<>())
                .put("t:plain", "{climate:{p:[D;-1.0,1.0]}}");
        files.computeIfAbsent(Kind.WORLD_TYPE, k -> new TreeMap<>())
                .put(
                        "t:world",
                        "{default_block:\"t:stone\", parameters:[T;\"t:p\"],"
                                + " biomes:[T;\"t:plain\"], fallback_biome:\"t:plain\","
                                + " density:{type:\"constant\", value:-1.0}}");
        WorldgenCompiler compiler = new WorldgenCompiler(WorldgenData.fromText(files), 1, null);
        WorldType type =
                compiler.worldType("t:world", null)
                        .orElseThrow(() -> new AssertionError(compiler.getDiagnostics()));
        return new WorldGenerator(type, 1);
    }

    @Test
    void testChunksGenerateWithPlatform() throws Exception {
        WorldGenerator generator = emptyWorld();
        SpawnFinder.Spawn spawn = new SpawnFinder.Spawn(0, 64, 0, true);
        try (ServerWorld world = new ServerWorld(Path.of("save"), "world", generator, spawn, 2)) {
            ChunkPos below = ChunkPos.containing(0, 63, 0);
            Chunk chunk = world.chunk(below).get(30, TimeUnit.SECONDS);

            assertEquals(generator.getWorldType().defaultBlock(), chunk.getBlock(0, 63 & 15, 0));
            assertEquals(World.AIR, chunk.getBlock(0, 0, 0));
            assertTrue(world.loadedChunk(below).isPresent());
            assertEquals(1, world.loadedChunkCount());

            // Somewhere else is only air
            Chunk far = world.chunk(new ChunkPos(10, 10, 10)).get(30, TimeUnit.SECONDS);
            assertTrue(far.isUniform());

            assertTrue(world.unload(below));
            assertFalse(world.loadedChunk(below).isPresent());
            assertFalse(world.unload(below));

            world.tick();
            assertEquals(1, world.getTicks());
        }
    }
}
