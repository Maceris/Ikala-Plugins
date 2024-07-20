package com.ikalagaming.graphics.backend.opengl.stages;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL11.glViewport;

import com.ikalagaming.graphics.backend.base.RenderStage;
import com.ikalagaming.graphics.backend.opengl.GuiMesh;
import com.ikalagaming.graphics.frontend.Shader;
import com.ikalagaming.graphics.scene.Scene;

import imgui.*;
import org.joml.Vector2f;

/** A GUI without any scene in the background. */
public class GuiRenderStandalone implements RenderStage {

    /** The scale of the GUI, kept here to prevent reallocation. */
    private final Vector2f scale;

    /** The shader to use for rendering. */
    private final Shader shader;

    /** The GUI Mesh to use. */
    private final GuiMesh guiMesh;

    /**
     * Set up the GUI render stage.
     *
     * @param guiMesh The mesh information ImGui uses.
     */
    public GuiRenderStandalone(final Shader shader, final GuiMesh guiMesh) {
        scale = new Vector2f();
        this.shader = shader;
        this.guiMesh = guiMesh;
    }

    @Override
    public void render(Scene scene) {
        ImGuiIO io = ImGui.getIO();

        final int width = (int) io.getDisplaySizeX();
        final int height = (int) io.getDisplaySizeY();

        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        glViewport(0, 0, width, height);

        GuiRender.renderGui(shader, width, height, scale, guiMesh);
    }
}
