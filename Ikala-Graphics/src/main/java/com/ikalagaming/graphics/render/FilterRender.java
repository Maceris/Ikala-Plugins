/*
 * NOTICE: This file is a modified version of contents from
 * https://github.com/lwjglgamedev/lwjglbook, which was licensed under Apache
 * v2.0. Changes have been made related to formatting, functionality, and
 * naming.
 */
package com.ikalagaming.graphics.render;

import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.GL_TRIANGLES;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_INT;
import static org.lwjgl.opengl.GL11.glBindTexture;
import static org.lwjgl.opengl.GL11.glDepthMask;
import static org.lwjgl.opengl.GL11.glDrawElements;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL20.GL_FRAGMENT_SHADER;
import static org.lwjgl.opengl.GL20.GL_VERTEX_SHADER;
import static org.lwjgl.opengl.GL30.glBindVertexArray;

import com.ikalagaming.graphics.GraphicsPlugin;
import com.ikalagaming.graphics.ShaderUniforms;
import com.ikalagaming.graphics.graph.QuadMesh;
import com.ikalagaming.graphics.graph.ShaderProgram;
import com.ikalagaming.graphics.graph.UniformsMap;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.launcher.PluginFolder;
import com.ikalagaming.launcher.PluginFolder.ResourceType;
import com.ikalagaming.plugins.config.ConfigManager;
import com.ikalagaming.plugins.config.PluginConfig;
import com.ikalagaming.util.SafeResourceLoader;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Handle post-processing filters. */
@Slf4j
public class FilterRender {

    /** A map of filter shaders that have been picked up from a filters folder. */
    private Map<String, ShaderProgram> shaders;

    /** A mesh for rendering the scene onto. */
    private QuadMesh quadMesh;

    /** The uniforms for the shader program. */
    private UniformsMap uniformsMap;

    /** Set up the filter renderer. */
    public FilterRender() {
        shaders = new HashMap<>();

        PluginConfig config = ConfigManager.loadConfig(GraphicsPlugin.PLUGIN_NAME);

        String folderPath = config.getString("filters-folder");
        File filtersFolder =
                PluginFolder.getResource(GraphicsPlugin.PLUGIN_NAME, ResourceType.DATA, folderPath);

        if (!filtersFolder.exists()) {
            log.warn(
                    SafeResourceLoader.getString(
                            "FILTERS_FOLDER_MISSING", GraphicsPlugin.getResourceBundle()),
                    filtersFolder.getAbsolutePath());
            return;
        }
        if (!folderPath.endsWith(File.separator)) {
            folderPath = folderPath.concat(File.separator);
        }

        List<ShaderProgram.ShaderModuleData> shaderModuleDataList = new ArrayList<>();

        for (File vert : filtersFolder.listFiles((dir, name) -> name.endsWith(".vert"))) {
            shaderModuleDataList.clear();
            String name = vert.getName();
            // Strip file ending
            name = name.substring(0, name.indexOf('.'));
            File frag = new File(filtersFolder, name.concat(".frag"));
            if (!frag.exists()) {
                log.debug(
                        SafeResourceLoader.getString(
                                "FILTER_MISSING_FRAGMENT", GraphicsPlugin.getResourceBundle()),
                        name);
                continue;
            }

            shaderModuleDataList.add(
                    new ShaderProgram.ShaderModuleData(
                            folderPath.concat(vert.getName()), GL_VERTEX_SHADER));
            shaderModuleDataList.add(
                    new ShaderProgram.ShaderModuleData(
                            folderPath.concat(frag.getName()), GL_FRAGMENT_SHADER));
            ShaderProgram fullProgram = new ShaderProgram(shaderModuleDataList);

            // Clean up names
            name = name.replaceAll("[^a-zA-Z0-9_-]+", "");
            shaders.put(name, fullProgram);
            log.debug(
                    SafeResourceLoader.getString(
                            "FILTER_REGISTERED", GraphicsPlugin.getResourceBundle()),
                    name);
        }

        final String defaultFilter = config.getString("default-filter");
        if (!shaders.containsKey(defaultFilter)) {
            log.warn(
                    SafeResourceLoader.getString(
                            "DEFAULT_FILTER_MISSING", GraphicsPlugin.getResourceBundle()),
                    defaultFilter);

            Render.configuration.setFilterNames(new String[0]);
            Render.configuration.setSelectedFilter(0);
            return;
        }
        quadMesh = new QuadMesh();
        createUniforms(defaultFilter);

        String[] names = shaders.keySet().toArray(new String[0]);

        int defaultIndex = 0;
        for (int i = 0; i < names.length; ++i) {
            if (defaultFilter.equals(names[i])) {
                defaultIndex = i;
                break;
            }
        }
        Render.configuration.setFilterNames(names);
        Render.configuration.setSelectedFilter(defaultIndex);
    }

    /** Clean up resources. */
    public void cleanup() {
        quadMesh.cleanup();
        shaders.forEach((name, program) -> program.cleanup());
        shaders.clear();
    }

    /**
     * Set up the uniforms for the shader program.
     *
     * @param defaultFilter The name of the default shader, which should have the standard uniforms.
     */
    private void createUniforms(@NonNull final String defaultFilter) {
        uniformsMap = new UniformsMap(shaders.get(defaultFilter).getProgramID());
        uniformsMap.createUniform(ShaderUniforms.Filter.SCREEN_TEXTURE);
    }

    /**
     * Render a scene.
     *
     * @param scene The scene we are rendering.
     * @param screenTexture The screen texture for post-processing.
     */
    public void render(@NonNull Scene scene, int screenTexture) {
        glDepthMask(false);

        String name =
                Render.configuration.getFilterNames()[Render.configuration.getSelectedFilter()];
        ShaderProgram program = shaders.get(name);

        program.bind();

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, screenTexture);

        uniformsMap.setUniform(ShaderUniforms.Filter.SCREEN_TEXTURE, 0);

        glBindVertexArray(quadMesh.getVaoID());
        glDrawElements(GL_TRIANGLES, quadMesh.getVertexCount(), GL_UNSIGNED_INT, 0);

        program.unbind();

        glDepthMask(true);
    }
}
