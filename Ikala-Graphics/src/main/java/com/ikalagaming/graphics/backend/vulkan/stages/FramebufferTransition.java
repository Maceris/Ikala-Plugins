package com.ikalagaming.graphics.backend.vulkan.stages;

import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.backend.base.RenderStage;
import com.ikalagaming.graphics.backend.base.State;
import com.ikalagaming.graphics.frontend.Framebuffer;
import com.ikalagaming.graphics.scene.Scene;

import imgui.ImGui;
import imgui.ImGuiIO;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.Setter;

/** Binds and clears (color+depth) a framebuffer. */
@AllArgsConstructor
public class FramebufferTransition implements RenderStage {

    /** The framebuffer to bind. */
    @Setter private Framebuffer framebuffer;

    // TODO(ches) figure out what these blend factors should be, or throw this class away if it's
    // pointless
    /**
     * The source weighting factor for the additive blending equation to use for this framebuffer.
     */
    private final int blendSFactor;

    /**
     * The destination weighting factor for the additive blending equation to use for this
     * framebuffer.
     */
    private final int blendDFactor;

    @Override
    public void render(Scene scene, @NonNull Window window, State state) {
        ImGuiIO io = ImGui.getIO();

        final int width = (int) io.getDisplaySizeX();
        final int height = (int) io.getDisplaySizeY();
        // TODO(ches) do this
    }
}
