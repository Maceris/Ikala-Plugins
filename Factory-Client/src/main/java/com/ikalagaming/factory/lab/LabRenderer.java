package com.ikalagaming.factory.lab;

import com.ikalagaming.factory.world.Chunk;
import com.ikalagaming.factory.world.ChunkPos;
import com.ikalagaming.factory.world.gen.debug.SliceImages;
import com.ikalagaming.factory.world.gen.debug.WorldgenDebug;
import com.ikalagaming.factory.world.gen.density.DensityNode;
import com.ikalagaming.graphics.Format;
import com.ikalagaming.graphics.GraphicsContext;
import com.ikalagaming.graphics.TextureHandle;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Draws the Lab's slices off the render thread. Each view change draws the view at {@value
 * #COARSE_FACTOR}x coarser first, then at full resolution, split into bands of rows across a pool
 * of worker threads. A newer view cancels older work between bands. Finished images are uploaded as
 * textures and handed back; replaced textures are released.
 *
 * <p>Drawing goes through {@link SliceImages}, the same code the {@code worldgen render} tool uses,
 * so the Lab always agrees with it.
 */
@Slf4j
public final class LabRenderer implements AutoCloseable {

    /** How much coarser the first pass is, along each side. */
    public static final int COARSE_FACTOR = 8;

    /** How many rows each band of work draws. */
    private static final int BAND_ROWS = 8;

    /** Bytes in an RGBA pixel. */
    private static final int RGBA_BYTES = 4;

    /** Bits to shift for the red channel of a packed color. */
    private static final int RED_SHIFT = 16;

    /** Bits to shift for the green channel of a packed color. */
    private static final int GREEN_SHIFT = 8;

    /** The mask of one channel. */
    private static final int CHANNEL_MASK = 0xff;

    /** Nanoseconds in a millisecond. */
    private static final long NANOS_PER_MILLI = 1_000_000;

    /** Fully opaque. */
    private static final byte OPAQUE = (byte) 0xff;

    /**
     * A drawn view.
     *
     * @param texture The image, on the GPU.
     * @param request What was drawn.
     * @param image The image's colors, legend and value range.
     * @param fine Whether this is the full resolution pass.
     * @param millis How long drawing took.
     */
    public record Frame(
            @NonNull TextureHandle texture,
            SliceImages.@NonNull Request request,
            SliceImages.@NonNull Image image,
            boolean fine,
            long millis) {}

    /** Where textures come from. */
    private final GraphicsContext graphics;

    /** Draws bands of rows. */
    private final ExecutorService bands;

    /** Splits views into bands, one view at a time. */
    private final ExecutorService coordinator;

    /** Draws node thumbnails, behind the main view. */
    private final ExecutorService thumbnails;

    /** Counts view requests, so older ones know to stop. */
    private final AtomicLong generation = new AtomicLong();

    /** Told about each finished frame, on a worker thread. */
    private final Consumer<Frame> onFrame;

    /**
     * Start the worker threads.
     *
     * @param graphics The Lab's graphics context, for textures.
     * @param onFrame Told about each finished frame, on a worker thread.
     */
    public LabRenderer(@NonNull GraphicsContext graphics, @NonNull Consumer<Frame> onFrame) {
        this.graphics = graphics;
        this.onFrame = onFrame;
        final int workers = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
        bands = Executors.newFixedThreadPool(workers, named("Worldgen Lab band"));
        coordinator = Executors.newSingleThreadExecutor(named("Worldgen Lab view"));
        thumbnails = Executors.newSingleThreadExecutor(named("Worldgen Lab thumbnail"));
    }

    /**
     * Name the threads, and make them daemons so they never keep the process alive.
     *
     * @param prefix The name prefix.
     * @return The thread factory.
     */
    private static ThreadFactory named(@NonNull String prefix) {
        final AtomicInteger count = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + " " + count.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    /**
     * Draw a view, coarse first, then full resolution. Cancels any view still drawing.
     *
     * @param debug The world generation to draw.
     * @param request The full resolution view.
     * @param colormap How numbers become colors.
     * @param range The diverging range, or 0 or less to fit the coarse pass's values.
     */
    public void draw(
            @NonNull WorldgenDebug debug,
            SliceImages.@NonNull Request request,
            SliceImages.@NonNull Colormap colormap,
            double range) {
        final long mine = generation.incrementAndGet();
        coordinator.execute(
                () -> {
                    try {
                        final SliceImages.Request coarse =
                                new SliceImages.Request(
                                        request.target(),
                                        request.channel(),
                                        request.plane(),
                                        request.origin(),
                                        Math.max(1, request.width() / COARSE_FACTOR),
                                        Math.max(1, request.height() / COARSE_FACTOR),
                                        request.scale() * COARSE_FACTOR,
                                        request.stage());
                        Map<ChunkPos, Chunk> chunks = new ConcurrentHashMap<>();
                        final Frame first =
                                render(debug, coarse, colormap, range, mine, chunks, false);
                        if (first == null) {
                            return;
                        }
                        onFrame.accept(first);
                        final double fitted =
                                first.image().range() == null
                                        ? range
                                        : (range > 0
                                                ? range
                                                : Math.max(
                                                        Math.abs(first.image().range().min()),
                                                        Math.abs(first.image().range().max())));
                        final Frame second =
                                render(debug, request, colormap, fitted, mine, chunks, true);
                        if (second != null) {
                            onFrame.accept(second);
                        }
                    } catch (RuntimeException e) {
                        log.warn("The Worldgen Lab could not draw {}", request.target(), e);
                    }
                });
    }

    /**
     * Draw one pass, in bands across the pool.
     *
     * @param debug The world generation.
     * @param request What to draw.
     * @param colormap How numbers become colors.
     * @param range The diverging range, or 0 or less to fit the values.
     * @param mine The request's generation; stops if a newer one arrives.
     * @param chunks Generated chunks, shared between bands and passes.
     * @param fine Whether this is the full resolution pass.
     * @return The frame, or null if it was cancelled.
     */
    private Frame render(
            @NonNull WorldgenDebug debug,
            SliceImages.@NonNull Request request,
            SliceImages.@NonNull Colormap colormap,
            double range,
            long mine,
            @NonNull Map<ChunkPos, Chunk> chunks,
            boolean fine) {
        final long start = System.nanoTime();
        final DensityNode node =
                request.isCategorical() ? null : debug.resolve(request.target(), request.channel());
        final SliceImages.Samples samples = SliceImages.Samples.allocate(request);
        List<Future<?>> work = new ArrayList<>();
        for (int row = 0; row < request.height(); row += BAND_ROWS) {
            final int first = row;
            final int rows = Math.min(BAND_ROWS, request.height() - row);
            work.add(
                    bands.submit(
                            () -> {
                                if (generation.get() == mine) {
                                    SliceImages.sampleRows(
                                            debug, request, node, first, rows, chunks, samples);
                                }
                            }));
        }
        try {
            for (Future<?> band : work) {
                band.get();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (ExecutionException e) {
            throw new IllegalStateException(e.getCause());
        }
        if (generation.get() != mine) {
            return null;
        }
        final double fitted =
                range > 0 || samples.values() == null
                        ? range
                        : SliceImages.autoRange(samples.values());
        final SliceImages.Image image = SliceImages.colorize(request, samples, colormap, fitted);
        final TextureHandle texture = upload(image);
        final long millis = (System.nanoTime() - start) / NANOS_PER_MILLI;
        return new Frame(texture, request, image, fine, millis);
    }

    /**
     * Draw a node's own output as a small image, for the node tree, behind the main view.
     *
     * @param debug The world generation.
     * @param node The node.
     * @param request The slice, at thumbnail size.
     * @return The thumbnail, when done.
     */
    public CompletableFuture<Frame> thumbnail(
            @NonNull WorldgenDebug debug,
            @NonNull DensityNode node,
            SliceImages.@NonNull Request request) {
        return CompletableFuture.supplyAsync(
                () -> {
                    final long start = System.nanoTime();
                    final SliceImages.Samples samples = SliceImages.Samples.allocate(request);
                    SliceImages.sampleRows(
                            debug, request, node, 0, request.height(), Map.of(), samples);
                    final SliceImages.Image image =
                            SliceImages.colorize(
                                    request,
                                    samples,
                                    SliceImages.Colormap.DIVERGING,
                                    SliceImages.autoRange(samples.values()));
                    return new Frame(
                            upload(image),
                            request,
                            image,
                            true,
                            (System.nanoTime() - start) / NANOS_PER_MILLI);
                },
                thumbnails);
    }

    /**
     * Upload an image as a texture.
     *
     * @param image The image.
     * @return The texture.
     */
    private TextureHandle upload(SliceImages.@NonNull Image image) {
        ByteBuffer pixels =
                ByteBuffer.allocateDirect(image.width() * image.height() * RGBA_BYTES)
                        .order(ByteOrder.nativeOrder());
        for (int rgb : image.rgb()) {
            pixels.put((byte) ((rgb >> RED_SHIFT) & CHANNEL_MASK));
            pixels.put((byte) ((rgb >> GREEN_SHIFT) & CHANNEL_MASK));
            pixels.put((byte) (rgb & CHANNEL_MASK));
            pixels.put(OPAQUE);
        }
        pixels.flip();
        return graphics.textures()
                .load(pixels, Format.R8G8B8A8_UNORM, image.width(), image.height());
    }

    /**
     * Release a texture this renderer made.
     *
     * @param texture The texture, or null.
     */
    public void release(TextureHandle texture) {
        graphics.textures().release(texture);
    }

    /** Stop drawing and stop the threads. Textures go with the plugin's graphics context. */
    @Override
    public void close() {
        generation.incrementAndGet();
        coordinator.shutdownNow();
        thumbnails.shutdownNow();
        bands.shutdownNow();
    }
}
