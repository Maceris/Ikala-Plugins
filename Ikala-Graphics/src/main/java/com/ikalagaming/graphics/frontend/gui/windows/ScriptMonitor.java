package com.ikalagaming.graphics.frontend.gui.windows;

import com.ikalagaming.graphics.frontend.gui.IkGui;
import com.ikalagaming.graphics.frontend.gui.component.GuiWindow;
import com.ikalagaming.graphics.frontend.gui.data.IkString;
import com.ikalagaming.graphics.frontend.gui.enums.Condition;
import com.ikalagaming.graphics.frontend.gui.flags.SelectableFlags;
import com.ikalagaming.graphics.frontend.gui.flags.TableColumnFlags;
import com.ikalagaming.graphics.frontend.gui.flags.TableFlags;
import com.ikalagaming.graphics.frontend.gui.flags.TreeNodeFlags;
import com.ikalagaming.graphics.frontend.gui.flags.WindowFlags;
import com.ikalagaming.graphics.frontend.gui.util.Alignment;
import com.ikalagaming.scripting.ScriptManager;
import com.ikalagaming.scripting.interpreter.ScriptRuntime;

import lombok.NonNull;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Shows the scripts the script manager is running or has yielded, and lets you resume or terminate
 * them.
 *
 * @author Ches Burks
 */
public class ScriptMonitor extends GuiWindow {

    public static final String WINDOW_NAME = "Script Monitor";

    /** What to call the tag that scripts yield with when they don't specify one. */
    private static final String NO_TAG = "(no tag)";

    /** The tag to resume, typed in by the user. */
    private final IkString tagToResume;

    public ScriptMonitor() {
        super(WINDOW_NAME, WindowFlags.NONE);
        setScale(0.3f, 0.45f);
        setDisplacement(0.01f, 0.05f);
        setAlignment(Alignment.SOUTH_EAST);

        tagToResume = new IkString(256);
    }

    /**
     * Format the progress of a script.
     *
     * @param script The script.
     * @return The current instruction and total instructions.
     */
    private static String formatProgress(@NonNull ScriptRuntime script) {
        return String.format(
                "%04d / %04d", script.getProgramCounter(), script.getInstructions().size());
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

            final List<ScriptRuntime> running = ScriptManager.getRunningScripts();
            final Map<ScriptRuntime, String> yielded = ScriptManager.getYieldedScripts();

            drawResumeControls();
            IkGui.separator();
            drawRunning(running);
            drawYielded(yielded);
        }

        IkGui.end();
    }

    /** Draw buttons for resuming scripts by tag. */
    private void drawResumeControls() {
        if (IkGui.button("Resume untagged")) {
            ScriptManager.resume();
        }
        IkGui.setItemTooltip("Resume every script that yielded without a tag");

        IkGui.setNextItemWidth(IkGui.getContentRegionAvailableX() * 0.5f);
        IkGui.inputText("##Tag", tagToResume);
        IkGui.sameLine();
        IkGui.beginDisabled(tagToResume.get().isEmpty());
        if (IkGui.button("Resume tag")) {
            ScriptManager.resume(tagToResume.get());
        }
        IkGui.setItemTooltip("Resume every script that yielded with this tag");
        IkGui.endDisabled();
    }

    /**
     * Draw the running scripts.
     *
     * @param running The running scripts.
     */
    private void drawRunning(@NonNull List<ScriptRuntime> running) {
        if (!IkGui.collapsingHeader(
                String.format("Running (%d)###Running", running.size()),
                TreeNodeFlags.DEFAULT_OPEN)) {
            return;
        }
        if (running.isEmpty()) {
            IkGui.textDisabled("No scripts are running");
            return;
        }
        if (!IkGui.beginTable(
                "Running", 3, TableFlags.ROW_BACKGROUND | TableFlags.BORDERS_INNER_V)) {
            return;
        }
        IkGui.tableSetupColumn("Script");
        IkGui.tableSetupColumn("Instruction", TableColumnFlags.WIDTH_FIXED);
        IkGui.tableSetupColumn("##Terminate", TableColumnFlags.WIDTH_FIXED);
        IkGui.tableHeadersRow();
        for (ScriptRuntime script : running) {
            IkGui.tableNextRow();
            IkGui.tableNextColumn();
            drawScriptName(script, null);
            IkGui.tableNextColumn();
            IkGui.text(formatProgress(script));
            IkGui.tableNextColumn();
            drawTerminateButton(script);
        }
        IkGui.endTable();
    }

    /**
     * Draw the yielded scripts, and the tags they are waiting on.
     *
     * @param yielded The yielded scripts, mapped to their tags.
     */
    private void drawYielded(@NonNull Map<ScriptRuntime, String> yielded) {
        if (!IkGui.collapsingHeader(
                String.format("Yielded (%d)###Yielded", yielded.size()),
                TreeNodeFlags.DEFAULT_OPEN)) {
            return;
        }
        if (yielded.isEmpty()) {
            IkGui.textDisabled("No scripts are yielded");
            return;
        }

        // Tags, with how many scripts are waiting on each
        final Map<String, Integer> tagCounts = new TreeMap<>();
        for (String tag : yielded.values()) {
            tagCounts.merge(tag, 1, Integer::sum);
        }
        IkGui.separatorText("Tags");
        if (IkGui.beginTable("Tags", 3, TableFlags.ROW_BACKGROUND | TableFlags.BORDERS_INNER_V)) {
            IkGui.tableSetupColumn("Tag");
            IkGui.tableSetupColumn("Waiting", TableColumnFlags.WIDTH_FIXED);
            IkGui.tableSetupColumn("##Resume", TableColumnFlags.WIDTH_FIXED);
            IkGui.tableHeadersRow();
            for (var entry : tagCounts.entrySet()) {
                final String tag = entry.getKey();
                IkGui.tableNextRow();
                IkGui.tableNextColumn();
                IkGui.text(tag.isEmpty() ? NO_TAG : tag);
                IkGui.tableNextColumn();
                IkGui.text(Integer.toString(entry.getValue()));
                IkGui.tableNextColumn();
                if (IkGui.smallButton("Resume##" + tag)) {
                    if (tag.isEmpty()) {
                        ScriptManager.resume();
                    } else {
                        ScriptManager.resume(tag);
                    }
                }
            }
            IkGui.endTable();
        }

        IkGui.separatorText("Scripts");
        if (IkGui.beginTable(
                "Yielded", 4, TableFlags.ROW_BACKGROUND | TableFlags.BORDERS_INNER_V)) {
            IkGui.tableSetupColumn("Script");
            IkGui.tableSetupColumn("Tag");
            IkGui.tableSetupColumn("Instruction", TableColumnFlags.WIDTH_FIXED);
            IkGui.tableSetupColumn("##Terminate", TableColumnFlags.WIDTH_FIXED);
            IkGui.tableHeadersRow();
            for (var entry : yielded.entrySet()) {
                IkGui.tableNextRow();
                IkGui.tableNextColumn();
                drawScriptName(entry.getKey(), entry.getValue());
                IkGui.tableNextColumn();
                IkGui.text(entry.getValue().isEmpty() ? NO_TAG : entry.getValue());
                IkGui.tableNextColumn();
                IkGui.text(formatProgress(entry.getKey()));
                IkGui.tableNextColumn();
                drawTerminateButton(entry.getKey());
            }
            IkGui.endTable();
        }
    }

    /**
     * Draw the name of a script, with a right click menu for controlling it.
     *
     * @param script The script.
     * @param tag The tag the script yielded with, or null if it is running.
     */
    private static void drawScriptName(@NonNull ScriptRuntime script, String tag) {
        IkGui.selectable(
                script.getDisplayName() + "##Script" + script.getId(),
                false,
                SelectableFlags.SPAN_ALL_COLUMNS | SelectableFlags.ALLOW_OVERLAP);
        IkGui.setItemTooltip("Right click for options");
        if (IkGui.beginPopupContextItem("##ScriptMenu" + script.getId())) {
            IkGui.textDisabled(script.getDisplayName());
            IkGui.separator();
            if (tag != null
                    && IkGui.menuItem(
                            tag.isEmpty()
                                    ? "Resume untagged scripts"
                                    : String.format("Resume scripts tagged \"%s\"", tag))) {
                if (tag.isEmpty()) {
                    ScriptManager.resume();
                } else {
                    ScriptManager.resume(tag);
                }
            }
            if (IkGui.menuItem("Terminate")) {
                ScriptManager.terminate(script);
            }
            IkGui.endPopup();
        }
    }

    /**
     * Draw a button that stops a script.
     *
     * @param script The script.
     */
    private static void drawTerminateButton(@NonNull ScriptRuntime script) {
        if (IkGui.smallButton("Terminate##Terminate" + script.getId())) {
            ScriptManager.terminate(script);
        }
        IkGui.setItemTooltip("Stop the script, it will not be resumed");
    }
}
