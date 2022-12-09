/*
 * NOTICE: This file is a modified version of contents from
 * https://github.com/lwjglgamedev/lwjglbook, which was licensed under Apache
 * v2.0. Changes have been made related to formatting, functionality, and
 * naming.
 */
package com.ikalagaming.graphics.scene;

import com.ikalagaming.graphics.graph.Model;

import lombok.Getter;
import org.joml.Matrix4f;

/**
 * Tracks an animation and what frame we are on within it.
 */
@Getter
public class AnimationData {
	/**
	 * The default bone matrices for an animation, which are all zero matrices.
	 * There is one for each (potential) bone in a model.
	 */
	public static final Matrix4f[] DEFAULT_BONES_MATRICES =
		new Matrix4f[ModelLoader.MAX_BONES];

	static {
		Matrix4f zeroMatrix = new Matrix4f().zero();
		for (int i = 0; i < AnimationData.DEFAULT_BONES_MATRICES.length; ++i) {
			AnimationData.DEFAULT_BONES_MATRICES[i] = zeroMatrix;
		}
	}
	/**
	 * The current ongoing animation.
	 *
	 * @return The current animation details.
	 */
	private Model.Animation currentAnimation;
	/**
	 * The index of the current frame in the animation.
	 *
	 * @return The current frame index.
	 */
	private int currentFrameIndex;

	/**
	 * Create new animation data for the given animation.
	 *
	 * @param currentAnimation The animation we are tracking.
	 */
	public AnimationData(Model.Animation currentAnimation) {
		this.currentFrameIndex = 0;
		this.currentAnimation = currentAnimation;
	}

	/**
	 * Fetch the data associated with the current animation frame.
	 *
	 * @return The data for the current frame.
	 */
	public Model.AnimatedFrame getCurrentFrame() {
		return this.currentAnimation.frames().get(this.currentFrameIndex);
	}

	/**
	 * Advance to the next frame in the animation, looping back to the start
	 * when we reach the end of the animation.
	 */
	public void nextFrame() {
		int nextFrame = this.currentFrameIndex + 1;
		if (nextFrame > this.currentAnimation.frames().size() - 1) {
			this.currentFrameIndex = 0;
		}
		else {
			this.currentFrameIndex = nextFrame;
		}
	}

	/**
	 * Update the animation we are tracking and start at the beginning.
	 *
	 * @param currentAnimation The new animation we are tracking.
	 */
	public void setCurrentAnimation(Model.Animation currentAnimation) {
		this.currentFrameIndex = 0;
		this.currentAnimation = currentAnimation;
	}
}