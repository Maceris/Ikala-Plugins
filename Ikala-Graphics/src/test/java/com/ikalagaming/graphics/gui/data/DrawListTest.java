package com.ikalagaming.graphics.gui.data;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ikalagaming.graphics.TextureInfo;
import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.gui.util.Color;

import org.joml.Vector2f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.function.Consumer;

/** Tests for the data the draw list sets up for the shaders. */
class DrawListTest {

    /** Vertices per quad, two triangles. */
    private static final int VERTICES_PER_QUAD = 6;

    /** Bytes per vertex, x and y floats. */
    private static final int BYTES_PER_VERTEX = 2 * Float.BYTES;

    /** A single command read back out of the command buffer. */
    private record Command(
            int pointIndex,
            int detailIndex,
            int pointCount,
            int detailCount,
            DrawList.ElementType type,
            DrawList.ElementStyle style,
            float stroke) {}

    private DrawList drawList;

    @BeforeEach
    void setUp() {
        IkGui.createContext();
        IkGui.getIO().displaySize.set(1000, 1000);
        drawList = new DrawList("Test");
    }

    @AfterEach
    void tearDown() {
        IkGui.destroyContext();
    }

    /**
     * Run some drawing, then read back the commands, checking that every command has exactly one
     * quad.
     *
     * @param draw The drawing to do.
     * @return The commands that were added.
     */
    private Command[] draw(Consumer<DrawList> draw) {
        drawList.clear();
        draw.accept(drawList);
        drawList.prepareForRender();

        final int commandCount = drawList.commandBuffer.limit() / DrawData.SIZE_OF_DRAW_COMMAND;
        final int vertexCount = drawList.vertexBuffer.limit() / BYTES_PER_VERTEX;
        assertEquals(
                commandCount * VERTICES_PER_QUAD,
                vertexCount,
                "Every command must have exactly one quad");

        Command[] commands = new Command[commandCount];
        for (int i = 0; i < commandCount; ++i) {
            final int offset = i * DrawData.SIZE_OF_DRAW_COMMAND;
            commands[i] =
                    new Command(
                            drawList.commandBuffer.getInt(offset),
                            drawList.commandBuffer.getInt(offset + 4),
                            drawList.commandBuffer.getInt(offset + 8),
                            drawList.commandBuffer.getInt(offset + 12),
                            DrawList.ElementType.fromID(drawList.commandBuffer.getInt(offset + 16)),
                            DrawList.ElementStyle.fromID(
                                    drawList.commandBuffer.getInt(offset + 20)),
                            drawList.commandBuffer.getFloat(offset + 24));
        }
        return commands;
    }

    private static void assertCommand(
            Command command,
            DrawList.ElementType type,
            DrawList.ElementStyle style,
            int pointCount) {
        assertEquals(type, command.type());
        assertEquals(style, command.style());
        assertEquals(pointCount, command.pointCount());
    }

    @Test
    void testLine() {
        Command[] commands = draw(list -> list.addLine(10, 10, 100, 50, Color.WHITE, 3));
        assertEquals(1, commands.length);
        assertCommand(
                commands[0], DrawList.ElementType.LINE_STRAIGHT, DrawList.ElementStyle.FILL, 2);
        assertEquals(2, commands[0].detailCount());
        assertEquals(3, commands[0].stroke(), 0.001f);
    }

    @Test
    void testCircles() {
        Command[] commands =
                draw(
                        list -> {
                            list.addCircle(50, 50, 20, Color.WHITE, 2);
                            list.addCircleFilled(150, 50, 20, Color.WHITE);
                        });
        assertEquals(2, commands.length);
        assertCommand(commands[0], DrawList.ElementType.CIRCLE, DrawList.ElementStyle.BORDER, 1);
        assertCommand(commands[1], DrawList.ElementType.CIRCLE, DrawList.ElementStyle.FILL, 1);
    }

    @Test
    void testTrianglesQuadsAndNgons() {
        Command[] commands =
                draw(
                        list -> {
                            list.addTriangle(0, 0, 10, 0, 5, 10, Color.WHITE, 1);
                            list.addTriangleFilled(0, 0, 10, 0, 5, 10, Color.WHITE);
                            list.addQuadFilled(0, 0, 10, 0, 10, 10, 0, 10, Color.WHITE);
                            list.addNgon(50, 50, 20, Color.WHITE, 6, 2);
                            list.addNgonFilled(50, 50, 20, Color.WHITE, 8);
                        });
        assertEquals(5, commands.length);
        assertCommand(commands[0], DrawList.ElementType.POLYGON, DrawList.ElementStyle.BORDER, 3);
        assertCommand(commands[1], DrawList.ElementType.POLYGON, DrawList.ElementStyle.FILL, 3);
        assertCommand(commands[2], DrawList.ElementType.POLYGON, DrawList.ElementStyle.FILL, 4);
        assertCommand(commands[3], DrawList.ElementType.POLYGON, DrawList.ElementStyle.BORDER, 6);
        assertCommand(commands[4], DrawList.ElementType.POLYGON, DrawList.ElementStyle.FILL, 8);
    }

    @Test
    void testCurves() {
        Command[] commands =
                draw(
                        list -> {
                            list.addBezierQuadratic(0, 0, 50, 100, 100, 0, Color.WHITE, 2);
                            list.addBezierCubic(0, 0, 30, 100, 70, -100, 100, 0, Color.WHITE, 2);
                            list.addArc(50, 50, 30, 0, (float) Math.PI, Color.WHITE, 2);
                        });
        assertEquals(3, commands.length);
        assertCommand(commands[0], DrawList.ElementType.LINE_BEZIER, DrawList.ElementStyle.FILL, 3);
        assertCommand(commands[1], DrawList.ElementType.LINE_BEZIER, DrawList.ElementStyle.FILL, 4);
        assertCommand(commands[2], DrawList.ElementType.LINE_ARC, DrawList.ElementStyle.FILL, 2);
    }

    @Test
    void testPolylines() {
        Vector2f[] points = {
            new Vector2f(0, 0), new Vector2f(50, 0), new Vector2f(50, 50), new Vector2f(0, 50)
        };
        Command[] open = draw(list -> list.addPolyline(points, 4, Color.WHITE, false, 2));
        assertEquals(3, open.length);
        for (Command command : open) {
            assertCommand(
                    command, DrawList.ElementType.LINE_STRAIGHT, DrawList.ElementStyle.FILL, 2);
        }

        Command[] closed = draw(list -> list.addPolyline(points, 4, Color.WHITE, true, 2));
        assertEquals(1, closed.length);
        assertCommand(closed[0], DrawList.ElementType.POLYGON, DrawList.ElementStyle.BORDER, 4);

        Command[] filled = draw(list -> list.addConvexPolyFilled(points, 4, Color.WHITE));
        assertEquals(1, filled.length);
        assertCommand(filled[0], DrawList.ElementType.POLYGON, DrawList.ElementStyle.FILL, 4);
    }

    @Test
    void testPaths() {
        Command[] rounded =
                draw(
                        list -> {
                            list.pathRect(10, 10, 110, 60, 8);
                            list.pathFillConvex(Color.WHITE);
                        });
        assertEquals(1, rounded.length);
        assertEquals(DrawList.ElementType.POLYGON, rounded[0].type());
        // Four corners with multiple points each
        assertEquals(true, rounded[0].pointCount() > 8);

        Command[] stroked =
                draw(
                        list -> {
                            list.pathLineTo(0, 0);
                            list.pathLineTo(50, 0);
                            list.pathLineToMergeDuplicate(50, 0);
                            list.pathBezierQuadraticCurveTo(75, 25, 50, 50, 4);
                            list.pathStroke(Color.WHITE, false, 2);
                        });
        // 1 straight segment, then 4 curve segments
        assertEquals(5, stroked.length);

        // The path is cleared after use
        Command[] empty = draw(list -> list.pathStroke(Color.WHITE, false, 2));
        assertEquals(0, empty.length);
    }

    @Test
    void testImages() {
        TextureInfo first = new TextureInfo() {};
        TextureInfo second = new TextureInfo() {};
        DrawTextures drawData = IkGui.getContext().drawTextures;
        drawData.clear();
        Command[] commands =
                draw(
                        list -> {
                            list.addImage(first, 0, 0, 64, 64);
                            list.addImageRounded(
                                    second, 100, 0, 164, 64, 0, 0, 1, 1, Color.WHITE, 8);
                            list.addImageQuad(first, 0, 100, 64, 100, 80, 164, 0, 164);
                        });
        assertEquals(3, commands.length);
        assertCommand(
                commands[0], DrawList.ElementType.RECTANGLE, DrawList.ElementStyle.TEXTURE, 2);
        assertCommand(
                commands[1], DrawList.ElementType.RECTANGLE, DrawList.ElementStyle.TEXTURE, 2);
        assertCommand(commands[2], DrawList.ElementType.POLYGON, DrawList.ElementStyle.TEXTURE, 4);

        // Each texture is registered once, and referenced by index in the point details
        assertEquals(2, drawData.textures.size());
        assertEquals(first, drawData.textures.get(0));
        assertEquals(second, drawData.textures.get(1));
        assertEquals(0, textureIndexOf(commands[0]));
        assertEquals(1, textureIndexOf(commands[1]));
        assertEquals(0, textureIndexOf(commands[2]));

        // The second point of a textured rect holds the texture coordinate range
        final int uvPoint = (commands[1].pointIndex() + 1) * DrawData.SIZE_OF_POINT;
        assertEquals(0, drawList.pointBuffer.getFloat(uvPoint), 0.001f);
        assertEquals(1, drawList.pointBuffer.getFloat(uvPoint + 8), 0.001f);
    }

    /**
     * Read the texture index (color or texture ID) out of the first point detail of a command.
     *
     * @param command The command.
     * @return The texture index.
     */
    private int textureIndexOf(Command command) {
        final int offset = command.detailIndex() * DrawData.SIZE_OF_POINT_DETAIL;
        // radius, alpha radius, then color or texture ID
        return drawList.pointDetailBuffer.getInt(offset + 2 * Float.BYTES);
    }

    @Test
    void testFullyClippedShapesAreSkipped() {
        Command[] commands =
                draw(
                        list -> {
                            list.pushClipRect(0, 0, 100, 100);
                            list.addCircleFilled(500, 500, 10, Color.WHITE);
                            list.addLine(200, 200, 300, 300, Color.WHITE, 1);
                            list.addTriangleFilled(200, 200, 300, 200, 250, 300, Color.WHITE);
                            list.addCircleFilled(50, 50, 10, Color.WHITE);
                            list.popClipRect();
                        });
        assertEquals(1, commands.length);
        assertCommand(commands[0], DrawList.ElementType.CIRCLE, DrawList.ElementStyle.FILL, 1);
    }

    @Test
    void testGradients() {
        final int red = Color.rgba(255, 0, 0, 255);
        final int blue = Color.rgba(0, 0, 255, 255);
        Command[] commands =
                draw(
                        list -> {
                            list.addArc(50, 50, 30, 0, (float) Math.PI, red, blue, 2);
                            list.addArc(50, 50, 30, 0, (float) Math.PI, red, red, 2);
                            list.addTriangleFilledMultiColor(0, 0, 50, 0, 25, 50, red, blue, red);
                            list.addRectFilledMultiColor(0, 0, 50, 50, red, red, blue, blue);
                        });
        assertEquals(4, commands.length);
        assertCommand(commands[0], DrawList.ElementType.LINE_ARC, DrawList.ElementStyle.FILL, 2);
        assertEquals(2, commands[0].detailCount(), "Gradient arcs need a color for each end");
        assertEquals(1, commands[1].detailCount(), "Solid arcs only need one color");
        assertCommand(commands[2], DrawList.ElementType.POLYGON, DrawList.ElementStyle.FILL, 3);
        assertEquals(3, commands[2].detailCount(), "Multi-color triangles need a color per vertex");
        assertCommand(commands[3], DrawList.ElementType.RECTANGLE, DrawList.ElementStyle.FILL, 1);
        assertEquals(4, commands[3].detailCount());
    }

    @Test
    void testChannelsReorderCommands() {
        Command[] commands =
                draw(
                        list -> {
                            list.channelsSplit(2);
                            list.channelsSetCurrent(1);
                            list.addRectFilled(0, 0, 10, 10, Color.WHITE);
                            list.channelsSetCurrent(0);
                            list.addCircleFilled(50, 50, 5, Color.WHITE);
                            list.channelsMerge();
                        });
        assertEquals(2, commands.length);
        // Channel 0 comes first, even though it was drawn second
        assertEquals(DrawList.ElementType.CIRCLE, commands[0].type());
        assertEquals(DrawList.ElementType.RECTANGLE, commands[1].type());
    }

    @Test
    void testNestedSplitters() {
        final DrawListSplitter outer = new DrawListSplitter();
        final DrawListSplitter inner = new DrawListSplitter();
        Command[] commands =
                draw(
                        list -> {
                            outer.split(list, 2);
                            outer.setCurrentChannel(list, 1);
                            // Nested split inside outer channel 1
                            inner.split(list, 2);
                            inner.setCurrentChannel(list, 1);
                            list.addCircleFilled(50, 50, 5, Color.WHITE);
                            inner.setCurrentChannel(list, 0);
                            list.addLine(0, 0, 10, 10, Color.WHITE, 1);
                            inner.merge(list);
                            outer.setCurrentChannel(list, 0);
                            list.addRectFilled(0, 0, 10, 10, Color.WHITE);
                            outer.merge(list);
                        });
        assertEquals(3, commands.length);
        assertEquals(DrawList.ElementType.RECTANGLE, commands[0].type());
        assertEquals(DrawList.ElementType.LINE_STRAIGHT, commands[1].type());
        assertEquals(DrawList.ElementType.CIRCLE, commands[2].type());
    }

    @Test
    void testChannelBuffersGrow() {
        Command[] commands =
                draw(
                        list -> {
                            list.channelsSplit(3);
                            for (int i = 0; i < 200; ++i) {
                                list.channelsSetCurrent(i % 3);
                                list.addRectFilled(i, 0, i + 1, 10, Color.WHITE);
                            }
                            list.channelsMerge();
                        });
        assertEquals(200, commands.length);
    }

    @Test
    void testHorizontalAndVerticalLines() {
        Command[] commands =
                draw(
                        list -> {
                            list.addLineH(10, 50, 20, Color.WHITE, 1);
                            list.addLineV(30, 5, 60, Color.WHITE, 1);
                            // Fully transparent lines are skipped
                            list.addLineH(10, 50, 20, Color.CLEAR, 1);
                        });
        assertEquals(2, commands.length);
        assertCommand(commands[0], DrawList.ElementType.RECTANGLE, DrawList.ElementStyle.FILL, 1);
        assertCommand(commands[1], DrawList.ElementType.RECTANGLE, DrawList.ElementStyle.FILL, 1);
    }

    /**
     * Read a vertex position from the vertex buffer, after preparing for rendering.
     *
     * @param vertex The vertex index.
     * @return The position.
     */
    private Vector2f vertex(int vertex) {
        final int offset = vertex * BYTES_PER_VERTEX;
        return new Vector2f(
                drawList.vertexBuffer.getFloat(offset),
                drawList.vertexBuffer.getFloat(offset + Float.BYTES));
    }

    /**
     * Read a point (x, y, a, b) from the point buffer, after preparing for rendering.
     *
     * @param point The point index.
     * @return The point values.
     */
    private float[] point(int point) {
        final int offset = point * DrawData.SIZE_OF_POINT;
        return new float[] {
            drawList.pointBuffer.getFloat(offset),
            drawList.pointBuffer.getFloat(offset + 4),
            drawList.pointBuffer.getFloat(offset + 8),
            drawList.pointBuffer.getFloat(offset + 12)
        };
    }

    @Test
    void testTransformedGlyphIsRotated() {
        final FontAtlas.CharInfo info = new FontAtlas.CharInfo('A');
        info.x = 5;
        info.y = 6;
        info.width = 10;
        info.height = 20;
        Command[] commands =
                draw(
                        list -> {
                            // Rotate 90 degrees around the label origin, then move to (100, 100)
                            list.pushTextTransform(0, 0, 0, 1, 100, 100);
                            list.addTransformedGlyph(0, 0, 10, 20, info, 0);
                            list.popTextTransform();
                        });
        assertEquals(1, commands.length);
        assertCommand(commands[0], DrawList.ElementType.TEXT, DrawList.ElementStyle.TEXTURE, 2);

        // (x, y) -> (-y, x): the glyph covers x 80 to 100, y 100 to 110, plus padding
        final Vector2f topLeft = vertex(0);
        final Vector2f bottomRight = vertex(2);
        assertEquals(79, topLeft.x, 0.001f);
        assertEquals(99, topLeft.y, 0.001f);
        assertEquals(101, bottomRight.x, 0.001f);
        assertEquals(111, bottomRight.y, 0.001f);

        // The rotated top left corner, with the rotation as (cos, sin)
        final float[] origin = point(commands[0].pointIndex());
        assertEquals(100, origin[0], 0.001f);
        assertEquals(100, origin[1], 0.001f);
        assertEquals(0, origin[2], 0.001f);
        assertEquals(1, origin[3], 0.001f);
        final float[] atlas = point(commands[0].pointIndex() + 1);
        assertEquals(5, atlas[0], 0.001f);
        assertEquals(10, atlas[2], 0.001f);
        assertEquals(20, atlas[3], 0.001f);
    }

    @Test
    void testTransformedGlyphLabelClip() {
        final FontAtlas.CharInfo info = new FontAtlas.CharInfo('A');
        info.width = 10;
        info.height = 20;
        Command[] commands =
                draw(
                        list -> {
                            list.pushClipRect(0, 0, 1000, 1000);
                            list.pushTextTransform(0, 0, 1, 0, 0, 0);
                            // A label space clip rect, which is not intersected with the screen
                            // clip rect
                            list.pushClipRect(-50, 0, 5, 20, true);
                            list.addTransformedGlyph(0, 0, 10, 20, info, 0);
                            // Entirely outside the label clip rect
                            list.addTransformedGlyph(20, 0, 30, 20, info, 0);
                            list.popClipRect();
                            list.popTextTransform();
                            list.popClipRect();
                        });
        assertEquals(1, commands.length);
        assertCommand(commands[0], DrawList.ElementType.TEXT, DrawList.ElementStyle.TEXTURE, 3);
        // The clip rect relative to the glyph top left
        final float[] clip = point(commands[0].pointIndex() + 2);
        assertEquals(-50, clip[0], 0.001f);
        assertEquals(0, clip[1], 0.001f);
        assertEquals(5, clip[2], 0.001f);
        assertEquals(20, clip[3], 0.001f);
    }
}
