package com.ikalagaming.graphics;

/**
 * Common definitions of the fields that we need when interacting with shaders.
 * 
 * This may be replaced at some point by a file based specification.
 *
 * @author Ches Burks
 *
 */
public class ShaderConstants {
	/**
	 * To be used to dynamically load in shaders.
	 * 
	 * @author Ches Burks
	 *
	 */
	public static class Program {
		/**
		 * A list of the names of directional light uniforms to create for the
		 * shader.
		 */
		public static final String DIRECTIONAL_LIGHT_UNIFORMS =
			"directionalLightUniforms";
		/**
		 * The location of the fragment shader file, relative to the resources
		 * directory.
		 */
		public static final String FRAGMENT_SHADER = "fragmentShader";

		/**
		 * A list of the names of material uniforms to create for the shader.
		 */
		public static final String MATERIAL_UNIFORMS = "materialUniforms";

		/**
		 * Should contain mappings from the names of the uniforms to the sizes
		 * of each uniform. <br>
		 * For example: <br>
		 * <code>
		 * pointLightListUniforms: <br>
		 * &nbsp;&nbsp;pointLights: 5 <br>
		 * &nbsp;&nbsp;specialPointLights: 2 <br>
		 * </code>
		 */
		public static final String POINT_LIGHT_LIST_UNIFORMS =
			"pointLightListUniforms";

		/**
		 * A list of the names of point light uniforms to create for the shader.
		 */
		public static final String POINT_LIGHT_UNIFORMS = "pointLightUniforms";

		/**
		 * Should contain mappings from the names of the uniforms to the sizes
		 * of each uniform. <br>
		 * For example: <br>
		 * <code>
		 * spotLightListUniforms: <br>
		 * &nbsp;&nbsp;pointLights: 5 <br>
		 * &nbsp;&nbsp;specialPointLights: 2 <br>
		 * </code>
		 */
		public static final String SPOT_LIGHT_LIST_UNIFORMS =
			"spotLightListUniforms";

		/**
		 * A list of the names of spot light uniforms to create for the shader.
		 */
		public static final String SPOT_LIGHT_UNIFORMS = "spotLightUniforms";

		/**
		 * A list of the names of uniforms to create for the shader.
		 */
		public static final String UNIFORMS = "uniforms";

		/**
		 * The location of the vertex shader file, relative to the resources
		 * directory.
		 */
		public static final String VERTEX_SHADER = "vertexShader";
	}

	/**
	 * Uniforms, or parameters that users of a shader program can pass to that
	 * program.
	 *
	 * @author Ches Burks
	 *
	 */
	public static class Uniform {
		/**
		 * Fragment shader variables.
		 *
		 * @author Ches Burks
		 *
		 */
		public static class Fragment {
			/**
			 * Used to calculate the attenuation function of light. Controls the
			 * way light falls off with distance. Calculated using the formula:
			 *
			 * {@code 1.0/(constant + linear * dist + exponent * dist^2)}.
			 *
			 * @author Ches Burks
			 *
			 */
			public static class Attenuation {
				/**
				 * A constant factor.
				 */
				public static final String CONSTANT = "constant";
				/**
				 * The factor at which light falls off linearly.
				 */
				public static final String LINEAR = "linear";
				/**
				 * The factor at which light falls of exponentially.
				 */
				public static final String EXPONENT = "exponent";

				/**
				 * Private constructor so this class is not instantiated.
				 */
				private Attenuation() {}
			}

			/**
			 * A directional light.
			 *
			 * @author Ches Burks
			 *
			 */
			public static class DirectionalLight {
				/**
				 * The color of the light.
				 */
				public static final String COLOR = "color";
				/**
				 * The direction that the light is coming from.
				 */
				public static final String DIRECTION = "direction";
				/**
				 * The intensity, a number between 0 and 1.
				 */
				public static final String INTENSITY = "intensity";

				/**
				 * Private constructor so this class is not instantiated.
				 */
				private DirectionalLight() {}
			}

			/**
			 * Information about a material.
			 *
			 * @author Ches Burks
			 *
			 */
			public static class Material {
				/**
				 * The color used for the ambient component.
				 */
				public static final String AMBIENT = "ambient";
				/**
				 * The color used for the diffuse component.
				 */
				public static final String DIFFUSE = "diffuse";
				/**
				 * The color used for the specular component.
				 */
				public static final String SPECULAR = "specular";
				/**
				 * A flag that controls if there is an associated texture or
				 * not.
				 */
				public static final String HAS_TEXTURE = "hasTexture";
				/**
				 * A reflectance index.
				 */
				public static final String REFLECTANCE = "reflectance";

				/**
				 * Private constructor so this class is not instantiated.
				 */
				private Material() {}
			}

			/**
			 * A point light.
			 *
			 * @author Ches Burks
			 *
			 */
			public static class PointLight {
				/**
				 * The color of the light.
				 */
				public static final String COLOR = "color";
				/**
				 * The position of the light.
				 */
				public static final String POSITION = "position";
				/**
				 * The intensity, a number between 0 and 1.
				 */
				public static final String INTENSITY = "intensity";
				/**
				 * Information about attenuation.
				 *
				 * @see Attenuation
				 */
				public static final String ATTENUATION = "attenuation";

				/**
				 * Private constructor so this class is not instantiated.
				 */
				private PointLight() {}
			}

			/**
			 * A Spot light.
			 *
			 * @author Ches Burks
			 *
			 */
			public static class SpotLight {
				/**
				 * The point light that stores lighting information.
				 */
				public static final String POINT_LIGHT = "pointLight";
				/**
				 * The direction the light is facing.
				 */
				public static final String CONE_DIRECTION = "coneDirection";
				/**
				 * The cutoff angle, in radians.
				 */
				public static final String CUTOFF = "cutoff";

				/**
				 * Private constructor so this class is not instantiated.
				 */
				private SpotLight() {}
			}

			/**
			 * Used to sample a 2d texture.
			 */
			public static final String TEXTURE_SAMPLER = "textureSampler";
			/**
			 * The ambient light color that affects every fragment the same way.
			 */
			public static final String AMBIENT_LIGHT = "ambientLight";
			/**
			 * The exponent used in manipulating the strength of the specular
			 * factor based on camera direction.
			 */
			public static final String SPECULAR_POWER = "specularPower";
			/**
			 * Material characteristics.
			 *
			 * @see Material
			 */
			public static final String MATERIAL = "material";
			/**
			 * A list of point lights.
			 *
			 * @see PointLight
			 */
			public static final String POINT_LIGHTS = "pointLights";
			/**
			 * A list of spot lights.
			 *
			 * @see SpotLight
			 */
			public static final String SPOT_LIGHTS = "spotLights";

			/**
			 * A directional light.
			 *
			 * @see DirectionalLight
			 */
			public static final String DIRECTIONAL_LIGHT = "directionalLight";
			/**
			 * The camera position in view space coordinates.
			 */
			public static final String CAMERA_POSITION = "cameraPos";

			/**
			 * The maximum number of point lights that are supported, must be a
			 * compile time constant in the shader.
			 */
			public static final int MAX_POINT_LIGHTS = 5;

			/**
			 * The maximum number of spot lights that are supported, must be a
			 * compile time constant in the shader.
			 */
			public static final int MAX_SPOT_LIGHTS = 5;

			/**
			 * Private constructor so this class is not instantiated.
			 */
			private Fragment() {}
		}

		/**
		 * Vertex shader variables.
		 *
		 * @author Ches Burks
		 *
		 */
		public static class Vertex {
			/**
			 * A combination of the view matrix and world matrix.
			 */
			public static final String MODEL_VIEW_MATRIX = "modelViewMatrix";
			/**
			 * Used to calculate the position when projected onto the screen
			 * space.
			 */
			public static final String PROJECTION_MATRIX = "projectionMatrix";

			/**
			 * Private constructor so this class is not instantiated.
			 */
			private Vertex() {}
		}

		/**
		 * Vertex shader variables.
		 *
		 * @author Ches Burks
		 *
		 */
		public static class HUD {
			/**
			 * Used to calculate the position when projecting a model onto the
			 * screen space.
			 */
			public static final String PROJECTION_MATRIX = "projModelMatrix";

			/**
			 * Used to calculate the position when projecting a model onto the
			 * screen space.
			 */
			public static final String COLOR = "color";

			/**
			 * Whether or not the shader is using a texture.
			 */
			public static final String HAS_TEXTURE = "hasTexture";

			/**
			 * Private constructor so this class is not instantiated.
			 */
			private HUD() {}
		}

		/**
		 * Private constructor so this class is not instantiated.
		 */
		private Uniform() {}
	}

	/**
	 * Private constructor so this class is not instantiated.
	 */
	private ShaderConstants() {}
}
