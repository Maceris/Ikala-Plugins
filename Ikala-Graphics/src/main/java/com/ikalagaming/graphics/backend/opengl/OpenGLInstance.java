package com.ikalagaming.graphics.backend.opengl;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER;
import static org.lwjgl.opengl.GL11.glDeleteTextures;
import static org.lwjgl.opengl.GL15.glDeleteBuffers;
import static org.lwjgl.opengl.GL20.glDeleteProgram;
import static org.lwjgl.opengl.GL20.glUseProgram;
import static org.lwjgl.opengl.GL30.glDeleteFramebuffers;

import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.GraphicsPlugin;
import com.ikalagaming.graphics.ShaderUniforms;
import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.backend.base.RenderStage;
import com.ikalagaming.graphics.backend.base.ShaderMap;
import com.ikalagaming.graphics.backend.opengl.stages.SceneRender;
import com.ikalagaming.graphics.exceptions.ShaderException;
import com.ikalagaming.graphics.frontend.*;
import com.ikalagaming.graphics.graph.CascadeShadow;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.launcher.PluginFolder;
import com.ikalagaming.plugins.config.ConfigManager;
import com.ikalagaming.plugins.config.PluginConfig;
import com.ikalagaming.util.SafeResourceLoader;

import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiKey;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.lwjgl.opengl.GL;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
public class OpenGLInstance implements Instance {

    private Pipeline pipeline;
    private TextureLoader textureLoader;
    private ShaderMap shaderMap;
    private PipelineManager pipelineManager;

    @Override
    public void initialize(@NonNull Window window) {
        GL.createCapabilities();

        textureLoader = new TextureLoaderOpenGL();
        shaderMap = new ShaderMap();
        initializeShaders();
        initializeImGui(window);
        pipelineManager = new PipelineManager(window, shaderMap);
        pipeline = pipelineManager.getPipeline(RenderConfig.builder().withGui().build());
        pipeline.initialize(window, shaderMap);
    }

    /**
     * Create an ImGui context and configure it.
     *
     * @param window The window to pull display info from.
     */
    private void initializeImGui(@NonNull Window window) {
        ImGui.createContext();

        ImGuiIO imGuiIO = ImGui.getIO();
        imGuiIO.setIniFilename(null);
        imGuiIO.setDisplaySize(window.getWidth(), window.getHeight());
        setUpImGuiKeys();
    }

    /**
     * Set up the shaders for each stage.
     *
     * @throws ShaderException If there was a problem finding or loading shaders.
     */
    private void initializeShaders() {
        initializeAnimationShader();
        initializeShadowShader();
        initializeSceneShader();
        initializeLightShader();
        initializeSkyboxShader();
        initializeFilterShader();
        initializeGuiShader();
    }

    /** Set up the animation shader and uniforms. */
    private void initializeAnimationShader() {
        List<Shader.ShaderModuleData> shaderModuleDataList = new ArrayList<>();
        shaderModuleDataList.add(
                new Shader.ShaderModuleData("shaders/anim.comp", Shader.Type.COMPUTE));
        var shaderProgram = new ShaderOpenGL(shaderModuleDataList);

        var uniformsMap = new UniformsMapOpenGL(shaderProgram.getProgramID());
        uniformsMap.createUniform(
                ShaderUniforms.Animation.DRAW_PARAMETERS
                        + "."
                        + ShaderUniforms.Animation.DrawParameters.SOURCE_OFFSET);
        uniformsMap.createUniform(
                ShaderUniforms.Animation.DRAW_PARAMETERS
                        + "."
                        + ShaderUniforms.Animation.DrawParameters.SOURCE_SIZE);
        uniformsMap.createUniform(
                ShaderUniforms.Animation.DRAW_PARAMETERS
                        + "."
                        + ShaderUniforms.Animation.DrawParameters.WEIGHTS_OFFSET);
        uniformsMap.createUniform(
                ShaderUniforms.Animation.DRAW_PARAMETERS
                        + "."
                        + ShaderUniforms.Animation.DrawParameters.BONES_MATRICES_OFFSET);
        uniformsMap.createUniform(
                ShaderUniforms.Animation.DRAW_PARAMETERS
                        + "."
                        + ShaderUniforms.Animation.DrawParameters.DESTINATION_OFFSET);
        shaderProgram.setUniforms(uniformsMap);

        shaderMap.addShader(RenderStage.Type.ANIMATION, shaderProgram);
    }

    /** Set up the shadow shader and uniforms. */
    private void initializeShadowShader() {
        List<Shader.ShaderModuleData> shaderModuleDataList = new ArrayList<>();
        shaderModuleDataList.add(
                new Shader.ShaderModuleData("shaders/shadow.vert", Shader.Type.VERTEX));
        var shaderProgram = new ShaderOpenGL(shaderModuleDataList);

        var uniformsMap = new UniformsMapOpenGL(shaderProgram.getProgramID());
        uniformsMap.createUniform(ShaderUniforms.Shadow.PROJECTION_VIEW_MATRIX);
        shaderProgram.setUniforms(uniformsMap);

        shaderMap.addShader(RenderStage.Type.SHADOW, shaderProgram);
    }

    /** Set up the scene shader and uniforms. */
    private void initializeSceneShader() {
        List<Shader.ShaderModuleData> shaderModuleDataList = new ArrayList<>();
        shaderModuleDataList.add(
                new Shader.ShaderModuleData("shaders/scene.vert", Shader.Type.VERTEX));
        shaderModuleDataList.add(
                new Shader.ShaderModuleData("shaders/scene.frag", Shader.Type.FRAGMENT));
        var shaderProgram = new ShaderOpenGL(shaderModuleDataList);

        var uniformsMap = new UniformsMapOpenGL(shaderProgram.getProgramID());
        uniformsMap.createUniform(ShaderUniforms.Scene.PROJECTION_MATRIX);
        uniformsMap.createUniform(ShaderUniforms.Scene.VIEW_MATRIX);

        for (int i = 0; i < SceneRender.MAX_TEXTURES; ++i) {
            uniformsMap.createUniform(ShaderUniforms.Scene.TEXTURE_SAMPLER + "[" + i + "]");
        }

        for (int i = 0; i < SceneRender.MAX_MATERIALS; ++i) {
            String name = ShaderUniforms.Scene.MATERIALS + "[" + i + "].";
            uniformsMap.createUniform(name + ShaderUniforms.Scene.Material.DIFFUSE);
            uniformsMap.createUniform(name + ShaderUniforms.Scene.Material.SPECULAR);
            uniformsMap.createUniform(name + ShaderUniforms.Scene.Material.REFLECTANCE);
            uniformsMap.createUniform(name + ShaderUniforms.Scene.Material.NORMAL_MAP_INDEX);
            uniformsMap.createUniform(name + ShaderUniforms.Scene.Material.TEXTURE_INDEX);
        }
        shaderProgram.setUniforms(uniformsMap);

        shaderMap.addShader(RenderStage.Type.SCENE, shaderProgram);
    }

    /** Set up the light shader and uniforms. */
    private void initializeLightShader() {
        List<Shader.ShaderModuleData> shaderModuleDataList = new ArrayList<>();
        shaderModuleDataList.add(
                new Shader.ShaderModuleData("shaders/lights.vert", Shader.Type.VERTEX));
        shaderModuleDataList.add(
                new Shader.ShaderModuleData("shaders/lights.frag", Shader.Type.FRAGMENT));
        var shaderProgram = new ShaderOpenGL(shaderModuleDataList);

        var uniformsMap = new UniformsMapOpenGL(shaderProgram.getProgramID());
        uniformsMap.createUniform(ShaderUniforms.Light.ALBEDO_SAMPLER);
        uniformsMap.createUniform(ShaderUniforms.Light.NORMAL_SAMPLER);
        uniformsMap.createUniform(ShaderUniforms.Light.SPECULAR_SAMPLER);
        uniformsMap.createUniform(ShaderUniforms.Light.DEPTH_SAMPLER);
        uniformsMap.createUniform(ShaderUniforms.Light.INVERSE_PROJECTION_MATRIX);
        uniformsMap.createUniform(ShaderUniforms.Light.INVERSE_VIEW_MATRIX);
        uniformsMap.createUniform(
                ShaderUniforms.Light.AMBIENT_LIGHT
                        + "."
                        + ShaderUniforms.Light.AmbientLight.INTENSITY);
        uniformsMap.createUniform(
                ShaderUniforms.Light.AMBIENT_LIGHT + "." + ShaderUniforms.Light.AmbientLight.COLOR);

        uniformsMap.createUniform(
                ShaderUniforms.Light.DIRECTIONAL_LIGHT
                        + "."
                        + ShaderUniforms.Light.DirectionalLight.COLOR);
        uniformsMap.createUniform(
                ShaderUniforms.Light.DIRECTIONAL_LIGHT
                        + "."
                        + ShaderUniforms.Light.DirectionalLight.DIRECTION);
        uniformsMap.createUniform(
                ShaderUniforms.Light.DIRECTIONAL_LIGHT
                        + "."
                        + ShaderUniforms.Light.DirectionalLight.INTENSITY);

        uniformsMap.createUniform(ShaderUniforms.Light.POINT_LIGHT_COUNT);
        uniformsMap.createUniform(ShaderUniforms.Light.SPOT_LIGHT_COUNT);

        uniformsMap.createUniform(
                ShaderUniforms.Light.FOG + "." + ShaderUniforms.Light.Fog.ENABLED);
        uniformsMap.createUniform(ShaderUniforms.Light.FOG + "." + ShaderUniforms.Light.Fog.COLOR);
        uniformsMap.createUniform(
                ShaderUniforms.Light.FOG + "." + ShaderUniforms.Light.Fog.DENSITY);

        for (int i = 0; i < CascadeShadow.SHADOW_MAP_CASCADE_COUNT; ++i) {
            uniformsMap.createUniform(ShaderUniforms.Light.SHADOW_MAP_PREFIX + i);
            uniformsMap.createUniform(
                    ShaderUniforms.Light.CASCADE_SHADOWS
                            + "["
                            + i
                            + "]."
                            + ShaderUniforms.Light.CascadeShadow.PROJECTION_VIEW_MATRIX);
            uniformsMap.createUniform(
                    ShaderUniforms.Light.CASCADE_SHADOWS
                            + "["
                            + i
                            + "]."
                            + ShaderUniforms.Light.CascadeShadow.SPLIT_DISTANCE);
        }
        shaderProgram.setUniforms(uniformsMap);

        shaderMap.addShader(RenderStage.Type.LIGHT, shaderProgram);
    }

    /** Set up the skybox shader and uniforms. */
    private void initializeSkyboxShader() {
        List<Shader.ShaderModuleData> shaderModuleDataList = new ArrayList<>();
        shaderModuleDataList.add(
                new Shader.ShaderModuleData("shaders/skybox.vert", Shader.Type.VERTEX));
        shaderModuleDataList.add(
                new Shader.ShaderModuleData("shaders/skybox.frag", Shader.Type.FRAGMENT));
        var shaderProgram = new ShaderOpenGL(shaderModuleDataList);

        var uniformsMap = new UniformsMapOpenGL(shaderProgram.getProgramID());
        uniformsMap.createUniform(ShaderUniforms.Skybox.PROJECTION_MATRIX);
        uniformsMap.createUniform(ShaderUniforms.Skybox.VIEW_MATRIX);
        uniformsMap.createUniform(ShaderUniforms.Skybox.DIFFUSE);
        uniformsMap.createUniform(ShaderUniforms.Skybox.TEXTURE_SAMPLER);
        uniformsMap.createUniform(ShaderUniforms.Skybox.HAS_TEXTURE);
        shaderProgram.setUniforms(uniformsMap);

        shaderMap.addShader(RenderStage.Type.SKYBOX, shaderProgram);
    }

    /**
     * Set up the default filter shader.
     *
     * @throws ShaderException If the default filter could not be found or loaded properly.
     */
    private void initializeFilterShader() {
        PluginConfig config = ConfigManager.loadConfig(GraphicsPlugin.PLUGIN_NAME);

        String folderPath = config.getString("filters-folder");
        File filtersFolder =
                PluginFolder.getResource(
                        GraphicsPlugin.PLUGIN_NAME, PluginFolder.ResourceType.DATA, folderPath);

        if (!filtersFolder.exists()) {
            var message =
                    SafeResourceLoader.getStringFormatted(
                            "FILTERS_FOLDER_MISSING",
                            GraphicsPlugin.getResourceBundle(),
                            folderPath);
            log.error(message);
            throw new ShaderException(message);
        }
        if (!folderPath.endsWith(File.separator)) {
            folderPath = folderPath.concat(File.separator);
        }

        final String defaultFilter = config.getString("default-filter");

        File vertex = new File(filtersFolder, defaultFilter.concat(".vert"));
        if (!vertex.exists()) {
            var message =
                    SafeResourceLoader.getStringFormatted(
                            "FILTER_MISSING_VERTEX",
                            GraphicsPlugin.getResourceBundle(),
                            vertex.getAbsolutePath());
            log.error(message);
            throw new ShaderException(message);
        }

        File fragment = new File(filtersFolder, defaultFilter.concat(".frag"));
        if (!fragment.exists()) {
            var message =
                    SafeResourceLoader.getStringFormatted(
                            "FILTER_MISSING_FRAGMENT",
                            GraphicsPlugin.getResourceBundle(),
                            fragment.getAbsolutePath());
            log.error(message);
            throw new ShaderException(message);
        }

        List<Shader.ShaderModuleData> shaderModuleDataList = new ArrayList<>();
        shaderModuleDataList.add(
                new Shader.ShaderModuleData(
                        folderPath.concat(vertex.getName()), Shader.Type.VERTEX));
        shaderModuleDataList.add(
                new Shader.ShaderModuleData(
                        folderPath.concat(fragment.getName()), Shader.Type.FRAGMENT));
        var shaderProgram = new ShaderOpenGL(shaderModuleDataList);

        var uniformsMap = new UniformsMapOpenGL(shaderProgram.getProgramID());
        uniformsMap.createUniform(ShaderUniforms.Filter.SCREEN_TEXTURE);
        shaderProgram.setUniforms(uniformsMap);

        shaderMap.addShader(RenderStage.Type.FILTER, shaderProgram);
    }

    /** Set up the GUI shader and uniforms. */
    private void initializeGuiShader() {
        List<Shader.ShaderModuleData> shaderModuleDataList = new ArrayList<>();
        shaderModuleDataList.add(
                new Shader.ShaderModuleData("shaders/gui.vert", Shader.Type.VERTEX));
        shaderModuleDataList.add(
                new Shader.ShaderModuleData("shaders/gui.frag", Shader.Type.FRAGMENT));
        var shaderProgram = new ShaderOpenGL(shaderModuleDataList);

        var uniformsMap = new UniformsMapOpenGL(shaderProgram.getProgramID());
        uniformsMap.createUniform(ShaderUniforms.GUI.SCALE);
        shaderProgram.setUniforms(uniformsMap);

        shaderMap.addShader(RenderStage.Type.GUI, shaderProgram);
    }

    @Override
    public void cleanup() {
        pipelineManager.cleanup();
        shaderMap.clearAll();

        var nextEntry = GraphicsManager.getDeletionQueue().pop();
        while (nextEntry != null) {
            deleteResource(nextEntry);
            nextEntry = GraphicsManager.getDeletionQueue().pop();
        }
    }

    @Override
    public void processResources() {
        var toDelete = GraphicsManager.getDeletionQueue().pop();
        if (toDelete != null) {
            deleteResource(toDelete);
        }
    }

    @Override
    public TextureLoader getTextureLoader() {
        return textureLoader;
    }

    @Override
    public void render(@NonNull Scene scene) {
        pipeline.render(scene, shaderMap);
    }

    @Override
    public void resize(int width, int height) {
        // TODO(ches) move this out of the render pass itself
        pipelineManager.resize(width, height);
        ImGuiIO imGuiIO = ImGui.getIO();
        imGuiIO.setDisplaySize(width, height);
    }

    @Override
    public void setupData(@NonNull Scene scene) {
        pipelineManager.setupData(scene);
    }

    @Override
    public void swapPipeline(final int config) {
        pipeline = pipelineManager.getPipeline(config);
    }

    /**
     * Process resource deletion.
     *
     * @param entry The deletion queue entry to handle.
     */
    private void deleteResource(@NonNull DeletionQueue.Entry entry) {
        switch (entry.type()) {
            case BUFFER -> {
                var buffer = (Buffer) entry.resource();
                glDeleteBuffers((int) buffer.id());
            }
            case FRAME_BUFFER -> {
                var framebuffer = (Framebuffer) entry.resource();
                glDeleteFramebuffers((int) framebuffer.id());
                Arrays.stream(framebuffer.textures())
                        .forEach(texture -> glDeleteTextures((int) texture));
            }
            case TEXTURE -> {
                var texture = (Texture) entry.resource();
                glDeleteTextures((int) texture.id());
            }
            case SHADER -> {
                var shader = (Shader) entry.resource();
                glUseProgram(0);
                if (shader.getProgramID() != 0) {
                    glDeleteProgram(shader.getProgramID());
                }
            }
        }
    }

    /** Set up nonstandard key codes to make sure they work. */
    private void setUpImGuiKeys() {
        ImGuiIO io = ImGui.getIO();
        io.setKeyMap(ImGuiKey.Tab, GLFW_KEY_TAB);
        io.setKeyMap(ImGuiKey.LeftArrow, GLFW_KEY_LEFT);
        io.setKeyMap(ImGuiKey.RightArrow, GLFW_KEY_RIGHT);
        io.setKeyMap(ImGuiKey.UpArrow, GLFW_KEY_UP);
        io.setKeyMap(ImGuiKey.DownArrow, GLFW_KEY_DOWN);
        io.setKeyMap(ImGuiKey.PageUp, GLFW_KEY_PAGE_UP);
        io.setKeyMap(ImGuiKey.PageDown, GLFW_KEY_PAGE_DOWN);
        io.setKeyMap(ImGuiKey.Home, GLFW_KEY_HOME);
        io.setKeyMap(ImGuiKey.End, GLFW_KEY_END);
        io.setKeyMap(ImGuiKey.Insert, GLFW_KEY_INSERT);
        io.setKeyMap(ImGuiKey.Delete, GLFW_KEY_DELETE);
        io.setKeyMap(ImGuiKey.Backspace, GLFW_KEY_BACKSPACE);
        io.setKeyMap(ImGuiKey.Space, GLFW_KEY_SPACE);
        io.setKeyMap(ImGuiKey.Enter, GLFW_KEY_ENTER);
        io.setKeyMap(ImGuiKey.Escape, GLFW_KEY_ESCAPE);
        io.setKeyMap(ImGuiKey.KeyPadEnter, GLFW_KEY_KP_ENTER);
    }
}
