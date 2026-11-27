package com.ikalagaming.graphics;

import com.ikalagaming.graphics.graph.MaterialCache;
import com.ikalagaming.graphics.graph.Model;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.graphics.vulkan.FilterView;
import com.ikalagaming.graphics.vulkan.InstanceTable;

import lombok.Getter;
import lombok.NonNull;
import org.joml.Quaternionfc;
import org.joml.Vector3dc;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * A small scene the renderer draws into its own texture, for showing in the GUI: a material on a
 * sphere, a model on a floor, a block among its neighbors. It has its own camera, lights, sky and
 * exposure in {@link #getScene()}, and sees only the instances placed through {@link #place}.
 *
 * <p>The renderer draws one preview a frame, on frames that draw no world, and only while a preview
 * has frames wanted. Ask for frames after changing anything it shows; it then keeps its last image
 * until asked again. Create them through {@code GraphicsContext.previews()}.
 */
public final class PreviewView {
    /** How many frames a change asks for, enough for temporal anti-aliasing to settle. */
    public static final int SETTLE_FRAMES = 16;

    /**
     * How much larger a supersampled preview is drawn, each way, as far as the render targets
     * allow.
     */
    public static final int SUPERSAMPLE = 2;

    /**
     * Whether it is drawn larger and filtered down, without temporal anti-aliasing, for still
     * images without jitter or ghosts. -- GETTER -- Whether it is supersampled.
     *
     * @return True if supersampled.
     */
    @Getter private volatile boolean supersampled;

    /** The key of the plugin that owns it. */
    @Getter private final String owner;

    /**
     * The view its instances are placed in, which no other preview or the world shares. -- GETTER
     * -- The view ID.
     *
     * @return The view ID.
     */
    @Getter private final int viewID;

    /**
     * What it shows: the camera, lights, sky and exposure. Change it on the render thread, then
     * call {@link #requestFrames()}. -- GETTER -- The scene.
     *
     * @return The scene.
     */
    @Getter private final Scene scene;

    /** Finds the instance table, or null while the renderer isn't running. */
    private final Supplier<InstanceTable> table;

    /**
     * The width it is drawn at, in pixels. -- GETTER -- The width.
     *
     * @return The width.
     */
    @Getter private volatile int width;

    /**
     * The height it is drawn at, in pixels. -- GETTER -- The height.
     *
     * @return The height.
     */
    @Getter private volatile int height;

    /**
     * The texture it is drawn into, for {@code DrawList.addImage} or the retained {@code Image}
     * node, or null until the renderer first draws it. It changes when the size does. -- GETTER --
     * The texture.
     *
     * @return The texture, or null.
     */
    @Getter private volatile TextureHandle texture;

    /**
     * The stages it is drawn with: {@link RenderConfig#PREVIEW}, with the debug stage while debug
     * shapes are drawn. -- GETTER -- The stages it is drawn with.
     *
     * @return The configuration.
     */
    @Getter private volatile RenderConfig renderConfig = RenderConfig.PREVIEW;

    /** How many more frames to draw it for. */
    private final AtomicInteger framesWanted = new AtomicInteger(SETTLE_FRAMES);

    /** Guards {@link #captures}. */
    private final ReentrantLock lock = new ReentrantLock();

    /** Saves asked for, each taken once it has been drawn enough frames since. */
    private final List<Pending> captures = new ArrayList<>();

    /** The instances placed in it, removed when it closes. Guarded by {@link #lock}. */
    private final List<InstanceHandle> placed = new ArrayList<>();

    /**
     * Whether it was closed. -- GETTER -- Whether it was closed.
     *
     * @return True once closed.
     */
    @Getter private volatile boolean closed;

    /**
     * A save of the image to a PNG file.
     *
     * @param path Where to save it.
     * @param done Completes with the path once written.
     */
    public record Capture(@NonNull Path path, @NonNull CompletableFuture<Path> done) {}

    /** A save waiting for frames to be drawn. Guarded by {@link #lock}. */
    private static final class Pending {
        /** The save. */
        private final Capture capture;

        /** How many more frames to draw before it is taken. */
        private int framesLeft = SETTLE_FRAMES;

        /**
         * Wait to save.
         *
         * @param capture The save.
         */
        private Pending(Capture capture) {
            this.capture = capture;
        }
    }

    /**
     * Create a preview. Use {@code GraphicsContext.previews().create} instead.
     *
     * @param owner The key of the plugin that owns it.
     * @param viewID The view its instances are placed in.
     * @param width The width to draw it at, in pixels.
     * @param height The height to draw it at, in pixels.
     * @param materials The world's material cache, which previews share.
     * @param table Finds the instance table.
     */
    public PreviewView(
            @NonNull String owner,
            int viewID,
            int width,
            int height,
            @NonNull MaterialCache materials,
            @NonNull Supplier<InstanceTable> table) {
        this.owner = owner;
        this.viewID = viewID;
        this.table = table;
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
        scene = new Scene(this.width, this.height, materials, viewID);
        // A preview is judged at a fixed exposure, so two compare under the same light
        scene.setAutoExposure(false);
    }

    /**
     * Place a model in this preview only. The model must be in the world scene with its meshes
     * registered, as for any instance. Remove it with {@code instances().remove}, or leave it to be
     * removed when the preview closes.
     *
     * @param model The model.
     * @param position The position, in the preview's own space.
     * @param rotation The rotation.
     * @param scale The uniform scale.
     * @return The handle.
     * @throws IllegalStateException If the preview was closed or the renderer isn't running.
     */
    public InstanceHandle place(
            @NonNull Model model,
            @NonNull Vector3dc position,
            @NonNull Quaternionfc rotation,
            float scale) {
        if (closed) {
            throw new IllegalStateException("The preview was closed");
        }
        final InstanceTable instances = table.get();
        if (instances == null) {
            throw new IllegalStateException("The renderer is not running");
        }
        final InstanceHandle handle =
                instances.getRegistry().place(owner, model, position, rotation, scale, viewID);
        lock.lock();
        try {
            placed.add(handle);
        } finally {
            lock.unlock();
        }
        requestFrames();
        return handle;
    }

    /**
     * Draw debug shapes placed in it, like a selection's outline, and its scene's debug
     * visualizers, or stop.
     *
     * @param drawsDebug Whether to draw them.
     */
    public void setDrawsDebug(boolean drawsDebug) {
        renderConfig = renderConfig.withDebug(drawsDebug);
        requestFrames();
    }

    /**
     * Draw it larger and filter it down, with temporal anti-aliasing off, or go back to temporal
     * anti-aliasing. Supersampling costs about four times the pixels, and is limited by the render
     * targets' size, so a preview as large as the window gains nothing.
     *
     * @param on Whether to supersample.
     */
    public void setSupersampled(boolean on) {
        supersampled = on;
        scene.setTemporalAa(!on);
        requestFrames();
    }

    /**
     * Show the lit scene, or one of the g-buffer debug views in its place, like the roughness.
     *
     * @param view What to show; {@link FilterView#DEFAULT} for the lit scene.
     */
    public void setFilterView(@NonNull FilterView view) {
        final boolean lit = view == FilterView.DEFAULT;
        scene.setFilterView(lit ? null : view);
        renderConfig = renderConfig.withFilters(!lit);
        requestFrames();
    }

    /** Ask for it to be drawn again until it settles, after something it shows changed. */
    public void requestFrames() {
        requestFrames(SETTLE_FRAMES);
    }

    /**
     * Ask for it to be drawn for at least this many more frames, like every frame while the camera
     * turns.
     *
     * @param frames How many frames.
     */
    public void requestFrames(int frames) {
        framesWanted.accumulateAndGet(frames, Math::max);
    }

    /**
     * How many more frames it wants drawn.
     *
     * @return The count.
     */
    public int getFramesWanted() {
        return framesWanted.get();
    }

    /**
     * Note that it was drawn a frame, bringing waiting saves a frame closer. Render thread.
     *
     * @return How many frames it still wants after this one.
     */
    public int frameDrawn() {
        lock.lock();
        try {
            for (Pending pending : captures) {
                --pending.framesLeft;
            }
        } finally {
            lock.unlock();
        }
        return framesWanted.updateAndGet(left -> Math.max(0, left - 1));
    }

    /**
     * Change the size it is drawn at. Its texture is replaced the next time it is drawn.
     *
     * @param newWidth The width, in pixels.
     * @param newHeight The height, in pixels.
     */
    public void resize(int newWidth, int newHeight) {
        final int w = Math.max(1, newWidth);
        final int h = Math.max(1, newHeight);
        if (w == width && h == height) {
            return;
        }
        width = w;
        height = h;
        scene.resize(w, h);
        requestFrames();
    }

    /**
     * Save the image to a PNG file once it has settled: after {@link #SETTLE_FRAMES} more frames
     * are drawn, even while it keeps asking for frames, like a view drawn every frame.
     *
     * @param path Where to save it.
     * @return Completes with the path once written, or exceptionally if the preview closes first.
     */
    public CompletableFuture<Path> capture(@NonNull Path path) {
        final CompletableFuture<Path> done = new CompletableFuture<>();
        lock.lock();
        try {
            captures.add(new Pending(new Capture(path, done)));
        } finally {
            lock.unlock();
        }
        requestFrames();
        return done;
    }

    /**
     * Take the saves that have waited their frames, to save from the frame just drawn. Render
     * thread.
     *
     * @return The saves, oldest first.
     */
    public List<Capture> takeCaptures() {
        return take(false);
    }

    /**
     * Take saves.
     *
     * @param all Whether to take every save, not only those that have waited their frames.
     * @return The saves, oldest first.
     */
    private List<Capture> take(boolean all) {
        lock.lock();
        try {
            final List<Capture> taken = new ArrayList<>();
            captures.removeIf(
                    pending -> {
                        if (all || pending.framesLeft <= 0) {
                            taken.add(pending.capture);
                            return true;
                        }
                        return false;
                    });
            return taken;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Set the texture it is drawn into. Render thread.
     *
     * @param handle The texture, or null once it is freed.
     */
    public void setTexture(TextureHandle handle) {
        texture = handle;
    }

    /** Stop drawing it, remove the instances placed in it, and free its texture. */
    public void close() {
        closed = true;
        final List<InstanceHandle> toRemove;
        lock.lock();
        try {
            toRemove = List.copyOf(placed);
            placed.clear();
        } finally {
            lock.unlock();
        }
        final InstanceTable instances = table.get();
        if (instances != null) {
            for (InstanceHandle handle : toRemove) {
                final int slot = instances.getRegistry().remove(owner, handle);
                if (slot >= 0) {
                    instances.retire(slot);
                }
            }
        }
        for (Capture capture : take(true)) {
            capture.done().completeExceptionally(new IllegalStateException("The preview closed"));
        }
    }
}
