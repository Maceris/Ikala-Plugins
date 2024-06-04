package com.ikalagaming.graphics.backend.opengl;

import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL20.glGetProgramInfoLog;
import static org.lwjgl.opengl.GL43.GL_COMPUTE_SHADER;

import com.ikalagaming.graphics.GraphicsPlugin;
import com.ikalagaming.graphics.backend.base.UniformsMap;
import com.ikalagaming.graphics.exceptions.ShaderException;
import com.ikalagaming.graphics.frontend.Shader;
import com.ikalagaming.launcher.PluginFolder;
import com.ikalagaming.util.FileUtils;
import com.ikalagaming.util.SafeResourceLoader;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.lwjgl.opengl.GL20;

import java.util.ArrayList;
import java.util.List;

@Getter
@Slf4j
public class ShaderOpenGL implements Shader {

    /**
     * Map the shader type to an OpenGL constant version.
     *
     * @param type The type of shader.
     * @return The corresponding OpenGL type.
     */
    private static int mapShaderType(@NonNull Shader.Type type) {
        return switch (type) {
            case VERTEX -> GL_VERTEX_SHADER;
            case FRAGMENT -> GL_FRAGMENT_SHADER;
            case COMPUTE -> GL_COMPUTE_SHADER;
        };
    }

    /** The Program ID for the program. */
    private final int programID;

    /** The uniform map for this shader. */
    @Setter private @NonNull UniformsMap uniforms;

    /**
     * Create a new shader program.
     *
     * @param shaderModuleDataList The list of shader modules for the program.
     * @throws ShaderException If a new program could not be created.
     */
    public ShaderOpenGL(@NonNull List<ShaderModuleData> shaderModuleDataList) {
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
                                                        PluginFolder.ResourceType.DATA,
                                                        data.shaderFile())),
                                        mapShaderType(data.shaderType()))));

        link(shaderModules);
        validate();
    }

    @Override
    public void bind() {
        glUseProgram(programID);
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

    @Override
    public UniformsMap getUniformMap() {
        return uniforms;
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

    @Override
    public void unbind() {
        glUseProgram(0);
    }

    /** Validate the program. */
    private void validate() {
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
