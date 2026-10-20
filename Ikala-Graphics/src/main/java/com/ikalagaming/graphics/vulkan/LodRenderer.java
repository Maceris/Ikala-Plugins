package com.ikalagaming.graphics.vulkan;

import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK12.vkCmdDrawIndexedIndirectCount;

import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.MeshHandle;
import com.ikalagaming.graphics.graph.Material;
import com.ikalagaming.graphics.graph.MaterialCache;
import com.ikalagaming.graphics.lod.ClusterMeshes;
import com.ikalagaming.graphics.lod.CoverageBricks;
import com.ikalagaming.graphics.lod.CoverageShells;
import com.ikalagaming.graphics.lod.CoverageVolume;
import com.ikalagaming.graphics.lod.LiveLod;
import com.ikalagaming.graphics.lod.LodCut;
import com.ikalagaming.graphics.lod.LodDag;
import com.ikalagaming.graphics.lod.LodResidency;
import com.ikalagaming.graphics.lod.LodStore;
import com.ikalagaming.graphics.lod.LodVertex;
import com.ikalagaming.graphics.scene.Observer;
import com.ikalagaming.graphics.scene.debug.DebugVisualizers;
import com.ikalagaming.graphics.vulkan.stages.InstanceDrawUpdate;

import lombok.Getter;
import lombok.NonNull;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3dc;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VkCommandBuffer;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.SortedSet;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

/**
 * Keeps level of detail worlds on the GPU and draws the cut the culling pass picks for them.
 *
 * <p>A world is a set of pieces, one per region cell of a live world ({@link LiveLod}) or one for a
 * whole DAG. Each piece's clusters live in their own geometry arena in the {@link LodVertex}
 * format, one mesh each, with an entry each in the cluster table that {@code lod_cull.comp} reads
 * (see {@link LodCullMath}). Changes come as batches, whose meshes are uploaded first; a batch is
 * applied in one frame once all of them are resident, so cells that replace each other never show
 * together or leave a gap.
 *
 * <p>Only what a cut near the observer can need is on the GPU. A world's clusters are kept in
 * streaming units, each the clusters one group consumes, and {@link LodResidency} decides a few
 * times a second which units are wanted, from a cut at half the threshold: those are uploaded,
 * parents first and nearest first within a budget, and the rest evicted after a delay. Every mesh
 * stays in memory on the CPU meanwhile. While a unit is missing, the cut draws the coarser clusters
 * it has, see {@link LodCullMath}.
 *
 * <p>Each frame the culling pass picks every world's cut, culls it against each view and writes
 * compacted draw commands and their counts, one list per view, which the scene and shadow stages
 * draw with one call each. Cells marked stale and sections marked hidden change the cut, see {@link
 * LodCullMath}.
 *
 * <p>Adding worlds, publishing batches and setting a world up are safe from any thread. Applying
 * batches, overrides, uploads and the frame's tables happen on the render thread.
 */
public class LodRenderer {

    /** Coordinates in a position. */
    private static final int AXES = 3;

    /** Bytes in a draw command. */
    private static final int COMMAND_SIZE = InstanceDrawUpdate.DRAW_COMMAND_SIZE;

    /**
     * The draw lists, which are the same as the instance culling's up to the translucent one: the
     * early scene, the late scene, then one per shadow cascade.
     */
    public static final int LIST_COUNT = InstanceDrawUpdate.LIST_TRANSLUCENT;

    /**
     * Where each list's cut out share starts: clusters with {@link LodCullMath#FLAG_CUT_OUT} go to
     * their list plus this, to be drawn with an alpha test.
     */
    public static final int CUT_OUT_LISTS = LIST_COUNT;

    /**
     * Where each list's volume share starts: clusters with {@link LodCullMath#FLAG_VOLUME} go to
     * their list plus this, to be drawn by walking their bricks.
     */
    public static final int VOLUME_LISTS = 2 * LIST_COUNT;

    /** Every list, opaque, cut out, then volumes. */
    public static final int LIST_SLOTS = 3 * LIST_COUNT;

    /** The counter of clusters in the cut, after one count per list. */
    public static final int COUNTER_SELECTED = LIST_SLOTS;

    /** The counter of triangles in the cut. */
    public static final int COUNTER_TRIANGLES = COUNTER_SELECTED + 1;

    /** The counter of clusters in the cut outside the scene's frustum. */
    public static final int COUNTER_OUTSIDE = COUNTER_TRIANGLES + 1;

    /** The counter of clusters in the cut the occlusion test hid. */
    public static final int COUNTER_OCCLUDED = COUNTER_OUTSIDE + 1;

    /**
     * The first level counters: for each level up to {@link #LEVEL_COUNTERS}, the cut's clusters,
     * its triangles, and its triangles inside the scene's frustum.
     */
    public static final int COUNTER_LEVELS = COUNTER_OCCLUDED + 1;

    /** How many levels have counters; deeper ones count as the last. */
    public static final int LEVEL_COUNTERS = 32;

    /** Counters per level. */
    public static final int COUNTERS_PER_LEVEL = 3;

    /** How many counters there are. */
    public static final int COUNTER_COUNT = COUNTER_LEVELS + LEVEL_COUNTERS * COUNTERS_PER_LEVEL;

    /** How many words of coverage volume bricks the brick table starts with room for. */
    private static final int INITIAL_BRICK_WORDS = 4096;

    /** How many clusters the tables start with room for. */
    private static final int INITIAL_CLUSTERS = 1024;

    /** How many cells and words of sections the flag tables start with room for. */
    private static final int INITIAL_SLOTS = 64;

    /** Bytes in a history, cell flag or hidden word entry. */
    private static final int WORD_SIZE = Integer.BYTES;

    /** The most levels of cells an override marks stale above its section. */
    private static final int MAX_CELL_LEVELS = 32;

    /** The key of a lone DAG's one piece. */
    private static final LodStore.CellKey WHOLE = new LodStore.CellKey(-1, 0, 0, 0);

    /** Nanoseconds in a millisecond. */
    private static final double NANOS_PER_MILLI = 1e6;

    /** How often each world's residency is worked out again. */
    private static final long RESIDENCY_INTERVAL_NANOS = 100_000_000L;

    /**
     * The longest a newly added world or model waits for its first cut to be resident before it is
     * drawn anyway, coarse levels standing in for what hasn't loaded, as when its wanted units
     * don't fit the byte cap.
     */
    private static final long SETTLE_NANOS = 2_000_000_000L;

    /**
     * The clusters' geometry. -- GETTER -- The arena.
     *
     * @return The arena.
     */
    @Getter private final GeometryArena arena;

    /** The pages baked clusters' tiles are in, which load and unload with their units. */
    private final LodBakes bakes;

    /**
     * Each cluster's entry, see {@link LodCullMath}. -- GETTER -- The cluster table.
     *
     * @return The cluster table.
     */
    @Getter private final DeviceTable clusters;

    /**
     * Whether each cluster was visible last frame, written by the culling pass, indexed like the
     * cluster table. -- GETTER -- The visibility history.
     *
     * @return The history.
     */
    @Getter private final DeviceTable history;

    /**
     * Each cell slot's flags, {@link LodCullMath#FLAG_STALE}. -- GETTER -- The cell flags.
     *
     * @return The cell flags.
     */
    @Getter private final DeviceTable cellFlags;

    /**
     * One bit per section slot, set while the section draws itself. -- GETTER -- The hidden
     * sections.
     *
     * @return The hidden section set.
     */
    @Getter private final DeviceTable hiddenSections;

    /**
     * One bit per streaming unit slot, set while the unit's clusters may be drawn. -- GETTER -- The
     * shown units.
     *
     * @return The shown unit set.
     */
    @Getter private final DeviceTable shownUnits;

    /** Where what the clusters draw changed, for cached shadow maps. */
    private final ShadowInvalidation shadows;

    /** Where each piece's clusters are in the tables. Render thread. */
    private final RangeAllocator ranges = new RangeAllocator(INITIAL_CLUSTERS, Integer.MAX_VALUE);

    /** Ranges of removed pieces, freed next frame so no frame writes an entry twice. */
    private final List<int[]> freeNextFrame = new ArrayList<>();

    /** Cell slots, shared by every world. Render thread. */
    private final Slots cellSlots = new Slots();

    /** Section slots, shared by every world. Render thread. */
    private final Slots sectionSlots = new Slots();

    /** World slots. Guarded by {@link #lock}. */
    private final Slots worldSlots = new Slots();

    /** Streaming unit slots, shared by every world. Render thread. */
    private final Slots unitSlots = new Slots();

    /** The shown unit words as uploaded. Render thread. */
    private int[] unitWords = new int[INITIAL_SLOTS];

    /** Shown unit words that changed since the last upload. Render thread. */
    private final BitSet dirtyUnitWords = new BitSet();

    /** The cell flags as uploaded. Render thread. */
    private int[] flags = new int[INITIAL_SLOTS];

    /** The hidden section words as uploaded. Render thread. */
    private int[] hiddenWords = new int[INITIAL_SLOTS];

    /** Cell slots whose flags changed since the last upload. Render thread. */
    private final BitSet dirtyFlags = new BitSet();

    /** Hidden words that changed since the last upload. Render thread. */
    private final BitSet dirtyWords = new BitSet();

    /** Cluster entries changed this frame, uploaded by {@link #record}. Render thread. */
    private DeviceTable.Entries clusterEntries =
            new DeviceTable.Entries(LodCullMath.CLUSTER_ENTRY_SIZE);

    /** History entries cleared this frame. Render thread. */
    private DeviceTable.Entries historyEntries = new DeviceTable.Entries(WORD_SIZE);

    /** Guards the worlds. */
    private final ReentrantLock lock = new ReentrantLock();

    /** The worlds by id, in the order they were added. */
    private final Map<Long, World> worlds = new LinkedHashMap<>();

    /** Removed worlds whose pieces are taken out on the render thread. */
    private final Queue<World> removedWorlds = new ConcurrentLinkedQueue<>();

    /** The next world's or instance's id. */
    private long nextId = 1;

    /** Instances of models by id, in the order they were placed. Guarded by {@link #lock}. */
    private final Map<Long, Instance> instances = new LinkedHashMap<>();

    /** Removed instances whose pairs are taken out on the render thread. */
    private final Queue<Instance> removedInstances = new ConcurrentLinkedQueue<>();

    /** Where each instance's pairs are in the pair table. Render thread. */
    private final RangeAllocator pairRanges = new RangeAllocator(INITIAL_SLOTS, Integer.MAX_VALUE);

    /** Pair ranges of removed instances, freed next frame so no frame writes an entry twice. */
    private final List<int[]> freePairsNextFrame = new ArrayList<>();

    /**
     * Each instance's pairs, a pair per cluster of its model, see {@link LodCullMath}. -- GETTER --
     * The pair table.
     *
     * @return The pair table.
     */
    @Getter private final DeviceTable pairs;

    /**
     * The coverage volumes' bricks, see {@link CoverageBricks}, word by word, each volume's
     * together. -- GETTER -- The brick table.
     *
     * @return The table.
     */
    @Getter private final DeviceTable bricks;

    /** Brick table entries changed this frame. */
    private DeviceTable.Entries brickEntries = new DeviceTable.Entries(CoverageBricks.WORD_BYTES);

    /** The brick table's words, a range for each registered volume. */
    private final RangeAllocator brickWords =
            new RangeAllocator(INITIAL_BRICK_WORDS, Integer.MAX_VALUE);

    /** Where each registered volume mesh's words start and how many, given back with it. */
    private final Map<MeshHandle, int[]> brickRangeOf = new IdentityHashMap<>();

    /** Pair entries changed this frame, uploaded by {@link #record}. Render thread. */
    private DeviceTable.Entries pairEntries = new DeviceTable.Entries(LodCullMath.PAIR_ENTRY_SIZE);

    /**
     * What the culling pass did, the last time its counts were read. -- GETTER -- Recent counts.
     *
     * @return The counts.
     */
    @Getter private volatile Stats stats = Stats.NONE;

    /**
     * What the culling pass did in a frame, over every world.
     *
     * @param selectedClusters Clusters in the cut.
     * @param selectedTriangles Triangles in the cut.
     * @param outside Clusters in the cut outside the scene's frustum.
     * @param occluded Clusters in the cut the occlusion test hid.
     * @param early Clusters the scene drew before the depth pyramid.
     * @param late Clusters the scene drew after it.
     * @param levelClusters Clusters in the cut at each level, the last counting any deeper.
     * @param levelTriangles Triangles in the cut at each level.
     * @param levelInView Triangles in the cut inside the scene's frustum at each level.
     */
    public record Stats(
            int selectedClusters,
            int selectedTriangles,
            int outside,
            int occluded,
            int early,
            int late,
            int @NonNull [] levelClusters,
            int @NonNull [] levelTriangles,
            int @NonNull [] levelInView) {
        /** Nothing culled yet. */
        public static final Stats NONE =
                new Stats(0, 0, 0, 0, 0, 0, new int[0], new int[0], new int[0]);
    }

    /**
     * One of each level's counters, up to the deepest level counted.
     *
     * @param counts The counters.
     * @param which Which of a level's counters: clusters, triangles, or triangles in view.
     * @return Each level's count.
     */
    private static int[] levelCounts(@NonNull IntBuffer counts, int which) {
        int levels = 0;
        for (int level = 0; level < LEVEL_COUNTERS; ++level) {
            if (counts.get(COUNTER_LEVELS + level * COUNTERS_PER_LEVEL) != 0) {
                levels = level + 1;
            }
        }
        final int[] out = new int[levels];
        for (int level = 0; level < levels; ++level) {
            out[level] = counts.get(COUNTER_LEVELS + level * COUNTERS_PER_LEVEL + which);
        }
        return out;
    }

    /**
     * How a live world is keeping up.
     *
     * @param pieces Cells on the GPU.
     * @param pendingBatches Batches waiting for their meshes to upload.
     * @param batches Batches applied so far.
     * @param staleCells Cells marked stale by the last batch or by overrides.
     * @param hiddenSections Sections drawing themselves instead of their leaves.
     * @param lastBuildMillis How long the last applied batch took to build.
     * @param lastLatencyMillis From the oldest change in the last applied batch to applying it.
     * @param units Its streaming units.
     * @param shownUnits Units whose clusters may be drawn.
     * @param loadedBytes Mesh bytes on the GPU.
     * @param wantedBytes Mesh bytes of the units the observer wants.
     * @param totalBytes Mesh bytes of every unit, all kept on the CPU.
     * @param loads Units loaded so far.
     * @param evictions Units evicted so far.
     */
    public record LiveStats(
            int pieces,
            int pendingBatches,
            long batches,
            int staleCells,
            int hiddenSections,
            double lastBuildMillis,
            double lastLatencyMillis,
            int units,
            int shownUnits,
            long loadedBytes,
            long wantedBytes,
            long totalBytes,
            long loads,
            long evictions) {}

    /** Small integers handed out and taken back, lowest free first. */
    private static final class Slots {
        /** Slots given back. */
        private final ArrayDeque<Integer> free = new ArrayDeque<>();

        /** The next slot never handed out. */
        private int next;

        /**
         * Hand out a slot.
         *
         * @return The slot.
         */
        int take() {
            final Integer reused = free.poll();
            return reused != null ? reused : next++;
        }

        /**
         * Take a slot back.
         *
         * @param slot The slot.
         */
        void give(int slot) {
            free.add(slot);
        }

        /**
         * One past the highest slot ever handed out.
         *
         * @return The bound.
         */
        int bound() {
            return next;
        }
    }

    /** One cell of a world: its clusters, in the tables, with whichever meshes are on the GPU. */
    private static final class Piece {
        /** Its cell. */
        final LodStore.CellKey key;

        /** How many of its cell's first clusters aren't its own. */
        final int inputs;

        /** Where its clusters start in the tables. */
        final int first;

        /** Its clusters, with what each gives way to; tops are updated as parents are rebuilt. */
        final LodStore.PieceCluster[] clusters;

        /** Each cluster's mesh data, kept on the CPU. */
        final List<ClusterMeshes.Mesh> meshData;

        /** Each cluster's volume's shell, kept on the CPU with its mesh, or null where none. */
        final List<CoverageShells.Shell> shells;

        /** Each cluster's mesh on the GPU, or null while it isn't or it has no triangles. */
        final MeshHandle[] meshes;

        /** Each cluster's section, or null above the leaves. */
        final LodStore.SectionPos[] sections;

        /**
         * Keep a piece.
         *
         * @param added The piece as built.
         * @param first Its first entry.
         * @param meshes Its meshes on the GPU so far.
         */
        Piece(LiveLod.@NonNull Piece added, int first, @NonNull MeshHandle[] meshes) {
            this.key = added.key();
            this.inputs = added.inputs();
            this.first = first;
            this.clusters = added.clusters().toArray(new LodStore.PieceCluster[0]);
            this.meshData = added.meshes();
            this.shells = added.shells();
            this.meshes = meshes;
            this.sections = new LodStore.SectionPos[clusters.length];
        }
    }

    /**
     * A streaming unit's key: the group that consumes it, by its cell and its id there, or the root
     * unit.
     *
     * @param cell The group's cell, or null for the root unit.
     * @param group The group's id in its cell, or -1 for the root unit.
     */
    private record UnitKey(LodStore.CellKey cell, int group) {
        /** The root unit, of the clusters nothing consumes. */
        static final UnitKey ROOT = new UnitKey(null, -1);

        @Override
        public int hashCode() {
            return cell == null
                    ? group
                    : LodStore.hashOf(cell.hashCode(), cell.level(), group, cell.x());
        }
    }

    /**
     * One cluster of a unit.
     *
     * @param piece Its piece.
     * @param index Its index there.
     */
    private record Member(@NonNull Piece piece, int index) {}

    /**
     * What a streaming unit is made of, kept as pieces come and go so the units needn't be worked
     * out again from every cluster after each batch. Render thread.
     */
    private static final class UnitState {
        /** The bound of the group that consumes its clusters. */
        final double[] bound;

        /** That group's error. */
        final double error;

        /** Its clusters. */
        final Set<Member> members = new LinkedHashSet<>();

        /** Its clusters' mesh bytes. */
        long bytes;

        /**
         * A unit state.
         *
         * @param bound The consuming group's bound.
         * @param error The consuming group's error.
         */
        UnitState(double @NonNull [] bound, double error) {
            this.bound = bound;
            this.error = error;
        }
    }

    /** A batch whose meshes are being registered and uploaded. Render thread. */
    private static final class Uploading {
        /** The batch, merged from every batch waiting when it was taken. */
        final LiveLod.Batch batch;

        /** Each added piece's meshes, null where a cluster has no triangles or isn't yet in. */
        final MeshHandle[][] meshes;

        /** The piece whose meshes are registered next. */
        int piece;

        /** The cluster of that piece registered next. */
        int cluster;

        /**
         * Start uploading a batch.
         *
         * @param batch The batch.
         */
        Uploading(LiveLod.@NonNull Batch batch) {
            this.batch = batch;
            meshes = new MeshHandle[batch.added().size()][];
            for (int p = 0; p < meshes.length; ++p) {
                meshes[p] = new MeshHandle[batch.added().get(p).meshes().size()];
            }
        }

        /**
         * Whether every mesh has been registered.
         *
         * @return True once they all have.
         */
        boolean registered() {
            return piece >= meshes.length;
        }
    }

    /**
     * The most mesh bytes registered for level of detail in a frame, so a big batch is staged a
     * part at a time instead of overflowing the staging ring.
     */
    private static final long REGISTER_BYTES_PER_FRAME = 8L * 1024 * 1024;

    /** One world. */
    private static final class World {
        /** Its id. */
        final long id;

        /** The plugin that owns it. */
        final String owner;

        /** Its baked clusters, or null if it has none. */
        final LodBakes.Plan bakes;

        /** Where its positions are measured from, in the world. */
        final double[] origin;

        /** Its slot in the world table. */
        final int slot;

        /** The level 0 cell a section is in, or null for a lone DAG. */
        final Function<LodStore.SectionPos, LodStore.CellKey> cellOf;

        /** Batches published and not yet taken, in order. */
        final Queue<LiveLod.Batch> incoming = new ConcurrentLinkedQueue<>();

        /** The batch being uploaded, or null. Render thread. */
        Uploading uploading;

        /** Whether a batch is being uploaded, for other threads. */
        volatile boolean busy;

        /** Its pieces by cell. Render thread. */
        final Map<LodStore.CellKey, Piece> pieces = new HashMap<>();

        /** Each cell's slot, while it has a piece. Render thread. */
        final Map<LodStore.CellKey, Integer> cells = new HashMap<>();

        /** Each section's slot and how many clusters use it. Render thread. */
        final Map<LodStore.SectionPos, int[]> sections = new HashMap<>();

        /** Cells the last batch left stale. Render thread. */
        Set<LodStore.CellKey> batchStale = Set.of();

        /** Sections drawing themselves, from the overrides. Render thread. */
        Set<LodStore.SectionPos> hidden = Set.of();

        /** Cells stale because of the overrides. Render thread. */
        Set<LodStore.CellKey> overrideStale = Set.of();

        /** The newest generation of each section the pieces show. Render thread. */
        final Map<LodStore.SectionPos, Long> covered = new HashMap<>();

        /** Whether it is drawn. */
        volatile boolean visible = true;

        /** The most error a drawn cluster may have, in pixels. */
        volatile double thresholdPixels;

        /** Whether it has any pieces, for other threads. */
        volatile boolean shown;

        /**
         * Whether it has been drawn yet: not before its first cut is resident, so a camera near it
         * never sees only its coarsest levels while the rest streams in, see {@link #SETTLE_NANOS}.
         */
        volatile boolean settled;

        /** When it first wanted any units, from {@link System#nanoTime()}, or 0 until it has. */
        long firstWanted;

        /** How many pieces it has, for other threads. */
        volatile int pieceCount;

        /** Batches applied. */
        volatile long batches;

        /** How long the last applied batch took to build. */
        volatile double lastBuildMillis;

        /** From the oldest change in the last applied batch to applying it. */
        volatile double lastLatencyMillis;

        /** Stale cells, last counted. */
        volatile int staleCount;

        /** Hidden sections, last counted. */
        volatile int hiddenCount;

        /** Which units are on the GPU, or null before the first batch. Render thread. */
        LodResidency residency;

        /** Each unit's clusters, by unit index. Render thread. */
        List<Set<Member>> unitMembers = List.of();

        /** Each unit's makeup, by key, in a stable order. Render thread. */
        final Map<UnitKey, UnitState> unitStates = new LinkedHashMap<>();

        /**
         * For each unit key, the units of the clusters its group produced, counted: its parents.
         * Render thread.
         */
        final Map<UnitKey, Map<UnitKey, Integer>> unitParents = new HashMap<>();

        /** Units changed since the last rebuild. Render thread. */
        final Set<UnitKey> dirtyUnits = new HashSet<>();

        /** Each unit key's index at the last rebuild. Render thread. */
        Map<UnitKey, Integer> unitIndex = Map.of();

        /** Each unit's slot, by unit index. Render thread. */
        int[] unitSlotOf = new int[0];

        /** Each unit key's slot. Render thread. */
        final Map<UnitKey, Integer> unitSlotByKey = new HashMap<>();

        /** Units to load, most needed first. Render thread. */
        final ArrayDeque<Integer> loadQueue = new ArrayDeque<>();

        /** Units whose meshes are registered, waiting for them to be uploaded. Render thread. */
        final List<Integer> uploadingUnits = new ArrayList<>();

        /** When residency was last worked out. Render thread. */
        long lastResidency;

        /** The observer, from its origin, as of the last frame. Render thread. */
        double[] eye = new double[3];

        /**
         * Where the observers residency works from are, from its origin: the observer, or for a
         * model the observer as each of its shown instances sees it. Render thread.
         */
        double[][] eyes = {eye};

        /** How many clusters it has if it is a model, which only its instances draw; else -1. */
        volatile int modelClusters = -1;

        /** A model's sphere around everything it draws, from its origin, or null. */
        volatile double[] modelBound;

        /** Pixels per unit at distance 1, as of the last frame. Render thread. */
        double pixelsPerUnit;

        /** Residency numbers, for other threads. */
        volatile long[] residencyNumbers = new long[6];

        /** Units loaded so far. */
        volatile long loads;

        /** Units evicted so far. */
        volatile long evictions;

        /**
         * Keep a world.
         *
         * @param id Its id.
         * @param owner The owner.
         * @param bakes Its baked clusters, or null.
         * @param origin The origin.
         * @param slot Its slot.
         * @param cellOf The level 0 cell a section is in, or null.
         * @param thresholdPixels Its error threshold.
         */
        World(
                long id,
                String owner,
                LodBakes.Plan bakes,
                double[] origin,
                int slot,
                Function<LodStore.SectionPos, LodStore.CellKey> cellOf,
                double thresholdPixels) {
            this.id = id;
            this.owner = owner;
            this.bakes = bakes;
            this.origin = origin;
            this.slot = slot;
            this.cellOf = cellOf;
            this.thresholdPixels = thresholdPixels;
        }
    }

    /**
     * Set up the renderer.
     *
     * @param state The Vulkan state.
     */
    public LodRenderer(@NonNull VulkanState state) {
        bakes = state.lodBakes;
        arena = new GeometryArena(state, LodVertex.SIZE);
        clusters =
                new DeviceTable(
                        state, "lod cluster", LodCullMath.CLUSTER_ENTRY_SIZE, INITIAL_CLUSTERS);
        history = new DeviceTable(state, "lod history", WORD_SIZE, INITIAL_CLUSTERS);
        cellFlags = new DeviceTable(state, "lod cell flags", WORD_SIZE, INITIAL_SLOTS);
        hiddenSections = new DeviceTable(state, "lod hidden sections", WORD_SIZE, INITIAL_SLOTS);
        shownUnits = new DeviceTable(state, "lod shown units", WORD_SIZE, INITIAL_SLOTS);
        pairs = new DeviceTable(state, "lod pairs", LodCullMath.PAIR_ENTRY_SIZE, INITIAL_SLOTS);
        bricks =
                new DeviceTable(
                        state, "lod volume bricks", CoverageBricks.WORD_BYTES, INITIAL_BRICK_WORDS);
        shadows =
                state.shadowInvalidation != null
                        ? state.shadowInvalidation
                        : new ShadowInvalidation();
    }

    /**
     * Add a whole DAG as a world, uploading every cluster. Safe from any thread.
     *
     * @param state The Vulkan state.
     * @param owner The key of the plugin that owns it.
     * @param dag The world.
     * @param meshes Each cluster's mesh, by cluster id.
     * @param thresholdPixels The most error a drawn cluster may have, in pixels.
     * @param bakes Its baked clusters, whose tiles load with them, or null if it has none.
     * @return The world's id.
     */
    public long add(
            @NonNull VulkanState state,
            @NonNull String owner,
            @NonNull LodDag dag,
            @NonNull List<ClusterMeshes.Mesh> meshes,
            double thresholdPixels,
            LodBakes.Plan bakes) {
        return addDag(state, owner, dag, meshes, thresholdPixels, bakes, false);
    }

    /**
     * Add a whole DAG as a model, which draws nothing itself: its instances, from {@link #place},
     * each draw their own cut of its clusters, which are on the GPU once for all of them. Safe from
     * any thread.
     *
     * @param state The Vulkan state.
     * @param owner The key of the plugin that owns it.
     * @param dag The model.
     * @param meshes Each cluster's mesh, by cluster id.
     * @param thresholdPixels The most error a drawn cluster may have, in pixels.
     * @param bakes Its baked clusters, naming materials, or null.
     * @return The model's id.
     * @throws IllegalArgumentException If it has more clusters than an instanced draw can name.
     */
    public long addModel(
            @NonNull VulkanState state,
            @NonNull String owner,
            @NonNull LodDag dag,
            @NonNull List<ClusterMeshes.Mesh> meshes,
            double thresholdPixels,
            LodBakes.Plan bakes) {
        if (dag.clusters().size() > LodCullMath.MAX_MODEL_CLUSTERS) {
            throw new IllegalArgumentException(
                    "A model can have at most "
                            + LodCullMath.MAX_MODEL_CLUSTERS
                            + " clusters, not "
                            + dag.clusters().size());
        }
        return addDag(state, owner, dag, meshes, thresholdPixels, bakes, true);
    }

    /**
     * Add a whole DAG as a world or a model.
     *
     * @param state The Vulkan state.
     * @param owner The key of the plugin that owns it.
     * @param dag The DAG.
     * @param meshes Each cluster's mesh, by cluster id.
     * @param thresholdPixels The most error a drawn cluster may have, in pixels.
     * @param bakes Its baked clusters, or null.
     * @param model Whether it is a model, drawn only by its instances.
     * @return Its id.
     */
    private long addDag(
            @NonNull VulkanState state,
            @NonNull String owner,
            @NonNull LodDag dag,
            @NonNull List<ClusterMeshes.Mesh> meshes,
            double thresholdPixels,
            LodBakes.Plan bakes,
            boolean model) {
        final long id = newWorld(owner, dag.getOrigin(), null, thresholdPixels, bakes);
        if (model) {
            final World world = world(id);
            world.visible = false;
            world.modelBound = rootBound(dag);
            world.modelClusters = dag.clusters().size();
        }
        final List<LodStore.PieceCluster> clusterList = new ArrayList<>(meshes.size());
        // Shells are built here, on the caller's thread, not when their meshes register
        final List<CoverageShells.Shell> shells = new ArrayList<>(meshes.size());
        for (int i = 0; i < meshes.size(); ++i) {
            final CoverageVolume volume = dag.volumeOf(i);
            shells.add(volume == null ? null : CoverageShells.of(volume, model));
            final boolean consumed = dag.consumer(i) >= 0;
            clusterList.add(
                    new LodStore.PieceCluster(
                            i,
                            dag.clusters().get(i),
                            dag.parentError(i),
                            dag.parentBound(i),
                            consumed ? WHOLE : null,
                            dag.consumer(i),
                            null,
                            dag.volumeOf(i)));
        }
        publish(
                state,
                id,
                new LiveLod.Batch(
                        List.of(new LiveLod.Piece(WHOLE, 0, clusterList, meshes, shells)),
                        List.of(),
                        List.of(),
                        Collections.emptySortedSet(),
                        Map.of(),
                        0,
                        System.nanoTime()));
        return id;
    }

    /**
     * A sphere around a DAG's roots, which cover everything it draws.
     *
     * @param dag The DAG.
     * @return The sphere, x, y, z from its origin, radius.
     */
    private static double[] rootBound(@NonNull LodDag dag) {
        final double[] center = new double[AXES];
        int roots = 0;
        for (LodDag.Cluster cluster : dag.clusters()) {
            if (dag.consumer(cluster.id()) < 0) {
                for (int axis = 0; axis < AXES; ++axis) {
                    center[axis] += cluster.tight()[axis];
                }
                ++roots;
            }
        }
        for (int axis = 0; axis < AXES; ++axis) {
            center[axis] /= Math.max(1, roots);
        }
        double radius = 0;
        for (LodDag.Cluster cluster : dag.clusters()) {
            if (dag.consumer(cluster.id()) < 0) {
                final double[] tight = cluster.tight();
                double sum = 0;
                for (int axis = 0; axis < AXES; ++axis) {
                    final double d = tight[axis] - center[axis];
                    sum += d * d;
                }
                radius = Math.max(radius, Math.sqrt(sum) + tight[AXES]);
            }
        }
        return new double[] {center[0], center[1], center[2], radius};
    }

    /**
     * Where an instance puts its model: the model's frame turned, scaled and moved.
     *
     * @param position Where the model's frame goes, in the world.
     * @param rotation Its turn.
     * @param scale Its scale.
     */
    private record Placement(
            double @NonNull [] position, @NonNull Quaterniond rotation, double scale) {}

    /** One instance of a model. */
    private static final class Instance {
        /** Its id. */
        final long id;

        /** The plugin that owns it. */
        final String owner;

        /** Its model. */
        final World model;

        /** Its world slot, whose world table entry is its own. */
        final int slot;

        /** Where it is. */
        volatile Placement placement;

        /** Material indices it draws with others, each a pair: what it replaces, and with what. */
        volatile int[][] remaps = new int[0][];

        /** Whether it is drawn. */
        volatile boolean visible = true;

        /** Where its pairs start, or -1 until it has them. Render thread. */
        int firstPair = -1;

        /** Where cached shadows last saw it, or null. Render thread. */
        Placement shadowed;

        /**
         * Keep an instance.
         *
         * @param id Its id.
         * @param owner Its owner.
         * @param model Its model.
         * @param slot Its world slot.
         * @param placement Where it is.
         */
        Instance(long id, String owner, World model, int slot, Placement placement) {
            this.id = id;
            this.owner = owner;
            this.model = model;
            this.slot = slot;
            this.placement = placement;
        }
    }

    /**
     * Place an instance of a model. Safe from any thread.
     *
     * @param owner The key of the plugin that owns it.
     * @param model The model's id, from {@link #addModel}.
     * @param position Where the model's frame goes, in the world.
     * @param rotation Its turn.
     * @param scale Its scale.
     * @return The instance's id.
     * @throws IllegalArgumentException If there is no such model.
     * @throws IllegalStateException If there are too many worlds and instances.
     */
    public long place(
            @NonNull String owner,
            long model,
            double @NonNull [] position,
            @NonNull Quaterniondc rotation,
            double scale) {
        lock.lock();
        try {
            final World world = worlds.get(model);
            if (world == null || world.modelClusters < 0) {
                throw new IllegalArgumentException("There is no model " + model);
            }
            final int slot = worldSlots.take();
            if (slot >= LodCullMath.MAX_INSTANCE_SLOTS) {
                worldSlots.give(slot);
                throw new IllegalStateException("Out of level of detail instance slots");
            }
            final long id = nextId++;
            instances.put(
                    id,
                    new Instance(
                            id,
                            owner,
                            world,
                            slot,
                            new Placement(position.clone(), new Quaterniond(rotation), scale)));
            return id;
        } finally {
            lock.unlock();
        }
    }

    /**
     * An instance by id.
     *
     * @param id The instance.
     * @return It, or null if there is no such instance.
     */
    private Instance instance(long id) {
        lock.lock();
        try {
            return instances.get(id);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Move an instance. Safe from any thread.
     *
     * @param id The instance.
     * @param position Where the model's frame goes, in the world.
     * @param rotation Its turn.
     * @param scale Its scale.
     */
    public void move(
            long id, double @NonNull [] position, @NonNull Quaterniondc rotation, double scale) {
        final Instance instance = instance(id);
        if (instance != null) {
            instance.placement = new Placement(position.clone(), new Quaterniond(rotation), scale);
        }
    }

    /**
     * Draw one of an instance's model's materials with another. Safe from any thread.
     *
     * @param id The instance.
     * @param from The model's material index to replace.
     * @param to The index to draw it with, or -1 to draw it as the model does again.
     * @throws IllegalStateException If the instance would replace too many materials.
     */
    public void setInstanceMaterial(long id, int from, int to) {
        final Instance instance = instance(id);
        if (instance == null) {
            return;
        }
        final List<int[]> kept = new ArrayList<>();
        for (int[] remap : instance.remaps) {
            if (remap[0] != from) {
                kept.add(remap);
            }
        }
        if (to >= 0) {
            kept.add(new int[] {from, to});
        }
        if (kept.size() > LodCullMath.MAX_REMAPS) {
            throw new IllegalStateException(
                    "An instance can replace at most " + LodCullMath.MAX_REMAPS + " materials");
        }
        instance.remaps = kept.toArray(new int[0][]);
    }

    /**
     * Show or hide an instance. Safe from any thread.
     *
     * @param id The instance.
     * @param visible Whether it is drawn.
     */
    public void setInstanceVisible(long id, boolean visible) {
        final Instance instance = instance(id);
        if (instance != null) {
            instance.visible = visible;
        }
    }

    /**
     * Remove an instance. Safe from any thread.
     *
     * @param id The instance.
     * @param owner The plugin asking, which must own it.
     * @return True if it was removed.
     */
    public boolean removeInstance(long id, @NonNull String owner) {
        lock.lock();
        try {
            final Instance instance = instances.get(id);
            if (instance == null || !instance.owner.equals(owner)) {
                return false;
            }
            instances.remove(id);
            removedInstances.add(instance);
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Take out the instances of models that are gone. Lock held.
     *
     * @param model Whether an instance's model is going.
     */
    private void removeInstancesOf(@NonNull java.util.function.Predicate<World> model) {
        final Iterator<Instance> it = instances.values().iterator();
        while (it.hasNext()) {
            final Instance instance = it.next();
            if (model.test(instance.model)) {
                removedInstances.add(instance);
                it.remove();
            }
        }
    }

    /**
     * The instances, copied.
     *
     * @return The instances.
     */
    private List<Instance> snapshotInstances() {
        lock.lock();
        try {
            return new ArrayList<>(instances.values());
        } finally {
            lock.unlock();
        }
    }

    /**
     * Give new instances their pairs and take removed ones' out. Render thread, at the start of a
     * frame.
     */
    private void updateInstances() {
        for (int[] range : freePairsNextFrame) {
            pairRanges.free(range[0], range[1]);
        }
        freePairsNextFrame.clear();
        Instance removed;
        while ((removed = removedInstances.poll()) != null) {
            if (removed.firstPair >= 0) {
                writePairs(removed.firstPair, removed.model.modelClusters, 0);
                freePairsNextFrame.add(new int[] {removed.firstPair, removed.model.modelClusters});
            }
            if (removed.shadowed != null) {
                invalidate(removed, removed.shadowed);
            }
            lock.lock();
            try {
                worldSlots.give(removed.slot);
            } finally {
                lock.unlock();
            }
        }
        for (Instance instance : snapshotInstances()) {
            if (instance.firstPair < 0) {
                instance.firstPair = pairRanges.allocate(instance.model.modelClusters);
                writePairs(instance.firstPair, instance.model.modelClusters, instance.slot + 1);
            }
        }
    }

    /**
     * Write a run of pair entries, each not yet visible.
     *
     * @param first The first pair.
     * @param count How many.
     * @param slot The world slot + 1 they name, 0 for none.
     */
    private void writePairs(int first, int count, int slot) {
        for (int i = 0; i < count; ++i) {
            final ByteBuffer out = pairEntries.next(first + i);
            out.putInt(out.position(), slot);
            out.putInt(out.position() + Integer.BYTES, 0);
        }
    }

    /**
     * Report where an instance's model is drawn as changed, for cached shadow maps.
     *
     * @param instance The instance.
     * @param placement Where it was or is.
     */
    private void invalidate(@NonNull Instance instance, @NonNull Placement placement) {
        final double[] bound = instance.model.modelBound;
        if (bound == null) {
            return;
        }
        shadows.addSphere(
                new double[AXES],
                LodCullMath.instanceSphere(
                        bound,
                        placement.position(),
                        placement.rotation(),
                        placement.scale(),
                        instance.model.origin));
    }

    /**
     * Add an empty world that a live world fills with its batches, through {@link #publish}. Safe
     * from any thread.
     *
     * @param owner The key of the plugin that owns it.
     * @param origin Where its positions are measured from, in the world.
     * @param cellOf The level 0 cell a section is in.
     * @param thresholdPixels The most error a drawn cluster may have, in pixels.
     * @return The world's id.
     */
    public long addLive(
            @NonNull String owner,
            double @NonNull [] origin,
            @NonNull Function<LodStore.SectionPos, LodStore.CellKey> cellOf,
            double thresholdPixels) {
        return newWorld(owner, origin, cellOf, thresholdPixels, null);
    }

    /**
     * Make a world.
     *
     * @param owner The owner.
     * @param origin Its origin.
     * @param cellOf The level 0 cell a section is in, or null for a lone DAG.
     * @param thresholdPixels Its error threshold.
     * @param bakes Its baked clusters, or null.
     * @return Its id.
     */
    private long newWorld(
            @NonNull String owner,
            double @NonNull [] origin,
            Function<LodStore.SectionPos, LodStore.CellKey> cellOf,
            double thresholdPixels,
            LodBakes.Plan bakes) {
        lock.lock();
        try {
            final long id = nextId++;
            worlds.put(
                    id,
                    new World(
                            id,
                            owner,
                            bakes,
                            origin.clone(),
                            worldSlots.take(),
                            cellOf,
                            thresholdPixels));
            return id;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Take a batch for a world. Its meshes are uploaded over the next frames and it is applied once
     * they all are; batches that pile up meanwhile are merged into one. Safe from any thread;
     * batches of one world are applied in the order they come.
     *
     * @param state The Vulkan state.
     * @param id The world.
     * @param batch The batch.
     */
    public void publish(@NonNull VulkanState state, long id, LiveLod.@NonNull Batch batch) {
        final World world = world(id);
        if (world != null) {
            world.incoming.add(batch);
        }
    }

    /**
     * Merge batches in the order they came, as if they were one: later pieces replace earlier ones
     * of the same cell, and later parents update the clusters of pieces still in the merge.
     *
     * @param batches The batches, oldest first.
     * @return The merged batch.
     */
    static LiveLod.Batch merge(@NonNull List<LiveLod.Batch> batches) {
        if (batches.size() == 1) {
            return batches.get(0);
        }
        final Map<LodStore.CellKey, LiveLod.Piece> added = new LinkedHashMap<>();
        final Set<LodStore.CellKey> removed = new LinkedHashSet<>();
        final Map<LodStore.CellKey, Map<Integer, LodStore.PieceCluster>> tops = new HashMap<>();
        final Map<LodStore.CellKey, Integer> topInputs = new HashMap<>();
        final Map<LodStore.SectionPos, Long> covered = new HashMap<>();
        SortedSet<LodStore.CellKey> stale = Collections.emptySortedSet();
        long buildNanos = 0;
        long oldest = Long.MAX_VALUE;
        for (LiveLod.Batch batch : batches) {
            for (LodStore.CellKey key : batch.removed()) {
                added.remove(key);
                tops.remove(key);
                removed.add(key);
            }
            for (LiveLod.Piece piece : batch.added()) {
                added.put(piece.key(), piece);
                tops.remove(piece.key());
            }
            for (LiveLod.Piece top : batch.tops()) {
                final LiveLod.Piece piece = added.get(top.key());
                if (piece != null) {
                    // Still in the merge: update its clusters in place
                    final List<LodStore.PieceCluster> clusters = new ArrayList<>(piece.clusters());
                    for (LodStore.PieceCluster cluster : top.clusters()) {
                        clusters.set(cluster.local() - piece.inputs(), cluster);
                    }
                    added.put(
                            top.key(),
                            new LiveLod.Piece(
                                    piece.key(),
                                    piece.inputs(),
                                    clusters,
                                    piece.meshes(),
                                    piece.shells()));
                } else {
                    topInputs.put(top.key(), top.inputs());
                    final Map<Integer, LodStore.PieceCluster> byLocal =
                            tops.computeIfAbsent(top.key(), key -> new HashMap<>());
                    for (LodStore.PieceCluster cluster : top.clusters()) {
                        byLocal.put(cluster.local(), cluster);
                    }
                }
            }
            for (Map.Entry<LodStore.SectionPos, Long> entry : batch.covered().entrySet()) {
                covered.merge(entry.getKey(), entry.getValue(), Math::max);
            }
            stale = batch.stale();
            buildNanos += batch.buildNanos();
            oldest = Math.min(oldest, batch.oldestChangeNanos());
        }
        final List<LiveLod.Piece> topPieces = new ArrayList<>();
        for (Map.Entry<LodStore.CellKey, Map<Integer, LodStore.PieceCluster>> entry :
                tops.entrySet()) {
            topPieces.add(
                    new LiveLod.Piece(
                            entry.getKey(),
                            topInputs.get(entry.getKey()),
                            new ArrayList<>(entry.getValue().values()),
                            List.of(),
                            List.of()));
        }
        return new LiveLod.Batch(
                new ArrayList<>(added.values()),
                new ArrayList<>(removed),
                topPieces,
                stale,
                covered,
                buildNanos,
                oldest);
    }

    /**
     * Register the next meshes of a batch, up to the frame's budget. Render thread.
     *
     * @param state The Vulkan state.
     * @param owner The owner.
     * @param uploading The batch.
     */
    private void registerSome(
            @NonNull VulkanState state, @NonNull World world, @NonNull Uploading uploading) {
        long bytes = 0;
        while (!uploading.registered() && bytes < REGISTER_BYTES_PER_FRAME) {
            final LiveLod.Piece piece = uploading.batch.added().get(uploading.piece);
            final List<ClusterMeshes.Mesh> pieceMeshes = piece.meshes();
            if (uploading.cluster >= pieceMeshes.size()) {
                uploading.piece += 1;
                uploading.cluster = 0;
                continue;
            }
            final ClusterMeshes.Mesh mesh = pieceMeshes.get(uploading.cluster);
            // Only what the observer wants now; the rest streams in once the batch is applied
            if ((mesh.triangleCount() > 0 || isVolume(piece.clusters().get(uploading.cluster)))
                    && isWanted(world, piece.clusters().get(uploading.cluster))) {
                uploading.meshes[uploading.piece][uploading.cluster] =
                        registerDrawn(
                                state,
                                world,
                                piece.clusters().get(uploading.cluster),
                                mesh,
                                piece.shells().get(uploading.cluster));
                bytes += bytes(mesh);
            }
            uploading.cluster += 1;
        }
    }

    /**
     * Whether the observer wants a cluster's unit, by the rule {@link LodResidency} follows.
     *
     * @param world The world.
     * @param cluster The cluster.
     * @return True if it is wanted, or streaming is off.
     */
    private static boolean isWanted(@NonNull World world, LodStore.@NonNull PieceCluster cluster) {
        if (!GraphicsManager.getSettings().lodStreaming || cluster.parentCell() == null) {
            return true;
        }
        // Any of the observers, as a model's instances each see it
        for (double[] eye : world.eyes) {
            if (LodCut.projected(
                            cluster.parentError(), cluster.parentBound(), eye, world.pixelsPerUnit)
                    > world.thresholdPixels * LodResidency.PREFETCH) {
                return true;
            }
        }
        return false;
    }

    /**
     * A cluster's mesh as it is drawn: a baked cluster takes a tile, and draws from it once it is
     * baked. Render thread.
     *
     * @param world The world.
     * @param cluster The cluster.
     * @param mesh Its mesh as built.
     * @return The mesh to register.
     */
    private ClusterMeshes.Mesh drawn(
            @NonNull World world,
            LodStore.@NonNull PieceCluster cluster,
            ClusterMeshes.@NonNull Mesh mesh) {
        final LodBakes.Baked baked = baked(world, cluster);
        return baked == null ? mesh : bakes.load(world.id, baked);
    }

    /**
     * Register a cluster's mesh as it is drawn: a coverage volume's brick boxes, its bricks taking
     * slots in the brick table, or its triangles, from its tile if it is baked. Render thread.
     *
     * @param state The Vulkan state.
     * @param world The world.
     * @param cluster The cluster.
     * @param mesh Its mesh as built.
     * @param shell Its volume's shell as built off the render thread, or null to build it here.
     * @return Its handle.
     */
    private MeshHandle registerDrawn(
            @NonNull VulkanState state,
            @NonNull World world,
            LodStore.@NonNull PieceCluster cluster,
            ClusterMeshes.@NonNull Mesh mesh,
            CoverageShells.Shell shell) {
        final CoverageVolume volume = cluster.volume();
        if (volume == null) {
            return register(state, world.owner, drawn(world, cluster, mesh));
        }
        // Coverage volumes draw as the boxes of their bricks; a model's name their materials, since
        // its instances may draw it with others
        final List<CoverageBricks.Brick> list = CoverageBricks.of(volume, world.modelClusters >= 0);
        // Its bricks together, each as long as the voxels it holds
        int words = 0;
        for (CoverageBricks.Brick brick : list) {
            words += brick.words();
        }
        final int first = brickWords.allocate(words);
        if (first < 0) {
            throw new IllegalStateException("No room for " + words + " more brick words");
        }
        final int[] offsets = new int[list.size()];
        int at = first;
        for (int b = 0; b < offsets.length; ++b) {
            offsets[b] = at;
            final ByteBuffer data = list.get(b).data();
            for (int w = 0; w < list.get(b).words(); ++w) {
                brickEntries
                        .next(at + w)
                        .put(
                                data.duplicate()
                                        .position(w * CoverageBricks.WORD_BYTES)
                                        .limit((w + 1) * CoverageBricks.WORD_BYTES));
            }
            at += list.get(b).words();
        }
        ClusterMeshes.Mesh drawn = CoverageBricks.mesh(volume, list, offsets);
        // Then its shell, drawn in place of the boxes once it is small on screen; a model's names
        // its materials too, so its instances' remaps reach it
        drawn =
                withShell(
                        drawn,
                        shell != null
                                ? shell
                                : CoverageShells.of(volume, world.modelClusters >= 0));
        final MeshHandle handle = register(state, world.owner, drawn);
        brickRangeOf.put(handle, new int[] {first, words});
        return handle;
    }

    /**
     * A volume's brick boxes with its shell after them, in one mesh.
     *
     * @param boxes The boxes.
     * @param shell The shell.
     * @return The mesh.
     */
    private static ClusterMeshes.Mesh withShell(
            ClusterMeshes.@NonNull Mesh boxes, CoverageShells.@NonNull Shell shell) {
        if (shell.triangles() == 0) {
            return boxes;
        }
        final int triangles = shell.triangles();
        final ClusterMeshes.Mesh hull =
                ClusterMeshes.of(
                        new LodDag.Cluster(
                                -1,
                                0,
                                shell.corners(),
                                shell.materials(),
                                new float[triangles],
                                shell.attributes(),
                                0,
                                new double[4],
                                new double[4],
                                -1,
                                -1));
        final int vertexCount = boxes.vertexCount() + hull.vertexCount();
        final ByteBuffer vertices =
                ByteBuffer.allocate(vertexCount * LodVertex.SIZE).order(ByteOrder.LITTLE_ENDIAN);
        vertices.put(
                boxes.vertices().duplicate().clear().limit(boxes.vertexCount() * LodVertex.SIZE));
        vertices.put(
                hull.vertices().duplicate().clear().limit(hull.vertexCount() * LodVertex.SIZE));
        vertices.flip();
        final int[] indices = new int[boxes.indices().length + hull.indices().length];
        System.arraycopy(boxes.indices(), 0, indices, 0, boxes.indices().length);
        for (int i = 0; i < hull.indices().length; ++i) {
            indices[boxes.indices().length + i] = boxes.vertexCount() + hull.indices()[i];
        }
        final float[] min = boxes.min().clone();
        final float[] max = boxes.max().clone();
        for (int axis = 0; axis < min.length; ++axis) {
            min[axis] = Math.min(min[axis], hull.min()[axis]);
            max[axis] = Math.max(max[axis], hull.max()[axis]);
        }
        return new ClusterMeshes.Mesh(vertices, vertexCount, indices, min, max);
    }

    /**
     * Whether a cluster is a coverage volume.
     *
     * @param cluster The cluster.
     * @return True if it is.
     */
    private static boolean isVolume(LodStore.@NonNull PieceCluster cluster) {
        return cluster.volume() != null;
    }

    /**
     * Release a cluster's mesh, and its bricks' words if it is a volume. Render thread.
     *
     * @param mesh The mesh.
     */
    private void release(@NonNull MeshHandle mesh) {
        arena.release(mesh);
        final int[] range = brickRangeOf.remove(mesh);
        if (range != null) {
            brickWords.free(range[0], range[1]);
        }
    }

    /**
     * A cluster's bake.
     *
     * @param world The world.
     * @param cluster The cluster.
     * @return Its bake, or null if it isn't baked.
     */
    private static LodBakes.Baked baked(
            @NonNull World world, LodStore.@NonNull PieceCluster cluster) {
        return world.bakes == null ? null : world.bakes.of(cluster.cluster().id());
    }

    /**
     * The bytes a mesh takes on the GPU.
     *
     * @param mesh The mesh.
     * @return The bytes.
     */
    private static long bytes(ClusterMeshes.@NonNull Mesh mesh) {
        return (long) mesh.vertexCount() * LodVertex.SIZE
                + (long) mesh.indices().length * Integer.BYTES;
    }

    /**
     * Register one cluster's mesh in the arena.
     *
     * @param state The Vulkan state.
     * @param owner The owner.
     * @param mesh The mesh.
     * @return Its handle.
     */
    private MeshHandle register(
            @NonNull VulkanState state, @NonNull String owner, ClusterMeshes.@NonNull Mesh mesh) {
        final ByteBuffer vertices = MemoryUtil.memAlloc(mesh.vertexCount() * LodVertex.SIZE);
        final ByteBuffer indices = MemoryUtil.memAlloc(mesh.indices().length * Integer.BYTES);
        try {
            // The vertex bytes are already little endian, as the GPU wants
            vertices.put(0, mesh.vertices(), 0, mesh.vertexCount() * LodVertex.SIZE);
            indices.asIntBuffer().put(mesh.indices());
            return arena.register(
                    state,
                    owner,
                    vertices,
                    indices,
                    new Vector3f(mesh.min()[0], mesh.min()[1], mesh.min()[2]),
                    new Vector3f(mesh.max()[0], mesh.max()[1], mesh.max()[2]));
        } finally {
            MemoryUtil.memFree(indices);
            MemoryUtil.memFree(vertices);
        }
    }

    /**
     * A world by id.
     *
     * @param id The world.
     * @return The world, or null.
     */
    private World world(long id) {
        lock.lock();
        try {
            return worlds.get(id);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Whether there are any worlds or instances of models. Safe from any thread.
     *
     * @return True if there are.
     */
    public boolean hasWorlds() {
        lock.lock();
        try {
            return !worlds.isEmpty() || !instances.isEmpty();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Set the most error a world's drawn clusters may have.
     *
     * @param id The world.
     * @param thresholdPixels The error, in pixels.
     */
    public void setThreshold(long id, double thresholdPixels) {
        final World world = world(id);
        if (world != null) {
            world.thresholdPixels = thresholdPixels;
        }
    }

    /**
     * Show or hide a world. A model stays hidden, drawn only by its instances.
     *
     * @param id The world.
     * @param visible Whether it is drawn.
     */
    public void setVisible(long id, boolean visible) {
        final World world = world(id);
        // A model is only drawn by its instances, which are shown or hidden one by one
        if (world != null && world.modelClusters < 0) {
            world.visible = visible;
        }
    }

    /**
     * Whether a world has every batch published so far on the GPU, something to draw, and is drawn:
     * its first cut has streamed in, see {@link World#settled}.
     *
     * @param id The world.
     * @return False while a batch is uploading, before it settles, or if there is no such world.
     */
    public boolean isResident(long id) {
        final World world = world(id);
        return world != null
                && world.shown
                && world.settled
                && !world.busy
                && world.incoming.isEmpty();
    }

    /**
     * How a world is keeping up.
     *
     * @param id The world.
     * @return Its numbers, or null if there is no such world.
     */
    public LiveStats liveStats(long id) {
        final World world = world(id);
        if (world == null) {
            return null;
        }
        return new LiveStats(
                world.pieceCount,
                world.incoming.size() + (world.busy ? 1 : 0),
                world.batches,
                world.staleCount,
                world.hiddenCount,
                world.lastBuildMillis,
                world.lastLatencyMillis,
                (int) world.residencyNumbers[0],
                (int) world.residencyNumbers[1],
                world.residencyNumbers[2],
                world.residencyNumbers[3],
                world.residencyNumbers[4],
                world.loads,
                world.evictions);
    }

    /**
     * Remove a world, releasing its clusters.
     *
     * @param id The world.
     * @param owner The plugin asking, which must own it.
     * @return True if it was removed.
     */
    public boolean remove(long id, @NonNull String owner) {
        lock.lock();
        try {
            final World world = worlds.get(id);
            if (world == null || !world.owner.equals(owner)) {
                return false;
            }
            worlds.remove(id);
            removedWorlds.add(world);
            removeInstancesOf(model -> model == world);
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Remove every world a plugin owns.
     *
     * @param owner The plugin.
     * @return How many were removed.
     */
    public int removeAllOwnedBy(@NonNull String owner) {
        int count = 0;
        lock.lock();
        try {
            final Iterator<World> it = worlds.values().iterator();
            while (it.hasNext()) {
                final World world = it.next();
                if (world.owner.equals(owner)) {
                    removedWorlds.add(world);
                    it.remove();
                    ++count;
                }
            }
            // Instances it owns, and every instance of a model it owned
            final Iterator<Instance> placed = instances.values().iterator();
            while (placed.hasNext()) {
                final Instance instance = placed.next();
                if (instance.owner.equals(owner) || instance.model.owner.equals(owner)) {
                    removedInstances.add(instance);
                    placed.remove();
                }
            }
        } finally {
            lock.unlock();
        }
        return count;
    }

    /**
     * Take out removed worlds, register the next meshes of each world's batch, and apply each batch
     * whose meshes are all resident. Render thread, at the start of a frame, before the overrides
     * are set.
     *
     * @param state The Vulkan state.
     */
    public void applyReady(@NonNull VulkanState state) {
        for (int[] range : freeNextFrame) {
            ranges.free(range[0], range[1]);
        }
        freeNextFrame.clear();
        updateInstances();
        World removed;
        while ((removed = removedWorlds.poll()) != null) {
            for (Piece piece : removed.pieces.values()) {
                invalidate(removed, piece);
                retire(removed, piece);
            }
            removed.pieces.clear();
            for (int slot : removed.cells.values()) {
                setFlag(slot, 0);
                cellSlots.give(slot);
            }
            removed.cells.clear();
            for (int slot : removed.unitSlotByKey.values()) {
                setUnitBit(slot, false);
                unitSlots.give(slot);
            }
            removed.unitSlotByKey.clear();
            removed.unitStates.clear();
            removed.unitParents.clear();
            removed.dirtyUnits.clear();
            removed.residency = null;
            removed.incoming.clear();
            if (removed.bakes != null) {
                bakes.release(removed.id);
            }
            if (removed.uploading != null) {
                releaseAll(removed.uploading.meshes);
                removed.uploading = null;
            }
            lock.lock();
            try {
                worldSlots.give(removed.slot);
            } finally {
                lock.unlock();
            }
        }
        for (World world : snapshotWorlds()) {
            if (world.uploading == null && !world.incoming.isEmpty()) {
                final List<LiveLod.Batch> waiting = new ArrayList<>();
                LiveLod.Batch next;
                while ((next = world.incoming.poll()) != null) {
                    waiting.add(next);
                }
                world.uploading = new Uploading(merge(waiting));
                world.busy = true;
            }
            final Uploading uploading = world.uploading;
            if (uploading != null && !uploading.registered()) {
                registerSome(state, world, uploading);
            } else if (uploading != null && isResident(uploading.meshes)) {
                // One batch a frame, and no streaming that frame, so no entry is written twice
                world.uploading = null;
                world.busy = false;
                apply(world, uploading.batch, uploading.meshes);
                rebuildUnits(world);
                continue;
            }
            stream(state, world);
        }
    }

    /**
     * Rebuild a world's streaming units from its pieces, after a batch, keeping what is on the GPU.
     * Render thread.
     *
     * @param world The world.
     */
    private void rebuildUnits(@NonNull World world) {
        final Map<UnitKey, Integer> index = new HashMap<>();
        final List<Set<Member>> members = new ArrayList<>(world.unitStates.size());
        for (Map.Entry<UnitKey, UnitState> entry : world.unitStates.entrySet()) {
            index.put(entry.getKey(), members.size());
            members.add(entry.getValue().members);
        }
        final List<LodResidency.Unit> units = new ArrayList<>(members.size());
        final boolean[] loaded = new boolean[members.size()];
        final LodResidency before = world.residency;
        int u = 0;
        for (Map.Entry<UnitKey, UnitState> entry : world.unitStates.entrySet()) {
            final UnitState state = entry.getValue();
            final Map<UnitKey, Integer> parentKeys =
                    world.unitParents.getOrDefault(entry.getKey(), Map.of());
            final int[] parents = new int[parentKeys.size()];
            int count = 0;
            for (UnitKey parent : parentKeys.keySet()) {
                final Integer at = index.get(parent);
                if (at != null) {
                    parents[count++] = at;
                }
            }
            units.add(
                    new LodResidency.Unit(
                            state.error, state.bound, state.bytes, Arrays.copyOf(parents, count)));
            // Only changed units are checked again; the rest are as the last residency had them
            final Integer was = world.unitIndex.get(entry.getKey());
            loaded[u] =
                    before == null || was == null || world.dirtyUnits.contains(entry.getKey())
                            ? isLoaded(world, members.get(u))
                            : before.isLoaded(was);
            ++u;
        }
        world.dirtyUnits.clear();
        world.unitIndex = index;
        world.residency = new LodResidency(units, loaded);
        world.unitMembers = members;
        // Slots stay with their keys; the ones gone are given back
        final Iterator<Map.Entry<UnitKey, Integer>> it = world.unitSlotByKey.entrySet().iterator();
        while (it.hasNext()) {
            final Map.Entry<UnitKey, Integer> entry = it.next();
            if (!index.containsKey(entry.getKey())) {
                setUnitBit(entry.getValue(), false);
                unitSlots.give(entry.getValue());
                it.remove();
            }
        }
        world.unitSlotOf = new int[members.size()];
        for (Map.Entry<UnitKey, Integer> entry : index.entrySet()) {
            final int slot = unitSlot(world, entry.getKey());
            world.unitSlotOf[entry.getValue()] = slot;
            setUnitBit(slot, world.residency.isShown(entry.getValue()));
        }
        world.loadQueue.clear();
        world.uploadingUnits.clear();
        // Plan again next frame
        world.lastResidency = 0;
    }

    /**
     * Count a piece's cluster into its unit, and its unit into its producer's parents.
     *
     * @param world The world.
     * @param piece The piece.
     * @param i The cluster's index in it.
     */
    private void joinUnits(@NonNull World world, @NonNull Piece piece, int i) {
        final LodStore.PieceCluster cluster = piece.clusters[i];
        final UnitKey own = ownUnitKey(cluster);
        final UnitState state =
                world.unitStates.computeIfAbsent(
                        own, key -> new UnitState(cluster.parentBound(), cluster.parentError()));
        state.members.add(new Member(piece, i));
        state.bytes += bytes(world, piece, i);
        world.dirtyUnits.add(own);
        final int producer = cluster.cluster().producer();
        if (producer >= 0) {
            final UnitKey made = new UnitKey(piece.key, producer);
            world.unitParents
                    .computeIfAbsent(made, key -> new HashMap<>())
                    .merge(own, 1, Integer::sum);
            world.dirtyUnits.add(made);
        }
    }

    /**
     * Take a piece's cluster out of its unit and its producer's parents, as {@link #joinUnits}
     * counted it.
     *
     * @param world The world.
     * @param piece The piece.
     * @param i The cluster's index in it.
     */
    private void leaveUnits(@NonNull World world, @NonNull Piece piece, int i) {
        final LodStore.PieceCluster cluster = piece.clusters[i];
        final UnitKey own = ownUnitKey(cluster);
        final UnitState state = world.unitStates.get(own);
        if (state != null && state.members.remove(new Member(piece, i))) {
            state.bytes -= bytes(world, piece, i);
            if (state.members.isEmpty()) {
                world.unitStates.remove(own);
            }
            world.dirtyUnits.add(own);
        }
        final int producer = cluster.cluster().producer();
        if (producer >= 0) {
            final UnitKey made = new UnitKey(piece.key, producer);
            final Map<UnitKey, Integer> parents = world.unitParents.get(made);
            if (parents != null) {
                parents.computeIfPresent(own, (key, n) -> n > 1 ? n - 1 : null);
                if (parents.isEmpty()) {
                    world.unitParents.remove(made);
                }
            }
            world.dirtyUnits.add(made);
        }
    }

    /**
     * The key of the unit a cluster is in.
     *
     * @param cluster The cluster.
     * @return Its unit's key.
     */
    private static UnitKey ownUnitKey(LodStore.@NonNull PieceCluster cluster) {
        return cluster.parentCell() == null
                ? UnitKey.ROOT
                : new UnitKey(cluster.parentCell(), cluster.parentGroup());
    }

    /**
     * A unit key's slot, handing one out if it has none.
     *
     * @param world The world.
     * @param key The key.
     * @return The slot.
     */
    private int unitSlot(@NonNull World world, @NonNull UnitKey key) {
        return world.unitSlotByKey.computeIfAbsent(key, k -> unitSlots.take());
    }

    /**
     * The bytes a piece's cluster takes on the GPU: its mesh, and its tile if it is baked.
     *
     * @param world The world.
     * @param piece The piece.
     * @param i The cluster's index in it.
     * @return The bytes.
     */
    private static long bytes(@NonNull World world, @NonNull Piece piece, int i) {
        final LodBakes.Baked baked = baked(world, piece.clusters[i]);
        return bytes(piece.meshData.get(i)) + (baked == null ? 0 : baked.bytes());
    }

    /**
     * Whether every mesh of a unit is on the GPU, and every tile of it baked.
     *
     * @param world The world.
     * @param members The unit's clusters.
     * @return True if all with triangles are uploaded.
     */
    private boolean isLoaded(@NonNull World world, @NonNull Collection<Member> members) {
        for (Member member : members) {
            if (member.piece().meshData.get(member.index()).triangleCount() == 0) {
                continue;
            }
            final MeshHandle mesh = member.piece().meshes[member.index()];
            if (mesh == null || !arena.getRegistry().isResident(mesh)) {
                return false;
            }
            final LodStore.PieceCluster cluster = member.piece().clusters[member.index()];
            if (baked(world, cluster) != null && !bakes.isBaked(world.id, cluster.cluster().id())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Load and evict a world's units: work out what is wanted every so often, register the next
     * wanted meshes within the budget, and show units once they and their parents are uploaded.
     * Render thread.
     *
     * @param state The Vulkan state.
     * @param world The world.
     */
    private void stream(@NonNull VulkanState state, @NonNull World world) {
        final LodResidency residency = world.residency;
        if (residency == null) {
            world.settled = true;
            return;
        }
        final long now = System.nanoTime();
        if (now - world.lastResidency >= RESIDENCY_INTERVAL_NANOS) {
            world.lastResidency = now;
            residency.update(
                    world.eyes,
                    world.pixelsPerUnit,
                    world.thresholdPixels,
                    now,
                    GraphicsManager.getSettings().lodStreaming);
            for (int unit :
                    residency.toEvict(now, GraphicsManager.getSettings().lodResidentBytes)) {
                evict(world, unit);
            }
            world.loadQueue.clear();
            for (int unit : residency.toLoad()) {
                if (!world.uploadingUnits.contains(unit)) {
                    world.loadQueue.add(unit);
                }
            }
        }
        // Register the next wanted units' meshes
        long bytes = 0;
        while (bytes < REGISTER_BYTES_PER_FRAME && !world.loadQueue.isEmpty()) {
            final int unit = world.loadQueue.poll();
            for (Member member : world.unitMembers.get(unit)) {
                final Piece piece = member.piece();
                final ClusterMeshes.Mesh mesh = piece.meshData.get(member.index());
                if (piece.meshes[member.index()] == null
                        && (mesh.triangleCount() > 0 || isVolume(piece.clusters[member.index()]))) {
                    piece.meshes[member.index()] =
                            registerDrawn(
                                    state,
                                    world,
                                    piece.clusters[member.index()],
                                    mesh,
                                    piece.shells.get(member.index()));
                    writeEntry(world, piece, member.index());
                    bytes += bytes(mesh);
                }
            }
            world.uploadingUnits.add(unit);
        }
        // Units whose meshes have arrived, then show what can be
        final Iterator<Integer> it = world.uploadingUnits.iterator();
        while (it.hasNext()) {
            final int unit = it.next();
            if (isLoaded(world, world.unitMembers.get(unit))) {
                residency.setLoaded(unit);
                world.loads += 1;
                it.remove();
            }
        }
        for (int unit : residency.toShow()) {
            if (setUnitBit(world.unitSlotOf[unit], true)) {
                invalidate(world, world.unitMembers.get(unit));
            }
        }
        // Drawn once the first cut is all there, or once it has waited long enough
        if (!world.settled && residency.anyWanted()) {
            if (world.firstWanted == 0) {
                world.firstWanted = now;
            }
            world.settled = residency.wantedAllShown() || now - world.firstWanted >= SETTLE_NANOS;
        }
        world.residencyNumbers =
                new long[] {
                    residency.size(),
                    residency.shownCount(),
                    residency.loadedBytes(),
                    residency.wantedBytes(),
                    residency.totalBytes()
                };
    }

    /**
     * Take a unit off the GPU: hide it, clear its clusters' meshes from their entries, and release
     * them. Render thread.
     *
     * @param world The world.
     * @param unit The unit.
     */
    private void evict(@NonNull World world, int unit) {
        if (setUnitBit(world.unitSlotOf[unit], false)) {
            invalidate(world, world.unitMembers.get(unit));
        }
        for (Member member : world.unitMembers.get(unit)) {
            final Piece piece = member.piece();
            final MeshHandle mesh = piece.meshes[member.index()];
            if (mesh != null) {
                piece.meshes[member.index()] = null;
                release(mesh);
                writeEntry(world, piece, member.index());
            }
            if (baked(world, piece.clusters[member.index()]) != null) {
                bakes.unload(world.id, piece.clusters[member.index()].cluster().id());
            }
        }
        world.evictions += 1;
    }

    /**
     * Set or clear a unit slot's shown bit, noting the change.
     *
     * @param slot The slot.
     * @param shown Whether its clusters may be drawn.
     * @return True if the bit changed.
     */
    private boolean setUnitBit(int slot, boolean shown) {
        final int word = slot / LodCullMath.BITS_PER_WORD;
        final int bit = 1 << (slot % LodCullMath.BITS_PER_WORD);
        if (word >= unitWords.length) {
            if (!shown) {
                return false;
            }
            unitWords = Arrays.copyOf(unitWords, Math.max(word + 1, unitWords.length * 2));
        }
        final int value = shown ? unitWords[word] | bit : unitWords[word] & ~bit;
        if (unitWords[word] != value) {
            unitWords[word] = value;
            dirtyUnitWords.set(word);
            return true;
        }
        return false;
    }

    /**
     * Report where a piece's clusters are as changed, for cached shadow maps.
     *
     * @param world The world.
     * @param piece The piece, or null for none.
     */
    private void invalidate(@NonNull World world, Piece piece) {
        if (piece == null) {
            return;
        }
        for (LodStore.PieceCluster cluster : piece.clusters) {
            shadows.addSphere(world.origin, cluster.cluster().tight());
        }
    }

    /**
     * Report where a unit's clusters are as changed, for cached shadow maps.
     *
     * @param world The world.
     * @param members The unit's clusters.
     */
    private void invalidate(@NonNull World world, @NonNull Collection<Member> members) {
        for (Member member : members) {
            shadows.addSphere(
                    world.origin, member.piece().clusters[member.index()].cluster().tight());
        }
    }

    /**
     * Report the pieces of cells that became stale or stopped being, for cached shadow maps.
     *
     * @param world The world.
     * @param before The stale cells before.
     * @param after The stale cells after.
     */
    private void invalidateChanged(
            @NonNull World world,
            @NonNull Set<LodStore.CellKey> before,
            @NonNull Set<LodStore.CellKey> after) {
        for (LodStore.CellKey key : before) {
            if (!after.contains(key)) {
                invalidate(world, world.pieces.get(key));
            }
        }
        for (LodStore.CellKey key : after) {
            if (!before.contains(key)) {
                invalidate(world, world.pieces.get(key));
            }
        }
    }

    /**
     * The worlds, copied.
     *
     * @return The worlds.
     */
    private List<World> snapshotWorlds() {
        lock.lock();
        try {
            return new ArrayList<>(worlds.values());
        } finally {
            lock.unlock();
        }
    }

    /**
     * Whether every mesh is uploaded.
     *
     * @param meshes The meshes, null where there are none.
     * @return True if all are.
     */
    private boolean isResident(@NonNull MeshHandle[][] meshes) {
        for (MeshHandle[] piece : meshes) {
            for (MeshHandle mesh : piece) {
                if (mesh != null && !arena.getRegistry().isResident(mesh)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Release every mesh of a batch that won't be applied.
     *
     * @param meshes The meshes.
     */
    private void releaseAll(@NonNull MeshHandle[][] meshes) {
        for (MeshHandle[] piece : meshes) {
            for (MeshHandle mesh : piece) {
                if (mesh != null) {
                    release(mesh);
                }
            }
        }
    }

    /**
     * Apply one batch. Render thread.
     *
     * @param world The world.
     * @param batch The batch.
     * @param meshes Its meshes, resident.
     */
    private void apply(
            @NonNull World world, LiveLod.@NonNull Batch batch, @NonNull MeshHandle[][] meshes) {
        // Out with what is replaced or removed
        final Set<LodStore.CellKey> going = new HashSet<>(batch.removed());
        for (LiveLod.Piece piece : batch.added()) {
            going.add(piece.key());
        }
        for (LodStore.CellKey key : going) {
            final Piece old = world.pieces.remove(key);
            if (old != null) {
                for (int i = 0; i < old.clusters.length; ++i) {
                    leaveUnits(world, old, i);
                }
                invalidate(world, old);
                retire(world, old);
            }
        }
        // Every new cell has its slot before any entry names it as a parent
        for (LiveLod.Piece piece : batch.added()) {
            world.cells.computeIfAbsent(piece.key(), key -> cellSlots.take());
        }
        for (LodStore.CellKey key : batch.removed()) {
            final Integer slot = world.cells.get(key);
            if (slot != null && !world.pieces.containsKey(key) && !isAdded(batch, key)) {
                world.cells.remove(key);
                setFlag(slot, 0);
                cellSlots.give(slot);
            }
        }
        for (int p = 0; p < batch.added().size(); ++p) {
            place(world, batch.added().get(p), meshes[p]);
            invalidate(world, world.pieces.get(batch.added().get(p).key()));
        }
        for (LiveLod.Piece tops : batch.tops()) {
            final Piece piece = world.pieces.get(tops.key());
            if (piece == null) {
                continue;
            }
            for (LodStore.PieceCluster cluster : tops.clusters()) {
                final int index = cluster.local() - piece.inputs;
                leaveUnits(world, piece, index);
                piece.clusters[index] = cluster;
                joinUnits(world, piece, index);
                writeEntry(world, piece, index);
                shadows.addSphere(world.origin, cluster.cluster().tight());
            }
        }
        invalidateChanged(world, world.batchStale, batch.stale());
        world.batchStale = new HashSet<>(batch.stale());
        for (Map.Entry<LodStore.SectionPos, Long> entry : batch.covered().entrySet()) {
            world.covered.merge(entry.getKey(), entry.getValue(), Math::max);
        }
        world.shown = !world.pieces.isEmpty();
        world.pieceCount = world.pieces.size();
        world.batches += 1;
        world.lastBuildMillis = batch.buildNanos() / NANOS_PER_MILLI;
        world.lastLatencyMillis = (System.nanoTime() - batch.oldestChangeNanos()) / NANOS_PER_MILLI;
    }

    /**
     * Whether a batch adds a cell.
     *
     * @param batch The batch.
     * @param key The cell.
     * @return True if it does.
     */
    private static boolean isAdded(LiveLod.@NonNull Batch batch, LodStore.@NonNull CellKey key) {
        for (LiveLod.Piece piece : batch.added()) {
            if (piece.key().equals(key)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Put a piece in the tables. Render thread.
     *
     * @param world The world.
     * @param added The piece.
     * @param meshes Its meshes, resident.
     */
    private void place(
            @NonNull World world, LiveLod.@NonNull Piece added, @NonNull MeshHandle[] meshes) {
        final int count = added.clusters().size();
        final int first = ranges.allocate(count);
        if (first < 0) {
            throw new IllegalStateException("No room for " + count + " more LOD clusters");
        }
        final Piece piece = new Piece(added, first, meshes);
        for (int i = 0; i < count; ++i) {
            final LodStore.SectionPos section = added.clusters().get(i).section();
            piece.sections[i] = section;
            if (section != null) {
                final int[] slot = world.sections.get(section);
                if (slot == null) {
                    world.sections.put(section, new int[] {sectionSlots.take(), 1});
                } else {
                    slot[1] += 1;
                }
            }
            writeEntry(world, piece, i);
            historyEntries.next(first + i);
        }
        world.pieces.put(added.key(), piece);
        for (int i = 0; i < piece.clusters.length; ++i) {
            joinUnits(world, piece, i);
        }
    }

    /**
     * Write one cluster's entry. Render thread.
     *
     * @param world The world.
     * @param piece The cluster's piece.
     * @param index The cluster's index in the piece.
     */
    private void writeEntry(@NonNull World world, @NonNull Piece piece, int index) {
        putEntry(world, piece, index, clusterEntries.next(piece.first + index));
    }

    /**
     * Write one cluster's entry somewhere. Render thread.
     *
     * @param world The world.
     * @param piece The cluster's piece.
     * @param index The cluster's index in the piece.
     * @param out Where it goes, from its position.
     */
    private void putEntry(
            @NonNull World world, @NonNull Piece piece, int index, @NonNull ByteBuffer out) {
        final LodStore.PieceCluster cluster = piece.clusters[index];
        final MeshHandle mesh = piece.meshes[index];
        final Integer cell = world.cells.get(piece.key);
        final int producer = cluster.cluster().producer();
        final Integer parentCell =
                cluster.parentCell() == null ? null : world.cells.get(cluster.parentCell());
        final int[] section =
                cluster.section() == null ? null : world.sections.get(cluster.section());
        LodCullMath.putCluster(
                out,
                cluster.cluster(),
                cluster.parentError(),
                cluster.parentBound(),
                mesh == null ? LodCullMath.NO_MESH : mesh.slot(),
                mesh == null ? 0 : mesh.generation(),
                new LodCullMath.Place(
                        world.slot,
                        cell == null ? LodCullMath.NONE : cell,
                        parentCell == null ? LodCullMath.NONE : parentCell,
                        section == null ? LodCullMath.NONE : section[0],
                        unitSlot(world, ownUnitKey(cluster)),
                        producer < 0
                                ? LodCullMath.NONE
                                : unitSlot(world, new UnitKey(piece.key, producer)),
                        // Counted from the finest: the cell's own level plus the level within it
                        piece.key.level() + cluster.cluster().level()),
                (cutOut(cluster.cluster()) ? LodCullMath.FLAG_CUT_OUT : 0)
                        | (cluster.volume() != null ? volumeFlags(cluster.volume()) : 0));
    }

    /**
     * A coverage volume cluster's flags: that it is one, and, if it has a shell after its boxes,
     * how many of its mesh's indices the boxes take, see {@link LodCullMath#SHELL_SHIFT}.
     *
     * @param volume The volume.
     * @return The flags.
     */
    private static int volumeFlags(@NonNull CoverageVolume volume) {
        final int boxes = CoverageBricks.count(volume) * CoverageBricks.BOX_INDICES;
        return LodCullMath.FLAG_VOLUME | boxes << LodCullMath.SHELL_SHIFT;
    }

    /**
     * Whether any of a cluster's triangles has a material with holes cut out by its texture.
     *
     * @param cluster The cluster.
     * @return True if one has.
     */
    private static boolean cutOut(LodDag.@NonNull Cluster cluster) {
        final MaterialCache cache = GraphicsManager.getScene().getMaterialCache();
        int last = -1;
        for (int material : cluster.materials()) {
            if (material == last) {
                continue;
            }
            last = material;
            final Material found = cache.getMaterial(material);
            if (found != null && found.getTransparency() == Material.Transparency.CUTOUT) {
                return true;
            }
        }
        return false;
    }

    /**
     * The cut of a live world as the GPU picks it from the observer of the last frame, before
     * culling, worked out on the CPU from the same entries, flags and shown units, for checking.
     *
     * @param clusters Every cluster of the world's cells, all at its origin.
     * @param highest The highest level each stands for its part of the input at: below the level
     *     its consumer makes, or the top for a root.
     * @param drawn Whether each is drawn. One picked whose mesh has no triangles counts as drawn,
     *     since there is nothing of it to draw.
     * @param missing How many were picked with triangles but no mesh on the GPU: holes.
     * @param hidden The sections drawing themselves instead.
     * @param triangles The triangles of the meshes drawn.
     */
    public record Cut(
            @NonNull List<LodDag.Cluster> clusters,
            int @NonNull [] highest,
            boolean @NonNull [] drawn,
            int missing,
            @NonNull Set<LodStore.SectionPos> hidden,
            long triangles,
            @NonNull List<CoverageVolume> volumes) {}

    /**
     * Work out the cut of a world as the GPU picks it from the last frame's observer. Render
     * thread.
     *
     * @param id The world.
     * @return The cut, or null if there is no such world.
     */
    public Cut cut(long id) {
        final World world = world(id);
        if (world == null) {
            return null;
        }
        // The level each group was simplified at, below its outputs
        final Map<UnitKey, Integer> groupLevels = new HashMap<>();
        int count = 0;
        int top = 0;
        for (Piece piece : world.pieces.values()) {
            for (LodStore.PieceCluster cluster : piece.clusters) {
                final LodDag.Cluster c = cluster.cluster();
                top = Math.max(top, c.level());
                if (c.producer() >= 0) {
                    groupLevels.merge(
                            new UnitKey(piece.key, c.producer()), c.level() - 1, Math::max);
                }
            }
            count += piece.clusters.length;
        }
        final ByteBuffer entry =
                ByteBuffer.allocate(LodCullMath.CLUSTER_ENTRY_SIZE).order(ByteOrder.nativeOrder());
        final float[] eye = {(float) world.eye[0], (float) world.eye[1], (float) world.eye[2]};
        final List<LodDag.Cluster> clusters = new ArrayList<>(count);
        final List<CoverageVolume> volumes = new ArrayList<>(count);
        final int[] highest = new int[count];
        final boolean[] drawn = new boolean[count];
        int missing = 0;
        long triangles = 0;
        int k = 0;
        for (Piece piece : world.pieces.values()) {
            for (int i = 0; i < piece.clusters.length; ++i, ++k) {
                final LodStore.PieceCluster cluster = piece.clusters[i];
                clusters.add(cluster.cluster());
                volumes.add(cluster.volume());
                highest[k] =
                        cluster.parentCell() == null
                                ? top
                                : groupLevels.getOrDefault(
                                        new UnitKey(cluster.parentCell(), cluster.parentGroup()),
                                        top);
                entry.clear();
                putEntry(world, piece, i, entry);
                if (!LodCullMath.selected(
                        entry,
                        0,
                        eye,
                        (float) world.pixelsPerUnit,
                        (float) world.thresholdPixels,
                        flags,
                        hiddenWords,
                        unitWords)) {
                    continue;
                }
                final int meshTriangles = piece.meshData.get(i).triangleCount();
                final MeshHandle mesh = piece.meshes[i];
                if (meshTriangles == 0) {
                    drawn[k] = true;
                } else if (mesh != null && arena.getRegistry().isResident(mesh)) {
                    drawn[k] = true;
                    triangles += meshTriangles;
                } else {
                    ++missing;
                }
            }
        }
        return new Cut(clusters, highest, drawn, missing, world.hidden, triangles, volumes);
    }

    /**
     * Whether a world has nothing waiting: no batch uploading or queued, and no unit loading.
     * Render thread.
     *
     * @param id The world.
     * @return True if it is settled, or there is no such world.
     */
    public boolean isSettled(long id) {
        final World world = world(id);
        return world == null
                || (world.uploading == null
                        && world.incoming.isEmpty()
                        && world.loadQueue.isEmpty()
                        && world.uploadingUnits.isEmpty());
    }

    /**
     * Take a piece out of the tables and release its meshes. Render thread.
     *
     * @param world The world.
     * @param piece The piece.
     */
    private void retire(@NonNull World world, @NonNull Piece piece) {
        final int count = piece.meshes.length;
        for (int i = 0; i < count; ++i) {
            // An empty entry, which the culling pass skips
            clusterEntries.next(piece.first + i);
            if (piece.meshes[i] != null) {
                release(piece.meshes[i]);
            }
            if (baked(world, piece.clusters[i]) != null) {
                bakes.unload(world.id, piece.clusters[i].cluster().id());
            }
            final LodStore.SectionPos section = piece.sections[i];
            if (section != null) {
                final int[] slot = world.sections.get(section);
                if (slot != null && --slot[1] == 0) {
                    world.sections.remove(section);
                    setHidden(slot[0], false);
                    sectionSlots.give(slot[0]);
                }
            }
        }
        freeNextFrame.add(new int[] {piece.first, count});
    }

    /**
     * The newest generation of a section a live world shows. Render thread.
     *
     * @param id The world.
     * @param section The section.
     * @return The generation, or -1 if it shows none.
     */
    public long coveredGeneration(long id, LodStore.@NonNull SectionPos section) {
        final World world = world(id);
        if (world == null) {
            return -1;
        }
        final Long generation = world.covered.get(section);
        return generation == null ? -1 : generation;
    }

    /**
     * Forget the generations shown of sections no longer around. Render thread.
     *
     * @param id The world.
     * @param section The section.
     */
    public void forgetCovered(long id, LodStore.@NonNull SectionPos section) {
        final World world = world(id);
        if (world != null) {
            world.covered.remove(section);
        }
    }

    /**
     * Set which sections of a live world draw themselves this frame: their leaves are hidden, and
     * their cells and every cell above them are stale, so nothing coarser covers them. Render
     * thread, after {@link #applyReady(VulkanState)} and before {@link #record}.
     *
     * @param id The world.
     * @param overrides The sections.
     */
    public void setOverrides(long id, @NonNull Set<LodStore.SectionPos> overrides) {
        final World world = world(id);
        if (world == null || world.cellOf == null) {
            return;
        }
        final Set<LodStore.CellKey> stale = new HashSet<>();
        for (LodStore.SectionPos section : overrides) {
            LodStore.CellKey key = world.cellOf.apply(section);
            for (int level = 0; level < MAX_CELL_LEVELS; ++level) {
                if (!stale.add(key)) {
                    break;
                }
                key = key.parent();
            }
        }
        world.hidden = Set.copyOf(overrides);
        invalidateChanged(world, world.overrideStale, stale);
        world.overrideStale = stale;
    }

    /**
     * Bring the cell flags and hidden sections up to date with every world's batches and overrides.
     * Render thread.
     */
    private void updateFlags() {
        final int[] wantFlags = new int[cellSlots.bound()];
        final int[] wantWords =
                new int
                        [(sectionSlots.bound() + LodCullMath.BITS_PER_WORD - 1)
                                / LodCullMath.BITS_PER_WORD];
        for (World world : snapshotWorlds()) {
            int stale = 0;
            for (Map.Entry<LodStore.CellKey, Integer> entry : world.cells.entrySet()) {
                if (world.batchStale.contains(entry.getKey())
                        || world.overrideStale.contains(entry.getKey())) {
                    wantFlags[entry.getValue()] |= LodCullMath.FLAG_STALE;
                    ++stale;
                }
            }
            int hidden = 0;
            for (LodStore.SectionPos section : world.hidden) {
                final int[] slot = world.sections.get(section);
                if (slot != null) {
                    wantWords[slot[0] / LodCullMath.BITS_PER_WORD] |=
                            1 << (slot[0] % LodCullMath.BITS_PER_WORD);
                    ++hidden;
                }
            }
            world.staleCount = stale;
            world.hiddenCount = hidden;
        }
        for (int slot = 0; slot < Math.max(wantFlags.length, flags.length); ++slot) {
            setFlag(slot, slot < wantFlags.length ? wantFlags[slot] : 0);
        }
        for (int word = 0; word < Math.max(wantWords.length, hiddenWords.length); ++word) {
            setWord(word, word < wantWords.length ? wantWords[word] : 0);
        }
    }

    /**
     * Set a cell slot's flags, noting the change.
     *
     * @param slot The slot.
     * @param value The flags.
     */
    private void setFlag(int slot, int value) {
        if (slot >= flags.length) {
            if (value == 0) {
                return;
            }
            flags = Arrays.copyOf(flags, Math.max(slot + 1, flags.length * 2));
        }
        if (flags[slot] != value) {
            flags[slot] = value;
            dirtyFlags.set(slot);
        }
    }

    /**
     * Set or clear a section slot's hidden bit, noting the change.
     *
     * @param slot The slot.
     * @param hidden Whether it is hidden.
     */
    private void setHidden(int slot, boolean hidden) {
        final int word = slot / LodCullMath.BITS_PER_WORD;
        final int bit = 1 << (slot % LodCullMath.BITS_PER_WORD);
        final int old = word < hiddenWords.length ? hiddenWords[word] : 0;
        setWord(word, hidden ? old | bit : old & ~bit);
    }

    /**
     * Set a hidden section word, noting the change.
     *
     * @param word The word.
     * @param value Its bits.
     */
    private void setWord(int word, int value) {
        if (word >= hiddenWords.length) {
            if (value == 0) {
                return;
            }
            hiddenWords = Arrays.copyOf(hiddenWords, Math.max(word + 1, hiddenWords.length * 2));
        }
        if (hiddenWords[word] != value) {
            hiddenWords[word] = value;
            dirtyWords.set(word);
        }
    }

    /**
     * Upload waiting clusters, free released ones, and copy this frame's changed entries and flags
     * into the tables. Render thread, after the overrides are set.
     *
     * @param state The Vulkan state.
     * @param commandBuffer The frame's command buffer.
     */
    public void record(@NonNull VulkanState state, @NonNull VkCommandBuffer commandBuffer) {
        arena.record(state, commandBuffer);
        updateFlags();
        final DeviceTable.Entries flagEntries = new DeviceTable.Entries(WORD_SIZE);
        final DeviceTable.Entries wordEntries = new DeviceTable.Entries(WORD_SIZE);
        final DeviceTable.Entries unitEntries = new DeviceTable.Entries(WORD_SIZE);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            for (int slot = dirtyFlags.nextSetBit(0);
                    slot >= 0;
                    slot = dirtyFlags.nextSetBit(slot + 1)) {
                final ByteBuffer out = flagEntries.next(slot);
                out.putInt(out.position(), flags[slot]);
            }
            dirtyFlags.clear();
            for (int word = dirtyWords.nextSetBit(0);
                    word >= 0;
                    word = dirtyWords.nextSetBit(word + 1)) {
                final ByteBuffer out = wordEntries.next(word);
                out.putInt(out.position(), hiddenWords[word]);
            }
            dirtyWords.clear();
            for (int word = dirtyUnitWords.nextSetBit(0);
                    word >= 0;
                    word = dirtyUnitWords.nextSetBit(word + 1)) {
                final ByteBuffer out = unitEntries.next(word);
                out.putInt(out.position(), unitWords[word]);
            }
            dirtyUnitWords.clear();
            final int capacity = ranges.getCapacity();
            // Every slot handed out is covered, so the culling pass never reads past the end
            final int flagCapacity = Math.max(1, Math.max(flags.length, cellSlots.bound()));
            final int wordCapacity =
                    Math.max(
                            1,
                            Math.max(
                                    hiddenWords.length,
                                    (sectionSlots.bound() + LodCullMath.BITS_PER_WORD - 1)
                                            / LodCullMath.BITS_PER_WORD));
            final int unitCapacity =
                    Math.max(
                            1,
                            Math.max(
                                    unitWords.length,
                                    (unitSlots.bound() + LodCullMath.BITS_PER_WORD - 1)
                                            / LodCullMath.BITS_PER_WORD));
            final int pairCapacity = Math.max(1, pairRanges.getCapacity());
            final int brickCapacity = Math.max(1, brickWords.getCapacity());
            if (!clusters.hasWork(capacity, clusterEntries)
                    && !bricks.hasWork(brickCapacity, brickEntries)
                    && !pairs.hasWork(pairCapacity, pairEntries)
                    && !history.hasWork(capacity, historyEntries)
                    && !cellFlags.hasWork(flagCapacity, flagEntries)
                    && !hiddenSections.hasWork(wordCapacity, wordEntries)
                    && !shownUnits.hasWork(unitCapacity, unitEntries)) {
                return;
            }
            DeviceTable.barrierBefore(commandBuffer, stack);
            clusters.record(state, commandBuffer, capacity, clusterEntries, stack);
            history.record(state, commandBuffer, capacity, historyEntries, stack);
            pairs.record(state, commandBuffer, pairCapacity, pairEntries, stack);
            cellFlags.record(state, commandBuffer, flagCapacity, flagEntries, stack);
            hiddenSections.record(state, commandBuffer, wordCapacity, wordEntries, stack);
            shownUnits.record(state, commandBuffer, unitCapacity, unitEntries, stack);
            bricks.record(state, commandBuffer, brickCapacity, brickEntries, stack);
            DeviceTable.barrierAfter(commandBuffer, stack);
        } finally {
            unitEntries.free();
            wordEntries.free();
            flagEntries.free();
            clusterEntries.free();
            historyEntries.free();
            pairEntries.free();
            clusterEntries = new DeviceTable.Entries(LodCullMath.CLUSTER_ENTRY_SIZE);
            historyEntries = new DeviceTable.Entries(WORD_SIZE);
            pairEntries = new DeviceTable.Entries(LodCullMath.PAIR_ENTRY_SIZE);
            brickEntries.free();
            brickEntries = new DeviceTable.Entries(CoverageBricks.WORD_BYTES);
        }
    }

    /**
     * Write this frame's world table and clear its draw counts, after reading the counts the GPU
     * left there the last time this frame's data was used. Render thread, before anything culls or
     * draws.
     *
     * @param state The Vulkan state.
     * @param frameData The frame's data, which the GPU is done with.
     * @param camera The camera's position, in the world.
     * @param observer Where the cut is seen from, which may be frozen away from the camera.
     * @param heightPixels The height the scene is drawn at.
     * @param colors What the clusters are colored by, for debugging.
     */
    public void prepare(
            @NonNull VulkanState state,
            @NonNull PerFrameData frameData,
            @NonNull Vector3dc camera,
            @NonNull Observer observer,
            int heightPixels,
            DebugVisualizers.@NonNull LodColors colors) {
        frameData.lodCounts.ensureCapacity((long) COUNTER_COUNT * Integer.BYTES, state);
        final IntBuffer counts =
                MemoryUtil.memIntBuffer(
                        frameData.lodCounts.allocationInfo.pMappedData(), COUNTER_COUNT);
        if (frameData.lodWorldCount > 0) {
            stats =
                    new Stats(
                            counts.get(COUNTER_SELECTED),
                            counts.get(COUNTER_TRIANGLES),
                            counts.get(COUNTER_OUTSIDE),
                            counts.get(COUNTER_OCCLUDED),
                            counts.get(InstanceDrawUpdate.LIST_SCENE_EARLY)
                                    + counts.get(
                                            CUT_OUT_LISTS + InstanceDrawUpdate.LIST_SCENE_EARLY),
                            counts.get(InstanceDrawUpdate.LIST_SCENE_LATE)
                                    + counts.get(
                                            CUT_OUT_LISTS + InstanceDrawUpdate.LIST_SCENE_LATE),
                            levelCounts(counts, 0),
                            levelCounts(counts, 1),
                            levelCounts(counts, 2));
        }
        for (int i = 0; i < COUNTER_COUNT; ++i) {
            counts.put(i, 0);
        }

        // m11 is 1 / tan(fov / 2), negative with Vulkan's y flip
        final float pixelsPerUnit =
                Math.abs(observer.getProjectionMatrix().m11()) * heightPixels / 2;
        final Vector3dc eye = observer.getPosition();
        final List<World> all = snapshotWorlds();
        final List<Instance> placed = snapshotInstances();
        int slots = 0;
        int drawn = 0;
        for (World world : all) {
            slots = Math.max(slots, world.slot + 1);
            if (world.visible && world.shown && world.settled) {
                ++drawn;
            }
        }
        for (Instance instance : placed) {
            slots = Math.max(slots, instance.slot + 1);
        }
        final int capacity = ranges.getCapacity();
        final int pairCapacity = Math.max(1, pairRanges.getCapacity());
        frameData.lodCapacity = capacity;
        frameData.lodPairCapacity = pairCapacity;
        frameData.lodWorlds.ensureCapacity(
                (long) Math.max(1, slots) * LodCullMath.WORLD_ENTRY_SIZE, state);
        frameData.lodDrawCommands.ensureCapacity(
                ((long) Math.max(1, capacity) + pairCapacity) * LIST_SLOTS * COMMAND_SIZE, state);
        final ByteBuffer table =
                MemoryUtil.memByteBuffer(
                        frameData.lodWorlds.allocationInfo.pMappedData(),
                        Math.max(1, slots) * LodCullMath.WORLD_ENTRY_SIZE);
        MemoryUtil.memSet(MemoryUtil.memAddress(table), 0, table.capacity());
        for (World world : all) {
            world.eye =
                    new double[] {
                        eye.x() - world.origin[0],
                        eye.y() - world.origin[1],
                        eye.z() - world.origin[2]
                    };
            world.pixelsPerUnit = pixelsPerUnit;
            putWorld(
                    table,
                    world.slot * LodCullMath.WORLD_ENTRY_SIZE,
                    world,
                    camera,
                    eye,
                    world.visible && world.shown && world.settled,
                    (float) world.thresholdPixels,
                    pixelsPerUnit,
                    colors);
            if (world.modelClusters < 0) {
                world.eyes = new double[][] {world.eye};
            }
        }
        // Each instance's entry, and its model's observers for residency
        final Map<World, List<double[]>> modelEyes = new java.util.IdentityHashMap<>();
        final double[] from = {eye.x(), eye.y(), eye.z()};
        for (Instance instance : placed) {
            final World model = instance.model;
            final Placement placement = instance.placement;
            final double[] modelEye =
                    LodCullMath.modelEye(
                            from,
                            placement.position(),
                            placement.rotation(),
                            placement.scale(),
                            model.origin);
            final List<double[]> eyes = modelEyes.computeIfAbsent(model, key -> new ArrayList<>());
            if (!instance.visible) {
                continue;
            }
            eyes.add(modelEye);
            final Piece piece = model.pieces.get(WHOLE);
            if (piece == null || instance.firstPair < 0 || !model.settled) {
                continue;
            }
            ++drawn;
            putInstance(
                    table,
                    instance.slot * LodCullMath.WORLD_ENTRY_SIZE,
                    instance,
                    placement,
                    piece.first,
                    camera,
                    modelEye,
                    (float) model.thresholdPixels,
                    pixelsPerUnit,
                    colors);
            if (instance.shadowed != placement) {
                if (instance.shadowed != null) {
                    invalidate(instance, instance.shadowed);
                }
                invalidate(instance, placement);
                instance.shadowed = placement;
            }
        }
        for (World world : all) {
            if (world.modelClusters >= 0) {
                world.eyes = modelEyes.getOrDefault(world, List.of()).toArray(new double[0][]);
            }
        }
        frameData.lodWorldCount = drawn;
    }

    /**
     * Write an instance's entry in the world table, see {@link LodCullMath}.
     *
     * @param out The table.
     * @param base Where the entry starts.
     * @param instance The instance.
     * @param placement Where it is.
     * @param firstEntry Its model's first cluster entry.
     * @param camera The camera's position, in the world.
     * @param modelEye The observer in the model's frame, from its origin.
     * @param thresholdPixels The error threshold.
     * @param pixelsPerUnit Pixels per world unit at distance 1.
     * @param colors What its clusters are colored by, for debugging.
     */
    private static void putInstance(
            @NonNull ByteBuffer out,
            int base,
            @NonNull Instance instance,
            @NonNull Placement placement,
            int firstEntry,
            @NonNull Vector3dc camera,
            double @NonNull [] modelEye,
            float thresholdPixels,
            float pixelsPerUnit,
            DebugVisualizers.@NonNull LodColors colors) {
        // Where the model's origin lands
        final double[] origin =
                LodCullMath.instanceSphere(
                        new double[AXES + 1],
                        placement.position(),
                        placement.rotation(),
                        placement.scale(),
                        instance.model.origin);
        final double[] at = {camera.x(), camera.y(), camera.z()};
        for (int axis = 0; axis < AXES; ++axis) {
            out.putFloat(
                    base + LodCullMath.WORLD_RENDER_OFFSET_OFFSET + axis * Float.BYTES,
                    (float) (origin[axis] - at[axis]));
            out.putFloat(
                    base + LodCullMath.WORLD_EYE_OFFSET + axis * Float.BYTES,
                    (float) modelEye[axis]);
        }
        out.putFloat(
                base + LodCullMath.WORLD_RENDER_OFFSET_OFFSET + AXES * Float.BYTES,
                (float) placement.scale());
        out.putFloat(
                base + LodCullMath.WORLD_EYE_OFFSET + AXES * Float.BYTES,
                GraphicsManager.getSettings().lodShellPixels);
        out.putInt(base + LodCullMath.WORLD_INFO_OFFSET, colors.ordinal());
        out.putInt(base + LodCullMath.WORLD_INFO_OFFSET + Integer.BYTES, 1);
        out.putFloat(base + LodCullMath.WORLD_INFO_OFFSET + 2 * Integer.BYTES, thresholdPixels);
        out.putFloat(base + LodCullMath.WORLD_INFO_OFFSET + 3 * Integer.BYTES, pixelsPerUnit);
        final Quaterniond rotation = placement.rotation();
        out.putFloat(base + LodCullMath.WORLD_ROTATION_OFFSET, (float) rotation.x);
        out.putFloat(base + LodCullMath.WORLD_ROTATION_OFFSET + Float.BYTES, (float) rotation.y);
        out.putFloat(
                base + LodCullMath.WORLD_ROTATION_OFFSET + 2 * Float.BYTES, (float) rotation.z);
        out.putFloat(
                base + LodCullMath.WORLD_ROTATION_OFFSET + 3 * Float.BYTES, (float) rotation.w);
        final int[][] remaps = instance.remaps;
        out.putInt(base + LodCullMath.WORLD_MODEL_OFFSET, firstEntry + 1);
        out.putInt(
                base + LodCullMath.WORLD_MODEL_OFFSET + Integer.BYTES,
                instance.model.modelClusters);
        out.putInt(base + LodCullMath.WORLD_MODEL_OFFSET + 2 * Integer.BYTES, instance.firstPair);
        out.putInt(base + LodCullMath.WORLD_MODEL_OFFSET + 3 * Integer.BYTES, remaps.length);
        for (int i = 0; i < remaps.length; ++i) {
            out.putInt(
                    base + LodCullMath.WORLD_REMAP_FROM_OFFSET + i * Integer.BYTES, remaps[i][0]);
            out.putInt(base + LodCullMath.WORLD_REMAP_TO_OFFSET + i * Integer.BYTES, remaps[i][1]);
        }
    }

    /**
     * Write a world's entry in the world table.
     *
     * @param out The table.
     * @param base Where the entry starts.
     * @param world The world.
     * @param camera The camera's position, in the world.
     * @param eye The observer's position, in the world.
     * @param drawn Whether it is drawn.
     * @param thresholdPixels The world's error threshold.
     * @param pixelsPerUnit Pixels per world unit at distance 1.
     * @param colors What its clusters are colored by, for debugging.
     */
    private static void putWorld(
            @NonNull ByteBuffer out,
            int base,
            @NonNull World world,
            @NonNull Vector3dc camera,
            @NonNull Vector3dc eye,
            boolean drawn,
            float thresholdPixels,
            float pixelsPerUnit,
            DebugVisualizers.@NonNull LodColors colors) {
        final double[] at = {camera.x(), camera.y(), camera.z()};
        final double[] from = {eye.x(), eye.y(), eye.z()};
        for (int axis = 0; axis < AXES; ++axis) {
            final double relative = at[axis] - world.origin[axis];
            final double step = Math.floor(relative * LodVertex.STEPS_PER_UNIT);
            out.putFloat(
                    base + LodCullMath.WORLD_RENDER_OFFSET_OFFSET + axis * Float.BYTES,
                    (float) -relative);
            out.putFloat(
                    base + LodCullMath.WORLD_EYE_OFFSET + axis * Float.BYTES,
                    (float) (from[axis] - world.origin[axis]));
            out.putInt(
                    base + LodCullMath.WORLD_CAMERA_GRID_OFFSET + axis * Integer.BYTES, (int) step);
            out.putFloat(
                    base + LodCullMath.WORLD_CAMERA_FRACTION_OFFSET + axis * Float.BYTES,
                    (float) (relative - step / LodVertex.STEPS_PER_UNIT));
        }
        // How small a coverage volume must be on screen to be drawn as its shell
        out.putFloat(
                base + LodCullMath.WORLD_EYE_OFFSET + AXES * Float.BYTES,
                GraphicsManager.getSettings().lodShellPixels);
        out.putInt(base + LodCullMath.WORLD_INFO_OFFSET, colors.ordinal());
        out.putInt(base + LodCullMath.WORLD_INFO_OFFSET + Integer.BYTES, drawn ? 1 : 0);
        out.putFloat(base + LodCullMath.WORLD_INFO_OFFSET + 2 * Integer.BYTES, thresholdPixels);
        out.putFloat(base + LodCullMath.WORLD_INFO_OFFSET + 3 * Integer.BYTES, pixelsPerUnit);
    }

    /**
     * Whether a frame has any worlds to cull and draw.
     *
     * @param frameData The frame's data.
     * @return True if there are.
     */
    public static boolean hasDraws(@NonNull PerFrameData frameData) {
        return frameData.lodWorldCount > 0;
    }

    /**
     * Draw one list of the frame's clusters with a bound pipeline: as many commands as the culling
     * pass counted, in one call.
     *
     * @param commandBuffer The command buffer, inside the rendering, with the pipeline bound.
     * @param frameData The frame's data.
     * @param pipelineLayout The pipeline's layout.
     * @param worldsOffset Where the world table's address goes in its vertex push constants; the
     *     cluster table's goes right after it.
     * @param list Which list to draw, plus {@link #CUT_OUT_LISTS} for its cut out share or {@link
     *     #VOLUME_LISTS} for its volumes.
     */
    public void draw(
            @NonNull VkCommandBuffer commandBuffer,
            @NonNull PerFrameData frameData,
            long pipelineLayout,
            int worldsOffset,
            int list) {
        if (!hasDraws(frameData) || list < 0 || list >= LIST_SLOTS) {
            return;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            vkCmdBindIndexBuffer(commandBuffer, arena.getIndices().buffer, 0, VK_INDEX_TYPE_UINT32);
            vkCmdBindVertexBuffers(
                    commandBuffer, 0, stack.longs(arena.getVertices().buffer), stack.callocLong(1));
            final ByteBuffer constants = stack.malloc(2 * Long.BYTES);
            constants.putLong(0, frameData.lodWorlds.deviceAddress);
            constants.putLong(Long.BYTES, clusters.getBuffer().deviceAddress);
            vkCmdPushConstants(
                    commandBuffer,
                    pipelineLayout,
                    VK_SHADER_STAGE_VERTEX_BIT,
                    worldsOffset,
                    constants);
            vkCmdDrawIndexedIndirectCount(
                    commandBuffer,
                    frameData.lodDrawCommands.buffer,
                    (long) list
                            * (frameData.lodCapacity + frameData.lodPairCapacity)
                            * COMMAND_SIZE,
                    frameData.lodCounts.buffer,
                    (long) list * Integer.BYTES,
                    frameData.lodCapacity + frameData.lodPairCapacity,
                    COMMAND_SIZE);
        }
    }

    /**
     * Free everything. Render thread, with the device idle.
     *
     * @param state The Vulkan state.
     */
    public void cleanup(@NonNull VulkanState state) {
        lock.lock();
        try {
            worlds.clear();
            instances.clear();
        } finally {
            lock.unlock();
        }
        removedWorlds.clear();
        removedInstances.clear();
        pairEntries.free();
        pairEntries = new DeviceTable.Entries(LodCullMath.PAIR_ENTRY_SIZE);
        pairs.cleanup(state);
        clusterEntries.free();
        historyEntries.free();
        clusterEntries = new DeviceTable.Entries(LodCullMath.CLUSTER_ENTRY_SIZE);
        historyEntries = new DeviceTable.Entries(WORD_SIZE);
        shownUnits.cleanup(state);
        hiddenSections.cleanup(state);
        cellFlags.cleanup(state);
        history.cleanup(state);
        clusters.cleanup(state);
        bricks.cleanup(state);
        brickEntries.free();
        brickEntries = new DeviceTable.Entries(CoverageBricks.WORD_BYTES);
        brickRangeOf.clear();
        arena.cleanup(state);
    }
}
