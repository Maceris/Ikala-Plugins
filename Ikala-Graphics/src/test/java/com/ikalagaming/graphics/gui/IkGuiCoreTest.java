package com.ikalagaming.graphics.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.Format;
import com.ikalagaming.graphics.TextureHandle;
import com.ikalagaming.graphics.gui.data.Context;
import com.ikalagaming.graphics.gui.data.DrawData;
import com.ikalagaming.graphics.gui.data.IkBoolean;
import com.ikalagaming.graphics.gui.data.Storage;
import com.ikalagaming.graphics.gui.data.Window;
import com.ikalagaming.graphics.gui.enums.Condition;
import com.ikalagaming.graphics.gui.enums.MouseButton;
import com.ikalagaming.graphics.gui.enums.StyleVariable;
import com.ikalagaming.graphics.gui.flags.WindowFlags;

import org.joml.Vector2f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for the core window, layout and interaction logic. These run headless, without any fonts
 * loaded, so text sizes are estimated.
 */
class IkGuiCoreTest {

    /** How much precision we expect in float comparisons. */
    private static final float DELTA = 0.001f;

    private Context context;

    @BeforeEach
    void setUp() {
        context = IkGui.createContext();
        // Don't read or write an .ini file
        context.io.iniFilename = null;
        context.io.displaySize.set(1280, 720);
        context.io.mouseInsideWindow = true;
        // Process one input event per frame for each input, so we can be precise about timing
        context.io.configInputTrickleEventQueue = true;
    }

    @AfterEach
    void tearDown() {
        IkGui.destroyContext();
    }

    /**
     * Run one frame of the GUI.
     *
     * @param ui The UI code for the frame.
     */
    private static void frame(Runnable ui) {
        IkGui.newFrame();
        ui.run();
        IkGui.render();
    }

    /**
     * Run multiple frames of the GUI with the same code.
     *
     * @param count The number of frames.
     * @param ui The UI code for each frame.
     */
    private static void frames(int count, Runnable ui) {
        for (int i = 0; i < count; ++i) {
            frame(ui);
        }
    }

    private void moveMouse(float x, float y) {
        context.io.addMousePosEvent(x, y);
    }

    private void mouseDown() {
        context.io.addMouseButtonEvent(MouseButton.LEFT, true);
    }

    private void mouseUp() {
        context.io.addMouseButtonEvent(MouseButton.LEFT, false);
    }

    private static Runnable fixedWindow(
            String name, float x, float y, float w, float h, Runnable body) {
        return () -> {
            IkGui.setNextWindowPos(x, y, Condition.FIRST_USE_EVER);
            IkGui.setNextWindowSize(w, h, Condition.FIRST_USE_EVER);
            IkGui.begin(name);
            body.run();
            IkGui.end();
        };
    }

    @Test
    void testWindowIsPositionedAndRendered() {
        frames(2, fixedWindow("Test", 100, 120, 200, 150, () -> IkGui.text("hello")));

        Window window = IkGuiInternal.findWindowByName("Test");
        assertNotNull(window);
        assertEquals(100, window.position.x, DELTA);
        assertEquals(120, window.position.y, DELTA);
        assertEquals(200, window.size.x, DELTA);
        assertEquals(150, window.size.y, DELTA);
        assertTrue(window.active);

        var drawLists = context.mainViewport.drawData.drawLists;
        assertTrue(drawLists.contains(window.drawList));

        // The implicit debug window was never written to, so it is not rendered
        Window fallback = IkGuiInternal.findWindowByName("Debug##Default");
        assertNotNull(fallback);
        assertFalse(drawLists.contains(fallback.drawList));
        // But it still exists in the window list
        assertTrue(context.windowDisplayOrder.contains(fallback));
    }

    @Test
    void testEveryDrawCommandHasOneQuad() {
        // The shader looks up the command for each quad by index, so the counts must line up even
        // with clipped items and primitives like triangles (arrows, resize grips) and lines
        Runnable ui =
                () -> {
                    IkGui.setNextWindowPos(0, 0, Condition.FIRST_USE_EVER);
                    IkGui.setNextWindowSize(150, 100, Condition.FIRST_USE_EVER);
                    IkGui.begin("Commands", new IkBoolean(true));
                    for (int i = 0; i < 20; ++i) {
                        IkGui.button("Button " + i);
                        IkGui.bulletText("Bullet " + i);
                    }
                    IkGui.arrowButton("arrow", com.ikalagaming.graphics.gui.enums.Direction.LEFT);
                    IkGui.end();
                };
        frames(3, ui);
        // Hover the window so the resize grips and close button render their hovered state
        moveMouse(140, 90);
        frames(2, ui);

        var drawData = context.mainViewport.drawData;
        assertTrue(drawData.getDrawListCount() > 0);
        for (int i = 0; i < drawData.getDrawListCount(); ++i) {
            assertEquals(
                    drawData.getDrawListCommandCount(i) * 6,
                    drawData.getDrawListVertexCount(i),
                    "Draw list " + i + " has mismatched commands and quads");
        }
    }

    @Test
    void testNewWindowAutoFitsContents() {
        final Vector2f textSize = new Vector2f();
        Runnable ui =
                () -> {
                    IkGui.begin("Auto");
                    IkGui.text("0123456789");
                    IkGui.calcTextSize(textSize, "0123456789");
                    IkGui.end();
                };

        frame(ui);
        Window window = IkGuiInternal.findWindowByName("Auto");
        // New windows are hidden for a frame while we measure them
        assertTrue(window.hidden);
        assertFalse(context.mainViewport.drawData.drawLists.contains(window.drawList));

        frames(2, ui);
        assertFalse(window.hidden);
        assertTrue(context.mainViewport.drawData.drawLists.contains(window.drawList));

        final Vector2f padding = context.style.variable.windowPadding;
        final float titleBar =
                IkGui.getTextLineHeight() + context.style.variable.framePadding.y * 2;
        assertEquals(textSize.x + padding.x * 2, window.sizeFull.x, DELTA);
        assertEquals(titleBar + textSize.y + padding.y * 2, window.sizeFull.y, DELTA);
    }

    @Test
    void testButtonPressedOnClickRelease() {
        final boolean[] pressed = new boolean[1];
        final boolean[] active = new boolean[1];
        final Vector2f buttonMin = new Vector2f();
        final Vector2f buttonMax = new Vector2f();
        Runnable ui =
                fixedWindow(
                        "Buttons",
                        0,
                        0,
                        300,
                        200,
                        () -> {
                            pressed[0] = IkGui.button("Click me");
                            active[0] = IkGui.isItemActive();
                            IkGui.getItemRectMin(buttonMin);
                            IkGui.getItemRectMax(buttonMax);
                        });

        frames(2, ui);
        assertTrue(buttonMax.x > buttonMin.x);
        assertTrue(buttonMax.y > buttonMin.y);

        moveMouse((buttonMin.x + buttonMax.x) / 2, (buttonMin.y + buttonMax.y) / 2);
        frame(ui);
        assertFalse(pressed[0]);

        mouseDown();
        frame(ui);
        assertFalse(pressed[0], "Default buttons trigger on release");
        assertTrue(active[0]);

        mouseUp();
        frame(ui);
        assertTrue(pressed[0]);
        assertFalse(active[0]);

        frame(ui);
        assertFalse(pressed[0], "Press should only be reported once");
    }

    @Test
    void testButtonNotPressedWhenReleasedOutside() {
        final boolean[] pressed = new boolean[1];
        final Vector2f buttonMin = new Vector2f();
        final Vector2f buttonMax = new Vector2f();
        Runnable ui =
                fixedWindow(
                        "Buttons",
                        0,
                        0,
                        300,
                        200,
                        () -> {
                            pressed[0] |= IkGui.button("Click me");
                            IkGui.getItemRectMin(buttonMin);
                            IkGui.getItemRectMax(buttonMax);
                        });

        frames(2, ui);
        moveMouse(buttonMin.x + 1, buttonMin.y + 1);
        frame(ui);
        mouseDown();
        frame(ui);
        moveMouse(buttonMax.x + 50, buttonMax.y + 50);
        frame(ui);
        mouseUp();
        frames(2, ui);
        assertFalse(pressed[0]);
    }

    @Test
    void testDraggingWindowBackgroundMovesWindow() {
        Runnable ui = fixedWindow("Movable", 50, 50, 200, 200, () -> IkGui.text("Contents"));
        frames(2, ui);

        Window window = IkGuiInternal.findWindowByName("Movable");
        // Click in empty space in the window body
        moveMouse(60, 200);
        frame(ui);
        mouseDown();
        frame(ui);
        assertSame(window, context.windowMoving);

        moveMouse(110, 250);
        frame(ui);
        assertEquals(100, window.position.x, DELTA);
        assertEquals(100, window.position.y, DELTA);

        mouseUp();
        frames(2, ui);
        assertEquals(null, context.windowMoving);
        assertEquals(100, window.position.x, DELTA);
        assertEquals(100, window.position.y, DELTA);
    }

    @Test
    void testResizeGripResizesWindow() {
        Runnable ui = fixedWindow("Resizable", 0, 0, 200, 200, () -> IkGui.text("Contents"));
        frames(2, ui);

        Window window = IkGuiInternal.findWindowByName("Resizable");
        // The lower right resize grip
        moveMouse(198, 198);
        frame(ui);
        mouseDown();
        frame(ui);
        moveMouse(248, 228);
        frames(2, ui);
        mouseUp();
        frame(ui);

        assertEquals(250, window.sizeFull.x, 1.0f);
        assertEquals(230, window.sizeFull.y, 1.0f);
        assertEquals(0, window.position.x, DELTA);
        assertEquals(0, window.position.y, DELTA);
    }

    @Test
    void testClickingWindowBringsItToFront() {
        Runnable ui =
                () -> {
                    fixedWindow("A", 0, 0, 200, 200, () -> IkGui.text("A")).run();
                    fixedWindow("B", 100, 100, 200, 200, () -> IkGui.text("B")).run();
                };
        frames(3, ui);

        Window windowA = IkGuiInternal.findWindowByName("A");
        Window windowB = IkGuiInternal.findWindowByName("B");
        var order = context.windowDisplayOrder;
        assertTrue(order.indexOf(windowB) > order.indexOf(windowA));
        assertSame(windowB, context.navFocusedWindow);

        // Click on A, in a region not covered by B
        moveMouse(20, 150);
        frame(ui);
        mouseDown();
        frame(ui);
        mouseUp();
        frame(ui);

        assertTrue(order.indexOf(windowA) > order.indexOf(windowB));
        assertSame(windowA, context.navFocusedWindow);
        var drawLists = context.mainViewport.drawData.drawLists;
        assertTrue(drawLists.indexOf(windowA.drawList) > drawLists.indexOf(windowB.drawList));
    }

    @Test
    void testCloseButton() {
        final IkBoolean open = new IkBoolean(true);
        Runnable ui =
                () -> {
                    IkGui.setNextWindowPos(0, 0, Condition.FIRST_USE_EVER);
                    IkGui.setNextWindowSize(200, 200, Condition.FIRST_USE_EVER);
                    if (open.get()) {
                        IkGui.begin("Closable", open);
                        IkGui.end();
                    }
                };
        frames(2, ui);

        Window window = IkGuiInternal.findWindowByName("Closable");
        assertTrue(window.hasCloseButton);
        final float fontSize = IkGui.getTextLineHeight();
        final Vector2f framePadding = context.style.variable.framePadding;
        // The close button is at the right of the title bar
        final float closeX = window.size.x - window.borderSize - framePadding.x - fontSize / 2;
        final float closeY = framePadding.y + fontSize / 2;
        moveMouse(closeX, closeY);
        frame(ui);
        mouseDown();
        frame(ui);
        mouseUp();
        frame(ui);
        assertFalse(open.get());
    }

    @Test
    void testMouseWheelScrolls() {
        Runnable ui =
                fixedWindow(
                        "Scrolling",
                        0,
                        0,
                        200,
                        100,
                        () -> {
                            for (int i = 0; i < 30; ++i) {
                                IkGui.text("Line " + i);
                            }
                        });
        frames(3, ui);

        Window window = IkGuiInternal.findWindowByName("Scrolling");
        assertTrue(window.scrollMax.y > 0);
        assertTrue(window.scrollbarY);
        assertEquals(0, window.scrollPosition.y, DELTA);

        moveMouse(50, 50);
        frame(ui);
        context.io.addMouseWheelEvent(0, -1);
        frames(2, ui);
        assertTrue(window.scrollPosition.y > 0);
        assertTrue(window.scrollPosition.y <= window.scrollMax.y);
    }

    @Test
    void testSetScrollIsClamped() {
        Runnable ui =
                fixedWindow(
                        "Scrolling",
                        0,
                        0,
                        200,
                        100,
                        () -> {
                            for (int i = 0; i < 30; ++i) {
                                IkGui.text("Line " + i);
                            }
                        });
        frames(3, ui);
        Window window = IkGuiInternal.findWindowByName("Scrolling");

        frame(
                () -> {
                    ui.run();
                    IkGui.begin("Scrolling");
                    IkGui.setScrollY(100_000);
                    IkGui.end();
                });
        frame(ui);
        assertEquals(window.scrollMax.y, window.scrollPosition.y, DELTA);
    }

    @Test
    void testSameLineAndNewLineLayout() {
        final Vector2f firstMin = new Vector2f();
        final Vector2f firstMax = new Vector2f();
        final Vector2f secondMin = new Vector2f();
        final Vector2f thirdMin = new Vector2f();
        Runnable ui =
                fixedWindow(
                        "Layout",
                        0,
                        0,
                        300,
                        300,
                        () -> {
                            IkGui.text("first");
                            IkGui.getItemRectMin(firstMin);
                            IkGui.getItemRectMax(firstMax);
                            IkGui.sameLine();
                            IkGui.text("second");
                            IkGui.getItemRectMin(secondMin);
                            IkGui.text("third");
                            IkGui.getItemRectMin(thirdMin);
                        });
        frames(2, ui);

        final Vector2f spacing = context.style.variable.itemSpacing;
        assertEquals(firstMax.x + spacing.x, secondMin.x, DELTA);
        assertEquals(firstMin.y, secondMin.y, DELTA);
        assertEquals(firstMin.x, thirdMin.x, DELTA);
        assertEquals(firstMax.y + spacing.y, thirdMin.y, DELTA);
    }

    @Test
    void testGroupBoundingBox() {
        final Vector2f buttonMax = new Vector2f();
        final Vector2f groupMin = new Vector2f();
        final Vector2f groupMax = new Vector2f();
        final Vector2f firstMin = new Vector2f();
        Runnable ui =
                fixedWindow(
                        "Groups",
                        0,
                        0,
                        300,
                        300,
                        () -> {
                            IkGui.beginGroup();
                            IkGui.button("One");
                            IkGui.getItemRectMin(firstMin);
                            IkGui.button("Two two two");
                            IkGui.getItemRectMax(buttonMax);
                            IkGui.endGroup();
                            IkGui.getItemRectMin(groupMin);
                            IkGui.getItemRectMax(groupMax);
                        });
        frames(2, ui);

        assertEquals(firstMin.x, groupMin.x, DELTA);
        assertEquals(firstMin.y, groupMin.y, DELTA);
        assertEquals(buttonMax.x, groupMax.x, DELTA);
        assertEquals(buttonMax.y, groupMax.y, DELTA);
    }

    @Test
    void testChildWindowLayout() {
        final float[] cursorBefore = new float[1];
        final float[] cursorAfter = new float[1];
        final boolean[] childVisible = new boolean[1];
        Runnable ui =
                fixedWindow(
                        "Parent",
                        0,
                        0,
                        300,
                        300,
                        () -> {
                            cursorBefore[0] = IkGui.getCursorPosY();
                            childVisible[0] = IkGui.beginChild("child", 100, 50);
                            IkGui.text("In the child");
                            IkGui.endChild();
                            cursorAfter[0] = IkGui.getCursorPosY();
                        });
        frames(3, ui);

        assertTrue(childVisible[0]);
        assertEquals(
                cursorBefore[0] + 50 + context.style.variable.itemSpacing.y, cursorAfter[0], DELTA);

        Window parent = IkGuiInternal.findWindowByName("Parent");
        assertEquals(1, parent.childWindows.size());
        Window child = parent.childWindows.getFirst();
        assertSame(parent, child.parentWindow);
        assertSame(parent, child.rootWindow);
        assertEquals(100, child.size.x, DELTA);
        assertEquals(50, child.size.y, DELTA);

        var drawLists = context.mainViewport.drawData.drawLists;
        assertEquals(drawLists.indexOf(parent.drawList) + 1, drawLists.indexOf(child.drawList));
    }

    @Test
    void testCollapsedWindowSkipsItems() {
        final boolean[] visible = new boolean[1];
        Runnable ui =
                () -> {
                    IkGui.setNextWindowPos(0, 0, Condition.FIRST_USE_EVER);
                    IkGui.setNextWindowSize(200, 200, Condition.FIRST_USE_EVER);
                    visible[0] = IkGui.begin("Collapsible");
                    IkGui.end();
                };
        frames(2, ui);
        assertTrue(visible[0]);

        frame(
                () -> {
                    IkGui.setNextWindowCollapsed(true);
                    ui.run();
                });
        frame(ui);
        Window window = IkGuiInternal.findWindowByName("Collapsible");
        assertTrue(window.collapsed);
        assertFalse(visible[0]);
        assertEquals(window.titleBarHeight, window.size.y, DELTA);
    }

    @Test
    void testStyleVarPushPop() {
        frame(
                () -> {
                    IkGui.pushStyleVarFloat2(StyleVariable.WINDOW_PADDING, 20, 10);
                    assertEquals(20, context.style.variable.windowPadding.x, DELTA);
                    assertEquals(10, context.style.variable.windowPadding.y, DELTA);
                    IkGui.pushStyleVarFloat(StyleVariable.ALPHA, 0.5f);
                    assertEquals(0.5f, IkGui.getStyleVarFloat(StyleVariable.ALPHA), DELTA);
                    IkGui.popStyleVar(2);
                    assertEquals(8, context.style.variable.windowPadding.x, DELTA);
                    assertEquals(8, context.style.variable.windowPadding.y, DELTA);
                    assertEquals(1.0f, context.style.variable.alpha, DELTA);
                });
    }

    @Test
    void testIdStackIsPerWindow() {
        final int[] ids = new int[4];
        frame(
                () -> {
                    IkGui.begin("First");
                    ids[0] = IkGui.getID("item");
                    IkGui.pushID("scope");
                    ids[1] = IkGui.getID("item");
                    IkGui.popID();
                    IkGui.end();
                    IkGui.begin("Second");
                    ids[2] = IkGui.getID("item");
                    IkGui.end();
                    IkGui.begin("First");
                    ids[3] = IkGui.getID("item");
                    IkGui.end();
                });
        assertTrue(ids[0] != ids[1]);
        assertTrue(ids[0] != ids[2]);
        assertEquals(ids[0], ids[3]);
    }

    @Test
    void testUnbalancedStacksAreRecovered() {
        frame(
                () -> {
                    IkGui.begin("Forgot to end");
                    IkGui.pushStyleVarFloat(StyleVariable.ALPHA, 0.5f);
                });
        assertEquals(1.0f, context.style.variable.alpha, DELTA);
        assertTrue(context.windowStack.isEmpty());

        // The next frame still works
        frames(2, fixedWindow("Fine", 0, 0, 100, 100, () -> IkGui.text("ok")));
        assertTrue(IkGuiInternal.findWindowByName("Fine").active);
    }

    @Test
    void testDisabledItemsDoNotPress() {
        final boolean[] pressed = new boolean[1];
        final Vector2f buttonMin = new Vector2f();
        Runnable ui =
                fixedWindow(
                        "Disabled",
                        0,
                        0,
                        300,
                        200,
                        () -> {
                            IkGui.beginDisabled();
                            pressed[0] |= IkGui.button("Nope");
                            IkGui.getItemRectMin(buttonMin);
                            IkGui.endDisabled();
                        });
        frames(2, ui);
        moveMouse(buttonMin.x + 2, buttonMin.y + 2);
        frame(ui);
        mouseDown();
        frame(ui);
        mouseUp();
        frames(2, ui);
        assertFalse(pressed[0]);
        assertEquals(1.0f, context.style.variable.alpha, DELTA);
    }

    @Test
    void testIsWindowHoveredAndFocused() {
        final boolean[] hovered = new boolean[1];
        final boolean[] focused = new boolean[1];
        Runnable ui =
                fixedWindow(
                        "Query",
                        10,
                        10,
                        200,
                        200,
                        () -> {
                            hovered[0] = IkGui.isWindowHovered();
                            focused[0] = IkGui.isWindowFocused();
                        });
        frames(2, ui);
        assertTrue(focused[0], "New windows are focused when they appear");
        assertFalse(hovered[0]);

        moveMouse(100, 100);
        frames(2, ui);
        assertTrue(hovered[0]);
    }

    @Test
    void testAlwaysAutoResize() {
        final boolean[] wide = new boolean[1];
        Runnable ui =
                () -> {
                    IkGui.begin("AutoResize", null, WindowFlags.ALWAYS_AUTO_RESIZE);
                    IkGui.text(wide[0] ? "A much longer line of text" : "Short");
                    IkGui.end();
                };
        frames(3, ui);
        Window window = IkGuiInternal.findWindowByName("AutoResize");
        final float narrowWidth = window.size.x;

        wide[0] = true;
        frames(2, ui);
        assertTrue(window.size.x > narrowWidth);
    }

    @Test
    void testImageWidgets() {
        final TextureHandle texture = new TextureHandle(3, 0, 1, 1, Format.R8G8B8A8_UNORM);
        final boolean[] pressed = new boolean[1];
        final Vector2f imageMin = new Vector2f();
        final Vector2f imageMax = new Vector2f();
        final Vector2f buttonMin = new Vector2f();
        final Vector2f buttonMax = new Vector2f();
        Runnable ui =
                fixedWindow(
                        "Images",
                        0,
                        0,
                        300,
                        300,
                        () -> {
                            IkGui.image(texture, 64, 32);
                            IkGui.getItemRectMin(imageMin);
                            IkGui.getItemRectMax(imageMax);
                            pressed[0] |= IkGui.imageButton("button", texture, 16, 16);
                            IkGui.getItemRectMin(buttonMin);
                            IkGui.getItemRectMax(buttonMax);
                        });
        frames(2, ui);

        assertEquals(64, imageMax.x - imageMin.x, DELTA);
        assertEquals(32, imageMax.y - imageMin.y, DELTA);
        // Image buttons add frame padding around the image
        final Vector2f padding = context.style.variable.framePadding;
        assertEquals(16 + padding.x * 2, buttonMax.x - buttonMin.x, DELTA);
        assertEquals(16 + padding.y * 2, buttonMax.y - buttonMin.y, DELTA);
        // The same texture is only registered once per frame
        assertEquals(1, context.mainViewport.drawData.textures.size());
        assertSame(texture, context.mainViewport.drawData.textures.getFirst());

        moveMouse(buttonMin.x + 2, buttonMin.y + 2);
        frame(ui);
        mouseDown();
        frame(ui);
        mouseUp();
        frame(ui);
        assertTrue(pressed[0]);
    }

    @Test
    void testDemoWindowWithEverythingOpen() {
        final IkBoolean open = new IkBoolean(true);
        // Storage that reports every tree node and collapsing header as open
        final Storage openEverything =
                new Storage() {
                    @Override
                    public int getInt(int key, int defaultValue) {
                        return super.getInt(key, 1);
                    }
                };
        Runnable ui =
                () -> {
                    // Begin the demo window first, so the demo appends to it with our storage
                    IkGui.begin("IkGui Demo Window", open);
                    IkGui.setStateStorage(openEverything);
                    IkGuiDemo.showDemoWindowContents(open);
                    IkGui.end();
                };
        // A stand-in for the texture the rendering backend sets, so the images demo is shown
        context.io.fonts.texture = new TextureHandle(4, 0, 1, 1, Format.R8G8B8A8_UNORM);
        frames(4, ui);

        Window window = IkGuiInternal.findWindowByName("IkGui Demo Window");
        assertNotNull(window);
        assertTrue(window.active);
        // Lots of content, so the window scrolls
        assertTrue(window.scrollMax.y > 0);
        // Child windows from the layout section were submitted
        assertFalse(window.childWindows.isEmpty());
        assertTrue(context.windowStack.isEmpty());

        var drawData = context.mainViewport.drawData;
        for (int i = 0; i < drawData.getDrawListCount(); ++i) {
            assertEquals(
                    drawData.getDrawListCommandCount(i) * 6,
                    drawData.getDrawListVertexCount(i),
                    "Draw list " + i + " has mismatched commands and quads");
        }
        // None of the demo sections misuse the API
        assertFalse(
                context.debugLogBuffer.snapshot().stream()
                        .anyMatch(line -> line.contains("[ikgui-error]")),
                context.debugLogBuffer.getText());
    }

    @Test
    void testPushFontSizeKeepsFont() {
        final int[] sizes = new int[3];
        final Object[] fonts = new Object[2];
        frame(
                () -> {
                    IkGui.begin("Fonts");
                    sizes[0] = IkGui.getFontSize();
                    fonts[0] = context.font;
                    IkGui.pushFontSize(40);
                    sizes[1] = IkGui.getFontSize();
                    fonts[1] = context.font;
                    IkGui.popFont();
                    sizes[2] = IkGui.getFontSize();
                    IkGui.end();
                });
        assertEquals(40, sizes[1]);
        assertEquals(sizes[0], sizes[2]);
        assertSame(fonts[0], fonts[1]);
        assertTrue(context.fontStack.isEmpty());
    }

    @Test
    void testSeparatorTextLayout() {
        final Vector2f start = new Vector2f();
        final Vector2f labelSize = new Vector2f();
        final Vector2f sameLine = new Vector2f();
        final float[] itemRect = new float[4];
        final float[] workRight = new float[1];
        final int[] commandsAdded = new int[2];
        final Runnable ui =
                fixedWindow(
                        "Sections",
                        50,
                        50,
                        400,
                        300,
                        () -> {
                            final Window window = context.windowCurrent;
                            workRight[0] = window.rectWork.getRight();
                            IkGui.getCursorScreenPos(start);
                            IkGui.calcTextSize(labelSize, "Section");

                            int before = window.drawList.commandBuffer.position();
                            IkGui.separatorText("Section##hidden");
                            commandsAdded[0] =
                                    (window.drawList.commandBuffer.position() - before)
                                            / DrawData.SIZE_OF_DRAW_COMMAND;
                            itemRect[0] = context.lastItemData.rect.getLeft();
                            itemRect[1] = context.lastItemData.rect.getTop();
                            itemRect[2] = context.lastItemData.rect.getRight();
                            itemRect[3] = context.lastItemData.rect.getBottom();
                            IkGui.sameLine();
                            IkGui.getCursorScreenPos(sameLine);
                            IkGui.newLine();

                            // Without a label, it's just one line
                            before = window.drawList.commandBuffer.position();
                            IkGui.separatorText("##empty");
                            commandsAdded[1] =
                                    (window.drawList.commandBuffer.position() - before)
                                            / DrawData.SIZE_OF_DRAW_COMMAND;
                        });
        frames(3, ui);

        final var style = context.style.variable;
        // The item covers the full width, and is tall enough for the label and padding
        assertEquals(start.x, itemRect[0], DELTA);
        assertEquals(start.y, itemRect[1], DELTA);
        assertEquals(workRight[0], itemRect[2], DELTA);
        assertEquals(
                start.y
                        + Math.max(
                                labelSize.y + style.separatorTextPadding.y * 2.0f,
                                style.separatorTextBorderSize),
                itemRect[3],
                DELTA);
        // The "##" part is hidden, and the label is left aligned after the padding, so sameLine()
        // continues right after the label
        assertEquals(
                start.x + style.separatorTextPadding.x + labelSize.x + style.itemSpacing.x,
                sameLine.x,
                DELTA);
        // A line on either side of the label
        assertEquals(2, commandsAdded[0]);
        assertEquals(1, commandsAdded[1]);
    }

    @Test
    void testSeparatorTextAlignment() {
        final Vector2f labelSize = new Vector2f();
        final Vector2f sameLine = new Vector2f();
        final float[] workRight = new float[1];
        final int[] commandsAdded = new int[1];
        final Runnable ui =
                fixedWindow(
                        "Aligned",
                        50,
                        50,
                        400,
                        300,
                        () -> {
                            final Window window = context.windowCurrent;
                            workRight[0] = window.rectWork.getRight();
                            IkGui.calcTextSize(labelSize, "Right");
                            IkGui.pushStyleVarFloat2(StyleVariable.SEPARATOR_TEXT_ALIGN, 1, 0.5f);
                            final int before = window.drawList.commandBuffer.position();
                            IkGui.separatorText("Right");
                            commandsAdded[0] =
                                    (window.drawList.commandBuffer.position() - before)
                                            / DrawData.SIZE_OF_DRAW_COMMAND;
                            IkGui.popStyleVar();
                            IkGui.sameLine();
                            IkGui.getCursorScreenPos(sameLine);
                            IkGui.newLine();
                        });
        frames(3, ui);

        // Right aligned, the label ends at the padding from the right edge
        final var style = context.style.variable;
        assertEquals(
                workRight[0] - style.separatorTextPadding.x + style.itemSpacing.x,
                sameLine.x,
                DELTA);
        // The padding is wider than the item spacing, so a short line is still drawn after the
        // label
        assertEquals(2, commandsAdded[0]);
    }
}
