package com.ikalagaming.factory.client;

import com.ikalagaming.factory.networking.base.Connection;
import com.ikalagaming.factory.networking.base.Request;
import com.ikalagaming.factory.networking.request.clientbound.ChunkData;
import com.ikalagaming.factory.networking.request.clientbound.ChunkUnload;
import com.ikalagaming.factory.networking.request.clientbound.ClientBoundRequestHandler;
import com.ikalagaming.factory.networking.request.clientbound.Joined;
import com.ikalagaming.factory.networking.request.clientbound.PlayerState;
import com.ikalagaming.factory.networking.request.clientbound.TimeUpdate;
import com.ikalagaming.factory.networking.request.clientbound.UpdateTagRegistry;
import com.ikalagaming.factory.networking.request.serverbound.Join;
import com.ikalagaming.factory.networking.request.serverbound.PlayerInputs;
import com.ikalagaming.factory.sim.Fixed;
import com.ikalagaming.factory.sim.MoveMode;
import com.ikalagaming.factory.sim.Movement;
import com.ikalagaming.factory.world.ChunkCodec;
import com.ikalagaming.factory.world.ChunkPos;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * The client's end of its connection to a server. Requests from the server arrive on server threads
 * and are applied on the main thread, through the executor it is given, so the {@link ClientWorld}
 * is only changed there.
 */
@Slf4j
public final class ClientSession implements ClientBoundRequestHandler {

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
     * A session.
     *
     * @param mainThread Runs work on the main thread.
     * @param onJoined Told on the main thread once the server accepts the join.
     */
    public ClientSession(@NonNull Executor mainThread, @NonNull Consumer<Joined> onJoined) {
        this.mainThread = mainThread;
        this.onJoined = onJoined;
    }

    /**
     * Join the server over a connection.
     *
     * @param connection Sends requests to the server.
     * @param name The name to join as.
     */
    public void join(@NonNull Connection connection, @NonNull String name) {
        toServer = connection;
        toServer.send(new Join(name));
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
            toServer.send(inputs);
        }
    }

    @Override
    public void process(@NonNull Request request) {
        mainThread.execute(() -> request.handleUsing(this));
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
                                MoveMode.FLYING,
                                Math.round(request.getX() * Fixed.ONE),
                                Math.round(request.getY() * Fixed.ONE),
                                Math.round(request.getZ() * Fixed.ONE)));
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
        try {
            prediction.onServerState(
                    request.getFrame(),
                    request.movement(),
                    request.getBuffered(),
                    request.getMissed());
        } catch (IllegalArgumentException e) {
            log.warn("The server sent a player state this can't read", e);
        }
    }

    @Override
    public void handle(@NonNull ChunkUnload request) {
        world.remove(new ChunkPos(request.getX(), request.getY(), request.getZ()));
    }

    /** Drop everything, as when leaving the world. */
    public void close() {
        world.clear();
        toServer = null;
        playerId = null;
        prediction = null;
    }
}
