package com.ikalagaming.graphics;

import com.ikalagaming.graphics.scene.debug.DebugShape;
import com.ikalagaming.plugins.Plugin;

import lombok.Getter;
import lombok.NonNull;

import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import javax.annotation.Nullable;

/**
 * A plugin's view of the graphics system. Everything created through a context is owned by it, and
 * is released when its plugin is unloaded. Get one from {@link GraphicsManager#forPlugin(String)}.
 *
 * <p>A context belongs to one loaded instance of a plugin. Once that instance is unloaded the
 * context is closed, and using it throws {@link IllegalStateException}. A reloaded plugin gets a
 * new context, even if the unload is still being processed, so its resources are never released
 * along with the old ones.
 */
public final class GraphicsContext {

    /** Counts contexts, to give each one a unique owner key. */
    private static final AtomicInteger NEXT_ID = new AtomicInteger();

    /**
     * The name of the plugin this context belongs to.
     *
     * @return The plugin name.
     */
    @Getter private final String owner;

    /**
     * The key resources created through this context are tracked under. Unique to this context, so
     * a reloaded plugin's resources are tracked separately from the old instance's.
     *
     * @return The owner key.
     */
    @Getter private final String ownerKey;

    /**
     * The loaded plugin instance this context belongs to. Weak, so that the context never keeps an
     * unloaded plugin's classes alive. Holds null if the plugin wasn't loaded when the context was
     * created.
     */
    private final WeakReference<Plugin> plugin;

    /** Texture loading and releasing for this context. */
    private final Textures textures;

    /** Mesh registering and releasing for this context. */
    private final Meshes meshes;

    /** Placing models in the scene for this context. */
    private final Instances instances;

    /** The sections API for this plugin. */
    private final Sections sections;

    /** Debug shape drawing for this context. */
    private final DebugDraw debug;

    /** Retained UI for this context. */
    private final UI ui;

    /** Whether the plugin was unloaded. */
    private volatile boolean closed;

    /**
     * Create a context for a plugin.
     *
     * @param owner The plugin name.
     * @param plugin The loaded plugin instance, or null if it isn't loaded.
     */
    GraphicsContext(@NonNull String owner, @Nullable Plugin plugin) {
        this.owner = owner;
        ownerKey = owner + "#" + NEXT_ID.incrementAndGet();
        this.plugin = new WeakReference<>(plugin);
        textures = new Textures(this);
        meshes = new Meshes(this);
        instances = new Instances(this);
        sections = new Sections(this);
        debug = new DebugDraw(this);
        ui = new UI(this, GraphicsManager::getUiManager);
        closed = false;
    }

    /**
     * Load and release textures owned by this plugin.
     *
     * @return The textures API for this plugin.
     * @throws IllegalStateException If the plugin was unloaded.
     */
    public Textures textures() {
        checkOpen();
        return textures;
    }

    /**
     * Register and release meshes owned by this plugin.
     *
     * @return The meshes API for this plugin.
     * @throws IllegalStateException If the plugin was unloaded.
     */
    public Meshes meshes() {
        checkOpen();
        return meshes;
    }

    /**
     * Place models in the scene, owned by this plugin.
     *
     * @return The instances API for this plugin.
     * @throws IllegalStateException If the plugin was unloaded.
     */
    public Instances instances() {
        checkOpen();
        return instances;
    }

    /**
     * Bake sections of terrain from many placements, owned by this plugin.
     *
     * @return The sections API for this plugin.
     * @throws IllegalStateException If the plugin was unloaded.
     */
    public Sections sections() {
        checkOpen();
        return sections;
    }

    /**
     * Draw wireframe shapes into the scene for debugging, owned by this plugin.
     *
     * @return The debug drawing API for this plugin.
     * @throws IllegalStateException If the plugin was unloaded.
     */
    public DebugDraw debug() {
        checkOpen();
        return debug;
    }

    /**
     * Build and show retained UI owned by this plugin.
     *
     * @return The UI API for this plugin.
     * @throws IllegalStateException If the plugin was unloaded.
     */
    public UI ui() {
        checkOpen();
        return ui;
    }

    /**
     * Hand every debug shape this context wants drawn this frame to a consumer. Render thread only.
     *
     * @param consumer Receives each shape.
     */
    void collectDebugShapes(@NonNull Consumer<DebugShape> consumer) {
        if (!closed) {
            debug.collect(consumer);
        }
    }

    /**
     * Whether the plugin was unloaded, which makes the context unusable.
     *
     * @return True if the context is closed.
     */
    public boolean isClosed() {
        return closed;
    }

    /**
     * Whether this context belongs to a specific loaded instance of its plugin.
     *
     * @param instance The plugin instance that is loaded now, or null if none is.
     * @return True if the context was created for that instance.
     */
    boolean belongsTo(@Nullable Plugin instance) {
        return plugin.get() == instance;
    }

    /** Mark the context as unusable, because the plugin was unloaded. */
    void close() {
        closed = true;
        debug.release();
    }

    /**
     * Make sure the context can still be used.
     *
     * @throws IllegalStateException If the plugin was unloaded.
     */
    void checkOpen() {
        if (closed) {
            throw new IllegalStateException(
                    "The graphics context for " + owner + " was closed when it unloaded");
        }
    }
}
