package com.ikalagaming.graphics.frontend.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.frontend.gui.data.Context;
import com.ikalagaming.graphics.frontend.gui.data.KeyRoutingData;
import com.ikalagaming.graphics.frontend.gui.data.Window;
import com.ikalagaming.graphics.frontend.gui.enums.Condition;
import com.ikalagaming.graphics.frontend.gui.enums.Key;
import com.ikalagaming.graphics.frontend.gui.enums.MouseButton;

import org.joml.Vector2f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests for the mouse keys, which let mouse buttons and the wheel use key ownership. */
class IkGuiMouseKeysTest {
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

    /**
     * A window at a fixed position, with enough lines to scroll.
     *
     * @param body Extra contents, submitted before the lines.
     * @return The UI code.
     */
    private static Runnable scrollingWindow(Runnable body) {
        return () -> {
            IkGui.setNextWindowPos(0, 0, Condition.FIRST_USE_EVER);
            IkGui.setNextWindowSize(200, 100, Condition.FIRST_USE_EVER);
            IkGui.begin("Scrolling");
            body.run();
            for (int i = 0; i < 30; ++i) {
                IkGui.text("Line " + i);
            }
            IkGui.end();
        };
    }

    @Test
    void testMouseKeysFollowTheMouse() {
        final Runnable ui = () -> {};
        frames(2, ui);
        assertFalse(IkGui.isKeyDown(Key.MOUSE_LEFT));

        context.io.addMouseButtonEvent(MouseButton.LEFT, true);
        frame(ui);
        assertTrue(IkGui.isKeyDown(Key.MOUSE_LEFT));
        assertTrue(IkGui.isKeyPressed(Key.MOUSE_LEFT));
        assertFalse(IkGui.isKeyDown(Key.MOUSE_RIGHT));
        frame(ui);
        assertTrue(IkGui.isKeyDown(Key.MOUSE_LEFT));
        assertFalse(IkGui.isKeyPressed(Key.MOUSE_LEFT));

        context.io.addMouseButtonEvent(MouseButton.LEFT, false);
        frame(ui);
        assertFalse(IkGui.isKeyDown(Key.MOUSE_LEFT));
        assertTrue(IkGui.isKeyReleased(Key.MOUSE_LEFT));

        // The wheel keys are down on the frames the wheel moves
        context.io.addMouseWheelEvent(0, -1);
        frame(ui);
        assertTrue(IkGui.isKeyDown(Key.MOUSE_WHEEL_Y));
        assertFalse(IkGui.isKeyDown(Key.MOUSE_WHEEL_X));
        frame(ui);
        assertFalse(IkGui.isKeyDown(Key.MOUSE_WHEEL_Y));
    }

    @Test
    void testBackendCannotSubmitMouseKeys() {
        final Runnable ui = () -> {};
        frame(ui);
        context.io.addKeyEvent(Key.MOUSE_LEFT, true);
        frames(2, ui);
        assertFalse(IkGui.isKeyDown(Key.MOUSE_LEFT));
        assertFalse(IkGui.isMouseDown(MouseButton.LEFT));
    }

    @Test
    void testItemCanTakeTheWheelFromTheWindow() {
        final Vector2f buttonCenter = new Vector2f();
        final boolean[] takeWheel = {true};
        final float[] wheelSeen = {0.0f};
        final Runnable ui =
                scrollingWindow(
                        () -> {
                            IkGui.invisibleButton("canvas", 100, 40);
                            buttonCenter
                                    .set(IkGui.getItemRectMin())
                                    .add(IkGui.getItemRectMax())
                                    .mul(0.5f);
                            if (takeWheel[0] && IkGui.setItemKeyOwner(Key.MOUSE_WHEEL_Y)) {
                                wheelSeen[0] += IkGui.getIO().mouseWheel;
                            }
                        });
        frames(3, ui);
        final Window window = IkGuiInternal.findWindowByName("Scrolling");
        assertTrue(window.scrollMax.y > 0);

        context.io.addMousePosEvent(buttonCenter.x, buttonCenter.y);
        frames(2, ui);
        context.io.addMouseWheelEvent(0, -1);
        frames(2, ui);
        // The item got the wheel, and the window didn't scroll
        assertEquals(-1.0f, wheelSeen[0], DELTA);
        assertEquals(0.0f, window.scrollPosition.y, DELTA);

        // Without taking ownership, the window scrolls
        takeWheel[0] = false;
        frames(2, ui);
        context.io.addMouseWheelEvent(0, -1);
        frames(2, ui);
        assertTrue(window.scrollPosition.y > 0);
    }

    @Test
    void testButtonOwnsTheMouseButtonWhileHeld() {
        final Vector2f buttonCenter = new Vector2f();
        final int[] buttonID = {0};
        final int[] clicks = {0};
        final Runnable ui =
                scrollingWindow(
                        () -> {
                            if (IkGui.button("Button")) {
                                clicks[0]++;
                            }
                            buttonID[0] = IkGui.getItemID();
                            buttonCenter
                                    .set(IkGui.getItemRectMin())
                                    .add(IkGui.getItemRectMax())
                                    .mul(0.5f);
                        });
        frames(3, ui);
        context.io.addMousePosEvent(buttonCenter.x, buttonCenter.y);
        frames(2, ui);
        assertEquals(KeyRoutingData.KEY_OWNER_NO_OWNER, IkGuiInternal.getKeyOwner(Key.MOUSE_LEFT));

        context.io.addMouseButtonEvent(MouseButton.LEFT, true);
        frames(2, ui);
        assertEquals(buttonID[0], IkGuiInternal.getKeyOwner(Key.MOUSE_LEFT));
        // Code that requires an unowned button doesn't see it
        assertFalse(IkGuiInternal.isMouseDown(MouseButton.LEFT, KeyRoutingData.KEY_OWNER_NO_OWNER));
        assertTrue(IkGui.isMouseDown(MouseButton.LEFT));

        context.io.addMouseButtonEvent(MouseButton.LEFT, false);
        frame(ui);
        assertEquals(1, clicks[0]);
        // Ownership is released on the frame after the release
        frame(ui);
        assertEquals(KeyRoutingData.KEY_OWNER_NO_OWNER, IkGuiInternal.getKeyOwner(Key.MOUSE_LEFT));
    }

    @Test
    void testOwnedClickDoesNotReachOtherOwners() {
        final Vector2f buttonCenter = new Vector2f();
        final int otherOwner = 12_345;
        final boolean[] otherSawClick = {false};
        final Runnable ui =
                scrollingWindow(
                        () -> {
                            IkGui.button("Button");
                            buttonCenter
                                    .set(IkGui.getItemRectMin())
                                    .add(IkGui.getItemRectMax())
                                    .mul(0.5f);
                            if (IkGuiInternal.isMouseClicked(MouseButton.LEFT, 0, otherOwner)) {
                                otherSawClick[0] = true;
                            }
                        });
        frames(3, ui);
        context.io.addMousePosEvent(buttonCenter.x, buttonCenter.y);
        frames(2, ui);
        context.io.addMouseButtonEvent(MouseButton.LEFT, true);
        frame(ui);
        // The button took the click before the other code polled it
        assertFalse(otherSawClick[0]);
        assertTrue(IkGui.isMouseClicked(MouseButton.LEFT));
    }
}
