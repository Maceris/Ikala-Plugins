/*
 * NOTICE: This file is a modified version of contents from
 * https://github.com/lwjglgamedev/lwjglbook, which was licensed under Apache
 * v2.0. Changes have been made related to formatting, functionality, and
 * naming.
 */
package com.ikalagaming.graphics.scene;

import com.ikalagaming.graphics.graph.Model;

import lombok.Getter;

/** Tracks an animation and what frame we are on within it. */
@Getter
public class AnimationData {
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
        currentFrameIndex = 0;
        this.currentAnimation = currentAnimation;
    }

    /**
     * Fetch the data associated with the current animation frame.
     *
     * @return The data for the current frame.
     */
    public Model.AnimatedFrame getCurrentFrame() {
        return currentAnimation.frames().get(currentFrameIndex);
    }

    /**
     * Advance to the next frame in the animation, looping back to the start when we reach the end
     * of the animation.
     */
    public void nextFrame() {
        int nextFrame = currentFrameIndex + 1;
        if (nextFrame > currentAnimation.frames().size() - 1) {
            currentFrameIndex = 0;
        } else {
            currentFrameIndex = nextFrame;
        }
    }

    /**
     * Update the animation we are tracking and start at the beginning.
     *
     * @param currentAnimation The new animation we are tracking.
     */
    public void setCurrentAnimation(Model.Animation currentAnimation) {
        currentFrameIndex = 0;
        this.currentAnimation = currentAnimation;
    }
}
