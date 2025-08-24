package com.ikalagaming.graphics.backend.opengl.stages;

import static org.lwjgl.opengl.GL15.glBufferData;
import static org.lwjgl.opengl.GL30.glBindBufferBase;
import static org.lwjgl.opengl.GL42.glMemoryBarrier;
import static org.lwjgl.opengl.GL43.*;

import com.ikalagaming.graphics.backend.base.RenderStage;
import com.ikalagaming.graphics.backend.opengl.BufferUtil;
import com.ikalagaming.graphics.backend.opengl.RenderBuffers;
import com.ikalagaming.graphics.frontend.Buffer;
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

    /** The shader to use for rendering. */
    @NonNull
    private Shader shader;

    /**
     * Set up the animation render stage.
     *
     * @param shader The shader to use for rendering.
     * @param renderBuffers The buffers for indirect drawing of models.
     */
    public AnimationRender(
            final @NonNull Shader shader, final @NonNull RenderBuffers renderBuffers) {
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
            if (!model.isAnimated()) {
                continue;
            }

            int entityCount = model.getEntitiesList().size();
            if (entityCount > Model.MAX_ENTITIES) {
                //TODO(ches) log error.
                entityCount = Model.MAX_ENTITIES;
            }
            int entityCap = model.getMaxAnimatedBufferCapacity();
            if (entityCount > entityCap) {
                if (entityCap < 4) {
                    entityCap = 4;
                }

                while (entityCount >= entityCap) {
                    entityCap *= 2;
                }
                model.setMaxAnimatedBufferCapacity(entityCap);

                glBindBuffer(GL_SHADER_STORAGE_BUFFER, (int) model.getEntityAnimationOffsetsBuffer().id());
                glBufferData(GL_SHADER_STORAGE_BUFFER, entityCap, GL_STATIC_DRAW);
                glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);

            }

            glBindBuffer(GL_SHADER_STORAGE_BUFFER, (int) model.getEntityAnimationOffsetsBuffer().id());
            IntBuffer animationOffsets = IntBuffer.allocate(entityCount);
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
            glBufferData(GL_SHADER_STORAGE_BUFFER, animationOffsets, GL_STATIC_DRAW);
            MemoryUtil.memFree(animationOffsets);
            glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);

            BufferUtil.bind(model.getAnimationBuffer(), 0);
            BufferUtil.bind(model.getEntityAnimationOffsetsBuffer(), 1);

            for (MeshData meshData :  model.getMeshDataList()) {
                BufferUtil.bind(meshData.getVertexBuffer(), 2);
                BufferUtil.bind(meshData.getBoneWeightBuffer(), 3);
                BufferUtil.bind(meshData.getAnimationTargetBuffer(), 4);

                final int vertexCount = meshData.getVertexCount();
                glDispatchCompute(vertexCount, entityCount, 1);
            }
        }

        glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
        shader.unbind();
    }
}
