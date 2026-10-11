package com.ikalagaming.factory.lab;

import com.ikalagaming.factory.world.Chunk;
import com.ikalagaming.factory.world.ChunkPos;
import com.ikalagaming.factory.world.World;
import com.ikalagaming.factory.world.gen.Fluids;
import com.ikalagaming.factory.world.gen.Stage;
import com.ikalagaming.factory.world.gen.debug.WorldgenDebug;
import com.ikalagaming.graphics.GraphicsContext;
import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.MeshHandle;
import com.ikalagaming.graphics.RenderConfig;
import com.ikalagaming.graphics.SectionHandle;
import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.bake.BakeSource;
import com.ikalagaming.graphics.bake.Face;
import com.ikalagaming.graphics.bake.Faces;
import com.ikalagaming.graphics.bake.SectionBaker;
import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.gui.WindowManager;
import com.ikalagaming.graphics.gui.component.GuiWindow;
import com.ikalagaming.graphics.gui.data.IkInt;
import com.ikalagaming.graphics.gui.enums.Condition;
import com.ikalagaming.graphics.gui.flags.WindowFlags;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.graphics.scene.lights.AmbientLight;
import com.ikalagaming.graphics.scene.lights.DirectionalLight;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The Lab's 3D preview: a box of chunks generated up to a stage and baked into sections by the same
 * baker terrain uses, so you can walk around what the data makes. Blocks are drawn through {@link
 * BlockMeshes}. Faces between blocks in the box are hidden; faces on its outer edge show, so the
 * box can be seen in cross section.
 *
 * <p>Looking hides every surface, turns on the 3D scene with a sun, and moves the camera to look at
 * the box; the editor's camera controls take over from there. A small window leads back, which
 * restores everything.
 *
 * <p>Render thread only; generation and composing run on a worker pool.
 */
@Slf4j
final class LabPreview implements AutoCloseable {

    /** The window shown while looking at the preview. */
    private static final String LOOK_WINDOW = "Worldgen Lab preview";

    /** The box sizes offered, in chunks along each side. */
    private static final int[] SIZES = {2, 4, 8, 16};

    /** The box sizes as combo labels. */
    private static final String[] SIZE_LABELS = {
        "2^3 chunks", "4^3 chunks", "8^3 chunks", "16^3 chunks"
    };

    /** The default size, an index into {@link #SIZES}: 8 chunks, 128 blocks a side. */
    private static final int DEFAULT_SIZE = 2;

    /** How far the camera stands back from the box, as a multiple of its size. */
    private static final double CAMERA_DISTANCE = 0.9;

    /** How high the camera stands above the box's middle, as a multiple of its size. */
    private static final double CAMERA_HEIGHT = 0.6;

    /** The sun's color while looking. */
    private static final Vector3f SUN_COLOR = new Vector3f(1, 0.97f, 0.9f);

    /** The sun's direction while looking. */
    private static final Vector3f SUN_DIRECTION = new Vector3f(0.4f, -0.8f, 0.45f);

    /** The sun's intensity while looking. */
    private static final float SUN_INTENSITY = 3;

    /** The ambient light's intensity while looking. */
    private static final float AMBIENT_INTENSITY = 0.3f;

    /** How far the look window starts from the left edge, in pixels. */
    private static final float LOOK_MARGIN = 16;

    /** How far the look window starts from the top, below the toolbar, in pixels. */
    private static final float LOOK_TOP = 40;

    /** The width of the box size field, in UI units. */
    private static final float SIZE_FIELD = 160;

    /** Nanoseconds in a millisecond. */
    private static final long NANOS_PER_MILLI = 1_000_000;

    /** The plugin's graphics context. */
    private final GraphicsContext graphics;

    /** Block meshes. */
    private final BlockMeshes blockMeshes;

    /** Generates and composes. */
    private final ExecutorService workers;

    /** Counts builds, so older ones know to stop. */
    private final AtomicLong generation = new AtomicLong();

    /** The box size, an index into {@link #SIZES}. */
    private final IkInt size = new IkInt(DEFAULT_SIZE);

    /** The sections drawn. Render thread. */
    private final List<SectionHandle> sections = new ArrayList<>();

    /** The chunks of the last finished build, for the corpus. Render thread. */
    private Map<ChunkPos, Chunk> chunks = Map.of();

    /** The stage the last build generated to. Render thread. */
    private Stage builtStage;

    /** What made the last build. Render thread. */
    private WorldgenDebug builtFrom;

    /** The box's lowest corner, in blocks. Render thread. */
    private Vector3d boxCorner;

    /** The box's size, in blocks. Render thread. */
    private int boxBlocks;

    /** How the build is going. Render thread. */
    private String status = "Nothing built yet.";

    /**
     * Whether the Lab is hidden to look at the preview. Set on the render thread, read from any.
     */
    private volatile boolean looking;

    /** The pipeline before looking. Render thread. */
    private int previousPipeline;

    /** The sun before looking. Render thread. */
    private DirectionalLight previousSun;

    /** The ambient light before looking. Render thread. */
    private AmbientLight previousAmbient;

    /**
     * Set up the preview; nothing is built until asked.
     *
     * @param graphics The plugin's graphics context.
     */
    LabPreview(@NonNull GraphicsContext graphics) {
        this.graphics = graphics;
        blockMeshes = new BlockMeshes(graphics);
        // At least two: one build waits on the chunks the others generate
        final int count = Math.max(2, Runtime.getRuntime().availableProcessors() - 1);
        final AtomicInteger threads = new AtomicInteger();
        workers =
                Executors.newFixedThreadPool(
                        count,
                        runnable -> {
                            Thread thread =
                                    new Thread(
                                            runnable,
                                            "Worldgen Lab preview " + threads.incrementAndGet());
                            thread.setDaemon(true);
                            return thread;
                        });
    }

    /**
     * The data changed; the preview is out of date until rebuilt.
     *
     * @param debug The new data, or null.
     */
    void onDataChanged(WorldgenDebug debug) {
        if (builtFrom != null && debug != builtFrom && !sections.isEmpty()) {
            status = "The data changed; build again to see it.";
        }
    }

    /**
     * Draw the preview's controls in the side panel.
     *
     * @param debug The data, or null if none loaded.
     * @param view The slice view, whose center is the box's center.
     * @param stage The stage to generate to.
     */
    void draw(WorldgenDebug debug, @NonNull LabView view, @NonNull Stage stage) {
        IkGui.setNextItemWidth(SIZE_FIELD);
        IkGui.combo("Box", size, SIZE_LABELS);
        IkGui.sameLine();
        if (IkGui.button("Build at view center") && debug != null) {
            build(debug, view.getCenter(), stage);
        }
        IkGui.sameLine();
        if (IkGui.button("Clear")) {
            clear();
            status = "Cleared.";
        }
        if (!sections.isEmpty()) {
            IkGui.sameLine();
            if (IkGui.button("Look")) {
                look();
            }
        }
        IkGui.textWrapped(status);
        IkGui.textWrapped(
                "Blocks are placeholder cubes in their slice colors until block definitions name"
                        + " meshes.");
    }

    /**
     * Generate and bake a box of chunks around a point.
     *
     * @param debug The data.
     * @param center The box's center, in blocks.
     * @param stage The stage to generate to.
     */
    private void build(
            @NonNull WorldgenDebug debug, double @NonNull [] center, @NonNull Stage stage) {
        clear();
        final long mine = generation.incrementAndGet();
        final int side = SIZES[size.get()];
        final ChunkPos middle =
                ChunkPos.containing(
                        (long) Math.floor(center[0]),
                        (long) Math.floor(center[1]),
                        (long) Math.floor(center[2]));
        final ChunkPos low =
                new ChunkPos(middle.x() - side / 2, middle.y() - side / 2, middle.z() - side / 2);
        boxCorner = new Vector3d(low.blockX(), low.blockY(), low.blockZ());
        boxBlocks = side * World.CHUNK_SIZE;
        status = String.format("Generating %d chunks...", side * side * side);
        final Set<String> fluids = fluidNames(debug);
        CompletableFuture.runAsync(() -> generate(debug, low, side, stage, mine, fluids), workers)
                .exceptionally(
                        e -> {
                            log.warn("The Worldgen Lab preview could not build", e);
                            post(() -> status = "Could not build: " + e.getMessage());
                            return null;
                        });
    }

    /**
     * The blocks the world type fills open space with, drawn see-through.
     *
     * @param debug The data.
     * @return The fluid block names.
     */
    private static Set<String> fluidNames(@NonNull WorldgenDebug debug) {
        if (debug.getGenerator().getWorldType().fluids() instanceof Fluids.Level level) {
            return Set.of(level.fluid().getName());
        }
        return Set.of();
    }

    /**
     * Generate every chunk in the box, then register their blocks on the render thread. On a
     * worker.
     *
     * @param debug The data.
     * @param low The box's lowest chunk.
     * @param side Chunks along each side.
     * @param stage The stage to generate to.
     * @param mine This build's generation.
     * @param fluids The fluid block names.
     */
    private void generate(
            @NonNull WorldgenDebug debug,
            @NonNull ChunkPos low,
            int side,
            @NonNull Stage stage,
            long mine,
            @NonNull Set<String> fluids) {
        final long start = System.nanoTime();
        Map<ChunkPos, Future<Chunk>> pending = new LinkedHashMap<>();
        for (int y = 0; y < side; ++y) {
            for (int z = 0; z < side; ++z) {
                for (int x = 0; x < side; ++x) {
                    final ChunkPos pos = new ChunkPos(low.x() + x, low.y() + y, low.z() + z);
                    pending.put(
                            pos,
                            workers.submit(
                                    () ->
                                            generation.get() == mine
                                                    ? debug.generate(pos, stage)
                                                    : null));
                }
            }
        }
        Map<ChunkPos, Chunk> generated = new LinkedHashMap<>();
        Set<String> blocks = new TreeSet<>();
        try {
            for (Map.Entry<ChunkPos, Future<Chunk>> entry : pending.entrySet()) {
                final Chunk chunk = entry.getValue().get();
                if (chunk == null || generation.get() != mine) {
                    return;
                }
                generated.put(entry.getKey(), chunk);
                collectBlocks(chunk, blocks);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        } catch (ExecutionException e) {
            throw new CompletionException(e.getCause());
        }
        blocks.remove(World.AIR_NAME);
        final long generateMillis = (System.nanoTime() - start) / NANOS_PER_MILLI;
        post(
                () -> {
                    if (generation.get() != mine) {
                        return;
                    }
                    final Map<String, BlockMeshes.Entry> meshes =
                            blockMeshes.meshesFor(blocks, fluids);
                    chunks = generated;
                    builtStage = stage;
                    builtFrom = debug;
                    status =
                            String.format(
                                    "Generated %d chunks in %,d ms; composing...",
                                    generated.size(), generateMillis);
                    CompletableFuture.runAsync(
                                    () -> compose(generated, meshes, mine, generateMillis), workers)
                            .exceptionally(
                                    e -> {
                                        log.warn("The Worldgen Lab preview could not compose", e);
                                        post(() -> status = "Could not compose: " + e.getMessage());
                                        return null;
                                    });
                });
    }

    /**
     * Add every block name in a chunk to a set.
     *
     * @param chunk The chunk.
     * @param into The set.
     */
    private static void collectBlocks(@NonNull Chunk chunk, @NonNull Set<String> into) {
        if (chunk.isUniform()) {
            into.add(chunk.getBlock(0, 0, 0).getName());
            return;
        }
        for (int y = 0; y < World.CHUNK_SIZE; ++y) {
            for (int z = 0; z < World.CHUNK_SIZE; ++z) {
                for (int x = 0; x < World.CHUNK_SIZE; ++x) {
                    into.add(chunk.getBlock(x, y, z).getName());
                }
            }
        }
    }

    /**
     * Work out every chunk's placements and hidden faces and submit it as a section. On a worker.
     *
     * @param generated The chunks.
     * @param meshes The block meshes, by name.
     * @param mine This build's generation.
     * @param generateMillis How long generating took.
     */
    private void compose(
            @NonNull Map<ChunkPos, Chunk> generated,
            @NonNull Map<String, BlockMeshes.Entry> meshes,
            long mine,
            long generateMillis) {
        final long start = System.nanoTime();
        List<SectionHandle> made = new ArrayList<>();
        int placed = 0;
        for (Map.Entry<ChunkPos, Chunk> entry : generated.entrySet()) {
            if (generation.get() != mine) {
                break;
            }
            final ChunkPos pos = entry.getKey();
            final Chunk chunk = entry.getValue();
            List<MeshHandle> handles = new ArrayList<>();
            List<Integer> positions = new ArrayList<>();
            List<Byte> masks = new ArrayList<>();
            BakeSource[] neighbors = new BakeSource[Face.ALL.length];
            final int[] noRotations = new int[Face.ALL.length];
            for (int y = 0; y < World.CHUNK_SIZE; ++y) {
                for (int z = 0; z < World.CHUNK_SIZE; ++z) {
                    for (int x = 0; x < World.CHUNK_SIZE; ++x) {
                        final BlockMeshes.Entry block =
                                meshes.get(chunk.getBlock(x, y, z).getName());
                        if (block == null) {
                            continue;
                        }
                        for (Face face : Face.ALL) {
                            final int[] at = {x, y, z};
                            at[face.getAxis()] += face.getSign();
                            final BlockMeshes.Entry neighbor =
                                    neighbor(generated, meshes, pos, chunk, at);
                            neighbors[face.ordinal()] = neighbor == null ? null : neighbor.source();
                        }
                        handles.add(block.mesh());
                        positions.add(SectionBaker.pack(x, y, z));
                        masks.add(
                                (byte)
                                        Faces.hiddenFaces(
                                                block.source(), 0, neighbors, noRotations));
                    }
                }
            }
            if (handles.isEmpty()) {
                continue;
            }
            final int count = handles.size();
            int[] packed = new int[count];
            byte[] faceMasks = new byte[count];
            for (int i = 0; i < count; ++i) {
                packed[i] = positions.get(i);
                faceMasks[i] = masks.get(i);
            }
            placed += count;
            made.add(
                    graphics.sections()
                            .compose(new Vector3d(pos.blockX(), pos.blockY(), pos.blockZ()))
                            .placements(
                                    handles.toArray(MeshHandle[]::new),
                                    packed,
                                    new byte[count],
                                    faceMasks,
                                    new short[count])
                            .submit());
        }
        final long composeMillis = (System.nanoTime() - start) / NANOS_PER_MILLI;
        final int blocks = placed;
        post(
                () -> {
                    if (generation.get() != mine) {
                        made.forEach(graphics.sections()::remove);
                        return;
                    }
                    sections.addAll(made);
                    status =
                            String.format(
                                    "%d chunks: generated in %,d ms, %,d blocks composed into %d"
                                            + " sections in %,d ms.",
                                    generated.size(),
                                    generateMillis,
                                    blocks,
                                    made.size(),
                                    composeMillis);
                });
    }

    /**
     * The block next to a cell, which may be in a neighboring chunk of the box.
     *
     * @param generated The box's chunks.
     * @param meshes The block meshes, by name.
     * @param pos The cell's chunk.
     * @param chunk That chunk.
     * @param at The neighbor's coordinates within the cell's chunk, possibly just outside it.
     * @return The neighbor's mesh, or null for air or outside the box.
     */
    private static BlockMeshes.Entry neighbor(
            @NonNull Map<ChunkPos, Chunk> generated,
            @NonNull Map<String, BlockMeshes.Entry> meshes,
            @NonNull ChunkPos pos,
            @NonNull Chunk chunk,
            int @NonNull [] at) {
        Chunk holder = chunk;
        int[] offset = {0, 0, 0};
        for (int axis = 0; axis < at.length; ++axis) {
            if (at[axis] < 0) {
                offset[axis] = -1;
                at[axis] += World.CHUNK_SIZE;
            } else if (at[axis] >= World.CHUNK_SIZE) {
                offset[axis] = 1;
                at[axis] -= World.CHUNK_SIZE;
            }
        }
        if (offset[0] != 0 || offset[1] != 0 || offset[2] != 0) {
            holder =
                    generated.get(
                            new ChunkPos(
                                    pos.x() + offset[0], pos.y() + offset[1], pos.z() + offset[2]));
            if (holder == null) {
                return null;
            }
        }
        return meshes.get(holder.getBlock(at[0], at[1], at[2]).getName());
    }

    /** Hide every surface, turn on the scene with a sun, and look at the box. */
    private void look() {
        final Scene scene = GraphicsManager.getScene();
        if (scene == null || boxCorner == null) {
            return;
        }
        looking = true;
        previousPipeline = GraphicsManager.getPipelineConfig();
        GraphicsManager.swapPipeline(
                previousPipeline | RenderConfig.SCENE_ENABLED_MASK | RenderConfig.GUI_ENABLED_MASK);
        previousSun = scene.getSceneLights().getDirLight();
        previousAmbient = scene.getSceneLights().getAmbientLight();
        scene.getSceneLights()
                .setDirLight(new DirectionalLight(SUN_COLOR, SUN_DIRECTION, SUN_INTENSITY));
        scene.getSceneLights()
                .setAmbientLight(new AmbientLight(new Vector3f(1, 1, 1), AMBIENT_INTENSITY));
        // Stand south of the box and above it, facing north (-z) and down at its middle
        final double half = boxBlocks / 2.0;
        final double back = boxBlocks * CAMERA_DISTANCE;
        final double up = boxBlocks * CAMERA_HEIGHT;
        scene.getCamera()
                .setPosition(
                        boxCorner.x + half, boxCorner.y + half + up, boxCorner.z + half + back);
        scene.getCamera().setRotation((float) Math.atan2(up, back), 0);

        // Every surface goes, the editor's menu and its backdrop too; the way back is a window
        GraphicsManager.getUiManager().setSurfacesHidden(true);
        final WindowManager windows = GraphicsManager.getWindowManager();
        windows.addWindow(graphics, LOOK_WINDOW, new LookWindow());
        windows.show(LOOK_WINDOW);
    }

    /** The window shown while looking at the preview, with the way back to the Lab. */
    private final class LookWindow extends GuiWindow {
        /** Set up the window. */
        LookWindow() {
            super(LOOK_WINDOW, WindowFlags.ALWAYS_AUTO_RESIZE | WindowFlags.NO_SAVED_SETTINGS);
        }

        @Override
        public void draw(final int width, final int height) {
            IkGui.setNextWindowViewport(IkGui.getMainViewport().id);
            IkGui.setNextWindowPos(LOOK_MARGIN, LOOK_TOP, Condition.ONCE);
            IkGui.begin(title, windowOpen, windowFlags);
            IkGui.text(status);
            IkGui.text("Hold the right mouse button to turn; W, A, S, D, Space and Shift to move.");
            final boolean back = IkGui.button("Back to the Lab") || !windowOpen.get();
            IkGui.end();
            if (back) {
                // After the window ends, since stopping removes it
                stopLooking();
            }
        }

        @Override
        public boolean handleGuiInput(@NonNull Scene scene, @NonNull Window window) {
            return false;
        }
    }

    /** Show the Lab again and put the scene back as it was. Render thread. */
    private void stopLooking() {
        if (!looking) {
            return;
        }
        looking = false;
        restoreScene(previousSun, previousAmbient, previousPipeline);
        GraphicsManager.getWindowManager().removeWindow(LOOK_WINDOW);
        GraphicsManager.getUiManager().setSurfacesHidden(false);
        graphics.ui().setVisible(WorldgenLab.SURFACE_ID, true);
    }

    /**
     * Put back the lights and pipeline from before looking. Render thread.
     *
     * @param sun The sun.
     * @param ambient The ambient light.
     * @param pipeline The pipeline configuration.
     */
    private static void restoreScene(DirectionalLight sun, AmbientLight ambient, int pipeline) {
        final Scene scene = GraphicsManager.getScene();
        if (scene != null && sun != null) {
            scene.getSceneLights().setDirLight(sun);
            scene.getSceneLights().setAmbientLight(ambient);
            GraphicsManager.swapPipeline(pipeline);
        }
    }

    /** Remove the sections and stop any build. */
    private void clear() {
        generation.incrementAndGet();
        sections.forEach(graphics.sections()::remove);
        sections.clear();
        chunks = Map.of();
    }

    /**
     * Write the last build's chunks to a section corpus, with the data, seed and stage that made
     * them.
     *
     * @param dataFolder The plugin's data folder; corpora go in {@code corpus/} there.
     * @return What happened.
     */
    String exportCorpus(@NonNull Path dataFolder) {
        if (chunks.isEmpty() || builtFrom == null) {
            return "Build the 3D preview first; the corpus is its chunks.";
        }
        final String worldType = builtFrom.getGenerator().getWorldType().id();
        final String name =
                worldType.replaceAll("[^a-zA-Z0-9_.-]", "_")
                        + "-"
                        + builtFrom.seed()
                        + "-"
                        + System.currentTimeMillis()
                        + ".ika";
        final Path file = dataFolder.resolve("corpus").resolve(name);
        try {
            Files.createDirectories(file.getParent());
            SectionCorpus.write(
                    file,
                    new SectionCorpus.Source(
                            worldType, builtFrom.seed(), builtStage.name(), builtFrom.dataHash()),
                    chunks);
            return "Wrote " + chunks.size() + " chunks to " + file.toAbsolutePath();
        } catch (Exception e) {
            return "Could not write the corpus: " + e.getMessage();
        }
    }

    /**
     * Run something on the render thread.
     *
     * @param change What to run.
     */
    private void post(@NonNull Runnable change) {
        if (!graphics.isClosed()) {
            // Dropped if the plugin unloads first; graphics releases what the context owns
            graphics.ui()
                    .post(
                            () -> {
                                if (!graphics.isClosed()) {
                                    change.run();
                                }
                            });
        }
    }

    /**
     * Undo everything outside what the plugin's graphics context owns, and stop building. Safe from
     * any thread, so it works while the plugin is disabled, when work posted through the context
     * may never run: surfaces show again and materials go at once, and the scene is put back
     * through the UI manager's own queue, which outlives the plugin. Sections and meshes are the
     * context's, released with it.
     */
    void stop() {
        workers.shutdownNow();
        if (looking) {
            looking = false;
            GraphicsManager.getUiManager().setSurfacesHidden(false);
            final DirectionalLight sun = previousSun;
            final AmbientLight ambient = previousAmbient;
            final int pipeline = previousPipeline;
            GraphicsManager.getUiManager().post(() -> restoreScene(sun, ambient, pipeline));
        }
        blockMeshes.removeMaterials();
    }

    /** Stop, remove the sections, and release the meshes. Render thread. */
    @Override
    public void close() {
        stop();
        if (!graphics.isClosed()) {
            clear();
            GraphicsManager.getWindowManager().removeWindow(LOOK_WINDOW);
        }
        blockMeshes.close();
    }
}
