package com.ikalagaming.graphics.frontend.gui;

import static com.ikalagaming.graphics.frontend.gui.IkGuiTestContext.chord;
import static com.ikalagaming.graphics.frontend.gui.data.KeyRoutingData.KEY_OWNER_ANY;
import static com.ikalagaming.graphics.frontend.gui.data.KeyRoutingData.KEY_OWNER_NO_OWNER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.frontend.gui.data.IkBoolean;
import com.ikalagaming.graphics.frontend.gui.data.IkIO;
import com.ikalagaming.graphics.frontend.gui.data.IkInt;
import com.ikalagaming.graphics.frontend.gui.data.IkString;
import com.ikalagaming.graphics.frontend.gui.data.InputTextState;
import com.ikalagaming.graphics.frontend.gui.data.Window;
import com.ikalagaming.graphics.frontend.gui.enums.Key;
import com.ikalagaming.graphics.frontend.gui.enums.MouseButton;
import com.ikalagaming.graphics.frontend.gui.flags.BackendFlags;
import com.ikalagaming.graphics.frontend.gui.flags.ButtonFlags;
import com.ikalagaming.graphics.frontend.gui.flags.ChildFlags;
import com.ikalagaming.graphics.frontend.gui.flags.ColorEditFlags;
import com.ikalagaming.graphics.frontend.gui.flags.InputFlags;
import com.ikalagaming.graphics.frontend.gui.flags.KeyModFlags;
import com.ikalagaming.graphics.frontend.gui.flags.SelectableFlags;
import com.ikalagaming.graphics.frontend.gui.flags.WindowFlags;
import com.ikalagaming.graphics.frontend.gui.util.KeyChord;

import org.joml.Vector2f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

/**
 * Input tests ported from the Dear ImGui test suite (imgui_tests_inputs.cpp), which is MIT
 * licensed. Each test notes the name of the upstream test it is ported from.
 */
class IkGuiSuiteInputsTest {
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

    private static void assertPos(float x, float y, Vector2f pos) {
        assertEquals(x, pos.x, "x");
        assertEquals(y, pos.y, "y");
    }

    private static int mod(int mods) {
        return KeyChord.ofMods(mods);
    }

    /**
     * inputs_io_capture_on_release_not_owned: wantCaptureMouse on the frame a button that isn't
     * owned by the UI is released.
     */
    @Test
    void testIoCaptureOnReleaseNotOwned() {
        ctx.setGui(ctx::showApp);
        final var g = ctx.context;
        ctx.mouseMoveToVoid();
        ctx.mouseDown(MouseButton.LEFT);
        assertFalse(g.io.wantCaptureMouse);
        assertEquals(0, g.activeID);

        // Move over the window without checking what is hovered
        final Window demo = ctx.getWindowByRef("//" + IkGuiTestContext.DEMO);
        ctx.mouseMoveToPos(
                demo.position.x + demo.size.x * 0.5f, demo.position.y + demo.size.y * 0.5f);
        assertFalse(g.io.wantCaptureMouse);
        assertEquals(0, g.activeID);

        // Includes a frame
        ctx.mouseUp(MouseButton.LEFT);
        assertFalse(g.io.wantCaptureMouse);

        ctx.yieldFrame();
        assertTrue(g.io.wantCaptureMouse);
    }

    /** inputs_io_inputqueue: trickling the input queue. */
    @Test
    void testIoInputQueue() {
        final boolean[] wantTextInput = {false};
        ctx.setGui(
                () -> {
                    IkGui.setNextWindowPos(80, 80);
                    IkGui.setNextWindowSize(500, 500);
                    IkGui.begin(
                            "Test Window",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS | WindowFlags.NO_RESIZE);
                    // Simulate a text input without eating inputs
                    if (wantTextInput[0]) {
                        ctx.context.platformImeData.wantTextInput = true;
                        ctx.context.platformImeData.viewportID = IkGui.getMainViewport().id;
                    }
                    IkGui.end();
                });
        final IkIO io = ctx.context.io;
        ctx.yieldFrame();

        // Mouse position: 1 frame
        io.addMousePosEvent(100, 100);
        ctx.yieldFrame();
        assertPos(100, 100, io.mousePosition);

        // Mouse position x3: 1 frame
        io.addMousePosEvent(110, 110);
        io.addMousePosEvent(120, 120);
        io.addMousePosEvent(130, 130);
        ctx.yieldFrame();
        assertPos(130, 130, io.mousePosition);

        // Mouse position, button: 1 frame
        io.addMousePosEvent(140, 140);
        io.addMouseButtonEvent(MouseButton.LEFT, true);
        ctx.yieldFrame();
        assertPos(140, 140, io.mousePosition);
        assertTrue(io.mouseDown[0]);
        io.addMouseButtonEvent(MouseButton.LEFT, false);
        ctx.yieldFrame();

        // Mouse button | position: 2 frames
        io.addMouseButtonEvent(MouseButton.LEFT, true);
        io.addMousePosEvent(150, 150);
        ctx.yieldFrame();
        assertPos(140, 140, io.mousePosition);
        assertTrue(io.mouseDown[0]);
        ctx.yieldFrame();
        assertPos(150, 150, io.mousePosition);
        assertTrue(io.mouseDown[0]);
        io.addMouseButtonEvent(MouseButton.LEFT, false);
        ctx.yieldFrame();

        // Mouse position, button | position: 2 frames
        io.addMousePosEvent(100, 100);
        io.addMouseButtonEvent(MouseButton.LEFT, true);
        io.addMousePosEvent(110, 110);
        ctx.yieldFrame();
        assertPos(100, 100, io.mousePosition);
        assertTrue(io.mouseDown[0]);
        ctx.yieldFrame();
        assertPos(110, 110, io.mousePosition);
        assertTrue(io.mouseDown[0]);
        io.addMouseButtonEvent(MouseButton.LEFT, false);
        ctx.yieldFrame();

        // Mouse button down | up: 2 frames
        io.addMouseButtonEvent(MouseButton.LEFT, true);
        io.addMouseButtonEvent(MouseButton.LEFT, false);
        ctx.yieldFrame();
        assertTrue(io.mouseDown[0]);
        ctx.yieldFrame();
        assertFalse(io.mouseDown[0]);
        ctx.yieldFrame();

        // Mouse buttons down 0, down 1, up 0, up 1: 2 frames
        io.addMouseButtonEvent(MouseButton.LEFT, true);
        io.addMouseButtonEvent(MouseButton.RIGHT, true);
        io.addMouseButtonEvent(MouseButton.LEFT, false);
        io.addMouseButtonEvent(MouseButton.RIGHT, false);
        ctx.yieldFrame();
        assertTrue(io.mouseDown[0]);
        assertTrue(io.mouseDown[1]);
        ctx.yieldFrame();
        assertFalse(io.mouseDown[0]);
        assertFalse(io.mouseDown[1]);
        ctx.yieldFrame();

        // Mouse button down | up | down: 3 frames
        io.addMouseButtonEvent(MouseButton.LEFT, true);
        io.addMouseButtonEvent(MouseButton.LEFT, false);
        io.addMouseButtonEvent(MouseButton.LEFT, true);
        ctx.yieldFrame();
        assertTrue(io.mouseDown[0]);
        ctx.yieldFrame();
        assertFalse(io.mouseDown[0]);
        ctx.yieldFrame();
        assertTrue(io.mouseDown[0]);
        io.addMouseButtonEvent(MouseButton.LEFT, false);
        ctx.yieldFrame();

        // Mouse button double click: 4 frames
        // Make sure the previous clicks aren't counted
        ctx.sleep(1.0f);
        io.addMouseButtonEvent(MouseButton.LEFT, true);
        io.addMouseButtonEvent(MouseButton.LEFT, false);
        io.addMouseButtonEvent(MouseButton.LEFT, true);
        io.addMouseButtonEvent(MouseButton.LEFT, false);
        ctx.yieldFrame();
        assertTrue(io.mouseDown[0]);
        ctx.yieldFrame();
        assertFalse(io.mouseDown[0]);
        ctx.yieldFrame();
        assertTrue(io.mouseDown[0]);
        assertTrue(IkGui.isMouseDoubleClicked(MouseButton.LEFT));
        ctx.yieldFrame();
        assertFalse(io.mouseDown[0]);

        // Mouse position | wheel: 2 frames
        io.addMousePosEvent(100, 100);
        io.addMouseWheelEvent(0.0f, 1.0f);
        ctx.yieldFrame();
        assertPos(100, 100, io.mousePosition);
        assertEquals(0.0f, io.mouseWheel);
        ctx.yieldFrame();
        assertEquals(1.0f, io.mouseWheel);
        ctx.yieldFrame();
        assertEquals(0.0f, io.mouseWheel);

        // Mouse wheel, wheel: 1 frame
        io.addMouseWheelEvent(0.0f, 1.0f);
        io.addMouseWheelEvent(0.0f, 1.0f);
        ctx.yieldFrame();
        assertEquals(2.0f, io.mouseWheel);
        ctx.yieldFrame();
        assertEquals(0.0f, io.mouseWheel);

        // Mouse wheel | position: 2 frames
        io.addMouseWheelEvent(0.0f, 2.0f);
        io.addMousePosEvent(110, 110);
        ctx.yieldFrame();
        assertPos(100, 100, io.mousePosition);
        assertEquals(2.0f, io.mouseWheel);
        ctx.yieldFrame();
        assertPos(110, 110, io.mousePosition);
        assertEquals(0.0f, io.mouseWheel);
        ctx.yieldFrame();

        // Mouse wheel | button: 2 frames
        io.addMouseWheelEvent(0.0f, 2.0f);
        io.addMouseButtonEvent(MouseButton.RIGHT, true);
        ctx.yieldFrame();
        assertEquals(2.0f, io.mouseWheel);
        assertFalse(io.mouseDown[1]);
        ctx.yieldFrame();
        assertEquals(0.0f, io.mouseWheel);
        assertTrue(io.mouseDown[1]);
        io.addMouseButtonEvent(MouseButton.RIGHT, false);
        ctx.yieldFrame();
        assertFalse(io.mouseDown[1]);

        // Mouse button | wheel: 2 frames
        io.addMouseButtonEvent(MouseButton.RIGHT, true);
        io.addMouseWheelEvent(0.0f, 3.0f);
        ctx.yieldFrame();
        assertTrue(io.mouseDown[1]);
        assertEquals(0.0f, io.mouseWheel);
        ctx.yieldFrame();
        assertEquals(3.0f, io.mouseWheel);
        assertTrue(io.mouseDown[1]);
        ctx.yieldFrame();
        io.addMouseButtonEvent(MouseButton.RIGHT, false);
        ctx.yieldFrame();
        assertFalse(io.mouseDown[1]);

        // Mouse position, key: 1 frame
        io.addMousePosEvent(120, 120);
        io.addKeyEvent(Key.F, true);
        ctx.yieldFrame();
        assertPos(120, 120, io.mousePosition);
        assertTrue(IkGui.isKeyPressed(Key.F));
        ctx.yieldFrame();
        assertFalse(IkGui.isKeyPressed(Key.F));
        assertTrue(IkGui.isKeyDown(Key.F));
        io.addKeyEvent(Key.F, false);
        ctx.yieldFrame();
        assertFalse(IkGui.isKeyDown(Key.F));

        // macOS: Ctrl + left click is aliased to a right click
        for (int isOSX = 0; isOSX < 2; ++isOSX) {
            io.configMacOSXBehaviors = isOSX != 0;
            ctx.yieldFrame();
            if (io.configMacOSXBehaviors) {
                // Ctrl is swapped to super for raw inputs
                io.addKeyEvent(Key.MOD_CTRL, true);
                io.addMouseButtonEvent(MouseButton.LEFT, true);
                ctx.yieldFrame();
                assertFalse(io.mouseDown[0]);
                // Aliased
                assertTrue(io.mouseDown[1]);
                ctx.yieldFrames(3);
                assertFalse(io.mouseDown[0]);
                // Still aliased
                assertTrue(io.mouseDown[1]);
                io.addKeyEvent(Key.MOD_CTRL, false);
                ctx.yieldFrame();
                // Still aliased
                assertTrue(io.mouseDown[1]);
                io.addMouseButtonEvent(MouseButton.LEFT, false);
                ctx.yieldFrame();
                // Still aliased for the release
                assertFalse(io.mouseDown[1]);
            } else {
                io.addKeyEvent(Key.MOD_SUPER, true);
                io.addMouseButtonEvent(MouseButton.LEFT, true);
                ctx.yieldFrame();
                assertTrue(io.mouseDown[0]);
                // Not aliased
                assertFalse(io.mouseDown[1]);
                ctx.yieldFrames(3);
                assertTrue(io.mouseDown[0]);
                assertFalse(io.mouseDown[1]);
                io.addMouseButtonEvent(MouseButton.LEFT, false);
                ctx.yieldFrame();
                assertFalse(io.mouseDown[0]);
                io.addKeyEvent(Key.MOD_SUPER, false);
            }
        }
        io.configMacOSXBehaviors = false;
        ctx.yieldFrame();

        // Key | mouse position: 2 frames
        io.addKeyEvent(Key.G, true);
        io.addMousePosEvent(130, 130);
        ctx.yieldFrame();
        assertTrue(IkGui.isKeyPressed(Key.G));
        assertNotEquals(130.0f, io.mousePosition.x);
        ctx.yieldFrame();
        assertFalse(IkGui.isKeyPressed(Key.G));
        assertTrue(IkGui.isKeyDown(Key.G));
        assertPos(130, 130, io.mousePosition);
        io.addKeyEvent(Key.G, false);
        ctx.yieldFrame();
        assertFalse(IkGui.isKeyDown(Key.G));

        // Key down | up | down: 3 frames
        io.addKeyEvent(Key.H, true);
        io.addKeyEvent(Key.H, false);
        io.addKeyEvent(Key.H, true);
        ctx.yieldFrame();
        assertTrue(IkGui.isKeyDown(Key.H));
        ctx.yieldFrame();
        assertFalse(IkGui.isKeyDown(Key.H));
        ctx.yieldFrame();
        assertTrue(IkGui.isKeyDown(Key.H));
        io.addKeyEvent(Key.H, false);
        ctx.yieldFrame();
        assertFalse(IkGui.isKeyDown(Key.H));

        // Key down, other key down: 1 frame
        io.addKeyEvent(Key.I, true);
        io.addKeyEvent(Key.J, true);
        ctx.yieldFrame();
        assertTrue(IkGui.isKeyDown(Key.I));
        assertTrue(IkGui.isKeyDown(Key.J));
        io.addKeyEvent(Key.I, false);
        io.addKeyEvent(Key.J, false);
        ctx.yieldFrame();
        assertFalse(IkGui.isKeyDown(Key.I));
        assertFalse(IkGui.isKeyDown(Key.J));

        // Character: 1 frame
        IkGuiInternal.clearActiveID();
        io.addInputCharacter('L');
        ctx.yieldFrame();
        assertEquals("L", io.inputQueueCharacters.toString());
        ctx.yieldFrame();
        assertEquals("", io.inputQueueCharacters.toString());

        // With and without a simulated active text input
        for (int step = 0; step < 2; ++step) {
            final boolean isInputTextActive = step == 1;
            final String description = isInputTextActive ? "text input" : "no text input";
            wantTextInput[0] = isInputTextActive;
            ctx.yieldFrame();

            // Key down | maybe-char character: 1 frame
            io.addKeyEvent(Key.K, true);
            io.addInputCharacter('L');
            ctx.yieldFrame();
            assertTrue(IkGui.isKeyDown(Key.K), description);
            assertEquals("L", io.inputQueueCharacters.toString(), description);
            io.addKeyEvent(Key.K, false);
            ctx.yieldFrame();
            assertEquals("", io.inputQueueCharacters.toString(), description);

            // Character | maybe-char key: 1 frame
            io.addInputCharacter('L');
            io.addKeyEvent(Key.K, true);
            ctx.yieldFrame();
            assertEquals("L", io.inputQueueCharacters.toString(), description);
            assertTrue(IkGui.isKeyDown(Key.K), description);
            ctx.yieldFrame();
            assertEquals("", io.inputQueueCharacters.toString(), description);
            io.addKeyEvent(Key.K, false);
            ctx.yieldFrame();

            // Non-char key down | character: 2 frames with a text input, 1 otherwise
            io.addKeyEvent(Key.ARROW_LEFT, true);
            io.addInputCharacter('L');
            ctx.yieldFrame();
            assertTrue(IkGui.isKeyDown(Key.ARROW_LEFT), description);
            if (isInputTextActive) {
                assertEquals("", io.inputQueueCharacters.toString(), description);
                ctx.yieldFrame();
            }
            assertEquals("L", io.inputQueueCharacters.toString(), description);
            io.addKeyEvent(Key.ARROW_LEFT, false);
            ctx.yieldFrame();
            assertEquals("", io.inputQueueCharacters.toString(), description);

            // Character | non-char key: 2 frames with a text input, 1 otherwise
            io.addInputCharacter('L');
            io.addKeyEvent(Key.ARROW_LEFT, true);
            ctx.yieldFrame();
            assertEquals("L", io.inputQueueCharacters.toString(), description);
            if (isInputTextActive) {
                assertFalse(IkGui.isKeyDown(Key.ARROW_LEFT), description);
                ctx.yieldFrame();
                assertEquals("", io.inputQueueCharacters.toString(), description);
            }
            assertTrue(IkGui.isKeyDown(Key.ARROW_LEFT), description);
            ctx.yieldFrame();
            io.addKeyEvent(Key.ARROW_LEFT, false);
            ctx.yieldFrame();
        }

        // Key, modifiers: 1 frame
        assertEquals(KeyModFlags.NONE, io.keyMods);
        io.addKeyEvent(Key.K, true);
        io.addKeyEvent(Key.MOD_CTRL, true);
        io.addKeyEvent(Key.MOD_SHIFT, true);
        ctx.yieldFrame();
        assertTrue(IkGui.isKeyDown(Key.K));
        assertEquals(KeyModFlags.CTRL | KeyModFlags.SHIFT, io.keyMods);
        io.addKeyEvent(Key.K, false);
        io.addKeyEvent(Key.MOD_CTRL, false);
        io.addKeyEvent(Key.MOD_SHIFT, false);
        ctx.yieldFrame();

        // Modifiers, key: 1 frame
        assertEquals(KeyModFlags.NONE, io.keyMods);
        io.addKeyEvent(Key.MOD_CTRL, true);
        io.addKeyEvent(Key.MOD_SHIFT, true);
        io.addKeyEvent(Key.K, true);
        ctx.yieldFrame();
        assertTrue(IkGui.isKeyDown(Key.K));
        assertEquals(KeyModFlags.CTRL | KeyModFlags.SHIFT, io.keyMods);
        io.addKeyEvent(Key.K, false);
        io.addKeyEvent(Key.MOD_CTRL, false);
        io.addKeyEvent(Key.MOD_SHIFT, false);
        ctx.yieldFrame();

        // Modifier | same modifier: 2 frames
        assertEquals(KeyModFlags.NONE, io.keyMods);
        io.addKeyEvent(Key.MOD_CTRL, true);
        io.addKeyEvent(Key.MOD_CTRL, false);
        ctx.yieldFrame();
        assertEquals(KeyModFlags.CTRL, io.keyMods);
        ctx.yieldFrame();
        assertEquals(KeyModFlags.NONE, io.keyMods);

        // Modifiers, different modifiers: 1 frame
        io.addKeyEvent(Key.MOD_CTRL, true);
        io.addKeyEvent(Key.MOD_SHIFT, true);
        io.addKeyEvent(Key.MOD_CTRL, false);
        io.addKeyEvent(Key.MOD_SHIFT, false);
        ctx.yieldFrame();
        assertEquals(KeyModFlags.CTRL | KeyModFlags.SHIFT, io.keyMods);
        ctx.yieldFrame();
        assertEquals(KeyModFlags.NONE, io.keyMods);

        // Mouse position, modifier: 1 frame
        io.addMousePosEvent(200, 200);
        io.addKeyEvent(Key.MOD_CTRL, true);
        ctx.yieldFrame();
        assertPos(200, 200, io.mousePosition);
        assertEquals(KeyModFlags.CTRL, io.keyMods);
        io.addKeyEvent(Key.MOD_CTRL, false);
        ctx.yieldFrame();
        assertEquals("", io.inputQueueCharacters.toString());
        assertEquals(KeyModFlags.NONE, io.keyMods);

        // Modifier | mouse position: 2 frames
        io.addKeyEvent(Key.MOD_CTRL, true);
        io.addMousePosEvent(210, 210);
        io.addKeyEvent(Key.MOD_CTRL, false);
        ctx.yieldFrame();
        assertPos(200, 200, io.mousePosition);
        assertEquals(KeyModFlags.CTRL, io.keyMods);
        ctx.yieldFrame();
        assertPos(210, 210, io.mousePosition);
        assertEquals(KeyModFlags.NONE, io.keyMods);

        // Mouse position | character: 2 frames
        io.addMousePosEvent(220, 220);
        io.addInputCharacter('B');
        ctx.yieldFrame();
        assertPos(220, 220, io.mousePosition);
        assertEquals("", io.inputQueueCharacters.toString());
        ctx.yieldFrame();
        assertEquals("B", io.inputQueueCharacters.toString());
        ctx.yieldFrame();
        assertEquals("", io.inputQueueCharacters.toString());

        // Analog values with trickling
        io.backendFlags |= BackendFlags.HAS_GAMEPAD;
        io.addKeyAnalogEvent(Key.GAMEPAD_LSTICK_UP, false, 0.0f);
        ctx.yieldFrame();
        assertFalse(IkGui.isKeyPressed(Key.GAMEPAD_LSTICK_UP));
        io.addKeyAnalogEvent(Key.GAMEPAD_LSTICK_UP, true, 0.5f);
        ctx.yieldFrame();
        assertTrue(IkGui.isKeyPressed(Key.GAMEPAD_LSTICK_UP));
        assertEquals(0.5f, io.keysAnalogValue[Key.GAMEPAD_LSTICK_UP.ordinal()]);
        io.addKeyAnalogEvent(Key.GAMEPAD_LSTICK_UP, true, 0.6f);
        io.addKeyAnalogEvent(Key.GAMEPAD_LSTICK_UP, true, 0.7f);
        io.addKeyAnalogEvent(Key.GAMEPAD_LSTICK_UP, true, 0.8f);
        ctx.yieldFrame();
        assertTrue(IkGui.isKeyDown(Key.GAMEPAD_LSTICK_UP));
        assertEquals(0.8f, io.keysAnalogValue[Key.GAMEPAD_LSTICK_UP.ordinal()]);
        // Analog changes alone don't block other events
        io.addMousePosEvent(0, 0);
        io.addKeyAnalogEvent(Key.GAMEPAD_LSTICK_UP, true, 0.81f);
        io.addMousePosEvent(10, 10);
        io.addMousePosEvent(20, 20);
        io.addKeyAnalogEvent(Key.GAMEPAD_LSTICK_UP, true, 0.82f);
        ctx.yieldFrame();
        assertPos(20, 20, io.mousePosition);
        assertTrue(IkGui.isKeyDown(Key.GAMEPAD_LSTICK_UP));
        assertEquals(0.82f, io.keysAnalogValue[Key.GAMEPAD_LSTICK_UP.ordinal()]);
    }

    /** inputs_io_inputqueue_filtering: duplicate events are filtered out. */
    @Test
    void testIoInputQueueFiltering() {
        ctx.setGui(() -> {});
        final IkIO io = ctx.context.io;
        ctx.yieldFrame();

        io.addMousePosEvent(-Float.MAX_VALUE, -Float.MAX_VALUE);
        ctx.yieldFrame();
        assertEquals(0, io.getEventQueueSize());

        Vector2f voidPos = ctx.findVoidPosition();
        if (voidPos == null) {
            voidPos = new Vector2f(1, 1);
        }

        io.addKeyEvent(Key.SPACE, true);
        io.addMousePosEvent(voidPos.x, voidPos.y);
        io.addMouseButtonEvent(MouseButton.RIGHT, true);
        io.addMouseWheelEvent(0.0f, 1.0f);
        assertEquals(4, io.getEventQueueSize());

        // These would interfere
        io.addMouseButtonEvent(MouseButton.RIGHT, true);
        io.addMousePosEvent(voidPos.x, voidPos.y);
        io.addKeyEvent(Key.SPACE, true);
        io.addMouseWheelEvent(0.0f, 0.0f);
        assertEquals(4, io.getEventQueueSize());

        ctx.yieldFrames(4);
        assertEquals(0, io.getEventQueueSize());
        assertFalse(io.appFocusLost);
        io.addFocusEvent(true);
        io.addFocusEvent(true);
        assertEquals(0, io.getEventQueueSize());
    }

    /**
     * Count presses of a key with each repeat flag.
     *
     * @param counters The counters to add to.
     * @param key The key.
     */
    private static void countRepeats(int[] counters, Key key) {
        final int[] flags = {
            InputFlags.NONE,
            InputFlags.REPEAT,
            InputFlags.INTERNAL_REPEAT_UNTIL_RELEASE,
            InputFlags.INTERNAL_REPEAT_UNTIL_KEY_MODS_CHANGE,
            InputFlags.INTERNAL_REPEAT_UNTIL_KEY_MODS_CHANGE_FROM_NONE,
            InputFlags.INTERNAL_REPEAT_UNTIL_OTHER_KEY_PRESS
        };
        for (int i = 0; i < flags.length; ++i) {
            counters[i] += IkGuiImplKeys.isKeyPressed(key, flags[i], KEY_OWNER_ANY) ? 1 : 0;
        }
    }

    /** inputs_repeat_key: key and key chord repeats, and the repeat until flags. */
    @Test
    void testRepeatKey() {
        final int[] counters = new int[6];
        final Key key = Key.A;
        ctx.setGui(
                () -> {
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    countRepeats(counters, key);
                    for (int counter : counters) {
                        IkGui.text(String.valueOf(counter));
                    }
                    IkGui.end();
                });
        final IkIO io = ctx.context.io;

        ctx.keyPress(key);
        for (int n = 0; n < 6; ++n) {
            assertEquals(1, counters[n], "counter " + n);
        }
        Arrays.fill(counters, 0);

        final float duration = 0.8f;
        final long durationMs = (long) (duration * 1000);
        final int repeatCountForDuration =
                1
                        + IkGuiInternal.calcTypematicRepeatAmount(
                                0, durationMs, io.keyRepeatDelay, io.keyRepeatRate);
        final int repeatCountForDurationX2 =
                1
                        + IkGuiInternal.calcTypematicRepeatAmount(
                                0, durationMs * 2, io.keyRepeatDelay, io.keyRepeatRate);

        ctx.keyHold(KeyChord.of(key), duration);
        assertEquals(1, counters[0]);
        for (int n = 1; n < 6; ++n) {
            assertEquals(repeatCountForDuration, counters[n], "hold, counter " + n);
        }
        Arrays.fill(counters, 0);

        // Hold the key and press Ctrl mid-way
        ctx.keySetEx(KeyChord.of(key), true, duration);
        for (int n = 1; n < 6; ++n) {
            assertEquals(repeatCountForDuration, counters[n], "key, counter " + n);
        }
        ctx.keySetEx(mod(KeyModFlags.CTRL), true, duration);
        assertEquals(repeatCountForDurationX2, counters[2]);
        assertEquals(repeatCountForDuration, counters[3]);
        assertEquals(repeatCountForDuration, counters[4]);
        assertEquals(repeatCountForDuration, counters[5]);
        ctx.keyUp(chord(KeyModFlags.CTRL, key));
        Arrays.fill(counters, 0);

        // Hold Ctrl+key and release Ctrl mid-way. RepeatUntilKeyModsChangeFromNone behaves
        // differently in this direction, which is useful for some shortcuts.
        ctx.keySetEx(chord(KeyModFlags.CTRL, key), true, duration);
        for (int n = 1; n < 6; ++n) {
            assertEquals(repeatCountForDuration, counters[n], "ctrl+key, counter " + n);
        }
        ctx.keySetEx(mod(KeyModFlags.CTRL), false, duration);
        assertEquals(repeatCountForDurationX2, counters[2]);
        assertEquals(repeatCountForDuration, counters[3]);
        assertEquals(repeatCountForDurationX2, counters[4]);
        assertEquals(repeatCountForDurationX2, counters[5]);
        ctx.keyUp(key);
        Arrays.fill(counters, 0);

        // Hold the key and press another key
        ctx.keySetEx(KeyChord.of(key), true, duration);
        for (int n = 1; n < 6; ++n) {
            assertEquals(repeatCountForDuration, counters[n], "other key, counter " + n);
        }
        ctx.keySetEx(KeyChord.of(Key.ARROW_UP), true, duration);
        assertEquals(repeatCountForDurationX2, counters[2]);
        assertEquals(repeatCountForDurationX2, counters[3]);
        assertEquals(repeatCountForDurationX2, counters[4]);
        assertEquals(repeatCountForDuration, counters[5]);
        ctx.keyUp(key);
        ctx.keyUp(Key.ARROW_UP);
    }

    /** inputs_repeat_typematic. */
    @Test
    void testRepeatTypematic() {
        // Times in milliseconds, where upstream uses seconds
        // Regular repeat delay and rate, triggers at 0, 1000, 1200, 1400, ...
        assertEquals(1, IkGuiInternal.calcTypematicRepeatAmount(0, 0, 1000, 200));
        assertEquals(0, IkGuiInternal.calcTypematicRepeatAmount(0, 990, 1000, 200));
        assertEquals(1, IkGuiInternal.calcTypematicRepeatAmount(990, 1000, 1000, 200));
        assertEquals(1, IkGuiInternal.calcTypematicRepeatAmount(990, 1010, 1000, 200));
        assertEquals(3, IkGuiInternal.calcTypematicRepeatAmount(990, 1410, 1000, 200));
        assertEquals(2, IkGuiInternal.calcTypematicRepeatAmount(1010, 1410, 1000, 200));

        // Triggers at 0, 1100, 1300, 1500, ...
        assertEquals(0, IkGuiInternal.calcTypematicRepeatAmount(990, 1010, 1100, 200));

        // Triggers at 0, 100, 1100, 2100, ...
        assertEquals(0, IkGuiInternal.calcTypematicRepeatAmount(990, 1010, 100, 1000));
        assertEquals(1, IkGuiInternal.calcTypematicRepeatAmount(990, 1110, 100, 1000));

        // No repeat delay, triggers at 0, 200, 400, 600, ...
        assertEquals(1, IkGuiInternal.calcTypematicRepeatAmount(0, 0, 0, 200));
        assertEquals(1, IkGuiInternal.calcTypematicRepeatAmount(190, 200, 0, 200));
        assertEquals(0, IkGuiInternal.calcTypematicRepeatAmount(200, 200, 0, 200));
        assertEquals(5, IkGuiInternal.calcTypematicRepeatAmount(190, 1010, 0, 200));

        // No repeat rate, triggers at 0, 1000
        assertEquals(1, IkGuiInternal.calcTypematicRepeatAmount(0, 0, 1000, 0));
        assertEquals(1, IkGuiInternal.calcTypematicRepeatAmount(990, 1010, 1000, 0));
        assertEquals(0, IkGuiInternal.calcTypematicRepeatAmount(1010, 2000, 1000, 0));

        // No repeat delay or rate, triggers at 0
        assertEquals(1, IkGuiInternal.calcTypematicRepeatAmount(0, 0, 0, 0));
        assertEquals(0, IkGuiInternal.calcTypematicRepeatAmount(10, 1010, 0, 0));
    }

    /** inputs_mouse_stationary_timer. */
    @Test
    void testMouseStationaryTimer() {
        ctx.setGui(() -> {});
        final var g = ctx.context;
        final var viewport = IkGui.getMainViewport();
        final float x = viewport.position.x + viewport.size.x * 0.5f;
        final float y = viewport.position.y + viewport.size.y * 0.5f;
        ctx.mouseMoveToPos(x, y);
        ctx.mouseMoveToPos(x + 10, y + 10);
        assertTrue(g.io.mouseStationaryTimer <= g.io.deltaTime * 3);
        ctx.sleep(5.0f);
        assertTrue(g.io.mouseStationaryTimer >= 5000);
    }

    /** inputs_owner_basic_1: setKeyOwner() and testKeyOwner(). */
    @Test
    void testOwnerBasic1() {
        final IkBoolean steal = new IkBoolean(false);
        ctx.setGui(
                () -> {
                    IkGui.begin(
                            "Test Window",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS | WindowFlags.MENU_BAR);
                    if (IkGui.beginMenuBar()) {
                        IkGui.menuItem("MenuItem");
                        IkGui.endMenuBar();
                    }
                    IkGui.button("Button Up");
                    IkGui.checkbox("Steal Key.HOME", steal);
                    IkGui.colorButton(
                            "hello1",
                            new float[] {0.4f, 0.4f, 0.8f, 1.0f},
                            ColorEditFlags.NO_TOOLTIP | ColorEditFlags.NO_DRAG_DROP,
                            128,
                            128);
                    if (steal.get()) {
                        IkGuiInternal.setKeyOwner(Key.HOME, IkGui.getItemID(), 0);
                    }
                    IkGui.button("Button Down");
                    IkGui.end();
                });
        final var g = ctx.context;
        ctx.setRef("Test Window");
        assertTrue(IkGuiInternal.testKeyOwner(Key.HOME, KEY_OWNER_NO_OWNER));
        ctx.itemCheck("Steal Key.HOME");

        assertFalse(IkGuiInternal.testKeyOwner(Key.HOME, KEY_OWNER_NO_OWNER));
        assertTrue(IkGuiInternal.testKeyOwner(Key.HOME, ctx.getID("hello1")));
        assertTrue(IkGuiInternal.testKeyOwner(Key.END, ctx.getID("hello1")));
        assertEquals(KEY_OWNER_NO_OWNER, g.keysOwnerData[Key.END.ordinal()].ownerCurr);
        ctx.keyPress(Key.END);
        assertEquals(ctx.getID("Button Down"), g.navID);
        ctx.keyPress(Key.HOME);
        assertEquals(ctx.getID("Button Down"), g.navID);

        ctx.itemUncheck("Steal Key.HOME");
        ctx.keyPress(Key.HOME);
        assertEquals(ctx.getID("Button Up"), g.navID);
    }

    /** inputs_owner_basic_2: setKeyOwner() and testKeyOwner() in the same frame. */
    @Test
    void testOwnerBasic2() {
        final boolean[] firstFrame = {true};
        final int[] failures = {0};
        final IkGuiTestContext test = ctx;
        test.setGui(
                () -> {
                    IkGui.begin(
                            "Test Window",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS | WindowFlags.MENU_BAR);
                    IkGui.button("Button 1");
                    if (IkGuiInternal.testKeyOwner(Key.A, KEY_OWNER_NO_OWNER) != firstFrame[0]) {
                        failures[0]++;
                    }
                    if (!IkGuiInternal.testKeyOwner(Key.A, IkGui.getItemID())) {
                        failures[0]++;
                    }
                    IkGuiInternal.setKeyOwner(Key.A, IkGui.getItemID(), 0);

                    IkGui.button("Button 2");
                    // Can't check this on the first frame, since testKeyOwner() doesn't check
                    // the next owner
                    if (!firstFrame[0]) {
                        if (IkGuiInternal.testKeyOwner(Key.A, KEY_OWNER_NO_OWNER)) {
                            failures[0]++;
                        }
                        if (IkGuiInternal.testKeyOwner(Key.A, IkGui.getItemID())) {
                            failures[0]++;
                        }
                    }
                    IkGui.end();
                    firstFrame[0] = false;
                });
        test.yieldFrames(3);
        assertEquals(0, failures[0]);
    }

    /**
     * inputs_owner_popup_overlap: the release of a double click in a popup isn't caught by the
     * window under it.
     */
    @Test
    void testOwnerPopupOverlap() {
        final int[] count = {0};
        ctx.setGui(
                () -> {
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    if (IkGui.button("Open Popup")) {
                        IkGui.openPopup("Popup");
                    }

                    final Vector2f cursor = IkGui.getCursorScreenPos();
                    IkGui.setNextWindowPos(cursor.x, cursor.y);
                    if (IkGui.beginPopup("Popup")) {
                        // Takes ownership on mouse down, the frame of the double click
                        if (IkGui.selectable(
                                        "Front",
                                        false,
                                        SelectableFlags.NO_AUTO_CLOSE_POPUPS
                                                | SelectableFlags.ALLOW_DOUBLE_CLICK,
                                        200,
                                        200)
                                && IkGui.isMouseDoubleClicked(MouseButton.LEFT)) {
                            IkGui.closeCurrentPopup();
                        }
                        IkGui.endPopup();
                    }

                    if (IkGui.selectable(
                            "Back", false, SelectableFlags.INTERNAL_SELECT_ON_RELEASE, 200, 200)) {
                        count[0]++;
                    }
                    IkGui.text("Counter " + count[0]);
                    IkGui.end();
                });
        ctx.setRef("Test Window");
        ctx.itemClick("Open Popup");
        ctx.setRef("//$FOCUSED");
        ctx.itemDoubleClick("Front");
        assertEquals(0, count[0]);
    }

    /** inputs_owner_override: overriding the owner, and the next frame behavior. */
    @Test
    void testOwnerOverride() {
        final int[] step = {0};
        final boolean[] check = {false};
        final int[] failures = {0};
        ctx.setGui(
                () -> {
                    final var g = ctx.context;
                    IkGui.begin(
                            "Test Window",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS | WindowFlags.MENU_BAR);
                    IkGui.button("Button 1");
                    if (step[0] >= 1) {
                        IkGuiInternal.setKeyOwner(Key.SPACE, IkGui.getItemID(), 0);
                    }
                    final var ownerData = g.keysOwnerData[Key.SPACE.ordinal()];
                    if (check[0]) {
                        if (step[0] == 0 && ownerData.ownerCurr != KEY_OWNER_NO_OWNER) {
                            failures[0]++;
                        } else if ((step[0] == 1 || step[0] == 2)
                                && ownerData.ownerCurr != IkGui.getID("Button 1")) {
                            failures[0]++;
                        }
                        if (step[0] >= 1 && ownerData.ownerNext != IkGui.getID("Button 1")) {
                            failures[0]++;
                        }
                    }

                    IkGui.button("Button 2");
                    if (step[0] >= 2) {
                        IkGuiInternal.setKeyOwner(Key.SPACE, IkGui.getItemID(), 0);
                    }
                    if (check[0]
                            && step[0] >= 2
                            && ownerData.ownerNext != IkGui.getID("Button 2")) {
                        failures[0]++;
                    }
                    IkGui.end();
                });
        for (int n = 0; n < 3; ++n) {
            step[0] = n;
            // Let it run without checking
            check[0] = false;
            ctx.yieldFrames(2);
            // Start checking
            check[0] = true;
            ctx.yieldFrames(2);
            assertEquals(0, failures[0], "step " + n);
        }
    }

    /** inputs_owner_lock_this_frame: InputFlags.LOCK_THIS_FRAME steals inputs from everyone. */
    @Test
    void testOwnerLockThisFrame() {
        final int[] step = {0};
        final int[] counts = new int[4];
        ctx.setGui(
                () -> {
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    IkGui.button("Button 1");
                    final int ownerID = IkGui.getItemID();

                    if (IkGui.isKeyPressed(Key.A)) {
                        counts[0]++;
                    }
                    IkGuiInternal.setKeyOwner(
                            Key.A,
                            step[0] == 0 ? ownerID : KEY_OWNER_ANY,
                            InputFlags.INTERNAL_LOCK_THIS_FRAME);
                    if (IkGui.isKeyPressed(Key.A)) {
                        counts[1]++;
                    }
                    if (IkGuiImplKeys.isKeyPressed(Key.A, 0, ownerID ^ 0x42424242)) {
                        counts[2]++;
                    }
                    if (IkGuiImplKeys.isKeyPressed(Key.A, 0, ownerID)) {
                        counts[3]++;
                    }
                    IkGui.end();
                });
        ctx.setRef("Test Window");
        for (int s = 0; s < 2; ++s) {
            Arrays.fill(counts, 0);
            step[0] = s;
            ctx.yieldFrame();
            ctx.keyPress(Key.A);
            ctx.keyPress(Key.A);
            assertEquals(2, counts[0], "step " + s);
            assertEquals(0, counts[1], "step " + s);
            assertEquals(0, counts[2], "step " + s);
            assertEquals(s == 0 ? 2 : 0, counts[3], "step " + s);
        }
    }

    /** inputs_owner_lock_until_release: InputFlags.LOCK_UNTIL_RELEASE. */
    @Test
    void testOwnerLockUntilRelease() {
        final int[] step = {0};
        final int[] counts = new int[4];
        ctx.setGui(
                () -> {
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    IkGui.button("Button 1");
                    final int ownerID = IkGui.getItemID();

                    if (IkGui.isKeyPressed(Key.A)) {
                        counts[0]++;
                        IkGuiInternal.setKeyOwner(
                                Key.A,
                                step[0] == 0 ? ownerID : KEY_OWNER_ANY,
                                InputFlags.INTERNAL_LOCK_UNTIL_RELEASE);
                    }
                    if (IkGui.isKeyDown(Key.A)) {
                        counts[1]++;
                    }
                    if (IkGuiImplKeys.isKeyDown(Key.A, ownerID ^ 0x42424242)) {
                        counts[2]++;
                    }
                    if (IkGuiImplKeys.isKeyDown(Key.A, ownerID)) {
                        counts[3]++;
                    }
                    IkGui.end();
                });
        ctx.setRef("Test Window");
        for (int s = 0; s < 2; ++s) {
            final String description = "step " + s;
            Arrays.fill(counts, 0);
            step[0] = s;
            ctx.yieldFrame();
            ctx.keyPress(Key.A);
            assertEquals(1, counts[0], description);
            assertEquals(0, counts[1], description);
            assertEquals(0, counts[2], description);
            assertEquals(s == 0 ? 1 : 0, counts[3], description);
            ctx.keyHold(KeyChord.of(Key.A), 1.0f);
            assertEquals(2, counts[0], description);
            assertEquals(0, counts[1], description);
            assertEquals(0, counts[2], description);
            if (s == 0) {
                assertTrue(counts[3] > 2, description);
            } else {
                assertEquals(0, counts[3], description);
            }
        }
    }

    /** inputs_owner_activeid_using_all_keys: setActiveIDUsingAllKeyboardKeys(). */
    @Test
    void testOwnerActiveIdUsingAllKeys() {
        final int[] counts = new int[3];
        ctx.setGui(
                () -> {
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    if (IkGui.shortcut(KeyChord.of(Key.W))) {
                        counts[0]++;
                    }
                    IkGui.button("behavior", 100, 100);
                    if (IkGui.isItemActive()) {
                        final int behaviorID = IkGui.getItemID();
                        IkGuiImplKeys.setActiveIDUsingAllKeyboardKeys();
                        if (IkGuiImplKeys.isKeyDown(Key.W, behaviorID)) {
                            counts[1]++;
                        }
                        if (IkGuiImplKeys.isKeyDown(Key.S, behaviorID)) {
                            counts[2]++;
                        }
                    }
                    IkGui.end();
                });
        ctx.setRef("Test Window");
        // A dummy click to get the focus
        ctx.itemClick("behavior");
        ctx.keyPress(Key.W);
        assertEquals(1, counts[0]);
        assertEquals(0, counts[1]);
        ctx.keyPress(Key.S);
        // No-op
        assertEquals(0, counts[2]);
        Arrays.fill(counts, 0);
        ctx.mouseDown(MouseButton.LEFT);
        ctx.yieldFrame();
        assertEquals(ctx.getID("behavior"), ctx.context.activeID);
        // Caught by the behavior
        ctx.keyPress(Key.W);
        assertEquals(0, counts[0]);
        assertTrue(counts[1] >= 1);
        ctx.keyPress(Key.S);
        assertTrue(counts[2] >= 1);
    }

    /** inputs_owner_mod_alt: claiming Alt prevents opening the menu layer. */
    @Test
    void testOwnerModAlt() {
        ctx.setGui(
                () -> {
                    IkGui.begin(
                            "Test Window",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS | WindowFlags.MENU_BAR);
                    if (IkGui.beginMenuBar()) {
                        IkGui.menuItem("MenuItem");
                        IkGui.endMenuBar();
                    }
                    IkGui.button("Button1");
                    IkGui.button("Button2");
                    IkGui.setItemKeyOwner(Key.MOD_ALT);
                    IkGui.end();
                });
        final var g = ctx.context;
        ctx.setRef("Test Window");
        final int alt = mod(KeyModFlags.ALT);

        for (int repeat = 0; repeat < 2; ++repeat) {
            // Alt is captured
            ctx.mouseMove("Button1");
            ctx.keyPress(alt);
            assertEquals(IkGuiImplNav.NAV_LAYER_MENU, g.navLayer);
            ctx.keyPress(alt);
            assertEquals(IkGuiImplNav.NAV_LAYER_MAIN, g.navLayer);

            ctx.mouseMove("Button2");
            ctx.keyPress(alt);
            assertEquals(IkGuiImplNav.NAV_LAYER_MAIN, g.navLayer);
        }
    }

    /** inputs_owner_single_mod: the left/right modifier keys versus the modifiers. */
    @Test
    void testOwnerSingleMod() {
        final int[] step = {0};
        final int[] counts = new int[5];
        final int[] ownerID = {0};
        ctx.setGui(
                () -> {
                    IkGui.begin(
                            "Test Window",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS | WindowFlags.MENU_BAR);
                    ownerID[0] = IkGui.getID("OwnerID");
                    if (IkGui.beginMenuBar()) {
                        IkGui.menuItem("MenuItem");
                        IkGui.endMenuBar();
                    }
                    IkGui.button("Button1");

                    // Case 1: works
                    if (step[0] == 1) {
                        IkGuiInternal.setKeyOwner(Key.MOD_ALT, ownerID[0], 0);
                    }
                    // Case 2: doesn't inhibit nav
                    if (step[0] == 2) {
                        IkGuiInternal.setKeyOwner(Key.LEFT_ALT, ownerID[0], 0);
                    }
                    // Case 3: works
                    if (step[0] == 3
                            && IkGuiImplKeys.shortcut(mod(KeyModFlags.ALT), 0, ownerID[0])) {
                        counts[3]++;
                        IkGui.text("PRESSED");
                    }
                    // Case 4: the shortcut works, but doesn't inhibit nav
                    if (step[0] == 4
                            && IkGuiImplKeys.shortcut(KeyChord.of(Key.LEFT_ALT), 0, ownerID[0])) {
                        counts[4]++;
                        IkGui.text("PRESSED");
                    }
                    IkGui.end();
                });
        final var g = ctx.context;
        ctx.setRef("Test Window");
        final int alt = mod(KeyModFlags.ALT);

        // setKeyOwner(MOD_ALT)
        step[0] = 1;
        ctx.yieldFrames(2);
        assertEquals(ownerID[0], IkGuiInternal.getKeyOwner(Key.MOD_ALT));
        ctx.keyPress(alt);
        assertEquals(IkGuiImplNav.NAV_LAYER_MAIN, g.navLayer);
        ctx.keyPress(Key.LEFT_ALT);
        assertEquals(IkGuiImplNav.NAV_LAYER_MAIN, g.navLayer);

        // shortcut(MOD_ALT)
        Arrays.fill(counts, 0);
        step[0] = 3;
        ctx.yieldFrames(2);
        assertEquals(KEY_OWNER_NO_OWNER, IkGuiInternal.getKeyOwner(Key.MOD_ALT));
        ctx.keyDown(alt);
        assertEquals(ownerID[0], IkGuiInternal.getKeyOwner(Key.MOD_ALT));
        assertEquals(1, counts[3]);
        ctx.keyUp(alt);
        assertEquals(IkGuiImplNav.NAV_LAYER_MAIN, g.navLayer);
        ctx.yieldFrames(2);
        ctx.keyDown(Key.LEFT_ALT);
        assertEquals(ownerID[0], IkGuiInternal.getKeyOwner(Key.MOD_ALT));
        assertEquals(2, counts[3]);
        ctx.keyUp(Key.LEFT_ALT);
        assertEquals(IkGuiImplNav.NAV_LAYER_MAIN, g.navLayer);

        // shortcut(LEFT_ALT)
        Arrays.fill(counts, 0);
        step[0] = 4;
        ctx.yieldFrames(2);
        assertEquals(KEY_OWNER_NO_OWNER, IkGuiInternal.getKeyOwner(Key.LEFT_ALT));
        ctx.keyDown(alt);
        assertEquals(KEY_OWNER_NO_OWNER, IkGuiInternal.getKeyOwner(Key.MOD_ALT));
        assertEquals(ownerID[0], IkGuiInternal.getKeyOwner(Key.LEFT_ALT));
        // The driver submits a modifier press as both the modifier and the left key
        assertEquals(1, counts[4]);
        ctx.keyUp(alt);
        // No effect
        assertEquals(IkGuiImplNav.NAV_LAYER_MAIN, g.navLayer);
        ctx.yieldFrames(2);
        ctx.keyDown(Key.LEFT_ALT);
        assertEquals(KEY_OWNER_NO_OWNER, IkGuiInternal.getKeyOwner(Key.MOD_ALT));
        assertEquals(ownerID[0], IkGuiInternal.getKeyOwner(Key.LEFT_ALT));
        assertEquals(2, counts[4]);
        ctx.keyUp(Key.LEFT_ALT);
        // No effect
        assertEquals(IkGuiImplNav.NAV_LAYER_MAIN, g.navLayer);
        ctx.yieldFrames(2);
        ctx.keyDown(Key.RIGHT_ALT);
        assertEquals(2, counts[4]);
        ctx.keyUp(Key.RIGHT_ALT);
        // Toggles the layer
        assertEquals(IkGuiImplNav.NAV_LAYER_MENU, g.navLayer);
    }

    /** inputs_owner_button_behavior: the key owner related button behavior flags. */
    @Test
    void testOwnerButtonBehavior() {
        final int[] counts = new int[9];
        ctx.setGui(
                () -> {
                    IkGui.begin(
                            "Test Window",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS | WindowFlags.MENU_BAR);
                    final String[] labels = {
                        "Button _None##A1",
                        "Button _PressedOnRelease##A2",
                        "Button _None##A3",
                        "Button _NoSetKeyOwner##B1",
                        "Button _PressedOnRelease##B2",
                        "Button _NoSetKeyOwner##B3",
                        "Button _None##C1",
                        "Button _PressedOnRelease | _NoTestKeyOwner##C2",
                        "Button _None##C3"
                    };
                    final int[] flags = {
                        ButtonFlags.NONE,
                        ButtonFlags.INTERNAL_PRESSED_ON_RELEASE,
                        ButtonFlags.NONE,
                        ButtonFlags.INTERNAL_NO_SET_KEY_OWNER,
                        ButtonFlags.INTERNAL_PRESSED_ON_RELEASE,
                        ButtonFlags.INTERNAL_NO_SET_KEY_OWNER,
                        ButtonFlags.NONE,
                        ButtonFlags.INTERNAL_PRESSED_ON_RELEASE
                                | ButtonFlags.INTERNAL_NO_TEST_KEY_OWNER,
                        ButtonFlags.NONE
                    };
                    for (int i = 0; i < labels.length; ++i) {
                        if (IkGuiImplButtons.buttonEx(labels[i], 400, 0, flags[i])) {
                            counts[i]++;
                        }
                        IkGui.sameLine();
                        IkGui.text(String.valueOf(counts[i]));
                        if (i % 3 == 2) {
                            IkGui.spacing();
                        }
                    }
                    IkGui.end();
                });
        ctx.setRef("Test Window");
        ctx.itemClick("Button _None##A1");
        assertEquals(1, counts[0]);
        Arrays.fill(counts, 0);

        ctx.itemDragAndDrop("Button _None##A1", "Button _PressedOnRelease##A2");
        assertEquals(0, counts[0]);
        assertEquals(0, counts[1]);
        assertEquals(0, counts[2]);

        ctx.itemDragAndDrop("Button _None##A3", "Button _PressedOnRelease##A2");
        assertEquals(0, counts[0]);
        assertEquals(0, counts[1]);
        assertEquals(0, counts[2]);

        ctx.itemDragAndDrop("Button _NoSetKeyOwner##B1", "Button _PressedOnRelease##B2");
        assertEquals(0, counts[3]);
        assertEquals(1, counts[4]);
        assertEquals(0, counts[5]);
        Arrays.fill(counts, 0);

        ctx.itemDragAndDrop("Button _NoSetKeyOwner##B3", "Button _PressedOnRelease##B2");
        assertEquals(0, counts[3]);
        // Upstream notes the order matters here, because the active ID is taken
        assertEquals(0, counts[4]);
        assertEquals(0, counts[5]);

        ctx.itemDragAndDrop("Button _None##C1", "Button _PressedOnRelease | _NoTestKeyOwner##C2");
        assertEquals(0, counts[6]);
        assertEquals(1, counts[7]);
        assertEquals(0, counts[8]);
        Arrays.fill(counts, 0);

        ctx.itemDragAndDrop("Button _None##C3", "Button _PressedOnRelease | _NoTestKeyOwner##C2");
        assertEquals(0, counts[6]);
        assertEquals(0, counts[7]);
        assertEquals(0, counts[8]);
    }

    /**
     * inputs_owner_all_keys_w_owner_unaware: dragging a window uses all the keys, but code that
     * doesn't know about owners can still read them.
     */
    @Test
    void testOwnerAllKeysWithOwnerUnaware() {
        final boolean[] state = new boolean[4];
        ctx.setGui(
                () -> {
                    IkGui.setNextWindowSize(100, 200);
                    IkGui.begin("Test", null, WindowFlags.NO_SAVED_SETTINGS);
                    state[0] = IkGui.isKeyDown(Key.MOD_CTRL);
                    state[1] = IkGui.isKeyDown(Key.MOD_SHIFT);
                    state[2] = IkGui.isKeyDown(Key.A);
                    state[3] =
                            IkGui.isMouseClicked(MouseButton.LEFT)
                                    || IkGui.isMouseDragging(MouseButton.LEFT);
                    IkGui.end();
                });
        final IkIO io = ctx.context.io;

        // Start dragging the window
        final Window window = ctx.getWindowByRef("Test");
        assertNotNull(window);
        ctx.windowFocus(window);
        final Vector2f titleBar = IkGuiTestContext.windowTitleBarPoint(window);
        ctx.mouseMoveToPos(titleBar.x, titleBar.y);

        // Hold shift before dragging to disable docking, and to test shift
        assertFalse(io.configDockingWithShift);
        ctx.keyDown(mod(KeyModFlags.SHIFT));

        final Vector2f oldPos = new Vector2f(window.position);
        ctx.mouseDown(MouseButton.LEFT);
        ctx.mouseMoveToPos(io.mousePosition.x + 10, io.mousePosition.y);
        assertNotEquals(oldPos, window.position);

        // Keys are detected along with shift and the left mouse button, both held earlier
        ctx.keyDown(mod(KeyModFlags.CTRL));
        ctx.keyDown(Key.A);
        Arrays.fill(state, false);
        ctx.yieldFrame();
        assertTrue(state[0], "ctrl");
        assertTrue(state[1], "shift");
        assertTrue(state[2], "A");
        assertTrue(state[3], "mouse");

        ctx.keyUp(Key.A);
        ctx.keyUp(mod(KeyModFlags.CTRL));
        ctx.mouseUp(MouseButton.LEFT);
        ctx.keyUp(mod(KeyModFlags.SHIFT));
    }

    /** Shared state for the routing tests, like upstream's InputRoutingVars. */
    private static class RoutingVars {
        final boolean[] isRouting = new boolean[26];
        final int[] pressedCount = new int[26];
        int keyChord = chord(KeyModFlags.CTRL, Key.A);
        final IkString str = new IkString("Hello", 64);
        final IkInt flags = new IkInt(0);
        boolean stealOwner;

        void clear() {
            Arrays.fill(isRouting, false);
            Arrays.fill(pressedCount, 0);
        }

        void assertIsRoutingOnly(int idx) {
            for (int n = 0; n < isRouting.length; ++n) {
                assertEquals(n == idx, isRouting[n], "routing " + (char) ('A' + n));
            }
        }

        void assertIsPressedOnly(int idx) {
            for (int n = 0; n < pressedCount.length; ++n) {
                if (n == idx) {
                    assertTrue(pressedCount[n] > 0, "pressed " + (char) ('A' + n));
                } else {
                    assertEquals(0, pressedCount[n], "pressed " + (char) ('A' + n));
                }
            }
        }
    }

    private static int idx(char c) {
        return c - 'A';
    }

    /** inputs_routing_1: shortcut routing between windows, child windows and popups. */
    @Test
    void testRouting1() {
        final RoutingVars vars = new RoutingVars();
        final java.util.function.IntConsumer doRoute =
                i -> {
                    final boolean isRouting = IkGuiImplKeys.testShortcutRouting(vars.keyChord, 0);
                    final boolean pressed = IkGui.shortcut(vars.keyChord);
                    vars.isRouting[i] = isRouting;
                    vars.pressedCount[i] += pressed ? 1 : 0;
                    IkGui.text("Routing: " + isRouting + (pressed ? " PRESSED" : " ..."));
                };
        final java.util.function.BiConsumer<Integer, Integer> doRouteForItem =
                (i, id) -> {
                    final boolean isRouting = IkGuiImplKeys.testShortcutRouting(vars.keyChord, id);
                    final boolean pressed = IkGuiImplKeys.shortcut(vars.keyChord, 0, id);
                    vars.isRouting[i] = isRouting;
                    vars.pressedCount[i] += pressed ? 1 : 0;
                    IkGui.text("Routing: " + isRouting + (pressed ? " PRESSED" : " ..."));
                };
        ctx.setGui(
                () -> {
                    IkGui.begin("WindowA");
                    IkGui.text("Chord: " + KeyChord.getName(vars.keyChord));
                    IkGui.button("WindowA");
                    doRoute.accept(idx('A'));

                    IkGui.inputText("InputTextB", vars.str);
                    doRouteForItem.accept(idx('B'), IkGui.getItemID());

                    IkGui.button("ButtonC");
                    IkGui.beginChild("ChildD", -Float.MIN_VALUE, 100, ChildFlags.BORDERS, 0);
                    IkGui.button("ChildD");
                    IkGui.endChild();
                    IkGui.beginChild("ChildE", -Float.MIN_VALUE, 100, ChildFlags.BORDERS, 0);
                    IkGui.button("ChildE");
                    doRoute.accept(idx('E'));
                    IkGui.endChild();
                    if (IkGui.button("Open Popup")) {
                        IkGui.openPopup("PopupF");
                    }
                    if (IkGui.beginPopup("PopupF")) {
                        IkGui.button("PopupF");
                        doRoute.accept(idx('F'));
                        IkGui.inputText("InputTextG", vars.str);
                        doRouteForItem.accept(idx('G'), IkGui.getItemID());
                        IkGui.endPopup();
                    }
                    IkGui.end();
                });
        ctx.setRef("WindowA");
        final int ctrlA = chord(KeyModFlags.CTRL, Key.A);
        final int ctrlB = chord(KeyModFlags.CTRL, Key.B);

        // A
        ctx.itemClick("WindowA");
        vars.assertIsRoutingOnly(idx('A'));
        vars.assertIsPressedOnly(-1);
        ctx.keyPress(ctrlA);
        vars.assertIsPressedOnly(idx('A'));
        vars.clear();

        // B catches Ctrl+A
        ctx.itemClick("InputTextB");
        vars.assertIsRoutingOnly(idx('B'));
        vars.assertIsPressedOnly(-1);
        InputTextState inputState = IkGuiImplInputText.getInputTextState(ctx.getID("InputTextB"));
        assertNotNull(inputState);
        assertFalse(inputState.hasSelection());
        ctx.keyPress(ctrlA);
        vars.assertIsPressedOnly(idx('B'));
        inputState = IkGuiImplInputText.getInputTextState(ctx.getID("InputTextB"));
        assertNotNull(inputState);
        assertTrue(inputState.hasSelection());
        vars.clear();

        // Another shortcut, Ctrl+B, while B is active but not using it. Upstream notes nobody
        // should get it, but calling shortcut() for the item has a side effect.
        vars.keyChord = ctrlB;
        ctx.yieldFrames(2);
        vars.assertIsRoutingOnly(idx('B'));
        ctx.keyPress(ctrlB);
        vars.assertIsPressedOnly(idx('B'));
        vars.keyChord = ctrlA;
        vars.clear();

        // D: a focused child that doesn't poll or route, so the parent A gets it
        ctx.itemClick("**/ChildD");
        ctx.yieldFrame();
        vars.assertIsRoutingOnly(idx('A'));
        vars.assertIsPressedOnly(-1);
        ctx.keyPress(ctrlA);
        vars.assertIsPressedOnly(idx('A'));
        vars.clear();

        // E: a focused child that polls and routes
        ctx.itemClick("**/ChildE");
        vars.assertIsRoutingOnly(idx('E'));
        vars.assertIsPressedOnly(-1);
        ctx.keyPress(ctrlA);
        vars.assertIsPressedOnly(idx('E'));
        vars.clear();

        // F: open the popup
        ctx.itemClick("Open Popup");
        // An appearing route needs a frame to establish
        ctx.yieldFrame();
        vars.assertIsRoutingOnly(idx('F'));
        vars.assertIsPressedOnly(-1);
        ctx.keyPress(ctrlA);
        vars.assertIsPressedOnly(idx('F'));
        vars.clear();

        // G: the popup's text input catches Ctrl+A
        ctx.itemClick("**/InputTextG");
        vars.assertIsRoutingOnly(idx('G'));
        vars.assertIsPressedOnly(-1);
        inputState = IkGuiImplInputText.getInputTextState(ctx.context.activeID);
        assertNotNull(inputState);
        assertFalse(inputState.hasSelection());
        ctx.keyPress(ctrlA);
        vars.assertIsPressedOnly(idx('G'));
        inputState = IkGuiImplInputText.getInputTextState(ctx.context.activeID);
        assertNotNull(inputState);
        assertTrue(inputState.hasSelection());
        vars.clear();

        // Ctrl+B while G is active but not using it, and F isn't either
        vars.keyChord = ctrlB;
        ctx.yieldFrames(2);
        vars.assertIsRoutingOnly(idx('G'));
        ctx.keyPress(ctrlB);
        vars.assertIsPressedOnly(idx('G'));
    }

    /** inputs_routing_over_active: InputFlags.ROUTE_OVER_ACTIVE. */
    @Test
    void testRoutingOverActive() {
        final RoutingVars vars = new RoutingVars();
        ctx.setGui(
                () -> {
                    IkGui.begin(
                            "Test Window",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS | WindowFlags.ALWAYS_AUTO_RESIZE);
                    IkGui.button("Button 1");
                    IkGui.inputText("InputText 1", vars.str);

                    final boolean ctrlZParent =
                            IkGui.shortcut(
                                    chord(KeyModFlags.CTRL, Key.Z),
                                    InputFlags.ROUTE_OVER_ACTIVE | InputFlags.REPEAT);
                    final boolean ctrlYParent =
                            IkGui.shortcut(
                                    chord(KeyModFlags.CTRL, Key.Y),
                                    InputFlags.ROUTE_OVER_ACTIVE | InputFlags.REPEAT);
                    vars.pressedCount[0] += ctrlZParent ? 1 : 0;
                    vars.pressedCount[1] += ctrlYParent ? 1 : 0;

                    IkGui.beginChild("Child", 0, 200, ChildFlags.BORDERS, 0);
                    IkGui.button("Button 2");
                    IkGui.inputText("InputText 2", vars.str);
                    final boolean ctrlZChild =
                            IkGui.shortcut(
                                    chord(KeyModFlags.CTRL, Key.Z),
                                    InputFlags.ROUTE_OVER_ACTIVE | InputFlags.REPEAT);
                    vars.pressedCount[2] += ctrlZChild ? 1 : 0;
                    IkGui.endChild();

                    final boolean ctrlCGlobal =
                            IkGui.shortcut(
                                    chord(KeyModFlags.CTRL, Key.C),
                                    InputFlags.ROUTE_GLOBAL | InputFlags.REPEAT);
                    vars.pressedCount[3] += ctrlCGlobal ? 1 : 0;
                    final boolean ctrlAGlobal =
                            IkGui.shortcut(
                                    chord(KeyModFlags.CTRL, Key.A),
                                    InputFlags.ROUTE_GLOBAL
                                            | InputFlags.ROUTE_OVER_ACTIVE
                                            | InputFlags.REPEAT);
                    vars.pressedCount[4] += ctrlAGlobal ? 1 : 0;
                    IkGui.end();

                    IkGui.begin("Another Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    IkGui.button("Button");
                    IkGui.inputText("InputText", vars.str);
                    IkGui.end();
                });
        final int ctrlC = chord(KeyModFlags.CTRL, Key.C);
        final int ctrlA = chord(KeyModFlags.CTRL, Key.A);

        // ROUTE_GLOBAL with and without ROUTE_OVER_ACTIVE, from the other window
        ctx.setRef("Another Window");

        // Buttons don't submit shortcuts
        ctx.itemClick("Button");
        ctx.keyPress(ctrlC);
        assertEquals(1, vars.pressedCount[3]);
        ctx.keyPress(ctrlA);
        assertEquals(1, vars.pressedCount[4]);
        ctx.mouseDown(MouseButton.LEFT);
        assertEquals(ctx.getID("Button"), ctx.context.activeID);
        ctx.keyPress(ctrlC);
        assertEquals(2, vars.pressedCount[3]);
        ctx.keyPress(ctrlA);
        assertEquals(2, vars.pressedCount[4]);
        ctx.mouseUp(MouseButton.LEFT);

        // The text input stays active and submits both Ctrl+C and Ctrl+A
        vars.pressedCount[3] = 0;
        vars.pressedCount[4] = 0;
        ctx.itemClick("InputText");
        assertEquals(ctx.getID("InputText"), ctx.context.activeID);
        ctx.keyPress(ctrlC);
        assertEquals(0, vars.pressedCount[3]);
        ctx.keyPress(ctrlA);
        assertEquals(1, vars.pressedCount[4]);
        ctx.keyPress(Key.ESCAPE);

        // ROUTE_FOCUSED with ROUTE_OVER_ACTIVE, all in the test window
        ctx.setRef("Test Window");
        ctx.itemClick("InputText 1");
        assertEquals(ctx.getID("InputText 1"), ctx.context.activeID);
        ctx.keyPress(chord(KeyModFlags.CTRL, Key.Z));
        ctx.keyPress(chord(KeyModFlags.CTRL, Key.Y));
        assertEquals(1, vars.pressedCount[0]);
        assertEquals(1, vars.pressedCount[1]);
        assertEquals(0, vars.pressedCount[2]);
        ctx.setRef(ctx.windowInfo("//Test Window/Child"));
        ctx.itemClick("InputText 2");
        assertEquals(ctx.getID("InputText 2"), ctx.context.activeID);
        ctx.keyPress(chord(KeyModFlags.CTRL, Key.Z));
        ctx.keyPress(chord(KeyModFlags.CTRL, Key.Y));
        assertEquals(1, vars.pressedCount[0]);
        assertEquals(2, vars.pressedCount[1]);
        assertEquals(1, vars.pressedCount[2]);
    }

    /**
     * inputs_routing_shortcut: releasing a modifier doesn't trigger the other shortcut while
     * repeating.
     */
    @Test
    void testRoutingShortcut() {
        final RoutingVars vars = new RoutingVars();
        ctx.setGui(
                () -> {
                    IkGui.begin(
                            "Test Window",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS | WindowFlags.ALWAYS_AUTO_RESIZE);
                    IkGui.button("Button0");
                    // Upstream passes the arguments in this order, so the owner ID is
                    // InputFlags.REPEAT and the flags are 0
                    if (IkGuiImplKeys.shortcut(KeyChord.of(Key.W), 0, InputFlags.REPEAT)) {
                        vars.pressedCount[0]++;
                    }
                    IkGui.beginChild("Child1", 100, 100, ChildFlags.BORDERS, 0);
                    IkGui.button("Button1");
                    if (IkGuiImplKeys.shortcut(
                            chord(KeyModFlags.CTRL, Key.W), 0, InputFlags.REPEAT)) {
                        vars.pressedCount[1]++;
                    }
                    IkGui.endChild();
                    IkGui.end();
                });
        ctx.setRef("Test Window");
        final int ctrl = mod(KeyModFlags.CTRL);
        for (int step = 0; step < 2; ++step) {
            final String description = "step " + step;
            vars.pressedCount[0] = 0;
            vars.pressedCount[1] = 0;

            ctx.itemClick("Button0");
            ctx.keyPress(Key.W);
            assertEquals(1, vars.pressedCount[0], description);
            assertEquals(0, vars.pressedCount[1], description);
            ctx.itemClick("**/Button1");
            ctx.keyPress(Key.W);
            assertEquals(2, vars.pressedCount[0], description);
            assertEquals(0, vars.pressedCount[1], description);
            ctx.keyPress(chord(KeyModFlags.CTRL, Key.W));
            assertEquals(2, vars.pressedCount[0], description);
            assertEquals(1, vars.pressedCount[1], description);

            // Ctrl down, W down, Ctrl up doesn't trigger the W shortcut
            ctx.keyDown(ctrl);
            ctx.keyDown(Key.W);
            assertEquals(2, vars.pressedCount[0], description);
            assertEquals(2, vars.pressedCount[1], description);
            ctx.keyUp(ctrl);
            ctx.sleep(0.5f);
            assertEquals(2, vars.pressedCount[0], description);
            assertEquals(2, vars.pressedCount[1], description);
            ctx.keyDown(ctrl);
            ctx.keyUp(ctrl);
            assertEquals(2, vars.pressedCount[0], description);
            // The Ctrl+W shortcut didn't trigger
            assertEquals(2, vars.pressedCount[1], description);
            ctx.keyUp(Key.W);
        }
    }

    /** inputs_routing_shortcut_no_owners: key ownership is checked along with routing. */
    @Test
    void testRoutingShortcutNoOwners() {
        final RoutingVars vars = new RoutingVars();
        ctx.setGui(
                () -> {
                    IkGui.begin(
                            "Test Window",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS | WindowFlags.ALWAYS_AUTO_RESIZE);
                    vars.pressedCount[0] += IkGui.shortcut(chord(KeyModFlags.CTRL, Key.W)) ? 1 : 0;
                    if (vars.stealOwner) {
                        IkGuiInternal.setKeyOwner(Key.W, IkGui.getID("SomeOwner"), 0);
                    }
                    IkGui.end();
                });
        ctx.setRef("Test Window");
        ctx.itemClick("");
        ctx.keyPress(chord(KeyModFlags.CTRL, Key.W));
        assertEquals(1, vars.pressedCount[0]);
        vars.stealOwner = true;
        ctx.yieldFrame();
        ctx.keyPress(chord(KeyModFlags.CTRL, Key.W));
        assertEquals(1, vars.pressedCount[0]);
    }

    /**
     * inputs_routing_char_filter: shortcuts that could be characters are filtered out while a text
     * input is active.
     */
    @Test
    void testRoutingCharFilter() {
        final RoutingVars vars = new RoutingVars();
        ctx.setGui(
                () -> {
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    IkGui.checkboxFlags(
                            "InputFlags.ROUTE_GLOBAL", vars.flags, InputFlags.ROUTE_GLOBAL);
                    IkGui.inputText("buf", vars.str);
                    final int flags = vars.flags.get();
                    // Filtered
                    vars.pressedCount[0] += IkGui.shortcut(KeyChord.of(Key.G), flags) ? 1 : 0;
                    // Passes
                    vars.pressedCount[1] +=
                            IkGui.shortcut(chord(KeyModFlags.CTRL, Key.G), flags) ? 1 : 0;
                    // Filtered
                    vars.pressedCount[2] +=
                            IkGui.shortcut(chord(KeyModFlags.ALT, Key.G), flags) ? 1 : 0;
                    // Currently passes, upstream wonders if it should be filtered
                    vars.pressedCount[3] +=
                            IkGui.shortcut(chord(KeyModFlags.CTRL | KeyModFlags.ALT, Key.G), flags)
                                    ? 1
                                    : 0;
                    vars.pressedCount[4] +=
                            IkGui.shortcut(chord(KeyModFlags.SHIFT | KeyModFlags.ALT, Key.G), flags)
                                    ? 1
                                    : 0;
                    // Passes
                    vars.pressedCount[5] += IkGui.shortcut(KeyChord.of(Key.F1), flags) ? 1 : 0;
                    IkGui.end();
                });
        ctx.setRef("Test Window");

        for (int step = 0; step < 4; ++step) {
            final boolean isActive = (step & 1) != 0;
            final boolean isGlobal = (step & 2) != 0;
            vars.flags.set(isGlobal ? InputFlags.ROUTE_GLOBAL : InputFlags.ROUTE_FOCUSED);
            ctx.yieldFrames(2);

            ctx.itemClick("buf");
            if (!isActive) {
                ctx.keyPress(Key.ESCAPE);
            }

            ctx.keyPress(Key.G);
            vars.assertIsPressedOnly(isActive ? -1 : 0);
            vars.clear();
            ctx.keyPress(chord(KeyModFlags.CTRL, Key.G));
            vars.assertIsPressedOnly(1);
            vars.clear();
            ctx.keyPress(chord(KeyModFlags.ALT, Key.G));
            vars.assertIsPressedOnly(isActive ? -1 : 2);
            vars.clear();

            ctx.keyPress(chord(KeyModFlags.CTRL | KeyModFlags.ALT, Key.G));
            vars.assertIsPressedOnly(3);
            vars.clear();
            ctx.keyPress(chord(KeyModFlags.SHIFT | KeyModFlags.ALT, Key.G));
            vars.assertIsPressedOnly(4);
            vars.clear();
            ctx.keyPress(Key.F1);
            vars.assertIsPressedOnly(5);
            vars.clear();
        }
    }

    /** inputs_keychord_name. */
    @Test
    void testKeyChordName() {
        assertEquals("None", KeyChord.getName(KeyChord.of(Key.NONE)));
        assertEquals("A", KeyChord.getName(KeyChord.of(Key.A)));
        assertEquals("Ctrl+A", KeyChord.getName(chord(KeyModFlags.CTRL, Key.A)));
        assertEquals("LeftCtrl", KeyChord.getName(KeyChord.of(Key.LEFT_CTRL)));
        assertEquals("LeftCtrl", KeyChord.getName(chord(KeyModFlags.CTRL, Key.LEFT_CTRL)));
        assertEquals("Ctrl", KeyChord.getName(mod(KeyModFlags.CTRL)));
        assertEquals(
                "Shift+LeftCtrl",
                KeyChord.getName(chord(KeyModFlags.CTRL | KeyModFlags.SHIFT, Key.LEFT_CTRL)));
    }
}
