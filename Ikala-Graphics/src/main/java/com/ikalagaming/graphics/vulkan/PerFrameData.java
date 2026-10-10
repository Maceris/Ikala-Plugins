package com.ikalagaming.graphics.vulkan;

import com.ikalagaming.graphics.graph.CascadeShadowSplit;
import com.ikalagaming.graphics.graph.Model;

import java.util.Map;

/** Data buffers for a frame, only the data that the CPU cares about. */
public class PerFrameData {
    /**
     * The index of the first bone matrix in each animated model's animation buffer, per pose.
     * Entities on the same animation frame share a pose, so it's only skinned once. Every animated
     * model's poses are packed back to back, see {@link ModelDrawInfo#firstPose()}.
     */
    public SharedBuffer animationOffsets;

    public SharedBuffer guiUniforms;
    public SharedBuffer guiCommands;
    public SharedBuffer guiPoints;
    public SharedBuffer guiPointDetails;

    /** Vertices for every GUI draw list this frame, packed back to back. */
    public SharedBuffer guiVertices;

    /** Maps draw data texture IDs to bindless texture slots. */
    public SharedBuffer guiTextureIndices;

    /** Staging buffer for font glyphs that are copied into the font atlas this frame. */
    public SharedBuffer guiFontStaging;

    public SharedBuffer lightUniforms;
    public SharedBuffer lightPointLights;
    public SharedBuffer lightSpotLights;
    public SharedBuffer sceneUniforms;

    /**
     * The model matrix of every instance drawn this frame, in the order of {@link #instanceList},
     * relative to the camera. Written on the GPU by the instance transform stage.
     */
    public SharedBuffer sceneModelMatrices;

    /**
     * The instance table slot of every instance drawn this frame, model by model, written by the
     * instance draw update stage. Draws index it the way they index {@link #sceneModelMatrices}.
     */
    public SharedBuffer instanceList;

    /** How many entries {@link #instanceList} has this frame. */
    public int instanceCount;

    public SharedBuffer sceneMaterialOverrides;

    /** Indirect draw commands for every mesh in the scene this frame, packed back to back. */
    public SharedBuffer sceneDrawCommands;

    /**
     * Where each model's data starts in the packed scene buffers this frame. Filled out by the
     * instance draw update stage, and only contains models that have instances to draw.
     */
    public Map<Model, ModelDrawInfo> modelDrawInfo;

    /**
     * Where a model's data starts in the packed per-frame scene buffers.
     *
     * @param firstMatrix The index of the first model matrix in {@link #sceneModelMatrices}.
     * @param firstOverride The index of the first material override in {@link
     *     #sceneMaterialOverrides}.
     * @param firstCommand The index of the first draw command in {@link #sceneDrawCommands}. Each
     *     mesh has {@code commandCount} commands, one mesh after another.
     * @param commandCount How many draw commands each mesh has.
     * @param firstPose For animated models, where the model's poses start in {@link
     *     #animationOffsets}. Each pose gets its own copy of the mesh vertices in the animation
     *     target buffers.
     * @param poseCount For animated models, the number of distinct poses the entities are in.
     */
    public record ModelDrawInfo(
            int firstMatrix,
            int firstOverride,
            int firstCommand,
            int commandCount,
            int firstPose,
            int poseCount) {}

    public SharedBuffer skyboxUniforms;

    /** Line vertices for debug shapes, see {@link DebugGeometry}. */
    public SharedBuffer debugVertices;

    /** Uniforms for the filter stage, which give filters access to the g-buffer. */
    public SharedBuffer filterUniforms;

    /** Materials used by the light and scene render phases. */
    public SharedBuffer materials;

    public CascadeShadowSplit[] cascadeShadowSplits;
    public TextureInfoVulkan[] cascadeShadows;

    /** Base color, normal, tangent, material, and depth. */
    public GBuffer gBuffer;

    /**
     * A texture that is rendered to before the filter stage, if there is a filter stage. Unused if
     * there's no filter, as we can just bind the final texture directly.
     */
    public TextureInfoVulkan preFilterTexture;

    /**
     * The texture we render to just before moving over to the swapchain. The filter stage renders
     * here if present, otherwise the scene can be rendered here directly. The GUI is rendered on
     * top of here as well at the end.
     */
    public TextureInfoVulkan finalTexture;
}
