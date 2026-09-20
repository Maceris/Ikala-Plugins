package com.ikalagaming.graphics.backend.vulkan;

import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_TRANSFER_DST_BIT;
import static org.lwjgl.vulkan.VK12.VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT;

import com.ikalagaming.graphics.backend.base.State;

import lombok.NonNull;

/**
 * Used to provide our GUI with the data it needs to render. This should be created using the {@link
 * GuiMesh#create(State)} method instead of a constructor.
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
        @NonNull SharedBuffer commands,
        @NonNull SharedBuffer points,
        @NonNull SharedBuffer pointDetails) {

    /**
     * Create a new GUI mesh, and set it up with OpenGL. This should be called instead of a
     * constructor.
     *
     * @return The newly created GUI mesh.
     */
    public static GuiMesh create(@NonNull State state) {
        int vaoID = 0;

        int vertices = 0;
        // TODO(ches) create

        // TODO(ches) create SSBOs for commands, points, point details

        final int BUFFER_USAGE =
                VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT;
        SharedBuffer commands = SharedBuffer.allocate(0, (VulkanState) state, BUFFER_USAGE);
        SharedBuffer points = SharedBuffer.allocate(0, (VulkanState) state, BUFFER_USAGE);
        SharedBuffer pointDetails = SharedBuffer.allocate(0, (VulkanState) state, BUFFER_USAGE);
        return new GuiMesh(vaoID, vertices, commands, points, pointDetails);
    }

    /** Clean up the resources for this mesh. */
    public void cleanup() {
        // TODO(ches) clean up
    }
}
