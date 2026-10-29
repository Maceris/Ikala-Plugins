package com.ikalagaming.graphics.benchmark;

import static org.lwjgl.util.vma.Vma.vmaBuildStatsString;
import static org.lwjgl.util.vma.Vma.vmaCalculateStatistics;
import static org.lwjgl.util.vma.Vma.vmaFreeStatsString;

import com.ikalagaming.graphics.EmitterHandle;
import com.ikalagaming.graphics.Format;
import com.ikalagaming.graphics.GraphicsContext;
import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.GraphicsPlugin;
import com.ikalagaming.graphics.LightHandle;
import com.ikalagaming.graphics.LodHandle;
import com.ikalagaming.graphics.MediumHandle;
import com.ikalagaming.graphics.MeshHandle;
import com.ikalagaming.graphics.RenderConfig;
import com.ikalagaming.graphics.SectionHandle;
import com.ikalagaming.graphics.Sections;
import com.ikalagaming.graphics.TextureHandle;
import com.ikalagaming.graphics.WaterHandle;
import com.ikalagaming.graphics.bake.BakeSource;
import com.ikalagaming.graphics.bake.SectionBaker;
import com.ikalagaming.graphics.graph.Material;
import com.ikalagaming.graphics.graph.MeshData;
import com.ikalagaming.graphics.graph.SunShadowCache;
import com.ikalagaming.graphics.graph.VolumeMath;
import com.ikalagaming.graphics.lod.CrackCheck;
import com.ikalagaming.graphics.lod.LodCache;
import com.ikalagaming.graphics.lod.LodStore;
import com.ikalagaming.graphics.scene.Fog;
import com.ikalagaming.graphics.scene.MediumVolume;
import com.ikalagaming.graphics.scene.ParticleEffect;
import com.ikalagaming.graphics.scene.ParticleSamples;
import com.ikalagaming.graphics.scene.PhysicalSky;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.graphics.scene.WaterBody;
import com.ikalagaming.graphics.scene.WaterTile;
import com.ikalagaming.graphics.scene.debug.DebugVisualizers;
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
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
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
     * @param gate What the level of detail gate found, or null if it wasn't checked.
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
            int height,
            @Nullable Gate gate) {}

    /**
     * One crack check of the level of detail's cut, as the GPU picks it.
     *
     * @param where Where the camera was.
     * @param cracks What the check found.
     * @param missing Clusters picked whose meshes weren't on the GPU: holes.
     * @param triangles The triangles the cut draws.
     * @param hidden Sections drawing themselves instead, whose edges are excused.
     */
    public record Check(
            @NonNull String where,
            CrackCheck.@NonNull Tiled cracks,
            int missing,
            long triangles,
            int hidden) {

        /**
         * Whether the cut had no cracks and no holes.
         *
         * @return True if it was clean.
         */
        public boolean isClean() {
            return cracks.isClean() && missing == 0;
        }
    }

    /**
     * What the level of detail gate found, besides the GPU time.
     *
     * @param lodBytes Level of detail mesh bytes on the GPU once loaded.
     * @param lodTotalBytes Level of detail mesh bytes kept on the CPU.
     * @param cacheRead Cells read from the disk cache while loading.
     * @param cacheBuilt Cells built while loading.
     * @param checks The crack checks.
     * @param edits Edits made.
     * @param editsUnfinished Edits that weren't drawn or didn't reach the level of detail in time.
     * @param worstDrawnMillis The longest an edit took to be drawn at full resolution.
     * @param worstDrawnFrames The most frames an edit took to be drawn.
     * @param lodMillisP95 How long edits took to reach the level of detail, 95th percentile.
     */
    public record Gate(
            long lodBytes,
            long lodTotalBytes,
            long cacheRead,
            long cacheBuilt,
            @NonNull List<Check> checks,
            int edits,
            int editsUnfinished,
            double worstDrawnMillis,
            int worstDrawnFrames,
            double lodMillisP95) {

        /**
         * Whether every check was clean.
         *
         * @return True if no cut had cracks or holes.
         */
        public boolean cracksPassed() {
            return !checks.isEmpty() && checks.stream().allMatch(Check::isClean);
        }

        /**
         * Whether every edit was drawn at full resolution in time.
         *
         * @return True if they were.
         */
        public boolean editsPassed() {
            return edits == GATE_DIGS && editsUnfinished == 0 && worstDrawnMillis <= EDIT_BUDGET_MS;
        }

        /**
         * Whether this was a reload from the cache: something was read.
         *
         * @return True if cells were read.
         */
        public boolean isReload() {
            return cacheRead > 0;
        }

        /**
         * Whether a reload built nothing.
         *
         * @return True if it read every cell.
         */
        public boolean reloadPassed() {
            return isReload() && cacheBuilt == 0;
        }
    }

    /** Edits the gate makes. */
    public static final int GATE_DIGS = 20;

    /** Edits a second the gate makes, as a quick player digs. */
    public static final double GATE_DIG_RATE = 20;

    /** The longest an edit may take to be drawn at full resolution, in milliseconds. */
    public static final double EDIT_BUDGET_MS = 250;

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
    private final List<Check> checks = new ArrayList<>();

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

    /** The physical sky before the run, or null for the skybox. */
    @Nullable private PhysicalSky previousPhysicalSky;

    /** Whether the exposure adapted before the run. */
    private boolean previousAutoExposure;

    /** Where the fog's anisotropy is in the {@code .fog} property, after three other values. */
    private static final int FOG_ANISOTROPY_PART = 3;

    /** Whether the fog was drawn before the run. */
    private boolean previousFog;

    /** The test medium volumes the run placed, removed when it ends. */
    private final List<MediumHandle> testMedia = new ArrayList<>();

    /** The test particle emitters the run placed, stopped when it ends. */
    private final List<EmitterHandle> testEmitters = new ArrayList<>();

    /** Places to look from, its preset's and those the run finds, see {@link #getViews}. */
    private final List<View> views = new ArrayList<>();

    /** The weather falling, see {@link #setWeather}: none, {@link #WEATHER_RAIN} or snow. */
    private String weather = WEATHER_NONE;

    /** The weather's emitter, or null for none. */
    private EmitterHandle weatherEmitter;

    /** The particle effects placed from the debug window, see {@link #setEffects}. */
    private final List<EmitterHandle> effectEmitters = new ArrayList<>();

    /** The lakes' water tiles, laid out with the world, placed with its water. */
    private final List<WaterTile> lakeTiles = new ArrayList<>();

    /**
     * The corners of the lake tiles looked at when the world was laid out, with lake or not, as
     * {@link WorldStream#tileKey} gives them.
     */
    private final Set<Long> lakeTileKeys = new HashSet<>();

    /** The placed lake tiles, by their corners' {@link WorldStream#tileKey}. Render thread. */
    private final Map<Long, PlacedWater> lakeWater = new HashMap<>();

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

    /** Where a lake is near the world's middle, x and z, or null for none. */
    private int[] lakeSpot;

    /** How far the wind blows over a lake, at most, in meters. */
    private static final float LAKE_FETCH = 100;

    /** The lakes' wind, its speed 10 m up and the way it blows, in degrees from +x toward +z. */
    private static final float LAKE_WIND = 3;

    private static final float LAKE_WIND_DEGREES = 30;

    /** How far a view stands back from what it looks at, and how high above it, in meters. */
    private static final double VIEW_BACK = 20;

    private static final double VIEW_HEIGHT = 10;

    /** How far above the ground under it a view stands at least, in meters. */
    private static final double VIEW_CLEARANCE = 4;

    /** How high above a lake its view is at least, how far back along z, and how far down. */
    private static final double LAKE_VIEW_HEIGHT = 37;

    private static final double LAKE_VIEW_BACK = 10;

    private static final float LAKE_VIEW_PITCH = 1.2f;

    /** How far from the middle a lake to look at may be, along x and z, in meters. */
    private static final int LAKE_VIEW_REACH = 96;

    /** How far above the measuring turn the overview looks from, and how far down, in radians. */
    private static final double OVERVIEW_HEIGHT = 40;

    private static final float OVERVIEW_PITCH = 0.6f;

    /** How far it looks down, in radians. */
    private static final float VIEW_PITCH = 0.35f;

    /** How many tree cells out the hut nearest the world's middle is looked for. */
    private static final int HUT_SEARCH = 4;

    /** How far in front of the camera effects are placed, in meters. */
    private static final double EFFECTS_AHEAD = 8;

    /** The moving test emitters from the {@code .fxTest} option, or null for none. */
    private FxTest fxTest;

    /** Where the test sparks are from the fountain, in meters. */
    private static final Vector3d SPARKS_OFFSET = new Vector3d(6, 0, 0);

    /** Where the test smoke puffs are from the fountain, in meters. */
    private static final Vector3d PUFFS_OFFSET = new Vector3d(-6, 0, 0);

    /** Where the test thick smoke, drawn in the fog volume, is from the fountain, in meters. */
    private static final Vector3d THICK_SMOKE_OFFSET = new Vector3d(0, 0, -8);

    /** Where the test fire is from the fountain, in meters. */
    private static final Vector3d FIRE_OFFSET = new Vector3d(0, 0, 6);

    /** Where the test weather's box is centered, from the camera, in meters: a little above it. */
    private static final Vector3d WEATHER_OFFSET = new Vector3d(0, 2, 0);

    /**
     * The test ripples from the {@code .ripples} option: x, z, radius, strength and how many
     * seconds apart; null until read, empty for none.
     */
    private double[] testRipples;

    /** When the test water was last disturbed, from {@link System#nanoTime()}. */
    private long lastRippleNanos;

    /** Values in the {@code .ripples} option. */
    private static final int RIPPLE_VALUES = 5;

    /** Values in the {@code .turnAt} option: x, y and z. */
    private static final int TURN_AT_VALUES = 3;

    /** The scene options a preset set, which come before the startup properties. */
    private final Map<String, String> options = new HashMap<>();

    /** The test water tiles the run placed, removed when it ends. */
    private final List<WaterHandle> testWater = new ArrayList<>();

    /**
     * A test water tile the island run placed, kept to change its wind and flow.
     *
     * @param still The tile without flow.
     * @param fetch The most fetch the wind has over it, in meters: a pool's width, or infinite for
     *     the open sea.
     * @param handle Its handle, set once placed.
     */
    private record PlacedWater(
            WaterTile still, float fetch, WaterHandle[] handle, boolean followsBody) {}

    /** The island's water tiles, see {@link #setWater}. */
    private final List<PlacedWater> placedWater = new ArrayList<>();

    /** The flowing path out at sea, from the {@code .loop} option, or null for none. */
    private WaterLoop waterLoop;

    /** What the island's water holds and its wind, null until placed. */
    private WaterBody waterBody;

    /** Its name, one of {@link #WATER_BODY_CHOICES}. */
    private String waterBodyName;

    /** The lakes' water body's name. */
    private static final String LAKE_BODY = "lake";

    /** How fast the sea flows, in meters a second, and which way, radians from +x toward +z. */
    private float currentSpeed;

    private float currentDirection;

    /** How fast the loop's channel flows in its middle, in meters a second. */
    private float loopSpeed;

    /**
     * How tall the waves painted along the loop are, as a share of the wind's waves in its own
     * direction, 0 for none.
     */
    private float loopWaves;

    /** How far around each spot painting waves along the loop reaches, in meters. */
    private static final float PAINT_RADIUS = 4;

    /** How fast the loop's channel flows unless {@code .loopSpeed} says, in meters a second. */
    public static final float DEFAULT_LOOP_SPEED = 1;

    /**
     * What the fountain's pools hold: garden pool water, its dissolved organic matter tinting it
     * like weak tea and fine sediment clouding it, so the water shows as a body; the run's wind
     * blows over it.
     */
    private static final WaterBody FOUNTAIN_WATER = new WaterBody(1, 0.4f, 2.5f);

    /** How deep the fountain's pools are, in meters: a full water block's 7/8. */
    private static final double FOUNTAIN_POOL_DEPTH = 0.875;

    /** How fast the fountain's water drifts outward, in meters a second. */
    private static final double FOUNTAIN_FLOW = 0.4;

    /** The test splash's radius, in meters, and its ripples' height, in meters. */
    private static final float SPLASH_RADIUS = 1;

    private static final float SPLASH_STRENGTH = 0.06f;

    /** Where on the sea the test splash drops without a loop, along z: off the island's shore. */
    private static final double SPLASH_SEA_Z = 44;

    /** How far apart the test water tiles are, in meters: the huts' grid in HUTS. */
    private static final double WATER_SPACING = 12;

    /** How many cells across each test water tile is, of a meter. */
    private static final int WATER_CELLS = 6;

    /** How deep the test water tiles' columns are, in meters above the ground. */
    private static final float[] WATER_DEPTHS = {0.5f, 2, 6};

    /** What the test water tiles' rows hold. */
    private static final WaterBody[] WATER_BODIES = {
        WaterBody.OCEAN, WaterBody.GREEN, WaterBody.MUDDY
    };

    /** The {@code .waterBody} property's values, by what the ocean holds. */
    private static final Map<String, WaterBody> WATER_BODY_NAMES =
            Map.of(
                    "ocean",
                    WaterBody.OCEAN,
                    "lake",
                    WaterBody.ofChlorophyll(1),
                    "green",
                    WaterBody.GREEN,
                    "muddy",
                    WaterBody.MUDDY);

    /** How many cells across the ocean's tiles are, of a meter. */
    private static final int OCEAN_CELLS = WaterTile.MAX_CELLS;

    /** The {@code .weather} property's value for rain. */
    public static final String WEATHER_RAIN = "rain";

    /** The {@code .weather} property's value for snow. */
    public static final String WEATHER_SNOW = "snow";

    /** The {@code .weather} property's value for none. */
    public static final String WEATHER_NONE = "none";

    /** The weathers the debug window offers, in order. */
    public static final List<String> WEATHERS = List.of(WEATHER_NONE, WEATHER_RAIN, WEATHER_SNOW);

    /** The water bodies the debug window offers, in order, see {@link #setWaterBody}. */
    public static final List<String> WATER_BODY_CHOICES =
            List.of("ocean", "lake", "green", "muddy");

    /**
     * A place to look from in a preset's world, which the debug window can jump to.
     *
     * @param name What menus show for it.
     * @param x Where the camera is, along x.
     * @param y Along y.
     * @param z Along z.
     * @param pitch How far it looks down, in radians.
     * @param yaw How far it is turned about y, in radians.
     */
    public record View(
            @NonNull String name, double x, double y, double z, float pitch, float yaw) {}

    /**
     * A ready-made run to pick from a menu: a world, its view distance, and the options its scene
     * takes, as the startup properties name them without the {@code ikala.benchmark.} prefix, with
     * places to look from. Weather, particle effects, fog, the water and the sky can all be changed
     * from the debug window while its world is kept, so one preset covers each kind of landscape.
     *
     * @param id What it is called in code and scripts.
     * @param name What menus show for it.
     * @param description What it shows, a line for menus.
     * @param recipe The world.
     * @param radius The view distance, in sections.
     * @param lod Whether to draw with level of detail.
     * @param options The scene's options, like {@code weather} or {@code fog}.
     * @param views Places to look from, the first taken when the world is ready; runs add more they
     *     find, like a lake on the hills.
     */
    public record Preset(
            @NonNull String id,
            @NonNull String name,
            @NonNull String description,
            SyntheticTerrain.@NonNull Recipe recipe,
            int radius,
            boolean lod,
            @NonNull Map<String, String> options,
            @NonNull List<View> views) {}

    /** The physical sky, with height fog and haze, as the landscapes start. */
    private static final Map<String, String> HAZY =
            Map.of("physicalSky", "true", "fog", "0.01,0.05,20", "haze", "true");

    /** {@link #HAZY}, with the world streamed around the camera. */
    private static final Map<String, String> HAZY_STREAMED =
            Map.of("physicalSky", "true", "fog", "0.01,0.05,20", "haze", "true", "stream", "true");

    /** The ready-made runs, in the order menus show them. */
    public static final List<Preset> PRESETS =
            List.of(
                    new Preset(
                            "island",
                            "Island",
                            "A hilly island in the sea: wind waves, a figure eight path offshore, a"
                                    + " stepped fountain, a glass wall and smoke, and a glass dome"
                                    + " on the sea floor",
                            SyntheticTerrain.Recipe.ISLAND,
                            8,
                            false,
                            Map.of(
                                    "fountain",
                                    "true",
                                    "glassTest",
                                    "true",
                                    "wind",
                                    "6,30",
                                    "loop",
                                    "infinity",
                                    "loopSpeed",
                                    "0"),
                            List.of(
                                    new View("Shore", 0, 6, 70, 0.15f, 0),
                                    new View("Fountain", 0, 16, 16, 0.45f, 0),
                                    new View("Glass and water", 0, 15, 20, 0.3f, 0),
                                    new View("Figure eight", 0, 55, 130, 1.0f, 0),
                                    new View("Under the sea", 0, -4, 72, -0.1f, 0))),
                    new Preset(
                            "hills",
                            "Hills",
                            "Rolling hills with trees, huts with glass, lakes and caves, under a"
                                    + " hazy physical sky",
                            SyntheticTerrain.Recipe.TERRAIN,
                            12,
                            false,
                            HAZY,
                            List.of()),
                    new Preset(
                            "hills-stream",
                            "Hills, streaming",
                            "The hills out to 512 m with level of detail, generated around the"
                                    + " camera as it moves: fly anywhere",
                            SyntheticTerrain.Recipe.TERRAIN,
                            32,
                            true,
                            HAZY_STREAMED,
                            List.of()),
                    new Preset(
                            "caves",
                            "Caves",
                            "Rock full of caves under a solid roof",
                            SyntheticTerrain.Recipe.CAVES,
                            8,
                            false,
                            Map.of(),
                            List.of()),
                    new Preset(
                            "glass",
                            "Glass",
                            "Walls of clear and tinted glass, many layers deep: the worst case for"
                                    + " transparency",
                            SyntheticTerrain.Recipe.GLASS,
                            8,
                            false,
                            Map.of(),
                            List.of()),
                    new Preset(
                            "jumble",
                            "Jumble",
                            "Hills of mixed block shapes, drawn with level of detail: the worst"
                                    + " case for culling and level of detail on real shapes",
                            SyntheticTerrain.Recipe.JUMBLE,
                            16,
                            true,
                            Map.of(),
                            List.of()));

    /** How tall the test smoke column is, half of it, in meters. */
    private static final float SMOKE_HALF_HEIGHT = 20;

    /** The test smoke column's radius, in meters. */
    private static final float SMOKE_RADIUS = 4;

    /** How dense the test smoke is, per meter. */
    private static final float SMOKE_DENSITY = 0.4f;

    /** How much of the light the test smoke stops it scatters: white smoke, like steam. */
    private static final Vector3fc SMOKE_ALBEDO = new Vector3f(0.9f);

    /** The test smoke's drift, up and along x, in meters a second. */
    private static final Vector3fc SMOKE_WIND = new Vector3f(1, 3, 0);

    /** Where the glass test's smoke stands along z: behind the fountain. */
    private static final double GLASS_TEST_SMOKE_Z = -10;

    /** Where the glowing sphere is from the smoke, and its radius, in meters. */
    private static final Vector3d GLOW_OFFSET = new Vector3d(-25, 5, 0);

    private static final float GLOW_RADIUS = 6;

    /** The glowing sphere's light per unit of density: an orange glow, as over a fire. */
    private static final Vector3fc GLOW_EMISSION = new Vector3f(4, 1.5f, 0.3f);

    /** How fast the glowing sphere's noise rises, in meters a second. */
    private static final Vector3fc GLOW_DRIFT = new Vector3f(0, 1, 0);

    /** Where the fog bank is from the smoke, and its half size, in meters. */
    private static final Vector3d BANK_OFFSET = new Vector3d(0, -12, 40);

    private static final Vector3fc BANK_HALF_SIZE = new Vector3f(40, 5, 15);

    /** How dense the fog bank is, per meter. */
    private static final float BANK_DENSITY = 0.08f;

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
    public static void start(@NonNull Preset preset) {
        stop();
        removeWorld();
        current = new GateBenchmark(preset.recipe(), preset.radius(), 0, true, preset.lod(), false);
        current.options.putAll(preset.options());
        current.views.addAll(preset.views());
        current.begin();
    }

    /**
     * How long the measuring turn takes, in seconds: {@link #TURN_SECONDS}, or the {@code .seconds}
     * option's, so a walk can go farther through the world while it is measured.
     *
     * @return The seconds.
     */
    private double measureSeconds() {
        final String value = option("seconds");
        return value == null ? TURN_SECONDS : Math.max(1, Double.parseDouble(value.trim()));
    }

    /**
     * One of the run's options: what its preset set, else the startup property of that name.
     *
     * @param key The option, the property's name after {@code ikala.benchmark.}.
     * @return Its value, or null if neither sets it.
     */
    private String option(@NonNull String key) {
        final String value = options.get(key);
        return value != null ? value : System.getProperty(PROPERTY + "." + key);
    }

    /**
     * One of the run's options, or a default.
     *
     * @param key The option.
     * @param fallback What it is if nothing sets it.
     * @return Its value.
     */
    private String option(@NonNull String key, @NonNull String fallback) {
        final String value = option(key);
        return value != null ? value : fallback;
    }

    /**
     * Where the camera turns while measuring: over the world's middle, or where the {@code
     * .turnAt=x,y,z} option says, as under the sea.
     *
     * @param world The world.
     * @return The place, in the world.
     */
    private Vector3d turnPosition(@NonNull SyntheticTerrain world) {
        final Vector3d middle = new Vector3d(0.5, world.cameraY(), 0.5);
        final String value = option("turnAt");
        if (value == null) {
            return middle;
        }
        final String[] parts = value.split(",");
        if (parts.length != TURN_AT_VALUES) {
            log.warn("The benchmark's turnAt must be x,y,z, not {}", value);
            return middle;
        }
        return new Vector3d(
                Double.parseDouble(parts[0].trim()),
                Double.parseDouble(parts[1].trim()),
                Double.parseDouble(parts[2].trim()));
    }

    /**
     * The flowing path out at sea, from the {@code .loop=circle|infinity} option, see {@link
     * WaterLoop}.
     *
     * @return The loop, or null for none.
     */
    private @Nullable WaterLoop loopOption() {
        final String value = option("loop");
        if (value == null || recipe != SyntheticTerrain.Recipe.ISLAND) {
            return null;
        }
        try {
            return new WaterLoop(WaterLoop.Shape.valueOf(value.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            log.warn("The benchmark's loop must be circle or infinity, not {}", value);
            return null;
        }
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
                                                    / measureSeconds()));
            case CHECKING ->
                    String.format(
                            "Checking for cracks: %d of %d",
                            Math.min(checkIndex, crackSpots) + 1,
                            crackSpots + (editCracks ? 1 : 0));
            case EDITING -> String.format("Editing: %d of %d edits made", digCount, GATE_DIGS);
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
                // Sections' cut out meshes cyan and volume bricks magenta
                if (Boolean.getBoolean(PROPERTY + ".volumeSources")) {
                    GraphicsManager.getScene().getDebugVisualizers().setVolumeSources(true);
                }
                // Another level of detail cache folder, such as an empty one to build every
                // cell; before starting, which opens the cache
                final String lodCache = System.getProperty(PROPERTY + ".lodCache");
                if (lodCache != null) {
                    GraphicsManager.getSettings().lodCacheDirectory = lodCache;
                }
                // A bigger level of detail cache for test runs, in MiB; players keep the default
                final String lodCacheMb = System.getProperty(PROPERTY + ".lodCacheMb");
                if (lodCacheMb != null) {
                    GraphicsManager.getSettings().lodCacheMaxBytes =
                            (long) (Long.parseLong(lodCacheMb.trim()) * BYTES_PER_MIB);
                }
                // Another GPU budget for level of detail, in MiB, to test cutting coarser for it
                final String lodBudgetMb = System.getProperty(PROPERTY + ".lodBudgetMb");
                if (lodBudgetMb != null) {
                    GraphicsManager.getSettings().lodResidentBytes =
                            (long) (Long.parseLong(lodBudgetMb.trim()) * BYTES_PER_MIB);
                }
                // Whether far level of detail blends its materials' looks, for comparisons
                final String average = System.getProperty(PROPERTY + ".average");
                if (average != null) {
                    GraphicsManager.getSettings().lodAverageMaterials =
                            Boolean.parseBoolean(average.trim());
                }
                // On, far parts finer than their group's resolution become coverage volumes; before
                // starting, which makes the live world
                final String lodVolumes = System.getProperty(PROPERTY + ".volumes");
                if (lodVolumes != null) {
                    GraphicsManager.getSettings().lodVolumes = Boolean.parseBoolean(lodVolumes);
                }
                start(
                        SyntheticTerrain.Recipe.valueOf(recipe.trim().toUpperCase()),
                        Integer.getInteger(PROPERTY + ".radius", 12),
                        Integer.getInteger(PROPERTY + ".lights", 0),
                        keep,
                        Boolean.getBoolean(PROPERTY + ".lod"),
                        Boolean.getBoolean(PROPERTY + ".gate"));
                current.digsLeft = Integer.getInteger(PROPERTY + ".digs", 0);
                current.walkSpeed = Double.parseDouble(System.getProperty(PROPERTY + ".walk", "0"));
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
                // Off, every cascade is drawn whole every frame, for comparing against the cache
                final String sunCache = System.getProperty(PROPERTY + ".sunCache");
                if (sunCache != null) {
                    GraphicsManager.getSettings().sunShadowCache = Boolean.parseBoolean(sunCache);
                }
                // Off, far leaves whose meshes are off the GPU cast no sun shadow
                final String coarseBricks = System.getProperty(PROPERTY + ".coarseBricks");
                if (coarseBricks != null) {
                    GraphicsManager.getSettings().coarseBricks = Boolean.parseBoolean(coarseBricks);
                }
                final String translucentShadows =
                        System.getProperty(PROPERTY + ".translucentShadows");
                if (translucentShadows != null) {
                    GraphicsManager.getSettings().translucentShadows =
                            Boolean.parseBoolean(translucentShadows);
                }
                final String volumeShadows = System.getProperty(PROPERTY + ".volumeShadows");
                if (volumeShadows != null) {
                    GraphicsManager.getSettings().volumeShadows =
                            Boolean.parseBoolean(volumeShadows);
                }
                // Off, ambient light isn't dimmed under cover; the view shows sky light in gray
                final String sky = System.getProperty(PROPERTY + ".sky");
                if (sky != null) {
                    GraphicsManager.getSettings().skyOcclusion = Boolean.parseBoolean(sky);
                }
                if (Boolean.getBoolean(PROPERTY + ".occupancyView")) {
                    scene.getDebugVisualizers().setOccupancyView(true);
                }
                // Off, the occupancy volume isn't kept, so nothing reads it
                final String occupancy = System.getProperty(PROPERTY + ".occupancy");
                if (occupancy != null) {
                    GraphicsManager.getSettings().occupancy = Boolean.parseBoolean(occupancy);
                }
                // Off, ambient light isn't dimmed by the occupancy volume; the view shows it in
                // gray
                final String environmentOcclusion =
                        System.getProperty(PROPERTY + ".environmentOcclusion");
                if (environmentOcclusion != null) {
                    GraphicsManager.getSettings().environmentOcclusion =
                            Boolean.parseBoolean(environmentOcclusion);
                }
                final String ao = System.getProperty(PROPERTY + ".ao");
                if (ao != null) {
                    GraphicsManager.getSettings().occupancyAo = Boolean.parseBoolean(ao);
                }
                if (Boolean.getBoolean(PROPERTY + ".aoView")) {
                    scene.getDebugVisualizers().setOccupancyAoView(true);
                }
                if (Boolean.getBoolean(PROPERTY + ".rayView")) {
                    scene.getDebugVisualizers().setOccupancyRayView(true);
                }
                // Off, no temporal anti-aliasing; the view shows each pixel's history
                final String taa = System.getProperty(PROPERTY + ".taa");
                if (taa != null) {
                    GraphicsManager.getSettings().taa = Boolean.parseBoolean(taa);
                }
                if (Boolean.getBoolean(PROPERTY + ".taaView")) {
                    scene.getDebugVisualizers().setTaaView(true);
                }
                // 0 draws every section's own cut out and translucent blocks, with no volumes
                final String volumeDistance = System.getProperty(PROPERTY + ".volumeDistance");
                if (volumeDistance != null) {
                    GraphicsManager.getSettings().volumeDistance =
                            Double.parseDouble(volumeDistance);
                }
                // Level of detail colored by CLUSTERS, GROUPS, LEVELS or CELLS
                final String lodColors = System.getProperty(PROPERTY + ".lodColors");
                if (lodColors != null) {
                    scene.getDebugVisualizers()
                            .setLodColors(
                                    DebugVisualizers.LodColors.valueOf(
                                            lodColors.trim().toUpperCase()));
                }
                // Off, lights without a shadow map shine through walls
                final String rays = System.getProperty(PROPERTY + ".lightRays");
                if (rays != null) {
                    GraphicsManager.getSettings().occupancyLightRays = Boolean.parseBoolean(rays);
                }
                GraphicsManager.getSettings().occupancyRaysPerPixel =
                        Integer.getInteger(
                                PROPERTY + ".raysPerPixel",
                                GraphicsManager.getSettings().occupancyRaysPerPixel);
                if (Boolean.getBoolean(PROPERTY + ".skyView")) {
                    scene.getDebugVisualizers().setSkyOcclusionView(true);
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
            final String pixels = option("lodPx");
            if (pixels != null) {
                graphics.lods().setThreshold(lodWorld, Double.parseDouble(pixels));
            }
        }

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
            terrain.setFountain(option("fountain") != null);
            terrain.setGlassTest(option("glassTest") != null);
            terrain.setWaterTiles(terrain.hasLakes());
            this.terrain = terrain;
            layOutLakes(terrain);
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
            cameraPosition = turnPosition(terrain);
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
        sectionsLock.lock();
        try {
            lights.add(light);
        } finally {
            sectionsLock.unlock();
        }
    }

    /**
     * Scatter torches over the ground within the view distance, the same ones every run. On a
     * worker.
     *
     * @param terrain The world.
     */
    private void placeLights(@NonNull SyntheticTerrain terrain) {
        final String hutTorch = option("hutTorch");
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
        rippleTestWater();
        paintTestWater();
        if (fxTest != null && !released && !graphics.isClosed()) {
            fxTest.step(graphics.particles());
        }
        if (!released) {
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
                    renderer().getState().frameTimings.setListener(this::record);
                }
            }
            case MEASURING -> {
                final double seconds = (System.nanoTime() - measureStart) / 1e9;
                placeCamera(scene, seconds / measureSeconds(), seconds);
                logResidency(seconds);
                if (seconds >= measureSeconds()) {
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
                placeView(scene);
                updateDigs();
            }
            default -> {
                // Nothing to do while building, or once stopped
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
        scene.getDebugVisualizers().setWireframe(Boolean.parseBoolean(option("wireframe")));
        GraphicsManager.swapPipeline(RenderConfig.WORLD);
        previousSun = scene.getSceneLights().getDirLight();
        previousAmbient = scene.getSceneLights().getAmbientLight();
        previousViewDistance = scene.getViewDistance();
        scene.setViewDistance((double) radius * SyntheticTerrain.SECTION);
        // At night only the torches light the world, to look at their shadows
        final boolean night = Boolean.parseBoolean(option("night"));
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
        if (Boolean.parseBoolean(option("physicalSky"))) {
            scene.setPhysicalSky(new PhysicalSky());
        }
        previousAutoExposure = scene.isAutoExposure();
        scene.setAutoExposure(Boolean.parseBoolean(option("autoExposure")));
        previousFog = scene.getFog().isActive();
        placeFog(scene.getFog());
        placeTestMedia();
        placeTestParticles();
        placeTestWater();
    }

    /**
     * In the island recipe, fill the sea with water tiles out to the view distance: every cell
     * whose ground is under {@link SyntheticTerrain#SEA_LEVEL}, the bottom at the ground's top. The
     * {@code .waterBody} property picks what it holds: ocean (the default), green or muddy; {@code
     * .wind=speed[,degrees[,fetch]]} sets its wind, blowing that many degrees from +x toward +z
     * over a fetch in meters, the open sea if none; {@code .current=speed[,degrees]} makes the sea
     * flow that many meters a second, that many degrees from +x toward +z, except on the {@code
     * .loop=circle|infinity} path out at sea, which flows along itself, {@code .loopSpeed=speed} in
     * its middle. All of these can be changed while the world is kept, see {@link #setWater}.
     */
    private void placeOcean() {
        final SyntheticTerrain world = terrain;
        if (world == null || world.getRecipe() != SyntheticTerrain.Recipe.ISLAND) {
            return;
        }
        final String name = option("waterBody", "ocean");
        WaterBody body = WATER_BODY_NAMES.get(name.trim().toLowerCase(Locale.ROOT));
        if (body == null) {
            log.warn(
                    "The benchmark's water body must be one of {}, not {}",
                    WATER_BODY_NAMES.keySet(),
                    name);
            body = WaterBody.OCEAN;
        }
        final String wind = option("wind");
        if (wind != null) {
            final String[] parts = wind.split(",");
            final float speed = Float.parseFloat(parts[0].trim());
            final float direction =
                    parts.length > 1
                            ? (float) Math.toRadians(Double.parseDouble(parts[1].trim()))
                            : 0;
            final float fetch =
                    parts.length > 2 ? Float.parseFloat(parts[2].trim()) : Float.POSITIVE_INFINITY;
            body = body.withWind(speed, direction, fetch);
        }
        waterBody = body;
        waterBodyName =
                WATER_BODY_NAMES.containsKey(name.trim().toLowerCase(Locale.ROOT))
                        ? name.trim().toLowerCase(Locale.ROOT)
                        : WATER_BODY_CHOICES.getFirst();
        waterLoop = loopOption();
        final String current = option("current");
        if (current != null) {
            final String[] parts = current.split(",");
            currentSpeed = Float.parseFloat(parts[0].trim());
            currentDirection =
                    parts.length > 1
                            ? (float) Math.toRadians(Double.parseDouble(parts[1].trim()))
                            : 0;
        }
        loopSpeed =
                Float.parseFloat(option("loopSpeed", Float.toString(DEFAULT_LOOP_SPEED)).trim());
        loopWaves = Float.parseFloat(option("loopWaves", "0").trim());
        final int reach = radius * SyntheticTerrain.SECTION;
        final int tiles = (2 * reach + OCEAN_CELLS - 1) / OCEAN_CELLS;
        final int corners = OCEAN_CELLS + 1;
        for (int tileZ = 0; tileZ < tiles; ++tileZ) {
            for (int tileX = 0; tileX < tiles; ++tileX) {
                final int startX = -reach + tileX * OCEAN_CELLS;
                final int startZ = -reach + tileZ * OCEAN_CELLS;
                final float[] heights = new float[corners * corners];
                final float[] bottoms = new float[corners * corners];
                final boolean[] mask = new boolean[OCEAN_CELLS * OCEAN_CELLS];
                boolean any = false;
                for (int z = 0; z < corners; ++z) {
                    for (int x = 0; x < corners; ++x) {
                        heights[z * corners + x] = (float) SyntheticTerrain.SEA_LEVEL;
                        bottoms[z * corners + x] =
                                (float) Math.floor(world.islandHeight(startX + x, startZ + z)) + 1;
                    }
                }
                for (int z = 0; z < OCEAN_CELLS; ++z) {
                    for (int x = 0; x < OCEAN_CELLS; ++x) {
                        final boolean water =
                                Math.floor(world.islandHeight(startX + x, startZ + z)) + 1
                                        < SyntheticTerrain.SEA_LEVEL;
                        mask[z * OCEAN_CELLS + x] = water;
                        any |= water;
                    }
                }
                if (any) {
                    placeWater(
                            new WaterTile(
                                    new Vector3d(startX, 0, startZ),
                                    1,
                                    OCEAN_CELLS,
                                    OCEAN_CELLS,
                                    heights,
                                    bottoms,
                                    mask,
                                    body));
                }
            }
        }
    }

    /**
     * In the island recipe, with the {@code .fountain} option, fill the pools on the island's
     * stepped stone fountain, see {@link SyntheticTerrain#fountainTier}, each drifting outward, so
     * water falls off every side of each past the stone onto the pool below, and off the outermost
     * onto the ground: the water's sides seen from all around, with stone behind them.
     */
    private void placeFountain() {
        final SyntheticTerrain world = terrain;
        if (world == null
                || world.getRecipe() != SyntheticTerrain.Recipe.ISLAND
                || !world.isFountain()
                || waterBody == null) {
            return;
        }
        final int[] sizes = SyntheticTerrain.FOUNTAIN_SIZES;
        for (int tier = 0; tier < sizes.length; ++tier) {
            final int size = sizes[tier];
            final int inner = tier + 1 < sizes.length ? sizes[tier + 1] : 0;
            // On the tier's top block, as deep as a full water block
            final double surface =
                    world.fountainBase()
                            + SyntheticTerrain.FOUNTAIN_STEP * tier
                            + 1
                            + FOUNTAIN_POOL_DEPTH;
            final int start = -size / 2;
            final int corners = size + 1;
            final float[] heights = new float[corners * corners];
            final float[] bottoms = new float[corners * corners];
            final float[] flows = new float[2 * corners * corners];
            final boolean[] mask = new boolean[size * size];
            Arrays.fill(heights, (float) surface);
            for (int z = 0; z < corners; ++z) {
                for (int x = 0; x < corners; ++x) {
                    final int at = z * corners + x;
                    bottoms[at] = (float) Math.floor(world.islandHeight(start + x, start + z)) + 1;
                    final double length = Math.hypot(start + x, start + z);
                    if (length > 0) {
                        flows[2 * at] = (float) (FOUNTAIN_FLOW * (start + x) / length);
                        flows[2 * at + 1] = (float) (FOUNTAIN_FLOW * (start + z) / length);
                    }
                }
            }
            for (int z = 0; z < size; ++z) {
                for (int x = 0; x < size; ++x) {
                    final int wx = start + x;
                    final int wz = start + z;
                    // Leave out where the next pool up stands
                    mask[z * size + x] =
                            inner == 0
                                    || Math.max(Math.abs(wx + 0.5), Math.abs(wz + 0.5))
                                            > inner / 2.0;
                }
            }
            placeWater(
                    new WaterTile(
                                    new Vector3d(start, 0, start),
                                    1,
                                    size,
                                    size,
                                    heights,
                                    bottoms,
                                    mask,
                                    FOUNTAIN_WATER)
                            .withFlows(flows),
                    size);
        }
    }

    /**
     * Place a test water tile with the run's wind and flow, remembering it so they can change.
     *
     * @param still The tile, without flow.
     */
    private void placeWater(@NonNull WaterTile still) {
        placeWater(still, Float.POSITIVE_INFINITY);
    }

    /**
     * Place a test water tile with the run's wind and flow, remembering it so they can change.
     *
     * @param still The tile, without flow.
     * @param fetch The most fetch the wind has over it, in meters: a pool's width, which keeps its
     *     waves to ripples, or infinite for the open sea.
     */
    private void placeWater(@NonNull WaterTile still, float fetch) {
        // The sea and lakes take the run's water body; water with its own flow keeps its own
        final PlacedWater placed =
                new PlacedWater(still, fetch, new WaterHandle[1], still.flows() == null);
        placed.handle()[0] = graphics.water().add(withWaterSettings(placed));
        if (placed.handle()[0] != null) {
            testWater.add(placed.handle()[0]);
            placedWater.add(placed);
        }
    }

    /**
     * A placed tile as the run's wind and flow have it now.
     *
     * @param placed The tile.
     * @return It with the run's body and wind, and its flow.
     */
    private WaterTile withWaterSettings(@NonNull PlacedWater placed) {
        final WaterTile still = placed.still();
        if (still.flows() != null) {
            // Water with its own flow, like the fountain's, keeps it
            return still.withBody(withRunWind(still.body(), placed.fetch()));
        }
        final int cornersX = still.cellsX() + 1;
        final int corners = cornersX * (still.cellsZ() + 1);
        final WaterLoop path = waterLoop;
        if (currentSpeed == 0 && (path == null || loopSpeed == 0)) {
            return still.withBody(withRunWind(still.body(), placed.fetch()));
        }
        final float[] flows = new float[2 * corners];
        final int originX = (int) Math.floor(still.origin().x());
        final int originZ = (int) Math.floor(still.origin().z());
        final float seaX = (float) (currentSpeed * Math.cos(currentDirection));
        final float seaZ = (float) (currentSpeed * Math.sin(currentDirection));
        for (int i = 0; i < corners; ++i) {
            final int x = originX + i % cornersX;
            final int z = originZ + i / cornersX;
            final boolean onPath = path != null && path.coversCorner(x, z);
            flows[2 * i] = onPath ? path.flowAt(x, z, loopSpeed, false) : seaX;
            flows[2 * i + 1] = onPath ? path.flowAt(x, z, loopSpeed, true) : seaZ;
        }
        return still.withBody(withRunWind(still.body(), placed.fetch())).withFlows(flows);
    }

    /**
     * What a placed tile's water holds, under the run's wind.
     *
     * @param own What the tile was placed holding.
     * @param fetch The most fetch the wind has over the tile, in meters.
     * @return It, with the run's wind, over no more than that fetch.
     */
    private WaterBody withRunWind(@NonNull WaterBody own, float fetch) {
        return own.withWind(
                waterBody.windSpeed(),
                waterBody.windDirection(),
                Math.min(waterBody.fetch(), fetch));
    }

    /**
     * Change the test water's wind and flow while its world is kept, from the debug window. Render
     * thread only.
     *
     * @param windSpeed The wind's speed 10 m up, in meters a second.
     * @param windDegrees The way it blows, in degrees from +x toward +z.
     * @param seaCurrent How fast the sea flows, in meters a second.
     * @param currentDegrees The way it flows, in degrees from +x toward +z.
     * @param channelSpeed How fast the loop's path flows in its middle, in meters a second;
     *     negative runs it the other way.
     * @param pathWaves How tall the waves painted along the loop are, as a share of the wind's
     *     waves in its own direction, 0 for none.
     */
    public void setWater(
            float windSpeed,
            float windDegrees,
            float seaCurrent,
            float currentDegrees,
            float channelSpeed,
            float pathWaves) {
        if (waterBody == null) {
            return;
        }
        loopWaves = Math.max(0, pathWaves);
        final WaterBody body =
                waterBody.withWind(
                        Math.max(0, windSpeed),
                        (float) Math.toRadians(windDegrees),
                        waterBody.fetch());
        final float direction = (float) Math.toRadians(currentDegrees);
        if (body.equals(waterBody)
                && seaCurrent == currentSpeed
                && direction == currentDirection
                && channelSpeed == loopSpeed) {
            return;
        }
        waterBody = body;
        currentSpeed = seaCurrent;
        currentDirection = direction;
        loopSpeed = channelSpeed;
        for (PlacedWater placed : placedWater) {
            graphics.water().update(placed.handle()[0], withWaterSettings(placed));
        }
    }

    /**
     * Lay out the lakes' water tiles, for a world whose lakes are left to them, see {@link
     * SyntheticTerrain#lakeBottom}: tiles of the ocean's size over the view distance, each lake
     * column's water from its bottom up to {@link SyntheticTerrain#LAKE_SURFACE}, and a lake near
     * the middle to look at. On the worker that lays out the world.
     *
     * @param world The world.
     */
    private void layOutLakes(@NonNull SyntheticTerrain world) {
        lakeTiles.clear();
        lakeTileKeys.clear();
        lakeSpot = null;
        if (!world.isWaterTiles()) {
            return;
        }
        final int reach = radius * SyntheticTerrain.SECTION;
        // On the grid of tile corners a streamed world adds to, see WorldStream
        final int first = Math.floorDiv(-reach, OCEAN_CELLS);
        final int last = Math.floorDiv(reach - 1, OCEAN_CELLS);
        // The tile near the middle with the most lake, to look at
        int most = 0;
        long lakeCells = 0;
        for (int tileZ = first; tileZ <= last; ++tileZ) {
            for (int tileX = first; tileX <= last; ++tileX) {
                final int startX = tileX * OCEAN_CELLS;
                final int startZ = tileZ * OCEAN_CELLS;
                lakeTileKeys.add(WorldStream.tileKey(startX, startZ));
                final LakeTile lake = lakeTile(world, startX, startZ);
                if (lake == null) {
                    continue;
                }
                final boolean near =
                        Math.abs(startX + OCEAN_CELLS / 2) <= LAKE_VIEW_REACH
                                && Math.abs(startZ + OCEAN_CELLS / 2) <= LAKE_VIEW_REACH;
                if (near && lake.count() > most) {
                    most = lake.count();
                    lakeSpot =
                            nearestLakeCell(
                                    lake.mask(),
                                    startX,
                                    startZ,
                                    lake.sumX() / lake.count(),
                                    lake.sumZ() / lake.count());
                }
                lakeCells += lake.count();
                lakeTiles.add(lake.tile());
            }
        }
        log.info(
                "Gate benchmark lakes: {} tiles, {} cells; spot {} holds water: {}",
                lakeTiles.size(),
                lakeCells,
                lakeSpot == null ? "none" : lakeSpot[0] + "," + lakeSpot[1],
                lakeSpot != null
                        && world.lakeBottom(lakeSpot[0], lakeSpot[1]) != SyntheticTerrain.NO_LAKE);
    }

    /**
     * A lake water tile and what it covers.
     *
     * @param tile The tile.
     * @param mask Which of its cells hold lake.
     * @param count How many do.
     * @param sumX Their x added up, for their middle.
     * @param sumZ Their z added up.
     */
    private record LakeTile(
            @NonNull WaterTile tile, boolean @NonNull [] mask, int count, long sumX, long sumZ) {}

    /**
     * The lake water tile with its corner at a place: each lake column's water from its bottom up
     * to {@link SyntheticTerrain#LAKE_SURFACE}. On a worker.
     *
     * @param world The world.
     * @param startX The tile's corner along x, in blocks.
     * @param startZ Along z.
     * @return The tile, or null if no lake is in it.
     */
    @Nullable private static LakeTile lakeTile(@NonNull SyntheticTerrain world, int startX, int startZ) {
        final float surface = (float) SyntheticTerrain.LAKE_SURFACE;
        final int corners = OCEAN_CELLS + 1;
        final int[] cellBottoms = new int[OCEAN_CELLS * OCEAN_CELLS];
        final boolean[] mask = new boolean[OCEAN_CELLS * OCEAN_CELLS];
        int count = 0;
        long sumX = 0;
        long sumZ = 0;
        for (int z = 0; z < OCEAN_CELLS; ++z) {
            for (int x = 0; x < OCEAN_CELLS; ++x) {
                final int bottom = world.lakeBottom(startX + x, startZ + z);
                cellBottoms[z * OCEAN_CELLS + x] = bottom;
                if (bottom == SyntheticTerrain.NO_LAKE) {
                    continue;
                }
                mask[z * OCEAN_CELLS + x] = true;
                ++count;
                sumX += startX + x;
                sumZ += startZ + z;
            }
        }
        if (count == 0) {
            return null;
        }
        final float[] heights = new float[corners * corners];
        final float[] bottoms = new float[corners * corners];
        Arrays.fill(heights, surface);
        for (int z = 0; z < corners; ++z) {
            for (int x = 0; x < corners; ++x) {
                // The lowest of the lake cells touching the corner
                float bottom = surface;
                for (int cz = z - 1; cz <= z; ++cz) {
                    for (int cx = x - 1; cx <= x; ++cx) {
                        if (cx < 0 || cz < 0 || cx >= OCEAN_CELLS || cz >= OCEAN_CELLS) {
                            continue;
                        }
                        final int cell = cellBottoms[cz * OCEAN_CELLS + cx];
                        if (cell != SyntheticTerrain.NO_LAKE) {
                            bottom = Math.min(bottom, cell);
                        }
                    }
                }
                bottoms[z * corners + x] = bottom;
            }
        }
        final WaterTile tile =
                new WaterTile(
                        new Vector3d(startX, 0, startZ),
                        1,
                        OCEAN_CELLS,
                        OCEAN_CELLS,
                        heights,
                        bottoms,
                        mask,
                        WATER_BODY_NAMES.get(LAKE_BODY));
        return new LakeTile(tile, mask, count, sumX, sumZ);
    }

    /**
     * The lake cell of a tile nearest a point, for a view to look down at water.
     *
     * @param mask Which of the tile's cells hold lake.
     * @param startX The tile's corner along x.
     * @param startZ Along z.
     * @param x The point's x.
     * @param z Its z.
     * @return The cell's x and z.
     */
    private static int[] nearestLakeCell(
            boolean @NonNull [] mask, int startX, int startZ, long x, long z) {
        int[] best = null;
        long bestDistance = Long.MAX_VALUE;
        for (int cz = 0; cz < OCEAN_CELLS; ++cz) {
            for (int cx = 0; cx < OCEAN_CELLS; ++cx) {
                if (!mask[cz * OCEAN_CELLS + cx]) {
                    continue;
                }
                final long dx = startX + cx - x;
                final long dz = startZ + cz - z;
                if (dx * dx + dz * dz < bestDistance) {
                    bestDistance = dx * dx + dz * dz;
                    best = new int[] {startX + cx, startZ + cz};
                }
            }
        }
        return best;
    }

    /** Place the lakes the world was laid out with, under a light wind. */
    private void placeLakes() {
        for (WaterTile tile : lakeTiles) {
            placeLakeTile(tile);
        }
    }

    /**
     * Place a lake water tile under the lakes' light wind, unless the run has a water body already.
     *
     * @param tile The tile.
     */
    private void placeLakeTile(@NonNull WaterTile tile) {
        if (waterBody == null) {
            waterBody =
                    WATER_BODY_NAMES
                            .get(LAKE_BODY)
                            .withWind(
                                    LAKE_WIND,
                                    (float) Math.toRadians(LAKE_WIND_DEGREES),
                                    Float.POSITIVE_INFINITY);
            waterBodyName = LAKE_BODY;
        }
        final int count = placedWater.size();
        placeWater(tile, LAKE_FETCH);
        if (placedWater.size() > count) {
            lakeWater.put(lakeKey(tile), placedWater.getLast());
        }
    }

    /**
     * Take away a placed lake water tile, if there is one at a corner.
     *
     * @param key The corner's {@link WorldStream#tileKey}.
     */
    private void removeLakeTile(long key) {
        final PlacedWater placed = lakeWater.remove(key);
        if (placed == null) {
            return;
        }
        placedWater.remove(placed);
        final WaterHandle handle = placed.handle()[0];
        if (handle != null) {
            testWater.remove(handle);
            graphics.water().remove(handle);
        }
    }

    /**
     * A lake tile's key, from its corner.
     *
     * @param tile The tile.
     * @return The key.
     */
    private static long lakeKey(@NonNull WaterTile tile) {
        return WorldStream.tileKey(
                (int) Math.floor(tile.origin().x()), (int) Math.floor(tile.origin().z()));
    }

    /**
     * Add the places to look from the run finds: the world's middle, where the measuring turn is,
     * if the preset gave none; a lake and the huts near the middle on the hills.
     */
    private void findViews() {
        final SyntheticTerrain world = terrain;
        if (world == null) {
            return;
        }
        if (views.isEmpty()) {
            views.add(
                    new View(
                            "Overview",
                            0.5,
                            world.cameraY() + OVERVIEW_HEIGHT,
                            0.5 + OVERVIEW_HEIGHT,
                            OVERVIEW_PITCH,
                            0));
            views.add(new View("Middle", 0.5, world.cameraY(), 0.5, PITCH, 0));
        }
        final int[] lake = lakeSpot;
        if (lake != null) {
            // From above, looking steeply down: the lakes lie in valleys under the trees
            views.add(
                    new View(
                            "Over a lake",
                            lake[0] + 0.5,
                            Math.max(
                                    SyntheticTerrain.LAKE_SURFACE + LAKE_VIEW_HEIGHT,
                                    world.top(lake[0], lake[1]) + VIEW_HEIGHT),
                            lake[1] + LAKE_VIEW_BACK,
                            LAKE_VIEW_PITCH,
                            0));
        }
        final int[] hut = world.hutNear(0, 0, HUT_SEARCH);
        if (hut != null) {
            views.add(lookingAt(world, "By the huts", hut[0], hut[1], hut[2]));
        }
        for (View view : views) {
            log.info(
                    "Gate benchmark view {}: {},{},{},{},{}",
                    view.name(),
                    view.x(),
                    view.y(),
                    view.z(),
                    view.pitch(),
                    view.yaw());
        }
    }

    /**
     * A view looking down at a point from back along +z and above it, clear of the ground there.
     *
     * @param world The world.
     * @param name The view's name.
     * @param x The point's x.
     * @param y Its y.
     * @param z Its z.
     * @return The view.
     */
    private static View lookingAt(
            @NonNull SyntheticTerrain world, @NonNull String name, int x, double y, int z) {
        final int backZ = (int) (z + VIEW_BACK);
        final double height = Math.max(y + VIEW_HEIGHT, world.top(x, backZ) + 1 + VIEW_CLEARANCE);
        return new View(name, x + 0.5, height, backZ + 0.5, VIEW_PITCH, 0);
    }

    /**
     * The places to look from: the preset's, then those the run found.
     *
     * @return The views.
     */
    public List<View> getViews() {
        return Collections.unmodifiableList(views);
    }

    /**
     * Put the camera at a place to look from. Render thread only.
     *
     * @param view Where.
     */
    public void goTo(@NonNull View view) {
        final Scene scene = GraphicsManager.getScene();
        if (scene == null) {
            return;
        }
        scene.getCamera().setPosition(view.x(), view.y(), view.z());
        scene.getCamera().setRotation(view.pitch(), view.yaw());
    }

    /**
     * What falls around the camera, see {@link #WEATHERS}.
     *
     * @return The weather.
     */
    public String getWeather() {
        return weather;
    }

    /**
     * Make it rain or snow around the camera, or stop. Render thread only.
     *
     * @param kind {@link #WEATHER_NONE}, {@link #WEATHER_RAIN} or {@link #WEATHER_SNOW}.
     */
    public void setWeather(@NonNull String kind) {
        final String wanted = kind.trim().toLowerCase(Locale.ROOT);
        final ParticleEffect effect =
                switch (wanted) {
                    case WEATHER_RAIN -> ParticleSamples.RAIN;
                    case WEATHER_SNOW -> ParticleSamples.SNOW;
                    case WEATHER_NONE -> null;
                    default -> {
                        log.warn(
                                "The benchmark's weather must be one of {}, not {}",
                                WEATHERS,
                                kind);
                        yield null;
                    }
                };
        if (wanted.equals(weather) || graphics.isClosed()) {
            return;
        }
        if (weatherEmitter != null) {
            graphics.particles().stop(weatherEmitter);
            weatherEmitter = null;
        }
        weather = effect == null ? WEATHER_NONE : wanted;
        if (effect != null) {
            weatherEmitter = graphics.particles().spawn(effect, WEATHER_OFFSET);
        }
    }

    /**
     * Whether the particle effects are placed, see {@link #setEffects}.
     *
     * @return True if they are.
     */
    public boolean hasEffects() {
        return !effectEmitters.isEmpty();
    }

    /**
     * Place the test particle effects on the ground in front of the camera, as {@code .particles}
     * does, or take them away. Render thread only.
     *
     * @param on Whether to place them.
     */
    public void setEffects(boolean on) {
        effectEmitters.forEach(graphics.particles()::stop);
        effectEmitters.clear();
        final SyntheticTerrain world = terrain;
        final Scene scene = GraphicsManager.getScene();
        if (!on || world == null || scene == null || graphics.isClosed()) {
            return;
        }
        final double yaw = scene.getCamera().getRotation().y;
        final Vector3dc camera = scene.getCamera().getPosition();
        final int x = (int) Math.floor(camera.x() + Math.sin(yaw) * EFFECTS_AHEAD);
        final int z = (int) Math.floor(camera.z() - Math.cos(yaw) * EFFECTS_AHEAD);
        final Vector3d at = new Vector3d(x + 0.5, world.top(x, z) + 1, z + 0.5);
        for (EmitterHandle handle : placeEffectSet(at)) {
            if (handle != null) {
                effectEmitters.add(handle);
            }
        }
    }

    /**
     * What the run's water holds, see {@link #WATER_BODY_CHOICES}.
     *
     * @return Its name, or null if the run placed no water.
     */
    public @Nullable String getWaterBody() {
        return waterBody == null ? null : waterBodyName;
    }

    /**
     * Change what the run's sea or lakes hold, keeping their wind; the fountain keeps its own
     * water. Render thread only.
     *
     * @param name One of {@link #WATER_BODY_CHOICES}.
     */
    public void setWaterBody(@NonNull String name) {
        final WaterBody base = WATER_BODY_NAMES.get(name);
        if (base == null || waterBody == null || name.equals(waterBodyName)) {
            return;
        }
        waterBodyName = name;
        waterBody =
                base.withWind(waterBody.windSpeed(), waterBody.windDirection(), waterBody.fetch());
        for (int i = 0; i < placedWater.size(); ++i) {
            final PlacedWater placed = placedWater.get(i);
            if (!placed.followsBody()) {
                continue;
            }
            final PlacedWater changed =
                    new PlacedWater(
                            placed.still().withBody(base), placed.fetch(), placed.handle(), true);
            placedWater.set(i, changed);
            graphics.water().update(changed.handle()[0], withWaterSettings(changed));
        }
    }

    /**
     * Paint waves going along the loop, every frame while the world is up, as the water surface
     * wavelets paper's figure eight does.
     */
    private void paintTestWater() {
        final WaterLoop path = waterLoop;
        if (path == null || loopWaves <= 0 || released || graphics.isClosed()) {
            return;
        }
        final float reverse = loopSpeed < 0 ? (float) Math.PI : 0;
        for (int i = 0; i < path.paintSpotCount(); ++i) {
            graphics.water()
                    .paintWaves(
                            new Vector3d(
                                    path.paintSpot(i, 0),
                                    SyntheticTerrain.SEA_LEVEL,
                                    path.paintSpot(i, 1)),
                            PAINT_RADIUS,
                            (float) path.paintSpot(i, 2) + reverse,
                            loopWaves);
        }
    }

    /**
     * The test water's settings, for the debug window: the wind's speed and degrees, the sea's
     * current's speed and degrees, the loop's speed, and its painted waves' height.
     *
     * @return The settings, or null if the run placed no island water.
     */
    public @Nullable float[] getWater() {
        if (waterBody == null) {
            return null;
        }
        return new float[] {
            waterBody.windSpeed(),
            (float) Math.toDegrees(waterBody.windDirection()),
            currentSpeed,
            (float) Math.toDegrees(currentDirection),
            loopSpeed,
            loopWaves
        };
    }

    /**
     * Whether the sea has a flowing path, for the debug window.
     *
     * @return Whether there is one.
     */
    public boolean hasWaterLoop() {
        return waterLoop != null;
    }

    /**
     * Drop a ring of ripples on the test water: on the flowing path if there is one, else on the
     * sea in front of the island. Render thread only.
     */
    public void splashTestWater() {
        final WaterLoop path = waterLoop;
        final Vector3d at =
                path != null
                        ? new Vector3d(
                                path.getSplashX(), SyntheticTerrain.SEA_LEVEL, path.getSplashZ())
                        : new Vector3d(0, SyntheticTerrain.SEA_LEVEL, SPLASH_SEA_Z);
        graphics.water().disturb(at, SPLASH_RADIUS, SPLASH_STRENGTH);
    }

    /**
     * Disturb the water every so often at one place, from the {@code .ripples=x,z,radius,strength,
     * seconds} option, to watch ripples spread and reflect, while the world is up.
     */
    private void rippleTestWater() {
        if (testRipples == null) {
            final String value = option("ripples");
            testRipples = new double[0];
            if (value != null) {
                final String[] parts = value.split(",");
                if (parts.length != RIPPLE_VALUES) {
                    log.warn(
                            "The benchmark's ripples must be x,z,radius,strength,seconds, not {}",
                            value);
                } else {
                    testRipples =
                            Arrays.stream(parts)
                                    .mapToDouble(p -> Double.parseDouble(p.trim()))
                                    .toArray();
                }
            }
        }
        if (testRipples.length == 0 || released || graphics.isClosed()) {
            return;
        }
        final long now = System.nanoTime();
        if ((now - lastRippleNanos) / NANOS_PER_SECOND < testRipples[RIPPLE_VALUES - 1]) {
            return;
        }
        lastRippleNanos = now;
        graphics.water()
                .disturb(
                        new Vector3d(testRipples[0], SyntheticTerrain.SEA_LEVEL, testRipples[1]),
                        (float) testRipples[2],
                        (float) testRipples[3]);
    }

    /**
     * Place test water from the startup property: {@code .water=x,y,z} puts a grid of 6 m tiles
     * with a corner there, on the ground at y, 12 m apart: clear ocean, green and muddy water from
     * front to back, 0.5, 2 and 6 m deep from left to right, so their colors can be compared.
     */
    private void placeTestWater() {
        placeOcean();
        placeFountain();
        placeLakes();
        placeGlassTestSmoke();
        findViews();
        final String value = option("water");
        if (value == null) {
            return;
        }
        final String[] parts = value.split(",");
        if (parts.length != 3) {
            log.warn("The benchmark's water must be x,y,z, not {}", value);
            return;
        }
        final Vector3d corner =
                new Vector3d(
                        Double.parseDouble(parts[0].trim()),
                        Double.parseDouble(parts[1].trim()),
                        Double.parseDouble(parts[2].trim()));
        for (int row = 0; row < WATER_BODIES.length; ++row) {
            for (int column = 0; column < WATER_DEPTHS.length; ++column) {
                final WaterHandle handle =
                        graphics.water()
                                .add(
                                        WaterTile.flat(
                                                new Vector3d(corner)
                                                        .add(
                                                                column * WATER_SPACING,
                                                                0,
                                                                row * WATER_SPACING),
                                                1,
                                                WATER_CELLS,
                                                WATER_CELLS,
                                                WATER_DEPTHS[column],
                                                WATER_BODIES[row]));
                if (handle != null) {
                    testWater.add(handle);
                }
            }
        }
    }

    /**
     * Place test particle emitters from the startup properties: {@code .particles=x,y,z} puts a
     * fountain there, glowing sparks 6 m along x, rising smoke puffs 6 m along -x, thick smoke in
     * the fog volume 8 m along -z and a fire 6 m along z, for looking at particles with the fog,
     * the lights and transparency; {@code .weather=rain} or {@code snow} makes it rain or snow
     * around the camera; {@code .fxTest=x,y,z} places emitters moving as a character's effects do
     * there, see {@link FxTest}.
     */
    private void placeTestParticles() {
        final String fx = option("fxTest");
        if (fx != null) {
            final String[] parts = fx.split(",");
            if (parts.length != 3) {
                log.warn("The benchmark's fxTest must be x,y,z, not {}", fx);
            } else {
                fxTest =
                        new FxTest(
                                graphics.particles(),
                                new Vector3d(
                                        Double.parseDouble(parts[0].trim()),
                                        Double.parseDouble(parts[1].trim()),
                                        Double.parseDouble(parts[2].trim())));
                testEmitters.addAll(fxTest.handles());
            }
        }
        final String startWeather = option("weather");
        if (startWeather != null) {
            setWeather(startWeather);
        }
        final String value = option("particles");
        if (value == null) {
            return;
        }
        final String[] parts = value.split(",");
        if (parts.length != 3) {
            log.warn("The benchmark's particles must be x,y,z, not {}", value);
            return;
        }
        final Vector3d at =
                new Vector3d(
                        Double.parseDouble(parts[0].trim()),
                        Double.parseDouble(parts[1].trim()),
                        Double.parseDouble(parts[2].trim()));
        for (EmitterHandle handle : placeEffectSet(at)) {
            if (handle != null) {
                testEmitters.add(handle);
            }
        }
    }

    /**
     * Place the test particle effects around a point: a fountain there, sparks, smoke puffs, thick
     * smoke in the fog volume and a fire around it.
     *
     * @param at The point.
     * @return Their emitters, null where the scene was full.
     */
    private EmitterHandle[] placeEffectSet(@NonNull Vector3dc at) {
        return new EmitterHandle[] {
            graphics.particles().spawn(ParticleSamples.FOUNTAIN, at),
            graphics.particles().spawn(ParticleSamples.SPARKS, new Vector3d(at).add(SPARKS_OFFSET)),
            graphics.particles().spawn(ParticleSamples.SMOKE, new Vector3d(at).add(PUFFS_OFFSET)),
            graphics.particles()
                    .spawn(ParticleSamples.THICK_SMOKE, new Vector3d(at).add(THICK_SMOKE_OFFSET)),
            graphics.particles().spawn(ParticleSamples.FIRE, new Vector3d(at).add(FIRE_OFFSET))
        };
    }

    /**
     * With the {@code .glassTest} option, a column of gray smoke behind the fountain, to see
     * through its water.
     */
    private void placeGlassTestSmoke() {
        final SyntheticTerrain world = terrain;
        if (world == null
                || !world.isGlassTest()
                || world.getRecipe() != SyntheticTerrain.Recipe.ISLAND) {
            return;
        }
        final MediumVolume smoke =
                MediumVolume.of(
                                MediumVolume.Shape.CYLINDER,
                                new Vector3d(
                                        0,
                                        world.fountainBase() + SMOKE_HALF_HEIGHT,
                                        GLASS_TEST_SMOKE_Z),
                                new Vector3f(SMOKE_RADIUS / 2, SMOKE_HALF_HEIGHT, 0))
                        .withMedium(SMOKE_DENSITY, SMOKE_ALBEDO, new Vector3f())
                        .withNoise(
                                MediumVolume.DEFAULT_EDGE,
                                MediumVolume.DEFAULT_NOISE_SCALE,
                                0.7f,
                                SMOKE_WIND);
        final MediumHandle handle = graphics.media().add(smoke);
        if (handle != null) {
            testMedia.add(handle);
        }
    }

    /**
     * Place test medium volumes from the startup property: {@code .smoke=x,y,z} puts a column of
     * gray smoke drifting up there, a glowing orange sphere beside it and a flat fog bank in front,
     * for looking at smoke with the fog, the lights and transparency.
     */
    private void placeTestMedia() {
        final String value = option("smoke");
        if (value == null) {
            return;
        }
        final String[] parts = value.split(",");
        if (parts.length != 3) {
            log.warn("The benchmark's smoke must be x,y,z, not {}", value);
            return;
        }
        final Vector3d at =
                new Vector3d(
                        Double.parseDouble(parts[0].trim()),
                        Double.parseDouble(parts[1].trim()),
                        Double.parseDouble(parts[2].trim()));
        final MediumVolume smoke =
                MediumVolume.of(
                                MediumVolume.Shape.CYLINDER,
                                at,
                                new Vector3f(SMOKE_RADIUS, SMOKE_HALF_HEIGHT, 0))
                        .withMedium(SMOKE_DENSITY, SMOKE_ALBEDO, new Vector3f())
                        .withNoise(
                                MediumVolume.DEFAULT_EDGE * 2,
                                MediumVolume.DEFAULT_NOISE_SCALE,
                                0.7f,
                                SMOKE_WIND);
        final MediumVolume glow =
                MediumVolume.of(
                                MediumVolume.Shape.SPHERE,
                                new Vector3d(at).add(GLOW_OFFSET),
                                new Vector3f(GLOW_RADIUS))
                        .withMedium(SMOKE_DENSITY / 2, new Vector3f(0.5f), GLOW_EMISSION)
                        .withNoise(
                                GLOW_RADIUS / 2,
                                MediumVolume.DEFAULT_NOISE_SCALE,
                                MediumVolume.DEFAULT_NOISE_STRENGTH,
                                GLOW_DRIFT);
        final MediumVolume bank =
                MediumVolume.of(
                                MediumVolume.Shape.BOX,
                                new Vector3d(at).add(BANK_OFFSET),
                                BANK_HALF_SIZE)
                        .withMedium(BANK_DENSITY, new Vector3f(0.9f), new Vector3f())
                        .withNoise(
                                MediumVolume.DEFAULT_EDGE * 4,
                                MediumVolume.DEFAULT_NOISE_SCALE * 3,
                                MediumVolume.DEFAULT_NOISE_STRENGTH,
                                new Vector3f(0.5f, 0, 0));
        for (MediumVolume volume : List.of(smoke, glow, bank)) {
            final MediumHandle handle = graphics.media().add(volume);
            if (handle != null) {
                testMedia.add(handle);
            }
        }
    }

    /**
     * Turn the scene's fog on from the startup properties: {@code .fog} gives its density per
     * meter, then, comma separated, its height falloff per meter, base height and phase anisotropy;
     * {@code .haze=true} adds the physical sky's air and haze.
     *
     * @param fog The scene's fog.
     */
    private void placeFog(@NonNull Fog fog) {
        final String value = option("fog");
        if (value == null) {
            return;
        }
        final String[] parts = value.split(",");
        fog.setActive(true);
        fog.setDensity(Float.parseFloat(parts[0].trim()));
        if (parts.length > 1) {
            fog.setHeightFalloff(Float.parseFloat(parts[1].trim()));
        }
        if (parts.length > 2) {
            fog.setBaseHeight(Float.parseFloat(parts[2].trim()));
        }
        if (parts.length > FOG_ANISOTROPY_PART) {
            fog.setAnisotropy(Float.parseFloat(parts[FOG_ANISOTROPY_PART].trim()));
        }
        fog.setHaze(Boolean.parseBoolean(option("haze")));
    }

    /**
     * The way the sun's light travels: {@link #SUN_DIRECTION}, or from the same side at the height
     * the startup property gives, in degrees above the horizon, to look at the sky at dawn or dusk.
     *
     * @return The direction.
     */
    private Vector3f sunDirection() {
        final String up = option("sunUp");
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
     * Put the camera where it turns, walked along x for a while if it walks, facing part of the way
     * around.
     *
     * @param scene The scene.
     * @param turns How far around, from 0 to 1.
     * @param seconds How long it has walked.
     */
    private void placeCamera(@NonNull Scene scene, double turns, double seconds) {
        final Vector3d position = new Vector3d(cameraPosition);
        final SyntheticTerrain world = terrain;
        if (walkSpeed > 0 && world != null) {
            position.x += walkSpeed * seconds;
            final int x = (int) Math.floor(position.x);
            final int z = (int) Math.floor(position.z);
            position.y = Math.max(position.y, world.top(x, z) + 1 + WALK_CLEARANCE);
        }
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
        logHitch(frame);
    }

    /** How long a frame may take before it counts as a hitch, in milliseconds: a 60 Hz frame. */
    public static final double HITCH_MS = 1000.0 / 60;

    /** How many of a hitch's slowest GPU stages are logged. */
    private static final int HITCH_STAGES = 3;

    /** The parts of a frame's timings that aren't GPU stages. */
    private static final Set<String> NOT_STAGES =
            Set.of(
                    FrameTimings.CPU,
                    FrameTimings.FRAME_INTERVAL,
                    FrameTimings.WAIT,
                    FrameTimings.BETWEEN_FRAMES,
                    FrameTimings.GPU_TOTAL);

    /**
     * Log a measured frame that took longer than {@link #HITCH_MS}, from start to start or on the
     * CPU, with when it was read back, to the millisecond, for matching against a GC log or a
     * profile, and its slowest GPU stages. The timings are read back a frame or two late.
     *
     * @param frame The frame's timings, in milliseconds.
     */
    private void logHitch(@NonNull Map<String, Double> frame) {
        final double interval = frame.getOrDefault(FrameTimings.FRAME_INTERVAL, 0.0);
        final double cpu = frame.getOrDefault(FrameTimings.CPU, 0.0);
        if (interval <= HITCH_MS && cpu <= HITCH_MS) {
            return;
        }
        final StringBuilder stages = new StringBuilder();
        frame.entrySet().stream()
                .filter(entry -> !NOT_STAGES.contains(entry.getKey()))
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(HITCH_STAGES)
                .forEach(
                        entry ->
                                stages.append(' ')
                                        .append(entry.getKey())
                                        .append(' ')
                                        .append(String.format("%.2f", entry.getValue())));
        log.info(
                "Gate benchmark hitch at {}: interval {} ms, CPU {} ms, wait {} ms, GPU {} ms;"
                        + " slowest:{}",
                LocalTime.now(),
                String.format("%.1f", interval),
                String.format("%.1f", cpu),
                String.format("%.1f", frame.getOrDefault(FrameTimings.WAIT, 0.0)),
                String.format("%.1f", frame.getOrDefault(FrameTimings.GPU_TOTAL, 0.0)),
                stages);
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

    /** Whether the camera was placed from the startup property's view. */
    private boolean viewPlaced;

    /** Values in a view: x, y, z, pitch and yaw. */
    private static final int VIEW_VALUES = 5;

    /**
     * Once the run is done, put the camera where the startup property's view says, if it says:
     * {@code x,y,z,pitch,yaw}, the angles in radians. For comparing frames from the same place.
     *
     * @param scene The scene.
     */
    private void placeView(@NonNull Scene scene) {
        final String view = option("view");
        if (viewPlaced || (view == null && views.isEmpty())) {
            return;
        }
        viewPlaced = true;
        if (view == null) {
            goTo(views.getFirst());
            return;
        }
        final String[] parts = view.split(",");
        if (parts.length != VIEW_VALUES) {
            log.warn("The benchmark view must be x,y,z,pitch,yaw, not {}", view);
            return;
        }
        final double[] values = new double[VIEW_VALUES];
        for (int i = 0; i < VIEW_VALUES; ++i) {
            values[i] = Double.parseDouble(parts[i].trim());
        }
        scene.getCamera().setPosition(values[0], values[1], values[2]);
        scene.getCamera().setRotation((float) values[3], (float) values[4]);
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
            final Check check =
                    new Check(where, cracks, cut.missing(), cut.triangles(), cut.hidden().size());
            checks.add(check);
            log.info("Gate benchmark crack check {}", describe(check));
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
        digsLeft = GATE_DIGS;
        propertyDigRate = GATE_DIG_RATE;
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
    @Nullable private Gate gateResults() {
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
        return new Gate(
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
     * Describe a crack check in a line.
     *
     * @param check The check.
     * @return The line.
     */
    private static String describe(@NonNull Check check) {
        final CrackCheck.Tiled cracks = check.cracks();
        final StringBuilder out =
                new StringBuilder(
                        String.format(
                                "%s: %,d cracks, %,d overdrawn edges, %,d holes (%,d gaps within"
                                        + " the error, %,d edges under volumes); %,d clusters and"
                                        + " %,d triangles drawn, %,d edges checked, %,d too long to"
                                        + " check, %,d on %,d sections drawing themselves",
                                check.where(),
                                cracks.openEdges(),
                                cracks.overdrawnEdges(),
                                check.missing(),
                                cracks.nearEdges(),
                                cracks.coveredEdges(),
                                cracks.drawnClusters(),
                                check.triangles(),
                                cracks.checkedEdges(),
                                cracks.longEdges(),
                                cracks.excusedEdges(),
                                check.hidden()));
        for (double[] at : cracks.examples()) {
            out.append(String.format("; at (%.2f, %.2f, %.2f)", at[0], at[1], at[2]));
        }
        return out.toString();
    }

    /**
     * The gate's verdicts, a line each, for the report and the debug window.
     *
     * @param results The results.
     * @return The lines, empty if the gate wasn't checked.
     */
    public static List<String> gateLines(@NonNull Results results) {
        final Gate gate = results.gate();
        if (gate == null) {
            return List.of();
        }
        final FrameTimings.Timing gpu =
                results.timings().isEmpty() ? null : results.timings().get(0);
        final List<String> lines = new ArrayList<>();
        lines.add(
                String.format(
                        "GPU at the 95th percentile: %.2f of %.1f ms, %s",
                        gpu == null ? Double.NaN : gpu.p95(),
                        GPU_BUDGET_MS,
                        verdict(results.passed())));
        final long cracks = gate.checks().stream().mapToLong(c -> c.cracks().openEdges()).sum();
        final long overdrawn =
                gate.checks().stream().mapToLong(c -> c.cracks().overdrawnEdges()).sum();
        final long holes = gate.checks().stream().mapToLong(Check::missing).sum();
        lines.add(
                gate.checks().isEmpty()
                        ? "Cracks: not checked"
                        : String.format(
                                "Cracks: %,d cracks, %,d overdrawn edges and %,d holes in %d cuts,"
                                        + " %s",
                                cracks,
                                overdrawn,
                                holes,
                                gate.checks().size(),
                                verdict(gate.cracksPassed())));
        lines.add(
                String.format(
                        "Edits: %d of %d at %.0f a second, worst drawn at full resolution after %.1f"
                                + " ms (%d frames) of %.0f ms, in level of detail after %.0f ms at"
                                + " the 95th percentile, %s",
                        gate.edits() - gate.editsUnfinished(),
                        GATE_DIGS,
                        GATE_DIG_RATE,
                        gate.worstDrawnMillis(),
                        gate.worstDrawnFrames(),
                        EDIT_BUDGET_MS,
                        gate.lodMillisP95(),
                        verdict(gate.editsPassed())));
        lines.add(
                gate.isReload()
                        ? String.format(
                                "Reload: %,d cells read from the cache and %,d built, ready after"
                                        + " %.1f s, %s",
                                gate.cacheRead(),
                                gate.cacheBuilt(),
                                results.bakeMilliseconds() / 1e3,
                                verdict(gate.reloadPassed()))
                        : String.format(
                                "First build: %,d cells built, ready after %.1f s; run again to"
                                        + " check the reload",
                                gate.cacheBuilt(), results.bakeMilliseconds() / 1e3));
        lines.add(
                String.format(
                        "Level of detail: %.1f of %.1f MiB on the GPU",
                        gate.lodBytes() / BYTES_PER_MIB, gate.lodTotalBytes() / BYTES_PER_MIB));
        return lines;
    }

    /**
     * A verdict in a word.
     *
     * @param passed Whether it passed.
     * @return The word.
     */
    private static String verdict(boolean passed) {
        return passed ? "passed" : "FAILED";
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
            final String dump = option("gpuMemoryDump");
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
                mib(VolumeMath.ATLAS_BYTES));
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
                    VolumeMath.ATLAS_BYTES / BYTES_PER_MIB,
                    renderer().getState().sections.getPlacedTriangles());
        }
        final int[] checks = renderer().getState().sections.getCheckCounts();
        log.info("Gate benchmark section checks: {} last frame, {} queued", checks[0], checks[1]);
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
                    frames.get(),
                    walkSpeed);
        }
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
                        GraphicsManager.getSceneWindow().getWidth(),
                        GraphicsManager.getSceneWindow().getHeight(),
                        gateResults());
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
        if (results.gate() != null) {
            for (Check check : results.gate().checks()) {
                out.append("  Crack check ").append(describe(check)).append(System.lineSeparator());
            }
            out.append("  Level of detail gate:").append(System.lineSeparator());
            for (String line : gateLines(results)) {
                out.append("    ").append(line).append(System.lineSeparator());
            }
        }
        return out.toString();
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
        sectionsLock.lock();
        try {
            sections.add(handle);
            sectionAt.put(position, handle);
        } finally {
            sectionsLock.unlock();
        }
        return handle;
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
            if (world == null || !world.isWaterTiles()) {
                return;
            }
            final LakeTile lake = lakeTile(world, startX, startZ);
            if (lake != null) {
                streamTasks.add(() -> placeLakeTile(lake.tile()));
            }
        }

        @Override
        public void removeLakeTile(int startX, int startZ) {
            final long key = WorldStream.tileKey(startX, startZ);
            streamTasks.add(() -> GateBenchmark.this.removeLakeTile(key));
        }
    }

    /**
     * Start streaming the world around the camera, if the run's {@code .stream} option asks, once
     * the world first laid out is drawn.
     */
    private void startStream() {
        final SyntheticTerrain world = terrain;
        if (!Boolean.parseBoolean(option("stream")) || world == null || stream != null) {
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
                        OCEAN_CELLS,
                        Set.copyOf(lakeTileKeys));
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
        lakeWater.clear();
        final Scene scene = GraphicsManager.getScene();
        if (scene != null && previousSun != null) {
            scene.getSceneLights().setDirLight(previousSun);
            scene.getSceneLights().setAmbientLight(previousAmbient);
            scene.setPhysicalSky(previousPhysicalSky);
            scene.setAutoExposure(previousAutoExposure);
            scene.getFog().setActive(previousFog);
        }
        if (!graphics.isClosed()) {
            testMedia.forEach(graphics.media()::remove);
            testMedia.clear();
            testEmitters.forEach(graphics.particles()::stop);
            testEmitters.clear();
            fxTest = null;
            if (weatherEmitter != null) {
                graphics.particles().stop(weatherEmitter);
                weatherEmitter = null;
            }
            weather = WEATHER_NONE;
            effectEmitters.forEach(graphics.particles()::stop);
            effectEmitters.clear();
            testWater.forEach(graphics.water()::remove);
            testWater.clear();
            placedWater.clear();
            waterBody = null;
            waterLoop = null;
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
