package com.ikalagaming.factory.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.factory.networking.base.Connection;
import com.ikalagaming.factory.networking.base.Request;
import com.ikalagaming.factory.networking.request.clientbound.ChunkData;
import com.ikalagaming.factory.networking.request.clientbound.ChunkUnload;
import com.ikalagaming.factory.networking.request.clientbound.Joined;
import com.ikalagaming.factory.networking.request.clientbound.TimeUpdate;
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
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

/** Tests for a world open on the server. */
class TestServerWorld {

    /** An empty world: air everywhere. */
    static WorldGenerator emptyWorld() {
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
        try (ServerWorld world =
                new ServerWorld(Path.of("save"), "world", generator, spawn, 2, 0)) {
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

            long time = world.getTime();
            world.tick();
            assertEquals(1, world.getTicks());
            assertEquals(time + 1, world.getTime());
        }
    }

    /** Records what the server sends a client. */
    private static final class Recorder implements Connection {
        final List<Request> sent = new CopyOnWriteArrayList<>();

        @Override
        public void connect() {}

        @Override
        public void disconnect() {}

        @Override
        public boolean isLocal() {
            return true;
        }

        @Override
        public void receive(Request request) {}

        @Override
        public void send(Request request) {
            sent.add(request);
        }

        long count(Class<?> type) {
            return sent.stream().filter(type::isInstance).count();
        }
    }

    /** Tick until a condition holds, failing after a while. */
    private static void tickUntil(ServerWorld world, java.util.function.BooleanSupplier done)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
        while (!done.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("Timed out");
            }
            world.tick();
            Thread.sleep(1);
        }
    }

    @Test
    void testStreamsNearestFirstAndDropsBehind() throws Exception {
        SpawnFinder.Spawn spawn = new SpawnFinder.Spawn(0, 64, 0, false);
        try (ServerWorld world =
                new ServerWorld(Path.of("save"), "world", emptyWorld(), spawn, 4, 0)) {
            Recorder client = new Recorder();
            world.post(() -> world.join("Tester", client));
            int all =
                    (2 * ServerWorld.VIEW_RADIUS + 1)
                            * (2 * ServerWorld.VIEW_RADIUS + 1)
                            * (2 * ServerWorld.VERTICAL_RADIUS + 1);
            tickUntil(world, () -> client.count(ChunkData.class) >= all);

            Joined joined = (Joined) client.sent.getFirst();
            assertEquals(0.5, joined.getX());
            assertEquals(64.0, joined.getY());
            TimeUpdate time = (TimeUpdate) client.sent.get(1);
            assertEquals(Server.TICKS_PER_SECOND, time.getTicksPerSecond());
            ChunkData first =
                    (ChunkData)
                            client.sent.stream()
                                    .filter(ChunkData.class::isInstance)
                                    .findFirst()
                                    .orElseThrow();
            // Chunks are asked for nearest first; threads finish in any order, so the first to
            // arrive is one of the nearest
            long dy = first.getY() - 4L;
            assertTrue(
                    first.getX() * (long) first.getX()
                                    + dy * dy
                                    + first.getZ() * (long) first.getZ()
                            <= 4);
            assertEquals(all, client.count(ChunkData.class));

            // Move 10 chunks east: the chunks more than a chunk past the view behind are dropped
            ServerPlayer player = world.players().iterator().next();
            world.post(() -> player.moveTo(160.5, 64, 0.5));
            int width = 2 * ServerWorld.VIEW_RADIUS + 1;
            int height = 2 * ServerWorld.VERTICAL_RADIUS + 1;
            int dropped = (10 - ServerWorld.DROP_MARGIN) * width * height;
            tickUntil(world, () -> client.count(ChunkUnload.class) >= dropped);
            world.tick();
            assertEquals(dropped, client.count(ChunkUnload.class));
        }
    }
}
