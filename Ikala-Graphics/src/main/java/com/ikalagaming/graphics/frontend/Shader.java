package com.ikalagaming.graphics.frontend;

import com.ikalagaming.graphics.backend.base.UniformsMap;

import lombok.NonNull;

/** A shader program. */
public interface Shader {
    /**
     * A file and what type of shader that is.
     *
     * @param shaderFile The path to the shader file including name.
     * @param shaderType The type of shader to be created.
     * @param location The general location of the shader file.
     */
    record ShaderModuleData(
            @NonNull String shaderFile, Type shaderType, @NonNull Location location) {}

    /** The type of shader module. */
    enum Type {
        VERTEX,
        FRAGMENT,
        COMPUTE
    }

    /** Where the shader can be found. */
    enum Location {
        /** In the jar resources. */
        BUNDLED,
        /** In the plugin data folder. */
        DATA_FOLDER,
    }

    /** Install this program as part of the current rendering state. */
    void bind();

    /**
     * Returns the unique ID for the program.
     *
     * @return The implementation-specific unique ID.
     */
    int getProgramID();

    /**
     * Fetch the uniform map for this shader.
     *
     * @return The uniform map.
     */
    UniformsMap getUniformMap();

    /** Stop using this program. */
    void unbind();
}
