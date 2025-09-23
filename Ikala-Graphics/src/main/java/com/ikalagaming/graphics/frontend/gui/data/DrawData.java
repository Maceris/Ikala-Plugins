package com.ikalagaming.graphics.frontend.gui.data;

import org.joml.Vector2f;
import org.joml.Vector4f;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;

public class DrawData {

    public static final int SIZE_OF_DRAW_INDEX = Short.BYTES;
    public static final int SIZE_OF_DRAW_VERTEX = 4 * Float.BYTES + Integer.BYTES;
    public static final int SIZE_OF_DRAW_COMMAND = 4 * Float.BYTES + 4 * Integer.BYTES;

    private static final int RESIZE_FACTOR = 5000;

    private static ByteBuffer dataBuffer =
            ByteBuffer.allocateDirect(25_000).order(ByteOrder.nativeOrder());

    private static Viewport OWNER_VIEWPORT = null;

    final ArrayList<DrawList> drawLists;

    public DrawData() {
        drawLists = new ArrayList<>();
    }

    public int getCommandListCommandBufferSize(int commandListIndex) {
        // TODO(ches) implement this
        return 0;
    }

    public int getCommandListCommandBufferElementCount(
            int commandListIndex, int commandBufferIndex) {
        // TODO(ches) implement this
        return 0;
    }

    public Vector4f getCommandListCommandBufferClipRect(
            final int commandListIndex, final int commandBufferIndex) {
        final Vector4f value = new Vector4f();
        getCommandListCommandBufferClipRect(commandListIndex, commandBufferIndex, value);
        return value;
    }

    public void getCommandListCommandBufferClipRect(
            int commandListIndex, int commandBufferIndex, Vector4f output) {
        // TODO(ches) implement this
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
