package com.ikalagaming.graphics.backend.opengl;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_DELETE;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_END;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_HOME;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_INSERT;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_DOWN;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_UP;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_TAB;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_UP;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL14.GL_FUNC_ADD;
import static org.lwjgl.opengl.GL14.glBlendEquation;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL30.*;

import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.ShaderUniforms;
import com.ikalagaming.graphics.frontend.Format;
import com.ikalagaming.graphics.frontend.Shader;
import com.ikalagaming.graphics.frontend.Texture;
import com.ikalagaming.graphics.frontend.gui.WindowManager;

import imgui.ImDrawData;
import imgui.ImFontAtlas;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.ImVec2;
import imgui.ImVec4;
import imgui.flag.ImGuiKey;
import imgui.type.ImInt;
import lombok.NonNull;
import org.joml.Vector2f;

import java.nio.ByteBuffer;

/** A utility for handling the rendering of a Graphical User Interface. */
public class GuiRender {

    /** The scale of the GUI. */
    private final Vector2f scale;

    /** The texture we store font data in. */
    private Texture font;

    /** The width of the drawable area in pixels. */
    private int cachedWidth;

    /** The width of the drawable area in pixels. */
    private int cachedHeight;

    /**
     * Set up a new GUI renderer for the window.
     *
     * @param width The drawable area width in pixels.
     * @param height The drawable area height in pixels.
     */
    public GuiRender(final int width, final int height) {
        scale = new Vector2f();
        cachedWidth = width;
        cachedHeight = height;
        createUIResources();
    }

    /** Clean up shaders and textures. */
    public void cleanup() {
        glDeleteTextures((int) font.id());
    }

    /** Set up imgui and create fonts, textures, meshes, etc. */
    private void createUIResources() {
        ImGui.createContext();

        ImGuiIO imGuiIO = ImGui.getIO();
        imGuiIO.setIniFilename(null);
        imGuiIO.setDisplaySize(cachedWidth, cachedHeight);
        setUpImGuiKeys();

        ImFontAtlas fontAtlas = ImGui.getIO().getFonts();
        ImInt width = new ImInt();
        ImInt height = new ImInt();
        ByteBuffer buf = fontAtlas.getTexDataAsRGBA32(width, height);
        font =
                GraphicsManager.getRenderInstance()
                        .getTextureLoader()
                        .load(buf, Format.R8G8B8A8_UINT, width.get(), height.get());
        fontAtlas.setTexID((int) font.id());
    }

    /**
     * Render the GUI over the given scene.
     *
     * @param shader The shader to use for rendering.
     */
    public void render(@NonNull Shader shader, @NonNull GuiMesh guiMesh) {
        var uniformsMap = shader.getUniformMap();
        WindowManager windowManager = GraphicsManager.getWindowManager();
        if (windowManager == null) {
            return;
        }
        windowManager.drawGui(cachedWidth, cachedHeight);

        shader.bind();

        glEnable(GL_BLEND);
        glBlendEquation(GL_FUNC_ADD);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_CULL_FACE);

        glBindVertexArray(guiMesh.vaoID());

        glBindBuffer(GL_ARRAY_BUFFER, guiMesh.verticesVBO());
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, guiMesh.indicesVBO());

        ImGuiIO io = ImGui.getIO();
        scale.x = 2.0f / io.getDisplaySizeX();
        scale.y = -2.0f / io.getDisplaySizeY();
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
                final int indices = indexBufferOffset * ImDrawData.SIZEOF_IM_DRAW_IDX;

                int id = drawData.getCmdListCmdBufferTextureId(i, j);

                ImVec4 clipRect = drawData.getCmdListCmdBufferClipRect(i, j);

                glScissor(
                        (int) clipRect.x,
                        (int) (framebufferHeight - clipRect.w),
                        (int) (clipRect.z - clipRect.x),
                        (int) (clipRect.w - clipRect.y));

                glBindTexture(GL_TEXTURE_2D, id);

                glDrawElements(GL_TRIANGLES, elementCount, GL_UNSIGNED_SHORT, indices);
            }
        }

        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glDisable(GL_BLEND);
        shader.unbind();
    }

    /**
     * Update the GUI when we resize the screen.
     *
     * @param width The new screen width in pixels.
     * @param height The new screen height in pixels.
     */
    public void resize(final int width, final int height) {
        cachedWidth = width;
        cachedHeight = height;
        ImGuiIO imGuiIO = ImGui.getIO();
        imGuiIO.setDisplaySize(width, height);
    }

    /** Set up nonstandard key codes to make sure they work. */
    private void setUpImGuiKeys() {
        ImGuiIO io = ImGui.getIO();
        io.setKeyMap(ImGuiKey.Tab, GLFW_KEY_TAB);
        io.setKeyMap(ImGuiKey.LeftArrow, GLFW_KEY_LEFT);
        io.setKeyMap(ImGuiKey.RightArrow, GLFW_KEY_RIGHT);
        io.setKeyMap(ImGuiKey.UpArrow, GLFW_KEY_UP);
        io.setKeyMap(ImGuiKey.DownArrow, GLFW_KEY_DOWN);
        io.setKeyMap(ImGuiKey.PageUp, GLFW_KEY_PAGE_UP);
        io.setKeyMap(ImGuiKey.PageDown, GLFW_KEY_PAGE_DOWN);
        io.setKeyMap(ImGuiKey.Home, GLFW_KEY_HOME);
        io.setKeyMap(ImGuiKey.End, GLFW_KEY_END);
        io.setKeyMap(ImGuiKey.Insert, GLFW_KEY_INSERT);
        io.setKeyMap(ImGuiKey.Delete, GLFW_KEY_DELETE);
        io.setKeyMap(ImGuiKey.Backspace, GLFW_KEY_BACKSPACE);
        io.setKeyMap(ImGuiKey.Space, GLFW_KEY_SPACE);
        io.setKeyMap(ImGuiKey.Enter, GLFW_KEY_ENTER);
        io.setKeyMap(ImGuiKey.Escape, GLFW_KEY_ESCAPE);
        io.setKeyMap(ImGuiKey.KeyPadEnter, GLFW_KEY_KP_ENTER);
    }
}
