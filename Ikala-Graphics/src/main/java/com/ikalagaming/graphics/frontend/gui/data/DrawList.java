package com.ikalagaming.graphics.frontend.gui.data;

import static com.ikalagaming.graphics.frontend.gui.flags.DrawFlags.*;

import com.ikalagaming.graphics.frontend.TextureInfo;
import com.ikalagaming.graphics.frontend.gui.IkGui;
import com.ikalagaming.graphics.frontend.gui.IkGuiInternal;
import com.ikalagaming.graphics.frontend.gui.flags.DrawFlags;
import com.ikalagaming.graphics.frontend.gui.util.Color;
import com.ikalagaming.graphics.frontend.gui.util.MathUtil;
import com.ikalagaming.graphics.frontend.gui.util.RectFloat;
import com.ikalagaming.util.FloatArrayList;
import com.ikalagaming.util.IntArrayList;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector2f;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;

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

    /**
     * Details for one point.
     *
     * @param radius The radius for rounding.
     * @param alphaRadius The radius for blending to transparent. 0 means no blending, positive
     *     numbers indicate the radius fading from an opaque (well, "regular" colored) center to
     *     fully transparent edge, negative numbers indicate an opaque (unmodified) edge fading to a
     *     transparent center.
     * @param colorOrTextureID The color (RGBA32) or texture ID.
     * @param tint The tint to modify by.
     */
    private record SDFPointDetail(
            float radius, float alphaRadius, int colorOrTextureID, int tint) {}

    /** Extra space around anti-aliased shapes, so the soft edge isn't clipped by the quad. */
    private static final float ANTI_ALIAS_PADDING = 1.0f;

    /**
     * Extra space around hard-edged rectangle fills. The shader decides which pixels on the edges
     * are filled (so rectangles sharing an edge never leave a gap), so the quad must cover those
     * pixels rather than leaving it to how the GPU rasterizes the quad's own edges.
     */
    private static final float FILL_EDGE_PADDING = 1.0f;

    /** The maximum error allowed when approximating circles and curves with line segments. */
    private static final float CURVE_TESSELLATION_MAX_ERROR = 0.3f;

    /** The maximum number of segments we use for approximating curves in paths. */
    private static final int MAX_CURVE_SEGMENTS = 512;

    public final String windowName;

    /**
     * The vertices making up the quad that each element (command) will be drawn on, with each quad
     * being a pair of triangles. This is the region that is needed to draw the element, clipped as
     * appropriate.
     */
    public ByteBuffer vertexBuffer;

    /**
     * float posX, float posY, float a, float b. A and B are additional values that depend on the
     * type of point. The layout of each element type is:
     *
     * <ul>
     *   <li>Circle - 1 point: center, a = x radius, b = y radius
     *   <li>Line (arc) - 2 points: center with a = radius, then (start angle, end angle) in radians
     *   <li>Line (bezier) - 3 points (quadratic) or 4 points (cubic), the control points
     *   <li>Line (straight) - 2 points, the end points
     *   <li>Rectangle - 1 point: center, a = width, b = height. Textured rectangles have a second
     *       point: (min u, min v) with a = max u, b = max v
     *   <li>Polygon - at least 3 points in order, a = u, b = v, for textures. Otherwise ignored.
     *   <li>Text - 2 or 3 points: glyph top left position with a = cos and b = sin of the rotation,
     *       then (atlas x, atlas y) with a = width, b = height. The optional third point is a clip
     *       rect relative to the unrotated glyph top left, (min x, min y) with a = max x, b = max y
     * </ul>
     */
    public ByteBuffer pointBuffer;

    /**
     * float radius (for rounding), float alphaRadius (used to blend to transparent), int
     * colorOrTextureID, int tint. For textured elements, the texture ID is the index of the texture
     * in {@link DrawData#textures}, and the tint is multiplied with the texture color. Stored
     * generally starting on the top-left, and always ordered clockwise for polygons and in-order
     * for paths.
     */
    public ByteBuffer pointDetailBuffer;

    /**
     * int pointIndex, int detailIndex, int pointCount, int detailCount, int type, int style, float
     * stroke (borders, line thickness).
     */
    public ByteBuffer commandBuffer;

    /**
     * True once the buffers have been flipped for reading by {@link #prepareForRender()}, false
     * while they are being written to.
     */
    private boolean preparedForRender;

    private final Deque<RectFloat> clipRects;
    private final IntArrayList textures;

    /** The points of the current path, as x, y pairs. */
    private final FloatArrayList path;

    /** The splitter used by {@link #channelsSplit(int)}. */
    private final DrawListSplitter splitter;

    /**
     * A transform applied to text, see {@link #pushTextTransform}. While active, text is laid out
     * in "label space", then rotated and moved into place.
     *
     * @param pivotInX The label space pivot x.
     * @param pivotInY The label space pivot y.
     * @param cos The cosine of the rotation angle.
     * @param sin The sine of the rotation angle.
     * @param pivotOutX The screen space position the pivot ends up at, x.
     * @param pivotOutY The screen space position the pivot ends up at, y.
     * @param screenClip The screen clip rect when the transform was pushed, may be null.
     * @param clipDepth The depth of the clip rect stack when the transform was pushed.
     */
    private record TextTransform(
            float pivotInX,
            float pivotInY,
            float cos,
            float sin,
            float pivotOutX,
            float pivotOutY,
            RectFloat screenClip,
            int clipDepth) {}

    /** The current text transform, or null if text is drawn normally. */
    private TextTransform textTransform;

    /**
     * Set up point details for four corners of a rectangle.
     *
     * @param textureID The texture ID or color to use.
     * @param rounding The rounding radius.
     * @param alphaRadius The alpha radius.
     * @param drawFlagsRoundingCorners Draw flags for which corners need rounding.
     * @param tint Tint, for tinting textures.
     * @return The (four) point details for each of the corners (top left, top right, bottom right,
     *     then bottom left).
     */
    private static SDFPointDetail[] getSdfPointDetails(
            int textureID,
            float rounding,
            float alphaRadius,
            int drawFlagsRoundingCorners,
            int tint) {
        final float topLeftRadius =
                (drawFlagsRoundingCorners & ROUND_CORNERS_TOP_LEFT) != 0 ? rounding : 0;
        final float topRightRadius =
                (drawFlagsRoundingCorners & ROUND_CORNERS_TOP_RIGHT) != 0 ? rounding : 0;
        final float bottomLeftRadius =
                (drawFlagsRoundingCorners & ROUND_CORNERS_BOTTOM_LEFT) != 0 ? rounding : 0;
        final float bottomRightRadius =
                (drawFlagsRoundingCorners & ROUND_CORNERS_BOTTOM_RIGHT) != 0 ? rounding : 0;

        final float topLeftAlphaRadius =
                (drawFlagsRoundingCorners & ROUND_CORNERS_TOP_LEFT) != 0 ? alphaRadius : 0;
        final float topRightAlphaRadius =
                (drawFlagsRoundingCorners & ROUND_CORNERS_TOP_RIGHT) != 0 ? alphaRadius : 0;
        final float bottomLeftAlphaRadius =
                (drawFlagsRoundingCorners & ROUND_CORNERS_BOTTOM_LEFT) != 0 ? alphaRadius : 0;
        final float bottomRightAlphaRadius =
                (drawFlagsRoundingCorners & ROUND_CORNERS_BOTTOM_RIGHT) != 0 ? alphaRadius : 0;

        return new SDFPointDetail[] {
            new SDFPointDetail(topLeftRadius, topLeftAlphaRadius, textureID, tint),
            new SDFPointDetail(topRightRadius, topRightAlphaRadius, textureID, tint),
            new SDFPointDetail(bottomRightRadius, bottomRightAlphaRadius, textureID, tint),
            new SDFPointDetail(bottomLeftRadius, bottomLeftAlphaRadius, textureID, tint),
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
        path = new FloatArrayList();
        splitter = new DrawListSplitter();
        textTransform = null;
    }

    /** Clear out everything in the draw list. */
    public void clear() {
        preparedForRender = false;
        splitter.discard(this);
        pointBuffer.clear();
        pointDetailBuffer.clear();
        commandBuffer.clear();
        vertexBuffer.clear();
        clipRects.clear();
        textures.clear();
        path.clear();
        textTransform = null;
    }

    /**
     * The number of bytes that have been written to a buffer, whether we are still writing to it or
     * it has been prepared for rendering.
     *
     * @param buffer The buffer.
     * @return The number of bytes of data.
     */
    private int bytesUsed(@NonNull ByteBuffer buffer) {
        return preparedForRender ? buffer.limit() : buffer.position();
    }

    /**
     * Fetch the number of vertices, which is valid while drawing and after the list is prepared for
     * rendering. Used by debug tools.
     *
     * @return The number of vertices.
     */
    public int getVertexCount() {
        return bytesUsed(vertexBuffer) / DrawData.SIZE_OF_VERTEX;
    }

    /**
     * Fetch the number of points, which is valid while drawing and after the list is prepared for
     * rendering. Used by debug tools.
     *
     * @return The number of points.
     */
    public int getPointCount() {
        return bytesUsed(pointBuffer) / DrawData.SIZE_OF_POINT;
    }

    /**
     * Fetch the number of point details, which is valid while drawing and after the list is
     * prepared for rendering. Used by debug tools.
     *
     * @return The number of point details.
     */
    public int getPointDetailCount() {
        return bytesUsed(pointDetailBuffer) / DrawData.SIZE_OF_POINT_DETAIL;
    }

    /** Set up all the buffers for reading once we are done with a frame. */
    public void prepareForRender() {
        preparedForRender = true;
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
    private boolean addScreenQuad(float minX, float minY, float maxX, float maxY) {
        return addScreenQuad(minX, minY, maxX, maxY, clipRects.peekFirst());
    }

    /**
     * Add a quad that fully contains the entire feature, clipped to a specific clip rect.
     *
     * @param minX The minimum X coordinate.
     * @param minY The minimum Y coordinate.
     * @param maxX The maximum X coordinate.
     * @param maxY The maximum Y coordinate.
     * @param clipRect The clip rect, or null for no clipping.
     * @return If we actually added the quad.
     */
    @Synchronized
    private boolean addScreenQuad(
            float minX, float minY, float maxX, float maxY, RectFloat clipRect) {
        float actualMinX = minX;
        float actualMinY = minY;
        float actualMaxX = maxX;
        float actualMaxY = maxY;

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

    /**
     * Add a screen quad that fully contains all the given points, expanded by some padding. The
     * shader looks up the command for each quad by index, so every command needs exactly one quad.
     *
     * @param padding Extra space around the points, for things like line thickness.
     * @param coordinates Pairs of x, y coordinates.
     * @return If we actually added the quad, false if it was entirely clipped.
     */
    private boolean addBoundingQuad(float padding, float... coordinates) {
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        for (int i = 0; i + 1 < coordinates.length; i += 2) {
            minX = Math.min(minX, coordinates[i]);
            maxX = Math.max(maxX, coordinates[i]);
            minY = Math.min(minY, coordinates[i + 1]);
            maxY = Math.max(maxY, coordinates[i + 1]);
        }
        return addScreenQuad(minX - padding, minY - padding, maxX + padding, maxY + padding);
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
            pointDetailBuffer.putFloat(detail.alphaRadius());
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

    public void pushClipRect(float minX, float minY, float maxX, float maxY) {
        pushClipRect(minX, minY, maxX, maxY, false);
    }

    public void pushClipRect(
            float minX, float minY, float maxX, float maxY, boolean intersectWithCurrentClipRect) {
        RectFloat newClipRect = new RectFloat(minX, minY, maxX, maxY);

        // While transforming text, clip rects are in label space, so they can't be intersected
        // with the screen space clip rect from before the transform started
        if (textTransform != null && clipRects.size() <= textTransform.clipDepth()) {
            intersectWithCurrentClipRect = false;
        }

        if (!intersectWithCurrentClipRect) {
            clipRects.addFirst(newClipRect);
            return;
        }

        RectFloat oldRect = clipRects.peekFirst();
        if (oldRect == null) {
            // No existing rect
            clipRects.addFirst(newClipRect);
            return;
        }

        float left = Math.max(newClipRect.getLeft(), oldRect.getLeft());
        float top = Math.max(newClipRect.getTop(), oldRect.getTop());
        float right = Math.min(newClipRect.getRight(), oldRect.getRight());
        float bottom = Math.min(newClipRect.getBottom(), oldRect.getBottom());

        if (left > right || top > bottom) {
            // No intersection, so nothing should be drawn
            newClipRect.set(left, top, left, top);
            clipRects.addFirst(newClipRect);
            return;
        }

        newClipRect.set(left, top, right, bottom);
        clipRects.addFirst(newClipRect);
    }

    public void pushClipRectFullScreen() {
        IkIO io = IkGui.getIO();
        RectFloat newRect = new RectFloat(0, 0, io.displaySize.x, io.displaySize.y);
        clipRects.addFirst(newRect);
    }

    public void popClipRect() {
        clipRects.pop();
    }

    /**
     * Replace the current clip rect without pushing a new one, or push it if there is none. This is
     * used by tables when switching draw channels, where the clip rect changes per column.
     *
     * @param minX Minimum X coordinate.
     * @param minY Minimum Y coordinate.
     * @param maxX Maximum X coordinate.
     * @param maxY Maximum Y coordinate.
     */
    public void setCurrentClipRect(float minX, float minY, float maxX, float maxY) {
        final RectFloat current = clipRects.peekFirst();
        if (current == null) {
            clipRects.addFirst(new RectFloat(minX, minY, maxX, maxY));
        } else {
            current.set(minX, minY, maxX, maxY);
        }
    }

    /**
     * Split the draw list into channels, so that things can be drawn out of order. Commands are
     * added to channel 0 until {@link #channelsSetCurrent(int)} is called. Call {@link
     * #channelsMerge()} to put the channels back together, in order. To nest splits, use your own
     * {@link DrawListSplitter}.
     *
     * @param count The number of channels.
     */
    public void channelsSplit(int count) {
        splitter.split(this, count);
    }

    /** Merge channels created with {@link #channelsSplit(int)} back together, in order. */
    public void channelsMerge() {
        splitter.merge(this);
    }

    /**
     * Set the channel that commands are added to, after {@link #channelsSplit(int)}.
     *
     * @param index The channel index.
     */
    public void channelsSetCurrent(int index) {
        splitter.setCurrentChannel(this, index);
    }

    /**
     * The number of commands currently in the draw list (or the current channel, if split). This is
     * valid while drawing and after the list is prepared for rendering.
     *
     * @return The command count.
     */
    public int getCommandCount() {
        return bytesUsed(commandBuffer) / DrawData.SIZE_OF_DRAW_COMMAND;
    }

    /**
     * Start transforming text. Text added afterward is laid out normally in "label space", then
     * each point p is moved to pivotOut + rotate(p - pivotIn). This is the equivalent of ImGui's
     * ShadeVertsTransformPos() for text, used for angled table headers.
     *
     * <p>The current clip rect still clips in screen space. Clip rects pushed while the transform
     * is active are treated as label space clip rects, so text can be clipped before rotating it.
     * Only text is transformed, other shapes are drawn normally.
     *
     * @param pivotInX The label space pivot x.
     * @param pivotInY The label space pivot y.
     * @param cos The cosine of the rotation angle.
     * @param sin The sine of the rotation angle.
     * @param pivotOutX The screen position the pivot is moved to, x.
     * @param pivotOutY The screen position the pivot is moved to, y.
     */
    public void pushTextTransform(
            float pivotInX,
            float pivotInY,
            float cos,
            float sin,
            float pivotOutX,
            float pivotOutY) {
        if (textTransform != null) {
            IkGuiInternal.reportError(log, "Nested text transforms are not supported");
            return;
        }
        final RectFloat screenClip = clipRects.peekFirst();
        textTransform =
                new TextTransform(
                        pivotInX,
                        pivotInY,
                        cos,
                        sin,
                        pivotOutX,
                        pivotOutY,
                        screenClip == null ? null : new RectFloat(screenClip),
                        clipRects.size());
    }

    /** Stop transforming text, see {@link #pushTextTransform}. */
    public void popTextTransform() {
        if (textTransform == null) {
            IkGuiInternal.reportError(log, "popTextTransform() without pushTextTransform()");
            return;
        }
        if (clipRects.size() != textTransform.clipDepth()) {
            IkGuiInternal.reportError(log, "Unbalanced clip rects while transforming text");
        }
        textTransform = null;
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
        RectFloat clipRect = clipRects.peekFirst();
        if (clipRect == null) {
            output.set(0, 0);
            return;
        }
        output.set(clipRect.getLeft(), clipRect.getTop());
    }

    public float getClipRectMinX() {
        RectFloat clipRect = clipRects.peekFirst();
        if (clipRect == null) {
            return 0;
        }
        return clipRect.getLeft();
    }

    public float getClipRectMinY() {
        RectFloat clipRect = clipRects.peekFirst();
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
        RectFloat clipRect = clipRects.peekFirst();
        if (clipRect == null) {
            output.set(0, 0);
            return;
        }
        output.set(clipRect.getRight(), clipRect.getBottom());
    }

    public float getClipRectMaxX() {
        RectFloat clipRect = clipRects.peekFirst();
        if (clipRect == null) {
            IkIO io = IkGui.getIO();
            return io.displaySize.x;
        }
        return clipRect.getRight();
    }

    public float getClipRectMaxY() {
        RectFloat clipRect = clipRects.peekFirst();
        if (clipRect == null) {
            IkIO io = IkGui.getIO();
            return io.displaySize.y;
        }
        return clipRect.getBottom();
    }

    public void addLine(float p1X, float p1Y, float p2X, float p2Y, int color) {
        addLine(p1X, p1Y, p2X, p2Y, color, 1.0f);
    }

    /**
     * Add a straight line with round caps.
     *
     * @param p1X The x coordinate of the start.
     * @param p1Y The y coordinate of the start.
     * @param p2X The x coordinate of the end.
     * @param p2Y The y coordinate of the end.
     * @param color The color of the line.
     * @param thickness The thickness of the line.
     */
    public void addLine(float p1X, float p1Y, float p2X, float p2Y, int color, float thickness) {
        addLine(p1X, p1Y, p2X, p2Y, color, color, thickness);
    }

    /**
     * Add a straight line with round caps, with a color gradient from one end to the other.
     *
     * @param p1X The x coordinate of the start.
     * @param p1Y The y coordinate of the start.
     * @param p2X The x coordinate of the end.
     * @param p2Y The y coordinate of the end.
     * @param color1 The color at the start of the line.
     * @param color2 The color at the end of the line.
     * @param thickness The thickness of the line.
     */
    public void addLine(
            float p1X, float p1Y, float p2X, float p2Y, int color1, int color2, float thickness) {
        if (!Float.isFinite(thickness) || thickness <= 0) {
            log.warn("Invalid thickness {}", thickness);
            return;
        }

        if (!addBoundingQuad(thickness / 2 + ANTI_ALIAS_PADDING, p1X, p1Y, p2X, p2Y)) {
            return;
        }

        SDFPointDetail[] details = {
            new SDFPointDetail(0.0f, 0.0f, color1, Color.CLEAR),
            new SDFPointDetail(0.0f, 0.0f, color2, Color.CLEAR),
        };
        int pointIndex = addPoint(p1X, p1Y, 0, 0);
        addPoint(p2X, p2Y, 0, 0);
        int detailIndex = addDetails(details);
        final int pointCount = 2;
        final int detailCount = details.length;
        addCommand(
                pointIndex,
                detailIndex,
                pointCount,
                detailCount,
                ElementType.LINE_STRAIGHT,
                ElementStyle.FILL,
                thickness);
    }

    /**
     * Add a horizontal line, covering the pixel row at y (for a thickness of 1). This is drawn as a
     * hard-edged rectangle, which is crisper than an anti-aliased line.
     *
     * @param minX The left of the line.
     * @param maxX The right of the line.
     * @param y The y position of the line.
     * @param color The color of the line.
     * @param thickness The thickness of the line, in pixels.
     */
    public void addLineH(float minX, float maxX, float y, int color, float thickness) {
        if ((color & 0xFF) == 0) {
            return;
        }
        final float center = y + 0.5f;
        addRectFilled(minX, center - thickness * 0.5f, maxX, center + thickness * 0.5f, color);
    }

    /**
     * Add a vertical line, covering the pixel column at x (for a thickness of 1). This is drawn as
     * a hard-edged rectangle, which is crisper than an anti-aliased line.
     *
     * @param x The x position of the line.
     * @param minY The top of the line.
     * @param maxY The bottom of the line.
     * @param color The color of the line.
     * @param thickness The thickness of the line, in pixels.
     */
    public void addLineV(float x, float minY, float maxY, int color, float thickness) {
        if ((color & 0xFF) == 0) {
            return;
        }
        final float center = x + 0.5f;
        addRectFilled(center - thickness * 0.5f, minY, center + thickness * 0.5f, maxY, color);
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
            float thickness) {

        if (rounding < 0) {
            log.warn("Invalid rounding {} in addRect", rounding);
            return;
        }
        if (thickness <= 0) {
            log.warn("Trying to add a non-filled rect with {} thickness", thickness);
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
                getSdfPointDetails(color, rounding, 0, drawFlagsRoundingCorners, Color.CLEAR);

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
        addRectFilled(minX, minY, maxX, maxY, color, 0.0f, DrawFlags.ROUND_CORNERS_ALL, 0.0f);
    }

    public void addRectFilled(
            float minX, float minY, float maxX, float maxY, int color, float rounding) {
        addRectFilled(minX, minY, maxX, maxY, color, rounding, DrawFlags.ROUND_CORNERS_ALL, 0.0f);
    }

    public void addRectFilled(
            float minX,
            float minY,
            float maxX,
            float maxY,
            int color,
            float rounding,
            int drawFlagsRoundingCorners) {
        addRectFilled(minX, minY, maxX, maxY, color, rounding, drawFlagsRoundingCorners, 0.0f);
    }

    public void addRectFilled(
            float minX,
            float minY,
            float maxX,
            float maxY,
            int color,
            float rounding,
            int drawFlagsRoundingCorners,
            float alphaRadius) {

        if (rounding < 0) {
            log.warn("Invalid rounding {} in addRectFilled", rounding);
            return;
        }

        if (!addScreenQuad(
                minX - FILL_EDGE_PADDING,
                minY - FILL_EDGE_PADDING,
                maxX + FILL_EDGE_PADDING,
                maxY + FILL_EDGE_PADDING)) {
            return;
        }

        final float centerX = (minX + maxX) / 2;
        final float centerY = (minY + maxY) / 2;
        final float width = maxX - minX;
        final float height = maxY - minY;
        final int borderStroke = 0;

        SDFPointDetail[] details =
                getSdfPointDetails(
                        color, rounding, alphaRadius, drawFlagsRoundingCorners, Color.CLEAR);

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
                colorBottomLeft,
                0.0f);
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
                colorBottomLeft,
                0.0f);
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
        addRectFilledMultiColor(
                minX,
                minY,
                maxX,
                maxY,
                rounding,
                drawFlagsRoundingCorners,
                colorUpperLeft,
                colorUpperRight,
                colorBottomRight,
                colorBottomLeft,
                0.0f);
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
            int colorBottomLeft,
            float alphaRadius) {

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
            new SDFPointDetail(topLeftRadius, alphaRadius, colorUpperLeft, Color.CLEAR),
            new SDFPointDetail(topRightRadius, alphaRadius, colorUpperRight, Color.CLEAR),
            new SDFPointDetail(bottomRightRadius, alphaRadius, colorBottomRight, Color.CLEAR),
            new SDFPointDetail(bottomLeftRadius, alphaRadius, colorBottomLeft, Color.CLEAR),
        };
        if (!addScreenQuad(
                minX - FILL_EDGE_PADDING,
                minY - FILL_EDGE_PADDING,
                maxX + FILL_EDGE_PADDING,
                maxY + FILL_EDGE_PADDING)) {
            return;
        }
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
            new SDFPointDetail(0, 0, color, Color.CLEAR),
            new SDFPointDetail(0, 0, color, Color.CLEAR),
            new SDFPointDetail(0, 0, color, Color.CLEAR),
            new SDFPointDetail(0, 0, color, Color.CLEAR),
        };

        if (!addBoundingQuad(
                thickness / 2 + ANTI_ALIAS_PADDING, p1X, p1Y, p2X, p2Y, p3X, p3Y, p4X, p4Y)) {
            return;
        }

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
        addQuadFilled(p1X, p1Y, p2X, p2Y, p3X, p3Y, p4X, p4Y, color, 0.0f);
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
            int color,
            float alphaRadius) {

        final int borderStroke = 0;

        SDFPointDetail[] details = {
            new SDFPointDetail(0, alphaRadius, color, Color.CLEAR),
            new SDFPointDetail(0, alphaRadius, color, Color.CLEAR),
            new SDFPointDetail(0, alphaRadius, color, Color.CLEAR),
            new SDFPointDetail(0, alphaRadius, color, Color.CLEAR),
        };
        if (!addBoundingQuad(ANTI_ALIAS_PADDING, p1X, p1Y, p2X, p2Y, p3X, p3Y, p4X, p4Y)) {
            return;
        }
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
            new SDFPointDetail(0, 0.0f, color, Color.CLEAR),
        };

        if (!addBoundingQuad(thickness / 2 + ANTI_ALIAS_PADDING, p1X, p1Y, p2X, p2Y, p3X, p3Y)) {
            return;
        }
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
                ElementStyle.BORDER,
                thickness);
    }

    public void addTriangleFilled(
            float p1X, float p1Y, float p2X, float p2Y, float p3X, float p3Y, int color) {
        addTriangleFilled(p1X, p1Y, p2X, p2Y, p3X, p3Y, color, 0.0f);
    }

    public void addTriangleFilled(
            float p1X,
            float p1Y,
            float p2X,
            float p2Y,
            float p3X,
            float p3Y,
            int color,
            float alphaRadius) {

        final int borderStroke = 0;

        SDFPointDetail[] details = {
            new SDFPointDetail(0, alphaRadius, color, Color.CLEAR),
        };
        if (!addBoundingQuad(ANTI_ALIAS_PADDING, p1X, p1Y, p2X, p2Y, p3X, p3Y)) {
            return;
        }
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

    /**
     * Add a filled triangle, with the color blended between the colors at each vertex.
     *
     * @param p1X The x coordinate of the first vertex.
     * @param p1Y The y coordinate of the first vertex.
     * @param p2X The x coordinate of the second vertex.
     * @param p2Y The y coordinate of the second vertex.
     * @param p3X The x coordinate of the third vertex.
     * @param p3Y The y coordinate of the third vertex.
     * @param color1 The color at the first vertex.
     * @param color2 The color at the second vertex.
     * @param color3 The color at the third vertex.
     */
    public void addTriangleFilledMultiColor(
            float p1X,
            float p1Y,
            float p2X,
            float p2Y,
            float p3X,
            float p3Y,
            int color1,
            int color2,
            int color3) {
        if (!addBoundingQuad(ANTI_ALIAS_PADDING, p1X, p1Y, p2X, p2Y, p3X, p3Y)) {
            return;
        }
        SDFPointDetail[] details = {
            new SDFPointDetail(0, 0.0f, color1, Color.CLEAR),
            new SDFPointDetail(0, 0.0f, color2, Color.CLEAR),
            new SDFPointDetail(0, 0.0f, color3, Color.CLEAR),
        };
        int pointIndex = addPoint(p1X, p1Y, 0, 0);
        addPoint(p2X, p2Y, 0, 0);
        addPoint(p3X, p3Y, 0, 0);
        int detailIndex = addDetails(details);

        addCommand(
                pointIndex,
                detailIndex,
                3,
                details.length,
                ElementType.POLYGON,
                ElementStyle.FILL,
                0);
    }

    public void addCircle(float centerX, float centerY, float radius, int color) {
        addCircle(centerX, centerY, radius, color, 1.0f);
    }

    public void addCircle(float centerX, float centerY, float radius, int color, float thickness) {

        SDFPointDetail[] details = {
            new SDFPointDetail(0, 0.0f, color, Color.CLEAR),
        };

        if (!addBoundingQuad(
                thickness / 2 + ANTI_ALIAS_PADDING,
                centerX - radius,
                centerY - radius,
                centerX + radius,
                centerY + radius)) {
            return;
        }
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
        addCircleFilled(centerX, centerY, radius, color, 0.0f);
    }

    public void addCircleFilled(
            float centerX, float centerY, float radius, int color, float alphaRadius) {
        SDFPointDetail[] details = {
            new SDFPointDetail(0, alphaRadius, color, Color.CLEAR),
        };

        final int borderStroke = 0;

        if (!addBoundingQuad(
                ANTI_ALIAS_PADDING,
                centerX - radius,
                centerY - radius,
                centerX + radius,
                centerY + radius)) {
            return;
        }
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

    /**
     * Add the outline of a regular polygon.
     *
     * @param centerX The x coordinate of the center.
     * @param centerY The y coordinate of the center.
     * @param radius The distance from the center to each corner.
     * @param color The color.
     * @param segmentCount The number of sides, at least 3.
     * @param thickness The thickness of the outline.
     */
    public void addNgon(
            float centerX,
            float centerY,
            float radius,
            int color,
            int segmentCount,
            float thickness) {
        if (segmentCount < 3) {
            log.warn("Invalid segment count {} in addNgon", segmentCount);
            return;
        }
        addPolygon(
                ngonPoints(centerX, centerY, radius, segmentCount),
                segmentCount,
                color,
                ElementStyle.BORDER,
                thickness,
                0.0f);
    }

    public void addNgonFilled(
            float centerX, float centerY, float radius, int color, int segmentCount) {
        addNgonFilled(centerX, centerY, radius, color, segmentCount, 0.0f);
    }

    /**
     * Add a filled regular polygon.
     *
     * @param centerX The x coordinate of the center.
     * @param centerY The y coordinate of the center.
     * @param radius The distance from the center to each corner.
     * @param color The color.
     * @param segmentCount The number of sides, at least 3.
     * @param alphaRadius The radius for blending to transparent.
     */
    public void addNgonFilled(
            float centerX,
            float centerY,
            float radius,
            int color,
            int segmentCount,
            float alphaRadius) {
        if (segmentCount < 3) {
            log.warn("Invalid segment count {} in addNgonFilled", segmentCount);
            return;
        }
        addPolygon(
                ngonPoints(centerX, centerY, radius, segmentCount),
                segmentCount,
                color,
                ElementStyle.FILL,
                0.0f,
                alphaRadius);
    }

    /**
     * Calculate the corners of a regular polygon.
     *
     * @param centerX The x coordinate of the center.
     * @param centerY The y coordinate of the center.
     * @param radius The distance from the center to each corner.
     * @param segmentCount The number of sides.
     * @return The corners, as x, y pairs.
     */
    private static float[] ngonPoints(
            float centerX, float centerY, float radius, int segmentCount) {
        float[] coordinates = new float[segmentCount * 2];
        for (int i = 0; i < segmentCount; ++i) {
            final double angle = (Math.PI * 2.0 * i) / segmentCount;
            coordinates[i * 2] = centerX + (float) Math.cos(angle) * radius;
            coordinates[i * 2 + 1] = centerY + (float) Math.sin(angle) * radius;
        }
        return coordinates;
    }

    /**
     * Add text to the draw list.
     *
     * @param fontSize Font size.
     * @param posX X position to start drawing at.
     * @param posY Y position to start drawing at.
     * @param color Color of the text.
     * @param text Text to draw.
     * @return Total width of the text.
     */
    public int addText(int fontSize, float posX, float posY, int color, @NonNull String text) {
        if (!hasAnyFont()) {
            return 0;
        }
        FontAtlas atlas = IkGui.getContext().io.fonts;

        SDFPointDetail detail = new SDFPointDetail(0, 0.0f, 0, color);
        final int detailIndex = addDetails(detail);
        final int pointCount = 2;
        final int detailCount = 1;
        final int borderStroke = 0;

        float fontScale =
                (float) IkGui.getContext().dpiScaleScreen / IkGui.getContext().dpiScaleFont;

        // TODO(ches) RTL, vertical

        List<FontAtlas.CharInfo> infoList = new ArrayList<>(text.length());

        for (int pos = 0; pos < text.length(); ++pos) {
            final char c = text.charAt(pos);
            atlas.registerCharacter(c, fontSize);

            Optional<FontAtlas.CharInfo> maybeInfo = atlas.getFontMapInfo(c, fontSize);
            if (maybeInfo.isEmpty()) {
                IkGuiInternal.reportError(log, "Could not find a font for character '{}'", c);
            } else {
                infoList.add(maybeInfo.get());
            }
        }

        // TODO(ches) We should probably share this logic with IkGui.calcTextSize()
        float penX = posX;
        // Use the same baseline for all text in the font, so it lines up regardless of which
        // characters are in the string. We know there is a font from the check above.
        final FontMetrics metrics = Objects.requireNonNull(atlas.getFontMetrics(fontSize));
        final float lineHeight = (int) (fontScale * fontSize);
        final float baseline = posY + (float) Math.floor(metrics.baselineOffset(lineHeight));

        for (int i = 0; i < infoList.size(); ++i) {
            FontAtlas.CharInfo info = infoList.get(i);
            // Glyphs are drawn at whole pixels so they stay sharp
            final float glyphX = (float) Math.floor(penX + 0.5f);
            penX += penAdvance(atlas, infoList, i, fontSize);

            if (info.width == 0 || info.height == 0) {
                // Nothing to draw, like a space
                // TODO(ches) tab stops
                continue;
            }

            final float quadMinX = glyphX + info.bearingX;
            final float quadMinY = baseline - info.bearingY;
            final float quadMaxX = glyphX + info.width + info.bearingX;
            // Needs to include the lil nub below baseline
            final float quadMaxY = baseline + (info.height - info.bearingY);

            if (textTransform != null) {
                addTransformedGlyph(quadMinX, quadMinY, quadMaxX, quadMaxY, info, detailIndex);
            } else if (addScreenQuad(quadMinX, quadMinY, quadMaxX, quadMaxY)) {
                // Every command needs exactly one quad, so skip glyphs that are entirely clipped
                // Quad info, misc is the rotation (cos, sin)
                final int pointIndex = addPoint(quadMinX, quadMinY, 1, 0);
                // Text info
                addPoint(info.x, info.y, info.width, info.height);

                addCommand(
                        pointIndex,
                        detailIndex,
                        pointCount,
                        detailCount,
                        ElementType.TEXT,
                        ElementStyle.TEXTURE,
                        borderStroke);
            }
        }

        return (int) Math.ceil(penX - posX);
    }

    /**
     * How far to move the pen after a character, which is its advance plus the kerning between it
     * and the next character.
     *
     * @param atlas The font atlas.
     * @param infoList The characters being laid out.
     * @param index The index of the character.
     * @param fontSize The font size.
     * @return The distance to move the pen, in pixels.
     */
    private static float penAdvance(
            @NonNull FontAtlas atlas,
            @NonNull List<FontAtlas.CharInfo> infoList,
            int index,
            int fontSize) {
        final FontAtlas.CharInfo info = infoList.get(index);
        if (index + 1 >= infoList.size()) {
            return info.advance;
        }
        return info.advance + atlas.getKerning(info.value, infoList.get(index + 1).value, fontSize);
    }

    /**
     * Add a glyph using the current text transform. The glyph is clipped in label space by any clip
     * rect pushed since the transform started, then rotated into place, and the quad is clipped by
     * the screen clip rect from when the transform started.
     *
     * @param minX The label space left of the glyph.
     * @param minY The label space top of the glyph.
     * @param maxX The label space right of the glyph.
     * @param maxY The label space bottom of the glyph.
     * @param info The glyph info.
     * @param detailIndex The detail index for the text color.
     */
    void addTransformedGlyph(
            float minX,
            float minY,
            float maxX,
            float maxY,
            @NonNull FontAtlas.CharInfo info,
            int detailIndex) {
        final TextTransform transform = textTransform;
        RectFloat labelClip = null;
        if (clipRects.size() > transform.clipDepth()) {
            labelClip = clipRects.peekFirst();
            if (labelClip != null
                    && (maxX <= labelClip.getLeft()
                            || maxY <= labelClip.getTop()
                            || minX >= labelClip.getRight()
                            || minY >= labelClip.getBottom())) {
                // Entirely clipped in label space
                return;
            }
        }

        // Rotate the corners to find the screen space bounding box
        final float[] corners = {minX, minY, maxX, minY, maxX, maxY, minX, maxY};
        float boxMinX = Float.MAX_VALUE;
        float boxMinY = Float.MAX_VALUE;
        float boxMaxX = -Float.MAX_VALUE;
        float boxMaxY = -Float.MAX_VALUE;
        for (int i = 0; i < corners.length; i += 2) {
            final float dx = corners[i] - transform.pivotInX();
            final float dy = corners[i + 1] - transform.pivotInY();
            final float x = transform.pivotOutX() + dx * transform.cos() - dy * transform.sin();
            final float y = transform.pivotOutY() + dx * transform.sin() + dy * transform.cos();
            corners[i] = x;
            corners[i + 1] = y;
            boxMinX = Math.min(boxMinX, x);
            boxMinY = Math.min(boxMinY, y);
            boxMaxX = Math.max(boxMaxX, x);
            boxMaxY = Math.max(boxMaxY, y);
        }
        if (!addScreenQuad(
                boxMinX - ANTI_ALIAS_PADDING,
                boxMinY - ANTI_ALIAS_PADDING,
                boxMaxX + ANTI_ALIAS_PADDING,
                boxMaxY + ANTI_ALIAS_PADDING,
                transform.screenClip())) {
            return;
        }

        // The rotated top left corner, with the rotation in misc
        final int pointIndex = addPoint(corners[0], corners[1], transform.cos(), transform.sin());
        addPoint(info.x, info.y, info.width, info.height);
        int pointCount = 2;
        if (labelClip != null) {
            // The visible part of the glyph, relative to the glyph top left
            addPoint(
                    labelClip.getLeft() - minX,
                    labelClip.getTop() - minY,
                    labelClip.getRight() - minX,
                    labelClip.getBottom() - minY);
            pointCount = 3;
        }
        addCommand(
                pointIndex, detailIndex, pointCount, 1, ElementType.TEXT, ElementStyle.TEXTURE, 0);
    }

    /**
     * Check if there are any fonts available to render text with.
     *
     * @return True if there is a current font or a fallback font.
     */
    private static boolean hasAnyFont() {
        return IkGui.getContext().font != null || !IkGui.getContext().fontFallbacks.isEmpty();
    }

    /**
     * Calculate the width of a single line of text, matching the layout that {@link #addText(int,
     * float, float, int, String)} uses. If no fonts are loaded, an estimate is returned instead.
     *
     * @param fontSize The font size.
     * @param text The text to measure, which should not contain newlines.
     * @return The width of the text in pixels.
     */
    public static float calcTextWidth(int fontSize, @NonNull String text) {
        if (text.isEmpty()) {
            return 0;
        }
        final float fontScale =
                (float) IkGui.getContext().dpiScaleScreen / IkGui.getContext().dpiScaleFont;
        if (!hasAnyFont()) {
            // Rough estimate so that layout still works without any fonts loaded
            return (float) Math.ceil(text.length() * fontSize * fontScale * 0.5f);
        }
        FontAtlas atlas = IkGui.getContext().io.fonts;
        final List<FontAtlas.CharInfo> infoList =
                layoutCharacters(atlas, text, 0, text.length(), fontSize);
        float penX = 0;
        for (int i = 0; i < infoList.size(); ++i) {
            penX += penAdvance(atlas, infoList, i, fontSize);
        }
        return (float) Math.ceil(penX);
    }

    /**
     * Register and look up the characters of some text, skipping any that can't be found.
     *
     * @param atlas The font atlas.
     * @param text The text.
     * @param start The start of the range (inclusive).
     * @param end The end of the range (exclusive).
     * @param fontSize The font size.
     * @return The info for each character that was found.
     */
    private static List<FontAtlas.CharInfo> layoutCharacters(
            @NonNull FontAtlas atlas,
            @NonNull CharSequence text,
            int start,
            int end,
            int fontSize) {
        final List<FontAtlas.CharInfo> infoList = new ArrayList<>(Math.max(0, end - start));
        for (int i = start; i < end; ++i) {
            final char c = text.charAt(i);
            atlas.registerCharacter(c, fontSize);
            atlas.getFontMapInfo(c, fontSize).ifPresent(infoList::add);
        }
        return infoList;
    }

    /**
     * Calculate the width of every prefix of a single line of text, matching the layout of {@link
     * #calcTextWidth(int, String)}. This is the same as calling calcTextWidth() on every prefix,
     * but in a single pass.
     *
     * @param fontSize The font size.
     * @param text The text to measure, the range of which should not contain newlines.
     * @param start The start of the range to measure (inclusive).
     * @param end The end of the range to measure (exclusive).
     * @return An array of size (end - start + 1), where element i is the width of the first i
     *     characters of the range.
     */
    public static float[] calcTextOffsets(
            int fontSize, @NonNull CharSequence text, int start, int end) {
        final int count = Math.max(0, end - start);
        final float[] offsets = new float[count + 1];
        if (count == 0) {
            return offsets;
        }
        final float fontScale =
                (float) IkGui.getContext().dpiScaleScreen / IkGui.getContext().dpiScaleFont;
        if (!hasAnyFont()) {
            for (int i = 1; i <= count; ++i) {
                offsets[i] = (float) Math.ceil(i * fontSize * fontScale * 0.5f);
            }
            return offsets;
        }
        FontAtlas atlas = IkGui.getContext().io.fonts;

        float penX = 0;
        for (int i = 0; i < count; ++i) {
            final char c = text.charAt(start + i);
            atlas.registerCharacter(c, fontSize);
            final Optional<FontAtlas.CharInfo> maybeInfo = atlas.getFontMapInfo(c, fontSize);
            if (maybeInfo.isPresent()) {
                penX += maybeInfo.get().advance;
                if (i + 1 < count) {
                    penX += atlas.getKerning(c, text.charAt(start + i + 1), fontSize);
                }
            }
            offsets[i + 1] = (float) Math.ceil(penX);
        }
        return offsets;
    }

    /**
     * Add text to the draw list, which may contain multiple lines, wrapping lines that are wider
     * than the wrap width.
     *
     * @param fontSize Font size.
     * @param posX X position to start drawing at.
     * @param posY Y position to start drawing at.
     * @param color Color of the text.
     * @param text Text to draw, which may contain newlines.
     * @param wrapWidth The width to wrap at, 0 or less for no wrapping.
     */
    public void addText(
            int fontSize,
            float posX,
            float posY,
            int color,
            @NonNull String text,
            float wrapWidth) {
        final String wrapped = wrapWidth > 0 ? wrapText(fontSize, text, wrapWidth) : text;
        final float lineHeight =
                (int)
                        (fontSize
                                * (float) IkGui.getContext().dpiScaleScreen
                                / IkGui.getContext().dpiScaleFont);
        float y = posY;
        int lineStart = 0;
        while (lineStart <= wrapped.length()) {
            int lineEnd = wrapped.indexOf('\n', lineStart);
            if (lineEnd < 0) {
                lineEnd = wrapped.length();
            }
            if (lineEnd > lineStart) {
                addText(fontSize, posX, y, color, wrapped.substring(lineStart, lineEnd));
            }
            lineStart = lineEnd + 1;
            y += lineHeight;
        }
    }

    /** Character class for word wrapping: anything that isn't a blank or punctuation. */
    private static final int WRAP_CLASS_OTHER = 0;

    /** Character class for word wrapping: spaces and tabs. */
    private static final int WRAP_CLASS_BLANK = 1;

    /** Character class for word wrapping: punctuation that lines can break after. */
    private static final int WRAP_CLASS_PUNCT = 2;

    /**
     * Classify a character for word wrapping. The separators are hardcoded, like upstream: blanks
     * are space, tab and the ideographic space, punctuation is .,;!?" and the ideographic comma and
     * full stop.
     *
     * @param c The character.
     * @return The character class.
     */
    private static int wrapCharClass(char c) {
        return switch (c) {
            case ' ', '\t', '　' -> WRAP_CLASS_BLANK;
            case '.', ',', ';', '!', '?', '"', '、', '。' -> WRAP_CLASS_PUNCT;
            default -> WRAP_CLASS_OTHER;
        };
    }

    /**
     * Find where to wrap a line of text, like Dear ImGui's ImFontCalcWordWrapPositionEx(). This is
     * simple word wrapping for English:
     *
     * <ul>
     *   <li>Lines break at blanks, and after punctuation unless a digit follows, so "1.5" stays
     *       together. The possible wrap points in "aaa bbb, ccc,ddd. eee fff. ggg!" are after
     *       "aaa", "bbb,", "ccc,", "ddd.", "eee" and "fff.".
     *   <li>Blanks at the end of a line don't count toward its width, unless keeping blanks.
     *   <li>Words that can't fit on a line at all are cut, so "The tropical fish" at about 5
     *       characters wide becomes "The tr", "opical", "fish".
     *   <li>When the width is too small for anything, one character is kept on the line.
     * </ul>
     *
     * <p>Wrapping stops at a newline, which is returned as the position. Continue with {@link
     * #calcWordWrapNextLineStart(CharSequence, int, int, boolean)} to find the start of the next
     * line.
     *
     * @param fontSize The font size.
     * @param text The text.
     * @param start The start of the line.
     * @param end The end of the text.
     * @param wrapWidth The width to wrap at, in pixels.
     * @param keepBlanks Whether blanks stay at the end of lines and count toward their width, which
     *     input text uses so every character is shown somewhere. Dear ImGui calls this
     *     ImDrawTextFlags_WrapKeepBlanks.
     * @return The position to wrap at, which is the end of the line's text.
     */
    public static int calcWordWrapPosition(
            int fontSize,
            @NonNull CharSequence text,
            int start,
            int end,
            float wrapWidth,
            boolean keepBlanks) {
        // Only measure up to the next newline, since wrapping stops there
        int lineEnd = start;
        while (lineEnd < end && text.charAt(lineEnd) != '\n') {
            ++lineEnd;
        }
        final float[] offsets = calcTextOffsets(fontSize, text, start, lineEnd);

        float lineWidth = 0.0f;
        float blankWidth = 0.0f;
        int spanEnd = start;
        float spanWidth = 0.0f;
        int prevType = WRAP_CLASS_OTHER;

        int s = start;
        while (s < lineEnd) {
            final char c = text.charAt(s);
            if (c == '\r') {
                ++s;
                continue;
            }
            final float charWidth = offsets[s - start + 1] - offsets[s - start];
            final int currType = wrapCharClass(c);

            if (currType == WRAP_CLASS_BLANK) {
                // End a span: 'A ' or '. '
                if (prevType != WRAP_CLASS_BLANK && !keepBlanks) {
                    spanEnd = s;
                    lineWidth += spanWidth;
                    spanWidth = 0.0f;
                }
                blankWidth += charWidth;
            } else {
                if (prevType == WRAP_CLASS_PUNCT
                        && currType != WRAP_CLASS_PUNCT
                        && !(c >= '0' && c <= '9')) {
                    // End a span: '.X', unless X is a digit
                    spanEnd = s;
                    lineWidth += spanWidth + blankWidth;
                    spanWidth = 0.0f;
                    blankWidth = 0.0f;
                } else if (prevType == WRAP_CLASS_BLANK && keepBlanks) {
                    // End a span: 'A ' or '. '
                    spanEnd = s;
                    lineWidth += spanWidth + blankWidth;
                    spanWidth = 0.0f;
                    blankWidth = 0.0f;
                }
                spanWidth += charWidth;
            }

            if (spanWidth + blankWidth + lineWidth > wrapWidth) {
                if (spanWidth + blankWidth > wrapWidth) {
                    // The span can't fit on a line at all, so cut it here
                    break;
                }
                return spanEnd;
            }

            prevType = currType;
            ++s;
        }

        if (s == lineEnd && lineEnd < end) {
            // Stopped at a newline
            return s;
        }
        // The width is too small to fit anything. Keep one character to minimize the height
        // discontinuity.
        if (s == start && start < end) {
            int next = start + 1;
            if (next < end
                    && Character.isHighSurrogate(text.charAt(start))
                    && Character.isLowSurrogate(text.charAt(next))) {
                ++next;
            }
            return next;
        }
        return s;
    }

    /**
     * Find the start of the next line after a wrap position, like Dear ImGui's
     * ImTextCalcWordWrapNextLineStart(). Unless keeping blanks, this skips the blanks after the
     * wrap position, then it skips one newline.
     *
     * @param text The text.
     * @param position The wrap position.
     * @param end The end of the text.
     * @param keepBlanks Whether blanks are kept at the end of lines.
     * @return The start of the next line.
     */
    public static int calcWordWrapNextLineStart(
            @NonNull CharSequence text, int position, int end, boolean keepBlanks) {
        int s = position;
        if (!keepBlanks) {
            while (s < end && (text.charAt(s) == ' ' || text.charAt(s) == '\t')) {
                ++s;
            }
        }
        if (s < end && text.charAt(s) == '\n') {
            ++s;
        }
        return s;
    }

    /**
     * Insert line breaks into text so that no line is wider than the wrap width, using the same
     * rules as Dear ImGui (see {@link #calcWordWrapPosition(int, CharSequence, int, int, float,
     * boolean)}). Blanks where lines wrap are removed.
     *
     * @param fontSize The font size.
     * @param text The text to wrap.
     * @param wrapWidth The maximum line width in pixels.
     * @return The wrapped text.
     */
    public static String wrapText(int fontSize, @NonNull String text, float wrapWidth) {
        final int length = text.length();
        final StringBuilder result = new StringBuilder(length + 16);
        int s = 0;
        while (s < length) {
            final int eol = calcWordWrapPosition(fontSize, text, s, length, wrapWidth, false);
            result.append(text, s, eol);
            if (eol >= length) {
                break;
            }
            result.append('\n');
            s = calcWordWrapNextLineStart(text, eol, length, false);
        }
        return result.toString();
    }

    /**
     * Add a line through a series of points. Open lines are drawn as separate segments with round
     * caps, closed lines are drawn as the outline of a polygon.
     *
     * @param points The points.
     * @param pointCount The number of points to use from the array.
     * @param color The color.
     * @param closed Whether to connect the last point back to the first.
     * @param thickness The thickness of the line.
     */
    public void addPolyline(
            @NonNull Vector2f[] points,
            int pointCount,
            int color,
            boolean closed,
            float thickness) {
        addPolyline(toCoordinates(points, pointCount), pointCount, color, closed, thickness);
    }

    /**
     * Add a line through a series of points. Open lines are drawn as separate segments with round
     * caps, closed lines are drawn as the outline of a polygon.
     *
     * @param coordinates The points, as x, y pairs.
     * @param pointCount The number of points to use from the array.
     * @param color The color.
     * @param closed Whether to connect the last point back to the first.
     * @param thickness The thickness of the line.
     */
    public void addPolyline(
            @NonNull float[] coordinates,
            int pointCount,
            int color,
            boolean closed,
            float thickness) {
        if (pointCount < 2) {
            return;
        }
        if (closed && pointCount >= 3) {
            addPolygon(coordinates, pointCount, color, ElementStyle.BORDER, thickness, 0.0f);
            return;
        }
        for (int i = 0; i + 1 < pointCount; ++i) {
            addLine(
                    coordinates[i * 2],
                    coordinates[i * 2 + 1],
                    coordinates[i * 2 + 2],
                    coordinates[i * 2 + 3],
                    color,
                    thickness);
        }
        if (closed) {
            // Only two points, so close it with a line back to the start
            addLine(
                    coordinates[2],
                    coordinates[3],
                    coordinates[0],
                    coordinates[1],
                    color,
                    thickness);
        }
    }

    public void addConvexPolyFilled(@NonNull Vector2f[] points, int pointCount, int color) {
        addConvexPolyFilled(points, pointCount, color, 0.0f);
    }

    /**
     * Add a filled convex polygon.
     *
     * @param points The points, in order.
     * @param pointCount The number of points to use from the array.
     * @param color The color.
     * @param alphaRadius The radius for blending to transparent.
     */
    public void addConvexPolyFilled(
            @NonNull Vector2f[] points, int pointCount, int color, float alphaRadius) {
        addPolygon(
                toCoordinates(points, pointCount),
                pointCount,
                color,
                ElementStyle.FILL,
                0.0f,
                alphaRadius);
    }

    /**
     * Add a filled concave polygon. Since we render with signed distance fields, this is the same
     * as {@link #addConvexPolyFilled(Vector2f[], int, int)}, but the points should not
     * self-intersect.
     *
     * @param points The points, in order.
     * @param pointCount The number of points to use from the array.
     * @param color The color.
     */
    public void addConcavePolyFilled(@NonNull Vector2f[] points, int pointCount, int color) {
        addPolygon(
                toCoordinates(points, pointCount),
                pointCount,
                color,
                ElementStyle.FILL,
                0.0f,
                0.0f);
    }

    /**
     * Add a polygon command with an arbitrary number of points.
     *
     * @param coordinates The points, as x, y pairs.
     * @param pointCount The number of points to use.
     * @param color The color.
     * @param style Whether to fill or outline the polygon.
     * @param thickness The thickness of the outline.
     * @param alphaRadius The radius for blending to transparent.
     */
    private void addPolygon(
            @NonNull float[] coordinates,
            int pointCount,
            int color,
            @NonNull ElementStyle style,
            float thickness,
            float alphaRadius) {
        if (pointCount < 3 || coordinates.length < pointCount * 2) {
            log.warn("Invalid polygon with {} points", pointCount);
            return;
        }
        final float padding =
                (style == ElementStyle.BORDER ? thickness / 2 : 0.0f) + ANTI_ALIAS_PADDING;
        if (!addBoundingQuad(padding, Arrays.copyOf(coordinates, pointCount * 2))) {
            return;
        }

        int pointIndex = addPoint(coordinates[0], coordinates[1], 0, 0);
        for (int i = 1; i < pointCount; ++i) {
            addPoint(coordinates[i * 2], coordinates[i * 2 + 1], 0, 0);
        }
        final int detailIndex =
                addDetails(new SDFPointDetail(0.0f, alphaRadius, color, Color.CLEAR));

        addCommand(
                pointIndex,
                detailIndex,
                pointCount,
                1,
                ElementType.POLYGON,
                style,
                style == ElementStyle.BORDER ? thickness : 0.0f);
    }

    /**
     * Convert points to a flat array of x, y pairs.
     *
     * @param points The points.
     * @param pointCount The number of points to use.
     * @return The coordinates.
     */
    private static float[] toCoordinates(@NonNull Vector2f[] points, int pointCount) {
        final int count = Math.min(pointCount, points.length);
        float[] coordinates = new float[count * 2];
        for (int i = 0; i < count; ++i) {
            coordinates[i * 2] = points[i].x;
            coordinates[i * 2 + 1] = points[i].y;
        }
        return coordinates;
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

    /**
     * Add a cubic bezier curve.
     *
     * @param p1X The x coordinate of the start.
     * @param p1Y The y coordinate of the start.
     * @param p2X The x coordinate of the first control point.
     * @param p2Y The y coordinate of the first control point.
     * @param p3X The x coordinate of the second control point.
     * @param p3Y The y coordinate of the second control point.
     * @param p4X The x coordinate of the end.
     * @param p4Y The y coordinate of the end.
     * @param color The color.
     * @param thickness The thickness of the line.
     * @param segmentCount Ignored, curves are rendered smoothly with signed distance fields.
     */
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
        if (!Float.isFinite(thickness) || thickness <= 0) {
            log.warn("Invalid thickness {}", thickness);
            return;
        }
        // The curve is always within the bounds of the control points
        if (!addBoundingQuad(
                thickness / 2 + ANTI_ALIAS_PADDING, p1X, p1Y, p2X, p2Y, p3X, p3Y, p4X, p4Y)) {
            return;
        }
        int pointIndex = addPoint(p1X, p1Y, 0, 0);
        addPoint(p2X, p2Y, 0, 0);
        addPoint(p3X, p3Y, 0, 0);
        addPoint(p4X, p4Y, 0, 0);
        final int detailIndex = addDetails(new SDFPointDetail(0.0f, 0.0f, color, Color.CLEAR));
        addCommand(
                pointIndex,
                detailIndex,
                4,
                1,
                ElementType.LINE_BEZIER,
                ElementStyle.FILL,
                thickness);
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

    /**
     * Add a quadratic bezier curve.
     *
     * @param p1X The x coordinate of the start.
     * @param p1Y The y coordinate of the start.
     * @param p2X The x coordinate of the control point.
     * @param p2Y The y coordinate of the control point.
     * @param p3X The x coordinate of the end.
     * @param p3Y The y coordinate of the end.
     * @param color The color.
     * @param thickness The thickness of the line.
     * @param segmentCount Ignored, curves are rendered smoothly with signed distance fields.
     */
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
        if (!Float.isFinite(thickness) || thickness <= 0) {
            log.warn("Invalid thickness {}", thickness);
            return;
        }
        // The curve is always within the bounds of the control points
        if (!addBoundingQuad(thickness / 2 + ANTI_ALIAS_PADDING, p1X, p1Y, p2X, p2Y, p3X, p3Y)) {
            return;
        }
        int pointIndex = addPoint(p1X, p1Y, 0, 0);
        addPoint(p2X, p2Y, 0, 0);
        addPoint(p3X, p3Y, 0, 0);
        final int detailIndex = addDetails(new SDFPointDetail(0.0f, 0.0f, color, Color.CLEAR));
        addCommand(
                pointIndex,
                detailIndex,
                3,
                1,
                ElementType.LINE_BEZIER,
                ElementStyle.FILL,
                thickness);
    }

    /**
     * Add an arc of a circle, as a line with round caps. Angles are in radians, clockwise from the
     * positive x-axis (since y points down on screen).
     *
     * @param centerX The x coordinate of the center.
     * @param centerY The y coordinate of the center.
     * @param radius The radius.
     * @param minAngle The start angle, in radians.
     * @param maxAngle The end angle, in radians.
     * @param color The color.
     * @param thickness The thickness of the line.
     */
    public void addArc(
            float centerX,
            float centerY,
            float radius,
            float minAngle,
            float maxAngle,
            int color,
            float thickness) {
        addArc(centerX, centerY, radius, minAngle, maxAngle, color, color, thickness);
    }

    /**
     * Add an arc of a circle, as a line with round caps, with a color gradient along the arc.
     * Angles are in radians, clockwise from the positive x-axis (since y points down on screen).
     *
     * @param centerX The x coordinate of the center.
     * @param centerY The y coordinate of the center.
     * @param radius The radius.
     * @param minAngle The start angle, in radians.
     * @param maxAngle The end angle, in radians. Should be larger than the start angle.
     * @param minColor The color at the start angle.
     * @param maxColor The color at the end angle.
     * @param thickness The thickness of the line.
     */
    public void addArc(
            float centerX,
            float centerY,
            float radius,
            float minAngle,
            float maxAngle,
            int minColor,
            int maxColor,
            float thickness) {
        if (!Float.isFinite(thickness) || thickness <= 0 || radius <= 0) {
            log.warn("Invalid thickness {} or radius {}", thickness, radius);
            return;
        }
        if (!addBoundingQuad(
                thickness / 2 + ANTI_ALIAS_PADDING,
                centerX - radius,
                centerY - radius,
                centerX + radius,
                centerY + radius)) {
            return;
        }
        int pointIndex = addPoint(centerX, centerY, radius, radius);
        addPoint(minAngle, maxAngle, 0, 0);
        final int detailIndex;
        final int detailCount;
        if (minColor == maxColor) {
            detailIndex = addDetails(new SDFPointDetail(0.0f, 0.0f, minColor, Color.CLEAR));
            detailCount = 1;
        } else {
            detailIndex =
                    addDetails(
                            new SDFPointDetail(0.0f, 0.0f, minColor, Color.CLEAR),
                            new SDFPointDetail(0.0f, 0.0f, maxColor, Color.CLEAR));
            detailCount = 2;
        }
        addCommand(
                pointIndex,
                detailIndex,
                2,
                detailCount,
                ElementType.LINE_ARC,
                ElementStyle.FILL,
                thickness);
    }

    public void addImage(
            @NonNull TextureInfo texture, float minX, float minY, float maxX, float maxY) {
        addImage(texture, minX, minY, maxX, maxY, 0, 0, 1, 1, Color.WHITE);
    }

    public void addImage(
            @NonNull TextureInfo texture,
            float minX,
            float minY,
            float maxX,
            float maxY,
            float minU,
            float minV,
            float maxU,
            float maxV) {
        addImage(texture, minX, minY, maxX, maxY, minU, minV, maxU, maxV, Color.WHITE);
    }

    public void addImage(
            @NonNull TextureInfo texture,
            float minX,
            float minY,
            float maxX,
            float maxY,
            float minU,
            float minV,
            float maxU,
            float maxV,
            int tint) {
        addImage(texture, minX, minY, maxX, maxY, minU, minV, maxU, maxV, tint, 0.0f);
    }

    /**
     * Add an image in an axis aligned rectangle.
     *
     * @param texture The texture to draw.
     * @param minX The left edge.
     * @param minY The top edge.
     * @param maxX The right edge.
     * @param maxY The bottom edge.
     * @param minU The texture coordinate at the left edge.
     * @param minV The texture coordinate at the top edge.
     * @param maxU The texture coordinate at the right edge.
     * @param maxV The texture coordinate at the bottom edge.
     * @param tint The color to multiply the texture by.
     * @param alphaRadius The radius for blending to transparent.
     */
    public void addImage(
            @NonNull TextureInfo texture,
            float minX,
            float minY,
            float maxX,
            float maxY,
            float minU,
            float minV,
            float maxU,
            float maxV,
            int tint,
            float alphaRadius) {
        addImageRounded(
                texture,
                minX,
                minY,
                maxX,
                maxY,
                minU,
                minV,
                maxU,
                maxV,
                tint,
                0.0f,
                ROUND_CORNERS_NONE,
                alphaRadius);
    }

    public void addImageQuad(
            @NonNull TextureInfo texture,
            float p1X,
            float p1Y,
            float p2X,
            float p2Y,
            float p3X,
            float p3Y,
            float p4X,
            float p4Y) {
        addImageQuad(texture, p1X, p1Y, p2X, p2Y, p3X, p3Y, p4X, p4Y, Color.WHITE);
    }

    public void addImageQuad(
            @NonNull TextureInfo texture,
            float p1X,
            float p1Y,
            float p2X,
            float p2Y,
            float p3X,
            float p3Y,
            float p4X,
            float p4Y,
            int tint) {
        addImageQuad(texture, p1X, p1Y, p2X, p2Y, p3X, p3Y, p4X, p4Y, 0, 0, 1, 0, 1, 1, 0, 1, tint);
    }

    public void addImageQuad(
            @NonNull TextureInfo texture,
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
            int tint) {
        addImageQuad(
                texture, p1X, p1Y, p2X, p2Y, p3X, p3Y, p4X, p4Y, u1, v1, u2, v2, u3, v3, u4, v4,
                tint, 0.0f);
    }

    /**
     * Add an image mapped onto an arbitrary quad. The points should be in order around the quad, by
     * default the texture coordinates map p1 to the top left, p2 top right, p3 bottom right, p4
     * bottom left.
     *
     * @param texture The texture to draw.
     * @param p1X The x coordinate of the first point.
     * @param p1Y The y coordinate of the first point.
     * @param p2X The x coordinate of the second point.
     * @param p2Y The y coordinate of the second point.
     * @param p3X The x coordinate of the third point.
     * @param p3Y The y coordinate of the third point.
     * @param p4X The x coordinate of the fourth point.
     * @param p4Y The y coordinate of the fourth point.
     * @param u1 The u texture coordinate of the first point.
     * @param v1 The v texture coordinate of the first point.
     * @param u2 The u texture coordinate of the second point.
     * @param v2 The v texture coordinate of the second point.
     * @param u3 The u texture coordinate of the third point.
     * @param v3 The v texture coordinate of the third point.
     * @param u4 The u texture coordinate of the fourth point.
     * @param v4 The v texture coordinate of the fourth point.
     * @param tint The color to multiply the texture by.
     * @param alphaRadius The radius for blending to transparent.
     */
    public void addImageQuad(
            @NonNull TextureInfo texture,
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
            int tint,
            float alphaRadius) {
        if (!addBoundingQuad(ANTI_ALIAS_PADDING, p1X, p1Y, p2X, p2Y, p3X, p3Y, p4X, p4Y)) {
            return;
        }
        final int textureIndex = IkGui.getContext().drawTextures.register(texture);

        int pointIndex = addPoint(p1X, p1Y, u1, v1);
        addPoint(p2X, p2Y, u2, v2);
        addPoint(p3X, p3Y, u3, v3);
        addPoint(p4X, p4Y, u4, v4);
        final int detailIndex =
                addDetails(new SDFPointDetail(0.0f, alphaRadius, textureIndex, tint));

        final int pointCount = 4;
        final int detailCount = 1;
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

    public void addImageRounded(
            @NonNull TextureInfo texture,
            float minX,
            float minY,
            float maxX,
            float maxY,
            float minU,
            float minV,
            float maxU,
            float maxV,
            int tint,
            float rounding) {
        addImageRounded(
                texture,
                minX,
                minY,
                maxX,
                maxY,
                minU,
                minV,
                maxU,
                maxV,
                tint,
                rounding,
                ROUND_CORNERS_ALL,
                0.0f);
    }

    public void addImageRounded(
            @NonNull TextureInfo texture,
            float minX,
            float minY,
            float maxX,
            float maxY,
            float minU,
            float minV,
            float maxU,
            float maxV,
            int tint,
            float rounding,
            int drawFlagsRoundingCorners) {
        addImageRounded(
                texture,
                minX,
                minY,
                maxX,
                maxY,
                minU,
                minV,
                maxU,
                maxV,
                tint,
                rounding,
                drawFlagsRoundingCorners,
                0.0f);
    }

    /**
     * Add an image in an axis aligned rectangle with rounded corners.
     *
     * @param texture The texture to draw.
     * @param minX The left edge.
     * @param minY The top edge.
     * @param maxX The right edge.
     * @param maxY The bottom edge.
     * @param minU The texture coordinate at the left edge.
     * @param minV The texture coordinate at the top edge.
     * @param maxU The texture coordinate at the right edge.
     * @param maxV The texture coordinate at the bottom edge.
     * @param tint The color to multiply the texture by.
     * @param rounding The radius of the rounded corners.
     * @param drawFlagsRoundingCorners Which corners to round.
     * @param alphaRadius The radius for blending to transparent.
     * @see DrawFlags
     */
    public void addImageRounded(
            @NonNull TextureInfo texture,
            float minX,
            float minY,
            float maxX,
            float maxY,
            float minU,
            float minV,
            float maxU,
            float maxV,
            int tint,
            float rounding,
            int drawFlagsRoundingCorners,
            float alphaRadius) {
        if (rounding < 0) {
            log.warn("Invalid rounding {} in addImageRounded", rounding);
            return;
        }
        if (!addScreenQuad(
                minX - FILL_EDGE_PADDING,
                minY - FILL_EDGE_PADDING,
                maxX + FILL_EDGE_PADDING,
                maxY + FILL_EDGE_PADDING)) {
            return;
        }
        final int textureIndex = IkGui.getContext().drawTextures.register(texture);

        SDFPointDetail[] details =
                getSdfPointDetails(
                        textureIndex, rounding, alphaRadius, drawFlagsRoundingCorners, tint);

        // The rectangle, then the texture coordinates of the top left and bottom right
        int pointIndex = addPoint((minX + maxX) / 2, (minY + maxY) / 2, maxX - minX, maxY - minY);
        addPoint(minU, minV, maxU, maxV);
        int detailIndex = addDetails(details);

        final int pointCount = 2;
        final int detailCount = details.length;
        final int borderStroke = 0;
        addCommand(
                pointIndex,
                detailIndex,
                pointCount,
                detailCount,
                ElementType.RECTANGLE,
                ElementStyle.TEXTURE,
                borderStroke);
    }

    /** Clear the current path, discarding any points in it. */
    public void pathClear() {
        path.clear();
    }

    /**
     * Add a point to the current path.
     *
     * @param posX The x coordinate.
     * @param posY The y coordinate.
     */
    public void pathLineTo(float posX, float posY) {
        path.addFloat(posX);
        path.addFloat(posY);
    }

    /**
     * Add a point to the current path, unless it is the same as the last point.
     *
     * @param posX The x coordinate.
     * @param posY The y coordinate.
     */
    public void pathLineToMergeDuplicate(float posX, float posY) {
        final int size = path.size();
        if (size >= 2 && path.getFloat(size - 2) == posX && path.getFloat(size - 1) == posY) {
            return;
        }
        pathLineTo(posX, posY);
    }

    /**
     * Fill the current path as a convex polygon, then clear the path.
     *
     * @param color The color.
     */
    public void pathFillConvex(int color) {
        addPolygon(pathCoordinates(), path.size() / 2, color, ElementStyle.FILL, 0.0f, 0.0f);
        path.clear();
    }

    /**
     * Fill the current path as a concave polygon, then clear the path. The path should not
     * self-intersect.
     *
     * @param color The color.
     */
    public void pathFillConcave(int color) {
        // The SDF approach handles concave polygons the same way
        pathFillConvex(color);
    }

    public void pathStroke(int color, boolean closed) {
        pathStroke(color, closed, 1.0f);
    }

    /**
     * Draw a line along the current path, then clear the path.
     *
     * @param color The color.
     * @param closed Whether to connect the end of the path back to the start.
     * @param thickness The thickness of the line.
     */
    public void pathStroke(int color, boolean closed, float thickness) {
        addPolyline(pathCoordinates(), path.size() / 2, color, closed, thickness);
        path.clear();
    }

    public void pathArcTo(float centerX, float centerY, float radius, float minA, float maxA) {
        pathArcTo(centerX, centerY, radius, minA, maxA, 0);
    }

    /**
     * Add an arc of a circle to the current path. Angles are in radians, clockwise from the
     * positive x-axis (since y points down on screen).
     *
     * @param centerX The x coordinate of the center.
     * @param centerY The y coordinate of the center.
     * @param radius The radius.
     * @param minA The start angle.
     * @param maxA The end angle.
     * @param segmentCount The number of segments, or 0 to calculate an appropriate amount.
     */
    public void pathArcTo(
            float centerX, float centerY, float radius, float minA, float maxA, int segmentCount) {
        if (radius < 0.5f) {
            pathLineTo(centerX, centerY);
            return;
        }
        if (segmentCount <= 0) {
            segmentCount = calcArcSegmentCount(radius, Math.abs(maxA - minA));
        }
        for (int i = 0; i <= segmentCount; ++i) {
            final double angle = minA + ((double) i / segmentCount) * (maxA - minA);
            pathLineTo(
                    centerX + (float) Math.cos(angle) * radius,
                    centerY + (float) Math.sin(angle) * radius);
        }
    }

    /**
     * Add an arc of a circle to the current path, using angles in twelfths of a circle. Useful for
     * corners, for example 0 to 3 is the bottom right quarter, 6 to 9 is the top left.
     *
     * @param centerX The x coordinate of the center.
     * @param centerY The y coordinate of the center.
     * @param radius The radius.
     * @param minAMult12 The start angle in twelfths of a circle.
     * @param maxAMult12 The end angle in twelfths of a circle.
     */
    public void pathArcToFast(
            float centerX, float centerY, float radius, int minAMult12, int maxAMult12) {
        final float twelfth = (float) (Math.PI * 2.0 / 12.0);
        pathArcTo(centerX, centerY, radius, minAMult12 * twelfth, maxAMult12 * twelfth, 0);
    }

    public void pathBezierCubicCurveTo(
            float p2X, float p2Y, float p3X, float p3Y, float p4X, float p4Y) {
        pathBezierCubicCurveTo(p2X, p2Y, p3X, p3Y, p4X, p4Y, 0);
    }

    /**
     * Add a cubic bezier curve to the current path, starting from the last point in the path.
     *
     * @param p2X The x coordinate of the first control point.
     * @param p2Y The y coordinate of the first control point.
     * @param p3X The x coordinate of the second control point.
     * @param p3Y The y coordinate of the second control point.
     * @param p4X The x coordinate of the end.
     * @param p4Y The y coordinate of the end.
     * @param segmentCount The number of segments, or 0 to calculate an appropriate amount.
     */
    public void pathBezierCubicCurveTo(
            float p2X, float p2Y, float p3X, float p3Y, float p4X, float p4Y, int segmentCount) {
        if (path.size() < 2) {
            log.warn("pathBezierCubicCurveTo() needs a starting point in the path");
            return;
        }
        final float p1X = path.getFloat(path.size() - 2);
        final float p1Y = path.getFloat(path.size() - 1);
        if (segmentCount <= 0) {
            final float length =
                    distance(p1X, p1Y, p2X, p2Y)
                            + distance(p2X, p2Y, p3X, p3Y)
                            + distance(p3X, p3Y, p4X, p4Y);
            segmentCount = MathUtil.clamp((int) Math.ceil(length / 4.0f), 4, MAX_CURVE_SEGMENTS);
        }
        for (int i = 1; i <= segmentCount; ++i) {
            final float t = (float) i / segmentCount;
            final float u = 1.0f - t;
            final float w1 = u * u * u;
            final float w2 = 3 * u * u * t;
            final float w3 = 3 * u * t * t;
            final float w4 = t * t * t;
            pathLineTo(
                    w1 * p1X + w2 * p2X + w3 * p3X + w4 * p4X,
                    w1 * p1Y + w2 * p2Y + w3 * p3Y + w4 * p4Y);
        }
    }

    public void pathBezierQuadraticCurveTo(float p2X, float p2Y, float p3X, float p3Y) {
        pathBezierQuadraticCurveTo(p2X, p2Y, p3X, p3Y, 0);
    }

    /**
     * Add a quadratic bezier curve to the current path, starting from the last point in the path.
     *
     * @param p2X The x coordinate of the control point.
     * @param p2Y The y coordinate of the control point.
     * @param p3X The x coordinate of the end.
     * @param p3Y The y coordinate of the end.
     * @param segmentCount The number of segments, or 0 to calculate an appropriate amount.
     */
    public void pathBezierQuadraticCurveTo(
            float p2X, float p2Y, float p3X, float p3Y, int segmentCount) {
        if (path.size() < 2) {
            log.warn("pathBezierQuadraticCurveTo() needs a starting point in the path");
            return;
        }
        final float p1X = path.getFloat(path.size() - 2);
        final float p1Y = path.getFloat(path.size() - 1);
        if (segmentCount <= 0) {
            final float length = distance(p1X, p1Y, p2X, p2Y) + distance(p2X, p2Y, p3X, p3Y);
            segmentCount = MathUtil.clamp((int) Math.ceil(length / 4.0f), 4, MAX_CURVE_SEGMENTS);
        }
        for (int i = 1; i <= segmentCount; ++i) {
            final float t = (float) i / segmentCount;
            final float u = 1.0f - t;
            final float w1 = u * u;
            final float w2 = 2 * u * t;
            final float w3 = t * t;
            pathLineTo(w1 * p1X + w2 * p2X + w3 * p3X, w1 * p1Y + w2 * p2Y + w3 * p3Y);
        }
    }

    public void pathRect(float minX, float minY, float maxX, float maxY) {
        pathRect(minX, minY, maxX, maxY, 0.0f, DrawFlags.ROUND_CORNERS_ALL);
    }

    public void pathRect(float minX, float minY, float maxX, float maxY, float rounding) {
        pathRect(minX, minY, maxX, maxY, rounding, DrawFlags.ROUND_CORNERS_ALL);
    }

    /**
     * Add a (possibly rounded) rectangle to the current path, clockwise starting from the top left.
     *
     * @param minX The left edge.
     * @param minY The top edge.
     * @param maxX The right edge.
     * @param maxY The bottom edge.
     * @param rounding The radius of the rounded corners.
     * @param drawFlagsRoundingCorners Which corners to round.
     * @see DrawFlags
     */
    public void pathRect(
            float minX,
            float minY,
            float maxX,
            float maxY,
            float rounding,
            int drawFlagsRoundingCorners) {
        if ((drawFlagsRoundingCorners & ROUND_CORNERS_MASK) == 0
                || (drawFlagsRoundingCorners & ROUND_CORNERS_NONE) != 0) {
            drawFlagsRoundingCorners = ROUND_CORNERS_NONE;
        }
        // Rounding can't be bigger than half the size of the rectangle
        rounding = Math.min(rounding, Math.abs(maxX - minX) * 0.5f);
        rounding = Math.min(rounding, Math.abs(maxY - minY) * 0.5f);

        if (rounding < 0.5f || (drawFlagsRoundingCorners & ROUND_CORNERS_NONE) != 0) {
            pathLineTo(minX, minY);
            pathLineTo(maxX, minY);
            pathLineTo(maxX, maxY);
            pathLineTo(minX, maxY);
            return;
        }
        final float topLeft =
                (drawFlagsRoundingCorners & ROUND_CORNERS_TOP_LEFT) != 0 ? rounding : 0.0f;
        final float topRight =
                (drawFlagsRoundingCorners & ROUND_CORNERS_TOP_RIGHT) != 0 ? rounding : 0.0f;
        final float bottomRight =
                (drawFlagsRoundingCorners & ROUND_CORNERS_BOTTOM_RIGHT) != 0 ? rounding : 0.0f;
        final float bottomLeft =
                (drawFlagsRoundingCorners & ROUND_CORNERS_BOTTOM_LEFT) != 0 ? rounding : 0.0f;
        pathArcToFast(minX + topLeft, minY + topLeft, topLeft, 6, 9);
        pathArcToFast(maxX - topRight, minY + topRight, topRight, 9, 12);
        pathArcToFast(maxX - bottomRight, maxY - bottomRight, bottomRight, 0, 3);
        pathArcToFast(minX + bottomLeft, maxY - bottomLeft, bottomLeft, 3, 6);
    }

    /**
     * Fetch the current path as a flat array of x, y pairs.
     *
     * @return The path coordinates.
     */
    private float[] pathCoordinates() {
        float[] coordinates = new float[path.size()];
        for (int i = 0; i < coordinates.length; ++i) {
            coordinates[i] = path.getFloat(i);
        }
        return coordinates;
    }

    /**
     * Calculate how many line segments we need to approximate an arc closely enough.
     *
     * @param radius The radius of the arc.
     * @param angle The angle covered by the arc, in radians.
     * @return The number of segments.
     */
    private static int calcArcSegmentCount(float radius, float angle) {
        // The angle of each segment such that the distance from the chord to the arc is at most
        // the max error
        final double segmentAngle =
                2.0 * Math.acos(Math.max(-1.0, 1.0 - CURVE_TESSELLATION_MAX_ERROR / radius));
        if (segmentAngle <= 0) {
            return MAX_CURVE_SEGMENTS;
        }
        return MathUtil.clamp((int) Math.ceil(angle / segmentAngle), 1, MAX_CURVE_SEGMENTS);
    }

    private static float distance(float x1, float y1, float x2, float y2) {
        final float dx = x2 - x1;
        final float dy = y2 - y1;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
}
