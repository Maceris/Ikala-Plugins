/*
 * NOTICE: This file is a modified version of contents from
 * https://github.com/lwjglgamedev/lwjglbook, which was licensed under Apache
 * v2.0. Changes have been made related to formatting, functionality, and
 * naming.
 */
package com.ikalagaming.graphics.graph;

import com.ikalagaming.graphics.GraphicsPlugin;
import com.ikalagaming.graphics.exceptions.ShaderException;
import com.ikalagaming.launcher.PluginFolder;
import com.ikalagaming.launcher.PluginFolder.ResourceType;
import com.ikalagaming.util.FileUtils;
import com.ikalagaming.util.SafeResourceLoader;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GL40;

import java.util.ArrayList;
import java.util.List;

/**
 * An OpenGL shader program.
 */
@Slf4j
public class ShaderProgram {

	/**
	 * A file and what type of shader that is.
	 */
	@Getter
	@AllArgsConstructor
	public static class ShaderModuleData {
		/**
		 * The path to the shader file including name.
		 *
		 * @param The shader file to load.
		 * @return The shader file to load.
		 */
		private final String shaderFile;
		/**
		 * The type of shader to be created. One of:<br/>
		 * <ul>
		 * <li>{@link GL20C#GL_VERTEX_SHADER VERTEX_SHADER}</li>
		 * <li>{@link GL20C#GL_FRAGMENT_SHADER FRAGMENT_SHADER}</li>
		 * <li>{@link GL32#GL_GEOMETRY_SHADER GEOMETRY_SHADER}</li>
		 * <li>{@link GL40#GL_TESS_CONTROL_SHADER TESS_CONTROL_SHADER}</li>
		 * <li>{@link GL40#GL_TESS_EVALUATION_SHADER
		 * GL_TESS_EVALUATION_SHADER}</li>
		 * </ul>
		 *
		 * @param shaderType The type of shader.
		 * @return The type of shader.
		 */
		private final int shaderType;
	}

	/**
	 * The Program ID for the program.
	 * 
	 * @return The opengl program ID.
	 */
	@Getter
	private final int programID;

	/**
	 * Create a new shader program.
	 *
	 * @param shaderModuleDataList The list of shader modules for the program.
	 * @throws ShaderException If an new program could not be created.
	 */
	public ShaderProgram(@NonNull List<ShaderModuleData> shaderModuleDataList) {
		this.programID = GL20.glCreateProgram();
		if (this.programID == 0) {
			String error = SafeResourceLoader.getString("SHADER_ERROR_ZERO_ID",
				GraphicsPlugin.getResourceBundle());
			ShaderProgram.log.info(error);
			throw new ShaderException(error);
		}

		List<Integer> shaderModules = new ArrayList<>();

		shaderModuleDataList
			.forEach(data -> shaderModules.add(this.createShader(
				FileUtils.readAsString(
					PluginFolder.getResource(GraphicsPlugin.PLUGIN_NAME,
						ResourceType.DATA, data.shaderFile)),
				data.shaderType)));

		this.link(shaderModules);
	}

	/**
	 * Install this program as part of the current rendering state.
	 */
	public void bind() {
		GL20.glUseProgram(this.programID);
	}

	/**
	 * Unbind and delete this program.
	 */
	public void cleanup() {
		this.unbind();
		if (this.programID != 0) {
			GL20.glDeleteProgram(this.programID);
		}
	}

	/**
	 * Create a shader from code.
	 *
	 * @param shaderCode The code to compile.
	 * @param shaderType The type of shader we are compiling.
	 * @return The newly created shader ID.
	 * @throws ShaderException If there was an error creating or compiling the
	 *             shader.
	 */
	protected int createShader(@NonNull String shaderCode, int shaderType) {
		int shaderId = GL20.glCreateShader(shaderType);
		if (shaderId == 0) {
			String error = SafeResourceLoader.getString("SHADER_ERROR_CREATING",
				GraphicsPlugin.getResourceBundle());
			ShaderProgram.log.info(error, shaderType);
			throw new ShaderException(
				SafeResourceLoader.format(error, "" + shaderType));
		}

		GL20.glShaderSource(shaderId, shaderCode);
		GL20.glCompileShader(shaderId);

		if (GL20.glGetShaderi(shaderId, GL20.GL_COMPILE_STATUS) == 0) {
			String error = SafeResourceLoader.getString(
				"SHADER_ERROR_COMPILING", GraphicsPlugin.getResourceBundle());
			ShaderProgram.log.info(error,
				GL20.glGetShaderInfoLog(shaderId, 1024));
			throw new ShaderException(SafeResourceLoader.format(error,
				GL20.glGetShaderInfoLog(shaderId, 1024)));
		}

		GL20.glAttachShader(this.programID, shaderId);

		return shaderId;
	}

	/**
	 * Link and validate this program.
	 *
	 * @param shaderModules The modules to link.
	 */
	private void link(@NonNull List<Integer> shaderModules) {
		GL20.glLinkProgram(this.programID);
		if (GL20.glGetProgrami(this.programID, GL20.GL_LINK_STATUS) == 0) {
			String error = SafeResourceLoader.getString("SHADER_ERROR_LINKING",
				GraphicsPlugin.getResourceBundle());
			ShaderProgram.log.info(error,
				GL20.glGetProgramInfoLog(this.programID, 1024));
			throw new ShaderException(SafeResourceLoader.format(error,
				GL20.glGetProgramInfoLog(this.programID, 1024)));
		}

		shaderModules.forEach(s -> GL20.glDetachShader(this.programID, s));
		shaderModules.forEach(GL20::glDeleteShader);
	}

	/**
	 * Stop using this program.
	 */
	public void unbind() {
		GL20.glUseProgram(0);
	}

	/**
	 * Validate the program.
	 */
	public void validate() {
		GL20.glValidateProgram(this.programID);
		if (GL20.glGetProgrami(this.programID, GL20.GL_VALIDATE_STATUS) == 0) {
			String error =
				SafeResourceLoader.getString("SHADER_ERROR_VALIDATION_WARNING",
					GraphicsPlugin.getResourceBundle());
			ShaderProgram.log.warn(error,
				GL20.glGetProgramInfoLog(this.programID, 1024));
			throw new ShaderException(SafeResourceLoader.format(error,
				GL20.glGetProgramInfoLog(this.programID, 1024)));
		}
	}
}