package com.ikalagaming.graphics.frontend.gui.data;

import lombok.extern.slf4j.Slf4j;
import org.joml.Vector2f;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;

@Slf4j
public class DrawData {
    /** The number of bytes that it takes to store the vertices of one quad. */
    static final int SIZE_OF_QUAD_VERTICES = 6 * 2 * Float.BYTES;

    public static final int SIZE_OF_POINT = 4 * Float.BYTES;
    public static final int SIZE_OF_POINT_DETAIL = Float.BYTES + 2 * Integer.BYTES;
    public static final int SIZE_OF_DRAW_COMMAND =
            2 * Integer.BYTES + 4 * Short.BYTES + Float.BYTES;

    private static final int RESIZE_FACTOR = 5000;

    private ByteBuffer dataBuffer =
            ByteBuffer.allocateDirect(25_000).order(ByteOrder.nativeOrder());

    public final ArrayList<DrawList> drawLists;
    public boolean valid;
    final Vector2f displayPosition;
    final Vector2f displaySize;
    final Vector2f framebufferScale;

    public DrawData() {
        drawLists = new ArrayList<>();
        valid = false;
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

    private ByteBuffer getCommandBuffer(int commandListIndex) {
        DrawList list = getDrawList(commandListIndex);
        return list == null ? null : list.commandBuffer;
    }

    /** Clear out all the draw lists, then clear out the list of draw lists for a new frame. */
    public void clear() {
        drawLists.forEach(DrawList::clear);
        drawLists.clear();
    }

    public ByteBuffer getCommandListCommandBuffer(final int commandListIndex) {
        return getCommandBuffer(commandListIndex);
    }

    public int getCommandListVertexBufferVertexCount(int commandListIndex) {
        DrawList list = getDrawList(commandListIndex);
        if (list == null) {
            return 0;
        }
        ByteBuffer vertexBuffer = list.vertexBuffer;
        if (vertexBuffer == null) {
            return 0;
        }
        return vertexBuffer.limit() / (2 * Float.BYTES);
    }

    public ByteBuffer getCommandListVertexBuffer(final int commandListIndex) {
        DrawList list = getDrawList(commandListIndex);
        return list == null ? null : list.vertexBuffer;
    }

    public ByteBuffer getCommandListPointDetailBufferData(final int commandListIndex) {
        DrawList list = getDrawList(commandListIndex);
        return list == null ? null : list.pointDetailBuffer;
    }

    public ByteBuffer getCommandListPointBufferData(final int commandListIndex) {
        DrawList list = getDrawList(commandListIndex);
        return list == null ? null : list.pointBuffer;
    }

    public int getCommandListsCount() {
        return drawLists.size();
    }

    public int getTotalDetailCount() {
        int total = 0;
        for (DrawList list : drawLists) {
            total += list.pointDetailBuffer.position() / SIZE_OF_POINT_DETAIL;
        }
        return total;
    }

    public int getTotalPointCount() {
        int total = 0;
        for (DrawList list : drawLists) {
            total += list.pointBuffer.position() / SIZE_OF_POINT;
        }
        return total;
    }
}
