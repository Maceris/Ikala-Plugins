package com.ikalagaming.graphics.backend.vulkan.stages;

import static com.ikalagaming.graphics.ShaderUniforms.Scene.*;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_STORAGE_BUFFER_BIT;

import com.ikalagaming.graphics.ShaderUniforms;
import com.ikalagaming.graphics.backend.base.RenderStage;
import com.ikalagaming.graphics.frontend.*;
import com.ikalagaming.graphics.graph.MaterialCache;
import com.ikalagaming.graphics.graph.MeshData;
import com.ikalagaming.graphics.graph.Model;
import com.ikalagaming.graphics.scene.Entity;
import com.ikalagaming.graphics.scene.Scene;

import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

/** Handles rendering of scene geometry to the g-buffer. */
@Slf4j
public class SceneRender implements RenderStage {

    /** The binding for the materials SSBO. */
    static final int MATERIALS_BINDING = 1;

    /** The binding for the material override SSBO. */
    static final int MATERIAL_OVERRIDES_BINDING = 2;

    /** The shader to use for rendering. */
    @NonNull @Setter private Shader shader;

    /** The g-buffer for rendering geometry to. */
    @Setter @NonNull private Framebuffer gBuffer;

    /**
     * Set up the shadow render stage.
     *
     * @param shader The shader to use for rendering.
     * @param gBuffer The depth map buffers.
     */
    public SceneRender(final @NonNull Shader shader, final @NonNull Framebuffer gBuffer) {
        this.shader = shader;
        this.gBuffer = gBuffer;
    }

    /**
     * Compute animation transformations for all animated models in the scene.
     *
     * @param scene The scene we are rendering.
     */
    public void render(Scene scene) {
        commonSceneRender(scene, shader, gBuffer);
    }

    /**
     * Common rendering code for the scene, shared between stages.
     *
     * @param scene The scene we are rendering.
     * @param shader The shader to use for rendering.
     * @param gBuffer The depth map buffers.
     */
    static void commonSceneRender(Scene scene, Shader shader, Framebuffer gBuffer) {
        var uniformsMap = shader.getUniformMap();
        // TODO(ches) clear the framebuffer
        shader.bind();

        updateMaterialBuffers(scene);
        updateMaterialOverrides(scene);

        uniformsMap.setUniform(
                ShaderUniforms.Scene.PROJECTION_MATRIX,
                scene.getProjection().getProjectionMatrix());
        uniformsMap.setUniform(ShaderUniforms.Scene.VIEW_MATRIX, scene.getCamera().getViewMatrix());

        for (Model model : scene.getModelMap().values()) {
            final int entityCount = model.getEntitiesList().size();
            if (entityCount == 0) {
                continue;
            }

            final int commandCount = model.isAnimated() ? entityCount : 1;

            // TODO(ches) figure out right buffer usage or redesign BufferUtil API
            BufferUtil.INSTANCE.bindBuffer(
                    model.getModelMatricesBuffer(), VK_BUFFER_USAGE_STORAGE_BUFFER_BIT);
            BufferUtil.INSTANCE.bindBuffer(
                    scene.getMaterialCache().getMaterialBuffer(), MATERIALS_BINDING);
            BufferUtil.INSTANCE.bindBuffer(
                    model.getMaterialOverridesBuffer(), MATERIAL_OVERRIDES_BINDING);

            int meshIndex = 0;
            for (MeshData mesh : model.getMeshDataList()) {
                int indexOrFallback = scene.getMaterialCache().getMaterialIndex(mesh.getMaterial());
                Material assignedOrDefaultMaterial =
                        scene.getMaterialCache().getMaterial(indexOrFallback);

                uniformsMap.setUniformUnsigned(MATERIAL_INDEX, indexOrFallback);
                uniformsMap.setUniformUnsigned(MESH_INDEX, meshIndex);

                if (assignedOrDefaultMaterial.getTexture() != null) {
                    // TODO(ches) make sure image is resident
                }
                if (assignedOrDefaultMaterial.getNormalMap() != null) {
                    // TODO(ches) make sure image is resident
                }
                uniformsMap.setUniform(BASE_COLOR_SAMPLER, assignedOrDefaultMaterial.getTexture());
                uniformsMap.setUniform(NORMAL_SAMPLER, assignedOrDefaultMaterial.getNormalMap());

                if (model.isAnimated()) {
                    // TODO(ches) bind mesh.getAnimationTargetBuffer().id()

                } else {
                    // TODO(ches) ... don't bind mesh.getAnimationTargetBuffer().id()
                }
                BufferUtil.INSTANCE.bindBuffer(mesh.getIndexBuffer());
                BufferUtil.INSTANCE.bindBuffer(mesh.getDrawIndirectBuffer());
                // TODO(ches) draw indirect
                meshIndex += 1;
            }

            // TODO(ches) unbind model matrices (?)
        }

        shader.unbind();
    }

    private static void updateMaterialOverrides(Scene scene) {
        MaterialCache cache = scene.getMaterialCache();

        for (Model model : scene.getModelMap().values()) {
            if (model.getEntitiesList().isEmpty() || !model.isMaterialOverridesDirty()) {
                continue;
            }

            final int MESH_COUNT = model.getMeshDataList().size();
            final int ENTITY_COUNT = model.getEntitiesList().size();

            IntBuffer buffer = MemoryUtil.memAllocInt(ENTITY_COUNT * MESH_COUNT);

            for (Entity entity : model.getEntitiesList()) {
                for (Material material : entity.getMaterialOverrides()) {
                    int override = cache.getMaterialIndex(material);
                    buffer.put(override);
                }
            }

            buffer.flip();

            BufferUtil.INSTANCE.bindBuffer(model.getMaterialOverridesBuffer());
            // TODO(ches) figure out right buffer usage or redesign BufferUtil API
            BufferUtil.INSTANCE.bufferData(
                    model.getMaterialOverridesBuffer(), buffer, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT);
            BufferUtil.INSTANCE.unbindBuffer(model.getMaterialOverridesBuffer());

            MemoryUtil.memFree(buffer);
            model.setMaterialOverridesDirty(false);
        }
    }

    private static void updateMaterialBuffers(Scene scene) {
        if (!scene.getMaterialCache().isDirty()) {
            return;
        }

        final int MATERIAL_SIZE = 16 /* floats/ints */ * 4 /* bytes per float/int */;
        final int materialCount = scene.getMaterialCache().getMaterialCount();
        if (materialCount <= 0) {
            return;
        }

        ByteBuffer materialData = MemoryUtil.memAlloc(materialCount * MATERIAL_SIZE);

        for (int i = 0; i < materialCount; ++i) {
            Material material = scene.getMaterialCache().getMaterial(i);

            int normalMapID = 0;
            int textureID = 0;

            if (material.getNormalMap() != null) {
                normalMapID = (int) material.getNormalMap().id();
            }
            if (material.getTexture() != null) {
                textureID = (int) material.getTexture().id();
            }

            Vector4f baseColor = material.getBaseColor();
            materialData.putFloat(baseColor.x);
            materialData.putFloat(baseColor.y);
            materialData.putFloat(baseColor.z);
            materialData.putFloat(baseColor.w);

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
        // TODO(ches) upload data
        BufferUtil.INSTANCE.unbindBuffer(materialBuffer);

        MemoryUtil.memFree(materialData);
        scene.getMaterialCache().setDirty(false);
    }
}
