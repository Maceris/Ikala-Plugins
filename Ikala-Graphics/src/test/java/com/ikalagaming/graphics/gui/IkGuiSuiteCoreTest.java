package com.ikalagaming.graphics.gui;

import static com.ikalagaming.graphics.gui.IkGuiTestContext.chord;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.Format;
import com.ikalagaming.graphics.TextureHandle;
import com.ikalagaming.graphics.gui.data.DrawList;
import com.ikalagaming.graphics.gui.data.ErrorRecoveryState;
import com.ikalagaming.graphics.gui.data.IkBoolean;
import com.ikalagaming.graphics.gui.data.IkInt;
import com.ikalagaming.graphics.gui.data.IkString;
import com.ikalagaming.graphics.gui.data.ListClipper;
import com.ikalagaming.graphics.gui.data.TextFilter;
import com.ikalagaming.graphics.gui.data.Viewport;
import com.ikalagaming.graphics.gui.data.Window;
import com.ikalagaming.graphics.gui.data.WindowSettings;
import com.ikalagaming.graphics.gui.enums.ColorType;
import com.ikalagaming.graphics.gui.enums.Condition;
import com.ikalagaming.graphics.gui.enums.Key;
import com.ikalagaming.graphics.gui.enums.MouseButton;
import com.ikalagaming.graphics.gui.enums.StyleVariable;
import com.ikalagaming.graphics.gui.flags.ChildFlags;
import com.ikalagaming.graphics.gui.flags.ColorEditFlags;
import com.ikalagaming.graphics.gui.flags.InputFlags;
import com.ikalagaming.graphics.gui.flags.InputTextFlags;
import com.ikalagaming.graphics.gui.flags.ItemFlags;
import com.ikalagaming.graphics.gui.flags.KeyModFlags;
import com.ikalagaming.graphics.gui.flags.MultiSelectFlags;
import com.ikalagaming.graphics.gui.flags.PopupFlags;
import com.ikalagaming.graphics.gui.flags.TableFlags;
import com.ikalagaming.graphics.gui.flags.TreeNodeFlags;
import com.ikalagaming.graphics.gui.flags.WindowFlags;
import com.ikalagaming.graphics.gui.util.Hash;
import com.ikalagaming.graphics.gui.util.KeyChord;
import com.ikalagaming.graphics.gui.util.RectFloat;

import org.joml.Vector2f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.BitSet;

/**
 * Core tests ported from the Dear ImGui test suite (imgui_tests_core.cpp), which is MIT licensed.
 * Each test notes the name of the upstream test it is ported from.
 */
class IkGuiSuiteCoreTest {
    private IkGuiTestContext ctx;

    @BeforeEach
    void setUp() {
        ctx = new IkGuiTestContext();
    }

    @AfterEach
    void tearDown() {
        try {
            ctx.assertNoErrors();
        } finally {
            ctx.destroy();
        }
    }

    private static final float EPSILON = 0.0001f;

    /** window_empty: the size of an empty window. */
    @Test
    void testWindowEmpty() {
        ctx.setGui(
                () -> {
                    IkGui.begin(
                            "Test Window",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS | WindowFlags.ALWAYS_AUTO_RESIZE);
                    IkGui.end();
                });
        ctx.yieldFrames(2);
        final var style = ctx.context.style.variable;
        final Window window = IkGuiInternal.findWindowByName("Test Window");
        assertEquals(style.windowPadding.x * 2.0f, window.size.x);
        assertEquals(
                IkGuiInternal.getFontSize()
                        + style.framePadding.y * 2.0f
                        + style.windowPadding.y * 2.0f,
                window.size.y);
        assertEquals(new Vector2f(0, 0), window.contentSize);
        assertEquals(new Vector2f(0, 0), window.scrollPosition);
    }

    /** window_size_min: the minimum window size with setNextWindowSize() and auto resizing. */
    @Test
    void testWindowSizeMin() {
        ctx.setGui(
                () -> {
                    IkGui.pushStyleVarFloat2(StyleVariable.WINDOW_MIN_SIZE, 40, 40);
                    // Checked in the test code, after auto-resizing it should be 40x40
                    IkGui.begin("Test Window 1", null, WindowFlags.NO_TITLE_BAR);
                    IkGui.end();
                    // Checked in the test code, it should be the window padding * 2
                    IkGui.begin(
                            "Test Window 2",
                            null,
                            WindowFlags.NO_TITLE_BAR | WindowFlags.ALWAYS_AUTO_RESIZE);
                    IkGui.end();
                    {
                        IkGui.setNextWindowSize(400, 10);
                        IkGui.begin("Test Window 3", null, WindowFlags.NO_TITLE_BAR);
                        final Vector2f size = IkGui.getWindowSize();
                        IkGui.end();
                        ctx.checkEquals(new Vector2f(400, 40), size, "window 3");
                    }
                    {
                        IkGui.setNextWindowSize(400, 10);
                        IkGui.begin(
                                "Test Window 4",
                                null,
                                WindowFlags.NO_TITLE_BAR | WindowFlags.ALWAYS_AUTO_RESIZE);
                        final Vector2f size = IkGui.getWindowSize();
                        IkGui.end();
                        ctx.checkEquals(new Vector2f(400, 10), size, "window 4");
                    }
                    {
                        IkGui.setNextWindowSize(400, 400);
                        IkGui.begin(
                                "Test Window 5",
                                null,
                                WindowFlags.NO_TITLE_BAR | WindowFlags.ALWAYS_AUTO_RESIZE);
                        IkGui.beginChild("child", 200, 5);
                        final Vector2f size = IkGui.getWindowSize();
                        IkGui.endChild();
                        IkGui.end();
                        ctx.checkEquals(new Vector2f(200, 5), size, "child");
                    }
                    IkGui.popStyleVar();
                });
        // Upstream resizes to (-1, -1), the smallest possible size
        ctx.windowResize("Test Window 1", 1.0f, 1.0f);
        final Window window1 = ctx.getWindowByRef("Test Window 1");
        final Window window2 = ctx.getWindowByRef("Test Window 2");
        assertNotNull(window1);
        assertNotNull(window2);
        assertEquals(new Vector2f(40, 40), window1.size);
        assertEquals(
                new Vector2f(ctx.context.style.variable.windowPadding).mul(2.0f), window2.size);
    }

    /**
     * window_size_collapsed_1: a window that starts collapsed measures its width and contents on
     * its first frames.
     */
    @Test
    void testWindowSizeCollapsed1() {
        final int[] frame = {-2};
        ctx.setGui(
                () -> {
                    IkGui.setNextWindowCollapsed(true, Condition.APPEARING);
                    IkGui.begin("Issue 2336", null, WindowFlags.NO_SAVED_SETTINGS);
                    IkGui.text("This is some text");
                    IkGui.text("This is some more text");
                    IkGui.text("This is some more text again");
                    final float width = IkGui.getWindowWidth();
                    // Past the warm-up frames
                    if (frame[0] == 0) {
                        final float expected =
                                IkGui.calcTextSize("This is some more text again").x
                                        + ctx.context.style.variable.windowPadding.x * 2.0f;
                        ctx.check(Math.abs(width - expected) < 1.0f, "width " + width);
                    }
                    IkGui.end();
                    frame[0]++;
                });
        ctx.yieldFrames(3);
    }

    /** window_size_collapsed_2: a collapsed window doesn't show a scrollbar when expanded. */
    @Test
    void testWindowSizeCollapsed2() {
        final boolean[] warmUp = {true};
        ctx.setGui(
                () -> {
                    IkGui.setNextWindowSize(400, 200, Condition.APPEARING);
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    if (!warmUp[0]) {
                        ctx.check(!IkGuiInternal.context.windowCurrent.scrollbarY, "window 1");
                    }
                    IkGui.end();

                    IkGui.setNextWindowSize(400, 200, Condition.APPEARING);
                    IkGui.begin("Test Window 2", null, WindowFlags.NO_SAVED_SETTINGS);
                    if (!warmUp[0]) {
                        ctx.check(!IkGuiInternal.context.windowCurrent.scrollbarY, "window 2");
                    }
                    for (int n = 0; n < 3; ++n) {
                        IkGui.text("Test " + n);
                    }
                    IkGui.end();
                });
        ctx.yieldFrame();
        warmUp[0] = false;
        ctx.setRef("Test Window");
        ctx.windowCollapse("", true);
        ctx.windowCollapse("", false);

        ctx.setRef("Test Window 2");
        ctx.windowCollapse("", true);
        ctx.windowCollapse("", false);
    }

    /** window_size_collapsed_3: a collapsed window with ALWAYS_AUTO_RESIZE still skips items. */
    @Test
    void testWindowSizeCollapsed3() {
        final boolean[] visible = {false};
        ctx.setGui(
                () -> {
                    visible[0] =
                            IkGui.begin(
                                    "Test Window",
                                    null,
                                    WindowFlags.NO_SAVED_SETTINGS | WindowFlags.ALWAYS_AUTO_RESIZE);
                    if (visible[0]) {
                        for (int n = 0; n < 10; ++n) {
                            IkGui.text("Test line " + n);
                        }
                    }
                    IkGui.end();
                });
        ctx.setRef("Test Window");
        ctx.yieldFrames(2);
        // begin() returns true
        assertTrue(visible[0]);
        ctx.windowCollapse("", true);
        // begin() returns false
        assertFalse(visible[0]);
        ctx.windowCollapse("", false);
        assertTrue(visible[0]);
    }

    /**
     * window_size_collapsed_4: content sizes are preserved while collapsed, along with the effect
     * of setWindowCollapsed() and setNextWindowCollapsed().
     */
    @Test
    void testWindowSizeCollapsed4() {
        final int[] step = {0};
        final boolean[] firstFrame = {true};
        ctx.setGui(
                () -> {
                    if (step[0] == 1) {
                        IkGui.setNextWindowCollapsed(true);
                    }
                    if (step[0] == 2) {
                        IkGui.setNextWindowCollapsed(false);
                    }
                    if (IkGui.begin(
                            "Test Window",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS | WindowFlags.ALWAYS_AUTO_RESIZE)) {
                        IkGui.button("Button", 100, 100);
                    }
                    if (step[0] == 1) {
                        IkGui.setWindowCollapsed(true);
                    }
                    if (step[0] == 2) {
                        IkGui.setWindowCollapsed(false);
                    }
                    if (!firstFrame[0]) {
                        ctx.checkEquals(
                                new Vector2f(100, 100),
                                IkGuiInternal.context.windowCurrent.contentSize,
                                "content size, step " + step[0]);
                    }
                    IkGui.end();
                    firstFrame[0] = false;
                });
        ctx.setRef("Test Window");
        ctx.windowCollapse("", false);
        ctx.yieldFrames(3);
        for (int s = 1; s <= 4; ++s) {
            step[0] = s;
            ctx.yieldFrames(3);
        }
    }

    /** window_size_contents: content sizes and their effect on scrollbar visibility. */
    @Test
    void testWindowSizeContents() {
        final int[] frame = {-2};
        ctx.setGui(
                () -> {
                    final var g = ctx.context;
                    final var style = g.style.variable;
                    {
                        IkGui.begin(
                                "Test Contents Size 1",
                                null,
                                WindowFlags.NO_SAVED_SETTINGS | WindowFlags.ALWAYS_AUTO_RESIZE);
                        IkGui.colorButton(
                                "test",
                                new float[] {1, 0.4f, 0, 1.0f},
                                ColorEditFlags.NO_TOOLTIP,
                                150,
                                150);
                        if (frame[0] > 0) {
                            ctx.checkEquals(
                                    new Vector2f(150.0f, 150.0f),
                                    g.windowCurrent.contentSize,
                                    "size 1");
                        }
                        IkGui.end();
                    }
                    {
                        IkGui.setNextWindowContentSize(150, 150);
                        IkGui.begin(
                                "Test Contents Size 2",
                                null,
                                WindowFlags.NO_SAVED_SETTINGS | WindowFlags.ALWAYS_AUTO_RESIZE);
                        if (frame[0] >= 0) {
                            ctx.checkEquals(
                                    new Vector2f(150.0f, 150.0f),
                                    g.windowCurrent.contentSize,
                                    "size 2");
                        }
                        IkGui.end();
                    }
                    {
                        IkGui.setNextWindowContentSize(150, 150);
                        IkGui.setNextWindowSize(
                                150 + style.windowPadding.x * 2.0f,
                                150 + style.windowPadding.y * 2.0f + IkGui.getFrameHeight());
                        IkGui.begin("Test Contents Size 3", null, WindowFlags.NO_SAVED_SETTINGS);
                        if (frame[0] >= 0) {
                            ctx.check(!g.windowCurrent.scrollbarY, "size 3 scrollbar");
                            ctx.checkEquals(0.0f, g.windowCurrent.scrollMax.y, "size 3 scroll");
                        }
                        IkGui.end();
                    }
                    {
                        IkGui.setNextWindowContentSize(150, 150 + 1);
                        IkGui.setNextWindowSize(
                                150 + style.windowPadding.x * 2.0f,
                                150 + style.windowPadding.y * 2.0f + IkGui.getFrameHeight());
                        IkGui.begin("Test Contents Size 4", null, WindowFlags.NO_SAVED_SETTINGS);
                        if (frame[0] >= 0) {
                            ctx.check(g.windowCurrent.scrollbarY, "size 4 scrollbar");
                            ctx.checkEquals(1.0f, g.windowCurrent.scrollMax.y, "size 4 scroll");
                        }
                        IkGui.end();
                    }
                    frame[0]++;
                });
        // Upstream finishes when the frame count reaches 2
        ctx.yieldFrames(4);
    }

    /** window_size_scrollbar_xy: the effect of the horizontal scrollbar on vertical scrolling. */
    @Test
    void testWindowSizeScrollbarXY() {
        final Vector2f windowSize = new Vector2f();
        final Vector2f itemSize = new Vector2f();
        ctx.setGui(
                () -> {
                    IkGui.setNextWindowSize(windowSize.x, windowSize.y);
                    IkGui.pushStyleVarFloat2(StyleVariable.WINDOW_PADDING, 0.0f, 0.0f);
                    IkGui.begin(
                            "Test Window",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS
                                    | WindowFlags.HORIZONTAL_SCROLLBAR
                                    | WindowFlags.NO_TITLE_BAR);
                    IkGui.popStyleVar();
                    IkGui.dummy(itemSize);
                    IkGui.end();
                });
        final Window window = ctx.getWindowByRef("Test Window");
        assertNotNull(window);

        windowSize.set(100, 100);
        itemSize.set(100, 100);
        ctx.yieldFrames(2);
        assertFalse(window.scrollbarX);
        assertFalse(window.scrollbarY);

        itemSize.set(100, 101);
        ctx.yieldFrames(2);
        assertTrue(window.scrollbarX);
        assertTrue(window.scrollbarY);

        itemSize.set(101, 100);
        ctx.yieldFrames(2);
        assertTrue(window.scrollbarX);
        assertTrue(window.scrollbarY);
    }

    /** window_size_unrounded: non-integer sizes and positions are rounded down without drifting. */
    @Test
    void testWindowSizeUnrounded() {
        ctx.setGui(
                () -> {
                    final Vector2f viewportPos = IkGui.getMainViewport().position;
                    // Upstream issue #2067
                    {
                        IkGui.setNextWindowPos(
                                viewportPos.x + 401.0f,
                                viewportPos.y + 103.0f,
                                Condition.APPEARING);
                        IkGui.setNextWindowSize(348.48f, 400.0f, Condition.APPEARING);
                        IkGui.begin("Issue 2067", null, WindowFlags.NO_SAVED_SETTINGS);
                        final Vector2f pos = IkGui.getWindowPos().sub(viewportPos);
                        final Vector2f size = IkGui.getWindowSize();
                        ctx.check(pos.x == 401.0f && pos.y == 103.0f, "2067 position " + pos);
                        ctx.check(size.x == 348.0f && size.y == 400.0f, "2067 size " + size);
                        IkGui.end();
                    }
                    // Non-rounded size constraints don't alter the position or size (#2530)
                    {
                        IkGui.setNextWindowPos(
                                viewportPos.x + 401.0f,
                                viewportPos.y + 103.0f,
                                Condition.APPEARING);
                        IkGui.setNextWindowSize(348.48f, 400.0f, Condition.APPEARING);
                        IkGui.setNextWindowSizeConstraints(
                                475.200_012f, 0.0f, 475.200_012f, 100.4f);
                        IkGui.begin("Issue 2530", null, WindowFlags.NO_SAVED_SETTINGS);
                        final Vector2f pos = IkGui.getWindowPos();
                        final Vector2f size = IkGui.getWindowSize();
                        ctx.checkEquals(
                                new Vector2f(viewportPos).add(401.0f, 103.0f),
                                pos,
                                "2530 position");
                        ctx.checkEquals(new Vector2f(475.0f, 100.0f), size, "2530 size");
                        IkGui.end();
                    }
                });
        // Upstream finishes when the frame count reaches 2
        ctx.yieldFrames(4);
    }

    /** window_size_constraints: window size constraints with a callback. */
    @Test
    void testWindowSizeConstraints() {
        final boolean[] firstFrame = {true};
        ctx.setGui(
                () -> {
                    IkGui.setNextWindowSizeConstraints(
                            0,
                            0,
                            500,
                            500,
                            data -> {
                                if (firstFrame[0]) {
                                    final Window window = IkGuiInternal.context.windowCurrent;
                                    ctx.checkEquals(window.position, data.position, "position");
                                    ctx.checkEquals(window.sizeFull, data.currentSize, "size");
                                }
                                final float max = Math.max(data.desiredSize.x, data.desiredSize.y);
                                data.desiredSize.set(max, max);
                            });
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    IkGui.textUnformatted("Lorem ipsum dolor sit amet");
                    IkGui.end();
                    firstFrame[0] = false;
                });
        final Window window = ctx.getWindowByRef("Test Window");
        for (int i = 0; i < 2; ++i) {
            ctx.windowResize("Test Window", 200.0f + i * 100.0f, 100.0f);
            assertEquals(200.0f + i * 100.0f, window.sizeFull.x);
            assertEquals(200.0f + i * 100.0f, window.sizeFull.y);
        }
    }

    /** window_size_auto_basic: basic window auto resizing. */
    @Test
    void testWindowSizeAutoBasic() {
        final int[] frame = {-2};
        ctx.setGui(
                () -> {
                    // Upstream sets the size here to avoid side effects of other tests using a
                    // window with the same name
                    IkGui.setNextWindowSize(1, 1, Condition.APPEARING);
                    IkGui.begin(
                            "Test Window",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS | WindowFlags.ALWAYS_AUTO_RESIZE);
                    IkGui.text(
                            ctx.context.io.keyShift ? "This is some longer text" : "Hello World");
                    IkGui.beginChild("Child", 0, 200, ChildFlags.BORDERS);
                    IkGui.endChild();
                    final Vector2f size = IkGui.getWindowSize();
                    IkGui.end();
                    if (frame[0] >= 0 && frame[0] <= 2) {
                        final var style = ctx.context.style.variable;
                        ctx.checkEquals(
                                (int)
                                        (IkGui.calcTextSize("Hello World").x
                                                + style.windowPadding.x * 2.0f),
                                (int) size.x,
                                "width");
                        ctx.checkEquals(
                                (int)
                                        (IkGui.getFrameHeight()
                                                + IkGui.calcTextSize("Hello World").y
                                                + style.itemSpacing.y
                                                + 200.0f
                                                + style.windowPadding.y * 2.0f),
                                (int) size.y,
                                "height");
                    }
                    frame[0]++;
                });
        ctx.yieldFrames(5);
    }

    /**
     * window_size_auto_single_axis: auto resizing a single axis accounts for the other axis'
     * scrollbar.
     */
    @Test
    void testWindowSizeAutoSingleAxis() {
        ctx.setGui(
                () -> {
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    for (int n : new int[] {1, 5, 2, 3, 9, 6, 3, 1}) {
                        IkGui.text(String.format("%" + (n + 10) + "s|", ""));
                    }
                    IkGui.end();
                });
        ctx.setRef("Test Window");
        final Window window = ctx.getWindowByRef("");
        assertNotNull(window);
        // The same as resizing to (0, 0), but more explicit
        ctx.itemDoubleClick(IkGuiImplWindows.getWindowResizeID(window, 0));
        assertFalse(window.scrollbarX);
        assertFalse(window.scrollbarY);
        final Vector2f sizeAutoFitBoth = new Vector2f(window.size);
        ctx.windowResize("", window.size.x, window.size.y / 2.0f);
        assertFalse(window.scrollbarX);
        assertTrue(window.scrollbarY);
        // The right border
        ctx.itemDoubleClick(IkGuiImplWindows.getWindowResizeID(window, 4 + 1));
        assertEquals(sizeAutoFitBoth.x + ctx.context.style.variable.scrollbarSize, window.size.x);
        assertFalse(window.scrollbarX);
        assertTrue(window.scrollbarY);
        ctx.itemDoubleClick(IkGuiImplWindows.getWindowResizeID(window, 0));
        assertEquals(sizeAutoFitBoth, window.size);
    }

    /**
     * window_size_auto_uncollapse: expanding an auto-resizing window doesn't go through a frame
     * where it is smaller than expected.
     */
    @Test
    void testWindowSizeAutoUncollapse() {
        ctx.setGui(
                () -> {
                    IkGui.begin(
                            "Test Window",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS | WindowFlags.ALWAYS_AUTO_RESIZE);
                    IkGui.text("Some text\nOver two lines\nOver three lines");
                    IkGui.end();
                });
        // Upstream's warm-up frames let the window auto-fit before the test starts
        ctx.yieldFrame();
        final Window window = IkGuiInternal.findWindowByName("Test Window");
        ctx.setRef("Test Window");
        ctx.windowCollapse("", false);
        assertEquals(window.sizeFull, window.size);
        final Vector2f sizeFullWhenExpanded = new Vector2f(window.sizeFull);
        ctx.windowCollapse("", true);
        final Vector2f sizeCollapsed = new Vector2f(window.size);
        assertTrue(sizeFullWhenExpanded.y > sizeCollapsed.y);
        assertEquals(sizeFullWhenExpanded, window.sizeFull);
        ctx.windowCollapse("", false);
        // The window should have restored to its full size
        assertEquals(sizeFullWhenExpanded.y, window.size.y);
        ctx.yieldFrame();
        assertEquals(sizeFullWhenExpanded.y, window.size.y);
    }

    /** window_append_child: appending to a child window several times. */
    @Test
    void testWindowAppendChild() {
        ctx.setGui(
                () -> {
                    IkGui.setNextWindowSize(200, 200);
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    IkGui.text("Line 1");
                    IkGui.end();
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    IkGui.text("Line 2");
                    IkGui.beginChild("Blah", 0, 50, ChildFlags.BORDERS);
                    IkGui.text("Line 3");
                    IkGui.endChild();
                    final Vector2f pos1 = IkGui.getCursorScreenPos();
                    IkGui.beginChild("Blah");
                    IkGui.text("Line 4");
                    IkGui.endChild();
                    final Vector2f pos2 = IkGui.getCursorScreenPos();
                    // Appending to a child doesn't affect the cursor position in the parent
                    ctx.checkEquals(pos1, pos2, "first append");
                    IkGui.text("Line 5");
                    final Vector2f pos3 = IkGui.getCursorScreenPos();
                    IkGui.beginChild("Blah");
                    IkGui.text("Line 6");
                    IkGui.endChild();
                    final Vector2f pos4 = IkGui.getCursorScreenPos();
                    ctx.checkEquals(pos3, pos4, "second append");
                    IkGui.end();
                });
        ctx.yieldFrames(2);
    }

    /** window_append_status: isItemXXX() calls after an appending begin(). */
    @Test
    void testWindowAppendStatus() {
        final boolean[] hoveredFirst = {false};
        final boolean[] hoveredAppend = {false};
        ctx.setGui(
                () -> {
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    hoveredFirst[0] = IkGui.isItemHovered();
                    IkGui.text("IsItemHovered = " + hoveredFirst[0] + " (first)");
                    final Window window = IkGuiInternal.getCurrentWindowRead();
                    final float right = window.position.x + window.size.x;
                    final float top = window.position.y;
                    IkGui.end();

                    // An item in between, so the old last item isn't used
                    IkGui.setNextWindowPos(right, top);
                    IkGui.begin("Another window");
                    IkGui.button("Foobar");
                    {
                        IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                        hoveredAppend[0] = IkGui.isItemHovered();
                        IkGui.text("IsItemHovered = " + hoveredAppend[0] + " (append)");
                        IkGui.end();
                    }
                    IkGui.end();
                });
        final Vector2f point =
                IkGuiTestContext.windowTitleBarPoint(ctx.getWindowByRef("Test Window"));
        ctx.mouseMoveToPos(point.x, point.y);
        assertTrue(hoveredFirst[0]);
        assertTrue(hoveredAppend[0]);
    }

    /** window_append_status_child: isItemXXX() calls after an appending beginChild(). */
    @Test
    void testWindowAppendStatusChild() {
        final boolean[] status = new boolean[8];
        ctx.setGui(
                () -> {
                    IkGui.begin(
                            "Test Window",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS | WindowFlags.ALWAYS_AUTO_RESIZE);
                    IkGui.beginChild("ChildWindow", 200, 100, ChildFlags.FRAME_STYLE);
                    // Upstream notes this is undefined behavior, since it normally gives the
                    // title bar's data, but it wants to keep the option of child windows having it
                    status[0] = IkGui.isItemHovered();
                    status[1] = IkGui.isItemVisible();
                    IkGui.text("Hello");
                    IkGui.endChild();
                    status[2] = IkGui.isItemHovered();
                    status[3] = IkGui.isItemVisible();
                    IkGui.button("Button");
                    IkGui.beginChild("ChildWindow", 200, 100);
                    status[4] = IkGui.isItemHovered();
                    status[5] = IkGui.isItemVisible();
                    IkGui.endChild();
                    status[6] = IkGui.isItemHovered();
                    status[7] = IkGui.isItemVisible();
                    for (int n = 0; n < 8; n += 2) {
                        IkGui.text("hovered: " + status[n] + " visible: " + status[n + 1]);
                    }
                    IkGui.end();
                });
        ctx.mouseMove("Test Window/Button");
        assertArrayEquals(
                new boolean[] {false, false, false, true, false, false, false, true}, status);

        final Window child = ctx.windowInfo("Test Window/ChildWindow");
        assertNotNull(child);
        ctx.mouseMoveToPos(
                child.position.x + child.size.x * 0.5f, child.position.y + child.size.y * 0.5f);
        assertArrayEquals(
                new boolean[] {false, false, true, true, false, false, true, true}, status);
    }

    /** window_focus_1: basic focus behavior as windows appear and disappear. */
    @Test
    void testWindowFocus1() {
        final int[] frameCount = {0};
        ctx.setGui(
                () -> {
                    final int frame = frameCount[0];
                    IkGui.begin("AAAA", null, WindowFlags.NO_SAVED_SETTINGS);
                    IkGui.end();
                    IkGui.begin("BBBB", null, WindowFlags.NO_SAVED_SETTINGS);
                    IkGui.end();
                    if ((frame >= 20 && frame < 40) || frame >= 50) {
                        IkGui.begin("CCCC", null, WindowFlags.NO_SAVED_SETTINGS);
                        IkGui.end();
                        IkGui.begin("DDDD", null, WindowFlags.NO_SAVED_SETTINGS);
                        IkGui.end();
                    }
                });
        final var g = ctx.context;
        final java.util.function.IntConsumer yieldUntil =
                n -> {
                    while (frameCount[0] < n) {
                        frameCount[0]++;
                        ctx.yieldFrame();
                    }
                };
        assertEquals(ctx.getID("//BBBB"), g.navFocusedWindow.id);
        yieldUntil.accept(19);
        assertEquals(ctx.getID("//BBBB"), g.navFocusedWindow.id);
        yieldUntil.accept(20);
        assertEquals(ctx.getID("//DDDD"), g.navFocusedWindow.id);
        yieldUntil.accept(30);
        // Focus without the driver's frame counter, so the window still shows
        IkGuiInternal.focusWindow(
                ctx.getWindowByRef("//CCCC"),
                com.ikalagaming.graphics.gui.flags.WindowFocusRequestFlags.NONE);
        frameCount[0]++;
        ctx.yieldFrame();
        assertEquals(ctx.getID("//CCCC"), g.navFocusedWindow.id);
        yieldUntil.accept(40);
        assertEquals(ctx.getID("//CCCC"), g.navFocusedWindow.id);

        // Upstream notes that when docked, it used to take an extra frame to lose focus
        yieldUntil.accept(41);
        assertEquals(ctx.getID("//BBBB"), g.navFocusedWindow.id);

        yieldUntil.accept(49);
        assertEquals(ctx.getID("//BBBB"), g.navFocusedWindow.id);
        yieldUntil.accept(50);
        assertEquals(ctx.getID("//DDDD"), g.navFocusedWindow.id);
    }

    /** window_popup_focus: popup focus, and right clicking to close popups above a level. */
    @Test
    void testWindowPopupFocus() {
        ctx.setGui(ctx::showApp);
        final var g = ctx.context;
        ctx.setRef(IkGuiTestContext.DEMO);
        ctx.itemOpen("Popups & Modal windows");
        ctx.itemClick("Popups/Toggle..");

        final Window popup1 = g.navFocusedWindow;
        ctx.setRef(popup1);
        ctx.itemClick("Stacked Popup");
        assertTrue(popup1.wasActive);

        final Window popup2 = g.navFocusedWindow;
        ctx.mouseMoveNoFocus("Bream");
        // Close with a right click
        ctx.mouseClick(MouseButton.RIGHT);
        assertTrue(popup1.wasActive);
        assertFalse(popup2.wasActive);
        assertSame(popup1, g.navFocusedWindow);
    }

    /** window_popup_focus2: calling closeCurrentPopup() after clicking it doesn't close twice. */
    @Test
    void testWindowPopupFocus2() {
        ctx.setGui(
                () -> {
                    IkGui.begin("Stacked Modal Popups");
                    if (IkGui.button("Open Modal Popup 1")) {
                        IkGui.openPopup("Popup1");
                    }
                    if (IkGui.beginPopupModal("Popup1", null, WindowFlags.NO_SAVED_SETTINGS)) {
                        if (IkGui.button("Open Modal Popup 2")) {
                            IkGui.openPopup("Popup2");
                        }
                        IkGui.setNextWindowSize(100, 100);
                        if (IkGui.beginPopupModal("Popup2", null, WindowFlags.NO_SAVED_SETTINGS)) {
                            IkGui.text("Click anywhere");
                            if (IkGui.isMouseClicked(MouseButton.LEFT)) {
                                IkGui.closeCurrentPopup();
                            }
                            IkGui.endPopup();
                        }
                        IkGui.endPopup();
                    }
                    IkGui.end();
                });
        final var g = ctx.context;
        ctx.setRef("Stacked Modal Popups");
        ctx.itemClick("Open Modal Popup 1");
        assertEquals(ctx.getID("//Popup1"), g.navFocusedWindow.id);
        assertEquals(1, g.openPopupStack.size());
        ctx.setRef("Popup1");
        ctx.itemClick("Open Modal Popup 2");
        assertEquals(ctx.getID("//Popup2"), g.navFocusedWindow.id);
        assertEquals(2, g.openPopupStack.size());
        final Window popup2 = g.navFocusedWindow;
        ctx.mouseMoveToPos(
                popup2.position.x + popup2.size.x * 0.5f, popup2.position.y + popup2.size.y * 0.5f);
        ctx.mouseClick(MouseButton.LEFT);
        assertEquals(1, g.openPopupStack.size());
        assertEquals(ctx.getID("//Popup1"), g.navFocusedWindow.id);
    }

    /** window_popup_close_current: closing the current popup from menus and popups. */
    @Test
    void testWindowPopupCloseCurrent() {
        ctx.setGui(
                () -> {
                    IkGui.begin(
                            "Popups", null, WindowFlags.NO_SAVED_SETTINGS | WindowFlags.MENU_BAR);
                    if (IkGui.beginMenuBar()) {
                        if (IkGui.beginMenu("Menu")) {
                            if (IkGui.beginMenu("Submenu")) {
                                IkGui.menuItem("Close1");
                                IkGui.endMenu();
                            }
                            IkGui.endMenu();
                        }
                        IkGui.endMenuBar();
                    }
                    if (IkGui.button("Open Popup")) {
                        IkGui.openPopup("Popup");
                    }
                    if (IkGui.beginPopup("Popup")) {
                        IkGui.menuItem("Close2");
                        if (IkGui.beginMenu("Submenu2")) {
                            IkGui.menuItem("Close3");
                            IkGui.endMenu();
                        }
                        IkGui.endPopup();
                    }
                    IkGui.end();
                });
        final var g = ctx.context;
        ctx.setRef("Popups");
        assertEquals(0, g.openPopupStack.size());
        ctx.menuClick("Menu");
        assertEquals(1, g.openPopupStack.size());
        ctx.menuClick("Menu/Submenu");
        assertEquals(2, g.openPopupStack.size());
        ctx.menuClick("Menu/Submenu/Close1");
        assertEquals(0, g.openPopupStack.size());

        ctx.itemClick("Open Popup");
        assertEquals(1, g.openPopupStack.size());
        ctx.itemClick("//$FOCUSED/Close2");
        assertEquals(0, g.openPopupStack.size());

        ctx.itemClick("//Popups/Open Popup");
        ctx.setRef("//$FOCUSED");
        ctx.menuClick("Submenu2/Close3");
        assertEquals(0, g.openPopupStack.size());
    }

    /** window_popup_close_above: right clicking a window closes the popups above it. */
    @Test
    void testWindowPopupCloseAbove() {
        ctx.setGui(
                () -> {
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    if (IkGui.button("Open modal")) {
                        IkGui.openPopup("Modal");
                    }
                    if (IkGui.beginPopupModal("Modal")) {
                        if (IkGui.button("Open popup1")) {
                            IkGui.openPopup("Popup1");
                        }
                        if (IkGui.beginPopup("Popup1")) {
                            if (IkGui.button("Open popup2")) {
                                IkGui.openPopup("Popup2");
                            }
                            if (IkGui.beginPopup("Popup2")) {
                                if (IkGui.button("Close")) {
                                    IkGui.closeCurrentPopup();
                                }
                                IkGui.endPopup();
                            }
                            if (IkGui.button("Close")) {
                                IkGui.closeCurrentPopup();
                            }
                            IkGui.endPopup();
                        }
                        if (IkGui.button("Close")) {
                            IkGui.closeCurrentPopup();
                        }
                        IkGui.endPopup();
                    }
                    IkGui.end();
                });
        final var g = ctx.context;
        ctx.itemClick("//Test Window/Open modal");
        ctx.itemClick("//$FOCUSED/Open popup1");
        final Window popup1 = g.navFocusedWindow;
        ctx.itemClick("//$FOCUSED/Open popup2");
        assertNotSame(popup1, g.navFocusedWindow);
        assertEquals(3, g.openPopupStack.size());
        ctx.mouseMoveToPos(popup1.position.x + 5, popup1.position.y + 5);
        ctx.mouseClick(MouseButton.RIGHT);
        assertSame(popup1, g.navFocusedWindow);
        assertEquals(2, g.openPopupStack.size());
    }

    /** window_popup_close_with_void: clicking the void closes popups, but not modals. */
    @Test
    void testWindowPopupCloseWithVoid() {
        ctx.setGui(
                () -> {
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    if (IkGui.button("Open modal")) {
                        IkGui.openPopup("Modal");
                    }
                    if (IkGui.beginPopupModal("Modal")) {
                        if (IkGui.button("Open popup")) {
                            IkGui.openPopup("Popup");
                        }
                        if (IkGui.beginPopup("Popup")) {
                            if (IkGui.button("Close popup")) {
                                IkGui.closeCurrentPopup();
                            }
                            IkGui.endPopup();
                        }
                        if (IkGui.button("Close modal")) {
                            IkGui.closeCurrentPopup();
                        }
                        IkGui.endPopup();
                    }
                    IkGui.end();
                });
        final var g = ctx.context;
        ctx.itemClick("Test Window/Open modal");
        ctx.itemClick("//$FOCUSED/Open popup");
        assertEquals(2, g.openPopupStack.size());
        ctx.mouseMoveToVoid();
        ctx.mouseClick(MouseButton.LEFT);
        assertEquals(1, g.openPopupStack.size());
        ctx.itemClick("//$FOCUSED/Close modal");
        assertEquals(0, g.openPopupStack.size());
    }

    /** window_popup_close_signal: the input and output value of a modal's open flag. */
    @Test
    void testWindowPopupCloseSignal() {
        final IkBoolean open = new IkBoolean(false);
        final boolean[] visible = {false};
        ctx.setGui(
                () -> {
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    if (IkGui.button("Open modal")) {
                        IkGui.openPopup("Modal");
                        open.set(true);
                    }
                    visible[0] = false;
                    if (IkGui.beginPopupModal("Modal", open)) {
                        visible[0] = true;
                        if (IkGui.button("Close")) {
                            IkGui.closeCurrentPopup();
                        }
                        IkGui.endPopup();
                    }
                    IkGui.end();
                });
        final var g = ctx.context;

        // The open flag as an output, when closing with the window's close button
        ctx.itemClick("//Test Window/Open modal");
        assertTrue(open.get());
        assertTrue(visible[0]);
        ctx.windowClose("//$FOCUSED");
        ctx.yieldFrame();
        assertFalse(open.get());
        assertFalse(visible[0]);
        assertEquals(0, g.openPopupStack.size());

        // The open flag as an output, when closing with the API
        ctx.itemClick("//Test Window/Open modal");
        assertTrue(open.get());
        assertTrue(visible[0]);
        ctx.itemClick("//$FOCUSED/Close");
        ctx.yieldFrame();
        assertFalse(open.get());
        assertFalse(visible[0]);
        assertEquals(0, g.openPopupStack.size());

        // The open flag as an input
        ctx.itemClick("//Test Window/Open modal");
        assertTrue(open.get());
        assertTrue(visible[0]);
        ctx.yieldFrames(2);
        assertTrue(visible[0]);
        open.set(false);
        ctx.yieldFrame();
        assertFalse(open.get());
        assertFalse(visible[0]);
    }

    /** window_popup_on_void: beginPopupContextVoid(). */
    @Test
    void testWindowPopupOnVoid() {
        final boolean[] popupVisible = {false};
        final boolean[] openModal = {false};
        final int[] popupFlags = {0};
        ctx.setGui(
                () -> {
                    popupVisible[0] = false;
                    if (IkGui.beginPopupContextVoid("testpopup", popupFlags[0])) {
                        popupVisible[0] = true;
                        IkGui.selectable("Close");
                        IkGui.endPopup();
                    }
                    if (openModal[0]) {
                        IkGui.openPopup("Modal");
                        openModal[0] = false;
                    }
                    if (IkGui.beginPopupModal("Modal")) {
                        IkGui.selectable("Close");
                        IkGui.endPopup();
                    }
                });
        final var g = ctx.context;

        // Clear the focus
        popupFlags[0] = PopupFlags.MOUSE_BUTTON_RIGHT;
        ctx.mouseClickOnVoid(MouseButton.LEFT);
        assertNull(g.navFocusedWindow);

        // Open the popup with a right click
        ctx.mouseClickOnVoid(MouseButton.RIGHT);
        assertTrue(popupVisible[0]);
        assertNotNull(g.navFocusedWindow);
        // This doesn't make the debug window appear
        final Window debugWindow = IkGuiInternal.findWindowByName("Debug##Default");
        assertNotNull(debugWindow);
        assertFalse(debugWindow.wasActive);

        // Close the popup, and check that the null nav window was restored
        ctx.mouseClickOnVoid(MouseButton.LEFT);
        assertFalse(popupVisible[0]);
        assertNull(g.navFocusedWindow);

        // The opposite buttons
        popupFlags[0] = PopupFlags.MOUSE_BUTTON_LEFT;
        ctx.mouseClickOnVoid(MouseButton.LEFT);
        assertTrue(popupVisible[0]);
        ctx.mouseClickOnVoid(MouseButton.RIGHT);
        assertFalse(popupVisible[0]);
        assertNull(g.navFocusedWindow);

        // With a blocking modal
        openModal[0] = true;
        ctx.yieldFrames(2);
        assertSame(ctx.getWindowByRef("//Modal"), g.navFocusedWindow);
        ctx.mouseClickOnVoid(MouseButton.LEFT);
        assertSame(ctx.getWindowByRef("//Modal"), g.navFocusedWindow);
        ctx.itemClick("//Modal/Close");
    }

    /** window_popup_open_after: opening a popup after beginPopup(), while out of focus. */
    @Test
    void testWindowPopupOpenAfter() {
        ctx.setGui(
                () -> {
                    IkGui.begin("Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    if (IkGui.beginPopup("Popup")) {
                        IkGui.text("...");
                        IkGui.endPopup();
                    }
                    IkGui.button("Open");
                    IkGui.openPopupOnItemClick("Popup", PopupFlags.MOUSE_BUTTON_RIGHT);
                    IkGui.end();
                });
        final var g = ctx.context;
        ctx.setRef("Window");
        // Make sure no window is focused
        ctx.mouseClickOnVoid();
        // Open the popup without focusing the window
        ctx.itemClickNoFocus("Open", MouseButton.RIGHT);
        assertEquals(1, g.openPopupStack.size());
        assertNotNull(g.openPopupStack.get(0).window);
        final int popupWindowID = Hash.getID(String.format("##Popup_%08x", ctx.getID("Popup")));
        assertEquals(popupWindowID, g.openPopupStack.get(0).window.id);
    }

    /** window_popup_reopen: reopening an open popup moves it. */
    @Test
    void testWindowPopupReopen() {
        final boolean[] openPopup = {false};
        final Vector2f popupPos = new Vector2f();
        ctx.setGui(
                () -> {
                    if (openPopup[0]) {
                        IkGui.openPopup("TestPopup");
                    }
                    if (IkGui.beginPopup("TestPopup")) {
                        popupPos.set(IkGui.getWindowPos());
                        IkGui.text("HELLO\nWORLD");
                        IkGui.endPopup();
                    }
                });
        final Vector2f workPos = IkGui.getMainViewport().workPosition;

        final Vector2f pos1 = new Vector2f(workPos).add(40, 40);
        ctx.mouseTeleportToPos(pos1.x, pos1.y, true);
        assertFalse(IkGui.isPopupOpen("TestPopup"));
        // Open
        openPopup[0] = true;
        ctx.yieldFrame();
        openPopup[0] = false;
        assertTrue(IkGui.isPopupOpen("TestPopup"));
        assertTrue(new RectFloat(pos1, new Vector2f(30, 30)).contains(popupPos), "at " + popupPos);

        final Vector2f pos2 = new Vector2f(workPos).add(100, 100);
        ctx.mouseTeleportToPos(pos2.x, pos2.y, true);
        // Still open
        assertTrue(IkGui.isPopupOpen("TestPopup"));
        assertTrue(new RectFloat(pos1, new Vector2f(30, 30)).contains(popupPos), "at " + popupPos);
        // Reopen
        openPopup[0] = true;
        ctx.yieldFrame();
        openPopup[0] = false;
        assertTrue(IkGui.isPopupOpen("TestPopup"));
        assertTrue(new RectFloat(pos2, new Vector2f(30, 30)).contains(popupPos), "at " + popupPos);
    }

    /** window_popup_menu: menus in a popup window's menu bar. */
    @Test
    void testWindowPopupMenu() {
        final IkBoolean useModal = new IkBoolean(false);
        final boolean[] firstOpen = {false};
        final boolean[] secondOpen = {false};
        ctx.setGui(
                () -> {
                    firstOpen[0] = false;
                    secondOpen[0] = false;
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    if (IkGui.button("Open Menu Popup")) {
                        IkGui.openPopup("Menu Popup");
                    }
                    final boolean popupOpen =
                            useModal.get()
                                    ? IkGui.beginPopupModal(
                                            "Menu Popup", null, WindowFlags.MENU_BAR)
                                    : IkGui.beginPopup("Menu Popup", WindowFlags.MENU_BAR);
                    if (popupOpen) {
                        if (IkGui.beginMenuBar()) {
                            if (IkGui.beginMenu("First")) {
                                firstOpen[0] = true;
                                IkGui.menuItem("Lorem");
                                IkGui.endMenu();
                            }
                            if (IkGui.beginMenu("Second")) {
                                secondOpen[0] = true;
                                IkGui.menuItem("Ipsum");
                                IkGui.endMenu();
                            }
                            IkGui.endMenuBar();
                        }
                        if (IkGui.isKeyPressed(Key.ESCAPE)) {
                            IkGui.closeCurrentPopup();
                        }
                        IkGui.dummy(100, 50);
                        IkGui.endPopup();
                    }
                    IkGui.checkbox("Is Modal", useModal);
                    IkGui.text(firstOpen[0] + " " + secondOpen[0]);
                    IkGui.end();
                });
        final var g = ctx.context;
        final Window window = ctx.getWindowByRef("Test Window");
        for (int variant = 0; variant < 2; ++variant) {
            final String d = variant == 1 ? "modal" : "popup";
            useModal.set(variant == 1);
            ctx.itemClick("//Test Window/Open Menu Popup");
            final Window popup =
                    useModal.get()
                            ? ctx.getWindowByRef("//Menu Popup")
                            : IkGuiInternal.findWindowByID(
                                    ctx.popupGetWindowID("//Test Window/Menu Popup"));
            assertNotNull(popup, d);
            ctx.setRef(popup);
            // Nothing is open
            assertFalse(firstOpen[0], d);
            assertFalse(secondOpen[0], d);

            // Menus open by hovering their item while another menu is open
            ctx.itemClick("##MenuBar/First");
            assertTrue(firstOpen[0], d);
            assertFalse(secondOpen[0], d);
            ctx.mouseMoveNoFocus("##MenuBar/Second");
            assertFalse(firstOpen[0], d);
            assertTrue(secondOpen[0], d);
            ctx.mouseMoveNoFocus("##MenuBar/First");
            assertTrue(firstOpen[0], d);
            assertFalse(secondOpen[0], d);

            // Clicking the menu's item again closes it
            ctx.itemClickNoFocus("##MenuBar/First");
            assertFalse(firstOpen[0], d);
            assertFalse(secondOpen[0], d);

            // Clicking the popup window's body closes the menu
            ctx.itemClick("##MenuBar/First");
            ctx.mouseMoveToPos(
                    popup.position.x + popup.size.x - 20.0f,
                    popup.position.y + popup.size.y - 20.0f);
            ctx.mouseClick(MouseButton.LEFT);
            assertFalse(firstOpen[0], d);
            assertEquals(1, g.openPopupStack.size(), d);
            assertFalse(secondOpen[0], d);
            assertTrue(popup.active, d);

            if (useModal.get()) {
                ctx.windowMove(
                        "//" + popup.name, window.position.x + window.size.x, window.position.y);
            }

            // Clicking the popup's parent window body closes the menu
            ctx.itemClick("##MenuBar/First");
            ctx.mouseMoveToPos(
                    window.position.x + 20.0f, window.position.y + window.size.y - 20.0f);
            ctx.mouseClick(MouseButton.LEFT);
            if (useModal.get()) {
                assertEquals(1, g.openPopupStack.size(), d);
            }
            assertFalse(firstOpen[0], d);
            assertFalse(secondOpen[0], d);
            assertEquals(useModal.get(), popup.active, d);
            if (!popup.active) {
                // Reopen the popup if it was closed
                ctx.itemClick("//Test Window/Open Menu Popup");
            }

            // Clicking empty space closes the menu
            ctx.itemClick("##MenuBar/First");
            ctx.mouseClickOnVoid();
            // Upstream notes this doesn't work with modals yet
            if (!useModal.get()) {
                assertFalse(firstOpen[0], d);
                assertFalse(secondOpen[0], d);
            }
            assertEquals(useModal.get(), popup.active, d);
            ctx.popupCloseAll();
        }
    }

    /**
     * window_popup_want_capture: io.wantCaptureMouse and io.wantCaptureMouseUnlessPopupClose with
     * popups.
     */
    @Test
    void testWindowPopupWantCapture() {
        ctx.setGui(
                () -> {
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    if (IkGui.button("Open Popup")) {
                        IkGui.openPopup("Popup");
                    }
                    if (IkGui.button("Open Modal")) {
                        IkGui.openPopup("Modal");
                    }
                    final Vector2f pos = IkGui.getWindowPos();
                    IkGui.setNextWindowPos(pos.x + IkGui.getWindowWidth(), pos.y);
                    if (IkGui.beginPopup("Popup")) {
                        IkGui.textUnformatted("...");
                        IkGui.endPopup();
                    }
                    IkGui.setNextWindowPos(pos.x + IkGui.getWindowWidth(), pos.y);
                    if (IkGui.beginPopupModal("Modal")) {
                        IkGui.textUnformatted("...");
                        IkGui.endPopup();
                    }
                    IkGui.end();
                });
        final var g = ctx.context;
        final var io = g.io;
        ctx.setRef("Test Window");
        ctx.itemClick("Open Popup");
        assertNotNull(g.navFocusedWindow);
        ctx.mouseMoveToPos(g.navFocusedWindow.position.x + 10, g.navFocusedWindow.position.y + 10);
        assertTrue(io.wantCaptureMouse);
        assertTrue(io.wantCaptureMouseUnlessPopupClose);
        ctx.mouseMoveToVoid();
        assertTrue(io.wantCaptureMouse);
        assertFalse(io.wantCaptureMouseUnlessPopupClose);
        ctx.popupCloseAll();

        ctx.itemClick("Open Modal");
        assertNotNull(g.navFocusedWindow);
        ctx.mouseMoveToPos(g.navFocusedWindow.position.x + 10, g.navFocusedWindow.position.y + 10);
        assertTrue(io.wantCaptureMouse);
        assertTrue(io.wantCaptureMouseUnlessPopupClose);
        ctx.mouseMoveToVoid();
        assertNull(g.windowHovered);
        assertTrue(io.wantCaptureMouse);
        assertTrue(io.wantCaptureMouseUnlessPopupClose);

        // Move to the button behind, and check that it's blocked
        final RectFloat button = ctx.itemInfo("//Test Window/Open Modal").rect;
        ctx.mouseMoveToPos(button.getCenterX(), button.getCenterY());
        assertNull(g.windowHovered);
        assertTrue(io.wantCaptureMouse);
        assertTrue(io.wantCaptureMouseUnlessPopupClose);

        ctx.popupCloseAll();
    }

    /**
     * window_popup_nested_interruptions: popups aren't interrupted by various appearing elements.
     * The docking checks are left out, since the driver docks windows directly rather than by
     * dragging, so it can't test that docking is blocked.
     */
    @Test
    void testWindowPopupNestedInterruptions() {
        final boolean[] isModalPopup = {false, false};
        final boolean[] openPopup = {false, false};
        final int[] variant = {0};
        final boolean[] showInterrupts = {false};
        ctx.setGui(
                () -> {
                    if (showInterrupts[0]) {
                        switch (variant[0]) {
                            case 1 -> {
                                // Remains open when the main menu bar appears
                                IkGui.beginMainMenuBar();
                                IkGui.endMainMenuBar();
                            }
                            case 2 -> {
                                // A modal remains open when an unrelated window appears, a
                                // popup closes
                                IkGui.begin(
                                        "FocusOnAppearing",
                                        null,
                                        WindowFlags.NO_SAVED_SETTINGS
                                                | WindowFlags.ALWAYS_AUTO_RESIZE);
                                IkGui.textUnformatted("...");
                                IkGui.end();
                            }
                            case 3 -> {
                                // Remains open when an unrelated no-focus window appears
                                IkGui.begin(
                                        "NoFocusOnAppearing",
                                        null,
                                        WindowFlags.NO_SAVED_SETTINGS
                                                | WindowFlags.ALWAYS_AUTO_RESIZE
                                                | WindowFlags.NO_FOCUS_ON_APPEARING);
                                IkGui.textUnformatted("...");
                                IkGui.end();
                            }
                                // Remains open when a tooltip appears
                            case 4 -> IkGui.setTooltip("...");
                            default -> {}
                        }
                    }

                    IkGui.begin(
                            "Interrupts",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS | WindowFlags.ALWAYS_AUTO_RESIZE);
                    final float spacing = (float) Math.floor(IkGuiInternal.getFontSize() * 2.0f);
                    final Vector2f pos =
                            new Vector2f(IkGui.getWindowPos())
                                    .add(spacing, IkGui.getWindowSize().y + spacing);
                    IkGui.setNextWindowPos(pos.x, pos.y, Condition.APPEARING);

                    boolean isOpen =
                            isModalPopup[0]
                                    ? IkGui.beginPopupModal(
                                            "Popup1",
                                            null,
                                            WindowFlags.NO_SAVED_SETTINGS
                                                    | WindowFlags.ALWAYS_AUTO_RESIZE)
                                    : IkGui.beginPopup(
                                            "Popup1",
                                            WindowFlags.NO_SAVED_SETTINGS
                                                    | WindowFlags.ALWAYS_AUTO_RESIZE);
                    if (isOpen) {
                        IkGui.text("This is Popup1" + (isModalPopup[0] ? " (modal)" : ""));
                        if (IkGui.button("Close") || IkGui.isKeyPressed(Key.ESCAPE)) {
                            IkGui.closeCurrentPopup();
                        }
                        if (IkGui.button("Open Popup2") || openPopup[1]) {
                            openPopup[1] = false;
                            IkGui.openPopup("Popup2");
                        }

                        if (variant[0] == 0) {
                            // Newly appearing windows should appear above this window
                            pos.add(spacing, spacing);
                            IkGui.setNextWindowPos(pos.x, pos.y, Condition.APPEARING);
                            IkGui.setNextWindowSize(100, 200, Condition.APPEARING);
                            IkGui.begin("Window0");
                            IkGui.textUnformatted("Another window.");
                            IkGui.end();

                            // Remains open when windows are created from inside a popup, and the
                            // windows can be moved
                            if (showInterrupts[0]) {
                                pos.add(spacing, spacing);
                                IkGui.setNextWindowPos(pos.x, pos.y, Condition.APPEARING);
                                IkGui.setNextWindowSize(100, 200, Condition.APPEARING);
                                IkGui.begin("Window1", null, WindowFlags.NO_SAVED_SETTINGS);
                                IkGui.beginChild("C", 100, 10);

                                // A window nested inside a child window
                                pos.add(spacing, spacing);
                                IkGui.setNextWindowPos(pos.x, pos.y, Condition.APPEARING);
                                IkGui.setNextWindowSize(100, 200, Condition.APPEARING);
                                IkGui.begin("Window2", null, WindowFlags.NO_SAVED_SETTINGS);
                                IkGui.end();

                                IkGui.endChild();
                                IkGui.end();
                            }
                        }

                        // A second popup layer
                        pos.add(spacing, spacing);
                        IkGui.setNextWindowPos(pos.x, pos.y, Condition.APPEARING);
                        final int popup2Flags =
                                WindowFlags.NO_SAVED_SETTINGS
                                        | WindowFlags.ALWAYS_AUTO_RESIZE
                                        | WindowFlags.MENU_BAR;
                        isOpen =
                                isModalPopup[1]
                                        ? IkGui.beginPopupModal("Popup2", null, popup2Flags)
                                        : IkGui.beginPopup("Popup2", popup2Flags);
                        if (isOpen) {
                            if (IkGui.beginMenuBar()) {
                                if (IkGui.beginMenu("File")) {
                                    IkGui.menuItem("...");
                                    IkGui.endMenu();
                                }
                                IkGui.endMenuBar();
                            }
                            IkGui.text("This is Popup2" + (isModalPopup[1] ? " (modal)" : ""));
                            if (IkGui.button("Close Popup2") || IkGui.isKeyPressed(Key.ESCAPE)) {
                                IkGui.closeCurrentPopup();
                            }

                            pos.add(spacing, spacing);
                            IkGui.setNextWindowPos(pos.x, pos.y, Condition.APPEARING);
                            IkGui.setNextWindowSize(100, 200, Condition.APPEARING);
                            IkGui.begin("Window3", null, WindowFlags.NO_SAVED_SETTINGS);
                            IkGui.button("Button");
                            IkGui.end();

                            IkGui.endPopup();
                        }
                        IkGui.endPopup();
                    }

                    if (IkGui.button("Open Popup1") || openPopup[0]) {
                        openPopup[0] = false;
                        IkGui.openPopup("Popup1");
                    }
                    IkGui.end();
                });
        final var g = ctx.context;
        final Vector2f moveDest = new Vector2f(IkGui.getMainViewport().position).add(300, 300);
        final java.util.function.ToIntFunction<String> displayIndex =
                ref -> IkGuiInternal.findWindowDisplayIndex(ctx.getWindowByRef(ref));
        final java.util.function.IntConsumer open =
                n -> {
                    openPopup[n] = true;
                    ctx.yieldFrames(2);
                };
        final java.util.function.Consumer<Boolean> setShowInterrupts =
                visible -> {
                    showInterrupts[0] = visible;
                    ctx.yieldFrames(2);
                };

        for (int popupKind = 0; popupKind < 4; ++popupKind) {
            final String d = "kind " + popupKind;
            isModalPopup[0] = (popupKind & 1) != 0;
            isModalPopup[1] = (popupKind & 2) != 0;
            variant[0] = 0;

            // Popup1 remains open while interacting with nested windows. Window0 appears above
            // it, and Window1 and Window2 (inside Window1's child) appear and can be moved.
            open.accept(0);
            final Window popup1 =
                    isModalPopup[0]
                            ? ctx.getWindowByRef("Popup1")
                            : IkGuiInternal.findWindowByID(
                                    ctx.popupGetWindowID("Interrupts/Popup1"));
            final Window window0 = ctx.getWindowByRef("Window0");
            assertNotNull(popup1, d);
            assertNotNull(window0, d);
            assertSame(window0, g.navFocusedWindow, d);
            assertTrue(popup1.active, d);
            // Window1 and Window2 appear
            setShowInterrupts.accept(true);
            final Window window1 = ctx.getWindowByRef("Window1");
            final Window window2 = ctx.getWindowByRef("Window2");
            assertTrue(
                    displayIndex.applyAsInt("//" + popup1.name)
                            < displayIndex.applyAsInt("Window0"),
                    d);
            assertTrue(displayIndex.applyAsInt("Window0") < displayIndex.applyAsInt("Window1"), d);
            assertTrue(displayIndex.applyAsInt("Window1") < displayIndex.applyAsInt("Window2"), d);
            assertNotEquals(moveDest, window1.position, d);
            assertNotEquals(moveDest, window2.position, d);
            ctx.windowMoveByDrag("Window1", moveDest.x, moveDest.y);
            ctx.windowMoveByDrag("Window2", moveDest.x, moveDest.y);
            // The windows can move
            assertEquals(moveDest, window1.position, d);
            assertEquals(moveDest, window2.position, d);
            // The popup or modal remains active
            assertTrue(popup1.active, d);
            setShowInterrupts.accept(false);
            ctx.popupCloseAll();

            // Popup2 blocks interactions with appearing windows when it's a modal, or closes
            // when it's a popup
            open.accept(0);
            assertSame(window0, g.navFocusedWindow, d);
            assertTrue(popup1.active, d);
            open.accept(1);
            final Window window3 = ctx.getWindowByRef("Window3");
            final Window popup2 =
                    isModalPopup[1]
                            ? ctx.getWindowByRef("Popup2")
                            : IkGuiInternal.findWindowByID(
                                    IkGuiTestContext.popupGetWindowID(
                                            com.ikalagaming.graphics.gui.util.Hash.getID(
                                                    "Popup2", popup1.id)));
            assertNotNull(window3, d);
            assertNotNull(popup2, d);
            assertSame(window3, g.navFocusedWindow, d);
            assertTrue(popup2.active, d);
            // Upstream notes that menuClick() doesn't work well with menus inside a popup
            ctx.itemClick("//" + popup2.name + "/##MenuBar/File");
            // Window1 and Window2 appear
            setShowInterrupts.accept(true);
            assertTrue(
                    displayIndex.applyAsInt("//" + popup1.name)
                            < displayIndex.applyAsInt("Window0"),
                    d);
            assertTrue(displayIndex.applyAsInt("Window0") < displayIndex.applyAsInt("Window1"), d);
            assertTrue(displayIndex.applyAsInt("Window1") < displayIndex.applyAsInt("Window2"), d);
            if (isModalPopup[1]) {
                // Appearing windows go below a modal
                assertTrue(
                        displayIndex.applyAsInt("Window2")
                                < displayIndex.applyAsInt("//" + popup2.name),
                        d);
                // The popup's window remains active as long as the popup is active
                assertTrue(window3.active, d);
            } else {
                // But above a popup, which gets deactivated
                assertTrue(
                        displayIndex.applyAsInt("Window2")
                                > displayIndex.applyAsInt("//" + popup2.name),
                        d);
                // The popup's window gets deactivated along with the popup
                assertFalse(window3.active, d);
            }
            ctx.windowMoveByDrag("Window1", moveDest.x, moveDest.y);
            ctx.windowMoveByDrag("Window2", moveDest.x, moveDest.y);
            if (isModalPopup[1]) {
                // Window interactions are blocked by the modal
                assertNotEquals(moveDest, window1.position, d);
                assertNotEquals(moveDest, window2.position, d);
                // The modal remains active
                assertTrue(popup2.active, d);
            } else {
                // The windows can move
                assertEquals(moveDest, window1.position, d);
                assertEquals(moveDest, window2.position, d);
                // The popup is closed
                assertFalse(popup2.active, d);
            }
            // The popup or modal remains active
            assertTrue(popup1.active, d);
            setShowInterrupts.accept(false);
            ctx.popupCloseAll();

            // The main menu bar or a window that takes focus appears. Popup1 remains open when
            // modal, and closes when it's a popup.
            for (int i = 0; i < 2; ++i) {
                variant[0]++;
                final String dv = d + " variant " + variant[0];
                open.accept(0);
                assertSame(popup1, g.navFocusedWindow, dv);
                assertTrue(popup1.active, dv);
                setShowInterrupts.accept(true);
                if (isModalPopup[0]) {
                    // The modal state is unchanged
                    assertSame(popup1, g.navFocusedWindow, dv);
                    assertTrue(popup1.active, dv);
                } else {
                    if (variant[0] == 1) {
                        // The popup closes, since the window doesn't have the no focus flag
                        assertEquals("Interrupts", g.navFocusedWindow.name, dv);
                    } else {
                        assertEquals("FocusOnAppearing", g.navFocusedWindow.name, dv);
                    }
                    assertFalse(popup1.active, dv);
                }
                setShowInterrupts.accept(false);
                ctx.popupCloseAll();
            }

            // A window with NO_FOCUS_ON_APPEARING or a tooltip appears, and Popup1 stays open
            for (int i = 0; i < 2; ++i) {
                variant[0]++;
                final String dv = d + " variant " + variant[0];
                open.accept(0);
                assertSame(popup1, g.navFocusedWindow, dv);
                assertTrue(popup1.active, dv);
                setShowInterrupts.accept(true);
                // The popup or modal state is unchanged
                assertSame(popup1, g.navFocusedWindow, dv);
                assertTrue(popup1.active, dv);
                setShowInterrupts.accept(false);
                ctx.popupCloseAll();
            }
        }
    }

    /**
     * window_popup_nested_interruptions_2: a focused window appears behind the lowest of nested
     * modals.
     */
    @Test
    void testWindowPopupNestedInterruptions2() {
        ctx.setGui(
                () -> {
                    if (IkGui.getIO().keyShift) {
                        IkGui.begin("Test Window 1", null, WindowFlags.NO_SAVED_SETTINGS);
                        IkGui.end();
                    }
                    IkGui.begin("Test Window 2", null, WindowFlags.NO_SAVED_SETTINGS);
                    if (IkGui.button("Open Modal 1")) {
                        IkGui.openPopup("Modal 1");
                    }
                    if (IkGui.beginPopupModal("Modal 1")) {
                        if (IkGui.button("Open Modal 2")) {
                            IkGui.openPopup("Modal 2");
                        }
                        if (IkGui.button("Close")) {
                            IkGui.closeCurrentPopup();
                        }
                        if (IkGui.beginPopupModal("Modal 2")) {
                            if (IkGui.button("Close")) {
                                IkGui.closeCurrentPopup();
                            }
                            IkGui.endPopup();
                        }
                        IkGui.endPopup();
                    }
                    IkGui.end();
                });
        ctx.itemClick("//Test Window 2/Open Modal 1");
        final Window modal1 = ctx.getWindowByRef("//$FOCUSED");
        ctx.itemClick("//$FOCUSED/Open Modal 2");
        final Window modal2 = ctx.getWindowByRef("//$FOCUSED");
        ctx.keyDown(KeyChord.ofMods(KeyModFlags.SHIFT));
        final Window window1 = ctx.getWindowByRef("//Test Window 1");
        assertNotNull(window1);
        assertTrue(
                IkGuiInternal.findWindowDisplayIndex(window1)
                        < IkGuiInternal.findWindowDisplayIndex(modal2));
        assertTrue(
                IkGuiInternal.findWindowDisplayIndex(window1)
                        < IkGuiInternal.findWindowDisplayIndex(modal1));
        ctx.keyUp(KeyChord.ofMods(KeyModFlags.SHIFT));
    }

    /** window_popup_nested_begin: begin() nested inside a popup can be hovered. */
    @Test
    void testWindowPopupNestedBegin() {
        ctx.setGui(
                () -> {
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    if (IkGui.button("Open Popup")) {
                        IkGui.openPopup("Popup");
                    }
                    if (IkGui.beginPopupModal("Popup")) {
                        IkGui.text("Hello from popup");
                        final Window window = IkGuiInternal.context.windowCurrent;
                        IkGui.setNextWindowPos(
                                window.position.x, window.position.y + window.size.y);
                        IkGui.begin("Nested Window", null, WindowFlags.NO_SAVED_SETTINGS);
                        IkGui.button("Button");
                        IkGui.end();
                        IkGui.endPopup();
                    }
                    IkGui.end();
                });
        final var g = ctx.context;
        ctx.itemClick("//Test Window/Open Popup");

        ctx.windowFocus("//Nested Window");
        ctx.mouseMoveNoFocus("//Nested Window/Button");
        assertEquals(ctx.getID("//Nested Window/Button"), g.hoveredID);

        ctx.windowFocus("//Popup");
        ctx.mouseMoveNoFocus("//Nested Window/Button");
        assertEquals(ctx.getID("//Nested Window/Button"), g.hoveredID);
    }

    /** window_popup_reopen_with_mouse: popups reopen at the same position. */
    @Test
    void testWindowPopupReopenWithMouse() {
        ctx.setGui(
                () -> {
                    IkGui.begin(
                            "Test Window",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS | WindowFlags.MENU_BAR);
                    IkGui.beginMenuBar();
                    if (IkGui.beginMenu("File")) {
                        IkGui.menuItem("Exit");
                        IkGui.endMenu();
                    }
                    IkGui.endMenuBar();
                    IkGui.button("Open Popup");
                    IkGui.openPopupOnItemClick("Popup", PopupFlags.MOUSE_BUTTON_RIGHT);
                    if (IkGui.beginPopup("Popup")) {
                        IkGui.endPopup();
                    }
                    IkGui.button("Open Modal");
                    IkGui.openPopupOnItemClick("Modal", PopupFlags.MOUSE_BUTTON_RIGHT);
                    if (IkGui.beginPopupModal("Modal")) {
                        if (IkGui.isKeyPressed(Key.ESCAPE)) {
                            IkGui.closeCurrentPopup();
                        }
                        IkGui.endPopup();
                    }
                    IkGui.end();
                });
        final var g = ctx.context;
        ctx.setRef("Test Window");
        // Upstream loops over three variants, though the third repeats the modal
        for (int variant = 0; variant < 3; ++variant) {
            final String d = "variant " + variant;
            final int expectedFlag =
                    variant == 0 ? WindowFlags.INTERNAL_POPUP : WindowFlags.INTERNAL_MODAL;
            ctx.yieldFrame();
            ctx.mouseMove(variant == 0 ? "Open Popup" : "Open Modal");
            final Window testWindow = g.windowHovered;
            assertNotNull(testWindow, d);
            ctx.mouseClick(MouseButton.RIGHT);
            final Vector2f mousePos = new Vector2f(g.io.mousePosition);
            final Window popup = g.navFocusedWindow;
            assertNotNull(popup, d);
            assertTrue((popup.flags & expectedFlag) != 0, d);
            final Vector2f pos = new Vector2f(popup.position);
            // Try to mess with the popup state by opening an unrelated popup at another position
            ctx.menuClick("File/Exit");
            ctx.mouseMoveToPos(mousePos.x, mousePos.y);
            ctx.mouseClick(MouseButton.RIGHT);
            assertTrue(popup.active, d);
            assertTrue((popup.flags & expectedFlag) != 0, d);
            assertEquals(pos, popup.position, d);
            ctx.popupCloseOne();
        }
    }

    /**
     * window_popup_child_without_input_blocking: a popup with the child window flag, used for
     * auto-completion.
     */
    @Test
    void testWindowPopupChildWithoutInputBlocking() {
        final IkBoolean open = new IkBoolean(true);
        final IkString str = new IkString(256);
        ctx.setGui(
                () -> {
                    if (!open.get()) {
                        return;
                    }
                    IkGui.begin("Test Window", open, WindowFlags.NO_SAVED_SETTINGS);
                    final boolean enterPressed =
                            IkGui.inputText("Input", str, InputTextFlags.ENTER_RETURNS_TRUE);
                    final boolean inputActive = IkGui.isItemActive();
                    final boolean inputActivated = IkGui.isItemActivated();
                    if (inputActivated) {
                        IkGui.openPopup("##popup");
                    }
                    IkGui.setNextWindowPos(IkGui.getItemRectMin().x, IkGui.getItemRectMax().y);
                    if (IkGui.beginPopup(
                            "##popup",
                            WindowFlags.NO_TITLE_BAR
                                    | WindowFlags.NO_MOVE
                                    | WindowFlags.NO_RESIZE
                                    | WindowFlags.INTERNAL_CHILD_WINDOW)) {
                        for (String option : new String[] {"cats", "dogs", "rabbits", "turtles"}) {
                            if (IkGui.selectable(option)) {
                                IkGuiInternal.clearActiveID();
                                str.set(option);
                            }
                        }
                        if (enterPressed || (!inputActive && !IkGui.isWindowFocused())) {
                            IkGui.closeCurrentPopup();
                        }
                        IkGui.endPopup();
                    }
                    IkGui.end();
                });
        ctx.setRef("Test Window");
        ctx.itemClick("Input");
        ctx.keyChars("aa");
        assertEquals("aa", str.get());
        // Upstream notes "cats" should be highlighted and not skipped
        ctx.keyPress(Key.ARROW_DOWN);
        ctx.keyPress(Key.ENTER);
        assertEquals("dogs", str.get());
        ctx.windowClose("");
        // This used to crash upstream
        ctx.yieldFrames(2);
    }

    /** window_popup_from_shortcut: opening a popup from a shortcut. */
    @Test
    void testWindowPopupFromShortcut() {
        ctx.setGui(
                () -> {
                    IkGui.setNextItemShortcut(
                            KeyChord.of(KeyModFlags.CTRL, Key.F1), InputFlags.ROUTE_GLOBAL);
                    if (IkGui.button("Open")) {
                        IkGui.openPopup("blah");
                    }
                    if (IkGui.beginPopup("blah")) {
                        IkGui.endPopup();
                    }
                });
        IkGuiInternal.focusWindow(
                null, com.ikalagaming.graphics.gui.flags.WindowFocusRequestFlags.NONE);
        ctx.keyPress(KeyChord.of(KeyModFlags.CTRL, Key.F1));
    }

    /** window_modal_begin_after: creating a window right after a modal opens. */
    @Test
    void testWindowModalBeginAfter() {
        ctx.setGui(
                () -> {
                    IkGui.begin("Test Window 1", null, WindowFlags.NO_SAVED_SETTINGS);
                    IkGui.openPopup("Popup");
                    if (IkGui.beginPopupModal("Popup")) {
                        IkGui.endPopup();
                    }
                    IkGui.begin("Test Window 2", null, WindowFlags.NO_SAVED_SETTINGS);
                    IkGui.end();
                    IkGui.end();
                });
        ctx.yieldFrames(3);
    }

    /** window_modal_bounds_exceeding_work_area: a modal taller than the work area. */
    @Test
    void testWindowModalBoundsExceedingWorkArea() {
        ctx.setGui(
                () -> {
                    if (IkGui.beginMainMenuBar()) {
                        IkGui.endMainMenuBar();
                    }
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    if (IkGui.button("Open Modal")) {
                        IkGui.openPopup("Modal");
                    }
                    if (IkGui.beginPopupModal("Modal", null, WindowFlags.NO_SAVED_SETTINGS)) {
                        IkGui.dummy(1.0f, IkGui.getIO().displaySize.y * 1.5f);
                        IkGui.endPopup();
                    }
                    IkGui.end();
                });
        final var style = ctx.context.style.variable;
        ctx.context.io.configWindowsMoveFromTitleBarOnly = true;
        ctx.yieldFrame();

        ctx.itemClick("//Test Window/Open Modal");

        final Window window = ctx.getWindowByRef("Modal");
        final Viewport viewport = IkGui.getMainViewport();
        final RectFloat workRect = viewport.getWorkRect(new RectFloat());
        final RectFloat mainRect = viewport.getMainRect(new RectFloat());
        mainRect.expand(
                -Math.max(style.displaySafeAreaPadding.x, style.displayWindowPadding.x),
                -Math.max(style.displaySafeAreaPadding.y, style.displayWindowPadding.y));

        // The modal is fully inside the work area, both while it is settling and once it has
        for (int i = 0; i < 3; ++i) {
            final RectFloat rect = new RectFloat(window.position, window.size);
            assertTrue(workRect.contains(rect) || mainRect.contains(rect), "frame " + i);
            ctx.yieldFrame();
        }
    }

    /** window_skipitems_basic: skipItems on regular and auto-resizing windows. */
    @Test
    void testWindowSkipItemsBasic() {
        ctx.setGui(
                () -> {
                    IkGui.begin("Test Window 1", null, WindowFlags.NO_SAVED_SETTINGS);
                    IkGui.text("Dummy text");
                    IkGui.end();
                    IkGui.begin(
                            "Test Window 2",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS | WindowFlags.ALWAYS_AUTO_RESIZE);
                    IkGui.text("Dummy text");
                    IkGui.end();
                });
        for (String name : new String[] {"Test Window 1", "Test Window 2"}) {
            ctx.setRef(name);
            final Window window = ctx.getWindowByRef("");
            ctx.windowCollapse("", false);
            assertNotNull(window, name);
            assertFalse(window.skipItems, name);
            ctx.windowCollapse("", true);
            assertTrue(window.skipItems, name);
            ctx.windowCollapse("", false);
        }
    }

    /** window_skipitems_child: child windows outside their parent's visible area are clipped. */
    @Test
    void testWindowSkipItemsChild() {
        final int[] frame = {-2};
        ctx.setGui(
                () -> {
                    IkGui.setNextWindowSize(200, 200);
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    IkGui.setCursorPos(0, 0);
                    IkGui.beginChild("Child 1", 100, 100);
                    if (frame[0] == 2) {
                        ctx.check(!IkGuiInternal.context.windowCurrent.skipItems, "child 1");
                    }
                    IkGui.endChild();
                    IkGui.setCursorPos(300, 300);
                    IkGui.beginChild("Child 2", 100, 100);
                    if (frame[0] == 2) {
                        ctx.check(IkGuiInternal.context.windowCurrent.skipItems, "child 2");
                    }
                    IkGui.endChild();
                    IkGui.end();
                    frame[0]++;
                });
        // Upstream finishes when the frame count reaches 2
        ctx.yieldFrames(4);
    }

    /** window_child_layout_autoresize: ALWAYS_AUTO_RESIZE with child windows. */
    @Test
    void testWindowChildLayoutAutoResize() {
        final int[] count = {0};
        ctx.setGui(
                () -> {
                    IkGui.begin("Test Debug");
                    IkGui.dragInt("Lines Count", count, 0.2f, 0, 100);
                    IkGui.end();

                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    IkGui.setNextWindowSizeConstraints(
                            0.0f, 0.0f, Float.MAX_VALUE, IkGui.getTextLineHeightWithSpacing() * 10);
                    IkGui.beginChild(
                            "Child 1",
                            -Float.MIN_VALUE,
                            0.0f,
                            ChildFlags.BORDERS | ChildFlags.AUTO_RESIZE_Y);
                    for (int n = 0; n < count[0]; ++n) {
                        IkGui.text("Line " + n);
                    }
                    IkGui.endChild();
                    IkGui.end();
                });
        final var style = ctx.context.style.variable;
        count[0] = 5;
        ctx.yieldFrame();
        // Set the width
        ctx.windowResize("//Test Window", 200.0f, 200.0f);
        final Window window = ctx.windowInfo("//Test Window");
        final Window child = ctx.windowInfo("//Test Window/Child 1");
        assertNotNull(window);
        assertNotNull(child);
        // Then auto-fit, which upstream does by resizing to (-1, -1)
        ctx.itemDoubleClick(IkGuiImplWindows.getWindowResizeID(window, 0));

        assertEquals(window.size.x - style.windowPadding.y * 2.0f, child.size.x);
        assertEquals(
                IkGui.getTextLineHeight() * 5
                        + style.itemSpacing.y * 4
                        + style.windowPadding.y * 2.0f,
                child.size.y);

        count[0] = 20;
        ctx.yieldFrame();
        // Auto-fit
        ctx.itemDoubleClick(IkGuiImplWindows.getWindowResizeID(window, 0));
        assertEquals(window.size.x - style.windowPadding.y * 2.0f, child.size.x);
        assertEquals(IkGui.getTextLineHeightWithSpacing() * 10, child.size.y);
    }

    /** window_child_layout_size: child windows affect the content size. */
    @Test
    void testWindowChildLayoutSize() {
        final int[] frame = {-2};
        ctx.setGui(
                () -> {
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    IkGui.text("Hi");
                    IkGui.beginChild("Child 1", 100, 100, ChildFlags.BORDERS);
                    IkGui.endChild();
                    if (frame[0] == 2) {
                        ctx.checkEquals(
                                new Vector2f(100, 100 + IkGui.getTextLineHeightWithSpacing()),
                                IkGuiInternal.context.windowCurrent.contentSize,
                                "content size");
                    }
                    IkGui.end();
                    frame[0]++;
                });
        // Upstream finishes when the frame count reaches 2
        ctx.yieldFrames(4);
    }

    /**
     * window_child_maxsize: a child's maximum size isn't clamped, but a child menu's is clamped to
     * the viewport.
     */
    @Test
    void testWindowChildMaxSize() {
        ctx.setGui(
                () -> {
                    IkGui.begin(
                            "Test Window",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS | WindowFlags.MENU_BAR);
                    if (IkGui.beginMenuBar()) {
                        if (IkGui.beginMenu("File")) {
                            IkGui.menuItem("Item1");
                            if (IkGui.beginMenu("Submenu")) {
                                IkGui.menuItem("Item2");
                                for (int n = 0; n < 100; ++n) {
                                    IkGui.text("Filler");
                                }
                                IkGui.endMenu();
                            }
                            IkGui.endMenu();
                        }
                        IkGui.endMenuBar();
                    }
                    IkGui.beginChild(
                            "Child 0",
                            0.0f,
                            0.0f,
                            ChildFlags.BORDERS
                                    | ChildFlags.AUTO_RESIZE_X
                                    | ChildFlags.AUTO_RESIZE_Y);
                    IkGui.dummy(500, 10_000);
                    IkGui.endChild();
                    IkGui.end();
                });
        // Upstream's warm-up frames let the auto-resizing child measure itself
        ctx.yieldFrame();
        final var style = ctx.context.style.variable;
        final Window child = ctx.windowInfo("//Test Window/Child 0");
        assertEquals(
                new Vector2f(500, 10_000)
                        .add(style.windowPadding.x * 2.0f, style.windowPadding.y * 2.0f),
                child.size);

        ctx.setRef("Test Window");
        ctx.menuClick("File/Submenu");
        final Window menu = ctx.getWindowByRef("//$FOCUSED");
        assertNotNull(menu);
        assertTrue(menu.size.y < menu.viewport.size.y);
    }

    /** window_child_resize: ChildFlags.RESIZE_X. */
    @Test
    void testWindowChildResize() {
        ctx.setGui(
                () -> {
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    if (IkGui.button("Set width to 200")) {
                        IkGui.setNextWindowSize(200, -Float.MIN_VALUE);
                    }
                    IkGui.beginChild(
                            "Child 1",
                            150,
                            -Float.MIN_VALUE,
                            ChildFlags.BORDERS | ChildFlags.RESIZE_X);
                    for (int n = 0; n < 20; ++n) {
                        IkGui.selectable("Object " + n);
                    }
                    IkGui.selectable("Object with Long Name");
                    IkGui.endChild();
                    IkGui.sameLine();
                    IkGui.beginChild("Child 2", 150, -Float.MIN_VALUE);
                    IkGui.text("Contents");
                    IkGui.endChild();
                    IkGui.end();
                });
        final var style = ctx.context.style.variable;
        ctx.setRef("Test Window");
        final Window child1 = ctx.windowInfo("Child 1");
        final Window child2 = ctx.windowInfo("Child 2");
        assertNotNull(child1);
        assertNotNull(child2);

        // Upstream resizes the child by dragging its right border, then double clicks it
        final java.util.function.Consumer<Float> resizeChild =
                width -> {
                    final float x = child1.position.x + child1.size.x;
                    final float y = child1.position.y + child1.size.y * 0.5f;
                    ctx.mouseMoveToPos(x, y);
                    ctx.mouseDown(MouseButton.LEFT);
                    ctx.mouseMoveToPos(x + (width - child1.size.x), y);
                    ctx.mouseUp(MouseButton.LEFT);
                };
        final float longNameWidth = IkGui.calcTextSize("Object with Long Name").x;

        // Without a scrollbar
        ctx.windowResize("", 500.0f, 30 * IkGui.getTextLineHeightWithSpacing());
        resizeChild.accept(300.0f);
        resizeChild.accept(100.0f);
        ctx.mouseDoubleClick(MouseButton.LEFT);
        assertFalse(child1.scrollbarY);
        assertEquals(longNameWidth + style.windowPadding.x * 2.0f, child1.size.x);

        // With a scrollbar
        ctx.windowResize("", 500.0f, 3 * IkGui.getFrameHeightWithSpacing());
        resizeChild.accept(300.0f);
        resizeChild.accept(100.0f);
        ctx.mouseDoubleClick(MouseButton.LEFT);
        assertTrue(child1.scrollbarY);
        assertEquals(
                longNameWidth + style.windowPadding.x * 2.0f + style.scrollbarSize, child1.size.x);

        ctx.itemClick("Set width to 200");
        assertEquals(200.0f, child1.size.x);
    }

    /** window_scroll_001: setScrollHereY() scrolls all the way, and the expected scroll max. */
    @Test
    void testWindowScroll001() {
        ctx.setGui(
                () -> {
                    IkGui.setNextWindowSize(0.0f, 500);
                    IkGui.begin("Test Scrolling", null, WindowFlags.NO_SAVED_SETTINGS);
                    for (int n = 0; n < 100; ++n) {
                        IkGui.text("Hello " + n + "\n");
                    }
                    IkGui.setScrollHereY(0.0f);
                    IkGui.end();
                });
        ctx.yieldFrames(2);
        final Window window = IkGuiInternal.findWindowByName("Test Scrolling");
        final var style = ctx.context.style.variable;

        assertTrue(window.contentSize.y > 0.0f);
        final float scrollY = window.scrollPosition.y;
        final float scrollMaxY = window.scrollMax.y;
        assertTrue(scrollY > 0.0f);
        assertEquals(scrollMaxY, scrollY);

        // The definition of the content size since upstream 1.71
        final float expectedContentY =
                100 * IkGui.getTextLineHeightWithSpacing() - style.itemSpacing.y;
        assertEquals(expectedContentY, window.contentSize.y, EPSILON);

        final float expectedScrollMaxY =
                expectedContentY + window.padding.y * 2.0f - window.rectInner.getHeight();
        assertEquals(expectedScrollMaxY, scrollMaxY, EPSILON);
    }

    /** window_scroll_002: the scroll max is exactly zero with forced scrollbars. */
    @Test
    void testWindowScroll002() {
        final int[] frame = {-2};
        ctx.setGui(
                () -> {
                    IkGui.begin(
                            "Test Scrolling 1",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS
                                    | WindowFlags.ALWAYS_HORIZONTAL_SCROLLBAR
                                    | WindowFlags.ALWAYS_AUTO_RESIZE);
                    IkGui.dummy(200, 200);
                    final Window window1 = IkGuiInternal.context.windowCurrent;
                    ctx.checkEquals(0.0f, window1.scrollMax.x, "window 1 x");
                    ctx.checkEquals(0.0f, window1.scrollMax.y, "window 1 y");
                    IkGui.end();

                    IkGui.begin(
                            "Test Scrolling 2",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS
                                    | WindowFlags.ALWAYS_VERTICAL_SCROLLBAR
                                    | WindowFlags.ALWAYS_AUTO_RESIZE);
                    IkGui.dummy(200, 200);
                    final Window window2 = IkGuiInternal.context.windowCurrent;
                    ctx.checkEquals(0.0f, window2.scrollMax.x, "window 2 x");
                    ctx.checkEquals(0.0f, window2.scrollMax.y, "window 2 y");
                    IkGui.end();
                    frame[0]++;
                });
        // Upstream finishes when the frame count reaches 2
        ctx.yieldFrames(4);
    }

    /** window_scroll_003: setScrollY() and getScrollY() values match. */
    @Test
    void testWindowScroll003() {
        final int[] step = {0};
        ctx.setGui(
                () -> {
                    IkGui.begin(
                            "Test Scrolling 3",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS
                                    | (step[0] == 1 ? WindowFlags.MENU_BAR : 0));
                    if (step[0] == 1 && IkGui.beginMenuBar()) {
                        IkGui.endMenuBar();
                    }
                    for (int n = 0; n < 100; ++n) {
                        IkGui.text("Line " + n);
                    }
                    IkGui.end();
                });
        final Window window = IkGuiInternal.findWindowByName("Test Scrolling 3");
        ctx.windowResize("//Test Scrolling 3", 200.0f, IkGui.getTextLineHeight() * 50.0f);
        for (int s = 0; s < 2; ++s) {
            step[0] = s;
            ctx.yieldFrame();
            IkGuiInternal.setScrollY(window, 0.0f);
            ctx.yieldFrame();
            assertEquals(0.0f, window.scrollPosition.y, "step " + s);
            ctx.yieldFrame();
            IkGuiInternal.setScrollY(window, 100.0f);
            ctx.yieldFrame();
            assertEquals(100.0f, window.scrollPosition.y, "step " + s);
        }
    }

    /**
     * window_scroll_latency: scroll functions apply on the next frame, except
     * setNextWindowScroll(), which applies immediately.
     */
    @Test
    void testWindowScrollLatency() {
        final int[] testStep = {0};
        final Vector2f scrollTarget = new Vector2f();
        final Vector2f gotScroll = new Vector2f();
        ctx.setGui(
                () -> {
                    IkGui.setNextWindowSize(100.0f, 100.0f);
                    if (testStep[0] == 1) {
                        IkGui.setNextWindowScroll(scrollTarget.x, scrollTarget.y);
                    }
                    IkGui.begin(
                            "Test", null, WindowFlags.NO_SAVED_SETTINGS | WindowFlags.NO_RESIZE);
                    IkGui.dummy(500.0f, 500.0f);
                    switch (testStep[0]) {
                        case 2 -> IkGui.setScrollX(scrollTarget.x);
                        case 3 -> IkGui.setScrollY(scrollTarget.y);
                        case 4 -> IkGui.setScrollHereX();
                        case 5 -> IkGui.setScrollHereY();
                        case 6 -> IkGui.setScrollFromPosX(scrollTarget.x);
                        case 7 -> IkGui.setScrollFromPosY(scrollTarget.y);
                        default -> {}
                    }
                    if (testStep[0] != 0) {
                        gotScroll.set(IkGui.getScrollX(), IkGui.getScrollY());
                        testStep[0] = 0;
                    }
                    IkGui.end();
                });
        for (int step = 1; step <= 7; ++step) {
            final String d = "step " + step;
            // Reset the window's scroll
            testStep[0] = 1;
            scrollTarget.set(0.0f, 0.0f);
            ctx.yieldFrame();

            final Window window = ctx.getWindowByRef("Test");
            assertNotNull(window, d);
            assertEquals(new Vector2f(0.0f, 0.0f), window.scrollPosition, d);

            // Scroll in the UI code
            testStep[0] = step;
            scrollTarget.set(100.0f, 200.0f);
            gotScroll.set(0, 0);
            ctx.yieldFrame();

            // setNextWindowScroll() applies immediately
            if (step == 1) {
                assertEquals(new Vector2f(100.0f, 200.0f), gotScroll, d);
                continue;
            }

            // For all the others, the API doesn't reflect the scroll until the next frame
            assertEquals(new Vector2f(0.0f, 0.0f), gotScroll, d);
            // Don't scroll, just get the scroll
            testStep[0] = -1;
            ctx.yieldFrame();

            // The right axis moved. The exact values don't matter, just near or far from zero.
            if (step % 2 == 0) {
                assertTrue(gotScroll.x > 1.0f, d);
                assertEquals(0.0f, gotScroll.y, EPSILON, d);
            } else {
                assertEquals(0.0f, gotScroll.x, EPSILON, d);
                assertTrue(gotScroll.y > 1.0f, d);
            }
        }
    }

    /**
     * window_scroll_tracking: setScrollHereX()/setScrollHereY() make items fully visible, whatever
     * the window padding and item spacing.
     */
    @Test
    void testWindowScrollTracking() {
        final int[] variant = {0};
        final boolean[][] fullyVisible = new boolean[30][30];
        final int[] track = {0, 0};
        ctx.setGui(
                () -> {
                    IkGui.begin(
                            "Test Window",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS | WindowFlags.ALWAYS_AUTO_RESIZE);
                    if (IkGui.button("Track 0,0")) {
                        track[0] = 0;
                        track[1] = 0;
                    }
                    if (IkGui.button("Track 29,0")) {
                        track[0] = 29;
                        track[1] = 0;
                    }
                    if (IkGui.button("Track 0,29")) {
                        track[0] = 0;
                        track[1] = 29;
                    }
                    if (IkGui.button("Track 29,29")) {
                        track[0] = 29;
                        track[1] = 29;
                    }

                    if ((variant[0] & 1) != 0) {
                        IkGui.pushStyleVarFloat2(StyleVariable.WINDOW_PADDING, 1, 1);
                    }
                    if ((variant[0] & 2) != 0) {
                        IkGui.pushStyleVarFloat2(StyleVariable.ITEM_SPACING, 1, 1);
                    }
                    IkGui.beginChild(
                            "Scrolling",
                            500,
                            500,
                            ChildFlags.BORDERS,
                            WindowFlags.ALWAYS_HORIZONTAL_SCROLLBAR
                                    | WindowFlags.HORIZONTAL_SCROLLBAR);
                    for (int y = 0; y < 30; ++y) {
                        for (int x = 0; x < 30; ++x) {
                            if (x > 0) {
                                IkGui.sameLine();
                            }
                            IkGui.button(
                                    String.format(
                                            "%d Item x%d y%d###%d,%d",
                                            fullyVisible[y][x] ? 1 : 0, x, y, x, y));
                            if (track[0] == x && track[1] == y) {
                                IkGui.setScrollHereX(track[0] / 29.0f);
                                IkGui.setScrollHereY(track[1] / 29.0f);
                            }
                            final RectFloat clip =
                                    IkGuiInternal.context.windowCurrent.rectCurrentClip;
                            fullyVisible[y][x] =
                                    clip.contains(IkGuiInternal.context.lastItemData.rect);
                        }
                    }
                    track[0] = -1;
                    track[1] = -1;
                    IkGui.endChild();
                    if ((variant[0] & 1) != 0) {
                        IkGui.popStyleVar();
                    }
                    if ((variant[0] & 2) != 0) {
                        IkGui.popStyleVar();
                    }
                    IkGui.end();
                });
        ctx.setRef("Test Window");
        // Upstream checks the scroll of the outer window here
        final Window window = ctx.getWindowByRef("");
        assertNotNull(window);

        for (int v = 0; v < 4; ++v) {
            final String d = "variant " + v;
            variant[0] = v;
            ctx.yieldFrame();

            ctx.itemClick("Track 29,29");
            assertTrue(fullyVisible[29][29], d);
            assertFalse(fullyVisible[0][0], d);
            assertEquals(window.scrollMax.x, window.scrollPosition.x, d);
            assertEquals(window.scrollMax.y, window.scrollPosition.y, d);

            ctx.itemClick("Track 0,29");
            assertTrue(fullyVisible[29][0], d);
            assertEquals(0.0f, window.scrollPosition.x, d);
            assertEquals(window.scrollMax.y, window.scrollPosition.y, d);

            ctx.itemClick("Track 29,0");
            assertTrue(fullyVisible[0][29], d);
            assertEquals(0.0f, window.scrollPosition.y, d);

            ctx.itemClick("Track 0,0");
            assertTrue(fullyVisible[0][0], d);
            assertFalse(fullyVisible[29][29], d);
            assertEquals(0.0f, window.scrollPosition.x, d);
            assertEquals(0.0f, window.scrollPosition.y, d);
        }
    }

    /** window_scroll_while_resizing: a nested auto-fit window doesn't scroll while resizing. */
    @Test
    void testWindowScrollWhileResizing() {
        final boolean[] warmUp = {true};
        ctx.setGui(
                () -> {
                    IkGui.begin("Test Scrolling", null, WindowFlags.NO_SAVED_SETTINGS);
                    IkGui.text("Below is a child window");
                    IkGui.beginChild("blah", 0, 0, 0, WindowFlags.HORIZONTAL_SCROLLBAR);
                    final Window child = IkGuiInternal.context.windowCurrent;
                    IkGui.endChild();
                    ctx.check(!child.scrollbarY, "child scrollbar");
                    if (!warmUp[0]) {
                        ctx.check(
                                !IkGuiInternal.context.windowCurrent.scrollbarY,
                                "window scrollbar");
                    }
                    IkGui.end();
                });
        ctx.yieldFrame();
        warmUp[0] = false;
        ctx.windowResize("Test Scrolling", 400, 400);
        ctx.windowResize("Test Scrolling", 100, IkGui.getFrameHeightWithSpacing() * 3);
    }

    /** window_scroll_visibility: when the vertical scrollbar shows up. */
    @Test
    void testWindowScrollVisibility() {
        final Vector2f inSize = new Vector2f();
        final Vector2f inDeclaredContentSize = new Vector2f();
        final Vector2f inSubmittedContentSize = new Vector2f();
        final boolean[] inSubmittedContentAuto = {false};
        final Window[] outWindow = {null};
        final boolean[] outScrollbarYOred = {false};
        ctx.setGui(
                () -> {
                    if (inSize.x >= 0.0f || inSize.y >= 0.0f) {
                        IkGui.setNextWindowSize(inSize.x, inSize.y);
                    }
                    if (inDeclaredContentSize.x >= 0.0f || inDeclaredContentSize.y >= 0.0f) {
                        IkGui.setNextWindowContentSize(
                                inDeclaredContentSize.x, inDeclaredContentSize.y);
                    }
                    IkGui.begin(
                            "Test Window",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS | WindowFlags.NO_TITLE_BAR);
                    if (inSubmittedContentAuto[0]) {
                        IkGui.dummy(IkGui.getContentRegionAvailable());
                    } else if (inSubmittedContentSize.x >= 0.0f
                            || inSubmittedContentSize.y >= 0.0f) {
                        IkGui.dummy(inSubmittedContentSize);
                    }
                    outWindow[0] = IkGuiInternal.context.windowCurrent;
                    outScrollbarYOred[0] |= outWindow[0].scrollbarY;
                    IkGui.end();
                });
        final var style = ctx.context.style.variable;

        inSize.set(100, 100);
        inDeclaredContentSize.set(-1, -1);
        inSubmittedContentSize.set(-1, -1);
        ctx.yieldFrame();
        assertFalse(outWindow[0].scrollbarY);

        // setNextWindowContentSize()
        inSize.set(100, 100);
        inDeclaredContentSize.set(50.0f, 100.0f - style.windowPadding.y * 2.0f);
        inSubmittedContentSize.set(-1, -1);
        ctx.yieldFrame();
        assertFalse(outWindow[0].scrollbarY);
        inDeclaredContentSize.y += 1.0f;
        ctx.yieldFrame();
        assertTrue(outWindow[0].scrollbarY);

        // Actual contents with dummy()
        inSize.set(100, 100);
        inDeclaredContentSize.set(-1, -1);
        inSubmittedContentSize.set(50.0f, 100.0f - style.windowPadding.y * 2.0f);
        ctx.yieldFrame();
        assertFalse(outWindow[0].scrollbarY);
        inSubmittedContentSize.y += 1.0f;
        ctx.yieldFrame();
        // It takes a frame to measure the content size
        assertFalse(outWindow[0].scrollbarY);
        ctx.yieldFrame();
        assertTrue(outWindow[0].scrollbarY);

        // A dummy() filling all the available space
        inSize.set(100, 100);
        inDeclaredContentSize.set(-1, -1);
        inSubmittedContentSize.set(-1, -1);
        inSubmittedContentAuto[0] = true;
        ctx.yieldFrames(2);
        assertFalse(outWindow[0].scrollbarY);
        inSize.set(90, 90);
        ctx.yieldFrame();
        inSize.set(80, 80);
        ctx.yieldFrame();
        assertFalse(outWindow[0].scrollbarY);
        inSize.set(-1, -1);
        outScrollbarYOred[0] = false;
        ctx.windowResize("//Test Window", 50, 50);
        // The scrollbar never appears
        assertFalse(outScrollbarYOred[0]);

        // Changing the size and the content size together (upstream issue #7252)
        inSize.set(100, 100);
        inDeclaredContentSize.set(100.0f, 100.0f - style.windowPadding.y * 2.0f);
        inSubmittedContentSize.set(100.0f, 100.0f - style.windowPadding.y * 2.0f);
        inSubmittedContentAuto[0] = false;
        ctx.yieldFrame();
        assertFalse(outWindow[0].scrollbarY);
        ctx.yieldFrame();
        assertFalse(outWindow[0].scrollbarY);
        // Increase the contents
        inSize.y += 50.0f;
        inDeclaredContentSize.y += 50.0f;
        inSubmittedContentSize.y += 50.0f;
        ctx.yieldFrame();
        assertFalse(outWindow[0].scrollbarY);
        ctx.yieldFrame();
        assertFalse(outWindow[0].scrollbarY);
    }

    /** window_scroll_wheel: scrolling a window with the mouse wheel. */
    @Test
    void testWindowScrollWheel() {
        ctx.setGui(
                () -> {
                    IkGui.setNextWindowSize(100, 100, Condition.ALWAYS);
                    IkGui.begin(
                            "Test Window",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS | WindowFlags.HORIZONTAL_SCROLLBAR);
                    IkGui.dummy(200, 200);
                    IkGui.end();
                });
        final Window window = ctx.getWindowByRef("Test Window");
        IkGuiInternal.setScrollX(window, 0);
        IkGuiInternal.setScrollY(window, 0);
        ctx.yieldFrame();

        ctx.mouseMoveToPos(
                window.position.x + window.size.x * 0.5f, window.position.y + window.size.y * 0.5f);
        assertEquals(0.0f, window.scrollPosition.x);
        assertEquals(0.0f, window.scrollPosition.y);
        // Scroll down
        ctx.mouseWheel(0, -5.0f);
        assertEquals(0.0f, window.scrollPosition.x);
        assertTrue(window.scrollPosition.y > 0.0f);
        // Scroll up
        ctx.mouseWheel(0, 5.0f);
        assertEquals(0.0f, window.scrollPosition.x);
        assertEquals(0.0f, window.scrollPosition.y);

        for (int n = 0; n < 2; ++n) {
            final String d = n == 0 ? "horizontal wheel" : "shift + vertical wheel";
            // Scroll right
            if (n == 0) {
                ctx.mouseWheel(-5.0f, 0);
            } else {
                ctx.keyDown(KeyChord.ofMods(KeyModFlags.SHIFT));
                ctx.mouseWheel(0, -5.0f);
                ctx.keyUp(KeyChord.ofMods(KeyModFlags.SHIFT));
            }
            assertTrue(window.scrollPosition.x > 0.0f, d);
            assertEquals(0.0f, window.scrollPosition.y, d);
            // Scroll left
            if (n == 0) {
                ctx.mouseWheel(5.0f, 0);
            } else {
                ctx.keyDown(KeyChord.ofMods(KeyModFlags.SHIFT));
                ctx.mouseWheel(0, 5.0f);
                ctx.keyUp(KeyChord.ofMods(KeyModFlags.SHIFT));
            }
            assertEquals(0.0f, window.scrollPosition.x, d);
            assertEquals(0.0f, window.scrollPosition.y, d);
        }
    }

    /** window_scroll_wheel_lock: the wheel locks onto a window while scrolling over a child. */
    @Test
    void testWindowScrollWheelLock() {
        ctx.setGui(
                () -> {
                    IkGui.setNextWindowSize(200.0f, 200.0f, Condition.ALWAYS);
                    IkGui.begin(
                            "Test Window",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS | WindowFlags.HORIZONTAL_SCROLLBAR);
                    IkGui.setCursorPosX(IkGuiInternal.context.windowCurrent.size.y * 0.5f);
                    IkGui.button("Top");
                    IkGui.button("Left");
                    IkGui.sameLine();
                    IkGui.beginChild(
                            "Child",
                            100.0f,
                            1000.0f,
                            ChildFlags.BORDERS,
                            WindowFlags.NO_SAVED_SETTINGS | WindowFlags.HORIZONTAL_SCROLLBAR);
                    IkGui.dummy(80.0f, 1000.0f + 80.0f);
                    IkGui.endChild();
                    IkGui.sameLine();
                    IkGui.dummy(80.0f, 1000.0f + 80.0f);
                    IkGui.end();
                });
        final var g = ctx.context;
        final Window window = ctx.getWindowByRef("Test Window");
        final Window child = ctx.windowInfo("Test Window/Child");
        assertNotNull(window);
        assertNotNull(child);
        IkGuiInternal.setScrollX(window, 0);
        IkGuiInternal.setScrollY(window, 0);
        IkGuiInternal.setScrollX(child, 0);
        IkGuiInternal.setScrollY(child, 0);
        ctx.yieldFrame();

        ctx.setRef("Test Window");
        assertEquals(0.0f, window.scrollPosition.y);
        assertEquals(0.0f, child.scrollPosition.y);

        // Vertical scrolling goes over the child, but doesn't scroll it
        ctx.mouseMove("Top");
        // Not hovering the child at first
        assertSame(window, g.windowHovered);
        ctx.mouseWheel(0, -3.0f);
        // Upstream notes the hovered window takes a frame to update
        ctx.yieldFrame();
        // The main window was scrolled, but not the child
        assertTrue(window.scrollPosition.y > 0.0f);
        assertEquals(0.0f, child.scrollPosition.y);
        // The scrolling left the mouse over the child
        assertSame(child, g.windowHovered);

        // After a pause, scrolling while over the child scrolls it
        final float prevScrollY = window.scrollPosition.y;
        ctx.sleepNoSkip(2.0f, 0.5f);
        ctx.mouseWheel(0, -3.0f);
        assertEquals(prevScrollY, window.scrollPosition.y);
        assertTrue(child.scrollPosition.y > 0.0f);
        // Still hovering the child
        assertSame(child, g.windowHovered);

        // Horizontal scrolling, with a horizontal wheel or a vertical wheel and shift
        IkGuiInternal.setScrollY(window, 0);
        IkGuiInternal.setScrollY(child, 0);
        for (int n = 0; n < 2; ++n) {
            final String d = n == 0 ? "horizontal wheel" : "shift + vertical wheel";
            IkGuiInternal.setScrollX(window, 0);
            IkGuiInternal.setScrollX(child, 0);
            ctx.yieldFrame();
            assertEquals(0.0f, window.scrollPosition.x, d);
            assertEquals(0.0f, child.scrollPosition.x, d);

            // The scrolling goes over the child, but doesn't scroll it. Upstream notes the mouse
            // needs to move, or the child would take the wheel as if it was hovered.
            ctx.mouseMoveToPos(child.position.x, child.position.y);
            ctx.mouseMove("Left");
            // Not hovering the child at first
            assertSame(window, g.windowHovered, d);
            if (n == 0) {
                ctx.mouseWheel(-3.0f, 0);
                ctx.yieldFrame();
            } else {
                ctx.keyDown(KeyChord.ofMods(KeyModFlags.SHIFT));
                ctx.mouseWheel(0, -3.0f);
                ctx.keyUp(KeyChord.ofMods(KeyModFlags.SHIFT));
            }
            final float prevScrollX = window.scrollPosition.x;
            // The main window was scrolled, but not the child
            assertTrue(window.scrollPosition.x > 0.0f, d);
            assertEquals(0.0f, child.scrollPosition.x, d);
            // The scrolling happened over the child
            assertSame(child, g.windowHovered, d);

            // After a pause, scrolling while over the child scrolls it
            ctx.sleepNoSkip(2.0f, 0.5f);
            if (n == 0) {
                ctx.mouseWheel(-3.0f, 0);
            } else {
                ctx.keyDown(KeyChord.ofMods(KeyModFlags.SHIFT));
                ctx.mouseWheel(0, -3.0f);
                ctx.keyUp(KeyChord.ofMods(KeyModFlags.SHIFT));
                ctx.yieldFrame();
            }
            assertEquals(prevScrollX, window.scrollPosition.x, d);
            assertTrue(child.scrollPosition.x > 0.0f, d);
            // Still hovering the child
            assertSame(child, g.windowHovered, d);
        }
    }

    /** window_scroll_wheel_lock_2: the wheel lock with ambiguous scrolling and inertia. */
    @Test
    void testWindowScrollWheelLock2() {
        ctx.setGui(
                () -> {
                    IkGui.setNextWindowSize(200.0f, 200.0f, Condition.ALWAYS);
                    IkGui.begin(
                            "Test Window",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS | WindowFlags.HORIZONTAL_SCROLLBAR);
                    IkGui.button("Button 1");
                    IkGui.beginChild("Child", 0.0f, 200.0f, ChildFlags.BORDERS);
                    IkGui.button("Button 2");
                    IkGui.text(
                            "this is a long line of text. this is a long line of text. this is a"
                                    + " long line of text. this is a long line of text. ");
                    IkGui.endChild();
                    for (int n = 0; n < 20; ++n) {
                        IkGui.text(" ".repeat(n) + " some extra contents");
                    }
                    IkGui.end();
                });
        final var g = ctx.context;
        final Window window = ctx.getWindowByRef("Test Window");
        final Window child = ctx.windowInfo("Test Window/Child");
        assertNotNull(window);
        assertNotNull(child);
        IkGuiInternal.setScrollX(window, 0);
        IkGuiInternal.setScrollY(window, 0);
        IkGuiInternal.setScrollX(child, 0);
        IkGuiInternal.setScrollY(child, 0);
        ctx.yieldFrame();

        ctx.setRef("Test Window");
        assertEquals(new Vector2f(0.0f, 0.0f), window.scrollPosition);
        assertEquals(new Vector2f(0.0f, 0.0f), child.scrollPosition);

        // Horizontal scrolling
        ctx.mouseMove("**/Button 2");
        ctx.mouseWheel(-10.0f, 0);
        ctx.yieldFrame();
        assertEquals(0.0f, window.scrollPosition.x);
        assertTrue(child.scrollPosition.x > 0.0f);
        assertSame(child, g.windowWheeling);

        // We are locked
        ctx.mouseWheel(0, 1.0f);
        assertEquals(0.0f, window.scrollPosition.y);
        assertEquals(0.0f, child.scrollPosition.y);
        assertSame(child, g.windowWheeling);
        ctx.sleepNoSkip(2.0f, 0.5f);
        assertNull(g.windowWheeling);
        IkGuiInternal.setScrollX(child, 0);
        IkGuiInternal.setScrollY(window, 0);
        ctx.yieldFrame();

        // Ambiguous scrolling (upstream issues #3795 and #4459)
        assertEquals(0.0f, window.scrollPosition.y);
        ctx.mouseWheel(1.0f, -10.0f);
        ctx.yieldFrame();
        assertTrue(window.scrollPosition.y > 0.0f);

        IkGuiInternal.setScrollX(child, 0);
        IkGuiInternal.setScrollY(window, 0);
        ctx.sleepNoSkip(2.0f, 0.5f);
        assertNull(g.windowWheeling);
        ctx.mouseMove("**/Button 2");
        ctx.mouseWheel(-10.0f, 1.0f);
        ctx.yieldFrame();
        assertTrue(child.scrollPosition.x > 0.0f);

        // Inertia that has no visible effect shouldn't keep resetting the lock timer (#3795)
        IkGuiInternal.setScrollX(child, 0);
        IkGuiInternal.setScrollY(window, 0);
        ctx.sleepNoSkip(2.0f, 0.5f);
        ctx.mouseWheel(0, -1.0f);
        ctx.yieldFrame();
        ctx.mouseWheel(0, -0.1f);
        assertSame(window, g.windowWheeling);
        ctx.yieldFrame();
        final long timer0 = g.windowWheelingReleaseTimer;
        ctx.sleepNoSkip(timer0 * 0.9f / 1000.0f, timer0 * 0.9f / 1000.0f);
        for (int n = 0; n < 20; ++n) {
            // Each wheel event raises the timer by 5% of the lock time, and each frame and sleep
            // lowers it by more than that
            ctx.mouseWheel(0, -0.05f);
            ctx.sleepNoSkip(0.05f, 0.05f);
            ctx.yieldFrame();
        }
        final long timer1 = g.windowWheelingReleaseTimer;
        assertTrue(timer1 < timer0);
        assertEquals(0, timer1);
        assertNull(g.windowWheeling);
        assertTrue(window.scrollPosition.y > 0.0f);
        ctx.mouseWheel(-10.0f, 0);
        ctx.yieldFrame();
        assertNotNull(g.windowWheeling);
    }

    /** window_move: moving a window by its title bar. */
    @Test
    void testWindowMove() {
        ctx.setGui(
                () -> {
                    IkGui.setNextWindowSize(0, 0);
                    IkGui.begin("Movable Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    IkGui.textUnformatted("Lorem ipsum dolor sit amet");
                    IkGui.end();
                });
        final Vector2f viewportPos = IkGui.getMainViewport().position;
        final Window window = ctx.getWindowByRef("Movable Window");
        for (float[] offset : new float[][] {{0, 0}, {100, 0}, {50, 100}}) {
            ctx.windowMoveByDrag(
                    "Movable Window", viewportPos.x + offset[0], viewportPos.y + offset[1]);
            assertEquals(new Vector2f(viewportPos).add(offset[0], offset[1]), window.position);
        }
    }

    /** window_pos_pivot: explicit window positions with a pivot. */
    @Test
    void testWindowPosPivot() {
        final Vector2f pos = new Vector2f();
        final Vector2f pivot = new Vector2f();
        ctx.setGui(
                () -> {
                    IkGui.setNextWindowSize(0, 0);
                    IkGui.setNextWindowPos(pos.x, pos.y, Condition.ALWAYS, pivot.x, pivot.y);
                    IkGui.begin("Movable Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    IkGui.textUnformatted("Lorem ipsum dolor sit amet");
                    IkGui.end();
                });
        final Window window = ctx.getWindowByRef("Movable Window");
        // All the pivot combinations, collapsed and not
        for (int n = 0; n < 4; ++n) {
            for (int c = 0; c < 2; ++c) {
                // Make sure the window is in the visible viewport
                pos.set(IkGui.getMainViewport().position).add(window.size).add(1, 1);
                pivot.set((n & 1) != 0 ? 1 : 0, (n & 2) != 0 ? 1 : 0);
                ctx.yieldFrames(2);
                ctx.windowCollapse("//Movable Window", c != 0);
                ctx.yieldFrame();
                assertEquals(
                        new Vector2f(pos).sub(new Vector2f(window.size).mul(pivot)),
                        window.position,
                        "pivot " + pivot + " collapsed " + c);
            }
        }
    }

    /** window_resizing: resizing a window from its edges and corners. */
    @Test
    void testWindowResizing() {
        ctx.setGui(
                () -> {
                    ctx.context.style.variable.windowMinSize.set(10, 10);
                    IkGui.begin(
                            "Test Window",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS | WindowFlags.NO_COLLAPSE);
                    IkGui.textUnformatted("Lorem ipsum dolor sit amet");
                    IkGui.end();
                });
        // {grab x, grab y, drag x, drag y, expected position x, y, expected size x, y}, where the
        // grab position is relative to the window size
        final float[][] testData = {
            // From the top edge, go up
            {0.5f, 0, 0, -10, 100, 90, 200, 60},
            // From the top right corner, no resize, so the window moves
            {1, 0, 10, -10, 110, 90, 200, 50},
            // From the right edge, go right
            {1, 0.5f, 10, 0, 100, 100, 210, 50},
            // From the bottom right corner, go right and down
            {1, 1, 10, 10, 100, 100, 210, 60},
            // From the bottom edge, go down
            {0.5f, 1, 0, 10, 100, 100, 200, 60},
            // From the bottom left corner, go left and down
            {0, 1, -10, 10, 90, 100, 210, 60},
            // From the left edge, go left
            {0, 0.5f, -10, 0, 90, 100, 210, 50},
            // From the top left corner, no resize, so the window moves
            {0, 0, -10, -10, 90, 90, 200, 50},
        };
        ctx.dockClear("Test Window");
        final Window window = ctx.getWindowByRef("Test Window");
        for (float[] data : testData) {
            final String d = "grab " + data[0] + "," + data[1];
            IkGuiImplWindows.setWindowPos(window, 100, 100, Condition.ALWAYS);
            IkGuiImplWindows.setWindowSize(window, 200, 50, Condition.ALWAYS);
            ctx.yieldFrame();
            ctx.mouseMoveToPos(
                    window.position.x + (window.size.x - 1.0f) * data[0],
                    window.position.y + (window.size.y - 1.0f) * data[1]);
            ctx.mouseDragWithDelta(data[2], data[3]);
            assertEquals(new Vector2f(data[6], data[7]), window.size, d);
            assertEquals(new Vector2f(data[4], data[5]), window.position, d);
        }
    }

    /** window_title_animation: an animated window title, updated in the window switcher. */
    @Test
    void testWindowTitleAnimation() {
        ctx.setGui(
                () -> {
                    IkGui.begin(
                            "Frame " + IkGui.getFrameCount() + "###Test Window",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS | WindowFlags.UNSAVED_DOCUMENT);
                    IkGui.textUnformatted("Lorem ipsum dolor sit amet");
                    IkGui.end();
                });
        final var g = ctx.context;
        final Window window = ctx.getWindowByRef("###Test Window");
        assertNotNull(window);

        // Open the window switcher, holding Ctrl down
        final int mods = KeyChord.getMods(g.configNavWindowingKeyNext);
        ctx.keyDown(KeyChord.ofMods(mods));
        ctx.keyPress(KeyChord.getKey(g.configNavWindowingKeyNext));
        ctx.sleepNoSkip(0.3f, 1.0f / 60.0f);
        for (int i = 0; i < 2; ++i) {
            // The window name gets updated
            assertEquals("Frame " + g.frameCount + "###Test Window", window.name);
            ctx.yieldFrame();
        }
        ctx.keyUp(KeyChord.ofMods(mods));
    }

    /** window_appearing: the appearing state of windows, tooltips and popups. */
    @Test
    void testWindowAppearing() {
        // 0 is hidden, then a window, a window with a set size, an auto-resizing window, a
        // tooltip and a popup
        final int[] state = {0};
        final int[] showFrame = {-1};
        final int[] appearingFrame = {-1};
        final int[] appearingCount = {0};
        ctx.setGui(
                () -> {
                    if (state[0] == 0) {
                        return;
                    }
                    switch (state[0]) {
                        case 1 ->
                                IkGui.begin(
                                        "Appearing Window 1", null, WindowFlags.NO_SAVED_SETTINGS);
                        case 2 -> {
                            IkGui.setNextWindowSize(100, 100);
                            IkGui.begin("Appearing Window 2", null, WindowFlags.NO_SAVED_SETTINGS);
                        }
                        case 3 ->
                                IkGui.begin(
                                        "Appearing Window 3",
                                        null,
                                        WindowFlags.NO_SAVED_SETTINGS
                                                | WindowFlags.ALWAYS_AUTO_RESIZE);
                        case 4 -> IkGui.beginTooltip();
                        default -> {
                            if (showFrame[0] == -1) {
                                IkGui.openPopup("##popup");
                            }
                            IkGui.beginPopup("##popup");
                        }
                    }
                    IkGui.text("Some text");
                    if (showFrame[0] == -1) {
                        showFrame[0] = IkGui.getFrameCount();
                    }
                    if (IkGui.isWindowAppearing()) {
                        appearingCount[0]++;
                        appearingFrame[0] = IkGui.getFrameCount();
                    }
                    switch (state[0]) {
                        case 1, 2, 3 -> IkGui.end();
                        case 4 -> IkGui.endTooltip();
                        default -> IkGui.endPopup();
                    }
                });
        final String[] names = {
            null, "Window", "WindowSetSize", "WindowAutoSize", "Tooltip", "Popup"
        };
        for (int variant = 1; variant <= 5; ++variant) {
            final String d = names[variant];
            state[0] = variant;
            showFrame[0] = -1;
            appearingFrame[0] = -1;
            appearingCount[0] = 0;
            // isWindowAppearing() only triggers on the very first frame
            ctx.yieldFrame();
            assertEquals(1, appearingCount[0], d);
            assertNotEquals(-1, showFrame[0], d);
            assertEquals(showFrame[0], appearingFrame[0], d);
            ctx.yieldFrames(5);
            assertEquals(1, appearingCount[0], d);
            assertNotEquals(-1, showFrame[0], d);
            assertEquals(showFrame[0], appearingFrame[0], d);
            state[0] = 0;
            ctx.yieldFrame();
        }
    }

    /** window_title_context_menu: context menus on a window title bar and docked window tab. */
    @Test
    void testWindowTitleContextMenu() {
        final IkBoolean openA = new IkBoolean(true);
        ctx.setGui(
                () -> {
                    IkGui.setNextWindowSize(300, 200, Condition.APPEARING);
                    IkGui.begin("Window A", openA, WindowFlags.NO_SAVED_SETTINGS);
                    if (IkGui.beginPopupContextItem("Popup A")) {
                        IkGui.text("Context menu");
                        IkGui.endPopup();
                    }
                    IkGui.textUnformatted("Window A");
                    IkGui.end();
                    IkGui.setNextWindowSize(300, 200, Condition.APPEARING);
                    IkGui.begin("Window B", null, WindowFlags.NO_SAVED_SETTINGS);
                    IkGui.textUnformatted("Window B");
                    IkGui.end();
                });
        final var g = ctx.context;
        final int popupWindowID = ctx.popupGetWindowID("//Window A/Popup A");
        final Window window = ctx.getWindowByRef("Window A");
        ctx.setRef("Window A");
        ctx.dockClear("//Window A", "//Window B");
        ctx.windowFocus("//Window A");

        // The title bar of an undocked window
        Vector2f point = IkGuiTestContext.windowTitleBarPoint(window);
        ctx.mouseMoveToPos(point.x, point.y);
        ctx.mouseClick(MouseButton.RIGHT);
        assertNotNull(g.navFocusedWindow);
        assertEquals(popupWindowID, g.navFocusedWindow.id);
        ctx.popupCloseAll();

        // The close button of an undocked window
        ctx.itemClick(ctx.getID("#CLOSE"), MouseButton.RIGHT);
        assertNotNull(g.navFocusedWindow);
        assertEquals(popupWindowID, g.navFocusedWindow.id);

        // The collapse button of an undocked window
        ctx.itemClick(ctx.getID("#COLLAPSE"), MouseButton.RIGHT);
        assertNotNull(g.navFocusedWindow);
        assertEquals(popupWindowID, g.navFocusedWindow.id);

        // The context menu with Shift+F10
        ctx.navMoveTo(ctx.getID("#CLOSE"));
        ctx.keyPress(KeyChord.of(KeyModFlags.SHIFT, Key.F10));
        assertNotNull(g.navFocusedWindow);
        assertEquals(popupWindowID, g.navFocusedWindow.id);

        ctx.navMoveTo(ctx.getID("#COLLAPSE"));
        ctx.keyPress(KeyChord.of(KeyModFlags.SHIFT, Key.F10));
        assertNotNull(g.navFocusedWindow);
        assertEquals(popupWindowID, g.navFocusedWindow.id);

        ctx.dockInto("//Window B", "//Window A");

        // The tab of a docked window
        point = IkGuiTestContext.windowTitleBarPoint(window);
        ctx.mouseMoveToPos(point.x, point.y);
        ctx.mouseClick(MouseButton.RIGHT);
        assertNotNull(g.navFocusedWindow);
        assertEquals(popupWindowID, g.navFocusedWindow.id);
        ctx.popupCloseAll();

        // The close button of a docked window's tab
        ctx.itemClick(ctx.getID("#CLOSE"), MouseButton.RIGHT);
        assertNotNull(g.navFocusedWindow);
        assertEquals(popupWindowID, g.navFocusedWindow.id);
        ctx.popupCloseAll();

        // The close button of the dock node
        assertNotNull(window.dockNode);
        ctx.itemClick(
                com.ikalagaming.graphics.gui.util.Hash.getID("#CLOSE", window.dockNode.id),
                MouseButton.RIGHT);
        assertSame(window, g.navFocusedWindow);
        ctx.popupCloseAll();

        // The collapse button of the dock node
        ctx.itemClick(
                com.ikalagaming.graphics.gui.util.Hash.getID("#COLLAPSE", window.dockNode.id),
                MouseButton.RIGHT);
        assertSame(window, g.navFocusedWindow);
    }

    /** window_appearing_focus: focusing a window that is appearing. */
    @Test
    void testWindowAppearingFocus() {
        final int[] step = {0};
        ctx.setGui(
                () -> {
                    if (step[0] != 2) {
                        final Vector2f pos = IkGui.getMainViewport().position;
                        // Inside the main viewport
                        IkGui.setNextWindowPos(pos.x + 10, pos.y + 10, Condition.APPEARING);
                        IkGui.begin(
                                "Test Window",
                                null,
                                WindowFlags.NO_SAVED_SETTINGS | WindowFlags.ALWAYS_AUTO_RESIZE);
                        IkGui.end();
                    }
                });
        final var g = ctx.context;
        // Upstream also tests a window outside the main viewport, with multiple viewports
        // 1. Submit the window
        step[0] = 1;
        ctx.yieldFrame();
        // 2. Stop submitting it
        step[0] = 2;
        ctx.yieldFrame();
        // 3. Submit it again
        step[0] = 3;
        ctx.yieldFrame();
        assertSame(ctx.getWindowByRef("Test Window"), g.navFocusedWindow);
    }

    /** window_settings: a few window settings functions. */
    @Test
    void testWindowSettings() {
        // Upstream sets NoGuiWarmUp
        ctx.setGui(
                () -> {
                    final Vector2f pos = IkGui.getMainViewport().position;
                    IkGui.setNextWindowPos(
                            pos.x + 101.0f, pos.y + 101.0f, Condition.FIRST_USE_EVER);
                    IkGui.begin("Test Window Settings", null, WindowFlags.NONE);
                    IkGui.end();
                });
        final Vector2f viewportPos = IkGui.getMainViewport().position;
        IkGuiInternal.clearWindowSettings("Test Window Settings");
        ctx.yieldFrames(2);
        final Window window = ctx.getWindowByRef("//Test Window Settings");
        assertNotNull(window);
        assertEquals(new Vector2f(viewportPos).add(101.0f, 101.0f), window.position);
        ctx.yieldFrames(2);

        // Change the settings and check the saved state
        ctx.setRef("//Test Window Settings");
        ctx.windowMoveByDrag("", window.position.x + 10, window.position.y + 10);
        IkGui.saveIniSettingsToMemory();
        final WindowSettings settings = IkGuiInternal.findWindowSettingsByWindow(window);
        assertNotNull(settings);
        // Relative to the viewport
        assertEquals(101 + 10, settings.position.x);
        assertEquals(101 + 10, settings.position.y);

        // Clearing the settings
        IkGuiInternal.clearWindowSettings("Test Window Settings");
        ctx.yieldFrame();
        assertEquals(new Vector2f(viewportPos).add(101.0f, 101.0f), window.position);

        // No extra garbage is created
        IkGui.saveIniSettingsToMemory();
        final WindowSettings settings2 = IkGuiInternal.findWindowSettingsByWindow(window);
        assertSame(settings, settings2);
    }

    /**
     * layout_baseline_and_cursormax: the text baseline, and the item rect matching the cursor max.
     */
    @Test
    void testLayoutBaselineAndCursorMax() {
        final TextureHandle texture = new TextureHandle(6, 0, 1, 1, Format.R8G8B8A8_UNORM);
        ctx.setGui(
                () -> {
                    final var g = ctx.context;
                    final var style = g.style.variable;
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    final Window window = g.windowCurrent;
                    final String[] typeNames = {
                        "SmallButton",
                        "Button",
                        "Text",
                        "BulletText",
                        "TreeNode",
                        "Selectable",
                        "ImageButton"
                    };
                    for (int type = 0; type < typeNames.length; ++type) {
                        for (int n = 0; n < 5; ++n) {
                            final String d = typeNames[type] + " variant " + n;
                            // A button with a different baseline
                            final float y = window.cursorPosition.y;
                            if (n > 0) {
                                if (n <= 2) {
                                    IkGui.smallButton("Button");
                                } else {
                                    IkGui.button("Button");
                                }
                                IkGui.sameLine();
                            }
                            final int lineCount = (n == 0 || n == 1 || n == 3) ? 1 : 2;
                            final String label =
                                    typeNames[type] + n + (lineCount == 1 ? "" : "\nHello");

                            float expectedPadding = 0.0f;
                            switch (type) {
                                case 0 -> {
                                    expectedPadding = window.baseOffsetCurrentLine;
                                    IkGui.smallButton(label);
                                }
                                case 1 -> {
                                    expectedPadding = style.framePadding.y * 2.0f;
                                    IkGui.button(label);
                                }
                                case 2 -> {
                                    expectedPadding = window.baseOffsetCurrentLine;
                                    IkGui.text(label);
                                }
                                case 3 -> {
                                    expectedPadding = n <= 2 ? 0.0f : style.framePadding.y;
                                    IkGui.bulletText(label);
                                }
                                case 4 -> {
                                    expectedPadding = n <= 2 ? 0.0f : style.framePadding.y * 2.0f;
                                    IkGui.treeNodeEx(label, TreeNodeFlags.NO_TREE_PUSH_ON_OPEN);
                                }
                                case 5 -> {
                                    // Upstream notes it may want to test the selectable's
                                    // specifics instead of clearing the item spacing
                                    expectedPadding = window.baseOffsetCurrentLine;
                                    IkGui.pushStyleVarFloat2(StyleVariable.ITEM_SPACING, 0, 0);
                                    IkGui.selectable(label);
                                    IkGui.popStyleVar();
                                    IkGui.spacing();
                                }
                                default -> {
                                    expectedPadding = style.framePadding.y * 2.0f;
                                    IkGui.imageButton(
                                            "tex",
                                            texture,
                                            100,
                                            IkGui.getTextLineHeight() * lineCount,
                                            0,
                                            0,
                                            1,
                                            1,
                                            IkGui.colorConvertFloat4ToU32(1.0f, 0.6f, 0.0f, 1.0f),
                                            0xFFFFFFFF);
                                }
                            }
                            if (lineCount > 1) {
                                ctx.checkEquals(
                                        g.lastItemData.rect.getBottom(),
                                        window.cursorMaxPosition.y,
                                        d + " cursor max");
                            }
                            final float currentHeight = g.lastItemData.rect.getBottom() - y;
                            final float expectedHeight =
                                    IkGuiInternal.getFontSize() * lineCount + expectedPadding;
                            ctx.checkEquals(expectedHeight, currentHeight, d + " height");
                        }
                        IkGui.spacing();
                    }
                    IkGui.end();
                });
        ctx.yieldFrames(2);
    }

    /** layout_cursor_max: the cursor max position with setCursorPos() and newLine(). */
    @Test
    void testLayoutCursorMax() {
        ctx.setGui(
                () -> {
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    final Window window = IkGuiInternal.context.windowCurrent;
                    ctx.checkEquals(
                            window.cursorStartPosition.x, window.cursorMaxPosition.x, "start x");
                    ctx.checkEquals(
                            window.cursorStartPosition.y, window.cursorMaxPosition.y, "start y");

                    IkGui.setCursorPos(200.0f, 100.0f);
                    ctx.checkEquals(
                            window.cursorStartPosition.x, window.cursorMaxPosition.x, "set x");
                    ctx.checkEquals(
                            window.cursorStartPosition.y, window.cursorMaxPosition.y, "set y");

                    IkGui.newLine();
                    ctx.checkEquals(
                            window.position.x + 200.0f, window.cursorMaxPosition.x, "new line x");
                    ctx.checkEquals(
                            window.position.y + 100.0f + IkGui.getTextLineHeight(),
                            window.cursorMaxPosition.y,
                            "new line y");
                    IkGui.end();
                });
        ctx.yieldFrames(2);
    }

    /** layout_menu_extents: contents in the menu layer and the content size. */
    @Test
    void testLayoutMenuExtents() {
        final boolean[] mainOutButton = {false};
        final Vector2f menuAvailSize = new Vector2f();
        ctx.setGui(
                () -> {
                    IkGui.begin(
                            "Test Window",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS
                                    | WindowFlags.MENU_BAR
                                    | WindowFlags.HORIZONTAL_SCROLLBAR);
                    if (IkGui.beginMenuBar()) {
                        menuAvailSize.set(IkGui.getContentRegionAvailable());
                        IkGui.button("Menu Button", 400.0f, 0.0f);
                        IkGui.endMenuBar();
                    }
                    IkGui.text("Hi");
                    if (mainOutButton[0]) {
                        IkGui.button("Main button", 500.0f, 500.0f);
                    }
                    IkGui.end();
                });
        final var style = ctx.context.style.variable;
        ctx.setRef("Test Window");
        final Window window = ctx.getWindowByRef("");
        ctx.scrollToX("", 0.0f);
        // Upstream auto-fits by resizing to (-1, -1)
        final Runnable autoFit =
                () -> ctx.itemDoubleClick(IkGuiImplWindows.getWindowResizeID(window, 0));

        // Auto-resizing uses the ideal content size, which is like the content size but doesn't
        // affect the scrolling range
        mainOutButton[0] = false;
        ctx.windowResize("", 100.0f, 100.0f);
        // Auto-resize up
        autoFit.run();
        assertEquals(400.0f + style.windowPadding.x * 2.0f, window.size.x);

        ctx.windowResize("", 600.0f, 100.0f);
        // Auto-resize down
        autoFit.run();
        assertEquals(400.0f + style.windowPadding.x * 2.0f, window.size.x);
        assertEquals(400.0f, menuAvailSize.x);

        mainOutButton[0] = true;
        ctx.yieldFrames(2);
        assertEquals(500.0f, window.contentSize.x);
        assertTrue(window.contentSize.y >= 500.0f);
        assertTrue(window.scrollbarX && window.scrollbarY);

        // The absolute position of the menu contents doesn't interfere with the main layer's
        // scrolling and content size
        final float scrollMaxX = window.scrollMax.x;
        ctx.scrollToX("", window.scrollMax.x);
        assertEquals(500.0f, window.contentSize.x);
        assertEquals(scrollMaxX, window.scrollMax.x);

        // Auto-resize up
        autoFit.run();
        assertEquals(500.0f + style.windowPadding.x * 2.0f, window.size.x);
        ctx.yieldFrames(3);
    }

    /** layout_sameline_cursorpos: sameLine() mixed with setCursorPos(). */
    @Test
    void testLayoutSameLineCursorPos() {
        final float[] red = {1, 0, 0, 1};
        final float[] orange = {1, 0.5f, 0, 1};
        final float[] yellow = {1, 1, 0, 1};
        ctx.setGui(
                () -> {
                    final var style = ctx.context.style.variable;
                    {
                        IkGui.begin("Test Window 0", null, WindowFlags.NO_SAVED_SETTINGS);
                        final Window window = IkGuiInternal.context.windowCurrent;
                        IkGui.text("First line");
                        float y1 = window.cursorPosition.y;
                        IkGui.colorButton("blah", red, 0, 100, 100);
                        IkGui.sameLine();
                        IkGui.setCursorPosY(IkGui.getCursorPosY() + 40.0f);
                        IkGui.text("Hello");
                        float y2 = window.cursorPosition.y;
                        ctx.checkEquals(100.0f + style.itemSpacing.y, y2 - y1, "window 0 a");
                        IkGui.text("Again");

                        IkGui.separator();

                        IkGui.text("First line");
                        y1 = window.cursorPosition.y;
                        IkGui.colorButton("blah2", red, 0, 20, 20);
                        IkGui.sameLine();
                        IkGui.setCursorPosY(IkGui.getCursorPosY() + 10.0f);
                        IkGui.colorButton("blah2", red, 0, 100, 100);
                        y2 = window.cursorPosition.y;
                        ctx.checkEquals(110.0f + style.itemSpacing.y, y2 - y1, "window 0 b");
                        IkGui.text("Again");
                        IkGui.end();
                    }
                    {
                        IkGui.begin("Test Window 1", null, WindowFlags.NO_SAVED_SETTINGS);
                        final Window window = IkGuiInternal.context.windowCurrent;
                        IkGui.text("First line 1");
                        final float y1 = window.cursorPosition.y;
                        IkGui.colorButton("blah", red, 0, 100, 100);
                        IkGui.sameLine();
                        IkGui.setCursorPosY(IkGui.getCursorPosY() + 20.0f);
                        IkGui.colorButton("blah2", orange, 0, 36, 16);
                        ctx.checkEquals(y1 + 20.0f, IkGui.getItemRectMin().y, "window 1 a");
                        IkGui.sameLine();
                        IkGui.colorButton("blah3", yellow, 0, 16, 16);
                        ctx.checkEquals(y1, IkGui.getItemRectMin().y, "window 1 b");
                        IkGui.separator();
                        ctx.checkEquals(
                                y1 + 100.0f + style.itemSpacing.y,
                                IkGui.getItemRectMin().y,
                                "window 1 c");
                        IkGui.text("Another line 1");
                        IkGui.end();
                    }
                    {
                        IkGui.begin("Test Window 2", null, WindowFlags.NO_SAVED_SETTINGS);
                        final Window window = IkGuiInternal.context.windowCurrent;
                        IkGui.text("First line 2");
                        final float y1 = window.cursorPosition.y;
                        IkGui.colorButton("blah", red, 0, 100, 100);
                        IkGui.sameLine();
                        IkGui.setCursorPosY(IkGui.getCursorPosY() - 20.0f);
                        IkGui.colorButton("blah2", orange, 0, 36, 16);
                        ctx.checkEquals(y1 - 20.0f, IkGui.getItemRectMin().y, "window 2 a");
                        IkGui.sameLine();
                        IkGui.colorButton("blah3", yellow, 0, 16, 16);
                        ctx.checkEquals(y1, IkGui.getItemRectMin().y, "window 2 b");
                        IkGui.separator();
                        ctx.checkEquals(
                                y1 + 100.0f + style.itemSpacing.y,
                                IkGui.getItemRectMin().y,
                                "window 2 c");
                        IkGui.text("Another line 2");
                        IkGui.end();
                    }
                    {
                        IkGui.begin("Test Window 3", null, WindowFlags.NO_SAVED_SETTINGS);
                        final Window window = IkGuiInternal.context.windowCurrent;
                        IkGui.text("First line 3");
                        final float y1 = window.cursorPosition.y;
                        IkGui.colorButton("blah", red, 0, 100, 100);
                        IkGui.setCursorScreenPos(
                                IkGui.getItemRectMax().x + style.itemSpacing.x,
                                IkGui.getItemRectMin().y + 20.0f);
                        IkGui.colorButton("blah2", orange, 0, 36, 16);
                        ctx.checkEquals(y1 + 20.0f, IkGui.getItemRectMin().y, "window 3 a");
                        IkGui.sameLine();
                        IkGui.colorButton("blah3", yellow, 0, 16, 16);
                        ctx.checkEquals(y1 + 20.0f, IkGui.getItemRectMin().y, "window 3 b");
                        IkGui.separator();
                        ctx.checkEquals(
                                y1 + 20.0f + 16.0f + style.itemSpacing.y,
                                IkGui.getItemRectMin().y,
                                "window 3 c");
                        IkGui.text("Another line 3");
                        IkGui.end();
                    }
                    {
                        IkGui.begin("Test Window 4", null, WindowFlags.NO_SAVED_SETTINGS);
                        final Window window = IkGuiInternal.context.windowCurrent;
                        IkGui.text("First line 4");
                        final float y1 = window.cursorPosition.y;
                        IkGui.colorButton("blah", red, 0, 100, 100);
                        IkGui.setCursorScreenPos(
                                IkGui.getItemRectMax().x + style.itemSpacing.x,
                                IkGui.getItemRectMin().y - 20.0f);
                        IkGui.colorButton("blah2", orange, 0, 36, 16);
                        ctx.checkEquals(y1 - 20.0f, IkGui.getItemRectMin().y, "window 4 a");
                        IkGui.sameLine();
                        IkGui.colorButton("blah3", yellow, 0, 16, 16);
                        ctx.checkEquals(y1 - 20.0f, IkGui.getItemRectMin().y, "window 4 b");
                        IkGui.separator();
                        ctx.checkEquals(
                                y1 - 20.0f + 16.0f + style.itemSpacing.y,
                                IkGui.getItemRectMin().y,
                                "window 4 c");
                        IkGui.text("Another line 4");
                        IkGui.end();
                    }
                    {
                        IkGui.begin("Test Window 5", null, WindowFlags.NO_SAVED_SETTINGS);
                        final Window window = IkGuiInternal.context.windowCurrent;
                        final float y1 = window.cursorPosition.y;
                        IkGui.colorButton("blah", red, 0, 100, 100);
                        IkGui.sameLine();
                        IkGui.setCursorPosY(IkGui.getCursorPosY() + 50.0f);
                        IkGui.beginGroup();
                        ctx.checkEquals(y1 + 50.0f, IkGui.getCursorScreenPos().y, "window 5 a");
                        IkGui.text("Text in Group");
                        IkGui.endGroup();
                        final float y2 = window.cursorPosition.y;
                        IkGui.text("Another line 5");
                        ctx.checkEquals(y1 + 100.0f + style.itemSpacing.y, y2, "window 5 b");
                        IkGui.end();
                    }
                });
        ctx.yieldFrames(2);
    }

    /** layout_align_to_frame_padding: alignTextToFramePadding(). */
    @Test
    void testLayoutAlignToFramePadding() {
        ctx.setGui(
                () -> {
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    final Window window = IkGuiInternal.context.windowCurrent;

                    float y1 = window.cursorPosition.y;
                    IkGui.text("Hello 1");
                    float y2 = window.cursorPosition.y;
                    ctx.checkEquals(IkGui.getTextLineHeightWithSpacing(), y2 - y1, "plain");

                    y1 = window.cursorPosition.y;
                    IkGui.alignTextToFramePadding();
                    IkGui.text("Hello 2");
                    y2 = window.cursorPosition.y;
                    ctx.checkEquals(IkGui.getFrameHeightWithSpacing(), y2 - y1, "aligned");

                    y1 = window.cursorPosition.y;
                    IkGui.alignTextToFramePadding();
                    IkGui.alignTextToFramePadding();
                    IkGui.alignTextToFramePadding();
                    IkGui.text("Hello 3");
                    y2 = window.cursorPosition.y;
                    ctx.checkEquals(IkGui.getFrameHeightWithSpacing(), y2 - y1, "aligned 3 times");

                    IkGui.text("Hello 4");
                    IkGui.end();
                });
        ctx.yieldFrames(2);
    }

    /** layout_newline: newLine(). */
    @Test
    void testLayoutNewLine() {
        ctx.setGui(
                () -> {
                    final var style = ctx.context.style.variable;
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    final Window window = IkGuiInternal.context.windowCurrent;

                    IkGui.text("1");
                    float y1 = window.cursorPosition.y;
                    // A new line on an empty line uses the font size
                    IkGui.newLine();
                    float y2 = window.cursorPosition.y;
                    ctx.checkEquals(IkGui.getTextLineHeightWithSpacing(), y2 - y1, "empty line");

                    y1 = window.cursorPosition.y;
                    IkGui.colorButton("blah", new float[] {1, 0, 0, 1}, 0, 100, 100);
                    IkGui.sameLine();
                    IkGui.newLine();
                    y2 = window.cursorPosition.y;
                    ctx.checkEquals(100.0f + style.itemSpacing.y, y2 - y1, "tall item");
                    IkGui.text("3");

                    y1 = window.cursorPosition.y;
                    IkGui.colorButton("blah", new float[] {0, 1, 0, 1}, 0, 5, 5);
                    IkGui.sameLine();
                    IkGui.newLine();
                    y2 = window.cursorPosition.y;
                    ctx.checkEquals(5.0f + style.itemSpacing.y, y2 - y1, "short item");
                    IkGui.text("4");
                    IkGui.end();
                });
        ctx.yieldFrames(2);
    }

    /** layout_group_status_query: endGroup() reports the merged item status. */
    @Test
    void testLayoutGroupStatusQuery() {
        final int[] sliderValue = {0};
        final IkBoolean checkbox = new IkBoolean(false);
        final IkGuiTestContext.ItemStatus status = new IkGuiTestContext.ItemStatus();
        ctx.setGui(
                () -> {
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    IkGui.beginGroup();
                    IkGui.button("Button");
                    IkGui.sliderInt("SliderInt", sliderValue, 0, 100);
                    IkGui.checkbox("Checkbox", checkbox);
                    IkGui.selectable("Selectable");
                    IkGui.endGroup();
                    status.queryInc(false);
                    IkGui.end();
                });
        ctx.setRef("Test Window");

        ctx.itemClick("Button");
        assertTrue(status.active >= 1);
        assertEquals(1, status.activated);
        assertEquals(1, status.deactivated);
        assertEquals(1, status.clicked);
        status.clear();

        // A single character is a single edit
        ctx.itemInputValue("SliderInt", 9);
        assertTrue(status.active >= 1);
        assertEquals(1, status.activated);
        assertEquals(1, status.deactivated);
        assertEquals(1, status.edited);
        status.clear();

        ctx.itemClick("Checkbox");
        assertTrue(status.active >= 1);
        assertEquals(1, status.activated);
        assertEquals(1, status.deactivated);
        assertEquals(1, status.edited);
        status.clear();

        ctx.itemClick("Selectable");
        assertTrue(status.active >= 1);
        assertEquals(1, status.activated);
        assertEquals(1, status.deactivated);
        assertEquals(1, status.edited);
        status.clear();
    }

    /** layout_group_endtable: endGroup() compensates for tables undershooting the cursor max. */
    @Test
    void testLayoutGroupEndTable() {
        ctx.setGui(
                () -> {
                    final var style = ctx.context.style.variable;
                    for (int step = 0; step < 2; ++step) {
                        IkGui.setNextWindowSize(500, 500);
                        IkGui.begin("Test Window " + step, null, WindowFlags.NO_SAVED_SETTINGS);
                        final Vector2f avail = IkGui.getContentRegionAvailable();
                        IkGui.beginGroup();

                        // The vertical behavior is rightly different between < 0 and == 0 for a
                        // table that doesn't scroll
                        final Vector2f outerSize =
                                step == 0 ? new Vector2f(-100, -100) : new Vector2f(0.0f, 0.0f);
                        final Vector2f expected =
                                step == 0
                                        ? new Vector2f(avail.x - 100.0f, avail.y - 100.0f)
                                        : new Vector2f(
                                                avail.x,
                                                IkGui.getTextLineHeight()
                                                        + style.cellPadding.y * 2.0f);
                        if (IkGui.beginTable(
                                "table", 2, TableFlags.BORDERS, outerSize.x, outerSize.y)) {
                            IkGui.tableNextColumn();
                            IkGui.text("Hello");
                            IkGui.tableNextColumn();
                            IkGui.text("World");
                            IkGui.endTable();
                            ctx.checkEquals(
                                    expected.x, IkGui.getItemRectSize().x, "table width " + step);
                            ctx.checkEquals(
                                    expected.y, IkGui.getItemRectSize().y, "table height " + step);
                        }
                        IkGui.endGroup();
                        ctx.checkEquals(
                                expected.x, IkGui.getItemRectSize().x, "group width " + step);
                        ctx.checkEquals(
                                expected.y, IkGui.getItemRectSize().y, "group height " + step);
                        IkGui.end();
                    }
                });
        ctx.yieldFrames(2);
    }

    /** Pop everything that can be pushed, with nothing pushed, for the error recovery tests. */
    private static void popEverything() {
        IkGui.popID();
        IkGuiImplNav.popFocusScope();
        IkGui.popFont();
        IkGui.popItemFlag();
        IkGui.popItemWidth();
        IkGui.popStyleColor();
        IkGui.popStyleVar();
        IkGui.popTextWrapPos();
    }

    /** misc_recover_1: recovering from popping without pushing, and a missing end(). */
    @Test
    void testMiscRecover1() {
        ctx.expectErrors();
        ctx.setGui(
                () -> {
                    IkGui.begin("Test window", null, WindowFlags.NO_SAVED_SETTINGS);
                    popEverything();
                });
        ctx.yieldFrames(2);
    }

    /** misc_recover_1_nested: misc_recover_1 in a nested window. */
    @Test
    void testMiscRecover1Nested() {
        ctx.expectErrors();
        ctx.setGui(
                () -> {
                    IkGui.begin("Parent Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    IkGui.begin("Test window", null, WindowFlags.NO_SAVED_SETTINGS);
                    popEverything();
                });
        ctx.yieldFrames(2);
    }

    /** misc_recover_2: recovering from end() without begin(). */
    @Test
    void testMiscRecover2() {
        ctx.expectErrors();
        ctx.setGui(IkGui::end);
        ctx.yieldFrames(2);
    }

    /** misc_recover_3: recovering from many unclosed scopes. */
    @Test
    void testMiscRecover3() {
        ctx.expectErrors();
        ctx.setGui(
                () -> {
                    IkGui.beginDisabled();
                    IkGui.begin("Test window", null, WindowFlags.NO_SAVED_SETTINGS);
                    IkGui.beginChild("Child");
                    IkGuiImplNav.pushFocusScope(IkGui.getID("focusscope1"));
                    IkGui.pushStyleVarFloat2(StyleVariable.ITEM_SPACING, 0, 0);
                    IkGui.pushStyleColor(ColorType.TEXT, 0.0f, 0.0f, 0.0f, 0.0f);
                    IkGui.pushID("hello");
                    IkGui.pushItemFlag(ItemFlags.BUTTON_REPEAT, true);
                    // Upstream pushes the current font with the current size
                    IkGui.pushFontSize(IkGui.getFontSize());
                    IkGui.beginGroup();
                    IkGui.beginMultiSelect(MultiSelectFlags.NONE);
                    IkGui.beginDisabled();
                    IkGui.setNextItemOpen(true);
                    IkGui.treeNode("node");
                    IkGui.beginTabBar("tabbar");
                    IkGui.beginChild("child", 200, 200);
                    IkGui.beginTable("table", 4);
                });
        // Make sure two frames run
        ctx.yieldFrames(2);
    }

    /** misc_recover_4_table: recovering from an unclosed table that scrolls, as a child window. */
    @Test
    void testMiscRecover4Table() {
        ctx.expectErrors();
        ctx.setGui(
                () -> {
                    IkGui.begin("Test window", null, WindowFlags.NO_SAVED_SETTINGS);
                    IkGui.beginTable("table", 4, TableFlags.SCROLL_Y);
                });
        ctx.yieldFrames(2);
    }

    /** misc_recover_5_midway: storing and recovering the state manually. */
    @Test
    void testMiscRecover5Midway() {
        ctx.expectErrors();
        ctx.setGui(
                () -> {
                    final var g = IkGuiInternal.context;
                    IkGui.begin("Test window", null, WindowFlags.NO_SAVED_SETTINGS);
                    IkGui.pushID("Hello");
                    IkGui.beginChild("Child");
                    IkGui.pushID("Hello2");
                    IkGui.pushStyleColor(ColorType.TEXT, 0.0f, 0.0f, 0.0f, 0.0f);

                    final ErrorRecoveryState state = new ErrorRecoveryState();
                    IkGuiInternal.errorRecoveryStoreState(state);
                    ctx.checkEquals(3, g.windowStack.size(), "window stack");
                    // The ID stack's type isn't visible to tests, so its size comes from the
                    // recovery state
                    ctx.checkEquals((short) 2, state.sizeOfIDStack, "ID stack");
                    final int colorStackSize = g.colorStack.size();

                    IkGui.pushID("World2");
                    IkGui.beginChild("Child2");
                    IkGui.pushStyleColor(ColorType.TEXT, 1.0f, 0.0f, 1.0f, 1.0f);
                    IkGuiInternal.errorRecoveryTryToRecoverState(state);

                    ctx.checkEquals(3, g.windowStack.size(), "recovered window stack");
                    final ErrorRecoveryState recovered = new ErrorRecoveryState();
                    IkGuiInternal.errorRecoveryStoreState(recovered);
                    ctx.checkEquals((short) 2, recovered.sizeOfIDStack, "recovered ID stack");
                    ctx.checkEquals(colorStackSize, g.colorStack.size(), "recovered color stack");
                });
        ctx.yieldFrames(2);
    }

    /** The state of the clipper tests. */
    private static class ClipperTestVars {
        Window windowOut;
        float windowHeightInItems = 10.0f;
        int itemsIn = 100;
        int itemsOut = 0;
        int forceDisplayStart = 0;
        int forceDisplayEnd = 0;
        float offsetY1 = 0.0f;
        float offsetY2 = 0.0f;
        final BitSet itemsOutMask = new BitSet();
        boolean clipperManualItemHeight = true;
        boolean clipperUnknownItemsCount = false;
        boolean tableEnable = false;
        int tableFreezeRows = 0;
    }

    /** The UI shared by the clipper tests. */
    private static Runnable clipperGui(ClipperTestVars vars) {
        return () -> {
            final var style = IkGuiInternal.context.style.variable;
            final float expectedItemHeight =
                    IkGui.getTextLineHeight()
                            + (vars.tableEnable ? style.cellPadding.y * 2.0f : style.itemSpacing.y);

            // Simpler without padding and decorations
            IkGui.pushStyleVarFloat2(StyleVariable.WINDOW_PADDING, 0, 0);
            IkGui.setNextWindowSize(300, expectedItemHeight * vars.windowHeightInItems);
            IkGui.begin(
                    "Test Window", null, WindowFlags.NO_TITLE_BAR | WindowFlags.NO_SAVED_SETTINGS);

            boolean open = true;
            if (vars.tableEnable) {
                open = IkGui.beginTable("table", 2, TableFlags.SCROLL_Y);
                if (open) {
                    IkGui.tableSetupScrollFreeze(0, vars.tableFreezeRows);
                }
            }
            if (open) {
                vars.windowOut = IkGuiInternal.context.windowCurrent;
                vars.itemsOut = 0;
                vars.itemsOutMask.clear();

                final float startY = IkGui.getCursorScreenPos().y;
                vars.offsetY1 = IkGui.getCursorScreenPos().y - startY;

                final int itemsCount =
                        vars.clipperUnknownItemsCount ? Integer.MAX_VALUE : vars.itemsIn;
                final ListClipper clipper = new ListClipper();
                if (vars.clipperManualItemHeight) {
                    clipper.begin(itemsCount, expectedItemHeight);
                } else {
                    clipper.begin(itemsCount);
                }
                if (vars.forceDisplayStart != vars.forceDisplayEnd) {
                    clipper.includeItemsByIndex(vars.forceDisplayStart, vars.forceDisplayEnd);
                }
                while (clipper.step()) {
                    for (int n = clipper.displayStart; n < clipper.displayEnd; ++n) {
                        if (vars.tableEnable) {
                            IkGui.tableNextRow();
                            IkGui.tableNextColumn();
                        }
                        IkGui.text(String.format("Item %04d", n));
                        vars.itemsOut++;
                        vars.itemsOutMask.set(n);
                    }
                }
                vars.offsetY2 = IkGui.getCursorScreenPos().y - startY;

                // An extra call
                clipper.end();
                if (vars.clipperUnknownItemsCount) {
                    clipper.seekCursorForItem(vars.itemsIn);
                }
                if (vars.tableEnable) {
                    IkGui.endTable();
                }
            }
            IkGui.end();
            IkGui.popStyleVar();
        };
    }

    /** misc_clipper: basic list clipper behavior, including tables with frozen rows. */
    @Test
    void testMiscClipper() {
        final ClipperTestVars vars = new ClipperTestVars();
        ctx.setGui(clipperGui(vars));
        final var style = ctx.context.style.variable;
        final int contentsStepCount = 5;
        for (int clipperStep = 0; clipperStep < 3; ++clipperStep) {
            for (int contentsStep = 0; contentsStep < contentsStepCount; ++contentsStep) {
                final String d = "step " + clipperStep + "-" + contentsStep;
                vars.clipperManualItemHeight = clipperStep == 1;
                vars.clipperUnknownItemsCount = clipperStep == 3;
                vars.tableEnable = contentsStep > 1;
                vars.tableFreezeRows = contentsStep == 3 ? 1 : contentsStep == 4 ? 2 : 0;
                vars.forceDisplayStart = 0;
                vars.forceDisplayEnd = 0;
                vars.itemsIn = 100;
                int extraForcedItems = 0;
                if (contentsStep == 1) {
                    extraForcedItems = 2;
                    vars.forceDisplayStart = 15;
                    vars.forceDisplayEnd = 15 + extraForcedItems;
                }
                final float itemHeight =
                        IkGui.getTextLineHeight()
                                + (vars.tableEnable
                                        ? style.cellPadding.y * 2.0f
                                        : style.itemSpacing.y);

                ctx.yieldFrame();
                ctx.yieldFrame();
                ctx.setRef(vars.windowOut);
                ctx.windowFocus("");

                // Only items 0 to 9 are rendered
                vars.windowHeightInItems = 10.0f;
                vars.itemsIn = 100;
                ctx.scrollToTop("");
                ctx.yieldFrame();
                assertEquals(vars.itemsIn * itemHeight, vars.offsetY2, d);
                assertEquals(10 + extraForcedItems, vars.itemsOut, d);
                if (extraForcedItems > 0) {
                    assertTrue(vars.itemsOutMask.get(vars.forceDisplayStart), d);
                    assertFalse(vars.itemsOutMask.get(vars.forceDisplayStart - 1), d);
                    assertFalse(vars.itemsOutMask.get(vars.forceDisplayEnd), d);
                }

                // Only items 0 to 10 are rendered, with a slightly taller window
                vars.windowHeightInItems = 10.5f;
                vars.itemsIn = 100;
                ctx.yieldFrame();
                assertEquals(vars.itemsIn * itemHeight, vars.offsetY2, d);
                assertEquals(11 + extraForcedItems, vars.itemsOut, d);

                // Item 0 and the following items, with the window scrolled a page down. The forced
                // items 15 and 16 are on page 2, so they're on this page.
                vars.windowHeightInItems = 10.0f;
                vars.itemsIn = 100;
                ctx.yieldFrame();
                ctx.keyPress(Key.PAGE_DOWN);
                ctx.yieldFrame();
                assertEquals(vars.itemsIn * itemHeight, vars.offsetY2, d);
                if (vars.clipperManualItemHeight) {
                    assertEquals(10, vars.itemsOut, d);
                } else {
                    assertEquals(1 + 10, vars.itemsOut, d);
                }
                if (vars.clipperManualItemHeight && vars.tableFreezeRows == 0) {
                    assertFalse(vars.itemsOutMask.get(0), d);
                } else {
                    assertTrue(vars.itemsOutMask.get(0), d);
                }

                // Scroll to the bottom
                ctx.scrollToBottom("");
                assertEquals(vars.itemsIn * itemHeight, vars.offsetY2, d);
                // A slight artifact of the size and alignment, item 89 is flagged as visible
                final int extraAtTop = vars.tableEnable ? 0 : 1;
                if (vars.clipperManualItemHeight) {
                    assertEquals(extraAtTop + 10 + extraForcedItems, vars.itemsOut, d);
                } else {
                    assertEquals(1 + extraAtTop + 10 + extraForcedItems, vars.itemsOut, d);
                }
                if (vars.clipperManualItemHeight && vars.tableFreezeRows == 0) {
                    assertFalse(vars.itemsOutMask.get(0), d);
                } else {
                    assertTrue(vars.itemsOutMask.get(0), d);
                }
                assertFalse(vars.itemsOutMask.get(1 + vars.tableFreezeRows), d);
                assertTrue(vars.itemsOutMask.get(90 + vars.tableFreezeRows), d);
                assertTrue(vars.itemsOutMask.get(99), d);
                if (extraForcedItems > 0) {
                    assertTrue(vars.itemsOutMask.get(vars.forceDisplayStart), d);
                    assertFalse(vars.itemsOutMask.get(vars.forceDisplayStart - 1), d);
                    assertFalse(vars.itemsOutMask.get(vars.forceDisplayEnd), d);
                }

                // Edge cases
                vars.itemsIn = 1;
                ctx.yieldFrame();
                ctx.yieldFrame();
                assertEquals(vars.itemsIn * itemHeight, vars.offsetY2, d);
                assertEquals(1, vars.itemsOut, d);
                assertTrue(vars.itemsOutMask.get(0), d);

                vars.itemsIn = 0;
                ctx.yieldFrame();
                assertEquals(vars.itemsIn * itemHeight, vars.offsetY2, d);
                assertEquals(0, vars.itemsOut, d);
            }
        }
    }

    /** misc_clipper_dupe_ranges: the user submitting duplicate ranges. */
    @Test
    void testMiscClipperDupeRanges() {
        final ClipperTestVars vars = new ClipperTestVars();
        ctx.setGui(clipperGui(vars));
        vars.clipperManualItemHeight = false;
        vars.forceDisplayStart = 0;
        vars.forceDisplayEnd = 1;
        ctx.setRef(vars.windowOut);
        vars.windowHeightInItems = 10.0f;
        vars.itemsIn = 100;
        ctx.scrollToTop("");
        ctx.yieldFrames(2);
        assertEquals(10, vars.itemsOut);

        vars.forceDisplayStart = 0;
        vars.forceDisplayEnd = 0;
        ctx.scrollToBottom("");
        ctx.yieldFrames(2);
        assertEquals(1 + 11, vars.itemsOut);

        vars.forceDisplayStart = 0;
        vars.forceDisplayEnd = 1;
        ctx.yieldFrames(2);
        assertEquals(1 + 11, vars.itemsOut);
    }

    /** misc_clipper_layout: the clipper's effect on the layout. */
    @Test
    void testMiscClipperLayout() {
        final boolean[] firstFrame = {true};
        ctx.setGui(
                () -> {
                    IkGui.begin("Test Window 1", null, WindowFlags.NO_SAVED_SETTINGS);
                    final Vector2f w0c =
                            new Vector2f(IkGuiInternal.context.windowCurrent.contentSize);
                    IkGui.text("ContentHeight " + w0c.y);
                    for (int n = 0; n < 99; ++n) {
                        IkGui.text(Integer.toString(n));
                    }
                    final Vector2f w0a = IkGui.getCursorPos();
                    IkGui.sameLine();
                    final Vector2f w0b = IkGui.getCursorPos();
                    IkGui.end();

                    IkGui.begin("Test Window 2", null, WindowFlags.NO_SAVED_SETTINGS);
                    final Vector2f w1c =
                            new Vector2f(IkGuiInternal.context.windowCurrent.contentSize);
                    IkGui.text("ContentHeight " + w1c.y);
                    final ListClipper clipper = new ListClipper();
                    clipper.begin(99);
                    while (clipper.step()) {
                        for (int n = clipper.displayStart; n < clipper.displayEnd; ++n) {
                            IkGui.text(Integer.toString(n));
                        }
                    }
                    final Vector2f w1a = IkGui.getCursorPos();
                    IkGui.sameLine();
                    final Vector2f w1b = IkGui.getCursorPos();
                    IkGui.end();

                    ctx.checkEquals(w0a, w1a, "cursor after");
                    ctx.checkEquals(w0b.y, w1b.y, "cursor after same line");
                    if (!firstFrame[0]) {
                        ctx.checkEquals(w0c.y, w1c.y, "content height");
                    }
                    firstFrame[0] = false;
                });
        ctx.yieldFrames(3);
    }

    /** misc_clipper_single: clippers with a single item or no items. */
    @Test
    void testMiscClipperSingle() {
        ctx.setGui(
                () -> {
                    IkGui.begin(
                            "Test Window",
                            null,
                            WindowFlags.NO_TITLE_BAR | WindowFlags.NO_SAVED_SETTINGS);
                    final int[] sections = {5, 1, 5, 0, 2};
                    for (int s = 0; s < sections.length; ++s) {
                        final int itemCount = sections[s];
                        IkGui.text("Begin");
                        final float startY = IkGui.getCursorScreenPos().y;
                        final ListClipper clipper = new ListClipper();
                        clipper.begin(itemCount);
                        while (clipper.step()) {
                            for (int n = clipper.displayStart; n < clipper.displayEnd; ++n) {
                                IkGui.button(
                                        "Section " + s + " Button " + n, -Float.MIN_VALUE, 0.0f);
                            }
                        }
                        final float offsetY2 = IkGui.getCursorScreenPos().y - startY;
                        ctx.checkEquals(
                                IkGui.getFrameHeightWithSpacing() * itemCount,
                                offsetY2,
                                "section " + s);
                        IkGui.text("End");
                        IkGui.separator();
                    }
                    IkGui.end();
                });
        ctx.yieldFrames(3);
    }

    /** misc_clipper_log: logging bypasses the clipper. */
    @Test
    void testMiscClipperLog() {
        ctx.setGui(
                () -> {
                    IkGui.begin(
                            "Test Window",
                            null,
                            WindowFlags.NO_TITLE_BAR | WindowFlags.NO_SAVED_SETTINGS);
                    IkGui.logToClipboard();
                    final ListClipper clipper = new ListClipper();
                    clipper.begin(100);
                    while (clipper.step()) {
                        for (int n = clipper.displayStart; n < clipper.displayEnd; ++n) {
                            IkGui.text(Integer.toString(n));
                        }
                    }
                    IkGui.logFinish();
                    final String clipboard = IkGui.getClipboardText();
                    ctx.checkEquals(
                            100L, clipboard.chars().filter(c -> c == '\n').count(), "line count");
                    IkGui.end();
                });
        ctx.yieldFrames(2);
    }

    /**
     * misc_clipper_floating_point_precision: a clipper with a very high number of items, which has
     * mitigations for floating point issues.
     */
    @Test
    void testMiscClipperFloatingPointPrecision() {
        final int[] step = {0};
        ctx.setGui(
                () -> {
                    IkGui.begin(
                            "Test Window",
                            null,
                            WindowFlags.NO_TITLE_BAR | WindowFlags.NO_SAVED_SETTINGS);
                    final int count =
                            switch (step[0]) {
                                case 0 -> 100_000;
                                case 1 -> 10_000_000;
                                case 2 -> 100_000_000;
                                default -> 0;
                            };
                    final float y1 = IkGui.getCursorScreenPos().y;
                    final ListClipper clipper = new ListClipper();
                    clipper.begin(count);
                    while (clipper.step()) {
                        for (int n = clipper.displayStart; n < clipper.displayEnd; ++n) {
                            IkGui.text(Integer.toString(n));
                        }
                    }
                    final float y2 = IkGui.getCursorScreenPos().y;
                    ctx.checkEquals(
                            count * IkGui.getTextLineHeightWithSpacing(),
                            y2 - y1,
                            "height, step " + step[0]);
                    IkGui.end();
                });
        ctx.setRef("Test Window");
        for (int s = 0; s < 4; ++s) {
            step[0] = s;
            ctx.yieldFrame();
            ctx.scrollToTop("");
            ctx.yieldFrames(2);
            ctx.scrollToBottom("");
            ctx.yieldFrames(2);
        }
    }

    /** misc_clipboard: the clipboard starts empty, and stores text. */
    @Test
    void testMiscClipboard() {
        ctx.setGui(() -> {});
        // By design, tests start with an empty clipboard, so the user's clipboard doesn't leak in
        assertEquals("", IkGui.getClipboardText());
        final String message = "Clippy is alive.";
        IkGui.setClipboardText(message);
        assertEquals(message, IkGui.getClipboardText());
    }

    /** misc_text_filter: the text filter's parsing and matching rules. */
    @Test
    void testMiscTextFilter() {
        final TextFilter drawnFilter = new TextFilter();
        ctx.setGui(
                () -> {
                    IkGui.begin("Text filter", null, WindowFlags.NO_SAVED_SETTINGS);
                    // Drawing the filter input
                    drawnFilter.draw("Filter");
                    IkGui.end();
                });
        ctx.setRef("Text filter");
        ctx.itemInput("Filter");
        // Rebuilds the filter
        ctx.keyCharsAppend("Big,Cat,, ,  ,Bird");

        final TextFilter filter = new TextFilter();
        final java.util.function.Consumer<String> setFilter =
                value -> {
                    filter.inputBuffer.set(value);
                    filter.build();
                };
        final java.util.function.BiConsumer<String, Boolean> check =
                (text, expected) ->
                        assertEquals(
                                expected,
                                filter.passFilter(text),
                                "filter '" + filter.inputBuffer + "' on '" + text + "'");

        // Or
        setFilter.accept("bar,car");
        check.accept("bartender", true);
        check.accept("cartender", true);

        setFilter.accept("bar");
        check.accept("bartender", true);
        check.accept("cartender", false);

        // Exclude
        setFilter.accept("-bar");
        check.accept("bartender", false);
        check.accept("cartender", true);
        setFilter.accept("   -bar");
        check.accept("bartender", false);
        check.accept("cartender", true);

        // Blank trimming
        setFilter.accept(" bar , foo");
        check.accept("bartender", true);
        check.accept("cartender", false);

        setFilter.accept(" ");
        assertFalse(filter.isActive());

        setFilter.accept("  ,  ,- ");
        assertFalse(filter.isActive());

        // A space is an AND operator
        setFilter.accept("foo bar");
        check.accept("bartender", false);
        check.accept("foobar", true);
        check.accept("barfoo", true);
        check.accept("bartenderfoo", true);

        setFilter.accept(" bar   foo");
        check.accept("bartender", false);
        check.accept("foobar", true);
        check.accept("barfoo", true);
        check.accept("bartenderfoo", true);

        // An isolated '-' is an empty word
        setFilter.accept("- bar");
        check.accept("bartender", true);
        check.accept("cartender", false);

        // Excludes in any order
        setFilter.accept("bar -foo");
        check.accept("bartender", true);
        check.accept("foobar", false);
        setFilter.accept("-foo bar");
        check.accept("bartender", true);
        check.accept("foobar", false);

        // Multiple excludes
        setFilter.accept("-foo -bar");
        check.accept("foo", false);
        check.accept("bar", false);
        check.accept("foobar", false);

        setFilter.accept("bar,-foo");
        check.accept("bartender", true);
        check.accept("foobar", false);
        setFilter.accept("-foo,bar");
        check.accept("bartender", true);
        check.accept("foobar", false);

        // Quotes
        setFilter.accept("\"foo bar\"");
        check.accept("bartender", false);
        check.accept("foobar", false);
        check.accept("foo bar", true);

        setFilter.accept("-\"foo bar\"");
        check.accept("bartender", true);
        check.accept("foobar", true);
        check.accept("foo bar", false);

        setFilter.accept("-tender");
        check.accept("tender", false);
        check.accept("-tender", false);

        setFilter.accept("-\"tender\"");
        check.accept("tender", false);
        check.accept("-tender", false);

        setFilter.accept("\"-tender\"");
        check.accept("tender", false);
        check.accept("-tender", true);

        setFilter.accept("-\" tender\"");
        check.accept("tender", true);
        check.accept(" tender", false);

        setFilter.accept("\"foo bar\",-tender");
        check.accept("bartender", false);
        check.accept("hello", false);
        check.accept("foo bar", true);
        check.accept("foo bar tender", false);

        // More AND mode
        setFilter.accept("foo bar");
        check.accept("bartender", false);
        check.accept("footender", false);
        check.accept("bar foo", true);

        setFilter.accept("\"foo bar\",hello world");
        check.accept("hello", false);
        check.accept("world", false);
        check.accept("world hello", true);
        check.accept("bar foo", false);
        check.accept("foo bar", true);

        setFilter.accept("foo bar -tender");
        check.accept("bar", false);
        check.accept("foo", false);
        check.accept("bar foo", true);
        check.accept("bartender foo", false);

        setFilter.accept("hello world,food truck");
        check.accept("hello world", true);
        check.accept("truck of food", true);
        check.accept("hello truck", false);
        check.accept("foodie world", false);
    }

    /** misc_log_functions: logging widgets and rendered text. */
    @Test
    void testMiscLogFunctions() {
        // Upstream uses IM_NEWLINE, which the port's logging writes as \n
        final String nl = "\n";
        ctx.setGui(
                () -> {
                    IkGui.begin(
                            "Test Window",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS | WindowFlags.ALWAYS_AUTO_RESIZE);
                    final java.util.function.BiConsumer<Runnable, String> expectLog =
                            (code, expected) -> {
                                IkGui.logToClipboard();
                                code.run();
                                IkGui.logFinish();
                                ctx.checkEquals(
                                        expected, IkGui.getClipboardText(), expected.trim());
                            };

                    // Button
                    expectLog.accept(() -> IkGui.button("Button"), "[ Button ]" + nl);

                    // Checkbox
                    expectLog.accept(
                            () -> IkGui.checkbox("Checkbox", new IkBoolean(false)),
                            "[ ] Checkbox" + nl);
                    expectLog.accept(
                            () -> IkGui.checkbox("Checkbox", new IkBoolean(true)),
                            "[x] Checkbox" + nl);
                    expectLog.accept(
                            () -> IkGui.checkboxFlags("Checkbox Flags", new IkInt(1), 3),
                            "[~] Checkbox Flags" + nl);

                    // Radio button
                    expectLog.accept(() -> IkGui.radioButton("Radio", false), "( ) Radio" + nl);
                    expectLog.accept(() -> IkGui.radioButton("Radio", true), "(x) Radio" + nl);

                    // Sliders and drags
                    expectLog.accept(
                            () ->
                                    IkGui.sliderFloat(
                                            "Slider Float", new float[] {42.42f}, 0.0f, 100.0f),
                            "{ 42.420 } Slider Float" + nl);
                    // Upstream passes (0, 100) as the speed and min, so the max is 0
                    expectLog.accept(
                            () -> IkGui.dragInt("Drag Int", new int[] {64}, 0, 100, 0),
                            "{ 64 } Drag Int" + nl);

                    // Separator
                    expectLog.accept(
                            () -> {
                                IkGui.separator();
                                IkGui.text("Hello World");
                            },
                            "--------------------------------" + nl + "Hello World" + nl);

                    // Collapsing header
                    expectLog.accept(
                            () -> {
                                IkGui.setNextItemOpen(false);
                                if (IkGui.collapsingHeader(
                                        "Collapsing Header", TreeNodeFlags.NO_AUTO_OPEN_ON_LOG)) {
                                    IkGui.text("Closed Header Content");
                                }
                            },
                            "### Collapsing Header ###" + nl);
                    expectLog.accept(
                            () -> {
                                IkGui.setNextItemOpen(true);
                                if (IkGui.collapsingHeader("Collapsing Header")) {
                                    IkGui.text("Open Header Content");
                                }
                            },
                            "### Collapsing Header ###" + nl + "Open Header Content" + nl);

                    // Tree node
                    expectLog.accept(
                            () -> {
                                IkGui.setNextItemOpen(false);
                                if (IkGui.treeNodeEx(
                                        "TreeNode", TreeNodeFlags.NO_AUTO_OPEN_ON_LOG)) {
                                    IkGui.text("Closed TreeNode Content");
                                    IkGui.treePop();
                                }
                            },
                            "> TreeNode" + nl);
                    expectLog.accept(
                            () -> {
                                IkGui.setNextItemOpen(true);
                                if (IkGui.treeNode("TreeNode")) {
                                    IkGui.text("Open TreeNode Content");
                                    IkGui.treePop();
                                }
                            },
                            "> TreeNode" + nl + "    Open TreeNode Content" + nl);
                    expectLog.accept(
                            () -> {
                                IkGui.setNextItemOpen(true);
                                final boolean open = IkGui.treeNode("TreeNode2");
                                IkGui.sameLine();
                                IkGui.smallButton("Button");
                                if (open) {
                                    IkGui.text("Open TreeNode Content");
                                    IkGui.treePop();
                                }
                            },
                            "> TreeNode2 [ Button ]" + nl + "    Open TreeNode Content" + nl);

                    // New line behaviors, where NaN means no reference position
                    final float p0 = 100;
                    final float p1 = 200;
                    final float none = Float.NaN;
                    // 1. Line breaks in the text
                    expectLog.accept(
                            () -> IkGuiImplLogging.logRenderedText(none, "Hello\nWorld"),
                            "Hello" + nl + "World" + nl);
                    expectLog.accept(
                            () -> IkGuiImplLogging.logRenderedText(p0, "Hello\nWorld"),
                            "Hello" + nl + "World" + nl);
                    // 2. Separate texts
                    expectLog.accept(
                            () -> {
                                IkGuiImplLogging.logRenderedText(none, "Hello");
                                IkGuiImplLogging.logRenderedText(none, "World");
                            },
                            "Hello World" + nl);
                    expectLog.accept(
                            () -> {
                                IkGuiImplLogging.logRenderedText(p0, "Hello");
                                IkGuiImplLogging.logRenderedText(p0, "World");
                            },
                            "Hello World" + nl);
                    expectLog.accept(
                            () -> {
                                IkGuiImplLogging.logRenderedText(p0, "Hello");
                                IkGuiImplLogging.logRenderedText(p1, "World");
                            },
                            "Hello" + nl + "World" + nl);
                    // 3. Trailing line breaks
                    expectLog.accept(
                            () -> {
                                IkGuiImplLogging.logRenderedText(p0, "Hello\n");
                                IkGuiImplLogging.logRenderedText(p0, "World");
                            },
                            "Hello" + nl + "World" + nl);
                    expectLog.accept(
                            () -> {
                                IkGuiImplLogging.logRenderedText(p0, "Hello\n");
                                IkGuiImplLogging.logRenderedText(p1, "World");
                            },
                            "Hello" + nl + nl + "World" + nl);
                    expectLog.accept(
                            () -> {
                                IkGuiImplLogging.logRenderedText(none, "Hello\n");
                                IkGuiImplLogging.logRenderedText(none, "World");
                            },
                            "Hello" + nl + "World" + nl);
                    IkGui.end();
                });
        ctx.yieldFrames(2);
    }

    /** misc_mouse_clicks: tracking mouse ownership, drag distance, multiple clicks and duration. */
    @Test
    void testMiscMouseClicks() {
        final int[] maxClickedCount = new int[MouseButton.COUNT];
        final long[] maxDownDuration = new long[MouseButton.COUNT];
        ctx.setGui(
                () -> {
                    final var io = ctx.context.io;
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    IkGui.button("Button");
                    for (int b = 0; b < MouseButton.COUNT; ++b) {
                        maxClickedCount[b] = Math.max(maxClickedCount[b], io.mouseClickedCount[b]);
                        maxDownDuration[b] = Math.max(maxDownDuration[b], io.mouseDownDuration[b]);
                    }
                    if (IkGui.button("Popup")) {
                        IkGui.openPopup("Popup");
                    }
                    if (IkGui.beginPopup("Popup")) {
                        IkGui.textUnformatted("...");
                        IkGui.endPopup();
                    }
                    IkGui.end();
                });
        final var io = ctx.context.io;
        ctx.setRef("Test Window");
        final MouseButton[] buttons = {MouseButton.LEFT, MouseButton.RIGHT, MouseButton.MIDDLE};

        // mouseDownOwned and mouseDownOwnedUnlessPopupClose
        for (MouseButton button : buttons) {
            final int b = button.index;
            ctx.mouseClickOnVoid(button);
            assertFalse(io.mouseDownOwned[b], button.name());
            assertFalse(io.mouseDownOwnedUnlessPopupClose[b], button.name());
            ctx.itemClick("Button", button);
            assertTrue(io.mouseDownOwned[b], button.name());
            assertTrue(io.mouseDownOwnedUnlessPopupClose[b], button.name());
            ctx.itemClick("Popup");
            ctx.mouseClickOnVoid(button);
            assertTrue(io.mouseDownOwned[b], button.name());
            assertFalse(io.mouseDownOwnedUnlessPopupClose[b], button.name());
        }
        // Close the popup
        ctx.mouseClickOnVoid(MouseButton.LEFT);

        // The maximum drag distance
        for (MouseButton button : buttons) {
            ctx.mouseMove("Button");
            ctx.mouseDown(button);
            ctx.mouseMoveToPos(io.mousePosition.x + 10.0f, io.mousePosition.y + 10.0f);
            ctx.mouseUp(button);
            assertEquals(
                    10.0f * 10.0f + 10.0f * 10.0f,
                    io.mouseDragMaxDistanceSquare[button.index],
                    button.name());
        }

        // Tracking multiple clicks
        ctx.mouseMove("Button");
        for (int clicks = 1; clicks < 6; ++clicks) {
            for (MouseButton button : buttons) {
                final String d = button.name() + " " + clicks + " clicks";
                maxClickedCount[button.index] = 0;
                ctx.mouseClickMulti(button, clicks);
                assertEquals(clicks, maxClickedCount[button.index], d);
                assertEquals(0, io.mouseClickedCount[button.index], d);
                assertEquals(clicks, io.mouseClickedLastCount[button.index], d);
            }
        }

        // Tracking how long the mouse is down, in milliseconds
        ctx.mouseMove("Button");
        for (int delay = 1; delay < 3; ++delay) {
            for (MouseButton button : buttons) {
                final String d = button.name() + " delay " + delay;
                maxDownDuration[button.index] = 0;
                ctx.mouseDown(button);
                ctx.sleepNoSkip(delay * 0.1f, 1.0f / 60.0f);
                ctx.mouseUp(button);
                assertTrue(
                        maxDownDuration[button.index] - delay * 100 <= 20,
                        d + " " + maxDownDuration[button.index]);
                assertTrue(io.mouseDownDuration[button.index] <= 0, d);
                assertTrue(
                        io.mouseDownDurationPrevious[button.index] - delay * 100 <= 20,
                        d + " " + io.mouseDownDurationPrevious[button.index]);
            }
        }
    }

    /** demo_misc_001: a few demo widgets. */
    @Test
    void testDemoMisc001() {
        ctx.setGui(ctx::showApp);
        ctx.setRef(IkGuiTestContext.DEMO);
        ctx.itemOpen("Widgets");
        ctx.itemOpen("Basic");
        ctx.itemClick("Basic/Button");
        ctx.itemClick("Basic/radio a");
        ctx.itemClick("Basic/radio b");
        ctx.itemClick("Basic/radio c");
        ctx.itemClick("Basic/combo");
        ctx.itemClick("Basic/combo");
        ctx.itemClick("Basic/color 2/##ColorButton");
        ctx.sleep(0.5f);
        ctx.popupCloseAll();

        ctx.itemOpen("Layout & Scrolling");
        ctx.itemHold("Scrolling/>>", 1.0f);
        ctx.sleep(0.5f);
    }

    /**
     * demo_cov_auto_open: opening everything in the demo window, and logging all of it. Upstream
     * also verifies the scroll max of the window with a test engine helper, which isn't ported.
     */
    @Test
    void testDemoCovAutoOpen() {
        ctx.setGui(ctx::showApp);
        ctx.setRef(IkGuiTestContext.DEMO);
        ctx.menuCheck("Tools/Debug Log");
        ctx.itemCheck("//IkGui Debug Log/All");
        ctx.itemClick("//IkGui Debug Log/Clear");
        ctx.itemUncheck("//IkGui Debug Log/Clipper");

        ctx.itemOpenAll("", -1);

        // The logging API
        assertEquals("", IkGui.getClipboardText());
        ctx.itemClick("Capture\\/Logging/LogButtons/Log To Clipboard");
        final int clipboardLength = IkGui.getClipboardText().length();
        // This varies, upstream had around 22000 characters
        assertTrue(clipboardLength > 15_000, "clipboard length " + clipboardLength);

        ctx.setRef("IkGui Debug Log");
        IkGui.setClipboardText("");
        ctx.itemClick("Copy");
        final int logLength = IkGui.getClipboardText().length();
        assertTrue(logLength > 1500, "log length " + logLength);
        ctx.itemClick("Clear");
        ctx.itemUncheck("All");
        ctx.windowClose("");
    }

    /** demo_cov_auto_close: closing everything in the demo window. */
    @Test
    void testDemoCovAutoClose() {
        ctx.setGui(ctx::showApp);
        ctx.setRef(IkGuiTestContext.DEMO);
        ctx.itemOpen("Widgets");
        ctx.itemOpen("Basic");
        ctx.itemCloseAll("");
    }

    /** demo_cov_001: opening the demo's main sections. */
    @Test
    void testDemoCov001() {
        ctx.setGui(ctx::showApp);
        ctx.setRef(IkGuiTestContext.DEMO);
        for (String section :
                new String[] {
                    "Help",
                    "Configuration",
                    "Window options",
                    "Widgets",
                    "Layout & Scrolling",
                    "Popups & Modal windows",
                    "Tables & Columns",
                    "Inputs & Focus"
                }) {
            ctx.itemOpen(section);
        }
    }

    /**
     * demo_cov_002: various demo elements that opening everything doesn't cover. The parts that use
     * upstream's test engine filters to click every checkbox in a window, and the circle
     * tessellation setting the port doesn't have, are left out.
     */
    @Test
    void testDemoCov002() {
        ctx.setGui(ctx::showApp);
        final var g = ctx.context;
        ctx.setRef(IkGuiTestContext.DEMO);
        ctx.itemOpen("Layout & Scrolling");
        ctx.itemCheck("Scrolling/Show Horizontal contents size demo window");
        ctx.itemUncheck("Scrolling/Show Horizontal contents size demo window");

        ctx.setRef(IkGuiTestContext.DEMO);
        ctx.menuCheck("Tools/About IkGui");
        ctx.setRef("About IkGui");
        ctx.itemCheck("Config\\/Build Information");

        ctx.setRef(IkGuiTestContext.DEMO);
        ctx.menuCheck("Tools/Style Editor");
        ctx.setRef("IkGui Style Editor");
        ctx.itemClick("##tabs/Sizes");
        ctx.itemClick("##tabs/Colors");
        ctx.itemClick("##tabs/Fonts");
        ctx.itemOpenAll("", 2);
        ctx.itemCloseAll("");
        ctx.itemClick("##tabs/Rendering");

        ctx.setRef(IkGuiTestContext.DEMO);
        ctx.menuCheck("Examples/Property editor");
        ctx.setRef("Example: Property editor");
        ctx.itemCheck("**/Use Clipper");
        ctx.itemUncheck("**/Use Clipper");

        ctx.setRef(IkGuiTestContext.DEMO);
        ctx.menuCheck("Examples/Custom rendering");
        ctx.setRef("Example: Custom rendering");
        ctx.itemClick("##TabBar/Primitives");
        ctx.itemClick("##TabBar/Canvas");
        ctx.itemClick("##TabBar/BG\\/FG draw lists");
        ctx.itemClick("##TabBar/Draw Channels");
        ctx.itemClick("##TabBar/Primitives");
        {
            ctx.itemInput("##TabBar/Primitives/Size");
            assertEquals(ctx.getID("##TabBar/Primitives/Size"), g.activeID);
            ctx.keyPress(chord(KeyModFlags.CTRL, Key.A));
            ctx.keyPress(chord(KeyModFlags.CTRL, Key.C));
            ctx.keyPress(Key.ENTER);
            final float backupSize = Float.parseFloat(IkGui.getClipboardText());

            // A basic flex of the sizing paths. Upstream also toggles anti-aliasing, which the
            // SDF draw list doesn't have.
            for (float size : new float[] {1.5f, 20.0f, 200.0f}) {
                ctx.itemInputValue("##TabBar/Primitives/Size", size);
                ctx.yieldFrames(2);
            }
            ctx.itemInputValue("##TabBar/Primitives/Size", backupSize);
        }

        ctx.setRef(IkGuiTestContext.DEMO);
        ctx.menuCheck("Examples/Console");
        ctx.setRef("Example: Console");
        ctx.itemClick("Input");
        ctx.keyCharsAppend("h");
        ctx.keyPress(Key.TAB);
        ctx.keyCharsReplace("cl");
        ctx.keyPress(Key.TAB);
        ctx.keyCharsReplace("cla");
        ctx.keyPress(Key.TAB);
        ctx.keyCharsReplace("zzZZzz");
        ctx.keyPress(Key.TAB);
        ctx.keyCharsReplaceEnter("HELP");
        ctx.keyCharsReplaceEnter("HISTORY");
        ctx.keyCharsReplaceEnter("CLEAR");

        ctx.setRef(IkGuiTestContext.DEMO);
        ctx.menuSetAllChecked("Examples", false);
        ctx.menuSetAllChecked("Tools", false);

        ctx.setRef(IkGuiTestContext.DEMO);
        ctx.itemOpen("Widgets");
        ctx.itemOpen("Color\\/Picker Widgets");
        ctx.itemClick("Color\\/Picker Widgets/Palette");
        ctx.itemClose("Widgets");

        ctx.setRef(IkGuiTestContext.DEMO);
        ctx.itemOpen("Popups & Modal windows");
        ctx.itemOpen("Modals");
        ctx.itemClick("Modals/Delete..");
        ctx.itemClick("//Delete?/OK");
        ctx.setRef(IkGuiTestContext.DEMO);
        ctx.itemClose("Modals");
        ctx.itemClose("Popups & Modal windows");

        ctx.itemOpen("Widgets");
        ctx.itemOpen("Text Input/Completion, History, Edit Callbacks");
        ctx.setRef(IkGuiTestContext.DEMO + "/Text Input/Completion, History, Edit Callbacks");
        ctx.itemClick("Completion");
        ctx.itemClick("History");
        ctx.itemClick("Edit");

        ctx.setRef(IkGuiTestContext.DEMO);
        ctx.itemOpen("Text Input/Resize Callback");
        ctx.setRef(IkGuiTestContext.DEMO + "/Text Input/Resize Callback");
        ctx.itemClick("##MyStr");

        // Horizontal scrolling works in the driver
        final Window window = ctx.getWindowByRef("//" + IkGuiTestContext.DEMO);
        assertNotNull(window);
        final Vector2f backupSize = new Vector2f(window.size);
        ctx.setRef(IkGuiTestContext.DEMO);
        ctx.windowResize("", 100, backupSize.y);
        ctx.itemOpen("Widgets");
        ctx.itemClick("Basic/radio c");
        ctx.windowResize("", backupSize.x, backupSize.y);
    }

    /** demo_cov_apps: the demo's example and tool windows. */
    @Test
    void testDemoCovApps() {
        ctx.setGui(ctx::showApp);
        ctx.setRef(IkGuiTestContext.DEMO);
        ctx.menuClick("Menu/Open Recent/More..");
        ctx.popupCloseAll();
        ctx.menuSetAllChecked("Examples", true);
        ctx.menuSetAllChecked("Examples", false);
        ctx.menuSetAllChecked("Tools", true);
        ctx.menuSetAllChecked("Tools", false);
    }

    /** demo_cov_examples: the documents example. */
    @Test
    void testDemoCovExamples() {
        // The demo's documents are static, so restore them for other tests afterward
        final var documents = IkGuiDemoExamples.documentsApp.documents;
        final boolean[] wasOpen = new boolean[documents.size()];
        final boolean[] wasDirty = new boolean[documents.size()];
        for (int i = 0; i < documents.size(); ++i) {
            wasOpen[i] = documents.get(i).open.get();
            wasDirty[i] = documents.get(i).dirty;
        }
        try {
            demoCovExamples();
        } finally {
            for (int i = 0; i < documents.size(); ++i) {
                documents.get(i).open.set(wasOpen[i]);
                documents.get(i).dirty = wasDirty[i];
            }
            IkGuiDemoExamples.documentsApp.closeQueue.clear();
            IkGuiDemoExamples.showAppDocuments.set(false);
        }
    }

    private void demoCovExamples() {
        ctx.setGui(ctx::showApp);
        ctx.setRef(IkGuiTestContext.DEMO);
        ctx.menuCheck("Examples/Documents");

        ctx.setRef("Example: Documents");
        ctx.itemCheck("**/Lettuce");
        ctx.itemClick("##tabs/Lettuce###doc0");
        ctx.itemClick("##tabs/Lettuce###doc0/**/Modify");
        ctx.menuClick("File");
        ctx.itemClick("//$FOCUSED/Close All Documents");
        ctx.itemClick("//Save?/Yes");

        // Reopen the Lettuce document, in case the test runs again
        ctx.setRef("Example: Documents");
        ctx.menuClick("File/Open/Lettuce");
    }

    /** demo_cov_styles: selecting every style in the style editor. */
    @Test
    void testDemoCovStyles() {
        ctx.setGui(ctx::showApp);
        ctx.setRef(IkGuiTestContext.DEMO);
        ctx.menuCheck("Tools/Style Editor");
        ctx.setRef("IkGui Style Editor");
        ctx.comboClickAll("Fonts##Selector");
        ctx.setRef("IkGui Style Editor");
        ctx.comboClickAll("Colors##Selector");
    }

    /** demo_cov_color_picker: the color edit and picker options popups. */
    @Test
    void testDemoCovColorPicker() {
        ctx.setGui(ctx::showApp);
        ctx.setRef(IkGuiTestContext.DEMO);
        ctx.itemOpen("Widgets");
        ctx.itemOpen("Basic");

        // Open the picker settings popup
        ctx.mouseMove("Basic/color 2/##ColorButton");
        ctx.mouseClick(MouseButton.RIGHT);
        ctx.yieldFrame();

        ctx.setRef("//$FOCUSED");
        for (String option :
                new String[] {"RGB", "HSV", "Hex", "RGB", "0..255", "0.00..1.00", "0..255"}) {
            ctx.itemClick(option);
        }
        ctx.itemClick("Copy as..");
        // Close the popup
        ctx.keyPress(Key.ESCAPE);

        for (int pickerType = 0; pickerType < 2; ++pickerType) {
            // Open the color picker
            ctx.setRef(IkGuiTestContext.DEMO);
            ctx.mouseMove("Basic/color 2/##ColorButton");
            ctx.mouseClick(MouseButton.LEFT);
            ctx.yieldFrame();

            // Open the picker style chooser
            ctx.setRef("//$FOCUSED");
            final Window picker = ctx.getWindowByRef("");
            ctx.mouseMoveToPos(
                    picker.position.x + picker.size.x * 0.5f,
                    picker.position.y + picker.size.y * 0.5f);
            ctx.mouseClick(MouseButton.RIGHT);
            ctx.yieldFrame();

            // Select the picker type
            ctx.setRef("//$FOCUSED");
            ctx.mouseMove(ctx.getID("$$" + pickerType + "/##selectable"));
            ctx.mouseClick(MouseButton.LEFT);

            // Use the picker
            ctx.setRef("//$FOCUSED");
            if (pickerType == 0) {
                final RectFloat sv = ctx.itemInfo("##picker/sv").rect;
                ctx.mouseMoveToPos(sv.getLeft() + 1, sv.getTop() + 1);
                ctx.mouseDown(MouseButton.LEFT);
                ctx.mouseMoveToPos(sv.getRight() - 1, sv.getBottom() - 1);
                ctx.mouseMoveToPos(sv.getCenterX(), sv.getCenterY());
                ctx.mouseUp(MouseButton.LEFT);

                final RectFloat hue = ctx.itemInfo("##picker/hue").rect;
                ctx.mouseMoveToPos(hue.getCenterX(), hue.getTop() + 1);
                ctx.mouseDown(MouseButton.LEFT);
                ctx.mouseMoveToPos(hue.getCenterX(), hue.getBottom() - 1);
                ctx.mouseMoveToPos(hue.getCenterX(), hue.getTop() + 1);
                ctx.mouseUp(MouseButton.LEFT);
            } else {
                ctx.mouseMove("##picker/hsv");
                ctx.mouseClick(MouseButton.LEFT);
            }
            ctx.popupCloseAll();
        }
    }

    /**
     * demo_cov_metrics: opening the sections of the metrics window. Upstream uses test engine
     * filters to limit how deep it opens each section, here each section is opened along with one
     * level below it.
     */
    @Test
    void testDemoCovMetrics() {
        ctx.setGui(ctx::showApp);
        ctx.setRef("//" + IkGuiTestContext.DEMO);
        ctx.menuCheck("Tools/Metrics\\/Debugger");

        // Show tables, to exercise more of the metrics
        ctx.itemOpen("Tables & Columns");
        ctx.itemOpen("Tables/Advanced");

        ctx.setRef("//IkGui Metrics\\/Debugger");
        ctx.itemCloseAll("");
        ctx.itemOpenAll("", 2);
        ctx.itemCloseAll("");
    }

    /**
     * drawlist_text_wordwrap_1: the word wrapping rules, with and without keeping blanks. Each case
     * gives the expected length of every wrapped line, or nothing when there's no expectation.
     */
    @Test
    void testDrawlistTextWordwrap1() {
        record WordWrapTestCase(
                String text, String widthText, float widthScale, int[] results0, int[] results1) {
            WordWrapTestCase(String text, String widthText, int[] results0, int[] results1) {
                this(text, widthText, 1.0f, results0, results1);
            }
        }
        final WordWrapTestCase[] testCases = {
            new WordWrapTestCase("Hello World", "Hello", new int[] {5, 5}, new int[] {5, 1, 5}),
            // #8990
            new WordWrapTestCase("Hello World", "Hello ", new int[] {5, 5}, new int[] {6, 5}),
            new WordWrapTestCase("Hello World", "Hello W", new int[] {5, 5}, new int[] {6, 5}),
            new WordWrapTestCase("Hello  World", "Hello", new int[] {5, 5}, new int[] {5, 2, 5}),
            new WordWrapTestCase("Hello  World", "Hello ", new int[] {5, 5}, new int[] {6, 6}),
            new WordWrapTestCase("Hello  World", "Hello  ", new int[] {5, 5}, new int[] {7, 5}),
            new WordWrapTestCase("Hello  World", "Hello  W", new int[] {5, 5}, new int[] {7, 5}),
            new WordWrapTestCase(
                    "Hello World!", "Hello", new int[] {5, 5, 1}, new int[] {5, 1, 5, 1}),
            new WordWrapTestCase("Hello World!", "Hello ", new int[] {5, 6}, new int[] {6, 6}),
            new WordWrapTestCase("Hello World!", "Hello W", new int[] {5, 6}, new int[] {6, 6}),
            new WordWrapTestCase("HelloWorld!", "Hello", new int[] {5, 5, 1}, new int[] {5, 5, 1}),
            new WordWrapTestCase("HelloWorld!", "HelloW", new int[] {6, 5}, new int[] {6, 5}),
            new WordWrapTestCase("HelloWorld!", "HelloWo", new int[] {7, 4}, new int[] {7, 4}),
            new WordWrapTestCase(
                    "Hello.World!", "Hello", new int[] {5, 1, 5, 1}, new int[] {5, 1, 5, 1}),
            new WordWrapTestCase("Hello.World!", "Hello.", new int[] {6, 6}, new int[] {6, 6}),
            new WordWrapTestCase("Hello.World!", "Hello.W", new int[] {6, 6}, new int[] {6, 6}),
            new WordWrapTestCase("abcde!.", "abcde!.", new int[] {7}, new int[] {7}),
            // #8139, #8439
            new WordWrapTestCase("abcde!. That", "abcde!.", new int[] {7, 4}, new int[] {7, 5}),
            // #8503
            new WordWrapTestCase("Hello 1.4023", "Hello 1.4", new int[] {5, 6}, new int[] {6, 6}),
            // #9094
            new WordWrapTestCase("example... is", "example", new int[] {7, 6}, new int[] {7, 6}),
            // #9094, too long to fit
            new WordWrapTestCase("example... is", "example.", new int[] {8, 5}, new int[] {8, 5}),
            new WordWrapTestCase("example... is", "example..", new int[] {9, 4}, new int[] {9, 4}),
            // #9094
            new WordWrapTestCase(
                    "example... is", "example...", new int[] {10, 2}, new int[] {10, 3}),
            new WordWrapTestCase(
                    "example... is", "example... ", new int[] {10, 2}, new int[] {11, 2}),
            new WordWrapTestCase("example...", "example... ", new int[] {10}, new int[] {10}),
            // #8990
            new WordWrapTestCase(
                    "a a a a a a a", "a a a", new int[] {5, 5, 1}, new int[] {4, 4, 5}),
            new WordWrapTestCase(
                    "a a a a a a a", "a a a ", new int[] {5, 5, 1}, new int[] {6, 6, 1}),
            new WordWrapTestCase("aaaaa  bbb", "aaaaa ", new int[] {5, 3}, new int[] {6, 4}),
            new WordWrapTestCase("aaaaa  bbb", "aaaaa  ", new int[] {5, 3}, new int[] {7, 3}),
            new WordWrapTestCase("aaaaa  bbb", "aaaaa  b", new int[] {5, 3}, new int[] {7, 3}),
            new WordWrapTestCase("aa   bb cc", "aa   bb c", new int[] {7, 2}, new int[] {8, 2}),
            // Upstream wonders if emitting the space would be better, but it would be inconsistent
            // with the wider version
            new WordWrapTestCase(
                    "A fox", "T", 0.3f, new int[] {1, 1, 1, 1}, new int[] {1, 1, 1, 1, 1}),
            // Getting this nicely compact requires scanning for the span width
            new WordWrapTestCase("The quick brown fox", "The ", new int[] {}, new int[] {}),
        };
        final java.util.List<String> errors = new java.util.ArrayList<>();
        ctx.setGui(
                () -> {
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    final int fontSize = IkGuiInternal.context.fontSize;
                    for (int testN = 0; testN < testCases.length; testN++) {
                        final WordWrapTestCase tc = testCases[testN];
                        final float wrapWidth =
                                IkGui.calcTextSize(tc.widthText()).x * tc.widthScale();
                        for (int step = 0; step < 2; step++) {
                            final boolean keepBlanks = step == 1;
                            final String text = tc.text();
                            final java.util.List<Integer> actual = new java.util.ArrayList<>();
                            int s = 0;
                            while (s < text.length() && actual.size() < 10) {
                                final int lineEnd =
                                        DrawList.calcWordWrapPosition(
                                                fontSize,
                                                text,
                                                s,
                                                text.length(),
                                                wrapWidth,
                                                keepBlanks);
                                actual.add(lineEnd - s);
                                // Wrapping skips upcoming blanks
                                s =
                                        DrawList.calcWordWrapNextLineStart(
                                                text, lineEnd, text.length(), keepBlanks);
                            }
                            // Show each wrapped line, like upstream does
                            IkGui.textDisabled(testN + ": '" + text + "' " + actual);
                            final int[] expected = keepBlanks ? tc.results1() : tc.results0();
                            if (expected.length == 0) {
                                continue;
                            }
                            final java.util.List<Integer> expectedList =
                                    java.util.Arrays.stream(expected).boxed().toList();
                            if (!expectedList.equals(actual)) {
                                errors.add(
                                        testN
                                                + " '"
                                                + text
                                                + "' keepBlanks="
                                                + keepBlanks
                                                + ": expected "
                                                + expectedList
                                                + " but was "
                                                + actual);
                            }
                        }
                    }
                    IkGui.end();
                });
        assertEquals(java.util.List.of(), errors);
    }

    /**
     * Not from upstream: in the metrics window, an open draw command scrolled partly out of view
     * doesn't make the commands after it overlap its contents. The commands used to go through a
     * list clipper, which assumes every item is one line tall.
     */
    @Test
    void testMetricsOpenDrawCommandScrolledOutOfView() {
        final DrawList[] targetDrawList = {null};
        ctx.setGui(
                () -> {
                    IkGui.setNextWindowPos(600, 20, Condition.ALWAYS);
                    IkGui.setNextWindowSize(300, 300, Condition.ALWAYS);
                    IkGui.begin("Target", null, WindowFlags.NO_SAVED_SETTINGS);
                    final Window target = IkGuiInternal.context.windowCurrent;
                    // Closed polygons with 32 points each, so an open command is tall
                    final int pointCount = 32;
                    final float[] coordinates = new float[pointCount * 2];
                    for (int n = 0; n < 12; n++) {
                        for (int p = 0; p < pointCount; p++) {
                            final double angle = 2 * Math.PI * p / pointCount;
                            coordinates[p * 2] =
                                    target.position.x + 100 + n + (float) Math.cos(angle) * 40;
                            coordinates[p * 2 + 1] =
                                    target.position.y + 100 + (float) Math.sin(angle) * 40;
                        }
                        IkGui.getWindowDrawList()
                                .addPolyline(coordinates, pointCount, 0xFF00FF00, true, 1.0f);
                    }
                    targetDrawList[0] = target.drawList;
                    IkGui.end();

                    IkGui.setNextWindowPos(20, 20, Condition.ALWAYS);
                    IkGui.setNextWindowSize(500, 300, Condition.ALWAYS);
                    IkGui.begin("Inspect", null, WindowFlags.NO_SAVED_SETTINGS);
                    IkGuiImplMetrics.debugNodeDrawList(
                            target, target.viewport, target.drawList, "DrawList");
                    IkGui.end();
                });
        final Window inspect = ctx.getWindowByRef("//Inspect");
        final String listRef = "//Inspect/" + System.identityHashCode(targetDrawList[0]);
        ctx.itemOpen(listRef);
        ctx.itemOpen(listRef + "/5");
        ctx.scrollToY(inspect, 0.0f);
        ctx.yieldFrame();
        final float unscrolledTop = ctx.itemInfo(listRef + "/6").rect.getTop();

        // Scroll so the header of command 5 is a few lines above the visible area, while most of
        // its points are still visible
        final float header5Top = ctx.itemInfo(listRef + "/5").rect.getTop();
        final float scroll =
                header5Top - inspect.rectInner.getTop() + IkGui.getTextLineHeightWithSpacing() * 4;
        ctx.scrollToY(inspect, scroll);
        ctx.yieldFrame();
        assertEquals(scroll, inspect.scrollPosition.y, "scroll");
        assertEquals(
                unscrolledTop - scroll, ctx.itemInfo(listRef + "/6").rect.getTop(), "command 6");
    }
}
