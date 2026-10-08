package com.ikalagaming.graphics.backend.opengl.stages;

import static org.lwjgl.opengl.GL15.glBufferData;
import static org.lwjgl.opengl.GL42.glMemoryBarrier;
import static org.lwjgl.opengl.GL43.*;

import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.backend.base.RenderStage;
import com.ikalagaming.graphics.backend.base.State;
import com.ikalagaming.graphics.backend.opengl.BufferOpenGL;
import com.ikalagaming.graphics.backend.opengl.BufferUtilOpenGL;
import com.ikalagaming.graphics.frontend.Shader;
import com.ikalagaming.graphics.graph.MeshData;
import com.ikalagaming.graphics.graph.Model;
import com.ikalagaming.graphics.scene.Scene;

import lombok.NonNull;
import lombok.Setter;
import org.lwjgl.system.MemoryUtil;

import java.nio.IntBuffer;

/** Handles computations for animated models. */
@Setter
public class AnimationRender implements RenderStage {

    private static void updateAnimationOffsets(Model model, int entityCount) {
        glBindBuffer(
                GL_SHADER_STORAGE_BUFFER,
                (int) ((BufferOpenGL) model.getEntityAnimationOffsetsBuffer()).id());
        IntBuffer animationOffsets = MemoryUtil.memAllocInt(entityCount);
        for (int i = 0; i < entityCount; ++i) {
            animationOffsets.put(Model.getAnimationMatrixOffset(model.getEntitiesList().get(i)));
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
                    GL_SHADER_STORAGE_BUFFER,
                    (int) ((BufferOpenGL) model.getEntityAnimationOffsetsBuffer()).id());
            glBufferData(GL_SHADER_STORAGE_BUFFER, entityCap, GL_STATIC_DRAW);
            glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);

            for (MeshData meshData : model.getMeshDataList()) {
                glBindBuffer(
                        GL_SHADER_STORAGE_BUFFER,
                        (int) ((BufferOpenGL) meshData.getAnimationTargetBuffer()).id());
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
    @Override
    public void render(Scene scene, @NonNull Window window, State state, int renderConfig) {
        shader.bind();

        for (Model model : scene.getModelMap().values()) {
            int entityCount = model.getEntitiesList().size();
            if (!model.isAnimated() || entityCount == 0) {
                continue;
            }

            updateInstancedStorage(model, entityCount);

            updateAnimationOffsets(model, entityCount);

            BufferUtilOpenGL.bindBuffer((BufferOpenGL) model.getAnimationBuffer(), 0);
            BufferUtilOpenGL.bindBuffer((BufferOpenGL) model.getEntityAnimationOffsetsBuffer(), 1);

            for (MeshData meshData : model.getMeshDataList()) {
                // These are uniform buffers, so bindBuffer would put them on uniform binding points
                // rather than the storage buffer bindings the shader reads
                glBindBufferBase(
                        GL_SHADER_STORAGE_BUFFER,
                        2,
                        (int) ((BufferOpenGL) meshData.getVertexBuffer()).id());
                glBindBufferBase(
                        GL_SHADER_STORAGE_BUFFER,
                        3,
                        (int) ((BufferOpenGL) meshData.getBoneWeightBuffer()).id());
                BufferUtilOpenGL.bindBuffer((BufferOpenGL) meshData.getAnimationTargetBuffer(), 4);

                final int vertexCount = meshData.getVertexCount();
                glDispatchCompute(vertexCount, entityCount, 1);
            }
        }

        // The scene and shadow stages read the results as vertex attributes
        glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT | GL_VERTEX_ATTRIB_ARRAY_BARRIER_BIT);
        shader.unbind();
    }
}
