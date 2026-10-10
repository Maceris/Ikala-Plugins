package com.ikalagaming.graphics.benchmark;

import com.ikalagaming.graphics.GraphicsContext;
import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.GraphicsPlugin;
import com.ikalagaming.graphics.LightHandle;
import com.ikalagaming.graphics.MeshHandle;
import com.ikalagaming.graphics.RenderConfig;
import com.ikalagaming.graphics.SectionHandle;
import com.ikalagaming.graphics.bake.BakeSource;
import com.ikalagaming.graphics.graph.MeshData;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.graphics.scene.lights.AmbientLight;
import com.ikalagaming.graphics.scene.lights.DirectionalLight;
import com.ikalagaming.graphics.vulkan.FrameTimings;
import com.ikalagaming.graphics.vulkan.VulkanInstance;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import javax.annotation.Nullable;

/**
 * The phase 2 gate: whether view distance 12 runs without LOD. It builds a {@link SyntheticTerrain}
 * world out to a radius, bakes every section with something in it, waits for it all to be on the
 * GPU, then turns the camera once around while recording how long each frame takes on the GPU and
 * CPU. The gate passes if the GPU's 95th percentile frame is within {@link #GPU_BUDGET_MS}.
 *
 * <p>Run it from the Graphics Debug window, or at startup with {@code -Dikala.benchmark=TERRAIN}
 * (and {@code -Dikala.benchmark.radius=12}, and {@code -Dikala.benchmark.lights=1000} to scatter
 * torches over it), which closes the app when done unless {@code -Dikala.benchmark.keep=true} keeps
 * the world to look around in. Results are logged, shown in Graphics Debug, and added to {@value
 * #RESULTS_FILE}. Everything it made is released when it finishes or is stopped, unless it was
 * asked to keep the world, which then stays to look around in until {@link #removeWorld()}.
 *
 * <p>Driven from the render thread once per frame through {@link #update(Scene)}; starting and
 * stopping are safe from the render thread.
 */
@Slf4j
public final class GateBenchmark {

    /** The world's seed, fixed so every run measures the same world. */
    public static final long SEED = 20261010L;

    /** The most the GPU may take per frame to pass, in milliseconds: half a 60 Hz frame. */
    public static final double GPU_BUDGET_MS = 8;

    /** How long the camera takes to turn once around, in seconds. */
    public static final double TURN_SECONDS = 10;

    /** How many frames to let settle once everything is resident, before measuring. */
    public static final int SETTLE_FRAMES = 60;

    /** The sun's color. */
    private static final Vector3f SUN_COLOR = new Vector3f(1, 1, 1);

    /** The way the sun's light travels: down at about 60 degrees, from one side. */
    private static final Vector3f SUN_DIRECTION = new Vector3f(0.3f, -0.85f, 0.4f).normalize();

    /** How far below level the camera looks while turning, in radians. */
    public static final float PITCH = 0.35f;

    /** Where results are added, in the working directory. */
    public static final String RESULTS_FILE = "benchmark-results.txt";

    /** The property that starts a run at startup. */
    public static final String PROPERTY = "ikala.benchmark";

    /** The color of the torches scattered over the world: a warm white. */
    private static final Vector3f TORCH_COLOR = new Vector3f(1, 0.85f, 0.6f);

    /** How bright each torch is, which with the default range reaches 10 m. */
    private static final float TORCH_INTENSITY = 1;

    /** How high above the ground each torch is, in meters. */
    private static final double TORCH_HEIGHT = 1.5;

    /** Where a run is. */
    public enum Phase {
        /** Laying out the world and composing its sections, on a worker. */
        BUILDING,
        /** Waiting for every section to be baked and uploaded. */
        BAKING,
        /** Letting the first frames with everything settle. */
        SETTLING,
        /** Turning the camera and recording. */
        MEASURING,
        /** Finished, with results. */
        DONE,
        /** Stopped or failed. */
        STOPPED
    }

    /**
     * What a run found.
     *
     * @param recipe The world.
     * @param radius The view distance, in sections.
     * @param lights How many torches were scattered over the world.
     * @param load How heavy the scene was.
     * @param buildMilliseconds How long laying out and composing took.
     * @param bakeMilliseconds How long until every section was resident, after composing.
     * @param frames How many frames were measured.
     * @param timings Each part of the frame: GPU total, CPU, then each stage.
     * @param passed Whether the GPU's 95th percentile was within budget.
     * @param width The width the scene was drawn at, in pixels.
     * @param height The height the scene was drawn at, in pixels.
     */
    public record Results(
            SyntheticTerrain.@NonNull Recipe recipe,
            int radius,
            int lights,
            GateScene.@NonNull LoadStats load,
            long buildMilliseconds,
            long bakeMilliseconds,
            int frames,
            @NonNull List<FrameTimings.Timing> timings,
            boolean passed,
            int width,
            int height) {}

    /** The run in progress, or the last one. Render thread only. */
    @Nullable private static GateBenchmark current;

    /** Whether the startup property has been looked at. */
    private static boolean checkedProperty;

    /** Whether the run was started by the startup property, so the app closes when it ends. */
    private static boolean unattended;

    /** The world. */
    private final SyntheticTerrain.Recipe recipe;

    /** The view distance, in sections. */
    private final int radius;

    /** How many torches to scatter over the world. */
    private final int lightCount;

    /** The torches, guarded by {@link #sectionsLock} since a worker places them. */
    private final List<LightHandle> lights = new ArrayList<>();

    /** The scene's view distance before the run set its own. */
    private double previousViewDistance = Double.NaN;

    /** The context everything is made under. */
    private final GraphicsContext graphics;

    /** Where the run is. */
    private volatile Phase phase = Phase.BUILDING;

    /** The block meshes, by block id. */
    private final MeshHandle[] meshes = new MeshHandle[SyntheticTerrain.BLOCK_TYPES];

    /**
     * The block meshes' data, made once and kept, so their materials are added to the cache only
     * once. Removing a material renumbers the ones after it, which would break indices already on
     * the GPU, so they stay. Render thread only.
     */
    @Nullable private static MeshData[] blockMeshes;

    /** Guards {@link #sections} and {@link #lights}, which a worker adds to while building. */
    private final ReentrantLock sectionsLock = new ReentrantLock();

    /** The sections composed. */
    private final List<SectionHandle> sections = new ArrayList<>();

    /** The load, once laid out. */
    private volatile GateScene.LoadStats load;

    /** Where the camera turns. */
    private volatile Vector3d cameraPosition;

    /** How long laying out and composing took. */
    private volatile long buildMilliseconds;

    /** When composing finished, from {@link System#nanoTime()}. */
    private long composedAt;

    /** How long baking took. */
    private long bakeMilliseconds;

    /** Frames left to settle. */
    private int settleLeft = SETTLE_FRAMES;

    /** When measuring started, from {@link System#nanoTime()}. */
    private long measureStart;

    /** Every measured frame's timings, by part, guarded by itself. */
    private final Map<String, List<Double>> samples = new LinkedHashMap<>();

    /** How many frames were measured. */
    private final AtomicInteger frames = new AtomicInteger();

    /** What went wrong, if building failed. */
    @Nullable private volatile String failure;

    /** The results, once done. */
    @Nullable private volatile Results results;

    /** Whether to leave the world in place when the run finishes. */
    private final boolean keepWorld;

    /** Whether the world has been released. */
    private boolean released;

    /** The scene's lights before the run set its own, put back when the world is released. */
    @Nullable private DirectionalLight previousSun;

    /** The scene's ambient light before the run set its own. */
    @Nullable private AmbientLight previousAmbient;

    /**
     * Set up a run.
     *
     * @param recipe The world.
     * @param radius The view distance, in sections.
     * @param lightCount How many torches to scatter over the world.
     * @param keepWorld Whether to leave the world in place when the run finishes.
     */
    private GateBenchmark(
            SyntheticTerrain.@NonNull Recipe recipe,
            int radius,
            int lightCount,
            boolean keepWorld) {
        this.recipe = recipe;
        this.radius = radius;
        this.lightCount = lightCount;
        this.keepWorld = keepWorld;
        this.graphics = GraphicsManager.forPlugin(GraphicsPlugin.PLUGIN_NAME);
    }

    /**
     * Start a run, stopping any in progress and removing a kept world. Render thread only.
     *
     * @param recipe The world.
     * @param radius The view distance, in sections.
     * @param lights How many torches to scatter over the world, to measure many lights.
     * @param keepWorld Whether to leave the world in place when the run finishes, to look around in
     *     with the camera, the debug views and a frozen observer.
     */
    public static void start(
            SyntheticTerrain.@NonNull Recipe recipe, int radius, int lights, boolean keepWorld) {
        stop();
        removeWorld();
        current = new GateBenchmark(recipe, radius, Math.max(0, lights), keepWorld);
        current.begin();
    }

    /** Remove the world a finished run kept. Render thread only. */
    public static void removeWorld() {
        if (current != null && current.isWorldKept()) {
            current.release();
            log.info("Gate benchmark world removed");
        }
    }

    /**
     * Whether a finished run left its world in place.
     *
     * @return True until it is removed.
     */
    public boolean isWorldKept() {
        return keepWorld && phase == Phase.DONE && !released;
    }

    /** Stop the run in progress, releasing what it made. Render thread only. */
    public static void stop() {
        if (current != null && current.phase != Phase.DONE && current.phase != Phase.STOPPED) {
            current.phase = Phase.STOPPED;
            current.release();
            log.info("Gate benchmark stopped");
        }
    }

    /**
     * The run in progress or the last one.
     *
     * @return The run, or null if there hasn't been one.
     */
    @Nullable public static GateBenchmark getCurrent() {
        return current;
    }

    /**
     * Where the run is.
     *
     * @return The phase.
     */
    public Phase getPhase() {
        return phase;
    }

    /**
     * What the run found.
     *
     * @return The results, or null until it is done.
     */
    @Nullable public Results getResults() {
        return results;
    }

    /**
     * Say how the run is going, for the debug window.
     *
     * @return A line of status.
     */
    public String status() {
        final GateScene.LoadStats currentLoad = load;
        return switch (phase) {
            case BUILDING -> "Laying out and composing " + recipe + " at radius " + radius;
            case BAKING -> {
                final var stats = renderer().getState().sections.getStats();
                yield String.format(
                        "Baking: %,d of %,d sections drawn, %,d on the workers",
                        stats.resident(),
                        currentLoad == null ? 0 : currentLoad.contentSections(),
                        stats.baking());
            }
            case SETTLING -> "Settling";
            case MEASURING ->
                    String.format(
                            "Measuring: %.0f%% around",
                            100
                                    * Math.min(
                                            1,
                                            (System.nanoTime() - measureStart)
                                                    / 1e9
                                                    / TURN_SECONDS));
            case DONE -> "Done";
            case STOPPED -> failure == null ? "Stopped" : "Failed: " + failure;
        };
    }

    /**
     * Advance the run in progress, and start one if the startup property asks. Render thread only,
     * once per frame before rendering.
     *
     * @param scene The scene.
     */
    public static void update(@NonNull Scene scene) {
        if (!checkedProperty) {
            checkedProperty = true;
            final String recipe = System.getProperty(PROPERTY);
            if (recipe != null) {
                final boolean keep = Boolean.getBoolean(PROPERTY + ".keep");
                // Close the app when done, unless the world stays to look around in, which plugin
                // menus would cover
                unattended = !keep;
                if (keep) {
                    GraphicsManager.getUiManager().setSurfacesHidden(true);
                }
                start(
                        SyntheticTerrain.Recipe.valueOf(recipe.trim().toUpperCase()),
                        Integer.getInteger(PROPERTY + ".radius", 12),
                        Integer.getInteger(PROPERTY + ".lights", 0),
                        keep);
            }
        }
        if (current != null) {
            current.step(scene);
            final Phase phase = current.getPhase();
            if (unattended && (phase == Phase.DONE || phase == Phase.STOPPED)) {
                unattended = false;
                log.info("Gate benchmark finished, closing");
                GraphicsManager.getWindow().requestClose();
            }
        }
    }

    /** Register the blocks, then lay out and compose the world on a worker. */
    private void begin() {
        log.info("Gate benchmark: {} at radius {} with {} lights", recipe, radius, lightCount);
        final Scene scene = GraphicsManager.getScene();
        if (blockMeshes == null) {
            blockMeshes = BlockMeshes.create();
            for (int block = 1; block < blockMeshes.length; ++block) {
                scene.getMaterialCache().addMaterial(blockMeshes[block].getMaterial());
            }
        }
        final BakeSource[] sources = new BakeSource[blockMeshes.length];
        for (int block = 1; block < blockMeshes.length; ++block) {
            meshes[block] =
                    graphics.meshes()
                            .registerBakeable(
                                    blockMeshes[block], BlockMeshes.BOX_MIN, BlockMeshes.BOX_MAX);
            sources[block] = graphics.meshes().faceInfo(meshes[block]);
        }
        scene.getMaterialCache().setDirty(true);

        final Thread builder = new Thread(() -> build(sources), "Gate benchmark builder");
        builder.setDaemon(true);
        builder.start();
    }

    /**
     * Lay out the world and compose every section with something in it. On a worker.
     *
     * @param sources The block sources, by block id.
     */
    private void build(@NonNull BakeSource @NonNull [] sources) {
        try {
            final long start = System.nanoTime();
            final SyntheticTerrain terrain = new SyntheticTerrain(recipe, SEED, sources);
            final GateScene.Layout layout = GateScene.build(terrain, radius);
            final int size = SyntheticTerrain.SECTION;
            for (GateScene.Section section : layout.sections()) {
                if (phase != Phase.BUILDING) {
                    return;
                }
                final SyntheticTerrain.Placements placements = section.placements();
                final int count = placements.count();
                final MeshHandle[] placed = new MeshHandle[count];
                for (int i = 0; i < count; ++i) {
                    placed[i] = meshes[placements.blocks()[i]];
                }
                final SectionHandle handle =
                        graphics.sections()
                                .compose(
                                        new Vector3d(
                                                section.x() * size,
                                                section.y() * size,
                                                section.z() * size))
                                .placements(
                                        placed,
                                        placements.packedPositions(),
                                        new byte[count],
                                        placements.faceMasks(),
                                        new short[count])
                                .submit();
                sectionsLock.lock();
                try {
                    sections.add(handle);
                } finally {
                    sectionsLock.unlock();
                }
            }
            placeLights(terrain);
            load = layout.load();
            cameraPosition = new Vector3d(0.5, terrain.cameraY(), 0.5);
            buildMilliseconds = (System.nanoTime() - start) / 1_000_000;
            log.info(
                    "Gate benchmark laid out {} sections with content of {} in the sphere in {} ms",
                    load.contentSections(),
                    load.sectionsInSphere(),
                    buildMilliseconds);
            phase = Phase.BAKING;
        } catch (RuntimeException e) {
            log.error("Gate benchmark failed to build", e);
            failure = e.getMessage();
            phase = Phase.STOPPED;
        }
    }

    /**
     * Scatter torches over the ground within the view distance, the same ones every run. On a
     * worker.
     *
     * @param terrain The world.
     */
    private void placeLights(@NonNull SyntheticTerrain terrain) {
        final SplittableRandom random = new SplittableRandom(SEED);
        final double reach = (double) radius * SyntheticTerrain.SECTION;
        for (int i = 0; i < lightCount && phase == Phase.BUILDING; ++i) {
            // Evenly over the disc, so the far edge isn't sparser than the middle
            final double distance = reach * Math.sqrt(random.nextDouble());
            final double angle = 2 * Math.PI * random.nextDouble();
            final int x = (int) Math.floor(distance * Math.cos(angle));
            final int z = (int) Math.floor(distance * Math.sin(angle));
            int y = terrain.maxY();
            while (y > terrain.minY() && terrain.block(x, y, z) == SyntheticTerrain.AIR) {
                --y;
            }
            final LightHandle light =
                    graphics.lights()
                            .point(
                                    new Vector3d(x + 0.5, y + 1 + TORCH_HEIGHT, z + 0.5),
                                    TORCH_COLOR,
                                    TORCH_INTENSITY);
            sectionsLock.lock();
            try {
                lights.add(light);
            } finally {
                sectionsLock.unlock();
            }
        }
    }

    /**
     * Advance one frame.
     *
     * @param scene The scene.
     */
    private void step(@NonNull Scene scene) {
        switch (phase) {
            case BAKING -> {
                if (composedAt == 0) {
                    composedAt = System.nanoTime();
                    showWorld(scene);
                }
                placeCamera(scene, 0);
                final var stats = renderer().getState().sections.getStats();
                if (stats.failed() > 0) {
                    failure = stats.failed() + " sections didn't fit in the baked geometry buffers";
                    log.error("Gate benchmark failed: {}", failure);
                    phase = Phase.STOPPED;
                    release();
                    return;
                }
                boolean allResident = true;
                sectionsLock.lock();
                try {
                    for (SectionHandle section : sections) {
                        allResident &= graphics.sections().isResident(section);
                    }
                } finally {
                    sectionsLock.unlock();
                }
                if (allResident && stats.baking() == 0) {
                    bakeMilliseconds = (System.nanoTime() - composedAt) / 1_000_000;
                    load = load.withTriangles(stats.triangles());
                    log.info(
                            "Gate benchmark: {} triangles resident after {} ms",
                            stats.triangles(),
                            bakeMilliseconds);
                    phase = Phase.SETTLING;
                }
            }
            case SETTLING -> {
                placeCamera(scene, 0);
                if (--settleLeft <= 0) {
                    phase = Phase.MEASURING;
                    measureStart = System.nanoTime();
                    renderer().getState().frameTimings.setListener(this::record);
                }
            }
            case MEASURING -> {
                final double seconds = (System.nanoTime() - measureStart) / 1e9;
                placeCamera(scene, seconds / TURN_SECONDS);
                if (seconds >= TURN_SECONDS) {
                    renderer().getState().frameTimings.setListener(null);
                    finish();
                }
            }
            default -> {
                // Nothing to do while building, or once finished
            }
        }
    }

    /**
     * Swap in a pipeline that draws the world, and light it with a low sun so the shadow cascades
     * are fitted the way they would be outdoors. The scene's own lights come back when the world is
     * released.
     *
     * @param scene The scene.
     */
    private void showWorld(@NonNull Scene scene) {
        GraphicsManager.swapPipeline(
                RenderConfig.builder().withAnimation().withScene().withSkybox().withGui().build());
        previousSun = scene.getSceneLights().getDirLight();
        previousAmbient = scene.getSceneLights().getAmbientLight();
        previousViewDistance = scene.getViewDistance();
        scene.setViewDistance((double) radius * SyntheticTerrain.SECTION);
        scene.getSceneLights().setDirLight(new DirectionalLight(SUN_COLOR, SUN_DIRECTION, 3));
        scene.getSceneLights().setAmbientLight(new AmbientLight(new Vector3f(1, 1, 1), 0.25f));
    }

    /**
     * Put the camera where it turns, facing part of the way around.
     *
     * @param scene The scene.
     * @param turns How far around, from 0 to 1.
     */
    private void placeCamera(@NonNull Scene scene, double turns) {
        final Vector3d position = cameraPosition;
        scene.getCamera().setPosition(position.x, position.y, position.z);
        scene.getCamera().setRotation(PITCH, (float) (2 * Math.PI * Math.min(1, turns)));
    }

    /**
     * Keep one frame's timings. On the render thread, as they are read back.
     *
     * @param frame Each part's time, in milliseconds.
     */
    private void record(@NonNull Map<String, Double> frame) {
        if (phase != Phase.MEASURING) {
            return;
        }
        frames.incrementAndGet();
        frame.forEach((name, ms) -> samples.computeIfAbsent(name, n -> new ArrayList<>()).add(ms));
    }

    /** Work out the results, report them, and release everything. */
    private void finish() {
        final List<FrameTimings.Timing> timings = new ArrayList<>();
        final List<String> order = new ArrayList<>();
        order.add(FrameTimings.GPU_TOTAL);
        order.add(FrameTimings.CPU);
        samples.keySet().stream().filter(name -> !order.contains(name)).forEach(order::add);
        for (String name : order) {
            final List<Double> values = samples.get(name);
            if (values != null) {
                timings.add(
                        FrameTimings.summarize(
                                name, values.stream().mapToDouble(Double::doubleValue).toArray()));
            }
        }
        final FrameTimings.Timing gpu = timings.isEmpty() ? null : timings.get(0);
        final boolean passed =
                gpu != null
                        && FrameTimings.GPU_TOTAL.equals(gpu.name())
                        && gpu.p95() <= GPU_BUDGET_MS;
        results =
                new Results(
                        recipe,
                        radius,
                        lightCount,
                        load,
                        buildMilliseconds,
                        bakeMilliseconds,
                        frames.get(),
                        timings,
                        passed,
                        GraphicsManager.getWindow().getWidth(),
                        GraphicsManager.getWindow().getHeight());
        phase = Phase.DONE;
        final String report = report(results);
        log.info("Gate benchmark results:\n{}", report);
        try {
            Files.writeString(
                    Path.of(RESULTS_FILE),
                    report + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (IOException e) {
            log.warn("Couldn't write {}", RESULTS_FILE, e);
        }
        if (keepWorld) {
            log.info("Gate benchmark keeping its world; the camera is free again");
        } else {
            release();
        }
    }

    /**
     * Write results out as text.
     *
     * @param results The results.
     * @return The report.
     */
    public static String report(@NonNull Results results) {
        final StringBuilder out = new StringBuilder();
        final GateScene.LoadStats load = results.load();
        out.append(
                String.format(
                        "%s gate benchmark: %s at radius %d with %,d lights, seed %d, %d x %d, %s%n",
                        LocalDateTime.now().withNano(0),
                        results.recipe(),
                        results.radius(),
                        results.lights(),
                        SEED,
                        results.width(),
                        results.height(),
                        results.passed() ? "PASSED" : "FAILED"));
        out.append(
                String.format(
                        "  Load: %,d sections in the sphere, %,d with content in %,d columns (%.2f per"
                                + " column), %,d placements, %,d triangles (%,.0f per section)%n",
                        load.sectionsInSphere(),
                        load.contentSections(),
                        load.columns(),
                        load.sectionsPerColumn(),
                        load.placements(),
                        load.triangles(),
                        load.trianglesPerSection()));
        out.append(
                String.format(
                        "  Laid out and composed in %,d ms, baked and uploaded in %,d ms, %d frames"
                                + " measured, budget %.1f ms of GPU at the 95th percentile%n",
                        results.buildMilliseconds(),
                        results.bakeMilliseconds(),
                        results.frames(),
                        GPU_BUDGET_MS));
        out.append(String.format("  %-24s %9s %9s %9s%n", "Part (ms)", "Average", "95%", "Worst"));
        for (FrameTimings.Timing timing : results.timings()) {
            out.append(
                    String.format(
                            "  %-24s %9.3f %9.3f %9.3f%n",
                            timing.name(), timing.average(), timing.p95(), timing.max()));
        }
        return out.toString();
    }

    /** Release every section and mesh the run made, once, and put the scene's lights back. */
    private void release() {
        if (released) {
            return;
        }
        released = true;
        final Scene scene = GraphicsManager.getScene();
        if (scene != null && previousSun != null) {
            scene.getSceneLights().setDirLight(previousSun);
            scene.getSceneLights().setAmbientLight(previousAmbient);
        }
        if (scene != null && !Double.isNaN(previousViewDistance)) {
            scene.setViewDistance(previousViewDistance);
        }
        sectionsLock.lock();
        try {
            for (SectionHandle section : sections) {
                graphics.sections().remove(section);
            }
            sections.clear();
            for (LightHandle light : lights) {
                graphics.lights().remove(light);
            }
            lights.clear();
        } finally {
            sectionsLock.unlock();
        }
        for (MeshHandle mesh : meshes) {
            graphics.meshes().release(mesh);
        }
    }

    /**
     * The running renderer.
     *
     * @return The renderer.
     */
    private static VulkanInstance renderer() {
        return GraphicsManager.getRenderInstance();
    }
}
