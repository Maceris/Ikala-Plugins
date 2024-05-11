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
import static org.lwjgl.opengl.GL15.glGenBuffers;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL20.glVertexAttribPointer;
import static org.lwjgl.opengl.GL30.*;

import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.ShaderUniforms;
import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.backend.base.TextureHandler;
import com.ikalagaming.graphics.frontend.Format;
import com.ikalagaming.graphics.frontend.Texture;
import com.ikalagaming.graphics.frontend.gui.WindowManager;
import com.ikalagaming.graphics.graph.ShaderProgram;
import com.ikalagaming.graphics.graph.UniformsMap;

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
import java.util.ArrayList;
import java.util.List;

/** A utility for handling the rendering of a Graphical User Interface. */
public class GuiRender {

    /**
     * Used to provide ImGUI with the VBOs it needs to render. This should be created using the
     * {@link GuiMesh#create()} method instead of a constructor.
     *
     * @param vaoID The VAO.
     * @param verticesVBO The vertices VBO, set up for ImGui.
     * @param indicesVBO The indices VBO.
     */
    private record GuiMesh(int vaoID, int verticesVBO, int indicesVBO) {
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
    }

    /** The mesh to render. */
    private GuiMesh guiMesh;

    /** The scale of the GUI. */
    private Vector2f scale;

    /** The shader program for rendering the UI. */
    private final ShaderProgram shaderProgram;

    /** The texture we store font data in. */
    private Texture font;

    /** The uniforms for the shader program. */
    private UniformsMap uniformsMap;

    /**
     * Set up a new GUI renderer for the window.
     *
     * @param window The window we are rendering in.
     * @param textureHandler The texture handler implementation to use.
     */
    public GuiRender(@NonNull Window window, @NonNull TextureHandler textureHandler) {
        List<ShaderProgram.ShaderModuleData> shaderModuleDataList = new ArrayList<>();
        shaderModuleDataList.add(
                new ShaderProgram.ShaderModuleData("shaders/gui.vert", GL_VERTEX_SHADER));
        shaderModuleDataList.add(
                new ShaderProgram.ShaderModuleData("shaders/gui.frag", GL_FRAGMENT_SHADER));
        shaderProgram = new ShaderProgram(shaderModuleDataList);
        createUniforms();
        createUIResources(window, textureHandler);
    }

    /**
     * Clean up shaders and textures.
     *
     * @param textureHandler The texture handler implementation to use.
     */
    public void cleanup(@NonNull TextureHandler textureHandler) {
        shaderProgram.cleanup();
        textureHandler.cleanup(font);
        glDeleteBuffers(guiMesh.indicesVBO());
        glDeleteBuffers(guiMesh.verticesVBO());
        glDeleteVertexArrays(guiMesh.vaoID());
    }

    /**
     * Set up imgui and create fonts, textures, meshes, etc.
     *
     * @param window The window we are using.
     * @param textureHandler The texture handler implementation to use.
     */
    private void createUIResources(@NonNull Window window, @NonNull TextureHandler textureHandler) {
        ImGui.createContext();

        ImGuiIO imGuiIO = ImGui.getIO();
        imGuiIO.setIniFilename(null);
        imGuiIO.setDisplaySize(window.getWidth(), window.getHeight());
        setUpImGuiKeys();

        ImFontAtlas fontAtlas = ImGui.getIO().getFonts();
        ImInt width = new ImInt();
        ImInt height = new ImInt();
        ByteBuffer buf = fontAtlas.getTexDataAsRGBA32(width, height);
        font = textureHandler.load(buf, Format.R8G8B8A8_UINT, width.get(), height.get());
        fontAtlas.setTexID(font.id());

        guiMesh = GuiMesh.create();
    }

    /** Set up uniforms for the GUI shader. */
    private void createUniforms() {
        uniformsMap = new UniformsMap(shaderProgram.getProgramID());
        uniformsMap.createUniform(ShaderUniforms.GUI.SCALE);
        scale = new Vector2f();
    }

    /**
     * Render the GUI over the given scene.
     *
     * @param textureHandler The texture handler implementation to use.
     */
    public void render(@NonNull Window window, @NonNull TextureHandler textureHandler) {
        WindowManager windowManager = GraphicsManager.getWindowManager();
        if (windowManager == null) {
            return;
        }
        windowManager.drawGui(window.getWidth(), window.getHeight(), textureHandler);

        shaderProgram.bind();

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
        ImVec2 bufScale = drawData.getFramebufferScale();
        ImVec2 displSize = drawData.getDisplaySize();

        int fbHeight = (int) (displSize.y * bufScale.y);

        int numLists = drawData.getCmdListsCount();
        for (int i = 0; i < numLists; ++i) {
            glBufferData(GL_ARRAY_BUFFER, drawData.getCmdListVtxBufferData(i), GL_STREAM_DRAW);
            glBufferData(
                    GL_ELEMENT_ARRAY_BUFFER, drawData.getCmdListIdxBufferData(i), GL_STREAM_DRAW);

            int numCmds = drawData.getCmdListCmdBufferSize(i);
            for (int j = 0; j < numCmds; j++) {
                final int elemCount = drawData.getCmdListCmdBufferElemCount(i, j);
                final int idxBufferOffset = drawData.getCmdListCmdBufferIdxOffset(i, j);
                final int indices = idxBufferOffset * ImDrawData.SIZEOF_IM_DRAW_IDX;

                int id = drawData.getCmdListCmdBufferTextureId(i, j);

                ImVec4 clipRect = drawData.getCmdListCmdBufferClipRect(i, j);

                glScissor(
                        (int) clipRect.x,
                        (int) (fbHeight - clipRect.w),
                        (int) (clipRect.z - clipRect.x),
                        (int) (clipRect.w - clipRect.y));

                glBindTexture(GL_TEXTURE_2D, id);

                glDrawElements(GL_TRIANGLES, elemCount, GL_UNSIGNED_SHORT, indices);
            }
        }

        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glDisable(GL_BLEND);
    }

    /**
     * Update the GUI when we resize the screen.
     *
     * @param width The new screen width in pixels.
     * @param height The new screen height in pixels.
     */
    public void resize(final int width, final int height) {
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
