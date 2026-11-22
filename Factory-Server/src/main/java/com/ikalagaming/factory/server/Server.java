package com.ikalagaming.factory.server;

import com.ikalagaming.factory.FactoryServerPlugin;
import com.ikalagaming.factory.networking.LocalConnection;
import com.ikalagaming.factory.networking.base.Connection;
import com.ikalagaming.factory.networking.base.RequestHandler;
import com.ikalagaming.factory.networking.request.serverbound.ServerBoundRequestHandler;
import com.ikalagaming.factory.registry.DefinitionLoader;
import com.ikalagaming.factory.registry.Registries;
import com.ikalagaming.factory.registry.events.LoadingTags;
import com.ikalagaming.factory.save.SaveSummary;
import com.ikalagaming.factory.save.Saves;
import com.ikalagaming.factory.server.events.ServerStopping;
import com.ikalagaming.factory.world.Block;
import com.ikalagaming.factory.world.BlockDefinition;
import com.ikalagaming.factory.world.gen.SpawnFinder;
import com.ikalagaming.factory.world.gen.WorldGenerator;
import com.ikalagaming.factory.world.gen.WorldType;
import com.ikalagaming.factory.world.gen.data.WorldgenCompiler;
import com.ikalagaming.factory.world.gen.data.WorldgenData;
import com.ikalagaming.util.SafeResourceLoader;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/**
 * The game server: loads the definitions, opens a save's world, and ticks it {@value
 * #TICKS_PER_SECOND} times a second. Single player runs one in the same process. See the Starting a
 * Game design doc.
 */
@Slf4j
public class Server {

    /** How many times a second the world ticks. */
    public static final int TICKS_PER_SECOND = 20;

    /** The tag that marks blocks that are fluids. */
    static final String FLUID_TAG = "liquid";

    /** The tag that marks blocks players never start on, like leaves. */
    static final String NO_SPAWN_TAG = "no_spawn";

    /** How long opening a world waits for the definitions to load. */
    private static final long LOAD_WAIT_SECONDS = 60;

    /**
     * The definitions loaded for this run. Replaced on each start; read by event handlers on the
     * event thread.
     */
    @Getter private volatile Registries registries;

    /** Completes when the definitions for this run have loaded. Replaced on each start. */
    private volatile CompletableFuture<Registries> loaded = new CompletableFuture<>();

    private final AtomicBoolean running;
    private final List<ServerBoundRequestHandler> activeConnections;

    /** Guards opening and closing worlds. */
    private final ReentrantLock worldLock = new ReentrantLock();

    /**
     * The open world.
     *
     * @return The world, or null if none is open.
     */
    @Getter private volatile ServerWorld world;

    /** Runs the world's ticks. */
    private ScheduledExecutorService ticker;

    public Server() {
        running = new AtomicBoolean(false);
        activeConnections = Collections.synchronizedList(new ArrayList<>());
    }

    /**
     * Whether the server is running.
     *
     * @return True between {@link #start()} and {@link #stop()}.
     */
    public boolean isRunning() {
        return running.get();
    }

    public void start() {
        if (running.compareAndSet(false, true)) {
            log.info(
                    SafeResourceLoader.getString(
                            "SERVER_STARTING", FactoryServerPlugin.getResourceBundle()));
            registries = new Registries();
            loaded = new CompletableFuture<>();
            new LoadingTags().fire();
        } else {
            log.warn(
                    SafeResourceLoader.getString(
                            "SERVER_ALREADY_RUNNING", FactoryServerPlugin.getResourceBundle()));
        }
    }

    /** Called once the definitions have loaded, so worlds can open. */
    void markLoaded() {
        loaded.complete(registries);
    }

    public void stop() {
        if (running.compareAndSet(true, false)) {
            log.info(
                    SafeResourceLoader.getString(
                            "SERVER_STOPPING", FactoryServerPlugin.getResourceBundle()));
            closeWorld();
            new ServerStopping().fire();
        }
    }

    /**
     * Open a save's world, starting the server if it isn't running. If that save is already open,
     * its world is reused; another open save is closed first. Runs in the background, since finding
     * a new world's spawn can take a while.
     *
     * @param saveFolder The save's folder.
     * @return Completes with the open world, or fails if the save or its world type can't be read.
     */
    public CompletableFuture<ServerWorld> open(@NonNull Path saveFolder) {
        CompletableFuture<ServerWorld> result = new CompletableFuture<>();
        Thread opener =
                new Thread(
                        () -> {
                            try {
                                result.complete(openNow(saveFolder));
                            } catch (Exception e) {
                                log.warn("Could not open the save {}", saveFolder, e);
                                result.completeExceptionally(e);
                            }
                        },
                        "Server open");
        opener.setDaemon(true);
        opener.start();
        return result;
    }

    /**
     * Open a save's world on this thread.
     *
     * @param saveFolder The save's folder.
     * @return The open world.
     * @throws IOException If the save can't be read or its spawn can't be stored.
     */
    private ServerWorld openNow(@NonNull Path saveFolder) throws IOException {
        worldLock.lock();
        try {
            final ServerWorld current = world;
            if (current != null && current.getSaveFolder().equals(saveFolder)) {
                return current;
            }
            closeWorld();
            if (!running.get()) {
                start();
            }
            final Registries loadedRegistries = awaitRegistries();

            final SaveSummary save =
                    Saves.read(saveFolder)
                            .orElseThrow(() -> new IOException("No save in " + saveFolder));
            if (save.worlds().isEmpty()) {
                throw new IOException("The save " + saveFolder + " has no worlds");
            }
            final String worldFolder = save.worlds().getFirst();
            final Saves.WorldInfo info =
                    Saves.readWorld(saveFolder, worldFolder)
                            .orElseThrow(
                                    () -> new IOException("Can't read the world " + worldFolder));

            final WorldGenerator generator = generator(info.worldType(), info.seed());
            SpawnFinder.Spawn spawn = info.spawn();
            if (spawn == null) {
                final long started = System.nanoTime();
                spawn =
                        SpawnFinder.find(
                                generator,
                                generator.getWorldType().spawn(),
                                kinds(loadedRegistries));
                Saves.writeSpawn(saveFolder, worldFolder, spawn);
                log.info(
                        "Found the spawn for {} at {}, {}, {}{} in {} ms",
                        save.name(),
                        spawn.x(),
                        spawn.y(),
                        spawn.z(),
                        spawn.platform() ? " (on a platform)" : "",
                        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
            }

            final ServerWorld opened =
                    new ServerWorld(
                            saveFolder,
                            worldFolder,
                            generator,
                            spawn,
                            ServerWorld.defaultThreads());
            world = opened;
            ticker =
                    Executors.newSingleThreadScheduledExecutor(
                            task -> {
                                Thread thread = new Thread(task, "Server tick");
                                thread.setDaemon(true);
                                return thread;
                            });
            ticker.scheduleAtFixedRate(
                    () -> tickSafely(opened),
                    0,
                    TimeUnit.SECONDS.toNanos(1) / TICKS_PER_SECOND,
                    TimeUnit.NANOSECONDS);
            log.info("Opened {} ({}, seed {})", save.name(), info.worldType(), info.seed());
            return opened;
        } finally {
            worldLock.unlock();
        }
    }

    /**
     * Connect a client in this process to the open world. Requests pass between the two as objects,
     * never serialized, but only through {@link Connection}, so a remote client works the same way.
     * The client then sends {@code Join}.
     *
     * @param client Handles the requests the server sends the client. They arrive on server
     *     threads.
     * @return The client's connection, which sends requests to the server.
     * @throws IllegalStateException If no world is open.
     */
    public Connection connect(@NonNull RequestHandler client) {
        final ServerWorld open = world;
        if (open == null) {
            throw new IllegalStateException("No world is open to connect to");
        }
        final ServerSession session = new ServerSession(open);
        session.setConnection(new LocalConnection(session, client));
        return new LocalConnection(client, session);
    }

    /**
     * Run a tick, keeping the tick loop alive if it throws.
     *
     * @param opened The world to tick.
     */
    private static void tickSafely(@NonNull ServerWorld opened) {
        try {
            opened.tick();
        } catch (RuntimeException e) {
            log.warn("A server tick failed", e);
        }
    }

    /** Close the open world, if there is one, and stop ticking it. */
    private void closeWorld() {
        worldLock.lock();
        try {
            if (ticker != null) {
                ticker.shutdownNow();
                ticker = null;
            }
            if (world != null) {
                world.close();
                world = null;
            }
        } finally {
            worldLock.unlock();
        }
    }

    /**
     * Wait for this run's definitions to finish loading.
     *
     * @return The definitions.
     * @throws IOException If they don't load in time.
     */
    private Registries awaitRegistries() throws IOException {
        try {
            return loaded.get(LOAD_WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while the definitions loaded", e);
        } catch (TimeoutException | java.util.concurrent.ExecutionException e) {
            throw new IOException("The definitions didn't load", e);
        }
    }

    /**
     * Compile a world type into a generator.
     *
     * @param worldType The world type's ID.
     * @param seed The world's seed.
     * @return The generator.
     * @throws IOException If the world type doesn't compile.
     */
    private static WorldGenerator generator(@NonNull String worldType, long seed)
            throws IOException {
        final Path dataFolder = DefinitionLoader.dataFolder();
        final WorldgenCompiler compiler =
                new WorldgenCompiler(
                        WorldgenData.load(List.of(dataFolder)),
                        seed,
                        DefinitionLoader.blockIds(dataFolder));
        final WorldType type =
                compiler.worldType(worldType, null)
                        .orElseThrow(
                                () ->
                                        new IOException(
                                                "The world type "
                                                        + worldType
                                                        + " doesn't compile: "
                                                        + compiler.getDiagnostics()));
        if (!compiler.getDiagnostics().isEmpty()) {
            log.warn("World type {} compiled with: {}", worldType, compiler.getDiagnostics());
        }
        return new WorldGenerator(type, seed);
    }

    /**
     * Which blocks are fluids or can be stood on, from their tags. Blocks that aren't defined, such
     * as ones a world type makes up, count as solid ground.
     *
     * @param registries The definitions.
     * @return The kinds.
     */
    static SpawnFinder.Kinds kinds(@NonNull Registries registries) {
        return new SpawnFinder.Kinds() {
            @Override
            public boolean isFluid(@NonNull Block block) {
                return hasTag(block, FLUID_TAG);
            }

            @Override
            public boolean canStandOn(@NonNull Block block) {
                return !hasTag(block, NO_SPAWN_TAG);
            }

            private boolean hasTag(Block block, String tag) {
                return registries
                        .getBlockRegistry()
                        .find(block.getName())
                        .map(BlockDefinition::tags)
                        .map(tags -> tags.contains(tag))
                        .orElse(false);
            }
        };
    }
}
