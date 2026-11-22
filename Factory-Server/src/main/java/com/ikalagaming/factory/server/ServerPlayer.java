package com.ikalagaming.factory.server;

import com.ikalagaming.factory.networking.base.Connection;
import com.ikalagaming.factory.world.ChunkPos;

import lombok.Getter;
import lombok.NonNull;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A player in an open world, as the server sees them: where they are, and which chunks they have
 * been sent. Only touched on the server's tick thread.
 */
public final class ServerPlayer {

    /**
     * The player's id.
     *
     * @return The id.
     */
    @Getter private final String id;

    /**
     * The name the player joined as.
     *
     * @return The name.
     */
    @Getter private final String name;

    /**
     * Sends requests to the player's client.
     *
     * @return The connection.
     */
    @Getter private final Connection connection;

    /**
     * The x of the player's feet, in blocks.
     *
     * @return The position.
     */
    @Getter private double x;

    /**
     * The y of the player's feet, in blocks.
     *
     * @return The position.
     */
    @Getter private double y;

    /**
     * The z of the player's feet, in blocks.
     *
     * @return The position.
     */
    @Getter private double z;

    /** Chunks the client has been sent and not told to drop. */
    final Set<ChunkPos> sent = new HashSet<>();

    /** Chunks being generated or encoded for this player. */
    final Set<ChunkPos> pending = new HashSet<>();

    /** The chunk the player was in when chunks were last chosen, or null before that. */
    ChunkPos center;

    /** The chunks near {@link #center}, nearest first. */
    List<ChunkPos> wanted = List.of();

    /**
     * A player.
     *
     * @param id The player's id.
     * @param name The name the player joined as.
     * @param connection Sends requests to the player's client.
     * @param x The x of the player's feet.
     * @param y The y of the player's feet.
     * @param z The z of the player's feet.
     */
    ServerPlayer(
            @NonNull String id,
            @NonNull String name,
            @NonNull Connection connection,
            double x,
            double y,
            double z) {
        this.id = id;
        this.name = name;
        this.connection = connection;
        moveTo(x, y, z);
    }

    /**
     * Move the player.
     *
     * @param newX The x of the player's feet.
     * @param newY The y of the player's feet.
     * @param newZ The z of the player's feet.
     */
    void moveTo(double newX, double newY, double newZ) {
        x = newX;
        y = newY;
        z = newZ;
    }

    /**
     * The chunk the player is in.
     *
     * @return The chunk.
     */
    public ChunkPos chunk() {
        return ChunkPos.containing(
                (long) Math.floor(x), (long) Math.floor(y), (long) Math.floor(z));
    }

    /**
     * How far a chunk is from the player's chunk, so chunks are sent nearest first.
     *
     * @param pos The chunk.
     * @return The squared distance between chunk centers, in chunks.
     */
    long distanceSquared(@NonNull ChunkPos pos) {
        final ChunkPos here = chunk();
        final long dx = pos.x() - here.x();
        final long dy = pos.y() - here.y();
        final long dz = pos.z() - here.z();
        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * Whether a position is a finite place in the world, so bad moves can be ignored.
     *
     * @param value A coordinate.
     * @return True if it is a finite number within the largest world.
     */
    static boolean isReasonable(double value) {
        return Double.isFinite(value) && Math.abs(value) < (double) Integer.MAX_VALUE;
    }
}
