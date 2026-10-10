package com.ikalagaming.graphics.vulkan;

import com.ikalagaming.graphics.InstanceHandle;
import com.ikalagaming.graphics.MeshHandle;
import com.ikalagaming.graphics.MeshKind;
import com.ikalagaming.graphics.bake.BakeSource;
import com.ikalagaming.graphics.bake.BakedVertex;
import com.ikalagaming.graphics.bake.SectionBaker;
import com.ikalagaming.graphics.graph.Material;
import com.ikalagaming.graphics.graph.MeshData;
import com.ikalagaming.graphics.graph.Model;

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
import java.util.List;
import java.util.Map;
import java.util.Queue;
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
 * <p>Submitting, rebaking and removing are safe from any thread. {@link #update(VulkanState)} runs
 * on the render thread at the start of each frame.
 */
@Slf4j
public class SectionManager {

    /**
     * Everything a bake needs, resolved on the thread that submitted it.
     *
     * @param origin The section's world position.
     * @param sources The mesh each placement puts down.
     * @param materials The material index of each placement.
     * @param packedPositions Each placement's cell.
     * @param rotations Each placement's rotation.
     * @param faceMasks The faces each placement's neighbors hide.
     * @param userData Each placement's user data.
     */
    public record Request(
            @NonNull Vector3dc origin,
            @NonNull BakeSource @NonNull [] sources,
            int @NonNull [] materials,
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
     * @param model The model holding its meshes, which the instance places.
     * @param meshes Its meshes, one per bucket that had anything.
     * @param triangles How many triangles it has.
     */
    private record Version(
            long generation,
            @NonNull Vector3dc origin,
            @NonNull Model model,
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

        Section(String owner) {
            this.owner = owner;
        }
    }

    /** How a worker thread is named. */
    private static final String WORKER_NAME = "Section baker ";

    /** Guards the sections. */
    private final ReentrantLock lock = new ReentrantLock();

    /** Every live section, by id. */
    private final Map<Long, Section> sections = new HashMap<>();

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

    /** Bakes submitted and not yet back. */
    private final AtomicInteger inFlight = new AtomicInteger();

    /** The worker threads. */
    private final ExecutorService workers;

    /** Create the manager and its worker threads. */
    public SectionManager() {
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
            sections.put(id, new Section(owner));
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
        lock.lock();
        try {
            Section section = sections.get(id);
            if (section == null) {
                return;
            }
            generation = ++section.requested;
        } finally {
            lock.unlock();
        }
        inFlight.incrementAndGet();
        try {
            workers.execute(() -> bake(id, generation, request));
        } catch (RejectedExecutionException e) {
            inFlight.decrementAndGet();
            log.debug("Not baking section {} of {}, the renderer is shutting down", id, owner);
        }
    }

    /**
     * Bake on a worker thread.
     *
     * @param id The section.
     * @param generation Which bake this is.
     * @param request What to bake.
     */
    private void bake(long id, long generation, @NonNull Request request) {
        try {
            SectionBaker.Result result =
                    SectionBaker.bake(
                            request.sources(),
                            request.materials(),
                            request.packedPositions(),
                            request.rotations(),
                            request.faceMasks(),
                            request.userData());
            baked.add(new Baked(id, generation, new Vector3d(request.origin()), result));
        } catch (RuntimeException e) {
            log.error("Baking section {} failed", id, e);
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
            forget(section);
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
            sections.values().removeIf(section -> section.owner.equals(owner));
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
     * Upload finished bakes and swap in the ones that are resident. Render thread only, at the
     * start of a frame, before the instance table is recorded.
     *
     * @param state The Vulkan state.
     */
    public void update(@NonNull VulkanState state) {
        Baked done;
        while ((done = baked.poll()) != null) {
            upload(state, done);
        }
        swapResident(state);
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
        final Model model = new Model("section-" + done.id() + "-" + done.generation());
        final List<MeshHandle> meshes = new ArrayList<>();
        for (Material.Transparency transparency : Material.Transparency.values()) {
            final SectionBaker.Bucket bucket = done.result().get(transparency);
            if (bucket == null) {
                continue;
            }
            final MeshHandle handle = register(state, owner, MeshKind.baked(transparency), bucket);
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
            model.getMeshDataList().add(meshData);
            meshes.add(handle);
        }
        final Version version =
                new Version(
                        done.generation(),
                        done.origin(),
                        model,
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
                if (section.current != null) {
                    releasing.addAll(section.current.meshes());
                }
                section.current = pending;
                section.pending = null;
                if (!pending.meshes().isEmpty()) {
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
     * Totals for the debug window.
     *
     * @param sections How many sections there are.
     * @param resident How many have a bake drawn.
     * @param triangles How many triangles the drawn bakes have.
     * @param baking How many bakes are on the workers.
     */
    public record Stats(int sections, int resident, long triangles, int baking) {}

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
            for (Section section : sections.values()) {
                if (section.current != null) {
                    resident += 1;
                    triangles += section.current.triangles();
                }
            }
            return new Stats(sections.size(), resident, triangles, inFlight.get());
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
            sections.clear();
        } finally {
            lock.unlock();
        }
        baked.clear();
        releasing.clear();
        removing.clear();
    }
}
