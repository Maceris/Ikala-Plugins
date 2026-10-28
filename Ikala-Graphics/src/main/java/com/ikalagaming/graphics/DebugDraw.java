package com.ikalagaming.graphics;

import com.ikalagaming.graphics.scene.debug.DebugLabel;
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
 * Draws wireframe shapes and labels into the scene for one plugin, for debugging. Shapes and labels
 * only show when the debug stage is in the pipeline, see {@link RenderConfig}; labels are drawn by
 * the GUI, so only while it is drawn too.
 *
 * <p>Immediate shapes from {@link #draw(DebugShape)} and labels from {@link #label(DebugLabel)}
 * show for one frame, so call them every tick for something that should stay visible. Persistent
 * ones from {@link #add(DebugShape)} and {@link #addLabel(DebugLabel)} stay until removed, or until
 * the plugin unloads. Everything here is safe from any thread.
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

    /** Labels to draw in the next frame only. */
    private final Queue<DebugLabel> immediateLabels = new ConcurrentLinkedQueue<>();

    /** Labels to draw every frame until removed, by handle ID, which shapes' IDs never share. */
    private final Map<Long, DebugLabel> persistentLabels = new ConcurrentHashMap<>();

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
     * Draw a label in the next frame only.
     *
     * @param label The label.
     */
    public void label(@NonNull DebugLabel label) {
        context.checkOpen();
        immediateLabels.add(label);
    }

    /**
     * Draw a label every frame until it is removed.
     *
     * @param label The label.
     * @return The handle for removing it, with {@link #remove(DebugShapeHandle)}.
     */
    public DebugShapeHandle addLabel(@NonNull DebugLabel label) {
        context.checkOpen();
        final long id = NEXT_ID.incrementAndGet();
        persistentLabels.put(id, label);
        return new DebugShapeHandle(id);
    }

    /**
     * Stop drawing a persistent shape or label. Does nothing if the handle is null, was already
     * removed, or belongs to another plugin.
     *
     * @param handle The shape's or label's handle.
     */
    public void remove(@Nullable DebugShapeHandle handle) {
        if (handle != null) {
            persistent.remove(handle.id());
            persistentLabels.remove(handle.id());
        }
    }

    /** Stop drawing every persistent shape and label this plugin added. */
    public void clear() {
        persistent.clear();
        persistentLabels.clear();
    }

    /**
     * Count the persistent shapes and labels.
     *
     * @return The number of persistent shapes and labels.
     */
    public int persistentCount() {
        return persistent.size() + persistentLabels.size();
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

    /**
     * Hand every label to draw this frame to a consumer: the queued immediate labels, which are
     * removed, and the persistent labels. Render thread only.
     *
     * @param consumer Receives each label.
     */
    void collectLabels(@NonNull Consumer<DebugLabel> consumer) {
        DebugLabel label;
        while ((label = immediateLabels.poll()) != null) {
            consumer.accept(label);
        }
        persistentLabels.values().forEach(consumer);
    }

    /** Drop everything, because the plugin unloaded. */
    void release() {
        immediate.clear();
        persistent.clear();
        immediateLabels.clear();
        persistentLabels.clear();
    }
}
