package com.ikalagaming.rpg.windows;

import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.frontend.gui.IkGui;
import com.ikalagaming.graphics.frontend.gui.data.IkBoolean;
import com.ikalagaming.graphics.frontend.gui.data.IkString;
import com.ikalagaming.graphics.frontend.gui.enums.ColorType;
import com.ikalagaming.graphics.frontend.gui.enums.Condition;
import com.ikalagaming.graphics.frontend.gui.enums.StyleVariable;
import com.ikalagaming.graphics.frontend.gui.flags.ChildFlags;
import com.ikalagaming.graphics.frontend.gui.flags.InputTextFlags;
import com.ikalagaming.graphics.frontend.gui.flags.WindowFlags;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.scripting.Engine;

import lombok.NonNull;
import org.luaj.vm2.LuaError;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import javax.script.ScriptEngine;
import javax.script.ScriptException;

/**
 * A window for running lua scripts.
 *
 * @author Ches Burks
 */
public class LuaConsole implements GUIWindow {

    /** The input to the console. */
    IkString input;

    /** The lines in the console. */
    List<String> logs;

    /** Commands that are available. */
    List<String> commands;

    /** The command history. */
    List<String> history;

    /**
     * The position in the history. -1 is a new line, 0 through History.Size-1 is browsing history.
     */
    int historyPos;

    /** Whether we are automatically staying scrolled to the bottom when new text shows up. */
    IkBoolean autoScroll;

    /** If we want to scroll to the bottom. */
    IkBoolean scrollToBottom;

    /** We override the scripting context to write here instead of stdout. */
    StringWriter luaOutput;

    /**
     * Add a line to the logs, functions as a string format.
     *
     * @param format The format of the string.
     * @param args The arguments to the format string.
     */
    private void addLog(String format, Object... args) {
        logs.add(String.format(format, args));
    }

    /** Clear out the logs. */
    private void clearLog() {
        logs.clear();
    }

    @Override
    public void draw() {
        IkGui.setNextWindowPos(200, 200, Condition.ONCE);
        IkGui.setNextWindowSize(450, 400, Condition.ONCE);
        IkGui.begin("Lua Console");

        IkGui.textWrapped("This is a console for interacting with the lua engine.");

        if (IkGui.smallButton("Clear")) {
            clearLog();
        }
        IkGui.sameLine();
        boolean copyToClipboard = IkGui.smallButton("Copy to clipboard");

        IkGui.separator();

        // Options menu
        if (IkGui.beginPopup("Options")) {
            IkGui.checkbox("Auto-scroll", autoScroll);
            IkGui.endPopup();
        }

        // Options, Filter
        if (IkGui.button("Options")) {
            IkGui.openPopup("Options");
        }
        IkGui.separator();
        // Reserve enough left-over height for 1 separator + 1 input text
        final float footerHeightToReserve =
                IkGui.getStyle().variable.itemSpacing.y + IkGui.getFrameHeightWithSpacing();
        if (IkGui.beginChild(
                "ScrollingRegion",
                0,
                -footerHeightToReserve,
                ChildFlags.NONE,
                WindowFlags.HORIZONTAL_SCROLLBAR)) {
            if (IkGui.beginPopupContextWindow()) {
                if (IkGui.selectable("Clear")) {
                    clearLog();
                }
                IkGui.endPopup();
            }
            // Tighten spacing
            IkGui.pushStyleVarFloat2(StyleVariable.ITEM_SPACING, 4, 1);
            if (copyToClipboard) {
                IkGui.logToClipboard();
            }
            for (int i = 0; i < logs.size(); i++) {
                String item = logs.get(i);

                boolean hasColor = false;
                if (item.startsWith("[error]")) {
                    IkGui.pushStyleColor(ColorType.TEXT, 1.0f, 0.4f, 0.4f, 1.0f);
                    hasColor = true;
                } else if (item.startsWith("-- ")) {
                    IkGui.pushStyleColor(ColorType.TEXT, 1.0f, 0.8f, 0.6f, 1.0f);
                    hasColor = true;
                }
                IkGui.textUnformatted(item);
                if (hasColor) {
                    IkGui.popStyleColor();
                }
            }
            if (copyToClipboard) {
                IkGui.logFinish();
            }

            /*
             * Keep up at the bottom of the scroll region if we were already at
             * the bottom at the beginning of the frame. Using a scrollbar or
             * mouse-wheel will take away from the bottom edge.
             */
            if (scrollToBottom.get()
                    || (autoScroll.get() && IkGui.getScrollY() >= IkGui.getScrollMaxY())) {
                IkGui.setScrollHereY(1.0f);
            }
            scrollToBottom.set(false);

            IkGui.popStyleVar();
        }
        IkGui.endChild();
        IkGui.separator();

        // Command-line
        boolean reclaimFocus = false;
        int inputTextFlags =
                InputTextFlags.ENTER_RETURNS_TRUE
                        | InputTextFlags.CALLBACK_COMPLETION
                        | InputTextFlags.CALLBACK_HISTORY;
        if (IkGui.inputText("Input", input, inputTextFlags)) {
            String s = input.get().trim();
            input.clear();
            if (!s.trim().isEmpty()) {
                executeCommand(s);
            }
            reclaimFocus = true;
        }

        // Auto-focus on window apparition
        IkGui.setItemDefaultFocus();
        if (reclaimFocus) {
            IkGui.setKeyboardFocusHere(-1); // Auto focus previous widget
        }

        IkGui.end();
    }

    /**
     * Run a command.
     *
     * @param command The command to run.
     */
    private void executeCommand(String command) {
        addLog("-- %s\n", command);

        history.removeIf(entry -> entry.equalsIgnoreCase(command));
        history.add(command);

        // Process command
        if ("CLEAR".equalsIgnoreCase(command)) {
            clearLog();
        } else {
            ScriptEngine engine = Engine.getLuaEngine();
            try {
                engine.eval(command);
                addLog(luaOutput.toString());
                luaOutput.getBuffer().setLength(0);
            } catch (ScriptException | LuaError e) {
                addLog("[error] %s", e.getMessage());
            }
        }

        // On command input, we scroll to bottom even if AutoScroll==false
        scrollToBottom.set(true);
    }

    @Override
    public void handleGuiInput(@NonNull Scene scene, @NonNull Window window) {}

    @Override
    public void setup(@NonNull Scene scene) {
        input = new IkString(256);
        autoScroll = new IkBoolean();
        scrollToBottom = new IkBoolean();
        logs = new ArrayList<>();
        commands = new ArrayList<>();
        commands.add("HELP");
        commands.add("HISTORY");
        commands.add("CLEAR");
        commands.add("LUA");
        history = new ArrayList<>();
        ScriptEngine engine = Engine.getLuaEngine();
        luaOutput = new StringWriter();
        engine.getContext().setWriter(luaOutput);
    }
}
