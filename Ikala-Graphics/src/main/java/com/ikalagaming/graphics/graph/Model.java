/*
 * NOTICE: This file is a modified version of contents from
 * https://github.com/lwjglgamedev/lwjglbook, which was licensed under Apache
 * v2.0. Changes have been made related to formatting, functionality, and
 * naming.
 */
package com.ikalagaming.graphics.graph;

import com.ikalagaming.graphics.scene.Entity;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/** A model for rendering. */
@Getter
public class Model {
    /** A single key frame of the animation, which defines transformations to apply to the bones. */
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
        @Setter private int offset;

        /**
         * Create a new frame given the transformation matrices.
         *
         * @param bonesMatrices The bone transformation matrices.
         */
        public AnimatedFrame(@NonNull Matrix4f[] bonesMatrices) {
            this.bonesMatrices = bonesMatrices;
        }
    }

    /**
     * A named animation.
     *
     * @param name The name of the animation.
     * @param duration The duration.
     * @param frames The frames that make up the animation.
     */
    public record Animation(String name, double duration, List<AnimatedFrame> frames) {}

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
    public Model(
            @NonNull String id,
            @NonNull List<MeshData> meshDataList,
            List<Animation> animationList) {
        entitiesList = new ArrayList<>();
        this.id = id;
        this.meshDataList = meshDataList;
        this.animationList = animationList;
        meshDrawDataList = new ArrayList<>();
    }

    /**
     * Whether the model is animated.
     *
     * @return If there are values in the animation list.
     */
    public boolean isAnimated() {
        return animationList != null && !animationList.isEmpty();
    }
}
