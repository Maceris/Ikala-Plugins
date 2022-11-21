package com.ikalagaming.graphics;

/**
 * Common definitions of the fields that we need when interacting with shaders.
 * Located centrally so there is an official source of all uniforms shaders
 * need, as well as documentation for what each uniform is used for.
 *
 * @author Ches Burks
 *
 */
public class ShaderUniforms {

	/**
	 * Animation compute shader variables.
	 *
	 * @author Ches Burks
	 *
	 */
	public static class Animation {

		/**
		 * The members of the draw parameters object.
		 *
		 * @author Ches Burks
		 *
		 */
		public static class DrawParameters {
			/**
			 * The offset in the list of bone transformation matrices.
			 */
			public static final String BONES_MATRICES_OFFSET =
				"bonesMatricesOffset";
			/**
			 * The offset in destination data to store output after processing.
			 */
			public static final String DESTINATION_OFFSET = "dstOffset";
			/**
			 * The binding pose offset.
			 */
			public static final String SOURCE_OFFSET = "srcOffset";
			/**
			 * The size of the source, in bytes.
			 */
			public static final String SOURCE_SIZE = "srcSize";
			/**
			 * The offset to the weight within the data.
			 */
			public static final String WEIGHTS_OFFSET = "weightsOffset";

			/**
			 * Private constructor so this class is not instantiated.
			 */
			private DrawParameters() {}
		}

		/**
		 * Draw parameters for the animation.
		 *
		 * @see DrawParameters
		 */
		public static final String DRAW_PARAMETERS = "drawParameters";

		/**
		 * Private constructor so this class is not instantiated.
		 */
		private Animation() {}
	}

	/**
	 * GUI shader variables.
	 *
	 * @author Ches Burks
	 *
	 */
	public static class GUI {
		/**
		 * The scaling of the UI.
		 */
		public static final String SCALE = "scale";

		/**
		 * Private constructor so this class is not instantiated.
		 */
		private GUI() {}
	}

	/**
	 * Light shader variables.
	 *
	 * @author Ches Burks
	 *
	 */
	public static class Light {
		/**
		 * The ambient light.
		 *
		 * @author Ches Burks
		 *
		 */
		public static class AmbientLight {
			/**
			 * The color of the light.
			 */
			public static final String COLOR = "color";
			/**
			 * The intensity, a number between 0 and 1.
			 */
			public static final String INTENSITY = "intensity";

			/**
			 * Private constructor so this class is not instantiated.
			 */
			private AmbientLight() {}
		}

		/**
		 * Used to calculate the attenuation function of light. Controls the way
		 * light falls off with distance. Calculated using the formula:
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
			 * The factor at which light falls of exponentially.
			 */
			public static final String EXPONENT = "exponent";
			/**
			 * The factor at which light falls off linearly.
			 */
			public static final String LINEAR = "linear";

			/**
			 * Private constructor so this class is not instantiated.
			 */
			private Attenuation() {}
		}

		/**
		 * A cascade shadow.
		 *
		 * @author Ches Burks
		 *
		 */
		public static class CascadeShadow {
			/**
			 * The combined projection and view matrix.
			 */
			public static final String PROJECTION_VIEW_MATRIX =
				"projViewMatrix";
			/**
			 * The distance to the split.
			 */
			public static final String SPLIT_DISTANCE = "splitDistance";

			/**
			 * Private constructor so this class is not instantiated.
			 */
			private CascadeShadow() {}
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
		 * Fog to render over the scene.
		 *
		 * @author Ches Burks
		 *
		 */
		public static class Fog {
			/**
			 * The base color of the fog.
			 */
			public static final String COLOR = "color";
			/**
			 * How dense the fog is.
			 */
			public static final String DENSITY = "density";
			/**
			 * Whether the fog is enabled, 1 if enabled.
			 */
			public static final String ENABLED = "enabled";

			/**
			 * Private constructor so this class is not instantiated.
			 */
			private Fog() {}
		}

		/**
		 * A point light.
		 *
		 * @author Ches Burks
		 *
		 */
		public static class PointLight {
			/**
			 * Information about attenuation.
			 *
			 * @see Attenuation
			 */
			public static final String ATTENUATION = "attenuation";
			/**
			 * The color of the light.
			 */
			public static final String COLOR = "color";
			/**
			 * The intensity, a number between 0 and 1.
			 */
			public static final String INTENSITY = "intensity";
			/**
			 * The position of the light.
			 */
			public static final String POSITION = "position";

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
			 * The direction the light is facing.
			 */
			public static final String CONE_DIRECTION = "coneDirection";
			/**
			 * The cutoff angle, in radians.
			 */
			public static final String CUTOFF = "cutoff";
			/**
			 * The point light that stores lighting information.
			 */
			public static final String POINT_LIGHT = "pointLight";

			/**
			 * Private constructor so this class is not instantiated.
			 */
			private SpotLight() {}
		}

		/**
		 * A sampler that measures the proportion of incident light that is
		 * reflected away from a surface at a given point.
		 */
		public static final String ALBEDO_SAMPLER = "albedoSampler";
		/**
		 * The ambient light that affects every fragment the same way.
		 *
		 * @see AmbientLight
		 */
		public static final String AMBIENT_LIGHT = "ambientLight";
		/**
		 * The cascade shadows.
		 */
		public static final String CASCADE_SHADOWS = "cascadeshadows";
		/**
		 * Used to reconstruct the world position using the inverse projection
		 * matrix to help calculate lighting.
		 */
		public static final String DEPTH_SAMPLER = "depthSampler";
		/**
		 * A directional light.
		 *
		 * @see DirectionalLight
		 */
		public static final String DIRECTIONAL_LIGHT = "directionalLight";
		/**
		 * Environmental fog.
		 *
		 * @see Fog
		 */
		public static final String FOG = "fog";
		/**
		 * The inverse of the projection matrix.
		 */
		public static final String INVERSE_PROJECTION_MATRIX =
			"invProjectionMatrix";
		/**
		 * The inverse of the view matrix.
		 */
		public static final String INVERSE_VIEW_MATRIX = "invViewMatrix";
		/**
		 * A sampler for the normal values.
		 */
		public static final String NORMAL_SAMPLER = "normalSampler";
		/**
		 * A list of point lights.
		 *
		 * @see PointLight
		 */
		public static final String POINT_LIGHTS = "pointLights";
		/**
		 * The prefix for shadow maps. There are several of these numbered
		 * uniforms, based on the shadow map cascade count.
		 */
		public static final String SHADOW_MAP_PREFIX = "shadowMap_";
		/**
		 * A sampler for the specular values.
		 */
		public static final String SPECULAR_SAMPLER = "specularSampler";
		/**
		 * A list of spot lights.
		 *
		 * @see SpotLight
		 */
		public static final String SPOT_LIGHTS = "spotLights";

		/**
		 * Private constructor so this class is not instantiated.
		 */
		private Light() {}
	}

	/**
	 * Fragment shader variables.
	 *
	 * @author Ches Burks
	 *
	 */
	public static class Scene {
		/**
		 * Per-entity specific information.
		 *
		 * @author Ches Burks
		 *
		 */
		public static class DrawElement {
			/**
			 * The index of the material.
			 */
			public static final String MATERIAL_INDEX = "materialIndex";
			/**
			 * The index of the model matrix.
			 */
			public static final String MODEL_MATRIX_INDEX = "modelMatrixIndex";

			/**
			 * Private constructor so this class is not instantiated.
			 */
			private DrawElement() {}
		}

		/**
		 * Information about a material.
		 *
		 * @author Ches Burks
		 *
		 */
		public static class Material {
			/**
			 * The color used for the diffuse component.
			 */
			public static final String DIFFUSE = "diffuse";
			/**
			 * The index of the normal map texture.
			 */
			public static final String NORMAL_MAP_INDEX = "normalMapIndex";
			/**
			 * A reflectance index.
			 */
			public static final String REFLECTANCE = "reflectance";
			/**
			 * The color used for the specular component.
			 */
			public static final String SPECULAR = "specular";
			/**
			 * The index of the texture.
			 */
			public static final String TEXTURE_INDEX = "textureIndex";

			/**
			 * Private constructor so this class is not instantiated.
			 */
			private Material() {}
		}

		/**
		 * Stores data per entity needed for indirect drawing.
		 */
		public static final String DRAW_ELEMENTS = "drawElements";
		/**
		 * Material characteristics.
		 *
		 * @see Material
		 */
		public static final String MATERIALS = "materials";
		/**
		 * Model matrices.
		 */
		public static final String MODEL_MATRICES = "modelMatrices";
		/**
		 * Used to calculate the position when projected onto the screen space.
		 */
		public static final String PROJECTION_MATRIX = "projectionMatrix";
		/**
		 * Used to sample a 2d texture.
		 */
		public static final String TEXTURE_SAMPLER = "textureSampler";
		/**
		 * The cameras view matrix.
		 */
		public static final String VIEW_MATRIX = "viewMatrix";

		/**
		 * Private constructor so this class is not instantiated.
		 */
		private Scene() {}
	}

	/**
	 * Shadow shader variables.
	 *
	 * @author Ches Burks
	 *
	 */
	public static class Shadow {
		/**
		 * Per-entity specific information.
		 *
		 * @author Ches Burks
		 *
		 */
		public static class DrawElement {
			/**
			 * The index of the model matrix.
			 */
			public static final String MODEL_MATRIX_INDEX = "modelMatrixIndex";

			/**
			 * Private constructor so this class is not instantiated.
			 */
			private DrawElement() {}
		}

		/**
		 * Stores data per entity needed for indirect drawing.
		 */
		public static final String DRAW_ELEMENTS = "drawElements";
		/**
		 * Model matrices.
		 */
		public static final String MODEL_MATRICES = "modelMatrices";

		/**
		 * The combined projection and view matrix.
		 */
		public static final String PROJECTION_VIEW_MATRIX = "projViewMatrix";

		/**
		 * Private constructor so this class is not instantiated.
		 */
		private Shadow() {}
	}

	/**
	 * Skybox shader variables.
	 *
	 * @author Ches Burks
	 *
	 */
	public static class Skybox {
		/**
		 * The color used for the diffuse component.
		 */
		public static final String DIFFUSE = "diffuse";
		/**
		 * Whether there is a texture, 1 if enabled.
		 */
		public static final String HAS_TEXTURE = "hasTexture";
		/**
		 * The model transformation matrix.
		 */
		public static final String MODEL_MATRIX = "modelMatrix";
		/**
		 * Used to calculate the position when projected onto the screen space.
		 */
		public static final String PROJECTION_MATRIX = "projectionMatrix";
		/**
		 * Used to sample a 2d texture.
		 */
		public static final String TEXTURE_SAMPLER = "textureSampler";
		/**
		 * The cameras view matrix.
		 */
		public static final String VIEW_MATRIX = "viewMatrix";

		/**
		 * Private constructor so this class is not instantiated.
		 */
		private Skybox() {}
	}

	/**
	 * Private constructor so this class is not instantiated.
	 */
	private ShaderUniforms() {}
}