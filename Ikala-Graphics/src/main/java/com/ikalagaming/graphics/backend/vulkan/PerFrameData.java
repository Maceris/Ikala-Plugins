package com.ikalagaming.graphics.backend.vulkan;

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
    public SharedBuffer lightMaterials;
    public SharedBuffer sceneUniforms;
    public SharedBuffer sceneModelMatrices;
    public SharedBuffer sceneMaterials;
    public SharedBuffer sceneMaterialOverrides;
    public SharedBuffer shadowUniforms;
    public SharedBuffer shadowModelMatrices;
    public SharedBuffer skyboxUniforms;
}
