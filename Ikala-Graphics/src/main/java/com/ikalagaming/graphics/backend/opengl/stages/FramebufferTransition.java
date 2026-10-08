package com.ikalagaming.graphics.backend.opengl.stages;

import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.glBlendFunc;
import static org.lwjgl.opengl.GL11.glClear;
import static org.lwjgl.opengl.GL11.glViewport;
import static org.lwjgl.opengl.GL14.glBlendEquation;
import static org.lwjgl.opengl.GL30.*;

import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.backend.base.RenderStage;
import com.ikalagaming.graphics.backend.base.State;
import com.ikalagaming.graphics.frontend.Framebuffer;
import com.ikalagaming.graphics.frontend.gui.IkGui;
import com.ikalagaming.graphics.frontend.gui.data.IkIO;
import com.ikalagaming.graphics.scene.Scene;

import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.Setter;

/** Binds and clears (color+depth) a framebuffer. */
@AllArgsConstructor
public class FramebufferTransition implements RenderStage {

    /** The framebuffer to bind. */
    @Setter private Framebuffer framebuffer;

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
    public void render(Scene scene, @NonNull Window window, State state, int renderConfig) {
        glBindFramebuffer(GL_FRAMEBUFFER, (int) framebuffer.id());

        final IkIO io = IkGui.getIO();

        final int width = (int) io.displaySize.x;
        final int height = (int) io.displaySize.y;

        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        glViewport(0, 0, width, height);

        glBlendEquation(GL_FUNC_ADD);
        glBlendFunc(blendSFactor, blendDFactor);
    }
}
