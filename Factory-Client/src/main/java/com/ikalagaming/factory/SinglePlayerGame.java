package com.ikalagaming.factory;

import com.ikalagaming.factory.save.SaveSummary;
import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.RenderConfig;
import com.ikalagaming.graphics.graph.Model;
import com.ikalagaming.graphics.scene.ModelLoader;
import com.ikalagaming.graphics.scene.lights.AmbientLight;
import com.ikalagaming.launcher.PluginFolder;
import com.ikalagaming.util.SafeResourceLoader;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector3d;
import org.joml.Vector3f;

/**
 * Starts a single player game: a local server, and for now a placeholder scene. The save is not
 * loaded into the server yet.
 */
@Slf4j
public final class SinglePlayerGame {

    /**
     * Start playing a save.
     *
     * @param save The save.
     */
    public static void start(@NonNull SaveSummary save) {
        log.info(
                "Starting {} ({}, {} seed {})",
                save.name(),
                save.folder(),
                save.worldType(),
                save.seed());
        // TODO(ches) load the save into the server and connect to it, FACT-16 follow up
        if (!LocalServer.start()) {
            log.warn(
                    SafeResourceLoader.getString(
                            "NO_LOCAL_SERVER", FactoryClientPlugin.getResourceBundle()));
        }

        Model cubeModel =
                ModelLoader.loadModel(
                        new ModelLoader.ModelLoadRequest(
                                "cube",
                                FactoryClientPlugin.PLUGIN_NAME,
                                "models/cube.obj",
                                GraphicsManager.getScene().getMaterialCache(),
                                false));
        GraphicsManager.getScene().addModel(cubeModel);
        GraphicsManager.forPlugin(FactoryClientPlugin.PLUGIN_NAME)
                .instances()
                .place(cubeModel, 0, 0, 0);
        GraphicsManager.getScene()
                .getSceneLights()
                .setAmbientLight(new AmbientLight(new Vector3f(1.0f, 1.0f, 1.0f), 0.6f));
        GraphicsManager.forPlugin(FactoryClientPlugin.PLUGIN_NAME)
                .lights()
                .point(new Vector3d(2.0, 1.1, 0.0), new Vector3f(1.0f, 0.0f, 0.0f), 1.0f);
        GraphicsManager.swapPipeline(RenderConfig.WORLD);
        GraphicsManager.getScene().getCamera().setPosition(1.86f, 2.43f, 2.33f);
        GraphicsManager.getScene().getCamera().setRotation(0.64f, 5.62f);

        var texturePath =
                PluginFolder.getResource(
                                FactoryClientPlugin.PLUGIN_NAME,
                                PluginFolder.ResourceType.DATA,
                                "textures/skybox.png")
                        .getAbsolutePath();
        GraphicsManager.getScene()
                .setSkyboxTexture(
                        GraphicsManager.forPlugin(FactoryClientPlugin.PLUGIN_NAME)
                                .textures()
                                .load(texturePath));
    }

    /** Private constructor so this class is not instantiated. */
    private SinglePlayerGame() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
