package com.ikalagaming.graphics.backend.opengl.stages;

import static org.lwjgl.opengl.GL30.glBindBufferBase;
import static org.lwjgl.opengl.GL42.glMemoryBarrier;
import static org.lwjgl.opengl.GL43.*;

import com.ikalagaming.graphics.backend.base.RenderStage;
import com.ikalagaming.graphics.backend.opengl.RenderBuffers;
import com.ikalagaming.graphics.frontend.Shader;
import com.ikalagaming.graphics.graph.Model;
import com.ikalagaming.graphics.scene.Scene;

import lombok.NonNull;
import lombok.Setter;

/** Handles computations for animated models. */
public class AnimationRender implements RenderStage {

    /** The shader to use for rendering. */
    @NonNull @Setter private Shader shader;

    /** The buffers for indirect drawing of models. */
    private final RenderBuffers renderBuffers;

    /**
     * Set up the animation render stage.
     *
     * @param shader The shader to use for rendering.
     * @param renderBuffers The buffers for indirect drawing of models.
     */
    public AnimationRender(
            final @NonNull Shader shader, final @NonNull RenderBuffers renderBuffers) {
        this.shader = shader;
        this.renderBuffers = renderBuffers;
    }

    /**
     * Compute animation transformations for all animated models in the scene.
     *
     * @param scene The scene we are rendering.
     */
    public void render(Scene scene) {
        shader.bind();
        var uniformsMap = shader.getUniformMap();
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, renderBuffers.getBindingPosesBuffer());
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 1, renderBuffers.getBonesIndicesWeightsBuffer());
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 2, renderBuffers.getBonesMatricesBuffer());
        glBindBufferBase(
                GL_SHADER_STORAGE_BUFFER, 3, renderBuffers.getDestinationAnimationBuffer());

        int dstOffset = 0;
        for (Model model : scene.getModelMap().values()) {
            if (!model.isAnimated()) {
                continue;
            }

            // TODO(ches) dispatch compute for animations
            //            for (RenderBuffers.MeshDrawData meshDrawData :
            // model.getMeshDrawDataList()) {
            //                RenderBuffers.AnimMeshDrawData animMeshDrawData =
            // meshDrawData.animMeshDrawData();
            //                int groupSize = (int) Math.ceil((float) meshDrawData.sizeInBytes() /
            // (14 * 4));
            //                uniformsMap.setUniform(
            //                        ShaderUniforms.Animation.DRAW_PARAMETERS
            //                                + "."
            //                                +
            // ShaderUniforms.Animation.DrawParameters.SOURCE_OFFSET,
            //                        animMeshDrawData.bindingPoseOffset());
            //                uniformsMap.setUniform(
            //                        ShaderUniforms.Animation.DRAW_PARAMETERS
            //                                + "."
            //                                + ShaderUniforms.Animation.DrawParameters.SOURCE_SIZE,
            //                        meshDrawData.sizeInBytes() / 4);
            //                uniformsMap.setUniform(
            //                        ShaderUniforms.Animation.DRAW_PARAMETERS
            //                                + "."
            //                                +
            // ShaderUniforms.Animation.DrawParameters.WEIGHTS_OFFSET,
            //                        animMeshDrawData.weightsOffset());
            //                uniformsMap.setUniform(
            //                        ShaderUniforms.Animation.DRAW_PARAMETERS
            //                                + "."
            //                                +
            // ShaderUniforms.Animation.DrawParameters.BONES_MATRICES_OFFSET,
            //                        frame.getOffset());
            //                uniformsMap.setUniform(
            //                        ShaderUniforms.Animation.DRAW_PARAMETERS
            //                                + "."
            //                                +
            // ShaderUniforms.Animation.DrawParameters.DESTINATION_OFFSET,
            //                        dstOffset);
            //                glDispatchCompute(groupSize, 1, 1);
            //                dstOffset += meshDrawData.sizeInBytes() / 4;
            //            }
        }

        glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
        shader.unbind();
    }
}
