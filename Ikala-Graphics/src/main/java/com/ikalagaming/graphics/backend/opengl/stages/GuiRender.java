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
import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.backend.base.RenderStage;
import com.ikalagaming.graphics.backend.base.State;
import com.ikalagaming.graphics.backend.opengl.GuiMesh;
import com.ikalagaming.graphics.backend.opengl.TextureInfoOpenGL;
import com.ikalagaming.graphics.frontend.Shader;
import com.ikalagaming.graphics.frontend.Texture;
import com.ikalagaming.graphics.frontend.TextureInfo;
import com.ikalagaming.graphics.frontend.gui.IkGui;
import com.ikalagaming.graphics.frontend.gui.WindowManager;
import com.ikalagaming.graphics.frontend.gui.data.DrawData;
import com.ikalagaming.graphics.frontend.gui.data.FontAtlas;
import com.ikalagaming.graphics.frontend.gui.data.IkIO;
import com.ikalagaming.graphics.scene.Scene;

import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector2f;
import org.lwjgl.system.MemoryUtil;

import java.nio.LongBuffer;

@Slf4j
public class GuiRender implements RenderStage {

    /** The binding for the commands SSBO. */
    static final int COMMANDS_BINDING = 0;

    /** The binding for the points SSBO. */
    static final int POINTS_BINDING = 1;

    /** The binding for the point details SSBO. */
    static final int POINT_DETAILS_BINDING = 2;

    /** The binding for the texture handles SSBO. */
    static final int TEXTURE_HANDLES_BINDING = 3;

    /** The scale of the GUI, kept here to prevent reallocation. */
    private final Vector2f scale;

    /** The GUI Mesh to use. */
    private final GuiMesh guiMesh;

    /** The shader to use for rendering. */
    @NonNull @Setter private Shader shader;

    /** The font atlas texture. */
    private final Texture fontAtlas;

    /**
     * Set up the GUI render stage.
     *
     * @param shader The shader to render the GUI with.
     * @param guiMesh The mesh information the GUI uses.
     * @param fontAtlas The font atlas texture.
     */
    public GuiRender(
            final @NonNull Shader shader,
            final @NonNull GuiMesh guiMesh,
            final @NonNull Texture fontAtlas) {
        scale = new Vector2f();
        this.shader = shader;
        this.guiMesh = guiMesh;
        this.fontAtlas = fontAtlas;
    }

    @Override
    public void render(Scene scene, @NonNull Window window, State state, int renderConfig) {
        final IkIO io = IkGui.getIO();

        final int width = (int) io.displaySize.x;
        final int height = (int) io.displaySize.y;

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

        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glDisable(GL_BLEND);
    }

    private void renderIkGui(int width, int height) {
        shader.bind();

        glBindVertexArray(guiMesh.vaoID());

        glBindBuffer(GL_ARRAY_BUFFER, guiMesh.vertices());
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, COMMANDS_BINDING, (int) guiMesh.commands().id());
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, POINTS_BINDING, (int) guiMesh.points().id());
        glBindBufferBase(
                GL_SHADER_STORAGE_BUFFER, POINT_DETAILS_BINDING, (int) guiMesh.pointDetails().id());
        glBindBufferBase(
                GL_SHADER_STORAGE_BUFFER,
                TEXTURE_HANDLES_BINDING,
                (int) guiMesh.textureHandles().id());

        scale.x = 2.0f / width;
        scale.y = -2.0f / height;
        var uniformsMap = shader.getUniformMap();
        uniformsMap.setUniform(ShaderUniforms.GUI.SCALE, scale);

        var atlasInfo = (TextureInfoOpenGL) fontAtlas.info();
        glBindTexture(GL_TEXTURE_2D, (int) atlasInfo.id);

        if (!IkGui.getIO().fonts.stagedBitmaps.isEmpty()) {
            for (FontAtlas.StagedBitmap letter : IkGui.getIO().fonts.stagedBitmaps) {
                glTexSubImage2D(
                        GL_TEXTURE_2D,
                        0,
                        letter.x(),
                        letter.y(),
                        letter.width(),
                        letter.height(),
                        GL_RGBA,
                        GL_UNSIGNED_BYTE,
                        letter.data());
            }
            IkGui.getIO().fonts.stagedBitmaps.clear();
        }

        DrawData drawData = IkGui.getDrawData();
        if (drawData == null) {
            glBindBuffer(GL_ARRAY_BUFFER, 0);
            glBindVertexArray(0);
            shader.unbind();
            return;
        }
        uniformsMap.setUniform(ShaderUniforms.GUI.DISPLAY_POSITION, drawData.displayPosition);
        uploadTextureHandles(drawData);

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

    /**
     * Upload the bindless handles for all the textures used by the GUI this frame, in the order of
     * {@link DrawData#textures} so that draw commands can refer to them by index.
     *
     * @param drawData The draw data for the frame.
     */
    private void uploadTextureHandles(@NonNull DrawData drawData) {
        // Always upload at least one handle, so the buffer is never empty
        final int count = Math.max(1, drawData.textures.size());
        LongBuffer handles = MemoryUtil.memAllocLong(count);
        try {
            final long fallback = getResidentHandle(fontAtlas.info());
            handles.put(0, fallback);
            for (int i = 0; i < drawData.textures.size(); ++i) {
                long handle = getResidentHandle(drawData.textures.get(i));
                handles.put(i, handle != 0 ? handle : fallback);
            }
            glBindBuffer(GL_SHADER_STORAGE_BUFFER, (int) guiMesh.textureHandles().id());
            glBufferData(GL_SHADER_STORAGE_BUFFER, handles, GL_STREAM_DRAW);
        } finally {
            MemoryUtil.memFree(handles);
        }
    }

    /**
     * Fetch the bindless handle for a texture, creating it and making it resident as required.
     *
     * @param texture The texture.
     * @return The bindless handle, or 0 if the texture is not an OpenGL texture.
     */
    private static long getResidentHandle(@NonNull TextureInfo texture) {
        if (!(texture instanceof TextureInfoOpenGL info)) {
            log.warn("Can't render non-OpenGL texture {} in the GUI", texture);
            return 0;
        }
        return info.makeResident();
    }
}
