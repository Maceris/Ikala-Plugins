package com.ikalagaming.graphics.vulkan;

import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK12.vkCmdDrawIndexedIndirectCount;

import com.ikalagaming.graphics.MeshHandle;
import com.ikalagaming.graphics.lod.ClusterMeshes;
import com.ikalagaming.graphics.lod.LiveLod;
import com.ikalagaming.graphics.lod.LodDag;
import com.ikalagaming.graphics.lod.LodStore;
import com.ikalagaming.graphics.lod.LodVertex;
import com.ikalagaming.graphics.scene.Observer;
import com.ikalagaming.graphics.vulkan.stages.InstanceDrawUpdate;

import lombok.Getter;
import lombok.NonNull;
import org.joml.Vector3dc;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VkCommandBuffer;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
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

    /** The counter of clusters in the cut, after one count per list. */
    public static final int COUNTER_SELECTED = LIST_COUNT;

    /** The counter of triangles in the cut. */
    public static final int COUNTER_TRIANGLES = COUNTER_SELECTED + 1;

    /** The counter of clusters in the cut outside the scene's frustum. */
    public static final int COUNTER_OUTSIDE = COUNTER_TRIANGLES + 1;

    /** The counter of clusters in the cut the occlusion test hid. */
    public static final int COUNTER_OCCLUDED = COUNTER_OUTSIDE + 1;

    /** How many counters there are. */
    public static final int COUNTER_COUNT = COUNTER_OCCLUDED + 1;

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

    /**
     * The clusters' geometry. -- GETTER -- The arena.
     *
     * @return The arena.
     */
    @Getter private final GeometryArena arena;

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

    /** The next world's id. */
    private long nextId = 1;

    /**
     * What the culling pass did, the last time its counts were read. -- GETTER -- Recent counts.
     *
     * @return The counts.
     */
    @Getter private volatile Stats stats = new Stats(0, 0, 0, 0, 0, 0);

    /**
     * What the culling pass did in a frame, over every world.
     *
     * @param selectedClusters Clusters in the cut.
     * @param selectedTriangles Triangles in the cut.
     * @param outside Clusters in the cut outside the scene's frustum.
     * @param occluded Clusters in the cut the occlusion test hid.
     * @param early Clusters the scene drew before the depth pyramid.
     * @param late Clusters the scene drew after it.
     */
    public record Stats(
            int selectedClusters,
            int selectedTriangles,
            int outside,
            int occluded,
            int early,
            int late) {}

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
     */
    public record LiveStats(
            int pieces,
            int pendingBatches,
            long batches,
            int staleCells,
            int hiddenSections,
            double lastBuildMillis,
            double lastLatencyMillis) {}

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

    /** One cell of a world on the GPU. */
    private static final class Piece {
        /** How many of its cell's first clusters aren't its own. */
        final int inputs;

        /** Where its clusters start in the tables. */
        final int first;

        /** Each cluster's mesh, or null if it has no triangles. */
        final MeshHandle[] meshes;

        /** Each cluster's section, or null above the leaves. */
        final LodStore.SectionPos[] sections;

        /**
         * Keep a piece.
         *
         * @param inputs Its cell's input count.
         * @param first Its first entry.
         * @param meshes Its meshes.
         * @param sections Its clusters' sections.
         */
        Piece(int inputs, int first, MeshHandle[] meshes, LodStore.SectionPos[] sections) {
            this.inputs = inputs;
            this.first = first;
            this.meshes = meshes;
            this.sections = sections;
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
        /** The plugin that owns it. */
        final String owner;

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

        /**
         * Keep a world.
         *
         * @param owner The owner.
         * @param origin The origin.
         * @param slot Its slot.
         * @param cellOf The level 0 cell a section is in, or null.
         * @param thresholdPixels Its error threshold.
         */
        World(
                String owner,
                double[] origin,
                int slot,
                Function<LodStore.SectionPos, LodStore.CellKey> cellOf,
                double thresholdPixels) {
            this.owner = owner;
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
        arena = new GeometryArena(state, LodVertex.SIZE);
        clusters =
                new DeviceTable(
                        state, "lod cluster", LodCullMath.CLUSTER_ENTRY_SIZE, INITIAL_CLUSTERS);
        history = new DeviceTable(state, "lod history", WORD_SIZE, INITIAL_CLUSTERS);
        cellFlags = new DeviceTable(state, "lod cell flags", WORD_SIZE, INITIAL_SLOTS);
        hiddenSections = new DeviceTable(state, "lod hidden sections", WORD_SIZE, INITIAL_SLOTS);
    }

    /**
     * Add a whole DAG as a world, uploading every cluster. Safe from any thread.
     *
     * @param state The Vulkan state.
     * @param owner The key of the plugin that owns it.
     * @param dag The world.
     * @param meshes Each cluster's mesh, by cluster id.
     * @param thresholdPixels The most error a drawn cluster may have, in pixels.
     * @return The world's id.
     */
    public long add(
            @NonNull VulkanState state,
            @NonNull String owner,
            @NonNull LodDag dag,
            @NonNull List<ClusterMeshes.Mesh> meshes,
            double thresholdPixels) {
        final long id = newWorld(owner, dag.getOrigin(), null, thresholdPixels);
        final List<LodStore.PieceCluster> clusterList = new ArrayList<>(meshes.size());
        for (int i = 0; i < meshes.size(); ++i) {
            final boolean consumed = dag.consumer(i) >= 0;
            clusterList.add(
                    new LodStore.PieceCluster(
                            i,
                            dag.clusters().get(i),
                            dag.parentError(i),
                            dag.parentBound(i),
                            consumed ? WHOLE : null,
                            null));
        }
        publish(
                state,
                id,
                new LiveLod.Batch(
                        List.of(new LiveLod.Piece(WHOLE, 0, clusterList, meshes)),
                        List.of(),
                        List.of(),
                        Collections.emptySortedSet(),
                        Map.of(),
                        0,
                        System.nanoTime()));
        return id;
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
        return newWorld(owner, origin, cellOf, thresholdPixels);
    }

    /**
     * Make a world.
     *
     * @param owner The owner.
     * @param origin Its origin.
     * @param cellOf The level 0 cell a section is in, or null for a lone DAG.
     * @param thresholdPixels Its error threshold.
     * @return Its id.
     */
    private long newWorld(
            @NonNull String owner,
            double @NonNull [] origin,
            Function<LodStore.SectionPos, LodStore.CellKey> cellOf,
            double thresholdPixels) {
        lock.lock();
        try {
            final long id = nextId++;
            worlds.put(
                    id,
                    new World(owner, origin.clone(), worldSlots.take(), cellOf, thresholdPixels));
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
                                    piece.key(), piece.inputs(), clusters, piece.meshes()));
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
            @NonNull VulkanState state, @NonNull String owner, @NonNull Uploading uploading) {
        long bytes = 0;
        while (!uploading.registered() && bytes < REGISTER_BYTES_PER_FRAME) {
            final List<ClusterMeshes.Mesh> pieceMeshes =
                    uploading.batch.added().get(uploading.piece).meshes();
            if (uploading.cluster >= pieceMeshes.size()) {
                uploading.piece += 1;
                uploading.cluster = 0;
                continue;
            }
            final ClusterMeshes.Mesh mesh = pieceMeshes.get(uploading.cluster);
            if (mesh.triangleCount() > 0) {
                uploading.meshes[uploading.piece][uploading.cluster] = register(state, owner, mesh);
                bytes +=
                        (long) mesh.vertexCount() * LodVertex.SIZE
                                + (long) mesh.indices().length * Integer.BYTES;
            }
            uploading.cluster += 1;
        }
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
     * Show or hide a world.
     *
     * @param id The world.
     * @param visible Whether it is drawn.
     */
    public void setVisible(long id, boolean visible) {
        final World world = world(id);
        if (world != null) {
            world.visible = visible;
        }
    }

    /**
     * Whether a world has every batch published so far on the GPU, and something to draw.
     *
     * @param id The world.
     * @return False while a batch is uploading, or there is no such world.
     */
    public boolean isResident(long id) {
        final World world = world(id);
        return world != null && world.shown && !world.busy && world.incoming.isEmpty();
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
                world.lastLatencyMillis);
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
        World removed;
        while ((removed = removedWorlds.poll()) != null) {
            for (Piece piece : removed.pieces.values()) {
                retire(removed, piece);
            }
            removed.pieces.clear();
            for (int slot : removed.cells.values()) {
                setFlag(slot, 0);
                cellSlots.give(slot);
            }
            removed.cells.clear();
            removed.incoming.clear();
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
            if (uploading == null) {
                continue;
            }
            if (!uploading.registered()) {
                registerSome(state, world.owner, uploading);
            } else if (isResident(uploading.meshes)) {
                // One batch a frame, so no entry is written twice in a frame
                world.uploading = null;
                world.busy = false;
                apply(world, uploading.batch, uploading.meshes);
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
                    arena.release(mesh);
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
        }
        for (LiveLod.Piece tops : batch.tops()) {
            final Piece piece = world.pieces.get(tops.key());
            if (piece == null) {
                continue;
            }
            for (LodStore.PieceCluster cluster : tops.clusters()) {
                final int index = cluster.local() - piece.inputs;
                writeEntry(world, tops.key(), piece, index, cluster);
            }
        }
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
        final LodStore.SectionPos[] sections = new LodStore.SectionPos[count];
        final Piece piece = new Piece(added.inputs(), first, meshes, sections);
        for (int i = 0; i < count; ++i) {
            final LodStore.SectionPos section = added.clusters().get(i).section();
            sections[i] = section;
            if (section != null) {
                final int[] slot = world.sections.get(section);
                if (slot == null) {
                    world.sections.put(section, new int[] {sectionSlots.take(), 1});
                } else {
                    slot[1] += 1;
                }
            }
            writeEntry(world, added.key(), piece, i, added.clusters().get(i));
            historyEntries.next(first + i);
        }
        world.pieces.put(added.key(), piece);
    }

    /**
     * Write one cluster's entry. Render thread.
     *
     * @param world The world.
     * @param key The cluster's cell.
     * @param piece The cell's piece.
     * @param index The cluster's index in the piece.
     * @param cluster The cluster, with its parent.
     */
    private void writeEntry(
            @NonNull World world,
            LodStore.@NonNull CellKey key,
            @NonNull Piece piece,
            int index,
            LodStore.@NonNull PieceCluster cluster) {
        final MeshHandle mesh = piece.meshes[index];
        final Integer cell = world.cells.get(key);
        final Integer parentCell =
                cluster.parentCell() == null ? null : world.cells.get(cluster.parentCell());
        final int[] section =
                cluster.section() == null ? null : world.sections.get(cluster.section());
        LodCullMath.putCluster(
                clusterEntries.next(piece.first + index),
                cluster.cluster(),
                cluster.parentError(),
                cluster.parentBound(),
                mesh == null ? LodCullMath.NO_MESH : mesh.slot(),
                mesh == null ? 0 : mesh.generation(),
                new LodCullMath.Place(
                        world.slot,
                        cell == null ? LodCullMath.NONE : cell,
                        parentCell == null ? LodCullMath.NONE : parentCell,
                        section == null ? LodCullMath.NONE : section[0]));
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
                arena.release(piece.meshes[i]);
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
     * thread, after {@link #applyReady()} and before {@link #record}.
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
            if (!clusters.hasWork(capacity, clusterEntries)
                    && !history.hasWork(capacity, historyEntries)
                    && !cellFlags.hasWork(flagCapacity, flagEntries)
                    && !hiddenSections.hasWork(wordCapacity, wordEntries)) {
                return;
            }
            DeviceTable.barrierBefore(commandBuffer, stack);
            clusters.record(state, commandBuffer, capacity, clusterEntries, stack);
            history.record(state, commandBuffer, capacity, historyEntries, stack);
            cellFlags.record(state, commandBuffer, flagCapacity, flagEntries, stack);
            hiddenSections.record(state, commandBuffer, wordCapacity, wordEntries, stack);
            DeviceTable.barrierAfter(commandBuffer, stack);
        } finally {
            wordEntries.free();
            flagEntries.free();
            clusterEntries.free();
            historyEntries.free();
            clusterEntries = new DeviceTable.Entries(LodCullMath.CLUSTER_ENTRY_SIZE);
            historyEntries = new DeviceTable.Entries(WORD_SIZE);
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
     */
    public void prepare(
            @NonNull VulkanState state,
            @NonNull PerFrameData frameData,
            @NonNull Vector3dc camera,
            @NonNull Observer observer,
            int heightPixels) {
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
                            counts.get(InstanceDrawUpdate.LIST_SCENE_EARLY),
                            counts.get(InstanceDrawUpdate.LIST_SCENE_LATE));
        }
        for (int i = 0; i < COUNTER_COUNT; ++i) {
            counts.put(i, 0);
        }

        // m11 is 1 / tan(fov / 2), negative with Vulkan's y flip
        final float pixelsPerUnit =
                Math.abs(observer.getProjectionMatrix().m11()) * heightPixels / 2;
        final Vector3dc eye = observer.getPosition();
        final List<World> all = snapshotWorlds();
        int slots = 0;
        int drawn = 0;
        for (World world : all) {
            slots = Math.max(slots, world.slot + 1);
            if (world.visible && world.shown) {
                ++drawn;
            }
        }
        final int capacity = ranges.getCapacity();
        frameData.lodWorldCount = drawn;
        frameData.lodCapacity = capacity;
        frameData.lodWorlds.ensureCapacity(
                (long) Math.max(1, slots) * LodCullMath.WORLD_ENTRY_SIZE, state);
        frameData.lodDrawCommands.ensureCapacity(
                (long) Math.max(1, capacity) * LIST_COUNT * COMMAND_SIZE, state);
        final ByteBuffer table =
                MemoryUtil.memByteBuffer(
                        frameData.lodWorlds.allocationInfo.pMappedData(),
                        Math.max(1, slots) * LodCullMath.WORLD_ENTRY_SIZE);
        MemoryUtil.memSet(MemoryUtil.memAddress(table), 0, table.capacity());
        for (World world : all) {
            putWorld(
                    table,
                    world.slot * LodCullMath.WORLD_ENTRY_SIZE,
                    world,
                    camera,
                    eye,
                    world.visible && world.shown,
                    (float) world.thresholdPixels,
                    pixelsPerUnit);
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
     */
    private static void putWorld(
            @NonNull ByteBuffer out,
            int base,
            @NonNull World world,
            @NonNull Vector3dc camera,
            @NonNull Vector3dc eye,
            boolean drawn,
            float thresholdPixels,
            float pixelsPerUnit) {
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
     * @param worldsOffset Where the world table's address goes in its vertex push constants.
     * @param list Which list to draw.
     */
    public void draw(
            @NonNull VkCommandBuffer commandBuffer,
            @NonNull PerFrameData frameData,
            long pipelineLayout,
            int worldsOffset,
            int list) {
        if (!hasDraws(frameData) || list < 0 || list >= LIST_COUNT) {
            return;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            vkCmdBindIndexBuffer(commandBuffer, arena.getIndices().buffer, 0, VK_INDEX_TYPE_UINT32);
            vkCmdBindVertexBuffers(
                    commandBuffer, 0, stack.longs(arena.getVertices().buffer), stack.callocLong(1));
            final ByteBuffer constants = stack.malloc(Long.BYTES);
            constants.putLong(0, frameData.lodWorlds.deviceAddress);
            vkCmdPushConstants(
                    commandBuffer,
                    pipelineLayout,
                    VK_SHADER_STAGE_VERTEX_BIT,
                    worldsOffset,
                    constants);
            vkCmdDrawIndexedIndirectCount(
                    commandBuffer,
                    frameData.lodDrawCommands.buffer,
                    (long) list * frameData.lodCapacity * COMMAND_SIZE,
                    frameData.lodCounts.buffer,
                    (long) list * Integer.BYTES,
                    frameData.lodCapacity,
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
        } finally {
            lock.unlock();
        }
        removedWorlds.clear();
        clusterEntries.free();
        historyEntries.free();
        clusterEntries = new DeviceTable.Entries(LodCullMath.CLUSTER_ENTRY_SIZE);
        historyEntries = new DeviceTable.Entries(WORD_SIZE);
        hiddenSections.cleanup(state);
        cellFlags.cleanup(state);
        history.cleanup(state);
        clusters.cleanup(state);
        arena.cleanup(state);
    }
}
