package com.ikalagaming.graphics.frontend.gui.data;

import com.ikalagaming.graphics.GraphicsPlugin;
import com.ikalagaming.graphics.frontend.gui.util.Rect;
import com.ikalagaming.util.SafeResourceLoader;

import lombok.extern.slf4j.Slf4j;
import org.joml.Vector2f;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;

@Slf4j
public class DrawData {
    private static final int SIZE_OF_RECT = 4 * Float.BYTES;

    public static final int SIZE_OF_DRAW_INDEX = Short.BYTES;
    public static final int SIZE_OF_DRAW_VERTEX = 4 * Float.BYTES + Integer.BYTES;
    public static final int SIZE_OF_DRAW_COMMAND = SIZE_OF_RECT + 4 * Integer.BYTES;

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
            log.error(
                    SafeResourceLoader.getString(
                            "INDEX_OUT_OF_BOUNDS",
                            GraphicsPlugin.getResourceBundle(),
                            Integer.toString(drawListIndex)));
            return null;
        }

        return drawLists.get(drawListIndex);
    }

    private ByteBuffer getCommandBuffer(int commandListIndex) {
        DrawList list = getDrawList(commandListIndex);
        return list == null ? null : list.commandBuffer;
    }

    private boolean offsetInvalid(ByteBuffer buffer, int position, int bytes) {
        boolean valid = (buffer != null) && (position + bytes < buffer.position());

        if (!valid) {
            log.error(
                    SafeResourceLoader.getString(
                            "INDEX_OUT_OF_BOUNDS",
                            GraphicsPlugin.getResourceBundle(),
                            Integer.toString(position)));
        }

        return !valid;
    }

    public int getCommandListCommandBufferSize(int commandListIndex) {
        ByteBuffer commandBuffer = getCommandBuffer(commandListIndex);
        if (commandBuffer == null) {
            return 0;
        }

        return commandBuffer.position();
    }

    public int getCommandListCommandBufferElementCount(
            int commandListIndex, int commandBufferIndex) {
        ByteBuffer commandBuffer = getCommandBuffer(commandListIndex);
        if (commandBuffer == null) {
            return 0;
        }

        final int countLocation =
                commandBufferIndex * SIZE_OF_DRAW_COMMAND + SIZE_OF_RECT + 3 * Integer.BYTES;

        if (offsetInvalid(commandBuffer, countLocation, Integer.BYTES)) {
            return 0;
        }

        return commandBuffer.getInt(countLocation);
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

    public int getCommandListCommandBufferTextureId(int commandListIndex, int commandBufferIndex) {
        ByteBuffer commandBuffer = getCommandBuffer(commandListIndex);
        if (commandBuffer == null) {
            return 0;
        }

        final int textureLocation = commandBufferIndex * SIZE_OF_DRAW_COMMAND + SIZE_OF_RECT;

        if (offsetInvalid(commandBuffer, textureLocation, Integer.BYTES)) {
            return 0;
        }

        return commandBuffer.getInt(textureLocation);
    }

    public int getCommandListCommandBufferVertexOffset(
            int commandListIndex, int commandBufferIndex) {
        ByteBuffer commandBuffer = getCommandBuffer(commandListIndex);
        if (commandBuffer == null) {
            return 0;
        }

        final int vertexOffsetLocation =
                commandBufferIndex * SIZE_OF_DRAW_COMMAND + SIZE_OF_RECT + Integer.BYTES;

        if (offsetInvalid(commandBuffer, vertexOffsetLocation, Integer.BYTES)) {
            return 0;
        }

        return commandBuffer.getInt(vertexOffsetLocation);
    }

    public int getCommandListCommandBufferIndexOffset(
            int commandListIndex, int commandBufferIndex) {
        ByteBuffer commandBuffer = getCommandBuffer(commandListIndex);
        if (commandBuffer == null) {
            return 0;
        }

        final int indexOffsetLocation =
                commandBufferIndex * SIZE_OF_DRAW_COMMAND + SIZE_OF_RECT + 2 * Integer.BYTES;

        if (offsetInvalid(commandBuffer, indexOffsetLocation, Integer.BYTES)) {
            return 0;
        }

        return commandBuffer.getInt(indexOffsetLocation);
    }

    /**
     * Fetch the size of the index buffer, in terms of indexes.
     *
     * @param commandListIndex The index of the command list to check.
     * @return The index buffer count, or 0 if an invalid index is provided.
     */
    public int getCommandListIndexBufferSize(int commandListIndex) {
        DrawList list = getDrawList(commandListIndex);
        ByteBuffer buffer = list == null ? null : list.indexBuffer;
        return buffer == null ? 0 : buffer.position() / SIZE_OF_DRAW_INDEX;
    }

    public ByteBuffer getCommandListIndexBufferData(final int commandListIndex) {
        DrawList list = getDrawList(commandListIndex);
        return list == null ? null : list.indexBuffer;
    }

    /**
     * Fetch the size of the index buffer, in terms of vertices.
     *
     * @param commandListIndex The index of the command list to check.
     * @return The vertex buffer count, or 0 if an invalid index is provided.
     */
    public int getCommandListVertexBufferSize(int commandListIndex) {
        DrawList list = getDrawList(commandListIndex);
        ByteBuffer buffer = list == null ? null : list.vertexBuffer;
        return buffer == null ? 0 : buffer.position() / SIZE_OF_DRAW_VERTEX;
    }

    public ByteBuffer getCommandListVertexBufferData(final int commandListIndex) {
        DrawList list = getDrawList(commandListIndex);
        return list == null ? null : list.vertexBuffer;
    }

    public int getCommandListsCount() {
        return drawLists.size();
    }

    public int getTotalIndexCount() {
        int total = 0;
        for (DrawList list : drawLists) {
            total += list.indexBuffer.position() / SIZE_OF_DRAW_INDEX;
        }
        return total;
    }

    public int getTotalVertexCount() {
        int total = 0;
        for (DrawList list : drawLists) {
            total += list.vertexBuffer.position() / SIZE_OF_DRAW_VERTEX;
        }
        return total;
    }
}
