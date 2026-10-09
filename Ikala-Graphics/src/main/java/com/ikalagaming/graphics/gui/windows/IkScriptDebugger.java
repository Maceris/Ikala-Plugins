package com.ikalagaming.graphics.gui.windows;

import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.gui.component.GuiWindow;
import com.ikalagaming.graphics.gui.data.IkString;
import com.ikalagaming.graphics.gui.enums.ColorType;
import com.ikalagaming.graphics.gui.enums.Condition;
import com.ikalagaming.graphics.gui.flags.ChildFlags;
import com.ikalagaming.graphics.gui.flags.InputTextFlags;
import com.ikalagaming.graphics.gui.flags.SelectableFlags;
import com.ikalagaming.graphics.gui.flags.TabItemFlags;
import com.ikalagaming.graphics.gui.flags.TableColumnFlags;
import com.ikalagaming.graphics.gui.flags.TableFlags;
import com.ikalagaming.graphics.gui.flags.TreeNodeFlags;
import com.ikalagaming.graphics.gui.flags.WindowFlags;
import com.ikalagaming.graphics.gui.util.Alignment;
import com.ikalagaming.graphics.gui.util.Color;
import com.ikalagaming.scripting.IkalaScriptCompiler;
import com.ikalagaming.scripting.IkalaScriptCompiler.CompileResult;
import com.ikalagaming.scripting.ScriptDiagnostics;
import com.ikalagaming.scripting.ScriptDiagnostics.Diagnostic;
import com.ikalagaming.scripting.ScriptManager;
import com.ikalagaming.scripting.ast.SyntaxTreePrinter;
import com.ikalagaming.scripting.interpreter.Instruction;
import com.ikalagaming.scripting.interpreter.InstructionFormatter;
import com.ikalagaming.scripting.interpreter.MemoryItem;
import com.ikalagaming.scripting.interpreter.ScriptRuntime;

import lombok.NonNull;
import org.antlr.v4.runtime.CharStreams;
import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * A window for writing, compiling, and debugging Ikala scripts. Scripts are run in a separate
 * runtime that only this window steps, so they don't interfere with the script manager.
 *
 * <p>Breakpoints can be set on lines of source or on individual instructions, and are kept when the
 * script is restarted. Breakpoints on lines are also kept when recompiling, as long as the line
 * still has code on it.
 *
 * @author Ches Burks
 */
public class IkScriptDebugger extends GuiWindow {

    public static final String WINDOW_NAME = "Ikala Script Debugger";

    /** The name scripts get when they are run in the background from the debugger. */
    private static final String BACKGROUND_SCRIPT_NAME = "Script Debugger";

    /** The most instructions to run per frame when continuing, so infinite loops don't hang. */
    private static final int MAX_INSTRUCTIONS_PER_FRAME = 1000;

    /** The color for errors. */
    private static final int ERROR_COLOR = Color.rgba(1.0f, 0.4f, 0.4f, 1.0f);

    /** The color for the current line or instruction. */
    private static final int CURRENT_COLOR = Color.rgba(0.35f, 0.65f, 1.0f, 1.0f);

    /** The color of breakpoint markers. */
    private static final int BREAKPOINT_COLOR = Color.rgba(0.9f, 0.2f, 0.2f, 1.0f);

    /** The color of the marker shown when hovering where a breakpoint could go. */
    private static final int BREAKPOINT_HOVER_COLOR = Color.rgba(0.9f, 0.2f, 0.2f, 0.35f);

    /** The number of rows of bytecode to show before scrolling. */
    private static final int BYTECODE_VISIBLE_ROWS = 14;

    /**
     * Format the value of a memory item.
     *
     * @param item The item to format.
     * @return The value as a string.
     */
    private static String formatValue(@NonNull MemoryItem item) {
        final Object value = item.value();
        if (value instanceof String string) {
            return '"' + string + '"';
        }
        if (value instanceof Character character) {
            return "'" + character + "'";
        }
        return String.valueOf(value);
    }

    /**
     * Draw a problem with the script, with where it is and the line of code it's on.
     *
     * @param problem The problem.
     * @param sourceLines The lines of the script, to show the line the problem is on.
     */
    private static void drawProblem(
            @NonNull Diagnostic problem, @NonNull List<String> sourceLines) {
        IkGui.pushStyleColor(ColorType.TEXT, ERROR_COLOR);
        IkGui.textWrapped(problem.toString());
        IkGui.popStyleColor();
        final int line = problem.line();
        if (line > 0 && line <= sourceLines.size()) {
            IkGui.indent();
            IkGui.textDisabled(sourceLines.get(line - 1).strip());
            IkGui.unindent();
        }
    }

    /** The script being edited. */
    private final IkString source;

    /** The source as of the last successful compile, split into lines, for the source view. */
    private List<String> compiledLines = List.of();

    /** The source as of the last compile attempt, split into lines, for showing errors. */
    private List<String> attemptedLines = List.of();

    /** The whole source as of the last compile, to tell if it has been edited since. */
    private String compiledSource = "";

    /** The results of the last compile, null if we have not compiled yet. */
    private CompileResult compileResult;

    /** The syntax tree from the last compile, as text. */
    private String syntaxTreeText = "";

    /** The instructions from the last successful compile, formatted for display. */
    private final List<String> instructionStrings = new ArrayList<>();

    /** The source line of each instruction from the last successful compile. */
    private final List<Integer> instructionLines = new ArrayList<>();

    /** The runtime we are stepping through, null if the script has not been compiled. */
    private ScriptRuntime runtime;

    /** Lines of source that have breakpoints, starting at 1. */
    private final TreeSet<Integer> lineBreakpoints = new TreeSet<>();

    /** Instructions that have breakpoints, which are forgotten when recompiling. */
    private final TreeSet<Integer> instructionBreakpoints = new TreeSet<>();

    /** Temporary breakpoints for running to a specific line or instruction. */
    private final TreeSet<Integer> runToInstructions = new TreeSet<>();

    /** The instruction of the breakpoint we stopped at, -1 if we are not stopped at one. */
    private int stoppedAtBreakpoint = -1;

    /** Problems that happened while running the script in the debugger. */
    private final List<Diagnostic> runtimeErrors = new ArrayList<>();

    /** A message about the last time we ran a script in the background, null if there is none. */
    private String backgroundMessage;

    /** Whether the script is currently yielded, and waiting to be resumed. */
    private boolean yielded;

    /** The tag the script yielded with, null if it yielded without one. */
    private String yieldTag;

    /** Whether we are running instructions every frame, until something stops us. */
    private boolean continuing;

    /** The program counter the bytecode view last scrolled to, so we only scroll on changes. */
    private int lastScrolledInstruction = -1;

    /** The line the source view last scrolled to, so we only scroll on changes. */
    private int lastScrolledLine = -1;

    /** A tab to switch to next frame, or null to leave the tabs alone. */
    private String tabToSelect;

    /** Used to look up item positions, to avoid allocating every frame. */
    private final Vector2f itemMin = new Vector2f();

    /** Used to look up item positions, to avoid allocating every frame. */
    private final Vector2f itemMax = new Vector2f();

    public IkScriptDebugger() {
        super(WINDOW_NAME, WindowFlags.NO_SCROLLBAR | WindowFlags.NO_SCROLL_WITH_MOUSE);
        setScale(0.66f, 0.6f);
        setDisplacement(0.25f, 0.03f);
        setAlignment(Alignment.NORTH_WEST);

        source = new IkString(4096);
        source.inputData.isResizable = true;
    }

    /**
     * Replace the contents of the script editor.
     *
     * @param contents The new script text.
     */
    public void setScriptContents(@NonNull String contents) {
        source.set(contents);
    }

    @Override
    public void draw(final int width, final int height) {
        IkGui.setNextWindowViewport(IkGui.getMainViewport().id);
        IkGui.setNextWindowPos(
                getActualDisplaceX() * width, getActualDisplaceY() * height, Condition.ONCE);
        IkGui.setNextWindowSize(
                getActualWidth() * width, getActualHeight() * height, Condition.ONCE);
        IkGui.begin(title, windowOpen, windowFlags);

        if (isVisible()) {
            recalculate();
            if (continuing) {
                continueRunning();
            }
            drawToolbar();
            IkGui.separator();
            drawBody();
        }

        IkGui.end();
    }

    /**
     * The runtime being debugged, for tests.
     *
     * @return The runtime, or null if the script has not been compiled.
     */
    ScriptRuntime getRuntime() {
        return runtime;
    }

    /**
     * Whether we are stopped at a breakpoint, for tests.
     *
     * @return True if the last thing that stopped the script was a breakpoint.
     */
    boolean isStoppedAtBreakpoint() {
        return stoppedAtBreakpoint >= 0;
    }

    /** Draw the buttons for compiling and controlling the script, and its status. */
    private void drawToolbar() {
        if (IkGui.button("Compile")) {
            compile();
        }
        IkGui.setItemTooltip("Compile the script, so it can be debugged");
        IkGui.sameLine();
        if (IkGui.button("Run in background")) {
            runInBackground();
        }
        IkGui.setItemTooltip("Run the script with the script manager, like a game would");

        IkGui.sameLine();
        IkGui.textDisabled("|");
        IkGui.sameLine();

        final boolean canRun = runtime != null && !runtime.hasTerminated() && !yielded;
        IkGui.beginDisabled(!canRun || continuing);
        if (IkGui.button("Step")) {
            stepInstruction();
        }
        IkGui.setItemTooltip("Run one instruction");
        IkGui.endDisabled();

        IkGui.sameLine();
        if (continuing) {
            if (IkGui.button("Pause")) {
                stopContinuing();
            }
        } else {
            IkGui.beginDisabled(!canRun);
            if (IkGui.button("Continue")) {
                startContinuing();
            }
            IkGui.setItemTooltip(
                    "Run until a breakpoint, or the script finishes, yields, or fails");
            IkGui.endDisabled();
        }

        IkGui.sameLine();
        IkGui.beginDisabled(!yielded);
        if (IkGui.button("Resume")) {
            yielded = false;
            yieldTag = null;
        }
        IkGui.setItemTooltip("Resume after the script yields");
        IkGui.endDisabled();

        IkGui.sameLine();
        IkGui.beginDisabled(runtime == null);
        if (IkGui.button("Restart")) {
            restart();
        }
        IkGui.setItemTooltip("Start the compiled script over from the beginning");
        IkGui.endDisabled();

        IkGui.sameLine();
        IkGui.beginDisabled(lineBreakpoints.isEmpty() && instructionBreakpoints.isEmpty());
        if (IkGui.button("Clear breakpoints")) {
            clearAllBreakpoints();
        }
        IkGui.endDisabled();

        IkGui.sameLine();
        IkGui.textDisabled("|");
        IkGui.sameLine();
        drawStatus();

        for (Diagnostic error : runtimeErrors) {
            drawProblem(error, compiledLines);
        }
        if (backgroundMessage != null) {
            IkGui.textDisabled(backgroundMessage);
        }
    }

    /** Draw the current state of the script. */
    private void drawStatus() {
        if (compileResult == null) {
            IkGui.textDisabled("Not compiled");
        } else if (!compileResult.succeeded()) {
            IkGui.textColored(ERROR_COLOR, "Compile failed");
        } else if (runtime.isFatalError()) {
            IkGui.textColored(ERROR_COLOR, "Halted with an error");
        } else if (runtime.hasTerminated()) {
            IkGui.text("Finished");
        } else if (yielded) {
            IkGui.text(
                    yieldTag == null
                            ? "Yielded without a tag"
                            : String.format("Yielded with tag \"%s\"", yieldTag));
        } else if (continuing) {
            IkGui.text("Running");
        } else {
            final String where = describeInstruction(runtime.getProgramCounter());
            IkGui.text(
                    stoppedAtBreakpoint >= 0
                            ? "Stopped at a breakpoint, " + where
                            : "Paused at " + where);
        }
    }

    /**
     * Describe where an instruction is, for the status.
     *
     * @param index The index of the instruction.
     * @return The instruction number, and the line if known.
     */
    private String describeInstruction(int index) {
        final int line = getLine(index);
        return line > 0
                ? String.format("instruction %04d (line %d)", index, line)
                : String.format("instruction %04d", index);
    }

    /**
     * Fetch the source line an instruction came from.
     *
     * @param index The index of the instruction.
     * @return The line, or -1 if it is unknown or out of range.
     */
    private int getLine(int index) {
        if (index < 0 || index >= instructionLines.size()) {
            return -1;
        }
        return instructionLines.get(index);
    }

    /** Draw the editor and the script state side by side. */
    private void drawBody() {
        final float bodyHeight = IkGui.getContentRegionAvailableY();
        if (!IkGui.beginTable(
                "Layout", 2, TableFlags.RESIZABLE | TableFlags.BORDERS_INNER_V, 0.0f, bodyHeight)) {
            return;
        }
        IkGui.tableSetupColumn("Source", TableColumnFlags.WIDTH_STRETCH, 0.45f);
        IkGui.tableSetupColumn("State", TableColumnFlags.WIDTH_STRETCH, 0.55f);

        IkGui.tableNextRow();
        IkGui.tableNextColumn();
        drawSourceTabs();

        IkGui.tableNextColumn();
        if (IkGui.beginChild("State", 0.0f, bodyHeight, ChildFlags.NONE)) {
            drawState();
        }
        IkGui.endChild();

        IkGui.endTable();
    }

    /** Draw the tabs for editing the script, and viewing the compiled source. */
    private void drawSourceTabs() {
        if (!IkGui.beginTabBar("SourceTabs")) {
            return;
        }
        if (IkGui.beginTabItem("Editor", tabFlags("Editor"))) {
            drawEditor();
            IkGui.endTabItem();
        }
        IkGui.setItemTooltip("Edit the script");
        IkGui.beginDisabled(runtime == null);
        if (IkGui.beginTabItem("Source", tabFlags("Source"))) {
            if (runtime != null) {
                drawSourceView();
            }
            IkGui.endTabItem();
        }
        IkGui.setItemTooltip("The compiled script, where you can set breakpoints on lines");
        IkGui.endDisabled();
        IkGui.endTabBar();
        tabToSelect = null;
    }

    /**
     * Fetch the flags for a source tab, selecting it if we want to switch to it.
     *
     * @param tab The name of the tab.
     * @return The tab item flags.
     */
    private int tabFlags(@NonNull String tab) {
        return tab.equals(tabToSelect) ? TabItemFlags.SET_SELECTED : TabItemFlags.NONE;
    }

    /** Draw the script editor, with compile errors under it. */
    private void drawEditor() {
        final float height = IkGui.getContentRegionAvailableY();
        final List<Diagnostic> errors = compileResult == null ? List.of() : compileResult.errors();
        float errorsHeight = 0.0f;
        if (!errors.isEmpty()) {
            // Room for a few problems, with their lines of code
            final int lines = Math.min(errors.size() * 2, 8) + 1;
            errorsHeight = lines * IkGui.getTextLineHeightWithSpacing() + 8.0f;
        }

        IkGui.inputTextMultiline(
                "##Source",
                source,
                IkGui.getContentRegionAvailableX(),
                Math.max(height - errorsHeight, IkGui.getFrameHeight() * 3),
                InputTextFlags.ALLOW_TAB_INPUT);

        if (!errors.isEmpty()) {
            if (IkGui.beginChild("Errors", 0.0f, 0.0f, ChildFlags.BORDERS)) {
                IkGui.text(String.format("%d problem(s):", errors.size()));
                for (Diagnostic error : errors) {
                    drawProblem(error, attemptedLines);
                }
            }
            IkGui.endChild();
        }
    }

    /** Draw the compiled source with line numbers, breakpoints, and the current line. */
    private void drawSourceView() {
        if (!compiledSource.equals(source.get())) {
            IkGui.textColored(
                    ERROR_COLOR,
                    "The script was edited since it was compiled, recompile to debug it");
        }
        if (!IkGui.beginTable(
                "SourceLines",
                3,
                TableFlags.SCROLL_Y | TableFlags.SIZING_FIXED_FIT,
                0.0f,
                IkGui.getContentRegionAvailableY())) {
            return;
        }
        IkGui.tableSetupColumn("##Breakpoint", TableColumnFlags.WIDTH_FIXED);
        IkGui.tableSetupColumn("##Line", TableColumnFlags.WIDTH_FIXED);
        IkGui.tableSetupColumn("##Text", TableColumnFlags.WIDTH_STRETCH);

        final int currentLine = getLine(runtime.getProgramCounter());
        for (int i = 0; i < compiledLines.size(); ++i) {
            final int line = i + 1;
            final List<Integer> lineStarts = runtime.getLineStartInstructions(line);
            IkGui.tableNextRow();

            IkGui.tableNextColumn();
            if (!lineStarts.isEmpty()
                    && drawBreakpointToggle("##LineBreakpoint" + line, lineHasBreakpoint(line))) {
                toggleLineBreakpoint(line);
            }

            IkGui.tableNextColumn();
            IkGui.textDisabled(Integer.toString(line));

            IkGui.tableNextColumn();
            final boolean current = line == currentLine && !runtime.hasTerminated();
            if (current) {
                IkGui.pushStyleColor(ColorType.TEXT, CURRENT_COLOR);
            }
            IkGui.selectable(
                    compiledLines.get(i) + "##Line" + line,
                    current,
                    SelectableFlags.SPAN_ALL_COLUMNS | SelectableFlags.ALLOW_OVERLAP);
            if (current) {
                IkGui.popStyleColor();
                if (lastScrolledLine != line) {
                    IkGui.setScrollHereY();
                    lastScrolledLine = line;
                }
            }
            if (!lineStarts.isEmpty() && IkGui.beginPopupContextItem("##LineMenu" + line)) {
                drawBreakpointMenu(
                        lineHasBreakpoint(line), lineStarts, () -> toggleLineBreakpoint(line));
                IkGui.endPopup();
            }
        }
        IkGui.endTable();
    }

    /**
     * Draw a right click menu for a breakpoint location.
     *
     * @param hasBreakpoint Whether there is a breakpoint there already.
     * @param instructions The instructions to run to.
     * @param toggle Toggles the breakpoint.
     */
    private void drawBreakpointMenu(
            boolean hasBreakpoint, @NonNull List<Integer> instructions, @NonNull Runnable toggle) {
        if (IkGui.menuItem(hasBreakpoint ? "Remove breakpoint" : "Add breakpoint")) {
            toggle.run();
        }
        final boolean canRun = !runtime.hasTerminated() && !yielded;
        if (IkGui.menuItem("Run to here", null, false, canRun)) {
            runTo(instructions);
        }
    }

    /**
     * Draw a clickable spot for a breakpoint, with a marker if there is a breakpoint.
     *
     * @param id The ID of the button.
     * @param set Whether there is a breakpoint there.
     * @return True if it was clicked, so the breakpoint should be toggled.
     */
    private boolean drawBreakpointToggle(@NonNull String id, boolean set) {
        final float size = IkGui.getTextLineHeight();
        final boolean clicked = IkGui.invisibleButton(id, size, size);
        final boolean hovered = IkGui.isItemHovered();
        IkGui.setItemTooltip(set ? "Remove breakpoint" : "Add breakpoint");
        if (set || hovered) {
            IkGui.getItemRectMin(itemMin);
            IkGui.getItemRectMax(itemMax);
            IkGui.getWindowDrawList()
                    .addCircleFilled(
                            (itemMin.x + itemMax.x) * 0.5f,
                            (itemMin.y + itemMax.y) * 0.5f,
                            size * 0.3f,
                            set ? BREAKPOINT_COLOR : BREAKPOINT_HOVER_COLOR);
        }
        return clicked;
    }

    /** Draw the syntax tree, bytecode, variables, and stack. */
    private void drawState() {
        if (compileResult == null) {
            IkGui.textWrapped("Compile the script to see the syntax tree and bytecode.");
            return;
        }

        if (IkGui.collapsingHeader("Syntax tree")) {
            if (syntaxTreeText.isEmpty()) {
                IkGui.textDisabled("No syntax tree, the script could not be parsed.");
            } else {
                if (IkGui.button("Copy to clipboard")) {
                    IkGui.setClipboardText(syntaxTreeText);
                }
                if (IkGui.beginChild(
                        "SyntaxTree",
                        0.0f,
                        IkGui.getTextLineHeightWithSpacing() * 12,
                        ChildFlags.BORDERS | ChildFlags.RESIZE_Y,
                        WindowFlags.HORIZONTAL_SCROLLBAR)) {
                    IkGui.textUnformatted(syntaxTreeText);
                }
                IkGui.endChild();
            }
        }

        if (runtime == null) {
            return;
        }

        if (IkGui.collapsingHeader("Bytecode", TreeNodeFlags.DEFAULT_OPEN)) {
            drawBytecode();
        }
        if (IkGui.collapsingHeader("Registers", TreeNodeFlags.DEFAULT_OPEN)) {
            drawRegisters();
        }
        if (IkGui.collapsingHeader("Stack", TreeNodeFlags.DEFAULT_OPEN)) {
            drawStack();
        }
    }

    /** Draw the instructions, highlighting the current one. */
    private void drawBytecode() {
        final int programCounter = runtime.getProgramCounter();
        final float tableHeight =
                IkGui.getTextLineHeightWithSpacing()
                        * (Math.min(instructionStrings.size(), BYTECODE_VISIBLE_ROWS) + 1.5f);
        if (!IkGui.beginTable(
                "Bytecode",
                4,
                TableFlags.ROW_BACKGROUND
                        | TableFlags.BORDERS_INNER_V
                        | TableFlags.SCROLL_Y
                        | TableFlags.RESIZABLE,
                0.0f,
                tableHeight)) {
            return;
        }
        IkGui.tableSetupScrollFreeze(0, 1);
        IkGui.tableSetupColumn("##Breakpoint", TableColumnFlags.WIDTH_FIXED);
        IkGui.tableSetupColumn("#", TableColumnFlags.WIDTH_FIXED);
        IkGui.tableSetupColumn("Line", TableColumnFlags.WIDTH_FIXED);
        IkGui.tableSetupColumn("Instruction", TableColumnFlags.WIDTH_STRETCH);
        IkGui.tableHeadersRow();

        for (int i = 0; i < instructionStrings.size(); ++i) {
            final int index = i;
            IkGui.tableNextRow();

            IkGui.tableNextColumn();
            if (drawBreakpointToggle("##InstructionBreakpoint" + i, runtime.hasBreakpoint(i))) {
                toggleInstructionBreakpoint(i);
            }

            final boolean current = i == programCounter;
            if (current) {
                IkGui.pushStyleColor(ColorType.TEXT, CURRENT_COLOR);
            }
            IkGui.tableNextColumn();
            IkGui.selectable(
                    String.format("%04d##Instruction%d", i, i),
                    current,
                    SelectableFlags.SPAN_ALL_COLUMNS | SelectableFlags.ALLOW_OVERLAP);
            if (IkGui.beginPopupContextItem("##InstructionMenu" + i)) {
                drawBreakpointMenu(
                        runtime.hasBreakpoint(i),
                        List.of(i),
                        () -> toggleInstructionBreakpoint(index));
                IkGui.endPopup();
            }
            IkGui.tableNextColumn();
            final int line = getLine(i);
            IkGui.text(line > 0 ? Integer.toString(line) : "");
            IkGui.tableNextColumn();
            IkGui.text(instructionStrings.get(i));
            if (current) {
                IkGui.popStyleColor();
                if (lastScrolledInstruction != programCounter) {
                    IkGui.setScrollHereY();
                    lastScrolledInstruction = programCounter;
                }
            }
        }
        IkGui.endTable();
    }

    /** Draw the variables and the last comparison. */
    private void drawRegisters() {
        IkGui.text("Last comparison: " + runtime.getLastComparison());
        final Map<String, MemoryItem> variables = new TreeMap<>(runtime.getSymbolTable());
        if (variables.isEmpty()) {
            IkGui.textDisabled("No variables");
            return;
        }
        if (!IkGui.beginTable(
                "Registers", 3, TableFlags.ROW_BACKGROUND | TableFlags.BORDERS_INNER_V)) {
            return;
        }
        IkGui.tableSetupColumn("Name");
        IkGui.tableSetupColumn("Type");
        IkGui.tableSetupColumn("Value");
        IkGui.tableHeadersRow();
        for (var entry : variables.entrySet()) {
            IkGui.tableNextRow();
            IkGui.tableNextColumn();
            IkGui.text(entry.getKey());
            IkGui.tableNextColumn();
            IkGui.text(entry.getValue().type().getSimpleName());
            IkGui.tableNextColumn();
            IkGui.text(formatValue(entry.getValue()));
        }
        IkGui.endTable();
    }

    /** Draw the stack, with the top first. */
    private void drawStack() {
        if (runtime.getStack().isEmpty()) {
            IkGui.textDisabled("The stack is empty");
            return;
        }
        if (!IkGui.beginTable("Stack", 3, TableFlags.ROW_BACKGROUND | TableFlags.BORDERS_INNER_V)) {
            return;
        }
        IkGui.tableSetupColumn("Depth", TableColumnFlags.WIDTH_FIXED);
        IkGui.tableSetupColumn("Type");
        IkGui.tableSetupColumn("Value");
        IkGui.tableHeadersRow();
        int depth = 0;
        for (MemoryItem item : runtime.getStack()) {
            IkGui.tableNextRow();
            IkGui.tableNextColumn();
            IkGui.text(depth == 0 ? "top" : Integer.toString(depth));
            IkGui.tableNextColumn();
            IkGui.text(item.type().getSimpleName());
            IkGui.tableNextColumn();
            IkGui.text(formatValue(item));
            ++depth;
        }
        IkGui.endTable();
    }

    /**
     * Check if any instruction from a line of source has a breakpoint.
     *
     * @param line The line, starting at 1.
     * @return True if there is a breakpoint on the line.
     */
    private boolean lineHasBreakpoint(int line) {
        for (int i = 0; i < instructionLines.size(); ++i) {
            if (instructionLines.get(i) == line && runtime.hasBreakpoint(i)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Toggle a breakpoint on a line of source. Adding one puts a breakpoint everywhere execution
     * enters the line, and removing one removes every breakpoint on the line, including ones set on
     * its instructions.
     *
     * @param line The line, starting at 1.
     */
    void toggleLineBreakpoint(int line) {
        final List<Integer> starts = runtime.getLineStartInstructions(line);
        if (starts.isEmpty()) {
            return;
        }
        if (lineHasBreakpoint(line)) {
            lineBreakpoints.remove(line);
            for (int i = 0; i < instructionLines.size(); ++i) {
                if (instructionLines.get(i) == line) {
                    instructionBreakpoints.remove(i);
                    runToInstructions.remove(i);
                    runtime.clearBreakpoint(i);
                }
            }
        } else {
            lineBreakpoints.add(line);
            starts.forEach(runtime::setBreakpoint);
        }
    }

    /**
     * Toggle a breakpoint on an instruction.
     *
     * @param index The index of the instruction.
     */
    void toggleInstructionBreakpoint(int index) {
        if (!runtime.hasBreakpoint(index)) {
            instructionBreakpoints.add(index);
            runtime.setBreakpoint(index);
            return;
        }
        instructionBreakpoints.remove(index);
        runToInstructions.remove(index);
        final int line = getLine(index);
        if (line > 0 && lineBreakpoints.remove(line)) {
            // The line breakpoint no longer covers the whole line, so keep the rest of it as
            // breakpoints on instructions
            for (int start : runtime.getLineStartInstructions(line)) {
                if (start != index) {
                    instructionBreakpoints.add(start);
                }
            }
        }
        runtime.clearBreakpoint(index);
    }

    /** Remove every breakpoint. */
    private void clearAllBreakpoints() {
        lineBreakpoints.clear();
        instructionBreakpoints.clear();
        runToInstructions.clear();
        if (runtime != null) {
            runtime.clearBreakpoints();
        }
    }

    /**
     * Check if the user wants a breakpoint on an instruction, as opposed to a temporary one.
     *
     * @param index The index of the instruction.
     * @return True if there is a line or instruction breakpoint there.
     */
    private boolean isUserBreakpoint(int index) {
        if (instructionBreakpoints.contains(index)) {
            return true;
        }
        final int line = getLine(index);
        return line > 0
                && lineBreakpoints.contains(line)
                && runtime.getLineStartInstructions(line).contains(index);
    }

    /**
     * Run until we reach any of the given instructions, using temporary breakpoints.
     *
     * @param indices The indices of the instructions.
     */
    void runTo(@NonNull List<Integer> indices) {
        removeRunToBreakpoints();
        for (int index : indices) {
            if (!runtime.hasBreakpoint(index)) {
                runToInstructions.add(index);
                runtime.setBreakpoint(index);
            }
        }
        startContinuing();
    }

    /** Remove the temporary breakpoints for running to a line or instruction. */
    private void removeRunToBreakpoints() {
        if (runtime != null) {
            for (int index : runToInstructions) {
                if (!isUserBreakpoint(index)) {
                    runtime.clearBreakpoint(index);
                }
            }
        }
        runToInstructions.clear();
    }

    /**
     * Called when the script hits a breakpoint.
     *
     * @param index The index of the instruction with the breakpoint.
     */
    private void onBreakpoint(int index) {
        stoppedAtBreakpoint = index;
        stopContinuing();
    }

    /** Compile the script, and set up a runtime to step through if it worked. */
    void compile() {
        final String text = source.get();
        compileResult = IkalaScriptCompiler.compile(CharStreams.fromString(text));
        attemptedLines = List.of(text.split("\n", -1));
        syntaxTreeText =
                compileResult.syntaxTree() == null
                        ? ""
                        : SyntaxTreePrinter.print(compileResult.syntaxTree());

        instructionStrings.clear();
        instructionLines.clear();
        instructionBreakpoints.clear();
        runToInstructions.clear();
        runtime = null;
        compileResult
                .runtime()
                .ifPresent(
                        compiled -> {
                            for (Instruction instruction : compiled.getInstructions()) {
                                instructionStrings.add(InstructionFormatter.format(instruction));
                                instructionLines.add(instruction.line());
                            }
                            // Keep line breakpoints on lines that still have code
                            lineBreakpoints.removeIf(
                                    line -> compiled.getLineStartInstructions(line).isEmpty());
                        });
        if (compileResult.succeeded()) {
            compiledSource = text;
            compiledLines = List.of(text.split("\n", -1));
            tabToSelect = "Source";
        } else {
            tabToSelect = "Editor";
        }
        restart();
    }

    /** Start the compiled script over, in a fresh runtime, keeping breakpoints. */
    void restart() {
        continuing = false;
        yielded = false;
        yieldTag = null;
        stoppedAtBreakpoint = -1;
        runToInstructions.clear();
        runtimeErrors.clear();
        lastScrolledInstruction = -1;
        lastScrolledLine = -1;
        if (compileResult == null || !compileResult.succeeded()) {
            runtime = null;
            return;
        }
        runtime = new ScriptRuntime(compileResult.runtime().get().getInstructions());
        runtime.setYieldHandler(
                (script, tag) -> {
                    yielded = true;
                    yieldTag = tag;
                    stopContinuing();
                });
        runtime.setBreakpointHandler((script, index) -> onBreakpoint(index));
        for (int line : lineBreakpoints) {
            runtime.getLineStartInstructions(line).forEach(runtime::setBreakpoint);
        }
        for (int index : instructionBreakpoints) {
            runtime.setBreakpoint(index);
        }
    }

    /** Run the script until something stops it. */
    void startContinuing() {
        stoppedAtBreakpoint = -1;
        continuing = true;
    }

    /** Stop running the script every frame, removing any temporary breakpoint. */
    private void stopContinuing() {
        continuing = false;
        removeRunToBreakpoints();
    }

    /** Run one instruction, collecting any problems. */
    private void stepRuntime() {
        if (runtime == null || runtime.hasTerminated() || yielded) {
            return;
        }
        ScriptDiagnostics.collect(
                runtimeErrors,
                () -> {
                    try {
                        runtime.step();
                    } catch (Exception e) {
                        runtimeErrors.add(
                                new Diagnostic(runtime.getCurrentLine(), -1, e.toString()));
                        runtime.halt();
                    }
                });
    }

    /**
     * Run one instruction. If that hits a breakpoint without running the instruction, run it, since
     * stepping should always move forward.
     */
    void stepInstruction() {
        final int before = runtime.getProgramCounter();
        stoppedAtBreakpoint = -1;
        stepRuntime();
        if (stoppedAtBreakpoint == before && runtime.getProgramCounter() == before) {
            stoppedAtBreakpoint = -1;
            stepRuntime();
        }
    }

    /** Run instructions until something stops us, or we run out of instructions for the frame. */
    private void continueRunning() {
        for (int i = 0; i < MAX_INSTRUCTIONS_PER_FRAME && continuing; ++i) {
            if (runtime == null || runtime.hasTerminated() || yielded) {
                stopContinuing();
                return;
            }
            stepRuntime();
        }
        if (runtime == null || runtime.hasTerminated()) {
            stopContinuing();
        }
    }

    /** Run the script with the script manager. */
    private void runInBackground() {
        if (ScriptManager.runScript(source.get(), BACKGROUND_SCRIPT_NAME)) {
            backgroundMessage = "Started the script in the background, see the script monitor";
        } else {
            backgroundMessage = null;
            // Show why it failed
            compile();
        }
    }
}
