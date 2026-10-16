package com.ikalagaming.graphics;

import com.ikalagaming.graphics.graph.Material;
import com.ikalagaming.graphics.graph.MaterialCache;
import com.ikalagaming.graphics.lod.ClusterMeshes;
import com.ikalagaming.graphics.lod.LodDag;
import com.ikalagaming.graphics.vulkan.LodBakes;
import com.ikalagaming.graphics.vulkan.LodRenderer;
import com.ikalagaming.graphics.vulkan.VulkanInstance;

import lombok.NonNull;
import org.joml.Quaterniond;
import org.joml.Quaternionfc;
import org.joml.Vector3dc;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws level of detail worlds, owned by one plugin. A world is a cluster DAG ({@link LodDag}) put
 * on the GPU whole. Each frame the GPU picks its cut from the observer, as {@link
 * com.ikalagaming.graphics.lod.LodCut} does, culls it, and draws it opaque with the scene and its
 * shadows, textured by projecting each material's texture along the triangle's main axis.
 *
 * <p>A model ({@link #addModel}) is a DAG put on the GPU once and drawn only by its instances
 * ({@link #place}): each is the model turned, scaled and placed, picks its own cut of the model's
 * clusters, and may draw some of the model's materials with others. Models aren't baked, nor
 * blended far out, since both would fix one look for every instance.
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
     * Put a world on the GPU. Its clusters are turned into meshes on the calling thread and
     * uploaded over the next frames, and it is drawn once they all are, with the error threshold in
     * {@link GraphicsSettings#lodThresholdPixels}.
     *
     * @param dag The world.
     * @return Its handle.
     * @throws IllegalStateException If the plugin was unloaded, or the renderer isn't running.
     */
    public LodHandle add(@NonNull LodDag dag) {
        context.checkOpen();
        final VulkanInstance renderer = renderer();
        // Baked clusters are drawn from tiles they get as they load, see LodBakes
        final LodBakes.Plan plan =
                LodBakes.plan(dag, GraphicsManager.getScene().getMaterialCache());
        final List<ClusterMeshes.Mesh> meshes = new ArrayList<>(dag.clusters().size());
        for (LodDag.Cluster cluster : dag.clusters()) {
            meshes.add(ClusterMeshes.of(cluster));
        }
        return new LodHandle(
                renderer.getState()
                        .lods
                        .add(
                                renderer.getState(),
                                context.getOwnerKey(),
                                dag,
                                meshes,
                                GraphicsManager.getSettings().lodThresholdPixels,
                                plan));
    }

    /**
     * Have this plugin's sections feed a live level of detail world, from now on and including the
     * sections it already has, which are baked again. Their opaque blocks are then drawn by level
     * of detail: region cells are rebuilt in the background as sections are submitted, rebaked and
     * removed. A section near the observer whose newest bake isn't in the level of detail yet, as
     * right after an edit, draws its own opaque blocks meanwhile, within {@link
     * GraphicsSettings#lodOverrideDistance}. Sections must sit on the {@value
     * Sections#SECTION_SIZE}-block grid to feed it. A plugin has one live world; removing it with
     * {@link #remove} has its sections draw their opaque blocks themselves again.
     *
     * @return The live world's handle, the same one on later calls.
     * @throws IllegalStateException If the plugin was unloaded, or the renderer isn't running.
     */
    public LodHandle fromSections() {
        context.checkOpen();
        final VulkanInstance renderer = renderer();
        return new LodHandle(
                renderer.getState()
                        .sections
                        .startLive(
                                renderer.getState(),
                                context.getOwnerKey(),
                                GraphicsManager.getSettings().lodThresholdPixels));
    }

    /**
     * How a live world is keeping up with its sections.
     *
     * @param world The world.
     * @return Its numbers, or null if the handle is stale or the renderer isn't running.
     */
    public LodRenderer.LiveStats liveStats(@NonNull LodHandle world) {
        final LodRenderer lods = lods();
        return lods == null ? null : lods.liveStats(world.id());
    }

    /**
     * Set the most error, in pixels, a world's drawn clusters may have, from the next frame on.
     * Nothing happens if the handle is stale.
     *
     * @param world The world.
     * @param pixels The error threshold.
     */
    public void setThreshold(@NonNull LodHandle world, double pixels) {
        final LodRenderer lods = lods();
        if (lods != null) {
            lods.setThreshold(world.id(), pixels);
        }
    }

    /**
     * Show or hide a world, from the next frame on. Worlds start out shown. Nothing happens if the
     * handle is stale.
     *
     * @param world The world.
     * @param visible Whether it is drawn.
     */
    public void setVisible(@NonNull LodHandle world, boolean visible) {
        final LodRenderer lods = lods();
        if (lods != null) {
            lods.setVisible(world.id(), visible);
        }
    }

    /**
     * What the GPU drew of every world, over all plugins, a few frames ago.
     *
     * @return The counts, all zero if the renderer isn't running.
     */
    public LodRenderer.Stats stats() {
        final LodRenderer lods = lods();
        return lods == null ? LodRenderer.Stats.NONE : lods.getStats();
    }

    /**
     * What baked clusters' tiles take on the GPU, over all plugins.
     *
     * @return The tiles loaded and the pages, all zero if the renderer isn't running.
     */
    public LodBakes.Stats bakeStats() {
        final VulkanInstance renderer = GraphicsManager.getRenderInstance();
        return renderer == null || renderer.getState().lodBakes == null
                ? LodBakes.Stats.NONE
                : renderer.getState().lodBakes.stats();
    }

    /**
     * The running renderer's worlds.
     *
     * @return The worlds, or null if it isn't running.
     */
    private static LodRenderer lods() {
        final VulkanInstance renderer = GraphicsManager.getRenderInstance();
        return renderer == null ? null : renderer.getState().lods;
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
     * Put a model on the GPU, which draws nothing until instances of it are placed. Its clusters
     * are turned into meshes on the calling thread and uploaded as its instances need them.
     *
     * @param dag The model, built without baking or blending materials.
     * @return Its handle, which {@link #remove(LodHandle)} takes off the GPU with its instances.
     * @throws IllegalStateException If the plugin was unloaded, or the renderer isn't running.
     * @throws IllegalArgumentException If it has more clusters than an instance can draw.
     */
    public LodHandle addModel(@NonNull LodDag dag) {
        context.checkOpen();
        final VulkanInstance renderer = renderer();
        final List<ClusterMeshes.Mesh> meshes = new ArrayList<>(dag.clusters().size());
        for (LodDag.Cluster cluster : dag.clusters()) {
            meshes.add(ClusterMeshes.of(cluster));
        }
        return new LodHandle(
                renderer.getState()
                        .lods
                        .addModel(
                                renderer.getState(),
                                context.getOwnerKey(),
                                dag,
                                meshes,
                                GraphicsManager.getSettings().lodThresholdPixels));
    }

    /**
     * Place an instance of a model: the model's frame turned, scaled and moved into the world.
     *
     * @param model The model.
     * @param position Where the model's frame goes.
     * @param rotation Its turn.
     * @param scale Its scale, the same along every axis.
     * @return Its handle.
     * @throws IllegalStateException If the plugin was unloaded, or the renderer isn't running.
     * @throws IllegalArgumentException If the model is gone.
     */
    public LodInstanceHandle place(
            @NonNull LodHandle model,
            @NonNull Vector3dc position,
            @NonNull Quaternionfc rotation,
            float scale) {
        context.checkOpen();
        return new LodInstanceHandle(
                renderer()
                        .getState()
                        .lods
                        .place(
                                context.getOwnerKey(),
                                model.id(),
                                new double[] {position.x(), position.y(), position.z()},
                                new Quaterniond(rotation),
                                scale));
    }

    /**
     * Move an instance. Nothing happens if the handle is stale.
     *
     * @param instance The instance.
     * @param position Where the model's frame goes.
     * @param rotation Its turn.
     * @param scale Its scale.
     */
    public void move(
            @NonNull LodInstanceHandle instance,
            @NonNull Vector3dc position,
            @NonNull Quaternionfc rotation,
            float scale) {
        final LodRenderer lods = lods();
        if (lods != null) {
            lods.move(
                    instance.id(),
                    new double[] {position.x(), position.y(), position.z()},
                    new Quaterniond(rotation),
                    scale);
        }
    }

    /**
     * Draw one of an instance's model's materials with another, as {@link Instances#setMaterial}
     * does for meshes. Both must be in the scene's material cache. Nothing happens if the handle is
     * stale.
     *
     * @param instance The instance.
     * @param original The model's material.
     * @param replacement What to draw it with, or null to draw it as the model does again.
     * @throws IllegalStateException If the instance would replace more than {@link
     *     com.ikalagaming.graphics.vulkan.LodCullMath#MAX_REMAPS} materials.
     */
    public void setMaterial(
            @NonNull LodInstanceHandle instance, @NonNull Material original, Material replacement) {
        final LodRenderer lods = lods();
        if (lods != null) {
            final MaterialCache cache = GraphicsManager.getScene().getMaterialCache();
            lods.setInstanceMaterial(
                    instance.id(),
                    cache.getMaterialIndex(original),
                    replacement == null ? -1 : cache.getMaterialIndex(replacement));
        }
    }

    /**
     * Show or hide an instance. Nothing happens if the handle is stale.
     *
     * @param instance The instance.
     * @param visible Whether it is drawn.
     */
    public void setVisible(@NonNull LodInstanceHandle instance, boolean visible) {
        final LodRenderer lods = lods();
        if (lods != null) {
            lods.setInstanceVisible(instance.id(), visible);
        }
    }

    /**
     * Remove an instance. Nothing happens if the handle is stale.
     *
     * @param instance The instance.
     */
    public void remove(@NonNull LodInstanceHandle instance) {
        final LodRenderer lods = lods();
        if (lods != null) {
            lods.removeInstance(instance.id(), context.getOwnerKey());
        }
    }

    /**
     * Take a world off the GPU. Nothing happens if the handle is stale.
     *
     * @param world The world.
     */
    public void remove(@NonNull LodHandle world) {
        final VulkanInstance renderer = GraphicsManager.getRenderInstance();
        if (renderer != null && renderer.getState().lods != null) {
            renderer.getState().sections.stopLive(context.getOwnerKey(), world.id());
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
