package com.ikalagaming.graphics.backend.opengl;

import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL30.*;

import com.ikalagaming.graphics.frontend.Buffer;
import com.ikalagaming.graphics.frontend.BufferUtil;

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
 */
public record GuiMesh(
        int vaoID,
        int vertices,
        @NonNull Buffer commands,
        @NonNull Buffer points,
        @NonNull Buffer pointDetails) {

    /**
     * Create a new GUI mesh, and set it up with OpenGL. This should be called instead of a
     * constructor.
     *
     * @return The newly created GUI mesh.
     */
    public static GuiMesh create() {
        int vaoID = glGenVertexArrays();
        int vertices = glGenBuffers();
        Buffer commands = BufferUtil.INSTANCE.createBuffer(Buffer.Type.SHADER_STORAGE);
        Buffer points = BufferUtil.INSTANCE.createBuffer(Buffer.Type.SHADER_STORAGE);
        Buffer pointDetails = BufferUtil.INSTANCE.createBuffer(Buffer.Type.SHADER_STORAGE);
        return new GuiMesh(vaoID, vertices, commands, points, pointDetails);
    }

    /** Clean up the resources for this mesh. */
    public void cleanup() {
        glDeleteBuffers(vertices);
        BufferUtil.INSTANCE.deleteBuffer(commands);
        BufferUtil.INSTANCE.deleteBuffer(points);
        BufferUtil.INSTANCE.deleteBuffer(pointDetails);
        glDeleteVertexArrays(vaoID);
    }
}
