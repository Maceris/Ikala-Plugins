package com.ikalagaming.graphics;

import com.ikalagaming.graphics.bake.BakeSource;
import com.ikalagaming.graphics.bake.Faces;
import com.ikalagaming.graphics.graph.MeshData;
import com.ikalagaming.graphics.graph.Model;
import com.ikalagaming.graphics.vulkan.GeometryArena;
import com.ikalagaming.graphics.vulkan.VulkanInstance;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector3fc;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import javax.annotation.Nullable;

/**
 * Registers and releases meshes for one plugin. Every mesh registered here is owned by the plugin's
 * {@link GraphicsContext}, and is released automatically when the plugin unloads if it wasn't
 * released before then. A plugin can only release its own meshes.
 *
 * <p>Meshes live in the GPU's shared geometry buffers. Everything here is safe from any thread, and
 * registering doesn't wait for the GPU: the data is checked and copied to staging memory right
 * away, and the render thread copies it in at the start of a later frame. Until then the handle is
 * valid but not {@linkplain #isResident(MeshHandle) resident}, and the mesh isn't drawn.
 *
 * @see GraphicsContext#meshes()
 */
@Slf4j
public final class Meshes {

    /** The context these meshes belong to. */
    private final GraphicsContext context;

    /**
     * Create the meshes API for a context.
     *
     * @param context The owning context.
     */
    Meshes(@NonNull GraphicsContext context) {
        this.context = context;
    }

    /**
     * Register a mesh from raw data. The data is copied before this returns.
     *
     * @param vertexData The vertices in the scene's vertex format ({@link
     *     MeshData#VERTEX_SIZE_IN_FLOATS} floats each).
     * @param indices The triangle indices, three per triangle.
     * @param aabbMin The minimum corner of the mesh's bounding box.
     * @param aabbMax The maximum corner of the mesh's bounding box.
     * @return The handle, which becomes resident once uploaded.
     * @throws IllegalArgumentException If the data isn't whole vertices and triangles, or an index
     *     points past the last vertex.
     * @throws IllegalStateException If the plugin was unloaded, or the renderer isn't running.
     */
    public MeshHandle register(
            float @NonNull [] vertexData,
            int @NonNull [] indices,
            @NonNull Vector3fc aabbMin,
            @NonNull Vector3fc aabbMax) {
        context.checkOpen();
        final VulkanInstance renderer = renderer();
        ByteBuffer vertexBuffer = MemoryUtil.memAlloc(vertexData.length * Float.BYTES);
        ByteBuffer indexBuffer = MemoryUtil.memAlloc(indices.length * Integer.BYTES);
        try {
            vertexBuffer.asFloatBuffer().put(vertexData);
            indexBuffer.asIntBuffer().put(indices);
            return renderer.getState()
                    .geometry
                    .register(
                            renderer.getState(),
                            context.getOwnerKey(),
                            vertexBuffer,
                            indexBuffer,
                            aabbMin,
                            aabbMax);
        } finally {
            MemoryUtil.memFree(indexBuffer);
            MemoryUtil.memFree(vertexBuffer);
        }
    }

    /**
     * Register one of a model's meshes, and remember its handle on the mesh.
     *
     * @param mesh The mesh.
     * @return The handle, which becomes resident once uploaded.
     * @throws IllegalArgumentException If the mesh data is broken.
     * @throws IllegalStateException If the plugin was unloaded, or the renderer isn't running.
     */
    public MeshHandle register(@NonNull MeshData mesh) {
        MeshHandle handle =
                register(
                        mesh.getVertexData(),
                        mesh.getIndices(),
                        mesh.getAabbMin(),
                        mesh.getAabbMax());
        mesh.setMesh(handle);
        return handle;
    }

    /**
     * Register one of a model's meshes so that sections can be baked from it, as well as placed
     * like any other mesh. A CPU copy is kept, with its face groups worked out against a box: each
     * triangle lying flat on a face of the box, facing out, can be left out of a baked section when
     * a neighbor hides that face. See {@link Sections}.
     *
     * @param mesh The mesh, which mustn't be animated.
     * @param boxMin The minimum corner of the box one placement fills, like {@code (0, 0, 0)}.
     * @param boxMax The maximum corner of the box, like {@code (1, 1, 1)}. It must be a cube.
     * @return The handle, which becomes resident once uploaded.
     * @throws IllegalArgumentException If the mesh data is broken or animated, or the box isn't a
     *     cube.
     * @throws IllegalStateException If the plugin was unloaded, or the renderer isn't running.
     */
    public MeshHandle registerBakeable(
            @NonNull MeshData mesh, @NonNull Vector3fc boxMin, @NonNull Vector3fc boxMax) {
        context.checkOpen();
        final BakeSource source = BakeSource.derive(mesh, boxMin, boxMax);
        final MeshHandle handle = register(mesh);
        renderer().getState().bakeSources.put(context.getOwnerKey(), handle, source);
        return handle;
    }

    /**
     * What a mesh registered for baking looks like from each side, for working out which faces its
     * neighbors hide with {@link Faces}.
     *
     * @param mesh The mesh.
     * @return Its face groups and coverage, or null if it wasn't registered for baking or was
     *     released.
     */
    @Nullable public BakeSource faceInfo(@Nullable MeshHandle mesh) {
        VulkanInstance renderer = GraphicsManager.getRenderInstance();
        return renderer == null || renderer.getState().bakeSources == null
                ? null
                : renderer.getState().bakeSources.get(mesh);
    }

    /**
     * Register every mesh of a model, and set up its animation data if it is animated, so it can be
     * added to the scene. Call this once per model, after it is fully loaded.
     *
     * @param model The model.
     * @throws IllegalArgumentException If a mesh's data is broken.
     * @throws IllegalStateException If the plugin was unloaded, or the renderer isn't running.
     */
    public void register(@NonNull Model model) {
        context.checkOpen();
        for (MeshData mesh : model.getMeshDataList()) {
            register(mesh);
        }
        renderer().initializeAnimation(model);
    }

    /**
     * Release a mesh this plugin owns. The handle, and any copies of it, become stale, and it stops
     * being drawn. Nothing happens if the handle is null or already stale. Releasing another
     * plugin's mesh is not allowed, and is ignored with a warning.
     *
     * @param mesh The mesh to release.
     */
    public void release(@Nullable MeshHandle mesh) {
        if (mesh == null) {
            return;
        }
        GeometryArena geometry = geometry(mesh);
        if (geometry == null) {
            return;
        }
        final String owner = geometry.getRegistry().ownerOf(mesh);
        if (owner == null) {
            return;
        }
        if (!owner.equals(context.getOwnerKey())) {
            log.warn(
                    "{} tried to release a mesh owned by {}, ignoring it",
                    context.getOwnerKey(),
                    owner);
            return;
        }
        geometry.release(mesh);
        GraphicsManager.getRenderInstance().getState().bakeSources.remove(mesh);
    }

    /**
     * Release every mesh of a model that this plugin owns.
     *
     * @param model The model.
     */
    public void release(@NonNull Model model) {
        for (MeshData mesh : model.getMeshDataList()) {
            release(mesh.getMesh());
        }
    }

    /**
     * Whether a mesh has been uploaded, so it is drawn.
     *
     * @param mesh The handle to check.
     * @return False if the handle is null, stale, or still uploading.
     */
    public boolean isResident(@Nullable MeshHandle mesh) {
        GeometryArena geometry = geometry(mesh);
        return geometry != null && geometry.getRegistry().isResident(mesh);
    }

    /**
     * Whether a handle still refers to a live mesh, which may still be uploading.
     *
     * @param mesh The handle to check.
     * @return False if the handle is null or stale.
     */
    public boolean isValid(@Nullable MeshHandle mesh) {
        GeometryArena geometry = geometry(mesh);
        return geometry != null && geometry.getRegistry().isValid(mesh);
    }

    /**
     * The shared geometry buffers a mesh lives in, if the renderer is running.
     *
     * @param mesh The mesh, whose kind picks the buffers.
     * @return The geometry arena, or null if the mesh is null or there is no renderer.
     */
    private static GeometryArena geometry(@Nullable MeshHandle mesh) {
        VulkanInstance renderer = GraphicsManager.getRenderInstance();
        return renderer == null || mesh == null ? null : renderer.getState().arenaFor(mesh.kind());
    }

    /**
     * Fetch the running renderer.
     *
     * @return The renderer.
     * @throws IllegalStateException If the renderer isn't running.
     */
    private static VulkanInstance renderer() {
        VulkanInstance renderer = GraphicsManager.getRenderInstance();
        if (renderer == null || renderer.getState().geometry == null) {
            throw new IllegalStateException("The renderer is not running");
        }
        return renderer;
    }
}
