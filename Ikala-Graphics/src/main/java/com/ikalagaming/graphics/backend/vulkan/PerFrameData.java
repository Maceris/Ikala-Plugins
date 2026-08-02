package com.ikalagaming.graphics.backend.vulkan;

/** Data buffers for a frame, only the data that the CPU cares about. */
public class PerFrameData {
    public SharedBuffer animation;
    public SharedBuffer filter;
    public SharedBuffer gui;
    @Deprecated public SharedBuffer guiLegacy;
    public SharedBuffer light;
    public SharedBuffer scene;
    public SharedBuffer shadow;
    public SharedBuffer skybox;
}
