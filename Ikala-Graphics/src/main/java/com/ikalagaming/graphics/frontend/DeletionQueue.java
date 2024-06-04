package com.ikalagaming.graphics.frontend;

import lombok.NonNull;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/** A queue of things that we want to delete. */
public class DeletionQueue {

    /** The types of resources that the queue supports deleting. */
    public enum ResourceType {
        FRAME_BUFFER,
        SHADER,
        TEXTURE
    }

    /**
     * An entry in the deletion queue. Tracks the type of resource, and some reference or handle for
     * that resource.
     *
     * @param type The type of resource to be deleted.
     * @param resource The resource object, generally a handle.
     */
    public record Entry(@NonNull ResourceType type, @NonNull Object resource) {}

    private final Queue<Entry> queue = new ConcurrentLinkedQueue<>();

    /**
     * Add a framebuffer to the queue to be deleted.
     *
     * @param framebuffer The framebuffer handle.
     */
    public void add(@NonNull Framebuffer framebuffer) {
        queue.add(new Entry(ResourceType.FRAME_BUFFER, framebuffer));
    }

    /**
     * Add a shader to the queue to be deleted.
     *
     * @param shader The shader object.
     */
    public void add(@NonNull Shader shader) {
        queue.add(new Entry(ResourceType.SHADER, shader));
    }

    /**
     * Add a texture to the queue to be deleted.
     *
     * @param texture The texture handle.
     */
    public void add(@NonNull Texture texture) {
        queue.add(new Entry(ResourceType.TEXTURE, texture));
    }

    /**
     * Fetch (and remove from the queue) the next item to be deleted.
     *
     * @return The object to delete, or null if there is not one in the queue.
     */
    public Entry pop() {
        return queue.poll();
    }
}
