package com.ikalagaming.graphics.backend.vulkan;

import com.ikalagaming.graphics.backend.base.UniformsMap;
import com.ikalagaming.graphics.exceptions.ShaderException;
import com.ikalagaming.graphics.frontend.Texture;
import com.ikalagaming.util.SafeResourceLoader;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class UniformsMapVulkan implements UniformsMap {
    /** The shader program identifier. */
    private final int programID;

    /** The mapping of uniform name to integer location for the shader program. */
    private final Map<String, Integer> uniforms;

    /**
     * Create a new uniform map for the given program ID.
     *
     * @param programId The shader program identifier.
     */
    public UniformsMapVulkan(int programId) {
        programID = programId;
        uniforms = new HashMap<>();
    }

    @Override
    public void createUniform(@NonNull String uniformName) {
        int uniformLocation = 0; // TODO(ches) figure out uniform location
        if (uniformLocation < 0) {
            String error =
                    SafeResourceLoader.format(
                            "Could not find uniform [{}] in shader program [{}]",
                            uniformName,
                            programID);
            log.info(error);

            throw new ShaderException(
                    SafeResourceLoader.format(error, uniformName, programID + ""));
        }
        uniforms.put(uniformName, uniformLocation);
    }

    /**
     * Get the location of a uniform.
     *
     * @param uniformName The name of the uniform.
     * @return The uniform location.
     * @throws ShaderException If the uniform can't be found.
     */
    private int getUniformLocation(@NonNull String uniformName) {
        Integer location = uniforms.get(uniformName);
        if (location == null) {
            final String error =
                    SafeResourceLoader.format("Could not find uniform [{}]", programID);
            log.info(error);
            throw new ShaderException(error);
        }
        return location;
    }

    @Override
    public void setUniform(@NonNull String uniformName, float value) {
        // TODO(ches) set uniform
    }

    @Override
    public void setUniform(@NonNull String uniformName, int value) {
        // TODO(ches) set uniform
    }

    @Override
    public void setUniformUnsigned(@NonNull String uniformName, int value) {
        // TODO(ches) set uniform
    }

    @Override
    public void setUniform(@NonNull String uniformName, @NonNull Matrix4f value) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            // TODO(ches) set uniform
        }
    }

    @Override
    public void setUniform(@NonNull String uniformName, @NonNull Matrix4f[] matrices) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            final int length = matrices.length;
            FloatBuffer fb = stack.mallocFloat(16 * length);
            for (int i = 0; i < length; ++i) {
                matrices[i].get(16 * i, fb);
            }
            // TODO(ches) set uniform
        }
    }

    @Override
    public void setUniform(@NonNull String uniformName, @NonNull Vector2f value) {
        // TODO(ches) set uniform
    }

    @Override
    public void setUniform(@NonNull String uniformName, @NonNull Vector3f value) {
        // TODO(ches) set uniform
    }

    @Override
    public void setUniform(@NonNull String uniformName, @NonNull Vector4f value) {
        // TODO(ches) set uniform
    }

    @Override
    public void setUniform(@NonNull String uniformName, Texture texture) {
        long handle = texture != null ? texture.handle() : 0;
        // TODO(ches) set uniform
    }
}
