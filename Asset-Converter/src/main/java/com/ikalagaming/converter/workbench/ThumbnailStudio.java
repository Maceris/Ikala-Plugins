package com.ikalagaming.converter.workbench;

import com.ikalagaming.converter.content.BlockContent;
import com.ikalagaming.converter.content.GameContent;
import com.ikalagaming.converter.index.AssetIndex;
import com.ikalagaming.converter.index.EditorTypes;
import com.ikalagaming.converter.index.GameTypes;
import com.ikalagaming.converter.index.Thumbnails;
import com.ikalagaming.converter.scene.BlockScenes;
import com.ikalagaming.converter.scene.ImportedScene;
import com.ikalagaming.converter.scene.PreviewShapes;
import com.ikalagaming.converter.scene.SceneImporter;
import com.ikalagaming.converter.scene.ScenePlacement;
import com.ikalagaming.converter.texgraph.Srgb;
import com.ikalagaming.graphics.GraphicsContext;
import com.ikalagaming.graphics.asset.AssetFile;
import com.ikalagaming.graphics.asset.MeshSection;
import com.ikalagaming.graphics.graph.Material;
import com.ikalagaming.graphics.graph.MeshData;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector4f;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import javax.annotation.Nullable;

/**
 * Renders thumbnails of materials, models and looks with the real renderer: a hidden 3D view, never
 * drawn in a panel, shows one file at a time as the workbench's 3D view would, and captures it into
 * the thumbnail cache. A render material goes on the sphere, a model stands on its floor, and a
 * look is shown as its block. Render thread, but for loading, done on its own thread.
 */
@Slf4j
public final class ThumbnailStudio implements AutoCloseable {
    /** A thumbnail's side, in pixels. */
    private static final int SIZE = Thumbnails.SIZE;

    /** How long one render may take before it is given up on, in nanoseconds. */
    private static final long TIMEOUT_NANOS = 20_000_000_000L;

    /** The least alpha a pixel needs to count toward a texture's average color, out of 255. */
    private static final int CUTOUT = 128;

    /** The types rendered. */
    private static final Set<String> TYPES =
            Set.of(
                    EditorTypes.RENDER_MATERIAL,
                    "material source",
                    EditorTypes.MODEL,
                    "model source",
                    GameTypes.LOOK);

    /** One file to render. */
    private record Job(
            Path file,
            String type,
            Path target,
            @Nullable Path look,
            CompletableFuture<Path> done) {}

    /** The graphics context. */
    private final GraphicsContext graphics;

    /** The thumbnail cache, which renders go into. */
    private final Thumbnails thumbnails;

    /** Runs changes on the render thread. */
    private final Consumer<Runnable> post;

    /** The hidden 3D view. */
    private final SceneView view;

    /** Reads files and loads textures. */
    private final ExecutorService loader =
            Executors.newSingleThreadExecutor(
                    runnable -> {
                        final Thread thread = new Thread(runnable, "ThumbnailStudio");
                        thread.setDaemon(true);
                        thread.setPriority(Thread.MIN_PRIORITY);
                        return thread;
                    });

    /** What waits to be rendered, the latest asked for first. */
    private final Deque<Job> queue = new ArrayDeque<>();

    /** Thumbnails that failed to render, so they aren't asked for again until their key changes. */
    private final Set<Path> failed = new java.util.HashSet<>();

    /** The render going on, or null. */
    private @Nullable Job current;

    /** When the render going on started. */
    private long started;

    /** Whether it was closed. */
    private volatile boolean closed;

    /**
     * Set up the studio.
     *
     * @param graphics The graphics context.
     * @param thumbnails The thumbnail cache.
     * @param materials Opens material files onto scenes, as the 3D view's does.
     * @param post Runs changes on the render thread.
     */
    ThumbnailStudio(
            @NonNull GraphicsContext graphics,
            @NonNull Thumbnails thumbnails,
            @NonNull MaterialEditor materials,
            @NonNull Consumer<Runnable> post) {
        this.graphics = graphics;
        this.thumbnails = thumbnails;
        this.post = post;
        view = new SceneView(graphics, materials);
        view.useForThumbnails(SIZE);
    }

    /**
     * Whether a file's thumbnail is rendered here.
     *
     * @param entry The file's entry.
     * @return True for render materials, models and looks.
     */
    public static boolean canRender(AssetIndex.@NonNull Entry entry) {
        if (GameTypes.LOOK.equals(entry.type())) {
            return GameContent.available();
        }
        return TYPES.contains(entry.type());
    }

    /**
     * Render a file's thumbnail, unless it is waiting or rendering already.
     *
     * @param entry The file's entry.
     * @param target Where the thumbnail goes.
     * @return Done with the thumbnail once rendered.
     */
    public CompletableFuture<Path> request(AssetIndex.@NonNull Entry entry, @NonNull Path target) {
        return request(entry, target, null);
    }

    /**
     * Render a file's thumbnail, unless it is waiting or rendering already; a render material with
     * the texture of a look using it, as a material alone holds no color.
     *
     * @param entry The file's entry.
     * @param target Where the thumbnail goes.
     * @param look A look using the render material, or null to show it plain.
     * @return Done with the thumbnail once rendered.
     */
    public CompletableFuture<Path> request(
            AssetIndex.@NonNull Entry entry, @NonNull Path target, @Nullable Path look) {
        if (failed.contains(target)) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("It failed to render before"));
        }
        if (current != null && current.target().equals(target)) {
            return current.done();
        }
        for (Job job : queue) {
            if (job.target().equals(target)) {
                return job.done();
            }
        }
        final Job job =
                new Job(
                        entry.file(),
                        entry.type(),
                        target,
                        GameContent.available() ? look : null,
                        new CompletableFuture<>());
        // Newest first: what was just scrolled to is what is looked at
        queue.addFirst(job);
        return job.done();
    }

    /**
     * Stop waiting to render a file, as when its tile scrolled away.
     *
     * @param file The file.
     */
    public void cancel(@NonNull Path file) {
        queue.removeIf(
                job -> {
                    if (job.file().equals(file)) {
                        job.done().cancel(false);
                        return true;
                    }
                    return false;
                });
    }

    /**
     * How many renders wait, with the one going on.
     *
     * @return The count.
     */
    public int pending() {
        return queue.size() + (current == null ? 0 : 1);
    }

    /**
     * Start the next render if none is going on, and give up on one taking too long. Each frame.
     */
    public void update() {
        if (closed) {
            return;
        }
        if (current != null && System.nanoTime() - started > TIMEOUT_NANOS) {
            finish(current, new IllegalStateException("The render took too long"));
        }
        if (current != null || queue.isEmpty()) {
            return;
        }
        final Job job = queue.pollFirst();
        if (Files.isRegularFile(job.target())) {
            job.done().complete(job.target());
            return;
        }
        current = job;
        started = System.nanoTime();
        loader.execute(() -> load(job));
    }

    /**
     * Read a file into a scene to show. Loader thread.
     *
     * @param job The render.
     */
    private void load(Job job) {
        try {
            final List<Material> values;
            final ImportedScene scene;
            Vector4f tint = null;
            switch (job.type()) {
                case EditorTypes.RENDER_MATERIAL, "material source" -> {
                    scene = PreviewShapes.scene(PreviewShapes.Shape.SPHERE);
                    values = List.of();
                    if (job.look() != null) {
                        tint = averageColor(job.look());
                    }
                }
                case EditorTypes.MODEL -> {
                    final MeshData mesh =
                            MeshSection.decode(
                                    AssetFile.open(job.file())
                                            .section(MeshSection.TAG)
                                            .orElseThrow(() -> new IOException("it has no mesh")));
                    scene = PreviewShapes.meshScene(PreviewHost.stem(name(job)), mesh);
                    values = List.of();
                }
                case GameTypes.LOOK -> {
                    final BlockContent.Look look =
                            BlockContent.readLook(job.file())
                                    .orElseThrow(() -> new IOException("it isn't a look"));
                    final BlockScenes.Built built = BlockScenes.single(look);
                    scene = built.scene();
                    values = built.values();
                }
                default -> {
                    scene = SceneImporter.read(job.file(), message -> {});
                    values = List.of();
                }
            }
            final ScenePlacement placement = ScenePlacement.prepare(graphics, scene);
            final Vector4f color = tint;
            post.accept(() -> show(job, placement, values, color));
        } catch (IOException | RuntimeException e) {
            post.accept(() -> finish(job, e));
        }
    }

    /**
     * Show a scene, then load its textures and capture it once they are in. Render thread.
     *
     * @param job The render.
     * @param placement The scene.
     * @param values Its render materials' own values, by material, for a look.
     */
    private void show(
            Job job, ScenePlacement placement, List<Material> values, @Nullable Vector4f tint) {
        if (job != current || closed) {
            placement.remove();
            return;
        }
        view.setFramingBall(
                EditorTypes.RENDER_MATERIAL.equals(job.type())
                        || "material source".equals(job.type()));
        view.show(placement);
        final List<Material> materials = placement.getMaterials();
        for (int i = 0; i < values.size() && i < materials.size(); ++i) {
            if (values.get(i) != null) {
                materials.get(i).setValues(values.get(i));
            }
        }
        if (EditorTypes.RENDER_MATERIAL.equals(job.type())
                || "material source".equals(job.type())) {
            if (!view.wearMaterial(job.file())) {
                finish(job, new IOException("Can't read the material"));
                return;
            }
            if (tint != null) {
                tintSubject(placement, tint);
            }
        }
        loader.execute(
                () -> {
                    placement.loadTextures(post::accept, () -> job != current, progress -> {});
                    post.accept(() -> capture(job));
                });
    }

    /**
     * The average color of the texture a look wears: its base color's pixels in linear light,
     * leaving out cut-out ones (alpha under half), opaque. A render material holds no color of its
     * own, so this stands in for what it is drawn as, without the texture's layout on a ball.
     *
     * @param lookFile The look.
     * @return The color, sRGB as base colors are, or null if the look has no readable texture or no
     *     opaque pixel.
     */
    static @Nullable Vector4f averageColor(@NonNull Path lookFile) {
        final Path texture =
                BlockContent.readLook(lookFile).flatMap(BlockContent::textureFile).orElse(null);
        if (texture == null) {
            return null;
        }
        final java.awt.image.BufferedImage image;
        try {
            image = Thumbnails.read(texture);
        } catch (IOException e) {
            return null;
        }
        double red = 0;
        double green = 0;
        double blue = 0;
        long count = 0;
        for (int y = 0; y < image.getHeight(); ++y) {
            for (int x = 0; x < image.getWidth(); ++x) {
                final int argb = image.getRGB(x, y);
                if ((argb >>> 24) < CUTOUT) {
                    continue;
                }
                red += Srgb.decode(argb >> 16 & 0xFF);
                green += Srgb.decode(argb >> 8 & 0xFF);
                blue += Srgb.decode(argb & 0xFF);
                ++count;
            }
        }
        if (count == 0) {
            return null;
        }
        // Averaged in linear light, then given as a base color is: sRGB, which lighting decodes
        return new Vector4f(
                Srgb.encode((float) (red / count)) / (float) Srgb.MAX_BYTE,
                Srgb.encode((float) (green / count)) / (float) Srgb.MAX_BYTE,
                Srgb.encode((float) (blue / count)) / (float) Srgb.MAX_BYTE,
                1);
    }

    /**
     * Give the shape a scene shows a base color, over the material worn.
     *
     * @param placement The scene.
     * @param color The color, sRGB.
     */
    private static void tintSubject(ScenePlacement placement, Vector4f color) {
        for (ImportedScene.Node node : placement.getImported().root().children()) {
            if (node.meshes().length > 0 && !"Floor".equals(node.name())) {
                for (int mesh : node.meshes()) {
                    placement.materialOf(node, mesh).getBaseColor().set(color);
                }
                break;
            }
        }
        final var scene = com.ikalagaming.graphics.GraphicsManager.getScene();
        if (scene != null) {
            scene.getMaterialCache().setDirty(true);
        }
    }

    /**
     * Capture the scene shown into the cache. Render thread.
     *
     * @param job The render.
     */
    private void capture(Job job) {
        if (job != current || closed) {
            return;
        }
        final Path temporary;
        try {
            Files.createDirectories(thumbnails.getFolder());
            temporary = Files.createTempFile(thumbnails.getFolder(), "render", ".png");
        } catch (IOException e) {
            finish(job, e);
            return;
        }
        view.capture(temporary)
                .whenComplete(
                        (saved, error) ->
                                post.accept(
                                        () -> {
                                            if (error != null) {
                                                finish(job, error);
                                                return;
                                            }
                                            try {
                                                thumbnails.adopt(saved, job.target());
                                                finish(job, null);
                                            } catch (IOException e) {
                                                finish(job, e);
                                            }
                                        }));
    }

    /**
     * End a render, clearing the view for the next.
     *
     * @param job The render.
     * @param error Why it failed, or null if it was made.
     */
    private void finish(Job job, @Nullable Throwable error) {
        if (job != current) {
            return;
        }
        current = null;
        view.clear();
        if (error == null) {
            job.done().complete(job.target());
        } else {
            log.debug("No thumbnail of {}: {}", job.file(), error.getMessage());
            failed.add(job.target());
            job.done().completeExceptionally(error);
        }
    }

    /**
     * A render's file name.
     *
     * @param job The render.
     * @return The name.
     */
    private static String name(Job job) {
        return job.file().getFileName().toString().toLowerCase(Locale.ROOT);
    }

    @Override
    public void close() {
        closed = true;
        loader.shutdownNow();
        queue.forEach(job -> job.done().cancel(false));
        queue.clear();
        view.close();
    }
}
