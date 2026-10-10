package com.ikalagaming.graphics.gui;

import com.ikalagaming.graphics.TextureHandle;
import com.ikalagaming.graphics.gui.callback.GuiInputTextCallback;
import com.ikalagaming.graphics.gui.data.*;
import com.ikalagaming.graphics.gui.enums.*;
import com.ikalagaming.graphics.gui.flags.*;
import com.ikalagaming.graphics.gui.util.Color;
import com.ikalagaming.graphics.gui.util.KeyChord;
import com.ikalagaming.graphics.gui.util.MathUtil;

import org.joml.Vector2f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.IntFunction;

class IkGuiDemo {

    // Window options
    private static final IkBoolean noTitleBar = new IkBoolean(false);
    private static final IkBoolean noMenu = new IkBoolean(false);
    private static final IkBoolean noScrollbar = new IkBoolean(false);
    private static final IkBoolean noMove = new IkBoolean(false);
    private static final IkBoolean noResize = new IkBoolean(false);
    private static final IkBoolean noCollapse = new IkBoolean(false);
    private static final IkBoolean noClose = new IkBoolean(false);
    private static final IkBoolean noNav = new IkBoolean(false);
    private static final IkBoolean noBackground = new IkBoolean(false);
    private static final IkBoolean noBringToFront = new IkBoolean(false);
    private static final IkBoolean noDocking = new IkBoolean(false);
    private static final IkBoolean unsavedDocument = new IkBoolean(false);

    /** Disables the whole widgets section, toggled from the "Disable Blocks" section. */
    private static final IkBoolean disableAll = new IkBoolean(false);

    // Tools, from the Tools menu
    private static final IkBoolean showMetrics = new IkBoolean(false);
    private static final IkBoolean showDebugLog = new IkBoolean(false);
    private static final IkBoolean showIDStackTool = new IkBoolean(false);
    private static final IkBoolean showStyleEditorWindow = new IkBoolean(false);
    static final IkBoolean showAbout = new IkBoolean(false);

    /**
     * Show the demo window, and any tool windows opened from its menu.
     *
     * @param open If not null, a close button is shown that sets this to false.
     */
    static void showDemoWindow(final IkBoolean open) {
        // Examples apps (accessible from the "Examples" menu)
        IkGuiDemoExamples.showExampleApps();

        // IkGui tools (accessible from the "Tools" menu)
        if (showMetrics.get()) {
            IkGui.showMetricsWindow(showMetrics);
        }
        if (showDebugLog.get()) {
            IkGui.showDebugLogWindow(showDebugLog);
        }
        if (showIDStackTool.get()) {
            IkGui.showIDStackToolWindow(showIDStackTool);
        }
        if (showStyleEditorWindow.get()) {
            IkGui.begin("IkGui Style Editor", showStyleEditorWindow, WindowFlags.NONE);
            IkGui.showStyleEditor();
            IkGui.end();
        }
        if (showAbout.get()) {
            IkGui.showAboutWindow(showAbout);
        }
        showDemoWindowContents(open);
    }

    /**
     * Show the demo window itself, without the tool windows.
     *
     * @param open If not null, a close button is shown that sets this to false.
     */
    static void showDemoWindowContents(final IkBoolean open) {
        int windowFlags = WindowFlags.NONE;
        if (noTitleBar.get()) {
            windowFlags |= WindowFlags.NO_TITLE_BAR;
        }
        if (noScrollbar.get()) {
            windowFlags |= WindowFlags.NO_SCROLLBAR;
        }
        if (!noMenu.get()) {
            windowFlags |= WindowFlags.MENU_BAR;
        }
        if (noMove.get()) {
            windowFlags |= WindowFlags.NO_MOVE;
        }
        if (noResize.get()) {
            windowFlags |= WindowFlags.NO_RESIZE;
        }
        if (noCollapse.get()) {
            windowFlags |= WindowFlags.NO_COLLAPSE;
        }
        if (noNav.get()) {
            windowFlags |= WindowFlags.NO_NAV;
        }
        if (noBackground.get()) {
            windowFlags |= WindowFlags.NO_BACKGROUND;
        }
        if (noBringToFront.get()) {
            windowFlags |= WindowFlags.NO_BRING_TO_FRONT_ON_FOCUS;
        }
        if (noDocking.get()) {
            windowFlags |= WindowFlags.NO_DOCKING;
        }
        if (unsavedDocument.get()) {
            windowFlags |= WindowFlags.UNSAVED_DOCUMENT;
        }

        // We specify a default position/size in case there's no data in the .ini file. We only do
        // it to make the demo applications a little more welcoming, but typically this isn't
        // required.
        final Viewport mainViewport = IkGui.getMainViewport();
        IkGui.setNextWindowPos(
                mainViewport.workPosition.x + 650,
                mainViewport.workPosition.y + 20,
                Condition.FIRST_USE_EVER);
        IkGui.setNextWindowSize(550, 680, Condition.FIRST_USE_EVER);

        // Main body of the Demo window starts here.
        if (!IkGui.begin("IkGui Demo Window", noClose.get() ? null : open, windowFlags)) {
            // Early out if the window is collapsed, as an optimization.
            IkGui.end();
            return;
        }

        // Most framed widgets share a common width settings. Remaining width is used for the
        // label. The width of the frame may be changed with pushItemWidth() or
        // setNextItemWidth().
        // - Positive value for absolute size, negative value for right-alignment.
        // - The default value is about getWindowWidth() * 0.65f.
        // - See 'Demo->Layout->Widgets Width' for details.
        // Here we change the frame width based on how much width we want to give to the label.
        // Some amount of width for label, based on font size.
        final float labelWidthBase = IkGui.getFontSize() * 12;
        // ...but always leave some room for framed widgets.
        final float labelWidthMax = IkGui.getContentRegionAvailableX() * 0.40f;
        final float labelWidth = Math.min(labelWidthBase, labelWidthMax);
        // Right-align: framed items will leave 'labelWidth' available for the label.
        IkGui.pushItemWidth(-labelWidth);

        // Menu Bar
        showDemoMenuBar();

        IkGui.text(
                String.format(
                        "IkGui says hello! (Dear ImGui %s) (%d)",
                        IkGui.DEAR_IMGUI_VERSION, IkGui.DEAR_IMGUI_VERSION_NUM));
        IkGui.spacing();

        showHelpSection();
        showConfigurationSection();
        showWindowOptionsSection();
        showWidgetsSection();
        showLayoutSection();
        showPopupsSection();
        IkGuiDemoTables.show();
        showInputsSection();

        // End of showDemoWindow()
        IkGui.popItemWidth();
        IkGui.end();
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

    /**
     * A slider for a value, returning the new value. The display precision is picked from the step
     * size, and values are rounded to that precision.
     *
     * @param label The label, also used for the ID.
     * @param value The current value.
     * @param step The precision of the value, 1 or more for whole numbers.
     * @param min The minimum value.
     * @param max The maximum value.
     * @return The new value.
     */
    private static float sliderValue(String label, float value, float step, float min, float max) {
        final float[] data = {value};
        IkGui.sliderFloat(label, data, min, max, step >= 1 ? "%.0f" : "%.2f");
        return data[0];
    }

    /**
     * A checkbox for a plain boolean value.
     *
     * @param label The label.
     * @param value The current value.
     * @return The new value.
     */
    private static boolean toggle(String label, boolean value) {
        return IkGui.checkbox(label, value) != value;
    }

    // ---------------------------------------------------------------------------------------------
    // Help
    // ---------------------------------------------------------------------------------------------

    private static void showHelpSection() {
        if (!IkGui.collapsingHeader("Help")) {
            return;
        }

        IkGui.separatorText("ABOUT THIS DEMO:");
        IkGui.bulletText("Sections below are demonstrating many aspects of the library.");
        IkGui.bulletText("The \"Examples\" menu above leads to more demo contents.");
        IkGui.bulletText(
                "The \"Tools\" menu above gives access to: About Box, Style Editor,\n"
                        + "and Metrics/Debugger (general purpose IkGui debugging tool).");
        IkGui.bulletText("Dear ImGui web demo (w/ source code browser): ");
        IkGui.sameLine(0, 0);
        IkGui.textLinkOpenURL("https://pthom.github.io/imgui_explorer");

        IkGui.separatorText("PROGRAMMER GUIDE:");
        IkGui.bulletText("See the showDemoWindow() code in IkGuiDemo.java. <- you are here!");
        IkGui.bulletText("See comments in IkGui.java.");
        IkGui.bulletText("Read the Dear ImGui FAQ at ");
        IkGui.sameLine(0, 0);
        IkGui.textLinkOpenURL("https://www.dearimgui.com/faq/");
        IkGui.bulletText("Set 'io.configFlags |= NAV_ENABLE_KEYBOARD' for keyboard controls.");
        IkGui.bulletText("Set 'io.configFlags |= NAV_ENABLE_GAMEPAD' for gamepad controls.");

        IkGui.separatorText("USER GUIDE:");
        IkGui.showUserGuide();
    }

    /**
     * Help text about how to use the GUI as an end user, with the mouse, keyboard and gamepad. This
     * is not a window, so it can be added to any window.
     */
    static void showUserGuide() {
        IkGui.bulletText("Double-click on title bar to collapse window.");
        IkGui.bulletText(
                "Click and drag on lower corner or border to resize window.\n"
                        + "(double-click to auto fit window to its contents)");
        IkGui.bulletText("Ctrl+Click on a slider or drag box to input value as text.");
        IkGui.bulletText("Tab/Shift+Tab to cycle through keyboard editable fields.");
        IkGui.bulletText("Ctrl+Tab/Ctrl+Shift+Tab to focus windows.");
        IkGui.bulletText("While inputting text:");
        IkGui.indent();
        IkGui.bulletText("Ctrl+Left/Right to word jump.");
        IkGui.bulletText("Ctrl+A or double-click to select all.");
        IkGui.bulletText("Ctrl+X/C/V to use clipboard cut/copy/paste.");
        IkGui.bulletText("Ctrl+Z to undo, Ctrl+Y/Ctrl+Shift+Z to redo.");
        IkGui.bulletText("Escape to revert.");
        IkGui.unindent();
        IkGui.bulletText("With Keyboard controls enabled:");
        IkGui.indent();
        IkGui.bulletText("Arrow keys or Home/End/PageUp/PageDown to navigate.");
        IkGui.bulletText("Space to activate a widget.");
        IkGui.bulletText("Return to input text into a widget.");
        IkGui.bulletText(
                "Escape to deactivate a widget, close popup,\n"
                        + "exit a child window or the menu layer, clear focus.");
        IkGui.bulletText("Alt to jump to the menu layer of a window.");
        IkGui.bulletText("Menu or Shift+F10 to open a context menu.");
        IkGui.unindent();
        IkGui.bulletText("With Gamepad controls enabled:");
        IkGui.indent();
        IkGui.bulletText("D-Pad: Navigate / Tweak / Resize (in Windowing mode).");
        IkGui.bulletText(
                "South Face button: Activate / Open / Toggle. Hold: activate with text input.");
        IkGui.bulletText("East Face button: Cancel / Close / Exit.");
        IkGui.bulletText(
                "West Face button: Toggle Menu. Hold for Windowing mode (Focus/Move/Resize"
                        + " windows).");
        IkGui.bulletText("North Face button: Open Context Menu.");
        IkGui.bulletText("L1/R1: Tweak Slower/Faster, Focus Previous/Next (in Windowing Mode).");
        IkGui.unindent();
    }

    // ---------------------------------------------------------------------------------------------
    // Configuration
    // ---------------------------------------------------------------------------------------------

    /** Storage for editing io.configFlags with checkboxes. */
    private static final IkInt configFlagsBox = new IkInt(0);

    private static void showConfigurationSection() {
        if (!IkGui.collapsingHeader("Configuration")) {
            return;
        }

        IkIO io = IkGui.getIO();
        if (IkGui.treeNode("Configuration##2")) {
            final IkInt configFlags = configFlagsBox;
            configFlags.set(io.configFlags);

            IkGui.separatorText("General");
            IkGui.checkboxFlags(
                    "io.configFlags: NAV_ENABLE_KEYBOARD",
                    configFlags,
                    ConfigFlags.NAV_ENABLE_KEYBOARD);
            IkGui.sameLine();
            helpMarker("Enable keyboard controls.");
            IkGui.checkboxFlags(
                    "io.configFlags: NAV_ENABLE_GAMEPAD",
                    configFlags,
                    ConfigFlags.NAV_ENABLE_GAMEPAD);
            IkGui.sameLine();
            helpMarker(
                    "Enable gamepad controls. Require backend to set io.backendFlags |="
                            + " BackendFlags.HAS_GAMEPAD.");
            IkGui.checkboxFlags("io.configFlags: NO_MOUSE", configFlags, ConfigFlags.NO_MOUSE);
            IkGui.sameLine();
            helpMarker("Instruct IkGui to disable mouse inputs and interactions.");

            // The "NO_MOUSE" option can get us stuck with a disabled mouse! Let's provide an
            // alternative way to fix it:
            if ((configFlags.get() & ConfigFlags.NO_MOUSE) != 0) {
                // The time is in milliseconds
                if (IkGui.getTime() % 400 < 200) {
                    IkGui.sameLine();
                    IkGui.text("<<PRESS SPACE TO DISABLE>>");
                }
                // Prevent both being checked
                if (IkGui.isKeyPressed(Key.SPACE)
                        || (configFlags.get() & ConfigFlags.NO_KEYBOARD) != 0) {
                    configFlags.set(configFlags.get() & ~ConfigFlags.NO_MOUSE);
                }
            }

            IkGui.checkboxFlags(
                    "io.configFlags: NO_MOUSE_CURSOR_CHANGE",
                    configFlags,
                    ConfigFlags.NO_MOUSE_CURSOR_CHANGE);
            IkGui.sameLine();
            helpMarker("Instruct backend to not alter mouse cursor shape and visibility.");
            IkGui.checkboxFlags(
                    "io.configFlags: NO_KEYBOARD", configFlags, ConfigFlags.NO_KEYBOARD);
            IkGui.sameLine();
            helpMarker("Instruct IkGui to disable keyboard inputs and interactions.");

            io.configInputTrickleEventQueue =
                    toggle("io.configInputTrickleEventQueue", io.configInputTrickleEventQueue);
            IkGui.sameLine();
            helpMarker(
                    "Enable input queue trickling: some types of events submitted during the same"
                            + " frame (e.g. button down + up) will be spread over multiple frames,"
                            + " improving interactions with low framerates.");
            io.configMouseDrawCursor = toggle("io.configMouseDrawCursor", io.configMouseDrawCursor);
            IkGui.sameLine();
            helpMarker(
                    "Instruct IkGui to render a mouse cursor itself. Note that a mouse cursor"
                            + " rendered via your application GPU rendering path will feel more"
                            + " laggy than hardware cursor, but will be more in sync with your"
                            + " other visuals.\n\n"
                            + "Some desktop applications may use both kinds of cursors (e.g."
                            + " enable software cursor only when resizing/dragging something).");

            IkGui.separatorText("Keyboard/Gamepad Navigation");
            io.configNavSwapGamepadButtons =
                    toggle("io.configNavSwapGamepadButtons", io.configNavSwapGamepadButtons);
            io.configNavMoveSetMousePosition =
                    toggle("io.configNavMoveSetMousePosition", io.configNavMoveSetMousePosition);
            IkGui.sameLine();
            helpMarker(
                    "Directional/tabbing navigation teleports the mouse cursor. May be useful on"
                            + " TV/console systems where moving a virtual mouse is difficult");
            io.configNavCaptureKeyboard =
                    toggle("io.configNavCaptureKeyboard", io.configNavCaptureKeyboard);
            io.configNavEscapeClearFocusItem =
                    toggle("io.configNavEscapeClearFocusItem", io.configNavEscapeClearFocusItem);
            IkGui.sameLine();
            helpMarker("Pressing Escape clears focused item.");
            io.configNavEscapeClearFocusWindow =
                    toggle(
                            "io.configNavEscapeClearFocusWindow",
                            io.configNavEscapeClearFocusWindow);
            IkGui.sameLine();
            helpMarker("Pressing Escape clears focused window.");
            io.configNavCursorVisibleAuto =
                    toggle("io.configNavCursorVisibleAuto", io.configNavCursorVisibleAuto);
            IkGui.sameLine();
            helpMarker(
                    "Using directional navigation key makes the cursor visible. Mouse click hides"
                            + " the cursor.");
            io.configNavCursorVisibleAlways =
                    toggle("io.configNavCursorVisibleAlways", io.configNavCursorVisibleAlways);
            IkGui.sameLine();
            helpMarker("Navigation cursor is always visible.");

            IkGui.separatorText("Docking");
            IkGui.checkboxFlags(
                    "io.configFlags: DOCKING_ENABLE", configFlags, ConfigFlags.DOCKING_ENABLE);
            IkGui.sameLine();
            if (io.configDockingWithShift) {
                helpMarker(
                        "Drag from window title bar or their tab to dock/undock. Hold SHIFT to"
                                + " enable docking.\n\n"
                                + "Drag from window menu button (upper-left button) to undock an"
                                + " entire node (all windows).");
            } else {
                helpMarker(
                        "Drag from window title bar or their tab to dock/undock. Hold SHIFT to"
                                + " disable docking.\n\n"
                                + "Drag from window menu button (upper-left button) to undock an"
                                + " entire node (all windows).");
            }
            if ((configFlags.get() & ConfigFlags.DOCKING_ENABLE) != 0) {
                IkGui.indent();
                io.configDockingNoSplit =
                        toggle("io.configDockingNoSplit", io.configDockingNoSplit);
                IkGui.sameLine();
                helpMarker(
                        "Simplified docking mode: disable window splitting, so docking is limited"
                                + " to merging multiple windows together into tab-bars.");
                io.configDockingNoDockingOver =
                        toggle("io.configDockingNoDockingOver", io.configDockingNoDockingOver);
                IkGui.sameLine();
                helpMarker(
                        "Simplified docking mode: disable window merging into a same tab-bar, so"
                                + " docking is limited to splitting windows.");
                io.configDockingWithShift =
                        toggle("io.configDockingWithShift", io.configDockingWithShift);
                IkGui.sameLine();
                helpMarker(
                        "Enable docking when holding Shift only (allow to drop in wider space,"
                                + " reduce visual noise)");
                io.configDockingAlwaysTabBar =
                        toggle("io.configDockingAlwaysTabBar", io.configDockingAlwaysTabBar);
                IkGui.sameLine();
                helpMarker("Create a docking node and tab-bar on single floating windows.");
                io.configDockingTransparentPayload =
                        toggle(
                                "io.configDockingTransparentPayload",
                                io.configDockingTransparentPayload);
                IkGui.sameLine();
                helpMarker(
                        "Make window or viewport transparent when docking and only display docking"
                                + " boxes on the target viewport. Useful if rendering of multiple"
                                + " viewport cannot be synced. Best used with"
                                + " configViewportsNoAutoMerge.");
                IkGui.unindent();
            }

            IkGui.separatorText("Multi-viewports");
            IkGui.checkboxFlags(
                    "io.configFlags: VIEWPORTS_ENABLE", configFlags, ConfigFlags.VIEWPORTS_ENABLE);
            IkGui.sameLine();
            helpMarker("[beta] Enable beta multi-viewports support. See PlatformIO for details.");
            if ((configFlags.get() & ConfigFlags.VIEWPORTS_ENABLE) != 0) {
                IkGui.indent();
                io.configViewportsNoAutoMerge =
                        toggle("io.configViewportsNoAutoMerge", io.configViewportsNoAutoMerge);
                IkGui.sameLine();
                helpMarker(
                        "Set to make all floating windows always create their own viewport."
                                + " Otherwise, they are merged into the main host viewports when"
                                + " overlapping it.");
                io.configViewportsNoTaskBarIcon =
                        toggle("io.configViewportsNoTaskBarIcon", io.configViewportsNoTaskBarIcon);
                IkGui.sameLine();
                helpMarker(
                        "(note: some platform backends may not reflect a change of this value for"
                                + " existing viewports, and may need the viewport to be"
                                + " recreated)");
                io.configViewportsNoDecoration =
                        toggle("io.configViewportsNoDecoration", io.configViewportsNoDecoration);
                IkGui.sameLine();
                helpMarker(
                        "(note: some platform backends may not reflect a change of this value for"
                                + " existing viewports, and may need the viewport to be"
                                + " recreated)");
                io.configViewportsNoDefaultParent =
                        toggle(
                                "io.configViewportsNoDefaultParent",
                                io.configViewportsNoDefaultParent);
                IkGui.sameLine();
                helpMarker(
                        "(note: some platform backends may not reflect a change of this value for"
                                + " existing viewports, and may need the viewport to be"
                                + " recreated)");
                io.configViewportsPlatformFocusSetsWindowFocus =
                        toggle(
                                "io.configViewportsPlatformFocusSetsWindowFocus",
                                io.configViewportsPlatformFocusSetsWindowFocus);
                IkGui.sameLine();
                helpMarker(
                        "When a platform window is focused (e.g. using Alt+Tab, clicking Platform"
                                + " Title Bar), apply corresponding focus on IkGui windows (may"
                                + " clear focus/active id from IkGui windows location in other"
                                + " platform windows). In principle this is better enabled but we"
                                + " provide an opt-out, because some Linux window managers tend to"
                                + " eagerly focus windows (e.g. on mouse hover, or even a simple"
                                + " window pos/size change).");
                IkGui.unindent();
            }

            IkGui.separatorText("Windows");
            io.configWindowsResizeFromEdges =
                    toggle("io.configWindowsResizeFromEdges", io.configWindowsResizeFromEdges);
            IkGui.sameLine();
            helpMarker(
                    "Enable resizing of windows from their edges and from the lower-left"
                            + " corner.\nThis requires BackendFlags.HAS_MOUSE_CURSORS for better"
                            + " mouse cursor feedback.");
            io.configWindowsMoveFromTitleBarOnly =
                    toggle(
                            "io.configWindowsMoveFromTitleBarOnly",
                            io.configWindowsMoveFromTitleBarOnly);
            io.configWindowsCopyContentsWithCtrlC =
                    toggle(
                            "io.configWindowsCopyContentsWithCtrlC",
                            io.configWindowsCopyContentsWithCtrlC);
            IkGui.sameLine();
            helpMarker(
                    "*EXPERIMENTAL* Ctrl+C copy the contents of focused window into the"
                            + " clipboard.\n\n"
                            + "Experimental because:\n"
                            + "- (1) has known issues with nested begin()/end() pairs.\n"
                            + "- (2) text output quality varies.\n"
                            + "- (3) text output is in submission order rather than spatial"
                            + " order.");
            io.configScrollbarScrollByPage =
                    toggle("io.configScrollbarScrollByPage", io.configScrollbarScrollByPage);
            IkGui.sameLine();
            helpMarker(
                    "Enable scrolling page by page when clicking outside the scrollbar grab.\n"
                            + "When disabled, always scroll to clicked location.\n"
                            + "When enabled, Shift+Click scrolls to clicked location.");

            IkGui.separatorText("Widgets");
            io.configInputTextCursorBlink =
                    toggle("io.configInputTextCursorBlink", io.configInputTextCursorBlink);
            IkGui.sameLine();
            helpMarker(
                    "Enable blinking cursor (optional as some users consider it to be"
                            + " distracting).");
            io.configInputTextEnterKeepActive =
                    toggle("io.configInputTextEnterKeepActive", io.configInputTextEnterKeepActive);
            IkGui.sameLine();
            helpMarker(
                    "Pressing Enter will reactivate item and select all text (single-line only).");
            io.configDragClickToInputText =
                    toggle("io.configDragClickToInputText", io.configDragClickToInputText);
            IkGui.sameLine();
            helpMarker(
                    "Enable turning drag widgets into text input with a simple mouse click-release"
                            + " (without moving).");
            io.configMacOSXBehaviors = toggle("io.configMacOSXBehaviors", io.configMacOSXBehaviors);
            IkGui.sameLine();
            helpMarker("Swap Cmd<>Ctrl keys, enable various MacOS style behaviors.");
            IkGui.text("Also see Style->Rendering for rendering options.");

            IkGui.separatorText("Settings");
            io.configIniSettingsSaveLastUsedDate =
                    toggle(
                            "io.configIniSettingsSaveLastUsedDate",
                            io.configIniSettingsSaveLastUsedDate);

            // Also read: https://github.com/ocornut/imgui/wiki/Error-Handling
            IkGui.separatorText("Error Handling");

            io.configErrorRecovery = toggle("io.configErrorRecovery", io.configErrorRecovery);
            IkGui.sameLine();
            helpMarker(
                    "Options to configure how we handle recoverable errors.\n"
                            + "- Error recovery is not perfect nor guaranteed! It is a feature to"
                            + " ease development.\n"
                            + "- You not are not supposed to rely on it in the course of a normal"
                            + " application run.\n"
                            + "- Possible usage: facilitate recovery from errors triggered from a"
                            + " scripting language or after specific exceptions handlers.\n"
                            + "- Always ensure that on programmers seat you have at minimum Asserts"
                            + " or Tooltips enabled when making direct IkGui API call! Otherwise it"
                            + " would severely hinder your ability to catch and correct"
                            + " mistakes!\n"
                            + "- Asserts throw an IkGuiUserError, and are off by default.");
            io.configErrorRecoveryEnableAssert =
                    toggle(
                            "io.configErrorRecoveryEnableAssert",
                            io.configErrorRecoveryEnableAssert);
            io.configErrorRecoveryEnableDebugLog =
                    toggle(
                            "io.configErrorRecoveryEnableDebugLog",
                            io.configErrorRecoveryEnableDebugLog);
            io.configErrorRecoveryEnableTooltip =
                    toggle(
                            "io.configErrorRecoveryEnableTooltip",
                            io.configErrorRecoveryEnableTooltip);
            if (!io.configErrorRecoveryEnableAssert
                    && !io.configErrorRecoveryEnableDebugLog
                    && !io.configErrorRecoveryEnableTooltip) {
                io.configErrorRecoveryEnableAssert = true;
                io.configErrorRecoveryEnableDebugLog = true;
                io.configErrorRecoveryEnableTooltip = true;
            }

            // Also read: https://github.com/ocornut/imgui/wiki/Debug-Tools
            IkGui.separatorText("Debug");
            io.configDebugIsDebuggerPresent =
                    toggle("io.configDebugIsDebuggerPresent", io.configDebugIsDebuggerPresent);
            IkGui.sameLine();
            helpMarker(
                    "Enable various tools that break into the debugger.\n\n"
                            + "Requires a debugger being attached.");
            io.configDebugHighlightIdConflicts =
                    toggle(
                            "io.configDebugHighlightIdConflicts",
                            io.configDebugHighlightIdConflicts);
            IkGui.sameLine();
            helpMarker(
                    "Highlight and show an error message when multiple items have conflicting"
                            + " identifiers.");
            IkGui.beginDisabled();
            io.configDebugBeginReturnValueOnce =
                    toggle(
                            "io.configDebugBeginReturnValueOnce",
                            io.configDebugBeginReturnValueOnce);
            IkGui.endDisabled();
            IkGui.sameLine();
            helpMarker(
                    "First calls to begin()/beginChild() will return false.\n\n"
                            + "THIS OPTION IS DISABLED because it needs to be set at application"
                            + " boot-time to make sense. Showing the disabled option is a way to"
                            + " make this feature easier to discover.");
            io.configDebugBeginReturnValueLoop =
                    toggle(
                            "io.configDebugBeginReturnValueLoop",
                            io.configDebugBeginReturnValueLoop);
            IkGui.sameLine();
            helpMarker(
                    "Some calls to begin()/beginChild() will return false.\n\n"
                            + "Will cycle through window depths then repeat. Windows should be"
                            + " flickering while running.");
            io.configDebugIgnoreFocusLoss =
                    toggle("io.configDebugIgnoreFocusLoss", io.configDebugIgnoreFocusLoss);
            IkGui.sameLine();
            helpMarker(
                    "Option to deactivate io.addFocusEvent(false) handling. May facilitate"
                            + " interactions with a debugger when focus loss leads to clearing"
                            + " inputs data.");
            io.configDebugIniSettings =
                    toggle("io.configDebugIniSettings", io.configDebugIniSettings);
            IkGui.sameLine();
            helpMarker(
                    "Option to save .ini data with extra comments (particularly helpful for"
                            + " Docking, but makes saving slower).");

            io.configFlags = configFlags.get();
            IkGui.treePop();
            IkGui.spacing();
        }

        if (IkGui.treeNode("Backend Flags")) {
            helpMarker(
                    "Those flags are set by the backends to specify their capabilities.\n"
                            + "Here we expose them as read-only fields to avoid breaking"
                            + " interactions with your backend.");

            // Make a local copy to avoid modifying the actual backend flags
            final IkInt backendFlags = new IkInt(io.backendFlags);
            IkGui.beginDisabled();
            IkGui.checkboxFlags(
                    "io.backendFlags: HAS_GAMEPAD", backendFlags, BackendFlags.HAS_GAMEPAD);
            IkGui.checkboxFlags(
                    "io.backendFlags: HAS_MOUSE_CURSORS",
                    backendFlags,
                    BackendFlags.HAS_MOUSE_CURSORS);
            IkGui.checkboxFlags(
                    "io.backendFlags: HAS_SET_MOUSE_POS",
                    backendFlags,
                    BackendFlags.HAS_SET_MOUSE_POS);
            IkGui.checkboxFlags(
                    "io.backendFlags: PLATFORM_HAS_VIEWPORTS",
                    backendFlags,
                    BackendFlags.PLATFORM_HAS_VIEWPORTS);
            IkGui.checkboxFlags(
                    "io.backendFlags: HAS_MOUSE_HOVERED_VIEWPORT",
                    backendFlags,
                    BackendFlags.HAS_MOUSE_HOVERED_VIEWPORT);
            IkGui.checkboxFlags(
                    "io.backendFlags: HAS_PARENT_VIEWPORT",
                    backendFlags,
                    BackendFlags.HAS_PARENT_VIEWPORT);
            IkGui.checkboxFlags(
                    "io.backendFlags: RENDER_HAS_VERTEX_OFFSET",
                    backendFlags,
                    BackendFlags.RENDER_HAS_VERTEX_OFFSET);
            IkGui.checkboxFlags(
                    "io.backendFlags: RENDERER_HAS_VIEWPORTS",
                    backendFlags,
                    BackendFlags.RENDERER_HAS_VIEWPORTS);
            IkGui.endDisabled();

            IkGui.treePop();
            IkGui.spacing();
        }

        if (IkGui.treeNode("Style, Fonts")) {
            IkGui.checkbox("Style Editor", showStyleEditorWindow);
            IkGui.sameLine();
            helpMarker(
                    "The same contents can be accessed in 'Tools->Style Editor' or by calling the"
                            + " showStyleEditor() function.");
            IkGui.treePop();
            IkGui.spacing();
        }

        if (IkGui.treeNode("Capture/Logging")) {
            helpMarker(
                    "The logging API redirects all text output so you can easily capture the"
                            + " content of a window or a block. Tree nodes can be automatically"
                            + " expanded.\nTry opening any of the contents below in this window"
                            + " and then click one of the \"Log To\" buttons.");
            IkGui.logButtons();

            helpMarker(
                    "You can also call IkGui.logText() to output directly to the log without a"
                            + " visual output.");
            if (IkGui.button("Copy \"Hello, world!\" to clipboard")) {
                IkGui.logToClipboard();
                IkGui.logText("Hello, world!");
                IkGui.logFinish();
            }
            IkGui.treePop();
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Style editor
    // ---------------------------------------------------------------------------------------------

    /** The last style chosen in showStyleSelector(), or -1 for none. */
    private static int styleSelectorIndex = -1;

    /**
     * A combo to pick one of the default color themes.
     *
     * @param label The label of the combo.
     * @return True if a theme was picked.
     */
    static boolean showStyleSelector(String label) {
        // Themes are functions, they don't register a name or the fact that one is active. So we
        // keep track of the last one picked here.
        final String[] styleNames = {"Dark", "Light", "Classic"};
        boolean ret = false;
        if (IkGui.beginCombo(
                label,
                styleSelectorIndex >= 0 && styleSelectorIndex < styleNames.length
                        ? styleNames[styleSelectorIndex]
                        : "")) {
            for (int n = 0; n < styleNames.length; ++n) {
                if (IkGui.selectable(
                        styleNames[n], styleSelectorIndex == n, SelectableFlags.SELECT_ON_NAV)) {
                    styleSelectorIndex = n;
                    ret = true;
                    switch (n) {
                        case 0 -> IkGui.styleColorsDark();
                        case 1 -> IkGui.styleColorsLight();
                        default -> IkGui.styleColorsClassic();
                    }
                } else if (styleSelectorIndex == n) {
                    IkGui.setItemDefaultFocus();
                }
            }
            IkGui.endCombo();
        }
        return ret;
    }

    /** The reference style, when showStyleEditor() isn't given one. */
    private static final Style styleEditorSavedRef = new Style();

    /** If the reference style still needs to be copied from the current style. */
    private static boolean styleEditorInit = true;

    /** Where colors are exported to, 0 for the clipboard and 1 for the console. */
    private static final IkInt styleEditorOutputDestination = new IkInt(0);

    private static final IkBoolean styleEditorOutputOnlyModified = new IkBoolean(true);
    private static int styleEditorAlphaFlags = ColorEditFlags.NONE;
    private static final TextFilter styleEditorColorFilter = new TextFilter();

    /**
     * A slider for a style value.
     *
     * @param label The label.
     * @param value The current value.
     * @param min The minimum.
     * @param max The maximum.
     * @param format The display format.
     * @return The new value.
     */
    private static float styleSlider(
            String label, float value, float min, float max, String format) {
        final float[] holder = {value};
        IkGui.sliderFloat(label, holder, min, max, format);
        return holder[0];
    }

    /**
     * A slider for a 2D style value, which is edited in place.
     *
     * @param label The label.
     * @param value The value.
     * @param min The minimum.
     * @param max The maximum.
     * @param format The display format.
     */
    private static void styleSlider2(
            String label, Vector2f value, float min, float max, String format) {
        final float[] holder = {value.x, value.y};
        if (IkGui.sliderFloat2(label, holder, min, max, format)) {
            value.set(holder[0], holder[1]);
        }
    }

    /**
     * A drag for a style value.
     *
     * @param label The label.
     * @param value The current value.
     * @param speed The speed.
     * @param min The minimum.
     * @param max The maximum.
     * @param format The display format.
     * @return The new value.
     */
    private static float styleDrag(
            String label, float value, float speed, float min, float max, String format) {
        final float[] holder = {value};
        IkGui.dragFloat(label, holder, speed, min, max, format);
        return holder[0];
    }

    /**
     * A combo for an enum style value.
     *
     * @param label The label.
     * @param current The index of the current value.
     * @param names The names of the values.
     * @return The index of the new value.
     */
    /**
     * The name of a style.treeLinesFlags option.
     *
     * @param flags The flags.
     * @return The name.
     */
    private static String treeLinesFlagsName(int flags) {
        return switch (flags) {
            case TreeNodeFlags.DRAW_LINES_NONE -> "DRAW_LINES_NONE";
            case TreeNodeFlags.DRAW_LINES_FULL -> "DRAW_LINES_FULL";
            case TreeNodeFlags.DRAW_LINES_TO_NODES -> "DRAW_LINES_TO_NODES";
            default -> "";
        };
    }

    private static int styleCombo(String label, int current, String[] names) {
        final IkInt holder = new IkInt(current);
        IkGui.combo(label, holder, names);
        return holder.get();
    }

    /**
     * Convert a color to floating point components.
     *
     * @param color The color, in RGBA format.
     * @return The red, green, blue and alpha components from 0 to 1.
     */
    private static float[] colorToFloats(int color) {
        return new float[] {
            ((color >>> 24) & 0xFF) / 255.0f,
            ((color >>> 16) & 0xFF) / 255.0f,
            ((color >>> 8) & 0xFF) / 255.0f,
            (color & 0xFF) / 255.0f
        };
    }

    /**
     * Convert floating point components to a color, rounding to the nearest value.
     *
     * @param components The red, green, blue and alpha components from 0 to 1.
     * @return The color, in RGBA format.
     */
    private static int floatsToColor(float[] components) {
        final int[] bytes = new int[4];
        for (int i = 0; i < 4; ++i) {
            bytes[i] = (int) (MathUtil.clamp(components[i], 0.0f, 1.0f) * 255.0f + 0.5f);
        }
        return Color.rgba(bytes[0], bytes[1], bytes[2], bytes[3]);
    }

    /**
     * Show the style editor. You can pass in a reference style to compare to, revert to and save
     * to. Without one, a copy of the style when the editor was first shown is used.
     *
     * @param ref The reference style, may be null.
     */
    static void showStyleEditor(Style ref) {
        final Style style = IkGui.getStyle();
        final StyleVariables vars = style.variable;

        // Default to using internal storage as the reference
        if (styleEditorInit && ref == null) {
            styleEditorSavedRef.set(style);
        }
        styleEditorInit = false;
        if (ref == null) {
            ref = styleEditorSavedRef;
        }

        // We don't encourage increasing border sizes too much, since it'll likely reveal subtle
        // rendering artifacts
        final float defaultBorderSize = 1.0f;
        final float maxBorderSize = Math.max(defaultBorderSize, 2.0f);

        IkGui.pushItemWidth(IkGui.getWindowWidth() * 0.50f);

        // General
        IkGui.separatorText("General");
        if (showStyleSelector("Colors##Selector")) {
            styleEditorSavedRef.set(style);
        }
        IkGui.showFontSelector("Fonts##Selector");
        final int[] fontSize = {IkGui.getFontSize()};
        if (IkGui.dragInt("FontSize", fontSize, 0.2f, 5, 100)) {
            IkGui.setFontSize(fontSize[0]);
        }

        // Simplified settings, which expose float border sizes as booleans for 0 or 1
        final float[] frameRounding = {vars.frameRounding};
        if (IkGui.sliderFloat("FrameRounding", frameRounding, 0.0f, 12.0f, "%.0f")) {
            vars.frameRounding = frameRounding[0];
            // Make the grab rounding always the same as the frame rounding
            vars.grabRounding = frameRounding[0];
        }
        if (IkGui.checkbox("WindowBorder", vars.windowBorderSize > 0.0f)) {
            vars.windowBorderSize = vars.windowBorderSize > 0.0f ? 0.0f : defaultBorderSize;
        }
        IkGui.sameLine();
        if (IkGui.checkbox("FrameBorder", vars.frameBorderSize > 0.0f)) {
            vars.frameBorderSize = vars.frameBorderSize > 0.0f ? 0.0f : defaultBorderSize;
        }
        IkGui.sameLine();
        if (IkGui.checkbox("PopupBorder", vars.popupBorderSize > 0.0f)) {
            vars.popupBorderSize = vars.popupBorderSize > 0.0f ? 0.0f : defaultBorderSize;
        }

        // Save/revert buttons
        if (IkGui.button("Save Ref")) {
            ref.set(style);
            styleEditorSavedRef.set(style);
        }
        IkGui.sameLine();
        if (IkGui.button("Revert Ref")) {
            style.set(ref);
        }
        IkGui.sameLine();
        helpMarker(
                "Save/Revert in local non-persistent storage. Default Colors definition are not"
                        + " affected. Use \"Export\" below to save them somewhere.");

        IkGui.separatorText("Details");
        if (IkGui.beginTabBar("##tabs", TabBarFlags.NONE)) {
            if (IkGui.beginTabItem("Sizes")) {
                showStyleEditorSizes(vars, maxBorderSize);
                IkGui.endTabItem();
            }
            if (IkGui.beginTabItem("Colors")) {
                showStyleEditorColors(style, ref);
                IkGui.endTabItem();
            }
            if (IkGui.beginTabItem("Fonts")) {
                IkGui.showFontAtlas(IkGui.getIO().fonts);
                IkGui.endTabItem();
            }
            if (IkGui.beginTabItem("Rendering")) {
                // Anti-aliasing and tessellation settings don't apply to our SDF based drawing
                IkGui.pushItemWidth(IkGui.getFontSize() * 8);
                // Zero isn't exposed so the user doesn't "lose" the UI, since zero alpha clips all
                // widgets
                vars.alpha = styleDrag("Global Alpha", vars.alpha, 0.005f, 0.20f, 1.0f, "%.2f");
                vars.disabledAlpha =
                        styleDrag("Disabled Alpha", vars.disabledAlpha, 0.005f, 0.0f, 1.0f, "%.2f");
                IkGui.sameLine();
                helpMarker(
                        "Additional alpha multiplier for disabled items (multiply over current"
                                + " value of Alpha).");
                IkGui.popItemWidth();
                IkGui.endTabItem();
            }
            IkGui.endTabBar();
        }
        IkGui.popItemWidth();
    }

    /**
     * The sizes tab of the style editor.
     *
     * @param vars The style variables.
     * @param maxBorderSize The maximum border size to allow.
     */
    private static void showStyleEditorSizes(StyleVariables vars, float maxBorderSize) {
        IkGui.separatorText("Main");
        styleSlider2("WindowPadding", vars.windowPadding, 0.0f, 20.0f, "%.0f");
        styleSlider2("FramePadding", vars.framePadding, 0.0f, 20.0f, "%.0f");
        styleSlider2("ItemSpacing", vars.itemSpacing, 0.0f, 20.0f, "%.0f");
        styleSlider2("ItemInnerSpacing", vars.itemInnerSpacing, 0.0f, 20.0f, "%.0f");
        styleSlider2("TouchExtraPadding", vars.touchExtraPadding, 0.0f, 10.0f, "%.0f");
        vars.indentSpacing = styleSlider("IndentSpacing", vars.indentSpacing, 0.0f, 30.0f, "%.0f");
        vars.grabMinSize = styleSlider("GrabMinSize", vars.grabMinSize, 1.0f, 20.0f, "%.0f");

        IkGui.separatorText("Borders");
        vars.windowBorderSize =
                styleSlider("WindowBorderSize", vars.windowBorderSize, 0.0f, maxBorderSize, "%.0f");
        vars.childBorderSize =
                styleSlider("ChildBorderSize", vars.childBorderSize, 0.0f, maxBorderSize, "%.0f");
        vars.popupBorderSize =
                styleSlider("PopupBorderSize", vars.popupBorderSize, 0.0f, maxBorderSize, "%.0f");
        vars.frameBorderSize =
                styleSlider("FrameBorderSize", vars.frameBorderSize, 0.0f, maxBorderSize, "%.0f");

        IkGui.separatorText("Rounding");
        vars.windowRounding =
                styleSlider("WindowRounding", vars.windowRounding, 0.0f, 12.0f, "%.0f");
        vars.childRounding = styleSlider("ChildRounding", vars.childRounding, 0.0f, 12.0f, "%.0f");
        vars.frameRounding = styleSlider("FrameRounding", vars.frameRounding, 0.0f, 12.0f, "%.0f");
        vars.popupRounding = styleSlider("PopupRounding", vars.popupRounding, 0.0f, 12.0f, "%.0f");
        vars.grabRounding = styleSlider("GrabRounding", vars.grabRounding, 0.0f, 12.0f, "%.0f");
        vars.menuItemRounding =
                styleSlider("MenuItemRounding", vars.menuItemRounding, 0.0f, 12.0f, "%.0f");
        // The selectable rounding is intentionally not shown here, since we don't want to
        // encourage using it

        IkGui.separatorText("Scrollbar");
        vars.scrollbarSize = styleSlider("ScrollbarSize", vars.scrollbarSize, 1.0f, 20.0f, "%.0f");
        vars.scrollbarRounding =
                styleSlider("ScrollbarRounding", vars.scrollbarRounding, 0.0f, 12.0f, "%.0f");
        vars.scrollbarPadding =
                styleSlider("ScrollbarPadding", vars.scrollbarPadding, 0.0f, 10.0f, "%.0f");

        IkGui.separatorText("Tabs");
        vars.tabBorderSize =
                styleSlider("TabBorderSize", vars.tabBorderSize, 0.0f, maxBorderSize, "%.0f");
        vars.tabBarBorderSize =
                styleSlider("TabBarBorderSize", vars.tabBarBorderSize, 0.0f, maxBorderSize, "%.0f");
        vars.tabBarOverlineSize =
                styleSlider(
                        "TabBarOverlineSize",
                        vars.tabBarOverlineSize,
                        0.0f,
                        Math.max(3.0f, maxBorderSize),
                        "%.0f");
        IkGui.sameLine();
        helpMarker(
                "Overline is only drawn over the selected tab when"
                        + " TabBarFlags.DRAW_SELECTED_OVERLINE is set.");
        vars.tabMinWidthBase =
                styleDrag("TabMinWidthBase", vars.tabMinWidthBase, 0.5f, 1.0f, 500.0f, "%.0f");
        vars.tabMinWidthShrink =
                styleDrag("TabMinWidthShrink", vars.tabMinWidthShrink, 0.5f, 1.0f, 500.0f, "%.0f");
        vars.tabCloseButtonMinWidthSelected =
                styleDrag(
                        "TabCloseButtonMinWidthSelected",
                        vars.tabCloseButtonMinWidthSelected,
                        0.5f,
                        -1.0f,
                        100.0f,
                        vars.tabCloseButtonMinWidthSelected < 0.0f ? "%.0f (Always)" : "%.0f");
        vars.tabCloseButtonMinWidthUnselected =
                styleDrag(
                        "TabCloseButtonMinWidthUnselected",
                        vars.tabCloseButtonMinWidthUnselected,
                        0.5f,
                        -1.0f,
                        100.0f,
                        vars.tabCloseButtonMinWidthUnselected < 0.0f ? "%.0f (Always)" : "%.0f");
        vars.tabRounding = styleSlider("TabRounding", vars.tabRounding, 0.0f, 12.0f, "%.0f");

        IkGui.separatorText("Tables");
        styleSlider2("CellPadding", vars.cellPadding, 0.0f, 20.0f, "%.0f");
        // We store this angle in degrees
        vars.tableAngledHeadersAngle =
                styleSlider(
                        "TableAngledHeadersAngle",
                        vars.tableAngledHeadersAngle,
                        -50.0f,
                        50.0f,
                        "%.0f deg");
        styleSlider2(
                "TableAngledHeadersTextAlign",
                vars.tableAngledHeadersTextAlign,
                0.0f,
                1.0f,
                "%.2f");

        IkGui.separatorText("Trees");
        final boolean treeLinesComboOpen =
                IkGui.beginCombo("TreeLinesFlags", treeLinesFlagsName(vars.treeLinesFlags));
        IkGui.sameLine();
        helpMarker(
                "[Experimental] Tree lines may not work in all situations (e.g. using a clipper)"
                        + " and may incur slight traversal overhead.\n\n"
                        + "TreeNodeFlags.DRAW_LINES_FULL is faster than"
                        + " TreeNodeFlags.DRAW_LINES_TO_NODES.");
        if (treeLinesComboOpen) {
            for (int option :
                    new int[] {
                        TreeNodeFlags.DRAW_LINES_NONE,
                        TreeNodeFlags.DRAW_LINES_FULL,
                        TreeNodeFlags.DRAW_LINES_TO_NODES
                    }) {
                if (IkGui.selectable(treeLinesFlagsName(option), vars.treeLinesFlags == option)) {
                    vars.treeLinesFlags = option;
                }
            }
            IkGui.endCombo();
        }
        vars.treeLinesSize =
                styleSlider("TreeLinesSize", vars.treeLinesSize, 0.0f, maxBorderSize, "%.0f");
        vars.treeLinesRounding =
                styleSlider("TreeLinesRounding", vars.treeLinesRounding, 0.0f, 12.0f, "%.0f");

        IkGui.separatorText("Windows");
        styleSlider2("WindowTitleAlign", vars.windowTitleAlign, 0.0f, 1.0f, "%.2f");
        vars.windowBorderHoverPadding =
                styleSlider(
                        "WindowBorderHoverPadding",
                        vars.windowBorderHoverPadding,
                        1.0f,
                        20.0f,
                        "%.0f");
        vars.windowMenuButtonPosition =
                WindowMenuButtonPosition.fromInteger(
                        styleCombo(
                                "WindowMenuButtonPosition",
                                vars.windowMenuButtonPosition.getIntValue(),
                                new String[] {"None", "Left", "Right"}));
        // Not part of Dear ImGui
        final float[] edgeFade = {
            vars.windowEdgeFade.x,
            vars.windowEdgeFade.y,
            vars.windowEdgeFade.z,
            vars.windowEdgeFade.w
        };
        if (IkGui.sliderFloat4("WindowEdgeFade", edgeFade, 0.0f, 64.0f, "%.0f")) {
            vars.windowEdgeFade.set(edgeFade[0], edgeFade[1], edgeFade[2], edgeFade[3]);
        }
        IkGui.sameLine();
        helpMarker(
                "How far in from the left, top, right, and bottom edges the window background and"
                        + " title bar fade from transparent. 0 is a hard edge. Widgets don't fade.");
        if (IkGui.checkbox("WindowEdgeFadeInvert", vars.windowEdgeFadeInvert)) {
            vars.windowEdgeFadeInvert = !vars.windowEdgeFadeInvert;
        }
        IkGui.sameLine();
        helpMarker("Inverts the fade, so the fading edges are opaque and fade to transparent.");

        IkGui.separatorText("Widgets");
        vars.colorMarkerSize =
                styleSlider("ColorMarkerSize", vars.colorMarkerSize, 0.0f, 8.0f, "%.0f");
        vars.colorButtonPosition =
                ColorButtonPosition.fromInteger(
                        styleCombo(
                                "ColorButtonPosition",
                                vars.colorButtonPosition.getIntValue(),
                                new String[] {"Left", "Right"}));
        styleSlider2("ButtonTextAlign", vars.buttonTextAlign, 0.0f, 1.0f, "%.2f");
        IkGui.sameLine();
        helpMarker("Alignment applies when a button is larger than its text content.");
        styleSlider2("SelectableTextAlign", vars.selectableTextAlign, 0.0f, 1.0f, "%.2f");
        IkGui.sameLine();
        helpMarker("Alignment applies when a selectable is larger than its text content.");
        vars.separatorSize = styleSlider("SeparatorSize", vars.separatorSize, 0.0f, 10.0f, "%.0f");
        vars.separatorTextBorderSize =
                styleSlider(
                        "SeparatorTextBorderSize",
                        vars.separatorTextBorderSize,
                        0.0f,
                        10.0f,
                        "%.0f");
        styleSlider2("SeparatorTextAlign", vars.separatorTextAlign, 0.0f, 1.0f, "%.2f");
        styleSlider2("SeparatorTextPadding", vars.separatorTextPadding, 0.0f, 40.0f, "%.0f");
        vars.logSliderDeadzone =
                styleSlider("LogSliderDeadzone", vars.logSliderDeadzone, 0.0f, 12.0f, "%.0f");
        vars.imageRounding = styleSlider("ImageRounding", vars.imageRounding, 0.0f, 12.0f, "%.0f");
        vars.imageBorderSize =
                styleSlider("ImageBorderSize", vars.imageBorderSize, 0.0f, maxBorderSize, "%.0f");
        vars.inputTextCursorSize =
                styleSlider("InputTextCursorSize", vars.inputTextCursorSize, 1.0f, 4.0f, "%.0f");

        IkGui.separatorText("Docking");
        if (IkGui.checkbox("DockingNodeHasCloseButton", vars.dockingNodeHasCloseButton)) {
            vars.dockingNodeHasCloseButton = !vars.dockingNodeHasCloseButton;
        }
        vars.dockingSeparatorSize =
                styleSlider("DockingSeparatorSize", vars.dockingSeparatorSize, 0.0f, 12.0f, "%.0f");

        IkGui.separatorText("Drag and Drop");
        vars.dragDropTargetRounding =
                styleSlider(
                        "DragDropTargetRounding", vars.dragDropTargetRounding, 0.0f, 12.0f, "%.0f");
        vars.dragDropTargetBorderSize =
                styleSlider(
                        "DragDropTargetBorderSize",
                        vars.dragDropTargetBorderSize,
                        0.0f,
                        12.0f,
                        "%.0f");
        vars.dragDropTargetPadding =
                styleSlider(
                        "DragDropTargetPadding", vars.dragDropTargetPadding, 0.0f, 12.0f, "%.0f");

        IkGui.separatorText("Tooltips");
        for (int n = 0; n < 2; ++n) {
            if (IkGui.treeNodeEx(
                    n == 0 ? "HoverFlagsForTooltipMouse" : "HoverFlagsForTooltipNav")) {
                final IkInt flags =
                        new IkInt(
                                n == 0
                                        ? vars.hoverFlagsForTooltipMouse
                                        : vars.hoverFlagsForTooltipNav);
                IkGui.checkboxFlags("HoveredFlags.DELAY_NONE", flags, HoveredFlags.DELAY_NONE);
                IkGui.checkboxFlags("HoveredFlags.DELAY_SHORT", flags, HoveredFlags.DELAY_SHORT);
                IkGui.checkboxFlags("HoveredFlags.DELAY_NORMAL", flags, HoveredFlags.DELAY_NORMAL);
                IkGui.checkboxFlags("HoveredFlags.STATIONARY", flags, HoveredFlags.STATIONARY);
                IkGui.checkboxFlags(
                        "HoveredFlags.NO_SHARED_DELAY", flags, HoveredFlags.NO_SHARED_DELAY);
                if (n == 0) {
                    vars.hoverFlagsForTooltipMouse = flags.get();
                } else {
                    vars.hoverFlagsForTooltipNav = flags.get();
                }
                IkGui.treePop();
            }
        }

        IkGui.separatorText("Misc");
        styleSlider2("DisplayWindowPadding", vars.displayWindowPadding, 0.0f, 30.0f, "%.0f");
        IkGui.sameLine();
        helpMarker(
                "Apply to regular windows: amount which we enforce to keep visible when moving near"
                        + " edges of your screen.");
        styleSlider2("DisplaySafeAreaPadding", vars.displaySafeAreaPadding, 0.0f, 30.0f, "%.0f");
        IkGui.sameLine();
        helpMarker(
                "Apply to every windows, menus, popups, tooltips: amount where we avoid displaying"
                        + " contents. Adjust if you cannot see the edges of your screen (e.g. on a"
                        + " TV where scaling has not been configured).");
    }

    /**
     * The colors tab of the style editor.
     *
     * @param style The style being edited.
     * @param ref The reference style.
     */
    private static void showStyleEditorColors(Style style, Style ref) {
        if (IkGui.button("Export")) {
            if (styleEditorOutputDestination.get() == 0) {
                IkGui.logToClipboard();
            } else {
                IkGui.logToTTY();
            }
            IkGui.logText("StyleColors colors = IkGui.getStyle().color;\n");
            for (ColorType type : ColorType.values()) {
                final int color = style.color.get(type);
                if (!styleEditorOutputOnlyModified.get() || color != ref.color.get(type)) {
                    final String name = IkGui.getStyleColorName(type);
                    IkGui.logText(
                            String.format(
                                    "colors.set(ColorType.%s,%s Color.rgba(%d, %d, %d, %d));\n",
                                    name,
                                    " ".repeat(Math.max(0, 28 - name.length())),
                                    (color >>> 24) & 0xFF,
                                    (color >>> 16) & 0xFF,
                                    (color >>> 8) & 0xFF,
                                    color & 0xFF));
                }
            }
            IkGui.logFinish();
        }
        IkGui.sameLine();
        IkGui.setNextItemWidth(IkGui.getFontSize() * 10);
        IkGui.combo(
                "##output_type",
                styleEditorOutputDestination,
                new String[] {"To Clipboard", "To TTY"});
        IkGui.sameLine();
        IkGui.checkbox("Only Modified Colors", styleEditorOutputOnlyModified);

        if (IkGui.radioButton("Opaque", styleEditorAlphaFlags == ColorEditFlags.ALPHA_OPAQUE)) {
            styleEditorAlphaFlags = ColorEditFlags.ALPHA_OPAQUE;
        }
        IkGui.sameLine();
        if (IkGui.radioButton("Alpha", styleEditorAlphaFlags == ColorEditFlags.NONE)) {
            styleEditorAlphaFlags = ColorEditFlags.NONE;
        }
        IkGui.sameLine();
        if (IkGui.radioButton("Both", styleEditorAlphaFlags == ColorEditFlags.ALPHA_PREVIEW_HALF)) {
            styleEditorAlphaFlags = ColorEditFlags.ALPHA_PREVIEW_HALF;
        }
        IkGui.sameLine();
        helpMarker(
                "In the color list:\n"
                        + "Left-click on color square to open color picker,\n"
                        + "Right-click to open edit options menu.");

        IkGui.setNextItemWidth(-Float.MIN_VALUE);
        styleEditorColorFilter.drawWithHint("##FilterColors", "Filter Colors (incl -excl)");

        IkGui.setNextWindowSizeConstraints(
                0.0f, IkGui.getTextLineHeightWithSpacing() * 10, Float.MAX_VALUE, Float.MAX_VALUE);
        IkGui.beginChild(
                "##colors",
                0,
                0,
                ChildFlags.BORDERS | ChildFlags.NAV_FLATTENED,
                WindowFlags.ALWAYS_VERTICAL_SCROLLBAR | WindowFlags.ALWAYS_HORIZONTAL_SCROLLBAR);
        IkGui.pushItemWidth(IkGui.getFontSize() * -12);
        final float innerSpacing = style.variable.itemInnerSpacing.x;
        for (ColorType type : ColorType.values()) {
            final String name = IkGui.getStyleColorName(type);
            if (!styleEditorColorFilter.passFilter(name)) {
                continue;
            }
            IkGui.pushID(type.ordinal());
            if (IkGui.button("?")) {
                IkGui.debugFlashStyleColor(type);
            }
            IkGui.setItemTooltip("Flash given color to identify places where it is used.");
            IkGui.sameLine();
            final float[] components = colorToFloats(style.color.get(type));
            if (IkGui.colorEdit4(
                    "##color", components, ColorEditFlags.ALPHA_BAR | styleEditorAlphaFlags)) {
                style.color.set(type, floatsToColor(components));
            }
            if (style.color.get(type) != ref.color.get(type)) {
                IkGui.sameLine(0.0f, innerSpacing);
                if (IkGui.button("Save")) {
                    ref.color.set(type, style.color.get(type));
                }
                IkGui.sameLine(0.0f, innerSpacing);
                if (IkGui.button("Revert")) {
                    style.color.set(type, ref.color.get(type));
                }
            }
            IkGui.sameLine(0.0f, innerSpacing);
            IkGui.textUnformatted(name);
            IkGui.popID();
        }
        IkGui.popItemWidth();
        IkGui.endChild();
    }

    // ---------------------------------------------------------------------------------------------
    // Window options
    // ---------------------------------------------------------------------------------------------

    private static void showWindowOptionsSection() {
        if (!IkGui.collapsingHeader("Window options")) {
            return;
        }

        if (IkGui.beginTable("split", 3)) {
            final String[] labels = {
                "No titlebar",
                "No scrollbar",
                "No menu",
                "No move",
                "No resize",
                "No collapse",
                "No close",
                "No nav",
                "No background",
                "No bring to front",
                "No docking",
                "Unsaved document"
            };
            final IkBoolean[] values = {
                noTitleBar,
                noScrollbar,
                noMenu,
                noMove,
                noResize,
                noCollapse,
                noClose,
                noNav,
                noBackground,
                noBringToFront,
                noDocking,
                unsavedDocument
            };
            for (int i = 0; i < labels.length; i++) {
                IkGui.tableNextColumn();
                IkGui.checkbox(labels[i], values[i]);
            }
            IkGui.endTable();
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Widgets
    // ---------------------------------------------------------------------------------------------

    private static int basicClicked = 0;
    private static final IkBoolean basicCheck = new IkBoolean(true);
    private static final IkInt basicRadio = new IkInt(0);
    private static int basicCounter = 0;
    private static final IkInt basicComboItem = new IkInt(0);
    private static final IkInt basicListBoxItem = new IkInt(1);
    private static final IkString basicInputText = new IkString("Hello, world!", 128);
    private static final IkString basicInputHint = new IkString(128);
    private static final int[] basicInputInt = {123};
    private static final float[] basicInputFloat = {0.001f};
    private static final double[] basicInputDouble = {999_999.000_000_01};
    private static final float[] basicInputScientific = {1.e10f};
    private static final float[] basicInputFloat3 = {0.10f, 0.20f, 0.30f, 0.44f};
    private static final float[] basicColor1 = {1.0f, 0.0f, 0.2f};
    private static final IkInt treeLinesBaseFlags =
            new IkInt(TreeNodeFlags.DRAW_LINES_FULL | TreeNodeFlags.DEFAULT_OPEN);
    private static final float[] basicColor2 = {0.4f, 0.7f, 0.0f, 0.5f};
    private static final String[] BASIC_LIST_BOX_ITEMS = {
        "Apple",
        "Banana",
        "Cherry",
        "Kiwi",
        "Mango",
        "Orange",
        "Pineapple",
        "Strawberry",
        "Watermelon"
    };
    private static final String[] BASIC_COMBO_ITEMS = {
        "AAAA", "BBBB", "CCCC", "DDDD", "EEEE", "FFFF", "GGGG", "HHHH", "IIIIIII", "JJJJ", "KKKKKKK"
    };
    private static final IkBoolean closableGroup = new IkBoolean(true);
    private static final IkInt queryItemType = new IkInt(4);
    private static final IkBoolean queryCheckbox = new IkBoolean(false);
    private static final IkBoolean queryItemDisabled = new IkBoolean(false);
    private static final IkBoolean queryItemMixedValue = new IkBoolean(false);
    private static final IkBoolean queryLiveEditOverride = new IkBoolean(false);
    private static final IkInt queryLiveEditFlags = new IkInt(0);
    private static final float[] queryColor = {1.0f, 0.5f, 0.0f, 1.0f};
    private static final IkString queryString = new IkString(16);
    private static final IkString queryUnused = new IkString(1);
    private static final String[] QUERY_FRUITS = {"Apple", "Banana", "Cherry", "Kiwi"};
    private static final IkInt queryComboItem = new IkInt(1);
    private static final IkInt queryListBoxItem = new IkInt(1);
    private static final IkBoolean embedAllInsideChild = new IkBoolean(false);
    private static float wrapWidth = 200.0f;
    private static final IkInt treeBaseFlags =
            new IkInt(
                    TreeNodeFlags.OPEN_ON_ARROW
                            | TreeNodeFlags.OPEN_ON_DOUBLE_CLICK
                            | TreeNodeFlags.SPAN_AVAIL_WIDTH);
    private static final IkBoolean treeAlignLabel = new IkBoolean(false);
    private static final IkBoolean treeDragAndDrop = new IkBoolean(false);
    private static int treeSelectionMask = 0;

    private static void showWidgetsSection() {
        if (!IkGui.collapsingHeader("Widgets")) {
            return;
        }

        final boolean disabled = disableAll.get();
        final boolean overrideLiveEdit = liveEditOverride.get();
        if (disabled) {
            IkGui.beginDisabled();
        }
        if (overrideLiveEdit) {
            IkGui.pushItemFlag(
                    ItemFlags.LIVE_EDIT_ON_INPUT_TEXT,
                    (liveEditFlags.get() & ItemFlags.LIVE_EDIT_ON_INPUT_TEXT) != 0);
            IkGui.pushItemFlag(
                    ItemFlags.LIVE_EDIT_ON_INPUT_SCALAR,
                    (liveEditFlags.get() & ItemFlags.LIVE_EDIT_ON_INPUT_SCALAR) != 0);
        }

        showWidgetsBasic();
        showWidgetsBullets();
        showWidgetsCollapsingHeaders();
        showWidgetsCombo();
        showWidgetsColorAndPickers();
        showWidgetsDataTypes();

        // The "Disable all" checkbox is in this section, so it is never disabled
        if (disabled) {
            IkGui.endDisabled();
        }
        showWidgetsDisableBlocks();
        if (disabled) {
            IkGui.beginDisabled();
        }

        showWidgetsDragAndDrop();
        showWidgetsDragAndSliderFlags();
        showWidgetsFonts();
        showWidgetsImages();
        showWidgetsListBoxes();
        showWidgetsLiveEdit();
        showWidgetsMixedValues();
        showWidgetsMultiComponents();
        showWidgetsPlotting();
        showWidgetsProgressBars();
        showWidgetsQueryingStatus();
        showWidgetsSelectables();
        showWidgetsSelectionAndMultiSelect();
        showWidgetsTabs();
        showWidgetsText();
        showWidgetsTextFilter();
        showWidgetsTextInput();
        showWidgetsTooltips();
        showWidgetsTrees();
        showWidgetsVerticalSliders();

        if (overrideLiveEdit) {
            IkGui.popItemFlag();
            IkGui.popItemFlag();
        }
        if (disabled) {
            IkGui.endDisabled();
        }
    }

    private static void showWidgetsDisableBlocks() {
        if (IkGui.treeNode("Disable Blocks")) {
            IkGui.checkbox("Disable entire section above", disableAll);
            IkGui.sameLine();
            helpMarker("Demonstrate using beginDisabled()/endDisabled() across other sections.");
            IkGui.treePop();
        }
    }

    private static void showWidgetsBasic() {
        if (!IkGui.treeNode("Basic")) {
            return;
        }

        IkGui.separatorText("General");
        if (IkGui.button("Button")) {
            basicClicked++;
        }
        if ((basicClicked & 1) != 0) {
            IkGui.sameLine();
            IkGui.text("Thanks for clicking me!");
        }

        IkGui.checkbox("checkbox", basicCheck);

        IkGui.radioButton("radio a", basicRadio, 0);
        IkGui.sameLine();
        IkGui.radioButton("radio b", basicRadio, 1);
        IkGui.sameLine();
        IkGui.radioButton("radio c", basicRadio, 2);

        IkGui.alignTextToFramePadding();
        IkGui.textLinkOpenURL("Hyperlink", "https://github.com/ocornut/imgui/wiki/Error-Handling");

        // Color buttons, demonstrate using pushID() to add unique identifier in the ID stack, and
        // changing style
        for (int i = 0; i < 7; i++) {
            if (i > 0) {
                IkGui.sameLine();
            }
            IkGui.pushID(i);
            IkGui.pushStyleColor(ColorType.BUTTON, Color.hsv(i / 7.0f, 0.6f, 0.6f));
            IkGui.pushStyleColor(ColorType.BUTTON_HOVERED, Color.hsv(i / 7.0f, 0.7f, 0.7f));
            IkGui.pushStyleColor(ColorType.BUTTON_ACTIVE, Color.hsv(i / 7.0f, 0.8f, 0.8f));
            IkGui.button("Click");
            IkGui.popStyleColor(3);
            IkGui.popID();
        }

        // Use alignTextToFramePadding() to align text baseline to the baseline of framed widgets
        // elements (otherwise a Text+SameLine+Button sequence will have the text a little too high
        // by default!) See 'Demo->Layout->Text Baseline Alignment' for details.
        IkGui.alignTextToFramePadding();
        IkGui.text("Hold to repeat:");
        IkGui.sameLine();

        // Arrow buttons with repeater
        final float spacing = IkGui.getContext().style.variable.itemInnerSpacing.x;
        IkGui.pushItemFlag(ItemFlags.BUTTON_REPEAT, true);
        if (IkGui.arrowButton("##left", Direction.LEFT)) {
            basicCounter--;
        }
        IkGui.sameLine(0.0f, spacing);
        if (IkGui.arrowButton("##right", Direction.RIGHT)) {
            basicCounter++;
        }
        IkGui.popItemFlag();
        IkGui.sameLine();
        IkGui.text(String.valueOf(basicCounter));

        IkGui.button("Tooltip");
        IkGui.setItemTooltip("I am a tooltip");

        IkGui.labelText("label", "Value");

        IkGui.separatorText("Inputs");

        // IkString resizes as needed, see 'Demo->Widgets->Text Input->Resize Callback'
        IkGui.inputText("input text", basicInputText);
        IkGui.sameLine();
        helpMarker(
                "USER:\n"
                        + "Hold Shift or use mouse to select text.\n"
                        + "Ctrl+Left/Right to word jump.\n"
                        + "Ctrl+A or Double-Click to select all.\n"
                        + "Ctrl+X,Ctrl+C,Ctrl+V for clipboard.\n"
                        + "Ctrl+Z to undo, Ctrl+Y/Ctrl+Shift+Z to redo.\n"
                        + "Escape to revert.\n\n"
                        + "PROGRAMMER:\n"
                        + "IkString grows as needed, so inputText() works with strings of any "
                        + "length.");

        IkGui.inputTextWithHint("input text (w/ hint)", "enter text here", basicInputHint);

        IkGui.inputInt("input int", basicInputInt);
        IkGui.inputFloat("input float", basicInputFloat, 0.01f, 1.0f, "%.3f");
        IkGui.inputDouble("input double", basicInputDouble, 0.01, 1.0, "%.8f");
        IkGui.inputFloat("input scientific", basicInputScientific, 0.0f, 0.0f, "%e");
        IkGui.sameLine();
        helpMarker(
                "You can input value using the scientific notation,\n"
                        + "  e.g. \"1e+8\" becomes \"100000000\".");
        IkGui.inputFloat3("input float3", basicInputFloat3);

        IkGui.separatorText("Drags");

        IkGui.dragInt("drag int", basicDragInt1, 1);
        IkGui.sameLine();
        helpMarker(
                "Click and drag to edit value.\n"
                        + "Hold Shift/Alt for faster/slower edit.\n"
                        + "Double-Click or Ctrl+Click to input value.");
        IkGui.dragInt(
                "drag int 0..100", basicDragInt2, 1, 0, 100, "%d%%", SliderFlags.ALWAYS_CLAMP);
        IkGui.dragInt(
                "drag int wrap 100..200",
                basicDragInt3,
                1,
                100,
                200,
                "%d",
                SliderFlags.WRAP_AROUND);

        IkGui.dragFloat("drag float", basicDragFloat1, 0.005f);
        IkGui.dragFloat("drag small float", basicDragFloat2, 0.0001f, 0.0f, 0.0f, "%.06f ns");

        IkGui.separatorText("Sliders");

        IkGui.sliderInt("slider int", basicSliderInt, -1, 3);
        IkGui.sameLine();
        helpMarker("Ctrl+Click to input value.");

        IkGui.sliderFloat("slider float", basicSliderFloat1, 0.0f, 1.0f, "ratio = %.3f");
        IkGui.sliderFloat(
                "slider float (log)",
                basicSliderFloat2,
                -10.0f,
                10.0f,
                "%.4f",
                SliderFlags.LOGARITHMIC);

        IkGui.sliderAngle("slider angle", basicSliderAngle);

        // Using the format string to display a name instead of an integer. Here we completely
        // omit '%d' from the format string, so it'll only display a name. This technique can also
        // be used with dragInt().
        final int element = basicSliderElement[0];
        final String elementName =
                element >= 0 && element < ELEMENT_NAMES.length ? ELEMENT_NAMES[element] : "Unknown";
        // Use SliderFlags.NO_INPUT to disable Ctrl+Click here
        IkGui.sliderInt(
                "slider enum", basicSliderElement, 0, ELEMENT_NAMES.length - 1, elementName);
        IkGui.sameLine();
        helpMarker(
                "Using the format string parameter to display a name instead of the underlying"
                        + " integer.");

        IkGui.separatorText("Selectors/Pickers");

        IkGui.colorEdit3("color 1", basicColor1);
        IkGui.sameLine();
        helpMarker(
                "Click on the color square to open a color picker.\n"
                        + "Click and hold to use drag and drop.\n"
                        + "Right-Click on the color square to show options.\n"
                        + "Ctrl+Click on individual component to input value.\n");
        IkGui.colorEdit4("color 2", basicColor2);

        // Using the simplified one-liner combo() api here. See "Combo" section for examples of how
        // to use the more flexible beginCombo()/endCombo() api.
        IkGui.combo("combo", basicComboItem, BASIC_COMBO_ITEMS);
        IkGui.sameLine();
        helpMarker(
                "Using the simplified one-liner Combo API here.\n"
                        + "Refer to the \"Combo\" section below for an explanation of how to use"
                        + " the more flexible and general BeginCombo/EndCombo API.");

        // Using the simplified one-liner listBox() api here. See "List boxes" section for
        // examples of how to use the more flexible beginListBox()/endListBox() api.
        IkGui.listBox("listbox", basicListBoxItem, BASIC_LIST_BOX_ITEMS, 4);
        IkGui.sameLine();
        helpMarker(
                "Using the simplified one-liner ListBox API here.\n"
                        + "Refer to the \"List boxes\" section below for an explanation of how to"
                        + " use the more flexible and general BeginListBox/EndListBox API.");

        IkGui.treePop();
    }

    private static void showWidgetsFonts() {
        if (!IkGui.treeNode("Fonts")) {
            return;
        }
        IkGui.showFontAtlas(IkGui.getIO().fonts);
        IkGui.treePop();
    }

    /** Whether the live edit flags below are applied to the whole widgets section. */
    private static final IkBoolean liveEditOverride = new IkBoolean(false);

    private static final IkInt liveEditFlags = new IkInt(ItemFlags.LIVE_EDIT_ON_INPUT_TEXT);
    private static final IkString liveEditString = new IkString(32);
    private static final IkInt liveEditInt = new IkInt(0);
    private static final float[] liveEditFloat = {0.0f};

    private static void showWidgetsLiveEdit() {
        if (!IkGui.treeNode("Live Edit Flags")) {
            return;
        }
        IkGui.textWrapped(
                "Select whether to apply keyboard edits to backing variables _while_ typing.");

        IkGui.checkbox("Override Live Edit Flags in Demo Window", liveEditOverride);
        if (!liveEditOverride.get()) {
            liveEditFlags.set(IkGui.getItemFlags());
        }

        IkGui.beginDisabled(!liveEditOverride.get());
        IkGui.indent();
        IkGui.checkboxFlags(
                "ItemFlags.LIVE_EDIT_ON_INPUT_TEXT",
                liveEditFlags,
                ItemFlags.LIVE_EDIT_ON_INPUT_TEXT);
        IkGui.checkboxFlags(
                "ItemFlags.LIVE_EDIT_ON_INPUT_SCALAR",
                liveEditFlags,
                ItemFlags.LIVE_EDIT_ON_INPUT_SCALAR);
        IkGui.unindent();
        IkGui.endDisabled();

        IkGui.text("Try typing '123' and seeing effect on backing value:");
        IkGui.inputText("str", liveEditString);
        IkGui.text("Backing value: \"" + liveEditString.get() + "\"");
        IkGui.inputInt("int", liveEditInt, 0, 0);
        IkGui.text("Backing value: " + liveEditInt.get());
        IkGui.sliderFloat("float", liveEditFloat, 0.0f, 100.0f);
        IkGui.text(String.format("Backing value: %f", liveEditFloat[0]));

        IkGui.treePop();
    }

    /** Whether the mixed values demo applies keyboard edits while typing. */
    private static final IkBoolean mixedValuesLiveEdit = new IkBoolean(false);

    /** The values being edited together by the mixed values demo. The first is the reference. */
    static final float[] mixedValuesItems = {12.0f, 0.0f, 0.0f};

    /**
     * Editing several values with one widget, like a property editor with several objects selected.
     * When the values differ, the widgets show the mixed value label instead.
     */
    static void showWidgetsMixedValues() {
        if (!IkGui.treeNode("Mixed Values")) {
            return;
        }
        // This is designed for advanced property editors which are generally reusable and
        // data-driven
        helpMarker("Using ItemFlags.MIXED_VALUE.");

        final float[] items = mixedValuesItems;
        IkGui.checkbox("ItemFlags.LIVE_EDIT_ON_INPUT", mixedValuesLiveEdit);

        IkGui.separatorText("Scalar/Text Widgets");
        final boolean isMixed =
                Float.floatToIntBits(items[0]) != Float.floatToIntBits(items[1])
                        || Float.floatToIntBits(items[0]) != Float.floatToIntBits(items[2]);

        // Drags, sliders and inputs all edit the first value, which is copied to the others
        IkGui.pushItemFlag(ItemFlags.LIVE_EDIT_ON_INPUT, mixedValuesLiveEdit.get());
        IkGui.pushItemFlag(ItemFlags.MIXED_VALUE, isMixed);
        final float[] reference = {items[0]};
        boolean edited = false;
        edited |= IkGui.dragFloat("DragFloat", reference);
        edited |= IkGui.sliderFloat("SliderFloat", reference, 0.0f, 100.0f);
        edited |= IkGui.inputFloat("InputFloat", reference, 1.0f);
        if (edited) {
            Arrays.fill(items, reference[0]);
        }
        IkGui.popItemFlag();

        IkGui.text("Underlying data:");
        final float[] item = new float[1];
        for (int i = 0; i < items.length; ++i) {
            item[0] = items[i];
            if (IkGui.inputFloat(i == 0 ? "item 0 (ref)" : "item " + i, item)) {
                items[i] = item[0];
            }
        }
        IkGui.popItemFlag();

        // Checkbox(), radioButton(), combo() and colorEdit4()
        IkGui.separatorText("Others Widgets");
        // Applying edits would need more state tracking
        IkGui.text("(note: edits are not applied in this demo)");
        IkGui.checkbox("Checkbox On", true);
        IkGui.checkbox("Checkbox Off", false);
        IkGui.pushItemFlag(ItemFlags.MIXED_VALUE, true);
        IkGui.checkbox("Checkbox Mixed", false);
        IkGui.radioButton("RadioButton Mixed", true);
        IkGui.sameLine();
        // Showing 2 radio buttons makes the example more clear
        IkGui.radioButton("RadioButton Mixed##2", true);
        IkGui.combo("Combo", new IkInt(0), new String[] {"One", "Two", "Three"});
        IkGui.colorEdit4("ColorEdit4", new float[] {0.5f, 0.5f, 0.5f, 0.5f});
        IkGui.popItemFlag();

        IkGui.treePop();
    }

    private static void showWidgetsBullets() {
        if (!IkGui.treeNode("Bullets")) {
            return;
        }
        IkGui.bulletText("Bullet point 1");
        IkGui.bulletText("Bullet point 2\nOn multiple lines");
        if (IkGui.treeNode("Tree node")) {
            IkGui.bulletText("Another bullet point");
            IkGui.treePop();
        }
        IkGui.bullet();
        IkGui.text("Bullet point 3 (two calls)");
        IkGui.bullet();
        IkGui.smallButton("Button");
        IkGui.treePop();
    }

    private static void showWidgetsCollapsingHeaders() {
        if (!IkGui.treeNode("Collapsing Headers")) {
            return;
        }
        IkGui.checkbox("Show 2nd header", closableGroup);
        if (IkGui.collapsingHeader("Header", TreeNodeFlags.NONE)) {
            IkGui.text("IsItemHovered: " + (IkGui.isItemHovered() ? 1 : 0));
            for (int i = 0; i < 5; i++) {
                IkGui.text("Some content " + i);
            }
        }
        if (IkGui.collapsingHeader("Header with a close button", closableGroup)) {
            IkGui.text("IsItemHovered: " + (IkGui.isItemHovered() ? 1 : 0));
            for (int i = 0; i < 5; i++) {
                IkGui.text("More content " + i);
            }
        }
        IkGui.treePop();
    }

    /** The state of the interactive image viewer. */
    static class ExampleImageViewerData {
        int imageBackgroundColor = Color.rgba(100, 100, 100, 255);
        int gridColor = Color.rgba(255, 255, 255, 100);
        final IkBoolean gridEnabled = new IkBoolean(true);
        boolean viewReset = true;

        /** The view offset, in image space. */
        final Vector2f viewOffset = new Vector2f();

        float zoom = 10.0f;
        float zoomMin = 1.0f;
        float zoomMax = 10_000.0f;
    }

    private static final ExampleImageViewerData imageViewer = new ExampleImageViewerData();
    private static int imagesPressedCount = 0;

    static void exampleImageViewerDrawOptions(ExampleImageViewerData data) {
        IkGui.setNextItemShortcut(KeyChord.of(Key.G), InputFlags.TOOLTIP);
        IkGui.checkbox("Grid", data.gridEnabled);
        IkGui.sameLine();
        IkGui.setNextItemWidth(IkGui.getFontSize() * 10.0f);
        final float[] zoom100 = {data.zoom * 100.0f};
        if (IkGui.dragFloat(
                "##Zoom",
                zoom100,
                5.0f,
                data.zoomMin * 100.0f,
                data.zoomMax * 100.0f,
                "%.0f%%",
                SliderFlags.ALWAYS_CLAMP)) {
            data.zoom = zoom100[0] / 100.0f;
        }
    }

    static void exampleImageViewerDrawCanvas(
            ExampleImageViewerData data,
            float canvasWidth,
            float canvasHeight,
            TextureHandle texture,
            int imageWidth,
            int imageHeight) {
        final IkIO io = IkGui.getIO();
        final DrawList drawList = IkGui.getWindowDrawList();

        // Layout canvas
        IkGui.invisibleButton("##Canvas", canvasWidth, canvasHeight);
        final Vector2f canvasMin = IkGui.getItemRectMin();
        final Vector2f canvasMax = IkGui.getItemRectMax();

        if (data.viewReset) {
            // Add half a pixel padding
            data.viewOffset.set(
                    canvasWidth * 0.5f / data.zoom - 0.5f, canvasHeight * 0.5f / data.zoom - 0.5f);
        }
        data.viewReset = false;

        // Handle inputs. Taking ownership of the wheel stops it from scrolling the window.
        if (IkGui.setItemKeyOwner(Key.MOUSE_WHEEL_Y) && io.mouseWheel != 0.0f) {
            data.zoom =
                    MathUtil.clamp(
                            data.zoom * (1.0f + io.mouseWheel * 0.10f), data.zoomMin, data.zoomMax);
        }
        final float zoom = data.zoom;
        if (IkGui.isItemActive() && IkGui.isMouseDragging(MouseButton.LEFT)) {
            data.viewOffset.x -= io.mouseDelta.x / zoom;
            data.viewOffset.y -= io.mouseDelta.y / zoom;
        }

        // Display image
        final float imageMinX = (int) (canvasMin.x - data.viewOffset.x * zoom + canvasWidth * 0.5f);
        final float imageMinY =
                (int) (canvasMin.y - data.viewOffset.y * zoom + canvasHeight * 0.5f);
        final float imageMaxX = (int) (imageMinX + imageWidth * zoom);
        final float imageMaxY = (int) (imageMinY + imageHeight * zoom);
        drawList.addRect(
                canvasMin.x - 1.0f,
                canvasMin.y - 1.0f,
                canvasMax.x + 1.0f,
                canvasMax.y + 1.0f,
                Color.rgba(255, 255, 255, 255));
        drawList.pushClipRect(canvasMin.x, canvasMin.y, canvasMax.x, canvasMax.y, true);
        drawList.addRectFilled(
                imageMinX, imageMinY, imageMaxX, imageMaxY, data.imageBackgroundColor);
        // Upstream switches to nearest sampling around the image when the backend supports it
        drawList.addImage(texture, imageMinX, imageMinY, imageMaxX, imageMaxY);

        // Display grid lines for visible pixels
        if (data.gridEnabled.get() && zoom > 6.0f) {
            final float step = zoom;
            for (int px = (int) ((canvasMin.x - imageMinX) / step);
                    px <= (int) ((canvasMax.x - imageMinX) / step);
                    px++) {
                drawList.addLineV(
                        imageMinX + px * step, canvasMin.y, canvasMax.y, data.gridColor, 1.0f);
            }
            for (int py = (int) ((canvasMin.y - imageMinY) / step);
                    py <= (int) ((canvasMax.y - imageMinY) / step);
                    py++) {
                drawList.addLineH(
                        canvasMin.x, canvasMax.x, imageMinY + py * step, data.gridColor, 1.0f);
            }
        }
        drawList.popClipRect();
    }

    private static void showWidgetsImages() {
        if (!IkGui.treeNode("Images")) {
            return;
        }
        IkGui.textWrapped(
                "Below we are displaying the font texture (which is the only texture we have access"
                        + " to in this demo). Use the 'TextureHandle' type to pass your own textures."
                        + " Hover the texture for a zoomed view!");

        // Below we are displaying the font texture because it is the only texture we have access
        // to inside the demo! Any TextureHandle created by the rendering backend can be passed to
        // image(), imageButton() or DrawList.addImage(). You can use showMetricsWindow() to
        // inspect the draw data that are being passed to your renderer.

        // Grab the texture used by the font atlas, which the rendering backend sets
        final FontAtlas atlas = IkGui.getIO().fonts;
        final TextureHandle texture = atlas.texture;
        if (texture == null) {
            IkGui.textDisabled("(The rendering backend hasn't set the font atlas texture yet.)");
            IkGui.treePop();
            return;
        }
        final float textureWidth = FontAtlas.FONT_ATLAS_IMAGE_WIDTH;
        final float textureHeight = FontAtlas.FONT_ATLAS_IMAGE_HEIGHT;
        IkGui.text(String.format("%.0fx%.0f", textureWidth, textureHeight));

        // Basic drawing. The atlas texture is large and mostly empty, so we only show the part in
        // use, scaled down to fit.
        IkGui.separatorText("image()/imageWithBackground() function");
        final float usedHeight = MathUtil.clamp(atlas.getShelvesHeight(), 1.0f, textureHeight);
        final float scale = Math.min(1.0f, IkGui.getContentRegionAvailableX() / textureWidth);
        IkGui.pushStyleVarFloat(
                StyleVariable.IMAGE_BORDER_SIZE,
                Math.max(1.0f, IkGui.getStyle().variable.imageBorderSize));
        IkGui.imageWithBackground(
                texture,
                textureWidth * scale,
                usedHeight * scale,
                0.0f,
                0.0f,
                1.0f,
                usedHeight / textureHeight,
                Color.rgba(0.0f, 0.0f, 0.0f, 1.0f),
                Color.WHITE);
        IkGui.popStyleVar();

        // Fancy widget
        IkGui.separatorText("Interactive Image Viewer");
        exampleImageViewerDrawOptions(imageViewer);
        exampleImageViewerDrawCanvas(
                imageViewer,
                IkGui.getContentRegionAvailableX(),
                Math.min(usedHeight * 2.0f, IkGui.getFontSize() * 30.0f),
                texture,
                (int) textureWidth,
                (int) textureHeight);

        IkGui.separatorText("Textured Buttons");
        IkGui.textWrapped("And now some textured buttons..");
        for (int i = 0; i < 8; i++) {
            // UV coordinates are often (0.0f, 0.0f) and (1.0f, 1.0f) to display an entire texture.
            // Here are trying to display only a 32x32 pixels area of the texture, hence the UV
            // computation.
            IkGui.pushID(i);
            if (i > 0) {
                IkGui.pushStyleVarFloat2(StyleVariable.FRAME_PADDING, i - 1.0f, i - 1.0f);
            }
            // UV coordinates for (32,32) in our texture
            final float u1 = 32.0f / textureWidth;
            final float v1 = 32.0f / textureHeight;
            final int backgroundColor = Color.rgba(0.0f, 0.0f, 0.0f, 1.0f); // Black background
            final int tintColor = Color.rgba(1.0f, 1.0f, 1.0f, 1.0f); // No tint
            if (IkGui.imageButton(
                    "", texture, 32.0f, 32.0f, 0.0f, 0.0f, u1, v1, backgroundColor, tintColor)) {
                imagesPressedCount += 1;
            }
            if (i > 0) {
                IkGui.popStyleVar();
            }
            IkGui.popID();
            IkGui.sameLine();
        }
        IkGui.newLine();
        IkGui.text(String.format("Pressed %d times.", imagesPressedCount));
        IkGui.treePop();
    }

    private static void showWidgetsQueryingStatus() {
        if (IkGui.treeNode("Querying Item Status (Edited/Active/Hovered etc.)")) {
            // Select an item type
            final String[] itemNames = {
                "Text",
                "Button",
                "Button (w/ repeat)",
                "Checkbox",
                "SliderFloat",
                "InputText",
                "InputTextMultiline",
                "InputFloat",
                "InputFloat3",
                "ColorEdit4",
                "Selectable",
                "MenuItem",
                "TreeNode",
                "TreeNode (w/ double-click)",
                "Combo",
                "ListBox"
            };
            IkGui.combo("Item Type", queryItemType, itemNames, itemNames.length);
            IkGui.sameLine();
            helpMarker(
                    "Testing how various types of items are interacting with the isItemXXX"
                            + " functions. Note that the bool return value of most IkGui functions"
                            + " is generally equivalent to calling IkGui.isItemHovered().");
            IkGui.checkbox("Item Disabled", queryItemDisabled);
            IkGui.checkbox("Item MixedValue", queryItemMixedValue);
            IkGui.checkbox("Override LiveEdit:", queryLiveEditOverride);
            IkGui.sameLine();
            if (!queryLiveEditOverride.get()) {
                queryLiveEditFlags.set(IkGui.getItemFlags());
            }
            IkGui.beginDisabled(!queryLiveEditOverride.get());
            IkGui.checkboxFlags(
                    "LIVE_EDIT_ON_INPUT", queryLiveEditFlags, ItemFlags.LIVE_EDIT_ON_INPUT);
            IkGui.sameLine();
            IkGui.checkboxFlags(
                    "LIVE_EDIT_ON_INPUT_TEXT",
                    queryLiveEditFlags,
                    ItemFlags.LIVE_EDIT_ON_INPUT_TEXT);
            IkGui.sameLine();
            IkGui.checkboxFlags(
                    "LIVE_EDIT_ON_INPUT_SCALAR",
                    queryLiveEditFlags,
                    ItemFlags.LIVE_EDIT_ON_INPUT_SCALAR);
            IkGui.endDisabled();
            final boolean liveEditOverride = queryLiveEditOverride.get();
            if (liveEditOverride) {
                IkGui.pushItemFlag(
                        ItemFlags.LIVE_EDIT_ON_INPUT_TEXT,
                        (queryLiveEditFlags.get() & ItemFlags.LIVE_EDIT_ON_INPUT_TEXT) != 0);
                IkGui.pushItemFlag(
                        ItemFlags.LIVE_EDIT_ON_INPUT_SCALAR,
                        (queryLiveEditFlags.get() & ItemFlags.LIVE_EDIT_ON_INPUT_SCALAR) != 0);
            }

            // Submit the selected item so we can query its status in the code following it
            final boolean disabled = queryItemDisabled.get();
            final boolean mixedValue = queryItemMixedValue.get();
            if (disabled) {
                IkGui.beginDisabled(true);
            }
            if (mixedValue) {
                IkGui.pushItemFlag(ItemFlags.MIXED_VALUE, true);
            }
            boolean returnValue = false;
            switch (queryItemType.get()) {
                case 0 -> IkGui.text("ITEM: Text");
                case 1 -> returnValue = IkGui.button("ITEM: Button");
                case 2 -> {
                    IkGui.pushItemFlag(ItemFlags.BUTTON_REPEAT, true);
                    returnValue = IkGui.button("ITEM: Button");
                    IkGui.popItemFlag();
                }
                case 3 -> returnValue = IkGui.checkbox("ITEM: Checkbox", queryCheckbox);
                case 4 -> returnValue = IkGui.sliderFloat("ITEM: SliderFloat", queryColor, 0, 1);
                case 5 -> returnValue = IkGui.inputText("ITEM: InputText", queryString);
                case 6 ->
                        returnValue =
                                IkGui.inputTextMultiline("ITEM: InputTextMultiline", queryString);
                case 7 -> returnValue = IkGui.inputFloat("ITEM: InputFloat", queryColor, 1.0f);
                case 8 -> returnValue = IkGui.inputFloat3("ITEM: InputFloat3", queryColor);
                case 9 -> returnValue = IkGui.colorEdit4("ITEM: ColorEdit4", queryColor);
                case 10 -> returnValue = IkGui.selectable("ITEM: Selectable");
                case 11 -> returnValue = IkGui.menuItem("ITEM: MenuItem");
                case 12 -> {
                    returnValue = IkGui.treeNode("ITEM: TreeNode");
                    if (returnValue) {
                        IkGui.treePop();
                    }
                }
                case 13 ->
                        // The tree node uses the PRESSED_ON_DOUBLE_CLICK button behavior
                        returnValue =
                                IkGui.treeNodeEx(
                                        "ITEM: TreeNode w/ TreeNodeFlags.OPEN_ON_DOUBLE_CLICK",
                                        TreeNodeFlags.OPEN_ON_DOUBLE_CLICK
                                                | TreeNodeFlags.NO_TREE_PUSH_ON_OPEN);
                case 14 -> returnValue = IkGui.combo("ITEM: Combo", queryComboItem, QUERY_FRUITS);
                default ->
                        returnValue =
                                IkGui.listBox(
                                        "ITEM: ListBox",
                                        queryListBoxItem,
                                        QUERY_FRUITS,
                                        QUERY_FRUITS.length);
            }

            final boolean hoveredDelayNone = IkGui.isItemHovered();
            final boolean hoveredDelayStationary = IkGui.isItemHovered(HoveredFlags.STATIONARY);
            final boolean hoveredDelayShort = IkGui.isItemHovered(HoveredFlags.DELAY_SHORT);
            final boolean hoveredDelayNormal = IkGui.isItemHovered(HoveredFlags.DELAY_NORMAL);
            // FOR_TOOLTIP is DELAY_NORMAL + STATIONARY
            final boolean hoveredDelayTooltip = IkGui.isItemHovered(HoveredFlags.FOR_TOOLTIP);

            // Display the values of isItemHovered() and other common item state functions. Since
            // bulletText() is an item itself and would affect the results of the isItemXXX
            // functions, everything is queried before submitting it.
            final Vector2f rectMin = new Vector2f();
            final Vector2f rectMax = new Vector2f();
            final Vector2f rectSize = new Vector2f();
            IkGui.getItemRectMin(rectMin);
            IkGui.getItemRectMax(rectMax);
            IkGui.getItemRectSize(rectSize);
            final String status =
                    String.format(
                            "Return value = %b%n"
                                    + "isItemFocused() = %b%n"
                                    + "isItemHovered() = %b%n"
                                    + "isItemHovered(ALLOW_WHEN_BLOCKED_BY_POPUP) = %b%n"
                                    + "isItemHovered(ALLOW_WHEN_BLOCKED_BY_ACTIVE_ITEM) = %b%n"
                                    + "isItemHovered(ALLOW_WHEN_OVERLAPPED_BY_ITEM) = %b%n"
                                    + "isItemHovered(ALLOW_WHEN_OVERLAPPED_BY_WINDOW) = %b%n"
                                    + "isItemHovered(ALLOW_WHEN_DISABLED) = %b%n"
                                    + "isItemHovered(RECT_ONLY) = %b%n"
                                    + "isItemActive() = %b%n"
                                    + "isItemEdited() = %b%n"
                                    + "isItemActivated() = %b%n"
                                    + "isItemDeactivated() = %b%n"
                                    + "isItemDeactivatedAfterEdit() = %b%n"
                                    + "isItemVisible() = %b%n"
                                    + "isItemClicked() = %b%n"
                                    + "isItemToggledOpen() = %b%n"
                                    + "getItemRectMin() = (%.1f, %.1f)%n"
                                    + "getItemRectMax() = (%.1f, %.1f)%n"
                                    + "getItemRectSize() = (%.1f, %.1f)",
                            returnValue,
                            IkGui.isItemFocused(),
                            IkGui.isItemHovered(),
                            IkGui.isItemHovered(HoveredFlags.ALLOW_WHEN_BLOCKED_BY_POPUP),
                            IkGui.isItemHovered(HoveredFlags.ALLOW_WHEN_BLOCKED_BY_ACTIVE_ITEM),
                            IkGui.isItemHovered(HoveredFlags.ALLOW_WHEN_OVERLAPPED_BY_ITEM),
                            IkGui.isItemHovered(HoveredFlags.ALLOW_WHEN_OVERLAPPED_BY_WINDOW),
                            IkGui.isItemHovered(HoveredFlags.ALLOW_WHEN_DISABLED),
                            IkGui.isItemHovered(HoveredFlags.RECT_ONLY),
                            IkGui.isItemActive(),
                            IkGui.isItemEdited(),
                            IkGui.isItemActivated(),
                            IkGui.isItemDeactivated(),
                            IkGui.isItemDeactivatedAfterEdit(),
                            IkGui.isItemVisible(),
                            IkGui.isItemClicked(),
                            IkGui.isItemToggledOpen(),
                            rectMin.x,
                            rectMin.y,
                            rectMax.x,
                            rectMax.y,
                            rectSize.x,
                            rectSize.y);
            IkGui.bulletText(status);
            IkGui.bulletText(
                    String.format(
                            "with Hovering Delay or Stationary test:%n"
                                    + "isItemHovered() = %b%n"
                                    + "isItemHovered(STATIONARY) = %b%n"
                                    + "isItemHovered(DELAY_SHORT) = %b%n"
                                    + "isItemHovered(DELAY_NORMAL) = %b%n"
                                    + "isItemHovered(FOR_TOOLTIP) = %b",
                            hoveredDelayNone,
                            hoveredDelayStationary,
                            hoveredDelayShort,
                            hoveredDelayNormal,
                            hoveredDelayTooltip));

            if (liveEditOverride) {
                IkGui.popItemFlag();
                IkGui.popItemFlag();
            }
            if (mixedValue) {
                IkGui.popItemFlag();
            }
            if (disabled) {
                IkGui.endDisabled();
            }

            IkGui.inputText("unused", queryUnused, InputTextFlags.READ_ONLY);
            IkGui.sameLine();
            helpMarker(
                    "This widget is only here to be able to tab-out of the widgets above and see"
                            + " e.g. Deactivated() status.");
            IkGui.treePop();
        }

        if (IkGui.treeNode("Querying Window Status (Focused/Hovered etc.)")) {
            IkGui.checkbox(
                    "Embed everything inside a child window for testing ROOT_WINDOW flag.",
                    embedAllInsideChild);
            if (embedAllInsideChild.get()) {
                IkGui.beginChild(
                        "outer_child", 0, IkGui.getTextLineHeight() * 20.0f, ChildFlags.BORDERS);
            }

            // Testing isWindowFocused() function with its various flags
            IkGui.bulletText(
                    String.format(
                            "isWindowFocused() = %b%n"
                                    + "isWindowFocused(CHILD_WINDOWS) = %b%n"
                                    + "isWindowFocused(CHILD_WINDOWS | NO_POPUP_HIERARCHY) = %b%n"
                                    + "isWindowFocused(CHILD_WINDOWS | ROOT_WINDOW) = %b%n"
                                    + "isWindowFocused(ROOT_WINDOW) = %b%n"
                                    + "isWindowFocused(ANY_WINDOW) = %b",
                            IkGui.isWindowFocused(),
                            IkGui.isWindowFocused(FocusedFlags.CHILD_WINDOWS),
                            IkGui.isWindowFocused(
                                    FocusedFlags.CHILD_WINDOWS | FocusedFlags.NO_POPUP_HIERARCHY),
                            IkGui.isWindowFocused(
                                    FocusedFlags.CHILD_WINDOWS | FocusedFlags.ROOT_WINDOW),
                            IkGui.isWindowFocused(FocusedFlags.ROOT_WINDOW),
                            IkGui.isWindowFocused(FocusedFlags.ANY_WINDOW)));

            // Testing isWindowHovered() function with its various flags
            IkGui.bulletText(
                    String.format(
                            "isWindowHovered() = %b%n"
                                    + "isWindowHovered(ALLOW_WHEN_BLOCKED_BY_POPUP) = %b%n"
                                    + "isWindowHovered(ALLOW_WHEN_BLOCKED_BY_ACTIVE_ITEM) = %b%n"
                                    + "isWindowHovered(CHILD_WINDOWS) = %b%n"
                                    + "isWindowHovered(CHILD_WINDOWS | ROOT_WINDOW) = %b%n"
                                    + "isWindowHovered(ROOT_WINDOW) = %b%n"
                                    + "isWindowHovered(ANY_WINDOW) = %b%n"
                                    + "isWindowHovered(STATIONARY) = %b",
                            IkGui.isWindowHovered(),
                            IkGui.isWindowHovered(HoveredFlags.ALLOW_WHEN_BLOCKED_BY_POPUP),
                            IkGui.isWindowHovered(HoveredFlags.ALLOW_WHEN_BLOCKED_BY_ACTIVE_ITEM),
                            IkGui.isWindowHovered(HoveredFlags.CHILD_WINDOWS),
                            IkGui.isWindowHovered(
                                    HoveredFlags.CHILD_WINDOWS | HoveredFlags.ROOT_WINDOW),
                            IkGui.isWindowHovered(HoveredFlags.ROOT_WINDOW),
                            IkGui.isWindowHovered(HoveredFlags.ANY_WINDOW),
                            IkGui.isWindowHovered(HoveredFlags.STATIONARY)));

            IkGui.beginChild("child", 0, 50, ChildFlags.BORDERS);
            IkGui.text("This is another child window for testing the CHILD_WINDOWS flag.");
            IkGui.endChild();
            if (embedAllInsideChild.get()) {
                IkGui.endChild();
            }
            IkGui.treePop();
        }
    }

    private static final IkInt tabsAdvancedFlags = new IkInt(TabBarFlags.REORDERABLE);
    private static final String[] TAB_NAMES = {"Artichoke", "Beetroot", "Celery", "Daikon"};
    private static final IkBoolean[] tabsOpened = {
        new IkBoolean(true), new IkBoolean(true), new IkBoolean(true), new IkBoolean(true)
    };
    private static final IkInt tabsButtonFlags =
            new IkInt(
                    TabBarFlags.AUTO_SELECT_NEW_TABS
                            | TabBarFlags.REORDERABLE
                            | TabBarFlags.FITTING_POLICY_MIXED);
    private static final List<Integer> activeTabs = new ArrayList<>(List.of(0, 1, 2));
    private static int nextTabID = 3;
    private static final IkBoolean showLeadingButton = new IkBoolean(true);
    private static final IkBoolean showTrailingButton = new IkBoolean(true);
    private static final IkBoolean showLeadingTrailingTabs = new IkBoolean(false);

    /**
     * Checkboxes to pick exactly one tab bar fitting policy.
     *
     * @param flags The tab bar flags to edit.
     */
    private static void editTabBarFittingPolicyFlags(IkInt flags) {
        if ((flags.get() & TabBarFlags.FITTING_POLICY_MASK) == 0) {
            flags.set(flags.get() | TabBarFlags.FITTING_POLICY_DEFAULT);
        }
        final int[] policies = {
            TabBarFlags.FITTING_POLICY_MIXED,
            TabBarFlags.FITTING_POLICY_SHRINK,
            TabBarFlags.FITTING_POLICY_SCROLL
        };
        final String[] names = {
            "FITTING_POLICY_MIXED", "FITTING_POLICY_SHRINK", "FITTING_POLICY_SCROLL"
        };
        for (int i = 0; i < policies.length; ++i) {
            if (IkGui.checkboxFlags(names[i], flags, policies[i])) {
                flags.set(flags.get() & ~(TabBarFlags.FITTING_POLICY_MASK ^ policies[i]));
            }
        }
    }

    private static void showWidgetsTabs() {
        if (!IkGui.treeNode("Tabs")) {
            return;
        }
        if (IkGui.treeNode("Basic")) {
            if (IkGui.beginTabBar("MyTabBar")) {
                if (IkGui.beginTabItem("Avocado")) {
                    IkGui.text("This is the Avocado tab!\nblah blah blah blah blah");
                    IkGui.endTabItem();
                }
                if (IkGui.beginTabItem("Broccoli")) {
                    IkGui.text("This is the Broccoli tab!\nblah blah blah blah blah");
                    IkGui.endTabItem();
                }
                if (IkGui.beginTabItem("Cucumber")) {
                    IkGui.text("This is the Cucumber tab!\nblah blah blah blah blah");
                    IkGui.endTabItem();
                }
                IkGui.endTabBar();
            }
            IkGui.separator();
            IkGui.treePop();
        }

        if (IkGui.treeNode("Advanced & Close Button")) {
            // Expose a couple of the available flags. In most cases you may just call
            // beginTabBar() with no flags.
            IkGui.checkboxFlags("REORDERABLE", tabsAdvancedFlags, TabBarFlags.REORDERABLE);
            IkGui.checkboxFlags(
                    "AUTO_SELECT_NEW_TABS", tabsAdvancedFlags, TabBarFlags.AUTO_SELECT_NEW_TABS);
            IkGui.checkboxFlags(
                    "TAB_LIST_POPUP_BUTTON", tabsAdvancedFlags, TabBarFlags.TAB_LIST_POPUP_BUTTON);
            IkGui.checkboxFlags(
                    "NO_CLOSE_WITH_MIDDLE_MOUSE_BUTTON",
                    tabsAdvancedFlags,
                    TabBarFlags.NO_CLOSE_WITH_MIDDLE_MOUSE_BUTTON);
            IkGui.checkboxFlags(
                    "DRAW_SELECTED_OVERLINE",
                    tabsAdvancedFlags,
                    TabBarFlags.DRAW_SELECTED_OVERLINE);
            editTabBarFittingPolicyFlags(tabsAdvancedFlags);

            IkGui.alignTextToFramePadding();
            IkGui.text("Opened:");
            for (int n = 0; n < TAB_NAMES.length; ++n) {
                IkGui.sameLine();
                IkGui.checkbox(TAB_NAMES[n], tabsOpened[n]);
            }

            // Passing an open flag to beginTabItem() is similar to passing one to begin(): the
            // value will be set to false when the tab is closed.
            if (IkGui.beginTabBar("MyTabBar", tabsAdvancedFlags.get())) {
                for (int n = 0; n < TAB_NAMES.length; ++n) {
                    if (tabsOpened[n].get()
                            && IkGui.beginTabItem(TAB_NAMES[n], tabsOpened[n], TabItemFlags.NONE)) {
                        IkGui.text("This is the " + TAB_NAMES[n] + " tab!");
                        if ((n & 1) != 0) {
                            IkGui.text("I am an odd tab.");
                        }
                        IkGui.endTabItem();
                    }
                }
                IkGui.endTabBar();
            }
            IkGui.separator();
            IkGui.treePop();
        }

        if (IkGui.treeNode("TabItemButton & Leading/Trailing flags")) {
            IkGui.checkbox("Show Leading tabItemButton()", showLeadingButton);
            IkGui.checkbox("Show Trailing tabItemButton()", showTrailingButton);
            IkGui.checkbox("Show Leading+Trailing tabItem()", showLeadingTrailingTabs);
            editTabBarFittingPolicyFlags(tabsButtonFlags);

            if (IkGui.beginTabBar("MyTabBar", tabsButtonFlags.get())) {
                // A leading button: click the "?" button to open a menu
                if (showLeadingButton.get()
                        && IkGui.tabItemButton(
                                "?", TabItemFlags.LEADING | TabItemFlags.NO_TOOLTIP)) {
                    IkGui.openPopup("MyHelpMenu");
                }
                if (IkGui.beginPopup("MyHelpMenu")) {
                    IkGui.selectable("Hello!");
                    IkGui.endPopup();
                }

                // Leading/trailing tabs
                if (showLeadingTrailingTabs.get()) {
                    if (IkGui.beginTabItem("Leading", TabItemFlags.LEADING)) {
                        IkGui.endTabItem();
                    }
                    if (IkGui.beginTabItem("Trailing", TabItemFlags.TRAILING)) {
                        IkGui.endTabItem();
                    }
                }

                // A trailing button: click the "+" button to add a new tab. We submit it before
                // the regular tabs, but thanks to the TRAILING flag it will always appear at the
                // end.
                if (showTrailingButton.get()
                        && IkGui.tabItemButton(
                                "+", TabItemFlags.TRAILING | TabItemFlags.NO_TOOLTIP)) {
                    activeTabs.add(nextTabID++);
                }

                // Submit our regular tabs
                for (int n = 0; n < activeTabs.size(); ) {
                    final IkBoolean open = new IkBoolean(true);
                    final String name = String.format("%04d", activeTabs.get(n));
                    if (IkGui.beginTabItem(name, open, TabItemFlags.NONE)) {
                        IkGui.text("This is the " + name + " tab!");
                        IkGui.endTabItem();
                    }
                    if (!open.get()) {
                        activeTabs.remove(n);
                    } else {
                        n++;
                    }
                }
                IkGui.endTabBar();
            }
            IkGui.separator();
            IkGui.treePop();
        }
        IkGui.treePop();
    }

    private static final IkInt comboCurrent = new IkInt(0);
    private static final IkInt comboFlags = new IkInt(ComboFlags.NONE);
    private static final TextFilter comboFilter = new TextFilter();
    private static final IkInt comboCurrent2 = new IkInt(0);
    private static final IkInt comboCurrent3 = new IkInt(-1);
    private static final IkInt comboCurrent4 = new IkInt(0);
    private static final String[] COMBO_ONE_LINER_ITEMS = {"aaaa", "bbbb", "cccc", "dddd", "eeee"};
    private static final String[] COMBO_ITEMS = {
        "AAAA", "BBBB", "CCCC", "DDDD", "EEEE", "FFFF", "GGGG", "HHHH", "IIII", "JJJJ", "KKKK",
        "LLLLLLL", "MMMM", "OOOOOOO"
    };

    private static void showWidgetsCombo() {
        if (!IkGui.treeNode("Combo")) {
            return;
        }
        // Combo Boxes are also called "Dropdown" in other systems. Expose flags as checkbox for
        // the demo.
        IkGui.checkboxFlags("POPUP_ALIGN_LEFT", comboFlags, ComboFlags.POPUP_ALIGN_LEFT);
        IkGui.sameLine();
        helpMarker("Only makes a difference if the popup is larger than the combo");
        if (IkGui.checkboxFlags("NO_ARROW_BUTTON", comboFlags, ComboFlags.NO_ARROW_BUTTON)) {
            // Clear incompatible flags
            comboFlags.set(comboFlags.get() & ~ComboFlags.NO_PREVIEW);
        }
        if (IkGui.checkboxFlags("NO_PREVIEW", comboFlags, ComboFlags.NO_PREVIEW)) {
            // Clear incompatible flags
            comboFlags.set(
                    comboFlags.get()
                            & ~(ComboFlags.NO_ARROW_BUTTON | ComboFlags.WIDTH_FIT_PREVIEW));
        }
        if (IkGui.checkboxFlags("WIDTH_FIT_PREVIEW", comboFlags, ComboFlags.WIDTH_FIT_PREVIEW)) {
            comboFlags.set(comboFlags.get() & ~ComboFlags.NO_PREVIEW);
        }

        // Override default popup height
        if (IkGui.checkboxFlags("HEIGHT_SMALL", comboFlags, ComboFlags.HEIGHT_SMALL)) {
            comboFlags.set(comboFlags.get() & ~(ComboFlags.HEIGHT_MASK & ~ComboFlags.HEIGHT_SMALL));
        }
        if (IkGui.checkboxFlags("HEIGHT_REGULAR", comboFlags, ComboFlags.HEIGHT_REGULAR)) {
            comboFlags.set(
                    comboFlags.get() & ~(ComboFlags.HEIGHT_MASK & ~ComboFlags.HEIGHT_REGULAR));
        }
        if (IkGui.checkboxFlags("HEIGHT_LARGEST", comboFlags, ComboFlags.HEIGHT_LARGEST)) {
            comboFlags.set(
                    comboFlags.get() & ~(ComboFlags.HEIGHT_MASK & ~ComboFlags.HEIGHT_LARGEST));
        }

        // Using the generic beginCombo() API, you have full control over how to display the combo
        // contents. (your selection data could be an index, a reference to the object, an id for
        // the object, a flag intrusively stored in the object itself, etc.)

        // Pass in the preview value visible before opening the combo (it could technically be
        // different contents or not pulled from the items)
        final String previewValue = COMBO_ITEMS[comboCurrent.get()];
        if (IkGui.beginCombo("combo 1", previewValue, comboFlags.get())) {
            for (int n = 0; n < COMBO_ITEMS.length; ++n) {
                final boolean isSelected = comboCurrent.get() == n;
                if (IkGui.selectable(COMBO_ITEMS[n], isSelected)) {
                    comboCurrent.set(n);
                }

                // Set the initial focus when opening the combo (scrolling + keyboard navigation
                // focus)
                if (isSelected) {
                    IkGui.setItemDefaultFocus();
                }
            }
            IkGui.endCombo();
        }

        // Show case embedding a filter using a simple trick: displaying the filter inside combo
        // contents. See https://github.com/ocornut/imgui/issues/718 for advanced/esoteric
        // alternatives.
        if (IkGui.beginCombo("combo 2 (w/ filter)", previewValue, comboFlags.get())) {
            if (IkGui.isWindowAppearing()) {
                IkGui.setKeyboardFocusHere();
                comboFilter.clear();
            }
            IkGui.setNextItemShortcut(KeyChord.of(KeyModFlags.CTRL, Key.F));
            IkGui.setNextItemWidth(-Float.MIN_VALUE);
            comboFilter.drawWithHint("##Filter", "Filter (incl -excl)");

            for (int n = 0; n < COMBO_ITEMS.length; ++n) {
                final boolean isSelected = comboCurrent.get() == n;
                if (comboFilter.passFilter(COMBO_ITEMS[n])
                        && IkGui.selectable(COMBO_ITEMS[n], isSelected)) {
                    comboCurrent.set(n);
                }
            }
            IkGui.endCombo();
        }

        IkGui.spacing();
        IkGui.separatorText("One-liner variants");
        helpMarker(
                "The combo() function is not greatly useful apart from cases were you want to embed"
                        + " all options in a single array.\n"
                        + "Flags above don't apply to this section.");

        // Simplified one-liner combo() API, using a small array of values. This is a convenience
        // for when the selection set is small and known at compile-time.
        IkGui.combo("combo 3 (one-liner)", comboCurrent2, COMBO_ONE_LINER_ITEMS);

        // Simplified one-liner combo() using the items array. If the selection isn't within
        // 0..count, combo won't display a preview.
        IkGui.combo("combo 4 (array)", comboCurrent3, COMBO_ITEMS);

        // Simplified one-liner combo() using an accessor function
        IkGui.combo("combo 5 (function)", comboCurrent4, n -> COMBO_ITEMS[n], COMBO_ITEMS.length);

        IkGui.treePop();
    }

    // ---------------------------------------------------------------------------------------------
    // Selection State & Multi-Select
    // ---------------------------------------------------------------------------------------------

    private static final String[] EXAMPLE_NAMES = {
        "Artichoke",
        "Arugula",
        "Asparagus",
        "Avocado",
        "Bamboo Shoots",
        "Bean Sprouts",
        "Beans",
        "Beet",
        "Belgian Endive",
        "Bell Pepper",
        "Bitter Gourd",
        "Bok Choy",
        "Broccoli",
        "Brussels Sprouts",
        "Burdock Root",
        "Cabbage",
        "Calabash",
        "Capers",
        "Carrot",
        "Cassava",
        "Cauliflower",
        "Celery",
        "Celery Root",
        "Celcuce",
        "Chayote",
        "Chinese Broccoli",
        "Corn",
        "Cucumber"
    };

    /** The label of a demo object, like "Object 00012: Bok Choy". */
    private static String exampleObjectLabel(int id) {
        return String.format("Object %05d: %s", id, EXAMPLE_NAMES[id % EXAMPLE_NAMES.length]);
    }

    /** Extra functions to add deletion support to SelectionBasicStorage. */
    static class ExampleSelectionWithDeletion extends SelectionBasicStorage {
        /**
         * Find which item should be focused after deletion. Call this before submitting items, and
         * call setKeyboardFocusHere() on the returned index (in the list before deletion). The
         * applyDeletionPostLoop() code will use it to apply the selection.
         *
         * <p>Deletion only works if the IDs of your items are stable, so they don't depend on their
         * index, but on e.g. an item ID.
         *
         * @param io The multi-select IO.
         * @param itemsCount The number of items before deletion.
         * @return The index of the item to focus, or -1.
         */
        int applyDeletionPreLoop(MultiSelectIO io, int itemsCount) {
            if (getSize() == 0) {
                return -1;
            }

            // If the focused item is not selected...
            final int focusedIndex = (int) io.navIDItem;
            if (!io.navIDSelected) {
                // Request to recover the range source from the nav ID next frame. It would be ok
                // to reset even when the focused item is selected, but it would take an extra
                // frame to recover the range source when deleting a selected item.
                io.rangeSourceReset = true;
                // Request to focus the same item after deletion
                return focusedIndex;
            }

            // If the focused item is selected: land on the first unselected item after it
            for (int index = focusedIndex + 1; index < itemsCount; ++index) {
                if (!contains(getStorageIDFromIndex(index))) {
                    return index;
                }
            }

            // Otherwise land on the last unselected item before it
            for (int index = Math.min(focusedIndex, itemsCount) - 1; index >= 0; --index) {
                if (!contains(getStorageIDFromIndex(index))) {
                    return index;
                }
            }
            return -1;
        }

        /**
         * Delete the selected items and update the selection. Call this after endMultiSelect().
         *
         * @param io The multi-select IO.
         * @param items The items, which selected items are removed from.
         * @param itemCurrentIndexToSelect The index returned by applyDeletionPreLoop().
         */
        <T> void applyDeletionPostLoop(
                MultiSelectIO io, List<T> items, int itemCurrentIndexToSelect) {
            // Rewrite the item list, and convert the old selection index (before deletion) to the
            // new selection index (after deletion). If the focused item was not part of the
            // selection, we stay on the same item.
            final List<T> newItems = new ArrayList<>(items.size());
            int itemNextIndexToSelect = -1;
            for (int index = 0; index < items.size(); ++index) {
                if (!contains(getStorageIDFromIndex(index))) {
                    newItems.add(items.get(index));
                }
                if (itemCurrentIndexToSelect == index) {
                    itemNextIndexToSelect = newItems.size() - 1;
                }
            }
            items.clear();
            items.addAll(newItems);

            // Update the selection
            clear();
            if (itemNextIndexToSelect != -1 && io.navIDSelected) {
                setItemSelected(getStorageIDFromIndex(itemNextIndexToSelect), true);
            }
        }
    }

    /** A dual list box, moving items between two lists. */
    private static class ExampleDualListBox {
        /** The item IDs on each side, which are indices into EXAMPLE_NAMES. */
        private final List<List<Integer>> items = List.of(new ArrayList<>(), new ArrayList<>());

        /** The selections on each side, which store item IDs. */
        private final SelectionBasicStorage[] selections = {
            new SelectionBasicStorage(), new SelectionBasicStorage()
        };

        private void moveAll(int source, int destination) {
            items.get(destination).addAll(items.get(source));
            items.get(source).clear();
            sortItems(destination);
            selections[source].swap(selections[destination]);
            selections[source].clear();
        }

        private void moveSelected(int source, int destination) {
            final List<Integer> sourceItems = items.get(source);
            for (int i = 0; i < sourceItems.size(); ++i) {
                final int itemID = sourceItems.get(i);
                if (!selections[source].contains(itemID)) {
                    continue;
                }
                sourceItems.remove(i--);
                items.get(destination).add(itemID);
            }
            sortItems(destination);
            selections[source].swap(selections[destination]);
            selections[source].clear();
        }

        private void applySelectionRequests(MultiSelectIO io, int side) {
            // In this example we store the item ID in the selection, instead of the index
            final List<Integer> sideItems = items.get(side);
            selections[side].adapterIndexToStorageID = sideItems::get;
            selections[side].applyRequests(io);
        }

        private void sortItems(int side) {
            items.get(side).sort(null);
        }

        void show() {
            if (!IkGui.beginTable("split", 3, TableFlags.NONE)) {
                return;
            }
            IkGui.tableSetupColumn("", TableColumnFlags.WIDTH_STRETCH); // Left side
            IkGui.tableSetupColumn("", TableColumnFlags.WIDTH_FIXED); // Buttons
            IkGui.tableSetupColumn("", TableColumnFlags.WIDTH_STRETCH); // Right side
            IkGui.tableNextRow();

            int requestMoveSelected = -1;
            int requestMoveAll = -1;
            float childHeight0 = 0.0f;
            for (int side = 0; side < 2; ++side) {
                final List<Integer> sideItems = items.get(side);
                final SelectionBasicStorage selection = selections[side];

                IkGui.tableSetColumnIndex(side == 0 ? 0 : 2);
                IkGui.text(
                        String.format(
                                "%s (%d)", side == 0 ? "Available" : "Basket", sideItems.size()));

                // Submit the scrolling range to avoid glitches on moving/deletion
                final float itemsHeight = IkGui.getTextLineHeightWithSpacing();
                IkGui.setNextWindowContentSize(0.0f, sideItems.size() * itemsHeight);

                final boolean childVisible;
                if (side == 0) {
                    // The left child is resizable
                    IkGui.setNextWindowSizeConstraints(
                            0.0f,
                            IkGui.getFrameHeightWithSpacing() * 4,
                            Float.MAX_VALUE,
                            Float.MAX_VALUE);
                    childVisible =
                            IkGui.beginChild(
                                    "0",
                                    -Float.MIN_VALUE,
                                    IkGui.getFontSize() * 20,
                                    ChildFlags.FRAME_STYLE | ChildFlags.RESIZE_Y);
                    childHeight0 = IkGui.getWindowSize().y;
                } else {
                    // The right child uses the same height as the left one
                    childVisible =
                            IkGui.beginChild(
                                    "1", -Float.MIN_VALUE, childHeight0, ChildFlags.FRAME_STYLE);
                }
                if (childVisible) {
                    MultiSelectIO io =
                            IkGui.beginMultiSelect(
                                    MultiSelectFlags.BOX_SELECT_1D,
                                    selection.getSize(),
                                    sideItems.size());
                    applySelectionRequests(io, side);

                    for (int n = 0; n < sideItems.size(); ++n) {
                        final int itemID = sideItems.get(n);
                        IkGui.setNextItemSelectionUserData(n);
                        IkGui.selectable(
                                EXAMPLE_NAMES[itemID],
                                selection.contains(itemID),
                                SelectableFlags.ALLOW_DOUBLE_CLICK);
                        if (IkGui.isItemFocused()) {
                            if (IkGui.isKeyPressed(Key.ENTER)
                                    || IkGui.isMouseDoubleClicked(MouseButton.LEFT)) {
                                requestMoveSelected = side;
                            }
                        }
                    }

                    io = IkGui.endMultiSelect();
                    applySelectionRequests(io, side);
                }
                IkGui.endChild();
            }

            // Buttons column
            IkGui.tableSetColumnIndex(1);
            IkGui.newLine();
            final float buttonSize = IkGui.getFrameHeight();
            if (IkGui.button(">>", buttonSize, buttonSize)) {
                requestMoveAll = 0;
            }
            if (IkGui.button(">", buttonSize, buttonSize)) {
                requestMoveSelected = 0;
            }
            if (IkGui.button("<", buttonSize, buttonSize)) {
                requestMoveSelected = 1;
            }
            if (IkGui.button("<<", buttonSize, buttonSize)) {
                requestMoveAll = 1;
            }

            // Process requests
            if (requestMoveAll != -1) {
                moveAll(requestMoveAll, requestMoveAll ^ 1);
            }
            if (requestMoveSelected != -1) {
                moveSelected(requestMoveSelected, requestMoveSelected ^ 1);
            }
            IkGui.endTable();
        }
    }

    /** A simple tree, for the multi-select tree demo and the property editor example. */
    static class ExampleTreeNode {
        /** The name, which can be edited in the property editor. */
        final IkString name;

        final int uid;
        final ExampleTreeNode parent;
        final List<ExampleTreeNode> children = new ArrayList<>();

        /** Maintaining this allows us to implement linear traversal more easily. */
        final int indexInParent;

        // Leaf data. All leaves have data.
        boolean hasData = false;
        final IkBoolean dataMyBool = new IkBoolean(true);
        final int[] dataMyInt = {128};
        final float[] dataMyVec2 = {0.0f, 3.141_592f};

        ExampleTreeNode(String name, int uid, ExampleTreeNode parent) {
            this.name = new IkString(name, 28);
            this.uid = uid;
            this.parent = parent;
            indexInParent = parent != null ? parent.children.size() : 0;
            if (parent != null) {
                parent.children.add(this);
            }
        }
    }

    /** The nodes of the demo tree, indexed by UID, so selection user data can be a UID. */
    private static final List<ExampleTreeNode> exampleTreeNodes = new ArrayList<>();

    private static ExampleTreeNode exampleTree;

    /**
     * Fetch the demo tree, creating it the first time.
     *
     * @return The root node.
     */
    static ExampleTreeNode getDemoTree() {
        if (exampleTree == null) {
            exampleTree = exampleTreeCreateDemoTree();
        }
        return exampleTree;
    }

    private static ExampleTreeNode exampleTreeCreateNode(
            String name, int uid, ExampleTreeNode parent) {
        final ExampleTreeNode node = new ExampleTreeNode(name, uid, parent);
        while (exampleTreeNodes.size() <= uid) {
            exampleTreeNodes.add(null);
        }
        exampleTreeNodes.set(uid, node);
        return node;
    }

    private static ExampleTreeNode exampleTreeCreateDemoTree() {
        final int rootItemsCount = 20;
        final String[] categoryNames = {
            "Apple",
            "Banana",
            "Cherry",
            "Kiwi",
            "Mango",
            "Orange",
            "Pear",
            "Pineapple",
            "Strawberry",
            "Watermelon"
        };
        final int perCategory = rootItemsCount / categoryNames.length;
        int uid = 0;
        final ExampleTreeNode root = exampleTreeCreateNode("<ROOT>", ++uid, null);
        for (int indexL0 = 0; indexL0 < rootItemsCount; ++indexL0) {
            final String name =
                    categoryNames[indexL0 / perCategory] + " " + (indexL0 % perCategory);
            final ExampleTreeNode nodeL1 = exampleTreeCreateNode(name, ++uid, root);
            final int numberOfChildren = nodeL1.name.getLength();
            for (int indexL1 = 0; indexL1 < numberOfChildren; ++indexL1) {
                final ExampleTreeNode nodeL2 =
                        exampleTreeCreateNode("Child " + indexL1, ++uid, nodeL1);
                nodeL2.hasData = true;
                if (indexL1 == 0) {
                    final ExampleTreeNode nodeL3 =
                            exampleTreeCreateNode("Sub-child 0", ++uid, nodeL2);
                    nodeL3.hasData = true;
                }
            }
        }
        return root;
    }

    private static void exampleTreeDrawNode(ExampleTreeNode node, SelectionBasicStorage selection) {
        int treeNodeFlags =
                TreeNodeFlags.SPAN_AVAIL_WIDTH
                        | TreeNodeFlags.OPEN_ON_ARROW
                        | TreeNodeFlags.OPEN_ON_DOUBLE_CLICK;
        // Enable pressing left to jump to the parent
        treeNodeFlags |= TreeNodeFlags.NAV_LEFT_JUMPS_TO_PARENT;
        if (node.children.isEmpty()) {
            treeNodeFlags |= TreeNodeFlags.BULLET | TreeNodeFlags.LEAF;
        }
        if (selection.contains(node.uid)) {
            treeNodeFlags |= TreeNodeFlags.SELECTED;
        }

        // We use the UID as selection user data instead of an index, which shows that
        // setNextItemSelectionUserData() never assumes indices. We use setNextItemStorageID() so
        // we can easily peek into the open state with treeNodeGetOpen()/treeNodeSetOpen().
        IkGui.setNextItemSelectionUserData(node.uid);
        IkGui.setNextItemStorageID(node.uid);
        if (IkGui.treeNodeEx(node.name.get(), treeNodeFlags)) {
            for (ExampleTreeNode child : node.children) {
                exampleTreeDrawNode(child, selection);
            }
            IkGui.treePop();
        } else if (IkGui.isItemToggledOpen()) {
            exampleTreeCloseAndUnselectChildNodes(node, selection, 0);
        }
    }

    /**
     * When closing a node, close and unselect all child nodes, and select the parent if any child
     * was selected.
     */
    private static int exampleTreeCloseAndUnselectChildNodes(
            ExampleTreeNode node, SelectionBasicStorage selection, int depth) {
        // Recursive close (the test for depth 0 is because we call this on a node that was just
        // closed)
        int unselectedCount = selection.contains(node.uid) ? 1 : 0;
        if (depth == 0 || IkGuiInternal.treeNodeGetOpen(node.uid)) {
            for (ExampleTreeNode child : node.children) {
                unselectedCount +=
                        exampleTreeCloseAndUnselectChildNodes(child, selection, depth + 1);
            }
            IkGuiInternal.treeNodeSetOpen(node.uid, false);
        }

        // Select the root node if any of its children were selected, otherwise unselect it
        selection.setItemSelected(node.uid, depth == 0 && unselectedCount > 0);
        return unselectedCount;
    }

    private static void exampleTreeApplySelectionRequests(
            MultiSelectIO io, ExampleTreeNode tree, SelectionBasicStorage selection) {
        for (SelectionRequest request : io.requests) {
            if (request.type() == SelectionRequestType.SET_ALL) {
                if (request.selected()) {
                    exampleTreeSetAllInOpenNodes(tree, selection, true);
                } else {
                    selection.clear();
                }
            } else if (request.type() == SelectionRequestType.SET_RANGE) {
                final ExampleTreeNode firstNode =
                        exampleTreeNodes.get((int) request.rangeFirstItem());
                final ExampleTreeNode lastNode =
                        exampleTreeNodes.get((int) request.rangeLastItem());
                for (ExampleTreeNode node = firstNode;
                        node != null;
                        node = exampleTreeGetNextNodeInVisibleOrder(node, lastNode)) {
                    selection.setItemSelected(node.uid, request.selected());
                }
            }
        }
    }

    private static void exampleTreeSetAllInOpenNodes(
            ExampleTreeNode node, SelectionBasicStorage selection, boolean selected) {
        // The root node isn't visible nor selectable in our scheme
        if (node.parent != null) {
            selection.setItemSelected(node.uid, selected);
        }
        if (node.parent == null || IkGuiInternal.treeNodeGetOpen(node.uid)) {
            for (ExampleTreeNode child : node.children) {
                exampleTreeSetAllInOpenNodes(child, selection, selected);
            }
        }
    }

    /**
     * Interpolate in user-visible order, and only over open nodes. With a sequential mapping table
     * (e.g. generated after a filter/search pass) this would be simpler.
     */
    private static ExampleTreeNode exampleTreeGetNextNodeInVisibleOrder(
            ExampleTreeNode currentNode, ExampleTreeNode lastNode) {
        // Reached the last node
        if (currentNode == lastNode) {
            return null;
        }

        // Recurse into children, querying storage to tell if the node is open
        if (!currentNode.children.isEmpty() && IkGuiInternal.treeNodeGetOpen(currentNode.uid)) {
            return currentNode.children.getFirst();
        }

        // Next sibling, then into our own parent
        while (currentNode.parent != null) {
            if (currentNode.indexInParent + 1 < currentNode.parent.children.size()) {
                return currentNode.parent.children.get(currentNode.indexInParent + 1);
            }
            currentNode = currentNode.parent;
        }
        return null;
    }

    private static final IkInt singleSelectSelected = new IkInt(-1);
    private static final boolean[] manualMultiSelection = new boolean[5];
    private static final SelectionBasicStorage multiSelectBasic = new SelectionBasicStorage();
    private static final SelectionBasicStorage multiSelectClipper = new SelectionBasicStorage();
    private static final List<Integer> multiSelectDeletionItems = new ArrayList<>();
    private static final ExampleSelectionWithDeletion multiSelectDeletion =
            new ExampleSelectionWithDeletion();
    private static int multiSelectDeletionNextID = 0;
    private static final ExampleDualListBox multiSelectDualListBox = new ExampleDualListBox();
    private static final SelectionBasicStorage multiSelectTable = new SelectionBasicStorage();
    private static final IkBoolean[] multiSelectCheckboxes = new IkBoolean[20];
    private static final IkInt multiSelectCheckboxFlags =
            new IkInt(
                    MultiSelectFlags.NO_AUTO_SELECT
                            | MultiSelectFlags.NO_AUTO_CLEAR
                            | MultiSelectFlags.CLEAR_ON_ESCAPE);
    private static final SelectionExternalStorage multiSelectCheckboxStorage =
            new SelectionExternalStorage(
                    (index, selected) -> multiSelectCheckboxes[index].set(selected));
    private static final SelectionBasicStorage[] multiSelectScopes = {
        new SelectionBasicStorage(), new SelectionBasicStorage(), new SelectionBasicStorage()
    };
    private static final IkInt multiSelectScopeFlags =
            new IkInt(MultiSelectFlags.SCOPE_RECT | MultiSelectFlags.CLEAR_ON_ESCAPE);
    private static final SelectionBasicStorage multiSelectTreeSelection =
            new SelectionBasicStorage();

    static {
        for (int i = 0; i < multiSelectCheckboxes.length; ++i) {
            multiSelectCheckboxes[i] = new IkBoolean(false);
        }
    }

    private static void showWidgetsSelectionAndMultiSelect() {
        if (!IkGui.treeNode("Selection State & Multi-Select")) {
            return;
        }
        helpMarker(
                "Selections can be built using selectable(), treeNode() or other widgets."
                        + " Selection state is owned by application code/data.");
        IkGui.bulletText("Wiki page:");
        IkGui.sameLine();
        IkGui.textLinkOpenURL(
                "imgui/wiki/Multi-Select", "https://github.com/ocornut/imgui/wiki/Multi-Select");

        // Without any fancy API: manage single-selection yourself
        if (IkGui.treeNode("Single-Select")) {
            for (int n = 0; n < 5; ++n) {
                if (IkGui.selectable("Object " + n, singleSelectSelected.get() == n)) {
                    singleSelectSelected.set(n);
                }
            }
            IkGui.treePop();
        }

        if (IkGui.treeNode("Click again to rename")) {
            showWidgetsClickToRename();
            IkGui.treePop();
        }

        // The most basic form of multi-selection, done manually. This doesn't support the Shift
        // modifier, which requires beginMultiSelect().
        if (IkGui.treeNode("Multi-Select (manual/simplified, without beginMultiSelect)")) {
            helpMarker("Hold Ctrl and Click to select multiple items.");
            for (int n = 0; n < 5; ++n) {
                if (IkGui.selectable("Object " + n, manualMultiSelection[n])) {
                    // Clear the selection when Ctrl is not held
                    if (!IkGui.getIO().keyCtrl) {
                        Arrays.fill(manualMultiSelection, false);
                    }
                    manualMultiSelection[n] ^= true;
                }
            }
            IkGui.treePop();
        }

        showMultiSelectBasic();
        showMultiSelectWithClipper();
        showMultiSelectWithDeletion();

        // A dual list box
        if (IkGui.treeNode("Multi-Select (dual list box)")) {
            final ExampleDualListBox listBox = multiSelectDualListBox;
            if (listBox.items.get(0).isEmpty() && listBox.items.get(1).isEmpty()) {
                for (int itemID = 0; itemID < EXAMPLE_NAMES.length; ++itemID) {
                    listBox.items.get(0).add(itemID);
                }
            }
            listBox.show();
            IkGui.treePop();
        }

        showMultiSelectInTable();
        showMultiSelectCheckboxes();
        showMultiSelectMultipleScopes();

        if (IkGui.treeNode("Multi-Select (tiled assets browser)")) {
            IkGui.checkbox("Assets Browser", IkGuiDemoExamples.showAppAssetsBrowser);
            IkGui.text("(also access from 'Examples->Assets Browser' in menu)");
            IkGui.treePop();
        }

        showMultiSelectTrees();
        showMultiSelectAdvanced();
        IkGui.treePop();
    }

    /** Proper multi-selection using beginMultiSelect()/endMultiSelect(). */
    private static void showMultiSelectBasic() {
        if (!IkGui.treeNode("Multi-Select")) {
            return;
        }
        IkGui.text("Supported features:");
        IkGui.bulletText("Keyboard navigation (arrows, page up/down, home/end, space).");
        IkGui.bulletText("Ctrl modifier to preserve and toggle selection.");
        IkGui.bulletText("Shift modifier for range selection.");
        IkGui.bulletText("Ctrl+A to select all.");
        IkGui.bulletText("Escape to clear selection.");
        IkGui.bulletText("Click and drag to box-select.");
        IkGui.text(
                "Tip: Use 'Demo->Tools->Debug Log->Selection' to see selection requests as they"
                        + " happen.");

        // Use the default adapter: pass the index to setNextItemSelectionUserData(), and store
        // the index in the selection
        final int itemsCount = 50;
        final SelectionBasicStorage selection = multiSelectBasic;
        IkGui.text(String.format("Selection: %d/%d", selection.getSize(), itemsCount));

        // The child window has no purpose for selection logic, other than a scrolling region
        if (IkGui.beginChild(
                "##Basket",
                -Float.MIN_VALUE,
                IkGui.getFontSize() * 20,
                ChildFlags.FRAME_STYLE | ChildFlags.RESIZE_Y)) {
            final int flags = MultiSelectFlags.CLEAR_ON_ESCAPE | MultiSelectFlags.BOX_SELECT_1D;
            MultiSelectIO io = IkGui.beginMultiSelect(flags, selection.getSize(), itemsCount);
            selection.applyRequests(io);
            for (int n = 0; n < itemsCount; ++n) {
                IkGui.setNextItemSelectionUserData(n);
                IkGui.selectable(exampleObjectLabel(n), selection.contains(n));
            }
            io = IkGui.endMultiSelect();
            selection.applyRequests(io);
        }
        IkGui.endChild();
        IkGui.treePop();
    }

    /** Using the clipper with beginMultiSelect()/endMultiSelect(). */
    private static void showMultiSelectWithClipper() {
        if (!IkGui.treeNode("Multi-Select (with clipper)")) {
            return;
        }
        final SelectionBasicStorage selection = multiSelectClipper;
        IkGui.text("Added features:");
        IkGui.bulletText("Using ListClipper.");

        final int itemsCount = 10_000;
        IkGui.text(String.format("Selection: %d/%d", selection.getSize(), itemsCount));
        if (IkGui.beginChild(
                "##Basket",
                -Float.MIN_VALUE,
                IkGui.getFontSize() * 20,
                ChildFlags.FRAME_STYLE | ChildFlags.RESIZE_Y)) {
            final int flags = MultiSelectFlags.CLEAR_ON_ESCAPE | MultiSelectFlags.BOX_SELECT_1D;
            MultiSelectIO io = IkGui.beginMultiSelect(flags, selection.getSize(), itemsCount);
            selection.applyRequests(io);

            final ListClipper clipper = new ListClipper();
            clipper.begin(itemsCount);
            if (io.rangeSourceItem != SelectionUserData.INVALID) {
                // Ensure the range source item is not clipped
                clipper.includeItemByIndex((int) io.rangeSourceItem);
            }
            while (clipper.step()) {
                for (int n = clipper.displayStart; n < clipper.displayEnd; ++n) {
                    IkGui.setNextItemSelectionUserData(n);
                    IkGui.selectable(exampleObjectLabel(n), selection.contains(n));
                }
            }

            io = IkGui.endMultiSelect();
            selection.applyRequests(io);
        }
        IkGui.endChild();
        IkGui.treePop();
    }

    /**
     * A dynamic item list with deletion support. To support deletion without any glitches you need
     * to:
     *
     * <ol>
     *   <li>If items are submitted in their own scrolling area, submit the contents size with
     *       setNextWindowContentSize() ahead of time to prevent a one frame scrolling readjustment.
     *   <li>Items need a persistent ID that doesn't depend on their index, so pushID(itemID)
     *       instead of pushID(index). This is so items can be focused reliably after deletion.
     *   <li>Apply the begin requests.
     *   <li>Focus the item returned by applyDeletionPreLoop().
     *   <li>Apply the end requests, then applyDeletionPostLoop().
     * </ol>
     */
    private static void showMultiSelectWithDeletion() {
        if (!IkGui.treeNode("Multi-Select (with deletion)")) {
            return;
        }
        // Store the items separately from the selection data, and use a custom adapter to store
        // item IDs in the selection (instead of indices)
        final List<Integer> items = multiSelectDeletionItems;
        final ExampleSelectionWithDeletion selection = multiSelectDeletion;
        selection.adapterIndexToStorageID = items::get;

        IkGui.text("Added features:");
        IkGui.bulletText("Dynamic list with Delete key support.");
        IkGui.text(String.format("Selection size: %d/%d", selection.getSize(), items.size()));

        // Initialize the default list with 50 items, and buttons to add/remove items
        if (multiSelectDeletionNextID == 0) {
            for (int n = 0; n < 50; ++n) {
                items.add(multiSelectDeletionNextID++);
            }
        }
        if (IkGui.smallButton("Add 20 items")) {
            for (int n = 0; n < 20; ++n) {
                items.add(multiSelectDeletionNextID++);
            }
        }
        IkGui.sameLine();
        if (IkGui.smallButton("Remove 20 items")) {
            for (int n = Math.min(20, items.size()); n > 0; --n) {
                selection.setItemSelected(items.getLast(), false);
                items.removeLast();
            }
        }

        // (1) Submit the scrolling range to avoid glitches on deletion
        final float itemsHeight = IkGui.getTextLineHeightWithSpacing();
        IkGui.setNextWindowContentSize(0.0f, items.size() * itemsHeight);

        if (IkGui.beginChild(
                "##Basket",
                -Float.MIN_VALUE,
                IkGui.getFontSize() * 20,
                ChildFlags.FRAME_STYLE | ChildFlags.RESIZE_Y)) {
            final int flags = MultiSelectFlags.CLEAR_ON_ESCAPE | MultiSelectFlags.BOX_SELECT_1D;
            MultiSelectIO io = IkGui.beginMultiSelect(flags, selection.getSize(), items.size());
            selection.applyRequests(io);

            final boolean wantDelete =
                    IkGui.shortcut(KeyChord.of(Key.DELETE), InputFlags.REPEAT)
                            && selection.getSize() > 0;
            final int itemCurrentIndexToFocus =
                    wantDelete ? selection.applyDeletionPreLoop(io, items.size()) : -1;

            for (int n = 0; n < items.size(); ++n) {
                final int itemID = items.get(n);
                IkGui.setNextItemSelectionUserData(n);
                IkGui.selectable(exampleObjectLabel(itemID), selection.contains(itemID));
                if (itemCurrentIndexToFocus == n) {
                    IkGui.setKeyboardFocusHere(-1);
                }
            }

            // Apply the multi-select requests
            io = IkGui.endMultiSelect();
            selection.applyRequests(io);
            if (wantDelete) {
                selection.applyDeletionPostLoop(io, items, itemCurrentIndexToFocus);
            }
        }
        IkGui.endChild();
        IkGui.treePop();
    }

    /** Using the clipper with beginMultiSelect()/endMultiSelect() inside a table. */
    private static void showMultiSelectInTable() {
        if (!IkGui.treeNode("Multi-Select (in a table)")) {
            return;
        }
        final SelectionBasicStorage selection = multiSelectTable;
        final int itemsCount = 10_000;
        IkGui.text(String.format("Selection: %d/%d", selection.getSize(), itemsCount));
        if (IkGui.beginTable(
                "##Basket",
                2,
                TableFlags.SCROLL_Y | TableFlags.ROW_BACKGROUND | TableFlags.BORDERS_OUTER,
                0.0f,
                IkGui.getFontSize() * 20)) {
            IkGui.tableSetupColumn("Object");
            IkGui.tableSetupColumn("Action");
            IkGui.tableSetupScrollFreeze(0, 1);
            IkGui.tableHeadersRow();

            final int flags = MultiSelectFlags.CLEAR_ON_ESCAPE | MultiSelectFlags.BOX_SELECT_1D;
            MultiSelectIO io = IkGui.beginMultiSelect(flags, selection.getSize(), itemsCount);
            selection.applyRequests(io);

            final ListClipper clipper = new ListClipper();
            clipper.begin(itemsCount);
            if (io.rangeSourceItem != SelectionUserData.INVALID) {
                // Ensure the range source item is not clipped
                clipper.includeItemByIndex((int) io.rangeSourceItem);
            }
            while (clipper.step()) {
                for (int n = clipper.displayStart; n < clipper.displayEnd; ++n) {
                    IkGui.tableNextRow();
                    IkGui.tableNextColumn();
                    IkGui.pushID(n);
                    IkGui.setNextItemSelectionUserData(n);
                    IkGui.selectable(
                            exampleObjectLabel(n),
                            selection.contains(n),
                            SelectableFlags.SPAN_ALL_COLUMNS | SelectableFlags.ALLOW_OVERLAP);
                    IkGui.tableNextColumn();
                    IkGui.smallButton("hello");
                    IkGui.popID();
                }
            }

            io = IkGui.endMultiSelect();
            selection.applyRequests(io);
            IkGui.endTable();
        }
        IkGui.treePop();
    }

    /** Multi-select in a list of checkboxes. */
    private static void showMultiSelectCheckboxes() {
        if (!IkGui.treeNode("Multi-Select (checkboxes)")) {
            return;
        }
        IkGui.text("In a list of checkboxes (not selectable):");
        IkGui.bulletText("Using NO_AUTO_SELECT + NO_AUTO_CLEAR flags.");
        IkGui.bulletText("Shift+Click to check multiple boxes.");
        IkGui.bulletText("Shift+Keyboard to copy current value to other boxes.");

        // With an array of checkboxes, you may want to use NO_AUTO_SELECT + NO_AUTO_CLEAR and the
        // SelectionExternalStorage helper
        final IkInt flags = multiSelectCheckboxFlags;
        IkGui.checkboxFlags("NO_AUTO_SELECT", flags, MultiSelectFlags.NO_AUTO_SELECT);
        IkGui.checkboxFlags("NO_AUTO_CLEAR", flags, MultiSelectFlags.NO_AUTO_CLEAR);
        // We can't use BOX_SELECT_1D, as checkboxes are of varying width
        IkGui.checkboxFlags("BOX_SELECT_2D", flags, MultiSelectFlags.BOX_SELECT_2D);

        if (IkGui.beginChild(
                "##Basket",
                -Float.MIN_VALUE,
                IkGui.getFontSize() * 20,
                ChildFlags.BORDERS | ChildFlags.RESIZE_Y)) {
            final IkBoolean[] items = multiSelectCheckboxes;
            MultiSelectIO io = IkGui.beginMultiSelect(flags.get(), -1, items.length);
            multiSelectCheckboxStorage.applyRequests(io);
            for (int n = 0; n < items.length; ++n) {
                IkGui.setNextItemSelectionUserData(n);
                IkGui.checkbox("Item " + n, items[n]);
            }
            io = IkGui.endMultiSelect();
            multiSelectCheckboxStorage.applyRequests(io);
        }
        IkGui.endChild();
        IkGui.treePop();
    }

    /** Individual selection scopes in the same window. */
    private static void showMultiSelectMultipleScopes() {
        if (!IkGui.treeNode("Multi-Select (multiple scopes)")) {
            return;
        }
        final int itemsCount = 8; // Per scope

        // Use SCOPE_RECT to not affect other selections in the same window
        final IkInt flags = multiSelectScopeFlags;
        if (IkGui.checkboxFlags("SCOPE_WINDOW", flags, MultiSelectFlags.SCOPE_WINDOW)
                && (flags.get() & MultiSelectFlags.SCOPE_WINDOW) != 0) {
            flags.set(flags.get() & ~MultiSelectFlags.SCOPE_RECT);
        }
        if (IkGui.checkboxFlags("SCOPE_RECT", flags, MultiSelectFlags.SCOPE_RECT)
                && (flags.get() & MultiSelectFlags.SCOPE_RECT) != 0) {
            flags.set(flags.get() & ~MultiSelectFlags.SCOPE_WINDOW);
        }
        IkGui.checkboxFlags("CLEAR_ON_CLICK_VOID", flags, MultiSelectFlags.CLEAR_ON_CLICK_VOID);
        IkGui.checkboxFlags("BOX_SELECT_1D", flags, MultiSelectFlags.BOX_SELECT_1D);

        for (int scope = 0; scope < multiSelectScopes.length; ++scope) {
            IkGui.pushID(scope);
            final SelectionBasicStorage selection = multiSelectScopes[scope];
            MultiSelectIO io = IkGui.beginMultiSelect(flags.get(), selection.getSize(), itemsCount);
            selection.applyRequests(io);

            IkGui.separatorText("Selection scope");
            IkGui.text(String.format("Selection size: %d/%d", selection.getSize(), itemsCount));
            for (int n = 0; n < itemsCount; ++n) {
                IkGui.setNextItemSelectionUserData(n);
                IkGui.selectable(exampleObjectLabel(n), selection.contains(n));
            }

            // Apply the multi-select requests
            io = IkGui.endMultiSelect();
            selection.applyRequests(io);
            IkGui.popID();
        }
        IkGui.treePop();
    }

    /**
     * Multiple selection in a tree. The difficulty is to "interpolate" from the range source to the
     * range destination in set range requests, matching what the user sees: in visible order,
     * skipping closed nodes. In a real codebase with filtering and clipping, you would more likely
     * build an array mapping sequential indices to visible tree nodes, which makes this easier.
     */
    private static void showMultiSelectTrees() {
        if (!IkGui.treeNode("Multi-Select (trees)")) {
            return;
        }
        helpMarker(
                "This is rather advanced and experimental. If you are getting started with"
                        + " multi-select, please don't start by looking at how to use it for a"
                        + " tree!\n\n"
                        + "Future versions will try to simplify and formalize some of this.");

        final SelectionBasicStorage selection = multiSelectTreeSelection;
        if (exampleTree == null) {
            exampleTree = exampleTreeCreateDemoTree();
        }
        IkGui.text("Selection size: " + selection.getSize());

        if (IkGui.beginChild(
                "##Tree",
                -Float.MIN_VALUE,
                IkGui.getFontSize() * 20,
                ChildFlags.FRAME_STYLE | ChildFlags.RESIZE_Y)) {
            final int flags = MultiSelectFlags.CLEAR_ON_ESCAPE | MultiSelectFlags.BOX_SELECT_2D;
            MultiSelectIO io = IkGui.beginMultiSelect(flags, selection.getSize(), -1);
            exampleTreeApplySelectionRequests(io, exampleTree, selection);
            for (ExampleTreeNode node : exampleTree.children) {
                exampleTreeDrawNode(node, selection);
            }
            io = IkGui.endMultiSelect();
            exampleTreeApplySelectionRequests(io, exampleTree, selection);
        }
        IkGui.endChild();
        IkGui.treePop();
    }

    private static final int MULTI_SELECT_WIDGET_SELECTABLE = 0;
    private static final int MULTI_SELECT_WIDGET_TREE_NODE = 1;
    private static final IkBoolean multiSelectUseClipper = new IkBoolean(true);
    private static final IkBoolean multiSelectUseDeletion = new IkBoolean(true);
    private static final IkBoolean multiSelectUseDragDrop = new IkBoolean(true);
    private static final IkBoolean multiSelectShowInTable = new IkBoolean(false);
    private static final IkBoolean multiSelectShowColorButton = new IkBoolean(true);
    private static final IkInt multiSelectAdvancedFlags =
            new IkInt(MultiSelectFlags.CLEAR_ON_ESCAPE | MultiSelectFlags.BOX_SELECT_1D);
    private static final IkInt multiSelectWidgetType = new IkInt(MULTI_SELECT_WIDGET_SELECTABLE);
    private static final List<Integer> multiSelectAdvancedItems = new ArrayList<>();
    private static final ExampleSelectionWithDeletion multiSelectAdvancedSelection =
            new ExampleSelectionWithDeletion();

    private static boolean multiSelectAdvancedInitialized = false;

    /** Queue deletion triggered from the context menu. */
    private static boolean multiSelectRequestDeletionFromMenu = false;

    /** A select-on checkbox, which turns off the other select-on flags when checked. */
    private static void multiSelectSelectOnCheckbox(String label, IkInt flags, int flag) {
        if (IkGui.checkboxFlags(label, flags, flag)) {
            flags.set(flags.get() & ~(MultiSelectFlags.SELECT_ON_MASK ^ flag));
        }
    }

    /**
     * An advanced demonstration of beginMultiSelect(): clipping, deletion, basic drag and drop, a
     * tree node variant (the tree nodes don't expand here, since expanding tree nodes with clipping
     * is a separate thing), and use inside a table.
     */
    private static void showMultiSelectAdvanced() {
        if (!IkGui.treeNode("Multi-Select (advanced)")) {
            return;
        }
        final IkInt flags = multiSelectAdvancedFlags;
        if (IkGui.treeNode("Options")) {
            IkGui.radioButton("Selectables", multiSelectWidgetType, MULTI_SELECT_WIDGET_SELECTABLE);
            IkGui.sameLine();
            IkGui.radioButton("Tree nodes", multiSelectWidgetType, MULTI_SELECT_WIDGET_TREE_NODE);
            IkGui.sameLine();
            helpMarker(
                    "treeNode() is technically supported but... using this correctly is more"
                            + " complicated (you need some sort of linear/random access to your"
                            + " tree, which is suited to advanced trees setups already"
                            + " implementing filters and clipper. We will work toward simplifying"
                            + " and demoing this.\n\n"
                            + "For now the tree demo is actually a little bit meaningless because"
                            + " it is an empty tree with only root nodes.");
            IkGui.checkbox("Enable clipper", multiSelectUseClipper);
            IkGui.checkbox("Enable deletion", multiSelectUseDeletion);
            IkGui.checkbox("Enable drag & drop", multiSelectUseDragDrop);
            IkGui.checkbox("Show in a table", multiSelectShowInTable);
            IkGui.checkbox("Show color button", multiSelectShowColorButton);
            IkGui.checkboxFlags("SINGLE_SELECT", flags, MultiSelectFlags.SINGLE_SELECT);
            IkGui.checkboxFlags("NO_SELECT_ALL", flags, MultiSelectFlags.NO_SELECT_ALL);
            IkGui.checkboxFlags("NO_RANGE_SELECT", flags, MultiSelectFlags.NO_RANGE_SELECT);
            IkGui.checkboxFlags("NO_AUTO_SELECT", flags, MultiSelectFlags.NO_AUTO_SELECT);
            IkGui.checkboxFlags("NO_AUTO_CLEAR", flags, MultiSelectFlags.NO_AUTO_CLEAR);
            IkGui.checkboxFlags(
                    "NO_AUTO_CLEAR_ON_RESELECT", flags, MultiSelectFlags.NO_AUTO_CLEAR_ON_RESELECT);
            IkGui.checkboxFlags(
                    "NO_SELECT_ON_RIGHT_CLICK", flags, MultiSelectFlags.NO_SELECT_ON_RIGHT_CLICK);
            IkGui.checkboxFlags("BOX_SELECT_1D", flags, MultiSelectFlags.BOX_SELECT_1D);
            IkGui.checkboxFlags("BOX_SELECT_2D", flags, MultiSelectFlags.BOX_SELECT_2D);
            IkGui.checkboxFlags(
                    "BOX_SELECT_NO_SCROLL", flags, MultiSelectFlags.BOX_SELECT_NO_SCROLL);
            IkGui.checkboxFlags("CLEAR_ON_ESCAPE", flags, MultiSelectFlags.CLEAR_ON_ESCAPE);
            IkGui.checkboxFlags("CLEAR_ON_CLICK_VOID", flags, MultiSelectFlags.CLEAR_ON_CLICK_VOID);
            if (IkGui.checkboxFlags("SCOPE_WINDOW", flags, MultiSelectFlags.SCOPE_WINDOW)
                    && (flags.get() & MultiSelectFlags.SCOPE_WINDOW) != 0) {
                flags.set(flags.get() & ~MultiSelectFlags.SCOPE_RECT);
            }
            if (IkGui.checkboxFlags("SCOPE_RECT", flags, MultiSelectFlags.SCOPE_RECT)
                    && (flags.get() & MultiSelectFlags.SCOPE_RECT) != 0) {
                flags.set(flags.get() & ~MultiSelectFlags.SCOPE_WINDOW);
            }
            multiSelectSelectOnCheckbox("SELECT_ON_AUTO", flags, MultiSelectFlags.SELECT_ON_AUTO);
            IkGui.sameLine();
            helpMarker(
                    "Apply selection on mouse down when clicking on unselected item, on mouse up"
                            + " when clicking on selected item. (Default)");
            multiSelectSelectOnCheckbox(
                    "SELECT_ON_CLICK_ALWAYS", flags, MultiSelectFlags.SELECT_ON_CLICK_ALWAYS);
            IkGui.sameLine();
            helpMarker(
                    "Prevents Drag and Drop from being used on multi-selection, but allows e.g."
                            + " BoxSelect to always reselect even when clicking inside an"
                            + " existing selection. (Excel style behavior)");
            multiSelectSelectOnCheckbox(
                    "SELECT_ON_CLICK_RELEASE", flags, MultiSelectFlags.SELECT_ON_CLICK_RELEASE);
            IkGui.sameLine();
            helpMarker("Allow dragging an unselected item without altering selection.");
            IkGui.treePop();
        }

        // Initialize the default list with 1000 items. Use the default adapter: pass the index to
        // setNextItemSelectionUserData(), and store the index in the selection.
        final List<Integer> items = multiSelectAdvancedItems;
        if (!multiSelectAdvancedInitialized) {
            multiSelectAdvancedInitialized = true;
            for (int n = 0; n < 1000; ++n) {
                items.add(n);
            }
        }
        final ExampleSelectionWithDeletion selection = multiSelectAdvancedSelection;
        final boolean treeNodes = multiSelectWidgetType.get() == MULTI_SELECT_WIDGET_TREE_NODE;
        final boolean useClipper = multiSelectUseClipper.get();
        final boolean showInTable = multiSelectShowInTable.get();

        IkGui.text(String.format("Selection size: %d/%d", selection.getSize(), items.size()));

        final float itemsHeight =
                treeNodes ? IkGui.getTextLineHeight() : IkGui.getTextLineHeightWithSpacing();
        IkGui.setNextWindowContentSize(0.0f, items.size() * itemsHeight);
        if (IkGui.beginChild(
                "##Basket",
                -Float.MIN_VALUE,
                IkGui.getFontSize() * 20,
                ChildFlags.FRAME_STYLE | ChildFlags.RESIZE_Y)) {
            final StyleVariables style = IkGui.getContext().style.variable;
            final float colorButtonSize = IkGui.getFontSize();
            if (treeNodes) {
                IkGui.pushStyleVarFloat2(StyleVariable.ITEM_SPACING, style.itemSpacing.x, 0.0f);
            }

            MultiSelectIO io =
                    IkGui.beginMultiSelect(flags.get(), selection.getSize(), items.size());
            selection.applyRequests(io);

            final boolean wantDelete =
                    (multiSelectUseDeletion.get()
                                    && IkGui.shortcut(KeyChord.of(Key.DELETE), InputFlags.REPEAT)
                                    && selection.getSize() > 0)
                            || multiSelectRequestDeletionFromMenu;
            final int itemCurrentIndexToFocus =
                    wantDelete ? selection.applyDeletionPreLoop(io, items.size()) : -1;
            multiSelectRequestDeletionFromMenu = false;

            if (showInTable) {
                if (treeNodes) {
                    IkGui.pushStyleVarFloat2(StyleVariable.CELL_PADDING, 0.0f, 0.0f);
                }
                IkGui.beginTable(
                        "##Split",
                        2,
                        TableFlags.RESIZABLE
                                | TableFlags.NO_SAVED_SETTINGS
                                | TableFlags.NO_PAD_OUTER_X);
                IkGui.tableSetupColumn("", TableColumnFlags.WIDTH_STRETCH, 0.70f);
                IkGui.tableSetupColumn("", TableColumnFlags.WIDTH_STRETCH, 0.30f);
            }

            final ListClipper clipper = new ListClipper();
            if (useClipper) {
                clipper.begin(items.size());
                if (itemCurrentIndexToFocus != -1) {
                    // Ensure the focused item is not clipped
                    clipper.includeItemByIndex(itemCurrentIndexToFocus);
                }
                if (io.rangeSourceItem != SelectionUserData.INVALID) {
                    // Ensure the range source item is not clipped
                    clipper.includeItemByIndex((int) io.rangeSourceItem);
                }
            }

            while (!useClipper || clipper.step()) {
                final int itemBegin = useClipper ? clipper.displayStart : 0;
                final int itemEnd = useClipper ? clipper.displayEnd : items.size();
                for (int n = itemBegin; n < itemEnd; ++n) {
                    if (showInTable) {
                        IkGui.tableNextColumn();
                    }
                    final int itemID = items.get(n);
                    final String itemCategory = EXAMPLE_NAMES[itemID % EXAMPLE_NAMES.length];
                    final String label = exampleObjectLabel(itemID);

                    // For refocusing after deletion to work, the object ID needs to be stable, so
                    // it doesn't depend on its index in the list. We use the persistent item ID
                    // instead of the index to build a unique ID.
                    IkGui.pushID(itemID);

                    // Emit a color button, to test that Shift+LeftArrow landing on an item that is
                    // not part of the selection scope doesn't erroneously alter our selection
                    if (multiSelectShowColorButton.get()) {
                        final int dummyColor = n * 0xC250B74B;
                        final float[] color = {
                            ((dummyColor >>> 24) & 0xFF) / 255.0f,
                            ((dummyColor >>> 16) & 0xFF) / 255.0f,
                            ((dummyColor >>> 8) & 0xFF) / 255.0f,
                            1.0f
                        };
                        IkGui.colorButton(
                                "##",
                                color,
                                ColorEditFlags.NO_TOOLTIP,
                                colorButtonSize,
                                colorButtonSize);
                        IkGui.sameLine();
                    }

                    // Submit the item
                    final boolean itemIsSelected = selection.contains(n);
                    boolean itemIsOpen = false;
                    IkGui.setNextItemSelectionUserData(n);
                    if (!treeNodes) {
                        IkGui.selectable(label, itemIsSelected, SelectableFlags.NONE);
                    } else {
                        int treeNodeFlags =
                                TreeNodeFlags.SPAN_AVAIL_WIDTH
                                        | TreeNodeFlags.OPEN_ON_ARROW
                                        | TreeNodeFlags.OPEN_ON_DOUBLE_CLICK;
                        if (itemIsSelected) {
                            treeNodeFlags |= TreeNodeFlags.SELECTED;
                        }
                        itemIsOpen = IkGui.treeNodeEx(label, treeNodeFlags);
                    }

                    // Focus (for after deletion)
                    if (itemCurrentIndexToFocus == n) {
                        IkGui.setKeyboardFocusHere(-1);
                    }

                    // Drag and drop
                    if (multiSelectUseDragDrop.get() && IkGui.beginDragDropSource()) {
                        // Create a payload with the full selection, or a single unselected item
                        // (the latter is only possible when using SELECT_ON_CLICK_RELEASE)
                        int[] payloadItems = IkGui.getDragDropPayload("MULTISELECT_DEMO_ITEMS");
                        if (payloadItems == null) {
                            payloadItems =
                                    itemIsSelected
                                            ? selection.getSelectedItems()
                                            : new int[] {itemID};
                            IkGui.setDragDropPayload("MULTISELECT_DEMO_ITEMS", payloadItems);
                        }

                        // Display the payload content in a tooltip
                        if (payloadItems.length == 1) {
                            IkGui.text(exampleObjectLabel(payloadItems[0]));
                        } else {
                            IkGui.text("Dragging " + payloadItems.length + " objects");
                        }
                        IkGui.endDragDropSource();
                    }

                    if (treeNodes && itemIsOpen) {
                        IkGui.treePop();
                    }

                    // Right-click: context menu
                    if (IkGui.beginPopupContextItem()) {
                        IkGui.beginDisabled(
                                !multiSelectUseDeletion.get() || selection.getSize() == 0);
                        if (IkGui.selectable(
                                "Delete " + selection.getSize() + " item(s)###DeleteSelected")) {
                            multiSelectRequestDeletionFromMenu = true;
                        }
                        IkGui.endDisabled();
                        IkGui.selectable("Close");
                        IkGui.endPopup();
                    }

                    // Demo content within a table
                    if (showInTable) {
                        IkGui.tableNextColumn();
                        IkGui.setNextItemWidth(-Float.MIN_VALUE);
                        IkGui.pushStyleVarFloat2(StyleVariable.FRAME_PADDING, 0.0f, 0.0f);
                        IkGui.inputText(
                                "##NoLabel", new IkString(itemCategory), InputTextFlags.READ_ONLY);
                        IkGui.popStyleVar();
                    }

                    IkGui.popID();
                }
                if (!useClipper) {
                    break;
                }
            }

            if (showInTable) {
                IkGui.endTable();
                if (treeNodes) {
                    IkGui.popStyleVar();
                }
            }

            // Apply the multi-select requests
            io = IkGui.endMultiSelect();
            selection.applyRequests(io);
            if (wantDelete) {
                selection.applyDeletionPostLoop(io, items, itemCurrentIndexToFocus);
            }

            if (treeNodes) {
                IkGui.popStyleVar();
            }
        }
        IkGui.endChild();
        IkGui.treePop();
    }

    private static final IkByte dataTypesByte = new IkByte((byte) 127);
    private static final IkShort dataTypesShort = new IkShort((short) 32_767);
    private static final IkInt dataTypesInt = new IkInt(-1);
    private static final IkLong dataTypesLong = new IkLong(-1);
    private static final IkFloat dataTypesFloat = new IkFloat(0.123f);
    private static final IkDouble dataTypesDouble = new IkDouble(90_000.012_345_678_901_234_567_89);
    private static final IkBoolean dataTypesDragClamp = new IkBoolean(false);
    private static final IkBoolean dataTypesInputsStep = new IkBoolean(true);
    private static final IkInt dataTypesInputFlags = new IkInt(InputTextFlags.NONE);

    /**
     * The scalar functions (dragScalar(), sliderScalar(), inputScalar()) work with various data
     * types, passed as a SliderDataType along with the value holder. Limits and steps are boxed
     * numbers, where null means there is none. Java has no unsigned types, so only signed integers
     * are supported.
     */
    private static void showWidgetsDataTypes() {
        if (!IkGui.treeNode("Data Types")) {
            return;
        }
        // Slider functions have a maximum usable range of half the natural type maximum for 64-bit
        // and floating point types
        final long longMin = Long.MIN_VALUE / 2;
        final long longMax = Long.MAX_VALUE / 2;
        final long longHighA = Long.MAX_VALUE / 2 - 100;
        final long longHighB = Long.MAX_VALUE / 2;
        final int intMin = Integer.MIN_VALUE / 2;
        final int intMax = Integer.MAX_VALUE / 2;
        final int intHighA = Integer.MAX_VALUE / 2 - 100;
        final int intHighB = Integer.MAX_VALUE / 2;
        final float floatLow = -10_000_000_000.0f;
        final float floatHigh = 10_000_000_000.0f;
        final double doubleLow = -1_000_000_000_000_000.0;
        final double doubleHigh = 1_000_000_000_000_000.0;

        final IkByte byteValue = dataTypesByte;
        final IkShort shortValue = dataTypesShort;
        final IkInt intValue = dataTypesInt;
        final IkLong longValue = dataTypesLong;
        final IkFloat floatValue = dataTypesFloat;
        final IkDouble doubleValue = dataTypesDouble;

        final float dragSpeed = 0.2f;
        final boolean clamp = dataTypesDragClamp.get();
        IkGui.separatorText("Drags");
        IkGui.checkbox("Clamp integers to 0..50", dataTypesDragClamp);
        IkGui.sameLine();
        helpMarker(
                "As with every widget in IkGui, we never modify values unless there is a user"
                        + " interaction.\n"
                        + "You can override the clamping limits by using Ctrl+Click to input a"
                        + " value.");
        IkGui.dragScalar(
                "drag byte",
                SliderDataType.BYTE,
                byteValue,
                dragSpeed,
                clamp ? 0 : null,
                clamp ? 50 : null);
        IkGui.dragScalar(
                "drag short",
                SliderDataType.SHORT,
                shortValue,
                dragSpeed,
                clamp ? 0 : null,
                clamp ? 50 : null,
                "%d ms");
        IkGui.dragScalar(
                "drag int",
                SliderDataType.INT,
                intValue,
                dragSpeed,
                clamp ? 0 : null,
                clamp ? 50 : null);
        IkGui.dragScalar(
                "drag int hex",
                SliderDataType.INT,
                intValue,
                dragSpeed,
                clamp ? 0 : null,
                clamp ? 50 : null,
                "0x%08X");
        IkGui.dragScalar(
                "drag long",
                SliderDataType.LONG,
                longValue,
                dragSpeed,
                clamp ? 0L : null,
                clamp ? 50L : null);
        IkGui.dragScalar("drag float", SliderDataType.FLOAT, floatValue, 0.005f, 0.0f, 1.0f, "%f");
        IkGui.dragScalar(
                "drag float log",
                SliderDataType.FLOAT,
                floatValue,
                0.005f,
                0.0f,
                1.0f,
                "%f",
                SliderFlags.LOGARITHMIC);
        // Only a minimum, so there's no maximum
        IkGui.dragScalar(
                "drag double",
                SliderDataType.DOUBLE,
                doubleValue,
                0.0005f,
                0.0,
                null,
                "%.10f grams");
        IkGui.dragScalar(
                "drag double log",
                SliderDataType.DOUBLE,
                doubleValue,
                0.0005f,
                0.0,
                1.0,
                "0 < %.10f < 1",
                SliderFlags.LOGARITHMIC);

        IkGui.separatorText("Sliders");
        IkGui.sliderScalar(
                "slider byte full",
                SliderDataType.BYTE,
                byteValue,
                Byte.MIN_VALUE,
                Byte.MAX_VALUE,
                "%d");
        IkGui.sliderScalar(
                "slider short full",
                SliderDataType.SHORT,
                shortValue,
                Short.MIN_VALUE,
                Short.MAX_VALUE,
                "%d");
        IkGui.sliderScalar("slider int low", SliderDataType.INT, intValue, 0, 50, "%d");
        IkGui.sliderScalar(
                "slider int high", SliderDataType.INT, intValue, intHighA, intHighB, "%d");
        IkGui.sliderScalar("slider int full", SliderDataType.INT, intValue, intMin, intMax, "%d");
        IkGui.sliderScalar("slider int hex", SliderDataType.INT, intValue, 0, 50, "0x%04X");
        IkGui.sliderScalar("slider long low", SliderDataType.LONG, longValue, 0L, 50L, "%d");
        IkGui.sliderScalar(
                "slider long high", SliderDataType.LONG, longValue, longHighA, longHighB, "%d");
        IkGui.sliderScalar(
                "slider long full", SliderDataType.LONG, longValue, longMin, longMax, "%d");
        IkGui.sliderScalar("slider float low", SliderDataType.FLOAT, floatValue, 0.0f, 1.0f);
        IkGui.sliderScalar(
                "slider float low log",
                SliderDataType.FLOAT,
                floatValue,
                0.0f,
                1.0f,
                "%.10f",
                SliderFlags.LOGARITHMIC);
        IkGui.sliderScalar(
                "slider float high", SliderDataType.FLOAT, floatValue, floatLow, floatHigh, "%e");
        IkGui.sliderScalar(
                "slider double low", SliderDataType.DOUBLE, doubleValue, 0.0, 1.0, "%.10f grams");
        IkGui.sliderScalar(
                "slider double low log",
                SliderDataType.DOUBLE,
                doubleValue,
                0.0,
                1.0,
                "%.10f",
                SliderFlags.LOGARITHMIC);
        IkGui.sliderScalar(
                "slider double high",
                SliderDataType.DOUBLE,
                doubleValue,
                doubleLow,
                doubleHigh,
                "%e grams");

        IkGui.separatorText("Sliders (reverse)");
        IkGui.sliderScalar(
                "slider byte reverse",
                SliderDataType.BYTE,
                byteValue,
                Byte.MAX_VALUE,
                Byte.MIN_VALUE,
                "%d");
        IkGui.sliderScalar("slider int reverse", SliderDataType.INT, intValue, 50, 0, "%d");
        IkGui.sliderScalar("slider long reverse", SliderDataType.LONG, longValue, 50L, 0L, "%d");

        IkGui.separatorText("Inputs");
        IkGui.checkbox("Show step buttons", dataTypesInputsStep);
        final IkInt flagsHolder = dataTypesInputFlags;
        IkGui.checkboxFlags("InputTextFlags.READ_ONLY", flagsHolder, InputTextFlags.READ_ONLY);
        IkGui.checkboxFlags(
                "InputTextFlags.PARSE_EMPTY_REF_VAL",
                flagsHolder,
                InputTextFlags.PARSE_EMPTY_REF_VAL);
        IkGui.checkboxFlags(
                "InputTextFlags.DISPLAY_EMPTY_REF_VAL",
                flagsHolder,
                InputTextFlags.DISPLAY_EMPTY_REF_VAL);
        final int flags = flagsHolder.get();
        final boolean step = dataTypesInputsStep.get();
        IkGui.inputScalar(
                "input byte", SliderDataType.BYTE, byteValue, step ? 1 : null, null, "%d", flags);
        IkGui.inputScalar(
                "input short",
                SliderDataType.SHORT,
                shortValue,
                step ? 1 : null,
                null,
                "%d",
                flags);
        IkGui.inputScalar(
                "input int", SliderDataType.INT, intValue, step ? 1 : null, null, "%d", flags);
        IkGui.inputScalar(
                "input int hex",
                SliderDataType.INT,
                intValue,
                step ? 1 : null,
                null,
                "%04X",
                flags);
        IkGui.inputScalar(
                "input long", SliderDataType.LONG, longValue, step ? 1L : null, null, null, flags);
        IkGui.inputScalar(
                "input float",
                SliderDataType.FLOAT,
                floatValue,
                step ? 1.0f : null,
                null,
                null,
                flags);
        IkGui.inputScalar(
                "input double",
                SliderDataType.DOUBLE,
                doubleValue,
                step ? 1.0 : null,
                null,
                null,
                flags);

        IkGui.treePop();
    }

    private static final TextFilter textFilterDemo = new TextFilter();

    private static void showWidgetsTextFilter() {
        if (!IkGui.treeNode("Text Filter")) {
            return;
        }
        // A helper class to easily set up a text filter. You may want to implement a more
        // feature-full filtering scheme in your own application.
        helpMarker(
                "Not a widget per-se, but TextFilter is a helper to perform simple filtering on"
                        + " text strings.");
        final TextFilter filter = textFilterDemo;
        IkGui.text(
                "Filter usage:\n"
                        + "  \"\"         display all lines\n"
                        + "  xxx        display lines containing \"xxx\"\n"
                        + "  xxx yyy    display lines containing \"xxx\" and \"yyy\"\n"
                        + "  \"xxx yyy\"  display lines containing \"xxx yyy\"\n"
                        + "  xxx,yyy    display lines containing \"xxx\" or \"yyy\"\n"
                        + "  -xxx       hide lines containing \"xxx\"");
        IkGui.setNextItemWidth(-Float.MIN_VALUE);
        filter.drawWithHint("##Filter", "Filter (incl -excl)");
        if (IkGui.beginChild(
                "##items",
                -Float.MIN_VALUE,
                IkGui.getTextLineHeightWithSpacing() * 15,
                ChildFlags.FRAME_STYLE)) {
            final String[] lines = {
                "aaa1.c",
                "bbb1.c",
                "ccc1.c",
                "aaa2.cpp",
                "bbb2.cpp",
                "ccc2.cpp",
                "abc.h",
                "hello, world"
            };
            for (String item : lines) {
                if (filter.passFilter(item)) {
                    IkGui.textUnformatted(item);
                }
            }
            for (String item : EXAMPLE_NAMES) {
                if (filter.passFilter(item)) {
                    IkGui.textUnformatted(item);
                }
            }
        }
        IkGui.endChild();
        IkGui.treePop();
    }

    private static void showWidgetsSelectables() {
        if (!IkGui.treeNode("Selectables")) {
            return;
        }
        // selectable() has 2 overloads:
        // - The one taking "boolean selected" as read-only selection information. When it has
        //   been clicked it returns true and you can change the selection state accordingly.
        // - The one taking an IkBoolean as read-write selection information, which is toggled.
        // The first is more flexible, as in a real application your selection may be stored in
        // many different ways and not necessarily inside a boolean.
        if (IkGui.treeNode("Basic")) {
            IkGui.selectable("1. I am selectable", selectableBasic[0]);
            IkGui.selectable("2. I am selectable", selectableBasic[1]);
            IkGui.selectable("3. I am selectable", selectableBasic[2]);
            if (IkGui.selectable(
                    "4. I am double clickable",
                    selectableBasic[3].get(),
                    SelectableFlags.ALLOW_DOUBLE_CLICK)) {
                if (IkGui.isMouseDoubleClicked(MouseButton.LEFT)) {
                    selectableBasic[3].set(!selectableBasic[3].get());
                }
            }
            IkGui.treePop();
        }

        if (IkGui.treeNode("Multiple items on the same line")) {
            // (1) Using setNextItemAllowOverlap(), and the selectable() overload that takes an
            // IkBoolean, which is toggled automatically
            final String[] files = {"main.c", "hello.cpp", "hello.h"};
            for (int i = 0; i < files.length; ++i) {
                IkGui.setNextItemAllowOverlap();
                IkGui.selectable(files[i], selectableSameLine[i]);
                IkGui.sameLine();
                IkGui.smallButton("Link " + (i + 1));
            }

            // (2) SelectableFlags.ALLOW_OVERLAP is a shortcut for setNextItemAllowOverlap(). There
            // is no visible label, the contents are displayed inside the selectable bounds. We
            // don't maintain an actual selection in this example to keep things simple.
            IkGui.spacing();
            final float colorMarkerWidth = IkGui.calcTextSize("x").x;
            for (int n = 0; n < 5; n++) {
                IkGui.pushID(n);
                IkGui.alignTextToFramePadding();
                if (IkGui.selectable(
                        "##selectable",
                        selectableSameLineSelected == n,
                        SelectableFlags.ALLOW_OVERLAP)) {
                    selectableSameLineSelected = n;
                }
                IkGui.sameLine(0, 0);
                IkGui.checkbox("##check", selectableSameLineChecked[n]);
                IkGui.sameLine();
                final float[] color = {
                    (n & 1) != 0 ? 1.0f : 0.2f, (n & 2) != 0 ? 1.0f : 0.2f, 0.2f, 1.0f
                };
                IkGui.colorButton("##color", color, ColorEditFlags.NO_TOOLTIP, colorMarkerWidth, 0);
                IkGui.sameLine();
                IkGui.text("Some label");
                IkGui.popID();
            }
            IkGui.treePop();
        }

        if (IkGui.treeNode("In Tables")) {
            final int tableFlags =
                    TableFlags.RESIZABLE | TableFlags.NO_SAVED_SETTINGS | TableFlags.BORDERS;
            if (IkGui.beginTable("split1", 3, tableFlags)) {
                for (int i = 0; i < 10; i++) {
                    IkGui.tableNextColumn();
                    IkGui.selectable("Item " + i, selectableInTables[i]);
                }
                IkGui.endTable();
            }
            IkGui.spacing();
            if (IkGui.beginTable("split2", 3, tableFlags)) {
                for (int i = 0; i < 10; i++) {
                    IkGui.tableNextRow();
                    IkGui.tableNextColumn();
                    IkGui.selectable(
                            "Item " + i, selectableInTables[i], SelectableFlags.SPAN_ALL_COLUMNS);
                    IkGui.tableNextColumn();
                    IkGui.text("Some other contents");
                    IkGui.tableNextColumn();
                    IkGui.text("123456");
                }
                IkGui.endTable();
            }
            IkGui.treePop();
        }

        if (IkGui.treeNode("Grid")) {
            // Add in a bit of silly fun...
            final float time = IkGui.getTime() / 1000.0f;
            boolean winningState = true;
            for (boolean cell : selectableGrid) {
                winningState &= cell;
            }
            if (winningState) {
                // If all cells are selected...
                IkGui.pushStyleVarFloat2(
                        StyleVariable.SELECTABLE_TEXT_ALIGN,
                        0.5f + 0.5f * (float) Math.cos(time * 2.0f),
                        0.5f + 0.5f * (float) Math.sin(time * 3.0f));
            }

            final float size = IkGui.calcTextSize("Sailor").x;
            for (int y = 0; y < 4; ++y) {
                for (int x = 0; x < 4; ++x) {
                    if (x > 0) {
                        IkGui.sameLine();
                    }
                    final int index = y * 4 + x;
                    IkGui.pushID(index);
                    if (IkGui.selectable(
                            "Sailor", selectableGrid[index], SelectableFlags.NONE, size, size)) {
                        // Toggle the clicked cell and its neighbors
                        selectableGrid[index] = !selectableGrid[index];
                        if (x > 0) {
                            selectableGrid[index - 1] = !selectableGrid[index - 1];
                        }
                        if (x < 3) {
                            selectableGrid[index + 1] = !selectableGrid[index + 1];
                        }
                        if (y > 0) {
                            selectableGrid[index - 4] = !selectableGrid[index - 4];
                        }
                        if (y < 3) {
                            selectableGrid[index + 4] = !selectableGrid[index + 4];
                        }
                    }
                    IkGui.popID();
                }
            }

            if (winningState) {
                IkGui.popStyleVar();
            }
            IkGui.treePop();
        }

        if (IkGui.treeNode("Alignment")) {
            helpMarker(
                    "By default, selectables use style.selectableTextAlign but it can be"
                            + " overridden on a per-item basis using pushStyleVar(). You'll"
                            + " probably want to always keep your default situation to left-align"
                            + " otherwise it becomes difficult to layout multiple items on a same"
                            + " line");
            final float size = IkGui.calcTextSize("(1.0,1.0)").x;
            for (int y = 0; y < 3; y++) {
                for (int x = 0; x < 3; x++) {
                    final float alignX = x / 2.0f;
                    final float alignY = y / 2.0f;
                    if (x > 0) {
                        IkGui.sameLine();
                    }
                    IkGui.pushStyleVarFloat2(StyleVariable.SELECTABLE_TEXT_ALIGN, alignX, alignY);
                    IkGui.selectable(
                            String.format("(%.1f,%.1f)", alignX, alignY),
                            selectableAlignment[3 * y + x],
                            SelectableFlags.NONE,
                            size,
                            size);
                    IkGui.popStyleVar();
                }
            }
            IkGui.treePop();
        }
        IkGui.treePop();
    }

    private static final IkBoolean[] selectableBasic = {
        new IkBoolean(false), new IkBoolean(true), new IkBoolean(false), new IkBoolean(false)
    };
    private static final IkBoolean[] selectableSameLine = {
        new IkBoolean(false), new IkBoolean(false), new IkBoolean(false)
    };
    private static int selectableSameLineSelected = 0;
    private static final IkBoolean[] selectableSameLineChecked = newBooleans(5, false);
    private static final IkBoolean[] selectableInTables = newBooleans(10, false);
    private static final boolean[] selectableGrid = {
        true, false, false, false, false, true, false, false, false, false, true, false, false,
        false, false, true
    };
    private static final IkBoolean[] selectableAlignment = {
        new IkBoolean(true),
        new IkBoolean(false),
        new IkBoolean(true),
        new IkBoolean(false),
        new IkBoolean(true),
        new IkBoolean(false),
        new IkBoolean(true),
        new IkBoolean(false),
        new IkBoolean(true)
    };

    /**
     * Create an array of booleans for widgets to edit.
     *
     * @param count The number of booleans.
     * @param value The initial value of each.
     * @return The booleans.
     */
    private static IkBoolean[] newBooleans(int count, boolean value) {
        final IkBoolean[] result = new IkBoolean[count];
        for (int i = 0; i < count; ++i) {
            result[i] = new IkBoolean(value);
        }
        return result;
    }

    private static final IkInt tooltipAlwaysOn = new IkInt(0);
    private static final float[] tooltipCurve = {0.6f, 0.1f, 1.0f, 0.5f, 0.92f, 0.1f, 0.2f};

    private static void showWidgetsTooltips() {
        if (!IkGui.treeNode("Tooltips")) {
            return;
        }
        // Tooltips are windows following the mouse. They do not take focus away.
        IkGui.separatorText("General");

        // Typical use cases:
        // - Short-form (text only):      setItemTooltip("Hello");
        // - Short-form (any contents):   if (beginItemTooltip()) { text("Hello"); endTooltip(); }

        // - Full-form (text only):       if (isItemHovered(...)) { setTooltip("Hello"); }
        // - Full-form (any contents):    if (isItemHovered(...) && beginTooltip()) { text("Hello");
        //                                endTooltip(); }

        helpMarker(
                "Tooltip are typically created by using a isItemHovered() + setTooltip()"
                        + " sequence.\n\n"
                        + "We provide a helper setItemTooltip() function to perform the two with"
                        + " standards flags.");

        final float width = -Float.MIN_VALUE;

        IkGui.button("Basic", width, 0.0f);
        IkGui.setItemTooltip("I am a tooltip");

        IkGui.button("Fancy", width, 0.0f);
        if (IkGui.beginItemTooltip()) {
            IkGui.text("I am a fancy tooltip");
            IkGui.plotLines("Curve", tooltipCurve, tooltipCurve.length);
            IkGui.text(String.format("Sin(time) = %f", (float) Math.sin(IkGui.getTime() / 1000.0)));
            IkGui.endTooltip();
        }

        IkGui.separatorText("Always On");

        // Showcase NOT relying on a isItemHovered() to emit a tooltip. Here the tooltip is always
        // emitted when 'always on' is selected.
        IkGui.radioButton("Off", tooltipAlwaysOn, 0);
        IkGui.sameLine();
        IkGui.radioButton("Always On (Simple)", tooltipAlwaysOn, 1);
        IkGui.sameLine();
        IkGui.radioButton("Always On (Advanced)", tooltipAlwaysOn, 2);
        if (tooltipAlwaysOn.get() == 1) {
            IkGui.setTooltip("I am following you around.");
        } else if (tooltipAlwaysOn.get() == 2 && IkGui.beginTooltip()) {
            IkGui.progressBar(
                    (float) Math.sin(IkGui.getTime() / 1000.0) * 0.5f + 0.5f,
                    IkGui.getFontSize() * 25,
                    0.0f);
            IkGui.endTooltip();
        }

        IkGui.separatorText("Custom");

        helpMarker(
                "Passing HoveredFlags.FOR_TOOLTIP to isItemHovered() is the preferred way to"
                        + " standardize tooltip activation details across your application. You"
                        + " may however decide to use custom flags for a specific tooltip"
                        + " instance.");

        // The following examples are passed for documentation purpose but may not be useful to
        // most users. Passing HoveredFlags.FOR_TOOLTIP to isItemHovered() will pull HoveredFlags
        // values from 'style.hoverFlagsForTooltipMouse' or 'style.hoverFlagsForTooltipNav'
        // depending on whether mouse or keyboard/gamepad is being used. With default settings,
        // FOR_TOOLTIP is equivalent to DELAY_SHORT + STATIONARY.
        final StyleVariables style = IkGui.getContext().style.variable;
        IkGui.button("Manual", width, 0.0f);
        if (IkGui.isItemHovered(HoveredFlags.FOR_TOOLTIP)) {
            IkGui.setTooltip("I am a manually emitted tooltip.");
        }

        IkGui.button("DelayNone", width, 0.0f);
        if (IkGui.isItemHovered(HoveredFlags.DELAY_NONE)) {
            IkGui.setTooltip("I am a tooltip with no delay.");
        }

        // The hover delays are stored in milliseconds
        IkGui.button("DelayShort", width, 0.0f);
        if (IkGui.isItemHovered(HoveredFlags.DELAY_SHORT | HoveredFlags.NO_SHARED_DELAY)) {
            IkGui.setTooltip(
                    String.format(
                            "I am a tooltip with a short delay (%.2f sec).",
                            style.hoverDelayShort / 1000.0f));
        }

        IkGui.button("DelayLong", width, 0.0f);
        if (IkGui.isItemHovered(HoveredFlags.DELAY_NORMAL | HoveredFlags.NO_SHARED_DELAY)) {
            IkGui.setTooltip(
                    String.format(
                            "I am a tooltip with a long delay (%.2f sec).",
                            style.hoverDelayNormal / 1000.0f));
        }

        IkGui.button("Stationary", width, 0.0f);
        if (IkGui.isItemHovered(HoveredFlags.STATIONARY)) {
            IkGui.setTooltip("I am a tooltip requiring mouse to be stationary before activating.");
        }

        // Using HoveredFlags.FOR_TOOLTIP will pull flags from 'style.hoverFlagsForTooltipMouse'
        // or 'style.hoverFlagsForTooltipNav', which default value include the ALLOW_WHEN_DISABLED
        // flag.
        IkGui.beginDisabled();
        IkGui.button("Disabled item", width, 0.0f);
        if (IkGui.isItemHovered(HoveredFlags.FOR_TOOLTIP)) {
            IkGui.setTooltip("I am a tooltip for a disabled item.");
        }
        IkGui.endDisabled();

        IkGui.treePop();
    }

    private static final int[] basicDragInt1 = {50};
    private static final int[] basicDragInt2 = {42};
    private static final int[] basicDragInt3 = {128};
    private static final float[] basicDragFloat1 = {1.0f};
    private static final float[] basicDragFloat2 = {0.0067f};
    private static final int[] basicSliderInt = {0};
    private static final float[] basicSliderFloat1 = {0.123f};
    private static final float[] basicSliderFloat2 = {0.0f};
    private static final float[] basicSliderAngle = {0.0f};
    private static final int[] basicSliderElement = {0};
    private static final String[] ELEMENT_NAMES = {"Fire", "Earth", "Air", "Water"};

    private static final IkInt dragSliderFlags = new IkInt(SliderFlags.NONE);

    private static final float[] flagsDragFloat = {0.5f};
    private static final float[] flagsDragFloat4 = new float[4];
    private static final int[] flagsDragInt = {50};
    private static final float[] flagsSliderFloat = {0.5f};
    private static final float[] flagsSliderFloat4 = new float[4];
    private static final int[] flagsSliderInt = {50};

    private static void showWidgetsDragAndSliderFlags() {
        if (!IkGui.treeNode("Drag/Slider Flags")) {
            return;
        }
        // Demonstrate using advanced flags for drag and slider functions. Note that the flags are
        // the same!
        IkGui.checkboxFlags("ALWAYS_CLAMP", dragSliderFlags, SliderFlags.ALWAYS_CLAMP);
        IkGui.checkboxFlags("CLAMP_ON_INPUT", dragSliderFlags, SliderFlags.CLAMP_ON_INPUT);
        IkGui.sameLine();
        helpMarker(
                "Clamp value to min/max bounds when input manually with Ctrl+Click. By default"
                        + " Ctrl+Click allows going out of bounds.");
        IkGui.checkboxFlags("CLAMP_ZERO_RANGE", dragSliderFlags, SliderFlags.CLAMP_ZERO_RANGE);
        IkGui.sameLine();
        helpMarker("Clamp even if min==max==0.0f. Otherwise drag functions don't clamp.");
        IkGui.checkboxFlags("LOGARITHMIC", dragSliderFlags, SliderFlags.LOGARITHMIC);
        IkGui.sameLine();
        helpMarker("Enable logarithmic editing (more precision for small values).");
        IkGui.checkboxFlags("NO_ROUND_TO_FORMAT", dragSliderFlags, SliderFlags.NO_ROUND_TO_FORMAT);
        IkGui.sameLine();
        helpMarker(
                "Disable rounding underlying value to match precision of the format string (e.g."
                        + " %.3f values are rounded to those 3 digits).");
        IkGui.checkboxFlags("NO_INPUT", dragSliderFlags, SliderFlags.NO_INPUT);
        IkGui.sameLine();
        helpMarker(
                "Disable Ctrl+Click or Enter key allowing to input text directly into the widget.");
        IkGui.checkboxFlags("NO_SPEED_TWEAKS", dragSliderFlags, SliderFlags.NO_SPEED_TWEAKS);
        IkGui.sameLine();
        helpMarker(
                "Disable keyboard modifiers altering tweak speed. Useful if you want to alter"
                        + " tweak speed yourself based on your own logic.");
        IkGui.checkboxFlags("WRAP_AROUND", dragSliderFlags, SliderFlags.WRAP_AROUND);
        IkGui.sameLine();
        helpMarker(
                "Enable wrapping around from max to min and from min to max (only supported by"
                        + " drag functions)");
        IkGui.checkboxFlags("COLOR_MARKERS", dragSliderFlags, SliderFlags.COLOR_MARKERS);
        final int flags = dragSliderFlags.get();

        // Drags
        IkGui.text(String.format("Underlying float value: %f", flagsDragFloat[0]));
        IkGui.dragFloat("DragFloat (0 -> 1)", flagsDragFloat, 0.005f, 0.0f, 1.0f, "%.3f", flags);
        IkGui.dragFloat(
                "DragFloat (0 -> +inf)",
                flagsDragFloat,
                0.005f,
                0.0f,
                Float.MAX_VALUE,
                "%.3f",
                flags);
        IkGui.dragFloat(
                "DragFloat (-inf -> 1)",
                flagsDragFloat,
                0.005f,
                -Float.MAX_VALUE,
                1.0f,
                "%.3f",
                flags);
        IkGui.dragFloat(
                "DragFloat (-inf -> +inf)",
                flagsDragFloat,
                0.005f,
                -Float.MAX_VALUE,
                Float.MAX_VALUE,
                "%.3f",
                flags);
        IkGui.dragInt("DragInt (0 -> 100)", flagsDragInt, 0.5f, 0, 100, "%d", flags);
        // Multi-component item, mostly here to document the effect of SliderFlags.COLOR_MARKERS
        IkGui.dragFloat4("DragFloat4 (0 -> 1)", flagsDragFloat4, 0.005f, 0.0f, 1.0f, "%.3f", flags);

        // Sliders
        final int flagsForSliders = flags & ~SliderFlags.WRAP_AROUND;
        IkGui.text(String.format("Underlying float value: %f", flagsSliderFloat[0]));
        IkGui.sliderFloat(
                "SliderFloat (0 -> 1)", flagsSliderFloat, 0.0f, 1.0f, "%.3f", flagsForSliders);
        IkGui.sliderInt("SliderInt (0 -> 100)", flagsSliderInt, 0, 100, "%d", flagsForSliders);
        // Multi-component item, mostly here to document the effect of SliderFlags.COLOR_MARKERS.
        // Sliders don't support wrapping around, so that flag is left out here too.
        IkGui.sliderFloat4(
                "SliderFloat4 (0 -> 1)", flagsSliderFloat4, 0.0f, 1.0f, "%.3f", flagsForSliders);

        IkGui.treePop();
    }

    private static final float[] multiFloats = {0.10f, 0.20f, 0.30f, 0.44f};
    private static final int[] multiInts = {1, 5, 100, 255};
    private static final IkInt multiFlags = new IkInt(SliderFlags.NONE);
    private static final float[] rangeBegin = {10};
    private static final float[] rangeEnd = {90};
    private static final int[] rangeBeginInt = {100};
    private static final int[] rangeEndInt = {1000};

    private static final float[] dragDropColor1 = {1.0f, 0.0f, 0.2f};
    private static final float[] dragDropColor2 = {0.4f, 0.7f, 0.0f, 0.5f};
    private static final float[] dragDropColor4 = {1.0f, 0.0f, 0.2f, 1.0f};
    private static final IkInt dragDropMode = new IkInt(0);
    private static final String[] dragDropNames = {
        "Bobby", "Beatrice", "Betty", "Brianna", "Barry", "Bernard", "Bibi", "Blaine", "Bryn"
    };
    private static final String[] dragDropItemNames = {
        "Item One", "Item Two", "Item Three", "Item Four", "Item Five"
    };

    private static void showWidgetsDragAndDrop() {
        if (!IkGui.treeNode("Drag and Drop")) {
            return;
        }

        if (IkGui.treeNode("Drag and drop in standard widgets")) {
            // Color edit widgets automatically act as drag sources and targets. They use the
            // standard payload types IkGui.PAYLOAD_TYPE_COLOR_3F and PAYLOAD_TYPE_COLOR_4F, so your
            // own widgets can use colors in their drag and drop interactions.
            helpMarker("You can drag from the color squares.");
            IkGui.colorEdit3("color 1", dragDropColor1);
            IkGui.colorEdit4("color 2", dragDropColor2);
            IkGui.treePop();
        }

        if (IkGui.treeNode("Drag and drop to copy/swap items")) {
            final int modeCopy = 0;
            final int modeMove = 1;
            final int modeSwap = 2;
            IkGui.radioButton("Copy", dragDropMode, modeCopy);
            IkGui.sameLine();
            IkGui.radioButton("Move", dragDropMode, modeMove);
            IkGui.sameLine();
            IkGui.radioButton("Swap", dragDropMode, modeSwap);
            final int mode = dragDropMode.get();
            final String payloadType = "DND_DEMO_CELL";
            for (int n = 0; n < dragDropNames.length; ++n) {
                IkGui.pushID(n);
                if (n % 3 != 0) {
                    IkGui.sameLine();
                }
                IkGui.button(dragDropNames[n], 60, 60);

                // Our buttons are both drag sources and drag targets here
                if (IkGui.beginDragDropSource()) {
                    // Set the payload to carry the index of our item (could be anything)
                    IkGui.setDragDropPayload(payloadType, n);

                    // Display a preview (could be anything, e.g. a filename and small preview
                    // of an image when dragging an image)
                    final String action =
                            switch (mode) {
                                case modeCopy -> "Copy";
                                case modeMove -> "Move";
                                default -> "Swap";
                            };
                    IkGui.text(action + " " + dragDropNames[n]);
                    IkGui.endDragDropSource();
                }
                if (IkGui.beginDragDropTarget()) {
                    final Integer payloadN = IkGui.acceptDragDropPayload(payloadType);
                    if (payloadN != null) {
                        if (mode == modeCopy) {
                            dragDropNames[n] = dragDropNames[payloadN];
                        } else if (mode == modeMove) {
                            dragDropNames[n] = dragDropNames[payloadN];
                            dragDropNames[payloadN] = "";
                        } else {
                            final String tmp = dragDropNames[n];
                            dragDropNames[n] = dragDropNames[payloadN];
                            dragDropNames[payloadN] = tmp;
                        }
                    }
                    IkGui.endDragDropTarget();
                }
                IkGui.popID();
            }
            IkGui.treePop();
        }

        if (IkGui.treeNode("Drag to reorder items (simple)")) {
            // The same item may be submitted twice for a frame while reordering
            IkGui.pushItemFlag(ItemFlags.ALLOW_DUPLICATE_ID, true);

            helpMarker(
                    "We don't use the drag and drop api at all here! Instead we query when the item"
                            + " is held but not hovered, and order items accordingly.");
            for (int n = 0; n < dragDropItemNames.length; ++n) {
                final String item = dragDropItemNames[n];
                IkGui.selectable(item);

                if (IkGui.isItemActive() && !IkGui.isItemHovered()) {
                    final int next =
                            n + (IkGui.getMouseDragDelta(MouseButton.LEFT).y < 0.0f ? -1 : 1);
                    if (next >= 0 && next < dragDropItemNames.length) {
                        dragDropItemNames[n] = dragDropItemNames[next];
                        dragDropItemNames[next] = item;
                        IkGui.resetMouseDragDelta();
                    }
                }
            }

            IkGui.popItemFlag();
            IkGui.treePop();
        }

        if (IkGui.treeNode("Tooltip at target location")) {
            for (int n = 0; n < 2; ++n) {
                // Drop targets
                IkGui.button(n == 1 ? "drop here##1" : "drop here##0");
                if (IkGui.beginDragDropTarget()) {
                    final int dropTargetFlags =
                            DragDropFlags.ACCEPT_BEFORE_DELIVERY
                                    | DragDropFlags.ACCEPT_NO_PREVIEW_TOOLTIP;
                    if (IkGui.acceptDragDropPayload(IkGui.PAYLOAD_TYPE_COLOR_4F, dropTargetFlags)
                            != null) {
                        IkGui.setMouseCursor(MouseCursor.NOT_ALLOWED);
                        IkGui.setTooltip("Cannot drop here!");
                    }
                    IkGui.endDragDropTarget();
                }

                // Drop source
                if (n == 0) {
                    IkGui.colorButton("drag me", dragDropColor4);
                }
            }
            IkGui.treePop();
        }

        IkGui.treePop();
    }

    /** A small "(?)" marker that shows a tooltip when hovered. */
    static void helpMarker(String description) {
        IkGui.textDisabled("(?)");
        IkGui.setItemTooltip(description);
    }

    private static final float[] pickerColor = {
        114.0f / 255.0f, 144.0f / 255.0f, 154.0f / 255.0f, 200.0f / 255.0f
    };
    private static final float[] pickerBackupColor = new float[4];
    private static final float[] pickerReferenceColor = {1.0f, 0.0f, 1.0f, 0.5f};
    private static final float[] pickerColorHSV = {0.23f, 1.0f, 1.0f, 1.0f};
    private static final IkInt pickerBaseFlags = new IkInt(ColorEditFlags.NONE);
    private static final IkInt pickerFlags = new IkInt(ColorEditFlags.ALPHA_BAR);
    private static final IkInt pickerMode = new IkInt(0);
    private static final IkInt pickerDisplayMode = new IkInt(0);
    private static final IkBoolean pickerNoBorder = new IkBoolean(false);
    private static final IkBoolean pickerUseReference = new IkBoolean(false);
    private static float[][] pickerPalette;

    private static void showWidgetsColorAndPickers() {
        if (!IkGui.treeNode("Color/Picker Widgets")) {
            return;
        }
        final float[] color = pickerColor;

        IkGui.separatorText("Options");
        IkGui.checkboxFlags("NO_ALPHA", pickerBaseFlags, ColorEditFlags.NO_ALPHA);
        IkGui.checkboxFlags("ALPHA_OPAQUE", pickerBaseFlags, ColorEditFlags.ALPHA_OPAQUE);
        IkGui.checkboxFlags(
                "ALPHA_NO_BACKGROUND", pickerBaseFlags, ColorEditFlags.ALPHA_NO_BACKGROUND);
        IkGui.checkboxFlags(
                "ALPHA_PREVIEW_HALF", pickerBaseFlags, ColorEditFlags.ALPHA_PREVIEW_HALF);
        IkGui.checkboxFlags("NO_OPTIONS", pickerBaseFlags, ColorEditFlags.NO_OPTIONS);
        IkGui.sameLine();
        helpMarker("Right-click on the individual color widget to show options.");
        IkGui.checkboxFlags("NO_DRAG_DROP", pickerBaseFlags, ColorEditFlags.NO_DRAG_DROP);
        IkGui.checkboxFlags("NO_COLOR_MARKERS", pickerBaseFlags, ColorEditFlags.NO_COLOR_MARKERS);
        IkGui.checkboxFlags("HDR", pickerBaseFlags, ColorEditFlags.HDR);
        IkGui.sameLine();
        helpMarker("Currently all this does is to lift the 0..1 limits on dragging widgets.");
        final int baseFlags = pickerBaseFlags.get();

        IkGui.separatorText("Inline color editor");
        IkGui.text("Color widget:");
        IkGui.sameLine();
        helpMarker(
                "Click on the color square to open a color picker.\n"
                        + "Ctrl+Click on individual component to input value.\n");
        IkGui.colorEdit3("MyColor##1", color, baseFlags);

        IkGui.text("Color widget HSV with Alpha:");
        IkGui.colorEdit4("MyColor##2", color, ColorEditFlags.DISPLAY_HSV | baseFlags);

        IkGui.text("Color widget with Float Display:");
        IkGui.colorEdit4("MyColor##2f", color, ColorEditFlags.FLOAT | baseFlags);

        IkGui.text("Color button with Picker:");
        IkGui.sameLine();
        helpMarker(
                "With the NO_INPUTS flag you can hide all the slider/text inputs.\n"
                        + "With the NO_LABEL flag you can pass a non-empty label which will only "
                        + "be used for the tooltip and picker popup.");
        IkGui.colorEdit4(
                "MyColor##3",
                color,
                ColorEditFlags.NO_INPUTS | ColorEditFlags.NO_LABEL | baseFlags);

        IkGui.text("Color button with Custom Picker Popup:");
        // Generate a default palette. The palette will persist and can be edited.
        if (pickerPalette == null) {
            pickerPalette = new float[32][];
            for (int n = 0; n < pickerPalette.length; ++n) {
                final float[] entry = new float[4];
                IkGui.colorConvertHSVtoRGB(new float[] {n / 31.0f, 0.8f, 0.8f}, entry);
                entry[3] = 1.0f;
                pickerPalette[n] = entry;
            }
        }
        boolean openPopup = IkGui.colorButton("MyColor##3b", color, baseFlags);
        IkGui.sameLine(0, IkGui.getContext().style.variable.itemInnerSpacing.x);
        openPopup |= IkGui.button("Palette");
        if (openPopup) {
            IkGui.openPopup("mypicker");
            System.arraycopy(color, 0, pickerBackupColor, 0, 4);
        }
        if (IkGui.beginPopup("mypicker")) {
            IkGui.text("MY CUSTOM COLOR PICKER WITH AN AMAZING PALETTE!");
            IkGui.separator();
            IkGui.colorPicker4(
                    "##picker",
                    color,
                    baseFlags | ColorEditFlags.NO_SIDE_PREVIEW | ColorEditFlags.NO_SMALL_PREVIEW);
            IkGui.sameLine();

            IkGui.beginGroup(); // Lock X position
            IkGui.text("Current");
            IkGui.colorButton(
                    "##current",
                    color,
                    ColorEditFlags.NO_PICKER | ColorEditFlags.ALPHA_PREVIEW_HALF,
                    60,
                    40);
            IkGui.text("Previous");
            if (IkGui.colorButton(
                    "##previous",
                    pickerBackupColor,
                    ColorEditFlags.NO_PICKER | ColorEditFlags.ALPHA_PREVIEW_HALF,
                    60,
                    40)) {
                System.arraycopy(pickerBackupColor, 0, color, 0, 4);
            }
            IkGui.separator();
            IkGui.text("Palette");
            final int paletteButtonFlags =
                    ColorEditFlags.NO_ALPHA | ColorEditFlags.NO_PICKER | ColorEditFlags.NO_TOOLTIP;
            for (int n = 0; n < pickerPalette.length; ++n) {
                IkGui.pushID(n);
                if ((n % 8) != 0) {
                    IkGui.sameLine(0.0f, IkGui.getContext().style.variable.itemSpacing.y);
                }
                if (IkGui.colorButton("##palette", pickerPalette[n], paletteButtonFlags, 20, 20)) {
                    // Preserve alpha!
                    System.arraycopy(pickerPalette[n], 0, color, 0, 3);
                }

                // Allow user to drop colors into each palette entry. Note that colorButton() is
                // already a drag source by default, unless specifying the NO_DRAG_DROP flag.
                if (IkGui.beginDragDropTarget()) {
                    final float[] payload3 =
                            IkGui.acceptDragDropPayload(IkGui.PAYLOAD_TYPE_COLOR_3F);
                    if (payload3 != null) {
                        System.arraycopy(payload3, 0, pickerPalette[n], 0, 3);
                    }
                    final float[] payload4 =
                            IkGui.acceptDragDropPayload(IkGui.PAYLOAD_TYPE_COLOR_4F);
                    if (payload4 != null) {
                        System.arraycopy(payload4, 0, pickerPalette[n], 0, 4);
                    }
                    IkGui.endDragDropTarget();
                }

                IkGui.popID();
            }
            IkGui.endGroup();
            IkGui.endPopup();
        }

        IkGui.text("Color button only:");
        IkGui.checkbox("NO_BORDER", pickerNoBorder);
        IkGui.colorButton(
                "MyColor##3c",
                color,
                baseFlags | (pickerNoBorder.get() ? ColorEditFlags.NO_BORDER : 0),
                80,
                80);

        IkGui.separatorText("Color picker");
        IkGui.pushID("Color picker");
        IkGui.checkboxFlags("NO_ALPHA", pickerFlags, ColorEditFlags.NO_ALPHA);
        IkGui.checkboxFlags("ALPHA_BAR", pickerFlags, ColorEditFlags.ALPHA_BAR);
        IkGui.checkboxFlags("NO_SIDE_PREVIEW", pickerFlags, ColorEditFlags.NO_SIDE_PREVIEW);
        if ((pickerFlags.get() & ColorEditFlags.NO_SIDE_PREVIEW) != 0) {
            IkGui.sameLine();
            IkGui.checkbox("With Ref Color", pickerUseReference);
            if (pickerUseReference.get()) {
                IkGui.sameLine();
                IkGui.colorEdit4(
                        "##RefColor", pickerReferenceColor, ColorEditFlags.NO_INPUTS | baseFlags);
            }
        }
        IkGui.checkboxFlags("PICKER_NO_ROTATE", pickerFlags, ColorEditFlags.PICKER_NO_ROTATE);

        IkGui.combo(
                "Picker Mode",
                pickerMode,
                new String[] {"Auto/Current", "PICKER_HUE_BAR", "PICKER_HUE_WHEEL"});
        IkGui.sameLine();
        helpMarker(
                "When not specified explicitly, user can right-click the picker to change mode.");

        IkGui.combo(
                "Display Mode",
                pickerDisplayMode,
                new String[] {
                    "Auto/Current", "NO_INPUTS", "DISPLAY_RGB", "DISPLAY_HSV", "DISPLAY_HEX"
                });
        IkGui.sameLine();
        helpMarker(
                "colorEdit defaults to displaying RGB inputs if you don't specify a display mode, "
                        + "but the user can change it with a right-click on those inputs.\n\n"
                        + "colorPicker defaults to displaying RGB+HSV+Hex if you don't specify a "
                        + "display mode.\n\nYou can change the defaults using "
                        + "io.configColorEditFlags.");

        int flags = baseFlags | pickerFlags.get();
        switch (pickerMode.get()) {
            case 1 -> flags |= ColorEditFlags.PICKER_HUE_BAR;
            case 2 -> flags |= ColorEditFlags.PICKER_HUE_WHEEL;
            default -> {}
        }
        switch (pickerDisplayMode.get()) {
                // Disable all RGB/HSV/Hex displays
            case 1 -> flags |= ColorEditFlags.NO_INPUTS;
                // Override display mode
            case 2 -> flags |= ColorEditFlags.DISPLAY_RGB;
            case 3 -> flags |= ColorEditFlags.DISPLAY_HSV;
            case 4 -> flags |= ColorEditFlags.DISPLAY_HEX;
            default -> {}
        }
        IkGui.colorPicker4(
                "MyColor##4", color, flags, pickerUseReference.get() ? pickerReferenceColor : null);

        IkGui.text("Set defaults in code:");
        IkGui.sameLine();
        helpMarker(
                "io.configColorEditFlags is designed to allow you to set boot-time defaults.\n"
                        + "We don't have push/pop functions because you can force options on a "
                        + "per-widget basis if needed, and the user can change non-forced ones "
                        + "with the options menu.\nWe don't have a getter to avoid encouraging "
                        + "you to persistently save values that aren't forward-compatible.");
        if (IkGui.button("Overwrite default: Uint8 + HSV + Hue Bar")) {
            IkGui.getIO().configColorEditFlags =
                    ColorEditFlags.UINT8
                            | ColorEditFlags.DISPLAY_HSV
                            | ColorEditFlags.PICKER_HUE_BAR
                            | ColorEditFlags.INPUT_RGB;
        }
        if (IkGui.button("Overwrite default: Float + HDR + Hue Wheel")) {
            IkGui.getIO().configColorEditFlags =
                    ColorEditFlags.FLOAT
                            | ColorEditFlags.HDR
                            | ColorEditFlags.DISPLAY_RGB
                            | ColorEditFlags.PICKER_HUE_WHEEL
                            | ColorEditFlags.INPUT_RGB;
        }

        // Always display a small version of both types of pickers (that's in order to make it
        // more visible in the demo to people who are skimming quickly through it)
        IkGui.text("Both types:");
        final float width =
                (IkGui.getContentRegionAvailableX()
                                - IkGui.getContext().style.variable.itemSpacing.y)
                        * 0.40f;
        final int smallFlags =
                ColorEditFlags.NO_SIDE_PREVIEW | ColorEditFlags.NO_INPUTS | ColorEditFlags.NO_ALPHA;
        IkGui.setNextItemWidth(width);
        IkGui.colorPicker3("##MyColor##5", color, ColorEditFlags.PICKER_HUE_BAR | smallFlags);
        IkGui.sameLine();
        IkGui.setNextItemWidth(width);
        IkGui.colorPicker3("##MyColor##6", color, ColorEditFlags.PICKER_HUE_WHEEL | smallFlags);
        IkGui.popID();

        // HSV encoded support (to avoid RGB<>HSV round trips and singularities when S==0 or V==0)
        IkGui.spacing();
        IkGui.text("HSV encoded colors");
        IkGui.sameLine();
        helpMarker(
                "By default, colors are given to colorEdit and colorPicker in RGB, but INPUT_HSV "
                        + "allows you to store colors as HSV and pass them to colorEdit and "
                        + "colorPicker as HSV. This comes with the added benefit that you can "
                        + "manipulate hue values with the picker even when saturation or value are "
                        + "zero.");
        IkGui.text("Color widget with InputHSV:");
        IkGui.colorEdit4(
                "HSV shown as RGB##1",
                pickerColorHSV,
                ColorEditFlags.DISPLAY_RGB | ColorEditFlags.INPUT_HSV | ColorEditFlags.FLOAT);
        IkGui.colorEdit4(
                "HSV shown as HSV##1",
                pickerColorHSV,
                ColorEditFlags.DISPLAY_HSV | ColorEditFlags.INPUT_HSV | ColorEditFlags.FLOAT);
        IkGui.dragFloat4("Raw HSV values", pickerColorHSV, 0.01f, 0.0f, 1.0f);

        IkGui.treePop();
    }

    private static final IkBoolean plotAnimate = new IkBoolean(true);
    private static final float[] plotArray = {0.6f, 0.1f, 1.0f, 0.5f, 0.92f, 0.1f, 0.2f};
    private static final float[] plotValues = new float[90];
    private static int plotValuesOffset = 0;
    private static double plotRefreshTime = 0;
    private static float plotPhase = 0.0f;
    private static final IkInt plotFunctionType = new IkInt(0);
    private static final int[] plotDisplayCount = {70};

    private static void showWidgetsPlotting() {
        // Plot/Graph widgets are not very good. Consider using a third-party library such as
        // ImPlot: https://github.com/epezent/implot
        if (!IkGui.treeNode("Plotting")) {
            return;
        }
        IkGui.text("Need better plotting and graphing? Consider using ImPlot:");
        IkGui.textLinkOpenURL("https://github.com/epezent/implot");
        IkGui.separator();

        IkGui.checkbox("Animate", plotAnimate);

        // Plot as lines and plot as histogram
        IkGui.plotLines("Frame Times", plotArray, plotArray.length);
        IkGui.plotHistogram(
                "Histogram", plotArray, plotArray.length, 0, null, 0.0f, 1.0f, 0, 80.0f);

        // Fill an array of contiguous float values to plot, at a fixed 60 Hz rate for the demo.
        // The time is in milliseconds.
        final long time = IkGui.getTime();
        if (!plotAnimate.get() || plotRefreshTime == 0) {
            plotRefreshTime = time;
        }
        while (plotRefreshTime < time) {
            plotValues[plotValuesOffset] = (float) Math.cos(plotPhase);
            plotValuesOffset = (plotValuesOffset + 1) % plotValues.length;
            plotPhase += 0.10f * plotValuesOffset;
            plotRefreshTime += 1000.0 / 60.0;
        }

        // Plots can display overlay texts (in this example, we will display an average value)
        float average = 0.0f;
        for (float value : plotValues) {
            average += value;
        }
        average /= plotValues.length;
        IkGui.plotLines(
                "Lines",
                plotValues,
                plotValues.length,
                plotValuesOffset,
                String.format("avg %f", average),
                -1.0f,
                1.0f,
                0,
                80.0f);

        // Use functions to generate output
        IkGui.separatorText("Functions");
        IkGui.setNextItemWidth(IkGui.getFontSize() * 8);
        IkGui.combo("func", plotFunctionType, new String[] {"Sin", "Saw"});
        IkGui.sameLine();
        IkGui.sliderInt("Sample count", plotDisplayCount, 1, 400);
        final IntFunction<Float> function =
                plotFunctionType.get() == 0
                        ? i -> (float) Math.sin(i * 0.1f)
                        : i -> (i & 1) != 0 ? 1.0f : -1.0f;
        IkGui.plotLines("Lines##2", function, plotDisplayCount[0], 0, null, -1.0f, 1.0f, 0, 80);
        IkGui.plotHistogram(
                "Histogram##2", function, plotDisplayCount[0], 0, null, -1.0f, 1.0f, 0, 80);

        IkGui.treePop();
    }

    private static float progressAccumulator = 0.0f;
    private static float progressDirection = 1.0f;

    private static void showWidgetsProgressBars() {
        if (!IkGui.treeNode("Progress Bars")) {
            return;
        }
        // Animate a simple progress bar
        progressAccumulator += progressDirection * 0.4f * (IkGui.getIO().deltaTime / 1000.0f);
        if (progressAccumulator >= 1.1f) {
            progressAccumulator = 1.1f;
            progressDirection *= -1.0f;
        }
        if (progressAccumulator <= -0.1f) {
            progressAccumulator = -0.1f;
            progressDirection *= -1.0f;
        }
        final float progress = MathUtil.clamp(progressAccumulator, 0.0f, 1.0f);
        final float innerSpacing = IkGui.getContext().style.variable.itemInnerSpacing.x;

        // Typically we would use (-1.0f, 0.0f) or (-Float.MIN_VALUE, 0.0f) to use all available
        // width, or (width, 0.0f) for a specified width. (0.0f, 0.0f) uses the item width.
        IkGui.progressBar(progress, 0.0f, 0.0f);
        IkGui.sameLine(0.0f, innerSpacing);
        IkGui.text("Progress Bar");

        IkGui.progressBar(
                progress, 0.0f, 0.0f, String.format("%d/%d", (int) (progress * 1753), 1753));

        // Pass an animated negative value, e.g. -1.0f * time in seconds is the recommended value.
        // Adjust the factor if you want to adjust the animation speed.
        IkGui.progressBar(-1.0f * (IkGui.getTime() / 1000.0f), 0.0f, 0.0f, "Searching..");
        IkGui.sameLine(0.0f, innerSpacing);
        IkGui.text("Indeterminate");

        IkGui.treePop();
    }

    private static void showWidgetsMultiComponents() {
        if (!IkGui.treeNode("Multi-component Widgets")) {
            return;
        }
        IkGui.checkboxFlags("COLOR_MARKERS", multiFlags, SliderFlags.COLOR_MARKERS);
        final int flags = multiFlags.get();

        IkGui.separatorText("2-wide");
        IkGui.inputFloat2("input float2", multiFloats);
        IkGui.inputInt2("input int2", multiInts);
        IkGui.dragFloat2("drag float2", multiFloats, 0.01f, 0.0f, 1.0f, null, flags);
        IkGui.dragInt2("drag int2", multiInts, 1, 0, 255, null, flags);
        IkGui.sliderFloat2("slider float2", multiFloats, 0.0f, 1.0f, null, flags);
        IkGui.sliderInt2("slider int2", multiInts, 0, 255, null, flags);

        IkGui.separatorText("3-wide");
        IkGui.inputFloat3("input float3", multiFloats);
        IkGui.inputInt3("input int3", multiInts);
        IkGui.dragFloat3("drag float3", multiFloats, 0.01f, 0.0f, 1.0f, null, flags);
        IkGui.dragInt3("drag int3", multiInts, 1, 0, 255, null, flags);
        IkGui.sliderFloat3("slider float3", multiFloats, 0.0f, 1.0f, null, flags);
        IkGui.sliderInt3("slider int3", multiInts, 0, 255, null, flags);

        IkGui.separatorText("4-wide");
        IkGui.inputFloat4("input float4", multiFloats);
        IkGui.inputInt4("input int4", multiInts);
        IkGui.dragFloat4("drag float4", multiFloats, 0.01f, 0.0f, 1.0f, null, flags);
        IkGui.dragInt4("drag int4", multiInts, 1, 0, 255, null, flags);
        IkGui.sliderFloat4("slider float4", multiFloats, 0.0f, 1.0f, null, flags);
        IkGui.sliderInt4("slider int4", multiInts, 0, 255, null, flags);

        IkGui.separatorText("Ranges");
        IkGui.dragFloatRange2(
                "range float",
                rangeBegin,
                rangeEnd,
                0.25f,
                0.0f,
                100.0f,
                "Min: %.1f %%",
                "Max: %.1f %%",
                SliderFlags.ALWAYS_CLAMP);
        IkGui.dragIntRange2(
                "range int",
                rangeBeginInt,
                rangeEndInt,
                5,
                0,
                1000,
                "Min: %d units",
                "Max: %d units");
        IkGui.dragIntRange2(
                "range int (no bounds)",
                rangeBeginInt,
                rangeEndInt,
                5,
                0,
                0,
                "Min: %d units",
                "Max: %d units");

        IkGui.treePop();
    }

    private static final int[] verticalInt = {0};
    private static final float[] verticalValues = {0.0f, 0.60f, 0.35f, 0.9f, 0.70f, 0.20f, 0.0f};
    private static final float[] verticalValues2 = {0.20f, 0.80f, 0.40f, 0.25f};

    private static void showWidgetsVerticalSliders() {
        if (!IkGui.treeNode("Vertical Sliders")) {
            return;
        }
        final float spacing = 4;
        IkGui.pushStyleVarFloat2(StyleVariable.ITEM_SPACING, spacing, spacing);

        IkGui.vSliderInt("##int", 18, 160, verticalInt, 0, 5);
        IkGui.sameLine();

        IkGui.pushID("set1");
        for (int i = 0; i < verticalValues.length; ++i) {
            if (i > 0) {
                IkGui.sameLine();
            }
            IkGui.pushID(i);
            final float hue = i / 7.0f;
            IkGui.pushStyleColor(ColorType.FRAME_BACKGROUND, Color.hsv(hue, 0.5f, 0.5f));
            IkGui.pushStyleColor(ColorType.FRAME_BACKGROUND_HOVERED, Color.hsv(hue, 0.6f, 0.5f));
            IkGui.pushStyleColor(ColorType.FRAME_BACKGROUND_ACTIVE, Color.hsv(hue, 0.7f, 0.5f));
            IkGui.pushStyleColor(ColorType.SLIDER_GRAB, Color.hsv(hue, 0.9f, 0.9f));
            final float[] value = {verticalValues[i]};
            IkGui.vSliderFloat("##v", 18, 160, value, 0.0f, 1.0f, "");
            verticalValues[i] = value[0];
            if (IkGui.isItemActive() || IkGui.isItemHovered()) {
                IkGui.setTooltip(String.format("%.3f", verticalValues[i]));
            }
            IkGui.popStyleColor(4);
            IkGui.popID();
        }
        IkGui.popID();

        IkGui.sameLine();
        IkGui.pushID("set2");
        final int rows = 3;
        final float smallHeight = (int) ((160.0f - (rows - 1) * spacing) / rows);
        for (int nx = 0; nx < verticalValues2.length; ++nx) {
            if (nx > 0) {
                IkGui.sameLine();
            }
            IkGui.beginGroup();
            for (int ny = 0; ny < rows; ++ny) {
                IkGui.pushID(nx * rows + ny);
                final float[] value = {verticalValues2[nx]};
                IkGui.vSliderFloat("##v", 18, smallHeight, value, 0.0f, 1.0f, "");
                verticalValues2[nx] = value[0];
                if (IkGui.isItemActive() || IkGui.isItemHovered()) {
                    IkGui.setTooltip(String.format("%.3f", verticalValues2[nx]));
                }
                IkGui.popID();
            }
            IkGui.endGroup();
        }
        IkGui.popID();

        IkGui.sameLine();
        IkGui.pushID("set3");
        for (int i = 0; i < 4; ++i) {
            if (i > 0) {
                IkGui.sameLine();
            }
            IkGui.pushID(i);
            IkGui.pushStyleVarFloat(StyleVariable.GRAB_MIN_SIZE, 40);
            final float[] value = {verticalValues[i]};
            IkGui.vSliderFloat("##v", 40, 160, value, 0.0f, 1.0f, "%.2f\nsec");
            verticalValues[i] = value[0];
            IkGui.popStyleVar();
            IkGui.popID();
        }
        IkGui.popID();
        IkGui.popStyleVar();
        IkGui.treePop();
    }

    private static int listBoxSelected = 0;
    private static final IkBoolean listBoxHighlight = new IkBoolean(false);

    private static void showWidgetsListBoxes() {
        if (!IkGui.treeNode("List Boxes")) {
            return;
        }
        // beginListBox() is essentially a thin wrapper around beginChild()/endChild() using the
        // ChildFlags.FRAME_STYLE flag for stylistic changes, plus displaying a label. Note that
        // endChild() always needs to be called, unlike endListBox().
        int highlighted = -1;
        IkGui.checkbox("Highlight hovered item in second listbox", listBoxHighlight);

        if (IkGui.beginListBox("listbox 1")) {
            for (int n = 0; n < COMBO_ITEMS.length; ++n) {
                final boolean isSelected = listBoxSelected == n;
                if (IkGui.selectable(COMBO_ITEMS[n], isSelected)) {
                    listBoxSelected = n;
                }
                if (listBoxHighlight.get() && IkGui.isItemHovered()) {
                    highlighted = n;
                }
                // Set the initial focus when opening the list box (scrolling + keyboard
                // navigation focus)
                if (isSelected) {
                    IkGui.setItemDefaultFocus();
                }
            }
            IkGui.endListBox();
        }
        IkGui.sameLine();
        helpMarker("Here we are sharing selection state between both boxes.");

        // Custom size: use all width, 5 items tall
        IkGui.text("Full-width:");
        if (IkGui.beginListBox(
                "##listbox 2", -Float.MIN_VALUE, 5 * IkGui.getTextLineHeightWithSpacing())) {
            for (int n = 0; n < COMBO_ITEMS.length; ++n) {
                final boolean isSelected = listBoxSelected == n;
                final int flags =
                        highlighted == n ? SelectableFlags.HIGHLIGHT : SelectableFlags.NONE;
                if (IkGui.selectable(COMBO_ITEMS[n], isSelected, flags)) {
                    listBoxSelected = n;
                }

                // Set the initial focus when opening the list box (scrolling + keyboard
                // navigation focus)
                if (isSelected) {
                    IkGui.setItemDefaultFocus();
                }
            }
            IkGui.endListBox();
        }
        IkGui.treePop();
    }

    private static void showWidgetsText() {
        if (!IkGui.treeNode("Text")) {
            return;
        }

        if (IkGui.treeNode("Colorful Text")) {
            // Using shortcut. You can use pushStyleColor()/popStyleColor() for more flexibility.
            IkGui.textColored(1.0f, 0.0f, 1.0f, 1.0f, "Pink");
            IkGui.textColored(1.0f, 1.0f, 0.0f, 1.0f, "Yellow");
            IkGui.textDisabled("Disabled");
            IkGui.sameLine();
            helpMarker("The TEXT_DISABLED color is stored in the style.");
            IkGui.treePop();
        }

        if (IkGui.treeNode("Font Size")) {
            // Font sizes are whole pixels, and there is no global font scale
            final int baseSize = IkGui.getFontSize();
            IkGui.text(String.format("FontSize = %d", baseSize));

            IkGui.separatorText("");
            IkGui.sliderInt("custom_size", textCustomSize, 10, 100);
            IkGui.text("IkGui.pushFontSize(customSize);");
            IkGui.pushFontSize(textCustomSize[0]);
            IkGui.text(
                    String.format("FontSize = %d (== %d)", IkGui.getFontSize(), textCustomSize[0]));
            IkGui.popFont();

            IkGui.separatorText("");
            IkGui.sliderFloat("custom_scale", textCustomScale, 0.5f, 4.0f, "%.2f");
            IkGui.text("IkGui.pushFontSize(Math.round(baseSize * customScale));");
            IkGui.pushFontSize(Math.round(baseSize * textCustomScale[0]));
            IkGui.text(
                    String.format(
                            "FontSize = %d (== baseSize * %.2f)",
                            IkGui.getFontSize(), textCustomScale[0]));
            IkGui.popFont();

            IkGui.separatorText("");
            for (float scaling = 0.5f; scaling <= 4.0f; scaling += 0.5f) {
                IkGui.pushFontSize(Math.round(baseSize * scaling));
                IkGui.text(
                        String.format(
                                "FontSize = %d (== baseSize * %.2f)",
                                IkGui.getFontSize(), scaling));
                IkGui.popFont();
            }

            IkGui.treePop();
        }

        if (IkGui.treeNode("Word Wrapping")) {
            // Using shortcut. You can use pushTextWrapPos()/popTextWrapPos() for more flexibility.
            IkGui.textWrapped(
                    "This text should automatically wrap on the edge of the window. The current"
                            + " implementation for text wrapping follows simple rules suitable for"
                            + " English and possibly other languages.");
            IkGui.spacing();

            wrapWidth = sliderValue("Wrap width", wrapWidth, 10, -20, 600);

            final DrawList drawList = IkGui.getWindowDrawList();
            for (int n = 0; n < 2; n++) {
                IkGui.text(String.format("Test paragraph %d:", n));
                final Vector2f position = IkGui.getCursorScreenPos();
                final float markerX = position.x + wrapWidth;
                final float lineHeight = IkGui.getTextLineHeight();
                IkGui.pushTextWrapPos(IkGui.getCursorPos().x + wrapWidth);
                if (n == 0) {
                    IkGui.text(
                            String.format(
                                    "The lazy dog is a good dog. This paragraph should fit within"
                                            + " %.0f pixels. Testing a 1 character word. The quick"
                                            + " brown fox jumps over the lazy dog.",
                                    wrapWidth));
                } else {
                    IkGui.text(
                            "aaaaaaaa bbbbbbbb, c cccccccc,dddddddd. d eeeeeeee   ffffffff."
                                    + " gggggggg!hhhhhhhh");
                }

                // Draw actual text bounding box, following by marker of our expected limit
                // (should not overlap!)
                final Vector2f rectMin = new Vector2f();
                final Vector2f rectMax = new Vector2f();
                IkGui.getItemRectMin(rectMin);
                IkGui.getItemRectMax(rectMax);
                drawList.addRect(
                        rectMin.x, rectMin.y, rectMax.x, rectMax.y, Color.rgba(255, 255, 0, 255));
                drawList.addRectFilled(
                        markerX,
                        position.y,
                        markerX + 10,
                        position.y + lineHeight,
                        Color.rgba(255, 0, 255, 255));
                IkGui.popTextWrapPos();
            }
            IkGui.treePop();
        }

        if (IkGui.treeNode("UTF-8 Text")) {
            // Java strings are Unicode, so any characters can be used. Characters missing from
            // the fonts are kept by inputText(), so you can safely copy & paste them into another
            // application.
            IkGui.textWrapped(
                    "CJK text will only appear if a loaded font or fallback font has the"
                            + " characters. Load one with io.fonts.loadFont() and add it with"
                            + " setFontFallbacks().");
            IkGui.text("Hiragana: \u304b\u304d\u304f\u3051\u3053 (kakikukeko)");
            IkGui.text("Kanjis: \u65e5\u672c\u8a9e (nihongo)");
            IkGui.inputText("UTF-8 input", utf8Input);
            IkGui.text("Accented: \u00e0\u00e9\u00ee\u00f5\u00fc \u00f1 \u00e7 \u00df");
            IkGui.treePop();
        }

        IkGui.treePop();
    }

    private static final IkString textInputMultiline =
            new IkString(
                    """
                    /*
                     The Pentium F00F bug, shorthand for F0 0F C7 C8,
                     the hexadecimal encoding of one offending instruction,
                     more formally, the invalid operand with locked CMPXCHG8B
                     instruction bug, is a design flaw in the majority of
                     Intel Pentium, Pentium MMX, and Pentium OverDrive
                     processors (all in the P5 microarchitecture).
                    */

                    label:
                    \tlock cmpxchg8b eax
                    """,
                    1024 * 16);
    private static final IkString utf8Input = new IkString("\u65e5\u672c\u8a9e", 32);
    private static final int[] textCustomSize = {16};
    private static final float[] textCustomScale = {1.0f};
    private static final IkInt textInputMultilineFlags = new IkInt(InputTextFlags.ALLOW_TAB_INPUT);
    private static final IkString textInputDefault = new IkString(32);
    private static final IkString textInputDecimal = new IkString(32);
    private static final IkString textInputHexadecimal = new IkString(32);
    private static final IkString textInputUppercase = new IkString(32);
    private static final IkString textInputNoBlank = new IkString(32);
    private static final IkString textInputCustom = new IkString(32);
    private static final IkString textInputCasingSwap = new IkString(32);
    private static final IkString textInputPassword = new IkString("password123", 64);
    private static final IkString textInputCompletion = new IkString(64);
    private static final IkString textInputHistory = new IkString(64);
    private static final IkString textInputEdit = new IkString(64);
    private static final IkInt textInputEditCount = new IkInt(0);
    private static final IkString textInputElide =
            new IkString("/path/to/some/folder/with/long/filename.cpp", 128);
    private static final IkString textInputEscapeClears = new IkString("Escape clears me", 64);
    private static final IkString textInputEnterReturnsTrue = new IkString(64);
    private static final IkString textInputReadOnly =
            new IkString("This text can be selected and copied, but not edited", 64);
    private static final IkString textInputResizable = new IkString("Resizable string", 4);
    private static final IkInt textInputResizableFlags = new IkInt(InputTextFlags.NONE);
    private static final IkInt textInputElideFlags = new IkInt(InputTextFlags.ELIDE_LEFT);
    private static final IkInt textInputMiscFlags = new IkInt(InputTextFlags.ESCAPE_CLEARS_ALL);
    private static final IkString textInputMisc = new IkString(16);
    private static String textInputEnterResult = "";

    static {
        textInputResizable.inputData.isResizable = true;
    }

    /** Only allow the letters 'i', 'k', 'g', 'u' to be typed. */
    private static final GuiInputTextCallback TEXT_INPUT_FILTER_IKGUI_LETTERS =
            new GuiInputTextCallback() {
                @Override
                public void accept(GuiInputTextCallbackData data) {
                    final int c = data.getEventChar();
                    if (c != 'i' && c != 'k' && c != 'g' && c != 'u') {
                        data.setEventChar(0);
                    }
                }
            };

    /** Insert ".." on completion, and replace the text on history. */
    private static final GuiInputTextCallback TEXT_INPUT_COMPLETION_HISTORY =
            new GuiInputTextCallback() {
                @Override
                public void accept(GuiInputTextCallbackData data) {
                    if (data.getEventFlag() == InputTextFlags.CALLBACK_COMPLETION) {
                        data.insertChars(data.getCursorPos(), "..");
                    } else if (data.getEventFlag() == InputTextFlags.CALLBACK_HISTORY) {
                        data.deleteChars(0, data.getBufTextLen());
                        data.insertChars(
                                0,
                                data.getEventKey() == Key.ARROW_UP
                                        ? "Pressed Up!"
                                        : "Pressed Down!");
                        data.selectAll();
                    }
                }
            };

    /** Toggle the casing of the first character on every edit, and count the edits. */
    private static final GuiInputTextCallback TEXT_INPUT_EDIT_COUNTER =
            new GuiInputTextCallback() {
                @Override
                public void accept(GuiInputTextCallbackData data) {
                    final StringBuilder buffer = data.getBuffer();
                    if (!buffer.isEmpty()) {
                        final char c = buffer.charAt(0);
                        if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) {
                            buffer.setCharAt(0, (char) (c ^ 32));
                        }
                    }
                    data.setBufDirty(true);

                    // Increment a counter
                    textInputEditCount.set(textInputEditCount.get() + 1);
                }
            };

    /** Lowercase becomes uppercase, and uppercase becomes lowercase. */
    private static final GuiInputTextCallback TEXT_INPUT_FILTER_CASING_SWAP =
            new GuiInputTextCallback() {
                @Override
                public void accept(GuiInputTextCallbackData data) {
                    final int c = data.getEventChar();
                    if (c >= 'a' && c <= 'z') {
                        data.setEventChar(c - ('a' - 'A'));
                    } else if (c >= 'A' && c <= 'Z') {
                        data.setEventChar(c + ('a' - 'A'));
                    }
                }
            };

    private static void showWidgetsTextInput() {
        if (!IkGui.treeNode("Text Input")) {
            return;
        }

        if (IkGui.treeNode("Multi-line Text Input")) {
            helpMarker(
                    "IkString grows as needed when inputData.isResizable is set, see 'Resize"
                            + " Callback' below.");
            IkGui.checkboxFlags("READ_ONLY", textInputMultilineFlags, InputTextFlags.READ_ONLY);
            IkGui.checkboxFlags("WORD_WRAP", textInputMultilineFlags, InputTextFlags.WORD_WRAP);
            IkGui.sameLine();
            helpMarker("Feature is currently in Beta.");
            IkGui.checkboxFlags(
                    "ALLOW_TAB_INPUT", textInputMultilineFlags, InputTextFlags.ALLOW_TAB_INPUT);
            IkGui.sameLine();
            helpMarker(
                    "When ALLOW_TAB_INPUT is set, passing through the widget with Tabbing doesn't"
                            + " automatically activate it, in order to also cycling through"
                            + " subsequent widgets.");
            IkGui.checkboxFlags(
                    "CTRL_ENTER_FOR_NEW_LINE",
                    textInputMultilineFlags,
                    InputTextFlags.CTRL_ENTER_FOR_NEW_LINE);
            IkGui.inputTextMultiline(
                    "##source",
                    textInputMultiline,
                    -Float.MIN_VALUE,
                    IkGui.getTextLineHeight() * 16,
                    textInputMultilineFlags.get());
            IkGui.treePop();
        }

        if (IkGui.treeNode("Filtered Text Input")) {
            IkGui.inputText("default", textInputDefault);
            IkGui.inputText("decimal", textInputDecimal, InputTextFlags.CHARS_DECIMAL);
            IkGui.inputText(
                    "hexadecimal",
                    textInputHexadecimal,
                    InputTextFlags.CHARS_HEXADECIMAL | InputTextFlags.CHARS_UPPERCASE);
            IkGui.inputText("uppercase", textInputUppercase, InputTextFlags.CHARS_UPPERCASE);
            IkGui.inputText("no blank", textInputNoBlank, InputTextFlags.CHARS_NO_BLANK);
            // Use a character filter callback to replace characters
            IkGui.inputText(
                    "casing swap",
                    textInputCasingSwap,
                    InputTextFlags.CALLBACK_CHAR_FILTER,
                    TEXT_INPUT_FILTER_CASING_SWAP);
            // Use a character filter callback to disable some characters
            IkGui.inputText(
                    "\"ikgui\"",
                    textInputCustom,
                    InputTextFlags.CALLBACK_CHAR_FILTER,
                    TEXT_INPUT_FILTER_IKGUI_LETTERS);
            IkGui.treePop();
        }

        if (IkGui.treeNode("Password Input")) {
            IkGui.inputText("password", textInputPassword, InputTextFlags.PASSWORD);
            IkGui.sameLine();
            helpMarker(
                    "Display all characters as '*'.\nDisable clipboard cut and copy.\nDisable"
                            + " logging.\n");
            IkGui.inputTextWithHint(
                    "password (w/ hint)", "<password>", textInputPassword, InputTextFlags.PASSWORD);
            IkGui.inputText("password (clear)", textInputPassword);
            IkGui.treePop();
        }

        if (IkGui.treeNode("Completion, History, Edit Callbacks")) {
            IkGui.inputText(
                    "Completion",
                    textInputCompletion,
                    InputTextFlags.CALLBACK_COMPLETION,
                    TEXT_INPUT_COMPLETION_HISTORY);
            IkGui.sameLine();
            helpMarker(
                    "Here we append \"..\" each time Tab is pressed. See 'Examples>Console' for a"
                            + " more meaningful demonstration of using this callback.");
            IkGui.inputText(
                    "History",
                    textInputHistory,
                    InputTextFlags.CALLBACK_HISTORY,
                    TEXT_INPUT_COMPLETION_HISTORY);
            IkGui.sameLine();
            helpMarker(
                    "Here we replace and select text each time Up/Down are pressed. See"
                            + " 'Examples>Console' for a more meaningful demonstration of using"
                            + " this callback.");
            IkGui.inputText(
                    "Edit", textInputEdit, InputTextFlags.CALLBACK_EDIT, TEXT_INPUT_EDIT_COUNTER);
            IkGui.sameLine();
            helpMarker(
                    "Here we toggle the casing of the first character on every edit + count"
                            + " edits.");
            IkGui.sameLine();
            IkGui.text("(" + textInputEditCount.get() + ")");
            IkGui.treePop();
        }

        if (IkGui.treeNode("Resize Callback")) {
            // Dear ImGui uses a resize callback to wire its text input to custom string types.
            // IkString handles that itself, growing as needed when it is resizable.
            helpMarker(
                    "Strings with inputData.isResizable set grow as needed, so the text isn't"
                            + " limited by the starting capacity.");
            IkGui.checkboxFlags(
                    "InputTextFlags.WORD_WRAP", textInputResizableFlags, InputTextFlags.WORD_WRAP);
            IkGui.inputTextMultiline(
                    "##MyStr",
                    textInputResizable,
                    -Float.MIN_VALUE,
                    IkGui.getTextLineHeight() * 16,
                    textInputResizableFlags.get());
            IkGui.text(
                    "Size: "
                            + textInputResizable.getLength()
                            + "\nCapacity: "
                            + textInputResizable.getBufferSize());
            IkGui.treePop();
        }

        if (IkGui.treeNode("Eliding, Alignment")) {
            IkGui.checkboxFlags(
                    "InputTextFlags.ELIDE_LEFT", textInputElideFlags, InputTextFlags.ELIDE_LEFT);
            IkGui.inputText("Path", textInputElide, textInputElideFlags.get());
            IkGui.treePop();
        }

        if (IkGui.treeNode("Miscellaneous")) {
            IkGui.checkboxFlags(
                    "InputTextFlags.ESCAPE_CLEARS_ALL",
                    textInputMiscFlags,
                    InputTextFlags.ESCAPE_CLEARS_ALL);
            IkGui.checkboxFlags(
                    "InputTextFlags.READ_ONLY", textInputMiscFlags, InputTextFlags.READ_ONLY);
            IkGui.checkboxFlags(
                    "InputTextFlags.NO_UNDO_REDO", textInputMiscFlags, InputTextFlags.NO_UNDO_REDO);
            IkGui.inputText("Hello", textInputMisc, textInputMiscFlags.get());
            IkGui.separator();
            IkGui.inputText(
                    "Escape clears all", textInputEscapeClears, InputTextFlags.ESCAPE_CLEARS_ALL);
            if (IkGui.inputText(
                    "Enter returns true",
                    textInputEnterReturnsTrue,
                    InputTextFlags.ENTER_RETURNS_TRUE)) {
                textInputEnterResult = textInputEnterReturnsTrue.get();
            }
            IkGui.text("Last validated: " + textInputEnterResult);
            IkGui.inputText("Read-only", textInputReadOnly, InputTextFlags.READ_ONLY);
            final IkIO io = IkGui.getIO();
            io.configInputTextCursorBlink =
                    toggle("io.configInputTextCursorBlink", io.configInputTextCursorBlink);
            io.configInputTextEnterKeepActive =
                    toggle("io.configInputTextEnterKeepActive", io.configInputTextEnterKeepActive);
            IkGui.treePop();
        }

        IkGui.treePop();
    }

    private static void showWidgetsTrees() {
        if (!IkGui.treeNode("Tree Nodes")) {
            return;
        }

        if (IkGui.treeNode("Basic Trees")) {
            for (int i = 0; i < 5; i++) {
                // Use setNextItemOpen() to set the default state of a node to be open. We could
                // also use treeNodeEx() with the DEFAULT_OPEN flag to achieve the same thing!
                if (i == 0) {
                    IkGui.setNextItemOpen(true, Condition.ONCE);
                }

                // Here we use pushID() to generate a unique base ID, so the "" used as the tree
                // node ID won't conflict
                IkGui.pushID(i);
                if (IkGui.treeNode("", String.format("Child %d", i))) {
                    IkGui.text("blah blah");
                    IkGui.sameLine();
                    IkGui.smallButton("button");
                    IkGui.treePop();
                }
                IkGui.popID();
            }
            IkGui.treePop();
        }

        if (IkGui.treeNode("Hierarchy Lines")) {
            helpMarker("Default option for DRAW_LINES_XXX is stored in style.treeLinesFlags");
            IkGui.checkboxFlags(
                    "TreeNodeFlags.DRAW_LINES_NONE",
                    treeLinesBaseFlags,
                    TreeNodeFlags.DRAW_LINES_NONE);
            IkGui.checkboxFlags(
                    "TreeNodeFlags.DRAW_LINES_FULL",
                    treeLinesBaseFlags,
                    TreeNodeFlags.DRAW_LINES_FULL);
            IkGui.checkboxFlags(
                    "TreeNodeFlags.DRAW_LINES_TO_NODES",
                    treeLinesBaseFlags,
                    TreeNodeFlags.DRAW_LINES_TO_NODES);

            final int flags = treeLinesBaseFlags.get();
            if (IkGui.treeNodeEx("Parent", flags)) {
                if (IkGui.treeNodeEx("Child 1", flags)) {
                    IkGui.button("Button for Child 1");
                    IkGui.treePop();
                }
                if (IkGui.treeNodeEx("Child 2", flags)) {
                    IkGui.button("Button for Child 2");
                    IkGui.treePop();
                }
                IkGui.text("Remaining contents");
                IkGui.text("Remaining contents");
                IkGui.treePop();
            }
            IkGui.treePop();
        }

        if (IkGui.treeNode("Clipping Large Trees")) {
            IkGui.textWrapped(
                    "- Using ListClipper with trees is less easy than on arrays or grids.\n"
                            + "- Refer to 'Demo->Examples->Property Editor' in Dear ImGui for an"
                            + " example of how to do that.");
            IkGui.treePop();
        }

        if (IkGui.treeNode("Selectable Nodes")) {
            helpMarker(
                    "Manually implemented selectable nodes.\n"
                            + "Click to select, Ctrl+Click to toggle, click on arrows or"
                            + " double-click to open.\n\n"
                            + "You may also use the multi-select API (see 'Demo->Widgets->Selection"
                            + " State & Multi-Select') for more advanced multi-selection"
                            + " features.");

            // treeSelectionMask is a simple representation of what may be user-side selection
            // state. We record which node was clicked and then apply the selection at the end of
            // the loop. This is a manual and simplified reimplementation of multi-selection, which
            // beginMultiSelect() implements better, but which is not trivial to wire for trees.
            int nodeClicked = -1;
            for (int i = 0; i < 6; i++) {
                // Disable the default "open on single-click" behavior and set the selected flag
                // according to our selection. To alter the selection we use
                // isItemClicked() && !isItemToggledOpen(), so clicking on an arrow doesn't alter
                // the selection.
                int flags =
                        TreeNodeFlags.OPEN_ON_ARROW
                                | TreeNodeFlags.OPEN_ON_DOUBLE_CLICK
                                | TreeNodeFlags.SPAN_AVAIL_WIDTH;
                if ((treeSelectionMask & (1 << i)) != 0) {
                    flags |= TreeNodeFlags.SELECTED;
                }
                final boolean isOpen =
                        IkGui.treeNodeEx("Node" + i, flags, String.format("Selectable Node %d", i));
                if (IkGui.isItemClicked() && !IkGui.isItemToggledOpen()) {
                    nodeClicked = i;
                }
                if (isOpen) {
                    IkGui.bulletText("<Node contents here>");
                    IkGui.treePop();
                }
            }
            if (nodeClicked != -1) {
                // Update the selection outside of the tree loop, to avoid visual inconsistencies
                // during the clicking frame
                if (IkGui.getIO().keyCtrl) {
                    // Ctrl+Click to toggle
                    treeSelectionMask ^= 1 << nodeClicked;
                } else {
                    // Click to single-select
                    treeSelectionMask = 1 << nodeClicked;
                }
            }
            IkGui.treePop();
        }

        if (IkGui.treeNode("Advanced")) {
            IkGui.checkboxFlags(
                    "TreeNodeFlags.OPEN_ON_ARROW", treeBaseFlags, TreeNodeFlags.OPEN_ON_ARROW);
            IkGui.checkboxFlags(
                    "TreeNodeFlags.OPEN_ON_DOUBLE_CLICK",
                    treeBaseFlags,
                    TreeNodeFlags.OPEN_ON_DOUBLE_CLICK);
            IkGui.checkboxFlags(
                    "TreeNodeFlags.SPAN_AVAIL_WIDTH",
                    treeBaseFlags,
                    TreeNodeFlags.SPAN_AVAIL_WIDTH);
            IkGui.sameLine();
            helpMarker(
                    "Extend hit area to all available width instead of allowing more items to be"
                            + " laid out after the node.");
            IkGui.checkboxFlags(
                    "TreeNodeFlags.SPAN_FULL_WIDTH", treeBaseFlags, TreeNodeFlags.SPAN_FULL_WIDTH);
            IkGui.checkboxFlags(
                    "TreeNodeFlags.SPAN_LABEL_WIDTH",
                    treeBaseFlags,
                    TreeNodeFlags.SPAN_LABEL_WIDTH);
            IkGui.sameLine();
            helpMarker("Reduce hit area to the text label and a bit of margin.");
            IkGui.checkboxFlags(
                    "TreeNodeFlags.SPAN_ALL_COLUMNS",
                    treeBaseFlags,
                    TreeNodeFlags.SPAN_ALL_COLUMNS);
            IkGui.sameLine();
            helpMarker("For use in Tables only.");
            IkGui.checkboxFlags(
                    "TreeNodeFlags.ALLOW_OVERLAP", treeBaseFlags, TreeNodeFlags.ALLOW_OVERLAP);
            IkGui.checkboxFlags("TreeNodeFlags.FRAMED", treeBaseFlags, TreeNodeFlags.FRAMED);
            IkGui.sameLine();
            helpMarker("Draw frame with background (e.g. for collapsingHeader())");
            IkGui.checkboxFlags(
                    "TreeNodeFlags.FRAME_PADDING", treeBaseFlags, TreeNodeFlags.FRAME_PADDING);
            IkGui.checkboxFlags(
                    "TreeNodeFlags.NAV_LEFT_JUMPS_TO_PARENT",
                    treeBaseFlags,
                    TreeNodeFlags.NAV_LEFT_JUMPS_TO_PARENT);

            helpMarker("Default option for DRAW_LINES_XXX is stored in style.treeLinesFlags");
            IkGui.checkboxFlags(
                    "TreeNodeFlags.DRAW_LINES_NONE", treeBaseFlags, TreeNodeFlags.DRAW_LINES_NONE);
            IkGui.checkboxFlags(
                    "TreeNodeFlags.DRAW_LINES_FULL", treeBaseFlags, TreeNodeFlags.DRAW_LINES_FULL);
            IkGui.checkboxFlags(
                    "TreeNodeFlags.DRAW_LINES_TO_NODES",
                    treeBaseFlags,
                    TreeNodeFlags.DRAW_LINES_TO_NODES);

            IkGui.checkbox("Align label with current X position", treeAlignLabel);
            IkGui.checkbox("Make Tree Nodes as drag & drop sources", treeDragAndDrop);
            if (treeAlignLabel.get()) {
                IkGui.unindent(IkGui.getTreeNodeToLabelSpacing());
            }

            for (int i = 0; i < 6; i++) {
                int nodeFlags = treeBaseFlags.get();
                if (i < 3) {
                    // Items 0..2 are tree nodes
                    final boolean isOpen =
                            IkGui.treeNodeEx(
                                    "Node" + i, nodeFlags, String.format("Selectable Node %d", i));
                    treeDemoDragSource();
                    if (i == 2 && (treeBaseFlags.get() & TreeNodeFlags.SPAN_LABEL_WIDTH) != 0) {
                        // Item 2 has an additional inline button to help demonstrate
                        // SPAN_LABEL_WIDTH
                        IkGui.sameLine();
                        IkGui.smallButton("button");
                    }
                    if (isOpen) {
                        IkGui.bulletText("Blah blah\nBlah Blah");
                        IkGui.sameLine();
                        IkGui.smallButton("Button");
                        IkGui.treePop();
                    }
                } else {
                    // Items 3..5 are tree leaves. The only reason we use treeNode at all is to
                    // allow selection of the leaf. Otherwise we can use bulletText() or advance
                    // the cursor by getTreeNodeToLabelSpacing() and call text().
                    nodeFlags |= TreeNodeFlags.LEAF | TreeNodeFlags.NO_TREE_PUSH_ON_OPEN;
                    IkGui.treeNodeEx("Leaf" + i, nodeFlags, String.format("Selectable Leaf %d", i));
                    treeDemoDragSource();
                }
            }
            if (treeAlignLabel.get()) {
                IkGui.indent(IkGui.getTreeNodeToLabelSpacing());
            }
            IkGui.treePop();
        }
        IkGui.treePop();
    }

    /** Make the last tree node a drag and drop source, if that is turned on. */
    private static void treeDemoDragSource() {
        if (treeDragAndDrop.get() && IkGui.beginDragDropSource()) {
            IkGui.setDragDropPayload("MY_TREENODE_PAYLOAD_TYPE", (Object) null);
            IkGui.text("This is a drag and drop source");
            IkGui.endDragDropSource();
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Layout & Scrolling
    // ---------------------------------------------------------------------------------------------

    private static final IkBoolean disableMouseWheel = new IkBoolean(false);
    private static final IkBoolean childDisableMenu = new IkBoolean(false);
    private static final int[] childDrawLines = {3};
    private static final int[] childMaxHeightInLines = {10};
    private static final int[] childOffsetX = {0};
    private static final IkBoolean childOverrideBackground = new IkBoolean(true);
    private static final IkInt childAdvancedFlags =
            new IkInt(ChildFlags.BORDERS | ChildFlags.RESIZE_X | ChildFlags.RESIZE_Y);
    private static final IkBoolean layoutCheck1 = new IkBoolean(false);
    private static final IkBoolean layoutCheck2 = new IkBoolean(false);
    private static final IkBoolean layoutCheck3 = new IkBoolean(false);
    private static final IkBoolean layoutCheck4 = new IkBoolean(false);
    private static final float[] layoutF0 = {1.0f};
    private static final float[] layoutF1 = {2.0f};
    private static final float[] layoutF2 = {3.0f};
    private static final String[] LAYOUT_ITEMS = {"AAAA", "BBBB", "CCCC", "DDDD"};
    private static final IkInt layoutComboItem = new IkInt(-1);
    private static final IkInt[] layoutListSelection = {
        new IkInt(0), new IkInt(1), new IkInt(2), new IkInt(3)
    };
    private static final float[] GROUP_VALUES = {0.5f, 0.20f, 0.80f, 0.60f, 0.25f};
    private static final float[] widthsValue = {0.0f};
    private static final IkBoolean widthsShowIndented = new IkBoolean(true);
    private static final int[] scrollTrackItem = {50};
    private static final IkBoolean scrollTrack = new IkBoolean(true);
    private static final IkBoolean scrollDecorations = new IkBoolean(false);
    private static final float[] scrollToOffset = {0.0f};
    private static final float[] scrollToPosition = {200.0f};
    private static final int[] scrollLines = {7};
    private static final IkBoolean showHorizontalContentsWindow = new IkBoolean(false);
    private static final IkBoolean horizontalScrollbar = new IkBoolean(true);
    private static final IkBoolean horizontalButton = new IkBoolean(true);
    private static final IkBoolean horizontalTreeNodes = new IkBoolean(true);
    private static final IkBoolean horizontalTextWrapped = new IkBoolean(false);
    private static final IkBoolean horizontalTable = new IkBoolean(true);
    private static final IkBoolean horizontalTabBar = new IkBoolean(true);
    private static final IkBoolean horizontalChild = new IkBoolean(false);
    private static final IkBoolean horizontalExplicitSize = new IkBoolean(false);
    private static final float[] horizontalContentsWidth = {300.0f};
    private static final float[] textClipSize = {100.0f, 100.0f};
    private static final float[] textClipOffset = {30.0f, 30.0f};
    private static final IkBoolean overlapEnable = new IkBoolean(true);

    private static void showLayoutSection() {
        if (!IkGui.collapsingHeader("Layout & Scrolling")) {
            return;
        }

        showLayoutChildWindows();
        showLayoutWidgetsWidth();
        showLayoutHorizontal();
        showLayoutGroups();
        showLayoutTextBaseline();
        showLayoutScrolling();
        showLayoutTextClipping();
        showLayoutOverlapMode();
    }

    /**
     * One row of the widgets width demo.
     *
     * @param title The title, describing the width.
     * @param help The help text.
     * @param width The item width to push.
     * @param id The suffix for the IDs.
     * @param firstLabel The label of the first item.
     */
    private static void widthsRow(
            String title, String help, float width, String id, String firstLabel) {
        IkGui.text(title);
        if (help != null) {
            IkGui.sameLine();
            helpMarker(help);
        }
        IkGui.pushItemWidth(width);
        IkGui.dragFloat(firstLabel, widthsValue);
        if (widthsShowIndented.get()) {
            IkGui.indent();
            IkGui.dragFloat("float (indented)##" + id + "b", widthsValue);
            IkGui.unindent();
        }
        IkGui.popItemWidth();
    }

    private static void showLayoutWidgetsWidth() {
        if (!IkGui.treeNode("Widgets Width")) {
            return;
        }
        IkGui.checkbox("Show indented items", widthsShowIndented);

        // Use setNextItemWidth() to set the width of a single upcoming item. Use
        // pushItemWidth()/popItemWidth() to set the width of a group of items. In real code you'll
        // probably want to choose width values that are proportional to your font size, e.g.
        // using '20.0f * getTextLineHeight()' as width instead of '200.0f', etc.
        widthsRow("setNextItemWidth/pushItemWidth(100)", "Fixed width.", 100, "1", "float##1a");
        widthsRow(
                "setNextItemWidth/pushItemWidth(-100)",
                "Align to right edge minus 100",
                -100,
                "2",
                "float##2a");
        widthsRow(
                "setNextItemWidth/pushItemWidth(getContentRegionAvailable().x * 0.5f)",
                "Half of available width.\n(~ right-cursor_pos)\n(works within a column set)",
                IkGui.getContentRegionAvailable().x * 0.5f,
                "3",
                "float##3a");
        widthsRow(
                "setNextItemWidth/pushItemWidth(-getContentRegionAvailable().x * 0.5f)",
                "Align to right edge minus half",
                -IkGui.getContentRegionAvailable().x * 0.5f,
                "4",
                "float##4a");
        widthsRow(
                "setNextItemWidth/pushItemWidth(-min(getContentRegionAvailable().x * 0.40f,"
                        + " getTextLineHeight() * 12))",
                null,
                -Math.min(
                        IkGui.getTextLineHeight() * 12,
                        IkGui.getContentRegionAvailable().x * 0.40f),
                "5",
                "float##5a");
        // pushItemWidth() surrounds the items, calling setNextItemWidth() before each of them
        // would have the same effect
        widthsRow(
                "setNextItemWidth/pushItemWidth(-Float.MIN_VALUE)",
                "Align to right edge",
                -Float.MIN_VALUE,
                "6",
                "##float6a");
        IkGui.treePop();
    }

    private static void showLayoutChildWindows() {
        if (!IkGui.treeNode("Child windows")) {
            return;
        }

        IkGui.separatorText("Child windows");

        helpMarker(
                "Use child windows to begin into a self-contained independent scrolling/clipping"
                        + " regions within a host window.");
        IkGui.checkbox("Disable Mouse Wheel", disableMouseWheel);
        IkGui.checkbox("Disable Menu", childDisableMenu);

        // Child 1: no border, enable horizontal scrollbar
        {
            int windowFlags = WindowFlags.HORIZONTAL_SCROLLBAR;
            if (disableMouseWheel.get()) {
                windowFlags |= WindowFlags.NO_SCROLL_WITH_MOUSE;
            }
            IkGui.beginChild(
                    "ChildL",
                    IkGui.getContentRegionAvailable().x * 0.5f,
                    260,
                    ChildFlags.NONE,
                    windowFlags);
            for (int i = 0; i < 100; i++) {
                IkGui.text(String.format("%04d: scrollable region", i));
            }
            IkGui.endChild();
        }

        IkGui.sameLine();

        // Child 2: rounded border
        {
            int windowFlags = WindowFlags.NONE;
            if (disableMouseWheel.get()) {
                windowFlags |= WindowFlags.NO_SCROLL_WITH_MOUSE;
            }
            if (!childDisableMenu.get()) {
                windowFlags |= WindowFlags.MENU_BAR;
            }
            IkGui.pushStyleVarFloat(StyleVariable.CHILD_ROUNDING, 5.0f);
            IkGui.beginChild("ChildR", 0, 260, ChildFlags.BORDERS, windowFlags);
            if (!childDisableMenu.get() && IkGui.beginMenuBar()) {
                if (IkGui.beginMenu("Menu")) {
                    showExampleMenuFile();
                    IkGui.endMenu();
                }
                IkGui.endMenuBar();
            }
            if (IkGui.beginTable("split", 2, TableFlags.RESIZABLE | TableFlags.NO_SAVED_SETTINGS)) {
                for (int i = 0; i < 100; i++) {
                    IkGui.tableNextColumn();
                    IkGui.button(String.format("%03d", i), -Float.MIN_VALUE, 0.0f);
                }
                IkGui.endTable();
            }
            IkGui.endChild();
            IkGui.popStyleVar();
        }

        // Child 3: manual-resize
        IkGui.separatorText("Manual-resize");
        {
            helpMarker(
                    "Drag bottom border to resize. Double-click bottom border to auto-fit to"
                            + " vertical contents.");
            final Vector4f frameBackground = IkGui.getStyleColorVec4(ColorType.FRAME_BACKGROUND);
            IkGui.pushStyleColor(
                    ColorType.CHILD_BACKGROUND,
                    frameBackground.x,
                    frameBackground.y,
                    frameBackground.z,
                    frameBackground.w);
            if (IkGui.beginChild(
                    "ResizableChild",
                    -Float.MIN_VALUE,
                    IkGui.getTextLineHeightWithSpacing() * 8,
                    ChildFlags.BORDERS | ChildFlags.RESIZE_Y)) {
                for (int n = 0; n < 10; n++) {
                    IkGui.text(String.format("Line %04d", n));
                }
            }
            IkGui.popStyleColor();
            IkGui.endChild();
        }

        // Child 4: auto-resizing height with a limit
        IkGui.separatorText("Auto-resize with constraints");
        {
            IkGui.setNextItemWidth(IkGui.getFontSize() * 8);
            IkGui.dragInt("Lines Count", childDrawLines, 0.2f);
            IkGui.setNextItemWidth(IkGui.getFontSize() * 8);
            IkGui.dragInt("Max Height (in Lines)", childMaxHeightInLines, 0.2f);

            IkGui.setNextWindowSizeConstraints(
                    0.0f,
                    IkGui.getTextLineHeightWithSpacing() * 1,
                    Float.MAX_VALUE,
                    IkGui.getTextLineHeightWithSpacing() * childMaxHeightInLines[0]);
            if (IkGui.beginChild(
                    "ConstrainedChild",
                    -Float.MIN_VALUE,
                    0.0f,
                    ChildFlags.BORDERS | ChildFlags.AUTO_RESIZE_Y)) {
                for (int n = 0; n < childDrawLines[0]; n++) {
                    IkGui.text(String.format("Line %04d", n));
                }
            }
            IkGui.endChild();
        }

        IkGui.separatorText("Misc/Advanced");

        // Demonstrate a few extra things
        // - Changing ColorType.CHILD_BACKGROUND (which is transparent black in default styles)
        // - Using setCursorPos() to position child window (the child window is an item from the
        //   POV of parent window). You can also call setNextWindowPos() to position the child
        //   window. The parent window will effectively layout from this position.
        // - Using getItemRectMin/Max() to query the "item" state (because the child window is an
        //   item from the POV of the parent window). See 'Demo->Querying Status
        //   (Edited/Active/Hovered etc.)' for details.
        {
            IkGui.setNextItemWidth(IkGui.getFontSize() * 8);
            IkGui.dragInt("Offset X", childOffsetX, 1.0f, -1000, 1000);
            IkGui.checkbox("Override ChildBg color", childOverrideBackground);
            IkGui.checkboxFlags("BORDERS", childAdvancedFlags, ChildFlags.BORDERS);
            IkGui.checkboxFlags(
                    "ALWAYS_USE_WINDOW_PADDING",
                    childAdvancedFlags,
                    ChildFlags.ALWAYS_USE_WINDOW_PADDING);
            IkGui.checkboxFlags("RESIZE_X", childAdvancedFlags, ChildFlags.RESIZE_X);
            IkGui.checkboxFlags("RESIZE_Y", childAdvancedFlags, ChildFlags.RESIZE_Y);
            IkGui.checkboxFlags("FRAME_STYLE", childAdvancedFlags, ChildFlags.FRAME_STYLE);
            IkGui.sameLine();
            helpMarker(
                    "Style the child window like a framed item: use FRAME_BACKGROUND,"
                            + " frameRounding, frameBorderSize, framePadding instead of"
                            + " CHILD_BACKGROUND, childRounding, childBorderSize, windowPadding.");
            final int childFlags = childAdvancedFlags.get();
            if ((childFlags & ChildFlags.FRAME_STYLE) != 0) {
                childOverrideBackground.set(false);
            }

            IkGui.setCursorPosX(IkGui.getCursorPosX() + childOffsetX[0]);
            final boolean overrideBackground = childOverrideBackground.get();
            if (overrideBackground) {
                IkGui.pushStyleColor(ColorType.CHILD_BACKGROUND, Color.rgba(255, 0, 0, 100));
            }
            IkGui.beginChild("Red", 200, 100, childFlags, WindowFlags.NONE);
            if (overrideBackground) {
                IkGui.popStyleColor();
            }

            for (int n = 0; n < 50; n++) {
                IkGui.text(String.format("Some test %d", n));
            }
            IkGui.endChild();
            final boolean childIsHovered = IkGui.isItemHovered();
            final Vector2f childRectMin = IkGui.getItemRectMin();
            final Vector2f childRectMax = IkGui.getItemRectMax();
            IkGui.text(String.format("Hovered: %d", childIsHovered ? 1 : 0));
            IkGui.text(
                    String.format(
                            "Rect of child window is: (%.0f,%.0f) (%.0f,%.0f)",
                            childRectMin.x, childRectMin.y, childRectMax.x, childRectMax.y));
        }

        IkGui.treePop();
    }

    private static void showLayoutHorizontal() {
        if (!IkGui.treeNode("Basic Horizontal Layout")) {
            return;
        }

        IkGui.textWrapped(
                "(Use IkGui.sameLine() to keep adding items to the right of the preceding item)");

        // Text
        IkGui.text("Two items: Hello");
        IkGui.sameLine();
        IkGui.textColored(1, 1, 0, 1, "Sailor");

        // Adjust spacing
        IkGui.text("More spacing: Hello");
        IkGui.sameLine(0, 20);
        IkGui.textColored(1, 1, 0, 1, "Sailor");

        // Button
        IkGui.alignTextToFramePadding();
        IkGui.text("Normal buttons");
        IkGui.sameLine();
        IkGui.button("Banana");
        IkGui.sameLine();
        IkGui.button("Apple");
        IkGui.sameLine();
        IkGui.button("Corniflower");

        // Button
        IkGui.text("Small buttons");
        IkGui.sameLine();
        IkGui.smallButton("Like this one");
        IkGui.sameLine();
        IkGui.text("can fit within a text block.");

        // Aligned to arbitrary position. Easy/cheap column.
        IkGui.text("Aligned");
        IkGui.sameLine(150);
        IkGui.text("x=150");
        IkGui.sameLine(300);
        IkGui.text("x=300");
        IkGui.text("Aligned");
        IkGui.sameLine(150);
        IkGui.smallButton("x=150");
        IkGui.sameLine(300);
        IkGui.smallButton("x=300");

        // Checkbox
        IkGui.checkbox("My", layoutCheck1);
        IkGui.sameLine();
        IkGui.checkbox("Tailor", layoutCheck2);
        IkGui.sameLine();
        IkGui.checkbox("Is", layoutCheck3);
        IkGui.sameLine();
        IkGui.checkbox("Rich", layoutCheck4);

        // Various
        IkGui.pushItemWidth(IkGui.calcTextSize("AAAAAAA").x);
        IkGui.combo("Combo", layoutComboItem, LAYOUT_ITEMS);
        IkGui.sameLine();
        IkGui.sliderFloat("X", layoutF0, 0.0f, 5.0f);
        IkGui.sameLine();
        IkGui.sliderFloat("Y", layoutF1, 0.0f, 5.0f);
        IkGui.sameLine();
        IkGui.sliderFloat("Z", layoutF2, 0.0f, 5.0f);

        IkGui.text("Lists:");
        for (int i = 0; i < 4; i++) {
            if (i > 0) {
                IkGui.sameLine();
            }
            IkGui.pushID(i);
            IkGui.listBox("", layoutListSelection[i], LAYOUT_ITEMS);
            IkGui.popID();
        }
        IkGui.popItemWidth();

        // Dummy
        final float buttonSize = 40;
        IkGui.button("A", buttonSize, buttonSize);
        IkGui.sameLine();
        IkGui.dummy(buttonSize, buttonSize);
        IkGui.sameLine();
        IkGui.button("B", buttonSize, buttonSize);

        // Manually wrapping (we should eventually provide this as an automatic layout feature, but
        // for now you can do it manually)
        IkGui.text("Manual wrapping:");
        final float itemSpacingX = IkGui.getContext().style.variable.itemSpacing.x;
        final int buttonsCount = 20;
        final float windowVisibleX2 =
                IkGui.getCursorScreenPos().x + IkGui.getContentRegionAvailable().x;
        for (int n = 0; n < buttonsCount; n++) {
            IkGui.pushID(n);
            IkGui.button("Box", buttonSize, buttonSize);
            final float lastButtonX2 = IkGui.getItemRectMaxX();
            // Expected position if next button was on same line
            final float nextButtonX2 = lastButtonX2 + itemSpacingX + buttonSize;
            if (n + 1 < buttonsCount && nextButtonX2 < windowVisibleX2) {
                IkGui.sameLine();
            }
            IkGui.popID();
        }

        IkGui.treePop();
    }

    private static void showLayoutGroups() {
        if (!IkGui.treeNode("Groups")) {
            return;
        }

        helpMarker(
                "beginGroup() basically locks the horizontal position for new line. endGroup()"
                        + " bundles the whole group so that you can use \"item\" functions such as"
                        + " isItemHovered()/isItemActive() or sameLine() etc. on the whole group.");
        IkGui.beginGroup();
        {
            IkGui.beginGroup();
            IkGui.button("AAA");
            IkGui.sameLine();
            IkGui.button("BBB");
            IkGui.sameLine();
            IkGui.beginGroup();
            IkGui.button("CCC");
            IkGui.button("DDD");
            IkGui.endGroup();
            IkGui.sameLine();
            IkGui.button("EEE");
            IkGui.endGroup();
            IkGui.setItemTooltip("First group hovered");
        }
        // Capture the group size and create widgets using the same size
        final Vector2f size = new Vector2f();
        IkGui.getItemRectSize(size);
        IkGui.plotHistogram(
                "##values", GROUP_VALUES, GROUP_VALUES.length, 0, null, 0.0f, 1.0f, size.x, size.y);

        final float itemSpacingX = IkGui.getContext().style.variable.itemSpacing.x;
        IkGui.button("ACTION", (size.x - itemSpacingX) * 0.5f, size.y);
        IkGui.sameLine();
        IkGui.button("REACTION", (size.x - itemSpacingX) * 0.5f, size.y);
        IkGui.endGroup();
        IkGui.sameLine();

        IkGui.button("LEVERAGE\nBUZZWORD", size.x, size.y);
        IkGui.sameLine();

        if (IkGui.beginListBox("List", size.x, size.y)) {
            IkGui.selectable("Selected", true);
            IkGui.selectable("Not Selected", false);
            IkGui.endListBox();
        }

        IkGui.treePop();
    }

    private static void showLayoutTextBaseline() {
        if (!IkGui.treeNode("Text Baseline Alignment")) {
            return;
        }

        {
            IkGui.bulletText("Text baseline:");
            IkGui.sameLine();
            helpMarker(
                    "This is testing the vertical alignment that gets applied on text to keep it"
                            + " aligned with widgets. Lines only composed of text or \"small\""
                            + " widgets use less vertical space than lines with framed widgets.");
            IkGui.indent();

            IkGui.text("KO Blahblah");
            IkGui.sameLine();
            IkGui.button("Some framed item");
            IkGui.sameLine();
            helpMarker("Baseline of button will look misaligned with text..");

            // If your line starts with text, call alignTextToFramePadding() to align text to
            // upcoming widgets. (because we don't know what's coming after the text() statement,
            // we need to move the text baseline down by framePadding.y ahead of time)
            IkGui.alignTextToFramePadding();
            IkGui.text("OK Blahblah");
            IkGui.sameLine();
            IkGui.button("Some framed item##2");
            IkGui.sameLine();
            helpMarker(
                    "We call alignTextToFramePadding() to vertically align the text baseline by"
                            + " +framePadding.y");

            // smallButton() uses the same vertical padding as text
            IkGui.button("TEST##1");
            IkGui.sameLine();
            IkGui.text("TEST");
            IkGui.sameLine();
            IkGui.smallButton("TEST##2");

            // If your line starts with text, call alignTextToFramePadding() to align text to
            // upcoming widgets.
            IkGui.alignTextToFramePadding();
            IkGui.text("Text aligned to framed item");
            IkGui.sameLine();
            IkGui.button("Item##1");
            IkGui.sameLine();
            IkGui.text("Item");
            IkGui.sameLine();
            IkGui.smallButton("Item##2");
            IkGui.sameLine();
            IkGui.button("Item##3");

            IkGui.unindent();
        }

        IkGui.spacing();

        {
            IkGui.bulletText("Multi-line text:");
            IkGui.indent();
            IkGui.text("One\nTwo\nThree");
            IkGui.sameLine();
            IkGui.text("Hello\nWorld");
            IkGui.sameLine();
            IkGui.text("Banana");

            IkGui.text("Banana");
            IkGui.sameLine();
            IkGui.text("Hello\nWorld");
            IkGui.sameLine();
            IkGui.text("One\nTwo\nThree");

            IkGui.button("HOP##1");
            IkGui.sameLine();
            IkGui.text("Banana");
            IkGui.sameLine();
            IkGui.text("Hello\nWorld");
            IkGui.sameLine();
            IkGui.text("Banana");

            IkGui.button("HOP##2");
            IkGui.sameLine();
            IkGui.text("Hello\nWorld");
            IkGui.sameLine();
            IkGui.text("Banana");
            IkGui.unindent();
        }

        IkGui.spacing();

        {
            IkGui.bulletText("Misc items:");
            IkGui.indent();

            // smallButton() sets framePadding to zero. Text baseline is aligned to match baseline
            // of previous button.
            IkGui.button("80x80", 80, 80);
            IkGui.sameLine();
            IkGui.button("50x50", 50, 50);
            IkGui.sameLine();
            IkGui.button("Button()");
            IkGui.sameLine();
            IkGui.smallButton("SmallButton()");

            // Tree (here the node appears after a button and has odd intent, so we use
            // DRAW_LINES_NONE to disable hierarchy outline)
            final float spacing = IkGui.getContext().style.variable.itemInnerSpacing.x;
            IkGui.button("Button##1"); // Will make line higher
            IkGui.sameLine(0.0f, spacing);
            if (IkGui.treeNodeEx("Node##1", TreeNodeFlags.DRAW_LINES_NONE)) {
                // Placeholder tree data
                for (int i = 0; i < 6; i++) {
                    IkGui.bulletText(String.format("Item %d..", i));
                }
                IkGui.treePop();
            }

            final float padding = (int) (IkGui.getFontSize() * 1.20f); // Large padding
            IkGui.pushStyleVarY(StyleVariable.FRAME_PADDING, padding);
            IkGui.button("Button##2");
            IkGui.popStyleVar();
            IkGui.sameLine(0.0f, spacing);
            if (IkGui.treeNodeEx("Node##2", TreeNodeFlags.DRAW_LINES_NONE)) {
                IkGui.treePop();
            }

            // Vertically align text node a bit lower so it'll be vertically centered with upcoming
            // widget. Otherwise you can use smallButton() (smaller fit).
            IkGui.alignTextToFramePadding();

            // Common mistake to avoid: if we want to sameLine() after treeNode we need to do it
            // before we add other contents "inside" the node.
            final boolean nodeOpen = IkGui.treeNode("Node##3");
            IkGui.sameLine(0.0f, spacing);
            IkGui.button("Button##3");
            if (nodeOpen) {
                // Placeholder tree data
                for (int i = 0; i < 6; i++) {
                    IkGui.bulletText(String.format("Item %d..", i));
                }
                IkGui.treePop();
            }

            // Bullet
            IkGui.button("Button##4");
            IkGui.sameLine(0.0f, spacing);
            IkGui.bulletText("Bullet text");

            IkGui.alignTextToFramePadding();
            IkGui.bulletText("Node");
            IkGui.sameLine(0.0f, spacing);
            IkGui.button("Button##5");
            IkGui.unindent();
        }

        IkGui.treePop();
    }

    private static void showLayoutScrolling() {
        if (!IkGui.treeNode("Scrolling")) {
            return;
        }
        final StyleVariables style = IkGui.getStyle().variable;

        // Vertical scroll functions
        helpMarker(
                "Use setScrollHereY() or setScrollFromPosY() to scroll to a given vertical"
                        + " position.");
        IkGui.checkbox("Decoration", scrollDecorations);

        IkGui.pushItemWidth(IkGui.getTextLineHeight() * 10);
        if (IkGui.dragInt("##item", scrollTrackItem, 0.25f, 0, 99, "Item = %d")) {
            scrollTrack.set(true);
        }
        IkGui.sameLine();
        IkGui.checkbox("Track", scrollTrack);

        boolean scrollToOffsetNow =
                IkGui.dragFloat("##off", scrollToOffset, 1.00f, 0, Float.MAX_VALUE, "+%.0f px");
        IkGui.sameLine();
        scrollToOffsetNow |= IkGui.button("Scroll Offset");

        boolean scrollToPositionNow =
                IkGui.dragFloat(
                        "##pos", scrollToPosition, 1.00f, -10, Float.MAX_VALUE, "X/Y = %.0f px");
        IkGui.sameLine();
        scrollToPositionNow |= IkGui.button("Scroll To Pos");
        IkGui.popItemWidth();

        if (scrollToOffsetNow || scrollToPositionNow) {
            scrollTrack.set(false);
        }
        final boolean track = scrollTrack.get();
        final int trackItem = scrollTrackItem[0];

        final float childWidth =
                Math.max(1.0f, (IkGui.getContentRegionAvailable().x - 4 * style.itemSpacing.x) / 5);
        IkGui.pushID("##VerticalScrolling");
        final String[] verticalNames = {"Top", "25%", "Center", "75%", "Bottom"};
        for (int i = 0; i < 5; i++) {
            if (i > 0) {
                IkGui.sameLine();
            }
            IkGui.beginGroup();
            IkGui.textUnformatted(verticalNames[i]);

            final int childFlags =
                    scrollDecorations.get() ? WindowFlags.MENU_BAR : WindowFlags.NONE;
            final boolean childVisible =
                    IkGui.beginChild(
                            IkGui.getID(i), childWidth, 200.0f, ChildFlags.BORDERS, childFlags);
            if (IkGui.beginMenuBar()) {
                IkGui.textUnformatted("abc");
                IkGui.endMenuBar();
            }
            if (scrollToOffsetNow) {
                IkGui.setScrollY(scrollToOffset[0]);
            }
            if (scrollToPositionNow) {
                IkGui.setScrollFromPosY(
                        IkGui.getCursorStartPos().y + scrollToPosition[0], i * 0.25f);
            }
            // Avoid calling setScrollHereY() when running with culled items
            if (childVisible) {
                for (int item = 0; item < 100; item++) {
                    if (track && item == trackItem) {
                        IkGui.textColored(1, 1, 0, 1, String.format("Item %d", item));
                        // 0.0f:top, 0.5f:center, 1.0f:bottom
                        IkGui.setScrollHereY(i * 0.25f);
                    } else {
                        IkGui.text(String.format("Item %d", item));
                    }
                }
            }
            final float scrollY = IkGui.getScrollY();
            final float scrollMaxY = IkGui.getScrollMaxY();
            IkGui.endChild();
            IkGui.text(String.format("%.0f/%.0f", scrollY, scrollMaxY));
            IkGui.endGroup();
        }
        IkGui.popID();

        // Horizontal scroll functions
        IkGui.spacing();
        helpMarker(
                "Use setScrollHereX() or setScrollFromPosX() to scroll to a given horizontal"
                        + " position.\n\n"
                        + "Because the clipping rectangle of most window hides half worth of"
                        + " windowPadding on the left/right, using setScrollFromPosX(+1) will"
                        + " usually result in clipped text whereas the equivalent"
                        + " setScrollFromPosY(+1) wouldn't.");
        IkGui.pushID("##HorizontalScrolling");
        final String[] horizontalNames = {"Left", "25%", "Center", "75%", "Right"};
        for (int i = 0; i < 5; i++) {
            final float childHeight =
                    IkGui.getTextLineHeight() + style.scrollbarSize + style.windowPadding.y * 2.0f;
            final int childFlags =
                    WindowFlags.HORIZONTAL_SCROLLBAR
                            | (scrollDecorations.get()
                                    ? WindowFlags.ALWAYS_VERTICAL_SCROLLBAR
                                    : WindowFlags.NONE);
            final boolean childVisible =
                    IkGui.beginChild(
                            IkGui.getID(i), -100, childHeight, ChildFlags.BORDERS, childFlags);
            if (scrollToOffsetNow) {
                IkGui.setScrollX(scrollToOffset[0]);
            }
            if (scrollToPositionNow) {
                IkGui.setScrollFromPosX(
                        IkGui.getCursorStartPos().x + scrollToPosition[0], i * 0.25f);
            }
            // Avoid calling setScrollHereX() when running with culled items
            if (childVisible) {
                for (int item = 0; item < 100; item++) {
                    if (item > 0) {
                        IkGui.sameLine();
                    }
                    if (track && item == trackItem) {
                        IkGui.textColored(1, 1, 0, 1, String.format("Item %d", item));
                        // 0.0f:left, 0.5f:center, 1.0f:right
                        IkGui.setScrollHereX(i * 0.25f);
                    } else {
                        IkGui.text(String.format("Item %d", item));
                    }
                }
            }
            final float scrollX = IkGui.getScrollX();
            final float scrollMaxX = IkGui.getScrollMaxX();
            IkGui.endChild();
            IkGui.sameLine();
            IkGui.text(String.format("%s\n%.0f/%.0f", horizontalNames[i], scrollX, scrollMaxX));
            IkGui.spacing();
        }
        IkGui.popID();

        // Miscellaneous horizontal scrolling demo
        helpMarker(
                "Horizontal scrolling for a window is enabled via the"
                        + " WindowFlags.HORIZONTAL_SCROLLBAR flag.\n\n"
                        + "You may want to also explicitly specify content width by using"
                        + " setNextWindowContentSize() before begin().");
        IkGui.sliderInt("Lines", scrollLines, 1, 15);
        IkGui.pushStyleVarFloat(StyleVariable.FRAME_ROUNDING, 3.0f);
        IkGui.pushStyleVarFloat2(StyleVariable.FRAME_PADDING, 2.0f, 1.0f);
        IkGui.beginChild(
                "scrolling",
                0,
                IkGui.getFrameHeightWithSpacing() * 7 + 30,
                ChildFlags.BORDERS,
                WindowFlags.HORIZONTAL_SCROLLBAR);
        for (int line = 0; line < scrollLines[0]; line++) {
            // Display random stuff. For the sake of this trivial demo we are using basic button()
            // + sameLine(). If you want to create your own time line for a real application you
            // may be better off manipulating the cursor position yourself, aka using
            // setCursorPos()/setCursorScreenPos() to position the widgets yourself. You may also
            // want to use the lower-level DrawList API.
            final int buttonCount = 10 + ((line & 1) != 0 ? line * 9 : line * 3);
            final float baseWidth = IkGui.getTextLineHeight() * 3;
            for (int n = 0; n < buttonCount; n++) {
                if (n > 0) {
                    IkGui.sameLine();
                }
                IkGui.pushID(n + line * 1000);
                final String label =
                        n % 15 == 0
                                ? "FizzBuzz"
                                : n % 3 == 0 ? "Fizz" : n % 5 == 0 ? "Buzz" : String.valueOf(n);
                final float hue = n * 0.05f;
                IkGui.pushStyleColor(ColorType.BUTTON, Color.hsv(hue, 0.6f, 0.6f));
                IkGui.pushStyleColor(ColorType.BUTTON_HOVERED, Color.hsv(hue, 0.7f, 0.7f));
                IkGui.pushStyleColor(ColorType.BUTTON_ACTIVE, Color.hsv(hue, 0.8f, 0.8f));
                IkGui.button(
                        label, baseWidth + (float) Math.sin(line + n) * baseWidth * 0.5f, 0.0f);
                IkGui.popStyleColor(3);
                IkGui.popID();
            }
        }
        final float scrollX = IkGui.getScrollX();
        final float scrollMaxX = IkGui.getScrollMaxX();
        IkGui.endChild();
        IkGui.popStyleVar(2);
        float scrollXDelta = 0.0f;
        IkGui.smallButton("<<");
        if (IkGui.isItemActive()) {
            scrollXDelta = -IkGui.getIO().deltaTime;
        }
        IkGui.sameLine();
        IkGui.text("Scroll from code");
        IkGui.sameLine();
        IkGui.smallButton(">>");
        if (IkGui.isItemActive()) {
            scrollXDelta = +IkGui.getIO().deltaTime;
        }
        IkGui.sameLine();
        IkGui.text(String.format("%.0f/%.0f", scrollX, scrollMaxX));
        if (scrollXDelta != 0.0f) {
            // Demonstrate a trick: you can use beginChild() to set yourself in the context of
            // another window (here we are already out of the child window)
            IkGui.beginChild("scrolling");
            IkGui.setScrollX(IkGui.getScrollX() + scrollXDelta);
            IkGui.endChild();
        }
        IkGui.spacing();

        IkGui.checkbox("Show Horizontal contents size demo window", showHorizontalContentsWindow);
        if (showHorizontalContentsWindow.get()) {
            showHorizontalContentsSizeWindow();
        }
        IkGui.treePop();
    }

    /** A window for testing how widgets grow the contents when scrolling horizontally. */
    private static void showHorizontalContentsSizeWindow() {
        if (horizontalExplicitSize.get()) {
            IkGui.setNextWindowContentSize(horizontalContentsWidth[0], 0.0f);
        }
        IkGui.begin(
                "Horizontal contents size demo window",
                showHorizontalContentsWindow,
                horizontalScrollbar.get() ? WindowFlags.HORIZONTAL_SCROLLBAR : WindowFlags.NONE);
        IkGui.pushStyleVarFloat2(StyleVariable.ITEM_SPACING, 2, 0);
        IkGui.pushStyleVarFloat2(StyleVariable.FRAME_PADDING, 2, 0);
        helpMarker(
                "Test how different widgets react and impact the work rectangle growing when"
                        + " horizontal scrolling is enabled.\n\n"
                        + "Use 'Metrics->Tools->Show windows rectangles' to visualize rectangles.");
        IkGui.checkbox("H-scrollbar", horizontalScrollbar);
        // Will grow the contents size (unless explicitly overwritten)
        IkGui.checkbox("Button", horizontalButton);
        // Will grow the contents size and display highlight over the full width
        IkGui.checkbox("Tree nodes", horizontalTreeNodes);
        // Will grow and use the contents size
        IkGui.checkbox("Text wrapped", horizontalTextWrapped);
        // Will use the contents size
        IkGui.checkbox("Tables", horizontalTable);
        // Will use the contents size
        IkGui.checkbox("Tab bar", horizontalTabBar);
        // Will grow and use the contents size
        IkGui.checkbox("Child", horizontalChild);
        IkGui.checkbox("Explicit content size", horizontalExplicitSize);
        IkGui.text(
                String.format(
                        "Scroll %.1f/%.1f %.1f/%.1f",
                        IkGui.getScrollX(),
                        IkGui.getScrollMaxX(),
                        IkGui.getScrollY(),
                        IkGui.getScrollMaxY()));
        if (horizontalExplicitSize.get()) {
            IkGui.sameLine();
            IkGui.setNextItemWidth(IkGui.calcTextSize("123456").x);
            IkGui.dragFloat("##csx", horizontalContentsWidth);
            final Vector2f position = IkGui.getCursorScreenPos();
            final DrawList drawList = IkGui.getWindowDrawList();
            drawList.addRectFilled(
                    position.x, position.y, position.x + 10, position.y + 10, Color.WHITE);
            drawList.addRectFilled(
                    position.x + horizontalContentsWidth[0] - 10,
                    position.y,
                    position.x + horizontalContentsWidth[0],
                    position.y + 10,
                    Color.WHITE);
            IkGui.dummy(0, 10);
        }
        IkGui.popStyleVar(2);
        IkGui.separator();
        if (horizontalButton.get()) {
            IkGui.button("this is a 300-wide button", 300, 0);
        }
        if (horizontalTreeNodes.get()) {
            if (IkGui.treeNode("this is a tree node")) {
                if (IkGui.treeNode("another one of those tree node...")) {
                    IkGui.text("Some tree contents");
                    IkGui.treePop();
                }
                IkGui.treePop();
            }
            IkGui.collapsingHeader("CollapsingHeader", new IkBoolean(true));
        }
        if (horizontalTextWrapped.get()) {
            IkGui.textWrapped(
                    "This text should automatically wrap on the edge of the work rectangle.");
        }
        if (horizontalTable.get()) {
            IkGui.text("Tables:");
            if (IkGui.beginTable("table", 4, TableFlags.BORDERS)) {
                for (int n = 0; n < 4; n++) {
                    IkGui.tableNextColumn();
                    IkGui.text(String.format("Width %.2f", IkGui.getContentRegionAvailable().x));
                }
                IkGui.endTable();
            }
        }
        if (horizontalTabBar.get() && IkGui.beginTabBar("Hello")) {
            for (String name :
                    new String[] {"OneOneOne", "TwoTwoTwo", "ThreeThreeThree", "FourFourFour"}) {
                if (IkGui.beginTabItem(name)) {
                    IkGui.endTabItem();
                }
            }
            IkGui.endTabBar();
        }
        if (horizontalChild.get()) {
            IkGui.beginChild("child", 0, 0, ChildFlags.BORDERS);
            IkGui.endChild();
        }
        IkGui.end();
    }

    private static void showLayoutTextClipping() {
        if (!IkGui.treeNode("Text Clipping")) {
            return;
        }
        IkGui.dragFloat2("size", textClipSize, 0.5f, 1.0f, 200.0f, "%.0f");
        IkGui.textWrapped("(Click and drag to scroll)");

        helpMarker(
                "(Left) Using IkGui.pushClipRect():\n"
                        + "Will alter IkGui hit-testing logic + DrawList rendering.\n"
                        + "(use this if you want your clipping rectangle to affect"
                        + " interactions)\n\n"
                        + "(Right) Using DrawList.pushClipRect():\n"
                        + "Will alter DrawList rendering only.\n"
                        + "(use this as a shortcut if you are only using DrawList calls)");

        for (int n = 0; n < 2; n++) {
            if (n > 0) {
                IkGui.sameLine();
            }
            IkGui.pushID(n);
            IkGui.invisibleButton("##canvas", textClipSize[0], textClipSize[1]);
            if (IkGui.isItemActive() && IkGui.isMouseDragging(MouseButton.LEFT)) {
                textClipOffset[0] += IkGui.getIO().mouseDelta.x;
                textClipOffset[1] += IkGui.getIO().mouseDelta.y;
            }
            IkGui.popID();
            if (!IkGui.isItemVisible()) {
                // Skip rendering, as DrawList elements are not clipped
                continue;
            }

            final Vector2f p0 = IkGui.getItemRectMin();
            final Vector2f p1 = IkGui.getItemRectMax();
            final String text = "Line 1 hello\nLine 2 clip me!";
            final float textX = p0.x + textClipOffset[0];
            final float textY = p0.y + textClipOffset[1];
            final DrawList drawList = IkGui.getWindowDrawList();
            if (n == 0) {
                IkGui.pushClipRect(p0.x, p0.y, p1.x, p1.y, true);
            } else {
                drawList.pushClipRect(p0.x, p0.y, p1.x, p1.y);
            }
            drawList.addRectFilled(p0.x, p0.y, p1.x, p1.y, Color.rgba(90, 90, 120, 255));
            drawList.addText(IkGui.getFontSize(), textX, textY, Color.WHITE, text, 0.0f);
            if (n == 0) {
                IkGui.popClipRect();
            } else {
                drawList.popClipRect();
            }
        }
        IkGui.treePop();
    }

    private static void showLayoutOverlapMode() {
        if (!IkGui.treeNode("Overlap Mode")) {
            return;
        }
        helpMarker(
                "Hit-testing is by default performed in item submission order, which generally is"
                        + " perceived as 'back-to-front'.\n\n"
                        + "By using setNextItemAllowOverlap() you can notify that an item may be"
                        + " overlapped by another. Doing so alters the hovering logic: items"
                        + " using AllowOverlap mode requires an extra frame to accept hovered"
                        + " state.");
        IkGui.checkbox("Enable AllowOverlap", overlapEnable);

        final Vector2f button1 = IkGui.getCursorScreenPos();
        if (overlapEnable.get()) {
            IkGui.setNextItemAllowOverlap();
        }
        IkGui.button("Button 1", 80, 80);
        IkGui.setCursorScreenPos(button1.x + 50.0f, button1.y + 50.0f);
        IkGui.button("Button 2", 80, 80);

        // This is typically used with width-spanning items. Note that selectable() has a
        // dedicated flag SelectableFlags.ALLOW_OVERLAP, which is a shortcut for
        // setNextItemAllowOverlap(). For demo purposes we use setNextItemAllowOverlap() here.
        if (overlapEnable.get()) {
            IkGui.setNextItemAllowOverlap();
        }
        IkGui.selectable("Some Selectable", false);
        IkGui.sameLine();
        IkGui.smallButton("++");
        IkGui.treePop();
    }

    // ---------------------------------------------------------------------------------------------
    // Popups and tables
    // ---------------------------------------------------------------------------------------------

    private static int selectedFish = -1;
    private static final String[] FISH_NAMES = {
        "Bream", "Haddock", "Mackerel", "Pollock", "Tilefish"
    };
    private static final IkBoolean[] fishToggles = {
        new IkBoolean(true),
        new IkBoolean(false),
        new IkBoolean(false),
        new IkBoolean(false),
        new IkBoolean(false)
    };
    private static int contextSelected = -1;
    private static final float[] contextValue = {0.5f};
    private static final IkString contextName = new IkString("Label1", 32);
    private static final IkBoolean dontAskMeNextTime = new IkBoolean(false);
    private static final IkInt modalComboItem = new IkInt(1);
    private static final float[] modalColor = {0.4f, 0.7f, 0.0f, 0.5f};

    private static void showPopupsSection() {
        if (!IkGui.collapsingHeader("Popups & Modal windows")) {
            return;
        }

        // The properties of popup windows are:
        // - They block normal mouse hovering detection outside them.
        // - Unless modal, they can be closed by clicking anywhere outside them.
        // - Their visibility state is held internally by IkGui instead of being held by the
        //   programmer as with regular begin() calls. Call openPopup() to change it.
        showPopupsPopups();
        showPopupsContextMenus();
        showPopupsModals();
        showPopupsMenusInRegularWindow();
    }

    private static void showPopupsPopups() {
        if (!IkGui.treeNode("Popups")) {
            return;
        }
        IkGui.textWrapped(
                "When a popup is active, it inhibits interacting with windows that are behind the "
                        + "popup. Clicking outside the popup closes it.");

        // Simple selection popup
        if (IkGui.button("Select..")) {
            IkGui.openPopup("my_select_popup");
        }
        IkGui.sameLine();
        IkGui.textUnformatted(selectedFish == -1 ? "<None>" : FISH_NAMES[selectedFish]);
        if (IkGui.beginPopup("my_select_popup")) {
            IkGui.separatorText("Aquarium");
            for (int i = 0; i < FISH_NAMES.length; ++i) {
                if (IkGui.selectable(FISH_NAMES[i])) {
                    selectedFish = i;
                }
            }
            IkGui.endPopup();
        }

        // Showing a menu with toggles
        if (IkGui.button("Toggle..")) {
            IkGui.openPopup("my_toggle_popup");
        }
        if (IkGui.beginPopup("my_toggle_popup")) {
            for (int i = 0; i < FISH_NAMES.length; ++i) {
                IkGui.menuItem(FISH_NAMES[i], "", fishToggles[i]);
            }
            if (IkGui.beginMenu("Sub-menu")) {
                IkGui.menuItem("Click me");
                IkGui.endMenu();
            }

            IkGui.separator();
            IkGui.text("Tooltip here");
            IkGui.setItemTooltip("I am a tooltip over a popup");

            if (IkGui.button("Stacked Popup")) {
                IkGui.openPopup("another popup");
            }
            if (IkGui.beginPopup("another popup")) {
                for (int i = 0; i < FISH_NAMES.length; ++i) {
                    IkGui.menuItem(FISH_NAMES[i], "", fishToggles[i]);
                }
                if (IkGui.beginMenu("Sub-menu")) {
                    IkGui.menuItem("Click me");
                    if (IkGui.button("Stacked Popup")) {
                        IkGui.openPopup("another popup");
                    }
                    if (IkGui.beginPopup("another popup")) {
                        IkGui.text("I am the last one here.");
                        IkGui.endPopup();
                    }
                    IkGui.endMenu();
                }
                IkGui.endPopup();
            }
            IkGui.endPopup();
        }

        // A popup with its own menu bar
        if (IkGui.button("With a menu..")) {
            IkGui.openPopup("my_file_popup");
        }
        if (IkGui.beginPopup("my_file_popup", WindowFlags.MENU_BAR)) {
            if (IkGui.beginMenuBar()) {
                if (IkGui.beginMenu("File")) {
                    showExampleMenuFile();
                    IkGui.endMenu();
                }
                if (IkGui.beginMenu("Edit")) {
                    IkGui.menuItem("Dummy");
                    IkGui.endMenu();
                }
                IkGui.endMenuBar();
            }
            IkGui.text("Hello from popup!");
            IkGui.button("This is a dummy button..");
            IkGui.endPopup();
        }

        IkGui.treePop();
    }

    private static void showPopupsContextMenus() {
        if (!IkGui.treeNode("Context menus")) {
            return;
        }
        helpMarker(
                "\"Context\" functions are simple helpers to associate a Popup to a given Item or"
                        + " Window identifier.");

        // beginPopupContextItem() is a helper to provide common/simple popup behavior of
        // essentially doing:
        //     if (id == 0)
        //         id = getItemID(); // Use last item id
        //     if (isItemHovered() && isMouseReleased(MouseButton.RIGHT))
        //         openPopup(id);
        //     return beginPopup(id);
        // For advanced uses you may want to replicate and customize this code. See more details in
        // beginPopupContextItem().

        // Example 1: When used after an item that has an ID (e.g. button), we can skip providing
        // an ID to beginPopupContextItem(), and beginPopupContextItem() will use the last item ID
        // as the popup ID.
        {
            final String[] names = {"Label1", "Label2", "Label3", "Label4", "Label5"};
            for (int n = 0; n < names.length; n++) {
                if (IkGui.selectable(names[n], contextSelected == n)) {
                    contextSelected = n;
                }
                if (IkGui.beginPopupContextItem()) { // <-- use last item id as popup id
                    contextSelected = n;
                    IkGui.text("This is a popup for \"" + names[n] + "\"!");
                    if (IkGui.button("Close")) {
                        IkGui.closeCurrentPopup();
                    }
                    IkGui.endPopup();
                }
                IkGui.setItemTooltip("Right-click to open popup");
            }
        }

        // Example 2: Popup on a text() element which doesn't have an identifier: we need to
        // provide an identifier to beginPopupContextItem(). Using an explicit identifier is also
        // convenient if you want to activate the popups from different locations.
        {
            helpMarker("text() elements don't have stable identifiers so we need to provide one.");
            IkGui.text(
                    String.format("Value = %.3f <-- (1) right-click this text", contextValue[0]));
            if (IkGui.beginPopupContextItem("my popup")) {
                if (IkGui.selectable("Set to zero")) {
                    contextValue[0] = 0.0f;
                }
                if (IkGui.selectable("Set to PI")) {
                    contextValue[0] = 3.1415f;
                }
                IkGui.setNextItemWidth(-Float.MIN_VALUE);
                IkGui.dragFloat("##Value", contextValue, 0.1f, 0.0f, 0.0f);
                IkGui.endPopup();
            }

            // We can also use openPopupOnItemClick() to toggle the visibility of a given popup.
            // Here we make it that right-clicking this other text element opens the same popup as
            // above. The popup itself will be submitted by the code above.
            IkGui.text("(2) Or right-click this text");
            IkGui.openPopupOnItemClick("my popup", PopupFlags.MOUSE_BUTTON_RIGHT);

            // Back to square one: manually open the same popup.
            if (IkGui.button("(3) Or click this button")) {
                IkGui.openPopup("my popup");
            }
        }

        // Example 3: When using beginPopupContextItem() with an implicit identifier (use last item
        // ID), we need to make sure your item identifier is stable. In this example we showcase
        // altering the item label while preserving its identifier, using the ### operator.
        {
            helpMarker(
                    "Showcase using a popup ID linked to item ID, with the item having a changing"
                            + " label + stable ID using the ### operator.");
            // ### operator override ID ignoring the preceding label
            IkGui.button("Button: " + contextName.get() + "###Button");
            if (IkGui.beginPopupContextItem()) {
                IkGui.text("Edit name:");
                IkGui.inputText("##edit", contextName);
                if (IkGui.button("Close")) {
                    IkGui.closeCurrentPopup();
                }
                IkGui.endPopup();
            }
            IkGui.sameLine();
            IkGui.text("(<-- right-click here)");
        }

        IkGui.treePop();
    }

    private static void showPopupsModals() {
        if (!IkGui.treeNode("Modals")) {
            return;
        }
        IkGui.textWrapped(
                "Modal windows are like popups but the user cannot close them by clicking"
                        + " outside.");

        if (IkGui.button("Delete..")) {
            IkGui.openPopup("Delete?");
        }

        // Always center this window when appearing
        final Vector2f center = IkGui.getMainViewport().getCenter(new Vector2f());
        IkGui.setNextWindowPos(center.x, center.y, Condition.APPEARING, 0.5f, 0.5f);

        if (IkGui.beginPopupModal("Delete?", null, WindowFlags.ALWAYS_AUTO_RESIZE)) {
            IkGui.text(
                    "All those beautiful files will be deleted.\nThis operation cannot be undone!");
            IkGui.separator();

            IkGui.pushStyleVarFloat2(StyleVariable.FRAME_PADDING, 0, 0);
            IkGui.checkbox("Don't ask me next time", dontAskMeNextTime);
            IkGui.popStyleVar();

            if (IkGui.button("OK", 120, 0)) {
                IkGui.closeCurrentPopup();
            }
            IkGui.setItemDefaultFocus();
            IkGui.sameLine();
            if (IkGui.button("Cancel", 120, 0)) {
                IkGui.closeCurrentPopup();
            }
            IkGui.endPopup();
        }

        if (IkGui.button("Stacked modals..")) {
            IkGui.openPopup("Stacked 1");
        }
        if (IkGui.beginPopupModal("Stacked 1", null, WindowFlags.MENU_BAR)) {
            if (IkGui.beginMenuBar()) {
                if (IkGui.beginMenu("File")) {
                    IkGui.menuItem("Some menu item");
                    IkGui.endMenu();
                }
                IkGui.endMenuBar();
            }
            IkGui.text(
                    "Hello from Stacked The First\n"
                            + "Using style colors[MODAL_WINDOW_DIM_BACKGROUND] behind it.");

            // Testing behavior of widgets stacking their own regular popups over the modal.
            IkGui.combo("Combo", modalComboItem, COMBO_ONE_LINER_ITEMS);
            IkGui.colorEdit4("Color", modalColor);

            if (IkGui.button("Add another modal..")) {
                IkGui.openPopup("Stacked 2");
            }

            // Passing an open flag to beginPopupModal() creates a regular close button which
            // closes the popup. The visibility state of popups is owned by IkGui, so the input
            // value doesn't matter here.
            final IkBoolean unusedOpen = new IkBoolean(true);
            if (IkGui.beginPopupModal("Stacked 2", unusedOpen)) {
                IkGui.text("Hello from Stacked The Second!");
                IkGui.colorEdit4("Color", modalColor); // Allow opening another nested popup
                if (IkGui.button("Close")) {
                    IkGui.closeCurrentPopup();
                }
                IkGui.endPopup();
            }

            if (IkGui.button("Close")) {
                IkGui.closeCurrentPopup();
            }
            IkGui.endPopup();
        }

        IkGui.treePop();
    }

    private static void showPopupsMenusInRegularWindow() {
        if (!IkGui.treeNode("Menus inside a regular window")) {
            return;
        }
        IkGui.textWrapped(
                "Below we are testing adding menu items to a regular window. It's rather unusual "
                        + "but should work!");
        IkGui.separator();

        IkGui.menuItem("Menu item", "Ctrl+M");
        if (IkGui.beginMenu("Menu inside a regular window")) {
            showExampleMenuFile();
            IkGui.endMenu();
        }
        IkGui.separator();
        IkGui.treePop();
    }

    private static final IkBoolean menuOptionEnabled = new IkBoolean(true);
    private static final IkBoolean menuSomeOption = new IkBoolean(true);
    private static final float[] menuValue = {0.5f};
    private static final IkInt menuComboItem = new IkInt(0);

    /** An example "File" menu, used in several places of the demo. */
    static void showExampleMenuFile() {
        IkGui.menuItem("(demo menu)", null, false, false);
        IkGui.menuItem("New");
        IkGui.menuItem("Open", "Ctrl+O");
        if (IkGui.beginMenu("Open Recent")) {
            IkGui.menuItem("fish_hat.c");
            IkGui.menuItem("fish_hat.inl");
            IkGui.menuItem("fish_hat.h");
            if (IkGui.beginMenu("More..")) {
                IkGui.menuItem("Hello");
                IkGui.menuItem("Sailor");
                if (IkGui.beginMenu("Recurse..")) {
                    showExampleMenuFile();
                    IkGui.endMenu();
                }
                IkGui.endMenu();
            }
            IkGui.endMenu();
        }
        IkGui.menuItem("Save", "Ctrl+S");
        IkGui.menuItem("Save As..");

        IkGui.separator();
        if (IkGui.beginMenu("Options")) {
            IkGui.menuItem("Enabled", "", menuOptionEnabled);
            IkGui.beginChild(
                    "child",
                    0,
                    IkGui.getTextLineHeightWithSpacing() * 5.0f,
                    ChildFlags.BORDERS,
                    WindowFlags.NONE);
            for (int i = 0; i < 10; ++i) {
                IkGui.text("Scrolling Text " + i);
            }
            IkGui.endChild();
            IkGui.sliderFloat("Value", menuValue, 0.0f, 1.0f);
            IkGui.inputFloat("Input", menuValue, 0.1f);
            IkGui.combo("Combo", menuComboItem, new String[] {"Yes", "No", "Maybe"});
            IkGui.endMenu();
        }

        if (IkGui.beginMenu("Colors")) {
            final float size = IkGui.getTextLineHeight();
            for (ColorType type : ColorType.values()) {
                final Vector2f position = IkGui.getCursorScreenPos();
                IkGui.getWindowDrawList()
                        .addRectFilled(
                                position.x,
                                position.y,
                                position.x + size,
                                position.y + size,
                                IkGui.getColor(type));
                IkGui.dummy(size, size);
                IkGui.sameLine();
                IkGui.menuItem(type.name());
            }
            IkGui.endMenu();
        }

        // Appending again to the "Options" menu we already created above
        if (IkGui.beginMenu("Options")) {
            IkGui.checkbox("SomeOption", menuSomeOption);
            IkGui.endMenu();
        }

        if (IkGui.beginMenu("Disabled", false)) {
            throw new IllegalStateException("Disabled menus should never open");
        }
        IkGui.menuItem("Checked", null, true);
        IkGui.separator();
        IkGui.menuItem("Quit", "Alt+F4");
    }

    /** The menu bar of the demo window. */
    private static void showDemoMenuBar() {
        if (!IkGui.beginMenuBar()) {
            return;
        }
        if (IkGui.beginMenu("Menu")) {
            showExampleMenuFile();
            IkGui.endMenu();
        }
        if (IkGui.beginMenu("Examples")) {
            IkGuiDemoExamples.showExamplesMenu();
            IkGui.endMenu();
        }
        if (IkGui.beginMenu("Tools")) {
            final IkIO io = IkGui.getIO();
            IkGui.menuItem("Metrics/Debugger", null, showMetrics);
            if (IkGui.beginMenu("Debug Options")) {
                io.configDebugHighlightIdConflicts =
                        toggle("Highlight ID Conflicts", io.configDebugHighlightIdConflicts);
                io.configErrorRecoveryEnableAssert =
                        toggle("Assert on error recovery", io.configErrorRecoveryEnableAssert);
                IkGui.textDisabled("(see Demo->Configuration for more)");
                IkGui.endMenu();
            }
            IkGui.menuItem("Debug Log", null, showDebugLog);
            IkGui.menuItem("ID Stack Tool", null, showIDStackTool);
            final boolean isDebuggerPresent = io.configDebugIsDebuggerPresent;
            if (IkGui.menuItem("Item Picker", null, false, true)) {
                IkGui.debugStartItemPicker();
            }
            if (!isDebuggerPresent) {
                IkGui.setItemTooltip(
                        "Requires io.configDebugIsDebuggerPresent=true to be set.\n\nWe otherwise"
                                + " disable some extra features to avoid casual users crashing the"
                                + " application.");
            }
            IkGui.menuItem("Style Editor", null, showStyleEditorWindow);
            IkGui.menuItem("About IkGui", null, showAbout);
            IkGui.endMenu();
        }
        IkGui.endMenuBar();
    }

    // ---------------------------------------------------------------------------------------------
    // Click again to rename
    // ---------------------------------------------------------------------------------------------

    /** The names of the items that can be renamed. */
    static final String[] renameItems = {"Apple", "Banana", "Cherry", "Dragon fruit", "Elderberry"};

    /** The selected item, or -1 for none. */
    static int renameSelected = -1;

    /** The item being renamed, or -1 for none. */
    static int renameEditing = -1;

    /** Whether the rename text input still needs keyboard focus. */
    private static boolean renameFocusPending;

    private static final IkString renameBuffer = new IkString(64);

    /** The last item that was opened with a double click. */
    static String renameLastOpened = "";

    /**
     * Rename items like a file browser, by clicking an item that's already selected. A double click
     * opens the item instead, so getItemClickedCountWithSingleClickDelay() is used to wait a little
     * after a single click, to tell it apart from a double click.
     */
    static void showWidgetsClickToRename() {
        helpMarker(
                "Click an item to select it, then click it again to rename it, like in a file"
                        + " browser. Double-click to open it.\n\n"
                        + "Uses getItemClickedCountWithSingleClickDelay(), which waits"
                        + " io.mouseSingleClickDelay after a single click, so it's not mistaken for"
                        + " the start of a double click.");
        for (int i = 0; i < renameItems.length; ++i) {
            IkGui.pushID(i);
            if (renameEditing == i) {
                if (renameFocusPending) {
                    IkGui.setKeyboardFocusHere();
                    renameFocusPending = false;
                }
                IkGui.setNextItemWidth(-Float.MIN_VALUE);
                IkGui.inputText("##rename", renameBuffer, InputTextFlags.AUTO_SELECT_ALL);
                // Enter or clicking away keeps the new name, Escape reverts the text first
                if (IkGui.isItemDeactivated()) {
                    final String name = renameBuffer.get().trim();
                    if (!name.isEmpty()) {
                        renameItems[i] = name;
                    }
                    renameEditing = -1;
                }
            } else {
                if (IkGui.selectable(renameItems[i], renameSelected == i)) {
                    renameSelected = i;
                }
                final int clicks = IkGui.getItemClickedCountWithSingleClickDelay();
                // Only rename if it was already selected when it was clicked, and the mouse is
                // still over it
                if (clicks == 1
                        && IkGui.getContext().lastActiveIDWasSoleSelected
                        && IkGui.isItemHovered()) {
                    renameEditing = i;
                    renameFocusPending = true;
                    renameBuffer.set(renameItems[i]);
                } else if (clicks == 2) {
                    renameLastOpened = renameItems[i];
                }
            }
            IkGui.popID();
        }
        IkGui.text("Last opened: " + (renameLastOpened.isEmpty() ? "none" : renameLastOpened));
    }

    // ---------------------------------------------------------------------------------------------
    // About window
    // ---------------------------------------------------------------------------------------------

    /** Whether the About window shows the config and build information. */
    static final IkBoolean aboutShowConfigInfo = new IkBoolean(false);

    /**
     * Show the About window, with credits and build/system information.
     *
     * @param open If not null, a close button is shown that sets this to false.
     */
    static void showAboutWindow(IkBoolean open) {
        if (!IkGui.begin("About IkGui", open, WindowFlags.ALWAYS_AUTO_RESIZE)) {
            IkGui.end();
            return;
        }
        IkGui.text(
                String.format(
                        "IkGui, a Java port of Dear ImGui %s (%d), docking branch",
                        IkGui.DEAR_IMGUI_VERSION, IkGui.DEAR_IMGUI_VERSION_NUM));

        IkGui.textLinkOpenURL("Homepage", "https://github.com/ocornut/imgui");
        IkGui.sameLine();
        IkGui.textLinkOpenURL("FAQ", "https://github.com/ocornut/imgui/blob/master/docs/FAQ.md");
        IkGui.sameLine();
        IkGui.textLinkOpenURL("Wiki", "https://github.com/ocornut/imgui/wiki");
        IkGui.sameLine();
        IkGui.textLinkOpenURL("Releases", "https://github.com/ocornut/imgui/releases");
        IkGui.sameLine();
        IkGui.textLinkOpenURL("Funding", "https://github.com/ocornut/imgui/wiki/Funding");

        IkGui.separator();
        IkGui.text("Dear ImGui (c) 2014-2026 Omar Cornut");
        IkGui.text("Developed by Omar Cornut and all Dear ImGui contributors.");
        IkGui.text(
                "Dear ImGui is licensed under the MIT License, see licenses/LICENSE-imgui for more"
                        + " information.");
        IkGui.text("If your company uses this, please consider funding the Dear ImGui project.");

        IkGui.checkbox("Config/Build Information", aboutShowConfigInfo);
        if (aboutShowConfigInfo.get()) {
            showAboutConfigInfo();
        }
        IkGui.end();
    }

    /** The config and build information section of the About window. */
    private static void showAboutConfigInfo() {
        final IkIO io = IkGui.getIO();
        final StyleVariables style = IkGui.getStyle().variable;

        final boolean copyToClipboard = IkGui.button("Copy to clipboard");
        IkGui.beginChild(
                IkGui.getID("cfg_infos"),
                0,
                IkGui.getTextLineHeightWithSpacing() * 18,
                ChildFlags.FRAME_STYLE);
        if (copyToClipboard) {
            IkGui.logToClipboard();
            IkGui.logText("// (Copy from the next line. Keep the ``` markers for formatting.)\n");
            // Back quotes will make text appear without formatting when pasting on GitHub
            IkGui.logText("```\n");
        }

        IkGui.text(
                String.format(
                        "IkGui, ported from Dear ImGui %s (%d)",
                        IkGui.DEAR_IMGUI_VERSION, IkGui.DEAR_IMGUI_VERSION_NUM));
        IkGui.separator();
        IkGui.text(
                String.format(
                        "Java: %s (%s), %s",
                        System.getProperty("java.version"),
                        System.getProperty("java.vendor"),
                        System.getProperty("java.vm.name")));
        IkGui.text(
                String.format(
                        "OS: %s %s (%s)",
                        System.getProperty("os.name"),
                        System.getProperty("os.version"),
                        System.getProperty("os.arch")));
        IkGui.text("LWJGL: " + org.lwjgl.Version.getVersion());

        IkGui.separator();
        IkGui.text(String.format("io.configFlags: 0x%08X", io.configFlags));
        for (String name : setFlagNames(ConfigFlags.class, io.configFlags)) {
            IkGui.text(" " + name);
        }
        final String[][] options = {
            {"io.configMouseDrawCursor", String.valueOf(io.configMouseDrawCursor)},
            {"io.configDpiScaleViewports", String.valueOf(io.configDpiScaleViewports)},
            {"io.configViewportsNoAutoMerge", String.valueOf(io.configViewportsNoAutoMerge)},
            {"io.configViewportsNoTaskBarIcon", String.valueOf(io.configViewportsNoTaskBarIcon)},
            {"io.configViewportsNoDecoration", String.valueOf(io.configViewportsNoDecoration)},
            {
                "io.configViewportsNoDefaultParent",
                String.valueOf(io.configViewportsNoDefaultParent)
            },
            {"io.configDockingNoSplit", String.valueOf(io.configDockingNoSplit)},
            {"io.configDockingNoDockingOver", String.valueOf(io.configDockingNoDockingOver)},
            {"io.configDockingWithShift", String.valueOf(io.configDockingWithShift)},
            {"io.configDockingAlwaysTabBar", String.valueOf(io.configDockingAlwaysTabBar)},
            {
                "io.configDockingTransparentPayload",
                String.valueOf(io.configDockingTransparentPayload)
            },
            {"io.configMacOSXBehaviors", String.valueOf(io.configMacOSXBehaviors)},
            {"io.configNavMoveSetMousePosition", String.valueOf(io.configNavMoveSetMousePosition)},
            {"io.configNavCaptureKeyboard", String.valueOf(io.configNavCaptureKeyboard)},
            {"io.configInputTextCursorBlink", String.valueOf(io.configInputTextCursorBlink)},
            {"io.configWindowsResizeFromEdges", String.valueOf(io.configWindowsResizeFromEdges)},
            {
                "io.configWindowsMoveFromTitleBarOnly",
                String.valueOf(io.configWindowsMoveFromTitleBarOnly)
            },
        };
        // Only list the options that are turned on
        for (String[] option : options) {
            if (Boolean.parseBoolean(option[1])) {
                IkGui.text(option[0]);
            }
        }
        if (io.configMemoryCompactTimer >= 0.0f) {
            IkGui.text(
                    String.format(
                            "io.configMemoryCompactTimer = %.1f", io.configMemoryCompactTimer));
        }
        IkGui.text(String.format("io.backendFlags: 0x%08X", io.backendFlags));
        for (String name : setFlagNames(BackendFlags.class, io.backendFlags)) {
            IkGui.text(" " + name);
        }

        IkGui.separator();
        IkGui.text(
                String.format(
                        "io.fonts: %d fonts, TexSize: %d,%d, FreeType %s",
                        io.fonts.getFontNames().size(),
                        FontAtlas.FONT_ATLAS_IMAGE_WIDTH,
                        FontAtlas.FONT_ATLAS_IMAGE_HEIGHT,
                        io.fonts.getFreeTypeVersion()));
        IkGui.text(String.format("io.displaySize: %.2f,%.2f", io.displaySize.x, io.displaySize.y));
        IkGui.text(
                String.format(
                        "io.displayFramebufferScale: %.2f,%.2f",
                        io.displayFramebufferScale.x, io.displayFramebufferScale.y));

        IkGui.separator();
        IkGui.text(
                String.format(
                        "style.windowPadding: %.2f,%.2f",
                        style.windowPadding.x, style.windowPadding.y));
        IkGui.text(String.format("style.windowBorderSize: %.2f", style.windowBorderSize));
        IkGui.text(
                String.format(
                        "style.framePadding: %.2f,%.2f",
                        style.framePadding.x, style.framePadding.y));
        IkGui.text(String.format("style.frameRounding: %.2f", style.frameRounding));
        IkGui.text(String.format("style.frameBorderSize: %.2f", style.frameBorderSize));
        IkGui.text(
                String.format(
                        "style.itemSpacing: %.2f,%.2f", style.itemSpacing.x, style.itemSpacing.y));
        IkGui.text(
                String.format(
                        "style.itemInnerSpacing: %.2f,%.2f",
                        style.itemInnerSpacing.x, style.itemInnerSpacing.y));

        if (copyToClipboard) {
            IkGui.logText("\n```\n");
            IkGui.logFinish();
        }
        IkGui.endChild();
    }

    /**
     * Find the names of the single-bit flags that are set, from the constants of a flags class.
     *
     * @param flagsClass The class with the flag constants.
     * @param value The flags to check.
     * @return The names of the flags that are set.
     */
    static List<String> setFlagNames(Class<?> flagsClass, int value) {
        final List<String> names = new ArrayList<>();
        for (java.lang.reflect.Field field : flagsClass.getFields()) {
            if (field.getType() != int.class
                    || !java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            try {
                final int flag = field.getInt(null);
                if (Integer.bitCount(flag) == 1 && (value & flag) != 0) {
                    names.add(field.getName());
                }
            } catch (IllegalAccessException e) {
                // Only public fields are listed, so this can't happen
            }
        }
        return names;
    }

    // ---------------------------------------------------------------------------------------------
    // Inputs & Focus
    // ---------------------------------------------------------------------------------------------

    private static final MouseButton[] MOUSE_BUTTONS = {
        MouseButton.LEFT,
        MouseButton.RIGHT,
        MouseButton.MIDDLE,
        MouseButton.BACK,
        MouseButton.FORWARD
    };
    private static final int[] captureOverrideMouse = {-1};
    private static final int[] captureOverrideKeyboard = {-1};
    private static final IkInt shortcutRouteOptions = new IkInt(InputFlags.REPEAT);
    private static final IkInt shortcutRouteType = new IkInt(InputFlags.ROUTE_FOCUSED);
    private static final float[] shortcutFactor = {0.5f};
    private static final IkString tabbingBuffer = new IkString("hello", 32);
    private static final IkString focusBuffer = new IkString("click on a button to set focus", 128);
    private static final float[] focusFloats = {0.0f, 0.0f, 0.0f};

    private static void showInputsSection() {
        if (!IkGui.collapsingHeader("Inputs & Focus")) {
            return;
        }

        final IkIO io = IkGui.getIO();

        // Display inputs submitted to IkIO
        IkGui.setNextItemOpen(true, Condition.ONCE);
        final boolean inputsOpened = IkGui.treeNode("Inputs");
        IkGui.sameLine();
        helpMarker(
                "This is a simplified view. See more detailed input state:\n"
                        + "- in 'Tools->Metrics/Debugger->Inputs'.\n"
                        + "- in 'Tools->Debug Log->IO'.");
        if (inputsOpened) {
            if (IkGui.isMousePosValid()) {
                IkGui.text(
                        String.format(
                                "Mouse pos: (%g, %g)", io.mousePosition.x, io.mousePosition.y));
            } else {
                IkGui.text("Mouse pos: <INVALID>");
            }
            IkGui.text(String.format("Mouse delta: (%g, %g)", io.mouseDelta.x, io.mouseDelta.y));
            // The durations are in milliseconds
            IkGui.text("Mouse down:");
            for (MouseButton button : MOUSE_BUTTONS) {
                if (IkGui.isMouseDown(button)) {
                    IkGui.sameLine();
                    IkGui.text(
                            String.format(
                                    "b%d (%.02f secs)",
                                    button.index, io.mouseDownDuration[button.index] / 1000.0f));
                }
            }
            IkGui.text(String.format("Mouse wheel: %.1f", io.mouseWheel));
            IkGui.text("Mouse clicked count:");
            for (MouseButton button : MOUSE_BUTTONS) {
                if (io.mouseClickedCount[button.index] > 0) {
                    IkGui.sameLine();
                    IkGui.text(
                            String.format(
                                    "b%d: %d", button.index, io.mouseClickedCount[button.index]));
                }
            }

            IkGui.text("Keys down:");
            for (Key key : Key.values()) {
                if (key == Key.NONE || !IkGui.isKeyDown(key)) {
                    continue;
                }
                IkGui.sameLine();
                IkGui.text(String.format("\"%s\" %d", IkGui.getKeyName(key), key.ordinal()));
            }
            IkGui.text(
                    String.format(
                            "Keys mods: %s%s%s%s",
                            io.keyCtrl ? "CTRL " : "",
                            io.keyShift ? "SHIFT " : "",
                            io.keyAlt ? "ALT " : "",
                            io.keySuper ? "SUPER " : ""));
            IkGui.text("Chars queue:");
            for (int i = 0; i < io.inputQueueCharacters.length(); i++) {
                final char c = io.inputQueueCharacters.charAt(i);
                IkGui.sameLine();
                IkGui.text(String.format("'%c' (0x%04X)", c > ' ' ? c : '?', (int) c));
            }

            IkGui.treePop();
        }

        // Display IkIO output flags
        IkGui.setNextItemOpen(true, Condition.ONCE);
        final boolean outputsOpened = IkGui.treeNode("Outputs");
        IkGui.sameLine();
        helpMarker(
                "The value of io.wantCaptureMouse and io.wantCaptureKeyboard are normally set by"
                        + " IkGui to instruct your application of how to route inputs. Typically,"
                        + " when a value is true, it means IkGui wants the corresponding inputs"
                        + " and we expect the underlying application to ignore them.\n\n"
                        + "The most typical case is: when hovering a window, IkGui set"
                        + " io.wantCaptureMouse to true, and underlying application should ignore"
                        + " mouse inputs (in practice there are many and more subtle rules leading"
                        + " to how those flags are set).");
        if (outputsOpened) {
            IkGui.text(String.format("io.wantCaptureMouse: %d", io.wantCaptureMouse ? 1 : 0));
            IkGui.text(
                    String.format(
                            "io.wantCaptureMouseUnlessPopupClose: %d",
                            io.wantCaptureMouseUnlessPopupClose ? 1 : 0));
            IkGui.text(String.format("io.wantCaptureKeyboard: %d", io.wantCaptureKeyboard ? 1 : 0));
            IkGui.text(String.format("io.wantTextInput: %d", io.wantTextInput ? 1 : 0));
            IkGui.text(
                    String.format("io.wantSetMousePosition: %d", io.wantSetMousePosition ? 1 : 0));
            IkGui.text(
                    String.format(
                            "io.navActive: %d, io.navVisible: %d",
                            io.navActive ? 1 : 0, io.navVisible ? 1 : 0));

            if (IkGui.treeNode("WantCapture override")) {
                helpMarker(
                        "Hovering the colored canvas will override io.wantCaptureXXX fields.\n"
                                + "Notice how normally (when set to none), the value of"
                                + " io.wantCaptureKeyboard would be false when hovering and true"
                                + " when clicking.");
                final String[] captureOverrideDescription = {"None", "Set to false", "Set to true"};
                IkGui.setNextItemWidth(IkGui.getFontSize() * 15);
                IkGui.sliderInt(
                        "setNextFrameWantCaptureMouse() on hover",
                        captureOverrideMouse,
                        -1,
                        1,
                        captureOverrideDescription[captureOverrideMouse[0] + 1],
                        SliderFlags.ALWAYS_CLAMP);
                IkGui.setNextItemWidth(IkGui.getFontSize() * 15);
                IkGui.sliderInt(
                        "setNextFrameWantCaptureKeyboard() on hover",
                        captureOverrideKeyboard,
                        -1,
                        1,
                        captureOverrideDescription[captureOverrideKeyboard[0] + 1],
                        SliderFlags.ALWAYS_CLAMP);

                // Dummy item
                IkGui.colorButton(
                        "##panel",
                        new float[] {0.7f, 0.1f, 0.7f, 1.0f},
                        ColorEditFlags.NO_TOOLTIP | ColorEditFlags.NO_DRAG_DROP,
                        128.0f,
                        96.0f);
                if (IkGui.isItemHovered() && captureOverrideMouse[0] != -1) {
                    IkGui.setNextFrameWantCaptureMouse(captureOverrideMouse[0] == 1);
                }
                if (IkGui.isItemHovered() && captureOverrideKeyboard[0] != -1) {
                    IkGui.setNextFrameWantCaptureKeyboard(captureOverrideKeyboard[0] == 1);
                }

                IkGui.treePop();
            }
            IkGui.treePop();
        }

        // Demonstrate using shortcut() and Routing Policies. The general flow is:
        // - Code interested in a chord (e.g. "Ctrl+A") declares their intent.
        // - Multiple locations may be interested in same chord! Routing helps find a winner.
        // - Every frame, we resolve all claims and assign one owner if the modifiers are matching.
        // - The lower-level function is setShortcutRouting(), returns true when caller got the
        //   route.
        // - Most of the times, setShortcutRouting() is not called directly. User mostly calls
        //   shortcut() with routing flags.
        // - If you call shortcut() WITHOUT any routing option, it uses InputFlags.ROUTE_FOCUSED.
        // TL;DR: Most uses will simply be:
        // - shortcut(KeyChord.of(KeyModFlags.CTRL, Key.A)); // Use the ROUTE_FOCUSED policy.
        if (IkGui.treeNode("Shortcuts")) {
            IkGui.checkboxFlags("REPEAT", shortcutRouteOptions, InputFlags.REPEAT);
            IkGui.radioButton("ROUTE_ACTIVE", shortcutRouteType, InputFlags.ROUTE_ACTIVE);
            IkGui.radioButton(
                    "ROUTE_FOCUSED (default)", shortcutRouteType, InputFlags.ROUTE_FOCUSED);
            IkGui.indent();
            IkGui.beginDisabled(shortcutRouteType.get() != InputFlags.ROUTE_FOCUSED);
            IkGui.checkboxFlags(
                    "ROUTE_OVER_ACTIVE##0", shortcutRouteOptions, InputFlags.ROUTE_OVER_ACTIVE);
            IkGui.endDisabled();
            IkGui.unindent();
            IkGui.radioButton("ROUTE_GLOBAL", shortcutRouteType, InputFlags.ROUTE_GLOBAL);
            IkGui.indent();
            IkGui.beginDisabled(shortcutRouteType.get() != InputFlags.ROUTE_GLOBAL);
            IkGui.checkboxFlags(
                    "ROUTE_OVER_FOCUSED", shortcutRouteOptions, InputFlags.ROUTE_OVER_FOCUSED);
            IkGui.checkboxFlags(
                    "ROUTE_OVER_ACTIVE", shortcutRouteOptions, InputFlags.ROUTE_OVER_ACTIVE);
            IkGui.checkboxFlags(
                    "ROUTE_UNLESS_BG_FOCUSED",
                    shortcutRouteOptions,
                    InputFlags.ROUTE_UNLESS_BG_FOCUSED);
            IkGui.endDisabled();
            IkGui.unindent();
            IkGui.radioButton("ROUTE_ALWAYS", shortcutRouteType, InputFlags.ROUTE_ALWAYS);
            int flags = shortcutRouteType.get() | shortcutRouteOptions.get(); // Merged flags
            if (shortcutRouteType.get() != InputFlags.ROUTE_GLOBAL) {
                flags &=
                        ~(InputFlags.ROUTE_OVER_FOCUSED
                                | InputFlags.ROUTE_OVER_ACTIVE
                                | InputFlags.ROUTE_UNLESS_BG_FOCUSED);
            }

            IkGui.separatorText("Using setNextItemShortcut()");
            IkGui.text("Ctrl+S");
            IkGui.setNextItemShortcut(
                    KeyChord.of(KeyModFlags.CTRL, Key.S), flags | InputFlags.TOOLTIP);
            IkGui.button("Save");
            IkGui.text("Alt+F");
            IkGui.setNextItemShortcut(
                    KeyChord.of(KeyModFlags.ALT, Key.F), flags | InputFlags.TOOLTIP);
            IkGui.sliderFloat("Factor", shortcutFactor, 0.0f, 1.0f);

            IkGui.separatorText("Using shortcut()");
            final float lineHeight = IkGui.getTextLineHeightWithSpacing();
            final int keyChord = KeyChord.of(KeyModFlags.CTRL, Key.A);

            IkGui.text("Ctrl+A");
            IkGui.text(shortcutStatus(keyChord, flags));

            IkGui.pushStyleColor(ColorType.CHILD_BACKGROUND, 1.0f, 0.0f, 1.0f, 0.1f);

            IkGui.beginChild("WindowA", -Float.MIN_VALUE, lineHeight * 14, ChildFlags.BORDERS);
            IkGui.text("Press Ctrl+A and see who receives it!");
            IkGui.separator();

            // 1: Window polling for Ctrl+A
            IkGui.text("(in WindowA)");
            IkGui.text(shortcutStatus(keyChord, flags));

            // 2: inputText() also polling for Ctrl+A: it always uses ROUTE_FOCUSED internally (gets
            // priority when active). Upstream leaves this out because the owner-aware version of
            // shortcut() is internal.

            // 3: Dummy child is not claiming the route: focusing them shouldn't steal route away
            // from WindowA
            IkGui.beginChild("ChildD", -Float.MIN_VALUE, lineHeight * 4, ChildFlags.BORDERS);
            IkGui.text("(in ChildD: not using same Shortcut)");
            IkGui.text(String.format("IsWindowFocused: %d", IkGui.isWindowFocused() ? 1 : 0));
            IkGui.endChild();

            // 4: Child window polling for Ctrl+A. It is deeper than WindowA and gets priority when
            // focused.
            IkGui.beginChild("ChildE", -Float.MIN_VALUE, lineHeight * 4, ChildFlags.BORDERS);
            IkGui.text("(in ChildE: using same Shortcut)");
            IkGui.text(shortcutStatus(keyChord, flags));
            IkGui.endChild();

            // 5: In a popup
            if (IkGui.button("Open Popup")) {
                IkGui.openPopup("PopupF");
            }
            if (IkGui.beginPopup("PopupF")) {
                IkGui.text("(in PopupF)");
                IkGui.text(shortcutStatus(keyChord, flags));
                IkGui.endPopup();
            }
            IkGui.endChild();
            IkGui.popStyleColor();

            IkGui.treePop();
        }

        // Display mouse cursors
        if (IkGui.treeNode("Mouse Cursors")) {
            final MouseCursor current = IkGui.getMouseCursor();
            IkGui.text(
                    String.format(
                            "Current mouse cursor = %d: %s",
                            current.ordinal() - 1, current.toString()));
            IkGui.beginDisabled(true);
            final IkInt backendFlags = new IkInt(io.backendFlags);
            IkGui.checkboxFlags(
                    "io.backendFlags: HAS_MOUSE_CURSORS",
                    backendFlags,
                    BackendFlags.HAS_MOUSE_CURSORS);
            IkGui.endDisabled();

            IkGui.text("Hover to see mouse cursors:");
            IkGui.sameLine();
            helpMarker(
                    "Your application can render a different mouse cursor based on what"
                            + " IkGui.getMouseCursor() returns. If software cursor rendering"
                            + " (io.mouseDrawCursor) is set IkGui will draw the right cursor for"
                            + " you, otherwise your backend needs to handle it.");
            // NONE hides the cursor, so it isn't listed
            final MouseCursor[] cursors = MouseCursor.values();
            for (int i = 1; i < cursors.length; i++) {
                IkGui.bullet();
                IkGui.selectable(
                        String.format("Mouse cursor %d: %s", i - 1, cursors[i].toString()), false);
                if (IkGui.isItemHovered()) {
                    IkGui.setMouseCursor(cursors[i]);
                }
            }
            IkGui.treePop();
        }

        if (IkGui.treeNode("Tabbing")) {
            IkGui.text("Use Tab/Shift+Tab to cycle through keyboard editable fields.");
            IkGui.inputText("1", tabbingBuffer);
            IkGui.inputText("2", tabbingBuffer);
            IkGui.inputText("3", tabbingBuffer);
            IkGui.pushItemFlag(ItemFlags.NO_TAB_STOP, true);
            IkGui.inputText("4 (tab skip)", tabbingBuffer);
            IkGui.sameLine();
            helpMarker("Item won't be cycled through when using TAB or Shift+Tab.");
            IkGui.popItemFlag();
            IkGui.inputText("5", tabbingBuffer);
            IkGui.treePop();
        }

        if (IkGui.treeNode("Focus from code")) {
            final boolean focus1 = IkGui.button("Focus on 1");
            IkGui.sameLine();
            final boolean focus2 = IkGui.button("Focus on 2");
            IkGui.sameLine();
            final boolean focus3 = IkGui.button("Focus on 3");
            int hasFocus = 0;

            if (focus1) {
                IkGui.setKeyboardFocusHere();
            }
            IkGui.inputText("1", focusBuffer);
            if (IkGui.isItemActive()) {
                hasFocus = 1;
            }

            if (focus2) {
                IkGui.setKeyboardFocusHere();
            }
            IkGui.inputText("2", focusBuffer);
            if (IkGui.isItemActive()) {
                hasFocus = 2;
            }

            IkGui.pushItemFlag(ItemFlags.NO_TAB_STOP, true);
            if (focus3) {
                IkGui.setKeyboardFocusHere();
            }
            IkGui.inputText("3 (tab skip)", focusBuffer);
            if (IkGui.isItemActive()) {
                hasFocus = 3;
            }
            IkGui.sameLine();
            helpMarker("Item won't be cycled through when using TAB or Shift+Tab.");
            IkGui.popItemFlag();

            if (hasFocus != 0) {
                IkGui.text("Item with focus: " + hasFocus);
            } else {
                IkGui.text("Item with focus: <none>");
            }

            // Use >= 0 parameter to setKeyboardFocusHere() to focus an upcoming item
            int focusAhead = -1;
            if (IkGui.button("Focus on X")) {
                focusAhead = 0;
            }
            IkGui.sameLine();
            if (IkGui.button("Focus on Y")) {
                focusAhead = 1;
            }
            IkGui.sameLine();
            if (IkGui.button("Focus on Z")) {
                focusAhead = 2;
            }
            if (focusAhead != -1) {
                IkGui.setKeyboardFocusHere(focusAhead);
            }
            IkGui.sliderFloat3("Float3", focusFloats, 0.0f, 1.0f);

            IkGui.textWrapped(
                    "NB: Cursor & selection are preserved when refocusing last used item in code.");
            IkGui.treePop();
        }

        if (IkGui.treeNode("Dragging")) {
            IkGui.textWrapped(
                    "You can use IkGui.getMouseDragDelta(0) to query for the dragged amount on any"
                            + " widget.");
            for (MouseButton button :
                    new MouseButton[] {MouseButton.LEFT, MouseButton.RIGHT, MouseButton.MIDDLE}) {
                IkGui.text(String.format("IsMouseDragging(%d):", button.index));
                IkGui.text(
                        String.format(
                                "  w/ default threshold: %d,",
                                IkGui.isMouseDragging(button) ? 1 : 0));
                IkGui.text(
                        String.format(
                                "  w/ zero threshold: %d,",
                                IkGui.isMouseDragging(button, 0.0f) ? 1 : 0));
                IkGui.text(
                        String.format(
                                "  w/ large threshold: %d,",
                                IkGui.isMouseDragging(button, 20.0f) ? 1 : 0));
            }

            IkGui.button("Drag Me");
            if (IkGui.isItemActive()) {
                // Draw a line between the button and the mouse cursor
                final Vector2f clicked = io.mouseClickedPosition[MouseButton.LEFT.index];
                IkGui.getForegroundDrawList()
                        .addLine(
                                clicked.x,
                                clicked.y,
                                io.mousePosition.x,
                                io.mousePosition.y,
                                IkGui.getColor(ColorType.BUTTON),
                                4.0f);
            }

            // Drag operations gets "unlocked" when the mouse has moved past a certain threshold
            // (the default threshold is stored in io.mouseDragThreshold). You can request a lower
            // or higher threshold using the second parameter of isMouseDragging() and
            // getMouseDragDelta().
            final Vector2f valueRaw = IkGui.getMouseDragDelta(MouseButton.LEFT, 0.0f);
            final Vector2f valueWithLockThreshold = IkGui.getMouseDragDelta(MouseButton.LEFT);
            final Vector2f mouseDelta = io.mouseDelta;
            IkGui.text("GetMouseDragDelta(0):");
            IkGui.text(
                    String.format(
                            "  w/ default threshold: (%.1f, %.1f)",
                            valueWithLockThreshold.x, valueWithLockThreshold.y));
            IkGui.text(String.format("  w/ zero threshold: (%.1f, %.1f)", valueRaw.x, valueRaw.y));
            IkGui.text(String.format("io.MouseDelta: (%.1f, %.1f)", mouseDelta.x, mouseDelta.y));
            IkGui.treePop();
        }
    }

    /**
     * The status line for the shortcuts demo, which also polls the shortcut.
     *
     * @param keyChord The shortcut to poll.
     * @param flags The routing flags.
     * @return The text to display.
     */
    private static String shortcutStatus(int keyChord, int flags) {
        return String.format(
                "IsWindowFocused: %d, Shortcut: %s",
                IkGui.isWindowFocused() ? 1 : 0,
                IkGui.shortcut(keyChord, flags) ? "PRESSED" : "...");
    }
}
