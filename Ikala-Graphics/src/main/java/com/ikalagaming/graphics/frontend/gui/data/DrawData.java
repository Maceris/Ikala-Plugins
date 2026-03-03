package com.ikalagaming.graphics.frontend.gui.data;

import com.ikalagaming.graphics.frontend.gui.util.Rect;

import lombok.extern.slf4j.Slf4j;
import org.joml.Vector2f;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;

@Slf4j
public class DrawData {
    private static final int SIZE_OF_RECT = 4 * Float.BYTES;

    public static final int SIZE_OF_POINT = 4 * Float.BYTES;
    public static final int SIZE_OF_POINT_DETAIL = Float.BYTES + 2 * Integer.BYTES;
    public static final int SIZE_OF_DRAW_COMMAND =
            2 * Integer.BYTES + 4 * Short.BYTES + Float.BYTES;

    private static final int RESIZE_FACTOR = 5000;

    private ByteBuffer dataBuffer =
            ByteBuffer.allocateDirect(25_000).order(ByteOrder.nativeOrder());

    final ArrayList<DrawList> drawLists;
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

    private boolean offsetInvalid(ByteBuffer buffer, int position, int bytes) {
        if (buffer == null || (position + bytes < buffer.position())) {
            log.error("Index {} out of bounds in offsetInvalid", position);
            return false;
        }
        return true;
    }

    public int getCommandListCommandBufferSize(int commandListIndex) {
        ByteBuffer commandBuffer = getCommandBuffer(commandListIndex);
        if (commandBuffer == null) {
            return 0;
        }

        return commandBuffer.position();
    }

    public ByteBuffer getCommandListCommandBuffer(final int commandListIndex) {
        return getCommandBuffer(commandListIndex);
    }

    public int getCommandListVertexBufferSize(int commandListIndex) {
        DrawList list = getDrawList(commandListIndex);
        if (list == null) {
            return 0;
        }
        ByteBuffer vertexBuffer = list.vertexBuffer;
        if (vertexBuffer == null) {
            return 0;
        }
        return vertexBuffer.position();
    }

    public ByteBuffer getCommandListVertexBuffer(final int commandListIndex) {
        DrawList list = getDrawList(commandListIndex);
        return list == null ? null : list.vertexBuffer;
    }

    public Rect getCommandListCommandBufferClipRect(
            final int commandListIndex, final int commandBufferIndex) {
        final Rect value = new Rect();
        getCommandListCommandBufferClipRect(commandListIndex, commandBufferIndex, value);
        return value;
    }

    public void getCommandListCommandBufferClipRect(
            int commandListIndex, int commandBufferIndex, Rect output) {

        ByteBuffer commandBuffer = getCommandBuffer(commandListIndex);
        if (commandBuffer == null) {
            return;
        }

        final int rectLocation = commandBufferIndex * SIZE_OF_DRAW_COMMAND;

        if (offsetInvalid(commandBuffer, rectLocation, SIZE_OF_RECT)) {
            return;
        }

        float clipMinX = commandBuffer.getFloat(rectLocation);
        float clipMinY = commandBuffer.getFloat(rectLocation + Float.BYTES);
        float clipMaxX = commandBuffer.getFloat(rectLocation + 2 * Float.BYTES);
        float clipMaxY = commandBuffer.getFloat(rectLocation + 3 * Float.BYTES);

        output.set(clipMinX, clipMinY, clipMaxX, clipMaxY);
    }

    /**
     * Fetch the size of the point detail buffer, in terms of entries.
     *
     * @param commandListIndex The index of the command list to check.
     * @return The index buffer count, or 0 if an invalid index is provided.
     */
    public int getCommandListPointDetailBufferSize(int commandListIndex) {
        DrawList list = getDrawList(commandListIndex);
        ByteBuffer buffer = list == null ? null : list.pointDetailBuffer;
        return buffer == null ? 0 : buffer.position() / SIZE_OF_POINT_DETAIL;
    }

    public ByteBuffer getCommandListPointDetailBufferData(final int commandListIndex) {
        DrawList list = getDrawList(commandListIndex);
        return list == null ? null : list.pointDetailBuffer;
    }

    /**
     * Fetch the size of the point buffer, in terms of points.
     *
     * @param commandListIndex The index of the command list to check.
     * @return The vertex buffer count, or 0 if an invalid index is provided.
     */
    public int getCommandListPointBufferSize(int commandListIndex) {
        DrawList list = getDrawList(commandListIndex);
        ByteBuffer buffer = list == null ? null : list.pointBuffer;
        return buffer == null ? 0 : buffer.position() / SIZE_OF_POINT;
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
