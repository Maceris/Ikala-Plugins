package com.ikalagaming.graphics.backend.vulkan.stages;

import com.ikalagaming.graphics.backend.base.RenderStage;
import com.ikalagaming.graphics.frontend.BufferUtil;
import com.ikalagaming.graphics.frontend.Shader;
import com.ikalagaming.graphics.graph.MeshData;
import com.ikalagaming.graphics.graph.Model;
import com.ikalagaming.graphics.scene.Scene;

import lombok.NonNull;
import lombok.Setter;

/** Handles computations for animated models. */
@Setter
public class AnimationRender implements RenderStage {

    private static void updateAnimationOffsets(Model model, int entityCount) {
        // TODO(ches) complete this
    }

    private static void updateInstancedStorage(Model model, int entityCount) {
        // TODO(ches) complete this
    }

    /** The shader to use for rendering. */
    @NonNull private Shader shader;

    /**
     * Set up the animation render stage.
     *
     * @param shader The shader to use for rendering.
     */
    public AnimationRender(final @NonNull Shader shader) {
        this.shader = shader;
    }

    /**
     * Compute animation transformations for all animated models in the scene.
     *
     * @param scene The scene we are rendering.
     */
    public void render(Scene scene) {
        shader.bind();

        for (Model model : scene.getModelMap().values()) {
            int entityCount = model.getEntitiesList().size();
            if (!model.isAnimated() || entityCount == 0) {
                continue;
            }

            updateInstancedStorage(model, entityCount);

            updateAnimationOffsets(model, entityCount);

            BufferUtil.INSTANCE.bindBuffer(model.getAnimationBuffer(), 0);
            BufferUtil.INSTANCE.bindBuffer(model.getEntityAnimationOffsetsBuffer(), 1);

            for (MeshData meshData : model.getMeshDataList()) {
                BufferUtil.INSTANCE.bindBuffer(meshData.getVertexBuffer(), 2);
                BufferUtil.INSTANCE.bindBuffer(meshData.getBoneWeightBuffer(), 3);
                BufferUtil.INSTANCE.bindBuffer(meshData.getAnimationTargetBuffer(), 4);

                final int vertexCount = meshData.getVertexCount();
                // TODO(ches) render
            }
        }

        shader.unbind();
    }
}
