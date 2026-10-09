package com.ikalagaming.graphics.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.gui.data.Context;
import com.ikalagaming.graphics.gui.data.DrawData;
import com.ikalagaming.graphics.gui.data.DrawList;
import com.ikalagaming.graphics.gui.data.Window;
import com.ikalagaming.graphics.gui.enums.ColorType;
import com.ikalagaming.graphics.gui.enums.Condition;
import com.ikalagaming.graphics.gui.flags.TableFlags;
import com.ikalagaming.graphics.gui.flags.TreeNodeFlags;
import com.ikalagaming.graphics.gui.flags.WindowFlags;
import com.ikalagaming.graphics.gui.util.Color;
import com.ikalagaming.graphics.gui.util.RectFloat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/** Tests for the lines connecting the tree node hierarchy. */
class IkGuiTreeLinesTest {
    /** A color nothing else uses, so the tree lines can be found in the draw list. */
    private static final int LINE_COLOR = Color.rgba(1, 2, 3, 255);

    private Context context;

    /** The rectangles of the tree nodes, captured while drawing. */
    private final List<RectFloat> nodeRects = new ArrayList<>();

    /** The cursor position after the tree, captured while drawing. */
    private float contentBottom;

    @BeforeEach
    void setUp() {
        context = IkGui.createContext();
        // Don't read or write an .ini file
        context.io.iniFilename = null;
        context.io.displaySize.set(1280, 720);
        context.style.color.set(ColorType.TREE_LINES, LINE_COLOR);
    }

    @AfterEach
    void tearDown() {
        IkGui.destroyContext();
    }

    private static void frame(Runnable ui) {
        IkGui.newFrame();
        ui.run();
        IkGui.render();
    }

    private static void frames(int count, Runnable ui) {
        for (int i = 0; i < count; ++i) {
            frame(ui);
        }
    }

    private static void beginHost() {
        IkGui.setNextWindowPos(100, 100, Condition.FIRST_USE_EVER);
        IkGui.setNextWindowSize(400, 400, Condition.FIRST_USE_EVER);
        IkGui.begin("Host", null, WindowFlags.NONE);
    }

    private void captureNode() {
        nodeRects.add(
                new RectFloat(
                        IkGui.getItemRectMinX(),
                        IkGui.getItemRectMinY(),
                        IkGui.getItemRectMaxX(),
                        IkGui.getItemRectMaxY()));
    }

    /** The tree from the demo: a parent with two children, and more contents after them. */
    private Runnable treeUI(int flags) {
        return () -> {
            nodeRects.clear();
            beginHost();
            final boolean parentOpen =
                    IkGui.treeNodeEx("Parent", flags | TreeNodeFlags.DEFAULT_OPEN);
            captureNode();
            if (parentOpen) {
                final boolean child1 =
                        IkGui.treeNodeEx("Child 1", flags | TreeNodeFlags.DEFAULT_OPEN);
                captureNode();
                if (child1) {
                    IkGui.button("Button for Child 1");
                    IkGui.treePop();
                }
                final boolean child2 =
                        IkGui.treeNodeEx("Child 2", flags | TreeNodeFlags.DEFAULT_OPEN);
                captureNode();
                if (child2) {
                    IkGui.button("Button for Child 2");
                    IkGui.treePop();
                }
                IkGui.text("Remaining contents");
                IkGui.text("Remaining contents");
                contentBottom = IkGui.getCursorScreenPos().y;
                IkGui.treePop();
            }
            IkGui.end();
        };
    }

    private static DrawList hostDrawList() {
        final Window window = IkGuiInternal.findWindowByName("Host");
        assertNotNull(window);
        return window.drawList;
    }

    /** Find the rectangles drawn in the tree line color. */
    private static List<RectFloat> lineRects(DrawList drawList) {
        final List<RectFloat> rects = new ArrayList<>();
        final int count = drawList.commandBuffer.limit() / DrawData.SIZE_OF_DRAW_COMMAND;
        for (int i = 0; i < count; ++i) {
            final int offset = i * DrawData.SIZE_OF_DRAW_COMMAND;
            if (drawList.commandBuffer.getInt(offset + 16)
                    != DrawList.ElementType.RECTANGLE.getTypeID()) {
                continue;
            }
            final int detail =
                    drawList.commandBuffer.getInt(offset + 4) * DrawData.SIZE_OF_POINT_DETAIL;
            if (drawList.pointDetailBuffer.getInt(detail + 8) != LINE_COLOR) {
                continue;
            }
            final int point = drawList.commandBuffer.getInt(offset) * DrawData.SIZE_OF_POINT;
            final float centerX = drawList.pointBuffer.getFloat(point);
            final float centerY = drawList.pointBuffer.getFloat(point + 4);
            final float width = drawList.pointBuffer.getFloat(point + 8);
            final float height = drawList.pointBuffer.getFloat(point + 12);
            rects.add(
                    new RectFloat(
                            centerX - width / 2,
                            centerY - height / 2,
                            centerX + width / 2,
                            centerY + height / 2));
        }
        return rects;
    }

    private static List<RectFloat> horizontal(List<RectFloat> rects) {
        return rects.stream().filter(r -> r.getWidth() > r.getHeight()).toList();
    }

    private static List<RectFloat> vertical(List<RectFloat> rects) {
        return rects.stream().filter(r -> r.getHeight() > r.getWidth()).toList();
    }

    /** Where the vertical line of a node goes, through the middle of its arrow. */
    private float lineX(RectFloat node) {
        return IkGuiInternal.truncate(
                        node.getLeft()
                                + IkGuiInternal.getFontSize() * 0.5f
                                + context.style.variable.framePadding.x)
                + 0.5f;
    }

    @Test
    void testNoLinesByDefault() {
        frames(3, treeUI(TreeNodeFlags.NONE));
        assertTrue(lineRects(hostDrawList()).isEmpty());
    }

    @Test
    void testDrawLinesFull() {
        frames(3, treeUI(TreeNodeFlags.DRAW_LINES_FULL));
        final List<RectFloat> lines = lineRects(hostDrawList());
        // A line to each child, from the parent
        final List<RectFloat> horizontal = horizontal(lines);
        assertEquals(2, horizontal.size(), lines.toString());
        // Vertical lines for the parent and for both children, which cover their contents
        final List<RectFloat> vertical = vertical(lines);
        assertEquals(3, vertical.size(), lines.toString());

        final RectFloat parent = nodeRects.get(0);
        final RectFloat parentLine =
                vertical.stream()
                        .filter(r -> Math.abs(r.getCenterX() - lineX(parent)) < 0.01f)
                        .findFirst()
                        .orElseThrow();
        // From the bottom of the parent, past the last child, to the remaining contents
        assertEquals(parent.getBottom(), parentLine.getTop(), 0.01f);
        assertTrue(parentLine.getBottom() > nodeRects.get(2).getBottom());
        assertTrue(parentLine.getBottom() < contentBottom);
        // The lines to the children start at the parent's line, at the middle of each child
        for (int i = 0; i < 2; ++i) {
            final RectFloat line = horizontal.get(i);
            assertEquals(parentLine.getCenterX(), line.getLeft() + 0.5f, 0.01f);
            final RectFloat child = nodeRects.get(i + 1);
            assertTrue(line.getCenterY() > child.getTop() && line.getCenterY() < child.getBottom());
        }
    }

    @Test
    void testDrawLinesToNodes() {
        frames(3, treeUI(TreeNodeFlags.DRAW_LINES_TO_NODES));
        final List<RectFloat> lines = lineRects(hostDrawList());
        assertEquals(2, horizontal(lines).size(), lines.toString());
        // Only the parent has child nodes to draw down to
        final List<RectFloat> vertical = vertical(lines);
        assertEquals(1, vertical.size(), lines.toString());
        // The vertical line stops at the line to the last child
        final RectFloat lastChildLine = horizontal(lines).get(1);
        assertEquals(
                IkGuiInternal.truncate(lastChildLine.getCenterY() - 0.5f),
                vertical.getFirst().getBottom(),
                0.01f);
    }

    @Test
    void testStyleDefaultAndOverride() {
        context.style.variable.treeLinesFlags = TreeNodeFlags.DRAW_LINES_FULL;
        frames(3, treeUI(TreeNodeFlags.NONE));
        assertEquals(5, lineRects(hostDrawList()).size());
        // An explicit flag on the node overrides the style
        frames(3, treeUI(TreeNodeFlags.DRAW_LINES_NONE));
        assertTrue(lineRects(hostDrawList()).isEmpty());
    }

    @Test
    void testZeroSizeDrawsNothing() {
        context.style.variable.treeLinesSize = 0;
        frames(3, treeUI(TreeNodeFlags.DRAW_LINES_FULL));
        assertTrue(lineRects(hostDrawList()).isEmpty());
    }

    @Test
    void testRoundedCorners() {
        context.style.variable.treeLinesRounding = 4;
        frames(3, treeUI(TreeNodeFlags.DRAW_LINES_FULL));
        final List<RectFloat> lines = lineRects(hostDrawList());
        final RectFloat parentLine =
                vertical(lines).stream()
                        .filter(r -> Math.abs(r.getCenterX() - lineX(nodeRects.get(0))) < 0.01f)
                        .findFirst()
                        .orElseThrow();
        // The lines to the children start after the corner arc
        for (RectFloat line : horizontal(lines)) {
            assertEquals(parentLine.getCenterX() + 4, line.getLeft(), 0.01f);
        }
        final DrawList drawList = hostDrawList();
        int arcs = 0;
        final int count = drawList.commandBuffer.limit() / DrawData.SIZE_OF_DRAW_COMMAND;
        for (int i = 0; i < count; ++i) {
            if (drawList.commandBuffer.getInt(i * DrawData.SIZE_OF_DRAW_COMMAND + 16)
                    == DrawList.ElementType.LINE_ARC.getTypeID()) {
                arcs++;
            }
        }
        assertEquals(2, arcs);
    }

    @Test
    void testInvalidStyleFlagsAreReported() {
        context.style.variable.treeLinesFlags =
                TreeNodeFlags.DRAW_LINES_FULL | TreeNodeFlags.DRAW_LINES_TO_NODES;
        frame(() -> {});
        assertEquals(TreeNodeFlags.DRAW_LINES_NONE, context.style.variable.treeLinesFlags);
        assertTrue(
                context.debugLogBuffer.snapshot().stream()
                        .anyMatch(line -> line.contains("style.treeLinesFlags")),
                context.debugLogBuffer.getText());
    }

    @Test
    void testLinesInATable() {
        final Runnable ui =
                () -> {
                    beginHost();
                    if (IkGui.beginTable("Tree", 2, TableFlags.BORDERS_V)) {
                        IkGui.tableNextRow();
                        IkGui.tableNextColumn();
                        final int flags =
                                TreeNodeFlags.SPAN_ALL_COLUMNS
                                        | TreeNodeFlags.DRAW_LINES_FULL
                                        | TreeNodeFlags.DEFAULT_OPEN;
                        if (IkGui.treeNodeEx("Folder", flags)) {
                            for (int i = 0; i < 3; ++i) {
                                IkGui.tableNextRow();
                                IkGui.tableNextColumn();
                                IkGui.treeNodeEx(
                                        "File " + i,
                                        TreeNodeFlags.LEAF
                                                | TreeNodeFlags.NO_TREE_PUSH_ON_OPEN
                                                | TreeNodeFlags.DRAW_LINES_FULL);
                                IkGui.tableNextColumn();
                                IkGui.text("Size");
                            }
                            IkGui.treePop();
                        }
                        IkGui.endTable();
                    }
                    IkGui.end();
                };
        frames(3, ui);
        // The table draws into its own channels, which end up in the window draw list
        final List<RectFloat> lines = lineRects(hostDrawList());
        assertEquals(3, horizontal(lines).size(), lines.toString());
        assertEquals(1, vertical(lines).size(), lines.toString());
        assertFalse(
                context.debugLogBuffer.snapshot().stream()
                        .anyMatch(line -> line.contains("[ikgui-error]")),
                context.debugLogBuffer.getText());
    }
}
