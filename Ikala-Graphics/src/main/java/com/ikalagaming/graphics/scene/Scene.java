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

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
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

    /** A list of entities that we tried to add, but did not yet have models loaded. */
    private final List<Entity> entityQueue;

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
        entityQueue = Collections.synchronizedList(new LinkedList<>());
        skyboxDiffuse = new Vector4f(0.65f, 0.65f, 0.65f, 1f);
    }

    /**
     * Add an entity to the scene.
     *
     * @param entity The entity to add.
     * @see #addModel(Model)
     */
    public void addEntity(@NonNull Entity entity) {
        String modelId = entity.getModelID();
        Model model = modelMap.get(modelId);
        if (model == null) {
            entityQueue.add(entity);
        } else {
            addEntity(entity, model);
        }
    }

    /**
     * Remove an entity from the scene.
     *
     * @param entity The entity to add.
     * @see #addModel(Model)
     */
    public void removeEntity(@NonNull Entity entity) {
        String modelId = entity.getModelID();
        Model model = modelMap.get(modelId);
        if (model == null) {
            log.warn(
                    SafeResourceLoader.getStringFormatted(
                            "REMOVING_UNKNOWN_ENTITY",
                            GraphicsPlugin.getResourceBundle(),
                            entity.getEntityID(),
                            entity.getModelID()));
        } else {
            model.getEntitiesList().remove(entity);
        }
    }

    /**
     * Add a model to the model map.
     *
     * @param model The model to add.
     */
    public void addModel(@NonNull Model model) {
        modelMap.put(model.getId(), model);
        for (Iterator<Entity> iter = entityQueue.iterator(); iter.hasNext(); ) {
            Entity current = iter.next();
            if (model.getId().equals(current.getModelID())) {
                addEntity(current, model);
                iter.remove();
            }
        }
    }

    /**
     * Add entity to the model, unless we have reached the limit.
     *
     * @param entity The entity to add.
     * @param model The model to add the entity to.
     */
    private void addEntity(@NonNull Entity entity, @NonNull Model model) {
        List<Entity> entityList = model.getEntitiesList();
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
