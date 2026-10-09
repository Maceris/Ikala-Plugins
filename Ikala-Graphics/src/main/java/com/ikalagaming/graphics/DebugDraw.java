package com.ikalagaming.graphics;

import com.ikalagaming.graphics.scene.debug.DebugShape;

import lombok.NonNull;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import javax.annotation.Nullable;

/**
 * Draws wireframe shapes into the scene for one plugin, for debugging. Shapes only show when the
 * debug stage is in the pipeline, see {@link RenderConfig}.
 *
 * <p>Immediate shapes from {@link #draw(DebugShape)} show for one frame, so call it every tick for
 * something that should stay visible. Persistent shapes from {@link #add(DebugShape)} stay until
 * removed, or until the plugin unloads. Everything here is safe from any thread.
 *
 * @see GraphicsContext#debug()
 */
public final class DebugDraw {

    /** Counts persistent shapes, so every handle is unique. */
    private static final AtomicLong NEXT_ID = new AtomicLong();

    /** Shapes to draw in the next frame only. */
    private final Queue<DebugShape> immediate = new ConcurrentLinkedQueue<>();

    /** Shapes to draw every frame until removed, by handle ID. */
    private final Map<Long, DebugShape> persistent = new ConcurrentHashMap<>();

    /** The context these shapes belong to. */
    private final GraphicsContext context;

    /**
     * Create the debug drawing API for a context.
     *
     * @param context The owning context.
     */
    DebugDraw(@NonNull GraphicsContext context) {
        this.context = context;
    }

    /**
     * Draw a shape in the next frame only.
     *
     * @param shape The shape.
     */
    public void draw(@NonNull DebugShape shape) {
        context.checkOpen();
        immediate.add(shape);
    }

    /**
     * Draw a shape every frame until it is removed.
     *
     * @param shape The shape.
     * @return The handle for removing it.
     */
    public DebugShapeHandle add(@NonNull DebugShape shape) {
        context.checkOpen();
        final long id = NEXT_ID.incrementAndGet();
        persistent.put(id, shape);
        return new DebugShapeHandle(id);
    }

    /**
     * Stop drawing a persistent shape. Does nothing if the handle is null, was already removed, or
     * belongs to another plugin.
     *
     * @param handle The shape's handle.
     */
    public void remove(@Nullable DebugShapeHandle handle) {
        if (handle != null) {
            persistent.remove(handle.id());
        }
    }

    /** Stop drawing every persistent shape this plugin added. */
    public void clear() {
        persistent.clear();
    }

    /**
     * Count the persistent shapes.
     *
     * @return The number of persistent shapes.
     */
    public int persistentCount() {
        return persistent.size();
    }

    /**
     * Hand every shape to draw this frame to a consumer: the queued immediate shapes, which are
     * removed, and the persistent shapes. Render thread only.
     *
     * @param consumer Receives each shape.
     */
    void collect(@NonNull Consumer<DebugShape> consumer) {
        DebugShape shape;
        while ((shape = immediate.poll()) != null) {
            consumer.accept(shape);
        }
        persistent.values().forEach(consumer);
    }

    /** Drop everything, because the plugin unloaded. */
    void release() {
        immediate.clear();
        persistent.clear();
    }
}
