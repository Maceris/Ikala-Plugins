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
import com.ikalagaming.graphics.graph.MeshData;
import com.ikalagaming.graphics.graph.Model;
import com.ikalagaming.graphics.lod.DagBuilder;
import com.ikalagaming.graphics.lod.LiveLod;
import com.ikalagaming.graphics.lod.LodCache;
import com.ikalagaming.graphics.lod.LodMesh;
import com.ikalagaming.graphics.lod.LodStore;
import com.ikalagaming.graphics.lod.PlanarMerge;
import com.ikalagaming.graphics.scene.Observer;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

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
     */
    private record Baked(
            long id,
            long generation,
            @NonNull Vector3dc origin,
            @NonNull SectionBaker.Result result) {}

    /**
     * One bake of a section in the GPU's buffers.
     *
     * @param generation Which of the section's bakes it is.
     * @param origin Where it goes.
     * @param model The model holding its meshes, which the instance places: all of them, or for a
     *     section feeding level of detail, all but the opaque one.
     * @param opaqueModel For a section feeding level of detail, the model holding its opaque mesh,
     *     placed only while it is an override; otherwise null.
     * @param meshes Its meshes, one per bucket that had anything.
     * @param triangles How many triangles it has.
     */
    private record Version(
            long generation,
            @NonNull Vector3dc origin,
            @NonNull Model model,
            Model opaqueModel,
            @NonNull List<MeshHandle> meshes,
            int triangles) {}

    /** A section's state. Guarded by {@link #lock}. */
    private static final class Section {
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

        /** What it was last asked to bake, to bake again when it starts feeding level of detail. */
        Request request;

        /** Where it is, in sections, or null if it isn't on the section grid. */
        LodStore.SectionPos position;

        /** Whether the last bake couldn't be uploaded, as when the buffers are full. */
        boolean failed;

        Section(String owner) {
            this.owner = owner;
        }
    }

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

    /** Each plugin's live world, by owner. Guarded by {@link #lock}. */
    private final Map<String, Live> live = new HashMap<>();

    /** Owners already warned about sections off the section grid. Guarded by {@link #lock}. */
    private final Set<String> warnedOffGrid = new HashSet<>();

    /** How many sections were overrides last frame. */
    private volatile int overrideCount;

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
            final Section section = new Section(owner);
            section.position = sectionPos(owner, request.origin());
            sections.put(id, section);
            if (section.position != null) {
                byPosition.put(section.position, section);
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
                                                new double[] {origin.x(), origin.y(), origin.z()})),
                        generation);
            }
            baked.add(new Baked(id, generation, new Vector3d(request.origin()), result));
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
                                }
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
        if (section.current != null) {
            releasing.addAll(section.current.meshes());
        }
        if (section.pending != null) {
            releasing.addAll(section.pending.meshes());
        }
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
        swapResident(state);
        updateOverrides(state, observer);
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
        lock.lock();
        try {
            Section section = sections.get(done.id());
            feedsLod = section != null && section.position != null && live.containsKey(owner);
        } finally {
            lock.unlock();
        }
        final Model model = new Model("section-" + done.id() + "-" + done.generation());
        final Model opaqueModel =
                feedsLod
                        ? new Model("section-" + done.id() + "-" + done.generation() + "-opaque")
                        : null;
        final List<MeshHandle> meshes = new ArrayList<>();
        for (Material.Transparency transparency : Material.Transparency.values()) {
            final SectionBaker.Bucket bucket = done.result().get(transparency);
            if (bucket == null) {
                continue;
            }
            final MeshHandle handle;
            try {
                handle = register(state, owner, MeshKind.baked(transparency), bucket);
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
            if (opaqueModel != null && transparency == Material.Transparency.OPAQUE) {
                opaqueModel.getMeshDataList().add(meshData);
            } else {
                model.getMeshDataList().add(meshData);
            }
            meshes.add(handle);
        }
        final Version version =
                new Version(
                        done.generation(),
                        done.origin(),
                        model,
                        opaqueModel,
                        meshes,
                        done.result().triangleCount());
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
            section.failed = false;
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
            if (section != null) {
                section.failed = true;
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
            for (Section section : sections.values()) {
                final Version pending = section.pending;
                if (pending == null) {
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
                if (section.current != null) {
                    releasing.addAll(section.current.meshes());
                }
                section.current = pending;
                section.pending = null;
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
        int behind = 0;
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
            for (Section section : sections.values()) {
                final Live world = live.get(section.owner);
                if (world == null || section.position == null) {
                    continue;
                }
                final Version current = section.current;
                final boolean newer =
                        current == null
                                || current.generation()
                                        > state.lods.coveredGeneration(
                                                world.world(), section.position);
                if (newer) {
                    ++behind;
                }
                final boolean override =
                        newer
                                && current != null
                                && current.opaqueModel() != null
                                && distance(eye, section.position) <= reach;
                if (override) {
                    overrides.get(section.owner).add(section.position);
                    if (section.opaqueInstance == null
                            && !current.opaqueModel().getMeshDataList().isEmpty()) {
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
                } else if (section.opaqueInstance != null) {
                    removing.add(new Removal(section.owner, section.opaqueInstance));
                    section.opaqueInstance = null;
                    if (current != null) {
                        shadows.addSection(current.origin());
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
        lodBehind = behind;
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
            int resident = 0;
            long triangles = 0;
            int failed = 0;
            for (Section section : sections.values()) {
                if (section.failed) {
                    failed += 1;
                }
                if (section.current != null) {
                    resident += 1;
                    triangles += section.current.triangles();
                }
            }
            return new Stats(sections.size(), resident, triangles, inFlight.get(), failed);
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
        } finally {
            lock.unlock();
        }
        baked.clear();
        urgent.clear();
        releasing.clear();
        removing.clear();
    }
}
