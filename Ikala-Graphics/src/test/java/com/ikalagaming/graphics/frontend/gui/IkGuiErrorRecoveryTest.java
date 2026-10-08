package com.ikalagaming.graphics.frontend.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.frontend.gui.data.Context;
import com.ikalagaming.graphics.frontend.gui.data.Window;
import com.ikalagaming.graphics.frontend.gui.enums.ColorType;
import com.ikalagaming.graphics.frontend.gui.enums.Condition;
import com.ikalagaming.graphics.frontend.gui.flags.ItemFlags;
import com.ikalagaming.graphics.frontend.gui.util.Color;

import org.joml.Vector2f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/** Tests for error recovery, the error reporting options, and ID conflict highlighting. */
class IkGuiErrorRecoveryTest {
    private static final String ERROR_TOOLTIP = "##Tooltip_Error";

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

    private boolean logContains(String text) {
        return context.debugLogBuffer.snapshot().stream().anyMatch(line -> line.contains(text));
    }

    private boolean errorTooltipActive() {
        final Window tooltip = IkGuiInternal.findWindowByName(ERROR_TOOLTIP);
        return tooltip != null && tooltip.active;
    }

    /** A window that forgets to pop or end several things. */
    private static final Runnable SLOPPY_WINDOW =
            () -> {
                IkGui.begin("Sloppy");
                IkGui.pushID("forgotten");
                IkGui.pushStyleColor(ColorType.TEXT, Color.rgba(255, 0, 0, 255));
                IkGui.beginGroup();
                IkGui.setNextItemOpen(true, Condition.ALWAYS);
                IkGui.treeNode("Node");
                IkGui.text("Hello");
                IkGui.end();
            };

    @Test
    void testWindowStateIsRecoveredInEnd() {
        frame(SLOPPY_WINDOW);
        assertTrue(logContains("Missing treePop()"), context.debugLogBuffer.getText());
        assertTrue(logContains("Missing endGroup()"));
        assertTrue(logContains("Missing popID()"));
        assertTrue(logContains("Missing popStyleColor()"));
        assertTrue(context.colorStack.isEmpty());
        assertTrue(context.groupStack.isEmpty());
        assertTrue(context.windowStack.isEmpty());

        final Window window = IkGuiInternal.findWindowByName("Sloppy");
        assertNotNull(window);
        assertEquals(0, window.treeDepth);
    }

    @Test
    void testMissingEndIsRecoveredAtEndOfFrame() {
        frame(
                () -> {
                    IkGui.begin("Forgot to end");
                    IkGui.beginChild("Child", 100, 100);
                    IkGui.text("Hello");
                });
        assertTrue(logContains("Missing endChild()"), context.debugLogBuffer.getText());
        assertTrue(logContains("Missing end()"));
        assertTrue(context.windowStack.isEmpty());
    }

    @Test
    void testRecoveryCanBeTurnedOff() {
        context.io.configErrorRecovery = false;
        frame(
                () -> {
                    IkGui.begin("Window");
                    IkGui.pushStyleColor(ColorType.TEXT, Color.rgba(255, 0, 0, 255));
                    IkGui.end();
                });
        // Nothing recovered the color
        assertEquals(1, context.colorStack.size());
        assertFalse(logContains("Missing popStyleColor()"));
    }

    @Test
    void testAssertThrows() {
        context.io.configErrorRecoveryEnableAssert = true;
        IkGui.newFrame();
        final IkGuiUserError error = assertThrows(IkGuiUserError.class, IkGui::popID);
        assertTrue(error.getMessage().contains("popID()"), error.getMessage());
    }

    @Test
    void testDebugLogCanBeTurnedOff() {
        context.io.configErrorRecoveryEnableDebugLog = false;
        final List<String> messages = new ArrayList<>();
        context.errorCallback = messages::add;
        frame(SLOPPY_WINDOW);
        assertFalse(logContains("[ikgui-error]"), context.debugLogBuffer.getText());
        // The callback still gets every error
        assertTrue(messages.contains("Missing popID()"), messages.toString());
        assertEquals(4, messages.size(), messages.toString());
    }

    @Test
    void testFirstErrorLogsTheSettings() {
        frame(SLOPPY_WINDOW);
        assertTrue(logContains("(current settings: Assert=0, Log=1, Tooltip=1)"));
        final long settingsLines =
                context.debugLogBuffer.snapshot().stream()
                        .filter(line -> line.contains("current settings"))
                        .count();
        assertEquals(1, settingsLines);
    }

    @Test
    void testErrorTooltip() {
        frame(SLOPPY_WINDOW);
        assertTrue(errorTooltipActive());
        assertEquals(4, context.errorCountCurrentFrame);
        // The tooltip goes away when there are no errors
        frame(() -> {});
        assertFalse(errorTooltipActive());

        context.io.configErrorRecoveryEnableTooltip = false;
        frame(SLOPPY_WINDOW);
        assertFalse(errorTooltipActive());
        assertTrue(context.windowStack.isEmpty());
    }

    @Test
    void testSilentRecoveryIsNotAllowed() {
        context.io.configErrorRecoveryEnableDebugLog = false;
        context.io.configErrorRecoveryEnableTooltip = false;
        frame(() -> {});
        assertTrue(context.io.configErrorRecoveryEnableDebugLog);
        assertTrue(logContains("Error recovery needs at least one"));
    }

    /**
     * Two buttons with the same ID, with the mouse over the first one.
     *
     * @param allowDuplicates Whether to push ItemFlags.ALLOW_DUPLICATE_ID.
     * @param firstButtonCenter Set to the center of the first button.
     * @param buttonID Set to the ID of the buttons.
     * @return The UI code.
     */
    private static Runnable conflictingButtons(
            boolean allowDuplicates, Vector2f firstButtonCenter, int[] buttonID) {
        return () -> {
            IkGui.setNextWindowPos(0, 0, Condition.FIRST_USE_EVER);
            IkGui.setNextWindowSize(300, 200, Condition.FIRST_USE_EVER);
            IkGui.begin("Conflicts");
            if (allowDuplicates) {
                IkGui.pushItemFlag(ItemFlags.ALLOW_DUPLICATE_ID, true);
            }
            IkGui.button("Same");
            firstButtonCenter.set(IkGui.getItemRectMin()).add(IkGui.getItemRectMax()).mul(0.5f);
            buttonID[0] = IkGui.getItemID();
            IkGui.button("Same");
            if (allowDuplicates) {
                IkGui.popItemFlag();
            }
            IkGui.end();
        };
    }

    @Test
    void testIdConflictsAreHighlighted() {
        final Vector2f center = new Vector2f();
        final int[] id = {0};
        final Runnable ui = conflictingButtons(false, center, id);
        frames(3, ui);
        assertEquals(0, context.debugDrawIdConflictsID);

        context.io.addMousePosEvent(center.x, center.y);
        frames(3, ui);
        assertEquals(id[0], context.debugDrawIdConflictsID);
        assertEquals(2, context.debugDrawIdConflictsCount);
        assertTrue(errorTooltipActive());
        // This isn't a recoverable error, so it's only shown in the tooltip
        assertEquals(0, context.errorCountCurrentFrame);

        // Turning the option off removes the highlight
        context.io.configDebugHighlightIdConflicts = false;
        frames(2, ui);
        assertEquals(0, context.debugDrawIdConflictsID);
        assertFalse(errorTooltipActive());
    }

    @Test
    void testAllowDuplicateIdIsNotAConflict() {
        final Vector2f center = new Vector2f();
        final int[] id = {0};
        final Runnable ui = conflictingButtons(true, center, id);
        frames(3, ui);
        context.io.addMousePosEvent(center.x, center.y);
        frames(3, ui);
        assertEquals(0, context.debugDrawIdConflictsID);
        assertFalse(errorTooltipActive());
    }
}
