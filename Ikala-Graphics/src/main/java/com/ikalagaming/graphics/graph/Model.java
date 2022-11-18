package com.ikalagaming.graphics.graph;

import com.ikalagaming.graphics.scene.Entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * A model for rendering.
 */
@Getter
public class Model {
	/**
	 * A single key frame of the animation, which is defines transformations to
	 * apply to the bones.
	 *
	 */
	@Getter
	public static class AnimatedFrame {
		/**
		 * The bone transformations.
		 *
		 * @return The bone transformation matrices.
		 */
		private Matrix4f[] bonesMatrices;
		/**
		 * The position in the list of bone transformation matrices.
		 *
		 * @param offset The offset in the list of bone transformation matrices.
		 * @return The offset in the list of bone transformation matrices.
		 */
		@Setter
		private int offset;

		/**
		 * Create a new frame given the transformation matrices.
		 *
		 * @param bonesMatrices The bone transformation matrices.
		 */
		public AnimatedFrame(Matrix4f[] bonesMatrices) {
			this.bonesMatrices = bonesMatrices;
		}

		/**
		 * Clear the matrix data.
		 */
		public void clearData() {
			this.bonesMatrices = null;
		}
	}

	/**
	 * A named animation.
	 */
	@Getter
	@AllArgsConstructor
	public static class Animation {
		/**
		 * The name of the animation.
		 *
		 * @param name The name of the animation.
		 * @return The name of the animation.
		 */
		private final String name;
		/**
		 * Duration of the animation in ticks.
		 *
		 * @param duration The duration.
		 * @return The duration.
		 */
		private final double duration;
		/**
		 * A list of frame data.
		 *
		 * @param The frames that make up the animation.
		 * @return The frames for the animation.
		 */
		private final List<AnimatedFrame> frames;
	}

	/**
	 * The ID for the model.
	 *
	 * @return The model ID.
	 */
	private final String id;
	/**
	 * A list of animations that can be applied to the model.
	 *
	 * @return The list of animations.
	 */
	private List<Animation> animationList;
	/**
	 * A list of entities that use this model.
	 *
	 * @return The list of entities.
	 */
	private List<Entity> entitiesList;
	/**
	 * A list of mesh data for this model.
	 *
	 * @return The list of mesh data.
	 */
	private List<MeshData> meshDataList;
	/**
	 * A list of mesh draw data for this model.
	 *
	 * @return The list of mesh draw data.
	 */
	private List<RenderBuffers.MeshDrawData> meshDrawDataList;

	/**
	 * Create a new model.
	 *
	 * @param id The model ID.
	 * @param meshDataList The mesh data for this model .
	 * @param animationList The animations for this model.
	 */
	public Model(String id, List<MeshData> meshDataList,
		List<Animation> animationList) {
		this.entitiesList = new ArrayList<>();
		this.id = id;
		this.meshDataList = meshDataList;
		this.animationList = animationList;
		this.meshDrawDataList = new ArrayList<>();
	}

	/**
	 * Whether the model is animated.
	 *
	 * @return If there are values in the animation list.
	 */
	public boolean isAnimated() {
		return this.animationList != null && !this.animationList.isEmpty();
	}
}