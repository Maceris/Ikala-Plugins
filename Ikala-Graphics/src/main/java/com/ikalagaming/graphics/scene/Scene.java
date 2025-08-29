package com.ikalagaming.graphics.scene;

import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.GraphicsPlugin;
import com.ikalagaming.graphics.frontend.Texture;
import com.ikalagaming.graphics.graph.MaterialCache;
import com.ikalagaming.graphics.graph.Model;
import com.ikalagaming.graphics.scene.lights.SceneLights;
import com.ikalagaming.util.SafeResourceLoader;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector4f;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
    private Texture skyboxTexture;

    /** The diffuse color of the skybox, for use when there is no texture. */
    private final Vector4f skyboxDiffuse;

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
        camera = new Camera();
        fog = new Fog();
        skyboxDiffuse = new Vector4f(0.65f, 0.65f, 0.65f, 1f);
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
                    SafeResourceLoader.getStringFormatted(
                            "TOO_MANY_ENTITIES",
                            GraphicsPlugin.getResourceBundle(),
                            String.valueOf(Model.MAX_ENTITIES),
                            model.getId()));
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
     * Update the projection matrix for when the screen is resized.
     *
     * @param width The new screen width, in pixels.
     * @param height The new screen height, in pixels.
     */
    public void resize(int width, int height) {
        projection.updateProjMatrix(width, height);
    }

    /**
     * Set the new skybox texture.
     *
     * @param texture The texture, or null if the skybox should be untextured.
     */
    public void setSkyboxTexture(Texture texture) {
        if (skyboxTexture != null) {
            GraphicsManager.getDeletionQueue().add(skyboxTexture);
        }
        skyboxTexture = texture;
    }
}
