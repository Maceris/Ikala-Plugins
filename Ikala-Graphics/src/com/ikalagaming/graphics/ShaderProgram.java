package com.ikalagaming.graphics;

import lombok.extern.slf4j.Slf4j;
import org.lwjgl.opengl.GL20;

/**
 * An OpenGL shader program.
 */
@Slf4j
public class ShaderProgram {

	private final int programId;
	private int vertexShaderId;
	private int fragmentShaderId;

	/**
	 * Create a new shader program.
	 *
	 * @throws RuntimeException If an new program could not be created.
	 */
	public ShaderProgram() {
		this.programId = GL20.glCreateProgram();
		if (this.programId == 0) {
			throw new RuntimeException("Could not create Shader");
		}
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
	 * @throws RuntimeException If there was an error creating or compiling the
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
	 * @throws RuntimeException If there was an error creating or compiling the
	 *             shader.
	 */
	protected int createShader(String shaderCode, int shaderType) {
		int shaderId = GL20.glCreateShader(shaderType);
		if (shaderId == 0) {
			throw new RuntimeException(
				"Error creating shader. Type: " + shaderType);
		}

		GL20.glShaderSource(shaderId, shaderCode);
		GL20.glCompileShader(shaderId);

		if (GL20.glGetShaderi(shaderId, GL20.GL_COMPILE_STATUS) == 0) {
			throw new RuntimeException("Error compiling Shader code: "
				+ GL20.glGetShaderInfoLog(shaderId, 1024));
		}

		GL20.glAttachShader(this.programId, shaderId);

		return shaderId;
	}

	/**
	 * Create a vertex shader.
	 *
	 * @param shaderCode The shader code to compile.
	 * @throws RuntimeException If there was an error creating or compiling the
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
			throw new RuntimeException("Error linking Shader code: "
				+ GL20.glGetProgramInfoLog(this.programId, 1024));
		}

		if (this.vertexShaderId != 0) {
			GL20.glDetachShader(this.programId, this.vertexShaderId);
		}
		if (this.fragmentShaderId != 0) {
			GL20.glDetachShader(this.programId, this.fragmentShaderId);
		}

		GL20.glValidateProgram(this.programId);
		if (GL20.glGetProgrami(this.programId, GL20.GL_VALIDATE_STATUS) == 0) {
			ShaderProgram.log.warn("Warning validating Shader code: {}",
				GL20.glGetProgramInfoLog(this.programId, 1024));
		}

	}

	/**
	 * Stop using this program.
	 */
	public void unbind() {
		GL20.glUseProgram(0);
	}
}
