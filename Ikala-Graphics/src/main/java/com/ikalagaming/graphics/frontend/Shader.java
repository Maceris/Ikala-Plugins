package com.ikalagaming.graphics.frontend;

import lombok.NonNull;

/** A shader program. */
public interface Shader {
    /**
     * A file and what type of shader that is.
     *
     * @param shaderFile The path to the shader file including name.
     * @param shaderType The type of shader to be created.
     */
    record ShaderModuleData(@NonNull String shaderFile, Type shaderType) {}

    /** The type of shader module. */
    enum Type {
        VERTEX,
        FRAGMENT,
        COMPUTE
    }

    /** Install this program as part of the current rendering state. */
    void bind();

    /** Unbind and delete this program. */
    void cleanup();

    /**
     * Returns the unique ID for the program.
     *
     * @return The implementation-specific unique ID.
     */
    int getProgramID();

    /** Stop using this program. */
    void unbind();
}
