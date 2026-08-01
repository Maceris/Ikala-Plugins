package com.ikalagaming.graphics.backend.vulkan.stages;

import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.ShaderUniforms;
import com.ikalagaming.graphics.backend.base.RenderStage;
import com.ikalagaming.graphics.backend.vulkan.GuiMesh;
import com.ikalagaming.graphics.backend.vulkan.ImGuiMesh;
import com.ikalagaming.graphics.frontend.Shader;
import com.ikalagaming.graphics.frontend.Texture;
import com.ikalagaming.graphics.frontend.gui.IkGui;
import com.ikalagaming.graphics.frontend.gui.WindowManager;
import com.ikalagaming.graphics.frontend.gui.data.DrawData;
import com.ikalagaming.graphics.frontend.gui.data.FontAtlas;
import com.ikalagaming.graphics.scene.Scene;

import imgui.*;
import lombok.NonNull;
import lombok.Setter;
import org.joml.Vector2f;

public class GuiRender implements RenderStage {

    /** The binding for the commands SSBO. */
    static final int COMMANDS_BINDING = 0;

    /** The binding for the points SSBO. */
    static final int POINTS_BINDING = 1;

    /** The binding for the point details SSBO. */
    static final int POINT_DETAILS_BINDING = 2;

    /** The scale of the GUI, kept here to prevent reallocation. */
    private final Vector2f scale;

    /** The GUI Mesh to use. */
    @Deprecated private final ImGuiMesh imGuiMesh;

    private final GuiMesh guiMesh;

    /** The shader to use for rendering ImGui. */
    @Deprecated @NonNull @Setter private Shader imGuiShader;

    /** The shader to use for rendering. */
    @NonNull @Setter private Shader shader;

    /** The font atlas texture. */
    private final Texture fontAtlas;

    /**
     * Set up the GUI render stage.
     *
     * @param imGuiMesh The mesh information ImGui uses.
     */
    public GuiRender(
            final @NonNull Shader imGuiShader,
            final @NonNull Shader shader,
            final @NonNull ImGuiMesh imGuiMesh,
            final @NonNull GuiMesh guiMesh,
            final @NonNull Texture fontAtlas) {
        scale = new Vector2f();
        this.imGuiShader = imGuiShader;
        this.shader = shader;
        this.imGuiMesh = imGuiMesh;
        this.guiMesh = guiMesh;
        this.fontAtlas = fontAtlas;
    }

    @Override
    public void render(Scene scene) {
        ImGuiIO io = ImGui.getIO();

        final int width = (int) io.getDisplaySizeX();
        final int height = (int) io.getDisplaySizeY();

        WindowManager windowManager = GraphicsManager.getWindowManager();
        if (windowManager == null) {
            return;
        }

        windowManager.drawGui(width, height);

        renderIkGui(width, height);
    }

    private void renderImGui(int width, int height) {
        // TODO(ches) render
        imGuiShader.bind();

        scale.x = 2.0f / width;
        scale.y = -2.0f / height;
        var uniformsMap = imGuiShader.getUniformMap();
        uniformsMap.setUniform(ShaderUniforms.GUI.SCALE, scale);

        ImDrawData drawData = ImGui.getDrawData();
        ImVec2 bufferScale = drawData.getFramebufferScale();
        ImVec2 displaySize = drawData.getDisplaySize();

        int framebufferHeight = (int) (displaySize.y * bufferScale.y);

        int commandListCount = drawData.getCmdListsCount();
        for (int i = 0; i < commandListCount; ++i) {
            // TODO(ches) opengl buffered stuff here

            int commandCount = drawData.getCmdListCmdBufferSize(i);
            for (int j = 0; j < commandCount; j++) {
                final int elementCount = drawData.getCmdListCmdBufferElemCount(i, j);
                final int indexBufferOffset = drawData.getCmdListCmdBufferIdxOffset(i, j);
                final int indices = indexBufferOffset * ImDrawData.sizeOfImDrawIdx();

                long id = drawData.getCmdListCmdBufferTextureId(i, j);

                ImVec4 clipRect = drawData.getCmdListCmdBufferClipRect(i, j);
                // TODO(ches) opengl bound and rendered stuff here
            }
        }

        imGuiShader.unbind();
    }

    private void renderIkGui(int width, int height) {
        // TODO(ches) render
        shader.bind();

        // TODO(ches) opengl buffered stuff here

        scale.x = 2.0f / width;
        scale.y = -2.0f / height;
        var uniformsMap = shader.getUniformMap();
        uniformsMap.setUniform(ShaderUniforms.GUI.SCALE, scale);

        if (!IkGui.getIO().fonts.stagedBitmaps.isEmpty()) {
            for (FontAtlas.StagedBitmap letter : IkGui.getIO().fonts.stagedBitmaps) {
                // TODO(ches) opengl rendered sub-images here

            }
            IkGui.getIO().fonts.stagedBitmaps.clear();
        }

        DrawData drawData = IkGui.getContext().drawData;
        int drawListCount = drawData.getDrawListCount();
        for (int i = 0; i < drawListCount; ++i) {
            int vertexCount = drawData.getDrawListVertexCount(i);
            // TODO(ches) opengl buffered and rendered stuff here

        }

        shader.unbind();
    }
}
