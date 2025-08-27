package com.ikalagaming.graphics.backend.opengl.stages;

import static org.lwjgl.opengl.GL15.glBufferData;
import static org.lwjgl.opengl.GL42.glMemoryBarrier;
import static org.lwjgl.opengl.GL43.*;

import com.ikalagaming.graphics.backend.base.RenderStage;
import com.ikalagaming.graphics.frontend.BufferUtil;
import com.ikalagaming.graphics.frontend.Shader;
import com.ikalagaming.graphics.graph.MeshData;
import com.ikalagaming.graphics.graph.Model;
import com.ikalagaming.graphics.scene.Entity;
import com.ikalagaming.graphics.scene.Scene;

import lombok.NonNull;
import lombok.Setter;
import org.lwjgl.system.MemoryUtil;

import java.nio.IntBuffer;

/** Handles computations for animated models. */
@Setter
public class AnimationRender implements RenderStage {

    private static void updateAnimationOffsets(Model model, int entityCount) {
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, (int) model.getEntityAnimationOffsetsBuffer().id());
        IntBuffer animationOffsets = MemoryUtil.memAllocInt(entityCount);
        for (int i = 0; i < entityCount; ++i) {
            Entity entity = model.getEntitiesList().get(i);

            Model.Animation animation = entity.getAnimationState().getCurrentAnimation();
            if (animation == null) {
                animationOffsets.put(-1);
                continue;
            }
            int baseOffset = animation.offset();
            int frameIndex = entity.getAnimationState().getCurrentFrameIndex();
            int frameSize = animation.boneCount() * 4 * 4 /* mat4 */ * 4 /* 4 bytes per float */;

            animationOffsets.put(baseOffset + frameIndex * frameSize);
        }
        animationOffsets.flip();

        glBufferData(GL_SHADER_STORAGE_BUFFER, animationOffsets, GL_STATIC_DRAW);
        MemoryUtil.memFree(animationOffsets);
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);
    }

    private static void updateInstancedStorage(Model model, int entityCount) {
        int entityCap = model.getMaxAnimatedBufferCapacity();
        if (entityCount > entityCap) {
            if (entityCap < 4) {
                entityCap = 4;
            }

            while (entityCount >= entityCap) {
                entityCap *= 2;
            }
            model.setMaxAnimatedBufferCapacity(entityCap);

            glBindBuffer(
                    GL_SHADER_STORAGE_BUFFER, (int) model.getEntityAnimationOffsetsBuffer().id());
            glBufferData(GL_SHADER_STORAGE_BUFFER, entityCap, GL_STATIC_DRAW);
            glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);

            for (MeshData meshData : model.getMeshDataList()) {
                glBindBuffer(
                        GL_SHADER_STORAGE_BUFFER, (int) meshData.getAnimationTargetBuffer().id());
                glBufferData(
                        GL_SHADER_STORAGE_BUFFER,
                        (long) entityCap * meshData.getVertexCount() * 14 * 4,
                        GL_DYNAMIC_COPY);
            }
            glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);
        }
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
                glDispatchCompute(vertexCount, entityCount, 1);
            }
        }

        glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
        shader.unbind();
    }
}
