package com.ikalagaming.graphics.gui.windows;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.gui.data.Context;
import com.ikalagaming.scripting.ScriptManager;
import com.ikalagaming.scripting.interpreter.ScriptRuntime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * Smoke tests for the script debugger and script monitor windows, which run them headless to make
 * sure they don't report GUI errors, and that the debugger controls work.
 */
class ScriptToolsTest {
    private static final int WIDTH = 1600;
    private static final int HEIGHT = 900;

    /** A script with a loop, and a line without any code. */
    private static final String SCRIPT =
            """
            int x;
            x += 1;

            for (int i = 0; i < 3; ++i) {
            \tx += i;
            }
            x = x * 2;
            """;

    private Context context;

    @BeforeEach
    void setUp() throws IOException {
        context = IkGui.createContext();
        // Don't read or write an .ini file
        context.io.iniFilename = null;
        context.io.displaySize.set(WIDTH, HEIGHT);
        try (InputStream stream = getClass().getResourceAsStream("/fonts/NotoSans.ttf")) {
            assertNotNull(stream, "Missing test font");
            assertTrue(context.io.fonts.loadFont("NotoSans", stream.readAllBytes()));
        }
        IkGui.setFont("NotoSans", 16);
    }

    @AfterEach
    void tearDown() {
        IkGui.destroyContext();
        ScriptManager.shutdown();
    }

    private void frames(int count, Runnable ui) {
        for (int i = 0; i < count; ++i) {
            IkGui.newFrame();
            ui.run();
            IkGui.render();
        }
        assertNoErrors();
    }

    private void assertNoErrors() {
        assertFalse(
                context.debugLogBuffer.snapshot().stream()
                        .anyMatch(line -> line.contains("[ikgui-error]")),
                context.debugLogBuffer.getText());
    }

    /**
     * Wait for something to happen on the script runner thread.
     *
     * @param condition The condition to wait for.
     */
    private static void waitUntil(BooleanSupplier condition) throws InterruptedException {
        final long end = System.currentTimeMillis() + 5000;
        while (!condition.getAsBoolean()) {
            assertTrue(System.currentTimeMillis() < end, "Timed out waiting for scripts");
            Thread.sleep(10);
        }
    }

    /** The current line of the script being debugged. */
    private static int currentLine(IkScriptDebugger debugger) {
        final ScriptRuntime runtime = debugger.getRuntime();
        return runtime.getOriginalInstruction(runtime.getProgramCounter()).line();
    }

    @Test
    void debuggerBreakpointsAndStepping() {
        final IkScriptDebugger debugger = new IkScriptDebugger();
        debugger.setVisible(true);
        debugger.setScriptContents(SCRIPT);
        final Runnable ui = () -> debugger.draw(WIDTH, HEIGHT);

        // The editor, before compiling
        frames(3, ui);

        debugger.compile();
        // Switches to the source view
        frames(3, ui);
        final ScriptRuntime runtime = debugger.getRuntime();
        assertNotNull(runtime);

        // Break inside the loop, each time around
        debugger.toggleLineBreakpoint(5);
        assertEquals(1, runtime.getBreakpoints().size());
        for (int i = 0; i < 3; ++i) {
            debugger.startContinuing();
            frames(2, ui);
            assertTrue(debugger.isStoppedAtBreakpoint());
            assertEquals(5, currentLine(debugger));
        }

        // Stepping moves forward past the breakpoint
        final int before = runtime.getProgramCounter();
        debugger.stepInstruction();
        frames(1, ui);
        assertEquals(before + 1, runtime.getProgramCounter());

        // Run to a line without a breakpoint, which doesn't leave a breakpoint behind
        debugger.toggleLineBreakpoint(5);
        debugger.runTo(runtime.getLineStartInstructions(7));
        frames(2, ui);
        assertEquals(7, currentLine(debugger));
        assertTrue(runtime.getBreakpoints().isEmpty());

        // And to the end
        debugger.startContinuing();
        frames(2, ui);
        assertTrue(runtime.hasTerminated());
        assertFalse(runtime.isFatalError());
        assertEquals(8, runtime.getSymbolTable().get("x").value());

        // Breakpoints on lines survive restarting
        debugger.toggleLineBreakpoint(2);
        debugger.restart();
        assertEquals(1, debugger.getRuntime().getBreakpoints().size());
        frames(2, ui);
    }

    @Test
    void debuggerCompileErrors() {
        final IkScriptDebugger debugger = new IkScriptDebugger();
        debugger.setVisible(true);
        debugger.setScriptContents("int x;\nx += ;");
        final Runnable ui = () -> debugger.draw(WIDTH, HEIGHT);

        debugger.compile();
        frames(3, ui);
        assertEquals(null, debugger.getRuntime());
    }

    @Test
    void monitorShowsAndTerminatesScripts() throws InterruptedException {
        final ScriptMonitor monitor = new ScriptMonitor();
        monitor.setVisible(true);
        final Runnable ui = () -> monitor.draw(WIDTH, HEIGHT);

        // Nothing running
        frames(2, ui);

        assertTrue(ScriptManager.runScript("while (true) {}", "Forever"));
        assertTrue(ScriptManager.runScript("yield(\"tag\");", "Waiting"));
        waitUntil(
                () ->
                        !ScriptManager.getRunningScripts().isEmpty()
                                && !ScriptManager.getYieldedScripts().isEmpty());
        frames(3, ui);

        ScriptManager.getRunningScripts().forEach(ScriptManager::terminate);
        ScriptManager.getYieldedScripts().keySet().forEach(ScriptManager::terminate);
        frames(2, ui);
        assertTrue(ScriptManager.getRunningScripts().isEmpty());
        assertTrue(ScriptManager.getYieldedScripts().isEmpty());
    }

    @Test
    void forLoopLineBreakpoints() {
        final IkScriptDebugger debugger = new IkScriptDebugger();
        debugger.setVisible(true);
        debugger.setScriptContents(SCRIPT);
        final Runnable ui = () -> debugger.draw(WIDTH, HEIGHT);
        debugger.compile();
        frames(2, ui);
        final ScriptRuntime runtime = debugger.getRuntime();

        // The for line runs before the loop, then again at the end of each iteration
        final List<Integer> starts = runtime.getLineStartInstructions(4);
        assertEquals(2, starts.size());
        debugger.toggleLineBreakpoint(4);
        assertEquals(Set.copyOf(starts), runtime.getBreakpoints());

        // Once before the loop, then after each of the 3 iterations
        int stops = 0;
        while (!runtime.hasTerminated() && stops < 10) {
            debugger.startContinuing();
            frames(1, ui);
            if (debugger.isStoppedAtBreakpoint()) {
                assertEquals(4, currentLine(debugger));
                ++stops;
            }
        }
        assertEquals(4, stops);

        // Removing it removes all of it, so it doesn't pause any more
        debugger.toggleLineBreakpoint(4);
        assertTrue(runtime.getBreakpoints().isEmpty());
        debugger.restart();
        assertTrue(debugger.getRuntime().getBreakpoints().isEmpty());
        debugger.startContinuing();
        frames(2, ui);
        assertTrue(debugger.getRuntime().hasTerminated());
        assertFalse(debugger.isStoppedAtBreakpoint());
    }

    @Test
    void removingLineRemovesInstructionBreakpoints() {
        final IkScriptDebugger debugger = new IkScriptDebugger();
        debugger.setVisible(true);
        debugger.setScriptContents(SCRIPT);
        final Runnable ui = () -> debugger.draw(WIDTH, HEIGHT);
        debugger.compile();
        frames(2, ui);
        final ScriptRuntime runtime = debugger.getRuntime();

        // A breakpoint on the loop condition, which is on the for line but isn't where it starts
        final int condition = runtime.getLineStartInstructions(4).get(1) + 2;
        assertEquals(4, runtime.getOriginalInstruction(condition).line());
        debugger.toggleInstructionBreakpoint(condition);
        frames(1, ui);

        // Toggling the line removes it, rather than adding another one
        debugger.toggleLineBreakpoint(4);
        assertTrue(runtime.getBreakpoints().isEmpty());
        debugger.startContinuing();
        frames(2, ui);
        assertTrue(runtime.hasTerminated());
    }
}
