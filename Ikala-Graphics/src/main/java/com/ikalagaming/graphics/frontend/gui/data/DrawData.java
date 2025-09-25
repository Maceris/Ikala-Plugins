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

    public DrawData() {
        drawLists = new ArrayList<>();
    }

    private ByteBuffer getCommandBuffer(int commandListIndex) {
        if (commandListIndex < 0 || commandListIndex > drawLists.size()) {
            log.error(
                    SafeResourceLoader.getString(
                            "INDEX_OUT_OF_BOUNDS",
                            GraphicsPlugin.getResourceBundle(),
                            Integer.toString(commandListIndex)));
            return null;
        }

        return drawLists.get(commandListIndex).commandBuffer;
    }

    private boolean offsetValid(ByteBuffer buffer, int position, int bytes) {
        boolean result = (buffer != null) && (position + bytes < buffer.position());

        if (!result) {
            log.error(
                    SafeResourceLoader.getString(
                            "INDEX_OUT_OF_BOUNDS",
                            GraphicsPlugin.getResourceBundle(),
                            Integer.toString(position)));
        }

        return result;
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

        final int countLocation = (commandBufferIndex + 1) * SIZE_OF_DRAW_COMMAND - Integer.BYTES;

        if (!offsetValid(commandBuffer, countLocation, Integer.BYTES)) {
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

        final int countLocation = commandBufferIndex * SIZE_OF_DRAW_COMMAND;

        if (!offsetValid(commandBuffer, countLocation, SIZE_OF_RECT)) {
            return;
        }

        float clipMinX = commandBuffer.getFloat(countLocation);
        float clipMinY = commandBuffer.getFloat(countLocation + Float.BYTES);
        float clipMaxX = commandBuffer.getFloat(countLocation + 2 * Float.BYTES);
        float clipMaxY = commandBuffer.getFloat(countLocation + 3 * Float.BYTES);

        output.set(clipMinX, clipMinY, clipMaxX, clipMaxY);
    }

    public int getCommandListCommandBufferTextureId(int commandListIndex, int commandBufferIndex) {
        // TODO(ches) implement this
        return 0;
    }

    public int getCommandListCommandBufferVertexOffset(
            int commandListIndex, int commandBufferIndex) {
        // TODO(ches) implement this
        return 0;
    }

    public int getCommandListCommandBufferIndexOffset(
            int commandListIndex, int commandBufferIndex) {
        // TODO(ches) implement this
        return 0;
    }

    public int getCommandListIndexBufferSize(int commandListIndex) {
        // TODO(ches) implement this
        return 0;
    }

    public ByteBuffer getCommandListIndexBufferData(final int commandListIndex) {
        final int indexBufferCapacity =
                getCommandListIndexBufferSize(commandListIndex) * SIZE_OF_DRAW_INDEX;
        if (dataBuffer.capacity() < indexBufferCapacity) {
            dataBuffer.clear();
            dataBuffer =
                    ByteBuffer.allocateDirect(indexBufferCapacity + RESIZE_FACTOR)
                            .order(ByteOrder.nativeOrder());
        }

        // TODO(ches) implement this

        dataBuffer.position(0);
        dataBuffer.limit(indexBufferCapacity);

        return dataBuffer;
    }

    public int getCommandListVertexBufferSize(int commandListIndex) {
        // TODO(ches) implement this
        return 0;
    }

    public ByteBuffer getCommandListVertexBufferData(final int commandListIndex) {
        final int vertexBufferCapacity =
                getCommandListVertexBufferSize(commandListIndex) * SIZE_OF_DRAW_VERTEX;
        if (dataBuffer.capacity() < vertexBufferCapacity) {
            dataBuffer.clear();
            dataBuffer =
                    ByteBuffer.allocateDirect(vertexBufferCapacity + RESIZE_FACTOR)
                            .order(ByteOrder.nativeOrder());
        }

        // TODO(ches) get buffer data

        dataBuffer.position(0);
        dataBuffer.limit(vertexBufferCapacity);

        return dataBuffer;
    }

    public boolean getValid() {
        // TODO(ches) implement this
        return false;
    }

    public int getCommandListsCount() {
        // TODO(ches) implement this
        return 0;
    }

    public int getTotalIndexCount() {
        // TODO(ches) implement this
        return 0;
    }

    public int getTotalVertexCount() {
        // TODO(ches) implement this
        return 0;
    }

    public Vector2f getDisplayPosition() {
        final Vector2f value = new Vector2f();
        getDisplayPosition(value);
        return value;
    }

    public void getDisplayPosition(Vector2f output) {
        // TODO(ches) implement this
    }

    public float getDisplayPositionX() {
        // TODO(ches) implement this
        return 0;
    }

    public float getDisplayPositionY() {
        // TODO(ches) implement this
        return 0;
    }

    public Vector2f getDisplaySize() {
        final Vector2f value = new Vector2f();
        getDisplaySize(value);
        return value;
    }

    public void getDisplaySize(Vector2f output) {
        // TODO(ches) implement this
    }

    public float getDisplaySizeX() {
        // TODO(ches) implement this
        return 0;
    }

    public float getDisplaySizeY() {
        // TODO(ches) implement this
        return 0;
    }

    public Vector2f getFramebufferScale() {
        final Vector2f value = new Vector2f();
        getFramebufferScale(value);
        return value;
    }

    public void getFramebufferScale(Vector2f output) {
        // TODO(ches) implement this
    }

    public float getFramebufferScaleX() {
        // TODO(ches) implement this
        return 0;
    }

    public float getFramebufferScaleY() {
        // TODO(ches) implement this
        return 0;
    }

    public Viewport getOwnerViewport() {
        return null;
    }

    public void deIndexAllBuffers() {
        // TODO(ches) implement this
    }

    public void scaleClipRects(float framebufferScaleX, float framebufferScaleY) {
        // TODO(ches) implement this

    }
}
