package com.ikalagaming.graphics.backend.opengl.stages;

import static com.ikalagaming.graphics.ShaderUniforms.Scene.*;
import static org.lwjgl.opengl.ARBBindlessTexture.glMakeImageHandleNonResidentARB;
import static org.lwjgl.opengl.ARBBindlessTexture.glMakeImageHandleResidentARB;
import static org.lwjgl.opengl.GL30.glBindBufferBase;
import static org.lwjgl.opengl.GL43.*;

import com.ikalagaming.graphics.ShaderUniforms;
import com.ikalagaming.graphics.backend.base.RenderStage;
import com.ikalagaming.graphics.backend.opengl.RenderBuffers;
import com.ikalagaming.graphics.frontend.*;
import com.ikalagaming.graphics.graph.MeshData;
import com.ikalagaming.graphics.graph.Model;
import com.ikalagaming.graphics.scene.Scene;

import lombok.NonNull;
import lombok.Setter;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Handles rendering of scene geometry to the g-buffer. */
public class SceneRender implements RenderStage {

    /** The shader to use for rendering. */
    @NonNull @Setter private Shader shader;

    /** The buffers for indirect drawing of models. */
    private final RenderBuffers renderBuffers;

    /** The g-buffer for rendering geometry to. */
    @Setter @NonNull private Framebuffer gBuffer;

    /**
     * Set up the shadow render stage.
     *
     * @param shader The shader to use for rendering.
     * @param renderBuffers The buffers for indirect drawing of models.
     * @param gBuffer The depth map buffers.
     */
    public SceneRender(
            final @NonNull Shader shader,
            final @NonNull RenderBuffers renderBuffers,
            final @NonNull Framebuffer gBuffer) {
        this.shader = shader;
        this.renderBuffers = renderBuffers;
        this.gBuffer = gBuffer;
    }

    /**
     * Compute animation transformations for all animated models in the scene.
     *
     * @param scene The scene we are rendering.
     */
    public void render(Scene scene) {
        commonSceneRender(scene, shader, renderBuffers, gBuffer);
    }

    /**
     * Common rendering code for the scene, shared between stages.
     *
     * @param scene The scene we are rendering.
     * @param shader The shader to use for rendering.
     * @param renderBuffers The buffers for indirect drawing of models.
     * @param gBuffer The depth map buffers.
     */
    static void commonSceneRender(
            Scene scene, Shader shader, RenderBuffers renderBuffers, Framebuffer gBuffer) {
        var uniformsMap = shader.getUniformMap();
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, (int) gBuffer.id());
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        glViewport(0, 0, gBuffer.width(), gBuffer.height());
        glDisable(GL_BLEND);
        shader.bind();

        updateMaterialBuffers(scene);

        uniformsMap.setUniform(
                ShaderUniforms.Scene.PROJECTION_MATRIX,
                scene.getProjection().getProjectionMatrix());
        uniformsMap.setUniform(ShaderUniforms.Scene.VIEW_MATRIX, scene.getCamera().getViewMatrix());

        List<Texture> activeTextures = new ArrayList<>();

        for (Model model : scene.getModelMap().values()) {
            final int entityCount = model.getEntitiesList().size();
            if (entityCount == 0) {
                continue;
            }

            final int commandCount = model.isAnimated() ? entityCount : 1;

            glBindBufferBase(
                    GL_SHADER_STORAGE_BUFFER,
                    ShadowRender.MODEL_MATRICES_BINDING,
                    (int) model.getModelMatricesBuffer().id());
            glBindVertexArray(renderBuffers.getVao());

            for (MeshData mesh : model.getMeshDataList()) {
                final int materialIndex = mesh.getMaterialIndex();
                uniformsMap.setUniformUnsigned(MATERIAL_INDEX, materialIndex);
                Material material = scene.getMaterialCache().getMaterial(materialIndex);
                if (material.getTexture() != null) {
                    glMakeImageHandleResidentARB(material.getTexture().handle(), GL_READ_ONLY);
                    activeTextures.add(material.getTexture());
                    uniformsMap.setUniform(BASE_COLOR_SAMPLER, material.getTexture());
                }
                if (material.getNormalMap() != null) {
                    glMakeImageHandleResidentARB(material.getNormalMap().handle(), GL_READ_ONLY);
                    activeTextures.add(material.getNormalMap());
                    uniformsMap.setUniform(NORMAL_SAMPLER, material.getNormalMap());
                }
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
            for (Texture texture : activeTextures) {
                glMakeImageHandleNonResidentARB(texture.handle());
            }
            activeTextures.clear();
        }

        glBindVertexArray(0);
        glEnable(GL_BLEND);
        shader.unbind();
    }

    private static void updateMaterialBuffers(Scene scene) {
        if (!scene.getMaterialCache().isDirty()) {
            return;
        }

        final int MATERIAL_SIZE = 16 /* floats/ints */ * 4 /* bytes per float/int */;
        final List<Material> materials = scene.getMaterialCache().getMaterialsList();
        if (materials.isEmpty()) {
            return;
        }

        ByteBuffer materialData = MemoryUtil.memAlloc(materials.size() * MATERIAL_SIZE);

        for (Material material : materials) {
            final int normalMapID =
                    Optional.ofNullable(material.getNormalMap())
                            .map(Texture::id)
                            .map(Math::toIntExact)
                            .orElse(0);
            final int textureID =
                    Optional.ofNullable(material.getTexture())
                            .map(Texture::id)
                            .map(Math::toIntExact)
                            .orElse(0);

            materialData.putFloat(material.getBaseColor().x);
            materialData.putFloat(material.getBaseColor().y);
            materialData.putFloat(material.getBaseColor().z);
            materialData.putFloat(material.getBaseColor().w);

            materialData.putFloat(material.getAnisotropic());
            materialData.putFloat(material.getClearcoat());
            materialData.putFloat(material.getClearcoatGloss());
            materialData.putFloat(material.getMetallic());

            materialData.putFloat(material.getRoughness());
            materialData.putFloat(material.getSheen());
            materialData.putFloat(material.getSheenTint());
            materialData.putFloat(material.getSpecular());

            materialData.putFloat(material.getSpecularTint());
            materialData.putFloat(material.getSubsurface());
            materialData.putInt(normalMapID);
            materialData.putInt(textureID);
        }

        materialData.flip();

        Buffer materialBuffer = scene.getMaterialCache().getMaterialBuffer();
        BufferUtil.INSTANCE.bindBuffer(materialBuffer);
        glBufferData(GL_SHADER_STORAGE_BUFFER, materialData, GL_STATIC_DRAW);
        BufferUtil.INSTANCE.unbindBuffer(materialBuffer);

        MemoryUtil.memFree(materialData);
        scene.getMaterialCache().setDirty(false);
    }
}
