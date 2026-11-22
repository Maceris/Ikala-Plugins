package com.ikalagaming.factory.server;

import com.ikalagaming.ecs.Schedule;
import com.ikalagaming.ecs.World;
import com.ikalagaming.factory.kvt.Node;
import com.ikalagaming.factory.networking.base.Connection;
import com.ikalagaming.factory.networking.request.clientbound.ChunkData;
import com.ikalagaming.factory.networking.request.clientbound.ChunkUnload;
import com.ikalagaming.factory.networking.request.clientbound.Joined;
import com.ikalagaming.factory.networking.request.clientbound.PlayerState;
import com.ikalagaming.factory.networking.request.clientbound.TimeUpdate;
import com.ikalagaming.factory.sim.Fixed;
import com.ikalagaming.factory.sim.Frames;
import com.ikalagaming.factory.sim.MoveMode;
import com.ikalagaming.factory.sim.Movement;
import com.ikalagaming.factory.sim.PlayerInput;
import com.ikalagaming.factory.sim.PlayerSimulation;
import com.ikalagaming.factory.world.Block;
import com.ikalagaming.factory.world.Chunk;
import com.ikalagaming.factory.world.ChunkCodec;
import com.ikalagaming.factory.world.ChunkPos;
import com.ikalagaming.factory.world.WorldTime;
import com.ikalagaming.factory.world.gen.SpawnFinder;
import com.ikalagaming.factory.world.gen.SpawnPlatform;
import com.ikalagaming.factory.world.gen.WorldGenerator;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * One open world on the server: its generator, its spawn, the chunks loaded so far and the pool
 * that generates them. Chunks nobody changed are never saved; they are generated again from the
 * seed when needed. See the Starting a Game design doc.
 */
@Slf4j
public final class ServerWorld implements AutoCloseable {

    /** Half a block, to put a player in the middle of their spawn block. */
    private static final double HALF_BLOCK = 0.5;

    /** How long {@link #close()} waits for generation threads to stop. */
    private static final long CLOSE_WAIT_SECONDS = 5;

    /** How many chunks out from a player's chunk are sent, sideways. */
    public static final int VIEW_RADIUS = 8;

    /** How many chunks up and down from a player's chunk are sent. */
    public static final int VERTICAL_RADIUS = 4;

    /**
     * How many chunks past the view radius a sent chunk may be before the client is told to drop
     * it, so walking back and forth over a chunk border doesn't send the same chunks again.
     */
    static final int DROP_MARGIN = 1;

    /** How many chunks each player may be waiting on at once, so the nearest come first. */
    static final int MAX_PENDING = 32;

    /** How long a chunk no player needs stays loaded, in ticks. */
    static final long IDLE_TICKS = 30L * Server.TICKS_PER_SECOND;

    /** How often players are sent the time, in ticks. */
    static final long TIME_EVERY_TICKS = 5L * Server.TICKS_PER_SECOND;

    /** How often idle chunks are dropped, in ticks. */
    static final long UNLOAD_EVERY_TICKS = Server.TICKS_PER_SECOND;

    /**
     * The save the world is in.
     *
     * @return The save's folder.
     */
    @Getter private final Path saveFolder;

    /**
     * The world's folder name inside the save.
     *
     * @return The folder name.
     */
    @Getter private final String worldFolder;

    /**
     * Makes the world's chunks.
     *
     * @return The generator.
     */
    @Getter private final WorldGenerator generator;

    /**
     * Where new players start.
     *
     * @return The spawn.
     */
    @Getter private final SpawnFinder.Spawn spawn;

    /** Chunks loaded or being generated. */
    private final Map<ChunkPos, CompletableFuture<Chunk>> chunks = new ConcurrentHashMap<>();

    /** Generates chunks at low priority, so the client's frames stay smooth. */
    private final ExecutorService generation;

    /** Ticks run so far. */
    private final AtomicLong ticks = new AtomicLong();

    /** Command frames run so far; the next frame's number. */
    private final AtomicLong frames = new AtomicLong();

    /** The world's clock, in ticks; see {@link WorldTime}. */
    private final AtomicLong time;

    /**
     * Work for the tick thread, from requests and finished chunks, run before each frame and tick.
     */
    private final Queue<Runnable> tasks = new ConcurrentLinkedQueue<>();

    /** Players and anything else simulated in command frames. Tick thread only. */
    private final World sim = new World();

    /** The systems that step {@link #sim} each frame. */
    private final Schedule schedule = PlayerSimulation.schedule();

    /** Players in the world, by id. Tick thread only. */
    private final Map<String, ServerPlayer> players = new LinkedHashMap<>();

    /** When each chunk was last near a player, in ticks. Tick thread only. */
    private final Map<ChunkPos, Long> lastWanted = new HashMap<>();

    /**
     * Open a world.
     *
     * @param saveFolder The save the world is in.
     * @param worldFolder The world's folder name inside the save.
     * @param generator Makes the world's chunks.
     * @param spawn Where new players start.
     * @param threads How many threads generate chunks.
     * @param time The world's clock, in ticks.
     */
    public ServerWorld(
            @NonNull Path saveFolder,
            @NonNull String worldFolder,
            @NonNull WorldGenerator generator,
            @NonNull SpawnFinder.Spawn spawn,
            int threads,
            long time) {
        this.time = new AtomicLong(time);
        this.saveFolder = saveFolder;
        this.worldFolder = worldFolder;
        this.generator = generator;
        this.spawn = spawn;
        final AtomicInteger count = new AtomicInteger();
        generation =
                new ThreadPoolExecutor(
                        Math.max(1, threads),
                        Math.max(1, threads),
                        0,
                        TimeUnit.MILLISECONDS,
                        new LinkedBlockingQueue<>(),
                        task -> {
                            Thread thread =
                                    new Thread(task, "World generation " + count.incrementAndGet());
                            thread.setDaemon(true);
                            thread.setPriority(Thread.MIN_PRIORITY);
                            return thread;
                        });
    }

    /**
     * How many threads generate chunks when not told: all but two of the processors, so the
     * client's render and main threads keep theirs.
     *
     * @return The thread count, at least 1.
     */
    public static int defaultThreads() {
        return Math.max(1, Runtime.getRuntime().availableProcessors() - 2);
    }

    /**
     * A chunk, generating it if it isn't loaded.
     *
     * @param pos The chunk.
     * @return Completes with the chunk once it is ready. Generating it again after it was unloaded
     *     gives the same blocks.
     */
    public CompletableFuture<Chunk> chunk(@NonNull ChunkPos pos) {
        return chunks.computeIfAbsent(
                pos, key -> CompletableFuture.supplyAsync(() -> generate(key), generation));
    }

    /**
     * A chunk, only if it is already loaded.
     *
     * @param pos The chunk.
     * @return The chunk, or empty if it isn't loaded or is still being generated.
     */
    public Optional<Chunk> loadedChunk(@NonNull ChunkPos pos) {
        CompletableFuture<Chunk> chunk = chunks.get(pos);
        if (chunk == null || !chunk.isDone() || chunk.isCompletedExceptionally()) {
            return Optional.empty();
        }
        return Optional.of(chunk.join());
    }

    /**
     * Drop a chunk. Asking for it again generates it again.
     *
     * @param pos The chunk.
     * @return Whether it was loaded or being generated.
     */
    public boolean unload(@NonNull ChunkPos pos) {
        CompletableFuture<Chunk> chunk = chunks.remove(pos);
        if (chunk == null) {
            return false;
        }
        chunk.cancel(false);
        return true;
    }

    /**
     * How many chunks are loaded or being generated.
     *
     * @return The count.
     */
    public int loadedChunkCount() {
        return chunks.size();
    }

    /**
     * The world's clock.
     *
     * @return The time, in ticks; see {@link WorldTime}.
     */
    public long getTime() {
        return time.get();
    }

    /**
     * How many ticks have run.
     *
     * @return The tick count.
     */
    public long getTicks() {
        return ticks.get();
    }

    /**
     * How many command frames have run.
     *
     * @return The frame count, which is also the number of the next frame.
     */
    public long getFrames() {
        return frames.get();
    }

    /**
     * Run something on the tick thread, before the next frame or tick, whichever comes first. Safe
     * from any thread.
     *
     * @param task The work.
     */
    public void post(@NonNull Runnable task) {
        tasks.add(task);
    }

    /**
     * Add a player at the spawn and tell their client it joined. Tick thread only.
     *
     * @param name The name the player joins as.
     * @param connection Sends requests to the player's client.
     * @return The player.
     */
    ServerPlayer join(@NonNull String name, @NonNull Connection connection) {
        final long entity = sim.create();
        final ServerPlayer player =
                new ServerPlayer(
                        UUID.randomUUID().toString(),
                        name,
                        connection,
                        entity,
                        spawn.x() + HALF_BLOCK,
                        spawn.y(),
                        spawn.z() + HALF_BLOCK);
        sim.set(
                PlayerSimulation.MOVEMENT,
                entity,
                Movement.at(
                        MoveMode.FLYING,
                        Fixed.ofMeters(spawn.x()) + Fixed.ONE / 2,
                        Fixed.ofMeters(spawn.y()),
                        Fixed.ofMeters(spawn.z()) + Fixed.ONE / 2));
        sim.set(PlayerSimulation.INPUT, entity, PlayerInput.NONE);
        players.put(player.getId(), player);
        connection.send(
                new Joined(
                        player.getId(),
                        player.getX(),
                        player.getY(),
                        player.getZ(),
                        0,
                        0,
                        VIEW_RADIUS,
                        VERTICAL_RADIUS,
                        frames.get()));
        connection.send(new TimeUpdate(time.get(), Server.TICKS_PER_SECOND));
        log.info("{} joined at {}, {}, {}", name, player.getX(), player.getY(), player.getZ());
        return player;
    }

    /**
     * The players in the world. Tick thread only.
     *
     * @return The players.
     */
    Collection<ServerPlayer> players() {
        return players.values();
    }

    /** Run the work posted to the tick thread. */
    private void runTasks() {
        Runnable task = tasks.poll();
        while (task != null) {
            try {
                task.run();
            } catch (RuntimeException e) {
                log.warn("A server task failed", e);
            }
            task = tasks.poll();
        }
    }

    /**
     * Run one command frame. Called every {@value Frames#FRAME_MILLIS} ms by the server, on the
     * same thread as {@link #tick()}.
     */
    void frame() {
        runTasks();
        final long frame = frames.get();
        for (ServerPlayer player : players.values()) {
            sim.set(PlayerSimulation.INPUT, player.getEntity(), player.inputs.take(frame));
        }
        schedule.run(sim);
        for (ServerPlayer player : players.values()) {
            final Movement movement = sim.get(PlayerSimulation.MOVEMENT, player.getEntity());
            player.moveTo(
                    (double) movement.x() / Fixed.ONE,
                    (double) movement.y() / Fixed.ONE,
                    (double) movement.z() / Fixed.ONE);
            final PlayerState state =
                    PlayerState.of(
                            frame, movement, player.inputs.buffered(frame), player.inputs.misses());
            if (player.echoNanos != 0) {
                state.setEchoNanos(player.echoNanos);
                state.setHeldNanos(System.nanoTime() - player.echoReceivedNanos);
            }
            player.getConnection().send(state);
        }
        frames.incrementAndGet();
    }

    /** Run one tick. Called {@value Server#TICKS_PER_SECOND} times a second by the server. */
    void tick() {
        final long now = ticks.incrementAndGet();
        final long clock = time.incrementAndGet();
        runTasks();
        for (ServerPlayer player : players.values()) {
            stream(player);
        }
        if (now % TIME_EVERY_TICKS == 0) {
            for (ServerPlayer player : players.values()) {
                player.getConnection().send(new TimeUpdate(clock, Server.TICKS_PER_SECOND));
            }
        }
        if (now % UNLOAD_EVERY_TICKS == 0) {
            unloadIdle(now);
        }
    }

    /**
     * Send a player the chunks near them, nearest first, and tell their client to drop chunks they
     * left behind.
     *
     * @param player The player.
     */
    private void stream(@NonNull ServerPlayer player) {
        final ChunkPos here = player.chunk();
        if (!here.equals(player.center)) {
            player.center = here;
            player.wanted = nearestFirst(here);
            final List<ChunkPos> dropped = new ArrayList<>();
            for (ChunkPos pos : player.sent) {
                if (!isNear(here, pos, DROP_MARGIN)) {
                    dropped.add(pos);
                }
            }
            for (ChunkPos pos : dropped) {
                player.sent.remove(pos);
                player.getConnection().send(new ChunkUnload(pos.x(), pos.y(), pos.z()));
            }
        }
        if (player.pending.size() >= MAX_PENDING) {
            return;
        }
        for (ChunkPos pos : player.wanted) {
            if (player.pending.size() >= MAX_PENDING) {
                return;
            }
            if (player.sent.contains(pos) || player.pending.contains(pos)) {
                continue;
            }
            player.pending.add(pos);
            chunk(pos)
                    .thenApply(ChunkCodec::encode)
                    .whenComplete((node, error) -> post(() -> deliver(player, pos, node, error)));
        }
    }

    /**
     * Send a chunk that finished generating, if the player still wants it. Tick thread only.
     *
     * @param player The player it was for.
     * @param pos The chunk.
     * @param node The encoded chunk, or null if it failed.
     * @param error Why it failed, or null.
     */
    private void deliver(
            @NonNull ServerPlayer player, @NonNull ChunkPos pos, Node node, Throwable error) {
        player.pending.remove(pos);
        if (error != null || node == null) {
            if (!(error instanceof CancellationException)
                    && !(error != null && error.getCause() instanceof CancellationException)) {
                log.warn("Chunk {} couldn't be made", pos, error);
            }
            return;
        }
        if (!players.containsKey(player.getId()) || !isNear(player.chunk(), pos, 0)) {
            return;
        }
        player.sent.add(pos);
        player.getConnection().send(new ChunkData(pos.x(), pos.y(), pos.z(), node));
    }

    /**
     * Drop chunks no player has been near for {@value #IDLE_TICKS} ticks.
     *
     * @param now The current tick.
     */
    private void unloadIdle(long now) {
        for (ServerPlayer player : players.values()) {
            for (ChunkPos pos : player.wanted) {
                lastWanted.put(pos, now);
            }
        }
        for (ChunkPos pos : List.copyOf(chunks.keySet())) {
            final long wanted = lastWanted.getOrDefault(pos, 0L);
            if (now - wanted > IDLE_TICKS) {
                unload(pos);
                lastWanted.remove(pos);
            }
        }
    }

    /**
     * The chunks within the view of a chunk, nearest first.
     *
     * @param center The chunk the player is in.
     * @return The chunks.
     */
    static List<ChunkPos> nearestFirst(@NonNull ChunkPos center) {
        final List<ChunkPos> result = new ArrayList<>();
        for (int dy = -VERTICAL_RADIUS; dy <= VERTICAL_RADIUS; ++dy) {
            for (int dz = -VIEW_RADIUS; dz <= VIEW_RADIUS; ++dz) {
                for (int dx = -VIEW_RADIUS; dx <= VIEW_RADIUS; ++dx) {
                    result.add(new ChunkPos(center.x() + dx, center.y() + dy, center.z() + dz));
                }
            }
        }
        result.sort(
                Comparator.comparingLong(
                        pos -> {
                            final long dx = pos.x() - center.x();
                            final long dy = pos.y() - center.y();
                            final long dz = pos.z() - center.z();
                            return dx * dx + dy * dy + dz * dz;
                        }));
        return result;
    }

    /**
     * Whether a chunk is within the view of a chunk.
     *
     * @param center The chunk the player is in.
     * @param pos The chunk.
     * @param margin How many chunks past the view still count.
     * @return True if it is near enough.
     */
    static boolean isNear(@NonNull ChunkPos center, @NonNull ChunkPos pos, int margin) {
        return Math.abs(pos.x() - center.x()) <= VIEW_RADIUS + margin
                && Math.abs(pos.z() - center.z()) <= VIEW_RADIUS + margin
                && Math.abs(pos.y() - center.y()) <= VERTICAL_RADIUS + margin;
    }

    /**
     * Generate a chunk, with the spawn platform stamped in if it reaches the chunk.
     *
     * @param pos The chunk.
     * @return The chunk.
     */
    private Chunk generate(@NonNull ChunkPos pos) {
        Chunk chunk = generator.generate(pos);
        if (spawn.platform() && SpawnPlatform.touches(pos, spawn)) {
            final Block floor = generator.getWorldType().defaultBlock();
            SpawnPlatform.stamp(chunk, pos, spawn, floor);
        }
        return chunk;
    }

    @Override
    public void close() {
        generation.shutdownNow();
        try {
            if (!generation.awaitTermination(CLOSE_WAIT_SECONDS, TimeUnit.SECONDS)) {
                log.warn("World generation threads didn't stop within {} s", CLOSE_WAIT_SECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        chunks.clear();
        tasks.clear();
    }
}
