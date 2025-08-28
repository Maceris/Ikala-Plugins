package com.ikalagaming.graphics.backend.opengl.stages;

import static org.lwjgl.opengl.GL43.*;

import com.ikalagaming.graphics.ShaderUniforms;
import com.ikalagaming.graphics.backend.base.RenderStage;
import com.ikalagaming.graphics.backend.opengl.RenderBuffers;
import com.ikalagaming.graphics.frontend.BufferUtil;
import com.ikalagaming.graphics.frontend.Framebuffer;
import com.ikalagaming.graphics.frontend.Shader;
import com.ikalagaming.graphics.graph.CascadeShadow;
import com.ikalagaming.graphics.graph.MeshData;
import com.ikalagaming.graphics.graph.Model;
import com.ikalagaming.graphics.scene.Scene;

import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/** Handles rendering of cascade shadows. */
@Slf4j
public class ShadowRender implements RenderStage {

    /** The binding for the model matrices buffer SSBO. */
    static final int MODEL_MATRICES_BINDING = 0;

    /** The shader to use for rendering. */
    @NonNull @Setter private Shader shader;

    /** The buffers for indirect drawing of models. */
    private final RenderBuffers renderBuffers;

    /** Cascade shadow information. */
    @Setter @NonNull private List<CascadeShadow> cascadeShadows;

    /** The buffers to render to. */
    @Setter @NonNull private Framebuffer depthMap;

    /**
     * Set up the shadow render stage.
     *
     * @param shader The shader to use for rendering.
     * @param renderBuffers The buffers for indirect drawing of models.
     * @param cascadeShadows Cascade shadow information.
     * @param depthMap The depth map buffers.
     */
    public ShadowRender(
            final @NonNull Shader shader,
            final @NonNull RenderBuffers renderBuffers,
            final @NonNull List<CascadeShadow> cascadeShadows,
            final @NonNull Framebuffer depthMap) {
        this.shader = shader;
        this.renderBuffers = renderBuffers;
        this.cascadeShadows = cascadeShadows;
        this.depthMap = depthMap;
    }

    /**
     * Compute animation transformations for all animated models in the scene.
     *
     * @param scene The scene we are rendering.
     */
    public void render(Scene scene) {
        var uniformsMap = shader.getUniformMap();
        CascadeShadow.updateCascadeShadows(cascadeShadows, scene);

        glBindFramebuffer(GL_FRAMEBUFFER, (int) depthMap.id());
        glViewport(0, 0, CascadeShadow.SHADOW_MAP_WIDTH, CascadeShadow.SHADOW_MAP_HEIGHT);

        shader.bind();

        glBindVertexArray(renderBuffers.getVao());

        for (int i = 0; i < CascadeShadow.SHADOW_MAP_CASCADE_COUNT; ++i) {
            glFramebufferTexture2D(
                    GL_FRAMEBUFFER,
                    GL_DEPTH_ATTACHMENT,
                    GL_TEXTURE_2D,
                    (int) depthMap.textures()[i],
                    0);
            glClear(GL_DEPTH_BUFFER_BIT);

            CascadeShadow shadowCascade = cascadeShadows.get(i);
            uniformsMap.setUniform(
                    ShaderUniforms.Shadow.PROJECTION_VIEW_MATRIX,
                    shadowCascade.getProjViewMatrix());

            // TODO(ches) frustum culling, this is pretty excessive
            renderScene(scene);
        }

        glBindVertexArray(0);
        shader.unbind();
    }

    private void renderScene(Scene scene) {
        for (Model model : scene.getModelMap().values()) {
            final int entityCount = model.getEntitiesList().size();
            if (entityCount == 0) {
                continue;
            }

            final int commandCount = model.isAnimated() ? entityCount : 1;

            BufferUtil.INSTANCE.bindBuffer(model.getModelMatricesBuffer(), MODEL_MATRICES_BINDING);

            for (MeshData mesh : model.getMeshDataList()) {
                if (model.isAnimated()) {
                    glBindVertexBuffer(
                            0,
                            (int) mesh.getAnimationTargetBuffer().id(),
                            0,
                            MeshData.VERTEX_SIZE_IN_BYTES);
                } else {
                    glBindVertexBuffer(
                            0, (int) mesh.getVertexBuffer().id(), 0, MeshData.VERTEX_SIZE_IN_BYTES);
                }
                BufferUtil.INSTANCE.bindBuffer(mesh.getIndexBuffer());
                BufferUtil.INSTANCE.bindBuffer(mesh.getDrawIndirectBuffer());
                glMultiDrawElementsIndirect(GL_TRIANGLES, GL_UNSIGNED_INT, 0, commandCount, 0);
            }

            BufferUtil.INSTANCE.unbindBuffer(
                    model.getModelMatricesBuffer(), MODEL_MATRICES_BINDING);
        }
    }
}
