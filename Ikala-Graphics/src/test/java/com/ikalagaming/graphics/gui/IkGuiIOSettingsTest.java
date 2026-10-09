package com.ikalagaming.graphics.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.gui.data.Context;
import com.ikalagaming.graphics.gui.data.Window;
import com.ikalagaming.graphics.gui.enums.Condition;
import com.ikalagaming.graphics.gui.enums.Key;
import com.ikalagaming.graphics.gui.enums.MouseButton;
import com.ikalagaming.graphics.gui.flags.BackendFlags;
import com.ikalagaming.graphics.gui.flags.ConfigFlags;

import org.joml.Vector2f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests for the smaller io configuration settings. */
class IkGuiIOSettingsTest {
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

    private void click(float x, float y, Runnable ui) {
        context.io.addMousePosEvent(x, y);
        frame(ui);
        context.io.addMouseButtonEvent(MouseButton.LEFT, true);
        frame(ui);
        context.io.addMouseButtonEvent(MouseButton.LEFT, false);
        frames(2, ui);
    }

    private static final Runnable SCROLLING_WINDOW =
            () -> {
                IkGui.setNextWindowPos(0, 0, Condition.FIRST_USE_EVER);
                IkGui.setNextWindowSize(200, 200, Condition.FIRST_USE_EVER);
                IkGui.begin("Scrolling");
                for (int i = 0; i < 100; ++i) {
                    IkGui.text("Line " + i);
                }
                IkGui.end();
            };

    /**
     * Click on the vertical scrollbar track near the bottom of the window, below the grab.
     *
     * @param shift Whether to hold Shift while clicking.
     * @return The window.
     */
    private Window clickScrollbarTrack(boolean shift) {
        frames(3, SCROLLING_WINDOW);
        final Window window = IkGuiInternal.findWindowByName("Scrolling");
        assertTrue(window.scrollbarY);
        assertEquals(0.0f, window.scrollPosition.y, DELTA);
        // Middle of the scrollbar width, and above the resize grip
        final float x =
                window.position.x
                        + window.size.x
                        - context.style.variable.scrollbarSize * 0.5f
                        - window.borderSize;
        final float y = window.position.y + window.size.y - 50;
        if (shift) {
            context.io.addKeyEvent(Key.LEFT_SHIFT, true);
            frame(SCROLLING_WINDOW);
        }
        click(x, y, SCROLLING_WINDOW);
        return window;
    }

    @Test
    void testScrollbarScrollsByPage() {
        final Window window = clickScrollbarTrack(false);
        // One page is the visible height, far less than the maximum
        final float page = window.rectInner.getHeight();
        assertTrue(window.scrollPosition.y > 0);
        assertTrue(window.scrollPosition.y <= page + 1, "Scrolled " + window.scrollPosition.y);
        assertTrue(window.scrollPosition.y < window.scrollMax.y * 0.5f);
    }

    @Test
    void testScrollbarScrollsToClickedLocation() {
        context.io.configScrollbarScrollByPage = false;
        final Window window = clickScrollbarTrack(false);
        // The click was low on the track, so we scroll most of the way, far more than a page
        assertTrue(
                window.scrollPosition.y > window.scrollMax.y * 0.5f,
                "Scrolled " + window.scrollPosition.y + " of " + window.scrollMax.y);
    }

    @Test
    void testShiftClickScrollsToClickedLocation() {
        final Window window = clickScrollbarTrack(true);
        assertTrue(
                window.scrollPosition.y > window.scrollMax.y * 0.5f,
                "Scrolled " + window.scrollPosition.y + " of " + window.scrollMax.y);
    }

    /**
     * A window with a drag float.
     *
     * @param center Set to the center of the drag.
     * @param id Set to the ID of the drag.
     * @return The UI code.
     */
    private static Runnable dragWindow(Vector2f center, int[] id) {
        final float[] value = {1.0f};
        return () -> {
            IkGui.setNextWindowPos(0, 0, Condition.FIRST_USE_EVER);
            IkGui.setNextWindowSize(300, 100, Condition.FIRST_USE_EVER);
            IkGui.begin("Drags");
            IkGui.dragFloat("drag", value);
            id[0] = IkGui.getItemID();
            center.set(IkGui.getItemRectMin()).add(IkGui.getItemRectMax()).mul(0.5f);
            IkGui.end();
        };
    }

    @Test
    void testDragClickToInputText() {
        context.io.configDragClickToInputText = true;
        final Vector2f center = new Vector2f();
        final int[] id = {0};
        final Runnable ui = dragWindow(center, id);
        frames(3, ui);
        click(center.x, center.y, ui);
        assertTrue(IkGuiImplInputText.tempInputIsActive(id[0]));
    }

    @Test
    void testDragClickIsNotTextInputByDefault() {
        final Vector2f center = new Vector2f();
        final int[] id = {0};
        final Runnable ui = dragWindow(center, id);
        frames(3, ui);
        click(center.x, center.y, ui);
        assertFalse(IkGuiImplInputText.tempInputIsActive(id[0]));
    }

    /**
     * Focus a button with navigation, then press a gamepad button.
     *
     * @param gamepadKey The gamepad button to press.
     * @return How many times the button was pressed.
     */
    private int pressButtonWithGamepad(Key gamepadKey) {
        context.io.configInputTrickleEventQueue = false;
        context.io.configFlags |= ConfigFlags.NAV_ENABLE_GAMEPAD;
        context.io.backendFlags |= BackendFlags.HAS_GAMEPAD;
        final int[] pressed = {0};
        final Runnable ui =
                () -> {
                    IkGui.setNextWindowPos(0, 0, Condition.FIRST_USE_EVER);
                    IkGui.setNextWindowSize(300, 100, Condition.FIRST_USE_EVER);
                    IkGui.begin("Gamepad");
                    if (IkGui.button("Button")) {
                        pressed[0]++;
                    }
                    IkGui.end();
                };
        frames(3, ui);
        // Moving with the D-pad focuses the button
        press(Key.GAMEPAD_DPAD_DOWN, ui);
        assertNotEquals(0, context.navID);
        press(gamepadKey, ui);
        return pressed[0];
    }

    private void press(Key key, Runnable ui) {
        context.io.addKeyEvent(key, true);
        frame(ui);
        context.io.addKeyEvent(key, false);
        frame(ui);
    }

    @Test
    void testGamepadActivateButton() {
        assertEquals(1, pressButtonWithGamepad(Key.GAMEPAD_FACE_DOWN));
    }

    @Test
    void testSwappedGamepadActivateButton() {
        context.io.configNavSwapGamepadButtons = true;
        assertEquals(Key.GAMEPAD_FACE_RIGHT, IkGuiImplNav.navGamepadActivateKey());
        assertEquals(Key.GAMEPAD_FACE_DOWN, IkGuiImplNav.navGamepadCancelKey());
        assertEquals(1, pressButtonWithGamepad(Key.GAMEPAD_FACE_RIGHT));
    }

    @Test
    void testSwappedGamepadIgnoresTheOldButton() {
        context.io.configNavSwapGamepadButtons = true;
        assertEquals(0, pressButtonWithGamepad(Key.GAMEPAD_FACE_DOWN));
    }

    /**
     * Press Ctrl+C with a window focused.
     *
     * @return The clipboard text afterward.
     */
    private String copyWindowWithCtrlC() {
        final Runnable ui =
                () -> {
                    IkGui.setNextWindowPos(0, 0, Condition.FIRST_USE_EVER);
                    IkGui.setNextWindowSize(300, 100, Condition.FIRST_USE_EVER);
                    IkGui.begin("Copy me");
                    IkGui.text("Hello from the window");
                    IkGui.end();
                };
        frames(2, ui);
        IkGui.setWindowFocus("Copy me");
        frames(2, ui);
        IkGui.setClipboardText("unchanged");
        context.io.addKeyEvent(Key.LEFT_CTRL, true);
        frame(ui);
        context.io.addKeyEvent(Key.C, true);
        frame(ui);
        context.io.addKeyEvent(Key.C, false);
        context.io.addKeyEvent(Key.LEFT_CTRL, false);
        frames(3, ui);
        return IkGui.getClipboardText();
    }

    @Test
    void testCtrlCCopiesWindowContents() {
        context.io.configWindowsCopyContentsWithCtrlC = true;
        final String copied = copyWindowWithCtrlC();
        assertTrue(copied.contains("Hello from the window"), copied);
        // Logging stopped at the end of the window
        assertFalse(context.logEnabled);
    }

    @Test
    void testCtrlCDoesNothingByDefault() {
        assertEquals("unchanged", copyWindowWithCtrlC());
        assertNotEquals(true, context.logEnabled);
    }
}
