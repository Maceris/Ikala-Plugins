package com.ikalagaming.graphics.frontend;

import lombok.NonNull;
import org.lwjgl.opengl.GL20;

/** A shader program. */
public interface Shader {
    /**
     * A file and what type of shader that is.
     *
     * @param shaderFile The path to the shader file including name.
     * @param shaderType The type of shader to be created. One of:<br>
     *     <ul>
     *       <li>{@link GL20#GL_VERTEX_SHADER VERTEX_SHADER}
     *       <li>{@link org.lwjgl.opengl.GL46#GL_FRAGMENT_SHADER FRAGMENT_SHADER}
     *       <li>{@link org.lwjgl.opengl.GL33#GL_GEOMETRY_SHADER GEOMETRY_SHADER}
     *       <li>{@link org.lwjgl.opengl.GL46#GL_TESS_CONTROL_SHADER TESS_CONTROL_SHADER}
     *       <li>{@link org.lwjgl.opengl.GL46#GL_TESS_EVALUATION_SHADER GL_TESS_EVALUATION_SHADER}
     *     </ul>
     */
    record ShaderModuleData(@NonNull String shaderFile, int shaderType) {}

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
