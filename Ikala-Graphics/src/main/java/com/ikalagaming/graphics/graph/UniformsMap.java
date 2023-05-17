/*
 * NOTICE: This file is a modified version of contents from
 * https://github.com/lwjglgamedev/lwjglbook, which was licensed under Apache
 * v2.0. Changes have been made related to formatting, functionality, and
 * naming.
 */
package com.ikalagaming.graphics.graph;

import com.ikalagaming.graphics.GraphicsPlugin;
import com.ikalagaming.graphics.exceptions.ShaderException;
import com.ikalagaming.util.SafeResourceLoader;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL20;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * Allows for creation and usage of uniforms for a shader program.
 */
@Slf4j
public class UniformsMap {
	/**
	 * The shader program identifier.
	 */
	private int programID;
	/**
	 * The mapping of uniform name to integer location for the shader program.
	 */
	private Map<String, Integer> uniforms;

	/**
	 * Create a new uniform map for the given program ID.
	 *
	 * @param programId The shader program identifier.
	 */
	public UniformsMap(int programId) {
		this.programID = programId;
		this.uniforms = new HashMap<>();
	}

	/**
	 * Create a new uniform, which should match the one defined in the shader
	 * code.
	 *
	 * @param uniformName The name of the new uniform.
	 * @throws RuntimeException If the uniform isn't found.
	 */
	public void createUniform(@NonNull String uniformName) {
		int uniformLocation =
			GL20.glGetUniformLocation(this.programID, uniformName);
		if (uniformLocation < 0) {

			String error = SafeResourceLoader.getString("UNIFORM_MISSING",
				GraphicsPlugin.getResourceBundle());
			UniformsMap.log.info(error, uniformName, this.programID);

			throw new ShaderException(SafeResourceLoader.format(error,
				uniformName, this.programID + ""));
		}
		this.uniforms.put(uniformName, uniformLocation);
	}

	/**
	 * Get the location of a uniform.
	 *
	 * @param uniformName The name of the uniform.
	 * @return The uniform location.
	 * @throws RuntimeException If the uniform can't be found.
	 */
	private int getUniformLocation(@NonNull String uniformName) {
		Integer location = this.uniforms.get(uniformName);
		if (location == null) {
			String error = SafeResourceLoader.getString(
				"UNIFORM_LOCATION_MISSING", GraphicsPlugin.getResourceBundle());
			UniformsMap.log.info(error, uniformName, this.programID);
			throw new ShaderException(
				SafeResourceLoader.format(error, uniformName));
		}
		return location.intValue();
	}

	/**
	 * Set a float uniform.
	 *
	 * @param uniformName The name of the uniform.
	 * @param value The value to set.
	 */
	public void setUniform(@NonNull String uniformName, float value) {
		GL20.glUniform1f(this.getUniformLocation(uniformName), value);
	}

	/**
	 * Set an int uniform.
	 *
	 * @param uniformName The name of the uniform.
	 * @param value The value to set.
	 */
	public void setUniform(@NonNull String uniformName, int value) {
		GL20.glUniform1i(this.getUniformLocation(uniformName), value);
	}

	/**
	 * Set a Matrix4f uniform.
	 *
	 * @param uniformName The name of the uniform.
	 * @param value The value to set.
	 */
	public void setUniform(@NonNull String uniformName,
		@NonNull Matrix4f value) {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			GL20.glUniformMatrix4fv(this.getUniformLocation(uniformName), false,
				value.get(stack.mallocFloat(16)));
		}
	}

	/**
	 * Set a uniform of matrix arrays.
	 *
	 * @param uniformName The name of the uniform.
	 * @param matrices The values to set.
	 */
	public void setUniform(@NonNull String uniformName,
		@NonNull Matrix4f[] matrices) {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			final int length = matrices.length;
			FloatBuffer fb = stack.mallocFloat(16 * length);
			for (int i = 0; i < length; ++i) {
				matrices[i].get(16 * i, fb);
			}
			GL20.glUniformMatrix4fv(this.uniforms.get(uniformName), false, fb);
		}
	}

	/**
	 * Set a Vector2f uniform.
	 *
	 * @param uniformName The name of the uniform.
	 * @param value The value to set.
	 */
	public void setUniform(@NonNull String uniformName,
		@NonNull Vector2f value) {
		GL20.glUniform2f(this.getUniformLocation(uniformName), value.x,
			value.y);
	}

	/**
	 * Set a Vector3f uniform.
	 *
	 * @param uniformName The name of the uniform.
	 * @param value The value to set.
	 */
	public void setUniform(@NonNull String uniformName,
		@NonNull Vector3f value) {
		GL20.glUniform3f(this.getUniformLocation(uniformName), value.x, value.y,
			value.z);
	}

	/**
	 * Set a Vector4f uniform.
	 *
	 * @param uniformName The name of the uniform.
	 * @param value The value to set.
	 */
	public void setUniform(@NonNull String uniformName,
		@NonNull Vector4f value) {
		GL20.glUniform4f(this.getUniformLocation(uniformName), value.x, value.y,
			value.z, value.w);
	}
}
