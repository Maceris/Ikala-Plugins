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

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Deque;

@Slf4j
public class DrawList {

    @AllArgsConstructor
    public enum ElementType {
        CIRCLE((short) 0),
        LINE_ARC((short) 1),
        LINE_BEZIER((short) 2),
        LINE_STRAIGHT((short) 3),
        RECTANGLE((short) 4),
        POLYGON((short) 5),
        TEXT((short) 6);

        /** Unique ID used in the command buffer. Must line up with the shader. */
        final short typeID;

        public static ElementType fromID(short typeID) throws IllegalArgumentException {
            for (ElementType type : ElementType.values()) {
                if (type.typeID == typeID) {
                    return type;
                }
            }
            throw new IllegalArgumentException("Unknown Element Type ID");
        }
    }

    @AllArgsConstructor
    public enum ElementStyle {
        ONLY_FILL((short) 0),
        ONLY_BORDER((short) 1),
        FILL_AND_BORDER((short) 2),
        TEXTURE((short) 3);

        /** Unique ID used in the command buffer. Must line up with the shader. */
        final short styleID;

        public static ElementStyle fromID(short styleID) throws IllegalArgumentException {
            for (ElementStyle type : ElementStyle.values()) {
                if (type.styleID == styleID) {
                    return type;
                }
            }
            throw new IllegalArgumentException("Unknown Element Style ID");
        }
    }

    private record SDFPointDetail(float radius, int colorOrTextureID, int tint) {}

    // TODO(ches) populate the quad buffer
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
     *   <li>Text - ???
     * </ul>
     */
    public ByteBuffer pointBuffer;

    /**
     * float radius (for rounding), int colorOrTextureID, int tint. Stored generally starting on the
     * top-left, and always ordered clockwise for polygons and in-order for paths.
     */
    public ByteBuffer pointDetailBuffer;

    /**
     * int pointIndex, int detailIndex, short pointCount, short detailCount, short type, short
     * style, float stroke (borders, line thickness).
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

    public DrawList() {
        pointBuffer = ByteBuffer.allocateDirect(100 * DrawData.SIZE_OF_POINT);
        pointDetailBuffer = ByteBuffer.allocateDirect(100 * DrawData.SIZE_OF_POINT_DETAIL);
        commandBuffer = ByteBuffer.allocateDirect(100 * DrawData.SIZE_OF_DRAW_COMMAND);
        vertexBuffer = ByteBuffer.allocateDirect(100 * DrawData.SIZE_OF_QUAD_VERTICES);
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
            ByteBuffer newBuffer = ByteBuffer.allocateDirect(vertexBuffer.limit() * 2);
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
            ByteBuffer newBuffer = ByteBuffer.allocateDirect(pointBuffer.limit() * 2);
            pointBuffer.flip();
            newBuffer.put(pointBuffer);
            pointBuffer = newBuffer;
        }

        final int newIndex = pointBuffer.position();

        pointBuffer.putFloat(posX);
        pointBuffer.putFloat(posY);
        pointBuffer.putFloat(a);
        pointBuffer.putFloat(b);

        return newIndex;
    }

    @Synchronized
    private int addDetails(@NonNull SDFPointDetail... details) {
        final int newDetailsSize = details.length * DrawData.SIZE_OF_POINT_DETAIL;

        if (pointDetailBuffer.position() + newDetailsSize >= pointDetailBuffer.limit()) {
            ByteBuffer newBuffer = ByteBuffer.allocateDirect(pointDetailBuffer.limit() * 2);
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

        return newDetailIndex;
    }

    @Synchronized
    private void addCommand(
            int pointIndex,
            int detailIndex,
            short pointCount,
            short detailCount,
            @NonNull ElementType type,
            @NonNull ElementStyle style,
            float stroke) {

        if (commandBuffer.position() + DrawData.SIZE_OF_DRAW_COMMAND >= commandBuffer.limit()) {
            ByteBuffer newBuffer = ByteBuffer.allocateDirect(commandBuffer.limit() * 2);
            commandBuffer.flip();
            newBuffer.put(commandBuffer);
            commandBuffer = newBuffer;
        }

        commandBuffer.putInt(pointIndex);
        commandBuffer.putInt(detailIndex);
        commandBuffer.putShort(pointCount);
        commandBuffer.putShort(detailCount);
        commandBuffer.putShort(type.typeID);
        commandBuffer.putShort(style.styleID);
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
        final short pointCount = 1;
        final short detailCount = 2;
        addCommand(
                pointIndex,
                detailIndex,
                pointCount,
                detailCount,
                ElementType.LINE_STRAIGHT,
                ElementStyle.ONLY_FILL,
                thickness);
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

        if (rounding < 0) {
            log.warn("Invalid rounding {} in addRect", rounding);
            return;
        }
        if (!addScreenQuad(minX, minY, maxX, maxY)) {
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
        final short pointCount = 1;
        final short detailCount = (short) details.length;

        addCommand(
                pointIndex,
                detailIndex,
                pointCount,
                detailCount,
                ElementType.RECTANGLE,
                ElementStyle.ONLY_BORDER,
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
        final short pointCount = 1;
        final short detailCount = (short) details.length;

        addCommand(
                pointIndex,
                detailIndex,
                pointCount,
                detailCount,
                ElementType.RECTANGLE,
                ElementStyle.ONLY_FILL,
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
        final short pointCount = 1;
        final short detailCount = (short) details.length;

        addCommand(
                pointIndex,
                detailIndex,
                pointCount,
                detailCount,
                ElementType.RECTANGLE,
                ElementStyle.ONLY_FILL,
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

        final short pointCount = 4;
        final short detailCount = (short) details.length;

        addCommand(
                pointIndex,
                detailIndex,
                pointCount,
                detailCount,
                ElementType.POLYGON,
                ElementStyle.ONLY_BORDER,
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

        final short pointCount = 4;
        final short detailCount = (short) details.length;

        addCommand(
                pointIndex,
                detailIndex,
                pointCount,
                detailCount,
                ElementType.POLYGON,
                ElementStyle.ONLY_FILL,
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

        final short pointCount = 3;
        final short detailCount = (short) details.length;

        addCommand(
                pointIndex,
                detailIndex,
                pointCount,
                detailCount,
                ElementType.POLYGON,
                ElementStyle.FILL_AND_BORDER,
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

        final short pointCount = 3;
        final short detailCount = (short) details.length;

        addCommand(
                pointIndex,
                detailIndex,
                pointCount,
                detailCount,
                ElementType.POLYGON,
                ElementStyle.ONLY_FILL,
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

        final short pointCount = 1;
        final short detailCount = (short) details.length;

        addCommand(
                pointIndex,
                detailIndex,
                pointCount,
                detailCount,
                ElementType.CIRCLE,
                ElementStyle.ONLY_BORDER,
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

        final short pointCount = 1;
        final short detailCount = (short) details.length;

        addCommand(
                pointIndex,
                detailIndex,
                pointCount,
                detailCount,
                ElementType.CIRCLE,
                ElementStyle.ONLY_FILL,
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
        final short pointCount = 4;
        final short detailCount = (short) details.length;
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

        final short pointCount = 4;
        final short detailCount = (short) details.length;

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
        final short pointCount = 4;
        final short detailCount = (short) details.length;
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
