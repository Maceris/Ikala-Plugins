package com.ikalagaming.graphics.backend.opengl;

import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL30.*;

import lombok.NonNull;

/**
 * Used to provide our GUI with the data it needs to render. This should be created using the {@link
 * GuiMesh#create()} method instead of a constructor.
 *
 * @param vaoID The VAO.
 * @param vertices Quad mesh vertices.
 * @param commands GUI Render commands.
 * @param points SDF points.
 * @param pointDetails SDF point extra details.
 * @param textureHandles Bindless handles for the textures used by the GUI.
 */
public record GuiMesh(
        int vaoID,
        int vertices,
        @NonNull BufferOpenGL commands,
        @NonNull BufferOpenGL points,
        @NonNull BufferOpenGL pointDetails,
        @NonNull BufferOpenGL textureHandles) {

    /**
     * Create a new GUI mesh, and set it up with OpenGL. This should be called instead of a
     * constructor.
     *
     * @return The newly created GUI mesh.
     */
    public static GuiMesh create() {
        int vaoID = glGenVertexArrays();
        glBindVertexArray(vaoID);

        int vertices = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vertices);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 0, 0);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);

        BufferOpenGL commands = BufferUtilOpenGL.createBuffer(BufferOpenGL.Type.SHADER_STORAGE);
        BufferOpenGL points = BufferUtilOpenGL.createBuffer(BufferOpenGL.Type.SHADER_STORAGE);
        BufferOpenGL pointDetails = BufferUtilOpenGL.createBuffer(BufferOpenGL.Type.SHADER_STORAGE);
        BufferOpenGL textureHandles =
                BufferUtilOpenGL.createBuffer(BufferOpenGL.Type.SHADER_STORAGE);
        return new GuiMesh(vaoID, vertices, commands, points, pointDetails, textureHandles);
    }

    /** Clean up the resources for this mesh. */
    public void cleanup() {
        glDeleteBuffers(vertices);
        BufferUtilOpenGL.deleteBuffer(commands);
        BufferUtilOpenGL.deleteBuffer(points);
        BufferUtilOpenGL.deleteBuffer(pointDetails);
        BufferUtilOpenGL.deleteBuffer(textureHandles);
        glDeleteVertexArrays(vaoID);
    }
}
