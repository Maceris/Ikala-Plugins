package com.ikalagaming.graphics.vulkan;

import lombok.NonNull;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * A queue of GPU resources to delete on the render thread, which can be added to from any thread.
 */
public class DeletionQueue {

    /** The types of resources that the queue supports deleting. */
    public enum ResourceType {
        BUFFER,
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
     * Add a buffer to the queue to be deleted.
     *
     * @param buffer The buffer.
     */
    public void add(@NonNull SharedBuffer buffer) {
        queue.add(new Entry(ResourceType.BUFFER, buffer));
    }

    /**
     * Add a shader to the queue to be deleted.
     *
     * @param shader The shader object.
     */
    public void add(@NonNull ShaderVulkan shader) {
        queue.add(new Entry(ResourceType.SHADER, shader));
    }

    /**
     * Add a texture to the queue to be deleted. It must already be removed from the {@link
     * TextureRegistry}.
     *
     * @param texture The texture.
     */
    public void add(@NonNull TextureInfoVulkan texture) {
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
