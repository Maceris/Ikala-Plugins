package com.ikalagaming.graphics.frontend.gui.data;

import static com.ikalagaming.graphics.frontend.gui.flags.DrawFlags.*;

import com.ikalagaming.graphics.frontend.gui.IkGui;
import com.ikalagaming.graphics.frontend.gui.flags.DrawFlags;
import com.ikalagaming.graphics.frontend.gui.util.Color;
import com.ikalagaming.graphics.frontend.gui.util.Rect;
import com.ikalagaming.util.IntArrayList;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector2f;
import org.joml.Vector4i;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.Deque;

@Slf4j
public class DrawList {

    @Getter
    @AllArgsConstructor
    public enum ElementType {
        CIRCLE(0),
        LINE_ARC(1),
        LINE_BEZIER(2),
        LINE_STRAIGHT(3),
        RECTANGLE(4),
        POLYGON(5),
        TEXT(6);

        /** Unique ID used in the command buffer. Must line up with the shader. */
        final int typeID;

        public static ElementType fromID(int typeID) throws IllegalArgumentException {
            for (ElementType type : ElementType.values()) {
                if (type.typeID == typeID) {
                    return type;
                }
            }
            throw new IllegalArgumentException("Unknown Element Type ID");
        }
    }

    @Getter
    @AllArgsConstructor
    public enum ElementStyle {
        FILL(0),
        BORDER(1),
        TEXTURE(2);

        /** Unique ID used in the command buffer. Must line up with the shader. */
        final int styleID;

        public static ElementStyle fromID(int styleID) throws IllegalArgumentException {
            for (ElementStyle type : ElementStyle.values()) {
                if (type.styleID == styleID) {
                    return type;
                }
            }
            throw new IllegalArgumentException("Unknown Element Style ID");
        }
    }

    private record SDFPointDetail(float radius, int colorOrTextureID, int tint) {}

    public final String windowName;

    /**
     * The vertices making up the quad that each element (command) will be drawn on, with each quad
     * being a pair of triangles. This is the region that is needed to draw the element, clipped as
     * appropriate.
     */
    public ByteBuffer vertexBuffer;

    // TODO(ches) figure out line arcs
    // TODO(ches) figure out line bezier
    // TODO(ches) figure out text
    /**
     * float posX, float posY, float a, float b. A and B are additional values that depend on the
     * type of point.
     *
     * <ul>
     *   <li>Circle - a = width, b = height
     *   <li>Line (arc) - ???
     *   <li>Line (bezier) - ???
     *   <li>Line (straight) - a = point 2 x, b = point 2 y
     *   <li>Rectangle - a = width, b = height
     *   <li>Polygon - a = u, b = v, for textures. Otherwise ignored.
     *   <li>Text - a = width, b = height
     * </ul>
     */
    public ByteBuffer pointBuffer;

    /**
     * float radius (for rounding), int colorOrTextureID, int tint. Stored generally starting on the
     * top-left, and always ordered clockwise for polygons and in-order for paths.
     */
    public ByteBuffer pointDetailBuffer;

    /**
     * int pointIndex, int detailIndex, int pointCount, int detailCount, int type, int style, float
     * stroke (borders, line thickness).
     */
    public ByteBuffer commandBuffer;

    private final Deque<Rect> clipRects;
    private final IntArrayList textures;

    /**
     * The current flags for the draw list.
     *
     * @see com.ikalagaming.graphics.frontend.gui.flags.DrawListFlags
     */
    @Getter @Setter private int drawListFlags;

    private boolean pathStarted;
    private int pathColor;
    private boolean pathClosed;
    private float pathThickness;

    /**
     * Set up point details for four corners of a rectangle.
     *
     * @param textureID The texture ID or color to use.
     * @param rounding The rounding radius.
     * @param drawFlagsRoundingCorners Draw flags for which corners need rounding.
     * @param tint Tint, for tinting textures.
     * @return The (four) point details for each of the corners (top left, top right, bottom right,
     *     then bottom left).
     */
    private static SDFPointDetail[] getSdfPointDetails(
            int textureID, float rounding, int drawFlagsRoundingCorners, int tint) {
        final float topLeftRadius =
                (drawFlagsRoundingCorners & ROUND_CORNERS_TOP_LEFT) != 0 ? rounding : 0;
        final float topRightRadius =
                (drawFlagsRoundingCorners & ROUND_CORNERS_TOP_RIGHT) != 0 ? rounding : 0;
        final float bottomLeftRadius =
                (drawFlagsRoundingCorners & ROUND_CORNERS_BOTTOM_LEFT) != 0 ? rounding : 0;
        final float bottomRightRadius =
                (drawFlagsRoundingCorners & ROUND_CORNERS_BOTTOM_RIGHT) != 0 ? rounding : 0;

        return new SDFPointDetail[] {
            new SDFPointDetail(topLeftRadius, textureID, tint),
            new SDFPointDetail(topRightRadius, textureID, tint),
            new SDFPointDetail(bottomRightRadius, textureID, tint),
            new SDFPointDetail(bottomLeftRadius, textureID, tint),
        };
    }

    public DrawList(@NonNull String windowName) {
        this.windowName = windowName;
        pointBuffer =
                ByteBuffer.allocateDirect(100 * DrawData.SIZE_OF_POINT)
                        .order(ByteOrder.nativeOrder());
        pointDetailBuffer =
                ByteBuffer.allocateDirect(100 * DrawData.SIZE_OF_POINT_DETAIL)
                        .order(ByteOrder.nativeOrder());
        commandBuffer =
                ByteBuffer.allocateDirect(100 * DrawData.SIZE_OF_DRAW_COMMAND)
                        .order(ByteOrder.nativeOrder());
        vertexBuffer =
                ByteBuffer.allocateDirect(100 * DrawData.SIZE_OF_QUAD_VERTICES)
                        .order(ByteOrder.nativeOrder());
        clipRects = new ArrayDeque<>();
        textures = new IntArrayList();
        pathStarted = false;
        pathColor = Color.CLEAR;
        pathClosed = false;
        pathThickness = 0;
    }

    /** Clear out everything in the draw list. */
    public void clear() {
        pointBuffer.clear();
        pointDetailBuffer.clear();
        commandBuffer.clear();
        vertexBuffer.clear();
        clipRects.clear();
        textures.clear();
        pathStarted = false;
        pathColor = Color.CLEAR;
        pathClosed = false;
        pathThickness = 0;
    }

    /** Set up all the buffers for reading once we are done with a frame. */
    public void prepareForRender() {
        pointBuffer.flip();
        pointDetailBuffer.flip();
        commandBuffer.flip();
        vertexBuffer.flip();
    }

    /**
     * Add a quad that fully contains the entire feature, once we know it's done. Every thing that
     * gets drawn must have a quad, and it should be large enough for any kind of border/blur/etc.
     * around it. The current clip rect will be accounted for inside this method.
     *
     * @param minX The minimum X coordinate.
     * @param minY The minimum Y coordinate.
     * @param maxX The maximum X coordinate.
     * @param maxY The maximum Y coordinate.
     * @return If we actually added the quad.
     */
    @Synchronized
    private boolean addScreenQuad(float minX, float minY, float maxX, float maxY) {
        float actualMinX = minX;
        float actualMinY = minY;
        float actualMaxX = maxX;
        float actualMaxY = maxY;

        Rect clipRect = clipRects.peekFirst();
        if (clipRect != null) {
            actualMinX = Math.max(minX, clipRect.getLeft());
            actualMinY = Math.max(minY, clipRect.getTop());
            actualMaxX = Math.min(maxX, clipRect.getRight());
            actualMaxY = Math.min(maxY, clipRect.getBottom());
        }
        if (actualMinX > actualMaxX || actualMinY > actualMaxY) {
            return false;
        }

        if (vertexBuffer.position() + DrawData.SIZE_OF_QUAD_VERTICES >= vertexBuffer.limit()) {
            ByteBuffer newBuffer =
                    ByteBuffer.allocateDirect(vertexBuffer.limit() * 2)
                            .order(ByteOrder.nativeOrder());
            vertexBuffer.flip();
            newBuffer.put(vertexBuffer);
            vertexBuffer = newBuffer;
        }

        // Top left
        vertexBuffer.putFloat(actualMinX);
        vertexBuffer.putFloat(actualMinY);
        // Bottom Left
        vertexBuffer.putFloat(actualMinX);
        vertexBuffer.putFloat(actualMaxY);
        // Bottom Right
        vertexBuffer.putFloat(actualMaxX);
        vertexBuffer.putFloat(actualMaxY);

        // Bottom Right
        vertexBuffer.putFloat(actualMaxX);
        vertexBuffer.putFloat(actualMaxY);
        // Top Right
        vertexBuffer.putFloat(actualMaxX);
        vertexBuffer.putFloat(actualMinY);
        // Top left
        vertexBuffer.putFloat(actualMinX);
        vertexBuffer.putFloat(actualMinY);
        return true;
    }

    @Synchronized
    private int addPoint(float posX, float posY, float a, float b) {
        if (pointBuffer.position() + DrawData.SIZE_OF_POINT >= pointBuffer.limit()) {
            ByteBuffer newBuffer =
                    ByteBuffer.allocateDirect(pointBuffer.limit() * 2)
                            .order(ByteOrder.nativeOrder());
            pointBuffer.flip();
            newBuffer.put(pointBuffer);
            pointBuffer = newBuffer;
        }

        final int newIndex = pointBuffer.position();

        pointBuffer.putFloat(posX);
        pointBuffer.putFloat(posY);
        pointBuffer.putFloat(a);
        pointBuffer.putFloat(b);

        return newIndex / DrawData.SIZE_OF_POINT;
    }

    @Synchronized
    private int addDetails(@NonNull SDFPointDetail... details) {
        final int newDetailsSize = details.length * DrawData.SIZE_OF_POINT_DETAIL;

        if (pointDetailBuffer.position() + newDetailsSize >= pointDetailBuffer.limit()) {
            ByteBuffer newBuffer =
                    ByteBuffer.allocateDirect(pointDetailBuffer.limit() * 2)
                            .order(ByteOrder.nativeOrder());
            pointDetailBuffer.flip();
            newBuffer.put(pointDetailBuffer);
            pointDetailBuffer = newBuffer;
        }

        final int newDetailIndex = pointDetailBuffer.position();

        for (SDFPointDetail detail : details) {
            pointDetailBuffer.putFloat(detail.radius());
            pointDetailBuffer.putInt(detail.colorOrTextureID());
            pointDetailBuffer.putInt(detail.tint());
        }

        return newDetailIndex / DrawData.SIZE_OF_POINT_DETAIL;
    }

    @Synchronized
    private void addCommand(
            int pointIndex,
            int detailIndex,
            int pointCount,
            int detailCount,
            @NonNull ElementType type,
            @NonNull ElementStyle style,
            float stroke) {

        if (commandBuffer.position() + DrawData.SIZE_OF_DRAW_COMMAND >= commandBuffer.limit()) {
            ByteBuffer newBuffer =
                    ByteBuffer.allocateDirect(commandBuffer.limit() * 2)
                            .order(ByteOrder.nativeOrder());
            commandBuffer.flip();
            newBuffer.put(commandBuffer);
            commandBuffer = newBuffer;
        }

        commandBuffer.putInt(pointIndex);
        commandBuffer.putInt(detailIndex);
        commandBuffer.putInt(pointCount);
        commandBuffer.putInt(detailCount);
        commandBuffer.putInt(type.typeID);
        commandBuffer.putInt(style.styleID);
        commandBuffer.putFloat(stroke);
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
        float top = Math.max(newClipRect.getTop(), oldRect.getTop());
        float right = Math.min(newClipRect.getRight(), oldRect.getRight());
        float bottom = Math.min(newClipRect.getBottom(), oldRect.getBottom());

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
        Rect newRect = new Rect(0, 0, io.displaySize.x, io.displaySize.y);
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
            return io.displaySize.x;
        }
        return clipRect.getRight();
    }

    public float getClipRectMaxY() {
        Rect clipRect = clipRects.peekFirst();
        if (clipRect == null) {
            IkIO io = IkGui.getIO();
            return io.displaySize.y;
        }
        return clipRect.getBottom();
    }

    public void addLine(float p1X, float p1Y, float p2X, float p2Y, int color) {
        addLine(p1X, p1Y, p2X, p2Y, color, 1.0f);
    }

    public void addLine(float p1X, float p1Y, float p2X, float p2Y, int color, float thickness) {
        if (!Float.isFinite(thickness) || thickness <= 0) {
            log.warn("Invalid thickness {}", thickness);
            return;
        }

        SDFPointDetail[] details = {
            new SDFPointDetail(thickness, color, Color.CLEAR),
            new SDFPointDetail(thickness, color, Color.CLEAR),
        };

        // TODO(ches) add quad
        // TODO(ches) Skip adding any other data if we didn't need to add the quad

        int pointIndex = addPoint(p1X, p1Y, p2X, p2Y);
        int detailIndex = addDetails(details);
        final int pointCount = 1;
        final int detailCount = 2;
        addCommand(
                pointIndex,
                detailIndex,
                pointCount,
                detailCount,
                ElementType.LINE_STRAIGHT,
                ElementStyle.FILL,
                thickness);
    }

    public void addRect(float minX, float minY, float maxX, float maxY, int color) {
        addRect(minX, minY, maxX, maxY, color, 0.0f, DrawFlags.ROUND_CORNERS_ALL, 1);
    }

    public void addRect(float minX, float minY, float maxX, float maxY, int color, float rounding) {
        addRect(minX, minY, maxX, maxY, color, rounding, DrawFlags.ROUND_CORNERS_ALL, 1);
    }

    public void addRect(
            float minX,
            float minY,
            float maxX,
            float maxY,
            int color,
            float rounding,
            int drawFlagsRoundingCorners) {
        addRect(minX, minY, maxX, maxY, color, rounding, drawFlagsRoundingCorners, 1);
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
     * @param thickness The thickness of the lines, in pixels.
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
            int thickness) {

        if (rounding < 0) {
            log.warn("Invalid rounding {} in addRect", rounding);
            return;
        }
        if (!addScreenQuad(
                minX - thickness, minY - thickness, maxX + thickness, maxY + thickness)) {
            return;
        }

        final float centerX = (minX + maxX) / 2;
        final float centerY = (minY + maxY) / 2;
        final float width = maxX - minX;
        final float height = maxY - minY;

        SDFPointDetail[] details =
                getSdfPointDetails(color, rounding, drawFlagsRoundingCorners, Color.CLEAR);

        int pointIndex = addPoint(centerX, centerY, width, height);
        int detailIndex = addDetails(details);
        final int pointCount = 1;
        final int detailCount = details.length;

        addCommand(
                pointIndex,
                detailIndex,
                pointCount,
                detailCount,
                ElementType.RECTANGLE,
                ElementStyle.BORDER,
                thickness);
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

        if (rounding < 0) {
            log.warn("Invalid rounding {} in addRectFilled", rounding);
            return;
        }

        if (!addScreenQuad(minX, minY, maxX, maxY)) {
            return;
        }

        final float centerX = (minX + maxX) / 2;
        final float centerY = (minY + maxY) / 2;
        final float width = maxX - minX;
        final float height = maxY - minY;
        final int borderStroke = 0;

        SDFPointDetail[] details =
                getSdfPointDetails(color, rounding, drawFlagsRoundingCorners, Color.CLEAR);

        int pointIndex = addPoint(centerX, centerY, width, height);
        int detailIndex = addDetails(details);
        final int pointCount = 1;
        final int detailCount = details.length;

        addCommand(
                pointIndex,
                detailIndex,
                pointCount,
                detailCount,
                ElementType.RECTANGLE,
                ElementStyle.FILL,
                borderStroke);
    }

    public void addRectFilledMultiColor(
            float minX,
            float minY,
            float maxX,
            float maxY,
            int colorUpperLeft,
            int colorUpperRight,
            int colorBottomRight,
            int colorBottomLeft) {
        addRectFilledMultiColor(
                minX,
                minY,
                maxX,
                maxY,
                0,
                DrawFlags.ROUND_CORNERS_ALL,
                colorUpperLeft,
                colorUpperRight,
                colorBottomRight,
                colorBottomLeft);
    }

    public void addRectFilledMultiColor(
            float minX,
            float minY,
            float maxX,
            float maxY,
            float rounding,
            int colorUpperLeft,
            int colorUpperRight,
            int colorBottomRight,
            int colorBottomLeft) {
        addRectFilledMultiColor(
                minX,
                minY,
                maxX,
                maxY,
                rounding,
                DrawFlags.ROUND_CORNERS_ALL,
                colorUpperLeft,
                colorUpperRight,
                colorBottomRight,
                colorBottomLeft);
    }

    public void addRectFilledMultiColor(
            float minX,
            float minY,
            float maxX,
            float maxY,
            float rounding,
            int drawFlagsRoundingCorners,
            int colorUpperLeft,
            int colorUpperRight,
            int colorBottomRight,
            int colorBottomLeft) {

        if (rounding < 0) {
            log.warn("Invalid rounding {} in addRectFilledMultiColor", rounding);
            return;
        }

        final float centerX = (minX + maxX) / 2;
        final float centerY = (minY + maxY) / 2;
        final float width = maxX - minX;
        final float height = maxY - minY;
        final int borderStroke = 0;

        final float topLeftRadius =
                (drawFlagsRoundingCorners & ROUND_CORNERS_TOP_LEFT) != 0 ? rounding : 0;
        final float topRightRadius =
                (drawFlagsRoundingCorners & ROUND_CORNERS_TOP_RIGHT) != 0 ? rounding : 0;
        final float bottomLeftRadius =
                (drawFlagsRoundingCorners & ROUND_CORNERS_BOTTOM_LEFT) != 0 ? rounding : 0;
        final float bottomRightRadius =
                (drawFlagsRoundingCorners & ROUND_CORNERS_BOTTOM_RIGHT) != 0 ? rounding : 0;

        SDFPointDetail[] details = {
            new SDFPointDetail(topLeftRadius, colorUpperLeft, Color.CLEAR),
            new SDFPointDetail(topRightRadius, colorUpperRight, Color.CLEAR),
            new SDFPointDetail(bottomRightRadius, colorBottomRight, Color.CLEAR),
            new SDFPointDetail(bottomLeftRadius, colorBottomLeft, Color.CLEAR),
        };
        // TODO(ches) add quad
        // TODO(ches) Skip adding any other data if we didn't need to add the quad
        int pointIndex = addPoint(centerX, centerY, width, height);
        int detailIndex = addDetails(details);
        final int pointCount = 1;
        final int detailCount = details.length;

        addCommand(
                pointIndex,
                detailIndex,
                pointCount,
                detailCount,
                ElementType.RECTANGLE,
                ElementStyle.FILL,
                borderStroke);
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

        SDFPointDetail[] details = {
            new SDFPointDetail(0, color, Color.CLEAR),
            new SDFPointDetail(0, color, Color.CLEAR),
            new SDFPointDetail(0, color, Color.CLEAR),
            new SDFPointDetail(0, color, Color.CLEAR),
        };

        // TODO(ches) add quad
        // TODO(ches) Skip adding any other data if we didn't need to add the quad

        int pointIndex = addPoint(p1X, p1Y, 0, 0);
        addPoint(p2X, p2Y, 0, 0);
        addPoint(p3X, p3Y, 0, 0);
        addPoint(p4X, p4Y, 0, 0);

        int detailIndex = addDetails(details);

        final int pointCount = 4;
        final int detailCount = details.length;

        addCommand(
                pointIndex,
                detailIndex,
                pointCount,
                detailCount,
                ElementType.POLYGON,
                ElementStyle.BORDER,
                thickness);
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

        final int borderStroke = 0;

        SDFPointDetail[] details = {
            new SDFPointDetail(0, color, Color.CLEAR),
            new SDFPointDetail(0, color, Color.CLEAR),
            new SDFPointDetail(0, color, Color.CLEAR),
            new SDFPointDetail(0, color, Color.CLEAR),
        };
        // TODO(ches) add quad
        // TODO(ches) Skip adding any other data if we didn't need to add the quad
        int pointIndex = addPoint(p1X, p1Y, 0, 0);
        addPoint(p2X, p2Y, 0, 0);
        addPoint(p3X, p3Y, 0, 0);
        addPoint(p4X, p4Y, 0, 0);

        int detailIndex = addDetails(details);

        final int pointCount = 4;
        final int detailCount = details.length;

        addCommand(
                pointIndex,
                detailIndex,
                pointCount,
                detailCount,
                ElementType.POLYGON,
                ElementStyle.FILL,
                borderStroke);
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

        SDFPointDetail[] details = {
            new SDFPointDetail(0, color, Color.CLEAR),
        };

        // TODO(ches) add quad
        // TODO(ches) Skip adding any other data if we didn't need to add the quad
        int pointIndex = addPoint(p1X, p1Y, 0, 0);
        addPoint(p2X, p2Y, 0, 0);
        addPoint(p3X, p3Y, 0, 0);

        int detailIndex = addDetails(details);

        final int pointCount = 3;
        final int detailCount = details.length;

        addCommand(
                pointIndex,
                detailIndex,
                pointCount,
                detailCount,
                ElementType.POLYGON,
                ElementStyle.FILL,
                thickness);
    }

    public void addTriangleFilled(
            float p1X, float p1Y, float p2X, float p2Y, float p3X, float p3Y, int color) {

        final int borderStroke = 0;

        SDFPointDetail[] details = {
            new SDFPointDetail(0, color, Color.CLEAR),
        };
        // TODO(ches) add quad
        // TODO(ches) Skip adding any other data if we didn't need to add the quad
        int pointIndex = addPoint(p1X, p1Y, 0, 0);
        addPoint(p2X, p2Y, 0, 0);
        addPoint(p3X, p3Y, 0, 0);

        int detailIndex = addDetails(details);

        final int pointCount = 3;
        final int detailCount = details.length;

        addCommand(
                pointIndex,
                detailIndex,
                pointCount,
                detailCount,
                ElementType.POLYGON,
                ElementStyle.FILL,
                borderStroke);
    }

    public void addCircle(float centerX, float centerY, float radius, int color) {
        addCircle(centerX, centerY, radius, color, 1.0f);
    }

    public void addCircle(float centerX, float centerY, float radius, int color, float thickness) {

        SDFPointDetail[] details = {
            new SDFPointDetail(0, color, Color.CLEAR),
        };

        // TODO(ches) add quad
        // TODO(ches) Skip adding any other data if we didn't need to add the quad
        int pointIndex = addPoint(centerX, centerY, radius, radius);
        int detailIndex = addDetails(details);

        final int pointCount = 1;
        final int detailCount = details.length;

        addCommand(
                pointIndex,
                detailIndex,
                pointCount,
                detailCount,
                ElementType.CIRCLE,
                ElementStyle.BORDER,
                thickness);
    }

    public void addCircleFilled(float centerX, float centerY, float radius, int color) {
        SDFPointDetail[] details = {
            new SDFPointDetail(0, color, Color.CLEAR),
        };

        final int borderStroke = 0;

        // TODO(ches) add quad
        // TODO(ches) Skip adding any other data if we didn't need to add the quad
        int pointIndex = addPoint(centerX, centerY, radius, radius);
        int detailIndex = addDetails(details);

        final int pointCount = 1;
        final int detailCount = details.length;

        addCommand(
                pointIndex,
                detailIndex,
                pointCount,
                detailCount,
                ElementType.CIRCLE,
                ElementStyle.FILL,
                borderStroke);
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

    public void addText(int fontSize, float posX, float posY, int color, @NonNull String text) {
        Vector4i charPosition = new Vector4i();
        FontAtlas atlas = IkGui.getContext().io.fonts;

        int pointCount = 0;
        final int pointIndex = pointBuffer.position() / DrawData.SIZE_OF_POINT;

        float minX = posX;
        float minY = posY;
        float maxX = posX;
        float maxY = posY;

        float charX;
        float charY;

        int currentX = (int) posX;
        // TODO(ches) RTL, vertical
        for (int pos = 0; pos < text.length(); ++pos) {
            char c = text.charAt(pos);
            atlas.registerCharacter(c, fontSize);
            if (!atlas.getFontMapPosition(c, fontSize, charPosition)) {
                log.error("Could not find a font for character '{}'", c);
                continue;
            }
            charX = currentX + (float) charPosition.x;
            charY = posY + charPosition.y;
            addPoint(charX, charY, charPosition.z, charPosition.w);
            pointCount += 1;

            currentX += charPosition.z;
            // TODO(ches) padding between letters
            maxX = currentX;
            if (charY > maxY) {
                maxY = charY;
            }
        }

        addScreenQuad(minX, minY, maxX, maxY);

        SDFPointDetail detail = new SDFPointDetail(0, color, 0);
        final int detailIndex = addDetails(detail);
        final int detailCount = 1;
        final int borderStroke = 0;

        addCommand(
                pointIndex,
                detailIndex,
                pointCount,
                detailCount,
                ElementType.TEXT,
                ElementStyle.TEXTURE,
                borderStroke);
    }

    public void addText(
            int fontSize,
            float posX,
            float posY,
            int color,
            @NonNull String text,
            float wrapWidth) {
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

        SDFPointDetail[] details = {
            new SDFPointDetail(0, textureID, color),
            new SDFPointDetail(0, textureID, color),
            new SDFPointDetail(0, textureID, color),
            new SDFPointDetail(0, textureID, color),
        };

        // TODO(ches) add quad
        // TODO(ches) Skip adding any other data if we didn't need to add the quad
        int pointIndex = addPoint(minX, minY, minU, minV);
        addPoint(maxX, minY, maxU, minV);
        addPoint(maxX, maxY, maxU, maxV);
        addPoint(minX, maxY, minU, maxV);

        int detailIndex = addDetails(details);
        final int pointCount = 4;
        final int detailCount = details.length;
        final int borderStroke = 0;

        addCommand(
                pointIndex,
                detailIndex,
                pointCount,
                detailCount,
                ElementType.POLYGON,
                ElementStyle.TEXTURE,
                borderStroke);
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

        final int borderStroke = 0;

        SDFPointDetail[] details = {
            new SDFPointDetail(0, textureID, color),
            new SDFPointDetail(0, textureID, color),
            new SDFPointDetail(0, textureID, color),
            new SDFPointDetail(0, textureID, color),
        };
        // TODO(ches) add quad
        // TODO(ches) Skip adding any other data if we didn't need to add the quad

        int pointIndex = addPoint(p1X, p1Y, u1, v1);
        addPoint(p2X, p2Y, u2, v2);
        addPoint(p3X, p3Y, u3, v3);
        addPoint(p4X, p4Y, u4, v4);

        int detailIndex = addDetails(details);

        final int pointCount = 4;
        final int detailCount = details.length;

        addCommand(
                pointIndex,
                detailIndex,
                pointCount,
                detailCount,
                ElementType.POLYGON,
                ElementStyle.TEXTURE,
                borderStroke);
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

        if (rounding < 0) {
            log.warn("Invalid rounding {} in addImageRounded", rounding);
            return;
        }

        SDFPointDetail[] details =
                getSdfPointDetails(textureID, rounding, drawFlagsRoundingCorners, color);

        // TODO(ches) add quad
        // TODO(ches) Skip adding any other data if we didn't need to add the quad
        int pointIndex = addPoint(minX, minY, minU, minV);
        addPoint(maxX, minY, maxU, minV);
        addPoint(maxX, maxY, maxU, maxV);
        addPoint(minX, maxY, minU, maxV);
        int detailIndex = addDetails(details);
        final int pointCount = 4;
        final int detailCount = details.length;
        final int borderStroke = 0;

        addCommand(
                pointIndex,
                detailIndex,
                pointCount,
                detailCount,
                ElementType.POLYGON,
                ElementStyle.TEXTURE,
                borderStroke);
    }

    public void pathClear() {
        if (!pathStarted) {
            // TODO(ches) log
            return;
        }
        // TODO(ches) complete previous line
        pathStarted = false;
        pathColor = Color.CLEAR;
        pathClosed = false;
        pathThickness = 0;
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
        if (pathStarted) {
            // TODO(ches) log
            return;
        }
        pathStarted = true;
        pathColor = color;
        pathClosed = closed;
        pathThickness = thickness;
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
}
