package com.ikalagaming.factory.server;

import com.ikalagaming.factory.FactoryServerPlugin;
import com.ikalagaming.factory.auth.HostIdentity;
import com.ikalagaming.factory.chat.ChatText;
import com.ikalagaming.factory.networking.LocalConnection;
import com.ikalagaming.factory.networking.SimulatedLink;
import com.ikalagaming.factory.networking.base.Connection;
import com.ikalagaming.factory.networking.base.RequestHandler;
import com.ikalagaming.factory.networking.request.serverbound.ServerBoundRequestHandler;
import com.ikalagaming.factory.networking.udp.PacketEndpoint;
import com.ikalagaming.factory.networking.udp.UdpConnection;
import com.ikalagaming.factory.networking.udp.UdpServer;
import com.ikalagaming.factory.registry.ContentFolders;
import com.ikalagaming.factory.registry.DefinitionLoader;
import com.ikalagaming.factory.registry.Registries;
import com.ikalagaming.factory.registry.events.LoadingTags;
import com.ikalagaming.factory.save.Accounts;
import com.ikalagaming.factory.save.Hosting;
import com.ikalagaming.factory.save.SaveSummary;
import com.ikalagaming.factory.save.Saves;
import com.ikalagaming.factory.server.events.ServerStopping;
import com.ikalagaming.factory.world.Block;
import com.ikalagaming.factory.world.BlockDefinition;
import com.ikalagaming.factory.world.ChunkBlockView;
import com.ikalagaming.factory.world.gen.SpawnFinder;
import com.ikalagaming.factory.world.gen.WorldGenerator;
import com.ikalagaming.factory.world.gen.WorldType;
import com.ikalagaming.factory.world.gen.data.WorldgenCompiler;
import com.ikalagaming.factory.world.gen.data.WorldgenData;
import com.ikalagaming.launcher.AppPaths;
import com.ikalagaming.permissions.PermissionStore;
import com.ikalagaming.permissions.Subject;
import com.ikalagaming.util.SafeResourceLoader;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/**
 * The game server: loads the definitions, opens a save's world, and runs it: command frames every
 * 16 ms and world ticks {@value #TICKS_PER_SECOND} times a second, on one thread (see {@link
 * ServerLoop}). Single player runs one in the same process. See the Starting a Game and Command
 * Frames design docs.
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

    /** How long closing a world waits for the frame or tick it is on to finish. */
    private static final long LOOP_STOP_MILLIS = 2000;

    /** Simulated links made for local clients, stopped when the world closes. */
    private final List<SimulatedLink> links = new CopyOnWriteArrayList<>();

    /** Runs the open world's frames and ticks. */
    private ServerLoop loop;

    /** The file in a save with its permission groups and players' own permissions. */
    public static final String PERMISSIONS_FILE = "permissions.yml";

    /** The system property that makes the server listen on a port, even in single player. */
    public static final String PORT_PROPERTY = "ikala.server.port";

    /** Players connected over the network, or null when not listening. */
    private UdpServer udp;

    /** The thread {@link #loop} runs on. */
    private Thread loopThread;

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
                            ServerWorld.defaultThreads(),
                            info.time(),
                            ChunkBlockView.solidByName(loadedRegistries.getBlockRegistry()));
            try {
                opened.setAccess(
                        new ServerWorld.Access(
                                HostIdentity.loadOrCreate(saveFolder),
                                Accounts.load(saveFolder),
                                Saves.readRegistration(saveFolder, isDedicated(saveFolder)),
                                true,
                                PermissionStore.load(
                                        saveFolder.resolve(PERMISSIONS_FILE),
                                        ServerWorld.Access.DEFAULT_GROUPS)));
            } catch (IOException e) {
                opened.close();
                throw new UncheckedIOException("Could not read the save's host identity", e);
            }
            world = opened;
            loop =
                    new ServerLoop(
                            () -> frameSafely(opened),
                            () -> tickSafely(opened),
                            System.nanoTime(),
                            Boolean.getBoolean(ServerLoop.PRECISE_PROPERTY));
            loop.whilePaused(opened::runTasks, () -> opened.playerCount() <= 1);
            loopThread = new Thread(loop, "Server tick");
            loopThread.setDaemon(true);
            loopThread.start();
            log.info("Opened {} ({}, seed {})", save.name(), info.worldType(), info.seed());
            listenIfHosted(saveFolder, opened);
            return opened;
        } finally {
            worldLock.unlock();
        }
    }

    /**
     * Connect a client in this process to the open world. Requests pass between the two as objects,
     * never serialized, but only through {@link Connection}, so a remote client works the same way.
     * The client then sends {@code Join}. When the {@code ikala.net.*} system properties ask for a
     * bad network, both directions go through a {@link SimulatedLink}.
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
        final ServerSession session = new ServerSession(open, true);
        final SimulatedLink.Settings link = SimulatedLink.Settings.fromProperties();
        if (!link.isActive()) {
            session.setConnection(new LocalConnection(session, client));
            return new LocalConnection(client, session);
        }
        log.info(
                "Simulating a bad network: {} ms delay, up to {} ms jitter, {}% loss each way",
                link.delayMillis(), link.jitterMillis(), String.format("%.1f", link.loss() * 100));
        final long seed = System.nanoTime();
        final SimulatedLink toClient =
                new SimulatedLink(new LocalConnection(session, client), link, seed);
        final SimulatedLink toServer =
                new SimulatedLink(new LocalConnection(client, session), link, ~seed);
        links.add(toClient);
        links.add(toServer);
        session.setConnection(toClient);
        return toServer;
    }

    /**
     * Open the save's port if it is hosted: when its {@value Saves#SERVER_FILE} says so, when this
     * is a dedicated server for it (the save is the instance), or when {@value #PORT_PROPERTY} is
     * set. Players then connect over UDP. Called with the world lock held.
     *
     * @param saveFolder The save.
     * @param opened Its open world.
     */
    private void listenIfHosted(@NonNull Path saveFolder, @NonNull ServerWorld opened) {
        Hosting hosting = Saves.readHosting(saveFolder);
        if (isDedicated(saveFolder)) {
            hosting = hosting.listening();
        }
        final Integer port = Integer.getInteger(PORT_PROPERTY);
        if (port != null) {
            hosting = hosting.withPort(port);
        }
        if (hosting.listen() == Hosting.Listen.OFF) {
            return;
        }
        final InetSocketAddress bind =
                hosting.bind().isEmpty()
                        ? new InetSocketAddress(hosting.port())
                        : new InetSocketAddress(hosting.bind(), hosting.port());
        try {
            udp =
                    new UdpServer(
                            bind,
                            connection -> accept(opened, connection),
                            Server::remoteClosed,
                            PacketEndpoint.Settings.DEFAULT,
                            UdpServer.packetLossFromProperties());
        } catch (IllegalStateException e) {
            log.warn("Could not open {} to other players on {}", saveFolder, bind, e);
        }
    }

    /**
     * Whether this process is a dedicated server for a save: the save is the instance.
     *
     * @param saveFolder The save.
     * @return True if dedicated.
     */
    private static boolean isDedicated(@NonNull Path saveFolder) {
        final Path save = saveFolder.toAbsolutePath().normalize();
        return AppPaths.instance()
                .map(instance -> instance.toAbsolutePath().normalize().equals(save))
                .orElse(false);
    }

    /**
     * Make the session for a player connecting over the network.
     *
     * @param opened The world.
     * @param connection The connection.
     * @return The session, which handles the requests the player sends.
     */
    private static RequestHandler accept(
            @NonNull ServerWorld opened, @NonNull UdpConnection connection) {
        final ServerSession session = new ServerSession(opened, false);
        session.setConnection(connection);
        return session;
    }

    /**
     * A network player's connection closed: they leave the world.
     *
     * @param connection The connection.
     */
    private static void remoteClosed(@NonNull UdpConnection connection) {
        if (connection.getLocal() instanceof ServerSession session) {
            session.leave();
        }
    }

    /**
     * The port the server listens on.
     *
     * @return The port, or -1 when not listening.
     */
    public int getListeningPort() {
        final UdpServer listening = udp;
        return listening == null ? -1 : listening.getPort();
    }

    /**
     * Run a command frame, keeping the loop alive if it throws.
     *
     * @param opened The world to step.
     */
    private static void frameSafely(@NonNull ServerWorld opened) {
        try {
            opened.frame();
        } catch (RuntimeException e) {
            log.warn("A server frame failed", e);
        }
    }

    /**
     * Run a tick, keeping the loop alive if it throws.
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

    /**
     * Pause or carry on the open world: no command frames, no ticks and no clock while paused. Only
     * a world with at most one player connected can pause, as single player can, so a friend who
     * joins never finds it stopped; a second player joining unpauses it.
     *
     * @param pause True to pause, false to carry on.
     * @return Whether the world is paused now.
     */
    public boolean setPaused(boolean pause) {
        worldLock.lock();
        try {
            if (loop == null || world == null) {
                return false;
            }
            loop.setPaused(pause && world.playerCount() <= 1);
            if (loop.isPaused()) {
                log.info("Paused {}", world.getSaveFolder().getFileName());
            }
            return loop.isPaused();
        } finally {
            worldLock.unlock();
        }
    }

    /**
     * Whether the open world is paused.
     *
     * @return True while paused.
     */
    public boolean isPaused() {
        final ServerLoop running = loop;
        return running != null && running.isPaused();
    }

    /**
     * Run a command from the server console, on the open world's tick thread.
     *
     * @param line The command, without a {@code /}.
     */
    public void consoleCommand(@NonNull String line) {
        final ServerWorld open = world;
        if (open == null) {
            log.warn("No world is open to run {} in", line);
            return;
        }
        open.post(() -> open.commands().dispatch(CONSOLE, line));
    }

    /** The server console as the sender of a command. */
    private static final CommandSender CONSOLE =
            new CommandSender() {
                @Override
                public String name() {
                    return "Console";
                }

                @Override
                public Subject subject() {
                    return Subject.CONSOLE;
                }

                @Override
                public void reply(@NonNull ChatText text) {
                    log.info("{}", text.plain());
                }

                @Override
                public ServerPlayer player() {
                    return null;
                }
            };

    /** Close the open world, if there is one, and stop ticking it, saving what it keeps. */
    public void closeWorld() {
        worldLock.lock();
        try {
            if (loop != null) {
                loop.stop();
                try {
                    loopThread.join(LOOP_STOP_MILLIS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                if (loopThread.isAlive()) {
                    log.warn("The server loop didn't stop in {} ms", LOOP_STOP_MILLIS);
                    loopThread.interrupt();
                }
                loop = null;
                loopThread = null;
            }
            for (SimulatedLink link : links) {
                link.disconnect();
            }
            links.clear();
            if (udp != null) {
                udp.close();
                udp = null;
            }
            if (world != null) {
                try {
                    Saves.writeTime(world.getSaveFolder(), world.getWorldFolder(), world.getTime());
                } catch (IOException e) {
                    log.warn("Could not save the time of {}", world.getSaveFolder(), e);
                }
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
        final List<Path> folders = ContentFolders.installed();
        final WorldgenCompiler compiler =
                new WorldgenCompiler(
                        WorldgenData.load(folders), seed, DefinitionLoader.blockIds(folders));
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
