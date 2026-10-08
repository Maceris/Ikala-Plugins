package com.ikalagaming.graphics.frontend.gui;

import com.ikalagaming.graphics.frontend.gui.data.Context;
import com.ikalagaming.graphics.frontend.gui.data.Window;
import com.ikalagaming.graphics.frontend.gui.flags.ItemFlags;
import com.ikalagaming.graphics.frontend.gui.flags.LogFlags;
import com.ikalagaming.graphics.frontend.gui.flags.SliderFlags;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector2f;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Logging/capturing. All text output from the interface can be captured into the terminal, a file,
 * the clipboard, or a buffer. By default, tree nodes are automatically opened during logging.
 */
@Slf4j
class IkGuiImplLogging {
    /** The new line used in logs, consistent across platforms. */
    private static final String NEWLINE = "\n";

    static Context context;

    /**
     * Start logging/capturing text output.
     *
     * @param logFlags Exactly one output type flag.
     * @param autoOpenDepth Tree nodes are opened up to this depth, or the default depth if
     *     negative.
     * @see LogFlags
     */
    static void logBegin(int logFlags, int autoOpenDepth) {
        final Window window = context.windowCurrent;
        if (context.logEnabled) {
            IkGuiImplDebugTools.reportError(log, "logBegin() called while already logging");
            return;
        }
        final int output = logFlags & LogFlags.OUTPUT_MASK;
        if (output == 0 || (output & (output - 1)) != 0) {
            IkGuiImplDebugTools.reportError(
                    log, "logBegin() requires exactly one output type flag");
            return;
        }

        context.logEnabled = true;
        context.itemUnclipByLog = true;
        context.logFlags = logFlags;
        context.logWindow = window;
        context.logNextPrefix = null;
        context.logNextSuffix = null;
        context.logDepthRef = window != null ? window.treeDepth : 0;
        context.logDepthToExpand =
                autoOpenDepth >= 0 ? autoOpenDepth : context.logDepthToExpandDefault;
        context.logLinePosY = Float.MAX_VALUE;
        context.logLineFirstItem = true;
        context.logBuffer.setLength(0);
    }

    /**
     * Helper to display buttons for logging to the terminal, a file, or the clipboard, and a slider
     * for the default depth to expand.
     */
    public static void logButtons() {
        IkGuiImplUtils.pushID("LogButtons");
        final boolean logToTTY = IkGuiImplButtons.button("Log To TTY", 0, 0);
        IkGuiImplLayout.sameLine(0, -1);
        final boolean logToFile = IkGuiImplButtons.button("Log To File", 0, 0);
        IkGuiImplLayout.sameLine(0, -1);
        final boolean logToClipboard = IkGuiImplButtons.button("Log To Clipboard", 0, 0);
        IkGuiImplLayout.sameLine(0, -1);
        IkGuiImplUtils.pushItemFlag(ItemFlags.NO_TAB_STOP, true);
        final Vector2f size = new Vector2f();
        IkGuiImplUtils.calcTextSize(size, "999", false, -1.0f);
        IkGuiImplUtils.setNextItemWidth(size.x);
        final int[] depth = {context.logDepthToExpandDefault};
        if (IkGuiImplSliders.sliderInt(
                "Default Depth",
                depth,
                0,
                9,
                IkGuiImplMiscWidgets.INT_DEFAULT_FORMAT,
                SliderFlags.NONE)) {
            context.logDepthToExpandDefault = depth[0];
        }
        IkGuiImplUtils.popItemFlag();
        IkGuiImplUtils.popID();

        // Start logging at the end of the function so that the buttons don't appear in the log
        if (logToTTY) {
            logToTTY(-1);
        }
        if (logToFile) {
            logToFile(-1, null);
        }
        if (logToClipboard) {
            logToClipboard(-1);
        }
    }

    /** Stop logging, writing out or closing the output as appropriate. */
    public static void logFinish() {
        if (!context.logEnabled) {
            return;
        }

        logText(NEWLINE);
        switch (context.logFlags & LogFlags.OUTPUT_MASK) {
            case LogFlags.OUTPUT_TERMINAL -> flushOutput(false);
            case LogFlags.OUTPUT_FILE -> flushOutput(true);
            case LogFlags.OUTPUT_CLIPBOARD -> {
                if (!context.logBuffer.isEmpty()) {
                    IkGuiImplUtils.setClipboardText(context.logBuffer.toString());
                }
            }
            default -> {
                // The buffer output is left for the caller to read until logging starts again
            }
        }

        // Unlike ImGui, we keep buffered output so it can be read after logging finishes
        if ((context.logFlags & LogFlags.OUTPUT_BUFFER) == 0) {
            context.logBuffer.setLength(0);
        }
        context.logEnabled = false;
        context.itemUnclipByLog = false;
        context.logFlags = LogFlags.NONE;
        context.logOutput = null;
    }

    /**
     * Log text that was rendered, splitting it into lines with indentation based on the tree depth,
     * and adding new lines based on the position of the text.
     *
     * @param refPosY The y position of the text, used to decide on new lines, or NaN if there is no
     *     position.
     * @param text The text, which is logged as-is.
     */
    static void logRenderedText(float refPosY, @NonNull String text) {
        final Window window = context.windowCurrent;

        final String prefix = context.logNextPrefix;
        final String suffix = context.logNextSuffix;
        context.logNextPrefix = null;
        context.logNextSuffix = null;

        final boolean hasPosition = !Float.isNaN(refPosY);
        final boolean logNewLine =
                hasPosition
                        && refPosY
                                > context.logLinePosY
                                        + Math.max(
                                                context.style.variable.framePadding.y,
                                                context.style.variable.itemSpacing.y)
                                        + 1;
        if (hasPosition) {
            context.logLinePosY = refPosY;
        }
        if (logNewLine) {
            logText(NEWLINE);
            context.logLineFirstItem = true;
        }

        if (prefix != null) {
            logRenderedText(refPosY, prefix);
        }

        // Re-adjust padding if we have popped out of our starting depth
        final int windowDepth = window != null ? window.treeDepth : 0;
        if (context.logDepthRef > windowDepth) {
            context.logDepthRef = windowDepth;
        }
        final int treeDepth = windowDepth - context.logDepthRef;

        // Split the string. Each new line (after a '\n') is followed by indentation corresponding
        // to the current depth of our log entry. We don't add a trailing new line yet, to allow a
        // subsequent item on the same line to be captured.
        int lineStart = 0;
        while (true) {
            int lineEnd = text.indexOf('\n', lineStart);
            final boolean isLastLine = lineEnd < 0;
            if (isLastLine) {
                lineEnd = text.length();
            }
            if (lineStart != lineEnd || !isLastLine) {
                final int indentation = context.logLineFirstItem ? treeDepth * 4 : 1;
                logText(" ".repeat(indentation) + text.substring(lineStart, lineEnd));
                context.logLineFirstItem = false;
                if (!isLastLine) {
                    logText(NEWLINE);
                    context.logLineFirstItem = true;
                }
            }
            if (isLastLine) {
                break;
            }
            lineStart = lineEnd + 1;
        }

        if (suffix != null) {
            logRenderedText(refPosY, suffix);
        }
    }

    /**
     * Set the text to log before and after the next rendered text, e.g. "[" and "]" for buttons.
     *
     * @param prefix The prefix, or null for none.
     * @param suffix The suffix, or null for none.
     */
    static void logSetNextTextDecoration(String prefix, String suffix) {
        context.logNextPrefix = prefix;
        context.logNextSuffix = suffix;
    }

    /** Clean up logging when the context is destroyed. */
    static void logShutdown() {
        if (context.logOutput != null) {
            flushOutput((context.logFlags & LogFlags.OUTPUT_FILE) != 0);
            context.logOutput = null;
        }
        context.logEnabled = false;
        context.logBuffer.setLength(0);
    }

    /**
     * Pass text data straight to the log, without being displayed.
     *
     * @param text The text.
     */
    public static void logText(@NonNull String text) {
        if (!context.logEnabled) {
            return;
        }
        if (context.logOutput != null) {
            try {
                context.logOutput.write(text);
            } catch (IOException e) {
                log.warn("Failed to write log output", e);
            }
        } else {
            context.logBuffer.append(text);
        }
    }

    /**
     * Start logging to a buffer, which can be read from the context log buffer once logging
     * finishes.
     *
     * @param autoOpenDepth Tree nodes are opened up to this depth, or the default depth if
     *     negative.
     */
    static void logToBuffer(int autoOpenDepth) {
        if (context.logEnabled) {
            return;
        }
        logBegin(LogFlags.OUTPUT_BUFFER, autoOpenDepth);
    }

    /**
     * Start logging to the clipboard, which is set when logging finishes.
     *
     * @param autoOpenDepth Tree nodes are opened up to this depth, or the default depth if
     *     negative.
     */
    public static void logToClipboard(int autoOpenDepth) {
        if (context.logEnabled) {
            return;
        }
        logBegin(LogFlags.OUTPUT_CLIPBOARD, autoOpenDepth);
    }

    /**
     * Start logging to a file, appending to it.
     *
     * @param autoOpenDepth Tree nodes are opened up to this depth, or the default depth if
     *     negative.
     * @param filename The file to log to, or null to use io.logFilename.
     */
    public static void logToFile(int autoOpenDepth, String filename) {
        if (context.logEnabled) {
            return;
        }
        if (filename == null) {
            filename = context.io.logFilename;
        }
        if (filename == null || filename.isEmpty()) {
            return;
        }
        final Writer writer;
        try {
            writer =
                    Files.newBufferedWriter(
                            Path.of(filename),
                            StandardCharsets.UTF_8,
                            StandardOpenOption.CREATE,
                            StandardOpenOption.APPEND);
        } catch (IOException e) {
            log.warn("Failed to open log file {}", filename, e);
            return;
        }

        logBegin(LogFlags.OUTPUT_FILE, autoOpenDepth);
        context.logOutput = writer;
    }

    /**
     * Start logging to the terminal (standard output).
     *
     * @param autoOpenDepth Tree nodes are opened up to this depth, or the default depth if
     *     negative.
     */
    public static void logToTTY(int autoOpenDepth) {
        if (context.logEnabled) {
            return;
        }
        logBegin(LogFlags.OUTPUT_TERMINAL, autoOpenDepth);
        context.logOutput = new OutputStreamWriter(System.out, StandardCharsets.UTF_8);
    }

    /**
     * Flush the log output, and optionally close it.
     *
     * @param close Whether to close the output, which we don't want to do for standard output.
     */
    private static void flushOutput(boolean close) {
        if (context.logOutput == null) {
            return;
        }
        try {
            if (close) {
                context.logOutput.close();
            } else {
                context.logOutput.flush();
            }
        } catch (IOException e) {
            log.warn("Failed to write log output", e);
        }
    }

    /** Private constructor so this is not instantiated. */
    private IkGuiImplLogging() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
