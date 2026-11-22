package com.ikalagaming.factory.server;

import com.ikalagaming.factory.networking.base.Connection;
import com.ikalagaming.factory.networking.base.Request;
import com.ikalagaming.factory.networking.request.serverbound.Join;
import com.ikalagaming.factory.networking.request.serverbound.PlayerInputs;
import com.ikalagaming.factory.networking.request.serverbound.ServerBoundRequestHandler;
import com.ikalagaming.factory.sim.PlayerInput;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * The server's end of one client's connection. Requests arrive on whatever thread the client sent
 * them from, and are handled on the world's tick thread, so the world is only ever changed there.
 */
@Slf4j
final class ServerSession implements ServerBoundRequestHandler {

    /** The world the client joins. */
    private final ServerWorld world;

    /** Sends requests to the client; set once the connection is made. */
    private volatile Connection toClient;

    /** The client's player, once it has joined. Tick thread only. */
    private ServerPlayer player;

    /**
     * A session for one client.
     *
     * @param world The world the client joins.
     */
    ServerSession(@NonNull ServerWorld world) {
        this.world = world;
    }

    /**
     * Set how to reach the client.
     *
     * @param connection Sends requests to the client.
     */
    void setConnection(@NonNull Connection connection) {
        toClient = connection;
    }

    @Override
    public void process(@NonNull Request request) {
        world.post(() -> request.handleUsing(this));
    }

    @Override
    public void handle(@NonNull Join request) {
        if (player != null) {
            log.warn("{} tried to join twice", player.getName());
            return;
        }
        final String name =
                request.getName() == null || request.getName().isBlank()
                        ? "Player"
                        : request.getName();
        player = world.join(name, toClient);
    }

    @Override
    public void handle(@NonNull PlayerInputs request) {
        if (player == null) {
            return;
        }
        final List<PlayerInput> inputs = request.inputs();
        final long next = world.getFrames();
        for (int i = 0; i < inputs.size(); ++i) {
            player.inputs.offer(next, request.getFirstFrame() + i, inputs.get(i).sanitized());
        }
    }
}
