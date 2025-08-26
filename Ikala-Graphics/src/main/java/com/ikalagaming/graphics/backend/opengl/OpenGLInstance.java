package com.ikalagaming.graphics.backend.opengl;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER;
import static org.lwjgl.opengl.GL11.glDeleteTextures;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.glDeleteProgram;
import static org.lwjgl.opengl.GL20.glUseProgram;
import static org.lwjgl.opengl.GL30.glDeleteFramebuffers;
import static org.lwjgl.opengl.GL31.GL_UNIFORM_BUFFER;
import static org.lwjgl.opengl.GL43.GL_SHADER_STORAGE_BUFFER;
import static org.lwjgl.opengl.GL44.glBufferStorage;

import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.GraphicsPlugin;
import com.ikalagaming.graphics.ShaderUniforms;
import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.backend.base.RenderStage;
import com.ikalagaming.graphics.backend.base.ShaderMap;
import com.ikalagaming.graphics.exceptions.ShaderException;
import com.ikalagaming.graphics.frontend.*;
import com.ikalagaming.graphics.graph.CascadeShadow;
import com.ikalagaming.graphics.graph.MeshData;
import com.ikalagaming.graphics.graph.Model;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.util.SafeResourceLoader;

import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiKey;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
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
    public boolean initialize(@NonNull Window window) {
        GL.createCapabilities();

        if (!checkCapabilities()) {
            return false;
        }

        textureLoader = new TextureLoaderOpenGL();
        shaderMap = new ShaderMap();
        initializeShaders();
        initializeImGui(window);
        pipelineManager = new PipelineManager(window, shaderMap);
        pipeline = pipelineManager.getPipeline(RenderConfig.builder().withGui().build());
        pipeline.initialize(window, shaderMap);
        return true;
    }

    private boolean checkCapabilities() {
        GLCapabilities capabilities = GL.getCapabilities();
        boolean result = true;
        List<String> missing = new ArrayList<>();

        if (!capabilities.GL_ARB_bindless_texture && !capabilities.GL_NV_bindless_texture) {
            missing.add("ARB_bindless_texture");
            result = false;
        }
        if (!capabilities.GL_ARB_buffer_storage) {
            missing.add("ARB_buffer_storage");
            result = false;
        }
        if (!capabilities.GL_ARB_uniform_buffer_object) {
            missing.add("ARB_uniform_buffer_object");
            result = false;
        }
        if (!capabilities.GL_ARB_shader_storage_buffer_object) {
            missing.add("ARB_shader_storage_buffer_object");
            result = false;
        }
        if (!capabilities.GL_ARB_explicit_uniform_location) {
            missing.add("ARB_explicit_uniform_location");
            result = false;
        }

        if (!result) {
            String message =
                    SafeResourceLoader.getString(
                            "EXTENSION_MISSING", GraphicsPlugin.getResourceBundle());
            log.error(message, Strings.join(missing, ','));
        }

        return result;
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
                new Shader.ShaderModuleData(
                        "shaders/anim.comp", Shader.Type.COMPUTE, Shader.Location.BUNDLED));
        var shaderProgram = new ShaderOpenGL(shaderModuleDataList);

        var uniformsMap = new UniformsMapOpenGL(shaderProgram.getProgramID());
        shaderProgram.setUniforms(uniformsMap);

        shaderMap.addShader(RenderStage.Type.ANIMATION, shaderProgram);
    }

    /** Set up the shadow shader and uniforms. */
    private void initializeShadowShader() {
        List<Shader.ShaderModuleData> shaderModuleDataList = new ArrayList<>();
        shaderModuleDataList.add(
                new Shader.ShaderModuleData(
                        "shaders/shadow.vert", Shader.Type.VERTEX, Shader.Location.BUNDLED));
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
                new Shader.ShaderModuleData(
                        "shaders/scene.vert", Shader.Type.VERTEX, Shader.Location.BUNDLED));
        shaderModuleDataList.add(
                new Shader.ShaderModuleData(
                        "shaders/scene.frag", Shader.Type.FRAGMENT, Shader.Location.BUNDLED));
        var shaderProgram = new ShaderOpenGL(shaderModuleDataList);

        var uniformsMap = new UniformsMapOpenGL(shaderProgram.getProgramID());
        uniformsMap.createUniform(ShaderUniforms.Scene.MATERIAL_INDEX);
        uniformsMap.createUniform(ShaderUniforms.Scene.PROJECTION_MATRIX);
        uniformsMap.createUniform(ShaderUniforms.Scene.VIEW_MATRIX);
        uniformsMap.createUniform(ShaderUniforms.Scene.BASE_COLOR_SAMPLER);
        uniformsMap.createUniform(ShaderUniforms.Scene.NORMAL_SAMPLER);

        shaderProgram.setUniforms(uniformsMap);

        shaderMap.addShader(RenderStage.Type.SCENE, shaderProgram);
    }

    /** Set up the light shader and uniforms. */
    private void initializeLightShader() {
        List<Shader.ShaderModuleData> shaderModuleDataList = new ArrayList<>();
        shaderModuleDataList.add(
                new Shader.ShaderModuleData(
                        "shaders/lights.vert", Shader.Type.VERTEX, Shader.Location.BUNDLED));
        shaderModuleDataList.add(
                new Shader.ShaderModuleData(
                        "shaders/lights.frag", Shader.Type.FRAGMENT, Shader.Location.BUNDLED));
        var shaderProgram = new ShaderOpenGL(shaderModuleDataList);

        var uniformsMap = new UniformsMapOpenGL(shaderProgram.getProgramID());
        uniformsMap.createUniform(ShaderUniforms.Light.BASE_COLOR_SAMPLER);
        uniformsMap.createUniform(ShaderUniforms.Light.NORMAL_SAMPLER);
        uniformsMap.createUniform(ShaderUniforms.Light.TANGENT_SAMPLER);
        uniformsMap.createUniform(ShaderUniforms.Light.MATERIAL_SAMPLER);
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
                new Shader.ShaderModuleData(
                        "shaders/skybox.vert", Shader.Type.VERTEX, Shader.Location.BUNDLED));
        shaderModuleDataList.add(
                new Shader.ShaderModuleData(
                        "shaders/skybox.frag", Shader.Type.FRAGMENT, Shader.Location.BUNDLED));
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
        List<Shader.ShaderModuleData> shaderModuleDataList = new ArrayList<>();
        shaderModuleDataList.add(
                new Shader.ShaderModuleData(
                        "shaders/filters/default.vert",
                        Shader.Type.VERTEX,
                        Shader.Location.BUNDLED));
        shaderModuleDataList.add(
                new Shader.ShaderModuleData(
                        "shaders/filters/default.frag",
                        Shader.Type.FRAGMENT,
                        Shader.Location.BUNDLED));
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
                new Shader.ShaderModuleData(
                        "shaders/gui.vert", Shader.Type.VERTEX, Shader.Location.BUNDLED));
        shaderModuleDataList.add(
                new Shader.ShaderModuleData(
                        "shaders/gui.frag", Shader.Type.FRAGMENT, Shader.Location.BUNDLED));
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
    public void initializeModel(@NonNull Model model) {
        if (model.isAnimated()) {
            // Filled out later
            model.setEntityAnimationOffsetsBuffer(
                    new Buffer(glGenBuffers(), Buffer.Type.SHADER_STORAGE));

            int animationBuffer = glGenBuffers();
            model.setAnimationBuffer(new Buffer(animationBuffer, Buffer.Type.UNIFORM));
            int totalSize = 0;
            for (Model.Animation animation : model.getAnimationList()) {
                totalSize += animation.frameData().length;
            }
            ByteBuffer animations = ByteBuffer.allocate(totalSize);
            for (Model.Animation animation : model.getAnimationList()) {
                animations.put(animation.frameData());
            }
            glBindBuffer(GL_SHADER_STORAGE_BUFFER, animationBuffer);
            glBufferStorage(GL_SHADER_STORAGE_BUFFER, animations, 0);
            MemoryUtil.memFree(animations);

            for (MeshData meshData : model.getMeshDataList()) {
                // Filled out later
                meshData.setAnimationTargetBuffer(
                        new Buffer(glGenBuffers(), Buffer.Type.SHADER_STORAGE));

                final BufferUtil bufferUtil = BufferUtil.getInstance();

                bufferUtil.bindBuffer(meshData.getVertexBuffer());
                glBufferStorage(GL_UNIFORM_BUFFER, meshData.getVertexData(), 0);

                bufferUtil.bindBuffer(meshData.getIndexBuffer());
                glBufferStorage(GL_ELEMENT_ARRAY_BUFFER, meshData.getIndices(), 0);

                int boneWeightBuffer = glGenBuffers();
                meshData.setBoneWeightBuffer(new Buffer(boneWeightBuffer, Buffer.Type.UNIFORM));
                glBindBuffer(GL_UNIFORM_BUFFER, boneWeightBuffer);
                glBufferStorage(
                        GL_UNIFORM_BUFFER, ByteBuffer.wrap(meshData.getBoneWeightData()), 0);
            }

            // Unbind buffers
            glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);
            glBindBuffer(GL_UNIFORM_BUFFER, 0);
        }
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
