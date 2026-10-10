package com.ikalagaming.graphics.graph;

import com.ikalagaming.graphics.scene.AnimationState;
import com.ikalagaming.graphics.vulkan.SharedBuffer;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** A model for rendering. */
@Getter
public class Model {

    /** The size of a 4x4 float matrix in bytes. */
    private static final int MATRIX_SIZE_IN_BYTES = 4 * 4 * Float.BYTES;

    /**
     * Find where an instance's current animation frame starts in the model's animation buffer, as
     * an index of 4x4 matrices, which is how the animation shaders read it.
     *
     * @param state The instance's animation state, or null if it isn't animating.
     * @return The index of the first bone matrix of the current frame, or -1 if it isn't animating.
     */
    public static int getAnimationMatrixOffset(AnimationState state) {
        if (state == null || state.getCurrentAnimation() == null) {
            return -1;
        }
        Model.Animation animation = state.getCurrentAnimation();
        // The animation offset is in bytes, frames are boneCount matrices each
        return animation.offset() / MATRIX_SIZE_IN_BYTES
                + state.getCurrentFrameIndex() * animation.boneCount();
    }

    /**
     * A named animation.
     *
     * @param name The name of the animation.
     * @param duration The duration.
     * @param boneCount The number of bones in a frame.
     * @param frameCount The number of frames.
     * @param frameData The frame data. Stored as a list of frames, each of which is an array of
     *     boneCount mat4's.
     * @param offset Offset into the animation buffer.
     */
    public record Animation(
            @NonNull String name,
            double duration,
            int boneCount,
            int frameCount,
            byte[] frameData,
            int offset) {
        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof Animation anim)) {
                return false;
            }
            return Objects.equals(name, anim.name)
                    && duration == anim.duration
                    && boneCount == anim.boneCount
                    && frameCount == anim.frameCount;
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, duration, boneCount, frameCount);
        }

        @Override
        public String toString() {
            return "Animation{"
                    + "frameCount="
                    + frameCount
                    + ", boneCount="
                    + boneCount
                    + ", duration="
                    + duration
                    + ", name='"
                    + name
                    + '\''
                    + '}';
        }
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
    private final List<Animation> animationList;

    /**
     * A list of mesh data for this model.
     *
     * @return The list of mesh data.
     */
    private final List<MeshData> meshDataList;

    /** Stores all the animations. */
    @Setter private SharedBuffer animationBuffer;

    /** Used to store animation states, for animated models. */
    @Setter private SharedBuffer entityAnimationOffsetsBuffer;

    /**
     * The highest buffer size (measured in poses), for animation state and destination buffers.
     * This will be doubled if we need more, but not all of it needs to be used.
     */
    @Setter private int maxAnimatedBufferCapacity;

    /**
     * Create a new model.
     *
     * @param id The model ID.
     */
    public Model(@NonNull String id) {
        this.id = id;
        this.meshDataList = new ArrayList<>();
        this.animationList = new ArrayList<>();
        this.entityAnimationOffsetsBuffer = null;
        this.maxAnimatedBufferCapacity = 0;
    }

    /**
     * Whether the model is animated.
     *
     * @return If there are values in the animation list.
     */
    public boolean isAnimated() {
        return !animationList.isEmpty();
    }
}
