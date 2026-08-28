package com.ikalagaming.graphics.backend.vulkan;

import static org.lwjgl.vulkan.VK13.VK_NULL_HANDLE;

import com.ikalagaming.graphics.frontend.Buffer;

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
        int vaoID = 0;

        int vertices = 0;
        // TODO(ches) create

        // TODO(ches) create SSBOs for commands, points, point details
        Buffer commands = new Buffer(VK_NULL_HANDLE, Buffer.Type.SHADER_STORAGE);
        Buffer points = new Buffer(VK_NULL_HANDLE, Buffer.Type.SHADER_STORAGE);
        Buffer pointDetails = new Buffer(VK_NULL_HANDLE, Buffer.Type.SHADER_STORAGE);
        return new GuiMesh(vaoID, vertices, commands, points, pointDetails);
    }

    /** Clean up the resources for this mesh. */
    public void cleanup() {
        // TODO(ches) clean up
    }
}
