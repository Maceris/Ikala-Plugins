package com.ikalagaming.graphics;

import com.ikalagaming.graphics.lod.ClusterMeshes;
import com.ikalagaming.graphics.lod.LodDag;
import com.ikalagaming.graphics.vulkan.VulkanInstance;

import lombok.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws level of detail worlds, owned by one plugin. A world is a cluster DAG ({@link LodDag}) put
 * on the GPU whole; its owner chooses which clusters are drawn, such as a cut from {@link
 * com.ikalagaming.graphics.lod.LodCut}, and they are drawn opaque with the scene and its shadows,
 * textured by projecting each material's texture along the triangle's main axis.
 *
 * <p>Safe from any thread.
 */
public final class Lods {

    /** The context these worlds belong to. */
    private final GraphicsContext context;

    /**
     * Create the API for a context.
     *
     * @param context The owning context.
     */
    Lods(@NonNull GraphicsContext context) {
        this.context = context;
    }

    /**
     * Put a world on the GPU, with nothing drawn yet. Its clusters are turned into meshes on the
     * calling thread and uploaded over the next frames.
     *
     * @param dag The world.
     * @return Its handle.
     * @throws IllegalStateException If the plugin was unloaded, or the renderer isn't running.
     */
    public LodHandle add(@NonNull LodDag dag) {
        context.checkOpen();
        final List<ClusterMeshes.Mesh> meshes = new ArrayList<>(dag.clusters().size());
        for (LodDag.Cluster cluster : dag.clusters()) {
            meshes.add(ClusterMeshes.of(cluster));
        }
        final VulkanInstance renderer = renderer();
        return new LodHandle(
                renderer.getState()
                        .lods
                        .add(renderer.getState(), context.getOwnerKey(), dag.getOrigin(), meshes));
    }

    /**
     * Choose which of a world's clusters are drawn, from the next frame on.
     *
     * @param world The world.
     * @param drawn Whether each cluster is drawn, by id.
     */
    public void setDrawn(@NonNull LodHandle world, boolean @NonNull [] drawn) {
        final VulkanInstance renderer = GraphicsManager.getRenderInstance();
        if (renderer != null && renderer.getState().lods != null) {
            renderer.getState().lods.setDrawn(world.id(), drawn);
        }
    }

    /**
     * Whether every cluster of a world has been uploaded, so a cut draws without holes.
     *
     * @param world The world.
     * @return False while any is still uploading, or if the handle is stale.
     */
    public boolean isResident(@NonNull LodHandle world) {
        final VulkanInstance renderer = GraphicsManager.getRenderInstance();
        return renderer != null
                && renderer.getState().lods != null
                && renderer.getState().lods.isResident(world.id());
    }

    /**
     * Take a world off the GPU. Nothing happens if the handle is stale.
     *
     * @param world The world.
     */
    public void remove(@NonNull LodHandle world) {
        final VulkanInstance renderer = GraphicsManager.getRenderInstance();
        if (renderer != null && renderer.getState().lods != null) {
            renderer.getState().lods.remove(world.id(), context.getOwnerKey());
        }
    }

    /**
     * The running renderer.
     *
     * @return The renderer.
     * @throws IllegalStateException If it isn't running.
     */
    private static VulkanInstance renderer() {
        VulkanInstance renderer = GraphicsManager.getRenderInstance();
        if (renderer == null || renderer.getState().lods == null) {
            throw new IllegalStateException("The renderer is not running");
        }
        return renderer;
    }
}
