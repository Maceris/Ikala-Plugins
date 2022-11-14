package com.ikalagaming.graphics.graph;

import static org.lwjgl.opengl.GL20.glGetUniformLocation;
import static org.lwjgl.opengl.GL20.glUniform1f;
import static org.lwjgl.opengl.GL20.glUniform1i;
import static org.lwjgl.opengl.GL20.glUniform2f;
import static org.lwjgl.opengl.GL20.glUniform3f;
import static org.lwjgl.opengl.GL20.glUniform4f;
import static org.lwjgl.opengl.GL20.glUniformMatrix4fv;

import com.ikalagaming.graphics.exceptions.ShaderException;

import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Allows for creation and usage of uniforms for a shader program.
 */
public class UniformsMap {
	/**
	 * The shader program identifier.
	 */
	final private int programId;
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
		this.programId = programId;
		uniforms = new HashMap<>();
	}

	/**
	 * Create a new uniform, which should match the one defined in the shader
	 * code.
	 * 
	 * @param uniformName The name of the new uniform.
	 * @throws ShaderException If the uniform isn't found.
	 */
	public void createUniform(String uniformName) {
		int uniformLocation = glGetUniformLocation(programId, uniformName);
		if (uniformLocation < 0) {
			throw new ShaderException("Could not find uniform [" + uniformName
				+ "] in shader program [" + programId + "]");
		}
		uniforms.put(uniformName, uniformLocation);
	}

	/**
	 * Create a list of new uniforms, which should match the ones defined in the
	 * shader code.
	 * 
	 * @param uniformNames The names of the new uniforms to create.
	 * @throws ShaderException For any uniforms that aren't found.
	 */
	public void createUniforms(String[] uniformNames) {
		List<String> errors = new ArrayList<>();
		for (String uniform : uniformNames) {
			int uniformLocation = glGetUniformLocation(programId, uniform);
			if (uniformLocation < 0) {
				errors.add(uniform);
				continue;
			}
			uniforms.put(uniform, uniformLocation);
		}
		if (errors.size() > 0) {
			throw new ShaderException("Could not find uniforms ["
				+ String.join(", ", errors.toArray(new String[0]))
				+ "] in shader program [" + programId + "]");
		}

	}

	/**
	 * Get the location of a uniform.
	 * 
	 * @param uniformName The name of the uniform.
	 * @return The uniform location.
	 * @throws ShaderException If the uniform can't be found.
	 */
	private int getUniformLocation(String uniformName) {
		Integer location = uniforms.get(uniformName);
		if (location == null) {
			throw new ShaderException(
				"Could not find uniform [" + uniformName + "]");
		}
		return location.intValue();
	}

	/**
	 * Set a Matrix4f uniform.
	 * 
	 * @param uniformName The name of the uniform.
	 * @param value The value to set.
	 */
	public void setUniform(String uniformName, Matrix4f value) {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			glUniformMatrix4fv(getUniformLocation(uniformName), false,
				value.get(stack.mallocFloat(16)));
		}
	}

	/**
	 * Set a float uniform.
	 * 
	 * @param uniformName The name of the uniform.
	 * @param value The value to set.
	 */
	public void setUniform(String uniformName, float value) {
		glUniform1f(getUniformLocation(uniformName), value);
	}

	/**
	 * Set an int uniform.
	 * 
	 * @param uniformName The name of the uniform.
	 * @param value The value to set.
	 */
	public void setUniform(String uniformName, int value) {
		glUniform1i(getUniformLocation(uniformName), value);
	}

	/**
	 * Set a Vector3f uniform.
	 * 
	 * @param uniformName The name of the uniform.
	 * @param value The value to set.
	 */
	public void setUniform(String uniformName, Vector3f value) {
		glUniform3f(getUniformLocation(uniformName), value.x, value.y, value.z);
	}

	/**
	 * Set a Vector4f uniform.
	 * 
	 * @param uniformName The name of the uniform.
	 * @param value The value to set.
	 */
	public void setUniform(String uniformName, Vector4f value) {
		glUniform4f(getUniformLocation(uniformName), value.x, value.y, value.z,
			value.w);
	}

	/**
	 * Set a Vector2f uniform.
	 * 
	 * @param uniformName The name of the uniform.
	 * @param value The value to set.
	 */
	public void setUniform(String uniformName, Vector2f value) {
		glUniform2f(getUniformLocation(uniformName), value.x, value.y);
	}

	/**
	 * Set a uniform of matrix arrays. If matrices is null, does nothing.
	 * 
	 * @param uniformName The name of the uniform.
	 * @param matrices The values to set.
	 */
	public void setUniform(String uniformName, Matrix4f[] matrices) {
		if (matrices == null) {
			return;
		}
		try (MemoryStack stack = MemoryStack.stackPush()) {
			final int length = matrices.length;
			FloatBuffer fb = stack.mallocFloat(16 * length);
			for (int i = 0; i < length; i++) {
				matrices[i].get(16 * i, fb);
			}
			glUniformMatrix4fv(uniforms.get(uniformName), false, fb);
		}
	}
}
