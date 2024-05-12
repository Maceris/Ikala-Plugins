/*
 * NOTICE: This file is a modified version of contents from
 * https://github.com/lwjglgamedev/lwjglbook, which was licensed under Apache
 * v2.0. Changes have been made related to formatting, functionality, and
 * naming.
 */
package com.ikalagaming.graphics.scene;

import com.ikalagaming.graphics.graph.MaterialCache;
import com.ikalagaming.graphics.graph.Model;
import com.ikalagaming.graphics.scene.lights.SceneLights;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** A scene to be rendered, containing the items and lighting. */
@Getter
public class Scene {
    /**
     * The camera for the scene.
     *
     * @return The scene's camera.
     */
    private Camera camera;

    /**
     * The fog for the scene.
     *
     * @param fog The new fog settings to use.
     * @return The current fog settings.
     */
    @Setter @NonNull private Fog fog;

    /**
     * The material cache to use for this scene.
     *
     * @return The material cache.
     */
    private MaterialCache materialCache;

    /** The list of models, mapped by ID. */
    private Map<String, Model> modelMap;

    /**
     * The projection matrix for the scene.
     *
     * @return The projection matrix information.
     */
    private Projection projection;

    /**
     * The scene lighting information.
     *
     * @param sceneLights The lights to render with.
     * @return The lights used in the scene.
     */
    @NonNull private SceneLights sceneLights;

    /** A list of entities that we tried to add, but did not yet have models loaded. */
    private List<Entity> entityQueue;

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
            model.getEntitiesList().add(entity);
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
                model.getEntitiesList().add(current);
                iter.remove();
            }
        }
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
}
