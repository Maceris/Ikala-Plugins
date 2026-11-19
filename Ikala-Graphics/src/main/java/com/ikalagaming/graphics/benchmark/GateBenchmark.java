package com.ikalagaming.graphics.benchmark;

import static org.lwjgl.util.vma.Vma.vmaBuildStatsString;
import static org.lwjgl.util.vma.Vma.vmaCalculateStatistics;
import static org.lwjgl.util.vma.Vma.vmaFreeStatsString;

import com.ikalagaming.graphics.Format;
import com.ikalagaming.graphics.GraphicsContext;
import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.GraphicsPlugin;
import com.ikalagaming.graphics.LightHandle;
import com.ikalagaming.graphics.LodHandle;
import com.ikalagaming.graphics.MeshHandle;
import com.ikalagaming.graphics.RenderConfig;
import com.ikalagaming.graphics.SectionHandle;
import com.ikalagaming.graphics.Sections;
import com.ikalagaming.graphics.TextureHandle;
import com.ikalagaming.graphics.ThreadStops;
import com.ikalagaming.graphics.bake.BakeSource;
import com.ikalagaming.graphics.bake.SectionBaker;
import com.ikalagaming.graphics.graph.Material;
import com.ikalagaming.graphics.graph.MeshData;
import com.ikalagaming.graphics.graph.SunShadowCache;
import com.ikalagaming.graphics.lod.CrackCheck;
import com.ikalagaming.graphics.lod.LodCache;
import com.ikalagaming.graphics.lod.LodStore;
import com.ikalagaming.graphics.scene.PhysicalSky;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.graphics.scene.WaterTile;
import com.ikalagaming.graphics.scene.lights.AmbientLight;
import com.ikalagaming.graphics.scene.lights.DirectionalLight;
import com.ikalagaming.graphics.vulkan.FrameTimings;
import com.ikalagaming.graphics.vulkan.LodRenderer;
import com.ikalagaming.graphics.vulkan.VulkanInstance;
import com.ikalagaming.util.IntArrayList;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.vma.VmaTotalStatistics;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.ReentrantLock;
import javax.annotation.Nullable;

/**
 * The phase 2 gate: whether view distance 12 runs without LOD. It builds a {@link SyntheticTerrain}
 * world out to a radius, bakes every section with something in it, waits for it all to be on the
 * GPU, then turns the camera once around while recording how long each frame takes on the GPU and
 * CPU. The gate passes if the GPU's 95th percentile frame is within {@link
 * GateReport#GPU_BUDGET_MS}.
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

    /** The name of the thread laying out the world. */
    private static final String BUILDER_NAME = "Gate benchmark builder";

    /**
     * How long the camera takes to turn once around, in seconds, unless the {@code .seconds}
     * property says, for a longer walk through the world while measuring.
     */
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

    /** How many tree cells out from the origin to look for a hut for the hut torch. */
    private static final int HUT_SEARCH_CELLS = 8;

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
        /** Checking the level of detail's cut for cracks, for the gate. */
        CHECKING,
        /** Digging near the camera and timing the edits, for the gate. */
        EDITING,
        /** Finished, with results. */
        DONE,
        /** Stopped or failed. */
        STOPPED
    }

    /**
     * Where the cut is checked for cracks: how far out, as a part of the view distance, and at what
     * angle around, in degrees. The cut depends on where the eye is, not where it looks.
     */
    private static final double[][] CHECK_SPOTS = {{0, 0}, {0.5, 0}, {0.5, 120}, {0.5, 240}};

    /** How high above the ground the camera is at each check, in blocks. */
    private static final double CHECK_HEIGHT = 6;

    /** Frames everything must stay loaded in a row before the world counts as ready. */
    private static final int READY_FRAMES = 10;

    /** The width of the columns the crack check works through, in blocks. */
    private static final double CHECK_COLUMN = 64;

    /** How long a view is held before its cut is checked, so streaming can catch up. */
    private static final long CHECK_HOLD_NANOS = 1_000_000_000L;

    /** How long the gate's edits may take to reach the level of detail, all told. */
    private static final long EDIT_TIMEOUT_NANOS = 60_000_000_000L;

    /** The percentile edit times are given at. */
    private static final double PERCENTILE = 0.95;

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

    /** The measured frames' timings. */
    private final GateFrameRecorder recorder =
            new GateFrameRecorder(() -> this.phase == Phase.MEASURING);

    /** The scene options set for the run, which come before the startup properties. */
    private final GateOptions options = new GateOptions();

    /** Places to look from, its preset's and those the run finds. */
    private final GateViews views = new GateViews();

    /** The run's fog, smoke, particle effects and weather. */
    private final GateEffects effects;

    /** The run's sea, fountain, lakes and test water. */
    private final GateWater water;

    /** Where the run is. */
    private volatile Phase phase = Phase.BUILDING;

    /** The block meshes, by block id. */
    private final MeshHandle[] meshes = new MeshHandle[SyntheticTerrain.BLOCK_TYPES];

    /**
     * Whether runs place textured test meshes on the terrain (see {@link TestMeshes}), from the
     * startup property.
     */
    private static final boolean TEXTURED_MESHES = Boolean.getBoolean(PROPERTY + ".textured");

    /** How far apart the textured test meshes stand, in blocks. */
    private static final int TEST_MESH_SPACING = 24;

    /** The textured test mesh, or null if this run has none. */
    @Nullable private MeshHandle testMesh;

    /**
     * Each section's cells with a textured test mesh, packed as {@link SectionBaker#pack} packs
     * them; empty without test meshes. Set before any section is composed, then only read.
     */
    private volatile Map<LodStore.SectionPos, IntArrayList> testCells = Map.of();

    /**
     * A section's placements as composed: the terrain's, then any textured test meshes.
     *
     * @param meshes Each placement's mesh.
     * @param packedPositions Each placement's cell.
     * @param faceMasks The faces each placement's neighbors hide.
     */
    private record Composed(
            MeshHandle @NonNull [] meshes,
            int @NonNull [] packedPositions,
            byte @NonNull [] faceMasks) {
        /**
         * How many placements there are.
         *
         * @return The count.
         */
        int count() {
            return meshes.length;
        }
    }

    /**
     * The block meshes' data, made once and kept, so their materials are added to the cache only
     * once. Removing a material renumbers the ones after it, which would break indices already on
     * the GPU, so they stay. Render thread only.
     */
    @Nullable private static MeshData[] blockMeshes;

    /** Guards {@link #sections} and {@link #lights}, which a worker adds to while building. */
    private final ReentrantLock sectionsLock = new ReentrantLock();

    /** The sections composed. */
    private final Set<SectionHandle> sections = new LinkedHashSet<>();

    /** Each composed section by position, to rebake after a dig. Guarded by sectionsLock. */
    private final Map<LodStore.SectionPos, SectionHandle> sectionAt = new HashMap<>();

    /** Whether the world's opaque blocks are drawn by live level of detail. */
    private final boolean lod;

    /** Whether the run also checks the level of detail gate: cracks, edits and the cache. */
    private final boolean gate;

    /** The next crack check, by index in {@link #CHECK_SPOTS}, then one after the edits. */
    private int checkIndex;

    /** When the current view was taken up, from {@link System#nanoTime()}. */
    private long checkSince;

    /** Where the camera is for the current check. */
    @Nullable private Vector3d checkPosition;

    /** When everything was first seen loaded in the current run of frames, or 0. */
    private long readySince;

    /** Frames in a row everything has been loaded. */
    private int readyFrames;

    /** The crack check running on a worker, or null. */
    @Nullable private CompletableFuture<CrackCheck.Tiled> checking;

    /** The cut being checked. */
    @Nullable private LodRenderer.Cut checkCut;

    /** The checks so far. */
    private final List<GateReport.Check> checks = new ArrayList<>();

    /** When the gate's edits started, from {@link System#nanoTime()}. */
    private long editStart;

    /** Edits that didn't finish in time. */
    private int editsUnfinished;

    /** The most frames an edit took to be drawn. */
    private int worstDrawnFrames;

    /** Where in a dig's latency the frames until it was drawn are. */
    private static final int LATENCY_FRAMES = 2;

    /** What the cache had done once the world was loaded, or null. */
    @Nullable private LodCache.Stats cacheAtLoad;

    /** Level of detail bytes on the GPU and kept, once loaded. */
    private long[] lodBytesAtLoad = new long[2];

    /** The live level of detail world, while there is one. */
    @Nullable private LodHandle lodWorld;

    /** The world being drawn, once laid out, for digging. */
    @Nullable private volatile SyntheticTerrain terrain;

    /** Digs waiting to be drawn and to reach level of detail. Render thread. */
    private final List<Dig> digs = new ArrayList<>();

    /** Where in a dig's block the dug block's id is, after its position. */
    private static final int DUG_BLOCK = 3;

    /** Digs left to make on their own, from the startup property. */
    private int digsLeft;

    /** Digs a second for the digs from the startup property. */
    private double propertyDigRate = PROPERTY_DIG_RATE;

    /** Digs a second to make on their own, 0 for none. */
    private double digRate;

    /** When the last dig was made, from {@link System#nanoTime()}. */
    private long lastDig;

    /** Every dig's latency so far: until drawn, then until level of detail, in milliseconds. */
    private final List<double[]> digLatencies = new ArrayList<>();

    /** How far in front of the camera a dig is, in blocks. */
    private static final double DIG_REACH = 6;

    /** How wide the pit digs from the startup property make, in blocks. */
    private static final int PIT_WIDTH = 7;

    /** How long the pit is, in blocks. */
    private static final int PIT_LENGTH = 3;

    /** How high above the pit the camera looks down from, for digs from the startup property. */
    private static final double PIT_VIEW_HEIGHT = 10;

    /** How far down the camera looks at the pit, in radians. */
    private static final float PIT_VIEW_PITCH = 1.1f;

    /** How many digs were made. */
    private int digCount;

    /** Whether the camera was placed over the pit for digs from the startup property. */
    private boolean pitView;

    /**
     * The pit's first cell, x and z, once found: an open patch of ground the digs from the startup
     * property and the gate make, row by row toward -z. Null until then.
     */
    @Nullable private int[] pit;

    /** How much the ground may rise or fall across the pit's patch, in blocks. */
    private static final int PIT_GROUND_STEP = 1;

    /** How far from where the camera turns to look for an open patch for the pit, in blocks. */
    private static final int PIT_SEARCH = 96;

    /** Digs a second when they are made on their own from the startup property, by default. */
    private static final double PROPERTY_DIG_RATE = 2;

    /** Texels in a million. */
    private static final double TEXELS_PER_MEGA = 1e6;

    /** Nanoseconds in a second. */
    private static final double NANOS_PER_SECOND = 1e9;

    /** Nanoseconds in a millisecond. */
    private static final double NANOS_PER_MILLI = 1e6;

    /** One dig, waiting to be drawn and to reach level of detail. */
    private static final class Dig {
        /** The section the dug block was in. */
        final SectionHandle section;

        /** Where that section is. */
        final LodStore.SectionPos position;

        /** The bake that has the block gone. */
        final long generation;

        /** When it was made, from {@link System#nanoTime()}. */
        final long start;

        /** The block dug: x, y, z, then which block it was, see {@link #DUG_BLOCK}. */
        final int[] block;

        /** When the bake was drawn, or 0. */
        long drawn;

        /** Frames until it was drawn. */
        int frames;

        /**
         * Keep a dig.
         *
         * @param section The section.
         * @param position Its position.
         * @param generation The bake with the block gone.
         * @param block The block dug.
         */
        Dig(SectionHandle section, LodStore.SectionPos position, long generation, int[] block) {
            this.section = section;
            this.position = position;
            this.generation = generation;
            this.block = block;
            start = System.nanoTime();
        }
    }

    /** The load, once laid out. */
    private volatile GateScene.LoadStats load;

    /** Where the camera turns. */
    private volatile Vector3d cameraPosition;

    /** How bright the benchmark's sun is. */
    private static final float SUN_INTENSITY = 3;

    /** How bright the benchmark's ambient light is. */
    private static final float AMBIENT_INTENSITY = 0.25f;

    /** How bright the ambient light is at night, from the startup property. */
    private static final float NIGHT_AMBIENT_INTENSITY = 0.01f;

    /** Whether the torches cast shadows, from the startup property. */
    private static boolean lightShadows = Boolean.getBoolean(PROPERTY + ".lightShadows");

    /** How fast the camera walks along x while it turns, in meters a second, 0 to stand still. */
    private double walkSpeed;

    /**
     * Where the walk goes instead of along x, from the {@code .walkPath} option: x and z offsets
     * from the start, in meters, each pair a waypoint, standing at the last; or null. Turning back
     * crosses sections that streamed out and in again, and standing lets them settle.
     */
    private double[] walkPath;

    /**
     * How many of the {@link #CHECK_SPOTS} the gate checks for cracks, the first so many: each
     * takes minutes at a large radius, and most runs only need some.
     */
    private int crackSpots = CHECK_SPOTS.length;

    /** Whether the gate checks the cut over its edits for cracks, after making them. */
    private boolean editCracks = true;

    /** How far above the ground a walking camera keeps, in blocks. */
    private static final double WALK_CLEARANCE = 6;

    /** What the sun shadow cache had done when measuring started. */
    @Nullable private SunShadowCache.Stats shadowCacheAtStart;

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

    /** What went wrong, if building failed. */
    @Nullable private volatile String failure;

    /** The results, once done. */
    @Nullable private volatile GateReport.Results results;

    /** Whether to leave the world in place when the run finishes. */
    private final boolean keepWorld;

    /**
     * Whether the world has been released. Set by the render thread, and read by workers that make
     * sections or lights, which release what they make once it is set.
     */
    private volatile boolean released;

    /** The thread laying out and composing the world, or null once it was waited for. */
    @Nullable private Thread builder;

    /** The scene's lights before the run set its own, put back when the world is released. */
    @Nullable private DirectionalLight previousSun;

    /** The scene's ambient light before the run set its own. */
    @Nullable private AmbientLight previousAmbient;

    /** The physical sky before the run, or null for the skybox. */
    @Nullable private PhysicalSky previousPhysicalSky;

    /** Whether the exposure adapted before the run. */
    private boolean previousAutoExposure;

    /** Whether the fog was drawn before the run. */
    private boolean previousFog;

    /** The stream keeping the world composed around the camera, or null if it isn't streamed. */
    @Nullable private WorldStream stream;

    /** Changes the stream's worker makes to the run on the render thread, such as lake water. */
    private final Queue<Runnable> streamTasks = new ConcurrentLinkedQueue<>();

    /** Frames since the stream last checked which sections are drawn. Render thread. */
    private int streamPollFrames;

    /** When the stream was last logged, in nanoseconds, or 0. Render thread. */
    private long streamLoggedAt;

    /** The largest backlog the stream logged. Render thread. */
    private int streamMostBacklog;

    /** How many frames apart the stream checks which sections are drawn. */
    private static final int STREAM_POLL_FRAMES = 4;

    /** How often the stream is logged while measuring, in seconds. */
    private static final double STREAM_LOG_SECONDS = 2;

    /** How often the stream is logged once the run is done and kept, in seconds. */
    private static final double STREAM_LOG_KEPT_SECONDS = 10;

    /** Values in each waypoint of the {@code .walkPath} option: x and z. */
    private static final int WALK_POINT_VALUES = 2;

    /**
     * Set up a run.
     *
     * @param recipe The world.
     * @param radius The view distance, in sections.
     * @param lightCount How many torches to scatter over the world.
     * @param keepWorld Whether to leave the world in place when the run finishes.
     * @param lod Whether the world's opaque blocks are drawn by live level of detail.
     * @param gate Whether to check the level of detail gate too, which draws with it.
     */
    private GateBenchmark(
            SyntheticTerrain.@NonNull Recipe recipe,
            int radius,
            int lightCount,
            boolean keepWorld,
            boolean lod,
            boolean gate) {
        this.recipe = recipe;
        this.radius = radius;
        this.lightCount = lightCount;
        this.keepWorld = keepWorld;
        this.lod = lod || gate;
        this.gate = gate;
        this.graphics = GraphicsManager.forPlugin(GraphicsPlugin.PLUGIN_NAME);
        this.effects = new GateEffects(graphics, options, () -> terrain);
        this.water = new GateWater(graphics, options, radius);
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
        start(recipe, radius, lights, keepWorld, false);
    }

    /**
     * Start a run, stopping any in progress and removing a kept world. Render thread only.
     *
     * @param recipe The world.
     * @param radius The view distance, in sections.
     * @param lights How many torches to scatter over the world, to measure many lights.
     * @param keepWorld Whether to leave the world in place when the run finishes, to look around in
     *     with the camera, the debug views and a frozen observer.
     * @param lod Whether the world's opaque blocks are drawn by live level of detail, built from
     *     its sections, rather than by the sections.
     */
    public static void start(
            SyntheticTerrain.@NonNull Recipe recipe,
            int radius,
            int lights,
            boolean keepWorld,
            boolean lod) {
        start(recipe, radius, lights, keepWorld, lod, false);
    }

    /**
     * Start a run, stopping any in progress and removing a kept world. Render thread only.
     *
     * @param recipe The world.
     * @param radius The view distance, in sections.
     * @param lights How many torches to scatter over the world, to measure many lights.
     * @param keepWorld Whether to leave the world in place when the run finishes, to look around in
     *     with the camera, the debug views and a frozen observer.
     * @param lod Whether the world's opaque blocks are drawn by live level of detail, built from
     *     its sections, rather than by the sections.
     * @param gate Whether to check the level of detail gate after the turn: the cut for cracks at
     *     several headings, then edits near the camera, with the cache's work while loading. Draws
     *     with level of detail.
     */
    public static void start(
            SyntheticTerrain.@NonNull Recipe recipe,
            int radius,
            int lights,
            boolean keepWorld,
            boolean lod,
            boolean gate) {
        stop();
        removeWorld();
        current = new GateBenchmark(recipe, radius, Math.max(0, lights), keepWorld, lod, gate);
        current.begin();
    }

    /**
     * Start a ready-made run, keeping its world to look around in, stopping any in progress and
     * removing a kept world. Render thread only.
     *
     * @param preset The run.
     */
    public static void start(GatePresets.@NonNull Preset preset) {
        stop();
        removeWorld();
        current = new GateBenchmark(preset.recipe(), preset.radius(), 0, true, preset.lod(), false);
        current.options.putAll(preset.options());
        current.views.addAll(preset.views());
        current.begin();
    }

    /**
     * Start a run with scene options, stopping any in progress and removing a kept world. Render
     * thread only.
     *
     * @param recipe The world.
     * @param radius The view distance, in sections.
     * @param lights How many torches to scatter over the world.
     * @param keepWorld Whether to leave the world in place when the run finishes.
     * @param lod Whether the world's opaque blocks are drawn by live level of detail.
     * @param gate Whether to check the level of detail gate after the turn.
     * @param sceneOptions The scene's options, like {@code loop} or {@code wind}, which come before
     *     the startup properties.
     */
    public static void start(
            SyntheticTerrain.@NonNull Recipe recipe,
            int radius,
            int lights,
            boolean keepWorld,
            boolean lod,
            boolean gate,
            @NonNull Map<String, String> sceneOptions) {
        stop();
        removeWorld();
        current = new GateBenchmark(recipe, radius, Math.max(0, lights), keepWorld, lod, gate);
        current.options.putAll(sceneOptions);
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

    /**
     * Stop the run in progress, releasing what it made. A builder still laying out the world is
     * stopped and waited for first, so it doesn't add sections after they are released. Render
     * thread only.
     */
    public static void stop() {
        if (current != null && current.phase != Phase.DONE && current.phase != Phase.STOPPED) {
            current.halt();
            current.release();
            log.info("Gate benchmark stopped");
        }
    }

    /**
     * Mark the run stopped, so its builder moves it on no further, and wait a little for the
     * builder to end. One that outlasts the wait releases whatever it makes afterward itself, see
     * {@link #released}. Render thread only.
     */
    private void halt() {
        sectionsLock.lock();
        try {
            phase = Phase.STOPPED;
        } finally {
            sectionsLock.unlock();
        }
        ThreadStops.join(builder, ThreadStops.STOP_WAIT, BUILDER_NAME);
        builder = null;
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
     * The places to look from in the run's world, for the debug window.
     *
     * @return The views.
     */
    public GateViews views() {
        return views;
    }

    /**
     * The run's fog, smoke, particle effects and weather, for the debug window.
     *
     * @return The effects.
     */
    public GateEffects effects() {
        return effects;
    }

    /**
     * The run's water, for the debug window.
     *
     * @return The water.
     */
    public GateWater water() {
        return water;
    }

    /**
     * What the run found.
     *
     * @return The results, or null until it is done.
     */
    @Nullable public GateReport.Results getResults() {
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
                                                    / options.measureSeconds()));
            case CHECKING ->
                    String.format(
                            "Checking for cracks: %d of %d",
                            Math.min(checkIndex, crackSpots) + 1,
                            crackSpots + (editCracks ? 1 : 0));
            case EDITING ->
                    String.format("Editing: %d of %d edits made", digCount, GateReport.GATE_DIGS);
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
                // The renderer's settings and debug views; before starting, which opens the
                // level of detail cache and makes the live world
                GateOptions.applyStartupSettings(scene);
                start(
                        SyntheticTerrain.Recipe.valueOf(recipe.trim().toUpperCase(Locale.ROOT)),
                        Integer.getInteger(PROPERTY + ".radius", 12),
                        Integer.getInteger(PROPERTY + ".lights", 0),
                        keep,
                        Boolean.getBoolean(PROPERTY + ".lod"),
                        Boolean.getBoolean(PROPERTY + ".gate"));
                current.digsLeft = Integer.getInteger(PROPERTY + ".digs", 0);
                current.walkSpeed = Double.parseDouble(System.getProperty(PROPERTY + ".walk", "0"));
                current.walkPath = parseWalkPath(System.getProperty(PROPERTY + ".walkPath"));
                // Fewer crack checks, for runs that only need some: the first so many views, and
                // whether the cut over the edits is checked; the edits are made and timed anyway
                current.crackSpots =
                        Math.clamp(
                                Integer.getInteger(PROPERTY + ".cracks", CHECK_SPOTS.length),
                                0,
                                CHECK_SPOTS.length);
                final String editCracks = System.getProperty(PROPERTY + ".editCracks");
                if (editCracks != null) {
                    current.editCracks = Boolean.parseBoolean(editCracks);
                }
                current.propertyDigRate =
                        Double.parseDouble(
                                System.getProperty(
                                        PROPERTY + ".digRate", String.valueOf(PROPERTY_DIG_RATE)));
            }
        }
        if (current != null) {
            current.step(scene);
            final Phase phase = current.getPhase();
            if (unattended && (phase == Phase.DONE || phase == Phase.STOPPED)) {
                unattended = false;
                log.info("Gate benchmark finished, closing");
                GraphicsManager.getMainWindow().requestClose();
            }
        }
    }

    /** Register the blocks, then lay out and compose the world on a worker. */
    private void begin() {
        log.info("Gate benchmark: {} at radius {} with {} lights", recipe, radius, lightCount);
        final Scene scene = GraphicsManager.getScene();
        if (blockMeshes == null) {
            final BlockMeshes.Textured blocks = BlockMeshes.textured(graphics.textures());
            blockMeshes = blocks.meshes();
            blocks.materials().forEach(scene.getMaterialCache()::addMaterial);
        }
        final BakeSource[] sources = new BakeSource[blockMeshes.length];
        for (int block = 1; block < blockMeshes.length; ++block) {
            meshes[block] =
                    graphics.meshes()
                            .registerBakeable(
                                    blockMeshes[block], BlockMeshes.BOX_MIN, BlockMeshes.BOX_MAX);
            sources[block] = graphics.meshes().faceInfo(meshes[block]);
        }
        if (TEXTURED_MESHES && testMesh == null) {
            testMesh = registerTestMesh(scene);
        }
        scene.getMaterialCache().setDirty(true);
        if (lod) {
            lodWorld = graphics.lods().fromSections();
            final String pixels = options.get("lodPx");
            if (pixels != null) {
                graphics.lods().setThreshold(lodWorld, Double.parseDouble(pixels));
            }
        }

        builder = ThreadStops.thread(BUILDER_NAME, () -> build(sources));
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
            terrain.setFountain(options.get("fountain") != null);
            terrain.setGlassTest(options.get("glassTest") != null);
            terrain.setWaterTiles(terrain.hasLakes());
            this.terrain = terrain;
            water.layOutLakes(terrain);
            final GateScene.Layout layout = GateScene.build(terrain, radius);
            if (testMesh != null) {
                testCells = testCells(terrain);
            }
            final Set<LodStore.SectionPos> composed = new HashSet<>();
            for (GateScene.Section section : layout.sections()) {
                if (phase != Phase.BUILDING) {
                    return;
                }
                final LodStore.SectionPos position =
                        new LodStore.SectionPos(section.x(), section.y(), section.z());
                compose(position, section.placements());
                composed.add(position);
            }
            // Test meshes standing in sections with nothing else
            for (LodStore.SectionPos position : testCells.keySet()) {
                if (!composed.contains(position)) {
                    compose(
                            position,
                            new SyntheticTerrain.Placements(
                                    0, new byte[0], new int[0], new byte[0]));
                }
            }
            placeLights(terrain);
            load = layout.load();
            cameraPosition = options.turnPosition(terrain);
            buildMilliseconds = (System.nanoTime() - start) / 1_000_000;
            log.info(
                    "Gate benchmark laid out {} sections with content of {} in the sphere in {} ms",
                    load.contentSections(),
                    load.sectionsInSphere(),
                    buildMilliseconds);
            moveOn(Phase.BAKING, null);
        } catch (RuntimeException e) {
            if (moveOn(Phase.STOPPED, e.getMessage())) {
                log.error("Gate benchmark failed to build", e);
            }
        }
    }

    /**
     * Move a run the builder finished on, unless it was stopped meanwhile. On the builder.
     *
     * @param next The phase it goes to.
     * @param failed What went wrong, or null if nothing did.
     * @return True if it moved on, false if it was stopped already.
     */
    private boolean moveOn(@NonNull Phase next, @Nullable String failed) {
        sectionsLock.lock();
        try {
            if (phase != Phase.BUILDING) {
                return false;
            }
            if (failed != null) {
                failure = failed;
            }
            phase = next;
            return true;
        } finally {
            sectionsLock.unlock();
        }
    }

    /**
     * Put a torch in the middle of the clear hut nearest a column, to see whether light gets
     * through its walls. On a worker.
     *
     * @param terrain The world.
     * @param where {@code true} for the origin, or the column as {@code x,z}.
     */
    private void placeHutTorch(@NonNull SyntheticTerrain terrain, @NonNull String where) {
        int x = 0;
        int z = 0;
        if (!Boolean.parseBoolean(where)) {
            final String[] parts = where.split(",");
            if (parts.length != 2) {
                log.warn("The benchmark hut torch must be true or x,z, not {}", where);
                return;
            }
            x = Integer.parseInt(parts[0].trim());
            z = Integer.parseInt(parts[1].trim());
        }
        final int[] hut = terrain.hutNear(x, z, HUT_SEARCH_CELLS);
        if (hut == null) {
            log.warn("Gate benchmark found no hut for the hut torch");
            return;
        }
        final Vector3d at = new Vector3d(hut[0] + 0.5, hut[1] + TORCH_HEIGHT, hut[2] + 0.5);
        final LightHandle light = graphics.lights().point(at, TORCH_COLOR, TORCH_INTENSITY);
        log.info("Gate benchmark hut torch at ({}, {}, {})", at.x, at.y, at.z);
        keepLight(light);
    }

    /**
     * Scatter torches over the ground within the view distance, the same ones every run. On a
     * worker.
     *
     * @param terrain The world.
     */
    private void placeLights(@NonNull SyntheticTerrain terrain) {
        final String hutTorch = options.get("hutTorch");
        if (hutTorch != null && !"false".equalsIgnoreCase(hutTorch)) {
            placeHutTorch(terrain, hutTorch);
        }
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
            if (lightShadows) {
                graphics.lights().setCastsShadows(light, true);
            }
            if (i == 0) {
                log.info(
                        "Gate benchmark first torch at ({}, {}, {})",
                        x + 0.5,
                        y + 1 + TORCH_HEIGHT,
                        z + 0.5);
            }
            keepLight(light);
        }
    }

    /**
     * Advance one frame.
     *
     * @param scene The scene.
     */
    private void step(@NonNull Scene scene) {
        if (!released) {
            water.step();
            effects.step();
            stepStream(scene);
        }
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
                // Every section the run made has a bake drawn; the counts save asking each one
                final int made;
                sectionsLock.lock();
                try {
                    made = sections.size();
                } finally {
                    sectionsLock.unlock();
                }
                final boolean allResident = stats.resident() >= made;
                // With level of detail, also until it shows every section's bake with every cell
                // built and loaded
                final boolean lodCaughtUp = !lod || isLodSettled();
                if (!allResident || stats.baking() != 0 || !lodCaughtUp) {
                    readyFrames = 0;
                    readySince = 0;
                } else if (readyFrames++ == 0) {
                    readySince = System.nanoTime();
                }
                if (readyFrames >= READY_FRAMES) {
                    bakeMilliseconds = (readySince - composedAt) / 1_000_000;
                    load = load.withTriangles(stats.triangles());
                    log.info(
                            "Gate benchmark: {} triangles resident after {} ms",
                            stats.triangles(),
                            bakeMilliseconds);
                    final var cache =
                            lod
                                    ? renderer()
                                            .getState()
                                            .sections
                                            .lodCacheStats(graphics.getOwnerKey())
                                    : null;
                    cacheAtLoad = cache;
                    final var live = lodWorld == null ? null : graphics.lods().liveStats(lodWorld);
                    if (live != null) {
                        lodBytesAtLoad = new long[] {live.loadedBytes(), live.totalBytes()};
                    }
                    if (cache != null) {
                        log.info(
                                "Gate benchmark LOD cache: {} cells read, {} built, {} MiB read,"
                                        + " {} MiB written, {} in the folder",
                                cache.hits(),
                                cache.misses(),
                                String.format("%.1f", cache.bytesRead() / BYTES_PER_MIB),
                                String.format("%.1f", cache.bytesWritten() / BYTES_PER_MIB),
                                cache.folderBytes() < 0
                                        ? "nothing saved, so not counted"
                                        : String.format(
                                                "%.1f MiB", cache.folderBytes() / BYTES_PER_MIB));
                    }
                    phase = Phase.SETTLING;
                    startStream();
                }
            }
            case SETTLING -> {
                placeCamera(scene, 0);
                if (--settleLeft <= 0) {
                    phase = Phase.MEASURING;
                    measureStart = System.nanoTime();
                    shadowCacheAtStart = renderer().getState().sunShadows.stats();
                    renderer().getState().frameTimings.setListener(recorder::record);
                }
            }
            case MEASURING -> {
                final double seconds = (System.nanoTime() - measureStart) / 1e9;
                placeCamera(scene, seconds / options.measureSeconds(), seconds);
                logResidency(seconds);
                if (seconds >= options.measureSeconds()) {
                    renderer().getState().frameTimings.setListener(null);
                    if (gate && lodWorld != null && crackSpots == 0) {
                        startEdits(System.nanoTime());
                    } else if (gate && lodWorld != null) {
                        phase = Phase.CHECKING;
                        checkSince = System.nanoTime();
                    } else {
                        finish();
                    }
                }
            }
            case CHECKING -> {
                if (checkIndex < crackSpots) {
                    final Vector3d at = checkSpot(checkIndex);
                    checkPosition = at;
                    scene.getCamera().setPosition(at.x, at.y, at.z);
                    scene.getCamera().setRotation(PITCH, 0);
                }
                stepCheck();
            }
            case EDITING -> stepEdits();
            case DONE -> {
                views.placeOnce(scene, options.get("view"));
                updateDigs();
            }
                // A builder that failed stops the run itself, leaving what it made to release here;
                // once released, nothing more to do
            case STOPPED -> release();
            default -> {
                // Nothing to do while building
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
        scene.getDebugVisualizers().setWireframe(options.flag("wireframe"));
        GraphicsManager.swapPipeline(RenderConfig.WORLD);
        previousSun = scene.getSceneLights().getDirLight();
        previousAmbient = scene.getSceneLights().getAmbientLight();
        previousViewDistance = scene.getViewDistance();
        scene.setViewDistance((double) radius * SyntheticTerrain.SECTION);
        // At night only the torches light the world, to look at their shadows
        final boolean night = options.flag("night");
        scene.getSceneLights()
                .setDirLight(
                        new DirectionalLight(SUN_COLOR, sunDirection(), night ? 0 : SUN_INTENSITY));
        scene.getSceneLights()
                .setAmbientLight(
                        new AmbientLight(
                                new Vector3f(1, 1, 1),
                                night ? NIGHT_AMBIENT_INTENSITY : AMBIENT_INTENSITY));
        // The physical sky in place of the gray skybox, from the startup property
        previousPhysicalSky = scene.getPhysicalSky();
        if (options.flag(GateOptions.PHYSICAL_SKY)) {
            scene.setPhysicalSky(new PhysicalSky());
        }
        previousAutoExposure = scene.isAutoExposure();
        scene.setAutoExposure(options.flag("autoExposure"));
        previousFog = scene.getFog().isActive();
        effects.placeFog(scene.getFog());
        effects.placeTestMedia();
        effects.placeTestParticles();
        final SyntheticTerrain world = terrain;
        if (world != null) {
            water.place(world);
            effects.placeGlassTestSmoke(world);
            views.find(world, water.lakeSpot());
        }
        water.placeGrid();
    }

    /**
     * The way the sun's light travels: {@link #SUN_DIRECTION}, or from the same side at the height
     * the startup property gives, in degrees above the horizon, to look at the sky at dawn or dusk.
     *
     * @return The direction.
     */
    private Vector3f sunDirection() {
        final String up = options.get("sunUp");
        if (up == null) {
            return new Vector3f(SUN_DIRECTION);
        }
        final double elevation = Math.toRadians(Double.parseDouble(up));
        final Vector3f side = new Vector3f(SUN_DIRECTION.x, 0, SUN_DIRECTION.z).normalize();
        return side.mul((float) Math.cos(elevation)).add(0, (float) -Math.sin(elevation), 0);
    }

    /**
     * Put the camera where it turns, facing part of the way around.
     *
     * @param scene The scene.
     * @param turns How far around, from 0 to 1.
     */
    private void placeCamera(@NonNull Scene scene, double turns) {
        placeCamera(scene, turns, 0);
    }

    /**
     * Put the camera where it turns, walked along x or {@link #walkPath} for a while if it walks,
     * facing part of the way around.
     *
     * @param scene The scene.
     * @param turns How far around, from 0 to 1.
     * @param seconds How long it has walked.
     */
    private void placeCamera(@NonNull Scene scene, double turns, double seconds) {
        final Vector3d position = new Vector3d(cameraPosition);
        final SyntheticTerrain world = terrain;
        if (walkSpeed > 0 && world != null) {
            if (walkPath == null) {
                position.x += walkSpeed * seconds;
            } else {
                final double[] offset = walkOffset(walkPath, walkSpeed * seconds);
                position.x += offset[0];
                position.z += offset[1];
            }
            final int x = (int) Math.floor(position.x);
            final int z = (int) Math.floor(position.z);
            position.y = Math.max(position.y, world.top(x, z) + 1 + WALK_CLEARANCE);
        }
        scene.getCamera().setPosition(position.x, position.y, position.z);
        scene.getCamera().setRotation(PITCH, (float) (2 * Math.PI * Math.min(1, turns)));
    }

    /**
     * Read the {@code .walkPath} option: x,z waypoints separated by semicolons.
     *
     * @param value The option, or null.
     * @return The offsets, x and z for each waypoint, or null if not given or not valid.
     */
    static double[] parseWalkPath(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        final String[] points = value.split(";");
        final double[] path = new double[points.length * WALK_POINT_VALUES];
        for (int i = 0; i < points.length; ++i) {
            final String[] parts = points[i].split(",");
            try {
                if (parts.length != WALK_POINT_VALUES) {
                    throw new NumberFormatException(points[i]);
                }
                path[WALK_POINT_VALUES * i] = Double.parseDouble(parts[0].trim());
                path[WALK_POINT_VALUES * i + 1] = Double.parseDouble(parts[1].trim());
            } catch (NumberFormatException e) {
                log.warn("The benchmark's walkPath must be x,z;x,z;..., not {}", value);
                return null;
            }
        }
        return path;
    }

    /**
     * How far along a walk path the camera is, from the start through each waypoint in turn,
     * stopping at the last.
     *
     * @param path The waypoints' x and z offsets.
     * @param distance How far it has walked, in meters.
     * @return Its x and z offset from the start.
     */
    static double[] walkOffset(double @NonNull [] path, double distance) {
        double x = 0;
        double z = 0;
        double left = distance;
        for (int i = 0; i + 1 < path.length; i += WALK_POINT_VALUES) {
            final double dx = path[i] - x;
            final double dz = path[i + 1] - z;
            final double length = Math.sqrt(dx * dx + dz * dz);
            if (left < length) {
                return new double[] {x + dx * left / length, z + dz * left / length};
            }
            left -= length;
            x = path[i];
            z = path[i + 1];
        }
        return new double[] {x, z};
    }

    /** The last whole second of measuring whose residency was logged. */
    private int residencyLogged = -1;

    /** Bytes in a mebibyte. */
    private static final double BYTES_PER_MIB = 1024.0 * 1024.0;

    /** A share as a percentage. */
    private static final double PERCENT = 100;

    /**
     * Log how much level of detail is on the GPU, once a second while the camera turns.
     *
     * @param seconds How long measuring has gone on.
     */
    private void logResidency(double seconds) {
        if (lodWorld == null || (int) seconds == residencyLogged) {
            return;
        }
        residencyLogged = (int) seconds;
        final var stats = graphics.lods().liveStats(lodWorld);
        if (stats != null) {
            log.info(
                    "Gate benchmark LOD at {} s: {} of {} units shown, {} MiB on the GPU of {} MiB"
                            + " ({} MiB wanted), {} loads, {} evictions; the cut draws {}"
                            + " triangles{}",
                    residencyLogged,
                    stats.shownUnits(),
                    stats.units(),
                    String.format("%.1f", stats.loadedBytes() / BYTES_PER_MIB),
                    String.format("%.1f", stats.totalBytes() / BYTES_PER_MIB),
                    String.format("%.1f", stats.wantedBytes() / BYTES_PER_MIB),
                    stats.loads(),
                    stats.evictions(),
                    graphics.lods().stats().selectedTriangles(),
                    stats.budgetScale() > 1
                            ? String.format(
                                    ", at %.1f times the threshold for the budget",
                                    stats.budgetScale())
                            : "");
        }
    }

    /**
     * Whether the level of detail shows every section's bake, with nothing waiting to be built,
     * uploaded or loaded. Render thread.
     *
     * @return True if it has caught up.
     */
    private boolean isLodSettled() {
        final LodHandle world = lodWorld;
        if (world == null) {
            return false;
        }
        final var stats = graphics.lods().liveStats(world);
        return renderer().getState().sections.getLodBehind() == 0
                && stats != null
                && stats.pendingBatches() == 0
                && stats.staleCells() == 0
                && renderer().getState().lods.isSettled(world.id());
    }

    /**
     * Once the view has been held long enough and the level of detail has caught up, work out the
     * cut the GPU picks and check it for cracks on a worker; when that is done, move on. Render
     * thread.
     */
    private void stepCheck() {
        final LodHandle world = lodWorld;
        if (world == null) {
            finish();
            return;
        }
        final long now = System.nanoTime();
        final var lods = renderer().getState().lods;
        if (checking == null) {
            if (now - checkSince < CHECK_HOLD_NANOS || !isLodSettled()) {
                return;
            }
            final LodRenderer.Cut cut = lods.cut(world.id());
            if (cut == null) {
                finish();
                return;
            }
            checkCut = cut;
            final List<double[]> boxes = sectionBoxes(cut.hidden());
            checking =
                    CompletableFuture.supplyAsync(
                            () ->
                                    CrackCheck.checkTiled(
                                            cut.clusters(),
                                            cut.highest(),
                                            cut.drawn(),
                                            CHECK_COLUMN,
                                            boxes,
                                            cut.volumes(),
                                            CrackCheck.TOLERANCE));
            return;
        }
        if (!checking.isDone()) {
            return;
        }
        final Vector3d at = checkPosition;
        final String where =
                checkIndex < crackSpots && at != null
                        ? String.format("from (%.0f, %.0f, %.0f)", at.x, at.y, at.z)
                        : "over the edits";
        final LodRenderer.Cut cut = checkCut;
        try {
            final CrackCheck.Tiled cracks = checking.join();
            final GateReport.Check check =
                    new GateReport.Check(
                            where, cracks, cut.missing(), cut.triangles(), cut.hidden().size());
            checks.add(check);
            log.info("Gate benchmark crack check {}", GateReport.describe(check));
        } catch (CompletionException e) {
            log.error("Gate benchmark crack check {} failed", where, e);
        }
        checking = null;
        checkCut = null;
        checkIndex += 1;
        checkSince = now;
        if (checkIndex == crackSpots) {
            startEdits(now);
        } else if (checkIndex > crackSpots) {
            finish();
        }
    }

    /**
     * Start making the gate's edits.
     *
     * @param now The time, from {@link System#nanoTime()}.
     */
    private void startEdits(long now) {
        checkIndex = crackSpots;
        phase = Phase.EDITING;
        digsLeft = GateReport.GATE_DIGS;
        propertyDigRate = GateReport.GATE_DIG_RATE;
        editStart = now;
    }

    /**
     * Where the camera goes for a crack check, above the ground.
     *
     * @param index The check.
     * @return The position.
     */
    private Vector3d checkSpot(int index) {
        final double distance = CHECK_SPOTS[index][0] * radius * SyntheticTerrain.SECTION;
        final double angle = Math.toRadians(CHECK_SPOTS[index][1]);
        final int x = (int) Math.floor(distance * Math.cos(angle));
        final int z = (int) Math.floor(distance * Math.sin(angle));
        final SyntheticTerrain world = terrain;
        final double ground = world == null ? 0 : world.top(x, z) + 1;
        return new Vector3d(x + 0.5, ground + CHECK_HEIGHT, z + 0.5);
    }

    /**
     * The boxes of some sections.
     *
     * @param positions The sections.
     * @return Their boxes, min x, y, z then max x, y, z, from the world's origin.
     */
    private static List<double[]> sectionBoxes(@NonNull Set<LodStore.SectionPos> positions) {
        final int size = SyntheticTerrain.SECTION;
        final List<double[]> boxes = new ArrayList<>();
        for (LodStore.SectionPos position : positions) {
            final double x = (double) position.x() * size;
            final double y = (double) position.y() * size;
            final double z = (double) position.z() * size;
            boxes.add(new double[] {x, y, z, x + size, y + size, z + size});
        }
        return boxes;
    }

    /**
     * Make the gate's edits and wait for each to reach the level of detail, then check the cut over
     * them. Render thread.
     */
    private void stepEdits() {
        updateDigs();
        final long now = System.nanoTime();
        final boolean done = digsLeft == 0 && digs.isEmpty();
        if (!done && now - editStart < EDIT_TIMEOUT_NANOS) {
            return;
        }
        if (!done) {
            editsUnfinished = digs.size() + digsLeft;
            log.warn("Gate benchmark: {} edits didn't finish in time", editsUnfinished);
            digs.clear();
            digsLeft = 0;
        }
        if (!editCracks) {
            finish();
            return;
        }
        phase = Phase.CHECKING;
        checkSince = now;
    }

    /**
     * Gather what the gate found.
     *
     * @return The gate, or null if it wasn't checked.
     */
    @Nullable private GateReport.Gate gateResults() {
        if (!gate) {
            return null;
        }
        double worstDrawn = 0;
        final double[] lodMillis = new double[digLatencies.size()];
        for (int i = 0; i < lodMillis.length; ++i) {
            final double[] latency = digLatencies.get(i);
            worstDrawn = Math.max(worstDrawn, latency[0]);
            lodMillis[i] = latency[1];
            worstDrawnFrames = Math.max(worstDrawnFrames, (int) latency[LATENCY_FRAMES]);
        }
        Arrays.sort(lodMillis);
        final double lodP95 =
                lodMillis.length == 0
                        ? 0
                        : lodMillis[
                                Math.clamp(
                                        (int) Math.ceil(PERCENTILE * lodMillis.length) - 1,
                                        0,
                                        lodMillis.length - 1)];
        final LodCache.Stats cache = cacheAtLoad;
        return new GateReport.Gate(
                lodBytesAtLoad[0],
                lodBytesAtLoad[1],
                cache == null ? 0 : cache.hits(),
                cache == null ? 0 : cache.misses(),
                List.copyOf(checks),
                digLatencies.size() + editsUnfinished,
                editsUnfinished,
                worstDrawn,
                worstDrawnFrames,
                lodP95);
    }

    /**
     * How many torches the run placed.
     *
     * @return The count.
     */
    private int placedTorches() {
        sectionsLock.lock();
        try {
            return lights.size();
        } finally {
            sectionsLock.unlock();
        }
    }

    /** Log the level of detail cut's clusters and triangles by level, if there is a world. */
    private void logLevels() {
        if (lodWorld == null) {
            return;
        }
        final var stats = graphics.lods().stats();
        final StringBuilder table = new StringBuilder();
        table.append(
                String.format(
                        "%-6s %10s %12s %8s %12s%n",
                        "Level", "Clusters", "Triangles", "Share", "In view"));
        for (int level = 0; level < stats.levelTriangles().length; ++level) {
            table.append(
                    String.format(
                            "%-6d %,10d %,12d %7.1f%% %,12d%n",
                            level,
                            stats.levelClusters()[level],
                            stats.levelTriangles()[level],
                            PERCENT
                                    * stats.levelTriangles()[level]
                                    / Math.max(1, stats.selectedTriangles()),
                            stats.levelInView()[level]));
        }
        log.info("Gate benchmark cut by level:\n{}", table);
    }

    /** Log what the run holds in GPU and CPU memory, by owner. */
    private void logMemory() {
        final var state = renderer().getState();
        final var arena = state.bakedGeometry;
        final var live = lodWorld == null ? null : graphics.lods().liveStats(lodWorld);
        long vmaBlocks;
        long vmaAllocations;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            final VmaTotalStatistics totals = VmaTotalStatistics.calloc(stack);
            vmaCalculateStatistics(state.vmaAllocator, totals);
            vmaBlocks = totals.total().statistics().blockBytes();
            vmaAllocations = totals.total().statistics().allocationBytes();
            // Every allocation, as VMA's JSON, for finding where the memory goes
            final String dump = options.get("gpuMemoryDump");
            if (dump != null) {
                final PointerBuffer text = stack.mallocPointer(1);
                vmaBuildStatsString(state.vmaAllocator, text, true);
                try {
                    Files.writeString(Path.of(dump), MemoryUtil.memUTF8(text.get(0)));
                } catch (IOException e) {
                    log.warn("Couldn't write the GPU memory dump to {}", dump, e);
                }
                vmaFreeStatsString(state.vmaAllocator, MemoryUtil.memByteBufferNT1(text.get(0)));
            }
        }
        // What is left on the heap once everything unreachable is gone
        System.gc();
        final Runtime runtime = Runtime.getRuntime();
        final long heapUsed = runtime.totalMemory() - runtime.freeMemory();
        log.info(
                "Gate benchmark memory, GPU: {} MiB in {} MiB of device memory blocks; level of"
                        + " detail {} MiB, baked geometry {} MiB used of {} MiB, brick atlas {}"
                        + " MiB",
                mib(vmaAllocations),
                mib(vmaBlocks),
                live == null ? "0" : mib(live.loadedBytes()),
                mib(arena.usedBytes()),
                mib(arena.capacityBytes()),
                mib(renderer().getState().volumeBricks.atlasBytes()));
        log.info(
                "Gate benchmark memory, CPU: {} MiB of heap used of {} MiB (max {} MiB); section"
                        + " bake requests {} MiB, level of detail store {} MiB",
                mib(heapUsed),
                mib(runtime.totalMemory()),
                mib(runtime.maxMemory()),
                mib(state.sections.requestBytes()),
                mib(state.sections.lodHeldBytes()));
    }

    /**
     * Bytes as mebibytes, to one decimal place.
     *
     * @param bytes The bytes.
     * @return The text.
     */
    private static String mib(long bytes) {
        return String.format("%.1f", bytes / BYTES_PER_MIB);
    }

    /** Work out the results, report them, and release everything. */
    private void finish() {
        final var bricks = renderer().getState().volumeBricks;
        if (bricks != null) {
            log.info(
                    "Gate benchmark volumes at {} m: {} bricks ({} of sections, {} at level 1, {}"
                            + " at level 2; {} queued, {} without a slot), {} drawn, {} MiB atlas,"
                            + " {} section triangles placed",
                    GraphicsManager.getSettings().volumeDistance,
                    bricks.brickCount(),
                    bricks.brickCount(0),
                    bricks.brickCount(1),
                    bricks.brickCount(2),
                    bricks.queuedCount(),
                    bricks.withoutSlotCount(),
                    bricks.drawCount(renderer().getState().frameIndex),
                    bricks.atlasBytes() / BYTES_PER_MIB,
                    renderer().getState().sections.getPlacedTriangles());
        }
        final int[] checks = renderer().getState().sections.getCheckCounts();
        log.info("Gate benchmark section checks: {} last frame, {} queued", checks[0], checks[1]);
        log.info(
                "Gate benchmark placed {} water tiles ({} following the {} water body, {} lakes),"
                        + " {} media, {} emitters, weather {}, {} views, {} torches",
                water.tileCount(),
                water.followingCount(),
                water.getWaterBody(),
                water.lakeCount(),
                effects.mediaCount(),
                effects.emitterCount(),
                effects.getWeather(),
                views.size(),
                placedTorches());
        logLevels();
        logMemory();
        logStreamTotals();
        final var points = renderer().getState().pointShadows;
        if (points != null && points.getCastingLights() > 0) {
            log.info(
                    "Gate benchmark point shadows: {} of {} shadow-casting lights in {} maps, {}"
                            + " faces drawn",
                    points.getShadowedLights(),
                    points.getCastingLights(),
                    points.getMapCount(),
                    points.getFaceRedraws());
        }
        final SunShadowCache.Stats start = shadowCacheAtStart;
        if (start != null) {
            final SunShadowCache.Stats end = renderer().getState().sunShadows.stats();
            log.info(
                    "Gate benchmark sun shadow cache while measuring: {} whole cascades, {} parts"
                            + " and {} shifted along, {} M texels, over {} frames, walking {} m/s",
                    end.fullRedraws() - start.fullRedraws(),
                    end.partRedraws() - start.partRedraws(),
                    end.scrolls() - start.scrolls(),
                    String.format(
                            "%.1f",
                            (end.texelsRedrawn() - start.texelsRedrawn()) / TEXELS_PER_MEGA),
                    recorder.frames(),
                    walkSpeed);
        }
        final List<FrameTimings.Timing> timings = recorder.timings();
        final FrameTimings.Timing gpu = timings.isEmpty() ? null : timings.get(0);
        final boolean passed =
                gpu != null
                        && FrameTimings.GPU_TOTAL.equals(gpu.name())
                        && gpu.p95() <= GateReport.GPU_BUDGET_MS;
        results =
                new GateReport.Results(
                        recipe,
                        radius,
                        lightCount,
                        load,
                        buildMilliseconds,
                        bakeMilliseconds,
                        recorder.frames(),
                        timings,
                        passed,
                        GraphicsManager.getSceneWindow().getWidth(),
                        GraphicsManager.getSceneWindow().getHeight(),
                        gateResults());
        phase = Phase.DONE;
        final String report = GateReport.report(results);
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
     * Dig out the ground a few blocks in front of the camera and rebake the sections around it,
     * timing how long until the hole is drawn and until level of detail has it. Render thread only,
     * once the world is laid out.
     *
     * @param scene The scene.
     * @return False if there is no world to dig in.
     */
    public boolean dig(@NonNull Scene scene) {
        final SyntheticTerrain world = terrain;
        if (world == null || released) {
            return false;
        }
        final Vector3d at = scene.getCamera().getPosition();
        final Vector3fc forward =
                scene.getCamera().getInvViewMatrix().transformDirection(new Vector3f(0, 0, -1));
        final double length = Math.max(1e-6, Math.hypot(forward.x(), forward.z()));
        // Digs side by side, then row by row, so they make a pit
        final int across = digCount % PIT_WIDTH - PIT_WIDTH / 2;
        final int along = digCount / PIT_WIDTH % PIT_LENGTH;
        // Forward by the reach and the row, sideways by the place in the row
        final double ahead = DIG_REACH + along;
        final int x =
                (int) Math.floor(at.x + (forward.x() * ahead - forward.z() * across) / length);
        final int z =
                (int) Math.floor(at.z + (forward.z() * ahead + forward.x() * across) / length);
        return digAt(world, x, z);
    }

    /**
     * Dig the next cell of the pit, row by row.
     *
     * @return False if there is no world or pit to dig in.
     */
    private boolean digPit() {
        final SyntheticTerrain world = terrain;
        final int[] origin = pit;
        if (world == null || released || origin == null) {
            return false;
        }
        final int across = digCount % PIT_WIDTH;
        final int along = digCount / PIT_WIDTH % PIT_LENGTH;
        return digAt(world, origin[0] + across, origin[1] - along);
    }

    /**
     * Dig out a column's ground block, and any plant standing on it, and rebake the sections around
     * it, timing how long until the hole is drawn and until level of detail has it.
     *
     * @param world The world.
     * @param x The column's x.
     * @param z The column's z.
     * @return False if the column has no ground to dig.
     */
    private boolean digAt(@NonNull SyntheticTerrain world, int x, int z) {
        final int y = world.ground(x, z);
        if (y < world.minY()) {
            return false;
        }
        final byte dug = world.block(x, y, z);
        world.setBlock(x, y, z, SyntheticTerrain.AIR);
        if (world.block(x, y + 1, z) == SyntheticTerrain.PLANT) {
            // It would float
            world.setBlock(x, y + 1, z, SyntheticTerrain.AIR);
        }
        final int size = SyntheticTerrain.SECTION;
        final LodStore.SectionPos home =
                new LodStore.SectionPos(
                        Math.floorDiv(x, size), Math.floorDiv(y, size), Math.floorDiv(z, size));
        // Its section, and any neighbor whose face masks it changes
        final Set<LodStore.SectionPos> touched = new LinkedHashSet<>();
        for (int dx = -1; dx <= 1; ++dx) {
            for (int dy = -1; dy <= 1; ++dy) {
                for (int dz = -1; dz <= 1; ++dz) {
                    touched.add(
                            new LodStore.SectionPos(
                                    Math.floorDiv(x + dx, size),
                                    Math.floorDiv(y + dy, size),
                                    Math.floorDiv(z + dz, size)));
                }
            }
        }
        SectionHandle homeHandle = null;
        for (LodStore.SectionPos position : touched) {
            final SectionHandle handle = rebake(world, position);
            if (position.equals(home)) {
                homeHandle = handle;
            }
        }
        if (homeHandle != null) {
            digs.add(
                    new Dig(
                            homeHandle,
                            home,
                            renderer().getState().sections.requestedGeneration(homeHandle.id()),
                            new int[] {x, y, z, dug}));
        }
        lastDig = System.nanoTime();
        digCount += 1;
        return true;
    }

    /**
     * Compose a section for the first time, with any textured test meshes in it, unless it has
     * nothing at all.
     *
     * @param position The section.
     * @param placements The terrain's placements in it.
     */
    @Nullable private SectionHandle compose(
            LodStore.@NonNull SectionPos position,
            SyntheticTerrain.@NonNull Placements placements) {
        final Composed placed = withTestMeshes(position, placements);
        if (placed.count() == 0) {
            return null;
        }
        final SectionHandle handle = request(position, placed).submit();
        final boolean late;
        sectionsLock.lock();
        try {
            // Released meanwhile: the release took what was kept, so this one goes here
            late = released;
            if (!late) {
                sections.add(handle);
                sectionAt.put(position, handle);
            }
        } finally {
            sectionsLock.unlock();
        }
        if (late) {
            graphics.sections().remove(handle);
            return null;
        }
        return handle;
    }

    /**
     * Keep a torch to remove with the world, or remove it now if the world was released while it
     * was being placed. On a worker.
     *
     * @param light The torch.
     */
    private void keepLight(@NonNull LightHandle light) {
        final boolean late;
        sectionsLock.lock();
        try {
            late = released;
            if (!late) {
                lights.add(light);
            }
        } finally {
            sectionsLock.unlock();
        }
        if (late) {
            graphics.lights().remove(light);
        }
    }

    /**
     * Remove the section composed at a position, if there is one. On the stream's worker.
     *
     * @param position The section.
     * @return True if there was one.
     */
    private boolean removeSection(LodStore.@NonNull SectionPos position) {
        final SectionHandle handle;
        sectionsLock.lock();
        try {
            handle = sectionAt.remove(position);
            if (handle != null) {
                sections.remove(handle);
            }
        } finally {
            sectionsLock.unlock();
        }
        if (handle != null) {
            graphics.sections().remove(handle);
        }
        return handle != null;
    }

    /** What the stream changes in this run. */
    private final class StreamTarget implements WorldStream.Target {
        @Override
        public boolean isComposed(LodStore.@NonNull SectionPos position) {
            sectionsLock.lock();
            try {
                return sectionAt.containsKey(position);
            } finally {
                sectionsLock.unlock();
            }
        }

        @Override
        public SectionHandle compose(
                LodStore.@NonNull SectionPos position,
                SyntheticTerrain.@NonNull Placements placements) {
            return GateBenchmark.this.compose(position, placements);
        }

        @Override
        public boolean remove(LodStore.@NonNull SectionPos position) {
            return removeSection(position);
        }

        @Override
        public void addLakeTile(int startX, int startZ) {
            final SyntheticTerrain world = terrain;
            final WaterTile tile =
                    world == null ? null : GateWater.lakeWaterTile(world, startX, startZ);
            if (tile != null) {
                streamTasks.add(() -> water.placeLakeTile(tile));
            }
        }

        @Override
        public void removeLakeTile(int startX, int startZ) {
            streamTasks.add(() -> water.removeLakeTile(startX, startZ));
        }
    }

    /**
     * Start streaming the world around the camera, if the run's {@code .stream} option asks, once
     * the world first laid out is drawn.
     */
    private void startStream() {
        final SyntheticTerrain world = terrain;
        if (!options.flag("stream") || world == null || stream != null) {
            return;
        }
        final LodStore.SectionPos start =
                new LodStore.SectionPos(
                        0,
                        Math.floorDiv((int) Math.floor(world.cameraY()), SyntheticTerrain.SECTION),
                        0);
        stream =
                new WorldStream(
                        world,
                        new StreamTarget(),
                        radius,
                        start,
                        GateWater.OCEAN_CELLS,
                        water.lakeTileKeys());
        streamLoggedAt = System.nanoTime();
        log.info("Gate benchmark streaming the world around the camera at radius {}", radius);
    }

    /**
     * Keep the stream following the camera, apply its changes, and log how it keeps up. Render
     * thread, every frame.
     *
     * @param scene The scene.
     */
    private void stepStream(@NonNull Scene scene) {
        final WorldStream streaming = stream;
        if (streaming == null) {
            return;
        }
        Runnable task;
        while ((task = streamTasks.poll()) != null) {
            task.run();
        }
        final Vector3dc camera = scene.getCamera().getPosition();
        streaming.follow(camera.x(), camera.y(), camera.z());
        if (++streamPollFrames >= STREAM_POLL_FRAMES) {
            streamPollFrames = 0;
            streaming.poll(graphics.sections()::isResident, graphics.sections()::isValid);
        }
        final double every =
                phase == Phase.MEASURING ? STREAM_LOG_SECONDS : STREAM_LOG_KEPT_SECONDS;
        final long now = System.nanoTime();
        if ((now - streamLoggedAt) / 1e9 < every) {
            return;
        }
        streamLoggedAt = now;
        final WorldStream.Stats stats = streaming.takeWindow();
        streamMostBacklog = Math.max(streamMostBacklog, stats.backlog());
        final var live = lodWorld == null ? null : graphics.lods().liveStats(lodWorld);
        final Runtime runtime = Runtime.getRuntime();
        log.info(
                "Gate benchmark streaming at x {}: {} composed, {} empty, {} removed; backlog {}"
                        + " cells, {} sections waiting to be drawn; drawn after {} ms (median), {} (95th), {}"
                        + " (most) for {} sections; LOD {} stale cells, {} batches waiting, {}"
                        + " sections behind, {} taken out still shown; heap {} MiB",
                String.format("%.0f", camera.x()),
                stats.composed(),
                stats.empty(),
                stats.removed(),
                stats.backlog(),
                stats.pending(),
                String.format("%.0f", WorldStream.percentile(stats.latencies(), MEDIAN)),
                String.format("%.0f", WorldStream.percentile(stats.latencies(), PERCENTILE_95)),
                String.format("%.0f", WorldStream.percentile(stats.latencies(), 1)),
                stats.latencies().length,
                live == null ? 0 : live.staleCells(),
                live == null ? 0 : live.pendingBatches(),
                renderer().getState().sections.getLodBehind(),
                renderer().getState().sections.getLodRemovedWaiting(),
                (runtime.totalMemory() - runtime.freeMemory()) / (long) BYTES_PER_MIB);
    }

    /** Log what the stream did over the whole run. Render thread. */
    private void logStreamTotals() {
        final WorldStream streaming = stream;
        if (streaming == null) {
            return;
        }
        final WorldStream.Stats stats = streaming.totals();
        log.info(
                "Gate benchmark streaming totals: {} sections composed, {} empty, {} removed; most"
                        + " backlog {} cells; drawn after {} ms (median), {} (95th), {} (most) for {}"
                        + " sections",
                stats.composed(),
                stats.empty(),
                stats.removed(),
                streamMostBacklog,
                String.format("%.0f", WorldStream.percentile(stats.latencies(), MEDIAN)),
                String.format("%.0f", WorldStream.percentile(stats.latencies(), PERCENTILE_95)),
                String.format("%.0f", WorldStream.percentile(stats.latencies(), 1)),
                stats.latencies().length);
    }

    /** The median, as a fraction. */
    private static final double MEDIAN = 0.5;

    /** The 95th percentile, as a fraction. */
    private static final double PERCENTILE_95 = 0.95;

    /**
     * A section's request to compose or bake again.
     *
     * @param position The section.
     * @param placed Its placements.
     * @return The request.
     */
    private Sections.Request request(
            LodStore.@NonNull SectionPos position, @NonNull Composed placed) {
        final int size = SyntheticTerrain.SECTION;
        final int count = placed.count();
        return graphics.sections()
                .compose(
                        new Vector3d(position.x() * size, position.y() * size, position.z() * size))
                .placements(
                        placed.meshes(),
                        placed.packedPositions(),
                        new byte[count],
                        placed.faceMasks(),
                        new short[count]);
    }

    /**
     * A section's placements: the terrain's, then the textured test meshes standing in it.
     *
     * @param position The section.
     * @param placements The terrain's placements in it.
     * @return Everything placed in it.
     */
    private Composed withTestMeshes(
            LodStore.@NonNull SectionPos position,
            SyntheticTerrain.@NonNull Placements placements) {
        final IntArrayList extra = testCells.get(position);
        final int terrain = placements.count();
        final int count = terrain + (extra == null ? 0 : extra.size());
        final MeshHandle[] placed = new MeshHandle[count];
        final int[] packed = Arrays.copyOf(placements.packedPositions(), count);
        final byte[] faceMasks = Arrays.copyOf(placements.faceMasks(), count);
        for (int i = 0; i < terrain; ++i) {
            placed[i] = meshes[placements.blocks()[i]];
        }
        for (int i = terrain; i < count; ++i) {
            placed[i] = testMesh;
            packed[i] = extra.getInt(i - terrain);
        }
        return new Composed(placed, packed, faceMasks);
    }

    /**
     * Load the textured test mesh's texture and material, and register the mesh for baking.
     *
     * @param scene The scene, whose material cache takes the material.
     * @return The mesh.
     */
    private MeshHandle registerTestMesh(@NonNull Scene scene) {
        final byte[] pixels = TestMeshes.checkerPixels();
        final ByteBuffer buffer = MemoryUtil.memAlloc(pixels.length);
        final TextureHandle texture;
        try {
            buffer.put(pixels).flip();
            texture =
                    graphics.textures()
                            .load(
                                    buffer,
                                    Format.R8G8B8A8_SRGB,
                                    TestMeshes.TEXTURE_SIZE,
                                    TestMeshes.TEXTURE_SIZE);
        } finally {
            MemoryUtil.memFree(buffer);
        }
        final Material material = TestMeshes.material();
        material.setTexture(texture);
        scene.getMaterialCache().addMaterial(material);
        return graphics.meshes()
                .registerBakeable(
                        TestMeshes.torus(material), BlockMeshes.BOX_MIN, BlockMeshes.BOX_MAX);
    }

    /**
     * Where the textured test meshes stand: on top of whatever is highest in their columns, tree
     * tops included, so they show above the canopy, on a grid {@link #TEST_MESH_SPACING} blocks
     * apart, within the run's radius.
     *
     * @param terrain The terrain.
     * @return Each section's cells with a test mesh.
     */
    private Map<LodStore.SectionPos, IntArrayList> testCells(@NonNull SyntheticTerrain terrain) {
        final Map<LodStore.SectionPos, IntArrayList> cells = new HashMap<>();
        final int size = SyntheticTerrain.SECTION;
        final int reach = radius * size;
        int count = 0;
        for (int x = -reach; x <= reach; x += TEST_MESH_SPACING) {
            for (int z = -reach; z <= reach; z += TEST_MESH_SPACING) {
                final int top = terrain.top(x, z);
                if ((long) x * x + (long) z * z > (long) reach * reach || top < terrain.minY()) {
                    continue;
                }
                final int y = top + 1;
                final LodStore.SectionPos position =
                        new LodStore.SectionPos(
                                Math.floorDiv(x, size),
                                Math.floorDiv(y, size),
                                Math.floorDiv(z, size));
                cells.computeIfAbsent(position, key -> new IntArrayList())
                        .add(
                                SectionBaker.pack(
                                        Math.floorMod(x, size),
                                        Math.floorMod(y, size),
                                        Math.floorMod(z, size)));
                ++count;
            }
        }
        log.info("Gate benchmark placed {} textured test meshes", count);
        return cells;
    }

    /**
     * Bake a section again from the edited world, or compose it if it had nothing before.
     *
     * @param world The world.
     * @param position The section.
     * @return Its handle, or null if it has nothing and never had.
     */
    @Nullable private SectionHandle rebake(
            @NonNull SyntheticTerrain world, LodStore.@NonNull SectionPos position) {
        final Composed placed =
                withTestMeshes(
                        position, world.placements(position.x(), position.y(), position.z()));
        final int count = placed.count();
        final var request = request(position, placed);
        sectionsLock.lock();
        try {
            if (released) {
                // Nothing made now would be released
                return null;
            }
            final SectionHandle handle = sectionAt.get(position);
            if (handle != null) {
                graphics.sections().rebake(handle, request);
                return handle;
            }
            if (count == 0) {
                return null;
            }
            final SectionHandle added = request.submit();
            sections.add(added);
            sectionAt.put(position, added);
            return added;
        } finally {
            sectionsLock.unlock();
        }
    }

    /**
     * Dig on a timer, as a quick player would. Render thread only.
     *
     * @param perSecond Digs a second, 0 to stop.
     */
    public void setDigRate(double perSecond) {
        digRate = Math.max(0, perSecond);
    }

    /**
     * Every dig's latency so far.
     *
     * @return For each dig, milliseconds until its hole was drawn and until level of detail had it,
     *     then frames until it was drawn.
     */
    public List<double[]> getDigLatencies() {
        return List.copyOf(digLatencies);
    }

    /**
     * Find an open patch of ground for the pit, with a column to spare around it and open ground
     * under the camera, nearest in front of where the camera turns: every column's ground open to
     * the sky, so the camera above sees each dig. Searches outward in squares.
     *
     * @param world The world.
     * @return The pit's first cell, x and z; in front of the camera if nothing nearby is open.
     */
    private static int[] findPit(@NonNull SyntheticTerrain world) {
        final int startX = -PIT_WIDTH / 2;
        final int startZ = (int) -DIG_REACH;
        for (int ring = 0; ring <= PIT_SEARCH; ++ring) {
            for (int dz = -ring; dz <= ring; ++dz) {
                for (int dx = -ring; dx <= ring; ++dx) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                        continue;
                    }
                    if (isOpenPatch(world, startX + dx, startZ + dz)) {
                        return new int[] {startX + dx, startZ + dz};
                    }
                }
            }
        }
        return new int[] {startX, startZ};
    }

    /**
     * Whether the pit would be open with its first cell somewhere, a column around it and the
     * ground toward the camera included: grass open to the sky, nearly level.
     *
     * @param world The world.
     * @param x The first cell's x.
     * @param z The first cell's z.
     * @return True if every column is open.
     */
    private static boolean isOpenPatch(@NonNull SyntheticTerrain world, int x, int z) {
        final int level = world.ground(x, z);
        for (int column = x - 1; column <= x + PIT_WIDTH; ++column) {
            // Up to under the camera, so nothing between it and the pit hides the digs
            for (int row = z - PIT_LENGTH; row <= z + (int) DIG_REACH + 1; ++row) {
                if (!world.isOpen(column, row)) {
                    return false;
                }
                // Natural ground, nearly level: no huts, no cliffs
                final int ground = world.ground(column, row);
                final byte surface = world.block(column, ground, row);
                if ((surface != SyntheticTerrain.GRASS && surface != SyntheticTerrain.SLAB)
                        || Math.abs(ground - level) > PIT_GROUND_STEP) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Make digs that are due, and time the ones in flight. Render thread, once a frame. */
    private void updateDigs() {
        if (digsLeft > 0 && !pitView && terrain != null) {
            // Look down at an open patch of ground where the pit goes
            pitView = true;
            pit = findPit(terrain);
            final Scene scene = GraphicsManager.getScene();
            final int middle = pit[0] + PIT_WIDTH / 2;
            final int ground = terrain.ground(middle, pit[1]);
            // Back from the middle row by as far as the view reaches looking down, so it is
            // in the middle of the frame
            final double z =
                    pit[1] - PIT_LENGTH / 2 + 0.5 + PIT_VIEW_HEIGHT / Math.tan(PIT_VIEW_PITCH);
            scene.getCamera().setPosition(middle + 0.5, ground + PIT_VIEW_HEIGHT, z);
            scene.getCamera().setRotation(PIT_VIEW_PITCH, 0);
            log.info(
                    "Gate benchmark digging a pit at ({}, {}, {}), {} by {} blocks",
                    pit[0],
                    ground,
                    pit[1],
                    PIT_WIDTH,
                    PIT_LENGTH);
        }
        final double rate = digsLeft > 0 ? propertyDigRate : digRate;
        if (rate > 0 && (System.nanoTime() - lastDig) / NANOS_PER_SECOND >= 1 / rate) {
            final Scene scene = GraphicsManager.getScene();
            if (digsLeft > 0) {
                if (digPit()) {
                    --digsLeft;
                }
            } else if (scene != null) {
                dig(scene);
            }
        }
        final var manager = renderer().getState().sections;
        final Iterator<Dig> it = digs.iterator();
        while (it.hasNext()) {
            final Dig dig = it.next();
            final long now = System.nanoTime();
            if (dig.drawn == 0) {
                dig.frames += 1;
                if (manager.currentGeneration(dig.section.id()) >= dig.generation) {
                    dig.drawn = now;
                }
            }
            final boolean inLod =
                    lodWorld == null
                            || renderer()
                                            .getState()
                                            .lods
                                            .coveredGeneration(lodWorld.id(), dig.position)
                                    >= dig.generation;
            if (dig.drawn != 0 && inLod) {
                final double drawnMillis = (dig.drawn - dig.start) / NANOS_PER_MILLI;
                final double lodMillis = (now - dig.start) / NANOS_PER_MILLI;
                digLatencies.add(new double[] {drawnMillis, lodMillis, dig.frames});
                log.info(
                        "Dig of block {} at ({}, {}, {}): drawn after {} ms ({} frames), in level of detail"
                                + " after {} ms; {} sections drawing themselves; last bake on the"
                                + " render thread {} ms",
                        dig.block[DUG_BLOCK],
                        dig.block[0],
                        dig.block[1],
                        dig.block[2],
                        String.format("%.1f", drawnMillis),
                        dig.frames,
                        String.format("%.1f", lodMillis),
                        manager.getOverrideCount(),
                        String.format("%.2f", manager.getLastUrgentNanos() / NANOS_PER_MILLI));
                it.remove();
            }
        }
    }

    /** Release every section and mesh the run made, once, and put the scene's lights back. */
    private void release() {
        if (released) {
            return;
        }
        released = true;
        if (stream != null) {
            // Before the sections go, so the worker doesn't add more
            stream.stop();
            stream = null;
        }
        streamTasks.clear();
        final Scene scene = GraphicsManager.getScene();
        if (scene != null && previousSun != null) {
            scene.getSceneLights().setDirLight(previousSun);
            scene.getSceneLights().setAmbientLight(previousAmbient);
            scene.setPhysicalSky(previousPhysicalSky);
            scene.setAutoExposure(previousAutoExposure);
            scene.getFog().setActive(previousFog);
        }
        effects.release();
        water.release();
        if (scene != null && !Double.isNaN(previousViewDistance)) {
            scene.setViewDistance(previousViewDistance);
        }
        sectionsLock.lock();
        try {
            for (SectionHandle section : sections) {
                graphics.sections().remove(section);
            }
            sections.clear();
            sectionAt.clear();
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
        if (lodWorld != null) {
            graphics.lods().remove(lodWorld);
            lodWorld = null;
        }
        digs.clear();
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
