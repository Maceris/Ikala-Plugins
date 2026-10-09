package com.ikalagaming.graphics.gui.data;

import com.ikalagaming.graphics.TextureInfo;
import com.ikalagaming.graphics.gui.IkGuiInternal;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector2f;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * The draw lists for one viewport, in the order they should be rendered. Built by render(), and
 * valid until the next call to newFrame().
 */
@Slf4j
public class DrawData {
    public static final int SIZE_OF_POINT = 4 * Float.BYTES;
    public static final int SIZE_OF_POINT_DETAIL = 2 * Float.BYTES + 2 * Integer.BYTES;
    public static final int SIZE_OF_DRAW_COMMAND = 6 * Integer.BYTES + Float.BYTES;
    public static final int SIZE_OF_VERTEX = 2 * Float.BYTES;

    /** The number of bytes that it takes to store the vertices of one quad. */
    static final int SIZE_OF_QUAD_VERTICES = 6 * SIZE_OF_VERTEX;

    public final List<DrawList> drawLists;

    /**
     * Textures used by draw lists this frame. Draw commands refer to textures by their index in
     * this list, and each rendering backend is responsible for mapping those indices to their own
     * texture handles. This list is shared between the draw data of all viewports.
     */
    public final List<TextureInfo> textures;

    /** The shared texture registry that {@link #textures} belongs to. */
    private final DrawTextures sharedTextures;

    /** Only valid after render() is called and before the next newFrame() is called. */
    public boolean valid;

    /**
     * The top-left position of the viewport to render, which is the position of the viewport in
     * desktop coordinates. Draw list coordinates are absolute, so renderers need to subtract this
     * to get framebuffer coordinates. This is (0, 0) for the main viewport unless multiple
     * viewports are enabled.
     */
    public final Vector2f displayPosition;

    /** The size of the viewport to render, which is (0, 0) when the viewport is minimized. */
    public final Vector2f displaySize;

    /** The amount of pixels for each unit of display size, from the viewport framebuffer scale. */
    public final Vector2f framebufferScale;

    /** The viewport that owns this draw data. */
    public Viewport ownerViewport;

    /**
     * Create draw data.
     *
     * @param sharedTextures The texture registry shared between all viewports.
     */
    public DrawData(@NonNull DrawTextures sharedTextures) {
        drawLists = new ArrayList<>();
        this.sharedTextures = sharedTextures;
        textures = sharedTextures.textures;
        valid = false;
        displayPosition = new Vector2f();
        displaySize = new Vector2f();
        framebufferScale = new Vector2f(1, 1);
        ownerViewport = null;
    }

    private DrawList getDrawList(int drawListIndex) {
        if (drawListIndex < 0 || drawListIndex >= drawLists.size()) {
            IkGuiInternal.reportError(log, "Index {} out of bounds in getDrawList", drawListIndex);
            return null;
        }

        return drawLists.get(drawListIndex);
    }

    private ByteBuffer getCommandBuffer(int drawListIndex) {
        DrawList list = getDrawList(drawListIndex);
        return list == null ? null : list.commandBuffer;
    }

    /**
     * Fetch the index of a texture for this frame, adding it to the list of textures if it is not
     * already there.
     *
     * @param texture The texture.
     * @return The index of the texture in {@link #textures}.
     */
    public int registerTexture(@NonNull TextureInfo texture) {
        return sharedTextures.register(texture);
    }

    public int getDrawListCommandCount(int drawListIndex) {
        ByteBuffer commandBuffer = getCommandBuffer(drawListIndex);
        if (commandBuffer == null) {
            return 0;
        }

        return commandBuffer.limit() / SIZE_OF_DRAW_COMMAND;
    }

    public ByteBuffer getDrawListCommandBuffer(final int drawListIndex) {
        return getCommandBuffer(drawListIndex);
    }

    public int getDrawListVertexCount(int drawListIndex) {
        DrawList list = getDrawList(drawListIndex);
        if (list == null) {
            return 0;
        }
        ByteBuffer vertexBuffer = list.vertexBuffer;
        if (vertexBuffer == null) {
            return 0;
        }
        return vertexBuffer.limit() / SIZE_OF_VERTEX;
    }

    public ByteBuffer getDrawListVertexBuffer(final int drawListIndex) {
        DrawList list = getDrawList(drawListIndex);
        return list == null ? null : list.vertexBuffer;
    }

    /**
     * Fetch the size of the point detail buffer, in terms of entries.
     *
     * @param drawListIndex The index of the command list to check.
     * @return The index buffer count, or 0 if an invalid index is provided.
     */
    public int getDrawListPointDetailCount(int drawListIndex) {
        DrawList list = getDrawList(drawListIndex);
        ByteBuffer buffer = list == null ? null : list.pointDetailBuffer;
        return buffer == null ? 0 : buffer.limit() / SIZE_OF_POINT_DETAIL;
    }

    public ByteBuffer getDrawListPointDetailBuffer(final int drawListIndex) {
        DrawList list = getDrawList(drawListIndex);
        return list == null ? null : list.pointDetailBuffer;
    }

    /**
     * Fetch the size of the point buffer, in terms of points.
     *
     * @param drawListIndex The index of the command list to check.
     * @return The vertex buffer count, or 0 if an invalid index is provided.
     */
    public int getDrawListPointCount(int drawListIndex) {
        DrawList list = getDrawList(drawListIndex);
        ByteBuffer buffer = list == null ? null : list.pointBuffer;
        return buffer == null ? 0 : buffer.limit() / SIZE_OF_POINT;
    }

    public ByteBuffer getDrawListPointBuffer(final int drawListIndex) {
        DrawList list = getDrawList(drawListIndex);
        return list == null ? null : list.pointBuffer;
    }

    public int getDrawListCount() {
        return drawLists.size();
    }

    public int getTotalDetailCount() {
        int total = 0;
        for (DrawList list : drawLists) {
            total += list.pointDetailBuffer.limit() / SIZE_OF_POINT_DETAIL;
        }
        return total;
    }

    public int getTotalPointCount() {
        int total = 0;
        for (DrawList list : drawLists) {
            total += list.pointBuffer.limit() / SIZE_OF_POINT;
        }
        return total;
    }
}
