package com.ikalagaming.graphics.backend.opengl;

import static org.lwjgl.opengl.GL11.GL_FLOAT;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL15.glDeleteBuffers;
import static org.lwjgl.opengl.GL20.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL20.glVertexAttribPointer;
import static org.lwjgl.opengl.GL30.*;

import imgui.ImDrawData;

/**
 * Used to provide ImGUI with the VBOs it needs to render. This should be created using the {@link
 * GuiMesh#create()} method instead of a constructor.
 *
 * @param vaoID The VAO.
 * @param verticesVBO The vertices VBO, set up for ImGui.
 * @param indicesVBO The indices VBO.
 */
public record GuiMesh(int vaoID, int verticesVBO, int indicesVBO) {
    /**
     * Create a new GUI mesh, and set it up with OpenGL. This should be called instead of a
     * constructor.
     *
     * @return The newly created GUI mesh.
     */
    public static GuiMesh create() {
        int vaoID = glGenVertexArrays();
        glBindVertexArray(vaoID);

        int verticesVBO = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, verticesVBO);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 2, GL_FLOAT, false, ImDrawData.SIZEOF_IM_DRAW_VERT, 0);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, ImDrawData.SIZEOF_IM_DRAW_VERT, 8);
        glEnableVertexAttribArray(2);
        glVertexAttribPointer(2, 4, GL_UNSIGNED_BYTE, true, ImDrawData.SIZEOF_IM_DRAW_VERT, 16);

        int indicesVBO = glGenBuffers();

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);

        return new GuiMesh(vaoID, verticesVBO, indicesVBO);
    }

    /** Clean up the resources for this mesh. */
    public void cleanup() {
        glDeleteBuffers(indicesVBO);
        glDeleteBuffers(verticesVBO);
        glDeleteVertexArrays(vaoID);
    }
}
