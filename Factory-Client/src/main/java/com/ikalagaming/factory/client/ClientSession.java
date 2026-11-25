package com.ikalagaming.factory.client;

import com.ikalagaming.factory.auth.Ed25519;
import com.ikalagaming.factory.auth.Identity;
import com.ikalagaming.factory.auth.KnownHosts;
import com.ikalagaming.factory.auth.SignInProof;
import com.ikalagaming.factory.chat.ChatText;
import com.ikalagaming.factory.networking.base.Connection;
import com.ikalagaming.factory.networking.base.Request;
import com.ikalagaming.factory.networking.request.clientbound.BlockChanges;
import com.ikalagaming.factory.networking.request.clientbound.ChatMessage;
import com.ikalagaming.factory.networking.request.clientbound.ChunkData;
import com.ikalagaming.factory.networking.request.clientbound.ChunkUnload;
import com.ikalagaming.factory.networking.request.clientbound.ClientBoundRequestHandler;
import com.ikalagaming.factory.networking.request.clientbound.Disconnect;
import com.ikalagaming.factory.networking.request.clientbound.Joined;
import com.ikalagaming.factory.networking.request.clientbound.PlayerState;
import com.ikalagaming.factory.networking.request.clientbound.ServerHello;
import com.ikalagaming.factory.networking.request.clientbound.TimeUpdate;
import com.ikalagaming.factory.networking.request.clientbound.UpdateTagRegistry;
import com.ikalagaming.factory.networking.request.serverbound.ChatSend;
import com.ikalagaming.factory.networking.request.serverbound.Hello;
import com.ikalagaming.factory.networking.request.serverbound.PlayerInputs;
import com.ikalagaming.factory.networking.request.serverbound.SignIn;
import com.ikalagaming.factory.sim.BlockView;
import com.ikalagaming.factory.sim.Fixed;
import com.ikalagaming.factory.sim.GameMode;
import com.ikalagaming.factory.sim.MoveMode;
import com.ikalagaming.factory.sim.Movement;
import com.ikalagaming.factory.world.Block;
import com.ikalagaming.factory.world.Chunk;
import com.ikalagaming.factory.world.ChunkBlockView;
import com.ikalagaming.factory.world.ChunkCodec;
import com.ikalagaming.factory.world.ChunkPos;
import com.ikalagaming.factory.world.World;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.security.KeyPair;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * The client's end of its connection to a server. Requests from the server arrive on server threads
 * and are applied on the main thread, through the executor it is given, so the {@link ClientWorld}
 * is only changed there.
 */
@Slf4j
public final class ClientSession implements ClientBoundRequestHandler {

    /** Makes nonces. */
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final Base64.Encoder BASE64 = Base64.getEncoder();
    private static final Base64.Decoder UNBASE64 = Base64.getDecoder();

    /**
     * The chunks the server sent.
     *
     * @return The world.
     */
    @Getter private final ClientWorld world = new ClientWorld();

    /**
     * The world's clock, kept in step with the server.
     *
     * @return The clock.
     */
    @Getter private final ClientClock clock = new ClientClock();

    /** Runs work on the main thread. */
    private final Executor mainThread;

    /** Told once the server accepts the join. */
    private final Consumer<Joined> onJoined;

    /** Told why, if the server turns the player away or can't prove who it is. */
    private final Consumer<String> onRefused;

    /** The player's master secret, which their key for each server comes from. */
    private final Identity identity;

    /** The host keys seen before, by server id. */
    private final KnownHosts knownHosts;

    /** The name the player signs in as. */
    private String username;

    /** This connection's nonce, sent in {@code Hello}. */
    private byte[] clientNonce;

    /** Whether the sign-in was sent, so a second {@code ServerHello} is ignored. */
    private boolean signInSent;

    /** Whether the session was refused, so nothing more is handled. */
    private boolean refused;

    /** Shows lines of chat. */
    private Consumer<ChatText> onChat = text -> {};

    /** Sends requests to the server; set once connected. */
    private Connection toServer;

    /**
     * The player's id, once joined.
     *
     * @return The id, or null before the server accepts the join.
     */
    @Getter private String playerId;

    /**
     * The prediction of the player's movement, once joined.
     *
     * @return The prediction, or null before the server accepts the join.
     */
    @Getter private Prediction prediction;

    /**
     * When the player's command frames run, once joined.
     *
     * @return The clock, or null before the server accepts the join.
     */
    @Getter private FrameClock frameClock;

    /**
     * A session.
     *
     * @param mainThread Runs work on the main thread.
     * @param identity The player's master secret.
     * @param knownHosts The host keys seen before.
     * @param onJoined Told on the main thread once the server accepts the join.
     * @param onRefused Told on the main thread, with the reason to show, if the server turns the
     *     player away or can't prove who it is.
     */
    public ClientSession(
            @NonNull Executor mainThread,
            @NonNull Identity identity,
            @NonNull KnownHosts knownHosts,
            @NonNull Consumer<Joined> onJoined,
            @NonNull Consumer<String> onRefused) {
        this.mainThread = mainThread;
        this.identity = identity;
        this.knownHosts = knownHosts;
        this.onJoined = onJoined;
        this.onRefused = onRefused;
    }

    /**
     * Join the server over a connection: say hello, check the server's proof of who it is, then
     * sign in with this player's key for that server. See {@link SignInProof}.
     *
     * @param connection Sends requests to the server.
     * @param name The name to join as.
     */
    public void join(@NonNull Connection connection, @NonNull String name) {
        toServer = connection;
        username = name;
        clientNonce = new byte[SignInProof.NONCE_BYTES];
        RANDOM.nextBytes(clientNonce);
        toServer.send(new Hello(BASE64.encodeToString(clientNonce)));
    }

    @Override
    public void handle(@NonNull ServerHello request) {
        if (signInSent || refused || toServer == null) {
            return;
        }
        final byte[] serverId;
        final byte[] hostKey;
        final byte[] serverNonce;
        final boolean proven;
        try {
            serverId = UNBASE64.decode(request.getServerId());
            hostKey = UNBASE64.decode(request.getHostKey());
            serverNonce = UNBASE64.decode(request.getServerNonce());
            proven =
                    serverId.length == SignInProof.SERVER_ID_BYTES
                            && Ed25519.verify(
                                    Ed25519.publicKey(hostKey),
                                    SignInProof.host(serverId, hostKey, clientNonce, serverNonce),
                                    UNBASE64.decode(request.getSignature()));
        } catch (IllegalArgumentException | NullPointerException e) {
            refuse("The server sent a greeting this can't read");
            return;
        }
        if (!proven) {
            refuse("The server couldn't prove who it is");
            return;
        }
        switch (knownHosts.check(serverId, hostKey)) {
            case CHANGED -> {
                refuse(
                        "This server's host key changed since you last joined it, so it may not"
                                + " be the same server. Not signing in.");
                return;
            }
            case NEW ->
                    log.info("First visit to server {}; its host key is now pinned", hex(serverId));
            case MATCH -> log.debug("Server {} proved its host key", hex(serverId));
        }
        final KeyPair key = identity.keyFor(serverId);
        final byte[] publicKey = key.getPublic().getEncoded();
        final byte[] signature =
                Ed25519.sign(
                        key.getPrivate(),
                        SignInProof.signIn(
                                serverId, hostKey, clientNonce, serverNonce, username, publicKey));
        signInSent = true;
        toServer.send(
                new SignIn(
                        username,
                        BASE64.encodeToString(publicKey),
                        BASE64.encodeToString(signature)));
    }

    /**
     * Set what shows lines of chat from the server.
     *
     * @param listener Gets each line, on the main thread.
     */
    public void setOnChat(@NonNull Consumer<ChatText> listener) {
        onChat = listener;
    }

    /**
     * Say something in chat, or run a command if it starts with {@code /}.
     *
     * @param text What the player typed.
     */
    public void sendChat(@NonNull String text) {
        if (toServer != null && playerId != null) {
            toServer.send(new ChatSend(text));
        }
    }

    @Override
    public void handle(@NonNull ChatMessage request) {
        onChat.accept(request.text());
    }

    @Override
    public void handle(@NonNull Disconnect request) {
        refuse(
                request.getReason() == null
                        ? "The server closed the connection"
                        : request.getReason());
    }

    /**
     * Give up on this session and say why.
     *
     * @param reason Why, to show the player.
     */
    private void refuse(@NonNull String reason) {
        if (refused) {
            return;
        }
        refused = true;
        log.warn("Not playing on the server: {}", reason);
        onRefused.accept(reason);
    }

    /**
     * Bytes as hex, for logs.
     *
     * @param bytes The bytes.
     * @return The hex.
     */
    private static String hex(byte @NonNull [] bytes) {
        return HexFormat.of().formatHex(bytes);
    }

    /**
     * Whether the server has accepted the join.
     *
     * @return True once joined.
     */
    public boolean isJoined() {
        return playerId != null;
    }

    /** Send the server the inputs it hasn't answered for yet. */
    public void sendInputs() {
        if (toServer == null || prediction == null) {
            return;
        }
        final PlayerInputs inputs = prediction.unacknowledged();
        if (inputs != null) {
            inputs.setSentNanos(System.nanoTime());
            toServer.send(inputs);
        }
    }

    @Override
    public void process(@NonNull Request request) {
        mainThread.execute(
                () -> {
                    if (!refused) {
                        request.handleUsing(this);
                    }
                });
    }

    @Override
    public void handle(@NonNull UpdateTagRegistry request) {
        // Tags aren't used by the client yet
    }

    @Override
    public void handle(@NonNull Joined request) {
        playerId = request.getPlayerId();
        prediction =
                new Prediction(
                        request.getFrame(),
                        Movement.at(
                                MoveMode.WALKING,
                                Math.round(request.getX() * Fixed.ONE),
                                Math.round(request.getY() * Fixed.ONE),
                                Math.round(request.getZ() * Fixed.ONE)),
                        GameMode.byOrdinal(request.getGameMode()),
                        blocksNow());
        frameClock = new FrameClock();
        frameClock.start(System.nanoTime());
        onJoined.accept(request);
    }

    @Override
    public void handle(@NonNull ChunkData request) {
        final ChunkPos pos = new ChunkPos(request.getX(), request.getY(), request.getZ());
        try {
            world.put(pos, ChunkCodec.decode(request.getChunk()));
        } catch (RuntimeException e) {
            log.warn("The server sent chunk {} in a form this can't read", pos, e);
        }
    }

    @Override
    public void handle(@NonNull TimeUpdate request) {
        clock.set(request.getTicks(), request.getTicksPerSecond());
    }

    @Override
    public void handle(@NonNull PlayerState request) {
        if (prediction == null) {
            return;
        }
        final long clientFrame = prediction.getFrame();
        try {
            prediction.onServerState(
                    request.getFrame(),
                    request.movement(),
                    request.gameMode(),
                    request.getBuffered(),
                    request.getMissed());
        } catch (IllegalArgumentException e) {
            log.warn("The server sent a player state this can't read", e);
            return;
        }
        final long resync =
                frameClock.onServer(
                        request.getFrame(),
                        clientFrame,
                        request.getBuffered(),
                        request.getMissed(),
                        request.getEchoNanos(),
                        request.getHeldNanos(),
                        System.nanoTime());
        if (resync != FrameClock.NO_RESYNC) {
            log.info(
                    "Resyncing the command frames from {} to {}; the server answered for {}",
                    prediction.getFrame(),
                    resync,
                    request.getFrame());
            prediction.resync(resync);
        }
    }

    @Override
    public void handle(@NonNull BlockChanges request) {
        final int count = request.size();
        if (request.getPositions().size() != count * 3) {
            log.warn("The server sent block changes this can't read");
            return;
        }
        // Kept first, as the blocks were, so frames before the change replay without it
        final BlockView before = blocksNow();
        final Map<ChunkPos, Chunk> changed = new LinkedHashMap<>();
        for (int i = 0; i < count; ++i) {
            final int x = request.getPositions().get(3 * i);
            final int y = request.getPositions().get(3 * i + 1);
            final int z = request.getPositions().get(3 * i + 2);
            final ChunkPos pos = ChunkPos.containing(x, y, z);
            Chunk chunk = changed.get(pos);
            if (chunk == null) {
                final Optional<Chunk> held = world.chunk(pos);
                if (held.isEmpty()) {
                    continue;
                }
                // A copy, since the renderer may be reading the one it has
                chunk = held.get().copy();
                changed.put(pos, chunk);
            }
            if (prediction != null) {
                prediction.blockChanged(x, y, z, request.getFrame(), before.solid(x, y, z));
            }
            chunk.setBlock(
                    Math.floorMod(x, World.CHUNK_SIZE),
                    Math.floorMod(y, World.CHUNK_SIZE),
                    Math.floorMod(z, World.CHUNK_SIZE),
                    new Block(request.getBlocks().get(i), null));
        }
        changed.forEach(world::put);
    }

    /**
     * Movement's view of the blocks this client has now.
     *
     * @return The view.
     */
    private BlockView blocksNow() {
        return new ChunkBlockView(
                pos -> world.chunk(pos).orElse(null), BlockCollisions.solidByName());
    }

    @Override
    public void handle(@NonNull ChunkUnload request) {
        world.remove(new ChunkPos(request.getX(), request.getY(), request.getZ()));
    }

    /**
     * The connection to the server.
     *
     * @return The connection, or null before joining or after leaving.
     */
    public Connection getConnection() {
        return toServer;
    }

    /** Drop everything, as when leaving the world. */
    public void close() {
        world.clear();
        if (toServer != null) {
            toServer.disconnect();
        }
        toServer = null;
        playerId = null;
        prediction = null;
        frameClock = null;
    }
}
