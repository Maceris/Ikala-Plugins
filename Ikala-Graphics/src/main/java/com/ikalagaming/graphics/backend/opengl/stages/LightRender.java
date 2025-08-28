package com.ikalagaming.graphics.backend.opengl.stages;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL15.glBufferSubData;
import static org.lwjgl.opengl.GL30.glBindBufferBase;
import static org.lwjgl.opengl.GL30.glBindVertexArray;
import static org.lwjgl.opengl.GL43.GL_SHADER_STORAGE_BUFFER;

import com.ikalagaming.graphics.GraphicsPlugin;
import com.ikalagaming.graphics.ShaderUniforms;
import com.ikalagaming.graphics.backend.base.RenderStage;
import com.ikalagaming.graphics.backend.base.UniformsMap;
import com.ikalagaming.graphics.backend.opengl.PipelineOpenGL;
import com.ikalagaming.graphics.backend.opengl.QuadMesh;
import com.ikalagaming.graphics.frontend.*;
import com.ikalagaming.graphics.graph.CascadeShadow;
import com.ikalagaming.graphics.scene.Fog;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.graphics.scene.lights.*;
import com.ikalagaming.util.SafeResourceLoader;

import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.util.List;

/**
 * Handles rendering the lighting for a scene, given the g-buffer, lighting, and shadow information.
 */
@Setter
@Slf4j
@AllArgsConstructor
public class LightRender implements RenderStage {

    /** The binding for the point light SSBO. */
    public static final int POINT_LIGHT_BINDING = 0;

    /** The binding for the spotlight SSBO. */
    public static final int SPOT_LIGHT_BINDING = 1;

    /** The binding for the materials SSBO. */
    public static final int MATERIALS_BINDING = 2;

    /** The shader to use for rendering. */
    @NonNull private Shader shader;

    /** The cascade shadows information. */
    @NonNull private List<CascadeShadow> cascadeShadows;

    /** The buffer to use for storing point light info. */
    @NonNull private Buffer pointLightsBuffer;

    /** The buffer to use for storing spotlight info. */
    @NonNull private Buffer spotLightsBuffer;

    /** The buffer for reading shadow info from. */
    @NonNull private Framebuffer shadowBuffers;

    /** The g-buffer for reading scene info from. */
    @NonNull private Framebuffer gBuffer;

    /** A mesh for rendering onto. */
    @NonNull private QuadMesh quadMesh;

    @Override
    public void render(Scene scene) {
        shader.bind();
        var uniformsMap = shader.getUniformMap();

        updateLights(scene, pointLightsBuffer, spotLightsBuffer, uniformsMap);

        int nextTexture = 0;
        long[] textureIds = gBuffer.textures();
        if (textureIds != null) {
            for (long textureId : textureIds) {
                glActiveTexture(GL_TEXTURE0 + nextTexture);
                glBindTexture(GL_TEXTURE_2D, (int) textureId);
                nextTexture += 1;
            }
        }

        uniformsMap.setUniform(ShaderUniforms.Light.BASE_COLOR_SAMPLER, 0);
        uniformsMap.setUniform(ShaderUniforms.Light.NORMAL_SAMPLER, 1);
        uniformsMap.setUniform(ShaderUniforms.Light.TANGENT_SAMPLER, 2);
        uniformsMap.setUniform(ShaderUniforms.Light.MATERIAL_SAMPLER, 3);
        uniformsMap.setUniform(ShaderUniforms.Light.DEPTH_SAMPLER, 4);

        Fog fog = scene.getFog();
        uniformsMap.setUniform(
                ShaderUniforms.Light.FOG + "." + ShaderUniforms.Light.Fog.ENABLED,
                fog.isActive() ? 1 : 0);
        uniformsMap.setUniform(
                ShaderUniforms.Light.FOG + "." + ShaderUniforms.Light.Fog.COLOR, fog.getColor());
        uniformsMap.setUniform(
                ShaderUniforms.Light.FOG + "." + ShaderUniforms.Light.Fog.DENSITY,
                fog.getDensity());

        for (int i = 0; i < CascadeShadow.SHADOW_MAP_CASCADE_COUNT; ++i) {
            uniformsMap.setUniform(ShaderUniforms.Light.SHADOW_MAP_PREFIX + i, nextTexture + i);
            CascadeShadow cascadeShadow = cascadeShadows.get(i);
            uniformsMap.setUniform(
                    ShaderUniforms.Light.CASCADE_SHADOWS
                            + "["
                            + i
                            + "]."
                            + ShaderUniforms.Light.CascadeShadow.PROJECTION_VIEW_MATRIX,
                    cascadeShadow.getProjViewMatrix());
            uniformsMap.setUniform(
                    ShaderUniforms.Light.CASCADE_SHADOWS
                            + "["
                            + i
                            + "]."
                            + ShaderUniforms.Light.CascadeShadow.SPLIT_DISTANCE,
                    cascadeShadow.getSplitDistance());
        }

        for (int i = 0; i < CascadeShadow.SHADOW_MAP_CASCADE_COUNT; ++i) {
            glActiveTexture(GL_TEXTURE0 + nextTexture + i);
            glBindTexture(GL_TEXTURE_2D, (int) shadowBuffers.textures()[i]);
        }

        uniformsMap.setUniform(
                ShaderUniforms.Light.INVERSE_PROJECTION_MATRIX,
                scene.getProjection().getInverseProjectionMatrix());
        uniformsMap.setUniform(
                ShaderUniforms.Light.INVERSE_VIEW_MATRIX, scene.getCamera().getInvViewMatrix());

        BufferUtil.INSTANCE.bindBuffer(
                scene.getMaterialCache().getMaterialBuffer(), MATERIALS_BINDING);

        glBindVertexArray(quadMesh.vao());
        glDrawElements(GL_TRIANGLES, QuadMesh.VERTEX_COUNT, GL_UNSIGNED_INT, 0);

        shader.unbind();
    }

    /**
     * Load all the point lights into the SSBO for rendering.
     *
     * @param scene The scene to fetch lights from.
     */
    private void setupPointLightBuffer(
            @NonNull Scene scene, int pointLightBuffer, @NonNull UniformsMap uniformsMap) {
        List<PointLight> pointLights = scene.getSceneLights().getPointLights();
        final Matrix4f viewMatrix = scene.getCamera().getViewMatrix();

        if (pointLights.size() > PipelineOpenGL.MAX_LIGHTS_SUPPORTED) {
            log.warn(
                    SafeResourceLoader.getString(
                            "MAX_POINT_LIGHTS", GraphicsPlugin.getResourceBundle()),
                    PipelineOpenGL.MAX_LIGHTS_SUPPORTED,
                    pointLights.size());
        }
        /*
         * Position (vec3 + ignored), color (vec3), intensity (1), Attenuation
         * (3 + ignored), in that order.
         */
        final int STRUCT_SIZE = 4 + 3 + 1 + 4;

        final int lightsToRender =
                Math.min(PipelineOpenGL.MAX_LIGHTS_SUPPORTED, pointLights.size());

        FloatBuffer lightBuffer = MemoryUtil.memAllocFloat(lightsToRender * STRUCT_SIZE);

        Vector4f lightPosition = new Vector4f();
        final float padding = 0.0f;
        for (int i = 0; i < lightsToRender; ++i) {
            PointLight light = pointLights.get(i);
            lightPosition.set(light.getPosition(), 1);
            lightPosition.mul(viewMatrix);
            lightBuffer.put(lightPosition.x);
            lightBuffer.put(lightPosition.y);
            lightBuffer.put(lightPosition.z);
            lightBuffer.put(padding);
            lightBuffer.put(light.getColor().x);
            lightBuffer.put(light.getColor().y);
            lightBuffer.put(light.getColor().z);
            lightBuffer.put(light.getIntensity());
            lightBuffer.put(padding);
        }

        lightBuffer.flip();

        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, POINT_LIGHT_BINDING, pointLightBuffer);
        glBufferSubData(GL_SHADER_STORAGE_BUFFER, 0, lightBuffer);

        MemoryUtil.memFree(lightBuffer);

        uniformsMap.setUniform(ShaderUniforms.Light.POINT_LIGHT_COUNT, lightsToRender);
    }

    /**
     * Load all the spotlights into the SSBO for rendering.
     *
     * @param scene The scene to fetch lights from.
     */
    private void setupSpotLightBuffer(
            @NonNull Scene scene, int spotLightBuffer, @NonNull UniformsMap uniformsMap) {
        List<SpotLight> spotLights = scene.getSceneLights().getSpotLights();
        final Matrix4f viewMatrix = scene.getCamera().getViewMatrix();

        if (spotLights.size() > PipelineOpenGL.MAX_LIGHTS_SUPPORTED) {
            log.warn(
                    SafeResourceLoader.getString(
                            "MAX_SPOT_LIGHTS", GraphicsPlugin.getResourceBundle()),
                    PipelineOpenGL.MAX_LIGHTS_SUPPORTED,
                    spotLights.size());
        }

        /*
         * Position (vec3 + ignored), color (vec3), intensity (1), Attenuation
         * (3 + ignored), cone direction (vec3), cutoff (1) in that order.
         */
        final int STRUCT_SIZE = 4 + 3 + 1 + 4 + 3 + 1;

        final int lightsToRender = Math.min(PipelineOpenGL.MAX_LIGHTS_SUPPORTED, spotLights.size());

        FloatBuffer lightBuffer = MemoryUtil.memAllocFloat(lightsToRender * STRUCT_SIZE);

        Vector4f lightPosition = new Vector4f();
        Vector4f lightDirection = new Vector4f();
        final float padding = 0.0f;
        for (int i = 0; i < lightsToRender; ++i) {
            SpotLight light = spotLights.get(i);
            lightPosition.set(light.getPointLight().getPosition(), 1);
            lightPosition.mul(viewMatrix);
            lightBuffer.put(lightPosition.x);
            lightBuffer.put(lightPosition.y);
            lightBuffer.put(lightPosition.z);
            lightBuffer.put(padding);
            lightBuffer.put(light.getPointLight().getColor().x);
            lightBuffer.put(light.getPointLight().getColor().y);
            lightBuffer.put(light.getPointLight().getColor().z);
            lightBuffer.put(light.getPointLight().getIntensity());
            lightBuffer.put(padding);
            lightDirection.set(light.getConeDirection(), 1);
            lightDirection.mul(viewMatrix);
            lightBuffer.put(lightDirection.x);
            lightBuffer.put(lightDirection.y);
            lightBuffer.put(lightDirection.z);
            lightBuffer.put(light.getCutOff());
        }
        lightBuffer.flip();

        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, SPOT_LIGHT_BINDING, spotLightBuffer);
        glBufferSubData(GL_SHADER_STORAGE_BUFFER, 0, lightBuffer);

        MemoryUtil.memFree(lightBuffer);

        uniformsMap.setUniform(ShaderUniforms.Light.SPOT_LIGHT_COUNT, lightsToRender);
    }

    /**
     * Update the uniforms for lights in the scene.
     *
     * @param scene The scene we are updating.
     */
    private void updateLights(
            Scene scene, Buffer pointLights, Buffer spotLights, UniformsMap uniformsMap) {
        Matrix4f viewMatrix = scene.getCamera().getViewMatrix();

        SceneLights sceneLights = scene.getSceneLights();
        AmbientLight ambientLight = sceneLights.getAmbientLight();
        uniformsMap.setUniform(
                ShaderUniforms.Light.AMBIENT_LIGHT
                        + "."
                        + ShaderUniforms.Light.AmbientLight.INTENSITY,
                ambientLight.getIntensity());
        uniformsMap.setUniform(
                ShaderUniforms.Light.AMBIENT_LIGHT + "." + ShaderUniforms.Light.AmbientLight.COLOR,
                ambientLight.getColor());

        DirectionalLight dirLight = sceneLights.getDirLight();
        Vector4f auxDir = new Vector4f(dirLight.getDirection(), 0);
        auxDir.mul(viewMatrix);
        Vector3f dir = new Vector3f(auxDir.x, auxDir.y, auxDir.z);
        uniformsMap.setUniform(
                ShaderUniforms.Light.DIRECTIONAL_LIGHT
                        + "."
                        + ShaderUniforms.Light.DirectionalLight.COLOR,
                dirLight.getColor());
        uniformsMap.setUniform(
                ShaderUniforms.Light.DIRECTIONAL_LIGHT
                        + "."
                        + ShaderUniforms.Light.DirectionalLight.DIRECTION,
                dir);
        uniformsMap.setUniform(
                ShaderUniforms.Light.DIRECTIONAL_LIGHT
                        + "."
                        + ShaderUniforms.Light.DirectionalLight.INTENSITY,
                dirLight.getIntensity());

        setupPointLightBuffer(scene, (int) pointLights.id(), uniformsMap);
        setupSpotLightBuffer(scene, (int) spotLights.id(), uniformsMap);
    }
}
