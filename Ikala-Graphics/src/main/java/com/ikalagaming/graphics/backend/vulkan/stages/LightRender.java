package com.ikalagaming.graphics.backend.vulkan.stages;

import com.ikalagaming.graphics.ShaderUniforms;
import com.ikalagaming.graphics.backend.base.RenderStage;
import com.ikalagaming.graphics.backend.base.UniformsMap;
import com.ikalagaming.graphics.backend.vulkan.PipelineVulkan;
import com.ikalagaming.graphics.backend.vulkan.QuadMesh;
import com.ikalagaming.graphics.backend.vulkan.ShaderVulkan;
import com.ikalagaming.graphics.frontend.Buffer;
import com.ikalagaming.graphics.frontend.BufferUtil;
import com.ikalagaming.graphics.frontend.Framebuffer;
import com.ikalagaming.graphics.graph.CascadeShadow;
import com.ikalagaming.graphics.scene.Fog;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.graphics.scene.lights.*;

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
    @NonNull private ShaderVulkan shader;

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
                // TODO(ches) opengl bound things here
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
            // TODO(ches) opengl bound things here
        }

        uniformsMap.setUniform(
                ShaderUniforms.Light.INVERSE_PROJECTION_MATRIX,
                scene.getProjection().getInverseProjectionMatrix());
        uniformsMap.setUniform(
                ShaderUniforms.Light.INVERSE_VIEW_MATRIX, scene.getCamera().getInvViewMatrix());

        BufferUtil.INSTANCE.bindBuffer(
                scene.getMaterialCache().getMaterialBuffer(), MATERIALS_BINDING);

        // TODO(ches) render things here

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

        if (pointLights.size() > PipelineVulkan.MAX_LIGHTS_SUPPORTED) {
            log.warn(
                    "Only {} point lights are supported but there are {} in the scene",
                    PipelineVulkan.MAX_LIGHTS_SUPPORTED,
                    pointLights.size());
        }
        /*
         * Position (vec3 + ignored), color (vec3), intensity (1),
         */
        final int STRUCT_SIZE = 3 + 1 + 3 + 1;

        final int lightsToRender =
                Math.min(PipelineVulkan.MAX_LIGHTS_SUPPORTED, pointLights.size());

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
        }

        lightBuffer.flip();

        // TODO(ches) upload to the buffer

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

        if (spotLights.size() > PipelineVulkan.MAX_LIGHTS_SUPPORTED) {
            log.warn(
                    "Only {} spotlights are supported but there are {} in the scene",
                    PipelineVulkan.MAX_LIGHTS_SUPPORTED,
                    spotLights.size());
        }

        /*
         * Position (vec3), padding (1), color (vec3), intensity (1), cone direction (vec3), cutoff (1) in that order.
         */
        final int STRUCT_SIZE = 3 + 1 + 3 + 1 + 3 + 1;

        final int lightsToRender = Math.min(PipelineVulkan.MAX_LIGHTS_SUPPORTED, spotLights.size());

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
            lightDirection.set(light.getConeDirection(), 1);
            lightDirection.mul(viewMatrix);
            lightBuffer.put(lightDirection.x);
            lightBuffer.put(lightDirection.y);
            lightBuffer.put(lightDirection.z);
            lightBuffer.put(light.getCutOff());
        }
        lightBuffer.flip();

        // TODO(ches) upload to the buffer

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
