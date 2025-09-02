package com.ikalagaming.graphics.frontend.gui.data;

import com.ikalagaming.graphics.frontend.gui.IkGui;
import com.ikalagaming.graphics.frontend.gui.flags.DrawFlags;
import com.ikalagaming.graphics.frontend.gui.util.Color;
import com.ikalagaming.graphics.frontend.gui.util.Rect;
import com.ikalagaming.util.IntArrayList;

import lombok.Getter;
import lombok.Setter;
import lombok.Synchronized;
import org.joml.Vector2f;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Deque;

public class DrawList {

    /** float posX, float posY, float u, float v, int color. */
    private ByteBuffer vertexBuffer;

    /** short index. */
    private ByteBuffer indexBuffer;

    /**
     * float clipMinX, float clipMinY, float clipMaxX, float clipMaxY, int textureID, int
     * vertexOffset, int indexOffset, int elementCount.
     */
    private ByteBuffer commandBuffer;

    private final Deque<Rect> clipRects;
    private final IntArrayList textures;

    /**
     * The current flags for the draw list.
     *
     * @see com.ikalagaming.graphics.frontend.gui.flags.DrawListFlags
     */
    @Getter @Setter private int drawListFlags;

    public DrawList() {
        vertexBuffer = ByteBuffer.allocateDirect(100 * DrawData.SIZE_OF_DRAW_VERTEX);
        indexBuffer = ByteBuffer.allocateDirect(100 * DrawData.SIZE_OF_DRAW_INDEX);
        commandBuffer = ByteBuffer.allocateDirect(100 * DrawData.SIZE_OF_DRAW_COMMAND);
        clipRects = new ArrayDeque<>();
        textures = new IntArrayList();
    }

    @Synchronized
    private void addCommand(
            float clipMinX,
            float clipMinY,
            float clipMaxX,
            float clipMaxY,
            int vertexOffset,
            int indexOffset,
            int elementCount) {
        if (commandBuffer.limit() == commandBuffer.position()) {
            ByteBuffer newBuffer = ByteBuffer.allocateDirect(commandBuffer.limit() * 2);
            commandBuffer.flip();
            newBuffer.put(commandBuffer);
            commandBuffer = newBuffer;
        }

        commandBuffer.putFloat(clipMinX);
        commandBuffer.putFloat(clipMinY);
        commandBuffer.putFloat(clipMaxX);
        commandBuffer.putFloat(clipMaxY);
        commandBuffer.putInt(textures.peek());
        commandBuffer.putInt(vertexOffset);
        commandBuffer.putInt(indexOffset);
        commandBuffer.putInt(elementCount);
    }

    /**
     * Add a draw list flag to the current flags.
     *
     * @param flags The flag to add.
     * @see com.ikalagaming.graphics.frontend.gui.flags.DrawListFlags
     */
    public void addDrawListFlags(int flags) {
        this.setDrawListFlags(this.getDrawListFlags() | flags);
    }

    /**
     * Remove a draw list flag from the current flags.
     *
     * @param flags The flag to remove.
     * @see com.ikalagaming.graphics.frontend.gui.flags.DrawListFlags
     */
    public void removeDrawListFlags(int flags) {
        this.setDrawListFlags(this.getDrawListFlags() & ~flags);
    }

    /**
     * Check if a particular flag is set.
     *
     * @param flags The flag to check.
     * @see com.ikalagaming.graphics.frontend.gui.flags.DrawListFlags
     */
    public boolean hasDrawListFlags(int flags) {
        return (this.getDrawListFlags() & flags) != 0;
    }

    public void pushClipRect(float minX, float minY, float maxX, float maxY) {
        pushClipRect(minX, minY, maxX, maxY, false);
    }

    public void pushClipRect(
            float minX, float minY, float maxX, float maxY, boolean intersectWithCurrentClipRect) {
        Rect newClipRect = new Rect(minX, minY, maxX, maxY);

        if (!intersectWithCurrentClipRect) {
            clipRects.addFirst(newClipRect);
            return;
        }

        Rect oldRect = clipRects.peekFirst();
        if (oldRect == null) {
            // No existing rect
            clipRects.addFirst(newClipRect);
            return;
        }

        float left = Math.max(newClipRect.getLeft(), oldRect.getLeft());
        float top = Math.min(newClipRect.getTop(), oldRect.getTop());
        float right = Math.min(newClipRect.getRight(), oldRect.getRight());
        float bottom = Math.max(newClipRect.getBottom(), oldRect.getBottom());

        if (left > right || bottom > top) {
            // No intersection
            clipRects.addFirst(newClipRect);
            return;
        }

        newClipRect.set(left, top, right, bottom);
        clipRects.addFirst(newClipRect);
    }

    public void pushClipRectFullScreen() {
        IkIO io = IkGui.getIO();
        Rect newRect = new Rect(0, 0, io.getDisplaySizeX(), io.getDisplaySizeY());
        clipRects.addFirst(newRect);
    }

    public void popClipRect() {
        clipRects.pop();
    }

    public void pushTextureId(int id) {
        textures.push(id);
    }

    public void popTextureId() {
        textures.pop();
    }

    public Vector2f getClipRectMin() {
        Vector2f value = new Vector2f();
        this.getClipRectMin(value);
        return value;
    }

    public void getClipRectMin(Vector2f output) {
        Rect clipRect = clipRects.peekFirst();
        if (clipRect == null) {
            output.set(0, 0);
            return;
        }
        output.set(clipRect.getLeft(), clipRect.getTop());
    }

    public float getClipRectMinX() {
        Rect clipRect = clipRects.peekFirst();
        if (clipRect == null) {
            return 0;
        }
        return clipRect.getLeft();
    }

    public float getClipRectMinY() {
        Rect clipRect = clipRects.peekFirst();
        if (clipRect == null) {
            return 0;
        }
        return clipRect.getTop();
    }

    public Vector2f getClipRectMax() {
        Vector2f value = new Vector2f();
        this.getClipRectMax(value);
        return value;
    }

    public void getClipRectMax(Vector2f output) {
        Rect clipRect = clipRects.peekFirst();
        if (clipRect == null) {
            output.set(0, 0);
            return;
        }
        output.set(clipRect.getRight(), clipRect.getBottom());
    }

    public float getClipRectMaxX() {
        Rect clipRect = clipRects.peekFirst();
        if (clipRect == null) {
            IkIO io = IkGui.getIO();
            return io.getDisplaySizeX();
        }
        return clipRect.getRight();
    }

    public float getClipRectMaxY() {
        Rect clipRect = clipRects.peekFirst();
        if (clipRect == null) {
            IkIO io = IkGui.getIO();
            return io.getDisplaySizeY();
        }
        return clipRect.getBottom();
    }

    public void addLine(float p1X, float p1Y, float p2X, float p2Y, int color) {
        addLine(p1X, p1Y, p2X, p2Y, color, 1.0f);
    }

    public void addLine(float p1X, float p1Y, float p2X, float p2Y, int color, float thickness) {
        // TODO(ches) implement this
    }

    public void addRect(float minX, float minY, float maxX, float maxY, int color) {
        addRect(minX, minY, maxX, maxY, color, 0.0f, DrawFlags.ROUND_CORNERS_ALL, 1.0f);
    }

    public void addRect(float minX, float minY, float maxX, float maxY, int color, float rounding) {
        addRect(minX, minY, maxX, maxY, color, rounding, DrawFlags.ROUND_CORNERS_ALL, 1.0f);
    }

    public void addRect(
            float minX,
            float minY,
            float maxX,
            float maxY,
            int color,
            float rounding,
            int drawFlagsRoundingCorners) {
        addRect(minX, minY, maxX, maxY, color, rounding, drawFlagsRoundingCorners, 1.0f);
    }

    public void addRect(
            float minX,
            float minY,
            float maxX,
            float maxY,
            int color,
            float rounding,
            int drawFlagsRoundingCorners,
            float thickness) {
        // TODO(ches) implement this
    }

    public void addRectFilled(float minX, float minY, float maxX, float maxY, int color) {
        addRectFilled(minX, minY, maxX, maxY, color, 0.0f, DrawFlags.ROUND_CORNERS_ALL);
    }

    public void addRectFilled(
            float minX, float minY, float maxX, float maxY, int color, float rounding) {
        addRectFilled(minX, minY, maxX, maxY, color, rounding, DrawFlags.ROUND_CORNERS_ALL);
    }

    public void addRectFilled(
            float minX,
            float minY,
            float maxX,
            float maxY,
            int color,
            float rounding,
            int drawFlagsRoundingCorners) {
        // TODO(ches) implement this
    }

    public void addRectFilledMultiColor(
            float minX,
            float minY,
            float maxX,
            float maxY,
            long colorUpperLeft,
            long colorUpperRight,
            long colorBottomRight,
            long colorBottomLeft) {
        // TODO(ches) implement this
    }

    public void addQuad(
            float p1X,
            float p1Y,
            float p2X,
            float p2Y,
            float p3X,
            float p3Y,
            float p4X,
            float p4Y,
            int color) {
        addQuad(p1X, p1Y, p2X, p2Y, p3X, p3Y, p4X, p4Y, color, 1.0f);
    }

    public void addQuad(
            float p1X,
            float p1Y,
            float p2X,
            float p2Y,
            float p3X,
            float p3Y,
            float p4X,
            float p4Y,
            int color,
            float thickness) {
        // TODO(ches) implement this
    }

    public void addQuadFilled(
            float p1X,
            float p1Y,
            float p2X,
            float p2Y,
            float p3X,
            float p3Y,
            float p4X,
            float p4Y,
            int color) {
        // TODO(ches) implement this
    }

    public void addTriangle(
            float p1X, float p1Y, float p2X, float p2Y, float p3X, float p3Y, int color) {
        addTriangle(p1X, p1Y, p2X, p2Y, p3X, p3Y, color, 1.0f);
    }

    public void addTriangle(
            float p1X,
            float p1Y,
            float p2X,
            float p2Y,
            float p3X,
            float p3Y,
            int color,
            float thickness) {
        // TODO(ches) implement this
    }

    public void addTriangleFilled(
            float p1X, float p1Y, float p2X, float p2Y, float p3X, float p3Y, int color) {
        // TODO(ches) implement this
    }

    public void addCircle(float centerX, float centerY, float radius, int color) {
        addCircle(centerX, centerY, radius, color, 0, 1.0f);
    }

    public void addCircle(float centerX, float centerY, float radius, int color, int segmentCount) {
        addCircle(centerX, centerY, radius, color, segmentCount, 1.0f);
    }

    public void addCircle(
            float centerX,
            float centerY,
            float radius,
            int color,
            int segmentCount,
            float thickness) {
        // TODO(ches) implement this
    }

    public void addCircleFilled(float centerX, float centerY, float radius, int color) {
        addCircleFilled(centerX, centerY, radius, color, 0);
    }

    public void addCircleFilled(
            float centerX, float centerY, float radius, int color, int segmentCount) {
        // TODO(ches) implement this
    }

    public void addNgon(float centerX, float centerY, float radius, int color, int segmentCount) {
        addNgon(centerX, centerY, radius, color, segmentCount, 1.0f);
    }

    public void addNgon(
            float centerX,
            float centerY,
            float radius,
            int color,
            int segmentCount,
            float thickness) {
        // TODO(ches) implement this
    }

    public void addNgonFilled(
            float centerX, float centerY, float radius, int color, int segmentCount) {
        // TODO(ches) implement this
    }

    public void addText(float posX, float posY, int color, String text) {
        // TODO(ches) implement this
    }

    public void addText(Font Font, float fontSize, float posX, float posY, int color, String text) {
        // TODO(ches) implement this
    }

    public void addText(
            Font Font,
            float fontSize,
            float posX,
            float posY,
            int color,
            String text,
            float wrapWidth) {
        // TODO(ches) implement this
    }

    public void addText(
            Font Font,
            float fontSize,
            float posX,
            float posY,
            int color,
            String text,
            float wrapWidth,
            float cpuFineClipRectX,
            float cpuFineClipRectY,
            float cpuFineClipRectZ,
            float cpuFineClipRectV) {
        // TODO(ches) implement this
    }

    public void addPolyline(
            Vector2f[] points, int pointCount, int color, boolean closed, float thickness) {
        // TODO(ches) implement this
    }

    public void addConvexPolyFilled(Vector2f[] points, int pointCount, int color) {
        // TODO(ches) implement this
    }

    public void addBezierCubic(
            float p1X,
            float p1Y,
            float p2X,
            float p2Y,
            float p3X,
            float p3Y,
            float p4X,
            float p4Y,
            int color,
            float thickness) {
        addBezierCubic(p1X, p1Y, p2X, p2Y, p3X, p3Y, p4X, p4Y, color, thickness, 0);
    }

    public void addBezierCubic(
            float p1X,
            float p1Y,
            float p2X,
            float p2Y,
            float p3X,
            float p3Y,
            float p4X,
            float p4Y,
            int color,
            float thickness,
            int segmentCount) {
        // TODO(ches) implement this
    }

    public void addBezierQuadratic(
            float p1X,
            float p1Y,
            float p2X,
            float p2Y,
            float p3X,
            float p3Y,
            int color,
            float thickness) {
        addBezierQuadratic(p1X, p1Y, p2X, p2Y, p3X, p3Y, color, thickness, 0);
    }

    public void addBezierQuadratic(
            float p1X,
            float p1Y,
            float p2X,
            float p2Y,
            float p3X,
            float p3Y,
            int color,
            float thickness,
            int segmentCount) {
        // TODO(ches) implement this
    }

    public void addImage(int textureID, float minX, float minY, float maxX, float maxY) {
        addImage(textureID, minX, minY, maxX, maxY, 0, 0, 1, 1, Color.WHITE);
    }

    public void addImage(
            int textureID, float minX, float minY, float maxX, float maxY, float minU, float minV) {
        addImage(textureID, minX, minY, maxX, maxY, minU, minV, 1, 1, Color.WHITE);
    }

    public void addImage(
            int textureID,
            float minX,
            float minY,
            float maxX,
            float maxY,
            float minU,
            float minV,
            float maxU,
            float maxV) {
        addImage(textureID, minX, minY, maxX, maxY, minU, minV, maxU, maxV, Color.WHITE);
    }

    public void addImage(
            int textureID,
            float minX,
            float minY,
            float maxX,
            float maxY,
            float minU,
            float minV,
            float maxU,
            float maxV,
            int color) {
        // TODO(ches) implement this
    }

    public void addImageQuad(
            int textureID,
            float p1X,
            float p1Y,
            float p2X,
            float p2Y,
            float p3X,
            float p3Y,
            float p4X,
            float p4Y) {
        addImageQuad(
                textureID,
                p1X,
                p1Y,
                p2X,
                p2Y,
                p3X,
                p3Y,
                p4X,
                p4Y,
                0,
                0,
                1,
                0,
                1,
                1,
                0,
                1,
                Color.WHITE);
    }

    public void addImageQuad(
            int textureID,
            float p1X,
            float p1Y,
            float p2X,
            float p2Y,
            float p3X,
            float p3Y,
            float p4X,
            float p4Y,
            float u1,
            float v1) {
        addImageQuad(
                textureID,
                p1X,
                p1Y,
                p2X,
                p2Y,
                p3X,
                p3Y,
                p4X,
                p4Y,
                u1,
                v1,
                1,
                0,
                1,
                1,
                0,
                1,
                Color.WHITE);
    }

    public void addImageQuad(
            int textureID,
            float p1X,
            float p1Y,
            float p2X,
            float p2Y,
            float p3X,
            float p3Y,
            float p4X,
            float p4Y,
            float u1,
            float v1,
            float u2,
            float v2) {
        addImageQuad(
                textureID,
                p1X,
                p1Y,
                p2X,
                p2Y,
                p3X,
                p3Y,
                p4X,
                p4Y,
                u1,
                v1,
                u2,
                v2,
                1,
                1,
                0,
                1,
                Color.WHITE);
    }

    public void addImageQuad(
            int textureID,
            float p1X,
            float p1Y,
            float p2X,
            float p2Y,
            float p3X,
            float p3Y,
            float p4X,
            float p4Y,
            float u1,
            float v1,
            float u2,
            float v2,
            float u3,
            float v3) {
        addImageQuad(
                textureID,
                p1X,
                p1Y,
                p2X,
                p2Y,
                p3X,
                p3Y,
                p4X,
                p4Y,
                u1,
                v1,
                u2,
                v2,
                u3,
                v3,
                0,
                1,
                Color.WHITE);
    }

    public void addImageQuad(
            int textureID,
            float p1X,
            float p1Y,
            float p2X,
            float p2Y,
            float p3X,
            float p3Y,
            float p4X,
            float p4Y,
            float u1,
            float v1,
            float u2,
            float v2,
            float u3,
            float v3,
            float u4,
            float v4) {
        addImageQuad(
                textureID,
                p1X,
                p1Y,
                p2X,
                p2Y,
                p3X,
                p3Y,
                p4X,
                p4Y,
                u1,
                v1,
                u2,
                v2,
                u3,
                v3,
                u4,
                v4,
                Color.WHITE);
    }

    public void addImageQuad(
            int textureID,
            float p1X,
            float p1Y,
            float p2X,
            float p2Y,
            float p3X,
            float p3Y,
            float p4X,
            float p4Y,
            float u1,
            float v1,
            float u2,
            float v2,
            float u3,
            float v3,
            float u4,
            float v4,
            int color) {
        // TODO(ches) implement this
        // TODO(ches) wow this function signature sucks, can we do this using static cached default
        // vecs?
    }

    public void addImageRounded(
            int textureID,
            float minX,
            float minY,
            float maxX,
            float maxY,
            float minU,
            float minV,
            float maxU,
            float maxV,
            int color,
            float rounding) {
        addImageRounded(
                textureID,
                minX,
                minY,
                maxX,
                maxY,
                minU,
                minV,
                maxU,
                maxV,
                color,
                rounding,
                DrawFlags.ROUND_CORNERS_ALL);
    }

    public void addImageRounded(
            int textureID,
            float minX,
            float minY,
            float maxX,
            float maxY,
            float minU,
            float minV,
            float maxU,
            float maxV,
            int color,
            float rounding,
            int drawFlagsRoundingCorners) {
        // TODO(ches) implement this
    }

    public void pathClear() {
        // TODO(ches) implement this
    }

    public void pathLineTo(float posX, float posY) {
        // TODO(ches) implement this
    }

    public void pathLineToMergeDuplicate(float posX, float posY) {
        // TODO(ches) implement this
    }

    public void pathFillConvex(int color) {
        // TODO(ches) implement this
    }

    public void pathStroke(int color, boolean closed) {
        pathStroke(color, closed, 1.0f);
    }

    public void pathStroke(int color, boolean closed, float thickness) {
        // TODO(ches) implement this
    }

    public void pathArcTo(float centerX, float centerY, float radius, float minA, float maxA) {
        pathArcTo(centerX, centerY, radius, minA, maxA, 10);
    }

    public void pathArcTo(
            float centerX, float centerY, float radius, float minA, float maxA, int segmentCount) {
        // TODO(ches) implement this
    }

    public void pathArcToFast(
            float centerX, float centerY, float radius, int minAMult12, int maxAMult12) {
        // TODO(ches) implement this
    }

    public void pathBezierCubicCurveTo(
            float p2X, float p2Y, float p3X, float p3Y, float p4X, float p4Y) {
        pathBezierCubicCurveTo(p2X, p2Y, p3X, p3Y, p4X, p4Y, 0);
    }

    public void pathBezierCubicCurveTo(
            float p2X, float p2Y, float p3X, float p3Y, float p4X, float p4Y, int segmentCount) {
        // TODO(ches) implement this
    }

    public void pathBezierQuadraticCurveTo(float p2X, float p2Y, float p3X, float p3Y) {
        pathBezierQuadraticCurveTo(p2X, p2Y, p3X, p3Y, 0);
    }

    public void pathBezierQuadraticCurveTo(
            float p2X, float p2Y, float p3X, float p3Y, int segmentCount) {
        // TODO(ches) implement this
    }

    public void pathRect(float minX, float minY, float maxX, float maxY) {
        pathRect(minX, minY, maxX, maxY, 0.0f, DrawFlags.ROUND_CORNERS_ALL);
    }

    public void pathRect(float minX, float minY, float maxX, float maxY, float rounding) {
        pathRect(minX, minY, maxX, maxY, rounding, DrawFlags.ROUND_CORNERS_ALL);
    }

    public void pathRect(
            float minX,
            float minY,
            float maxX,
            float maxY,
            float rounding,
            int drawFlagsRoundingCorners) {
        // TODO(ches) implement this
    }

    public void primReserve(int indexCount, int vertexCount) {
        // TODO(ches) implement this
    }

    public void primUnreserve(int indexCount, int vertexCount) {
        // TODO(ches) implement this
    }

    public void primRect(float aX, float aY, float bX, float bY, int color) {
        // TODO(ches) implement this
    }

    public void primRectUV(
            float aX,
            float aY,
            float bX,
            float bY,
            float uA,
            float vA,
            float uB,
            float vB,
            int color) {
        // TODO(ches) implement this
    }

    public void primQuadUV(
            float aX,
            float aY,
            float bX,
            float bY,
            float cX,
            float cY,
            float dX,
            float dY,
            float uA,
            float vA,
            float uB,
            float vB,
            float uC,
            float vC,
            float uD,
            float vD,
            int color) {
        // TODO(ches) implement this
    }

    public void primWriteVtx(float posX, float posY, float u, float v, int color) {
        // TODO(ches) implement this
    }

    public void primVtx(float posX, float posY, float u, float v, int color) {
        // TODO(ches) implement this
    }
}
