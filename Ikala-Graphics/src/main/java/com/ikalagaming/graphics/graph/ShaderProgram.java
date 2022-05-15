package com.ikalagaming.graphics.graph;

import com.ikalagaming.graphics.GraphicsPlugin;
import com.ikalagaming.graphics.ShaderConstants;
import com.ikalagaming.graphics.exceptions.ShaderException;
import com.ikalagaming.util.SafeResourceLoader;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL20;
import org.lwjgl.system.MemoryStack;

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
	 * Create a directional light uniform.
	 *
	 * @param uniformName The name of the uniform.
	 */
	public void createDirectionalLightUniform(@NonNull String uniformName) {
		final String name = uniformName + ".";
		this.createUniform(
			name + ShaderConstants.Uniform.Fragment.DirectionalLight.COLOR);
		this.createUniform(
			name + ShaderConstants.Uniform.Fragment.DirectionalLight.DIRECTION);
		this.createUniform(
			name + ShaderConstants.Uniform.Fragment.DirectionalLight.INTENSITY);
	}

	/**
	 * Create a fragment shader given shader code.
	 *
	 * @param shaderCode The shader code to compile.
	 * @throws ShaderException If there was an error creating or compiling the
	 *             shader.
	 */
	public void createFragmentShader(@NonNull String shaderCode) {
		this.fragmentShaderId =
			this.createShader(shaderCode, GL20.GL_FRAGMENT_SHADER);
	}

	/**
	 * Create a material uniform.
	 *
	 * @param uniformName The name of the uniform.
	 */
	public void createMaterialUniform(@NonNull String uniformName) {
		final String name = uniformName + ".";
		this.createUniform(
			name + ShaderConstants.Uniform.Fragment.Material.AMBIENT);
		this.createUniform(
			name + ShaderConstants.Uniform.Fragment.Material.DIFFUSE);
		this.createUniform(
			name + ShaderConstants.Uniform.Fragment.Material.SPECULAR);
		this.createUniform(
			name + ShaderConstants.Uniform.Fragment.Material.HAS_TEXTURE);
		this.createUniform(
			name + ShaderConstants.Uniform.Fragment.Material.REFLECTANCE);
	}

	/**
	 * Create a uniform for a point light list.
	 *
	 * @param uniformName The name of the uniform.
	 * @param size The size of the list.
	 * @throws ShaderException If the size is not valid.
	 */
	public void createPointLightListUniform(@NonNull String uniformName,
		int size) {
		if (size <= 0
			|| size > ShaderConstants.Uniform.Fragment.MAX_POINT_LIGHTS) {
			throw new ShaderException("Invalid size " + size);
		}
		for (int i = 0; i < size; i++) {
			this.createPointLightUniform(uniformName + "[" + i + "]");
		}
	}

	/**
	 * Create a point light uniform.
	 *
	 * @param uniformName The name of the uniform.
	 */
	public void createPointLightUniform(@NonNull String uniformName) {
		final String name = uniformName + ".";
		this.createUniform(
			name + ShaderConstants.Uniform.Fragment.PointLight.COLOR);
		this.createUniform(
			name + ShaderConstants.Uniform.Fragment.PointLight.POSITION);
		this.createUniform(
			name + ShaderConstants.Uniform.Fragment.PointLight.INTENSITY);
		this.createUniform(
			name + ShaderConstants.Uniform.Fragment.PointLight.ATTENUATION + "."
				+ ShaderConstants.Uniform.Fragment.Attenuation.CONSTANT);
		this.createUniform(
			name + ShaderConstants.Uniform.Fragment.PointLight.ATTENUATION + "."
				+ ShaderConstants.Uniform.Fragment.Attenuation.LINEAR);
		this.createUniform(
			name + ShaderConstants.Uniform.Fragment.PointLight.ATTENUATION + "."
				+ ShaderConstants.Uniform.Fragment.Attenuation.EXPONENT);
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
	 * Create a uniform for a spot light list.
	 *
	 * @param uniformName The name of the uniform.
	 * @param size The size of the list.
	 * @throws ShaderException If the size is not valid.
	 */
	public void createSpotLightListUniform(@NonNull String uniformName,
		int size) {
		if (size <= 0
			|| size > ShaderConstants.Uniform.Fragment.MAX_SPOT_LIGHTS) {
			throw new ShaderException("Invalid size " + size);
		}
		for (int i = 0; i < size; i++) {
			this.createSpotLightUniform(uniformName + "[" + i + "]");
		}
	}

	/**
	 * Create a spot light uniform.
	 *
	 * @param uniformName The name of the uniform.
	 */
	public void createSpotLightUniform(@NonNull String uniformName) {
		final String name = uniformName + ".";
		this.createPointLightUniform(
			name + ShaderConstants.Uniform.Fragment.SpotLight.POINT_LIGHT);
		this.createUniform(
			name + ShaderConstants.Uniform.Fragment.SpotLight.CONE_DIRECTION);
		this.createUniform(
			name + ShaderConstants.Uniform.Fragment.SpotLight.CUTOFF);
	}

	/**
	 * Create a new uniform, which is a global GLSL variable for communicating
	 * with shaders.
	 *
	 * @param name The name of the uniform.\
	 * @throws ShaderException If the uniform cannot be found.
	 */
	public void createUniform(@NonNull String name) {
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
	public void createVertexShader(@NonNull String shaderCode) {
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
	 * Set a directional light uniform.
	 *
	 * @param uniformName The name of the uniform.
	 * @param directionalLight The directional light to set.
	 */
	public void setUniform(@NonNull String uniformName,
		@NonNull DirectionalLight directionalLight) {
		final String name = uniformName + ".";
		this.setUniform(
			name + ShaderConstants.Uniform.Fragment.DirectionalLight.COLOR,
			directionalLight.getColor());
		this.setUniform(
			name + ShaderConstants.Uniform.Fragment.DirectionalLight.DIRECTION,
			directionalLight.getDirection());
		this.setUniform(
			name + ShaderConstants.Uniform.Fragment.DirectionalLight.INTENSITY,
			directionalLight.getIntensity());
	}

	/**
	 * Set the value of a uniform to the provided floating point value.
	 *
	 * @param uniformName The name of the uniform to set.
	 * @param value The value to set.
	 */
	public void setUniform(@NonNull String uniformName, float value) {
		GL20.glUniform1f(this.uniforms.get(uniformName), value);
	}

	/**
	 * Set the value of a uniform to the provided integer value.
	 *
	 * @param uniformName The name of the uniform to set.
	 * @param value The value to set.
	 */
	public void setUniform(@NonNull String uniformName, int value) {
		GL20.glUniform1i(this.uniforms.get(uniformName), value);
	}

	/**
	 * Set a material uniform.
	 *
	 * @param uniformName The name of the uniform.
	 * @param material The material to set.
	 */
	public void setUniform(@NonNull String uniformName,
		@NonNull Material material) {
		final String name = uniformName + ".";
		this.setUniform(
			name + ShaderConstants.Uniform.Fragment.Material.AMBIENT,
			material.getAmbientColor());
		this.setUniform(
			name + ShaderConstants.Uniform.Fragment.Material.DIFFUSE,
			material.getDiffuseColor());
		this.setUniform(
			name + ShaderConstants.Uniform.Fragment.Material.SPECULAR,
			material.getSpecularColor());
		this.setUniform(
			name + ShaderConstants.Uniform.Fragment.Material.HAS_TEXTURE,
			material.isTextured() ? 1 : 0);
		this.setUniform(
			name + ShaderConstants.Uniform.Fragment.Material.REFLECTANCE,
			material.getReflectance());
	}

	/**
	 * Set the value of a uniform to the provided matrix.
	 *
	 * @param uniformName The name of the uniform to set.
	 * @param value The value to set.
	 */
	public void setUniform(@NonNull String uniformName,
		@NonNull Matrix4f value) {
		// Using auto-managed buffer due to the small size and scope
		try (MemoryStack stack = MemoryStack.stackPush()) {
			// Dump the matrix into a float buffer
			GL20.glUniformMatrix4fv(this.uniforms.get(uniformName), false,
				value.get(stack.mallocFloat(16)));
		}
	}

	/**
	 * Set a point light uniform.
	 *
	 * @param uniformName The name of the uniform.
	 * @param pointLight The point light to set.
	 */
	public void setUniform(@NonNull String uniformName,
		@NonNull PointLight pointLight) {
		final String name = uniformName + ".";
		this.setUniform(
			name + ShaderConstants.Uniform.Fragment.PointLight.COLOR,
			pointLight.getColor());
		this.setUniform(
			name + ShaderConstants.Uniform.Fragment.PointLight.POSITION,
			pointLight.getPosition());
		this.setUniform(
			name + ShaderConstants.Uniform.Fragment.PointLight.INTENSITY,
			pointLight.getIntensity());
		PointLight.Attenuation att = pointLight.getAttenuation();
		this.setUniform(
			name + ShaderConstants.Uniform.Fragment.PointLight.ATTENUATION + "."
				+ ShaderConstants.Uniform.Fragment.Attenuation.CONSTANT,
			att.getConstant());
		this.setUniform(
			name + ShaderConstants.Uniform.Fragment.PointLight.ATTENUATION + "."
				+ ShaderConstants.Uniform.Fragment.Attenuation.LINEAR,
			att.getLinear());
		this.setUniform(
			name + ShaderConstants.Uniform.Fragment.PointLight.ATTENUATION + "."
				+ ShaderConstants.Uniform.Fragment.Attenuation.EXPONENT,
			att.getExponent());
	}

	/**
	 * Set one of the point lights in the array uniform.
	 *
	 * @param uniformName The name of the uniform.
	 * @param pointLight The point light we are setting.
	 * @param pos The position in the point light array.
	 * @throws ShaderException If the position is outside the valid bounds.
	 */
	public void setUniform(@NonNull String uniformName,
		@NonNull PointLight pointLight, int pos) {
		if (pos < 0
			|| pos >= ShaderConstants.Uniform.Fragment.MAX_POINT_LIGHTS) {
			throw new ShaderException("Index out of bounds");
		}
		this.setUniform(uniformName + "[" + pos + "]", pointLight);
	}

	/**
	 * Sets a point light array uniform.
	 *
	 * @param uniformName The name of the uniform.
	 * @param pointLights The point light array to set.
	 * @throws ShaderException If the point light array is larger than the
	 *             maximum number supported.
	 */
	public void setUniform(@NonNull String uniformName,
		@NonNull PointLight[] pointLights) {
		if (pointLights.length > ShaderConstants.Uniform.Fragment.MAX_POINT_LIGHTS) {
			throw new ShaderException("Point light array too large");
		}
		for (int i = 0; i < pointLights.length; i++) {
			this.setUniform(uniformName, pointLights[i], i);
		}
	}

	/**
	 * Set a spot light uniform.
	 *
	 * @param uniformName The name of the uniform.
	 * @param spotLight The spotlight to set.
	 */
	public void setUniform(@NonNull String uniformName,
		@NonNull SpotLight spotLight) {
		final String name = uniformName + ".";
		this.setUniform(
			name + ShaderConstants.Uniform.Fragment.SpotLight.POINT_LIGHT,
			spotLight.getPointLight());
		this.setUniform(
			name + ShaderConstants.Uniform.Fragment.SpotLight.CONE_DIRECTION,
			spotLight.getConeDirection());
		this.setUniform(
			name + ShaderConstants.Uniform.Fragment.SpotLight.CUTOFF,
			spotLight.getCutOff());
	}

	/**
	 * Set one of the spotlights in the array uniform.
	 *
	 * @param uniformName The name of the uniform.
	 * @param spotLight The spotlight we are setting.
	 * @param pos The position in the spot light array.
	 * @throws ShaderException If the position is outside the valid bounds.
	 */
	public void setUniform(@NonNull String uniformName,
		@NonNull SpotLight spotLight, int pos) {
		if (pos < 0
			|| pos >= ShaderConstants.Uniform.Fragment.MAX_SPOT_LIGHTS) {
			throw new ShaderException("Index out of bounds");
		}
		this.setUniform(uniformName + "[" + pos + "]", spotLight);
	}

	/**
	 * Sets a spot light array uniform.
	 *
	 * @param uniformName The name of the uniform.
	 * @param spotLights The spot light array to set.
	 * @throws ShaderException If the spot light array is larger than the
	 *             maximum number supported.
	 */
	public void setUniform(@NonNull String uniformName,
		@NonNull SpotLight[] spotLights) {
		if (spotLights.length > ShaderConstants.Uniform.Fragment.MAX_SPOT_LIGHTS) {
			throw new ShaderException("Spot light array too large");
		}
		for (int i = 0; i < spotLights.length; i++) {
			this.setUniform(uniformName, spotLights[i], i);
		}
	}

	/**
	 * Set the value of a uniform to the provided vector.
	 *
	 * @param uniformName The name of the uniform to set.
	 * @param value The value to set.
	 */
	public void setUniform(@NonNull String uniformName,
		@NonNull Vector3f value) {
		GL20.glUniform3f(this.uniforms.get(uniformName), value.x, value.y,
			value.z);
	}

	/**
	 * Set the value of a uniform to the provided vector.
	 *
	 * @param uniformName The name of the uniform to set.
	 * @param value The value to set.
	 */
	public void setUniform(@NonNull String uniformName,
		@NonNull Vector4f value) {
		GL20.glUniform4f(this.uniforms.get(uniformName), value.x, value.y,
			value.z, value.w);
	}

	/**
	 * Stop using this program.
	 */
	public void unbind() {
		GL20.glUseProgram(0);
	}
}
