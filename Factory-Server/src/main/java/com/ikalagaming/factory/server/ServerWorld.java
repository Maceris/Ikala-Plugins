package com.ikalagaming.factory.server;

import com.ikalagaming.ecs.Schedule;
import com.ikalagaming.ecs.World;
import com.ikalagaming.factory.auth.Ed25519;
import com.ikalagaming.factory.auth.HostIdentity;
import com.ikalagaming.factory.auth.SignInProof;
import com.ikalagaming.factory.chat.ChatText;
import com.ikalagaming.factory.kvt.Node;
import com.ikalagaming.factory.networking.base.Connection;
import com.ikalagaming.factory.networking.request.clientbound.ChatMessage;
import com.ikalagaming.factory.networking.request.clientbound.ChunkData;
import com.ikalagaming.factory.networking.request.clientbound.ChunkUnload;
import com.ikalagaming.factory.networking.request.clientbound.Joined;
import com.ikalagaming.factory.networking.request.clientbound.PlayerState;
import com.ikalagaming.factory.networking.request.clientbound.TimeUpdate;
import com.ikalagaming.factory.save.Accounts;
import com.ikalagaming.factory.save.PlayerFiles;
import com.ikalagaming.factory.save.Registration;
import com.ikalagaming.factory.sim.Angle;
import com.ikalagaming.factory.sim.Fixed;
import com.ikalagaming.factory.sim.Frames;
import com.ikalagaming.factory.sim.GameMode;
import com.ikalagaming.factory.sim.MoveMode;
import com.ikalagaming.factory.sim.Movement;
import com.ikalagaming.factory.sim.PlayerInput;
import com.ikalagaming.factory.sim.PlayerSimulation;
import com.ikalagaming.factory.world.Block;
import com.ikalagaming.factory.world.Chunk;
import com.ikalagaming.factory.world.ChunkBlockView;
import com.ikalagaming.factory.world.ChunkCodec;
import com.ikalagaming.factory.world.ChunkPos;
import com.ikalagaming.factory.world.WorldTime;
import com.ikalagaming.factory.world.gen.SpawnFinder;
import com.ikalagaming.factory.world.gen.SpawnPlatform;
import com.ikalagaming.factory.world.gen.WorldGenerator;
import com.ikalagaming.permissions.Group;
import com.ikalagaming.permissions.PermissionPlugin;
import com.ikalagaming.permissions.PermissionStore;
import com.ikalagaming.permissions.Permissions;
import com.ikalagaming.permissions.Subject;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Path;
import java.security.SecureRandom;
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

    /** Whether each block, by name, stops movement. */
    private final Map<String, Boolean> solidByName;

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

    /**
     * Who can sign in to the world: the save's host identity, its accounts and its registration
     * policy.
     *
     * @param host The save's server id and host key.
     * @param accounts The save's accounts.
     * @param registration Who can make an account.
     * @param savesPlayers Whether players' files are read and written in the save.
     * @param permissions The save's permission groups and players' own permissions.
     */
    public record Access(
            @NonNull HostIdentity host,
            @NonNull Accounts accounts,
            @NonNull Registration registration,
            boolean savesPlayers,
            @NonNull PermissionStore permissions) {

        /** The groups every save has: one for each role, each inheriting the one below. */
        public static final Group[] DEFAULT_GROUPS = {
            Group.inheriting(Groups.DEFAULT),
            Group.inheriting(Groups.OPERATOR, Groups.DEFAULT),
            Group.inheriting(Groups.OWNER, Groups.OPERATOR)
        };

        /**
         * Access kept in memory, with a new host key, for tests.
         *
         * @return The access.
         */
        public static Access inMemory() {
            final byte[] id = new byte[SignInProof.SERVER_ID_BYTES];
            new SecureRandom().nextBytes(id);
            return new Access(
                    new HostIdentity(id, Ed25519.generate()),
                    Accounts.inMemory(),
                    Registration.OPEN,
                    false,
                    PermissionStore.inMemory(DEFAULT_GROUPS));
        }
    }

    /**
     * Who can sign in.
     *
     * @return The access.
     */
    @Getter private volatile Access access = Access.inMemory();

    /** The game mode new players start in. */
    private volatile GameMode defaultGameMode = GameMode.SURVIVAL;

    /**
     * Set the game mode new players start in.
     *
     * @param mode The game mode.
     */
    public void setDefaultGameMode(@NonNull GameMode mode) {
        defaultGameMode = mode;
    }

    /** The node needed to talk in chat. */
    public static final String CHAT_PERMISSION = "lotomation.chat";

    /** What players may do; replaced with the access. */
    private volatile Permissions permissions =
            new Permissions(PermissionPlugin.getRegistry(), access.permissions());

    /** The commands players and the console run. */
    private final ServerCommands commands = new ServerCommands(this);

    /** How many entries {@link #players} has, for other threads. */
    private final AtomicInteger playerCount = new AtomicInteger();

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
        this(
                saveFolder,
                worldFolder,
                generator,
                spawn,
                threads,
                time,
                Map.of(com.ikalagaming.factory.world.World.AIR_NAME, false));
    }

    /**
     * Open a world.
     *
     * @param saveFolder The save the world is in.
     * @param worldFolder The world's folder name inside the save.
     * @param generator Makes the world's chunks.
     * @param spawn Where new players start.
     * @param threads How many threads generate chunks.
     * @param time The world's clock, in ticks.
     * @param solidByName Whether each block, by name, stops movement, from {@link
     *     ChunkBlockView#solidByName}. Blocks it doesn't name are solid.
     */
    public ServerWorld(
            @NonNull Path saveFolder,
            @NonNull String worldFolder,
            @NonNull WorldGenerator generator,
            @NonNull SpawnFinder.Spawn spawn,
            int threads,
            long time,
            @NonNull Map<String, Boolean> solidByName) {
        this.solidByName = solidByName;
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
     * Set the world's clock, telling every player at once. Tick thread only.
     *
     * @param newTime The time, in ticks; see {@link WorldTime}.
     */
    public void setTime(long newTime) {
        time.set(newTime);
        for (ServerPlayer player : players.values()) {
            player.getConnection().send(new TimeUpdate(newTime, Server.TICKS_PER_SECOND));
        }
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
     * Set who can sign in, before anyone connects.
     *
     * @param newAccess The access.
     */
    public void setAccess(@NonNull Access newAccess) {
        access = newAccess;
        permissions = new Permissions(PermissionPlugin.getRegistry(), newAccess.permissions());
    }

    /**
     * What players may do, from the declared nodes and the save's groups.
     *
     * @return The permissions.
     */
    public Permissions permissions() {
        return permissions;
    }

    /**
     * Who a player is for permissions: an operator if their role is, in their role's group.
     *
     * @param player The player.
     * @return The subject.
     */
    public static Subject subjectFor(@NonNull ServerPlayer player) {
        final Accounts.Account account = player.getAccount();
        return new Subject(
                account.id(), account.role().isOperator(), List.of(Groups.of(account.role())));
    }

    /**
     * The commands players and the console run.
     *
     * @return The commands.
     */
    public ServerCommands commands() {
        return commands;
    }

    /**
     * Send a player a line of chat. Tick thread only.
     *
     * @param player The player.
     * @param text The line.
     */
    public void tell(@NonNull ServerPlayer player, @NonNull ChatText text) {
        player.getConnection().send(ChatMessage.of(text));
    }

    /**
     * Send everyone a line of chat, and log it. Tick thread only.
     *
     * @param text The line.
     */
    public void broadcast(@NonNull ChatText text) {
        log.info("[Chat] {}", text.plain());
        for (ServerPlayer player : players.values()) {
            tell(player, text);
        }
    }

    /**
     * A player says something, or runs a command. Tick thread only.
     *
     * @param player The player.
     * @param text What they typed.
     */
    void chat(@NonNull ServerPlayer player, @NonNull String text) {
        final String line = text.strip();
        if (line.isEmpty()) {
            return;
        }
        if (line.startsWith("/")) {
            commands.dispatch(new PlayerSender(this, player), line);
            return;
        }
        if (!permissions.has(subjectFor(player), CHAT_PERMISSION)) {
            tell(player, ChatText.of("You can't talk in chat here.", ChatText.RED));
            return;
        }
        broadcast(nameTag(player).append(line, ChatText.DEFAULT));
    }

    /**
     * How a player's name starts their lines: their role's title in its color, then their name.
     *
     * @param player The player.
     * @return The start of the line.
     */
    static ChatText nameTag(@NonNull ServerPlayer player) {
        return switch (player.getAccount().role()) {
            case OWNER ->
                    ChatText.of("[Owner] ", ChatText.GOLD)
                            .append("<" + player.getName() + "> ", ChatText.DEFAULT);
            case OPERATOR ->
                    ChatText.of("[Op] ", ChatText.AQUA)
                            .append("<" + player.getName() + "> ", ChatText.DEFAULT);
            case PLAYER -> ChatText.of("<" + player.getName() + "> ");
        };
    }

    /** A player as the sender of a command. */
    private record PlayerSender(@NonNull ServerWorld world, @NonNull ServerPlayer player)
            implements CommandSender {
        @Override
        public String name() {
            return player.getName();
        }

        @Override
        public Subject subject() {
            return subjectFor(player);
        }

        @Override
        public void reply(@NonNull ChatText text) {
            world.tell(player, text);
        }
    }

    /**
     * Add a player with a throwaway account, for tests. Tick thread only.
     *
     * @param name The name the player joins as.
     * @param connection Sends requests to the player's client.
     * @return The player.
     */
    ServerPlayer join(@NonNull String name, @NonNull Connection connection) {
        return join(
                new Accounts.Account(
                        UUID.randomUUID().toString(),
                        name,
                        new byte[0],
                        Accounts.Role.PLAYER,
                        false,
                        false,
                        0),
                connection);
    }

    /**
     * Find a player who is in the world, by account id. Tick thread only.
     *
     * @param accountId The account id.
     * @return The player, or null if they aren't here.
     */
    ServerPlayer player(@NonNull String accountId) {
        return players.get(accountId);
    }

    /**
     * Add a signed-in player where they left off, or at the spawn the first time, and tell their
     * client it joined. Tick thread only.
     *
     * @param account The player's account.
     * @param connection Sends requests to the player's client.
     * @return The player.
     */
    ServerPlayer join(@NonNull Accounts.Account account, @NonNull Connection connection) {
        final long entity = sim.create();
        double startX = spawn.x() + HALF_BLOCK;
        double startY = spawn.y();
        double startZ = spawn.z() + HALF_BLOCK;
        float yaw = 0;
        float pitch = 0;
        GameMode gameMode = defaultGameMode;
        final Optional<PlayerFiles.PlayerData> saved =
                access.savesPlayers()
                        ? PlayerFiles.read(saveFolder, account.id())
                                .filter(data -> data.world().equals(worldFolder))
                        : Optional.empty();
        if (saved.isPresent()) {
            startX = saved.get().x();
            startY = saved.get().y();
            startZ = saved.get().z();
            yaw = saved.get().yaw();
            pitch = saved.get().pitch();
            gameMode = GameMode.named(saved.get().gameMode()).orElse(defaultGameMode);
        }
        final ServerPlayer player =
                new ServerPlayer(account, connection, entity, startX, startY, startZ);
        sim.set(
                PlayerSimulation.MOVEMENT,
                entity,
                Movement.at(
                        MoveMode.WALKING,
                        Math.round(startX * Fixed.ONE),
                        Math.round(startY * Fixed.ONE),
                        Math.round(startZ * Fixed.ONE)));
        sim.set(PlayerSimulation.INPUT, entity, PlayerInput.NONE);
        sim.set(PlayerSimulation.GAME_MODE, entity, gameMode);
        sim.set(
                PlayerSimulation.BLOCKS,
                entity,
                new ChunkBlockView(pos -> sentChunk(player, pos), solidByName));
        players.put(player.getId(), player);
        playerCount.set(players.size());
        connection.send(
                new Joined(
                        player.getId(),
                        player.getX(),
                        player.getY(),
                        player.getZ(),
                        (float) Math.toRadians(yaw),
                        (float) Math.toRadians(pitch),
                        VIEW_RADIUS,
                        VERTICAL_RADIUS,
                        frames.get(),
                        gameMode.ordinal()));
        connection.send(new TimeUpdate(time.get(), Server.TICKS_PER_SECOND));
        broadcast(ChatText.of(account.username() + " joined the game", ChatText.YELLOW));
        log.info(
                "{} joined at {}, {}, {}",
                account.username(),
                player.getX(),
                player.getY(),
                player.getZ());
        return player;
    }

    /**
     * A chunk as a player's movement sees it: only once it has been sent to them, so that the
     * server and their client agree that the chunks beyond are solid. Tick thread only.
     *
     * @param player The player.
     * @param pos The chunk.
     * @return The chunk, or null if the player hasn't been sent it.
     */
    private Chunk sentChunk(@NonNull ServerPlayer player, @NonNull ChunkPos pos) {
        if (!player.sent.contains(pos)) {
            return null;
        }
        final CompletableFuture<Chunk> chunk = chunks.get(pos);
        return chunk == null || !chunk.isDone() || chunk.isCompletedExceptionally()
                ? null
                : chunk.getNow(null);
    }

    /**
     * Remove a player whose client left, and everything simulated for them. Tick thread only.
     *
     * @param player The player.
     */
    void leave(@NonNull ServerPlayer player) {
        if (players.remove(player.getId()) == null) {
            return;
        }
        savePlayer(player);
        playerCount.set(players.size());
        sim.destroy(player.getEntity());
        log.info("{} left", player.getName());
        broadcast(ChatText.of(player.getName() + " left the game", ChatText.YELLOW));
    }

    /**
     * Write a player's file: where they are and which way they face. Tick thread only, or after it
     * stopped.
     *
     * @param player The player.
     */
    private void savePlayer(@NonNull ServerPlayer player) {
        if (!access.savesPlayers()) {
            return;
        }
        final Movement movement = sim.get(PlayerSimulation.MOVEMENT, player.getEntity());
        final float yaw = movement == null ? 0 : (float) (movement.yaw() * 360.0 / Angle.TURN);
        final float pitch = movement == null ? 0 : (float) (movement.pitch() * 360.0 / Angle.TURN);
        try {
            PlayerFiles.write(
                    saveFolder,
                    new PlayerFiles.PlayerData(
                            player.getId(),
                            worldFolder,
                            player.getX(),
                            player.getY(),
                            player.getZ(),
                            yaw,
                            pitch,
                            gameMode(player).id()));
        } catch (IOException e) {
            log.warn("Could not save {}'s player file", player.getName(), e);
        }
    }

    /**
     * A player's game mode. Tick thread only.
     *
     * @param player The player.
     * @return Their game mode.
     */
    public GameMode gameMode(@NonNull ServerPlayer player) {
        final GameMode mode = sim.get(PlayerSimulation.GAME_MODE, player.getEntity());
        return mode == null ? defaultGameMode : mode;
    }

    /**
     * Change a player's game mode from the next frame. Their client learns it from the next state
     * it gets. Tick thread only.
     *
     * @param player The player.
     * @param mode The game mode.
     */
    public void setGameMode(@NonNull ServerPlayer player, @NonNull GameMode mode) {
        sim.set(PlayerSimulation.GAME_MODE, player.getEntity(), mode);
        log.info("{} is now in {} mode", player.getName(), mode.id());
    }

    /**
     * The players in the world. Tick thread only.
     *
     * @return The players.
     */
    Collection<ServerPlayer> players() {
        return players.values();
    }

    /**
     * How many players are in the world. Safe from any thread.
     *
     * @return The count.
     */
    public int playerCount() {
        return playerCount.get();
    }

    /** Run the work posted to the tick thread, as a paused world still does. Tick thread only. */
    void runTasks() {
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
                            frame,
                            movement,
                            gameMode(player),
                            player.inputs.buffered(frame),
                            player.inputs.misses());
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
        // Called after the tick thread stopped, so the players can be read here
        for (ServerPlayer player : players.values()) {
            savePlayer(player);
        }
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
