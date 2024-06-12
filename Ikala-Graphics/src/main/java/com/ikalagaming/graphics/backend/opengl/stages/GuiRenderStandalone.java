package com.ikalagaming.graphics.backend.opengl.stages;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL11.glViewport;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL15.*;

import com.ikalagaming.graphics.backend.opengl.GuiMesh;
import com.ikalagaming.graphics.frontend.RenderStage;
import com.ikalagaming.graphics.frontend.Shader;
import com.ikalagaming.graphics.scene.Scene;

import imgui.*;
import org.joml.Vector2f;

/** A Gui renderer that clears the screen texture each time it draws. */
public class GuiRenderStandalone implements RenderStage {

    /** The scale of the GUI, kept here to prevent reallocation. */
    private final Vector2f scale;

    /** The shader to use for rendering. */
    private final Shader shader;

    /** The screen texture to clear between renders. */
    private final int screenTextureID;

    /** The GUI Mesh to use. */
    private final GuiMesh guiMesh;

    /**
     * Set up the GUI render stage.
     *
     * @param screenTextureID The screen texture to clear between renders.
     * @param guiMesh The mesh information ImGui uses.
     */
    public GuiRenderStandalone(
            final Shader shader, final int screenTextureID, final GuiMesh guiMesh) {
        scale = new Vector2f();
        this.shader = shader;
        this.screenTextureID = screenTextureID;
        this.guiMesh = guiMesh;
    }

    @Override
    public void render(Scene scene) {
        ImGuiIO io = ImGui.getIO();

        final int width = (int) io.getDisplaySizeX();
        final int height = (int) io.getDisplaySizeY();

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, screenTextureID);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        glViewport(0, 0, width, height);

        GuiRender.renderGui(shader, width, height, scale, guiMesh);
    }
}
