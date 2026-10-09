package com.ikalagaming.graphics.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.gui.data.Context;
import com.ikalagaming.graphics.gui.data.IkBoolean;
import com.ikalagaming.graphics.gui.data.Window;
import com.ikalagaming.graphics.gui.enums.Condition;
import com.ikalagaming.graphics.gui.enums.MouseButton;
import com.ikalagaming.graphics.gui.flags.BackendFlags;
import com.ikalagaming.graphics.gui.flags.ConfigFlags;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

/** Tests for the About window. */
class IkGuiAboutWindowTest {
    private Context context;

    /** The ID of the "Copy to clipboard" button, captured while drawing. */
    private int copyButtonID;

    @BeforeEach
    void setUp() {
        context = IkGui.createContext();
        // Don't read or write an .ini file
        context.io.iniFilename = null;
        context.io.displaySize.set(1280, 720);
        context.io.mouseInsideWindow = true;
        context.io.configInputTrickleEventQueue = false;
        context.platformIO.openInShellFunction = path -> true;
        IkGuiDemo.aboutShowConfigInfo.set(false);
    }

    @AfterEach
    void tearDown() {
        IkGuiDemo.aboutShowConfigInfo.set(false);
        IkGui.destroyContext();
    }

    private static void frame(Runnable ui) {
        IkGui.newFrame();
        ui.run();
        IkGui.render();
    }

    private boolean anyErrors() {
        return context.debugLogBuffer.snapshot().stream()
                .anyMatch(line -> line.contains("[ikgui-error]"));
    }

    private Runnable aboutUI(IkBoolean open) {
        return () -> {
            IkGui.setNextWindowPos(10, 10, Condition.FIRST_USE_EVER);
            IkGui.showAboutWindow(open);
            // Appending to the window gives the same ID stack as the button
            IkGui.begin("About IkGui", open, 0);
            copyButtonID = IkGui.getID("Copy to clipboard");
            IkGui.end();
        };
    }

    @Test
    void testAboutWindowShowsWithAndWithoutConfigInfo() {
        final IkBoolean open = new IkBoolean(true);
        final Runnable ui = aboutUI(open);
        for (int i = 0; i < 3; ++i) {
            frame(ui);
        }
        final Window window = IkGuiInternal.findWindowByName("About IkGui");
        assertNotNull(window);
        final float collapsedHeight = window.size.y;
        assertTrue(window.size.x > 0 && collapsedHeight > 0);

        IkGuiDemo.aboutShowConfigInfo.set(true);
        for (int i = 0; i < 3; ++i) {
            frame(ui);
        }
        // Auto-resized to fit the information
        assertTrue(window.size.y > collapsedHeight);
        assertFalse(anyErrors(), context.debugLogBuffer.getText());
        assertTrue(open.get());
    }

    @Test
    void testCopyConfigInfoToClipboard() {
        IkGuiDemo.aboutShowConfigInfo.set(true);
        final Runnable ui = aboutUI(null);
        for (int i = 0; i < 3; ++i) {
            frame(ui);
        }
        final Window window = IkGuiInternal.findWindowByName("About IkGui");
        assertNotNull(window);

        // Move down the left side of the window until the button is hovered
        final float x = window.position.x + context.style.variable.windowPadding.x + 4;
        boolean found = false;
        for (float y = window.position.y; y < window.position.y + window.size.y; y += 4) {
            context.io.addMousePosEvent(x, y);
            frame(ui);
            if (context.hoveredID == copyButtonID) {
                found = true;
                break;
            }
        }
        assertTrue(found, "Couldn't find the copy button");
        context.io.addMouseButtonEvent(MouseButton.LEFT, true);
        frame(ui);
        context.io.addMouseButtonEvent(MouseButton.LEFT, false);
        frame(ui);
        frame(ui);

        final String copied = IkGui.getClipboardText();
        assertTrue(copied.contains("```"), copied);
        assertTrue(copied.contains("IkGui, ported from Dear ImGui " + IkGui.DEAR_IMGUI_VERSION));
        assertTrue(copied.contains("Java: " + System.getProperty("java.version")), copied);
        assertTrue(copied.contains("style.itemSpacing"), copied);
        assertFalse(anyErrors(), context.debugLogBuffer.getText());
    }

    @Test
    void testSetFlagNames() {
        assertEquals(
                List.of("NAV_ENABLE_KEYBOARD", "DOCKING_ENABLE"),
                IkGuiDemo.setFlagNames(
                        ConfigFlags.class,
                        ConfigFlags.NAV_ENABLE_KEYBOARD | ConfigFlags.DOCKING_ENABLE));
        assertTrue(IkGuiDemo.setFlagNames(BackendFlags.class, BackendFlags.NONE).isEmpty());
    }

    @Test
    void testDemoMenuOpensAboutWindow() {
        // The demo's About window shows up with the rest of the demo, without errors
        frame(() -> IkGui.showDemoWindow());
        assertTrue(IkGuiInternal.findWindowByName("About IkGui") == null);
        // What the Tools menu item toggles
        IkGuiDemo.showAbout.set(true);
        try {
            frame(() -> IkGui.showDemoWindow());
        } finally {
            IkGuiDemo.showAbout.set(false);
        }
        assertNotNull(IkGuiInternal.findWindowByName("About IkGui"));
        assertNotNull(IkGuiInternal.findWindowByName("IkGui Demo Window"));
        assertFalse(anyErrors(), context.debugLogBuffer.getText());
    }
}
