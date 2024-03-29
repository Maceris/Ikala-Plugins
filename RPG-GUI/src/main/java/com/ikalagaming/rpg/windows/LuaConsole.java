package com.ikalagaming.rpg.windows;

import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.scripting.Engine;

import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImString;
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
    ImString input;

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
    ImBoolean autoScroll;

    /** If we want to scroll to the bottom. */
    ImBoolean scrollToBottom;

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
        ImGui.setNextWindowPos(200, 200, ImGuiCond.Once);
        ImGui.setNextWindowSize(450, 400, ImGuiCond.Once);
        ImGui.begin("Lua Console");

        ImGui.textWrapped("This is a console for interacting with the lua engine.");

        if (ImGui.smallButton("Clear")) {
            clearLog();
        }
        ImGui.sameLine();
        boolean copyToClipboard = ImGui.smallButton("Copy to clipboard");

        ImGui.separator();

        // Options menu
        if (ImGui.beginPopup("Options")) {
            ImGui.checkbox("Auto-scroll", autoScroll);
            ImGui.endPopup();
        }

        // Options, Filter
        if (ImGui.button("Options")) {
            ImGui.openPopup("Options");
        }
        ImGui.separator();
        // Reserve enough left-over height for 1 separator + 1 input text
        final float footerHeightToReserve =
                ImGui.getStyle().getItemSpacingY() + ImGui.getFrameHeightWithSpacing();
        if (ImGui.beginChild(
                "ScrollingRegion",
                0,
                -footerHeightToReserve,
                false,
                ImGuiWindowFlags.HorizontalScrollbar)) {
            if (ImGui.beginPopupContextWindow()) {
                if (ImGui.selectable("Clear")) {
                    clearLog();
                }
                ImGui.endPopup();
            }
            // Tighten spacing
            ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, 4, 1);
            if (copyToClipboard) {
                ImGui.logToClipboard();
            }
            for (int i = 0; i < logs.size(); i++) {
                String item = logs.get(i);

                boolean hasColor = false;
                if (item.startsWith("[error]")) {
                    ImGui.pushStyleColor(ImGuiCol.Text, 1.0f, 0.4f, 0.4f, 1.0f);
                    hasColor = true;
                } else if (item.startsWith("-- ")) {
                    ImGui.pushStyleColor(ImGuiCol.Text, 1.0f, 0.8f, 0.6f, 1.0f);
                    hasColor = true;
                }
                ImGui.textUnformatted(item);
                if (hasColor) {
                    ImGui.popStyleColor();
                }
            }
            if (copyToClipboard) {
                ImGui.logFinish();
            }

            /*
             * Keep up at the bottom of the scroll region if we were already at
             * the bottom at the beginning of the frame. Using a scrollbar or
             * mouse-wheel will take away from the bottom edge.
             */
            if (scrollToBottom.get()
                    || (autoScroll.get() && ImGui.getScrollY() >= ImGui.getScrollMaxY())) {
                ImGui.setScrollHereY(1.0f);
            }
            scrollToBottom.set(false);

            ImGui.popStyleVar();
        }
        ImGui.endChild();
        ImGui.separator();

        // Command-line
        boolean reclaimFocus = false;
        int inputTextFlags =
                ImGuiInputTextFlags.EnterReturnsTrue
                        | ImGuiInputTextFlags.CallbackCompletion
                        | ImGuiInputTextFlags.CallbackHistory;
        if (ImGui.inputText("Input", input, inputTextFlags)) {
            String s = input.get().trim();
            input.clear();
            if (!s.trim().isEmpty()) {
                executeCommand(s);
            }
            reclaimFocus = true;
        }

        // Auto-focus on window apparition
        ImGui.setItemDefaultFocus();
        if (reclaimFocus) {
            ImGui.setKeyboardFocusHere(-1); // Auto focus previous widget
        }

        ImGui.end();
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
        input = new ImString(256);
        autoScroll = new ImBoolean();
        scrollToBottom = new ImBoolean();
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
