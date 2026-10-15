package com.ikalagaming.graphics.vulkan;

import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.InstanceHandle;
import com.ikalagaming.graphics.MeshHandle;
import com.ikalagaming.graphics.MeshKind;
import com.ikalagaming.graphics.Sections;
import com.ikalagaming.graphics.bake.BakeSource;
import com.ikalagaming.graphics.bake.BakedVertex;
import com.ikalagaming.graphics.bake.SectionBaker;
import com.ikalagaming.graphics.graph.Material;
import com.ikalagaming.graphics.graph.MaterialCache;
import com.ikalagaming.graphics.graph.MeshData;
import com.ikalagaming.graphics.graph.Model;
import com.ikalagaming.graphics.graph.OccupancyClipmap;
import com.ikalagaming.graphics.graph.VolumeMaterials;
import com.ikalagaming.graphics.graph.VolumeMath;
import com.ikalagaming.graphics.graph.VolumeVoxelizer;
import com.ikalagaming.graphics.lod.DagBuilder;
import com.ikalagaming.graphics.lod.LiveLod;
import com.ikalagaming.graphics.lod.LodCache;
import com.ikalagaming.graphics.lod.LodMesh;
import com.ikalagaming.graphics.lod.LodStore;
import com.ikalagaming.graphics.lod.PlanarMerge;
import com.ikalagaming.graphics.scene.Observer;
import com.ikalagaming.graphics.scene.Scene;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.IntPredicate;

/**
 * Bakes sections on worker threads and keeps each one in the scene: its baked meshes, one per
 * transparency that has anything, placed as one instance at the section's origin.
 *
 * <p>Baking is plain CPU work over the placements, done on a small pool of daemon threads. Its
 * result comes back to the render thread, which registers the meshes in the baked geometry buffers,
 * and once they are all resident, places the new instance and takes the old one out. So a rebaked
 * section never disappears for a frame: the old one is drawn until the new one can be.
 *
 * <p>A plugin can have its sections feed a live level of detail world ({@link #startLive}). Its
 * sections' opaque blocks are then drawn by level of detail, each finished bake going to the
 * world's {@link LiveLod}, and only their cutout and translucent blocks are placed here. While a
 * section near the observer has a bake newer than the level of detail shows, as right after an
 * edit, it is an override: it draws its own opaque blocks, and the level of detail hides its leaves
 * and keeps coarser clusters off it, so the edit shows as soon as the bake is resident.
 *
 * <p>A bake of a section near the observer, as after an edit, is urgent: rather than wait for a
 * worker, the render thread bakes it at the start of the next frame, within a time budget, and the
 * frame records its upload before swapping it in, so the edit is drawn in that frame.
 *
 * <p>Submitting, rebaking and removing are safe from any thread. {@link #registerFinished} and then
 * {@link #update} run on the render thread at the start of each frame, with the baked geometry's
 * uploads recorded between them.
 */
@Slf4j
public class SectionManager {

    /**
     * Everything a bake needs, resolved on the thread that submitted it.
     *
     * @param origin The section's world position.
     * @param sources The mesh each placement puts down.
     * @param faceMaterials {@link SectionBaker#FACE_KINDS} material indices per placement, see
     *     {@link SectionBaker#bakeByFace}.
     * @param packedPositions Each placement's cell.
     * @param rotations Each placement's rotation.
     * @param faceMasks The faces each placement's neighbors hide.
     * @param userData Each placement's user data.
     */
    public record Request(
            @NonNull Vector3dc origin,
            @NonNull BakeSource @NonNull [] sources,
            int @NonNull [] faceMaterials,
            int @NonNull [] packedPositions,
            byte @NonNull [] rotations,
            byte @NonNull [] faceMasks,
            short @NonNull [] userData) {}

    /**
     * A bake that finished, waiting for the render thread.
     *
     * @param id The section.
     * @param generation Which of the section's bakes it is.
     * @param origin Where the section goes.
     * @param result The baked meshes.
     * @param brick For a section on the section grid, the volume brick of its cut out and
     *     translucent blocks, or null if it has none; otherwise null.
     * @param materialsVersion Which snapshot of the materials the brick was voxelized with.
     */
    private record Baked(
            long id,
            long generation,
            @NonNull Vector3dc origin,
            @NonNull SectionBaker.Result result,
            VolumeVoxelizer.Brick brick,
            int materialsVersion) {}

    /**
     * One bake of a section in the GPU's buffers.
     *
     * @param generation Which of the section's bakes it is.
     * @param origin Where it goes.
     * @param model The model holding its meshes, which the instance places: all of them, or for a
     *     section feeding level of detail, all but the opaque one.
     * @param opaqueModel For a section feeding level of detail, the model holding its opaque mesh,
     *     placed only while it is an override; otherwise null.
     * @param nearModel For a section on the section grid, the model holding its cut out and
     *     translucent meshes, placed only while it is within the volume distance, past which its
     *     volume brick stands in for them; otherwise null.
     * @param meshes Its meshes on the GPU.
     * @param buckets Each bucket that had anything, by transparency, its mesh null while it is off
     *     the GPU.
     * @param offGpu The buckets whose meshes are off the GPU, too far away to be needed: kept off
     *     at upload, or taken off since. Only the opaque model's and the near model's are.
     * @param triangles How many triangles it has.
     * @param nearTriangles How many of them the near model has.
     * @param brickMissing Whether its brick met a material the snapshot didn't have yet, so it is
     *     voxelized again once one does.
     * @param materialsVersion Which snapshot of the materials its brick was voxelized with.
     */
    private record Version(
            long generation,
            @NonNull Vector3dc origin,
            @NonNull Model model,
            Model opaqueModel,
            Model nearModel,
            @NonNull List<MeshHandle> meshes,
            @NonNull Map<Material.Transparency, MeshData> buckets,
            @NonNull Set<Material.Transparency> offGpu,
            int triangles,
            int nearTriangles,
            boolean brickMissing,
            int materialsVersion) {}

    /** A section's state. Guarded by {@link #lock}. */
    private static final class Section {
        /** Its id. */
        final long id;

        /** The owning plugin's key. */
        final String owner;

        /** The newest bake asked for. */
        long requested;

        /** The bake that is drawn, or null before the first is resident. */
        Version current;

        /** The newest bake whose meshes are uploading, or null. */
        Version pending;

        /** The instance placing the current bake, or null. */
        InstanceHandle instance;

        /** The instance placing the current bake's opaque blocks while it is an override. */
        InstanceHandle opaqueInstance;

        /** The instance placing the current bake's near model while it is near, or null. */
        InstanceHandle nearInstance;

        /** Whether it drew its own cut out and translucent blocks last frame. */
        boolean near;

        /** The pending bake's brick, put in the volume bricks as the bake swaps in. */
        VolumeVoxelizer.Brick pendingBrick;

        /**
         * Whether meshes kept off the GPU are being baked again on the workers, as it comes near.
         */
        boolean restoring;

        /** Whether its brick is being voxelized again, for a material it met too early. */
        boolean rebricking;

        /** What it was last asked to bake, to bake again when it starts feeding level of detail. */
        Request request;

        /** Where it is, in sections, or null if it isn't on the section grid. */
        LodStore.SectionPos position;

        /** Whether the last bake couldn't be uploaded, as when the buffers are full. */
        boolean failed;

        /** Whether it was removed, so checks still queued for it are dropped. */
        boolean removed;

        /** Which of its queued checks is the live one; older ones are dropped. */
        long checkStamp;

        /** Whether its brick is drawn for its cut out and translucent blocks. */
        boolean bricked;

        /** What it adds to {@link #placedTotal}. */
        long placed;

        Section(long id, String owner) {
            this.id = id;
            this.owner = owner;
        }
    }

    /**
     * When a section is next checked for what its distance from the observer decides, as how far
     * the observer will have traveled by then: no threshold can be crossed sooner.
     *
     * @param at The observer's travel, in meters, it is due at.
     * @param order When it was queued, to break ties in order.
     * @param section The section.
     * @param stamp The section's check stamp when it was queued.
     */
    private record Check(double at, long order, @NonNull Section section, long stamp) {}

    /** How a worker thread is named. */
    private static final String WORKER_NAME = "Section baker ";

    /** The size of a section, in blocks: the grid live level of detail cells are built on. */
    private static final int SECTION_SIZE = Sections.SECTION_SIZE;

    /** Coordinates in a position. */
    private static final int AXES = 3;

    /** Guards the sections. */
    private final ReentrantLock lock = new ReentrantLock();

    /** Every live section, by id. */
    private final Map<Long, Section> sections = new HashMap<>();

    /** Where changed sections are reported, for cached shadow maps. */
    private final ShadowInvalidation shadows;

    /** Each section on the section grid, by position. Guarded by {@link #lock}. */
    private final Map<LodStore.SectionPos, Section> byPosition = new HashMap<>();

    /**
     * A baked mesh that casts point light shadows.
     *
     * @param origin Its section's corner, in the world.
     * @param mesh The mesh, opaque or cut out, in the baked geometry buffers.
     */
    public record Caster(@NonNull Vector3dc origin, @NonNull MeshHandle mesh) {}

    /** Finished bakes, for the render thread. */
    private final Queue<Baked> baked = new ConcurrentLinkedQueue<>();

    /**
     * A section's meshes baked again, to put back on the GPU.
     *
     * @param id The section.
     * @param generation Which of its bakes they are.
     * @param kinds The buckets to put back.
     * @param result The bake.
     */
    private record Restored(
            long id,
            long generation,
            @NonNull Set<Material.Transparency> kinds,
            @NonNull SectionBaker.Result result) {}

    /** Meshes baked again, for the render thread. */
    private final Queue<Restored> restored = new ConcurrentLinkedQueue<>();

    /**
     * A section's brick voxelized again.
     *
     * @param id The section.
     * @param generation Which of its bakes it is.
     * @param brick The brick, or null if it has nothing to stand in for.
     * @param materialsVersion Which snapshot of the materials it was voxelized with.
     */
    private record Rebricked(
            long id, long generation, VolumeVoxelizer.Brick brick, int materialsVersion) {}

    /** Bricks voxelized again, for the render thread. */
    private final Queue<Rebricked> rebricked = new ConcurrentLinkedQueue<>();

    /** The materials' colors the workers voxelize bricks with, published by the render thread. */
    private volatile VolumeMaterials volumeMaterials = VolumeMaterials.NONE;

    /**
     * How far past its reach, in meters, a section's meshes that are only needed nearby are taken
     * off the GPU: its cut out and translucent ones past the end of the hand over to volume bricks,
     * once its brick is up to date, and its opaque one, for a section feeding level of detail, past
     * {@link #OPAQUE_REACH}, unless it is an override.
     */
    private static final double RELEASE_MARGIN = 32;

    /**
     * How far past its reach, in meters, a section's meshes that were kept off the GPU are baked
     * again, ahead of being needed, and how far past it they are uploaded at all: less than {@link
     * #RELEASE_MARGIN}, so one at the edge doesn't go back and forth.
     */
    private static final double RESTORE_MARGIN = 16;

    /**
     * How far from the observer, along any axis, a section feeding level of detail needs its opaque
     * mesh on the GPU, in meters: as far as the occupancy volume's coarsest level reaches, which
     * voxelizes the meshes, with the steps it lags the observer by.
     */
    private static final double OPAQUE_REACH =
            OccupancyClipmap.SIZE * OccupancyClipmap.VOXEL_SIZES[OccupancyClipmap.LEVELS - 1] / 2.0
                    + OccupancyClipmap.SNAP
                            * OccupancyClipmap.VOXEL_SIZES[OccupancyClipmap.LEVELS - 1];

    /** Meshes of removed sections and dropped bakes, to release on the render thread. */
    private final Queue<MeshHandle> releasing = new ConcurrentLinkedQueue<>();

    /**
     * An instance to take out, with the plugin that placed it, which the instance registry checks.
     *
     * @param owner The owning plugin's key.
     * @param instance The instance.
     */
    private record Removal(@NonNull String owner, @NonNull InstanceHandle instance) {}

    /** Instances of removed and replaced bakes, to take out on the render thread. */
    private final Queue<Removal> removing = new ConcurrentLinkedQueue<>();

    /** The next section's id. */
    private final AtomicLong nextId = new AtomicLong(1);

    /**
     * A plugin's live level of detail world.
     *
     * @param world Its id in the {@link LodRenderer}.
     * @param lod Its builder.
     * @param removed Sections taken out and the generation that took them, until the level of
     *     detail shows them gone. Guarded by {@link #lock}.
     */
    private record Live(
            long world, @NonNull LiveLod lod, @NonNull Map<LodStore.SectionPos, Long> removed) {}

    /**
     * The sections with a pending bake, so swapping them in doesn't look through every section.
     * Guarded by {@link #lock}.
     */
    private final Set<Section> pendingSections = new LinkedHashSet<>();

    /** Each plugin's live world, by owner. Guarded by {@link #lock}. */
    private final Map<String, Live> live = new HashMap<>();

    /** Owners already warned about sections off the section grid. Guarded by {@link #lock}. */
    private final Set<String> warnedOffGrid = new HashSet<>();

    /** How many sections were overrides last frame. */
    private volatile int overrideCount;

    /**
     * How much further than the volume distance a section drawing its own cut out and translucent
     * blocks keeps drawing them, in meters, so one at the edge doesn't swap every frame.
     */
    private static final double NEAR_HYSTERESIS = 8;

    /** Sections removed, whose bricks go. Guarded by {@link #lock}. */
    private final Set<LodStore.SectionPos> volumeRemovals = new HashSet<>();

    /**
     * Sections whose bricks stand in for some or all of their cut out and translucent blocks, a new
     * list whenever they change, so the same list means the same sections.
     */
    private volatile List<LodStore.SectionPos> volumeSections = List.of();

    /** The sections whose bricks are drawn. Guarded by {@link #lock}. */
    private final Set<LodStore.SectionPos> bricked = new LinkedHashSet<>();

    /** Whether {@link #bricked} changed since {@link #volumeSections} was made. */
    private boolean brickedChanged;

    /** How many triangles the resident bakes have, less those bricks stood in for last frame. */
    private volatile long placedTriangles;

    /** The sections' {@link Section#placed} added up. Guarded by {@link #lock}. */
    private long placedTotal;

    /** How many sections have a resident bake. Guarded by {@link #lock}. */
    private int residentCount;

    /** How many triangles the resident bakes have. Guarded by {@link #lock}. */
    private long residentTriangles;

    /** How many sections' last bakes couldn't be uploaded. Guarded by {@link #lock}. */
    private int failedCount;

    /**
     * Sections feeding level of detail whose drawn bake may be newer than the level of detail
     * shows; once it shows it, which only ever catches up, they leave. Guarded by {@link #lock}.
     */
    private final Set<Section> behind = new HashSet<>();

    /** Sections that are overrides now. Guarded by {@link #lock}. */
    private final Set<Section> overriding = new HashSet<>();

    /**
     * Sections on the section grid due a check of what their distance from the observer decides,
     * soonest first. Render thread, guarded by {@link #lock}.
     */
    private final PriorityQueue<Check> checks =
            new PriorityQueue<>(
                    Comparator.comparingDouble(Check::at).thenComparingLong(Check::order));

    /** How many queued checks per section, stale ones included, before stale ones are dropped. */
    private static final int STALE_CHECKS_FACTOR = 2;

    /** How many sections were checked last frame. */
    private volatile int lastChecked;

    /** How many checks were queued after last frame's, stale ones included. */
    private volatile int queuedChecks;

    /** How many checks were ever queued, for their order. */
    private long checkCount;

    /** How far the observer has moved in all, in meters, the clock checks are due by. */
    private double travel;

    /** Where the observer was when {@link #travel} was last added to, or null. */
    private Vector3d traveledFrom;

    /** The volume distance the checks were last made with, so all are made again if it changes. */
    private double checkedVolumeDistance = Double.NaN;

    /** The materials snapshot the checks were last made with, for bricks that met one too early. */
    private int checkedMaterials;

    /**
     * How far the observer moves before a section waiting on something else, such as its brick
     * coming in, is checked again: not at all, so it is checked every frame until it settles.
     */
    private static final double RETRY = 0;

    /**
     * A bake for the render thread to do at the start of the next frame.
     *
     * @param id The section.
     * @param generation Which bake it is.
     * @param request What to bake.
     * @param feeds The live level of detail world its opaque blocks go to, or null.
     * @param position Where it is, in sections.
     */
    private record Urgent(
            long id,
            long generation,
            @NonNull Request request,
            LiveLod feeds,
            LodStore.SectionPos position) {}

    /** Urgent bakes waiting for the next frame. */
    private final Queue<Urgent> urgent = new ConcurrentLinkedQueue<>();

    /**
     * How long the render thread may spend on urgent bakes in a frame; the rest go to the workers.
     */
    private static final long URGENT_NANOS_PER_FRAME = 4_000_000L;

    /** Where the observer was last frame, for deciding which bakes are urgent. */
    private volatile Vector3dc lastEye;

    /** How long the last urgent bake took, in nanoseconds. */
    private volatile long lastUrgentNanos;

    /** How many sections feeding level of detail it didn't show at their newest bake last frame. */
    private volatile int lodBehind;

    /** Bakes submitted and not yet back. */
    private final AtomicInteger inFlight = new AtomicInteger();

    /** The worker threads. */
    private final ExecutorService workers;

    /** Create the manager and its worker threads. */
    public SectionManager() {
        this(new ShadowInvalidation());
    }

    /**
     * Set up the manager, reporting where sections change to cached shadow maps.
     *
     * @param shadows Where changed sections are reported.
     */
    public SectionManager(@NonNull ShadowInvalidation shadows) {
        this.shadows = shadows;
        final int threads = Math.max(1, Runtime.getRuntime().availableProcessors() / 2);
        final AtomicInteger counter = new AtomicInteger();
        workers =
                Executors.newFixedThreadPool(
                        threads,
                        task -> {
                            Thread thread =
                                    new Thread(task, WORKER_NAME + counter.incrementAndGet());
                            thread.setDaemon(true);
                            return thread;
                        });
    }

    /**
     * Start baking a new section.
     *
     * @param owner The owning plugin's key.
     * @param request What to bake.
     * @return The section's id.
     */
    public long submit(@NonNull String owner, @NonNull Request request) {
        final long id = nextId.getAndIncrement();
        lock.lock();
        try {
            final Section section = new Section(id, owner);
            section.position = sectionPos(owner, request.origin());
            sections.put(id, section);
            if (section.position != null) {
                byPosition.put(section.position, section);
                if (live.containsKey(owner)) {
                    behind.add(section);
                }
            }
        } finally {
            lock.unlock();
        }
        schedule(id, owner, request);
        return id;
    }

    /**
     * Bake a section again, as after an edit. The current bake stays drawn until this one is.
     *
     * @param id The section.
     * @param owner The key of the plugin asking, which must own it.
     * @param request What to bake.
     * @return False if the section is gone or someone else's.
     */
    public boolean rebake(long id, @NonNull String owner, @NonNull Request request) {
        lock.lock();
        try {
            Section section = sections.get(id);
            if (section == null || !section.owner.equals(owner)) {
                return false;
            }
        } finally {
            lock.unlock();
        }
        schedule(id, owner, request);
        return true;
    }

    /**
     * Hand a bake to the workers.
     *
     * @param id The section.
     * @param owner Its owner.
     * @param request What to bake.
     */
    private void schedule(long id, @NonNull String owner, @NonNull Request request) {
        final long generation;
        final LiveLod feeds;
        final LodStore.SectionPos position;
        lock.lock();
        try {
            Section section = sections.get(id);
            if (section == null) {
                return;
            }
            generation = ++section.requested;
            section.request = request;
            position = section.position;
            final Live world = live.get(owner);
            feeds = world == null || position == null ? null : world.lod();
        } finally {
            lock.unlock();
        }
        if (feeds != null) {
            feeds.expect(position, generation);
        }
        inFlight.incrementAndGet();
        final Vector3dc eye = lastEye;
        if (eye != null
                && distance(eye, request.origin())
                        <= GraphicsManager.getSettings().lodOverrideDistance) {
            urgent.add(new Urgent(id, generation, request, feeds, position));
            return;
        }
        try {
            workers.execute(() -> bake(id, generation, request, feeds, position));
        } catch (RejectedExecutionException e) {
            inFlight.decrementAndGet();
            if (feeds != null) {
                feeds.abandon(position, generation);
            }
            log.debug("Not baking section {} of {}, the renderer is shutting down", id, owner);
        }
    }

    /**
     * Bake on a worker thread.
     *
     * @param id The section.
     * @param generation Which bake this is.
     * @param request What to bake.
     * @param feeds The live level of detail world its opaque blocks go to, or null.
     * @param position Where it is, in sections, when it feeds one.
     */
    private void bake(
            long id,
            long generation,
            @NonNull Request request,
            LiveLod feeds,
            LodStore.SectionPos position) {
        try {
            SectionBaker.Result result =
                    SectionBaker.bakeByFace(
                            request.sources(),
                            request.faceMaterials(),
                            request.packedPositions(),
                            request.rotations(),
                            request.faceMasks(),
                            request.userData());
            if (feeds != null) {
                final SectionBaker.Bucket opaque = result.get(Material.Transparency.OPAQUE);
                final Vector3dc origin = request.origin();
                feeds.put(
                        position,
                        opaque == null
                                ? null
                                : PlanarMerge.merge(
                                        LodMesh.fromBaked(
                                                opaque,
                                                new double[] {origin.x(), origin.y(), origin.z()},
                                                texturedMaterials())),
                        generation);
            }
            final VolumeMaterials table = volumeMaterials;
            baked.add(
                    new Baked(
                            id,
                            generation,
                            new Vector3d(request.origin()),
                            result,
                            position == null ? null : voxelize(result, table),
                            table.version()));
        } catch (RuntimeException e) {
            log.error("Baking section {} failed", id, e);
            if (feeds != null) {
                feeds.abandon(position, generation);
            }
        } finally {
            inFlight.decrementAndGet();
        }
    }

    /**
     * Which material indices level of detail keeps texture coordinates and normals for, rather than
     * projecting their textures: those whose material isn't triplanar. Asks the scene's material
     * cache, which is safe from any thread, once per material.
     *
     * @return The test.
     */
    private static IntPredicate texturedMaterials() {
        final Scene scene = GraphicsManager.getScene();
        if (scene == null) {
            return material -> false;
        }
        final MaterialCache cache = scene.getMaterialCache();
        final Map<Integer, Boolean> known = new HashMap<>();
        return material ->
                known.computeIfAbsent(material, index -> !cache.getMaterial(index).isTriplanar());
    }

    /**
     * Voxelize a bake's cut out and translucent blocks into a volume brick.
     *
     * @param result The bake.
     * @param table The materials' colors.
     * @return The brick, or null if it has nothing to stand in for.
     */
    private static VolumeVoxelizer.Brick voxelize(
            SectionBaker.@NonNull Result result, @NonNull VolumeMaterials table) {
        final List<VolumeVoxelizer.Input> inputs = new ArrayList<>();
        final SectionBaker.Bucket cutout = result.get(Material.Transparency.CUTOUT);
        if (cutout != null) {
            inputs.add(new VolumeVoxelizer.Input(cutout, false));
        }
        final SectionBaker.Bucket translucent = result.get(Material.Transparency.TRANSLUCENT);
        if (translucent != null) {
            inputs.add(new VolumeVoxelizer.Input(translucent, true));
        }
        return VolumeVoxelizer.voxelize(inputs, table);
    }

    /**
     * Publish the materials' colors for bricks voxelized from now on. A brick that met a material
     * missing from the last snapshot is voxelized again. Render thread.
     *
     * @param materials The snapshot.
     */
    public void publishMaterials(@NonNull VolumeMaterials materials) {
        volumeMaterials = materials;
    }

    /**
     * Remove a section. Its instance and meshes go at the start of the next frame.
     *
     * @param id The section.
     * @param owner The key of the plugin asking, which must own it.
     * @return False if it was already gone or is someone else's.
     */
    public boolean remove(long id, @NonNull String owner) {
        lock.lock();
        try {
            Section section = sections.get(id);
            if (section == null || !section.owner.equals(owner)) {
                return false;
            }
            sections.remove(id);
            if (section.position != null) {
                byPosition.remove(section.position, section);
            }
            if (section.current != null) {
                shadows.addSection(section.current.origin());
            }
            if (section.position != null) {
                volumeRemovals.add(section.position);
            }
            discard(section);
            forget(section);
            final Live world = live.get(owner);
            if (world != null && section.position != null) {
                final long generation = section.requested + 1;
                world.lod().remove(section.position, generation);
                world.removed().put(section.position, generation);
            }
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Forget every section a plugin owns. Its instances and meshes are removed by owner elsewhere
     * when a plugin unloads, so they are only forgotten here.
     *
     * @param owner The plugin's key.
     * @return How many sections were forgotten.
     */
    public int removeAllOwnedBy(@NonNull String owner) {
        lock.lock();
        try {
            int before = sections.size();
            sections.values()
                    .removeIf(
                            section -> {
                                if (!section.owner.equals(owner)) {
                                    return false;
                                }
                                if (section.position != null) {
                                    byPosition.remove(section.position, section);
                                    volumeRemovals.add(section.position);
                                }
                                discard(section);
                                // What it drew is gone from shadow maps and the occupancy too
                                if (section.current != null) {
                                    shadows.addSection(section.current.origin());
                                }
                                return true;
                            });
            final Live world = live.remove(owner);
            if (world != null) {
                world.lod().close();
            }
            warnedOffGrid.remove(owner);
            return before - sections.size();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Take a removed section out of the totals and sets kept about it. Hold the lock.
     *
     * @param section The section.
     */
    private void discard(@NonNull Section section) {
        section.removed = true;
        if (section.current != null) {
            --residentCount;
            residentTriangles -= section.current.triangles();
        }
        if (section.failed) {
            --failedCount;
        }
        placedTotal -= section.placed;
        section.placed = 0;
        setBricked(section, false);
        behind.remove(section);
        overriding.remove(section);
    }

    /**
     * Queue up taking a removed section's instance and meshes out. Hold the lock.
     *
     * @param section The section.
     */
    private void forget(@NonNull Section section) {
        if (section.instance != null) {
            removing.add(new Removal(section.owner, section.instance));
        }
        if (section.opaqueInstance != null) {
            removing.add(new Removal(section.owner, section.opaqueInstance));
        }
        if (section.nearInstance != null) {
            removing.add(new Removal(section.owner, section.nearInstance));
        }
        if (section.current != null) {
            releasing.addAll(section.current.meshes());
        }
        if (section.pending != null) {
            releasing.addAll(section.pending.meshes());
        }
        pendingSections.remove(section);
    }

    /**
     * Whether a section has a bake being drawn.
     *
     * @param id The section.
     * @return True once its first bake is resident and placed, until it is removed.
     */
    public boolean isResident(long id) {
        lock.lock();
        try {
            Section section = sections.get(id);
            return section != null && section.current != null;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Whether a section still exists.
     *
     * @param id The section.
     * @return False once it is removed.
     */
    public boolean isValid(long id) {
        lock.lock();
        try {
            return sections.containsKey(id);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Do this frame's urgent bakes, within a budget, and register every finished bake's meshes in
     * the baked geometry, so the frame records their uploads. Render thread only, at the start of a
     * frame, before the baked geometry is recorded.
     *
     * @param state The Vulkan state.
     */
    public void registerFinished(@NonNull VulkanState state) {
        final long start = System.nanoTime();
        Urgent next;
        while ((next = urgent.poll()) != null) {
            if (System.nanoTime() - start > URGENT_NANOS_PER_FRAME) {
                final Urgent later = next;
                try {
                    workers.execute(
                            () ->
                                    bake(
                                            later.id(),
                                            later.generation(),
                                            later.request(),
                                            later.feeds(),
                                            later.position()));
                } catch (RejectedExecutionException e) {
                    inFlight.decrementAndGet();
                }
                continue;
            }
            final long bakeStart = System.nanoTime();
            bake(next.id(), next.generation(), next.request(), next.feeds(), next.position());
            lastUrgentNanos = System.nanoTime() - bakeStart;
        }
        Baked done;
        while ((done = baked.poll()) != null) {
            upload(state, done);
        }
        Restored back;
        while ((back = restored.poll()) != null) {
            restore(state, back);
        }
        Rebricked brick;
        while ((brick = rebricked.poll()) != null) {
            rebrick(state, brick);
        }
    }

    /**
     * How long the last bake done on the render thread took.
     *
     * @return The time, in nanoseconds, or 0 if there hasn't been one.
     */
    public long getLastUrgentNanos() {
        return lastUrgentNanos;
    }

    /**
     * Swap in the bakes that are resident, including those whose uploads this frame recorded, and
     * decide which sections feeding level of detail are overrides. Render thread only, at the start
     * of a frame, after the baked geometry is recorded and before the instance table is.
     *
     * @param state The Vulkan state.
     * @param observer Where the scene is seen from, for overrides.
     */
    public void update(@NonNull VulkanState state, @NonNull Observer observer) {
        // Before swapping, so a section put back where one was removed keeps its new brick
        lock.lock();
        try {
            if (state.volumeBricks != null) {
                for (LodStore.SectionPos position : volumeRemovals) {
                    state.volumeBricks.remove(position);
                }
            }
            volumeRemovals.clear();
        } finally {
            lock.unlock();
        }
        swapResident(state);
        updateOverrides(state, observer);
        updateNear(state, observer);
        // After swapping, so a replaced bake goes in the same frame its replacement comes in
        Removal removal;
        while ((removal = removing.poll()) != null) {
            final int slot =
                    state.instances.getRegistry().remove(removal.owner(), removal.instance());
            if (slot >= 0) {
                state.instances.retire(slot);
            }
        }
        MeshHandle mesh;
        while ((mesh = releasing.poll()) != null) {
            state.bakedGeometry.release(mesh);
        }
    }

    /**
     * Every mesh of the section drawn now at a corner of the section grid, opaque, cut out and
     * translucent, for the occupancy volume. Render thread.
     *
     * @param corner The section's corner, in the world.
     * @return Its meshes with its corner, empty if there is none there or it isn't drawn yet.
     */
    public List<Caster> meshesAt(double @NonNull [] corner) {
        final LodStore.SectionPos position =
                new LodStore.SectionPos(
                        (int) Math.floor(corner[0] / SECTION_SIZE),
                        (int) Math.floor(corner[1] / SECTION_SIZE),
                        (int) Math.floor(corner[2] / SECTION_SIZE));
        lock.lock();
        try {
            final Section section = byPosition.get(position);
            if (section == null || section.current == null) {
                return List.of();
            }
            final List<Caster> out = new ArrayList<>();
            for (MeshHandle mesh : section.current.meshes()) {
                out.add(new Caster(section.current.origin(), mesh));
            }
            return out;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Whether a section's brick stands in for some of its cut out or translucent meshes off the
     * GPU, so the occupancy volume takes those surfaces from the brick. Render thread.
     *
     * @param position The section.
     * @return True if a drawn bake of it has any off the GPU.
     */
    public boolean bricksStandIn(LodStore.@NonNull SectionPos position) {
        lock.lock();
        try {
            final Section section = byPosition.get(position);
            final Version current = section == null ? null : section.current;
            return current != null
                    && (current.offGpu().contains(Material.Transparency.CUTOUT)
                            || current.offGpu().contains(Material.Transparency.TRANSLUCENT));
        } finally {
            lock.unlock();
        }
    }

    /**
     * The opaque and cut out meshes of every section drawn now whose box reaches into a sphere, for
     * point light shadows. Render thread.
     *
     * @param center The sphere's center, in the world.
     * @param radius Its radius.
     * @return The meshes, with their sections' corners.
     */
    public List<Caster> casters(@NonNull Vector3dc center, double radius) {
        final List<Caster> out = new ArrayList<>();
        lock.lock();
        try {
            for (Section section : sections.values()) {
                final Version current = section.current;
                if (current == null) {
                    continue;
                }
                final Vector3dc origin = current.origin();
                double squared = 0;
                for (int axis = 0; axis < AXES; ++axis) {
                    final double low = origin.get(axis);
                    final double d =
                            Math.max(
                                    0,
                                    Math.max(
                                            low - center.get(axis),
                                            center.get(axis) - (low + SECTION_SIZE)));
                    squared += d * d;
                }
                if (squared > radius * radius) {
                    continue;
                }
                for (MeshHandle mesh : current.meshes()) {
                    if (mesh.kind() == MeshKind.BAKED_OPAQUE
                            || mesh.kind() == MeshKind.BAKED_CUTOUT) {
                        out.add(new Caster(origin, mesh));
                    }
                }
            }
        } finally {
            lock.unlock();
        }
        return out;
    }

    /**
     * Register a finished bake's meshes, unless the section moved on without it.
     *
     * @param state The Vulkan state.
     * @param done The bake.
     */
    private void upload(@NonNull VulkanState state, @NonNull Baked done) {
        final String owner;
        lock.lock();
        try {
            Section section = sections.get(done.id());
            // Removed, or a newer bake was asked for since
            if (section == null || done.generation() < section.requested) {
                return;
            }
            owner = section.owner;
        } finally {
            lock.unlock();
        }
        final boolean feedsLod;
        final boolean onGrid;
        final LodStore.SectionPos position;
        lock.lock();
        try {
            Section section = sections.get(done.id());
            position = section == null ? null : section.position;
            onGrid = position != null;
            feedsLod = onGrid && live.containsKey(owner);
        } finally {
            lock.unlock();
        }
        final String name = "section-" + done.id() + "-" + done.generation();
        final Model model = new Model(name);
        final Model opaqueModel = feedsLod ? new Model(name + "-opaque") : null;
        final Model nearModel = onGrid ? new Model(name + "-near") : null;
        int nearTriangles = 0;
        final List<MeshHandle> meshes = new ArrayList<>();
        final Map<Material.Transparency, MeshData> buckets =
                new EnumMap<>(Material.Transparency.class);
        final Set<Material.Transparency> offGpu = EnumSet.noneOf(Material.Transparency.class);
        final Vector3dc eye = lastEye;
        for (Material.Transparency transparency : Material.Transparency.values()) {
            final SectionBaker.Bucket bucket = done.result().get(transparency);
            if (bucket == null) {
                continue;
            }
            // Meshes only needed nearby stay off the GPU when far, until it comes near
            final boolean managed =
                    transparency == Material.Transparency.OPAQUE ? feedsLod : onGrid;
            final boolean skip =
                    managed
                            && eye != null
                            && gap(transparency, eye, position)
                                    > reach(transparency) + RESTORE_MARGIN;
            MeshHandle handle = null;
            try {
                if (!skip) {
                    handle = register(state, owner, MeshKind.baked(transparency), bucket);
                }
            } catch (IllegalStateException e) {
                // Out of room in the buffers: this bake is dropped, the section keeps what it has
                log.error(
                        "Section {} doesn't fit in the baked geometry buffers: {}",
                        done.id(),
                        e.getMessage());
                releasing.addAll(meshes);
                markFailed(done.id());
                return;
            }
            final MeshData meshData =
                    new MeshData(
                            new Vector3f(bucket.min()),
                            new Vector3f(bucket.max()),
                            bucket.vertexCount(),
                            new float[0],
                            new int[0],
                            0,
                            new byte[0]);
            meshData.setMesh(handle);
            buckets.put(transparency, meshData);
            if (skip) {
                offGpu.add(transparency);
            }
            if (opaqueModel != null && transparency == Material.Transparency.OPAQUE) {
                opaqueModel.getMeshDataList().add(meshData);
            } else if (nearModel != null && transparency != Material.Transparency.OPAQUE) {
                nearModel.getMeshDataList().add(meshData);
                nearTriangles += bucket.indices().length / 3;
            } else {
                model.getMeshDataList().add(meshData);
            }
            if (handle != null) {
                meshes.add(handle);
            }
        }
        final Version version =
                new Version(
                        done.generation(),
                        done.origin(),
                        model,
                        opaqueModel,
                        nearModel,
                        meshes,
                        buckets,
                        offGpu,
                        done.result().triangleCount(),
                        nearTriangles,
                        done.brick() != null && done.brick().missingMaterial(),
                        done.materialsVersion());
        lock.lock();
        try {
            Section section = sections.get(done.id());
            if (section == null || done.generation() < section.requested) {
                releasing.addAll(meshes);
                return;
            }
            if (section.pending != null) {
                releasing.addAll(section.pending.meshes());
            }
            section.pending = version;
            pendingSections.add(section);
            section.pendingBrick = done.brick();
            if (section.failed) {
                --failedCount;
                section.failed = false;
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Note that a section's bake couldn't be uploaded.
     *
     * @param id The section.
     */
    private void markFailed(long id) {
        lock.lock();
        try {
            Section section = sections.get(id);
            if (section != null && !section.failed) {
                section.failed = true;
                ++failedCount;
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Copy one bucket into the baked geometry buffers.
     *
     * @param state The Vulkan state.
     * @param owner The owning plugin's key.
     * @param kind The kind of mesh.
     * @param bucket The baked mesh.
     * @return Its handle.
     */
    private static MeshHandle register(
            @NonNull VulkanState state,
            @NonNull String owner,
            @NonNull MeshKind kind,
            @NonNull SectionBaker.Bucket bucket) {
        final int vertexBytes = bucket.vertexCount() * BakedVertex.SIZE;
        ByteBuffer vertices = MemoryUtil.memAlloc(vertexBytes);
        ByteBuffer indices = MemoryUtil.memAlloc(bucket.indices().length * Integer.BYTES);
        try {
            // The baked bytes are already little endian, as the GPU wants
            vertices.put(0, bucket.vertices(), 0, vertexBytes);
            indices.asIntBuffer().put(bucket.indices());
            return state.bakedGeometry.register(
                    state,
                    owner,
                    kind,
                    vertices,
                    indices,
                    new Vector3f(bucket.min()),
                    new Vector3f(bucket.max()));
        } finally {
            MemoryUtil.memFree(indices);
            MemoryUtil.memFree(vertices);
        }
    }

    /**
     * Swap in every pending bake whose meshes are all resident: place it, and take out the one it
     * replaces.
     *
     * @param state The Vulkan state.
     */
    private void swapResident(@NonNull VulkanState state) {
        final MeshRegistry registry = state.bakedGeometry.getRegistry();
        lock.lock();
        try {
            final Iterator<Section> waiting = pendingSections.iterator();
            while (waiting.hasNext()) {
                final Section section = waiting.next();
                final Version pending = section.pending;
                if (pending == null || section.removed) {
                    waiting.remove();
                    continue;
                }
                boolean resident = true;
                for (MeshHandle mesh : pending.meshes()) {
                    resident &= registry.isResident(mesh);
                }
                if (!resident) {
                    continue;
                }
                if (section.instance != null) {
                    removing.add(new Removal(section.owner, section.instance));
                    section.instance = null;
                }
                // The override, if it still is one, places the new bake's opaque blocks below
                if (section.opaqueInstance != null) {
                    removing.add(new Removal(section.owner, section.opaqueInstance));
                    section.opaqueInstance = null;
                }
                // Placed again below if it is still near, and its brick put in
                if (section.nearInstance != null) {
                    removing.add(new Removal(section.owner, section.nearInstance));
                    section.nearInstance = null;
                }
                if (section.position != null && state.volumeBricks != null) {
                    state.volumeBricks.put(section.position, section.pendingBrick);
                }
                if (section.current != null) {
                    releasing.addAll(section.current.meshes());
                    residentTriangles -= section.current.triangles();
                } else {
                    ++residentCount;
                }
                residentTriangles += pending.triangles();
                section.current = pending;
                section.pending = null;
                waiting.remove();
                section.pendingBrick = null;
                section.restoring = false;
                section.rebricking = false;
                setPlaced(section);
                if (section.position != null) {
                    // What its distance decides is checked again for the new bake
                    queueCheck(section, travel);
                    if (live.containsKey(section.owner)) {
                        behind.add(section);
                    }
                }
                shadows.addSection(pending.origin());
                if (!pending.model().getMeshDataList().isEmpty()) {
                    section.instance =
                            state.instances
                                    .getRegistry()
                                    .place(
                                            section.owner,
                                            pending.model(),
                                            pending.origin(),
                                            new Quaternionf(),
                                            1.0f / BakedVertex.STEPS_PER_UNIT);
                }
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Have a plugin's sections feed a live level of detail world from now on, its sections already
     * there included, which are baked again. Their opaque blocks are then drawn by level of detail.
     *
     * @param state The Vulkan state.
     * @param owner The plugin's key.
     * @param thresholdPixels The world's error threshold.
     * @return The world's id in the {@link LodRenderer}, the same one if it already has one.
     */
    public long startLive(
            @NonNull VulkanState state, @NonNull String owner, double thresholdPixels) {
        final List<long[]> rebake = new ArrayList<>();
        final Live world;
        lock.lock();
        try {
            final Live existing = live.get(owner);
            if (existing != null) {
                return existing.world();
            }
            final double[] origin = new double[3];
            final long[] id = new long[1];
            final LiveLod lod =
                    new LiveLod(
                            origin,
                            SECTION_SIZE,
                            LodStore.DEFAULT_CELL_SECTIONS,
                            // Sections are merged as they are baked, on the workers
                            DagBuilder.Options.defaults().withMergePlanar(false),
                            batch -> state.lods.publish(state, id[0], batch),
                            LodCache.fromSettings());
            id[0] = state.lods.addLive(owner, origin, lod::cellOf, thresholdPixels);
            world = new Live(id[0], lod, new HashMap<>());
            live.put(owner, world);
            for (Map.Entry<Long, Section> entry : sections.entrySet()) {
                if (entry.getValue().owner.equals(owner) && entry.getValue().request != null) {
                    rebake.add(new long[] {entry.getKey()});
                }
                if (entry.getValue().owner.equals(owner) && entry.getValue().position != null) {
                    behind.add(entry.getValue());
                }
            }
        } finally {
            lock.unlock();
        }
        world.lod().start();
        rebakeAll(owner, rebake);
        return world.world();
    }

    /**
     * Stop a plugin's sections feeding level of detail, if the world is its live one. Its sections
     * are baked again to draw their opaque blocks themselves.
     *
     * @param owner The plugin's key.
     * @param world The world's id in the {@link LodRenderer}.
     * @return True if it was the plugin's live world.
     */
    public boolean stopLive(@NonNull String owner, long world) {
        final List<long[]> rebake = new ArrayList<>();
        final Live stopped;
        lock.lock();
        try {
            final Live existing = live.get(owner);
            if (existing == null || existing.world() != world) {
                return false;
            }
            stopped = live.remove(owner);
            for (Map.Entry<Long, Section> entry : sections.entrySet()) {
                if (entry.getValue().owner.equals(owner) && entry.getValue().request != null) {
                    rebake.add(new long[] {entry.getKey()});
                }
            }
            behind.removeIf(section -> section.owner.equals(owner));
        } finally {
            lock.unlock();
        }
        stopped.lod().close();
        rebakeAll(owner, rebake);
        return true;
    }

    /**
     * Bake sections again from their last requests.
     *
     * @param owner Their owner.
     * @param ids The sections.
     */
    private void rebakeAll(@NonNull String owner, @NonNull List<long[]> ids) {
        for (long[] id : ids) {
            final Request request;
            lock.lock();
            try {
                final Section section = sections.get(id[0]);
                request = section == null ? null : section.request;
            } finally {
                lock.unlock();
            }
            if (request != null) {
                schedule(id[0], owner, request);
            }
        }
    }

    /**
     * Where a section is on the section grid.
     *
     * @param owner Its owner, warned once if it is off the grid.
     * @param origin Its origin.
     * @return Its position, or null if its origin isn't a section corner.
     */
    private LodStore.SectionPos sectionPos(@NonNull String owner, @NonNull Vector3dc origin) {
        final double[] at = {origin.x(), origin.y(), origin.z()};
        final int[] out = new int[at.length];
        for (int axis = 0; axis < at.length; ++axis) {
            final double sections = at[axis] / SECTION_SIZE;
            if (sections != Math.rint(sections)
                    || Math.abs(sections) > Integer.MAX_VALUE / SECTION_SIZE) {
                if (warnedOffGrid.add(owner)) {
                    log.warn(
                            "Sections of {} off the {}-block grid, as at {}, don't feed level of"
                                    + " detail",
                            owner,
                            SECTION_SIZE,
                            origin);
                }
                return null;
            }
            out[axis] = (int) sections;
        }
        return new LodStore.SectionPos(out[0], out[1], out[2]);
    }

    /**
     * Decide which sections feeding level of detail are overrides this frame: those near the
     * observer whose bake is newer than the level of detail shows. Place or take out their opaque
     * blocks, and tell each world which sections to hide. Render thread.
     *
     * @param state The Vulkan state.
     * @param observer Where the scene is seen from.
     */
    private void updateOverrides(@NonNull VulkanState state, @NonNull Observer observer) {
        final Vector3dc eye = observer.getPosition();
        lastEye = new Vector3d(eye);
        final double reach = GraphicsManager.getSettings().lodOverrideDistance;
        int count = 0;
        int behindCount = 0;
        lock.lock();
        try {
            if (live.isEmpty()) {
                overrideCount = 0;
                lodBehind = 0;
                return;
            }
            final Map<String, Set<LodStore.SectionPos>> overrides = new HashMap<>();
            for (Map.Entry<String, Live> entry : live.entrySet()) {
                entry.getValue().lod().setNear(eye.x(), eye.y(), eye.z());
                overrides.put(entry.getKey(), new HashSet<>());
            }
            // Only a section whose bake may be newer than the level of detail shows can be an
            // override, or one that is one now and may stop being
            final List<Section> candidates = new ArrayList<>(behind);
            for (Section section : overriding) {
                if (!behind.contains(section)) {
                    candidates.add(section);
                }
            }
            for (Section section : candidates) {
                final Live world = live.get(section.owner);
                if (world == null || section.position == null) {
                    behind.remove(section);
                    continue;
                }
                final Version current = section.current;
                final boolean newer =
                        current == null
                                || current.generation()
                                        > state.lods.coveredGeneration(
                                                world.world(), section.position);
                if (newer) {
                    ++behindCount;
                } else {
                    behind.remove(section);
                }
                final boolean override =
                        newer
                                && current != null
                                && current.opaqueModel() != null
                                && distance(eye, section.position) <= reach;
                if (override) {
                    overriding.add(section);
                    overrides.get(section.owner).add(section.position);
                    if (section.opaqueInstance == null
                            && !current.opaqueModel().getMeshDataList().isEmpty()
                            && resident(state.bakedGeometry.getRegistry(), current.opaqueModel())) {
                        shadows.addSection(current.origin());
                        section.opaqueInstance =
                                state.instances
                                        .getRegistry()
                                        .place(
                                                section.owner,
                                                current.opaqueModel(),
                                                current.origin(),
                                                new Quaternionf(),
                                                1.0f / BakedVertex.STEPS_PER_UNIT);
                    }
                    ++count;
                } else {
                    overriding.remove(section);
                    if (section.opaqueInstance != null) {
                        removing.add(new Removal(section.owner, section.opaqueInstance));
                        section.opaqueInstance = null;
                        if (current != null) {
                            shadows.addSection(current.origin());
                        }
                    }
                }
            }
            // Sections taken out show as gaps near the observer until the level of detail drops
            // them
            for (Map.Entry<String, Live> entry : live.entrySet()) {
                final Live world = entry.getValue();
                final var it = world.removed().entrySet().iterator();
                while (it.hasNext()) {
                    final var removed = it.next();
                    final long shown =
                            state.lods.coveredGeneration(world.world(), removed.getKey());
                    if (shown >= removed.getValue()) {
                        state.lods.forgetCovered(world.world(), removed.getKey());
                        it.remove();
                    } else if (distance(eye, removed.getKey()) <= reach) {
                        overrides.get(entry.getKey()).add(removed.getKey());
                        ++count;
                    }
                }
                state.lods.setOverrides(world.world(), overrides.get(entry.getKey()));
            }
        } finally {
            lock.unlock();
        }
        overrideCount = count;
        lodBehind = behindCount;
    }

    /**
     * Decide which sections draw their own cut out and translucent blocks: those within the volume
     * distance, or all of them when volumes are off. Place or take out their near models, and keep
     * the rest's bricks drawn in their place. Take meshes only needed nearby off the GPU as
     * sections leave their reach, and bake them again as they come back. Render thread.
     *
     * <p>Only sections due are checked: each one is checked again once the observer could have
     * moved far enough to cross one of its thresholds, or at once when its bake changes, so a frame
     * costs what is near a threshold or changing, not what is loaded.
     *
     * @param state The Vulkan state.
     * @param observer Where the scene is seen from.
     */
    private void updateNear(@NonNull VulkanState state, @NonNull Observer observer) {
        final Vector3dc eye = observer.getPosition();
        final double reach = GraphicsManager.getSettings().volumeDistance;
        final int materialsVersion = volumeMaterials.version();
        lock.lock();
        try {
            if (traveledFrom == null) {
                traveledFrom = new Vector3d(eye);
            }
            travel += traveledFrom.distance(eye);
            traveledFrom.set(eye);
            // Every threshold moves with the volume distance, and bricks wait on the materials
            final boolean volumeDistanceChanged = Double.compare(reach, checkedVolumeDistance) != 0;
            final boolean materialsChanged = materialsVersion != checkedMaterials;
            if (volumeDistanceChanged || materialsChanged) {
                checkedVolumeDistance = reach;
                checkedMaterials = materialsVersion;
                for (Section section : sections.values()) {
                    if (section.current != null
                            && section.position != null
                            && (volumeDistanceChanged || section.current.brickMissing())) {
                        queueCheck(section, travel);
                    }
                }
            }
            // Checks queued while checking wait for the next frame, so a section waiting on
            // something is checked once a frame
            final List<Section> again = new ArrayList<>();
            final List<Double> waits = new ArrayList<>();
            while (!checks.isEmpty() && checks.peek().at() <= travel) {
                final Check check = checks.poll();
                final Section section = check.section();
                if (section.removed
                        || check.stamp() != section.checkStamp
                        || section.current == null) {
                    continue;
                }
                again.add(section);
                waits.add(check(state, section, eye, reach, materialsVersion));
            }
            for (int i = 0; i < again.size(); ++i) {
                queueCheck(again.get(i), travel + waits.get(i));
            }
            // Replaced checks stay queued until due; drop them if they pile up, as when the
            // observer stands still while sections are baked again
            if (checks.size() > STALE_CHECKS_FACTOR * Math.max(sections.size(), 1)) {
                checks.removeIf(
                        check ->
                                check.section().removed
                                        || check.stamp() != check.section().checkStamp);
            }
            lastChecked = again.size();
            queuedChecks = checks.size();
            if (brickedChanged) {
                volumeSections = List.copyOf(bricked);
                brickedChanged = false;
            }
            placedTriangles = placedTotal;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Queue a section's next check, replacing any it had. Hold the lock.
     *
     * @param section The section.
     * @param at The observer's travel it is due at; infinite for none.
     */
    private void queueCheck(@NonNull Section section, double at) {
        ++section.checkStamp;
        if (Double.isFinite(at)) {
            checks.add(new Check(at, checkCount++, section, section.checkStamp));
        }
    }

    /**
     * Check what a section's distance from the observer decides. Hold the lock.
     *
     * @param state The Vulkan state.
     * @param section The section, on the section grid, with a current bake.
     * @param eye Where the scene is seen from.
     * @param reach The volume distance.
     * @param materialsVersion The materials snapshot's version.
     * @return How far the observer can move before anything here could change, in meters: 0 if it
     *     waits on something else, infinite if only an event can change it.
     */
    private double check(
            @NonNull VulkanState state,
            @NonNull Section section,
            @NonNull Vector3dc eye,
            double reach,
            int materialsVersion) {
        final Version current = section.current;
        double wait = Double.POSITIVE_INFINITY;
        if (current.brickMissing()
                && !section.rebricking
                && materialsVersion > current.materialsVersion()) {
            rebrickLater(section.id, section);
        }
        if (current.nearModel() == null || current.nearModel().getMeshDataList().isEmpty()) {
            setBricked(section, false);
        } else {
            // Its blocks draw a share of their places until the hand over ends
            final double distance = distance(eye, section.position);
            final double fadeEnd = VolumeMath.fadeEnd(reach);
            final boolean wasNear = section.near;
            section.near =
                    reach <= 0
                            || distance <= fadeEnd
                            || (section.near && distance <= fadeEnd + NEAR_HYSTERESIS);
            if (section.near != wasNear) {
                setPlaced(section);
            }
            // Its brick takes over its blocks' places past where the hand over starts, which can
            // be inside a section still near
            if (reach > 0) {
                final double farthest = farthest(eye, section.position);
                final double fadeStart = VolumeMath.fadeStart(reach);
                setBricked(section, !section.near || farthest > fadeStart);
                wait =
                        Math.min(
                                Math.min(
                                        Math.abs(distance - fadeEnd),
                                        Math.abs(distance - fadeEnd - NEAR_HYSTERESIS)),
                                Math.abs(farthest - fadeStart));
            } else {
                setBricked(section, false);
            }
            if (section.near) {
                if (section.nearInstance == null) {
                    if (resident(state.bakedGeometry.getRegistry(), current.nearModel())) {
                        shadows.addSection(current.origin());
                        section.nearInstance =
                                state.instances
                                        .getRegistry()
                                        .place(
                                                section.owner,
                                                current.nearModel(),
                                                current.origin(),
                                                new Quaternionf(),
                                                1.0f / BakedVertex.STEPS_PER_UNIT);
                    } else {
                        // Its meshes are coming back on the GPU
                        wait = RETRY;
                    }
                }
            } else if (section.nearInstance != null) {
                removing.add(new Removal(section.owner, section.nearInstance));
                section.nearInstance = null;
                shadows.addSection(current.origin());
            }
        }
        return Math.min(wait, updateResidency(state, section.id, section, eye));
    }

    /**
     * Note whether a section's brick is drawn for its cut out and translucent blocks. Hold the
     * lock.
     *
     * @param section The section.
     * @param drawn Whether it is.
     */
    private void setBricked(@NonNull Section section, boolean drawn) {
        if (section.bricked == drawn || section.position == null) {
            return;
        }
        section.bricked = drawn;
        if (drawn) {
            bricked.add(section.position);
        } else {
            bricked.remove(section.position);
        }
        brickedChanged = true;
    }

    /**
     * Work out again what a section adds to the placed triangles: all of its current bake's, less
     * its near model's while its brick stands in for them. Hold the lock.
     *
     * @param section The section.
     */
    private void setPlaced(@NonNull Section section) {
        final Version current = section.current;
        final long placed =
                current == null
                        ? 0
                        : current.triangles()
                                - current.nearTriangles()
                                + (section.near ? current.nearTriangles() : 0);
        placedTotal += placed - section.placed;
        section.placed = placed;
    }

    /**
     * Take a section's meshes that are only needed nearby off the GPU once it is past their reach,
     * and bake them again once it comes back within it. Hold the lock.
     *
     * @param state The Vulkan state.
     * @param id The section.
     * @param section The section, on the section grid, with a current bake.
     * @param eye Where the scene is seen from.
     * @return How far the observer can move before any of its meshes could need to come or go, in
     *     meters: 0 while one waits on something else.
     */
    private double updateResidency(
            @NonNull VulkanState state, long id, @NonNull Section section, @NonNull Vector3dc eye) {
        final Version current = section.current;
        final Set<Material.Transparency> wanted = EnumSet.noneOf(Material.Transparency.class);
        double wait = Double.POSITIVE_INFINITY;
        for (Map.Entry<Material.Transparency, MeshData> bucket : current.buckets().entrySet()) {
            final Material.Transparency kind = bucket.getKey();
            final boolean opaque = kind == Material.Transparency.OPAQUE;
            if ((opaque ? current.opaqueModel() : current.nearModel()) == null) {
                continue;
            }
            final double gap = gap(kind, eye, section.position);
            final double reach = reach(kind);
            if (current.offGpu().contains(kind)) {
                if (gap <= reach + RESTORE_MARGIN) {
                    wanted.add(kind);
                    // Checked until it is back
                    wait = RETRY;
                } else {
                    wait = Math.min(wait, gap - reach - RESTORE_MARGIN);
                }
                continue;
            }
            if (gap <= reach + RELEASE_MARGIN) {
                wait = Math.min(wait, reach + RELEASE_MARGIN - gap);
                continue;
            }
            // Only once nothing places it, and, for blocks a brick stands in for, the brick does
            final boolean unused =
                    opaque
                            ? section.opaqueInstance == null
                            : !section.near
                                    && section.nearInstance == null
                                    && state.volumeBricks != null
                                    && state.volumeBricks.isCurrent(section.position);
            if (unused) {
                final MeshHandle mesh = bucket.getValue().getMesh();
                releasing.add(mesh);
                current.meshes().remove(mesh);
                bucket.getValue().setMesh(null);
                current.offGpu().add(kind);
                wait = Math.min(wait, gap - reach - RESTORE_MARGIN);
            } else {
                // Its brick isn't in yet, or it is an override
                wait = RETRY;
            }
        }
        if (!wanted.isEmpty() && !section.restoring) {
            restoreLater(id, section, wanted);
        }
        return wait;
    }

    /**
     * How far a section's meshes of a kind are needed on the GPU, for a section managing them: a
     * section feeding level of detail its opaque mesh, and a section on the section grid its cut
     * out and translucent ones.
     *
     * @param kind The meshes' transparency.
     * @return The reach, in meters, measured as {@link #gap} measures it.
     */
    private static double reach(Material.@NonNull Transparency kind) {
        if (kind == Material.Transparency.OPAQUE) {
            return OPAQUE_REACH;
        }
        final double volumeDistance = GraphicsManager.getSettings().volumeDistance;
        return volumeDistance <= 0 ? Double.POSITIVE_INFINITY : VolumeMath.fadeEnd(volumeDistance);
    }

    /**
     * How far a section is from the observer, for its meshes of a kind: along the farthest axis for
     * opaque meshes, as the occupancy volume's box reaches, else straight.
     *
     * @param kind The meshes' transparency.
     * @param eye Where the scene is seen from.
     * @param position The section.
     * @return The distance, in meters, 0 inside it.
     */
    private static double gap(
            Material.@NonNull Transparency kind,
            @NonNull Vector3dc eye,
            LodStore.@NonNull SectionPos position) {
        if (kind != Material.Transparency.OPAQUE) {
            return distance(eye, position);
        }
        final double[] at = {eye.x(), eye.y(), eye.z()};
        final int[] corner = {position.x(), position.y(), position.z()};
        double most = 0;
        for (int axis = 0; axis < at.length; ++axis) {
            final double min = (double) corner[axis] * SECTION_SIZE;
            most = Math.max(most, Math.max(min - at[axis], at[axis] - (min + SECTION_SIZE)));
        }
        return most;
    }

    /**
     * Whether a model's meshes are all on the GPU.
     *
     * @param registry The baked geometry's registry.
     * @param model The model.
     * @return True if it can be drawn.
     */
    private static boolean resident(@NonNull MeshRegistry registry, @NonNull Model model) {
        for (MeshData meshData : model.getMeshDataList()) {
            if (meshData.getMesh() == null || !registry.isResident(meshData.getMesh())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Bake a section's current blocks again on the workers, to put meshes kept off the GPU back on
     * it. Not while a newer bake is coming, which brings its own. Hold the lock.
     *
     * @param id The section.
     * @param section The section.
     * @param kinds The buckets to put back.
     */
    private void restoreLater(
            long id, @NonNull Section section, @NonNull Set<Material.Transparency> kinds) {
        final Version current = section.current;
        final Request request = section.request;
        if (current == null || request == null || section.requested != current.generation()) {
            return;
        }
        section.restoring = true;
        final long generation = current.generation();
        try {
            workers.execute(
                    () ->
                            restored.add(
                                    new Restored(
                                            id,
                                            generation,
                                            kinds,
                                            SectionBaker.bakeByFace(
                                                    request.sources(),
                                                    request.faceMaterials(),
                                                    request.packedPositions(),
                                                    request.rotations(),
                                                    request.faceMasks(),
                                                    request.userData()))));
        } catch (RejectedExecutionException e) {
            section.restoring = false;
        }
    }

    /**
     * Put a section's meshes back on the GPU from a bake done again, those still off it, if it is
     * still the drawn bake. Render thread, before the baked geometry is recorded.
     *
     * @param state The Vulkan state.
     * @param back The bake.
     */
    private void restore(@NonNull VulkanState state, @NonNull Restored back) {
        final String owner;
        final Set<Material.Transparency> kinds = EnumSet.noneOf(Material.Transparency.class);
        lock.lock();
        try {
            final Section section = sections.get(back.id());
            if (section == null) {
                return;
            }
            final Version current = section.current;
            if (current != null && current.generation() == back.generation()) {
                kinds.addAll(back.kinds());
                kinds.retainAll(current.offGpu());
            }
            if (kinds.isEmpty()) {
                section.restoring = false;
                return;
            }
            owner = section.owner;
        } finally {
            lock.unlock();
        }
        final Map<Material.Transparency, MeshHandle> handles =
                new EnumMap<>(Material.Transparency.class);
        try {
            for (Material.Transparency kind : kinds) {
                final SectionBaker.Bucket bucket = back.result().get(kind);
                if (bucket == null) {
                    throw new IllegalStateException("the bake no longer has a " + kind + " mesh");
                }
                handles.put(kind, register(state, owner, MeshKind.baked(kind), bucket));
            }
        } catch (IllegalStateException e) {
            log.error("Section {} couldn't get its meshes back: {}", back.id(), e.getMessage());
            releasing.addAll(handles.values());
            lock.lock();
            try {
                final Section section = sections.get(back.id());
                if (section != null) {
                    section.restoring = false;
                }
            } finally {
                lock.unlock();
            }
            return;
        }
        lock.lock();
        try {
            final Section section = sections.get(back.id());
            final Version current = section == null ? null : section.current;
            if (current == null || current.generation() != back.generation()) {
                releasing.addAll(handles.values());
                if (section != null) {
                    section.restoring = false;
                }
                return;
            }
            for (Map.Entry<Material.Transparency, MeshHandle> handle : handles.entrySet()) {
                if (!current.offGpu().remove(handle.getKey())) {
                    releasing.add(handle.getValue());
                    continue;
                }
                current.buckets().get(handle.getKey()).setMesh(handle.getValue());
                current.meshes().add(handle.getValue());
                if (handle.getKey() == Material.Transparency.OPAQUE && state.occupancy != null) {
                    // The occupancy volume only takes meshes on the GPU; nothing else changed,
                    // since level of detail draws them
                    final Vector3dc origin = current.origin();
                    state.occupancy.invalidate(
                            new double[] {origin.x(), origin.y(), origin.z()},
                            new double[] {
                                origin.x() + SECTION_SIZE,
                                origin.y() + SECTION_SIZE,
                                origin.z() + SECTION_SIZE
                            });
                }
            }
            section.restoring = false;
            queueCheck(section, travel);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Voxelize a section's brick again on the workers, with the materials it met too early now
     * published. Hold the lock.
     *
     * @param id The section.
     * @param section The section.
     */
    private void rebrickLater(long id, @NonNull Section section) {
        final Version current = section.current;
        final Request request = section.request;
        if (current == null || request == null || section.requested != current.generation()) {
            return;
        }
        section.rebricking = true;
        final long generation = current.generation();
        try {
            workers.execute(
                    () -> {
                        final VolumeMaterials table = volumeMaterials;
                        rebricked.add(
                                new Rebricked(
                                        id,
                                        generation,
                                        voxelize(
                                                SectionBaker.bakeByFace(
                                                        request.sources(),
                                                        request.faceMaterials(),
                                                        request.packedPositions(),
                                                        request.rotations(),
                                                        request.faceMasks(),
                                                        request.userData()),
                                                table),
                                        table.version()));
                    });
        } catch (RejectedExecutionException e) {
            section.rebricking = false;
        }
    }

    /**
     * Put a brick voxelized again in the volume bricks, if its bake is still the drawn one. Render
     * thread.
     *
     * @param state The Vulkan state.
     * @param back The brick.
     */
    private void rebrick(@NonNull VulkanState state, @NonNull Rebricked back) {
        lock.lock();
        try {
            final Section section = sections.get(back.id());
            if (section == null) {
                return;
            }
            final Version current = section.current;
            if (current == null || current.generation() != back.generation()) {
                section.rebricking = false;
                return;
            }
            if (state.volumeBricks != null) {
                state.volumeBricks.put(section.position, back.brick());
            }
            section.current =
                    new Version(
                            current.generation(),
                            current.origin(),
                            current.model(),
                            current.opaqueModel(),
                            current.nearModel(),
                            current.meshes(),
                            current.buckets(),
                            current.offGpu(),
                            current.triangles(),
                            current.nearTriangles(),
                            back.brick() != null && back.brick().missingMaterial(),
                            back.materialsVersion());
            section.rebricking = false;
        } finally {
            lock.unlock();
        }
    }

    /**
     * The sections whose volume bricks stand in for their cut out and translucent blocks this
     * frame.
     *
     * @return The sections.
     */
    public List<LodStore.SectionPos> getVolumeSections() {
        return volumeSections;
    }

    /**
     * How many triangles the resident bakes have, leaving out the cut out and translucent blocks
     * that bricks stood in for last frame: what the sections draw before culling, when they don't
     * feed level of detail.
     *
     * @return The count.
     */
    public long getPlacedTriangles() {
        return placedTriangles;
    }

    /**
     * What a plugin's live level of detail world's cache has done.
     *
     * @param owner The plugin's key.
     * @return The numbers, or null if it has no live world or no cache.
     */
    public LodCache.Stats lodCacheStats(@NonNull String owner) {
        lock.lock();
        try {
            final Live world = live.get(owner);
            return world == null ? null : world.lod().cacheStats();
        } finally {
            lock.unlock();
        }
    }

    /**
     * How many sections were checked for what their distance decides last frame, and how many are
     * waiting for a check: few once the scene settles, those near a threshold or waiting on
     * something.
     *
     * @return The checked count, then the queued count.
     */
    public int[] getCheckCounts() {
        return new int[] {lastChecked, queuedChecks};
    }

    /**
     * How many sections feeding level of detail it didn't show at their newest bake last frame,
     * counting those not yet baked: 0 once it has caught up.
     *
     * @return The count.
     */
    public int getLodBehind() {
        return lodBehind;
    }

    /**
     * The newest bake asked for of a section.
     *
     * @param id The section.
     * @return Its generation, or -1 if it is gone.
     */
    public long requestedGeneration(long id) {
        lock.lock();
        try {
            final Section section = sections.get(id);
            return section == null ? -1 : section.requested;
        } finally {
            lock.unlock();
        }
    }

    /**
     * The bake of a section that is drawn.
     *
     * @param id The section.
     * @return Its generation, or -1 before the first is resident or once it is gone.
     */
    public long currentGeneration(long id) {
        lock.lock();
        try {
            final Section section = sections.get(id);
            return section == null || section.current == null ? -1 : section.current.generation();
        } finally {
            lock.unlock();
        }
    }

    /**
     * How far a point is from a section's box.
     *
     * @param point The point, in the world.
     * @param section The section.
     * @return The distance, 0 inside it.
     */
    private static double distance(@NonNull Vector3dc point, LodStore.@NonNull SectionPos section) {
        final double[] at = {point.x(), point.y(), point.z()};
        final int[] corner = {section.x(), section.y(), section.z()};
        double sum = 0;
        for (int axis = 0; axis < at.length; ++axis) {
            final double min = (double) corner[axis] * SECTION_SIZE;
            final double d = Math.max(0, Math.max(min - at[axis], at[axis] - (min + SECTION_SIZE)));
            sum += d * d;
        }
        return Math.sqrt(sum);
    }

    /**
     * How far a point is from the farthest corner of a section's box.
     *
     * @param point The point, in the world.
     * @param section The section.
     * @return The distance.
     */
    private static double farthest(@NonNull Vector3dc point, LodStore.@NonNull SectionPos section) {
        final double[] at = {point.x(), point.y(), point.z()};
        final int[] corner = {section.x(), section.y(), section.z()};
        double sum = 0;
        for (int axis = 0; axis < at.length; ++axis) {
            final double min = (double) corner[axis] * SECTION_SIZE;
            final double d =
                    Math.max(Math.abs(min - at[axis]), Math.abs(min + SECTION_SIZE - at[axis]));
            sum += d * d;
        }
        return Math.sqrt(sum);
    }

    /**
     * How far a point is from the section whose cells start at an origin.
     *
     * @param point The point, in the world.
     * @param origin The section's origin.
     * @return The distance, 0 inside it.
     */
    private static double distance(@NonNull Vector3dc point, @NonNull Vector3dc origin) {
        final double[] at = {point.x(), point.y(), point.z()};
        final double[] min = {origin.x(), origin.y(), origin.z()};
        double sum = 0;
        for (int axis = 0; axis < at.length; ++axis) {
            final double d =
                    Math.max(
                            0,
                            Math.max(min[axis] - at[axis], at[axis] - (min[axis] + SECTION_SIZE)));
            sum += d * d;
        }
        return Math.sqrt(sum);
    }

    /**
     * Roughly how many bytes the sections' last bake requests hold: the placements each section was
     * baked from, what stands in for the world's block contents. Sources are shared between
     * sections and counted once each.
     *
     * @return The bytes.
     */
    public long requestBytes() {
        final Set<BakeSource> sources = Collections.newSetFromMap(new IdentityHashMap<>());
        long bytes = 0;
        lock.lock();
        try {
            for (Section section : sections.values()) {
                final Request request = section.request;
                if (request == null) {
                    continue;
                }
                bytes +=
                        ((long) request.faceMaterials().length + request.packedPositions().length)
                                        * Integer.BYTES
                                + request.rotations().length
                                + request.faceMasks().length
                                + (long) request.userData().length * Short.BYTES
                                + (long) request.sources().length * Long.BYTES;
                sources.addAll(Arrays.asList(request.sources()));
            }
        } finally {
            lock.unlock();
        }
        for (BakeSource source : sources) {
            bytes += source.heldBytes();
        }
        return bytes;
    }

    /**
     * Roughly how many bytes the live level of detail worlds' stores hold in memory, see {@link
     * LiveLod#heldBytes}.
     *
     * @return The bytes.
     */
    public long lodHeldBytes() {
        long bytes = 0;
        lock.lock();
        try {
            for (Live world : live.values()) {
                bytes += world.lod().heldBytes();
            }
        } finally {
            lock.unlock();
        }
        return bytes;
    }

    /**
     * How many sections drew themselves over level of detail last frame.
     *
     * @return The count.
     */
    public int getOverrideCount() {
        return overrideCount;
    }

    /**
     * Totals for the debug window.
     *
     * @param sections How many sections there are.
     * @param resident How many have a bake drawn.
     * @param triangles How many triangles the drawn bakes have.
     * @param baking How many bakes are on the workers.
     * @param failed How many sections' last bake couldn't be uploaded.
     */
    public record Stats(int sections, int resident, long triangles, int baking, int failed) {}

    /**
     * Totals for the debug window.
     *
     * @return The totals.
     */
    public Stats getStats() {
        lock.lock();
        try {
            return new Stats(
                    sections.size(), residentCount, residentTriangles, inFlight.get(), failedCount);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Stop the workers and forget every section, when the renderer shuts down. Their instances and
     * meshes go with the instance table and baked geometry buffers.
     */
    public void cleanup() {
        workers.shutdownNow();
        lock.lock();
        try {
            live.values().forEach(world -> world.lod().close());
            live.clear();
            sections.clear();
            checks.clear();
            behind.clear();
            overriding.clear();
            bricked.clear();
        } finally {
            lock.unlock();
        }
        baked.clear();
        urgent.clear();
        releasing.clear();
        removing.clear();
    }
}
