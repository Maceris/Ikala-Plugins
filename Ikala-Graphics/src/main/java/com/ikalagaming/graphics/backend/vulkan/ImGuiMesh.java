package com.ikalagaming.graphics.backend.vulkan;

/**
 * Used to provide ImGUI with the VBOs it needs to render. This should be created using the {@link
 * ImGuiMesh#create()} method instead of a constructor.
 *
 * @param vaoID The VAO.
 * @param verticesVBO The vertices VBO, set up for ImGui.
 * @param indicesVBO The indices VBO.
 */
@Deprecated
public record ImGuiMesh(int vaoID, int verticesVBO, int indicesVBO) {
    /**
     * Create a new GUI mesh, and set it up with OpenGL. This should be called instead of a
     * constructor.
     *
     * @return The newly created GUI mesh.
     */
    public static ImGuiMesh create() {
        int vaoID = 0;
        int verticesVBO = 0;
        int indicesVBO = 0;
        // TODO(ches) create

        return new ImGuiMesh(vaoID, verticesVBO, indicesVBO);
    }

    /** Clean up the resources for this mesh. */
    public void cleanup() {
        // TODO(ches) clean up
    }
}
