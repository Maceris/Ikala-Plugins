package com.ikalagaming.graphics.frontend.gui.data;

import static com.ikalagaming.graphics.frontend.gui.flags.DrawFlags.*;

import com.ikalagaming.graphics.GraphicsPlugin;
import com.ikalagaming.graphics.frontend.gui.IkGui;
import com.ikalagaming.graphics.frontend.gui.flags.DrawFlags;
import com.ikalagaming.graphics.frontend.gui.util.Color;
import com.ikalagaming.graphics.frontend.gui.util.Rect;
import com.ikalagaming.util.IntArrayList;
import com.ikalagaming.util.SafeResourceLoader;

import lombok.Getter;
import lombok.Setter;
import lombok.Synchronized;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector2f;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Deque;

@Slf4j
public class DrawList {

    /** float posX, float posY, float u, float v, int color. */
    ByteBuffer vertexBuffer;

    /** short index. */
    ByteBuffer indexBuffer;

    /**
     * float clipMinX, float clipMinY, float clipMaxX, float clipMaxY, int textureID, int
     * vertexOffset, int indexOffset, int elementCount.
     */
    ByteBuffer commandBuffer;

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

    /** Clear out everything in the draw list. */
    public void clear() {
        vertexBuffer.clear();
        indexBuffer.clear();
        commandBuffer.clear();
        clipRects.clear();
        textures.clear();
    }

    @Synchronized
    private void addCommand(
            float clipMinX,
            float clipMinY,
            float clipMaxX,
            float clipMaxY,
            int texture,
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
        commandBuffer.putInt(texture);
        commandBuffer.putInt(vertexOffset);
        commandBuffer.putInt(indexOffset);
        commandBuffer.putInt(elementCount);
    }

    @Synchronized
    private int addVertex(float x, float y, float u, float v, int color) {
        if (vertexBuffer.limit() == vertexBuffer.position()) {
            ByteBuffer newBuffer = ByteBuffer.allocateDirect(vertexBuffer.limit() * 2);
            vertexBuffer.flip();
            newBuffer.put(vertexBuffer);
            vertexBuffer = newBuffer;
        }

        final int newIndex = vertexBuffer.position() / DrawData.SIZE_OF_DRAW_VERTEX;

        vertexBuffer.putFloat(x);
        vertexBuffer.putFloat(y);
        vertexBuffer.putFloat(u);
        vertexBuffer.putFloat(v);
        vertexBuffer.putInt(color);

        return newIndex;
    }

    @Synchronized
    private int addIndex(short index) {
        if (indexBuffer.limit() == indexBuffer.position()) {
            ByteBuffer newBuffer = ByteBuffer.allocateDirect(indexBuffer.limit() * 2);
            indexBuffer.flip();
            newBuffer.put(indexBuffer);
            indexBuffer = newBuffer;
        }

        final int newIndex = indexBuffer.position() / DrawData.SIZE_OF_DRAW_INDEX;

        indexBuffer.putShort(index);

        return newIndex;
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
        if (!Float.isFinite(thickness) || thickness <= 0) {
            log.warn(
                    SafeResourceLoader.getString(
                            "INVALID_THICKNESS", GraphicsPlugin.getResourceBundle()),
                    thickness);
            return;
        }

        final float dx = p2X - p1X;
        final float dy = p2Y - p1Y;

        float tangentOffsetX = 0;
        float tangentOffsetY = 0;

        if (0 == dx && 0 == dy) {
            // point
            tangentOffsetX = thickness / 2;
            tangentOffsetY = thickness / 2;
        } else if (0 == dx) {
            // vertical
            tangentOffsetY = thickness / 2;
        } else if (0 == dy) {
            // horizontal
            tangentOffsetX = thickness / 2;
        } else {
            // sloped
            double angle = Math.atan((double) dx / dy);
            tangentOffsetX = (float) Math.cos(angle) * (thickness / 2);
            tangentOffsetY = (float) Math.sin(angle) * (thickness / 2);
        }

        int p1 = addVertex(p1X + tangentOffsetX, p1Y + tangentOffsetY, 0, 0, color);
        int p2 = addVertex(p1X - tangentOffsetX, p1Y - tangentOffsetY, 0, 0, color);
        int p3 = addVertex(p2X + tangentOffsetX, p2Y + tangentOffsetY, 0, 0, color);
        int p4 = addVertex(p2X - tangentOffsetX, p2Y - tangentOffsetY, 0, 0, color);

        int startIndex = addIndex((short) p1);
        addIndex((short) p2);
        addIndex((short) p3);

        addIndex((short) p3);
        addIndex((short) p2);
        addIndex((short) p4);

        Vector2f clipRectMin = getClipRectMin();
        Vector2f clipRectMax = getClipRectMax();

        final int vertexOffset = 0;
        final int elementCount = 6;
        addCommand(
                clipRectMin.x,
                clipRectMin.y,
                clipRectMax.x,
                clipRectMax.y,
                textures.peek(),
                vertexOffset,
                startIndex,
                elementCount);

        // TODO(ches) check this works
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

    /**
     * Add a (empty) rectangle.
     *
     * @param minX Minimum X coordinate.
     * @param minY Minimum Y coordinate.
     * @param maxX Maximum X coordinate.
     * @param maxY Maximum Y coordinate.
     * @param color Color of the line.
     * @param rounding Radius of the rounded corners, 0 indicates no rounding.
     * @param drawFlagsRoundingCorners Draw flags indicating which corner(s) to round.
     * @param thickness The thickness of the lines.
     * @see DrawFlags
     */
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

        if (rounding < 0) {
            log.warn(
                    SafeResourceLoader.getString(
                            "INVALID_ROUNDING", GraphicsPlugin.getResourceBundle()),
                    rounding);
            return;
        }

        final float topLeftRadius =
                (drawFlagsRoundingCorners & ROUND_CORNERS_TOP_LEFT) != 0 ? rounding : 0;
        final float topRightRadius =
                (drawFlagsRoundingCorners & ROUND_CORNERS_TOP_RIGHT) != 0 ? rounding : 0;
        final float bottomLeftRadius =
                (drawFlagsRoundingCorners & ROUND_CORNERS_BOTTOM_LEFT) != 0 ? rounding : 0;
        final float bottomRightRadius =
                (drawFlagsRoundingCorners & ROUND_CORNERS_BOTTOM_RIGHT) != 0 ? rounding : 0;

        // Top
        addLine(minX + topLeftRadius, minY, maxX - topRightRadius, minY, color, thickness);
        // Right
        addLine(maxX, minY + topRightRadius, maxX, maxY - bottomRightRadius, color, thickness);
        // Bottom
        addLine(minX + bottomLeftRadius, maxY, maxX - bottomRightRadius, maxY, color, thickness);
        // Left
        addLine(minX, minY + topLeftRadius, minX, maxY - bottomLeftRadius, color, thickness);

        if (topLeftRadius > 0) {}

        if (topRightRadius > 0) {}

        if (bottomLeftRadius > 0) {}

        if (bottomRightRadius > 0) {}
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
        addLine(p1X, p1Y, p2X, p2Y, color, thickness);
        addLine(p2X, p2Y, p3X, p3Y, color, thickness);
        addLine(p3X, p3Y, p4X, p4Y, color, thickness);
        addLine(p4X, p4Y, p1X, p1Y, color, thickness);
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
        int p1 = addVertex(p1X, p1Y, 0, 0, color);
        int p2 = addVertex(p2X, p2Y, 0, 0, color);
        int p3 = addVertex(p3X, p3Y, 0, 0, color);
        int p4 = addVertex(p4X, p4Y, 0, 0, color);

        int startIndex = addIndex((short) p1);
        addIndex((short) p2);
        addIndex((short) p3);

        addIndex((short) p3);
        addIndex((short) p2);
        addIndex((short) p4);

        Vector2f clipRectMin = getClipRectMin();
        Vector2f clipRectMax = getClipRectMax();

        final int vertexOffset = 0;
        final int elementCount = 6;
        addCommand(
                clipRectMin.x,
                clipRectMin.y,
                clipRectMax.x,
                clipRectMax.y,
                textures.peek(),
                vertexOffset,
                startIndex,
                elementCount);
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
        int p1 = addVertex(p1X, p1Y, 0, 0, color);
        int p2 = addVertex(p2X, p2Y, 0, 0, color);
        int p3 = addVertex(p3X, p3Y, 0, 0, color);

        int startIndex = addIndex((short) p1);
        addIndex((short) p2);
        addIndex((short) p3);

        Vector2f clipRectMin = getClipRectMin();
        Vector2f clipRectMax = getClipRectMax();

        final int vertexOffset = 0;
        final int elementCount = 3;
        addCommand(
                clipRectMin.x,
                clipRectMin.y,
                clipRectMax.x,
                clipRectMax.y,
                textures.peek(),
                vertexOffset,
                startIndex,
                elementCount);
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
        // TODO(ches) wow this function signature sucks, can we do this cheaply with vecs?

        int p1 = addVertex(p1X, p1Y, u1, v1, color);
        int p2 = addVertex(p2X, p2Y, u2, v2, color);
        int p3 = addVertex(p3X, p3Y, u3, v3, color);
        int p4 = addVertex(p4X, p4Y, u4, v4, color);

        int startIndex = addIndex((short) p1);
        addIndex((short) p2);
        addIndex((short) p3);

        addIndex((short) p3);
        addIndex((short) p2);
        addIndex((short) p4);

        Vector2f clipRectMin = getClipRectMin();
        Vector2f clipRectMax = getClipRectMax();

        final int vertexOffset = 0;
        final int elementCount = 6;
        addCommand(
                clipRectMin.x,
                clipRectMin.y,
                clipRectMax.x,
                clipRectMax.y,
                textureID,
                vertexOffset,
                startIndex,
                elementCount);
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
