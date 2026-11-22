package com.ikalagaming.factory.client;

import com.ikalagaming.factory.world.Chunk;
import com.ikalagaming.factory.world.ChunkPos;

import lombok.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The chunks the server has sent this client, which the renderer draws. Main thread only: the
 * client's session applies the server's requests there.
 */
public final class ClientWorld {

    /** Told when chunks arrive and go, such as the renderer. */
    public interface Listener {
        /**
         * A chunk arrived, or replaced the one there.
         *
         * @param pos Where it is.
         * @param chunk The chunk.
         */
        void chunkAdded(@NonNull ChunkPos pos, @NonNull Chunk chunk);

        /**
         * A chunk was dropped.
         *
         * @param pos Where it was.
         */
        void chunkRemoved(@NonNull ChunkPos pos);
    }

    /** The chunks, by position. */
    private final Map<ChunkPos, Chunk> chunks = new HashMap<>();

    /** Told about changes. */
    private final List<Listener> listeners = new ArrayList<>();

    /**
     * Add a listener, which is told about chunks from now on.
     *
     * @param listener The listener.
     */
    public void addListener(@NonNull Listener listener) {
        listeners.add(listener);
    }

    /**
     * Remove a listener.
     *
     * @param listener The listener.
     */
    public void removeListener(@NonNull Listener listener) {
        listeners.remove(listener);
    }

    /**
     * A chunk, if the client has it.
     *
     * @param pos Where it is.
     * @return The chunk, or empty.
     */
    public Optional<Chunk> chunk(@NonNull ChunkPos pos) {
        return Optional.ofNullable(chunks.get(pos));
    }

    /**
     * Every chunk the client has.
     *
     * @return The chunks, by position; not to be changed.
     */
    public Map<ChunkPos, Chunk> chunks() {
        return Collections.unmodifiableMap(chunks);
    }

    /**
     * How many chunks the client has.
     *
     * @return The count.
     */
    public int size() {
        return chunks.size();
    }

    /**
     * Store a chunk the server sent.
     *
     * @param pos Where it is.
     * @param chunk The chunk.
     */
    void put(@NonNull ChunkPos pos, @NonNull Chunk chunk) {
        chunks.put(pos, chunk);
        for (Listener listener : List.copyOf(listeners)) {
            listener.chunkAdded(pos, chunk);
        }
    }

    /**
     * Drop a chunk.
     *
     * @param pos Where it is.
     */
    void remove(@NonNull ChunkPos pos) {
        if (chunks.remove(pos) != null) {
            for (Listener listener : List.copyOf(listeners)) {
                listener.chunkRemoved(pos);
            }
        }
    }

    /** Drop every chunk, as when leaving the world. */
    void clear() {
        for (ChunkPos pos : List.copyOf(chunks.keySet())) {
            remove(pos);
        }
    }
}
