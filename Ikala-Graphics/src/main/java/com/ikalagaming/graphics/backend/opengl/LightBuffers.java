package com.ikalagaming.graphics.backend.opengl;

import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL30.glBindBufferBase;
import static org.lwjgl.opengl.GL43.GL_SHADER_STORAGE_BUFFER;

import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;

/**
 * Used to store light buffer handles.
 *
 * @param pointLightSSBO The point lights.
 * @param spotLightSSBO The spotlights.
 */
@Deprecated
public record LightBuffers(int pointLightSSBO, int spotLightSSBO) {

    /** Clean up resources. This invalidates the handles stored in this object. */
    public void cleanup() {
        glDeleteBuffers(pointLightSSBO);
        glDeleteBuffers(spotLightSSBO);
    }

    /**
     * Initialize the lighting SSBOs and fill them with zeroes. This should be called instead of the
     * constructor.
     */
    public static LightBuffers create() {
        int pointLightBuffer = glGenBuffers();
        int spotLightBuffer = glGenBuffers();
        /*
         * Position (vec3 + ignored), color (vec3), intensity (1), Attenuation
         * (3 + ignored), in that order.
         */
        final int POINT_LIGHT_SIZE = 4 + 3 + 1 + 4;
        FloatBuffer pointLightFloatBuffer =
                MemoryUtil.memAllocFloat(LightRender.MAX_LIGHTS_SUPPORTED * POINT_LIGHT_SIZE);

        pointLightFloatBuffer
                .put(new float[LightRender.MAX_LIGHTS_SUPPORTED * POINT_LIGHT_SIZE])
                .flip();

        glBindBufferBase(
                GL_SHADER_STORAGE_BUFFER, LightRender.POINT_LIGHT_BINDING, pointLightBuffer);
        glBufferData(GL_SHADER_STORAGE_BUFFER, pointLightFloatBuffer, GL_STATIC_DRAW);

        MemoryUtil.memFree(pointLightFloatBuffer);

        /*
         * Position (vec3 + ignored), color (vec3), intensity (1), Attenuation
         * (3 + ignored), cone direction (vec3), cutoff (1) in that order.
         */
        final int SPOT_LIGHT_SIZE = 4 + 3 + 1 + 4 + 3 + 1;
        FloatBuffer spotLightFloatBuffer =
                MemoryUtil.memAllocFloat(LightRender.MAX_LIGHTS_SUPPORTED * SPOT_LIGHT_SIZE);

        spotLightFloatBuffer
                .put(new float[LightRender.MAX_LIGHTS_SUPPORTED * POINT_LIGHT_SIZE])
                .flip();

        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, LightRender.SPOT_LIGHT_BINDING, spotLightBuffer);
        glBufferData(GL_SHADER_STORAGE_BUFFER, spotLightFloatBuffer, GL_STATIC_DRAW);

        MemoryUtil.memFree(spotLightFloatBuffer);

        return new LightBuffers(pointLightBuffer, spotLightBuffer);
    }
}
