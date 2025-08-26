package com.ikalagaming.graphics.backend.opengl;

import static org.lwjgl.opengl.ARBBindlessTexture.glUniformHandleui64ARB;
import static org.lwjgl.opengl.GL20.glGetUniformLocation;
import static org.lwjgl.opengl.GL20.glUniform1f;
import static org.lwjgl.opengl.GL20.glUniform1i;
import static org.lwjgl.opengl.GL20.glUniform2f;
import static org.lwjgl.opengl.GL20.glUniform3f;
import static org.lwjgl.opengl.GL20.glUniform4f;
import static org.lwjgl.opengl.GL20.glUniformMatrix4fv;

import com.ikalagaming.graphics.GraphicsPlugin;
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
public class UniformsMapOpenGL implements UniformsMap {
    /** The shader program identifier. */
    private final int programID;

    /** The mapping of uniform name to integer location for the shader program. */
    private final Map<String, Integer> uniforms;

    /**
     * Create a new uniform map for the given program ID.
     *
     * @param programId The shader program identifier.
     */
    public UniformsMapOpenGL(int programId) {
        programID = programId;
        uniforms = new HashMap<>();
    }

    @Override
    public void createUniform(@NonNull String uniformName) {
        int uniformLocation = glGetUniformLocation(programID, uniformName);
        if (uniformLocation < 0) {

            String error =
                    SafeResourceLoader.getString(
                            "UNIFORM_MISSING", GraphicsPlugin.getResourceBundle());
            log.info(error, uniformName, programID);

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
            String error =
                    SafeResourceLoader.getString(
                            "UNIFORM_LOCATION_MISSING", GraphicsPlugin.getResourceBundle());
            log.info(error, uniformName, programID);
            throw new ShaderException(SafeResourceLoader.format(error, uniformName));
        }
        return location;
    }

    @Override
    public void setUniform(@NonNull String uniformName, float value) {
        glUniform1f(getUniformLocation(uniformName), value);
    }

    @Override
    public void setUniform(@NonNull String uniformName, int value) {
        glUniform1i(getUniformLocation(uniformName), value);
    }

    @Override
    public void setUniform(@NonNull String uniformName, @NonNull Matrix4f value) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            glUniformMatrix4fv(
                    getUniformLocation(uniformName), false, value.get(stack.mallocFloat(16)));
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
            glUniformMatrix4fv(uniforms.get(uniformName), false, fb);
        }
    }

    @Override
    public void setUniform(@NonNull String uniformName, @NonNull Vector2f value) {
        glUniform2f(getUniformLocation(uniformName), value.x, value.y);
    }

    @Override
    public void setUniform(@NonNull String uniformName, @NonNull Vector3f value) {
        glUniform3f(getUniformLocation(uniformName), value.x, value.y, value.z);
    }

    @Override
    public void setUniform(@NonNull String uniformName, @NonNull Vector4f value) {
        glUniform4f(getUniformLocation(uniformName), value.x, value.y, value.z, value.w);
    }

    @Override
    public void setUniform(@NonNull String uniformName, @NonNull Texture texture) {
        glUniformHandleui64ARB(getUniformLocation(uniformName), texture.handle());
    }
}
