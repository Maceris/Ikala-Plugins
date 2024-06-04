package com.ikalagaming.graphics.backend.opengl;

import static org.lwjgl.opengl.GL30.glBindBufferBase;
import static org.lwjgl.opengl.GL42.glMemoryBarrier;
import static org.lwjgl.opengl.GL43.GL_SHADER_STORAGE_BARRIER_BIT;
import static org.lwjgl.opengl.GL43.GL_SHADER_STORAGE_BUFFER;
import static org.lwjgl.opengl.GL43.glDispatchCompute;

import com.ikalagaming.graphics.ShaderUniforms;
import com.ikalagaming.graphics.frontend.Shader;
import com.ikalagaming.graphics.graph.Model;
import com.ikalagaming.graphics.scene.Entity;
import com.ikalagaming.graphics.scene.Scene;

import lombok.NonNull;

/** Handles computations for animated models. */
public class AnimationRender {

    /**
     * Compute animation transformations for all animated models in the scene.
     *
     * @param scene The scene we are rendering.
     * @param renderBuffer The buffers for indirect drawing of models.
     */
    public void render(
            @NonNull Scene scene, @NonNull Shader shader, @NonNull RenderBuffers renderBuffer) {
        shader.bind();
        var uniformsMap = shader.getUniformMap();
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, renderBuffer.getBindingPosesBuffer());
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 1, renderBuffer.getBonesIndicesWeightsBuffer());
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 2, renderBuffer.getBonesMatricesBuffer());
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 3, renderBuffer.getDestinationAnimationBuffer());

        int dstOffset = 0;
        for (Model model : scene.getModelMap().values()) {
            if (!model.isAnimated()) {
                continue;
            }
            for (RenderBuffers.MeshDrawData meshDrawData : model.getMeshDrawDataList()) {
                RenderBuffers.AnimMeshDrawData animMeshDrawData = meshDrawData.animMeshDrawData();
                Entity entity = animMeshDrawData.entity();
                Model.AnimatedFrame frame = entity.getAnimationData().getCurrentFrame();
                int groupSize = (int) Math.ceil((float) meshDrawData.sizeInBytes() / (14 * 4));
                uniformsMap.setUniform(
                        ShaderUniforms.Animation.DRAW_PARAMETERS
                                + "."
                                + ShaderUniforms.Animation.DrawParameters.SOURCE_OFFSET,
                        animMeshDrawData.bindingPoseOffset());
                uniformsMap.setUniform(
                        ShaderUniforms.Animation.DRAW_PARAMETERS
                                + "."
                                + ShaderUniforms.Animation.DrawParameters.SOURCE_SIZE,
                        meshDrawData.sizeInBytes() / 4);
                uniformsMap.setUniform(
                        ShaderUniforms.Animation.DRAW_PARAMETERS
                                + "."
                                + ShaderUniforms.Animation.DrawParameters.WEIGHTS_OFFSET,
                        animMeshDrawData.weightsOffset());
                uniformsMap.setUniform(
                        ShaderUniforms.Animation.DRAW_PARAMETERS
                                + "."
                                + ShaderUniforms.Animation.DrawParameters.BONES_MATRICES_OFFSET,
                        frame.getOffset());
                uniformsMap.setUniform(
                        ShaderUniforms.Animation.DRAW_PARAMETERS
                                + "."
                                + ShaderUniforms.Animation.DrawParameters.DESTINATION_OFFSET,
                        dstOffset);
                glDispatchCompute(groupSize, 1, 1);
                dstOffset += meshDrawData.sizeInBytes() / 4;
            }
        }

        glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
        shader.unbind();
    }
}
