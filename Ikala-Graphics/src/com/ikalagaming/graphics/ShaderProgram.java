package com.ikalagaming.graphics;

import com.ikalagaming.graphics.exceptions.ShaderException;
import com.ikalagaming.util.SafeResourceLoader;

import lombok.extern.slf4j.Slf4j;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL20;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * An OpenGL shader program.
 */
@Slf4j
public class ShaderProgram {

	private int fragmentShaderId;
	private final int programId;
	private final Map<String, Integer> uniforms;
	private int vertexShaderId;

	/**
	 * Create a new shader program.
	 *
	 * @throws RuntimeException If an new program could not be created.
	 */
	public ShaderProgram() {
		this.programId = GL20.glCreateProgram();
		if (this.programId == 0) {
			throw new ShaderException("Could not create Shader");
		}
		this.uniforms = new HashMap<>();
	}

	/**
	 * Install this program as part of the current rendering state.
	 */
	public void bind() {
		GL20.glUseProgram(this.programId);
	}

	/**
	 * Unbind and delete this program.
	 */
	public void cleanup() {
		this.unbind();
		if (this.programId != 0) {
			GL20.glDeleteProgram(this.programId);
		}
	}

	/**
	 * Create a fragment shader given shader code.
	 *
	 * @param shaderCode The shader code to compile.
	 * @throws ShaderException If there was an error creating or compiling the
	 *             shader.
	 */
	public void createFragmentShader(String shaderCode) {
		this.fragmentShaderId =
			this.createShader(shaderCode, GL20.GL_FRAGMENT_SHADER);
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
	protected int createShader(String shaderCode, int shaderType) {
		int shaderId = GL20.glCreateShader(shaderType);
		if (shaderId == 0) {
			ShaderProgram.log
				.info(SafeResourceLoader.getString("SHADER_ERROR_CREATING",
					GraphicsPlugin.getResourceBundle()), shaderType);
			throw new ShaderException();
		}

		GL20.glShaderSource(shaderId, shaderCode);
		GL20.glCompileShader(shaderId);

		if (GL20.glGetShaderi(shaderId, GL20.GL_COMPILE_STATUS) == 0) {
			String message = SafeResourceLoader.getString(
				"SHADER_ERROR_COMPILING", GraphicsPlugin.getResourceBundle());
			ShaderProgram.log.info(message, shaderType);
			throw new ShaderException(
				message + " " + GL20.glGetShaderInfoLog(shaderId, 1024));
		}

		GL20.glAttachShader(this.programId, shaderId);

		return shaderId;
	}

	/**
	 * Create a new uniform, which is a global GLSL variable for communicating
	 * with shaders.
	 *
	 * @param name The name of the uniform.\
	 * @throws ShaderException If the uniform cannot be found.
	 */
	public void createUniform(String name) {
		int uniformLocation = GL20.glGetUniformLocation(this.programId, name);
		if (uniformLocation < 0) {
			ShaderProgram.log.warn(
				SafeResourceLoader.getString("SHADER_ERROR_MISSING_UNIFORM",
					GraphicsPlugin.getResourceBundle()),
				name);
			throw new ShaderException();
		}
		this.uniforms.put(name, uniformLocation);
	}

	/**
	 * Create a vertex shader.
	 *
	 * @param shaderCode The shader code to compile.
	 * @throws ShaderException If there was an error creating or compiling the
	 *             shader.
	 */
	public void createVertexShader(String shaderCode) {
		this.vertexShaderId =
			this.createShader(shaderCode, GL20.GL_VERTEX_SHADER);
	}

	/**
	 * Link and validate this program.
	 */
	public void link() {
		GL20.glLinkProgram(this.programId);
		if (GL20.glGetProgrami(this.programId, GL20.GL_LINK_STATUS) == 0) {
			String message = SafeResourceLoader.getString(
				"SHADER_ERROR_LINKING", GraphicsPlugin.getResourceBundle());
			ShaderProgram.log.warn(message);
			throw new ShaderException(
				message + " " + GL20.glGetProgramInfoLog(this.programId, 1024));
		}

		if (this.vertexShaderId != 0) {
			GL20.glDetachShader(this.programId, this.vertexShaderId);
		}
		if (this.fragmentShaderId != 0) {
			GL20.glDetachShader(this.programId, this.fragmentShaderId);
		}

		GL20.glValidateProgram(this.programId);
		if (GL20.glGetProgrami(this.programId, GL20.GL_VALIDATE_STATUS) == 0) {
			ShaderProgram.log.warn(
				SafeResourceLoader.getString("SHADER_ERROR_VALIDATION_WARNING",
					GraphicsPlugin.getResourceBundle()),
				GL20.glGetProgramInfoLog(this.programId, 1024));
		}
	}

	/**
	 * Set the value of a uniform to the provided integer value.
	 *
	 * @param uniformName The name of the uniform to set.
	 * @param value The value to set.
	 */
	public void setUniform(String uniformName, int value) {
		GL20.glUniform1i(this.uniforms.get(uniformName), value);
	}

	/**
	 * Set the value of a uniform to the provided matrix.
	 *
	 * @param uniformName The name of the uniform to set.
	 * @param value The value to set.
	 */
	public void setUniform(String uniformName, Matrix4f value) {
		// Using auto-managed buffer due to the small size and scope
		try (MemoryStack stack = MemoryStack.stackPush()) {
			FloatBuffer fb = stack.mallocFloat(16);
			// Dump the matrix into a float buffer
			value.get(fb);
			GL20.glUniformMatrix4fv(this.uniforms.get(uniformName), false, fb);
		}
	}

	/**
	 * Stop using this program.
	 */
	public void unbind() {
		GL20.glUseProgram(0);
	}
}
