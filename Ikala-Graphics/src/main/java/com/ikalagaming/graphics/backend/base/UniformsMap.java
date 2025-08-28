package com.ikalagaming.graphics.backend.base;

import com.ikalagaming.graphics.exceptions.ShaderException;
import com.ikalagaming.graphics.frontend.Texture;

import lombok.NonNull;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/** Allows for creation and usage of uniforms for a shader program. */
public interface UniformsMap {
    /**
     * Create a new uniform, which should match the one defined in the shader code.
     *
     * @param uniformName The name of the new uniform.
     * @throws ShaderException If the uniform isn't found.
     */
    void createUniform(@NonNull String uniformName);

    /**
     * Set a float uniform.
     *
     * @param uniformName The name of the uniform.
     * @param value The value to set.
     */
    void setUniform(@NonNull String uniformName, float value);

    /**
     * Set an int uniform.
     *
     * @param uniformName The name of the uniform.
     * @param value The value to set.
     */
    void setUniform(@NonNull String uniformName, int value);

    /**
     * Set an unsigned int uniform.
     *
     * @param uniformName The name of the uniform.
     * @param value The value to set.
     */
    void setUniformUnsigned(@NonNull String uniformName, int value);

    /**
     * Set a Matrix4f uniform.
     *
     * @param uniformName The name of the uniform.
     * @param value The value to set.
     */
    void setUniform(@NonNull String uniformName, @NonNull Matrix4f value);

    /**
     * Set a uniform of matrix arrays.
     *
     * @param uniformName The name of the uniform.
     * @param matrices The values to set.
     */
    void setUniform(@NonNull String uniformName, @NonNull Matrix4f[] matrices);

    /**
     * Set a Vector2f uniform.
     *
     * @param uniformName The name of the uniform.
     * @param value The value to set.
     */
    void setUniform(@NonNull String uniformName, @NonNull Vector2f value);

    /**
     * Set a Vector3f uniform.
     *
     * @param uniformName The name of the uniform.
     * @param value The value to set.
     */
    void setUniform(@NonNull String uniformName, @NonNull Vector3f value);

    /**
     * Set a Vector4f uniform.
     *
     * @param uniformName The name of the uniform.
     * @param value The value to set.
     */
    void setUniform(@NonNull String uniformName, @NonNull Vector4f value);

    /**
     * Sets the bindless handle for a texture sampler.
     *
     * @param uniformName The name of the uniform.
     * @param texture The texture set a bindless sampler handle from. Null to set a value of 0.
     */
    void setUniform(@NonNull String uniformName, Texture texture);
}
