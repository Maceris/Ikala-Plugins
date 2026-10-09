package com.ikalagaming.graphics.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.gui.data.Context;
import com.ikalagaming.graphics.gui.data.IkBoolean;
import com.ikalagaming.graphics.gui.enums.Condition;
import com.ikalagaming.graphics.gui.flags.LogFlags;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Tests for logging/capturing the text output of the interface. */
class IkGuiLoggingTest {

    private Context context;

    @BeforeEach
    void setUp() {
        context = IkGui.createContext();
        context.io.displaySize.set(1280, 720);
        // Don't read or write an .ini file
        context.io.iniFilename = null;
    }

    @AfterEach
    void tearDown() {
        IkGui.destroyContext();
    }

    /**
     * Run a frame with a window, logging the body to the buffer.
     *
     * @param body The window contents to log.
     * @return The logged text.
     */
    private String logFrame(Runnable body) {
        IkGui.newFrame();
        IkGui.setNextWindowPos(10, 10, Condition.FIRST_USE_EVER);
        IkGui.setNextWindowSize(400, 400, Condition.FIRST_USE_EVER);
        IkGui.begin("Log");
        IkGuiInternal.logToBuffer(-1);
        body.run();
        IkGui.logFinish();
        IkGui.end();
        IkGui.render();
        return context.logBuffer.toString();
    }

    @Test
    void testTextAndDecorations() {
        String logged =
                logFrame(
                        () -> {
                            IkGui.text("Hello");
                            IkGui.button("Press");
                            IkGui.checkbox("Enabled", new IkBoolean(true));
                            IkGui.radioButton("Choice", false);
                        });
        // Like ImGui, decorations are logged as separate items, so they are separated by spaces
        assertEquals("Hello\n[ Press ]\n[x] Enabled\n( ) Choice\n", logged);
        assertFalse(context.logEnabled);
    }

    @Test
    void testSameLineItemsAreSeparatedBySpace() {
        String logged =
                logFrame(
                        () -> {
                            IkGui.text("A");
                            IkGui.sameLine();
                            IkGui.text("B");
                        });
        assertEquals("A B\n", logged);
    }

    @Test
    void testTreeNodesAutoOpenAndIndent() {
        String logged =
                logFrame(
                        () -> {
                            if (IkGui.treeNode("Node")) {
                                IkGui.text("Child");
                                IkGui.treePop();
                            }
                            if (IkGui.collapsingHeader("Header")) {
                                IkGui.text("Hidden");
                            }
                        });
        assertTrue(logged.contains("> Node\n    Child\n"), logged);
        // Collapsing headers are not opened automatically
        assertTrue(logged.contains("### Header ###"), logged);
        assertFalse(logged.contains("Hidden"), logged);
    }

    @Test
    void testAutoOpenDepth() {
        IkGui.newFrame();
        IkGui.begin("Log");
        IkGuiInternal.logToBuffer(0);
        if (IkGui.treeNode("Node")) {
            IkGui.text("Child");
            IkGui.treePop();
        }
        IkGui.logFinish();
        IkGui.end();
        IkGui.render();
        assertFalse(context.logBuffer.toString().contains("Child"));
    }

    @Test
    void testClippedItemsAreLogged() {
        String logged =
                logFrame(
                        () -> {
                            for (int i = 0; i < 100; ++i) {
                                IkGui.text("Line " + i);
                            }
                        });
        assertTrue(logged.contains("Line 99\n"), logged);
        // Items are clipped again after logging
        assertFalse(context.itemUnclipByLog);
    }

    @Test
    void testLogTextOnlyWhenLogging() {
        IkGui.logText("ignored");
        String logged = logFrame(() -> IkGui.logText("raw"));
        assertTrue(logged.startsWith("raw"), logged);
        assertFalse(logged.contains("ignored"), logged);
    }

    @Test
    void testOnlyOneOutputAtATime() {
        IkGui.newFrame();
        IkGui.begin("Log");
        IkGui.logToClipboard();
        IkGuiInternal.logToBuffer(-1);
        assertEquals(LogFlags.OUTPUT_CLIPBOARD, context.logFlags);
        IkGui.logFinish();
        IkGui.end();
        IkGui.render();
    }

    @Test
    void testLogToClipboard() {
        IkGui.newFrame();
        IkGui.begin("Log");
        IkGui.logToClipboard();
        IkGui.text("Copied");
        IkGui.logFinish();
        IkGui.end();
        IkGui.render();
        assertEquals("Copied\n", IkGui.getClipboardText());
        assertEquals(0, context.logBuffer.length());
    }

    @Test
    void testLogToFile(@TempDir Path directory) throws IOException {
        Path file = directory.resolve("log.txt");
        Files.writeString(file, "existing\n");
        IkGui.newFrame();
        IkGui.begin("Log");
        IkGui.logToFile(-1, file.toString());
        IkGui.text("Appended");
        IkGui.logFinish();
        IkGui.end();
        IkGui.render();
        assertEquals("existing\nAppended\n", Files.readString(file));
    }

    @Test
    void testLogButtonsAreNotLogged() {
        IkGui.newFrame();
        IkGui.begin("Log");
        IkGui.logButtons();
        IkGui.end();
        IkGui.render();
        assertFalse(context.logEnabled);
    }

    @Test
    void testSeparatorText() {
        String logged =
                logFrame(
                        () -> {
                            IkGui.separatorText("Title##id");
                            IkGui.text("Body");
                        });
        assertEquals("--- Title\nBody\n", logged);
    }
}
