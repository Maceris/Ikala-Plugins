/*
 * NOTICE: This file is a modified version of contents from
 * https://github.com/lwjglgamedev/lwjglbook, which was licensed under Apache
 * v2.0. Changes have been made related to formatting, functionality, and
 * naming.
 */
package com.ikalagaming.graphics.graph;

import static org.lwjgl.opengl.GL20.GL_COMPILE_STATUS;
import static org.lwjgl.opengl.GL20.GL_LINK_STATUS;
import static org.lwjgl.opengl.GL20.GL_VALIDATE_STATUS;
import static org.lwjgl.opengl.GL20.glAttachShader;
import static org.lwjgl.opengl.GL20.glCompileShader;
import static org.lwjgl.opengl.GL20.glCreateProgram;
import static org.lwjgl.opengl.GL20.glCreateShader;
import static org.lwjgl.opengl.GL20.glDeleteProgram;
import static org.lwjgl.opengl.GL20.glDetachShader;
import static org.lwjgl.opengl.GL20.glGetProgramInfoLog;
import static org.lwjgl.opengl.GL20.glGetProgrami;
import static org.lwjgl.opengl.GL20.glGetShaderInfoLog;
import static org.lwjgl.opengl.GL20.glGetShaderi;
import static org.lwjgl.opengl.GL20.glLinkProgram;
import static org.lwjgl.opengl.GL20.glShaderSource;
import static org.lwjgl.opengl.GL20.glUseProgram;
import static org.lwjgl.opengl.GL20.glValidateProgram;

import com.ikalagaming.graphics.GraphicsPlugin;
import com.ikalagaming.graphics.exceptions.ShaderException;
import com.ikalagaming.launcher.PluginFolder;
import com.ikalagaming.launcher.PluginFolder.ResourceType;
import com.ikalagaming.util.FileUtils;
import com.ikalagaming.util.SafeResourceLoader;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.lwjgl.opengl.GL20;

import java.util.ArrayList;
import java.util.List;

/** An OpenGL shader program. */
@Getter
@Slf4j
public class ShaderProgram {

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
    public record ShaderModuleData(@NonNull String shaderFile, int shaderType) {}

    /**
     * The Program ID for the program.
     *
     * @return The opengl program ID.
     */
    private final int programID;

    /**
     * Create a new shader program.
     *
     * @param shaderModuleDataList The list of shader modules for the program.
     * @throws ShaderException If a new program could not be created.
     */
    public ShaderProgram(@NonNull List<ShaderModuleData> shaderModuleDataList) {
        programID = glCreateProgram();
        if (programID == 0) {
            String error =
                    SafeResourceLoader.getString(
                            "SHADER_ERROR_ZERO_ID", GraphicsPlugin.getResourceBundle());
            log.info(error);
            throw new ShaderException(error);
        }

        List<Integer> shaderModules = new ArrayList<>();

        shaderModuleDataList.forEach(
                data ->
                        shaderModules.add(
                                createShader(
                                        FileUtils.readAsString(
                                                PluginFolder.getResource(
                                                        GraphicsPlugin.PLUGIN_NAME,
                                                        ResourceType.DATA,
                                                        data.shaderFile)),
                                        data.shaderType)));

        link(shaderModules);
        validate();
    }

    /** Install this program as part of the current rendering state. */
    public void bind() {
        glUseProgram(programID);
    }

    /** Unbind and delete this program. */
    public void cleanup() {
        unbind();
        if (programID != 0) {
            glDeleteProgram(programID);
        }
    }

    /**
     * Create a shader from code.
     *
     * @param shaderCode The code to compile.
     * @param shaderType The type of shader we are compiling.
     * @return The newly created shader ID.
     * @throws ShaderException If there was an error creating or compiling the shader.
     */
    protected int createShader(@NonNull String shaderCode, int shaderType) {
        int shaderId = glCreateShader(shaderType);
        if (shaderId == 0) {
            String error =
                    SafeResourceLoader.getString(
                            "SHADER_ERROR_CREATING", GraphicsPlugin.getResourceBundle());
            log.info(error, shaderType);
            throw new ShaderException(SafeResourceLoader.format(error, "" + shaderType));
        }

        glShaderSource(shaderId, shaderCode);
        glCompileShader(shaderId);

        if (glGetShaderi(shaderId, GL_COMPILE_STATUS) == 0) {
            String error =
                    SafeResourceLoader.getString(
                            "SHADER_ERROR_COMPILING", GraphicsPlugin.getResourceBundle());
            log.info(error, glGetShaderInfoLog(shaderId, 1024));
            throw new ShaderException(
                    SafeResourceLoader.format(error, glGetShaderInfoLog(shaderId, 1024)));
        }

        glAttachShader(programID, shaderId);

        return shaderId;
    }

    /**
     * Link and validate this program.
     *
     * @param shaderModules The modules to link.
     */
    private void link(@NonNull List<Integer> shaderModules) {
        glLinkProgram(programID);
        if (glGetProgrami(programID, GL_LINK_STATUS) == 0) {
            String error =
                    SafeResourceLoader.getString(
                            "SHADER_ERROR_LINKING", GraphicsPlugin.getResourceBundle());
            log.info(error, glGetProgramInfoLog(programID, 1024));
            throw new ShaderException(
                    SafeResourceLoader.format(error, glGetProgramInfoLog(programID, 1024)));
        }

        shaderModules.forEach(s -> glDetachShader(programID, s));
        shaderModules.forEach(GL20::glDeleteShader);
    }

    /** Stop using this program. */
    public void unbind() {
        glUseProgram(0);
    }

    /** Validate the program. */
    public void validate() {
        glValidateProgram(programID);
        if (glGetProgrami(programID, GL_VALIDATE_STATUS) == 0) {
            String error =
                    SafeResourceLoader.getString(
                            "SHADER_ERROR_VALIDATION_WARNING", GraphicsPlugin.getResourceBundle());
            log.warn(error, glGetProgramInfoLog(programID, 1024));
            throw new ShaderException(
                    SafeResourceLoader.format(error, glGetProgramInfoLog(programID, 1024)));
        }
    }
}
