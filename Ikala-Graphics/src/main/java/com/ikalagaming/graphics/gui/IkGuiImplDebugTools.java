package com.ikalagaming.graphics.gui;

import com.ikalagaming.graphics.gui.data.Context;
import com.ikalagaming.graphics.gui.data.DebugItemPathQuery;
import com.ikalagaming.graphics.gui.data.DrawList;
import com.ikalagaming.graphics.gui.data.IDStackTool;
import com.ikalagaming.graphics.gui.data.IkBoolean;
import com.ikalagaming.graphics.gui.data.IkIO;
import com.ikalagaming.graphics.gui.data.IkInt;
import com.ikalagaming.graphics.gui.data.KeyRoutingData;
import com.ikalagaming.graphics.gui.data.ListClipper;
import com.ikalagaming.graphics.gui.data.Window;
import com.ikalagaming.graphics.gui.enums.ColorType;
import com.ikalagaming.graphics.gui.enums.Condition;
import com.ikalagaming.graphics.gui.enums.Key;
import com.ikalagaming.graphics.gui.enums.MouseButton;
import com.ikalagaming.graphics.gui.enums.MouseCursor;
import com.ikalagaming.graphics.gui.enums.StyleVariable;
import com.ikalagaming.graphics.gui.enums.TableBackgroundTarget;
import com.ikalagaming.graphics.gui.flags.ChildFlags;
import com.ikalagaming.graphics.gui.flags.DebugLogFlags;
import com.ikalagaming.graphics.gui.flags.HoveredFlags;
import com.ikalagaming.graphics.gui.flags.InputFlags;
import com.ikalagaming.graphics.gui.flags.KeyModFlags;
import com.ikalagaming.graphics.gui.flags.NavRenderCursorFlags;
import com.ikalagaming.graphics.gui.flags.NextWindowFlags;
import com.ikalagaming.graphics.gui.flags.PopupFlags;
import com.ikalagaming.graphics.gui.flags.TableColumnFlags;
import com.ikalagaming.graphics.gui.flags.TableFlags;
import com.ikalagaming.graphics.gui.flags.WindowFlags;
import com.ikalagaming.graphics.gui.util.Color;
import com.ikalagaming.graphics.gui.util.KeyChord;
import com.ikalagaming.graphics.gui.util.MathUtil;
import com.ikalagaming.graphics.gui.util.RectFloat;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector2f;
import org.joml.Vector4f;
import org.slf4j.Logger;
import org.slf4j.helpers.FormattingTuple;
import org.slf4j.helpers.MessageFormatter;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Debug tools: the debug log, the ID stack tool, the item picker, and helpers to locate and draw
 * items for debugging.
 */
@Slf4j
class IkGuiImplDebugTools {
    /** The shared context. */
    static Context context;

    /** The color used to highlight located items. */
    private static final int DEBUG_LOCATE_ITEM_COLOR = Color.rgba(0, 255, 0, 255);

    /** Finds IDs in log lines, which are written as 0x followed by 8 hex digits. */
    private static final Pattern ID_PATTERN =
            Pattern.compile("0[xX]([0-9a-fA-F]{8})(?![0-9a-fA-F])");

    /** Private constructor so this is not instantiated. */
    private IkGuiImplDebugTools() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }

    // ---------------------------------------------------------------------------------------------
    // Debug break
    // ---------------------------------------------------------------------------------------------

    /**
     * Called when a debug tool requests breaking into the debugger, which is the equivalent of
     * IM_DEBUG_BREAK(). Java can't trigger a breakpoint itself, so put a breakpoint in this method
     * and look at the call stack to find the code that submitted the item or window.
     *
     * @param reason What triggered the break, for the log.
     */
    static void debugBreak(@NonNull String reason) {
        // Put a breakpoint here, the caller is the code we wanted to break in
        log.warn("Debug break requested: {}", reason);
    }

    /** Clear the debug break hooks, after exactly one cycle. */
    static void debugBreakClearData() {
        context.debugBreakInWindow = 0;
        context.debugBreakInTable = 0;
        context.debugBreakInShortcutRouting = KeyChord.NONE;
    }

    /**
     * Show a tooltip explaining how to use a debug break button.
     *
     * @param keyboardOnly If the break can only be triggered by the keyboard.
     * @param descriptionOfLocation Where the break will happen, like "in begin()".
     */
    static void debugBreakButtonTooltip(
            boolean keyboardOnly, @NonNull String descriptionOfLocation) {
        if (!IkGuiImplPopups.beginItemTooltip()) {
            return;
        }
        final String key = KeyChord.getName(context.debugBreakKeyChord);
        IkGuiImplText.text("To call debugBreak() " + descriptionOfLocation + ":");
        IkGuiImplLayout.separator();
        IkGuiImplText.textUnformatted(
                keyboardOnly
                        ? "- Press '" + key + "' on keyboard."
                        : "- Press '"
                                + key
                                + "' on keyboard.\n- or Click (may alter focus/active id).\n"
                                + "- or navigate using keyboard and press space.");
        IkGuiImplLayout.separator();
        IkGuiImplText.textUnformatted(
                "Choose one way that doesn't interfere with what you are trying to debug!\n"
                        + "Put a breakpoint in IkGuiInternal.debugBreak() to stop there.");
        IkGuiImplPopups.endTooltip();
    }

    /**
     * A button that doesn't take focus or input ownership, and can be activated without a click, to
     * reduce interference with the contents we are trying to debug.
     *
     * @param label The label.
     * @param descriptionOfLocation Where the break will happen, for the tooltip.
     * @return True if the button was pressed.
     */
    static boolean debugBreakButton(@NonNull String label, @NonNull String descriptionOfLocation) {
        final Window window = IkGuiInternal.getCurrentWindow();
        if (window.skipItems) {
            return false;
        }

        final int id = window.getID(label);
        final Vector2f labelSize = new Vector2f();
        IkGuiImplUtils.calcTextSize(labelSize, label, true, -1.0f);
        final float posX = window.cursorPosition.x;
        final float posY = window.cursorPosition.y + window.baseOffsetCurrentLine;
        final float width = labelSize.x + context.style.variable.framePadding.x * 2.0f;
        final float height = labelSize.y;

        final RectFloat bb = new RectFloat(posX, posY, posX + width, posY + height);
        IkGuiInternal.itemSize(width, height, 0.0f);
        if (!IkGuiInternal.itemAdd(bb, id)) {
            return false;
        }

        // We don't use the regular button behavior, to reduce our side effects
        final boolean hovered = IkGuiInternal.itemHoverable(bb, id, context.currentItemFlags);
        final boolean pressed =
                hovered
                        && (IkGuiImplKeys.isKeyChordPressed(
                                        context.debugBreakKeyChord,
                                        InputFlags.NONE,
                                        KeyRoutingData.KEY_OWNER_ANY)
                                || IkGuiImplUtils.isMouseClicked(MouseButton.LEFT, false)
                                || context.navActivateID == id);
        debugBreakButtonTooltip(false, descriptionOfLocation);

        // Shift the hue of the button color, so it stands out
        final float[] rgb = new float[3];
        final float[] hsv = new float[3];
        final int baseColor =
                IkGuiImplUtils.getColor(hovered ? ColorType.BUTTON_HOVERED : ColorType.BUTTON);
        rgb[0] = ((baseColor >>> 24) & 0xFF) / 255.0f;
        rgb[1] = ((baseColor >>> 16) & 0xFF) / 255.0f;
        rgb[2] = ((baseColor >>> 8) & 0xFF) / 255.0f;
        IkGuiImplUtils.colorConvertRGBtoHSV(rgb, hsv);
        hsv[0] += 0.20f;
        IkGuiImplUtils.colorConvertHSVtoRGB(hsv, rgb);
        final int color =
                IkGuiImplUtils.applyGlobalAlpha(
                        Color.rgba(rgb[0], rgb[1], rgb[2], (baseColor & 0xFF) / 255.0f), false);

        IkGuiImplNav.renderNavCursor(
                bb, id, NavRenderCursorFlags.NONE, context.style.variable.frameRounding);
        IkGuiInternal.renderFrame(
                bb.getLeft(),
                bb.getTop(),
                bb.getRight(),
                bb.getBottom(),
                color,
                true,
                context.style.variable.frameRounding);
        IkGuiInternal.renderTextClipped(
                bb.getLeft(),
                bb.getTop(),
                bb.getRight(),
                bb.getBottom(),
                label,
                labelSize,
                context.style.variable.buttonTextAlign.x,
                context.style.variable.buttonTextAlign.y,
                bb);

        IkGuiInternal.testEngineItemInfo(id, label, context.lastItemData.statusFlags);
        return pressed;
    }

    // ---------------------------------------------------------------------------------------------
    // Debug log
    // ---------------------------------------------------------------------------------------------

    /**
     * Check if a debug log event type is enabled, to skip building expensive log messages.
     *
     * @param eventFlag The type of event.
     * @return True if events of that type are logged.
     */
    static boolean isDebugLogEnabled(int eventFlag) {
        return (context.debugLogFlags & eventFlag) != 0;
    }

    /**
     * The name of a window for log messages.
     *
     * @param window The window, may be null.
     * @return The window name, or "<NULL>".
     */
    static String nameOf(Window window) {
        return window != null ? window.name : "<NULL>";
    }

    /**
     * Add a line to the debug log, if the given event type is enabled.
     *
     * @param eventFlag The type of event, one of the event flags.
     * @param format The format string.
     * @param arguments The arguments for the format string.
     * @see DebugLogFlags
     */
    static void debugLog(int eventFlag, @NonNull String format, Object... arguments) {
        if ((context.debugLogFlags & eventFlag) == 0) {
            if (eventFlag == DebugLogFlags.EVENT_ERROR) {
                context.debugLogBuffer.addSkippedError();
            }
            return;
        }
        debugLog(String.format(format, arguments));
    }

    /**
     * Report an error about incorrect API usage. This is the equivalent of IM_ASSERT_USER_ERROR()
     * and ErrorLog() in Dear ImGui. Depending on the io.configErrorRecoveryEnable options, the
     * error is logged to the logger of the class that found it and to the debug log (as an error
     * event), shown in the error tooltip, passed to the error callback, and finally thrown as an
     * {@link IkGuiUserError}. When error events are disabled in the debug log, they are counted as
     * skipped instead.
     *
     * @param logger The logger of the class reporting the error.
     * @param format The SLF4J style message format, using {} for arguments.
     * @param arguments The arguments. A trailing throwable is logged with its stack trace.
     */
    static void reportError(@NonNull Logger logger, String format, Object... arguments) {
        final FormattingTuple formatted = MessageFormatter.arrayFormat(format, arguments);
        final String message = formatted.getMessage();
        final Throwable throwable = formatted.getThrowable();
        final Context target = context;
        if (target == null) {
            // Without a context there are no options, so always log
            logError(logger, message, throwable);
            return;
        }
        final IkIO io = target.io;
        final String suffix = throwable != null ? " (" + throwable + ")" : "";
        final Thread current = Thread.currentThread();
        final boolean otherThread = target.guiThread != null && target.guiThread != current;
        final Window window = otherThread ? null : target.windowCurrent;
        final String windowName = window != null ? window.name : "NULL";

        // Output to the logger and debug log
        if (io.configErrorRecoveryEnableDebugLog) {
            logError(logger, message, throwable);
            if (target.errorFirst) {
                debugLog(
                        DebugLogFlags.EVENT_ERROR,
                        "[ikgui-error] (current settings: Assert=%d, Log=%d, Tooltip=%d)",
                        io.configErrorRecoveryEnableAssert ? 1 : 0,
                        io.configErrorRecoveryEnableDebugLog ? 1 : 0,
                        io.configErrorRecoveryEnableTooltip ? 1 : 0);
            }
            if (otherThread) {
                // The current window belongs to the GUI thread, and means nothing here
                debugLog(
                        DebugLogFlags.EVENT_ERROR,
                        "[ikgui-error] On thread '%s': %s%s",
                        current.getName(),
                        message,
                        suffix);
            } else {
                debugLog(
                        DebugLogFlags.EVENT_ERROR,
                        "[ikgui-error] In window '%s': %s%s",
                        windowName,
                        message,
                        suffix);
            }
        }
        target.errorFirst = false;

        // Output to the error tooltip. Errors found while showing the tooltip aren't shown in it,
        // to avoid recursion.
        if (io.configErrorRecoveryEnableTooltip && !otherThread && !withinErrorTooltip) {
            if (target.withinFrameScope && !target.windowStack.isEmpty() && beginErrorTooltip()) {
                if (target.errorCountCurrentFrame < 20) {
                    IkGuiImplText.text(
                            String.format("In window '%s': %s%s", windowName, message, suffix));
                    if (window != null && (!window.isFallbackWindow || window.wasActive)) {
                        IkGuiImplUtils.getForegroundDrawList(window.viewport)
                                .addRect(
                                        window.position.x,
                                        window.position.y,
                                        window.position.x + window.size.x,
                                        window.position.y + window.size.y,
                                        Color.rgba(255, 0, 0, 255));
                    }
                }
                if (target.errorCountCurrentFrame == 20) {
                    IkGuiImplText.text("(and more errors)");
                }
                // endFrame() adds the debug buttons to this window, after all errors are reported
                endErrorTooltip();
            }
            target.errorCountCurrentFrame++;
        }

        // Output to the callback
        if (target.errorCallback != null) {
            target.errorCallback.accept(message);
        }

        // The equivalent of the assert
        if (io.configErrorRecoveryEnableAssert) {
            throw new IkGuiUserError(message, throwable);
        }
    }

    private static void logError(@NonNull Logger logger, String message, Throwable throwable) {
        if (throwable != null) {
            logger.error(message, throwable);
        } else {
            logger.error(message);
        }
    }

    /** Set while submitting the error tooltip, so errors in it don't recurse. */
    private static boolean withinErrorTooltip;

    /**
     * Begin the error tooltip, a pseudo-tooltip that follows the mouse until Ctrl is held. When
     * Ctrl is held the position is locked, so it can be clicked. It can be appended to several
     * times each frame.
     *
     * @return True if the tooltip is visible, and endErrorTooltip() must be called.
     */
    static boolean beginErrorTooltip() {
        final Window existing = IkGuiInternal.findWindowByName(ERROR_TOOLTIP_NAME);
        final boolean useLockedPosition =
                context.io.keyCtrl && existing != null && existing.wasActive;
        withinErrorTooltip = true;
        final Vector4f background = new Vector4f();
        IkGuiImplUtils.colorConvertU32ToFloat4(
                IkGuiImplUtils.getColor(ColorType.POPUP_BACKGROUND, 1.0f), background);
        background.lerp(new Vector4f(1.0f, 0.0f, 0.0f, 1.0f), 0.15f);
        IkGuiImplUtils.pushStyleColor(
                ColorType.POPUP_BACKGROUND,
                Color.rgba(background.x, background.y, background.z, background.w));
        if (useLockedPosition) {
            IkGuiImplWindows.setNextWindowPos(
                    context.errorTooltipLockedPosition.x,
                    context.errorTooltipLockedPosition.y,
                    Condition.ALWAYS,
                    0.0f,
                    0.0f);
        }
        final boolean visible =
                IkGuiImplWindows.begin(
                        ERROR_TOOLTIP_NAME,
                        null,
                        WindowFlags.INTERNAL_TOOLTIP
                                | WindowFlags.NO_DECORATION
                                | WindowFlags.NO_MOVE
                                | WindowFlags.NO_RESIZE
                                | WindowFlags.NO_SAVED_SETTINGS
                                | WindowFlags.ALWAYS_AUTO_RESIZE);
        IkGuiImplUtils.popStyleColor();
        if (visible && context.windowCurrent.beginCount == 1) {
            IkGuiImplLayout.separatorText("MESSAGE FROM IKGUI");
            IkGuiInternal.bringWindowToDisplayFront(context.windowCurrent);
            IkGuiInternal.bringWindowToFocusFront(context.windowCurrent);
            context.errorTooltipLockedPosition.set(context.windowCurrent.position);
        } else if (!visible) {
            IkGuiImplWindows.end();
            withinErrorTooltip = false;
        }
        return visible;
    }

    /** End the error tooltip, only call this if beginErrorTooltip() returned true. */
    static void endErrorTooltip() {
        IkGuiImplWindows.end();
        withinErrorTooltip = false;
    }

    /** The name of the error tooltip window. */
    private static final String ERROR_TOOLTIP_NAME = "##Tooltip_Error";

    /**
     * Show the ID conflict error tooltip when the hovered ID was submitted multiple times, and add
     * the debug buttons to the error tooltip. Called by endFrame(). See itemHoverable() for why
     * conflicts are associated with the hovered ID.
     */
    static void errorCheckEndFrameFinalizeErrorTooltip() {
        if (context.debugDrawIdConflictsID != 0 && !context.io.keyCtrl) {
            context.debugDrawIdConflictsCount = context.hoveredIDPreviousFrameItemCount;
        }
        if (context.debugDrawIdConflictsID != 0
                && !context.debugItemPickerActive.get()
                && beginErrorTooltip()) {
            IkGuiImplText.text(
                    String.format(
                            "Programmer error: %d visible items with conflicting ID!",
                            context.debugDrawIdConflictsCount));
            IkGuiImplText.bulletText(
                    "Code should use pushID()/popID() in loops, or append \"##xx\" to same-label"
                            + " identifiers!");
            IkGuiImplText.bulletText(
                    "Empty label e.g. button(\"\") == same ID as parent widget/node. Use"
                            + " button(\"##xx\") instead!");
            IkGuiImplText.bulletText(
                    "Set io.configDebugHighlightIdConflicts=false to disable this warning in"
                            + " non-programmers builds.");
            IkGuiImplLayout.separator();
            if (context.io.configDebugHighlightIdConflictsShowItemPicker) {
                IkGuiImplText.text("(Hold Ctrl to: use ");
                IkGuiImplLayout.sameLine(0.0f, 0.0f);
                if (IkGuiImplButtons.smallButton("Item Picker")) {
                    debugStartItemPicker();
                }
                IkGuiImplLayout.sameLine(0.0f, 0.0f);
                IkGuiImplText.text(" to break in item call-stack, or ");
            } else {
                IkGuiImplText.text("(Hold Ctrl to: ");
            }
            IkGuiImplLayout.sameLine(0.0f, 0.0f);
            IkGuiImplText.textLinkOpenURL(
                    "read FAQ \"About ID Stack System\"",
                    "https://github.com/ocornut/imgui/blob/master/docs/FAQ.md#qa-usage");
            IkGuiImplLayout.sameLine(0.0f, 0.0f);
            IkGuiImplText.text(")");
            endErrorTooltip();
        }

        // Amend the error tooltip at the end of the frame
        if (context.errorCountCurrentFrame > 0 && beginErrorTooltip()) {
            IkGuiImplLayout.separator();
            IkGuiImplText.text("(Hold Ctrl to: ");
            IkGuiImplLayout.sameLine(0.0f, 0.0f);
            if (IkGuiImplButtons.smallButton("Enable Asserts")) {
                context.io.configErrorRecoveryEnableAssert = true;
            }
            IkGuiImplLayout.sameLine(0.0f, 0.0f);
            IkGuiImplText.text(")");
            endErrorTooltip();
        }
    }

    /**
     * Add text to the debug log, unconditionally. Each line of the text becomes a log line. This
     * can be called from any thread.
     *
     * @param text The text to log.
     */
    static void debugLog(@NonNull String text) {
        final String prefix = String.format("[%05d] ", context.frameCount);
        final String[] split = text.split("\n");
        final List<String> lines = new ArrayList<>(split.length);
        for (String line : split) {
            lines.add(prefix + line);
        }
        context.debugLogBuffer.addAll(lines);
        if ((context.debugLogFlags & DebugLogFlags.OUTPUT_TO_LOGGER) != 0) {
            log.debug("{}{}", prefix, text.stripTrailing());
        }
    }

    /**
     * If the next item fits on the current line, call sameLine() so the next item is on it.
     *
     * @param width The width of the next item.
     * @param height The height of the next item.
     */
    private static void sameLineOrWrap(float width, float height) {
        final Window window = context.windowCurrent;
        final float x = window.cursorPreviousLinePosition.x + context.style.variable.itemSpacing.x;
        final float y = window.cursorPreviousLinePosition.y;
        if (window.rectWork.contains(new RectFloat(x, y, x + width, y + height))) {
            IkGuiImplLayout.sameLine(0.0f, -1.0f);
        }
    }

    /**
     * A checkbox for a debug log event type.
     *
     * @param name The name of the event type.
     * @param flags The flag for the event type.
     */
    private static void showDebugLogFlag(@NonNull String name, int flags) {
        final Vector2f textSize = new Vector2f();
        IkGuiImplUtils.calcTextSize(textSize, name, true, -1.0f);
        final float frameHeight = IkGuiImplLayout.getFrameHeight();
        sameLineOrWrap(
                frameHeight + context.style.variable.itemInnerSpacing.x + textSize.x, frameHeight);

        final boolean highlightErrors =
                flags == DebugLogFlags.EVENT_ERROR && context.debugLogBuffer.getSkippedErrors() > 0;
        if (highlightErrors) {
            IkGuiImplUtils.pushStyleColor(ColorType.TEXT, Color.rgba(255, 102, 102, 255));
        }
        final var logFlags = new IkInt(context.debugLogFlags);
        if (IkGuiImplMiscWidgets.checkboxFlags(name, logFlags, flags)) {
            context.debugLogFlags = logFlags.get();
            if (context.io.keyShift && (context.debugLogFlags & flags) != 0) {
                context.debugLogAutoDisableFrames = 2;
                context.debugLogAutoDisableFlags |= flags;
            }
        }
        if (highlightErrors) {
            IkGuiImplUtils.popStyleColor();
            IkGuiImplPopups.setItemTooltip(
                    context.debugLogBuffer.getSkippedErrors() + " past errors skipped.");
        } else {
            IkGuiImplPopups.setItemTooltip(
                    "Hold Shift when clicking to enable for 2 frames only (useful for spammy log"
                            + " entries)");
        }
    }

    /**
     * Show the debug log window, which shows events recorded by the library.
     *
     * @param open The open state of the window, may be null.
     */
    static void showDebugLogWindow(IkBoolean open) {
        if ((context.nextWindowData.fieldFlags & NextWindowFlags.HAS_SIZE) == 0) {
            IkGuiImplWindows.setNextWindowSize(
                    0.0f, IkGuiInternal.getFontSize() * 12.0f, Condition.FIRST_USE_EVER);
        }
        if (!IkGuiImplWindows.begin("IkGui Debug Log", open, WindowFlags.NONE)
                || IkGuiInternal.getCurrentWindow().beginCount > 1) {
            IkGuiImplWindows.end();
            return;
        }

        final int allEnableFlags = DebugLogFlags.EVENT_MASK & ~DebugLogFlags.EVENT_INPUT_ROUTING;
        final var logFlags = new IkInt(context.debugLogFlags);
        if (IkGuiImplMiscWidgets.checkboxFlags("All", logFlags, allEnableFlags)) {
            context.debugLogFlags = logFlags.get();
        }
        IkGuiImplPopups.setItemTooltip("(except InputRouting which is spammy)");

        showDebugLogFlag("Errors", DebugLogFlags.EVENT_ERROR);
        showDebugLogFlag("ActiveId", DebugLogFlags.EVENT_ACTIVE_ID);
        showDebugLogFlag("Clipper", DebugLogFlags.EVENT_CLIPPER);
        showDebugLogFlag("Docking", DebugLogFlags.EVENT_DOCKING);
        showDebugLogFlag("Focus", DebugLogFlags.EVENT_FOCUS);
        showDebugLogFlag("IO", DebugLogFlags.EVENT_IO);
        showDebugLogFlag("Font", DebugLogFlags.EVENT_FONT);
        showDebugLogFlag("Nav", DebugLogFlags.EVENT_NAV);
        showDebugLogFlag("Popup", DebugLogFlags.EVENT_POPUP);
        showDebugLogFlag("Selection", DebugLogFlags.EVENT_SELECTION);
        showDebugLogFlag("Table", DebugLogFlags.EVENT_TABLE);
        showDebugLogFlag("Viewport", DebugLogFlags.EVENT_VIEWPORT);
        showDebugLogFlag("InputRouting", DebugLogFlags.EVENT_INPUT_ROUTING);

        if (IkGuiImplButtons.smallButton("Clear")) {
            context.debugLogBuffer.clear();
        }
        IkGuiImplLayout.sameLine(0.0f, -1.0f);
        if (IkGuiImplButtons.smallButton("Copy")) {
            IkGuiImplUtils.setClipboardText(context.debugLogBuffer.getText());
        }
        IkGuiImplLayout.sameLine(0.0f, -1.0f);
        if (IkGuiImplButtons.smallButton("Configure Outputs..")) {
            IkGuiImplPopups.openPopup("Outputs", PopupFlags.NONE);
        }
        if (IkGuiImplPopups.beginPopup("Outputs", WindowFlags.NONE)) {
            final var outputFlags = new IkInt(context.debugLogFlags);
            if (IkGuiImplMiscWidgets.checkboxFlags(
                    "OutputToLogger", outputFlags, DebugLogFlags.OUTPUT_TO_LOGGER)) {
                context.debugLogFlags = outputFlags.get();
            }
            IkGuiImplPopups.setItemTooltip("Also send entries to the application logger.");
            IkGuiImplPopups.endPopup();
        }

        IkGuiImplWindows.beginChild(
                "##log",
                IkGuiImplUtils.getID("##log"),
                0.0f,
                0.0f,
                ChildFlags.BORDERS,
                WindowFlags.ALWAYS_VERTICAL_SCROLLBAR | WindowFlags.ALWAYS_HORIZONTAL_SCROLLBAR);

        // Don't log the clipper we use to display the log itself
        final int backupLogFlags = context.debugLogFlags;
        context.debugLogFlags = backupLogFlags & ~DebugLogFlags.EVENT_CLIPPER;

        final ListClipper clipper = new ListClipper();
        // Other threads might add lines while we draw, but indices below the size stay valid
        clipper.begin(context.debugLogBuffer.size());
        while (clipper.step()) {
            for (int lineNumber = clipper.displayStart;
                    lineNumber < clipper.displayEnd;
                    ++lineNumber) {
                debugTextUnformattedWithLocateItem(context.debugLogBuffer.get(lineNumber));
            }
        }
        context.debugLogFlags = backupLogFlags;
        if (IkGuiImplUtils.getScrollY() >= IkGuiImplUtils.getScrollMaxY()) {
            IkGuiImplUtils.setScrollHereY(1.0f);
        }
        IkGuiImplWindows.endChild();

        IkGuiImplWindows.end();
    }

    /**
     * Display a line of text, and when hovered search it for IDs written as 0xXXXXXXXX, locating
     * the item when hovering over one.
     *
     * @param line The line of text.
     */
    static void debugTextUnformattedWithLocateItem(@NonNull String line) {
        IkGuiImplText.textUnformatted(line);
        if (!IkGuiImplUtils.isItemHovered(
                HoveredFlags.ALLOW_WHEN_BLOCKED_BY_POPUP
                        | HoveredFlags.ALLOW_WHEN_BLOCKED_BY_ACTIVE_ITEM)) {
            return;
        }
        final RectFloat textRect = new RectFloat(context.lastItemData.rect);
        final Vector2f before = new Vector2f();
        final Vector2f idSize = new Vector2f();
        final Matcher matcher = ID_PATTERN.matcher(line);
        while (matcher.find()) {
            final int id = Integer.parseUnsignedInt(matcher.group(1), 16);
            IkGuiImplUtils.calcTextSize(before, line.substring(0, matcher.start()), false, -1.0f);
            IkGuiImplUtils.calcTextSize(idSize, matcher.group(), false, -1.0f);
            context.lastItemData.rect.set(
                    textRect.getLeft() + before.x,
                    textRect.getTop(),
                    textRect.getLeft() + before.x + idSize.x,
                    textRect.getTop() + idSize.y);
            if (IkGuiInternal.isMouseHoveringRect(
                    context.lastItemData.rect.getLeft(),
                    context.lastItemData.rect.getTop(),
                    context.lastItemData.rect.getRight(),
                    context.lastItemData.rect.getBottom(),
                    true)) {
                debugLocateItemOnHover(id);
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Drawing and locating items
    // ---------------------------------------------------------------------------------------------

    /**
     * Draw a small cross at the cursor position in the current window.
     *
     * @param color The color to draw with.
     */
    static void debugDrawCursorPos(int color) {
        final Window window = context.windowCurrent;
        final Vector2f pos = window.cursorPosition;
        window.drawList.addLineV(pos.x, pos.y - 3.0f, pos.y + 4.0f, color, 1.0f);
        window.drawList.addLineH(pos.x - 3.0f, pos.x + 4.0f, pos.y, color, 1.0f);
    }

    /**
     * Draw the extents of the current line in the current window, around the cursor.
     *
     * @param color The color to draw with.
     */
    static void debugDrawLineExtents(int color) {
        final Window window = context.windowCurrent;
        final float currentX = window.cursorPosition.x;
        final float lineY1 =
                window.sameLine ? window.cursorPreviousLinePosition.y : window.cursorPosition.y;
        final float lineY2 =
                lineY1 + (window.sameLine ? window.lineSizePrevious.y : window.lineSizeCurrent.y);
        window.drawList.addLineH(currentX - 5.0f, currentX + 5.0f, lineY1, color, 1.0f);
        window.drawList.addLineV(currentX - 0.5f, lineY1, lineY2, color, 1.0f);
        window.drawList.addLineH(currentX - 5.0f, currentX + 5.0f, lineY2, color, 1.0f);
    }

    /**
     * Draw the rectangle of the last item in the foreground draw list, so it is always visible.
     *
     * @param color The color to draw with.
     */
    static void debugDrawItemRect(int color) {
        final RectFloat rect = context.lastItemData.rect;
        IkGuiImplUtils.getForegroundDrawList(context.windowCurrent.viewport)
                .addRect(rect.getLeft(), rect.getTop(), rect.getRight(), rect.getBottom(), color);
    }

    /**
     * Highlight an item for the next couple of frames, when it is submitted.
     *
     * @param targetID The ID of the item.
     */
    static void debugLocateItem(int targetID) {
        context.debugLocateID = targetID;
        context.debugLocateFrames = 2;
        context.debugBreakInLocateID = false;
    }

    /**
     * When the last item is hovered, highlight the item with the given ID. Doesn't work through
     * modal windows, because they clear the hovered window.
     *
     * @param targetID The ID of the item to locate.
     */
    static void debugLocateItemOnHover(int targetID) {
        if (targetID == 0
                || !IkGuiImplUtils.isItemHovered(
                        HoveredFlags.ALLOW_WHEN_BLOCKED_BY_ACTIVE_ITEM
                                | HoveredFlags.ALLOW_WHEN_BLOCKED_BY_POPUP)) {
            return;
        }
        debugLocateItem(targetID);
        final RectFloat rect = context.lastItemData.rect;
        IkGuiImplUtils.getForegroundDrawList(context.windowCurrent.viewport)
                .addRect(
                        rect.getLeft() - 3.0f,
                        rect.getTop() - 3.0f,
                        rect.getRight() + 3.0f,
                        rect.getBottom() + 3.0f,
                        DEBUG_LOCATE_ITEM_COLOR);

        // We can't easily use a context menu here because it will mess with focus, active ID etc.
        if (context.io.configDebugIsDebuggerPresent && context.io.mouseStationaryTimer > 1000) {
            debugBreakButtonTooltip(false, "in itemAdd()");
            if (IkGuiImplKeys.isKeyChordPressed(
                    context.debugBreakKeyChord, InputFlags.NONE, KeyRoutingData.KEY_OWNER_ANY)) {
                context.debugBreakInLocateID = true;
            }
        }
    }

    /**
     * Called when the item being located is submitted, to draw a highlight around it and a line
     * from the mouse to it.
     */
    static void debugLocateItemResolveWithLastItem() {
        // Debug break requested by the user
        if (context.debugBreakInLocateID) {
            debugBreak("item 0x" + Integer.toHexString(context.debugLocateID).toUpperCase());
        }

        context.debugLocateID = 0;
        final DrawList drawList =
                IkGuiImplUtils.getForegroundDrawList(context.windowCurrent.viewport);
        final RectFloat rect = new RectFloat(context.lastItemData.rect);
        rect.expand(3.0f, 3.0f);
        final float p1X = context.io.mousePosition.x;
        final float p1Y = context.io.mousePosition.y;
        final float p2X =
                MathUtil.clamp(p1X, rect.getLeft(), Math.max(rect.getLeft(), rect.getRight()));
        final float p2Y =
                MathUtil.clamp(p1Y, rect.getTop(), Math.max(rect.getTop(), rect.getBottom()));
        drawList.addRect(
                rect.getLeft(),
                rect.getTop(),
                rect.getRight(),
                rect.getBottom(),
                DEBUG_LOCATE_ITEM_COLOR);
        drawList.addLine(p1X, p1Y, p2X, p2Y, DEBUG_LOCATE_ITEM_COLOR);
    }

    /**
     * Hook for itemAdd(), for the locate and item picker tools.
     *
     * @param id The item ID, not 0.
     * @param bb The bounding box of the item.
     */
    static void debugHookItemAdd(int id, @NonNull RectFloat bb) {
        if (id == context.debugLocateID) {
            debugLocateItemResolveWithLastItem();
        }
        if (context.debugItemPickerActive.get() && context.hoveredIDPreviousFrame == id) {
            IkGuiImplUtils.getForegroundDrawList(context.windowCurrent.viewport)
                    .addRect(
                            bb.getLeft(),
                            bb.getTop(),
                            bb.getRight(),
                            bb.getBottom(),
                            Color.rgba(255, 255, 0, 255));
        }
        if (context.debugItemPickerBreakID == id) {
            debugBreak("picked item 0x" + Integer.toHexString(id).toUpperCase());
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Item picker
    // ---------------------------------------------------------------------------------------------

    /** Start the item picker, to visually select an item and break into its call stack. */
    static void debugStartItemPicker() {
        context.debugItemPickerActive.set(true);
    }

    /** Update the item picker tool, called by newFrame(). */
    static void updateDebugToolItemPicker() {
        context.debugItemPickerBreakID = 0;
        if (!context.debugItemPickerActive.get()) {
            return;
        }

        final int hoveredID = context.hoveredIDPreviousFrame;
        IkGuiImplUtils.setMouseCursor(MouseCursor.HAND);
        if (IkGuiImplUtils.isKeyPressed(Key.ESCAPE, true)) {
            context.debugItemPickerActive.set(false);
        }
        final boolean changeMapping = context.io.keyMods == (KeyModFlags.CTRL | KeyModFlags.SHIFT);
        if (!changeMapping
                && IkGuiImplUtils.isMouseClicked(context.debugItemPickerMouseButton, false)
                && hoveredID != 0) {
            context.debugItemPickerBreakID = hoveredID;
            context.debugItemPickerActive.set(false);
        }
        final MouseButton[] buttons = {MouseButton.LEFT, MouseButton.RIGHT, MouseButton.MIDDLE};
        for (MouseButton button : buttons) {
            if (changeMapping && IkGuiImplUtils.isMouseClicked(button, false)) {
                context.debugItemPickerMouseButton = button;
            }
        }
        IkGuiImplWindows.setNextWindowBgAlpha(0.70f);
        if (!IkGuiImplPopups.beginTooltip()) {
            return;
        }
        IkGuiImplText.text(String.format("HoveredId: 0x%08X", hoveredID));
        IkGuiImplText.text("Press ESC to abort picking.");
        if (changeMapping) {
            IkGuiImplText.text("Remap w/ Ctrl+Shift: click anywhere to select new mouse button.");
        } else {
            final String buttonName =
                    switch (context.debugItemPickerMouseButton) {
                        case RIGHT -> "Right";
                        case MIDDLE -> "Middle";
                        default -> "Left";
                    };
            IkGuiImplText.textColored(
                    IkGuiImplUtils.getColor(
                            hoveredID != 0 ? ColorType.TEXT : ColorType.TEXT_DISABLED),
                    "Click " + buttonName + " Button to call debugBreak()! (remap w/ Ctrl+Shift)");
        }
        IkGuiImplPopups.endTooltip();
    }

    // ---------------------------------------------------------------------------------------------
    // ID stack tool
    // ---------------------------------------------------------------------------------------------

    /**
     * Update the query and figure out which ID the ID functions need to report next. The steps are:
     * -1 to query the stack, and 0 or more to query each stack item. We can only perform one query
     * every frame, which keeps the checks in the ID functions cheap.
     *
     * @param query The query.
     * @param id The ID we want to query, or 0 for none.
     * @return The ID to hook, or 0 for none.
     */
    private static int debugItemPathQueryUpdateAndGetHookID(
            @NonNull DebugItemPathQuery query, int id) {
        // Update the query, clearing the hook when there is no active query
        if (query.mainID != id) {
            query.mainID = id;
            query.step = -1;
            query.complete = false;
            query.results.clear();
        }
        query.active = false;
        if (id == 0) {
            return 0;
        }

        // Advance to the next stack level when we got our result, or after 2 frames (in case we
        // never get a result)
        if (query.step >= 0 && query.step < query.results.size()) {
            final DebugItemPathQuery.StackLevelInfo info = query.results.get(query.step);
            if (info.querySuccess || info.queryFrameCount > 2) {
                query.step++;
            }
        }

        // Update the status and hook
        query.complete = query.step == query.results.size();
        if (query.step == -1) {
            query.active = true;
            return id;
        } else if (query.step >= 0 && query.step < query.results.size()) {
            final DebugItemPathQuery.StackLevelInfo info = query.results.get(query.step);
            info.queryFrameCount++;
            query.active = true;
            return info.id;
        }
        return 0;
    }

    /** Update the ID stack tool query, called by newFrame(). */
    static void updateDebugToolItemPathQuery() {
        int id = 0;
        if (context.debugIDStackTool.lastActiveFrame + 1 == context.frameCount) {
            id =
                    context.hoveredIDPreviousFrame != 0
                            ? context.hoveredIDPreviousFrame
                            : context.activeID;
        }
        context.debugHookIDInfoID =
                debugItemPathQueryUpdateAndGetHookID(context.debugItemPathQuery, id);
    }

    /**
     * Hook called by the ID functions when they produce the ID the ID stack tool is looking for.
     *
     * @param id The ID that was produced.
     * @param data What was hashed to produce the ID: a String, an Integer, or null when the ID was
     *     pushed directly as an override.
     */
    static void debugHookIDInfo(int id, Object data) {
        final DebugItemPathQuery query = context.debugItemPathQuery;
        if (!query.active) {
            return;
        }
        final Window window = context.windowCurrent;
        if (window == null) {
            return;
        }

        // Step -1: query the stack. This assumes that the ID was computed with the current ID
        // stack, which tends to be the case for our widgets.
        final int stackSize = window.idStack.size();
        if (query.step == -1) {
            query.step++;
            query.results.clear();
            // The bottom of the ID stack is the window ID
            for (int n = 0; n < stackSize; ++n) {
                query.results.add(new DebugItemPathQuery.StackLevelInfo(window.idStack.getInt(n)));
            }
            query.results.add(new DebugItemPathQuery.StackLevelInfo(id));
            return;
        }

        // Step 0 or more: query for an individual level
        if (query.step != stackSize || query.step >= query.results.size()) {
            return;
        }
        final DebugItemPathQuery.StackLevelInfo info = query.results.get(query.step);
        if (info.id != id) {
            return;
        }
        if (info.description == null) {
            if (data instanceof String string) {
                info.description = string;
                info.fromString = true;
            } else if (data instanceof Integer integer) {
                info.description = Integer.toString(integer);
            } else {
                // Pushing an override ID is often used to avoid hashing twice, which would call
                // this hook twice. We prioritize the first one.
                info.description = String.format("0x%08X [override]", id);
            }
        }
        info.querySuccess = true;
    }

    /**
     * Skip the part of a string that doesn't contribute to its ID, which is everything before a
     * "###".
     *
     * @param text The text.
     * @return The part of the text that is hashed.
     */
    private static String skipUncontributingPrefix(@NonNull String text) {
        final int idStart = text.indexOf("###");
        return idStart >= 0 ? text.substring(idStart) : text;
    }

    /**
     * Describe one level of the ID stack.
     *
     * @param query The query.
     * @param n The stack level.
     * @param formatForUI If we are displaying it in the tool, which adds quotes and decorations.
     * @return The description.
     */
    private static String debugItemPathQueryFormatLevelInfo(
            @NonNull DebugItemPathQuery query, int n, boolean formatForUI) {
        final DebugItemPathQuery.StackLevelInfo info = query.results.get(n);
        final Window window =
                (info.description == null && n == 0) ? IkGuiInternal.findWindowByID(info.id) : null;
        if (window != null) {
            // The window name, because the root ID doesn't come from the ID functions
            final String name = skipUncontributingPrefix(window.name);
            return formatForUI ? "\"" + name + "\" [window]" : name;
        }
        if (info.querySuccess) {
            final String description = skipUncontributingPrefix(info.description);
            return (formatForUI && info.fromString) ? "\"" + description + "\"" : description;
        }
        if (query.step < query.results.size()) {
            // Only use the fallback once all queries are done, so we don't flicker ??? markers
            return "";
        }
        return "???";
    }

    /**
     * Build the full path of the queried ID, like "//Window/Child/Button".
     *
     * @param query The query.
     * @param hexEncodeNonAsciiChars Escape non-ASCII characters as hex.
     * @return The path.
     */
    private static String debugItemPathQueryGetResultAsPath(
            @NonNull DebugItemPathQuery query, boolean hexEncodeNonAsciiChars) {
        final StringBuilder result = new StringBuilder();
        for (int n = 0; n < query.results.size(); ++n) {
            final String levelDescription = debugItemPathQueryFormatLevelInfo(query, n, false);
            result.append(n == 0 ? "//" : "/");
            levelDescription
                    .codePoints()
                    .forEach(
                            c -> {
                                if (c == '/') {
                                    result.append('\\');
                                }
                                if (c < 256 || !hexEncodeNonAsciiChars) {
                                    result.appendCodePoint(c);
                                } else {
                                    for (byte b :
                                            new String(Character.toChars(c))
                                                    .getBytes(
                                                            java.nio.charset.StandardCharsets
                                                                    .UTF_8)) {
                                        result.append(String.format("\\x%02x", b & 0xFF));
                                    }
                                }
                            });
        }
        return result.toString();
    }

    /**
     * Show the ID stack tool window, which shows how the ID of the hovered item was built.
     *
     * @param open The open state of the window, may be null.
     */
    static void showIDStackToolWindow(IkBoolean open) {
        if ((context.nextWindowData.fieldFlags & NextWindowFlags.HAS_SIZE) == 0) {
            IkGuiImplWindows.setNextWindowSize(
                    0.0f, IkGuiInternal.getFontSize() * 8.0f, Condition.FIRST_USE_EVER);
        }
        if (!IkGuiImplWindows.begin("IkGui ID Stack Tool", open, WindowFlags.NONE)
                || IkGuiInternal.getCurrentWindow().beginCount > 1) {
            IkGuiImplWindows.end();
            return;
        }

        final DebugItemPathQuery query = context.debugItemPathQuery;
        final IDStackTool tool = context.debugIDStackTool;
        tool.lastActiveFrame = context.frameCount;
        final String resultPath =
                debugItemPathQueryGetResultAsPath(query, tool.optHexEncodeNonAsciiChars.get());
        IkGuiImplText.text(String.format("0x%08X", query.mainID));
        IkGuiImplLayout.sameLine(0.0f, -1.0f);
        IkGuiImplMetrics.metricsHelpMarker(
                "Hover an item with the mouse to display elements of the ID Stack leading to the"
                        + " item's final ID.\nEach level of the stack correspond to a pushID()"
                        + " call.\nAll levels of the stack are hashed together to make the final"
                        + " ID of a widget (ID displayed at the bottom level of the stack).");

        // Ctrl+C to copy the path
        final long timeSinceCopy = context.time - tool.copyToClipboardLastTime;
        IkGuiImplUtils.pushStyleVarFloat2(
                StyleVariable.FRAME_PADDING, context.style.variable.framePadding.x, 0.0f);
        IkGuiImplMiscWidgets.checkbox("Hex-encode non-ASCII", tool.optHexEncodeNonAsciiChars);
        IkGuiImplLayout.sameLine(0.0f, -1.0f);
        IkGuiImplMiscWidgets.checkbox("Ctrl+C: copy path", tool.optCopyToClipboardOnCtrlC);
        IkGuiImplUtils.popStyleVar();
        IkGuiImplLayout.sameLine(0.0f, -1.0f);
        final boolean flashCopied =
                timeSinceCopy >= 0 && timeSinceCopy < 750 && (timeSinceCopy % 250) < 125;
        IkGuiImplText.textColored(
                flashCopied ? Color.rgba(255, 255, 77, 255) : Color.CLEAR, "*COPIED*");
        if (tool.optCopyToClipboardOnCtrlC.get()
                && IkGuiImplKeys.shortcut(
                        KeyChord.of(KeyModFlags.CTRL, Key.C),
                        InputFlags.ROUTE_GLOBAL | InputFlags.ROUTE_OVER_FOCUSED,
                        0)) {
            tool.copyToClipboardLastTime = context.time;
            IkGuiImplUtils.setClipboardText(resultPath);
        }

        IkGuiImplText.text("- Path \"" + (query.complete ? resultPath : "") + "\"");
        IkGuiImplLayout.separator();

        // Display the decorated stack
        if (!query.results.isEmpty()
                && IkGuiImplTables.beginTable("##table", 3, TableFlags.BORDERS, 0, 0, 0)) {
            final Vector2f idSize = new Vector2f();
            IkGuiImplUtils.calcTextSize(idSize, "0xDDDDDDDD", false, -1.0f);
            IkGuiImplTables.tableSetupColumn("Seed", TableColumnFlags.WIDTH_FIXED, idSize.x, 0);
            IkGuiImplTables.tableSetupColumn("PushID", TableColumnFlags.WIDTH_STRETCH, 0.0f, 0);
            IkGuiImplTables.tableSetupColumn("Result", TableColumnFlags.WIDTH_FIXED, idSize.x, 0);
            IkGuiImplTableHeaders.tableHeadersRow();
            for (int n = 0; n < query.results.size(); ++n) {
                final DebugItemPathQuery.StackLevelInfo info = query.results.get(n);
                IkGuiImplTables.tableNextColumn();
                IkGuiImplText.text(
                        String.format("0x%08X", n > 0 ? query.results.get(n - 1).id : 0));
                IkGuiImplTables.tableNextColumn();
                IkGuiImplText.textUnformatted(debugItemPathQueryFormatLevelInfo(query, n, true));
                IkGuiImplTables.tableNextColumn();
                IkGuiImplText.text(String.format("0x%08X", info.id));
                if (n == query.results.size() - 1) {
                    IkGuiImplTables.tableSetBackgroundColor(
                            TableBackgroundTarget.CELL_BACKGROUND,
                            IkGuiImplUtils.getColor(ColorType.HEADER),
                            -1);
                }
            }
            IkGuiImplTables.endTable();
        }
        IkGuiImplWindows.end();
    }

    // ---------------------------------------------------------------------------------------------
    // Frame updates
    // ---------------------------------------------------------------------------------------------

    /** Update the debug tools at the start of the frame, called by newFrame(). */
    static void debugToolsNewFrame() {
        updateDebugToolItemPicker();
        updateDebugToolItemPathQuery();
        IkGuiImplMetrics.updateDebugToolFlashStyleColor();
        if (context.debugLocateFrames > 0 && --context.debugLocateFrames == 0) {
            context.debugLocateID = 0;
            context.debugBreakInLocateID = false;
        }
        if (context.debugLogAutoDisableFrames > 0 && --context.debugLogAutoDisableFrames == 0) {
            context.debugLogFlags = context.debugLogFlags & ~context.debugLogAutoDisableFlags;
            context.debugLogAutoDisableFlags = DebugLogFlags.NONE;
        }
    }
}
