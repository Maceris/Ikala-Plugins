package com.ikalagaming.graphics.frontend.gui.data;

import lombok.extern.slf4j.Slf4j;
import org.joml.Vector2f;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class DrawData {
    public static final int SIZE_OF_POINT = 4 * Float.BYTES;
    public static final int SIZE_OF_POINT_DETAIL = Float.BYTES + 2 * Integer.BYTES;
    public static final int SIZE_OF_DRAW_COMMAND = 6 * Integer.BYTES + Float.BYTES;
    public static final int SIZE_OF_VERTEX = 2 * Float.BYTES;

    /** The number of bytes that it takes to store the vertices of one quad. */
    static final int SIZE_OF_QUAD_VERTICES = 6 * SIZE_OF_VERTEX;

    public final List<DrawList> drawLists;
    final Vector2f displayPosition;
    final Vector2f displaySize;
    final Vector2f framebufferScale;

    public DrawData() {
        drawLists = new ArrayList<>();
        // TODO(ches) set up reasonable values
        displayPosition = new Vector2f();
        displaySize = new Vector2f();
        framebufferScale = new Vector2f();
    }

    private DrawList getDrawList(int drawListIndex) {
        if (drawListIndex < 0 || drawListIndex > drawLists.size()) {
            log.error("Index {} out of bounds in getDrawList", drawListIndex);
            return null;
        }

        return drawLists.get(drawListIndex);
    }

    private ByteBuffer getCommandBuffer(int drawListIndex) {
        DrawList list = getDrawList(drawListIndex);
        return list == null ? null : list.commandBuffer;
    }

    /** Clear out all the draw lists, then clear out the list of draw lists for a new frame. */
    public void clear() {
        drawLists.forEach(DrawList::clear);
        drawLists.clear();
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
