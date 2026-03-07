package com.ikalagaming.graphics.backend.opengl.stages;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL14.GL_FUNC_ADD;
import static org.lwjgl.opengl.GL14.glBlendEquation;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL30.glBindBufferBase;
import static org.lwjgl.opengl.GL30.glBindVertexArray;
import static org.lwjgl.opengl.GL43.GL_SHADER_STORAGE_BUFFER;

import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.ShaderUniforms;
import com.ikalagaming.graphics.backend.base.RenderStage;
import com.ikalagaming.graphics.backend.opengl.GuiMesh;
import com.ikalagaming.graphics.backend.opengl.ImGuiMesh;
import com.ikalagaming.graphics.frontend.Shader;
import com.ikalagaming.graphics.frontend.gui.IkGui;
import com.ikalagaming.graphics.frontend.gui.WindowManager;
import com.ikalagaming.graphics.frontend.gui.data.DrawData;
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

    /**
     * Set up the GUI render stage.
     *
     * @param imGuiMesh The mesh information ImGui uses.
     */
    public GuiRender(
            final @NonNull Shader imGuiShader,
            final @NonNull Shader shader,
            final @NonNull ImGuiMesh imGuiMesh,
            final @NonNull GuiMesh guiMesh) {
        scale = new Vector2f();
        this.imGuiShader = imGuiShader;
        this.shader = shader;
        this.imGuiMesh = imGuiMesh;
        this.guiMesh = guiMesh;
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

        glEnable(GL_BLEND);
        glBlendEquation(GL_FUNC_ADD);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_CULL_FACE);

        renderIkGui(width, height);
        renderImGui(width, height);

        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glDisable(GL_BLEND);
    }

    private void renderImGui(int width, int height) {
        imGuiShader.bind();

        glBindVertexArray(imGuiMesh.vaoID());

        glBindBuffer(GL_ARRAY_BUFFER, imGuiMesh.verticesVBO());
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, imGuiMesh.indicesVBO());

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
            glBufferData(GL_ARRAY_BUFFER, drawData.getCmdListVtxBufferData(i), GL_STREAM_DRAW);
            glBufferData(
                    GL_ELEMENT_ARRAY_BUFFER, drawData.getCmdListIdxBufferData(i), GL_STREAM_DRAW);

            int commandCount = drawData.getCmdListCmdBufferSize(i);
            for (int j = 0; j < commandCount; j++) {
                final int elementCount = drawData.getCmdListCmdBufferElemCount(i, j);
                final int indexBufferOffset = drawData.getCmdListCmdBufferIdxOffset(i, j);
                final int indices = indexBufferOffset * ImDrawData.sizeOfImDrawIdx();

                long id = drawData.getCmdListCmdBufferTextureId(i, j);

                ImVec4 clipRect = drawData.getCmdListCmdBufferClipRect(i, j);

                glScissor(
                        (int) clipRect.x,
                        (int) (framebufferHeight - clipRect.w),
                        (int) (clipRect.z - clipRect.x),
                        (int) (clipRect.w - clipRect.y));

                glBindTexture(GL_TEXTURE_2D, (int) id);

                glDrawElements(GL_TRIANGLES, elementCount, GL_UNSIGNED_SHORT, indices);
            }
        }

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
        glBindVertexArray(0);

        imGuiShader.unbind();
    }

    private void renderIkGui(int width, int height) {
        shader.bind();

        glBindVertexArray(guiMesh.vaoID());

        glBindBuffer(GL_ARRAY_BUFFER, guiMesh.vertices());
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, COMMANDS_BINDING, (int) guiMesh.commands().id());
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, POINTS_BINDING, (int) guiMesh.points().id());
        glBindBufferBase(
                GL_SHADER_STORAGE_BUFFER, POINT_DETAILS_BINDING, (int) guiMesh.pointDetails().id());

        scale.x = 2.0f / width;
        scale.y = -2.0f / height;
        var uniformsMap = shader.getUniformMap();
        uniformsMap.setUniform(ShaderUniforms.GUI.SCALE, scale);

        // TODO(ches) bind font texture

        DrawData drawData = IkGui.getContext().drawData;
        int drawListCount = drawData.getDrawListCount();
        for (int i = 0; i < drawListCount; ++i) {
            int vertexCount = drawData.getDrawListVertexCount(i);

            glBufferData(GL_ARRAY_BUFFER, drawData.getDrawListVertexBuffer(i), GL_STREAM_DRAW);

            glBindBuffer(GL_SHADER_STORAGE_BUFFER, (int) guiMesh.commands().id());
            glBufferData(
                    GL_SHADER_STORAGE_BUFFER, drawData.getDrawListCommandBuffer(i), GL_STREAM_DRAW);
            glBindBuffer(GL_SHADER_STORAGE_BUFFER, (int) guiMesh.points().id());
            glBufferData(
                    GL_SHADER_STORAGE_BUFFER, drawData.getDrawListPointBuffer(i), GL_STREAM_DRAW);
            glBindBuffer(GL_SHADER_STORAGE_BUFFER, (int) guiMesh.pointDetails().id());
            glBufferData(
                    GL_SHADER_STORAGE_BUFFER,
                    drawData.getDrawListPointDetailBuffer(i),
                    GL_STREAM_DRAW);

            glDrawArrays(GL_TRIANGLES, 0, vertexCount);
        }
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);

        shader.unbind();
    }
}
