package com.ikalagaming.graphics.scene;

import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.TextureHandle;
import com.ikalagaming.graphics.graph.MaterialCache;
import com.ikalagaming.graphics.graph.MeshData;
import com.ikalagaming.graphics.graph.Model;
import com.ikalagaming.graphics.scene.debug.DebugVisualizers;
import com.ikalagaming.graphics.scene.lights.LightRegistry;
import com.ikalagaming.graphics.scene.lights.SceneLights;
import com.ikalagaming.graphics.vulkan.DeletionQueue;
import com.ikalagaming.graphics.vulkan.GeometryArena;
import com.ikalagaming.graphics.vulkan.InstanceTable;
import com.ikalagaming.graphics.vulkan.SharedBuffer;
import com.ikalagaming.graphics.vulkan.VulkanInstance;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector4f;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** A scene to be rendered, containing the items and lighting. */
@Getter
@Slf4j
public class Scene {
    /**
     * The camera for the scene.
     *
     * @return The scene's camera.
     */
    private final Camera camera;

    /**
     * The fog for the scene.
     *
     * @param fog The new fog settings to use.
     * @return The current fog settings.
     */
    private final Fog fog;

    /**
     * The material cache to use for this scene.
     *
     * @return The material cache.
     */
    private final MaterialCache materialCache;

    /** The list of models, mapped by ID. */
    private final Map<String, Model> modelMap;

    /**
     * The projection matrix for the scene.
     *
     * @return The projection matrix information.
     */
    private final Projection projection;

    /**
     * The scene lighting information.
     *
     * @param sceneLights The lights to render with.
     * @return The lights used in the scene.
     */
    private final SceneLights sceneLights;

    /**
     * The point lights and spotlights plugins placed, through {@link
     * com.ikalagaming.graphics.Lights}.
     *
     * @return The light registry.
     */
    private final LightRegistry lightRegistry;

    /**
     * The texture of the skybox, which may be null if we want the sky to be just a single diffuse
     * color.
     */
    private TextureHandle skyboxTexture;

    /** The diffuse color of the skybox, for use when there is no texture. */
    private final Vector4f skyboxDiffuse;

    /**
     * Which built-in debug visualizations to draw.
     *
     * @return The debug visualization settings.
     */
    private final DebugVisualizers debugVisualizers;

    /**
     * How far the view reaches by default, in metres: twelve 16 m sections.
     *
     * @see #getViewDistance()
     */
    public static final double DEFAULT_VIEW_DISTANCE = 12 * 16;

    /**
     * How far the view reaches, in metres. Lights are sorted into clusters out to here; later the
     * shadows and level of detail follow it too. Things past it can still be drawn, out to the far
     * plane.
     *
     * @return The view distance.
     */
    private volatile double viewDistance;

    /** The observer while it is frozen, or null while it follows the camera. */
    @Getter(AccessLevel.NONE)
    private volatile Observer frozenObserver;

    /**
     * Set up a new scene.
     *
     * @param width The screen width, in pixels.
     * @param height The screen height, in pixels.
     */
    public Scene(int width, int height) {
        modelMap = new ConcurrentHashMap<>();
        projection = new Projection(width, height);
        materialCache = new MaterialCache();
        sceneLights = new SceneLights();
        lightRegistry = new LightRegistry();
        viewDistance = DEFAULT_VIEW_DISTANCE;
        camera = new Camera();
        fog = new Fog();
        skyboxDiffuse = new Vector4f(0.65f, 0.65f, 0.65f, 1f);
        debugVisualizers = new DebugVisualizers();
        frozenObserver = null;
    }

    /**
     * The point of view for deciding what is visible, which level of detail to draw, and what to
     * stream in. Follows the camera unless it was frozen.
     *
     * @return The observer.
     * @see Observer
     */
    public Observer getObserver() {
        Observer frozen = frozenObserver;
        return frozen != null ? frozen : new Observer(camera, projection);
    }

    /**
     * Stop the observer following the camera, keeping its current point of view until {@link
     * #unfreezeObserver()}. Lets the camera inspect culling and streaming decisions from outside.
     */
    public void freezeObserver() {
        frozenObserver = new Observer(camera, projection);
    }

    /** Make the observer follow the camera again. */
    public void unfreezeObserver() {
        frozenObserver = null;
    }

    /**
     * Whether the observer is frozen.
     *
     * @return True if the observer is not following the camera.
     */
    public boolean isObserverFrozen() {
        return frozenObserver != null;
    }

    /**
     * Add a model to the model map. A different model already added with the same ID is replaced,
     * and its GPU resources are released.
     *
     * @param model The model to add.
     */
    public void addModel(@NonNull Model model) {
        Model previous = modelMap.put(model.getId(), model);
        if (previous != null && previous != model) {
            releaseResources(previous);
        }
    }

    /**
     * Queue up deletion of a model's GPU resources: its instances, its meshes in the shared
     * geometry buffers, and its own buffers.
     *
     * @param model The model.
     */
    private static void releaseResources(@NonNull Model model) {
        DeletionQueue deletionQueue = GraphicsManager.getDeletionQueue();
        Consumer<SharedBuffer> delete =
                buffer -> {
                    if (buffer != null) {
                        deletionQueue.add(buffer);
                    }
                };
        VulkanInstance renderer = GraphicsManager.getRenderInstance();
        GeometryArena geometry = renderer == null ? null : renderer.getState().geometry;
        InstanceTable instances = renderer == null ? null : renderer.getState().instances;
        if (instances != null) {
            // Whichever plugins placed them, the scene no longer draws them
            instances.getRegistry().removeAllOf(model).forEach(instances::retire);
        }
        delete.accept(model.getAnimationBuffer());
        delete.accept(model.getEntityAnimationOffsetsBuffer());
        for (MeshData mesh : model.getMeshDataList()) {
            delete.accept(mesh.getBoneWeightBuffer());
            delete.accept(mesh.getAnimationTargetBuffer());
            if (geometry != null) {
                // Whichever plugin registered it, the scene no longer draws it
                geometry.release(mesh.getMesh());
            }
        }
    }

    /**
     * Queue up deletion of every GPU resource the scene owns, which are the models' meshes and
     * buffers, and the material buffers. Textures belong to the plugins that loaded them. The scene
     * should not be rendered afterward.
     */
    public void cleanup() {
        for (Model model : modelMap.values()) {
            releaseResources(model);
        }
        modelMap.clear();
        materialCache.cleanup();
        setSkyboxTexture(null);
    }

    /**
     * Change how far the view reaches.
     *
     * @param viewDistance The view distance, in metres, kept within the far plane.
     * @throws IllegalArgumentException If it isn't positive.
     */
    public void setViewDistance(double viewDistance) {
        if (viewDistance <= 0) {
            throw new IllegalArgumentException("The view distance must be positive");
        }
        this.viewDistance = Math.min(viewDistance, Projection.Z_FAR);
    }

    /**
     * Update the projection matrix for when the screen is resized.
     *
     * @param width The new screen width, in pixels.
     * @param height The new screen height, in pixels.
     */
    public void resize(int width, int height) {
        projection.updateProjMatrix(width, height);
    }

    /**
     * Set the new skybox texture. The scene doesn't take ownership: the plugin that loaded the
     * texture releases it.
     *
     * @param texture The texture, or null if the skybox should be untextured.
     */
    public void setSkyboxTexture(TextureHandle texture) {
        skyboxTexture = texture;
    }
}
