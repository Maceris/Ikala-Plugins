package com.ikalagaming.graphics.backend.vulkan;

import com.ikalagaming.graphics.graph.CascadeShadowSplit;

/** Data buffers for a frame, only the data that the CPU cares about. */
public class PerFrameData {
    public SharedBuffer animationData;
    public SharedBuffer animationOffsets;
    public SharedBuffer animationModelData;
    public SharedBuffer animationBoneWeight;
    public SharedBuffer animationTarget;
    public SharedBuffer guiUniforms;
    public SharedBuffer guiCommands;
    public SharedBuffer guiPoints;
    public SharedBuffer guiPointDetails;
    public SharedBuffer lightUniforms;
    public SharedBuffer lightPointLights;
    public SharedBuffer lightSpotLights;
    public SharedBuffer sceneUniforms;
    public SharedBuffer sceneModelMatrices;
    public SharedBuffer sceneMaterialOverrides;
    public SharedBuffer shadowUniforms;
    public SharedBuffer shadowModelMatrices;
    public SharedBuffer skyboxUniforms;

    /** Materials used by the light and scene render phases. */
    public SharedBuffer materials;

    /** The bindless texture array. Used by the scene, light, skybox, and GUI stages. */
    public SharedBuffer textures;

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
