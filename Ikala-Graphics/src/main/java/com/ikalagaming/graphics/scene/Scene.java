package com.ikalagaming.graphics.scene;

import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.TextureHandle;
import com.ikalagaming.graphics.graph.MaterialCache;
import com.ikalagaming.graphics.graph.MeshData;
import com.ikalagaming.graphics.graph.Model;
import com.ikalagaming.graphics.scene.debug.DebugVisualizers;
import com.ikalagaming.graphics.scene.lights.SceneLights;
import com.ikalagaming.graphics.vulkan.DeletionQueue;
import com.ikalagaming.graphics.vulkan.SharedBuffer;
import com.ikalagaming.graphics.vulkan.VulkanState;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector4f;

import java.util.List;
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

    /** The observer while it is frozen, or null while it follows the camera. */
    @Getter(AccessLevel.NONE)
    private volatile Observer frozenObserver;

    /**
     * Set up a new scene.
     *
     * @param state The Vulkan state.
     * @param width The screen width, in pixels.
     * @param height The screen height, in pixels.
     */
    public Scene(@NonNull VulkanState state, int width, int height) {
        modelMap = new ConcurrentHashMap<>();
        projection = new Projection(width, height);
        materialCache = new MaterialCache(state);
        sceneLights = new SceneLights();
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
     * Add an entity to the scene. The model must have a valid model that is in the scene, but this
     * will handle adding the entity to the model's entity list.
     *
     * @param entity The entity to add.
     * @see #addModel(Model)
     */
    public void addEntity(@NonNull Entity entity) {
        Model model = entity.getModel();
        List<Entity> entityList = model.getEntitiesList();
        // TODO(ches) handle duplicate entities

        if (entityList.size() >= Model.MAX_ENTITIES) {
            log.error(
                    "Reached limit ({}) of entities for model {}",
                    Model.MAX_ENTITIES,
                    model.getId());
            return;
        }
        model.getEntitiesList().add(entity);
    }

    /**
     * Remove an entity from the scene.
     *
     * @param entity The entity to add.
     * @see #addModel(Model)
     */
    public void removeEntity(@NonNull Entity entity) {
        Model model = entity.getModel();
        model.getEntitiesList().remove(entity);
    }

    /**
     * Add a model to the model map.
     *
     * @param model The model to add.
     */
    public void addModel(@NonNull Model model) {
        modelMap.put(model.getId(), model);
    }

    /**
     * Queue up deletion of every GPU resource the scene owns, which are the model and material
     * buffers. Textures belong to the plugins that loaded them. The scene should not be rendered
     * afterward.
     */
    public void cleanup() {
        DeletionQueue deletionQueue = GraphicsManager.getDeletionQueue();
        Consumer<SharedBuffer> delete =
                buffer -> {
                    if (buffer != null) {
                        deletionQueue.add(buffer);
                    }
                };
        for (Model model : modelMap.values()) {
            delete.accept(model.getAnimationBuffer());
            delete.accept(model.getEntityAnimationOffsetsBuffer());
            delete.accept(model.getModelMatricesBuffer());
            delete.accept(model.getMaterialOverridesBuffer());
            for (MeshData mesh : model.getMeshDataList()) {
                delete.accept(mesh.getBoneWeightBuffer());
                delete.accept(mesh.getVertexBuffer());
                delete.accept(mesh.getAnimationTargetBuffer());
                delete.accept(mesh.getIndexBuffer());
                delete.accept(mesh.getDrawIndirectBuffer());
            }
        }
        modelMap.clear();
        materialCache.cleanup();
        setSkyboxTexture(null);
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
